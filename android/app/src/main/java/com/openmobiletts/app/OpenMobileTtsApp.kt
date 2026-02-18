package com.openmobiletts.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class OpenMobileTtsApp : Application() {

    // Shared TtsManager — survives Activity recreation, accessible by Service
    val ttsManager = TtsManager()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)

            // Ongoing channel for generation progress (low importance = no sound)
            val progressChannel = NotificationChannel(
                CHANNEL_PROGRESS,
                "TTS Generation",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Shows progress while generating speech"
            }

            // Completion channel (default importance = sound + heads-up)
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

    companion object {
        const val CHANNEL_PROGRESS = "tts_progress"
        const val CHANNEL_COMPLETE = "tts_complete"
    }
}
