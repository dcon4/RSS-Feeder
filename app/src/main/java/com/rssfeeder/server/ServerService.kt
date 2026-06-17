package com.rssfeeder.server

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.rssfeeder.MainActivity
import com.rssfeeder.debug.DebugLogger
import java.net.Inet4Address
import java.net.NetworkInterface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ServerService : Service() {

    private var httpServer: FeedServer? = null
    private var httpsServer: FeedServer? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        instance = this
        startForeground(NOTIFICATION_ID, buildNotification(false, "127.0.0.1"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startServer()
            ACTION_STOP -> stopServer()
            ACTION_INSTALL_CERT -> handleInstallCert()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startServer() {
        if (httpServer != null) return
        try {
            if (!CertificateManager.isCertGenerated(this)) {
                CertificateManager.generate(this)
            }

            val httpPort = DEFAULT_PORT
            val http = FeedServer(httpPort, this)
            http.start()
            httpServer = http

            val httpsPort = DEFAULT_HTTPS_PORT
            val sslFactory = CertificateManager.getSslServerSocketFactory(this)
            if (sslFactory != null) {
                val https = FeedServer(httpsPort, this, sslFactory)
                https.start()
                httpsServer = https
            }

            updateState()
            registerNetworkCallback()

            DebugLogger.log("ServerService", "Servers started on HTTP:$httpPort HTTPS:$httpsPort")
        } catch (e: Exception) {
            DebugLogger.log("ServerService", "Failed to start server: ${e.message}")
            stopSelf()
        }
    }

    private fun handleInstallCert() {
        val intent = CertificateManager.getInstallIntent(this)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        }
    }

    private fun stopServer() {
        httpServer?.stop()
        httpServer = null
        httpsServer?.stop()
        httpsServer = null
        unregisterNetworkCallback()
        _serverState.value = ServerState(false, "127.0.0.1", DEFAULT_PORT, false)
        stopForeground(STOP_FOREGROUND_DETACH)
        stopSelf()
    }

    private fun updateState() {
        val ip = getLocalIpAddress()
        val port = DEFAULT_PORT
        httpServer?.setBaseUrl("http://$ip:$port")
        httpsServer?.setBaseUrl("https://$ip:$port")
        _serverState.value = ServerState(true, ip, port, true)
        val notification = buildNotification(true, ip)
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(isRunning: Boolean, ip: String): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (isRunning) "RSS-Feeder running on $ip:$DEFAULT_PORT" else "RSS-Feeder"
        val text = if (isRunning) "Tap to manage feeds" else "Server not running"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "RSS-Feeder Server",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows when the RSS proxy server is running"
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (networkInterface.isLoopback || !networkInterface.isUp) continue
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (address is Inet4Address && !address.isLoopbackAddress) {
                        return address.hostAddress ?: "127.0.0.1"
                    }
                }
            }
        } catch (e: Exception) {
            DebugLogger.verbose("ServerService", "Failed to get IP: ${e.message}")
        }
        return "127.0.0.1"
    }

    private fun registerNetworkCallback() {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .build()
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                scope.launch { updateState() }
            }
            override fun onLost(network: Network) {
                scope.launch { updateState() }
            }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                scope.launch { updateState() }
            }
        }
        networkCallback?.let { connectivityManager.registerNetworkCallback(request, it) }
    }

    private fun unregisterNetworkCallback() {
        networkCallback?.let {
            val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            connectivityManager.unregisterNetworkCallback(it)
        }
        networkCallback = null
    }

    override fun onDestroy() {
        httpServer?.stop()
        httpServer = null
        httpsServer?.stop()
        httpsServer = null
        instance = null
        unregisterNetworkCallback()
        super.onDestroy()
    }

    data class ServerState(
        val isRunning: Boolean,
        val ipAddress: String,
        val port: Int,
        val hasHttps: Boolean = false
    )

    companion object {
        const val DEFAULT_PORT = 8080
        const val DEFAULT_HTTPS_PORT = 8081
        const val CHANNEL_ID = "rss_feeder_server"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.rssfeeder.action.START_SERVER"
        const val ACTION_STOP = "com.rssfeeder.action.STOP_SERVER"
        const val ACTION_INSTALL_CERT = "com.rssfeeder.action.INSTALL_CERT"

        private val _serverState = MutableStateFlow(ServerState(false, "127.0.0.1", DEFAULT_PORT))
        val serverState: StateFlow<ServerState> = _serverState.asStateFlow()

        private var instance: ServerService? = null

        fun start(context: Context) {
            val intent = Intent(context, ServerService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            instance?.stopServer()
        }

        fun installCert(context: Context) {
            instance?.let { service ->
                val intent = Intent(service, ServerService::class.java).apply {
                    action = ACTION_INSTALL_CERT
                }
                service.startService(intent)
            }
        }
    }
}
