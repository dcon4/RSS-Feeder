package com.rssfeeder.feed

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.rssfeeder.data.model.Article
import com.rssfeeder.debug.DebugLogger
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ArticleExporter {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val exportDateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)

    fun exportArticle(
        context: Context,
        article: Article,
        folderUri: Uri
    ): Boolean {
        return try {
            val content = buildArticleText(article)
            val baseName = sanitizeFilename(article.title)
            val parent = DocumentFile.fromTreeUri(context, folderUri) ?: return false
            val file = resolveUniqueFile(parent, baseName, ".txt") ?: return false
            context.contentResolver.openOutputStream(file.uri)?.use { os ->
                os.write(content.toByteArray(Charsets.UTF_8))
            } ?: return false
            DebugLogger.verbose("ArticleExporter", "Exported: ${file.name}")
            true
        } catch (e: Exception) {
            DebugLogger.log("ArticleExporter", "Export failed for '${article.title}': ${e.message}")
            false
        }
    }

    private fun resolveUniqueFile(parent: DocumentFile, baseName: String, ext: String): DocumentFile? {
        val filename = "$baseName$ext"
        if (parent.findFile(filename) == null) {
            return parent.createFile("text/plain", filename)
        }
        var counter = 1
        while (true) {
            val numbered = "${baseName} ($counter)$ext"
            if (parent.findFile(numbered) == null) {
                return parent.createFile("text/plain", numbered)
            }
            counter++
        }
    }

    fun exportNewArticles(
        context: Context,
        articles: List<Article>,
        folderUri: Uri,
        lastExported: Long
    ): Int {
        val newArticles = articles.filter { it.publishedDate > lastExported || lastExported == 0L }
        if (newArticles.isEmpty()) return 0
        var count = 0
        for (article in newArticles) {
            if (exportArticle(context, article, folderUri)) count++
        }
        return count
    }

    private fun buildArticleText(article: Article): String {
        val sb = StringBuilder()
        sb.appendLine(article.title)
        if (!article.author.isNullOrBlank()) {
            sb.appendLine("By: ${article.author}")
        }
        if (article.publishedDate > 0) {
            sb.appendLine("Published: ${dateFormat.format(Date(article.publishedDate))}")
        }
        sb.appendLine("Link: ${article.link}")
        sb.appendLine()
        val body = article.content?.takeIf { it.isNotBlank() } ?: article.summary ?: ""
        sb.append(body)
        sb.appendLine()
        return sb.toString()
    }

    private fun sanitizeFilename(name: String): String {
        val cleaned = name.replace(Regex("[:\\\\/*?\"<>|]"), "_")
            .replace(Regex("\\s+"), " ")
            .trim()
        return if (cleaned.length > 100) cleaned.take(100) else cleaned
    }
}
