package com.example.rssfulltext.extraction

import com.example.rssfulltext.logging.DebugLogger
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.concurrent.TimeUnit

/**
 * Extracts full-text content from article URLs using multiple fallback methods:
 * 1. Jsoup content extraction (heuristic scoring of content blocks)
 * 2. Readability-style extraction (largest text block detection)
 * 3. Raw HTML cleaning (strip nav/ads/scripts, keep all remaining text)
 */
class FullTextExtractor {

    companion object {
        private const val TAG = "FullTextExtractor"
        private const val MIN_CONTENT_LENGTH = 100

        // Elements unlikely to be main content
        private val UNLIKELY_CANDIDATES = Regex(
            "combx|comment|community|disqus|extra|foot|header|menu|" +
                    "remark|rss|shoutbox|sidebar|sponsor|ad-break|agegate|" +
                    "pagination|pager|popup|tweet|twitter|share|social|" +
                    "related|newsletter|nav|cookie|banner",
            RegexOption.IGNORE_CASE
        )

        // Elements likely to be main content
        private val POSITIVE_CANDIDATES = Regex(
            "article|body|content|entry|hentry|main|page|post|text|blog|story",
            RegexOption.IGNORE_CASE
        )

        private val NEGATIVE_CANDIDATES = Regex(
            "hidden|banner|combx|comment|com-|contact|foot|footer|" +
                    "footnote|masthead|media|meta|outbrain|promo|related|" +
                    "scroll|shoutbox|sidebar|sponsor|shopping|tags|tool|widget",
            RegexOption.IGNORE_CASE
        )
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    data class ExtractionResult(
        val content: String,
        val method: String
    )

    /**
     * Attempts to extract full-text content from the given URL using
     * multiple methods in order of quality.
     */
    fun extract(url: String): ExtractionResult? {
        DebugLogger.log(TAG, "Extracting full text from: $url")

        val html = fetchHtml(url)
        if (html == null) {
            DebugLogger.log(TAG, "Failed to fetch HTML for: $url")
            return null
        }

        val document = Jsoup.parse(html, url)

        // Method 1: Structured content extraction (article tags, etc.)
        val structured = extractStructured(document)
        if (structured != null && structured.length >= MIN_CONTENT_LENGTH) {
            DebugLogger.verbose(TAG, "Method 1 (structured) succeeded: ${structured.length} chars")
            return ExtractionResult(structured, "structured")
        }

        // Method 2: Readability-style scored extraction
        val scored = extractScored(document)
        if (scored != null && scored.length >= MIN_CONTENT_LENGTH) {
            DebugLogger.verbose(TAG, "Method 2 (scored) succeeded: ${scored.length} chars")
            return ExtractionResult(scored, "scored")
        }

        // Method 3: Largest text block heuristic
        val largest = extractLargestBlock(document)
        if (largest != null && largest.length >= MIN_CONTENT_LENGTH) {
            DebugLogger.verbose(TAG, "Method 3 (largest-block) succeeded: ${largest.length} chars")
            return ExtractionResult(largest, "largest-block")
        }

        // Method 4: Raw cleaned extraction (last resort)
        val raw = extractRawCleaned(document)
        if (raw != null && raw.length >= MIN_CONTENT_LENGTH) {
            DebugLogger.verbose(TAG, "Method 4 (raw-cleaned) succeeded: ${raw.length} chars")
            return ExtractionResult(raw, "raw-cleaned")
        }

        DebugLogger.log(TAG, "All extraction methods failed for: $url")
        return null
    }

    private fun fetchHtml(url: String): String? {
        return try {
            DebugLogger.verbose(TAG, "Fetching: $url")
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Charset", "utf-8")
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                // Read raw bytes and detect encoding from content-type or HTML meta tag
                val bytes = response.body?.bytes() ?: return null
                val contentType = response.header("Content-Type") ?: ""

                // Try to determine charset from HTTP header
                val headerCharset = Regex("charset=([\\w-]+)", RegexOption.IGNORE_CASE)
                    .find(contentType)?.groupValues?.get(1)

                // If header says UTF-8 or doesn't specify, use UTF-8
                // Otherwise, try to detect from the first bytes
                val charset = when {
                    headerCharset != null && headerCharset.equals("utf-8", ignoreCase = true) -> Charsets.UTF_8
                    headerCharset != null -> try {
                        java.nio.charset.Charset.forName(headerCharset)
                    } catch (_: Exception) {
                        Charsets.UTF_8
                    }
                    else -> Charsets.UTF_8
                }

                val html = String(bytes, charset)

                // Check if the HTML meta tag declares a different charset
                val metaCharsetMatch = Regex("""<meta[^>]+charset="?([^";\s>]+)""", RegexOption.IGNORE_CASE)
                    .find(html.take(2048))
                if (metaCharsetMatch != null) {
                    val metaCharset = metaCharsetMatch.groupValues[1]
                    if (!metaCharset.equals(charset.name(), ignoreCase = true) &&
                        metaCharset.equals("utf-8", ignoreCase = true)) {
                        // Re-decode as UTF-8 if meta says UTF-8 but header didn't
                        return String(bytes, Charsets.UTF_8)
                    }
                }

                html
            } else {
                DebugLogger.log(TAG, "HTTP ${response.code} for $url")
                null
            }
        } catch (e: Exception) {
            DebugLogger.log(TAG, "Fetch error for $url: ${e.message}")
            null
        }
    }

    /**
     * Method 1: Look for semantic HTML5 elements (article, main) or
     * well-known class/id patterns for content.
     */
    private fun extractStructured(doc: Document): String? {
        // Try <article> tag first
        val article = doc.selectFirst("article")
        if (article != null) {
            val text = cleanElement(article)
            if (text.length >= MIN_CONTENT_LENGTH) return text
        }

        // Try <main> tag
        val main = doc.selectFirst("main")
        if (main != null) {
            val text = cleanElement(main)
            if (text.length >= MIN_CONTENT_LENGTH) return text
        }

        // Try common content selectors
        val selectors = listOf(
            "[itemprop=articleBody]",
            ".post-content",
            ".entry-content",
            ".article-content",
            ".article-body",
            ".story-body",
            "#article-body",
            ".post-body",
            ".content-body"
        )

        for (selector in selectors) {
            val element = doc.selectFirst(selector)
            if (element != null) {
                val text = cleanElement(element)
                if (text.length >= MIN_CONTENT_LENGTH) return text
            }
        }

        return null
    }

    /**
     * Method 2: Readability-style scoring algorithm.
     * Scores each paragraph's parent based on text density and class/id hints.
     */
    private fun extractScored(doc: Document): String? {
        // Remove unlikely candidates
        doc.select("script, style, noscript, iframe, form").remove()

        val candidates = mutableMapOf<Element, Double>()
        val paragraphs = doc.select("p, pre, td")

        for (p in paragraphs) {
            val parent = p.parent() ?: continue
            val grandParent = parent.parent()

            val text = p.text()
            if (text.length < 25) continue

            // Initialize parent score
            if (!candidates.containsKey(parent)) {
                candidates[parent] = scoreElement(parent)
            }
            if (grandParent != null && !candidates.containsKey(grandParent)) {
                candidates[grandParent] = scoreElement(grandParent)
            }

            // Score based on content
            var contentScore = 1.0
            contentScore += text.split(",").size.coerceAtMost(10)
            contentScore += (text.length / 100.0).coerceAtMost(3.0)

            candidates[parent] = (candidates[parent] ?: 0.0) + contentScore
            if (grandParent != null) {
                candidates[grandParent] = (candidates[grandParent] ?: 0.0) + contentScore / 2.0
            }
        }

        if (candidates.isEmpty()) return null

        val topCandidate = candidates.maxByOrNull { it.value }?.key ?: return null
        return cleanElement(topCandidate)
    }

    /**
     * Method 3: Find the div/section with the most text content.
     */
    private fun extractLargestBlock(doc: Document): String? {
        val blocks = doc.select("div, section, article")
        var bestBlock: Element? = null
        var bestLength = 0

        for (block in blocks) {
            // Skip blocks with unlikely class/id
            val classId = "${block.className()} ${block.id()}"
            if (UNLIKELY_CANDIDATES.containsMatchIn(classId)) continue

            val textLength = block.text().length
            val linkDensity = getLinkDensity(block)

            // Good candidate: lots of text, low link density
            if (textLength > bestLength && linkDensity < 0.3) {
                bestBlock = block
                bestLength = textLength
            }
        }

        return bestBlock?.let { cleanElement(it) }
    }

    /**
     * Method 4: Strip all non-content elements and return whatever remains.
     */
    private fun extractRawCleaned(doc: Document): String? {
        val body = doc.body() ?: return null

        // Remove all non-content elements
        body.select(
            "script, style, noscript, iframe, form, nav, header, footer, " +
                    "aside, .sidebar, .menu, .nav, .navigation, .comment, .comments, " +
                    ".ad, .ads, .advertisement, .social, .share, .related, " +
                    "#sidebar, #nav, #menu, #footer, #header, #comments"
        ).remove()

        val text = body.text()
        return if (text.length >= MIN_CONTENT_LENGTH) {
            // Convert to simple HTML paragraphs
            val paragraphs = body.select("p")
            if (paragraphs.isNotEmpty()) {
                paragraphs.joinToString("\n\n") { it.text() }
            } else {
                text
            }
        } else null
    }

    private fun scoreElement(element: Element): Double {
        var score = 0.0
        val classId = "${element.className()} ${element.id()}"

        if (NEGATIVE_CANDIDATES.containsMatchIn(classId)) score -= 25
        if (POSITIVE_CANDIDATES.containsMatchIn(classId)) score += 25

        return score
    }

    private fun getLinkDensity(element: Element): Double {
        val textLength = element.text().length
        if (textLength == 0) return 1.0
        val linkLength = element.select("a").sumOf { it.text().length }
        return linkLength.toDouble() / textLength.toDouble()
    }

    private fun cleanElement(element: Element): String {
        // Remove unwanted child elements
        element.select(
            "script, style, noscript, iframe, form, .share, .social, " +
                    ".comments, .comment, .ad, .ads, nav, .nav, .related"
        ).remove()

        // Get text content preserving paragraph structure
        val paragraphs = element.select("p, h1, h2, h3, h4, h5, h6, li, blockquote, pre")
        return if (paragraphs.isNotEmpty()) {
            paragraphs.joinToString("\n\n") { para ->
                when (para.tagName()) {
                    "h1", "h2", "h3", "h4", "h5", "h6" -> "## ${para.text()}"
                    "li" -> "- ${para.text()}"
                    "blockquote" -> "> ${para.text()}"
                    "pre" -> "```\n${para.text()}\n```"
                    else -> para.text()
                }
            }.trim()
        } else {
            element.text().trim()
        }
    }
}
