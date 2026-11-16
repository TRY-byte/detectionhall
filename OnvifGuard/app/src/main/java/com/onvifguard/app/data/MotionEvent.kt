package com.onvifguard.app.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class MotionType {
    MOTION,
    PERSON,
    INTRUSION,
    LINE_CROSSING,
    GENERIC
}

data class MotionEvent(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val type: MotionType = MotionType.MOTION,
    val topic: String = "",
    val details: String = "Motion activity detected by ONVIF camera"
) {
    fun getFormattedTime(): String {
        val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun getFormattedDate(): String {
        val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}
