package com.rssfeeder.data.repository

import com.rssfeeder.data.db.ArticleDao
import com.rssfeeder.data.db.FeedDao
import com.rssfeeder.data.db.FeedEntity
import com.rssfeeder.data.model.Feed
import com.rssfeeder.data.model.FeedType
import com.rssfeeder.debug.DebugLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class FeedRepository(
    private val feedDao: FeedDao,
    private val articleDao: ArticleDao
) {
    fun getAllFeeds(): Flow<List<Feed>> {
        return feedDao.getAllFeeds().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun getFeedList(): List<Feed> {
        return feedDao.getFeedList().map { it.toDomain() }
    }

    suspend fun getFeedById(id: Long): Feed? {
        return feedDao.getFeedById(id)?.toDomain()
    }

    suspend fun addFeed(title: String, url: String, type: FeedType): Long {
        val entity = FeedEntity(
            title = title,
            url = url,
            type = type.name
        )
        val id = feedDao.insertFeed(entity)
        DebugLogger.log("FeedRepository", "Added feed: $title ($type)")
        return id
    }

    suspend fun deleteFeed(id: Long) {
        articleDao.deleteArticlesForFeed(id)
        feedDao.deleteFeedById(id)
        DebugLogger.log("FeedRepository", "Deleted feed id=$id")
    }

    suspend fun updateRefreshTime(id: Long, time: Long) {
        feedDao.updateRefreshTime(id, time)
    }

    suspend fun updateError(id: Long, error: String?) {
        feedDao.updateError(id, error)
    }

    suspend fun markAllAsRead(feedId: Long) {
        articleDao.markAllAsRead(feedId)
    }

    fun getUnreadCount(feedId: Long): Flow<Int> {
        return articleDao.getUnreadCount(feedId)
    }

    private fun FeedEntity.toDomain(): Feed {
        return Feed(
            id = id,
            title = title,
            url = url,
            type = try { FeedType.valueOf(type) } catch (e: Exception) { FeedType.REMOTE },
            lastRefreshTime = lastRefreshTime,
            errorMessage = errorMessage
        )
    }
}
