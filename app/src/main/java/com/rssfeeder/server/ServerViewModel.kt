package com.rssfeeder.server

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rssfeeder.data.db.AppDatabase
import com.rssfeeder.data.model.Feed
import com.rssfeeder.data.repository.FeedRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ServerUiState(
    val isRunning: Boolean = false,
    val ipAddress: String = "127.0.0.1",
    val port: Int = ServerService.DEFAULT_PORT,
    val feeds: List<FeedWithUrl> = emptyList()
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
}
