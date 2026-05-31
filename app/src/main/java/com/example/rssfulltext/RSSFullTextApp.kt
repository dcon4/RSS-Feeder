package com.example.rssfulltext

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.example.rssfulltext.logging.DebugLogger

class RSSFullTextApp : Application() {

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "feed_server_channel"
    }

    override fun onCreate() {
        super.onCreate()
        DebugLogger.init(this)
        DebugLogger.log("App", "Application onCreate")
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_description)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
