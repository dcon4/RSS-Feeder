package com.example.rssfulltext.data.db

import androidx.room.*
import com.example.rssfulltext.data.model.DirectoryFeedItem
import com.example.rssfulltext.data.model.DirectoryFeedSource
import kotlinx.coroutines.flow.Flow

@Dao
interface DirectoryFeedDao {

    @Query("SELECT * FROM directory_feed_sources ORDER BY name ASC")
    fun getAllDirectorySources(): Flow<List<DirectoryFeedSource>>

    @Query("SELECT * FROM directory_feed_sources WHERE id = :id")
    suspend fun getDirectorySourceById(id: Long): DirectoryFeedSource?

    @Query("SELECT * FROM directory_feed_sources WHERE outputSlug = :slug")
    suspend fun getDirectorySourceBySlug(slug: String): DirectoryFeedSource?

    @Query("SELECT * FROM directory_feed_sources WHERE enabled = 1")
    suspend fun getEnabledDirectorySources(): List<DirectoryFeedSource>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDirectorySource(source: DirectoryFeedSource): Long

    @Update
    suspend fun updateDirectorySource(source: DirectoryFeedSource)

    @Delete
    suspend fun deleteDirectorySource(source: DirectoryFeedSource)

    // Directory Feed Items
    @Query("SELECT * FROM directory_feed_items WHERE directorySourceId = :sourceId ORDER BY lastModified DESC")
    suspend fun getItemsForDirectory(sourceId: Long): List<DirectoryFeedItem>

    @Query("SELECT * FROM directory_feed_items WHERE directorySourceId = :sourceId ORDER BY lastModified DESC")
    fun getItemsForDirectoryFlow(sourceId: Long): Flow<List<DirectoryFeedItem>>

    @Query("SELECT * FROM directory_feed_items WHERE filePath = :path AND directorySourceId = :sourceId LIMIT 1")
    suspend fun getItemByPath(path: String, sourceId: Long): DirectoryFeedItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDirectoryItem(item: DirectoryFeedItem): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDirectoryItems(items: List<DirectoryFeedItem>)

    @Query("DELETE FROM directory_feed_items WHERE directorySourceId = :sourceId")
    suspend fun deleteItemsForDirectory(sourceId: Long)

    @Query("SELECT COUNT(*) FROM directory_feed_items WHERE directorySourceId = :sourceId")
    suspend fun getItemCountForDirectory(sourceId: Long): Int
}
