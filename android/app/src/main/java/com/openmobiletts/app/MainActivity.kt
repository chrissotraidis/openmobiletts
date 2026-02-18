package com.openmobiletts.app

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.openmobiletts.app.ui.theme.OpenMobileTTSTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private val modelDownloader = ModelDownloader()
    private val ttsManager = TtsManager()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        super.onDestroy()
        ttsManager.release()
    }

    @Composable
    private fun TtsScreen() {
        val scope = rememberCoroutineScope()

        var status by remember { mutableStateOf("Ready") }
        var isModelDownloaded by remember {
            mutableStateOf(modelDownloader.isModelDownloaded(filesDir))
        }
        var isTtsReady by remember { mutableStateOf(false) }
        var isWorking by remember { mutableStateOf(false) }
        var downloadProgress by remember { mutableFloatStateOf(0f) }

        // Auto-init TTS if model already downloaded
        LaunchedEffect(isModelDownloaded) {
            if (isModelDownloaded && !isTtsReady) {
                status = "Initializing TTS engine..."
                isWorking = true
                try {
                    ttsManager.init(modelDownloader.getModelDir(filesDir))
                    isTtsReady = true
                    status = "TTS engine ready"
                } catch (e: Exception) {
                    status = "Init failed: ${e.message}"
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
                text = status,
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
            } else if (isWorking) {
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
                            status = "Downloading model..."
                            try {
                                modelDownloader.download(filesDir) { downloaded, total ->
                                    if (total > 0) {
                                        downloadProgress = downloaded.toFloat() / total
                                        status = "Downloading: ${downloaded / 1024 / 1024} / ${total / 1024 / 1024} MB"
                                    }
                                }
                                isModelDownloaded = true
                                status = "Download complete!"
                            } catch (e: Exception) {
                                status = "Download failed: ${e.message}"
                                isWorking = false
                            }
                        }
                    },
                    enabled = !isWorking,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 32.dp),
                ) {
                    Text("Download Model (~95 MB)")
                }
            }

            // Speak button (shown when TTS is ready)
            if (isTtsReady) {
                Button(
                    onClick = {
                        scope.launch {
                            isWorking = true
                            status = "Generating speech..."
                            try {
                                val samples = ttsManager.generate(
                                    text = "Hello! This is Open Mobile TTS running on your device.",
                                    sid = 3,  // af_heart
                                    speed = 1.0f,
                                )
                                status = "Playing audio..."
                                playAudio(samples)
                                status = "Done!"
                            } catch (e: Exception) {
                                status = "Error: ${e.message}"
                            }
                            isWorking = false
                        }
                    },
                    enabled = !isWorking,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 32.dp),
                ) {
                    Text("Speak")
                }
            }
        }
    }

    private suspend fun playAudio(samples: FloatArray) = withContext(Dispatchers.IO) {
        val bufferSize = AudioTrack.getMinBufferSize(
            TtsManager.SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT,
        )

        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(TtsManager.SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(bufferSize, samples.size * 4))
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        audioTrack.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
        audioTrack.play()

        // Wait for playback to finish
        val durationMs = (samples.size.toLong() * 1000) / TtsManager.SAMPLE_RATE
        Thread.sleep(durationMs + 200) // small buffer for safety

        audioTrack.stop()
        audioTrack.release()
    }
}
