package com.example.rssfulltext.logging

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * DebugLogger singleton that writes timestamped lines to a plain text file in app storage.
 *
 * Two logging levels:
 * - log() : always writes (commands, errors, state transitions, auth events)
 * - verbose() : only writes when verboseEnabled is true (network requests, parsing details, etc.)
 *
 * Log format: "yyyy-MM-dd HH:mm:ss.SSS [Tag] message"
 * Verbose lines prefixed with [VERBOSE]
 *
 * Persistence: verboseEnabled flag stored in SharedPreferences.
 * Sharing: FileProvider-based share mechanism so user can share log without USB/ADB.
 */
object DebugLogger {

    private const val PREFS_NAME = "debug_logger_prefs"
    private const val KEY_VERBOSE_ENABLED = "verbose_enabled"
    private const val LOG_FILE_NAME = "app_debug.log"
    private const val LOG_DIR = "logs"

    @Volatile
    var verboseEnabled: Boolean = false
        private set

    private lateinit var logFile: File
    private lateinit var prefs: SharedPreferences
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        val appContext = context.applicationContext
        prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        verboseEnabled = prefs.getBoolean(KEY_VERBOSE_ENABLED, false)

        val logDir = File(appContext.filesDir, LOG_DIR)
        if (!logDir.exists()) logDir.mkdirs()
        logFile = File(logDir, LOG_FILE_NAME)

        initialized = true
        log("Logger", "DebugLogger initialized. Verbose=${verboseEnabled}")
    }

    fun setVerboseEnabled(enabled: Boolean) {
        verboseEnabled = enabled
        if (::prefs.isInitialized) {
            prefs.edit().putBoolean(KEY_VERBOSE_ENABLED, enabled).apply()
        }
        log("Logger", "Verbose logging ${if (enabled) "ENABLED" else "DISABLED"}")
    }

    /**
     * Always writes to log file.
     */
    fun log(tag: String, message: String) {
        writeLine("[$tag] $message")
    }

    /**
     * Only writes when verboseEnabled is true.
     */
    fun verbose(tag: String, message: String) {
        if (!verboseEnabled) return
        writeLine("[VERBOSE] [$tag] $message")
    }

    private fun writeLine(content: String) {
        if (!initialized) return
        try {
            val timestamp = dateFormat.format(Date())
            val line = "$timestamp $content"
            synchronized(this) {
                PrintWriter(FileWriter(logFile, true)).use { writer ->
                    writer.println(line)
                }
            }
        } catch (e: Exception) {
            // Silently fail - we can't log a logging failure
        }
    }

    fun getLogFile(): File? {
        return if (initialized && logFile.exists()) logFile else null
    }

    fun clearLog() {
        if (initialized && logFile.exists()) {
            logFile.writeText("")
            log("Logger", "Log cleared")
        }
    }

    /**
     * Creates a share intent for the log file via FileProvider.
     */
    fun createShareIntent(context: Context): Intent? {
        val file = getLogFile() ?: return null
        if (!file.exists() || file.length() == 0L) return null

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "RSS Full Text Debug Log")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
