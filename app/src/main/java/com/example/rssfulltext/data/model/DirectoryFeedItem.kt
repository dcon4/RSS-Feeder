package com.example.rssfulltext.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A single item derived from a file in a directory feed.
 */
@Entity(
    tableName = "directory_feed_items",
    foreignKeys = [
        ForeignKey(
            entity = DirectoryFeedSource::class,
            parentColumns = ["id"],
            childColumns = ["directorySourceId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("directorySourceId"), Index("filePath")]
)
data class DirectoryFeedItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val directorySourceId: Long,
    val title: String,
    val filePath: String,
    val fileType: String, // "txt", "html", "epub", "pdf"
    val textContent: String? = null,
    val fileSize: Long = 0,
    val lastModified: Long = 0,
    val extractedAt: Long = System.currentTimeMillis()
)
