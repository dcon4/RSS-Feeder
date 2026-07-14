package com.rssfeeder.ui.addfeed

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.rssfeeder.ui.components.RssFeederTopBar

data class FeedAddConfig(
    val url: String,
    val title: String,
    val autoDownload: Boolean = false,
    val downloadFolder: String? = null,
    val pollingIntervalMinutes: Int? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFeedScreen(
    onAddFeed: (FeedAddConfig) -> Unit,
    onAddLocalFolder: () -> Unit,
    onBackClick: () -> Unit,
    onShareLog: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var urlText by remember { mutableStateOf("") }
    var feedTitle by remember { mutableStateOf("") }
    var autoDownload by remember { mutableStateOf(false) }
    var downloadFolderUri by remember { mutableStateOf<String?>(null) }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            downloadFolderUri = it.toString()
        }
    }

    Scaffold(
        topBar = {
            RssFeederTopBar(
                title = "Add feed",
                onShareLog = onShareLog,
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        },
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.RssFeed,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Add RSS feed by URL",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = urlText,
                        onValueChange = { urlText = it },
                        label = { Text("Feed URL") },
                        placeholder = { Text("https://example.com/feed.xml") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = feedTitle,
                        onValueChange = { feedTitle = it },
                        label = { Text("Feed title (optional)") },
                        placeholder = { Text("Leave blank for auto-detect") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Auto-download articles",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "Save articles to device folder after sync",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Switch(
                            checked = autoDownload,
                            onCheckedChange = { autoDownload = it }
                        )
                    }

                    if (autoDownload) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { folderPickerLauncher.launch(null) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                if (downloadFolderUri != null) "Folder selected"
                                else "Choose download folder"
                            )
                        }
                        if (downloadFolderUri != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = downloadFolderUri!!.substringAfterLast('/'),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            if (urlText.isNotBlank()) {
                                onAddFeed(
                                    FeedAddConfig(
                                        url = urlText.trim(),
                                        title = feedTitle.trim(),
                                        autoDownload = autoDownload,
                                        downloadFolder = downloadFolderUri
                                    )
                                )
                            }
                        },
                        enabled = urlText.isNotBlank() && (!autoDownload || downloadFolderUri != null),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Add feed")
                    }
                }
            }

            WebPageAddCard(
                onAddWebPage = { config ->
                    onAddFeed(config)
                }
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Add local folder as feed",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Choose a folder containing .txt, .html, or .pdf files. Each file will appear as a feed entry.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = onAddLocalFolder,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Select folder")
                    }
                }
            }
        }
    }
}

val POLLING_INTERVAL_OPTIONS = listOf(
    30 to "Every 30 minutes",
    60 to "Every 1 hour",
    180 to "Every 3 hours",
    360 to "Every 6 hours",
    720 to "Every 12 hours",
    1440 to "Every 24 hours"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WebPageAddCard(
    onAddWebPage: (FeedAddConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var urlText by remember { mutableStateOf("") }
    var feedTitle by remember { mutableStateOf("") }
    var autoDownload by remember { mutableStateOf(false) }
    var downloadFolderUri by remember { mutableStateOf<String?>(null) }
    var selectedInterval by remember { mutableStateOf(360) }
    var dropdownExpanded by remember { mutableStateOf(false) }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            downloadFolderUri = it.toString()
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Language,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Add web page as feed",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Enter a web page URL that lists articles but has no RSS feed. The app will scan for new links and generate a feed with full-text articles.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = urlText,
                onValueChange = { urlText = it },
                label = { Text("Web page URL") },
                placeholder = { Text("https://example.com/news/") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = feedTitle,
                onValueChange = { feedTitle = it },
                label = { Text("Feed title (optional)") },
                placeholder = { Text("Leave blank for auto-detect from page") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(12.dp))

            ExposedDropdownMenuBox(
                expanded = dropdownExpanded,
                onExpandedChange = { dropdownExpanded = it }
            ) {
                OutlinedTextField(
                    value = POLLING_INTERVAL_OPTIONS.first { it.first == selectedInterval }.second,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Polling frequency") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    singleLine = true
                )
                ExposedDropdownMenu(
                    expanded = dropdownExpanded,
                    onDismissRequest = { dropdownExpanded = false }
                ) {
                    POLLING_INTERVAL_OPTIONS.forEach { (minutes, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                selectedInterval = minutes
                                dropdownExpanded = false
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Auto-download articles",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = "Save articles to device folder after sync",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Switch(
                    checked = autoDownload,
                    onCheckedChange = { autoDownload = it }
                )
            }

            if (autoDownload) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { folderPickerLauncher.launch(null) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (downloadFolderUri != null) "Folder selected"
                        else "Choose download folder"
                    )
                }
                if (downloadFolderUri != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = downloadFolderUri!!.substringAfterLast('/'),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = {
                    if (urlText.isNotBlank()) {
                        onAddWebPage(
                            FeedAddConfig(
                                url = urlText.trim(),
                                title = feedTitle.trim(),
                                autoDownload = autoDownload,
                                downloadFolder = downloadFolderUri,
                                pollingIntervalMinutes = selectedInterval
                            )
                        )
                    }
                },
                enabled = urlText.isNotBlank() && (!autoDownload || downloadFolderUri != null),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Add web page feed")
            }
        }
    }
}
