package com.onvifguard.app

import android.app.Application
import com.onvifguard.app.service.NotificationHelper

class OnvifApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createNotificationChannels(this)
    }
}
