package com.rssfeeder.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.rssfeeder.data.db.AppDatabase
import com.rssfeeder.data.model.FeedType
import com.rssfeeder.data.repository.ArticleRepository
import com.rssfeeder.data.repository.FeedRepository
import com.rssfeeder.debug.DebugLogger
import com.rssfeeder.feed.WebPageScanner

class WebPagePollWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        DebugLogger.log("WebPagePollWorker", "Starting web page poll cycle")

        val db = AppDatabase.getInstance(applicationContext)
        val feedRepository = FeedRepository(db.feedDao(), db.articleDao())
        val articleRepository = ArticleRepository(db.articleDao())
        val scanner = WebPageScanner()

        return try {
            val webPageFeeds = feedRepository.getFeedsByType(FeedType.WEB_PAGE)
            var polledCount = 0
            var newArticleCount = 0

            for (feed in webPageFeeds) {
                val now = System.currentTimeMillis()
                val elapsed = now - feed.lastPolledAt
                if (elapsed < feed.pollingIntervalMinutes * 60_000L) continue

                DebugLogger.log("WebPagePollWorker", "Polling '${feed.title}' at ${feed.url}")
                try {
                    val links = scanner.scanPage(feed.url)
                    var newForFeed = 0

                    for (link in links) {
                        val existing = articleRepository.getArticleByLink(link.url)
                        if (existing == null) {
                            val article = scanner.extractArticle(link.url, feed.id, link.title)
                            if (article != null) {
                                articleRepository.insertArticle(article)
                                newForFeed++
                                newArticleCount++
                            }
                        }
                    }

                    feedRepository.updateLastPolledAt(feed.id, now)
                    feedRepository.updateRefreshTime(feed.id, now)
                    feedRepository.updateError(feed.id, null)
                    polledCount++
                    DebugLogger.log("WebPagePollWorker", "Polled '${feed.title}': $newForFeed new articles")
                } catch (e: Exception) {
                    feedRepository.updateError(feed.id, e.message ?: "Poll failed")
                    DebugLogger.log("WebPagePollWorker", "Poll failed for '${feed.title}': ${e.message}")
                }
            }

            DebugLogger.log("WebPagePollWorker", "Poll cycle complete: $polledCount feeds polled, $newArticleCount new articles")
            Result.success()
        } catch (e: Exception) {
            DebugLogger.log("WebPagePollWorker", "Poll cycle failed: ${e.message ?: "Unknown error"}")
            Result.retry()
        }
    }
}
