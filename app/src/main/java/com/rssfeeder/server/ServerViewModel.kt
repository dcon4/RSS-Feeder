package com.rssfeeder.server

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rssfeeder.data.db.AppDatabase
import com.rssfeeder.data.model.Feed
import com.rssfeeder.data.repository.FeedRepository
import com.rssfeeder.debug.DebugLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ServerUiState(
    val isRunning: Boolean = false,
    val ipAddress: String = "127.0.0.1",
    val port: Int = ServerService.DEFAULT_PORT,
    val feeds: List<FeedWithUrl> = emptyList(),
    val diagResult: String? = null,
    val diagRunning: Boolean = false,
    val certGenerated: Boolean = false,
    val hasHttps: Boolean = false,
    val relayPat: String = "",
    val pushResult: String? = null,
    val pushRunning: Boolean = false
)

data class FeedWithUrl(
    val feed: Feed,
    val localUrl: String,
    val networkUrl: String,
    val localHttpsUrl: String = "",
    val networkHttpsUrl: String = "",
    val relayUrl: String = ""
)

class ServerViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application
    private val feedRepository = FeedRepository(
        AppDatabase.getInstance(application).feedDao(),
        AppDatabase.getInstance(application).articleDao()
    )

    private val _uiState = MutableStateFlow(ServerUiState())
    val uiState: StateFlow<ServerUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            DebugLogger.getGithubPatFlow().collect { pat ->
                _uiState.value = _uiState.value.copy(relayPat = pat)
            }
        }
        viewModelScope.launch {
            ServerService.serverState.collect { serviceState ->
                val feeds = feedRepository.getFeedList()
                val port = serviceState.port
                val httpsPort = ServerService.DEFAULT_HTTPS_PORT
                val networkBase = if (serviceState.isRunning) {
                    "http://${serviceState.ipAddress}:$port"
                } else ""
                val localBase = if (serviceState.isRunning) {
                    "http://127.0.0.1:$port"
                } else ""
                val networkHttpsBase = if (serviceState.isRunning && serviceState.hasHttps) {
                    "https://${serviceState.ipAddress}:$httpsPort"
                } else ""
                val localHttpsBase = if (serviceState.isRunning && serviceState.hasHttps) {
                    "https://127.0.0.1:$httpsPort"
                } else ""

                _uiState.value = ServerUiState(
                    isRunning = serviceState.isRunning,
                    ipAddress = serviceState.ipAddress,
                    port = port,
                    certGenerated = CertificateManager.isCertGenerated(app),
                    hasHttps = serviceState.hasHttps,
                    relayPat = _uiState.value.relayPat,
                    feeds = feeds.map { feed ->
                        val suffix = "/feed/${feed.id}/rss.xml"
                        FeedWithUrl(
                            feed = feed,
                            localUrl = "$localBase$suffix",
                            networkUrl = "$networkBase$suffix",
                            localHttpsUrl = "$localHttpsBase$suffix",
                            networkHttpsUrl = "$networkHttpsBase$suffix",
                            relayUrl = RelayManager.getRelayUrl(feed.id)
                        )
                    }
                )
            }
        }
    }

    fun startServer() {
        if (!CertificateManager.isCertGenerated(app)) {
            try {
                CertificateManager.generate(app)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(diagResult = "Cert generation failed: ${e.message}")
                return
            }
        }
        ServerService.start(app)
    }

    fun stopServer() {
        ServerService.stop(app)
    }

    fun installCert() {
        ServerService.installCert(app)
    }

    fun pushAllFeeds() {
        viewModelScope.launch {
            val pat = getPat()
            if (pat.isEmpty()) {
                _uiState.value = _uiState.value.copy(
                    pushResult = "No GitHub PAT set. Go to Settings to add one."
                )
                return@launch
            }
            _uiState.value = _uiState.value.copy(pushResult = null, pushRunning = true)
            val feeds = feedRepository.getFeedList()
            if (feeds.isEmpty()) {
                _uiState.value = _uiState.value.copy(
                    pushResult = "No feeds to push",
                    pushRunning = false
                )
                return@launch
            }
            val port = _uiState.value.port
            val errors = mutableListOf<String>()
            var pushed = 0
            for (feed in feeds) {
                val result = withContext(Dispatchers.IO) {
                    try {
                        val rssXml = withContext(Dispatchers.IO) {
                            val url = java.net.URL("http://127.0.0.1:$port/feed/${feed.id}/rss.xml")
                            val conn = url.openConnection() as java.net.HttpURLConnection
                            conn.connectTimeout = 10000
                            conn.readTimeout = 10000
                            val code = conn.responseCode
                            if (code in 200..299) {
                                conn.inputStream.bufferedReader().use { it.readText() }
                            } else {
                                null
                            }
                        }
                        if (rssXml != null) {
                            val err = RelayManager.pushFeed(pat, feed.id, rssXml)
                            if (err != null) errors.add("Feed ${feed.id}: $err")
                            else pushed++
                        } else {
                            errors.add("Feed ${feed.id}: could not fetch RSS from local server")
                        }
                    } catch (e: Exception) {
                        errors.add("Feed ${feed.id}: ${e.message}")
                    }
                }
            }
            val msg = buildString {
                append("Pushed $pushed of ${feeds.size} feeds.")
                if (errors.isNotEmpty()) {
                    append(" Errors: ${errors.joinToString("; ")}")
                }
            }
            _uiState.value = _uiState.value.copy(
                pushResult = msg,
                pushRunning = false
            )
        }
    }

    fun refreshFeeds() {
        viewModelScope.launch {
            val feeds = feedRepository.getFeedList()
            val state = _uiState.value
            val port = state.port
            val httpsPort = ServerService.DEFAULT_HTTPS_PORT
            val networkBase = if (state.isRunning) "http://${state.ipAddress}:$port" else ""
            val localBase = if (state.isRunning) "http://127.0.0.1:$port" else ""
            val networkHttpsBase = if (state.isRunning && state.hasHttps) "https://${state.ipAddress}:$httpsPort" else ""
            val localHttpsBase = if (state.isRunning && state.hasHttps) "https://127.0.0.1:$httpsPort" else ""
            _uiState.value = state.copy(
                feeds = feeds.map { feed ->
                    val suffix = "/feed/${feed.id}/rss.xml"
                    FeedWithUrl(
                        feed = feed,
                        localUrl = "$localBase$suffix",
                        networkUrl = "$networkBase$suffix",
                        localHttpsUrl = "$localHttpsBase$suffix",
                        networkHttpsUrl = "$networkHttpsBase$suffix",
                        relayUrl = RelayManager.getRelayUrl(feed.id)
                    )
                }
            )
        }
    }

    fun runDiagnostics() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(diagResult = null, diagRunning = true)
            val result = withContext(Dispatchers.IO) {
                try {
                    val url = java.net.URL("http://127.0.0.1:${_uiState.value.port}/health")
                    val conn = url.openConnection() as java.net.HttpURLConnection
                    conn.connectTimeout = 5000
                    conn.readTimeout = 5000
                    val code = conn.responseCode
                    val body = if (code in 200..299) {
                        conn.inputStream.bufferedReader().use { it.readText() }
                    } else {
                        conn.errorStream?.bufferedReader()?.readText() ?: "No body"
                    }
                    conn.disconnect()
                    "HTTP health: $code $body"
                } catch (e: Exception) {
                    "HTTP health ERROR: ${e.message}"
                }
            }
            val httpsResult = withContext(Dispatchers.IO) {
                try {
                    val url = java.net.URL("https://127.0.0.1:${ServerService.DEFAULT_HTTPS_PORT}/health")
                    val conn = url.openConnection() as javax.net.ssl.HttpsURLConnection
                    conn.connectTimeout = 5000
                    conn.readTimeout = 5000
                    val code = conn.responseCode
                    val body = if (code in 200..299) {
                        conn.inputStream.bufferedReader().use { it.readText() }
                    } else {
                        conn.errorStream?.bufferedReader()?.readText() ?: "No body"
                    }
                    conn.disconnect()
                    "HTTPS health: $code $body"
                } catch (e: Exception) {
                    "HTTPS health ERROR: ${e.message}"
                }
            }
            val feedsResult = withContext(Dispatchers.IO) {
                try {
                    val state = _uiState.value
                    val sb = StringBuilder()
                    for (feed in state.feeds) {
                        val url = java.net.URL(feed.localUrl)
                        val conn = url.openConnection() as java.net.HttpURLConnection
                        conn.connectTimeout = 5000
                        conn.readTimeout = 5000
                        val code = conn.responseCode
                        val xml = if (code in 200..299) {
                            conn.inputStream.bufferedReader().use { it.readText() }
                        } else ""
                        val lineCount = xml.count { it == '\n' }
                        sb.appendLine("Feed ${feed.feed.id} ($feed.feed.title): HTTP $code, $lineCount lines")
                        conn.disconnect()
                    }
                    sb.toString()
                } catch (e: Exception) {
                    "Feeds ERROR: ${e.message}"
                }
            }
            _uiState.value = _uiState.value.copy(
                diagResult = "$result\n$httpsResult\n\n$feedsResult",
                diagRunning = false
            )
        }
    }
}
