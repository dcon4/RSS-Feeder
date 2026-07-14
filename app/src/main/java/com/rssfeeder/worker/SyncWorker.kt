package com.rssfeeder.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.rssfeeder.data.db.AppDatabase
import com.rssfeeder.data.model.Feed
import com.rssfeeder.data.model.FeedType
import com.rssfeeder.data.repository.ArticleRepository
import com.rssfeeder.data.repository.FeedRepository
import com.rssfeeder.debug.DebugLogger
import com.rssfeeder.feed.ArticleExporter
import com.rssfeeder.feed.FullTextExtractor
import com.rssfeeder.feed.RssFetcher
import com.rssfeeder.feed.WebPageScanner
import kotlinx.coroutines.flow.first

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
                    when (feed.type) {
                        FeedType.REMOTE -> {
                            val articles = rssFetcher.fetchFeed(feed.url).articles
                            var newCount = 0
                            for (article in articles) {
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
                            successCount++
                            DebugLogger.log("SyncWorker", "Synced '${feed.title}': $newCount new articles")
                        }
                        FeedType.WEB_PAGE -> {
                            val now = System.currentTimeMillis()
                            val elapsed = now - feed.lastPolledAt
                            if (elapsed < feed.pollingIntervalMinutes * 60_000L) continue

                            val scanner = WebPageScanner()
                            val links = scanner.scanPage(feed.url)
                            var newCount = 0
                            for (link in links) {
                                val existing = articleRepository.getArticleByLink(link.url)
                                if (existing == null) {
                                    val article = scanner.extractArticle(link.url, feed.id, link.title)
                                    if (article != null) {
                                        articleRepository.insertArticle(article)
                                        newCount++
                                    }
                                }
                            }
                            feedRepository.updateLastPolledAt(feed.id, now)
                            feedRepository.updateRefreshTime(feed.id, now)
                            feedRepository.updateError(feed.id, null)
                            successCount++
                            DebugLogger.log("SyncWorker", "Polled web page '${feed.title}': $newCount new articles")
                        }
                        FeedType.LOCAL_FOLDER -> continue
                    }

                    if (feed.autoDownload) {
                        exportFeedArticles(applicationContext, feed, feedRepository, articleRepository)
                    }
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

    private suspend fun exportFeedArticles(
        context: Context,
        feed: Feed,
        feedRepository: FeedRepository,
        articleRepository: ArticleRepository
    ) {
        try {
            val folderUri = feed.downloadFolder?.takeIf { it.isNotBlank() }
                ?: return
            val articles = articleRepository.getArticlesForFeedList(feed.id)
            if (articles.isEmpty()) return

            val uri = android.net.Uri.parse(folderUri)
            val count = ArticleExporter.exportNewArticles(context, articles, uri, feed.lastExportedTime)
            if (count > 0) {
                feedRepository.updateLastExportedTime(feed.id, System.currentTimeMillis())
                DebugLogger.log("SyncWorker", "Exported $count articles for '${feed.title}'")
            }
        } catch (e: Exception) {
            DebugLogger.log("SyncWorker", "Export failed for '${feed.title}': ${e.message}")
        }
    }
}
