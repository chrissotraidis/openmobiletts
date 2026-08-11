package com.openmobiletts.app

import android.content.Context

/** Persists the last successfully activated on-device TTS model. */
object TtsModelSelection {
    private const val PREFS = "tts_models"
    private const val ACTIVE_MODEL = "active_model_id"

    fun activeModelId(context: Context, downloader: ModelDownloader): String {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(ACTIVE_MODEL, ModelDownloader.DEFAULT_TTS_MODEL_ID)
            ?: ModelDownloader.DEFAULT_TTS_MODEL_ID
        return if (runCatching { downloader.isTtsModelDownloaded(context.filesDir, saved) }.getOrDefault(false)) {
            saved
        } else {
            ModelDownloader.DEFAULT_TTS_MODEL_ID
        }
    }

    fun save(context: Context, modelId: String) {
        check(
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(ACTIVE_MODEL, modelId)
            .commit(),
        ) { "Could not persist the selected voice model" }
    }
}
