package com.rssfeeder.server

import android.content.Context
import com.rssfeeder.data.db.AppDatabase
import com.rssfeeder.data.repository.ArticleRepository
import com.rssfeeder.data.repository.FeedRepository
import com.rssfeeder.debug.DebugLogger
import fi.iki.elonen.NanoHTTPD
import javax.net.ssl.SSLServerSocketFactory
import kotlinx.coroutines.runBlocking

class FeedServer(
    port: Int,
    private val context: Context,
    private val sslFactory: SSLServerSocketFactory? = null
) : NanoHTTPD(port) {

    private var baseUrl: String = if (sslFactory != null) "https://127.0.0.1:$port" else "http://127.0.0.1:$port"

    init {
        if (sslFactory != null) {
            makeSecure(sslFactory, null)
        }
    }

    private val db by lazy { AppDatabase.getInstance(context) }
    private val feedRepository by lazy { FeedRepository(db.feedDao(), db.articleDao()) }
    private val articleRepository by lazy { ArticleRepository(db.articleDao()) }

    fun setBaseUrl(url: String) {
        baseUrl = url.trimEnd('/')
    }

    override fun serve(session: IHTTPSession): Response {
        val rawPath = session.uri.split("?").first().trimEnd('/')

        return try {
            when {
                rawPath == "/" || rawPath == "/index.html" -> handleIndex()
                rawPath == "/health" -> newFixedLengthResponse(
                    Response.Status.OK, "text/plain", "OK"
                )
                rawPath.matches(Regex("/feed/\\d+/rss\\.xml")) -> handleFeedRss(rawPath)
                else -> newFixedLengthResponse(
                    Response.Status.NOT_FOUND, "text/xml", "Not found"
                )
            }
        } catch (e: Exception) {
            DebugLogger.log("FeedServer", "Request failed: ${e.message}")
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR, "text/plain", "Server error"
            )
        }
    }

    private fun handleIndex(): Response {
        val feeds = runBlocking { feedRepository.getFeedList() }
        val html = RssXmlBuilder.buildIndexHtml(feeds, baseUrl)
        return newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", html)
    }

    private fun handleFeedRss(uri: String): Response {
        val feedId = uri.removePrefix("/feed/").removeSuffix("/rss.xml").toLongOrNull()
            ?: return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/xml", "Invalid feed ID")

        val feed = runBlocking { feedRepository.getFeedById(feedId) }
            ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/xml", "Feed not found")

        val articles = runBlocking { articleRepository.getArticlesForFeedList(feedId) }
        val xml = RssXmlBuilder.buildFeedXml(feed, articles, baseUrl)
        return newFixedLengthResponse(Response.Status.OK, "text/xml; charset=utf-8", xml)
    }
}
