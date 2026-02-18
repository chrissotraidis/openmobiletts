package com.openmobiletts.app

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.openmobiletts.app.ui.theme.OpenMobileTTSTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val modelDownloader = ModelDownloader()
    private var ttsServiceState = mutableStateOf<TtsService?>(null)
    private var serviceBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            ttsServiceState.value = (binder as TtsService.LocalBinder).getService()
            serviceBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            ttsServiceState.value = null
            serviceBound = false
        }
    }

    // Notification permission launcher (Android 13+)
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* granted or not — generation proceeds either way */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Bind to TtsService (creates it if needed)
        Intent(this, TtsService::class.java).also { intent ->
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }

        setContent {
            OpenMobileTTSTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    TtsScreen()
                }
            }
        }
    }

    override fun onDestroy() {
        if (serviceBound) {
            unbindService(serviceConnection)
            serviceBound = false
        }
        super.onDestroy()
    }

    @Composable
    private fun TtsScreen() {
        val scope = rememberCoroutineScope()
        val app = application as OpenMobileTtsApp
        val ttsService = ttsServiceState.value

        var isModelDownloaded by remember {
            mutableStateOf(modelDownloader.isModelDownloaded(filesDir))
        }
        var isTtsReady by remember { mutableStateOf(app.ttsManager.isInitialized) }
        var localStatus by remember { mutableStateOf("Ready") }
        var isWorking by remember { mutableStateOf(false) }
        var downloadProgress by remember { mutableFloatStateOf(0f) }

        // Observe service status
        val serviceStatus by ttsService?.status?.collectAsState()
            ?: remember { mutableStateOf(TtsService.Status.IDLE) }
        val serviceMessage by ttsService?.statusMessage?.collectAsState()
            ?: remember { mutableStateOf("") }

        val displayStatus = if (serviceStatus != TtsService.Status.IDLE && serviceMessage.isNotEmpty()) {
            serviceMessage
        } else {
            localStatus
        }

        val isBusy = isWorking || serviceStatus == TtsService.Status.GENERATING || serviceStatus == TtsService.Status.PLAYING

        // Auto-init TTS if model already downloaded
        LaunchedEffect(isModelDownloaded) {
            if (isModelDownloaded && !isTtsReady) {
                localStatus = "Initializing TTS engine..."
                isWorking = true
                try {
                    app.ttsManager.init(modelDownloader.getModelDir(filesDir))
                    isTtsReady = true
                    localStatus = "TTS engine ready"
                } catch (e: Exception) {
                    localStatus = "Init failed: ${e.message}"
                }
                isWorking = false
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Open Mobile TTS",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "On-device text-to-speech",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Status
            Text(
                text = displayStatus,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Download progress bar
            if (isWorking && downloadProgress > 0f && downloadProgress < 1f) {
                LinearProgressIndicator(
                    progress = { downloadProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp),
                )
                Spacer(modifier = Modifier.height(16.dp))
            } else if (isBusy) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp),
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Download button (shown when model not yet downloaded)
            if (!isModelDownloaded) {
                Button(
                    onClick = {
                        scope.launch {
                            isWorking = true
                            downloadProgress = 0f
                            localStatus = "Downloading model..."
                            try {
                                modelDownloader.download(filesDir) { downloaded, total ->
                                    if (total > 0) {
                                        downloadProgress = downloaded.toFloat() / total
                                        localStatus = "Downloading: ${downloaded / 1024 / 1024} / ${total / 1024 / 1024} MB"
                                    }
                                }
                                isModelDownloaded = true
                                localStatus = "Download complete!"
                            } catch (e: Exception) {
                                localStatus = "Download failed: ${e.message}"
                                isWorking = false
                            }
                        }
                    },
                    enabled = !isBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 32.dp),
                ) {
                    Text("Download Model (~95 MB)")
                }
            }

            // Speak button — starts the foreground service
            if (isTtsReady) {
                Button(
                    onClick = {
                        val intent = Intent(this@MainActivity, TtsService::class.java).apply {
                            putExtra(TtsService.EXTRA_TEXT, "Hello! This is Open Mobile TTS running on your device.")
                            putExtra(TtsService.EXTRA_SID, 3) // af_heart
                            putExtra(TtsService.EXTRA_SPEED, 1.0f)
                        }
                        ContextCompat.startForegroundService(this@MainActivity, intent)
                    },
                    enabled = !isBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 32.dp),
                ) {
                    Text("Speak")
                }

                // Stop button (visible during generation/playback)
                if (serviceStatus == TtsService.Status.GENERATING || serviceStatus == TtsService.Status.PLAYING) {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { ttsService?.stopGeneration() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .padding(horizontal = 32.dp),
                    ) {
                        Text("Stop")
                    }
                }
            }
        }
    }
}
