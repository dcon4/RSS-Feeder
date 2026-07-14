package com.rssfeeder.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FeedDao {

    @Query("SELECT * FROM feeds ORDER BY title ASC")
    fun getAllFeeds(): Flow<List<FeedEntity>>

    @Query("SELECT * FROM feeds ORDER BY title ASC")
    suspend fun getFeedList(): List<FeedEntity>

    @Query("SELECT * FROM feeds WHERE id = :id")
    suspend fun getFeedById(id: Long): FeedEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFeed(feed: FeedEntity): Long

    @Update
    suspend fun updateFeed(feed: FeedEntity)

    @Delete
    suspend fun deleteFeed(feed: FeedEntity)

    @Query("DELETE FROM feeds WHERE id = :id")
    suspend fun deleteFeedById(id: Long)

    @Query("UPDATE feeds SET last_refresh_time = :time WHERE id = :id")
    suspend fun updateRefreshTime(id: Long, time: Long)

    @Query("UPDATE feeds SET error_message = :error WHERE id = :id")
    suspend fun updateError(id: Long, error: String?)

    @Query("UPDATE feeds SET auto_download = :enabled WHERE id = :id")
    suspend fun updateAutoDownload(id: Long, enabled: Boolean)

    @Query("UPDATE feeds SET download_folder = :folder WHERE id = :id")
    suspend fun updateDownloadFolder(id: Long, folder: String?)

    @Query("UPDATE feeds SET last_exported_time = :time WHERE id = :id")
    suspend fun updateLastExportedTime(id: Long, time: Long)

    @Query("SELECT * FROM feeds WHERE type = :type")
    suspend fun getFeedsByType(type: String): List<FeedEntity>

    @Query("UPDATE feeds SET polling_interval_minutes = :minutes WHERE id = :id")
    suspend fun updatePollingInterval(id: Long, minutes: Int)

    @Query("UPDATE feeds SET last_polled_at = :time WHERE id = :id")
    suspend fun updateLastPolledAt(id: Long, time: Long)
}
