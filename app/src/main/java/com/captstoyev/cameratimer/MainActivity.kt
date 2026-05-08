package com.captstoyev.cameratimer

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.captstoyev.cameratimer.camera.CameraManager
import com.captstoyev.cameratimer.data.SettingsRepository
import com.captstoyev.cameratimer.data.UserSettings
import com.captstoyev.cameratimer.service.CameraTimerService
import com.captstoyev.cameratimer.ui.CameraPreview
import com.captstoyev.cameratimer.ui.SettingsScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var cameraManager: CameraManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsRepository = SettingsRepository(applicationContext)
        cameraManager = CameraManager(applicationContext)

        setContent {
            MaterialTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    MainApp(settingsRepository, cameraManager)
                }
            }
        }
    }
}

@Composable
fun MainApp(
    settingsRepository: SettingsRepository,
    cameraManager: CameraManager
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by settingsRepository.settingsFlow.collectAsState(
        initial = UserSettings(10L, "", false, false)
    )
    
    var showSettings by remember { mutableStateOf(false) }
    var isRunning by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    val folderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            // Persist permission for the selected folder
            context.contentResolver.takePersistableUriPermission(
                it, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            scope.launch { settingsRepository.updateFolderUri(it.toString()) }
        }
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    if (showSettings) {
        SettingsScreen(
            settings = settings,
            onIntervalChange = { scope.launch { settingsRepository.updateInterval(it) } },
            onFolderSelect = { folderLauncher.launch(null) },
            onSoundToggle = { scope.launch { settingsRepository.updateSoundEnabled(it) } },
            onBackgroundToggle = { scope.launch { settingsRepository.updateBackgroundEnabled(it) } },
            onBack = { showSettings = false }
        )
    } else {
        Box(modifier = Modifier.fillMaxSize()) {
            CameraPreview(cameraManager = cameraManager)

            // Controls Overlay
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (settings.folderUri.isEmpty()) {
                    Text(
                        "Please select a folder in settings first",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            val action = if (isRunning) CameraTimerService.ACTION_STOP else CameraTimerService.ACTION_START
                            val intent = Intent(context, CameraTimerService::class.java).apply {
                                this.action = action
                            }
                            context.startService(intent)
                            isRunning = !isRunning
                        },
                        enabled = settings.folderUri.isNotEmpty()
                    ) {
                        Text(if (isRunning) "Stop Timer" else "Start Timer")
                    }

                    IconButton(
                        onClick = { showSettings = true },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            }
        }
    }
}
