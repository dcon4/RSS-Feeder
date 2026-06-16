package com.rssfeeder.ui.feedlist

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rssfeeder.data.db.AppDatabase
import com.rssfeeder.data.model.Feed
import com.rssfeeder.data.model.FeedType
import com.rssfeeder.data.repository.ArticleRepository
import com.rssfeeder.data.repository.FeedRepository
import com.rssfeeder.debug.DebugLogger
import com.rssfeeder.feed.FullTextExtractor
import com.rssfeeder.feed.LocalFeedScanner
import com.rssfeeder.feed.RssFetcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class FeedListUiState(
    val feeds: List<FeedWithCount> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

data class FeedWithCount(
    val feed: Feed,
    val unreadCount: Int = 0
)

class FeedListViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val feedRepository = FeedRepository(db.feedDao(), db.articleDao())
    private val articleRepository = ArticleRepository(db.articleDao())
    private val rssFetcher = RssFetcher()
    private val fullTextExtractor = FullTextExtractor()
    private val localFeedScanner = LocalFeedScanner()

    private val _uiState = MutableStateFlow(FeedListUiState())
    val uiState: StateFlow<FeedListUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            feedRepository.getAllFeeds().collect { feeds ->
                val feedsWithCounts = feeds.map { feed ->
                    val count = feedRepository.getUnreadCount(feed.id)
                    FeedWithCount(feed = feed, unreadCount = 0)
                }
                _uiState.value = _uiState.value.copy(feeds = feedsWithCounts)
            }
        }
        viewModelScope.launch {
            feedRepository.getAllFeeds().collect { feeds ->
                for (feed in feeds) {
                    feedRepository.getUnreadCount(feed.id).collect { count ->
                        val updatedFeeds = _uiState.value.feeds.map {
                            if (it.feed.id == feed.id) it.copy(unreadCount = count)
                            else it
                        }
                        _uiState.value = _uiState.value.copy(feeds = updatedFeeds)
                    }
                }
            }
        }
    }

    fun addFeedByUrl(url: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val articles = withContext(Dispatchers.IO) {
                    rssFetcher.fetchFeed(url)
                }
                if (articles.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "No articles found in feed"
                    )
                    return@launch
                }

                val title = articles.firstOrNull()?.let { extractTitle(it.title, url) } ?: "Feed"
                val feedId = feedRepository.addFeed(title, url, FeedType.REMOTE)

                val fullArticles = withContext(Dispatchers.IO) {
                    articles.map { article ->
                        val fullContent = article.link.let { link ->
                            fullTextExtractor.extractFullText(link)
                        }
                        article.copy(
                            feedId = feedId,
                            content = fullContent ?: article.summary
                        )
                    }
                }

                articleRepository.insertArticles(fullArticles)
                feedRepository.updateRefreshTime(feedId, System.currentTimeMillis())
                _uiState.value = _uiState.value.copy(isLoading = false)
            } catch (e: Exception) {
                DebugLogger.log("FeedListVM", "Failed to add feed: ${e.message ?: "Unknown error"}")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to add feed: ${e.message ?: "Unknown error"}"
                )
            }
        }
    }

    fun addLocalFolderFeed(title: String, folderUri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val feedId = feedRepository.addFeed(
                    title = title,
                    url = folderUri.toString(),
                    type = FeedType.LOCAL_FOLDER
                )

                val articles = withContext(Dispatchers.IO) {
                    localFeedScanner.scanFolder(
                        context = getApplication(),
                        folderUri = folderUri,
                        feedId = feedId,
                        feedUrl = folderUri.toString()
                    )
                }

                articleRepository.insertArticles(articles)
                feedRepository.updateRefreshTime(feedId, System.currentTimeMillis())
                _uiState.value = _uiState.value.copy(isLoading = false)
            } catch (e: Exception) {
                DebugLogger.log("FeedListVM", "Failed to add local feed: ${e.message}")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to add local feed: ${e.message}"
                )
            }
        }
    }

    fun deleteFeed(feedId: Long) {
        viewModelScope.launch {
            feedRepository.deleteFeed(feedId)
        }
    }

    fun markAllAsRead(feedId: Long) {
        viewModelScope.launch {
            feedRepository.markAllAsRead(feedId)
        }
    }

    fun refreshFeed(feedId: Long) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val feed = feedRepository.getFeedById(feedId) ?: return@launch
                when (feed.type) {
                    FeedType.REMOTE -> refreshRemoteFeed(feed)
                    FeedType.LOCAL_FOLDER -> refreshLocalFeed(feed)
                }
            } catch (e: Exception) {
                feedRepository.updateError(feedId, e.message)
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    private suspend fun refreshRemoteFeed(feed: Feed) {
        val articles = withContext(Dispatchers.IO) {
            rssFetcher.fetchFeed(feed.url)
        }
        val newArticles = articles.mapNotNull { article ->
            val existing = articleRepository.getArticleByLink(article.link)
            if (existing != null) null
            else {
                val fullContent = fullTextExtractor.extractFullText(article.link)
                article.copy(feedId = feed.id, content = fullContent ?: article.summary)
            }
        }
        if (newArticles.isNotEmpty()) {
            articleRepository.insertArticles(newArticles)
        }
        feedRepository.updateRefreshTime(feed.id, System.currentTimeMillis())
        feedRepository.updateError(feed.id, null)
    }

    private suspend fun refreshLocalFeed(feed: Feed) {
        val folderUri = Uri.parse(feed.url)
        articleRepository.deleteArticlesForFeed(feed.id)
        val articles = withContext(Dispatchers.IO) {
            localFeedScanner.scanFolder(
                context = getApplication(),
                folderUri = folderUri,
                feedId = feed.id,
                feedUrl = feed.url
            )
        }
        articleRepository.insertArticles(articles)
        feedRepository.updateRefreshTime(feed.id, System.currentTimeMillis())
        feedRepository.updateError(feed.id, null)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    private fun extractTitle(firstArticleTitle: String?, url: String): String {
        if (firstArticleTitle.isNullOrBlank()) {
            return try {
                val uri = Uri.parse(url)
                uri.host?.removePrefix("www.") ?: url
            } catch (e: Exception) {
                url
            }
        }
        return firstArticleTitle.trim().take(80)
    }
}
