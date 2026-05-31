package com.example.rssfulltext.network.rss

import com.example.rssfulltext.data.model.DirectoryFeedItem
import com.example.rssfulltext.data.model.DirectoryFeedSource
import com.example.rssfulltext.data.model.FeedItem
import com.example.rssfulltext.data.model.RssFeedSource
import com.example.rssfulltext.logging.DebugLogger
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Generates valid RSS 2.0 XML from stored full-text feed items.
 */
object FeedGenerator {

    private const val TAG = "FeedGenerator"
    private val rssDateFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US)

    /**
     * Generate RSS 2.0 XML for an RSS feed source with its extracted items.
     */
    fun generateRssFeed(
        source: RssFeedSource,
        items: List<FeedItem>,
        serverBaseUrl: String
    ): String {
        DebugLogger.verbose(TAG, "Generating RSS feed for '${source.name}' with ${items.size} items")

        val selfLink = "$serverBaseUrl/feed/${source.outputSlug}"
        val lastBuildDate = rssDateFormat.format(Date(source.lastRefreshed.takeIf { it > 0 } ?: System.currentTimeMillis()))

        val sb = StringBuilder()
        sb.appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        sb.appendLine("""<rss version="2.0" xmlns:atom="http://www.w3.org/2005/Atom" xmlns:content="http://purl.org/rss/1.0/modules/content/">""")
        sb.appendLine("  <channel>")
        sb.appendLine("    <title>${escapeXml(source.name)} (Full Text)</title>")
        sb.appendLine("    <link>${escapeXml(source.sourceUrl)}</link>")
        sb.appendLine("    <description>Full-text version of ${escapeXml(source.name)}</description>")
        sb.appendLine("    <lastBuildDate>$lastBuildDate</lastBuildDate>")
        sb.appendLine("""    <atom:link href="${escapeXml(selfLink)}" rel="self" type="application/rss+xml"/>""")
        sb.appendLine("    <generator>RSS Full Text Proxy</generator>")

        for (item in items) {
            sb.appendLine("    <item>")
            sb.appendLine("      <title>${escapeXml(item.title)}</title>")
            sb.appendLine("      <link>${escapeXml(item.link)}</link>")
            sb.appendLine("      <guid isPermaLink=\"true\">${escapeXml(item.link)}</guid>")

            if (item.author != null) {
                sb.appendLine("      <author>${escapeXml(item.author)}</author>")
            }

            if (item.publishDate != null) {
                sb.appendLine("      <pubDate>${rssDateFormat.format(Date(item.publishDate))}</pubDate>")
            }

            // Description gets the summary/original description
            if (item.originalDescription != null) {
                sb.appendLine("      <description><![CDATA[${item.originalDescription}]]></description>")
            }

            // content:encoded gets the full text
            val content = item.fullTextContent ?: item.originalDescription ?: ""
            sb.appendLine("      <content:encoded><![CDATA[${formatContentAsHtml(content)}]]></content:encoded>")

            sb.appendLine("    </item>")
        }

        sb.appendLine("  </channel>")
        sb.appendLine("</rss>")

        return sb.toString()
    }

    /**
     * Generate RSS 2.0 XML for a directory-based feed source.
     */
    fun generateDirectoryFeed(
        source: DirectoryFeedSource,
        items: List<DirectoryFeedItem>,
        serverBaseUrl: String
    ): String {
        DebugLogger.verbose(TAG, "Generating directory feed for '${source.name}' with ${items.size} items")

        val selfLink = "$serverBaseUrl/feed/${source.outputSlug}"
        val lastBuildDate = rssDateFormat.format(Date(source.lastScanned.takeIf { it > 0 } ?: System.currentTimeMillis()))

        val sb = StringBuilder()
        sb.appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        sb.appendLine("""<rss version="2.0" xmlns:atom="http://www.w3.org/2005/Atom" xmlns:content="http://purl.org/rss/1.0/modules/content/">""")
        sb.appendLine("  <channel>")
        sb.appendLine("    <title>${escapeXml(source.name)}</title>")
        sb.appendLine("    <link>file://${escapeXml(source.directoryPath)}</link>")
        sb.appendLine("    <description>Feed generated from local directory: ${escapeXml(source.directoryPath)}</description>")
        sb.appendLine("    <lastBuildDate>$lastBuildDate</lastBuildDate>")
        sb.appendLine("""    <atom:link href="${escapeXml(selfLink)}" rel="self" type="application/rss+xml"/>""")
        sb.appendLine("    <generator>RSS Full Text Proxy - Directory Feed</generator>")

        for (item in items) {
            sb.appendLine("    <item>")
            sb.appendLine("      <title>${escapeXml(item.title)}</title>")
            sb.appendLine("      <link>file://${escapeXml(item.filePath)}</link>")
            sb.appendLine("      <guid isPermaLink=\"false\">${escapeXml(item.filePath)}</guid>")

            if (item.lastModified > 0) {
                sb.appendLine("      <pubDate>${rssDateFormat.format(Date(item.lastModified))}</pubDate>")
            }

            sb.appendLine("      <description><![CDATA[File: ${item.title} (${item.fileType.uppercase()}, ${formatFileSize(item.fileSize)})]]></description>")

            val content = item.textContent ?: "(Content extraction failed for this file)"
            sb.appendLine("      <content:encoded><![CDATA[${formatContentAsHtml(content)}]]></content:encoded>")

            sb.appendLine("    </item>")
        }

        sb.appendLine("  </channel>")
        sb.appendLine("</rss>")

        return sb.toString()
    }

    /**
     * Generate an OPML file listing all available feeds for easy import.
     */
    fun generateOpml(
        rssFeeds: List<RssFeedSource>,
        directoryFeeds: List<DirectoryFeedSource>,
        serverBaseUrl: String
    ): String {
        val sb = StringBuilder()
        sb.appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        sb.appendLine("""<opml version="2.0">""")
        sb.appendLine("  <head>")
        sb.appendLine("    <title>RSS Full Text Proxy Feeds</title>")
        sb.appendLine("    <dateCreated>${rssDateFormat.format(Date())}</dateCreated>")
        sb.appendLine("  </head>")
        sb.appendLine("  <body>")

        if (rssFeeds.isNotEmpty()) {
            sb.appendLine("""    <outline text="Full Text Feeds" title="Full Text Feeds">""")
            for (feed in rssFeeds) {
                val xmlUrl = "$serverBaseUrl/feed/${feed.outputSlug}"
                sb.appendLine("""      <outline type="rss" text="${escapeXml(feed.name)}" title="${escapeXml(feed.name)}" xmlUrl="${escapeXml(xmlUrl)}" htmlUrl="${escapeXml(feed.sourceUrl)}"/>""")
            }
            sb.appendLine("    </outline>")
        }

        if (directoryFeeds.isNotEmpty()) {
            sb.appendLine("""    <outline text="Directory Feeds" title="Directory Feeds">""")
            for (feed in directoryFeeds) {
                val xmlUrl = "$serverBaseUrl/feed/${feed.outputSlug}"
                sb.appendLine("""      <outline type="rss" text="${escapeXml(feed.name)}" title="${escapeXml(feed.name)}" xmlUrl="${escapeXml(xmlUrl)}"/>""")
            }
            sb.appendLine("    </outline>")
        }

        sb.appendLine("  </body>")
        sb.appendLine("</opml>")

        return sb.toString()
    }

    private fun formatContentAsHtml(text: String): String {
        // Convert newline-separated text to HTML paragraphs
        return text.split("\n\n")
            .filter { it.isNotBlank() }
            .joinToString("\n") { paragraph ->
                val trimmed = paragraph.trim()
                when {
                    trimmed.startsWith("## ") -> "<h2>${trimmed.removePrefix("## ")}</h2>"
                    trimmed.startsWith("# ") -> "<h1>${trimmed.removePrefix("# ")}</h1>"
                    trimmed.startsWith("> ") -> "<blockquote><p>${trimmed.removePrefix("> ")}</p></blockquote>"
                    trimmed.startsWith("- ") -> "<ul><li>${trimmed.removePrefix("- ")}</li></ul>"
                    trimmed.startsWith("```") -> "<pre><code>${trimmed.removeSurrounding("```\n", "\n```")}</code></pre>"
                    else -> "<p>$trimmed</p>"
                }
            }
    }

    private fun escapeXml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> "${bytes / (1024 * 1024)} MB"
        }
    }
}
