package com.rssfeeder.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.rssfeeder.debug.DebugLogger
import com.rssfeeder.server.RelayManager
import com.rssfeeder.ui.components.RssFeederTopBar
import kotlinx.coroutines.launch

private val PUSH_INTERVALS = listOf(
    0 to "Manual",
    5 to "5 min",
    10 to "10 min",
    15 to "15 min",
    30 to "30 min",
    60 to "1 hour",
    120 to "2 hours",
    360 to "6 hours",
    720 to "12 hours",
    1440 to "24 hours"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onShareLog: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val verboseEnabled by DebugLogger.getVerboseEnabledFlow().collectAsState(initial = false)
    val savedPat by DebugLogger.getGithubPatFlow().collectAsState(initial = "")
    val pushInterval by DebugLogger.getPushIntervalFlow().collectAsState(initial = 0)
    var patInput by rememberSaveable { mutableStateOf(savedPat) }
    var patVisible by rememberSaveable { mutableStateOf(false) }
    var showIntervalMenu by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            if (patInput != savedPat) {
                scope.launch {
                    DebugLogger.persistGithubPat(context, patInput)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            RssFeederTopBar(
                title = "Settings",
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Verbose logging",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Log detailed information for troubleshooting",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Switch(
                    checked = verboseEnabled,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            DebugLogger.persistVerboseEnabled(context, enabled)
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "GitHub Relay",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Personal Access Token (public_repo scope). Create at github.com/settings/tokens",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = patInput,
                onValueChange = { patInput = it },
                label = { Text("GitHub PAT") },
                singleLine = true,
                visualTransformation = if (patVisible) VisualTransformation.None
                    else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        scope.launch {
                            DebugLogger.persistGithubPat(context, patInput)
                        }
                    }
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (savedPat.isNotEmpty()) "Token saved" else "No token saved",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (savedPat.isNotEmpty()) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(16.dp))
                Switch(
                    checked = patVisible,
                    onCheckedChange = { patVisible = it }
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (patVisible) "Hide" else "Show",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Auto-push interval",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Automatically push feeds to GitHub relay on a schedule",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Box {
                OutlinedButton(
                    onClick = { showIntervalMenu = true }
                ) {
                    Text(
                        PUSH_INTERVALS.firstOrNull { it.first == pushInterval }?.second
                            ?: "Manual"
                    )
                }
                DropdownMenu(
                    expanded = showIntervalMenu,
                    onDismissRequest = { showIntervalMenu = false }
                ) {
                    PUSH_INTERVALS.forEach { (minutes, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                scope.launch {
                                    DebugLogger.persistPushInterval(context, minutes)
                                }
                                showIntervalMenu = false
                            }
                        )
                    }
                }
            }
        }
    }
}
