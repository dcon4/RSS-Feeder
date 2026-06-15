package com.rssfeeder.feed

import com.rssfeeder.debug.DebugLogger
import org.jsoup.Jsoup
import org.jsoup.safety.Safelist

class FullTextExtractor {

    fun extractFullText(url: String): String? {
        DebugLogger.verbose("FullTextExtractor", "Extracting full text from: $url")
        try {
            val doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 RSS-Feeder")
                .timeout(15000)
                .followRedirects(true)
                .get()

            doc.select("script, style, nav, header, footer, iframe, .ad, .advertisement, .social-share, .comments").remove()

            val article = doc.select("article").first()
                ?: doc.select("[role=main]").first()
                ?: doc.select(".post-content").first()
                ?: doc.select(".entry-content").first()
                ?: doc.select(".content").first()
                ?: doc.body()

            if (article == null) {
                DebugLogger.verbose("FullTextExtractor", "No article element found, using body")
                val text = doc.body()?.text()?.trim()
                return if (text.isNullOrBlank()) null else text
            }

            val text = article.text().trim()
            DebugLogger.verbose("FullTextExtractor", "Extracted ${text.length} chars from $url")
            return text
        } catch (e: Exception) {
            DebugLogger.verbose("FullTextExtractor", "Failed to extract from $url: ${e.message}")
            return null
        }
    }

    fun convertHtmlToPlainText(html: String): String {
        return Jsoup.clean(html, Safelist.none()).trim()
    }
}
