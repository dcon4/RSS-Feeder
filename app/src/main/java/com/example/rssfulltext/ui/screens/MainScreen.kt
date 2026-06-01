package com.example.rssfulltext.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.example.rssfulltext.data.model.DirectoryFeedSource
import com.example.rssfulltext.data.model.RssFeedSource
import com.example.rssfulltext.ui.viewmodel.MainUiState
import com.example.rssfulltext.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onNavigateToSettings: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddRssDialog by remember { mutableStateOf(false) }
    var showAddDirectoryDialog by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) }

    // Show snackbar for refresh messages
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()

    val copyToClipboard: (String) -> Unit = { url ->
        clipboardManager.setText(AnnotatedString(url))
        coroutineScope.launch {
            snackbarHostState.showSnackbar("Feed URL copied")
        }
    }

    LaunchedEffect(uiState.refreshMessage) {
        uiState.refreshMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearRefreshMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("RSS Full Text") },
                actions = {
                    IconButton(onClick = { viewModel.refreshAllFeeds() }) {
                        Text("\u21BB") // Refresh symbol
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Text("\u2699") // Gear symbol
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    when (selectedTab) {
                        0 -> showAddRssDialog = true
                        1 -> showAddDirectoryDialog = true
                    }
                }
            ) {
                Text("+", style = MaterialTheme.typography.headlineMedium)
            }
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            // Server status bar
            ServerStatusBar(
                uiState = uiState,
                onStartServer = { viewModel.startServer() },
                onStopServer = { viewModel.stopServer() },
                onCopyUrl = copyToClipboard
            )

            // Tabs
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("RSS Feeds (${uiState.rssFeeds.size})") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Directories (${uiState.directoryFeeds.size})") }
                )
            }

            // Loading indicator
            if (uiState.isRefreshing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            // Content
            when (selectedTab) {
                0 -> RssFeedList(
                    feeds = uiState.rssFeeds,
                    serverPort = uiState.serverPort,
                    onRefresh = { viewModel.refreshRssFeed(it) },
                    onDelete = { viewModel.deleteRssFeed(it) },
                    onCopyUrl = copyToClipboard
                )
                1 -> DirectoryFeedList(
                    feeds = uiState.directoryFeeds,
                    serverPort = uiState.serverPort,
                    onScan = { viewModel.scanDirectory(it) },
                    onDelete = { viewModel.deleteDirectoryFeed(it) },
                    onCopyUrl = copyToClipboard
                )
            }
        }
    }

    // Dialogs
    if (showAddRssDialog) {
        AddRssFeedDialog(
            onDismiss = { showAddRssDialog = false },
            onConfirm = { name, url, slug, interval ->
                viewModel.addRssFeed(name, url, slug, interval)
                showAddRssDialog = false
            }
        )
    }

    if (showAddDirectoryDialog) {
        AddDirectoryFeedDialog(
            onDismiss = { showAddDirectoryDialog = false },
            onConfirm = { name, path, slug, includeSubdirs ->
                viewModel.addDirectoryFeed(name, path, slug, includeSubdirs)
                showAddDirectoryDialog = false
            }
        )
    }
}

@Composable
fun ServerStatusBar(
    uiState: MainUiState,
    onStartServer: () -> Unit,
    onStopServer: () -> Unit,
    onCopyUrl: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (uiState.serverRunning)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (uiState.serverRunning) "Server Running" else "Server Stopped",
                    style = MaterialTheme.typography.titleSmall
                )
                if (uiState.serverRunning) {
                    Text(
                        text = "http://127.0.0.1:${uiState.serverPort}/feeds",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            if (uiState.serverRunning) {
                Column {
                    Row {
                        TextButton(onClick = {
                            onCopyUrl("http://127.0.0.1:${uiState.serverPort}/feeds")
                        }) {
                            Text("Copy /feeds")
                        }
                        TextButton(onClick = {
                            onCopyUrl("http://127.0.0.1:${uiState.serverPort}/opml")
                        }) {
                            Text("Copy /opml")
                        }
                    }
                }
            }
            Button(
                onClick = { if (uiState.serverRunning) onStopServer() else onStartServer() }
            ) {
                Text(if (uiState.serverRunning) "Stop" else "Start")
            }
        }
    }
}

@Composable
fun RssFeedList(
    feeds: List<RssFeedSource>,
    serverPort: Int,
    onRefresh: (RssFeedSource) -> Unit,
    onDelete: (RssFeedSource) -> Unit,
    onCopyUrl: (String) -> Unit
) {
    if (feeds.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No RSS feeds added yet.\nTap + to add one.",
                style = MaterialTheme.typography.bodyLarge
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(8.dp)
        ) {
            items(feeds) { feed ->
                RssFeedCard(feed, serverPort, onRefresh, onDelete, onCopyUrl)
            }
        }
    }
}

@Composable
fun RssFeedCard(
    feed: RssFeedSource,
    serverPort: Int,
    onRefresh: (RssFeedSource) -> Unit,
    onDelete: (RssFeedSource) -> Unit,
    onCopyUrl: (String) -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = feed.name, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Source: ${feed.sourceUrl}",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1
            )
            Text(
                text = "Output: http://127.0.0.1:$serverPort/feed/${feed.outputSlug}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "Items: ${feed.itemCount} | Refresh: every ${feed.refreshIntervalMinutes}min",
                style = MaterialTheme.typography.bodySmall
            )
            if (feed.lastError != null) {
                Text(
                    text = "Error: ${feed.lastError}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = {
                    onCopyUrl("http://127.0.0.1:$serverPort/feed/${feed.outputSlug}")
                }) {
                    Text("Copy URL")
                }
                TextButton(onClick = { onRefresh(feed) }) {
                    Text("Refresh")
                }
                TextButton(onClick = { showDeleteConfirm = true }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Feed") },
            text = { Text("Delete '${feed.name}' and all its items?") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(feed)
                    showDeleteConfirm = false
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun DirectoryFeedList(
    feeds: List<DirectoryFeedSource>,
    serverPort: Int,
    onScan: (DirectoryFeedSource) -> Unit,
    onDelete: (DirectoryFeedSource) -> Unit,
    onCopyUrl: (String) -> Unit
) {
    if (feeds.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No directory feeds added yet.\nTap + to add one.",
                style = MaterialTheme.typography.bodyLarge
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(8.dp)
        ) {
            items(feeds) { feed ->
                DirectoryFeedCard(feed, serverPort, onScan, onDelete, onCopyUrl)
            }
        }
    }
}

@Composable
fun DirectoryFeedCard(
    feed: DirectoryFeedSource,
    serverPort: Int,
    onScan: (DirectoryFeedSource) -> Unit,
    onDelete: (DirectoryFeedSource) -> Unit,
    onCopyUrl: (String) -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = feed.name, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Path: ${feed.directoryPath}",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1
            )
            Text(
                text = "Output: http://127.0.0.1:$serverPort/feed/${feed.outputSlug}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "Items: ${feed.itemCount} | Subdirs: ${if (feed.includeSubdirectories) "Yes" else "No"}",
                style = MaterialTheme.typography.bodySmall
            )
            if (feed.lastError != null) {
                Text(
                    text = "Error: ${feed.lastError}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = {
                    onCopyUrl("http://127.0.0.1:$serverPort/feed/${feed.outputSlug}")
                }) {
                    Text("Copy URL")
                }
                TextButton(onClick = { onScan(feed) }) {
                    Text("Scan")
                }
                TextButton(onClick = { showDeleteConfirm = true }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Directory Feed") },
            text = { Text("Delete '${feed.name}' and all its items?") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(feed)
                    showDeleteConfirm = false
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun AddRssFeedDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, url: String, slug: String, interval: Int) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var slug by remember { mutableStateOf("") }
    var interval by remember { mutableStateOf("60") }

    // Auto-generate slug from name
    LaunchedEffect(name) {
        if (slug.isEmpty() || slug == name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trimEnd('-')) {
            slug = name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trimEnd('-')
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add RSS Feed") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Feed Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Feed URL") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = slug,
                    onValueChange = { slug = it },
                    label = { Text("Output Slug (URL path)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = interval,
                    onValueChange = { interval = it.filter { c -> c.isDigit() } },
                    label = { Text("Refresh Interval (minutes)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank() && url.isNotBlank() && slug.isNotBlank()) {
                        onConfirm(name, url, slug, interval.toIntOrNull() ?: 60)
                    }
                },
                enabled = name.isNotBlank() && url.isNotBlank() && slug.isNotBlank()
            ) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun AddDirectoryFeedDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, path: String, slug: String, includeSubdirs: Boolean) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var path by remember { mutableStateOf("") }
    var slug by remember { mutableStateOf("") }
    var includeSubdirs by remember { mutableStateOf(false) }

    val context = LocalContext.current

    val directoryPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            // Take persistable permissions so the app can access this URI across reboots
            val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(it, flags)
            path = it.toString()
        }
    }

    // Auto-generate slug from name
    LaunchedEffect(name) {
        if (slug.isEmpty() || slug == name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trimEnd('-')) {
            slug = name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trimEnd('-')
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Directory Feed") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Feed Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = path,
                    onValueChange = { path = it },
                    label = { Text("Directory Path") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = { directoryPicker.launch(null) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Browse...")
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = slug,
                    onValueChange = { slug = it },
                    label = { Text("Output Slug (URL path)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = includeSubdirs,
                        onCheckedChange = { includeSubdirs = it }
                    )
                    Text("Include subdirectories")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank() && path.isNotBlank() && slug.isNotBlank()) {
                        onConfirm(name, path, slug, includeSubdirs)
                    }
                },
                enabled = name.isNotBlank() && path.isNotBlank() && slug.isNotBlank()
            ) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
