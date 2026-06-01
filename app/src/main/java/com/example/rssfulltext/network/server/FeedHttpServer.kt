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
 * - GET /feed/{slug}       -> RSS 2.0 XML for the given feed
 * - GET /feeds             -> JSON list of available feeds
 * - GET /opml              -> OPML file for importing all feeds at once
 * - GET /status            -> Server status/health check
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
                uri.startsWith("/raw/") -> serveRawFeed(uri.removePrefix("/raw/"))
                uri.startsWith("/feed/") -> serveFeed(uri.removePrefix("/feed/"))
                else -> serveNotFound()
            }
        } catch (e: Exception) {
            DebugLogger.log(TAG, "Error serving $uri: ${e.message}")
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                "text/plain; charset=utf-8",
                "Internal Server Error: ${e.message}"
            )
        }
    }

    private fun serveStatus(): Response {
        val json = """{"status":"running","port":$listeningPort,"version":"1.0"}"""
        return newFixedLengthResponse(Response.Status.OK, "application/json", json).apply {
            addHeader("Cache-Control", "no-cache")
        }
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

        newFixedLengthResponse(Response.Status.OK, "application/json", feedsJson).apply {
            addHeader("Cache-Control", "no-cache")
        }
    }

    private fun serveOpml(): Response = runBlocking {
        val rssFeeds = rssFeedDao.getEnabledFeedSources()
        val dirFeeds = directoryFeedDao.getEnabledDirectorySources()
        val baseUrl = getBaseUrl()

        val opml = FeedGenerator.generateOpml(rssFeeds, dirFeeds, baseUrl)

        createXmlResponse(opml).apply {
            addHeader("Cache-Control", "no-cache")
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
            return@runBlocking createXmlResponse(xml).apply {
                addHeader("Cache-Control", "no-cache")
            }
        }

        // Try directory feed source
        val dirFeedSource = directoryFeedDao.getDirectorySourceBySlug(slug)
        if (dirFeedSource != null) {
            val items = directoryFeedDao.getItemsForDirectory(dirFeedSource.id)
            val xml = FeedGenerator.generateDirectoryFeed(dirFeedSource, items, baseUrl)
            DebugLogger.verbose(TAG, "Serving directory feed '${dirFeedSource.name}' with ${items.size} items")
            return@runBlocking createXmlResponse(xml).apply {
                addHeader("Cache-Control", "no-cache")
            }
        }

        DebugLogger.log(TAG, "Feed not found: $slug")
        serveNotFound()
    }

    private fun serveNotFound(): Response {
        return newFixedLengthResponse(
            Response.Status.NOT_FOUND,
            "text/plain; charset=utf-8",
            "Feed not found. Visit /feeds for available feeds or /opml for import file."
        )
    }

    /**
     * Serve the raw XML as plain text for debugging.
     * Access via http://127.0.0.1:8484/raw/slug-name in a browser to inspect the actual output.
     */
    private fun serveRawFeed(slug: String): Response = runBlocking {
        val baseUrl = getBaseUrl()

        val rssFeedSource = rssFeedDao.getFeedSourceBySlug(slug)
        if (rssFeedSource != null) {
            val items = rssFeedDao.getItemsForSource(rssFeedSource.id)
            val xml = FeedGenerator.generateRssFeed(rssFeedSource, items, baseUrl)
            return@runBlocking newFixedLengthResponse(Response.Status.OK, "text/plain; charset=utf-8", xml)
        }

        val dirFeedSource = directoryFeedDao.getDirectorySourceBySlug(slug)
        if (dirFeedSource != null) {
            val items = directoryFeedDao.getItemsForDirectory(dirFeedSource.id)
            val xml = FeedGenerator.generateDirectoryFeed(dirFeedSource, items, baseUrl)
            return@runBlocking newFixedLengthResponse(Response.Status.OK, "text/plain; charset=utf-8", xml)
        }

        serveNotFound()
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

    /**
     * Create an XML response with explicit UTF-8 byte handling.
     *
     * Uses ByteArrayInputStream to ensure:
     * 1. Content-Length header matches actual byte count exactly
     * 2. charset=utf-8 is declared in Content-Type
     * 3. No chunked transfer encoding (which some readers can't handle)
     */
    private fun createXmlResponse(content: String): Response {
        val bytes = content.toByteArray(Charsets.UTF_8)
        DebugLogger.verbose(TAG, "Response: ${bytes.size} bytes, starts with: ${content.take(100)}")
        return newFixedLengthResponse(
            Response.Status.OK,
            "text/xml; charset=utf-8",
            java.io.ByteArrayInputStream(bytes),
            bytes.size.toLong()
        )
    }
}
