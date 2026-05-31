package com.example.rssfulltext.worker

import android.content.Context
import androidx.work.*
import com.example.rssfulltext.data.db.AppDatabase
import com.example.rssfulltext.data.repository.DirectoryFeedRepository
import com.example.rssfulltext.data.repository.FeedRepository
import com.example.rssfulltext.logging.DebugLogger
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker that refreshes all enabled feeds on schedule.
 */
class FeedRefreshWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "FeedRefreshWorker"
        private const val WORK_NAME_PERIODIC = "feed_refresh_periodic"
        private const val WORK_NAME_SINGLE_RSS = "feed_refresh_single_rss"
        private const val WORK_NAME_SINGLE_DIR = "feed_refresh_single_dir"
        private const val KEY_FEED_SOURCE_ID = "feed_source_id"
        private const val KEY_FEED_TYPE = "feed_type"

        /**
         * Schedule periodic refresh for all feeds.
         */
        fun schedulePeriodicRefresh(context: Context, intervalMinutes: Long = 60) {
            DebugLogger.log(TAG, "Scheduling periodic refresh every $intervalMinutes minutes")

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<FeedRefreshWorker>(
                intervalMinutes, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME_PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        /**
         * Cancel periodic refresh.
         */
        fun cancelPeriodicRefresh(context: Context) {
            DebugLogger.log(TAG, "Cancelling periodic refresh")
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME_PERIODIC)
        }

        /**
         * Trigger an immediate one-time refresh of a single RSS feed.
         */
        fun refreshSingleRssFeed(context: Context, feedSourceId: Long) {
            DebugLogger.log(TAG, "Scheduling single RSS refresh for source $feedSourceId")

            val data = workDataOf(
                KEY_FEED_SOURCE_ID to feedSourceId,
                KEY_FEED_TYPE to "rss"
            )

            val request = OneTimeWorkRequestBuilder<FeedRefreshWorker>()
                .setInputData(data)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "${WORK_NAME_SINGLE_RSS}_$feedSourceId",
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        /**
         * Trigger an immediate one-time scan of a single directory feed.
         */
        fun scanSingleDirectory(context: Context, directorySourceId: Long) {
            DebugLogger.log(TAG, "Scheduling single directory scan for source $directorySourceId")

            val data = workDataOf(
                KEY_FEED_SOURCE_ID to directorySourceId,
                KEY_FEED_TYPE to "directory"
            )

            val request = OneTimeWorkRequestBuilder<FeedRefreshWorker>()
                .setInputData(data)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "${WORK_NAME_SINGLE_DIR}_$directorySourceId",
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }

    override suspend fun doWork(): Result {
        DebugLogger.log(TAG, "FeedRefreshWorker started")

        val database = AppDatabase.getInstance(applicationContext)
        val feedRepository = FeedRepository(database)
        val directoryRepository = DirectoryFeedRepository(database)

        val specificId = inputData.getLong(KEY_FEED_SOURCE_ID, -1)
        val feedType = inputData.getString(KEY_FEED_TYPE)

        return try {
            if (specificId > 0 && feedType != null) {
                // Single feed refresh
                when (feedType) {
                    "rss" -> {
                        val result = feedRepository.refreshFeed(specificId)
                        DebugLogger.log(TAG, "Single RSS refresh complete: $result new items")
                    }
                    "directory" -> {
                        val result = directoryRepository.scanDirectory(specificId)
                        DebugLogger.log(TAG, "Single directory scan complete: $result items processed")
                    }
                }
            } else {
                // Refresh all enabled feeds
                refreshAllFeeds(feedRepository, directoryRepository)
            }

            DebugLogger.log(TAG, "FeedRefreshWorker completed successfully")
            Result.success()
        } catch (e: Exception) {
            DebugLogger.log(TAG, "FeedRefreshWorker failed: ${e.message}")
            Result.retry()
        }
    }

    private suspend fun refreshAllFeeds(
        feedRepository: FeedRepository,
        directoryRepository: DirectoryFeedRepository
    ) {
        val database = AppDatabase.getInstance(applicationContext)

        // Refresh RSS feeds
        val rssFeeds = database.rssFeedDao().getEnabledFeedSources()
        DebugLogger.log(TAG, "Refreshing ${rssFeeds.size} RSS feeds")
        for (feed in rssFeeds) {
            try {
                val now = System.currentTimeMillis()
                val intervalMs = feed.refreshIntervalMinutes * 60 * 1000L
                if (now - feed.lastRefreshed >= intervalMs) {
                    feedRepository.refreshFeed(feed.id)
                } else {
                    DebugLogger.verbose(TAG, "Skipping '${feed.name}' - not due yet")
                }
            } catch (e: Exception) {
                DebugLogger.log(TAG, "Error refreshing feed '${feed.name}': ${e.message}")
            }
        }

        // Scan directory feeds
        val dirFeeds = database.directoryFeedDao().getEnabledDirectorySources()
        DebugLogger.log(TAG, "Scanning ${dirFeeds.size} directory feeds")
        for (feed in dirFeeds) {
            try {
                directoryRepository.scanDirectory(feed.id)
            } catch (e: Exception) {
                DebugLogger.log(TAG, "Error scanning directory '${feed.name}': ${e.message}")
            }
        }
    }
}
