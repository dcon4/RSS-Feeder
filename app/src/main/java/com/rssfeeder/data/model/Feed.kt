package com.rssfeeder.data.model

data class Feed(
    val id: Long = 0,
    val title: String,
    val url: String,
    val type: FeedType,
    val lastRefreshTime: Long = 0,
    val unreadCount: Int = 0,
    val errorMessage: String? = null
)

enum class FeedType {
    REMOTE,
    LOCAL_FOLDER
}
