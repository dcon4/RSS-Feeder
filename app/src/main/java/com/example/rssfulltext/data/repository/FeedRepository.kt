package com.example.rssfulltext.data.repository

import com.example.rssfulltext.data.db.AppDatabase
import com.example.rssfulltext.data.model.FeedItem
import com.example.rssfulltext.data.model.RssFeedSource
import com.example.rssfulltext.extraction.FullTextExtractor
import com.example.rssfulltext.logging.DebugLogger
import com.example.rssfulltext.network.rss.RssFetcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Repository coordinating RSS feed fetching, full-text extraction, and persistence.
 */
class FeedRepository(private val database: AppDatabase) {

    companion object {
        private const val TAG = "FeedRepository"
    }

    private val rssFetcher = RssFetcher()
    private val extractor = FullTextExtractor()
    private val rssFeedDao = database.rssFeedDao()

    // Date formats commonly used in RSS feeds
    private val dateFormats = listOf(
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US),
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US),
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US),
        SimpleDateFormat("yyyy-MM-dd", Locale.US)
    )

    fun getAllFeedSources(): Flow<List<RssFeedSource>> = rssFeedDao.getAllFeedSources()

    suspend fun getFeedSourceById(id: Long): RssFeedSource? = rssFeedDao.getFeedSourceById(id)

    suspend fun getFeedSourceBySlug(slug: String): RssFeedSource? = rssFeedDao.getFeedSourceBySlug(slug)

    suspend fun addFeedSource(source: RssFeedSource): Long {
        DebugLogger.log(TAG, "Adding feed source: ${source.name} (${source.sourceUrl})")
        return rssFeedDao.insertFeedSource(source)
    }

    suspend fun updateFeedSource(source: RssFeedSource) {
        rssFeedDao.updateFeedSource(source)
    }

    suspend fun deleteFeedSource(source: RssFeedSource) {
        DebugLogger.log(TAG, "Deleting feed source: ${source.name}")
        rssFeedDao.deleteFeedSource(source)
    }

    suspend fun getItemsForSource(sourceId: Long): List<FeedItem> {
        return rssFeedDao.getItemsForSource(sourceId)
    }

    fun getItemsForSourceFlow(sourceId: Long): Flow<List<FeedItem>> {
        return rssFeedDao.getItemsForSourceFlow(sourceId)
    }

    /**
     * Refreshes a feed source: fetches the RSS feed, extracts full text for new items,
     * and persists results to the database.
     *
     * @return number of new items extracted, or -1 on failure
     */
    suspend fun refreshFeed(sourceId: Long): Int = withContext(Dispatchers.IO) {
        val source = rssFeedDao.getFeedSourceById(sourceId) ?: run {
            DebugLogger.log(TAG, "Feed source $sourceId not found")
            return@withContext -1
        }

        DebugLogger.log(TAG, "Refreshing feed: ${source.name}")

        val parsedFeed = rssFetcher.fetchFeed(source.sourceUrl)
        if (parsedFeed == null) {
            val errorMsg = "Failed to fetch/parse feed"
            rssFeedDao.updateFeedSource(source.copy(lastError = errorMsg))
            DebugLogger.log(TAG, "$errorMsg: ${source.sourceUrl}")
            return@withContext -1
        }

        var newItemCount = 0

        for (parsedItem in parsedFeed.items) {
            if (parsedItem.link.isBlank()) continue

            // Skip if we already have this item
            val existing = rssFeedDao.getItemByLink(parsedItem.link, sourceId)
            if (existing != null) {
                DebugLogger.verbose(TAG, "Skipping existing item: ${parsedItem.title}")
                continue
            }

            // Extract full text
            val extraction = extractor.extract(parsedItem.link)

            val feedItem = FeedItem(
                feedSourceId = sourceId,
                title = parsedItem.title,
                link = parsedItem.link,
                originalDescription = parsedItem.description,
                fullTextContent = extraction?.content ?: parsedItem.description,
                author = parsedItem.author,
                publishDate = parseDate(parsedItem.publishDate),
                extractionMethod = extraction?.method ?: "original-description"
            )

            rssFeedDao.insertItem(feedItem)
            newItemCount++
            DebugLogger.verbose(TAG, "Extracted item: ${feedItem.title} (method: ${feedItem.extractionMethod})")
        }

        // Update source metadata
        val itemCount = rssFeedDao.getItemCountForSource(sourceId)
        rssFeedDao.updateFeedSource(
            source.copy(
                lastRefreshed = System.currentTimeMillis(),
                itemCount = itemCount,
                lastError = null
            )
        )

        DebugLogger.log(TAG, "Refresh complete for '${source.name}': $newItemCount new items, $itemCount total")
        return@withContext newItemCount
    }

    private fun parseDate(dateStr: String?): Long? {
        if (dateStr.isNullOrBlank()) return null
        for (format in dateFormats) {
            try {
                return format.parse(dateStr)?.time
            } catch (_: Exception) {
                // Try next format
            }
        }
        DebugLogger.verbose(TAG, "Could not parse date: $dateStr")
        return null
    }
}
