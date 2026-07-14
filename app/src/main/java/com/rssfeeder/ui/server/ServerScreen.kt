package com.rssfeeder.ui.server

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.rssfeeder.data.model.FeedType
import com.rssfeeder.server.FeedWithUrl
import com.rssfeeder.server.ServerService
import com.rssfeeder.server.ServerViewModel
import com.rssfeeder.ui.components.RssFeederTopBar
import com.rssfeeder.ui.theme.MutedCardColors
import com.rssfeeder.ui.theme.MutedCardColorsDark

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerScreen(
    viewModel: ServerViewModel,
    onAddFeedClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onShareLog: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    val cardColorPalette = if (isDark) MutedCardColorsDark else MutedCardColors

    Scaffold(
        topBar = {
            RssFeederTopBar(
                title = "RSS Feeder",
                onShareLog = onShareLog,
                navigationIcon = null,
                extraActions = {
                    IconButton(onClick = onAddFeedClick) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add feed"
                        )
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings"
                        )
                    }
                }
            )
        },
        modifier = modifier
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                ServerStatusCard(
                    isRunning = uiState.isRunning,
                    ipAddress = uiState.ipAddress,
                    port = uiState.port,
                    hasHttps = uiState.hasHttps,
                    certGenerated = uiState.certGenerated,
                    diagResult = uiState.diagResult,
                    diagRunning = uiState.diagRunning,
                    pushResult = uiState.pushResult,
                    pushRunning = uiState.pushRunning,
                    hasPat = uiState.relayPat.isNotEmpty(),
                    onStart = { viewModel.startServer() },
                    onStop = { viewModel.stopServer() },
                    onDiagnostics = { viewModel.runDiagnostics() },
                    onInstallCert = { viewModel.installCert() },
                    onPushFeeds = { viewModel.pushAllFeeds() }
                )
            }

            if (uiState.isRunning && uiState.feeds.isEmpty()) {
                item {
                    Text(
                        text = "No feeds yet. Add feeds so they can be served.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            itemsIndexed(
                items = uiState.feeds,
                key = { _, item -> item.feed.id }
            ) { index, feedWithUrl ->
                val cardColor = cardColorPalette[index % cardColorPalette.size]
                FeedRssCard(
                    feedWithUrl = feedWithUrl,
                    cardColor = cardColor,
                    onCopyLocal = {
                        copyToClipboard(context, feedWithUrl.localUrl)
                    },
                    onCopyNetwork = {
                        copyToClipboard(context, feedWithUrl.networkUrl)
                    },
                    onCopyLocalHttps = {
                        copyToClipboard(context, feedWithUrl.localHttpsUrl)
                    },
                    onCopyNetworkHttps = {
                        copyToClipboard(context, feedWithUrl.networkHttpsUrl)
                    },
                    onCopyRelay = {
                        copyToClipboard(context, feedWithUrl.relayUrl)
                    },
                    onDelete = { viewModel.deleteFeed(feedWithUrl.feed) },
                    onPollNow = { viewModel.refreshFeed(feedWithUrl.feed.id) }
                )
            }
        }
    }
}

@Composable
private fun ServerStatusCard(
    isRunning: Boolean,
    ipAddress: String,
    port: Int,
    hasHttps: Boolean,
    certGenerated: Boolean,
    diagResult: String?,
    diagRunning: Boolean,
    pushResult: String?,
    pushRunning: Boolean,
    hasPat: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onDiagnostics: () -> Unit,
    onInstallCert: () -> Unit,
    onPushFeeds: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Server",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(8.dp))

            val statusText = if (isRunning) {
                "Running"
            } else {
                "Not running"
            }
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isRunning) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (isRunning) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "HTTP: http://127.0.0.1:$port",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "HTTP: http://$ipAddress:$port",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (hasHttps) {
                    Text(
                        text = "HTTPS: https://127.0.0.1:${ServerService.DEFAULT_HTTPS_PORT}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "HTTPS: https://$ipAddress:${ServerService.DEFAULT_HTTPS_PORT}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "Add these URLs to your RSS reader",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = if (isRunning) onStop else onStart,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isRunning) "Stop server" else "Start server")
            }

            if (isRunning) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onDiagnostics,
                    enabled = !diagRunning,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (diagRunning) "Testing..." else "Test connection")
                }
            }

            if (certGenerated) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onInstallCert,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Install CA certificate")
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "If no install prompt appears: open Settings > Security > Install certificate > CA certificate. " +
                            "Find 'rss-feeder-ca.crt' in Downloads.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isRunning && hasPat) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onPushFeeds,
                    enabled = !pushRunning,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudUpload,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(if (pushRunning) "Pushing..." else "Push to GitHub relay")
                }
            }

            if (pushResult != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Text(
                        text = pushResult,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            if (diagResult != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Text(
                        text = diagResult,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun FeedRssCard(
    feedWithUrl: FeedWithUrl,
    cardColor: Color,
    onCopyLocal: () -> Unit,
    onCopyNetwork: () -> Unit,
    onCopyLocalHttps: () -> Unit = {},
    onCopyNetworkHttps: () -> Unit = {},
    onCopyRelay: () -> Unit = {},
    onDelete: () -> Unit = {},
    onPollNow: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = {
                Text(
                    text = "Delete feed",
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete \"${feedWithUrl.feed.title}\"? This will remove the feed and all its articles.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = feedWithUrl.feed.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = feedWithUrl.feed.url.let { url ->
                    if (url.length > 60) url.take(60) + "..." else url
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(8.dp))

            UrlCopyRow(
                label = "Same device (HTTP)",
                url = feedWithUrl.localUrl,
                onCopy = onCopyLocal
            )
            Spacer(modifier = Modifier.height(4.dp))
            UrlCopyRow(
                label = "Other devices (HTTP)",
                url = feedWithUrl.networkUrl,
                onCopy = onCopyNetwork
            )
            if (feedWithUrl.relayUrl.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                UrlCopyRow(
                    label = "GitHub relay (HTTPS)",
                    url = feedWithUrl.relayUrl,
                    onCopy = onCopyRelay
                )
            }
            if (feedWithUrl.localHttpsUrl.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                UrlCopyRow(
                    label = "Same device (HTTPS)",
                    url = feedWithUrl.localHttpsUrl,
                    onCopy = onCopyLocalHttps
                )
            }
            if (feedWithUrl.networkHttpsUrl.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                UrlCopyRow(
                    label = "Other devices (HTTPS)",
                    url = feedWithUrl.networkHttpsUrl,
                    onCopy = onCopyNetworkHttps
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            if (feedWithUrl.feed.type == FeedType.WEB_PAGE) {
                val minutes = feedWithUrl.feed.pollingIntervalMinutes
                val freqLabel = when {
                    minutes < 60 -> "Every $minutes minutes"
                    minutes < 1440 -> "Every ${minutes / 60} hours"
                    else -> "Every ${minutes / 1440} day"
                }
                val lastPolled = if (feedWithUrl.feed.lastPolledAt > 0) {
                    val elapsed = (System.currentTimeMillis() - feedWithUrl.feed.lastPolledAt) / 1000
                    when {
                        elapsed < 60 -> "Just now"
                        elapsed < 3600 -> "${elapsed / 60} min ago"
                        else -> "${elapsed / 3600} hours ago"
                    }
                } else "Never"

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Web page feed",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Poll: $freqLabel, Last: $lastPolled",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val error = feedWithUrl.feed.errorMessage
                        if (error != null) {
                            Text(
                                text = "Error: $error",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = onPollNow) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Poll now"
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            Button(
                onClick = { showDeleteConfirm = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Delete feed")
            }
        }
    }
}

@Composable
private fun UrlCopyRow(
    label: String,
    url: String,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(2.dp))
            IconButton(
                onClick = onCopy,
                modifier = Modifier.align(Alignment.End)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy $label URL"
                )
            }
            Text(
                text = url,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("RSS feed URL", text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "URL copied to clipboard", Toast.LENGTH_SHORT).show()
}
