package com.rssfeeder.feed

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.rssfeeder.data.model.Article
import com.rssfeeder.debug.DebugLogger
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.jsoup.Jsoup
import org.jsoup.safety.Safelist
import java.io.BufferedReader
import java.io.InputStreamReader

class LocalFeedScanner {

    fun scanFolder(
        context: Context,
        folderUri: Uri,
        feedId: Long,
        feedUrl: String
    ): List<Article> {
        DebugLogger.log("LocalFeedScanner", "Scanning folder: $folderUri")
        val articles = mutableListOf<Article>()

        try {
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(
                folderUri,
                DocumentsContract.getTreeDocumentId(folderUri)
            )

            val cursor = context.contentResolver.query(
                children, null, null, null, null
            )

            cursor?.use {
                while (it.moveToNext()) {
                    val docId = it.getString(
                        it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    )
                    val mimeType = it.getString(
                        it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    )
                    val displayName = it.getString(
                        it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    )
                    val lastModified = it.getLong(
                        it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                    )

                    val content = when {
                        mimeType.contains("text/plain") -> readTextFile(context, folderUri, docId)
                        mimeType.contains("text/html") -> readHtmlFile(context, folderUri, docId)
                        mimeType.contains("application/pdf") -> readPdfFile(context, folderUri, docId)
                        displayName.endsWith(".txt") -> readTextFile(context, folderUri, docId)
                        displayName.endsWith(".html") || displayName.endsWith(".htm") ->
                            readHtmlFile(context, folderUri, docId)
                        displayName.endsWith(".pdf") -> readPdfFile(context, folderUri, docId)
                        else -> null
                    }

                    if (content != null) {
                        articles.add(
                            Article(
                                feedId = feedId,
                                title = displayName,
                                link = "$feedUrl/$docId",
                                publishedDate = if (lastModified > 0) lastModified else System.currentTimeMillis(),
                                content = content
                            )
                        )
                        DebugLogger.verbose("LocalFeedScanner", "Scanned: $displayName")
                    }
                }
            }

            DebugLogger.log("LocalFeedScanner", "Scanned ${articles.size} files")
        } catch (e: Exception) {
            DebugLogger.log("LocalFeedScanner", "Error scanning folder: ${e.message}")
        }

        return articles
    }

    private fun readTextFile(context: Context, folderUri: Uri, docId: String): String? {
        return try {
            val uri = DocumentsContract.buildDocumentUriUsingTree(folderUri, docId)
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            BufferedReader(InputStreamReader(inputStream)).use { it.readText() }
        } catch (e: Exception) {
            DebugLogger.verbose("LocalFeedScanner", "Error reading text file: ${e.message}")
            null
        }
    }

    private fun readHtmlFile(context: Context, folderUri: Uri, docId: String): String? {
        return try {
            val html = readTextFile(context, folderUri, docId) ?: return null
            Jsoup.clean(html, Safelist.none()).trim()
        } catch (e: Exception) {
            DebugLogger.verbose("LocalFeedScanner", "Error reading HTML file: ${e.message}")
            null
        }
    }

    private fun readPdfFile(context: Context, folderUri: Uri, docId: String): String? {
        return try {
            PDFBoxResourceLoader.init(context)
            val uri = DocumentsContract.buildDocumentUriUsingTree(folderUri, docId)
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null

            inputStream.use { stream ->
                val document = PDDocument.load(stream)
                val stripper = PDFTextStripper()
                val text = stripper.getText(document)
                document.close()
                text.trim()
            }
        } catch (e: Exception) {
            DebugLogger.verbose("LocalFeedScanner", "Error reading PDF file: ${e.message}")
            null
        }
    }
}
