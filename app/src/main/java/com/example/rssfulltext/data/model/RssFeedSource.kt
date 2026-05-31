package com.example.rssfulltext.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents an RSS feed source that the user has added.
 * The app will fetch this feed, extract full text for each item,
 * and serve it as a new full-text feed.
 */
@Entity(tableName = "rss_feed_sources")
data class RssFeedSource(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val sourceUrl: String,
    val outputSlug: String, // Used in the served URL: /feed/{slug}
    val refreshIntervalMinutes: Int = 60,
    val lastRefreshed: Long = 0,
    val enabled: Boolean = true,
    val itemCount: Int = 0,
    val lastError: String? = null
)
