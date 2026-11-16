package com.onvifguard.app.data

import android.content.Context
import android.content.SharedPreferences

class CameraPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("onvif_guard_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_HOST = "camera_host"
        private const val KEY_PORT = "camera_port"
        private const val KEY_USER = "camera_user"
        private const val KEY_PASS = "camera_pass"
        private const val KEY_RTSP_PORT = "camera_rtsp_port"
        private const val KEY_RTSP_URL = "camera_rtsp_url"
        private const val KEY_PROFILE_TOKEN = "camera_profile_token"
        private const val KEY_MEDIA_URL = "camera_media_url"
        private const val KEY_EVENT_URL = "camera_event_url"
        private const val KEY_COOLDOWN = "camera_cooldown"
        private const val KEY_ALARM_SOUND = "camera_alarm_sound"
        private const val KEY_VIBRATE = "camera_vibrate"
        private const val KEY_FAST_POLL = "camera_fast_poll"
    }

    fun saveConfig(config: CameraConfig) {
        prefs.edit().apply {
            putString(KEY_HOST, config.host)
            putInt(KEY_PORT, config.port)
            putString(KEY_USER, config.username)
            putString(KEY_PASS, config.password)
            putInt(KEY_RTSP_PORT, config.rtspPort)
            putString(KEY_RTSP_URL, config.rtspUrl)
            putString(KEY_PROFILE_TOKEN, config.profileToken)
            putString(KEY_MEDIA_URL, config.mediaServiceUrl)
            putString(KEY_EVENT_URL, config.eventServiceUrl)
            putInt(KEY_COOLDOWN, config.cooldownSeconds)
            putBoolean(KEY_ALARM_SOUND, config.alarmSound)
            putBoolean(KEY_VIBRATE, config.vibrate)
            putBoolean(KEY_FAST_POLL, config.lowLatencyPolling)
            apply()
        }
    }

    fun loadConfig(): CameraConfig {
        return CameraConfig(
            host = prefs.getString(KEY_HOST, "") ?: "",
            port = prefs.getInt(KEY_PORT, 80),
            username = prefs.getString(KEY_USER, "admin") ?: "admin",
            password = prefs.getString(KEY_PASS, "") ?: "",
            rtspPort = prefs.getInt(KEY_RTSP_PORT, 554),
            rtspUrl = prefs.getString(KEY_RTSP_URL, "") ?: "",
            profileToken = prefs.getString(KEY_PROFILE_TOKEN, "") ?: "",
            mediaServiceUrl = prefs.getString(KEY_MEDIA_URL, "") ?: "",
            eventServiceUrl = prefs.getString(KEY_EVENT_URL, "") ?: "",
            cooldownSeconds = prefs.getInt(KEY_COOLDOWN, 3),
            alarmSound = prefs.getBoolean(KEY_ALARM_SOUND, true),
            vibrate = prefs.getBoolean(KEY_VIBRATE, true),
            lowLatencyPolling = prefs.getBoolean(KEY_FAST_POLL, true)
        )
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
