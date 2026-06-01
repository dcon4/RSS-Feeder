package com.example.rssfulltext

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.rssfulltext.logging.DebugLogger
import com.example.rssfulltext.ui.screens.MainScreen
import com.example.rssfulltext.ui.screens.SettingsScreen
import com.example.rssfulltext.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DebugLogger.log("MainActivity", "onCreate")

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }

    override fun onDestroy() {
        DebugLogger.log("MainActivity", "onDestroy")
        super.onDestroy()
    }
}

@Composable
fun AppNavigation() {
    val viewModel: MainViewModel = viewModel()
    var currentScreen by remember { mutableStateOf("main") }

    when (currentScreen) {
        "main" -> MainScreen(
            viewModel = viewModel,
            onNavigateToSettings = { currentScreen = "settings" }
        )
        "settings" -> SettingsScreen(
            viewModel = viewModel,
            onNavigateBack = { currentScreen = "main" }
        )
    }
}
