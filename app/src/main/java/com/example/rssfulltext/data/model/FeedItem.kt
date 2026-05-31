package com.example.rssfulltext.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A single item within a feed, with extracted full-text content.
 */
@Entity(
    tableName = "feed_items",
    foreignKeys = [
        ForeignKey(
            entity = RssFeedSource::class,
            parentColumns = ["id"],
            childColumns = ["feedSourceId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("feedSourceId"), Index("link")]
)
data class FeedItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val feedSourceId: Long,
    val title: String,
    val link: String,
    val originalDescription: String? = null,
    val fullTextContent: String? = null,
    val author: String? = null,
    val publishDate: Long? = null,
    val extractionMethod: String? = null, // "jsoup", "readability", "raw"
    val extractedAt: Long = System.currentTimeMillis()
)
