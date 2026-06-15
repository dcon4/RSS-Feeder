package com.rssfeeder.feed

import com.rssfeeder.data.model.Article
import com.rssfeeder.debug.DebugLogger
import com.rometools.rome.feed.synd.SyndFeed
import com.rometools.rome.io.SyndFeedInput
import com.rometools.rome.io.XmlReader
import java.net.URL

class RssFetcher {

    fun fetchFeed(feedUrl: String): List<Article> {
        DebugLogger.log("RssFetcher", "Fetching feed: $feedUrl")
        try {
            val url = URL(feedUrl)
            val input = SyndFeedInput()
            val feed: SyndFeed = input.build(XmlReader(url))

            val articles = feed.entries.mapNotNull { entry ->
                try {
                    Article(
                        feedId = 0,
                        title = entry.title ?: "Untitled",
                        link = entry.link ?: "",
                        author = entry.author,
                        publishedDate = entry.publishedDate?.time ?: 0,
                        summary = entry.description?.value
                    )
                } catch (e: Exception) {
                    DebugLogger.verbose("RssFetcher", "Skipping entry: ${e.message}")
                    null
                }
            }

            DebugLogger.log("RssFetcher", "Fetched ${articles.size} articles from $feedUrl")
            return articles
        } catch (e: Exception) {
            DebugLogger.log("RssFetcher", "Failed to fetch feed $feedUrl: ${e.message}")
            throw e
        }
    }
}
