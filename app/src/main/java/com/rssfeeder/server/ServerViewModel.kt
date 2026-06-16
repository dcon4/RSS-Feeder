package com.rssfeeder.server

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rssfeeder.data.db.AppDatabase
import com.rssfeeder.data.model.Feed
import com.rssfeeder.data.repository.FeedRepository
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
    val diagRunning: Boolean = false
)

data class FeedWithUrl(
    val feed: Feed,
    val localUrl: String,
    val networkUrl: String
)

class ServerViewModel(application: Application) : AndroidViewModel(application) {

    private val feedRepository = FeedRepository(
        AppDatabase.getInstance(application).feedDao(),
        AppDatabase.getInstance(application).articleDao()
    )

    private val _uiState = MutableStateFlow(ServerUiState())
    val uiState: StateFlow<ServerUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            ServerService.serverState.collect { serviceState ->
                val feeds = feedRepository.getFeedList()
                val port = serviceState.port
                val networkBase = if (serviceState.isRunning) {
                    "http://${serviceState.ipAddress}:$port"
                } else ""
                val localBase = if (serviceState.isRunning) {
                    "http://127.0.0.1:$port"
                } else ""

                _uiState.value = ServerUiState(
                    isRunning = serviceState.isRunning,
                    ipAddress = serviceState.ipAddress,
                    port = port,
                    feeds = feeds.map { feed ->
                        val suffix = "/feed/${feed.id}/rss.xml"
                        FeedWithUrl(
                            feed = feed,
                            localUrl = "$localBase$suffix",
                            networkUrl = "$networkBase$suffix"
                        )
                    }
                )
            }
        }
    }

    fun startServer() {
        ServerService.start(getApplication())
    }

    fun stopServer() {
        ServerService.stop(getApplication())
    }

    fun refreshFeeds() {
        viewModelScope.launch {
            val feeds = feedRepository.getFeedList()
            val state = _uiState.value
            val port = state.port
            val networkBase = if (state.isRunning) "http://${state.ipAddress}:$port" else ""
            val localBase = if (state.isRunning) "http://127.0.0.1:$port" else ""
            _uiState.value = state.copy(
                feeds = feeds.map { feed ->
                    val suffix = "/feed/${feed.id}/rss.xml"
                    FeedWithUrl(
                        feed = feed,
                        localUrl = "$localBase$suffix",
                        networkUrl = "$networkBase$suffix"
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
                    "Server: HTTP $code\n$body"
                } catch (e: Exception) {
                    "Server ERROR: ${e.message}"
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
                diagResult = "$result\n\n$feedsResult",
                diagRunning = false
            )
        }
    }
}
