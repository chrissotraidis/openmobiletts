package com.openmobiletts.app

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Foreground service that generates TTS audio and plays it back.
 * Survives Activity going to background, shows notifications.
 */
class TtsService : Service() {

    companion object {
        private const val TAG = "TtsService"
        private const val NOTIFICATION_ID = 1
        private const val COMPLETE_NOTIFICATION_ID = 2

        const val EXTRA_TEXT = "text"
        const val EXTRA_SID = "sid"
        const val EXTRA_SPEED = "speed"
    }

    enum class Status { IDLE, GENERATING, PLAYING, DONE, ERROR }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var generateJob: Job? = null
    @Volatile private var audioTrack: AudioTrack? = null

    // Observable state for the Activity
    private val _status = MutableStateFlow(Status.IDLE)
    val status: StateFlow<Status> = _status

    private val _statusMessage = MutableStateFlow("")
    val statusMessage: StateFlow<String> = _statusMessage

    // Binder for Activity to connect
    inner class LocalBinder : Binder() {
        fun getService(): TtsService = this@TtsService
    }

    private val binder = LocalBinder()

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val text = intent?.getStringExtra(EXTRA_TEXT)
        if (text != null) {
            val sid = intent.getIntExtra(EXTRA_SID, 3)
            val speed = intent.getFloatExtra(EXTRA_SPEED, 1.0f)
            startGeneration(text, sid, speed)
        }
        return START_NOT_STICKY
    }

    private fun startGeneration(text: String, sid: Int, speed: Float) {
        // Cancel any previous job
        generateJob?.cancel()
        audioTrack?.let { track ->
            try { track.stop() } catch (_: IllegalStateException) {}
            track.release()
        }
        audioTrack = null

        // Move to foreground with progress notification
        startForeground(NOTIFICATION_ID, buildProgressNotification("Generating speech..."))

        _status.value = Status.GENERATING
        _statusMessage.value = "Generating speech..."

        generateJob = scope.launch {
            try {
                val app = application as OpenMobileTtsApp
                val manager = app.ttsManager

                if (!manager.isInitialized) {
                    _statusMessage.value = "Initializing TTS engine..."
                    updateProgressNotification("Initializing TTS engine...")
                    val modelDir = ModelDownloader().getModelDir(filesDir)
                    manager.init(modelDir)
                }

                _statusMessage.value = "Generating speech..."
                updateProgressNotification("Generating speech...")

                val samples = manager.generate(text = text, sid = sid, speed = speed)

                _status.value = Status.PLAYING
                _statusMessage.value = "Playing audio..."
                updateProgressNotification("Playing audio...")

                playAudioInService(samples)

                _status.value = Status.DONE
                _statusMessage.value = "Done!"

                // Only show completion notification if app is in background
                if (!isAppInForeground()) {
                    showCompletionNotification()
                }

                stopForeground(STOP_FOREGROUND_REMOVE)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Generation failed", e)
                _status.value = Status.ERROR
                _statusMessage.value = "Error: ${e.message}"
                stopForeground(STOP_FOREGROUND_REMOVE)
            }
        }
    }

    private fun isAppInForeground(): Boolean {
        return try {
            ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        } catch (_: Exception) {
            true // Assume foreground if we can't check
        }
    }

    private suspend fun playAudioInService(samples: FloatArray) = withContext(Dispatchers.IO) {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        val bufferSize = AudioTrack.getMinBufferSize(
            TtsManager.SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT,
        )

        val track = AudioTrack.Builder()
            .setAudioAttributes(audioAttributes)
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

        // Request audio focus so other apps duck/pause
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(audioAttributes)
            .build()
        am.requestAudioFocus(focusRequest)

        audioTrack = track
        try {
            track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
            track.play()

            // Wait for playback to finish
            val durationMs = (samples.size.toLong() * 1000) / TtsManager.SAMPLE_RATE
            delay(durationMs + 200)
        } finally {
            try { track.stop() } catch (_: IllegalStateException) {}
            track.release()
            audioTrack = null
            am.abandonAudioFocusRequest(focusRequest)
        }
    }

    private fun buildProgressNotification(text: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, OpenMobileTtsApp.CHANNEL_PROGRESS)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Open Mobile TTS")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun updateProgressNotification(text: String) {
        try {
            val notification = buildProgressNotification(text)
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Missing POST_NOTIFICATIONS permission on Android 13+ — non-fatal
        }
    }

    private fun showCompletionNotification() {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, OpenMobileTtsApp.CHANNEL_COMPLETE)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Audio Ready")
            .setContentText("Your speech has finished generating.")
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(this).notify(COMPLETE_NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Missing POST_NOTIFICATIONS permission — non-fatal
        }
    }

    fun stopGeneration() {
        generateJob?.cancel()
        audioTrack?.let { track ->
            try { track.stop() } catch (_: IllegalStateException) {}
            track.release()
        }
        audioTrack = null
        _status.value = Status.IDLE
        _statusMessage.value = "Stopped"
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onDestroy() {
        scope.cancel()
        audioTrack?.release()
        super.onDestroy()
    }
}
