package com.onvifguard.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.onvifguard.app.R
import com.onvifguard.app.data.MotionEvent
import com.onvifguard.app.data.MotionType
import com.onvifguard.app.ui.MainActivity

object NotificationHelper {

    const val CHANNEL_MOTION_ALERTS = "onvif_motion_urgent_alerts"
    const val CHANNEL_SERVICE_STATUS = "onvif_service_status"

    const val NOTIFICATION_SERVICE_ID = 1001
    private var alertNotificationId = 2000

    private val urgentVibratePattern = longArrayOf(0, 350, 150, 350, 150, 500)

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build()

            // 1. High Priority Urgent Motion Channel (Heads-up popups)
            val motionChannel = NotificationChannel(
                CHANNEL_MOTION_ALERTS,
                context.getString(R.string.channel_motion_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.channel_motion_desc)
                enableVibration(true)
                vibrationPattern = urgentVibratePattern
                setSound(soundUri, audioAttributes)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setShowBadge(true)
                enableLights(true)
            }

            // 2. Persistent Foreground Service Channel (Low noise)
            val serviceChannel = NotificationChannel(
                CHANNEL_SERVICE_STATUS,
                context.getString(R.string.channel_service_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.channel_service_desc)
                setShowBadge(false)
            }

            notificationManager.createNotificationChannel(motionChannel)
            notificationManager.createNotificationChannel(serviceChannel)
        }
    }

    fun buildServiceNotification(context: Context, statusText: String): Notification {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_SERVICE_STATUS)
            .setContentTitle("ONVIF Guard • Active")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun sendInstantMotionAlert(
        context: Context,
        event: MotionEvent,
        playSound: Boolean = true,
        vibrate: Boolean = true
    ) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_NAV_LIVE", true)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            (System.currentTimeMillis() % 10000).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = when (event.type) {
            MotionType.PERSON -> "🚨 PERSON DETECTED AT CAMERA!"
            MotionType.LINE_CROSSING -> "⚠️ PERIMETER BREACH DETECTED!"
            MotionType.INTRUSION -> "⚠️ ZONE INTRUSION ALERT!"
            else -> "🚨 MOTION DETECTED!"
        }

        val timeStr = event.getFormattedTime()
        val contentText = "${event.details} at $timeStr"

        val builder = NotificationCompat.Builder(context, CHANNEL_MOTION_ALERTS)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSubText("Fast ONVIF Alert")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        if (vibrate) {
            builder.setVibrate(urgentVibratePattern)
        } else {
            builder.setVibrate(longArrayOf(0))
        }

        if (playSound) {
            builder.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
        } else {
            builder.setSilent(true)
        }

        alertNotificationId = if (alertNotificationId > 2999) 2000 else alertNotificationId + 1
        notificationManager.notify(alertNotificationId, builder.build())
    }
}
