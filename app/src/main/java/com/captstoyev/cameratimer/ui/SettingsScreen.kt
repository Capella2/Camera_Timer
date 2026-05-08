package com.captstoyev.cameratimer.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.captstoyev.cameratimer.data.UserSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: UserSettings,
    onIntervalChange: (Long) -> Unit,
    onFolderSelect: () -> Unit,
    onSoundToggle: (Boolean) -> Unit,
    onBackgroundToggle: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("<-") // Simplified back button
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Capture Settings", style = MaterialTheme.typography.titleMedium)

            // Interval Setting
            Column {
                Text("Interval (seconds): ${settings.intervalSeconds}")
                Slider(
                    value = settings.intervalSeconds.toFloat(),
                    onValueChange = { onIntervalChange(it.toLong()) },
                    valueRange = 1f..60f,
                    steps = 59
                )
            }

            // Folder Selection
            Column {
                Text("Target Folder:", style = MaterialTheme.typography.labelMedium)
                Text(
                    text = if (settings.folderUri.isEmpty()) "Not Selected" else settings.folderUri,
                    style = MaterialTheme.typography.bodySmall
                )
                Button(onClick = onFolderSelect) {
                    Text("Select Folder")
                }
            }

            // Sound Toggle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Shutter Sound")
                Switch(checked = settings.isSoundEnabled, onCheckedChange = onSoundToggle)
            }

            // Background Mode Toggle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Background Capture")
                Switch(checked = settings.isBackgroundEnabled, onCheckedChange = onBackgroundToggle)
            }

            HorizontalDivider()

            // About Section
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("Camera Timer App", style = MaterialTheme.typography.headlineSmall)
                Text("Version 1.0", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Author: Capt. Stoyev Igor", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
