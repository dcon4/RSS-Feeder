package com.example.rssfulltext.network.rss

import com.example.rssfulltext.logging.DebugLogger
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.util.concurrent.TimeUnit

/**
 * Fetches and parses RSS/Atom feeds from URLs.
 * Uses Jsoup XML parser for lightweight RSS parsing without heavy ROME dependency at runtime.
 */
class RssFetcher {

    companion object {
        private const val TAG = "RssFetcher"
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    data class ParsedFeedItem(
        val title: String,
        val link: String,
        val description: String?,
        val author: String?,
        val publishDate: String?,
        val guid: String?
    )

    data class ParsedFeed(
        val title: String?,
        val description: String?,
        val link: String?,
        val items: List<ParsedFeedItem>
    )

    /**
     * Fetches and parses an RSS or Atom feed from the given URL.
     */
    fun fetchFeed(url: String): ParsedFeed? {
        DebugLogger.log(TAG, "Fetching feed: $url")

        val xml = fetchXml(url)
        if (xml == null) {
            DebugLogger.log(TAG, "Failed to fetch feed XML from: $url")
            return null
        }

        return try {
            val doc = Jsoup.parse(xml, url, Parser.xmlParser())

            // Detect feed type
            val rssChannel = doc.selectFirst("channel")
            val atomFeed = doc.selectFirst("feed")

            when {
                rssChannel != null -> parseRss(rssChannel)
                atomFeed != null -> parseAtom(atomFeed)
                else -> {
                    DebugLogger.log(TAG, "Unknown feed format for: $url")
                    null
                }
            }
        } catch (e: Exception) {
            DebugLogger.log(TAG, "Parse error for $url: ${e.message}")
            null
        }
    }

    private fun fetchXml(url: String): String? {
        return try {
            DebugLogger.verbose(TAG, "HTTP GET: $url")
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "RSSFullTextProxy/1.0")
                .header("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml")
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                response.body?.string()
            } else {
                DebugLogger.log(TAG, "HTTP ${response.code} fetching feed: $url")
                null
            }
        } catch (e: Exception) {
            DebugLogger.log(TAG, "Network error fetching feed $url: ${e.message}")
            null
        }
    }

    private fun parseRss(channel: org.jsoup.nodes.Element): ParsedFeed {
        val title = channel.selectFirst("title")?.text()
        val description = channel.selectFirst("description")?.text()
        val link = channel.selectFirst("link")?.text()

        val items = channel.select("item").map { item ->
            ParsedFeedItem(
                title = item.selectFirst("title")?.text() ?: "Untitled",
                link = item.selectFirst("link")?.text()
                    ?: item.selectFirst("guid")?.text() ?: "",
                description = item.selectFirst("description")?.text()
                    ?: item.selectFirst("content|encoded")?.text(),
                author = item.selectFirst("author")?.text()
                    ?: item.selectFirst("dc|creator")?.text(),
                publishDate = item.selectFirst("pubDate")?.text()
                    ?: item.selectFirst("dc|date")?.text(),
                guid = item.selectFirst("guid")?.text()
            )
        }

        DebugLogger.log(TAG, "Parsed RSS feed: '$title' with ${items.size} items")
        return ParsedFeed(title, description, link, items)
    }

    private fun parseAtom(feed: org.jsoup.nodes.Element): ParsedFeed {
        val title = feed.selectFirst("title")?.text()
        val subtitle = feed.selectFirst("subtitle")?.text()
        val link = feed.selectFirst("link[rel=alternate]")?.attr("href")
            ?: feed.selectFirst("link")?.attr("href")

        val items = feed.select("entry").map { entry ->
            val entryLink = entry.selectFirst("link[rel=alternate]")?.attr("href")
                ?: entry.selectFirst("link")?.attr("href") ?: ""

            ParsedFeedItem(
                title = entry.selectFirst("title")?.text() ?: "Untitled",
                link = entryLink,
                description = entry.selectFirst("content")?.text()
                    ?: entry.selectFirst("summary")?.text(),
                author = entry.selectFirst("author name")?.text()
                    ?: entry.selectFirst("author")?.text(),
                publishDate = entry.selectFirst("published")?.text()
                    ?: entry.selectFirst("updated")?.text(),
                guid = entry.selectFirst("id")?.text()
            )
        }

        DebugLogger.log(TAG, "Parsed Atom feed: '$title' with ${items.size} items")
        return ParsedFeed(title, subtitle, link, items)
    }
}
