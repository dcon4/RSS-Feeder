package com.rssfeeder

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.rssfeeder.debug.DebugLogger
import com.rssfeeder.worker.SyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class RssFeederApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        DebugLogger.initialize(this)

        appScope.launch {
            val verboseEnabled = DebugLogger.getVerboseEnabledFlow().first()
            DebugLogger.setVerboseEnabled(verboseEnabled)
            DebugLogger.log("RssFeederApp", "App started, verbose=$verboseEnabled")
        }

        schedulePeriodicSync()
    }

    private fun schedulePeriodicSync() {
        val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(
            30, TimeUnit.MINUTES
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "rss_feed_sync",
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )
    }
}
