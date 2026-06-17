package com.rssfeeder.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.rssfeeder.data.db.AppDatabase
import kotlinx.coroutines.flow.first
import com.rssfeeder.data.model.FeedType
import com.rssfeeder.data.repository.ArticleRepository
import com.rssfeeder.data.repository.FeedRepository
import com.rssfeeder.debug.DebugLogger
import com.rssfeeder.feed.FullTextExtractor
import com.rssfeeder.feed.RssFetcher

class SyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        DebugLogger.log("SyncWorker", "Starting background sync")

        val db = AppDatabase.getInstance(applicationContext)
        val feedRepository = FeedRepository(db.feedDao(), db.articleDao())
        val articleRepository = ArticleRepository(db.articleDao())
        val rssFetcher = RssFetcher()
        val fullTextExtractor = FullTextExtractor()

        return try {
            val feedList = feedRepository.getAllFeeds().first()
            var successCount = 0
            var failCount = 0

            for (feed in feedList) {
                try {
                    if (feed.type != FeedType.REMOTE) continue

                    val articles = rssFetcher.fetchFeed(feed.url).articles
                    var newCount = 0

                    for (article in articles) {
                        val existing = articleRepository.getArticleByLink(article.link)
                        if (existing == null) {
                            val fullContent = try {
                                fullTextExtractor.extractFullText(article.link)
                            } catch (e: Exception) {
                                null
                            }
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
                    successCount++
                    DebugLogger.log("SyncWorker", "Synced '${feed.title}': $newCount new articles")
                } catch (e: Exception) {
                    feedRepository.updateError(feed.id, e.message ?: "Unknown error")
                    failCount++
                    DebugLogger.log("SyncWorker", "Sync failed for '${feed.title}': ${e.message ?: "Unknown error"}")
                }
            }

            DebugLogger.log("SyncWorker", "Sync complete: $successCount succeeded, $failCount failed")
            Result.success()
        } catch (e: Exception) {
            DebugLogger.log("SyncWorker", "Sync failed: ${e.message ?: "Unknown error"}")
            Result.retry()
        }
    }
}
