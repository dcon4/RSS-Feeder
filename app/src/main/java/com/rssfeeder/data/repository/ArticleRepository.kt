package com.rssfeeder.data.repository

import com.rssfeeder.data.db.ArticleDao
import com.rssfeeder.data.db.ArticleEntity
import com.rssfeeder.data.model.Article
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ArticleRepository(
    private val articleDao: ArticleDao
) {
    fun getArticlesForFeed(feedId: Long): Flow<List<Article>> {
        return articleDao.getArticlesForFeed(feedId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    fun getAllArticles(): Flow<List<Article>> {
        return articleDao.getAllArticles().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun getArticleById(id: Long): Article? {
        return articleDao.getArticleById(id)?.toDomain()
    }

    suspend fun getArticleByLink(link: String): Article? {
        return articleDao.getArticleByLink(link)?.toDomain()
    }

    suspend fun insertArticle(article: Article): Long {
        return articleDao.insertArticle(article.toEntity())
    }

    suspend fun insertArticles(articles: List<Article>) {
        articleDao.insertArticles(articles.map { it.toEntity() })
    }

    suspend fun markAsRead(id: Long) {
        articleDao.markAsRead(id)
    }

    private fun ArticleEntity.toDomain(): Article {
        return Article(
            id = id,
            feedId = feedId,
            title = title,
            link = link,
            author = author,
            publishedDate = publishedDate,
            summary = summary,
            content = content,
            isRead = isRead,
            isStarred = isStarred
        )
    }

    private fun Article.toEntity(): ArticleEntity {
        return ArticleEntity(
            id = id,
            feedId = feedId,
            title = title,
            link = link,
            author = author,
            publishedDate = publishedDate,
            summary = summary,
            content = content,
            isRead = isRead,
            isStarred = isStarred
        )
    }
}
