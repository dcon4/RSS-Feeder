package com.example.rssfulltext.data.db

import androidx.room.*
import com.example.rssfulltext.data.model.FeedItem
import com.example.rssfulltext.data.model.RssFeedSource
import kotlinx.coroutines.flow.Flow

@Dao
interface RssFeedDao {

    @Query("SELECT * FROM rss_feed_sources ORDER BY name ASC")
    fun getAllFeedSources(): Flow<List<RssFeedSource>>

    @Query("SELECT * FROM rss_feed_sources WHERE id = :id")
    suspend fun getFeedSourceById(id: Long): RssFeedSource?

    @Query("SELECT * FROM rss_feed_sources WHERE outputSlug = :slug")
    suspend fun getFeedSourceBySlug(slug: String): RssFeedSource?

    @Query("SELECT * FROM rss_feed_sources WHERE enabled = 1")
    suspend fun getEnabledFeedSources(): List<RssFeedSource>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFeedSource(source: RssFeedSource): Long

    @Update
    suspend fun updateFeedSource(source: RssFeedSource)

    @Delete
    suspend fun deleteFeedSource(source: RssFeedSource)

    // Feed Items
    @Query("SELECT * FROM feed_items WHERE feedSourceId = :sourceId ORDER BY publishDate DESC")
    suspend fun getItemsForSource(sourceId: Long): List<FeedItem>

    @Query("SELECT * FROM feed_items WHERE feedSourceId = :sourceId ORDER BY publishDate DESC")
    fun getItemsForSourceFlow(sourceId: Long): Flow<List<FeedItem>>

    @Query("SELECT * FROM feed_items WHERE link = :link AND feedSourceId = :sourceId LIMIT 1")
    suspend fun getItemByLink(link: String, sourceId: Long): FeedItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: FeedItem): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<FeedItem>)

    @Query("DELETE FROM feed_items WHERE feedSourceId = :sourceId")
    suspend fun deleteItemsForSource(sourceId: Long)

    @Query("SELECT COUNT(*) FROM feed_items WHERE feedSourceId = :sourceId")
    suspend fun getItemCountForSource(sourceId: Long): Int
}
