package com.rssfeeder.feed

import com.rssfeeder.data.model.Article
import com.rssfeeder.debug.DebugLogger
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URL

data class ScannedLink(
    val url: String,
    val title: String,
    val score: Int
)

class WebPageScanner {

    private val fullTextExtractor = FullTextExtractor()

    fun scanPage(pageUrl: String): List<ScannedLink> {
        DebugLogger.log("WebPageScanner", "Scanning page: $pageUrl")
        val doc = fetchDocument(pageUrl) ?: return emptyList()

        doc.select("nav, footer, .nav, .footer, .sidebar, .menu, .comments, script, style").remove()

        val domain = try { URL(pageUrl).host } catch (e: Exception) { return emptyList() }
        val seenUrls = mutableSetOf<String>()
        val links = mutableListOf<ScannedLink>()

        for (a in doc.select("a[href]")) {
            val href = a.attr("abs:href")
            if (href.isBlank()) continue

            try {
                val linkDomain = URL(href).host
                if (linkDomain != domain) continue
            } catch (e: Exception) { continue }

            val cleanHref = href.substringBefore('#')
            if (cleanHref in seenUrls) continue
            if (cleanHref == pageUrl || cleanHref == pageUrl.trimEnd('/')) continue

            val path = try { URL(cleanHref).path } catch (e: Exception) { continue }
            val linkText = a.text().trim()
            if (linkText.length < 10) continue

            val score = scoreLink(linkText, path)

            if (score >= 2) {
                seenUrls.add(cleanHref)
                links.add(ScannedLink(url = cleanHref, title = linkText, score = score))
            }
        }

        val sorted = links.sortedByDescending { it.score }.take(20)
        DebugLogger.log("WebPageScanner", "Found ${sorted.size} article links from $pageUrl")
        return sorted
    }

    fun extractArticle(linkUrl: String, feedId: Long, pageTitle: String?): Article? {
        try {
            val fullContent = fullTextExtractor.extractFullText(linkUrl)

            val doc = fetchDocument(linkUrl)
            val title: String = if (pageTitle != null && pageTitle.length > 10) {
                pageTitle
            } else {
                doc?.title()?.takeIf { it.isNotBlank() }
                    ?: try { URL(linkUrl).path.substringAfterLast('/').substringBefore('.').replace('-', ' ').replace('_', ' ') }
                    ?: "Untitled"
            }

            return Article(
                feedId = feedId,
                title = title.take(200),
                link = linkUrl,
                content = fullContent,
                summary = fullContent?.take(500),
                publishedDate = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            DebugLogger.verbose("WebPageScanner", "Failed to extract article from $linkUrl: ${e.message}")
            return null
        }
    }

    fun detectPageTitle(pageUrl: String): String? {
        val doc = fetchDocument(pageUrl) ?: return null
        return doc.title().takeIf { it.isNotBlank() }
    }

    private fun fetchDocument(url: String): Document? {
        return try {
            Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 RSS-Feeder")
                .timeout(15000)
                .followRedirects(true)
                .get()
        } catch (e: Exception) {
            DebugLogger.verbose("WebPageScanner", "Failed to fetch $url: ${e.message}")
            null
        }
    }

    private fun scoreLink(linkText: String, path: String): Int {
        var score = 0

        if (linkText.length >= 40) score += 3
        else if (linkText.length >= 25) score += 2
        else if (linkText.length >= 15) score += 1

        if (path.contains("/article") || path.contains("/articles")) score += 3
        if (path.contains("/news") || path.contains("/newswire")) score += 2
        if (path.contains("/story") || path.contains("/stories")) score += 2
        if (path.contains("/post") || path.contains("/posts")) score += 2
        if (path.contains("/blog")) score += 1

        if (Regex("""/\d{4}/\d{2}/\d{2}/""").containsMatchIn(path)) score += 3
        if (Regex("""/\d{4}/\d{2}/""").containsMatchIn(path)) score += 2

        return score
    }
}
