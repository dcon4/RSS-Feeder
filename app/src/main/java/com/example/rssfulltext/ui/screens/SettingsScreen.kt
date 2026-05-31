package com.example.rssfulltext.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.rssfulltext.logging.DebugLogger
import com.example.rssfulltext.network.server.FeedHttpServer
import com.example.rssfulltext.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var portText by remember { mutableStateOf(uiState.serverPort.toString()) }
    var refreshIntervalText by remember { mutableStateOf("60") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Text("\u2190") // Back arrow
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Server Settings
            Text(
                text = "Server Settings",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = portText,
                onValueChange = {
                    portText = it.filter { c -> c.isDigit() }
                    portText.toIntOrNull()?.let { port ->
                        if (port in 1024..65535) viewModel.setServerPort(port)
                    }
                },
                label = { Text("Server Port") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("LAN Access", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Allow other devices on your network to access feeds",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(
                    checked = uiState.bindAllInterfaces,
                    onCheckedChange = { viewModel.setBindAllInterfaces(it) }
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            // Refresh Settings
            Text(
                text = "Auto-Refresh",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = refreshIntervalText,
                onValueChange = { refreshIntervalText = it.filter { c -> c.isDigit() } },
                label = { Text("Refresh Interval (minutes)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val interval = refreshIntervalText.toLongOrNull() ?: 60
                        viewModel.schedulePeriodicRefresh(interval)
                    }
                ) {
                    Text("Enable Auto-Refresh")
                }
                OutlinedButton(
                    onClick = { viewModel.cancelPeriodicRefresh() }
                ) {
                    Text("Disable")
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            // Debug Logging
            Text(
                text = "Debug Logging",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Verbose Logging", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Log detailed network requests, parsing steps, etc.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(
                    checked = uiState.verboseLogging,
                    onCheckedChange = { viewModel.setVerboseLogging(it) }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Share log button - always visible per global learning spec
            Button(
                onClick = {
                    val shareIntent = DebugLogger.createShareIntent(context)
                    if (shareIntent != null) {
                        context.startActivity(Intent.createChooser(shareIntent, "Share Debug Log"))
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Share Debug Log")
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = { DebugLogger.clearLog() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Clear Log")
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            // Info section
            Text(
                text = "How to Use",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "1. Add RSS feed URLs on the main screen\n" +
                        "2. Start the server\n" +
                        "3. In your RSS reader app, subscribe to:\n" +
                        "   http://127.0.0.1:${uiState.serverPort}/feed/{slug}\n\n" +
                        "4. For all feeds at once, import the OPML file:\n" +
                        "   http://127.0.0.1:${uiState.serverPort}/opml\n\n" +
                        "5. For directory feeds, point to a folder with\n" +
                        "   .txt, .html, .epub, or .pdf files",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
