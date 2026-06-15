package com.rssfeeder.ui.article

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rssfeeder.data.db.AppDatabase
import com.rssfeeder.data.model.Article
import com.rssfeeder.data.model.Feed
import com.rssfeeder.data.repository.ArticleRepository
import com.rssfeeder.data.repository.FeedRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ArticleListUiState(
    val feed: Feed? = null,
    val articles: List<Article> = emptyList(),
    val isLoading: Boolean = false
)

class ArticleViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val feedRepository = FeedRepository(db.feedDao(), db.articleDao())
    private val articleRepository = ArticleRepository(db.articleDao())

    private val _uiState = MutableStateFlow(ArticleListUiState())
    val uiState: StateFlow<ArticleListUiState> = _uiState.asStateFlow()

    private var currentFeedId: Long = -1

    fun loadFeed(feedId: Long) {
        if (feedId == currentFeedId) return
        currentFeedId = feedId
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val feed = feedRepository.getFeedById(feedId)
            _uiState.value = _uiState.value.copy(feed = feed)
            articleRepository.getArticlesForFeed(feedId).collect { articles ->
                _uiState.value = _uiState.value.copy(
                    articles = articles,
                    isLoading = false
                )
            }
        }
    }

    fun markAsRead(articleId: Long) {
        viewModelScope.launch {
            articleRepository.markAsRead(articleId)
        }
    }
}
