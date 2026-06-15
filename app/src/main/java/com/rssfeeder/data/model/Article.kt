package com.rssfeeder.data.model

data class Article(
    val id: Long = 0,
    val feedId: Long,
    val title: String,
    val link: String,
    val author: String? = null,
    val publishedDate: Long = 0,
    val summary: String? = null,
    val content: String? = null,
    val isRead: Boolean = false,
    val isStarred: Boolean = false
)
