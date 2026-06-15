package com.rssfeeder.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.rssfeeder.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RssFeederTopBar(
    title: String,
    onShareLog: () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable (() -> Unit)? = null,
    extraActions: @Composable (() -> Unit)? = null
) {
    TopAppBar(
        title = { Text(text = title) },
        navigationIcon = { navigationIcon?.invoke() },
        actions = {
            extraActions?.invoke()
            IconButton(onClick = onShareLog) {
                Icon(
                    imageVector = Icons.Default.BugReport,
                    contentDescription = stringResource(R.string.share_log)
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(),
        modifier = modifier
    )
}
