package com.openmobiletts.app

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.media.app.NotificationCompat.MediaStyle
import android.graphics.Color

/**
 * Foreground service that keeps the process alive during TTS generation
 * and audio playback. The JS bridge (window.Android) controls the lifecycle:
 *   onGenerationStarted() -> start service + wake lock
 *   onPlaybackStarted()   -> update notification + wake lock (media controls)
 *   onPlaybackPaused()    -> release wake lock, show play button
 *   onPlaybackStopped()   -> release wake lock + stop service
 *
 * During playback, the notification provides media transport controls
 * (play/pause/stop) via a MediaSession, allowing control from the
 * notification bar, lock screen, and Bluetooth devices.
 *
 * The wake lock is held without a timeout so generation can run for any
 * duration. It is explicitly released via releaseWakeLock() on pause/stop,
 * and in onDestroy() as a safety net. If the process is killed, Android
 * reclaims the lock automatically.
 */
class TtsService : Service() {

    companion object {
        private const val TAG = "TtsService"
        private const val NOTIFICATION_ID = 1
        private const val WAKE_LOCK_TAG = "OpenMobileTTS::Playback"
        private const val ACTION_PLAY = "com.openmobiletts.ACTION_PLAY"
        private const val ACTION_PAUSE = "com.openmobiletts.ACTION_PAUSE"
        private const val ACTION_STOP = "com.openmobiletts.ACTION_STOP"
        private const val ACTION_NEXT = "com.openmobiletts.ACTION_NEXT"
        private const val ACTION_PREVIOUS = "com.openmobiletts.ACTION_PREVIOUS"

        /** Static reference for the JS bridge to access the running instance. */
        @Volatile
        var instance: TtsService? = null
            private set

        /** Callback to forward notification button presses to the WebView. */
        var playbackCommandCallback: ((String) -> Unit)? = null

        /** Callback for seek commands (passes position in ms). */
        var seekCallback: ((Long) -> Unit)? = null

        fun start(
            context: Context,
            message: String = "Generating speech...",
            wakeLock: Boolean = false,
            playbackMode: Boolean = false,
        ) {
            val intent = Intent(context, TtsService::class.java).apply {
                putExtra("message", message)
                putExtra("acquire_wake_lock", wakeLock)
                putExtra("playback_mode", playbackMode)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, TtsService::class.java))
        }
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private var mediaSession: MediaSessionCompat? = null
    private var lastPositionMs: Long = 0
    private var lastDurationMs: Long = 0
    private var generationStartTime: Long = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        setupMediaSession()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        instance = this

        // Handle notification action intents
        when (intent?.action) {
            ACTION_PLAY -> {
                playbackCommandCallback?.invoke("play")
                return START_NOT_STICKY
            }
            ACTION_PAUSE -> {
                playbackCommandCallback?.invoke("pause")
                return START_NOT_STICKY
            }
            ACTION_STOP -> {
                playbackCommandCallback?.invoke("stop")
                return START_NOT_STICKY
            }
            ACTION_NEXT -> {
                playbackCommandCallback?.invoke("next")
                return START_NOT_STICKY
            }
            ACTION_PREVIOUS -> {
                playbackCommandCallback?.invoke("previous")
                return START_NOT_STICKY
            }
        }

        val message = intent?.getStringExtra("message") ?: "Generating speech..."
        val playbackMode = intent?.getBooleanExtra("playback_mode", false) == true

        val notification = if (playbackMode) {
            val isPlaying = !message.contains("paused", ignoreCase = true)
            updateMediaSessionState(isPlaying, lastPositionMs, lastDurationMs)
            buildPlaybackNotification(isPlaying, lastPositionMs, lastDurationMs)
        } else {
            buildGenerationNotification(message)
        }

        startForeground(NOTIFICATION_ID, notification)
        if (intent?.getBooleanExtra("acquire_wake_lock", false) == true) {
            acquireWakeLock()
        }
        return START_NOT_STICKY
    }

    private fun setupMediaSession() {
        mediaSession = MediaSessionCompat(this, "OpenMobileTTS").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    playbackCommandCallback?.invoke("play")
                }
                override fun onPause() {
                    playbackCommandCallback?.invoke("pause")
                }
                override fun onStop() {
                    playbackCommandCallback?.invoke("stop")
                }
                override fun onSeekTo(pos: Long) {
                    seekCallback?.invoke(pos)
                }
                override fun onSkipToNext() {
                    playbackCommandCallback?.invoke("next")
                }
                override fun onSkipToPrevious() {
                    playbackCommandCallback?.invoke("previous")
                }
            })
            isActive = true
        }
    }

    fun updateNotification(text: String) {
        try {
            when {
                text.contains("paused", ignoreCase = true) -> {
                    updateMediaSessionState(false, lastPositionMs, lastDurationMs)
                    NotificationManagerCompat.from(this).notify(
                        NOTIFICATION_ID,
                        buildPlaybackNotification(false, lastPositionMs, lastDurationMs),
                    )
                }
                text.contains("Playing", ignoreCase = true) -> {
                    updateMediaSessionState(true, lastPositionMs, lastDurationMs)
                    NotificationManagerCompat.from(this).notify(
                        NOTIFICATION_ID,
                        buildPlaybackNotification(true, lastPositionMs, lastDurationMs),
                    )
                }
                else -> {
                    val genNotification = NotificationCompat.Builder(this, OpenMobileTtsApp.CHANNEL_PROGRESS)
                        .setSmallIcon(R.drawable.ic_notification)
                        .setContentTitle("Generating Speech")
                        .setContentText(text)
                        .setColor(Color.parseColor("#6c63ff"))
                        .setColorized(true)
                        .setOngoing(true)
                        .setContentIntent(buildContentIntent())
                        .build()
                    NotificationManagerCompat.from(this).notify(
                        NOTIFICATION_ID,
                        genNotification,
                    )
                }
            }
        } catch (_: SecurityException) {}
    }

    fun updatePlaybackProgress(positionMs: Long, durationMs: Long) {
        lastPositionMs = positionMs
        lastDurationMs = durationMs
        updateMediaSessionState(true, positionMs, durationMs)
        try {
            NotificationManagerCompat.from(this).notify(
                NOTIFICATION_ID,
                buildPlaybackNotification(true, positionMs, durationMs),
            )
        } catch (_: SecurityException) {}
    }

    fun updateProgress(current: Int, total: Int) {
        // Re-acquire wake lock if somehow released — defense-in-depth
        acquireWakeLock()
        if (generationStartTime == 0L) generationStartTime = System.currentTimeMillis()
        try {
            val percent = if (total > 0) (current * 100) / total else 0
            val elapsed = (System.currentTimeMillis() - generationStartTime) / 1000

            val title = if (total > 0) "Generating speech — $percent%" else "Generating speech..."
            val contentText = if (total > 0) {
                "Chunk $current of $total · ${formatTime(elapsed * 1000)}"
            } else {
                "Preparing..."
            }
            val subText = if (total > 0 && current > 1 && elapsed > 0) {
                val avgSecs = elapsed.toDouble() / current
                val remaining = ((total - current) * avgSecs).toLong()
                "~${formatTime(remaining * 1000)} remaining"
            } else null

            val notification = NotificationCompat.Builder(this, OpenMobileTtsApp.CHANNEL_PROGRESS)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(contentText)
                .setColor(Color.parseColor("#6c63ff"))
                .setColorized(true)
                .setOngoing(true)
                .setContentIntent(buildContentIntent())
                .apply {
                    if (total > 0) setProgress(total, current, false)
                    else setProgress(0, 0, true)
                    if (subText != null) setSubText(subText)
                }
                .build()
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {}
    }

    @Synchronized
    fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        @Suppress("WakelockTimeout") // Intentionally untimed — released explicitly on pause/stop/destroy
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
            acquire()
        }
        Log.d(TAG, "Wake lock acquired (no timeout)")
    }

    @Synchronized
    fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                Log.d(TAG, "Wake lock released")
            }
        }
        wakeLock = null
    }

    // ---- Notification builders ----

    private fun buildContentIntent(): PendingIntent {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun buildMediaActionIntent(action: String): PendingIntent {
        val intent = Intent(this, TtsService::class.java).apply { this.action = action }
        return PendingIntent.getForegroundService(
            this, action.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun buildGenerationNotification(text: String): Notification {
        generationStartTime = System.currentTimeMillis()
        return NotificationCompat.Builder(this, OpenMobileTtsApp.CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Generating Speech")
            .setContentText(text)
            .setColor(Color.parseColor("#6c63ff"))
            .setColorized(true)
            .setOngoing(true)
            .setProgress(0, 0, true)
            .setContentIntent(buildContentIntent())
            .build()
    }

    private fun buildPlaybackNotification(isPlaying: Boolean, positionMs: Long, durationMs: Long): Notification {
        val title = if (isPlaying) "Playing Audio" else "Paused"
        val statusText = if (durationMs > 0) {
            "${formatTime(positionMs)} / ${formatTime(durationMs)}"
        } else {
            if (isPlaying) "Playing..." else "Paused"
        }

        val builder = NotificationCompat.Builder(this, OpenMobileTtsApp.CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(statusText)
            .setSubText("Open Mobile TTS")
            .setColor(Color.parseColor("#6c63ff"))
            .setColorized(true)
            .setOngoing(isPlaying) // Allow swipe-to-dismiss when paused
            .setContentIntent(buildContentIntent())
            .setDeleteIntent(buildMediaActionIntent(ACTION_STOP)) // Swipe-to-dismiss stops playback

        // Previous action (action index 0)
        builder.addAction(
            NotificationCompat.Action.Builder(
                R.drawable.ic_skip_previous,
                "Previous",
                buildMediaActionIntent(ACTION_PREVIOUS),
            ).build(),
        )

        // Play/Pause toggle action (action index 1)
        if (isPlaying) {
            builder.addAction(
                NotificationCompat.Action.Builder(
                    android.R.drawable.ic_media_pause,
                    "Pause",
                    buildMediaActionIntent(ACTION_PAUSE),
                ).build(),
            )
        } else {
            builder.addAction(
                NotificationCompat.Action.Builder(
                    android.R.drawable.ic_media_play,
                    "Play",
                    buildMediaActionIntent(ACTION_PLAY),
                ).build(),
            )
        }

        // Next action (action index 2)
        builder.addAction(
            NotificationCompat.Action.Builder(
                R.drawable.ic_skip_next,
                "Next",
                buildMediaActionIntent(ACTION_NEXT),
            ).build(),
        )

        // MediaStyle for lock screen and notification shade integration
        // Show Previous (0), Play/Pause (1), Next (2) in compact view
        mediaSession?.sessionToken?.let { token ->
            builder.setStyle(
                MediaStyle()
                    .setMediaSession(token)
                    .setShowActionsInCompactView(0, 1, 2),
            )
        }

        return builder.build()
    }

    private fun updateMediaSessionState(isPlaying: Boolean, positionMs: Long, durationMs: Long) {
        val stateInt = if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        val actions = PlaybackStateCompat.ACTION_PLAY or
            PlaybackStateCompat.ACTION_PAUSE or
            PlaybackStateCompat.ACTION_STOP or
            PlaybackStateCompat.ACTION_SEEK_TO or
            PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS

        mediaSession?.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setState(stateInt, positionMs, if (isPlaying) 1f else 0f)
                .setActions(actions)
                .build(),
        )

        if (durationMs > 0) {
            mediaSession?.setMetadata(
                MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, "Open Mobile TTS")
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "Text-to-Speech")
                    .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, durationMs)
                    .build(),
            )
        }
    }

    private fun formatTime(ms: Long): String {
        val totalSecs = ms / 1000
        val mins = totalSecs / 60
        val secs = totalSecs % 60
        return "%d:%02d".format(mins, secs)
    }

    override fun onDestroy() {
        mediaSession?.apply {
            isActive = false
            release()
        }
        mediaSession = null
        releaseWakeLock()
        generationStartTime = 0
        instance = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        super.onDestroy()
    }
}
