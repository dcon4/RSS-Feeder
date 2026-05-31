package com.example.rssfulltext.network.server

import com.example.rssfulltext.data.db.AppDatabase
import com.example.rssfulltext.logging.DebugLogger
import com.example.rssfulltext.network.rss.FeedGenerator
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking

/**
 * Lightweight HTTP server that serves full-text RSS feeds locally.
 *
 * Endpoints:
 * - GET /feed/{slug}       → RSS 2.0 XML for the given feed
 * - GET /feeds             → JSON list of available feeds
 * - GET /opml              → OPML file for importing all feeds at once
 * - GET /status            → Server status/health check
 */
class FeedHttpServer(
    private val database: AppDatabase,
    port: Int = DEFAULT_PORT,
    private val bindAllInterfaces: Boolean = false
) : NanoHTTPD(if (bindAllInterfaces) "0.0.0.0" else "127.0.0.1", port) {

    companion object {
        private const val TAG = "FeedHttpServer"
        const val DEFAULT_PORT = 8484
    }

    private val rssFeedDao = database.rssFeedDao()
    private val directoryFeedDao = database.directoryFeedDao()

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri ?: "/"
        val method = session.method

        DebugLogger.verbose(TAG, "${method.name} $uri")

        return try {
            when {
                uri == "/status" -> serveStatus()
                uri == "/feeds" -> serveFeedList()
                uri == "/opml" -> serveOpml()
                uri.startsWith("/feed/") -> serveFeed(uri.removePrefix("/feed/"))
                else -> serveNotFound()
            }
        } catch (e: Exception) {
            DebugLogger.log(TAG, "Error serving $uri: ${e.message}")
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                "text/plain",
                "Internal Server Error: ${e.message}"
            )
        }
    }

    private fun serveStatus(): Response {
        val json = """{"status":"running","port":$listeningPort,"version":"1.0"}"""
        return newFixedLengthResponse(Response.Status.OK, "application/json", json)
    }

    private fun serveFeedList(): Response = runBlocking {
        val rssFeeds = rssFeedDao.getEnabledFeedSources()
        val dirFeeds = directoryFeedDao.getEnabledDirectorySources()
        val baseUrl = getBaseUrl()

        val feedsJson = buildString {
            append("""{"feeds":[""")
            val allFeeds = mutableListOf<String>()

            for (feed in rssFeeds) {
                allFeeds.add("""{"name":"${escapeJson(feed.name)}","slug":"${feed.outputSlug}","type":"rss","url":"$baseUrl/feed/${feed.outputSlug}","items":${feed.itemCount}}""")
            }
            for (feed in dirFeeds) {
                allFeeds.add("""{"name":"${escapeJson(feed.name)}","slug":"${feed.outputSlug}","type":"directory","url":"$baseUrl/feed/${feed.outputSlug}","items":${feed.itemCount}}""")
            }

            append(allFeeds.joinToString(","))
            append("]}")
        }

        newFixedLengthResponse(Response.Status.OK, "application/json", feedsJson)
    }

    private fun serveOpml(): Response = runBlocking {
        val rssFeeds = rssFeedDao.getEnabledFeedSources()
        val dirFeeds = directoryFeedDao.getEnabledDirectorySources()
        val baseUrl = getBaseUrl()

        val opml = FeedGenerator.generateOpml(rssFeeds, dirFeeds, baseUrl)

        newFixedLengthResponse(Response.Status.OK, "text/x-opml", opml).also {
            it.addHeader("Content-Disposition", "attachment; filename=\"feeds.opml\"")
        }
    }

    private fun serveFeed(slug: String): Response = runBlocking {
        val baseUrl = getBaseUrl()

        // Try RSS feed source first
        val rssFeedSource = rssFeedDao.getFeedSourceBySlug(slug)
        if (rssFeedSource != null) {
            val items = rssFeedDao.getItemsForSource(rssFeedSource.id)
            val xml = FeedGenerator.generateRssFeed(rssFeedSource, items, baseUrl)
            DebugLogger.verbose(TAG, "Serving RSS feed '${rssFeedSource.name}' with ${items.size} items")
            return@runBlocking newFixedLengthResponse(Response.Status.OK, "application/rss+xml", xml)
        }

        // Try directory feed source
        val dirFeedSource = directoryFeedDao.getDirectorySourceBySlug(slug)
        if (dirFeedSource != null) {
            val items = directoryFeedDao.getItemsForDirectory(dirFeedSource.id)
            val xml = FeedGenerator.generateDirectoryFeed(dirFeedSource, items, baseUrl)
            DebugLogger.verbose(TAG, "Serving directory feed '${dirFeedSource.name}' with ${items.size} items")
            return@runBlocking newFixedLengthResponse(Response.Status.OK, "application/rss+xml", xml)
        }

        DebugLogger.log(TAG, "Feed not found: $slug")
        serveNotFound()
    }

    private fun serveNotFound(): Response {
        return newFixedLengthResponse(
            Response.Status.NOT_FOUND,
            "text/plain",
            "Feed not found. Visit /feeds for available feeds or /opml for import file."
        )
    }

    private fun getBaseUrl(): String {
        return "http://127.0.0.1:$listeningPort"
    }

    private fun escapeJson(text: String): String {
        return text
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    fun startServer() {
        try {
            start(SOCKET_READ_TIMEOUT, false)
            DebugLogger.log(TAG, "HTTP server started on port $listeningPort (bindAll=$bindAllInterfaces)")
        } catch (e: Exception) {
            DebugLogger.log(TAG, "Failed to start HTTP server: ${e.message}")
            throw e
        }
    }

    fun stopServer() {
        stop()
        DebugLogger.log(TAG, "HTTP server stopped")
    }
}
