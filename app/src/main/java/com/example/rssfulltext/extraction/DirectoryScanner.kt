package com.example.rssfulltext.extraction

import com.example.rssfulltext.data.db.AppDatabase
import com.example.rssfulltext.data.model.DirectoryFeedItem
import com.example.rssfulltext.data.model.DirectoryFeedSource
import com.example.rssfulltext.logging.DebugLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Scans local directories and extracts text content from supported files
 * (txt, html, epub, pdf) to create RSS feed items.
 */
class DirectoryScanner(private val database: AppDatabase) {

    companion object {
        private const val TAG = "DirectoryScanner"
        private val SUPPORTED_EXTENSIONS = setOf("txt", "html", "htm", "epub", "pdf")
        private const val MAX_CONTENT_LENGTH = 500_000 // ~500KB of text per item
    }

    private val directoryFeedDao = database.directoryFeedDao()

    /**
     * Scan a directory source for supported files, extract text, and persist items.
     * @return number of new/updated items
     */
    suspend fun scanDirectory(sourceId: Long): Int = withContext(Dispatchers.IO) {
        val source = directoryFeedDao.getDirectorySourceById(sourceId) ?: run {
            DebugLogger.log(TAG, "Directory source $sourceId not found")
            return@withContext -1
        }

        DebugLogger.log(TAG, "Scanning directory: ${source.directoryPath}")

        val dir = File(source.directoryPath)
        if (!dir.exists() || !dir.isDirectory) {
            val error = "Directory does not exist or is not accessible: ${source.directoryPath}"
            DebugLogger.log(TAG, error)
            directoryFeedDao.updateDirectorySource(source.copy(lastError = error))
            return@withContext -1
        }

        val files = if (source.includeSubdirectories) {
            dir.walkTopDown()
                .filter { it.isFile && it.extension.lowercase() in SUPPORTED_EXTENSIONS }
                .toList()
        } else {
            dir.listFiles()
                ?.filter { it.isFile && it.extension.lowercase() in SUPPORTED_EXTENSIONS }
                ?: emptyList()
        }

        DebugLogger.log(TAG, "Found ${files.size} supported files in ${source.directoryPath}")

        var processedCount = 0

        for (file in files) {
            try {
                val existing = directoryFeedDao.getItemByPath(file.absolutePath, sourceId)

                // Skip if file hasn't changed
                if (existing != null && existing.lastModified == file.lastModified()) {
                    DebugLogger.verbose(TAG, "Skipping unchanged file: ${file.name}")
                    continue
                }

                val content = extractTextFromFile(file)

                val item = DirectoryFeedItem(
                    id = existing?.id ?: 0,
                    directorySourceId = sourceId,
                    title = file.nameWithoutExtension,
                    filePath = file.absolutePath,
                    fileType = file.extension.lowercase(),
                    textContent = content?.take(MAX_CONTENT_LENGTH),
                    fileSize = file.length(),
                    lastModified = file.lastModified()
                )

                directoryFeedDao.insertDirectoryItem(item)
                processedCount++
                DebugLogger.verbose(TAG, "Processed file: ${file.name} (${file.extension})")

            } catch (e: Exception) {
                DebugLogger.log(TAG, "Error processing file ${file.name}: ${e.message}")
            }
        }

        // Update source metadata
        val itemCount = directoryFeedDao.getItemCountForDirectory(sourceId)
        directoryFeedDao.updateDirectorySource(
            source.copy(
                lastScanned = System.currentTimeMillis(),
                itemCount = itemCount,
                lastError = null
            )
        )

        DebugLogger.log(TAG, "Directory scan complete: $processedCount new/updated items, $itemCount total")
        return@withContext processedCount
    }

    private fun extractTextFromFile(file: File): String? {
        return when (file.extension.lowercase()) {
            "txt" -> extractFromTxt(file)
            "html", "htm" -> extractFromHtml(file)
            "epub" -> extractFromEpub(file)
            "pdf" -> extractFromPdf(file)
            else -> null
        }
    }

    private fun extractFromTxt(file: File): String? {
        return try {
            file.readText(Charsets.UTF_8)
        } catch (e: Exception) {
            DebugLogger.log(TAG, "Error reading txt file ${file.name}: ${e.message}")
            null
        }
    }

    private fun extractFromHtml(file: File): String? {
        return try {
            val html = file.readText(Charsets.UTF_8)
            val doc = org.jsoup.Jsoup.parse(html)
            doc.select("script, style, nav, header, footer").remove()
            doc.body()?.text()
        } catch (e: Exception) {
            DebugLogger.log(TAG, "Error reading html file ${file.name}: ${e.message}")
            null
        }
    }

    private fun extractFromEpub(file: File): String? {
        return try {
            val epubInputStream = java.io.FileInputStream(file)
            val book = nl.siegmann.epublib.epub.EpubReader().readEpub(epubInputStream)
            val sb = StringBuilder()

            for (resource in book.contents) {
                if (resource.mediaType?.name?.contains("html") == true ||
                    resource.mediaType?.name?.contains("xhtml") == true
                ) {
                    val html = String(resource.data, Charsets.UTF_8)
                    val doc = org.jsoup.Jsoup.parse(html)
                    val text = doc.body()?.text() ?: ""
                    if (text.isNotBlank()) {
                        sb.appendLine(text)
                        sb.appendLine()
                    }
                }

                // Stop if we've extracted enough
                if (sb.length > MAX_CONTENT_LENGTH) break
            }

            epubInputStream.close()
            val result = sb.toString().trim()
            if (result.isNotEmpty()) result else null
        } catch (e: Exception) {
            DebugLogger.log(TAG, "Error reading epub file ${file.name}: ${e.message}")
            null
        }
    }

    private fun extractFromPdf(file: File): String? {
        return try {
            val document = com.tom_roush.pdfbox.pdmodel.PDDocument.load(file)
            val stripper = com.tom_roush.pdfbox.text.PDFTextStripper()
            val text = stripper.getText(document)
            document.close()
            if (text.isNotBlank()) text.trim() else null
        } catch (e: Exception) {
            DebugLogger.log(TAG, "Error reading pdf file ${file.name}: ${e.message}")
            null
        }
    }
}
