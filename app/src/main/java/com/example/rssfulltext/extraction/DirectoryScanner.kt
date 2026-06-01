package com.example.rssfulltext.extraction

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.example.rssfulltext.data.db.AppDatabase
import com.example.rssfulltext.data.model.DirectoryFeedItem
import com.example.rssfulltext.data.model.DirectoryFeedSource
import com.example.rssfulltext.logging.DebugLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

/**
 * Scans local directories and extracts text content from supported files
 * (txt, html, epub, pdf) to create RSS feed items.
 */
class DirectoryScanner(
    private val database: AppDatabase,
    private val context: Context
) {

    companion object {
        private const val TAG = "DirectoryScanner"
        private val SUPPORTED_EXTENSIONS = setOf("txt", "html", "htm", "epub", "pdf")
        private const val MAX_CONTENT_LENGTH = 500_000 // ~500KB of text per item
    }

    private val directoryFeedDao = database.directoryFeedDao()

    /**
     * Scan a directory source for supported files, extract text, and persist items.
     * Supports both regular file paths and content:// URIs (SAF).
     * @return number of new/updated items
     */
    suspend fun scanDirectory(sourceId: Long): Int = withContext(Dispatchers.IO) {
        val source = directoryFeedDao.getDirectorySourceById(sourceId) ?: run {
            DebugLogger.log(TAG, "Directory source $sourceId not found")
            return@withContext -1
        }

        DebugLogger.log(TAG, "Scanning directory: ${source.directoryPath}")

        val path = source.directoryPath
        return@withContext if (path.startsWith("content://")) {
            scanContentUri(source)
        } else {
            scanFilePath(source)
        }
    }

    private suspend fun scanFilePath(source: DirectoryFeedSource): Int {
        val dir = File(source.directoryPath)
        if (!dir.exists() || !dir.isDirectory) {
            val error = "Directory does not exist or is not accessible: ${source.directoryPath}"
            DebugLogger.log(TAG, error)
            directoryFeedDao.updateDirectorySource(source.copy(lastError = error))
            return -1
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
                val existing = directoryFeedDao.getItemByPath(file.absolutePath, source.id)

                // Skip if file hasn't changed
                if (existing != null && existing.lastModified == file.lastModified()) {
                    DebugLogger.verbose(TAG, "Skipping unchanged file: ${file.name}")
                    continue
                }

                val content = extractTextFromFile(file)

                val item = DirectoryFeedItem(
                    id = existing?.id ?: 0,
                    directorySourceId = source.id,
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
        val itemCount = directoryFeedDao.getItemCountForDirectory(source.id)
        directoryFeedDao.updateDirectorySource(
            source.copy(
                lastScanned = System.currentTimeMillis(),
                itemCount = itemCount,
                lastError = null
            )
        )

        DebugLogger.log(TAG, "Directory scan complete: $processedCount new/updated items, $itemCount total")
        return processedCount
    }

    private suspend fun scanContentUri(source: DirectoryFeedSource): Int {
        val uri = Uri.parse(source.directoryPath)
        val rootDoc = DocumentFile.fromTreeUri(context, uri)
        if (rootDoc == null || !rootDoc.exists()) {
            val error = "Content URI is not accessible: ${source.directoryPath}"
            DebugLogger.log(TAG, error)
            directoryFeedDao.updateDirectorySource(source.copy(lastError = error))
            return -1
        }

        val docFiles = mutableListOf<DocumentFile>()
        collectDocumentFiles(rootDoc, docFiles, source.includeSubdirectories)

        DebugLogger.log(TAG, "Found ${docFiles.size} supported files via SAF in ${source.directoryPath}")

        var processedCount = 0

        for (docFile in docFiles) {
            try {
                val filePath = docFile.uri.toString()
                val fileName = docFile.name ?: "unknown"
                val extension = fileName.substringAfterLast('.', "").lowercase()
                val lastModified = docFile.lastModified()
                val fileSize = docFile.length()

                val existing = directoryFeedDao.getItemByPath(filePath, source.id)

                // Skip if file hasn't changed
                if (existing != null && existing.lastModified == lastModified) {
                    DebugLogger.verbose(TAG, "Skipping unchanged file: $fileName")
                    continue
                }

                val content = extractTextFromDocumentFile(docFile, extension)

                val item = DirectoryFeedItem(
                    id = existing?.id ?: 0,
                    directorySourceId = source.id,
                    title = fileName.substringBeforeLast('.'),
                    filePath = filePath,
                    fileType = extension,
                    textContent = content?.take(MAX_CONTENT_LENGTH),
                    fileSize = fileSize,
                    lastModified = lastModified
                )

                directoryFeedDao.insertDirectoryItem(item)
                processedCount++
                DebugLogger.verbose(TAG, "Processed file: $fileName ($extension)")

            } catch (e: Exception) {
                DebugLogger.log(TAG, "Error processing SAF file ${docFile.name}: ${e.message}")
            }
        }

        // Update source metadata
        val itemCount = directoryFeedDao.getItemCountForDirectory(source.id)
        directoryFeedDao.updateDirectorySource(
            source.copy(
                lastScanned = System.currentTimeMillis(),
                itemCount = itemCount,
                lastError = null
            )
        )

        DebugLogger.log(TAG, "SAF directory scan complete: $processedCount new/updated items, $itemCount total")
        return processedCount
    }

    private fun collectDocumentFiles(
        dir: DocumentFile,
        result: MutableList<DocumentFile>,
        includeSubdirs: Boolean
    ) {
        for (file in dir.listFiles()) {
            if (file.isDirectory && includeSubdirs) {
                collectDocumentFiles(file, result, true)
            } else if (file.isFile) {
                val name = file.name ?: continue
                val ext = name.substringAfterLast('.', "").lowercase()
                if (ext in SUPPORTED_EXTENSIONS) {
                    result.add(file)
                }
            }
        }
    }

    private fun extractTextFromDocumentFile(docFile: DocumentFile, extension: String): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(docFile.uri) ?: return null
            when (extension) {
                "txt" -> extractFromInputStream(inputStream)
                "html", "htm" -> extractHtmlFromInputStream(inputStream)
                "epub" -> extractEpubFromInputStream(inputStream)
                "pdf" -> extractPdfFromInputStream(inputStream)
                else -> {
                    inputStream.close()
                    null
                }
            }
        } catch (e: Exception) {
            DebugLogger.log(TAG, "Error extracting text from SAF file ${docFile.name}: ${e.message}")
            null
        }
    }

    private fun extractFromInputStream(inputStream: InputStream): String? {
        return try {
            val text = inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            text
        } catch (e: Exception) {
            DebugLogger.log(TAG, "Error reading text stream: ${e.message}")
            null
        }
    }

    private fun extractHtmlFromInputStream(inputStream: InputStream): String? {
        return try {
            val html = inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val doc = org.jsoup.Jsoup.parse(html)
            doc.select("script, style, nav, header, footer").remove()
            doc.body()?.text()
        } catch (e: Exception) {
            DebugLogger.log(TAG, "Error reading html stream: ${e.message}")
            null
        }
    }

    private fun extractEpubFromInputStream(inputStream: InputStream): String? {
        return try {
            val sb = StringBuilder()
            val zipInput = java.util.zip.ZipInputStream(inputStream)
            var entry = zipInput.nextEntry

            while (entry != null) {
                val name = entry.name.lowercase()
                if ((name.endsWith(".xhtml") || name.endsWith(".html") || name.endsWith(".htm"))
                    && !name.contains("toc") && !name.contains("nav")
                ) {
                    val bytes = zipInput.readBytes()
                    val html = String(bytes, Charsets.UTF_8)
                    val doc = org.jsoup.Jsoup.parse(html)
                    doc.select("script, style, nav, header, footer").remove()
                    val text = doc.body()?.text() ?: ""
                    if (text.isNotBlank()) {
                        sb.appendLine(text)
                        sb.appendLine()
                    }
                }

                if (sb.length > MAX_CONTENT_LENGTH) break
                entry = zipInput.nextEntry
            }

            zipInput.close()
            val result = sb.toString().trim()
            if (result.isNotEmpty()) result else null
        } catch (e: Exception) {
            DebugLogger.log(TAG, "Error reading epub stream: ${e.message}")
            null
        }
    }

    private fun extractPdfFromInputStream(inputStream: InputStream): String? {
        return try {
            val document = com.tom_roush.pdfbox.pdmodel.PDDocument.load(inputStream)
            val stripper = com.tom_roush.pdfbox.text.PDFTextStripper()
            val text = stripper.getText(document)
            document.close()
            if (text.isNotBlank()) text.trim() else null
        } catch (e: Exception) {
            DebugLogger.log(TAG, "Error reading pdf stream: ${e.message}")
            null
        }
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
            val sb = StringBuilder()
            val zipInput = java.util.zip.ZipInputStream(java.io.FileInputStream(file))
            var entry = zipInput.nextEntry

            while (entry != null) {
                val name = entry.name.lowercase()
                if ((name.endsWith(".xhtml") || name.endsWith(".html") || name.endsWith(".htm"))
                    && !name.contains("toc") && !name.contains("nav")
                ) {
                    val bytes = zipInput.readBytes()
                    val html = String(bytes, Charsets.UTF_8)
                    val doc = org.jsoup.Jsoup.parse(html)
                    doc.select("script, style, nav, header, footer").remove()
                    val text = doc.body()?.text() ?: ""
                    if (text.isNotBlank()) {
                        sb.appendLine(text)
                        sb.appendLine()
                    }
                }

                // Stop if we've extracted enough
                if (sb.length > MAX_CONTENT_LENGTH) break
                entry = zipInput.nextEntry
            }

            zipInput.close()
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
