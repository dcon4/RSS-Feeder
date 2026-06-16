package com.rssfeeder

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.rssfeeder.debug.DebugLogger
import com.rssfeeder.server.ServerService
import com.rssfeeder.server.ServerViewModel
import com.rssfeeder.ui.addfeed.AddFeedScreen
import com.rssfeeder.ui.feedlist.FeedListViewModel
import com.rssfeeder.ui.server.ServerScreen
import com.rssfeeder.ui.settings.SettingsScreen
import com.rssfeeder.ui.theme.RSSFeederTheme

class MainActivity : ComponentActivity() {

    private var pendingFolderCallback: ((Uri) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ServerService.start(this)

        setContent {
            RSSFeederTheme {
                RssFeederApp()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_FOLDER_PICK && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                pendingFolderCallback?.invoke(uri)
                pendingFolderCallback = null
            }
        }
    }

    @Composable
    private fun RssFeederApp() {
        val navController = rememberNavController()
        val context = LocalContext.current

        val serverViewModel: ServerViewModel = viewModel()
        val feedListViewModel: FeedListViewModel = viewModel()

        val shareLog: () -> Unit = {
            try {
                val logFile = DebugLogger.getLogFile()
                if (logFile != null && logFile.exists()) {
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        logFile
                    )
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share log"))
                } else {
                    Toast.makeText(context, "No log file available", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                DebugLogger.log("MainActivity", "Share log failed: ${e.message}")
                Toast.makeText(context, "Failed to share log", Toast.LENGTH_SHORT).show()
            }
        }

        NavHost(
            navController = navController,
            startDestination = "server"
        ) {
            composable("server") {
                ServerScreen(
                    viewModel = serverViewModel,
                    onAddFeedClick = {
                        navController.navigate("add_feed")
                    },
                    onBackClick = {},
                    onShareLog = shareLog
                )
            }

            composable("add_feed") {
                AddFeedScreen(
                    onAddUrl = { url ->
                        feedListViewModel.addFeedByUrl(url)
                        navController.popBackStack()
                        serverViewModel.refreshFeeds()
                    },
                    onAddLocalFolder = {
                        pendingFolderCallback = { uri ->
                            val title = uri.lastPathSegment ?: "Local Folder"
                            feedListViewModel.addLocalFolderFeed(title, uri)
                            serverViewModel.refreshFeeds()
                        }
                        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                            addFlags(
                                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                            )
                        }
                        startActivityForResult(intent, REQUEST_FOLDER_PICK)
                    },
                    onBackClick = { navController.popBackStack() },
                    onShareLog = shareLog
                )
            }

            composable("settings") {
                SettingsScreen(
                    onBackClick = { navController.popBackStack() },
                    onShareLog = shareLog
                )
            }
        }
    }

    companion object {
        private const val REQUEST_FOLDER_PICK = 9001
    }
}
