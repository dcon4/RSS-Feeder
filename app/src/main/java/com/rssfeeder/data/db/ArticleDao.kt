package com.rssfeeder.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ArticleDao {

    @Query("SELECT * FROM articles WHERE feed_id = :feedId ORDER BY published_date DESC")
    fun getArticlesForFeed(feedId: Long): Flow<List<ArticleEntity>>

    @Query("SELECT * FROM articles WHERE feed_id = :feedId ORDER BY published_date DESC")
    suspend fun getArticleListForFeed(feedId: Long): List<ArticleEntity>

    @Query("SELECT * FROM articles ORDER BY published_date DESC")
    fun getAllArticles(): Flow<List<ArticleEntity>>

    @Query("SELECT * FROM articles WHERE id = :id")
    suspend fun getArticleById(id: Long): ArticleEntity?

    @Query("SELECT * FROM articles WHERE link = :link LIMIT 1")
    suspend fun getArticleByLink(link: String): ArticleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArticle(article: ArticleEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArticles(articles: List<ArticleEntity>)

    @Update
    suspend fun updateArticle(article: ArticleEntity)

    @Query("UPDATE articles SET is_read = 1 WHERE feed_id = :feedId")
    suspend fun markAllAsRead(feedId: Long)

    @Query("UPDATE articles SET is_read = 1 WHERE id = :id")
    suspend fun markAsRead(id: Long)

    @Query("SELECT COUNT(*) FROM articles WHERE feed_id = :feedId AND is_read = 0")
    fun getUnreadCount(feedId: Long): Flow<Int>

    @Query("DELETE FROM articles WHERE feed_id = :feedId")
    suspend fun deleteArticlesForFeed(feedId: Long)
}
