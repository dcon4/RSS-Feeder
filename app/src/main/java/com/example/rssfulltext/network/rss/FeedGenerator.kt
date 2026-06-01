package com.example.rssfulltext.network.rss

import com.example.rssfulltext.data.model.DirectoryFeedItem
import com.example.rssfulltext.data.model.DirectoryFeedSource
import com.example.rssfulltext.data.model.FeedItem
import com.example.rssfulltext.data.model.RssFeedSource
import com.example.rssfulltext.logging.DebugLogger
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Generates valid RSS 2.0 XML from stored full-text feed items.
 * Designed for maximum compatibility with strict RSS readers (gReader Pro, Pluma, etc).
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

        val lastBuildDate = rssDateFormat.format(Date(source.lastRefreshed.takeIf { it > 0 } ?: System.currentTimeMillis()))

        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8"?>""")
        sb.append("\n")
        sb.append("""<rss version="2.0">""")
        sb.append("\n")
        sb.append("  <channel>\n")
        sb.append("    <title>${escapeXml(source.name)} (Full Text)</title>\n")
        sb.append("    <link>${escapeXml(source.sourceUrl)}</link>\n")
        sb.append("    <description>Full-text version of ${escapeXml(source.name)}</description>\n")
        sb.append("    <lastBuildDate>$lastBuildDate</lastBuildDate>\n")
        sb.append("    <generator>RSS Full Text Proxy</generator>\n")

        for (item in items) {
            sb.append("    <item>\n")
            sb.append("      <title>${escapeXml(item.title)}</title>\n")
            sb.append("      <link>${escapeXml(item.link)}</link>\n")

            // Use a generated UUID as guid if link is empty or blank
            val guidValue = if (item.link.isBlank()) {
                UUID.nameUUIDFromBytes("${item.feedSourceId}:${item.title}:${item.publishDate}".toByteArray()).toString()
            } else {
                item.link
            }
            val isPermaLink = item.link.isNotBlank() && (item.link.startsWith("http://") || item.link.startsWith("https://"))
            sb.append("      <guid isPermaLink=\"$isPermaLink\">${escapeXml(guidValue)}</guid>\n")

            if (item.author != null) {
                sb.append("      <author>${escapeXml(item.author)}</author>\n")
            }

            if (item.publishDate != null) {
                sb.append("      <pubDate>${rssDateFormat.format(Date(item.publishDate))}</pubDate>\n")
            }

            // Put full text content in <description> only - maximum compatibility
            val fullContent = item.fullTextContent ?: item.originalDescription ?: ""
            val htmlContent = formatContentAsHtml(fullContent)
            sb.append("      <description>${wrapCdata(htmlContent)}</description>\n")

            sb.append("    </item>\n")
        }

        sb.append("  </channel>\n")
        sb.append("</rss>\n")

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

        val lastBuildDate = rssDateFormat.format(Date(source.lastScanned.takeIf { it > 0 } ?: System.currentTimeMillis()))

        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8"?>""")
        sb.append("\n")
        sb.append("""<rss version="2.0">""")
        sb.append("\n")
        sb.append("  <channel>\n")
        sb.append("    <title>${escapeXml(source.name)}</title>\n")
        sb.append("    <link>file://${escapeXml(source.directoryPath)}</link>\n")
        sb.append("    <description>Feed generated from local directory: ${escapeXml(source.directoryPath)}</description>\n")
        sb.append("    <lastBuildDate>$lastBuildDate</lastBuildDate>\n")
        sb.append("    <generator>RSS Full Text Proxy - Directory Feed</generator>\n")

        for (item in items) {
            sb.append("    <item>\n")
            sb.append("      <title>${escapeXml(item.title)}</title>\n")
            sb.append("      <link>file://${escapeXml(item.filePath)}</link>\n")
            sb.append("      <guid isPermaLink=\"false\">${escapeXml(item.filePath)}</guid>\n")

            if (item.lastModified > 0) {
                sb.append("      <pubDate>${rssDateFormat.format(Date(item.lastModified))}</pubDate>\n")
            }

            // Put content in description only for maximum reader compatibility
            val content = item.textContent ?: "(Content extraction failed for this file)"
            val htmlContent = formatContentAsHtml(content)
            sb.append("      <description>${wrapCdata(htmlContent)}</description>\n")

            sb.append("    </item>\n")
        }

        sb.append("  </channel>\n")
        sb.append("</rss>\n")

        return sb.toString()
    }

    /**
     * Generate an OPML 1.0 file listing all available feeds for easy import.
     * Uses OPML 1.0 for maximum reader compatibility.
     */
    fun generateOpml(
        rssFeeds: List<RssFeedSource>,
        directoryFeeds: List<DirectoryFeedSource>,
        serverBaseUrl: String
    ): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8"?>""")
        sb.append("\n")
        sb.append("""<opml version="1.0">""")
        sb.append("\n")
        sb.append("  <head>\n")
        sb.append("    <title>RSS Full Text Proxy Feeds</title>\n")
        sb.append("  </head>\n")
        sb.append("  <body>\n")

        for (feed in rssFeeds) {
            val xmlUrl = "$serverBaseUrl/feed/${feed.outputSlug}"
            sb.append("""    <outline type="rss" text="${escapeXmlAttr(feed.name)}" title="${escapeXmlAttr(feed.name)}" xmlUrl="${escapeXmlAttr(xmlUrl)}" htmlUrl="${escapeXmlAttr(feed.sourceUrl)}"/>""")
            sb.append("\n")
        }
        for (feed in directoryFeeds) {
            val xmlUrl = "$serverBaseUrl/feed/${feed.outputSlug}"
            sb.append("""    <outline type="rss" text="${escapeXmlAttr(feed.name)}" title="${escapeXmlAttr(feed.name)}" xmlUrl="${escapeXmlAttr(xmlUrl)}"/>""")
            sb.append("\n")
        }

        sb.append("  </body>\n")
        sb.append("</opml>\n")

        return sb.toString()
    }

    /**
     * Wrap content in a CDATA section, properly escaping any occurrences of ]]> in the content.
     * The sequence ]]> is split into ]]]]><![CDATA[> to keep the XML valid.
     */
    private fun wrapCdata(content: String): String {
        val escaped = content.replace("]]>", "]]]]><![CDATA[>")
        return "<![CDATA[$escaped]]>"
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

    /**
     * Escape text for use in XML attribute values.
     */
    private fun escapeXmlAttr(text: String): String {
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
