package com.rssfeeder

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.rssfeeder.debug.DebugLogger
import com.rssfeeder.ui.addfeed.AddFeedScreen
import com.rssfeeder.ui.article.ArticleListScreen
import com.rssfeeder.ui.article.ArticleReaderScreen
import com.rssfeeder.ui.article.ArticleViewModel
import com.rssfeeder.ui.feedlist.FeedListScreen
import com.rssfeeder.ui.feedlist.FeedListViewModel
import com.rssfeeder.ui.settings.SettingsScreen
import com.rssfeeder.ui.theme.RSSFeederTheme
import java.io.File

class MainActivity : ComponentActivity() {

    private var pendingFolderCallback: ((Uri) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

        val feedListViewModel: FeedListViewModel = viewModel()
        val articleViewModel: ArticleViewModel = viewModel()

        var pendingAddUrl by remember { mutableStateOf<String?>(null) }

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
            startDestination = "feed_list"
        ) {
            composable("feed_list") {
                FeedListScreen(
                    viewModel = feedListViewModel,
                    onAddFeedClick = {
                        navController.navigate("add_feed")
                    },
                    onFeedClick = { feedId ->
                        navController.navigate("article_list/$feedId")
                    },
                    onShareLog = shareLog
                )
            }

            composable("add_feed") {
                AddFeedScreen(
                    onAddUrl = { url ->
                        feedListViewModel.addFeedByUrl(url)
                        navController.popBackStack()
                    },
                    onAddLocalFolder = {
                        pendingFolderCallback = { uri ->
                            val title = uri.lastPathSegment ?: "Local Folder"
                            feedListViewModel.addLocalFolderFeed(title, uri)
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

            composable(
                route = "article_list/{feedId}",
                arguments = listOf(navArgument("feedId") { type = NavType.LongType })
            ) { backStackEntry ->
                val feedId = backStackEntry.arguments?.getLong("feedId") ?: return@composable

                ArticleListScreen(
                    feedId = feedId,
                    viewModel = articleViewModel,
                    onArticleClick = { articleId ->
                        navController.navigate("article_reader/$articleId")
                    },
                    onBackClick = { navController.popBackStack() },
                    onShareLog = shareLog
                )
            }

            composable(
                route = "article_reader/{articleId}",
                arguments = listOf(navArgument("articleId") { type = NavType.LongType })
            ) { backStackEntry ->
                val articleId = backStackEntry.arguments?.getLong("articleId") ?: return@composable

                ArticleReaderScreen(
                    articleId = articleId,
                    viewModel = articleViewModel,
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

        pendingAddUrl?.let { url ->
            feedListViewModel.addFeedByUrl(url)
            pendingAddUrl = null
        }
    }

    companion object {
        private const val REQUEST_FOLDER_PICK = 9001
    }
}
