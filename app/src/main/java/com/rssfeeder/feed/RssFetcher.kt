package com.rssfeeder.feed

import android.util.Xml
import com.rssfeeder.data.model.Article
import com.rssfeeder.debug.DebugLogger
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import org.xmlpull.v1.XmlPullParser

@Suppress("DEPRECATION")
class RssFetcher {

    fun fetchFeed(feedUrl: String): List<Article> {
        DebugLogger.log("RssFetcher", "Fetching feed: $feedUrl")
        return try {
            val url = URL(feedUrl)
            val inputStream = url.openConnection().let { conn ->
                conn.setRequestProperty("User-Agent", "RSS-Feeder/1.0 Android")
                conn.connectTimeout = 15000
                conn.readTimeout = 15000
                conn.getInputStream()
            }

            val parser = Xml.newPullParser()
            parser.setInput(inputStream.bufferedReader())

            val articles = mutableListOf<Article>()
            var currentTitle: String? = null
            var currentLink: String? = null
            var currentAuthor: String? = null
            var currentDate: String? = null
            var currentSummary: String? = null
            var inItem = false
            var tagContent = StringBuilder()

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        val tagName = parser.name.lowercase()
                        when (tagName) {
                            "item", "entry" -> {
                                inItem = true
                                currentTitle = null
                                currentLink = null
                                currentAuthor = null
                                currentDate = null
                                currentSummary = null
                                tagContent = StringBuilder()
                            }
                            "title", "link", "author", "name", "pubdate",
                            "published", "updated", "summary", "description",
                            "content" -> {
                                tagContent = StringBuilder()
                                if (tagName == "link") {
                                    currentLink = parser.getAttributeValue(null, "href")
                                }
                            }
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (inItem) {
                            tagContent.append(parser.text)
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (inItem) {
                            val text = tagContent.toString().trim()
                            when (parser.name.lowercase()) {
                                "item", "entry" -> {
                                    if (currentTitle != null || currentLink != null) {
                                        articles.add(
                                            Article(
                                                feedId = 0,
                                                title = currentTitle ?: "Untitled",
                                                link = currentLink ?: "",
                                                author = currentAuthor,
                                                publishedDate = parseDate(currentDate),
                                                summary = currentSummary
                                            )
                                        )
                                    }
                                    inItem = false
                                }
                                "title" -> if (currentTitle == null) currentTitle = text
                                "link" -> if (currentLink == null) currentLink = text
                                "author" -> if (currentAuthor == null) currentAuthor = text
                                "name" -> if (currentAuthor == null) currentAuthor = text
                                "pubdate", "published", "updated" ->
                                    if (currentDate == null) currentDate = text
                                "description", "summary" ->
                                    if (currentSummary == null) currentSummary = text
                            }
                        }
                    }
                }
                eventType = parser.next()
            }

            DebugLogger.log("RssFetcher", "Fetched ${articles.size} articles from $feedUrl")
            articles
        } catch (e: Exception) {
            DebugLogger.log("RssFetcher", "Failed to fetch feed $feedUrl: ${e.message}")
            throw e
        }
    }

    private fun parseDate(dateStr: String?): Long {
        if (dateStr.isNullOrBlank()) return 0
        val formats = listOf(
            "EEE, dd MMM yyyy HH:mm:ss Z",
            "EEE, dd MMM yyyy HH:mm:ss zzz",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd"
        )
        for (format in formats) {
            try {
                return SimpleDateFormat(format, Locale.US).parse(dateStr)?.time ?: 0
            } catch (_: Exception) {}
        }
        return 0
    }
}
