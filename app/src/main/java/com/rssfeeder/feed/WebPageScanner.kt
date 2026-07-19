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

    private val excludeLinkText = setOf(
        "comments", "discuss", "reply", "permalink",
        "next", "previous", "prev", "first", "last", "newer", "older",
        "pages", "home", "more", "read more", "continue reading",
        "leave a comment", "leave a reply", "add comment", "rss feed",
        "subscribe", "follow"
    )

    private val excludeLinkPrefixes = listOf(
        "share on", "share via", "share to", "share this",
        "pin it", "tweet", "email this", "print",
        "leave a", "add a", "rss", "subscribe", "follow"
    )

    private val shareDomains = setOf(
        "facebook.com", "twitter.com", "x.com", "reddit.com",
        "pinterest.com", "linkedin.com", "tumblr.com",
        "instagram.com", "youtube.com", "youtu.be",
        "bsky.social", "threads.net", "telegram.org"
    )

    fun scanPage(pageUrl: String): List<ScannedLink> {
        DebugLogger.log("WebPageScanner", "Scanning page: $pageUrl")
        val doc = fetchDocument(pageUrl) ?: return emptyList()

        doc.select("nav, footer, .nav, .footer, .sidebar, .menu, .comments, script, style").remove()
        doc.select("a[href*=login], a[href*=register], a[href*=signup], a[href*=logout]").remove()
        doc.select("a[href^=javascript], a[href^=#]").remove()

        val pageDomain = try { URL(pageUrl).host } catch (e: Exception) { return emptyList() }
        val seenUrls = mutableSetOf<String>()
        val links = mutableListOf<ScannedLink>()

        for (a in doc.select("a[href]")) {
            val href = a.attr("abs:href")
            if (href.isBlank()) continue

            val cleanHref = href.substringBefore('#')
            if (cleanHref in seenUrls) continue
            if (cleanHref == pageUrl || cleanHref == pageUrl.trimEnd('/')) continue

            if (cleanHref.contains("?share=") || cleanHref.contains("&share=")) continue
            if (cleanHref.contains("?print=") || cleanHref.contains("&print=")) continue
            if (cleanHref.contains("?replytocom=") || cleanHref.contains("&replytocom=")) continue
            if (cleanHref.contains("?like=") || cleanHref.contains("&like=") ||
                cleanHref.contains("&vote=")) continue

            val linkDomain = try { URL(cleanHref).host } catch (e: Exception) { continue }
            val path = try { URL(cleanHref).path } catch (e: Exception) { continue }
            val linkText = a.text().trim()

            var isShareDomain = false
            for (domain in shareDomains) {
                if (linkDomain == domain || linkDomain.endsWith(".$domain")) {
                    isShareDomain = true
                    break
                }
            }
            if (isShareDomain) continue

            if (path.startsWith("/category/") || path.startsWith("/tag/") ||
                path.startsWith("/author/")) continue
            if (path.startsWith("/page/") || path == "/page") continue
            if (path.contains("/feed/") || path.endsWith("/rss") || path.endsWith("/atom")) continue

            val lower = linkText.lowercase()
            if (lower.length < 10) continue
            if (lower in excludeLinkText) continue
            if (excludeLinkText.any { lower == it || lower.startsWith("$it ") || lower.endsWith(" $it") }) continue
            if (excludeLinkPrefixes.any { lower.startsWith(it) }) continue

            val isCrossDomain = linkDomain != pageDomain
            val score = scoreLink(linkText, path, isCrossDomain)

            val minScore = if (linkText.length < 30) 3 else 2
            if (score >= minScore) {
                seenUrls.add(cleanHref)
                links.add(ScannedLink(url = cleanHref, title = linkText, score = score))
            }
        }

        val deduped = links
            .groupBy { try { URL(it.url).path } catch (e: Exception) { it.url } }
            .mapValues { (_, group) -> group.maxBy { l -> l.score } }
            .values
            .sortedByDescending { it.score }
            .take(20)
        DebugLogger.log("WebPageScanner", "Found ${deduped.size} article links from $pageUrl (${
            if (deduped.size < links.size) "${links.size - deduped.size} duplicates removed" else "no duplicates"
        })")
        return deduped
    }

    fun extractArticle(linkUrl: String, feedId: Long, pageTitle: String?): Article? {
        try {
            val fullContent = fullTextExtractor.extractFullText(linkUrl)

            val doc = fetchDocument(linkUrl)
            val title: String = if (pageTitle != null && pageTitle.length > 10) {
                pageTitle
            } else {
                doc?.title()?.takeIf { it.isNotBlank() }
                    ?: try { URL(linkUrl).path.substringAfterLast('/').substringBefore('.').replace('-', ' ').replace('_', ' ') } catch (e: Exception) { null }
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

    private fun scoreLink(linkText: String, path: String, isCrossDomain: Boolean): Int {
        var score = 0

        if (linkText.length >= 50) score += 3
        else if (linkText.length >= 35) score += 2
        else if (linkText.length >= 20) score += 1

        if (path.contains("/article") || path.contains("/articles")) score += 3
        if (path.contains("/news") || path.contains("/newswire")) score += 2
        if (path.contains("/story") || path.contains("/stories")) score += 2
        if (path.contains("/post") || path.contains("/posts")) score += 2
        if (path.contains("/blog")) score += 1

        if (Regex("""/\d{4}/\d{2}/\d{2}/""").containsMatchIn(path)) score += 3
        if (Regex("""/\d{4}/\d{2}/""").containsMatchIn(path)) score += 2

        if (isCrossDomain) score += 2

        return score
    }
}
