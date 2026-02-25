package com.openmobiletts.app

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * Foreground service that keeps the process alive during TTS generation
 * and audio playback. The JS bridge (window.Android) controls the lifecycle:
 *   onGenerationStarted() -> start service + wake lock
 *   onPlaybackStarted()   -> update notification + wake lock
 *   onPlaybackPaused()    -> release wake lock, keep service
 *   onPlaybackStopped()   -> release wake lock + stop service
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

        /** Static reference for the JS bridge to access the running instance. */
        @Volatile
        var instance: TtsService? = null
            private set

        fun start(context: Context, message: String = "Generating speech...", wakeLock: Boolean = false) {
            val intent = Intent(context, TtsService::class.java).apply {
                putExtra("message", message)
                putExtra("acquire_wake_lock", wakeLock)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, TtsService::class.java))
        }
    }

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        instance = this
        val message = intent?.getStringExtra("message") ?: "Generating speech..."
        startForeground(NOTIFICATION_ID, buildNotification(message))
        if (intent?.getBooleanExtra("acquire_wake_lock", false) == true) {
            acquireWakeLock()
        }
        return START_NOT_STICKY
    }

    fun updateNotification(text: String) {
        try {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, buildNotification(text))
        } catch (_: SecurityException) {}
    }

    fun updateProgress(current: Int, total: Int) {
        // Re-acquire wake lock if somehow released — defense-in-depth
        acquireWakeLock()
        try {
            val text = if (total > 0) "Generating speech... $current/$total chunks" else "Generating speech..."
            val notification = NotificationCompat.Builder(this, OpenMobileTtsApp.CHANNEL_PROGRESS)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("Open Mobile TTS")
                .setContentText(text)
                .setOngoing(true)
                .setContentIntent(
                    PendingIntent.getActivity(
                        this, 0,
                        Intent(this, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                        },
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    )
                )
                .apply {
                    if (total > 0) setProgress(total, current, false)
                    else setProgress(0, 0, true)
                }
                .build()
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {}
    }

    fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        @Suppress("WakelockTimeout") // Intentionally untimed — released explicitly on pause/stop/destroy
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
            acquire()
        }
        Log.d(TAG, "Wake lock acquired (no timeout)")
    }

    fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                Log.d(TAG, "Wake lock released")
            }
        }
        wakeLock = null
    }

    private fun buildNotification(text: String): Notification {
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

    override fun onDestroy() {
        releaseWakeLock()
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
