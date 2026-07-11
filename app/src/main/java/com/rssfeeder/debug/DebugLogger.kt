package com.rssfeeder.debug

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal val Context.dataStore by preferencesDataStore(name = "debug_settings")

object DebugLogger {

    private const val TAG = "RSS-Feeder"
    private const val LOG_DIR = "logs"

    private val fileNameFormat = SimpleDateFormat("yyyy-MM-dd-HH-mm", Locale.US)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    var verboseEnabled: Boolean = false
        private set

    private var logFile: File? = null
    private var context: Context? = null

    fun initialize(appContext: Context) {
        context = appContext
        val timestamp = fileNameFormat.format(Date())
        logFile = File(appContext.cacheDir, "$LOG_DIR/rssfeeder-debug.log.$timestamp.txt")
        logFile?.parentFile?.mkdirs()
        Log.i(TAG, "DebugLogger initialized, log file: ${logFile?.absolutePath}")
        val buildNumber = try {
            appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionCode.toString()
        } catch (e: Exception) { "unknown" }
        log(TAG, "Logger initialized (build $buildNumber)")
    }

    fun setVerboseEnabled(enabled: Boolean) {
        verboseEnabled = enabled
        log(TAG, "Verbose logging set to $enabled")
    }

    fun log(tag: String, message: String) {
        Log.d(tag, message)
        writeToFile("[$tag] $message")
    }

    fun verbose(tag: String, message: String) {
        if (!verboseEnabled) return
        Log.v(tag, message)
        writeToFile("[VERBOSE][$tag] $message")
    }

    private fun writeToFile(line: String) {
        val file = logFile ?: return
        try {
            file.appendText(
                "${dateFormat.format(Date())} $line\n"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write log", e)
        }
    }

    fun getLogFile(): File? = logFile

    fun getLogFileUri(): String? {
        val file = logFile ?: return null
        if (!file.exists()) return null
        return file.absolutePath
    }

    fun getVerboseEnabledFlow(): Flow<Boolean> {
        val ctx = context ?: return emptyFlow()
        return ctx.dataStore.data.map { prefs ->
            prefs[VERBOSE_KEY] ?: false
        }
    }

    suspend fun persistVerboseEnabled(context: Context, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[VERBOSE_KEY] = enabled
        }
        verboseEnabled = enabled
    }

    private val VERBOSE_KEY = booleanPreferencesKey("verbose_logging_enabled")

    private val GITHUB_PAT_KEY = stringPreferencesKey("github_pat")

    fun getGithubPatFlow(): Flow<String> {
        val ctx = context ?: return emptyFlow()
        return ctx.dataStore.data.map { prefs ->
            prefs[GITHUB_PAT_KEY] ?: ""
        }
    }

    suspend fun persistGithubPat(context: Context, pat: String) {
        context.dataStore.edit { prefs ->
            prefs[GITHUB_PAT_KEY] = pat
        }
    }

    private val PUSH_INTERVAL_KEY = intPreferencesKey("push_interval_minutes")

    fun getPushIntervalFlow(): Flow<Int> {
        val ctx = context ?: return emptyFlow()
        return ctx.dataStore.data.map { prefs ->
            prefs[PUSH_INTERVAL_KEY] ?: 0
        }
    }

    suspend fun persistPushInterval(context: Context, minutes: Int) {
        context.dataStore.edit { prefs ->
            prefs[PUSH_INTERVAL_KEY] = minutes
        }
    }
}
