package com.openmobiletts.app

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleOwner
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

enum class DownloadModel(
    val wireName: String,
    val uniqueWorkName: String,
    val ttsModelId: String? = null,
) {
    TTS("tts", "model-download-tts", ModelDownloader.DEFAULT_TTS_MODEL_ID),
    KITTEN_MINI("kitten-mini-en-v0_8", "model-download-kitten-mini", "kitten-mini-en-v0_8"),
    KITTEN_MICRO("kitten-micro-en-v0_8", "model-download-kitten-micro", "kitten-micro-en-v0_8"),
    STT("stt", "model-download-stt");

    companion object {
        fun fromWireName(value: String?): DownloadModel? = entries.find { it.wireName == value }
        fun forTtsModelId(modelId: String): DownloadModel? = entries.find { it.ttsModelId == modelId }
    }
}

class ModelDownloadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val model = DownloadModel.fromWireName(inputData.getString(KEY_MODEL))
            ?: return Result.failure(workDataOf(KEY_ERROR to "Unknown model download"))
        val downloader = ModelDownloader(applicationContext)
        val totalBytes = model.ttsModelId?.let { downloader.ttsModel(it).archiveBytes }
            ?: downloader.sttModel.archiveBytes

        setForeground(foregroundInfo(model, 0L, totalBytes))
        return try {
            val progress: (Long, Long) -> Unit = { downloaded, total ->
                setProgressAsync(
                    workDataOf(
                        KEY_DOWNLOADED_BYTES to downloaded,
                        KEY_TOTAL_BYTES to total,
                    ),
                )
                setForegroundAsync(foregroundInfo(model, downloaded, total))
            }
            if (model.ttsModelId != null) {
                downloader.downloadTtsModel(
                    applicationContext.filesDir,
                    model.ttsModelId,
                    progress,
                )
            } else {
                downloader.downloadSttModel(
                    applicationContext.filesDir,
                    progress,
                    ::smokeTestStt,
                )
            }
            Result.success(
                workDataOf(
                    KEY_DOWNLOADED_BYTES to totalBytes,
                    KEY_TOTAL_BYTES to totalBytes,
                ),
            )
        } catch (error: IOException) {
            AppLog.w(TAG, "${model.name} model download interrupted: ${error.message}")
            if (runAttemptCount < MAX_RETRIES) Result.retry() else failure(error)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            AppLog.e(TAG, "${model.name} model download failed", error)
            failure(error)
        }
    }

    private fun failure(error: Exception): Result = Result.failure(
        workDataOf(KEY_ERROR to (error.message ?: "Model download failed")),
    )

    private suspend fun smokeTestStt(modelDir: String) {
        val manager = SttManager()
        try {
            manager.init(modelDir)
        } finally {
            manager.release()
        }
    }

    private fun foregroundInfo(model: DownloadModel, downloaded: Long, total: Long): ForegroundInfo {
        val percent = if (total > 0L) ((downloaded * 100L) / total).toInt().coerceIn(0, 100) else 0
        val label = if (model.ttsModelId != null) "voice" else "speech-to-text"
        val openApp = PendingIntent.getActivity(
            applicationContext,
            model.ordinal,
            Intent(applicationContext, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification: Notification = NotificationCompat.Builder(
            applicationContext,
            OpenMobileTtsApp.CHANNEL_MODEL_DOWNLOAD,
        )
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Downloading $label model")
            .setContentText(if (total > 0L) "$percent% complete" else "Starting download")
            .setProgress(100, percent, total <= 0L)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp)
            .build()

        val id = NOTIFICATION_BASE_ID + model.ordinal
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(id, notification)
        }
    }

    companion object {
        private const val TAG = "ModelDownloadWorker"
        private const val NOTIFICATION_BASE_ID = 2300
        private const val MAX_RETRIES = 3
        const val KEY_MODEL = "model"
        const val KEY_DOWNLOADED_BYTES = "downloaded_bytes"
        const val KEY_TOTAL_BYTES = "total_bytes"
        const val KEY_ERROR = "error"
    }
}

object ModelDownloadWork {
    fun enqueue(context: Context, model: DownloadModel): WorkInfo? {
        val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setInputData(workDataOf(ModelDownloadWorker.KEY_MODEL to model.wireName))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .addTag(model.uniqueWorkName)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            model.uniqueWorkName,
            ExistingWorkPolicy.KEEP,
            request,
        )
        return info(context, model)
    }

    fun cancel(context: Context, model: DownloadModel) {
        WorkManager.getInstance(context).cancelUniqueWork(model.uniqueWorkName)
    }

    fun observe(context: Context, owner: LifecycleOwner, model: DownloadModel, observer: (WorkInfo?) -> Unit) {
        WorkManager.getInstance(context)
            .getWorkInfosForUniqueWorkLiveData(model.uniqueWorkName)
            .observe(owner) { work -> observer(work.lastOrNull()) }
    }

    fun info(context: Context, model: DownloadModel): WorkInfo? = try {
        WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(model.uniqueWorkName)
            .get(2, TimeUnit.SECONDS)
            .lastOrNull()
    } catch (_: Exception) {
        null
    }
}
