package com.rssfeeder.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "feeds")
data class FeedEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val type: String,
    @ColumnInfo(name = "last_refresh_time") val lastRefreshTime: Long = 0,
    @ColumnInfo(name = "error_message") val errorMessage: String? = null
)
