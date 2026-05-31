package com.example.rssfulltext.data.repository

import com.example.rssfulltext.data.db.AppDatabase
import com.example.rssfulltext.data.model.DirectoryFeedItem
import com.example.rssfulltext.data.model.DirectoryFeedSource
import com.example.rssfulltext.extraction.DirectoryScanner
import com.example.rssfulltext.logging.DebugLogger
import kotlinx.coroutines.flow.Flow

/**
 * Repository for managing directory-based feed sources.
 */
class DirectoryFeedRepository(private val database: AppDatabase) {

    companion object {
        private const val TAG = "DirectoryFeedRepo"
    }

    private val directoryFeedDao = database.directoryFeedDao()
    private val directoryScanner = DirectoryScanner(database)

    fun getAllDirectorySources(): Flow<List<DirectoryFeedSource>> =
        directoryFeedDao.getAllDirectorySources()

    suspend fun getDirectorySourceById(id: Long): DirectoryFeedSource? =
        directoryFeedDao.getDirectorySourceById(id)

    suspend fun addDirectorySource(source: DirectoryFeedSource): Long {
        DebugLogger.log(TAG, "Adding directory source: ${source.name} (${source.directoryPath})")
        return directoryFeedDao.insertDirectorySource(source)
    }

    suspend fun updateDirectorySource(source: DirectoryFeedSource) {
        directoryFeedDao.updateDirectorySource(source)
    }

    suspend fun deleteDirectorySource(source: DirectoryFeedSource) {
        DebugLogger.log(TAG, "Deleting directory source: ${source.name}")
        directoryFeedDao.deleteDirectorySource(source)
    }

    suspend fun getItemsForDirectory(sourceId: Long): List<DirectoryFeedItem> =
        directoryFeedDao.getItemsForDirectory(sourceId)

    fun getItemsForDirectoryFlow(sourceId: Long): Flow<List<DirectoryFeedItem>> =
        directoryFeedDao.getItemsForDirectoryFlow(sourceId)

    /**
     * Scans a directory and updates its feed items.
     * @return number of new/updated items, -1 on failure
     */
    suspend fun scanDirectory(sourceId: Long): Int {
        return directoryScanner.scanDirectory(sourceId)
    }
}
