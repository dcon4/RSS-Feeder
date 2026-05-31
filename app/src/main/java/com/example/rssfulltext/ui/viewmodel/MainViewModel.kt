package com.example.rssfulltext.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.rssfulltext.data.db.AppDatabase
import com.example.rssfulltext.data.model.DirectoryFeedSource
import com.example.rssfulltext.data.model.RssFeedSource
import com.example.rssfulltext.data.repository.DirectoryFeedRepository
import com.example.rssfulltext.data.repository.FeedRepository
import com.example.rssfulltext.logging.DebugLogger
import com.example.rssfulltext.network.server.FeedHttpServer
import com.example.rssfulltext.network.server.FeedServerService
import com.example.rssfulltext.worker.FeedRefreshWorker
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class MainUiState(
    val rssFeeds: List<RssFeedSource> = emptyList(),
    val directoryFeeds: List<DirectoryFeedSource> = emptyList(),
    val serverRunning: Boolean = false,
    val serverPort: Int = FeedHttpServer.DEFAULT_PORT,
    val bindAllInterfaces: Boolean = false,
    val verboseLogging: Boolean = false,
    val isRefreshing: Boolean = false,
    val refreshMessage: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "MainViewModel"
    }

    private val database = AppDatabase.getInstance(application)
    private val feedRepository = FeedRepository(database)
    private val directoryFeedRepository = DirectoryFeedRepository(database)

    private val _uiState = MutableStateFlow(MainUiState(verboseLogging = DebugLogger.verboseEnabled))
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        // Observe RSS feeds
        viewModelScope.launch {
            feedRepository.getAllFeedSources().collect { feeds ->
                _uiState.update { it.copy(rssFeeds = feeds) }
            }
        }

        // Observe directory feeds
        viewModelScope.launch {
            directoryFeedRepository.getAllDirectorySources().collect { feeds ->
                _uiState.update { it.copy(directoryFeeds = feeds) }
            }
        }
    }

    fun addRssFeed(name: String, url: String, slug: String, refreshMinutes: Int = 60) {
        viewModelScope.launch {
            val source = RssFeedSource(
                name = name,
                sourceUrl = url,
                outputSlug = slug.lowercase().replace(Regex("[^a-z0-9-]"), "-"),
                refreshIntervalMinutes = refreshMinutes
            )
            val id = feedRepository.addFeedSource(source)
            DebugLogger.log(TAG, "Added RSS feed: $name (id=$id)")

            // Trigger immediate refresh
            FeedRefreshWorker.refreshSingleRssFeed(getApplication(), id)
            _uiState.update { it.copy(refreshMessage = "Feed '$name' added. Refreshing...") }
        }
    }

    fun deleteRssFeed(source: RssFeedSource) {
        viewModelScope.launch {
            feedRepository.deleteFeedSource(source)
        }
    }

    fun refreshRssFeed(source: RssFeedSource) {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, refreshMessage = "Refreshing '${source.name}'...") }
            val result = feedRepository.refreshFeed(source.id)
            val msg = if (result >= 0) "Refreshed '${source.name}': $result new items" else "Failed to refresh '${source.name}'"
            _uiState.update { it.copy(isRefreshing = false, refreshMessage = msg) }
        }
    }

    fun refreshAllFeeds() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, refreshMessage = "Refreshing all feeds...") }

            val rssFeeds = _uiState.value.rssFeeds
            for (feed in rssFeeds.filter { it.enabled }) {
                feedRepository.refreshFeed(feed.id)
            }

            val dirFeeds = _uiState.value.directoryFeeds
            for (feed in dirFeeds.filter { it.enabled }) {
                directoryFeedRepository.scanDirectory(feed.id)
            }

            _uiState.update { it.copy(isRefreshing = false, refreshMessage = "All feeds refreshed") }
        }
    }

    fun addDirectoryFeed(name: String, path: String, slug: String, includeSubdirs: Boolean = false) {
        viewModelScope.launch {
            val source = DirectoryFeedSource(
                name = name,
                directoryPath = path,
                outputSlug = slug.lowercase().replace(Regex("[^a-z0-9-]"), "-"),
                includeSubdirectories = includeSubdirs
            )
            val id = directoryFeedRepository.addDirectorySource(source)
            DebugLogger.log(TAG, "Added directory feed: $name (id=$id)")

            // Trigger immediate scan
            FeedRefreshWorker.scanSingleDirectory(getApplication(), id)
            _uiState.update { it.copy(refreshMessage = "Directory feed '$name' added. Scanning...") }
        }
    }

    fun deleteDirectoryFeed(source: DirectoryFeedSource) {
        viewModelScope.launch {
            directoryFeedRepository.deleteDirectorySource(source)
        }
    }

    fun scanDirectory(source: DirectoryFeedSource) {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, refreshMessage = "Scanning '${source.name}'...") }
            val result = directoryFeedRepository.scanDirectory(source.id)
            val msg = if (result >= 0) "Scanned '${source.name}': $result items processed" else "Failed to scan '${source.name}'"
            _uiState.update { it.copy(isRefreshing = false, refreshMessage = msg) }
        }
    }

    fun startServer() {
        val state = _uiState.value
        FeedServerService.startService(getApplication(), state.serverPort, state.bindAllInterfaces)
        _uiState.update { it.copy(serverRunning = true) }
        DebugLogger.log(TAG, "Server started via UI")
    }

    fun stopServer() {
        FeedServerService.stopService(getApplication())
        _uiState.update { it.copy(serverRunning = false) }
        DebugLogger.log(TAG, "Server stopped via UI")
    }

    fun setServerPort(port: Int) {
        _uiState.update { it.copy(serverPort = port) }
    }

    fun setBindAllInterfaces(bindAll: Boolean) {
        _uiState.update { it.copy(bindAllInterfaces = bindAll) }
        DebugLogger.log(TAG, "Bind all interfaces: $bindAll")
    }

    fun setVerboseLogging(enabled: Boolean) {
        DebugLogger.setVerboseEnabled(enabled)
        _uiState.update { it.copy(verboseLogging = enabled) }
    }

    fun schedulePeriodicRefresh(intervalMinutes: Long) {
        FeedRefreshWorker.schedulePeriodicRefresh(getApplication(), intervalMinutes)
        _uiState.update { it.copy(refreshMessage = "Scheduled refresh every $intervalMinutes minutes") }
    }

    fun cancelPeriodicRefresh() {
        FeedRefreshWorker.cancelPeriodicRefresh(getApplication())
        _uiState.update { it.copy(refreshMessage = "Periodic refresh cancelled") }
    }

    fun clearRefreshMessage() {
        _uiState.update { it.copy(refreshMessage = null) }
    }
}
