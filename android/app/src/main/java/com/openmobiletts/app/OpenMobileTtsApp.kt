package com.openmobiletts.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class OpenMobileTtsApp : Application() {

    companion object {
        private const val TAG = "OpenMobileTtsApp"
        const val CHANNEL_PROGRESS = "tts_progress"
        const val CHANNEL_COMPLETE = "tts_complete"
        const val PORT = 8080
    }

    val ttsManager = TtsManager()
    var httpServer: TtsHttpServer? = null
        private set

    override fun onCreate() {
        super.onCreate()
        AppLog.init(filesDir) // Initialize persistent logging FIRST
        createNotificationChannels()
        DocumentExtractor.init(this)
    }

    fun isModelDownloaded(): Boolean {
        return ModelDownloader().isModelDownloaded(filesDir)
    }

    fun ensureServerRunning() {
        if (httpServer?.isAlive == true) {
            AppLog.i(TAG, "Server already running on port $PORT")
            return
        }
        // Diagnostic: list webapp assets
        try {
            val topLevel = assets.list("webapp") ?: emptyArray()
            AppLog.i(TAG, "webapp/ assets: ${topLevel.joinToString()}")
            if (topLevel.contains("_app")) {
                val appLevel = assets.list("webapp/_app") ?: emptyArray()
                AppLog.i(TAG, "webapp/_app/ assets: ${appLevel.joinToString()}")
                if (appLevel.contains("immutable")) {
                    val immLevel = assets.list("webapp/_app/immutable") ?: emptyArray()
                    AppLog.i(TAG, "webapp/_app/immutable/ assets: ${immLevel.joinToString()}")
                    if (immLevel.contains("chunks")) {
                        val chunks = assets.list("webapp/_app/immutable/chunks") ?: emptyArray()
                        AppLog.i(TAG, "webapp/_app/immutable/chunks/ assets: ${chunks.joinToString()}")
                    }
                }
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to list assets", e)
        }

        AppLog.i(TAG, "Starting TtsHttpServer on port $PORT...")
        val server = TtsHttpServer(this, ttsManager, PORT)
        server.start()
        httpServer = server
        AppLog.i(TAG, "Server started, alive=${server.isAlive}")
    }

    fun stopServer() {
        httpServer?.stop()
        httpServer = null
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)

            val progressChannel = NotificationChannel(
                CHANNEL_PROGRESS,
                "TTS Generation",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Shows progress while generating speech"
            }

            val completeChannel = NotificationChannel(
                CHANNEL_COMPLETE,
                "Audio Ready",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Notifies when speech generation is complete"
            }

            nm.createNotificationChannel(progressChannel)
            nm.createNotificationChannel(completeChannel)
        }
    }

    override fun onTerminate() {
        stopServer()
        ttsManager.release()
        super.onTerminate()
    }

}
