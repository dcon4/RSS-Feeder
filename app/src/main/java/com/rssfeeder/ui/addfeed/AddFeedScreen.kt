package com.rssfeeder.ui.addfeed

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
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rssfeeder.ui.components.RssFeederTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFeedScreen(
    onAddFeed: (url: String, title: String) -> Unit,
    onAddLocalFolder: () -> Unit,
    onBackClick: () -> Unit,
    onShareLog: () -> Unit,
    modifier: Modifier = Modifier
) {
    var urlText by remember { mutableStateOf("") }
    var feedTitle by remember { mutableStateOf("") }

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
                    Button(
                        onClick = {
                            if (urlText.isNotBlank()) {
                                onAddFeed(urlText.trim(), feedTitle.trim())
                            }
                        },
                        enabled = urlText.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Add feed")
                    }
                }
            }

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
