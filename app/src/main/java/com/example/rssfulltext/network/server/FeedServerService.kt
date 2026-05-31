package com.example.rssfulltext.network.server

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.rssfulltext.MainActivity
import com.example.rssfulltext.RSSFullTextApp
import com.example.rssfulltext.data.db.AppDatabase
import com.example.rssfulltext.logging.DebugLogger

/**
 * Foreground service that keeps the NanoHTTPD feed server running
 * so other apps can access the feeds at any time.
 */
class FeedServerService : Service() {

    companion object {
        private const val TAG = "FeedServerService"
        private const val NOTIFICATION_ID = 1001

        private const val ACTION_START = "com.example.rssfulltext.action.START_SERVER"
        private const val ACTION_STOP = "com.example.rssfulltext.action.STOP_SERVER"
        private const val EXTRA_PORT = "extra_port"
        private const val EXTRA_BIND_ALL = "extra_bind_all"

        fun startService(context: Context, port: Int = FeedHttpServer.DEFAULT_PORT, bindAll: Boolean = false) {
            val intent = Intent(context, FeedServerService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_PORT, port)
                putExtra(EXTRA_BIND_ALL, bindAll)
            }
            context.startForegroundService(intent)
        }

        fun stopService(context: Context) {
            val intent = Intent(context, FeedServerService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private var httpServer: FeedHttpServer? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val port = intent.getIntExtra(EXTRA_PORT, FeedHttpServer.DEFAULT_PORT)
                val bindAll = intent.getBooleanExtra(EXTRA_BIND_ALL, false)
                startServer(port, bindAll)
            }
            ACTION_STOP -> {
                stopServer()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopServer()
        super.onDestroy()
    }

    private fun startServer(port: Int, bindAll: Boolean) {
        DebugLogger.log(TAG, "Starting feed server service on port $port")

        startForeground(NOTIFICATION_ID, createNotification(port))

        try {
            val database = AppDatabase.getInstance(applicationContext)
            httpServer = FeedHttpServer(database, port, bindAll).also {
                it.startServer()
            }
            DebugLogger.log(TAG, "Feed server service started successfully")
        } catch (e: Exception) {
            DebugLogger.log(TAG, "Failed to start feed server: ${e.message}")
            stopSelf()
        }
    }

    private fun stopServer() {
        httpServer?.stopServer()
        httpServer = null
        DebugLogger.log(TAG, "Feed server service stopped")
    }

    private fun createNotification(port: Int): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, RSSFullTextApp.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("RSS Full Text Server")
            .setContentText("Serving feeds on port $port")
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
