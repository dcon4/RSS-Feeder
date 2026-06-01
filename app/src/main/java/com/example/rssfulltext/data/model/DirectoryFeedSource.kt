package com.example.rssfulltext.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a local directory that the app monitors and converts to an RSS feed.
 * Each directory corresponds to its own output feed.
 */
@Entity(tableName = "directory_feed_sources")
data class DirectoryFeedSource(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val directoryPath: String,
    val outputSlug: String, // Used in the served URL: /feed/{slug}
    val enabled: Boolean = true,
    val includeSubdirectories: Boolean = false,
    val lastScanned: Long = 0,
    val itemCount: Int = 0,
    val lastError: String? = null
)
