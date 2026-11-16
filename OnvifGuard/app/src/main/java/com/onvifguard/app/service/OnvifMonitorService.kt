package com.onvifguard.app.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import com.onvifguard.app.data.CameraConfig
import com.onvifguard.app.data.CameraPreferences
import com.onvifguard.app.data.MotionEvent
import com.onvifguard.app.onvif.OnvifClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class OnvifMonitorService : Service() {

    companion object {
        const val ACTION_START = "com.onvifguard.action.START"
        const val ACTION_STOP = "com.onvifguard.action.STOP"

        private val _isRunning = MutableStateFlow(false)
        val isRunning = _isRunning.asStateFlow()

        private val _currentStatus = MutableStateFlow("Idle")
        val currentStatus = _currentStatus.asStateFlow()

        private val _lastMotionEvent = MutableStateFlow<MotionEvent?>(null)
        val lastMotionEvent = _lastMotionEvent.asStateFlow()

        private val _eventHistory = MutableStateFlow<List<MotionEvent>>(emptyList())
        val eventHistory = _eventHistory.asStateFlow()

        private val _lastLatencyMs = MutableStateFlow<Long>(0L)
        val lastLatencyMs = _lastLatencyMs.asStateFlow()

        fun startService(context: Context) {
            val intent = Intent(context, OnvifMonitorService::class.java).apply {
                action = ACTION_START
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, OnvifMonitorService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun clearHistory() {
            _eventHistory.value = emptyList()
            _lastMotionEvent.value = null
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var monitorJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val onvifClient = OnvifClient()
    private var subscriptionUrl: String? = null
    private var lastAlertTimeMs: Long = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "OnvifGuard::MonitorWakeLock"
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startMonitoring()
            }
            ACTION_STOP -> {
                stopMonitoring()
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun startMonitoring() {
        if (_isRunning.value) return

        wakeLock?.acquire(12 * 60 * 60 * 1000L) // 12 hours safe max
        _isRunning.value = true
        _currentStatus.value = "Connecting to camera..."

        val notification = NotificationHelper.buildServiceNotification(
            this,
            "Monitoring for instant motion alerts..."
        )
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            startForeground(
                NotificationHelper.NOTIFICATION_SERVICE_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NotificationHelper.NOTIFICATION_SERVICE_ID, notification)
        }

        monitorJob?.cancel()
        monitorJob = serviceScope.launch {
            val prefs = CameraPreferences(applicationContext)
            var config = prefs.loadConfig()

            if (!config.isConfigured()) {
                _currentStatus.value = "Camera not configured"
                stopMonitoring()
                return@launch
            }

            // Step 1: Initialize services if URLs are missing
            if (config.eventServiceUrl.isBlank() || config.mediaServiceUrl.isBlank()) {
                _currentStatus.value = "Discovering ONVIF services..."
                val initResult = onvifClient.testAndInitialize(config)
                if (initResult.isSuccess) {
                    config = initResult.getOrThrow()
                    prefs.saveConfig(config)
                } else {
                    _currentStatus.value = "Connection failed: ${initResult.exceptionOrNull()?.message}"
                    delay(5000)
                }
            }

            var consecutiveErrors = 0

            while (isActive && _isRunning.value) {
                try {
                    // Create PullPoint subscription if not present
                    if (subscriptionUrl.isNullOrBlank()) {
                        _currentStatus.value = "Subscribing to camera events..."
                        val subResult = onvifClient.createPullPointSubscription(config)
                        if (subResult.isSuccess) {
                            subscriptionUrl = subResult.getOrThrow()
                            _currentStatus.value = "Monitoring Active • Fast Response ON"
                            consecutiveErrors = 0
                        } else {
                            consecutiveErrors++
                            _currentStatus.value = "Sub error ($consecutiveErrors): ${subResult.exceptionOrNull()?.message}"
                            delay(3000)
                            continue
                        }
                    }

                    // Poll messages with 3-second long poll for low-latency notifications
                    val pollStart = System.currentTimeMillis()
                    val pollResult = onvifClient.pullEvents(
                        subscriptionUrl = subscriptionUrl!!,
                        config = config,
                        timeoutSeconds = if (config.lowLatencyPolling) 3 else 5
                    )
                    val elapsed = System.currentTimeMillis() - pollStart
                    _lastLatencyMs.value = elapsed

                    if (pollResult.isSuccess) {
                        val events = pollResult.getOrThrow()
                        consecutiveErrors = 0
                        _currentStatus.value = "Monitoring Active • Ping: ${elapsed}ms"

                        if (events.isNotEmpty()) {
                            val now = System.currentTimeMillis()
                            val cooldownMs = config.cooldownSeconds * 1000L

                            for (event in events) {
                                // Add to event history
                                val currentList = _eventHistory.value.toMutableList()
                                currentList.add(0, event)
                                if (currentList.size > 100) {
                                    currentList.removeAt(currentList.lastIndex)
                                }
                                _eventHistory.value = currentList
                                _lastMotionEvent.value = event

                                // Check cooldown for notifications
                                if (now - lastAlertTimeMs >= cooldownMs) {
                                    lastAlertTimeMs = now
                                    NotificationHelper.sendInstantMotionAlert(
                                        context = applicationContext,
                                        event = event,
                                        playSound = config.alarmSound,
                                        vibrate = config.vibrate
                                    )
                                }
                            }
                        }
                    } else {
                        val err = pollResult.exceptionOrNull()
                        val errMsg = err?.message ?: "Unknown error"

                        // If subscription expired or HTTP 400/404/500, re-subscribe
                        if (errMsg.contains("400") || errMsg.contains("404") || errMsg.contains("Subscription")) {
                            subscriptionUrl = null
                        }

                        consecutiveErrors++
                        _currentStatus.value = "Poll retry ($consecutiveErrors)..."
                        delay(2000)
                    }
                } catch (e: Exception) {
                    subscriptionUrl = null
                    consecutiveErrors++
                    _currentStatus.value = "Error: ${e.message}"
                    delay(3000)
                }
            }
        }
    }

    private fun stopMonitoring() {
        _isRunning.value = false
        _currentStatus.value = "Monitoring stopped"
        monitorJob?.cancel()
        monitorJob = null

        serviceScope.launch {
            val url = subscriptionUrl
            if (!url.isNullOrBlank()) {
                val prefs = CameraPreferences(applicationContext)
                val config = prefs.loadConfig()
                onvifClient.unsubscribe(url, config)
            }
            subscriptionUrl = null
        }

        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onDestroy() {
        stopMonitoring()
        serviceScope.cancel()
        super.onDestroy()
    }
}
