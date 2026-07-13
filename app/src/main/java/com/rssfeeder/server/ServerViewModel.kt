package com.rssfeeder.server

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import com.rssfeeder.data.db.AppDatabase
import com.rssfeeder.data.model.Feed
import com.rssfeeder.data.model.FeedType
import com.rssfeeder.data.repository.ArticleRepository
import com.rssfeeder.data.repository.FeedRepository
import com.rssfeeder.debug.DebugLogger
import com.rssfeeder.feed.ArticleExporter
import com.rssfeeder.feed.FullTextExtractor
import com.rssfeeder.feed.RssFetcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
    private val db = AppDatabase.getInstance(application)
    private val feedRepository = FeedRepository(db.feedDao(), db.articleDao())
    private val articleRepository = ArticleRepository(db.articleDao())

    private val _uiState = MutableStateFlow(ServerUiState())
    val uiState: StateFlow<ServerUiState> = _uiState.asStateFlow()

    private var autoPushJob: kotlinx.coroutines.Job? = null

    init {
        viewModelScope.launch {
            DebugLogger.getGithubPatFlow().collect { pat ->
                _uiState.value = _uiState.value.copy(relayPat = pat)
                restartAutoPush()
            }
        }
        viewModelScope.launch {
            ServerService.serverState.collect { serviceState ->
                restartAutoPush()
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
                            relayUrl = RelayManager.getRelayUrl(feed)
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

    private suspend fun getPushInterval(): Int {
        return DebugLogger.getPushIntervalFlow().first()
    }

    private fun restartAutoPush() {
        autoPushJob?.cancel()
        autoPushJob = null
        val state = _uiState.value
        if (state.isRunning && state.relayPat.isNotEmpty()) {
            viewModelScope.launch {
                val interval = getPushInterval()
                if (interval > 0) {
                    autoPushJob = viewModelScope.launch {
                        while (true) {
                            delay(interval * 60_000L)
                            if (!_uiState.value.isRunning) break
                            pushAllFeeds()
                        }
                    }
                }
            }
        }
    }

    fun deleteFeed(feed: Feed) {
        viewModelScope.launch {
            val pat = _uiState.value.relayPat
            if (pat.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    RelayManager.deleteFeedRelay(pat, feed)
                }
            }
            feedRepository.deleteFeed(feed.id)
            refreshFeeds()
        }
    }

    fun refreshFeed(feedId: Long) {
        viewModelScope.launch {
            val feed = feedRepository.getFeedById(feedId) ?: return@launch
            try {
                if (feed.type == FeedType.REMOTE) {
                    val rssFetcher = RssFetcher()
                    val fullTextExtractor = FullTextExtractor()
                    val result = withContext(Dispatchers.IO) {
                        rssFetcher.fetchFeed(feed.url)
                    }
                    var newCount = 0
                    for (article in result.articles) {
                        val existing = articleRepository.getArticleByLink(article.link)
                        if (existing == null) {
                            val fullContent = try {
                                fullTextExtractor.extractFullText(article.link)
                            } catch (e: Exception) { null }
                            articleRepository.insertArticle(
                                article.copy(
                                    feedId = feed.id,
                                    content = fullContent ?: article.summary
                                )
                            )
                            newCount++
                        }
                    }
                    feedRepository.updateRefreshTime(feed.id, System.currentTimeMillis())
                    feedRepository.updateError(feed.id, null)
                    DebugLogger.log("ServerVM", "Refreshed '${feed.title}': $newCount new articles")
                }
                if (feed.autoDownload) {
                    exportFeed(feed)
                }
            } catch (e: Exception) {
                feedRepository.updateError(feed.id, e.message ?: "Unknown error")
                DebugLogger.log("ServerVM", "Refresh failed for '${feed.title}': ${e.message}")
            }
            refreshFeeds()
        }
    }

    fun toggleAutoDownload(feed: Feed) {
        viewModelScope.launch {
            val newValue = !feed.autoDownload
            feedRepository.updateAutoDownload(feed.id, newValue)
            refreshFeeds()
        }
    }

    fun updateDownloadFolder(feedId: Long, folderUri: String) {
        viewModelScope.launch {
            feedRepository.updateDownloadFolder(feedId, folderUri)
            refreshFeeds()
        }
    }

    private suspend fun exportFeed(feed: Feed) {
        try {
            val folderUri = feed.downloadFolder?.takeIf { it.isNotBlank() } ?: return
            val articles = articleRepository.getArticlesForFeedList(feed.id)
            if (articles.isEmpty()) return
            val uri = Uri.parse(folderUri)
            val count = ArticleExporter.exportNewArticles(app, articles, uri, feed.lastExportedTime)
            if (count > 0) {
                feedRepository.updateLastExportedTime(feed.id, System.currentTimeMillis())
                DebugLogger.log("ServerVM", "Exported $count articles for '${feed.title}'")
            }
        } catch (e: Exception) {
            DebugLogger.log("ServerVM", "Export failed for '${feed.title}': ${e.message}")
        }
    }

    fun pushAllFeeds() {
        viewModelScope.launch {
            val pat = _uiState.value.relayPat
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
            val results = mutableListOf<String>()
            var pushed = 0
            try {
                for (feed in feeds) {
                    try {
                        val articles = articleRepository.getArticlesForFeedList(feed.id)
                        val relayUrl = RelayManager.getRelayUrl(feed)
                        val rssXml = RssXmlBuilder.buildFeedXml(feed, articles, "", relayUrl)

                        val err = withContext(Dispatchers.IO) {
                            RelayManager.pushFeed(pat, feed, rssXml)
                        }
                        if (err != null) {
                            results.add("Feed ${feed.id} (${feed.title}): FAILED - $err")
                        } else {
                            results.add("Feed ${feed.id} (${feed.title}): pushed OK (${articles.size} articles)")
                            pushed++
                        }
                    } catch (e: Exception) {
                        results.add("Feed ${feed.id} (${feed.title}): FAILED - ${e.message}")
                    }
                }
            } catch (e: Exception) {
                results.add("Push stopped early: ${e.message}")
            }
            val msg = results.joinToString("\n")
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
                        relayUrl = RelayManager.getRelayUrl(feed)
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
