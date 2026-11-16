package com.onvifguard.app.data

data class CameraConfig(
    val host: String = "",
    val port: Int = 80,
    val username: String = "admin",
    val password: String = "",
    val rtspPort: Int = 554,
    val rtspUrl: String = "",
    val profileToken: String = "",
    val mediaServiceUrl: String = "",
    val eventServiceUrl: String = "",
    val cooldownSeconds: Int = 3,
    val alarmSound: Boolean = true,
    val vibrate: Boolean = true,
    val lowLatencyPolling: Boolean = true // PT3S fast poll
) {
    fun getDeviceServiceUrl(): String {
        return "http://$host:$port/onvif/device_service"
    }

    fun isConfigured(): Boolean {
        return host.isNotBlank() && port > 0
    }
}
