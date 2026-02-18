package com.openmobiletts.app

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream

/**
 * Downloads the Sherpa-ONNX Kokoro INT8 model on first launch.
 *
 * Downloads to [destDir]/kokoro-int8-multi-lang-v1_1/ (~95 MB).
 * The INT8 quantized model is smaller and faster on mobile devices.
 */
class ModelDownloader {

    companion object {
        private const val TAG = "ModelDownloader"
        private const val MODEL_NAME = "kokoro-int8-multi-lang-v1_1"
        private const val MODEL_URL =
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/$MODEL_NAME.tar.bz2"
    }

    /**
     * Check if the model is already downloaded.
     */
    fun isModelDownloaded(destDir: File): Boolean {
        val modelDir = File(destDir, MODEL_NAME)
        return File(modelDir, "model.onnx").exists()
    }

    /**
     * Get the model directory path.
     */
    fun getModelDir(destDir: File): String {
        return File(destDir, MODEL_NAME).absolutePath
    }

    /**
     * Download and extract the model.
     * [onProgress] is called with (bytesDownloaded, totalBytes) — totalBytes may be -1 if unknown.
     */
    suspend fun download(
        destDir: File,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
    ) = withContext(Dispatchers.IO) {
        val modelDir = File(destDir, MODEL_NAME)
        if (File(modelDir, "model.onnx").exists()) {
            Log.i(TAG, "Model already exists at: $modelDir")
            return@withContext
        }

        Log.i(TAG, "Downloading model from: $MODEL_URL")
        val tempFile = File(destDir, "$MODEL_NAME.tar.bz2")

        try {
            // Download
            val url = URL(MODEL_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = true
            connection.connect()

            // GitHub redirects — follow manually if needed
            var conn = connection
            var redirectCount = 0
            while (conn.responseCode in 301..302 && redirectCount < 5) {
                val newUrl = conn.getHeaderField("Location")
                conn = URL(newUrl).openConnection() as HttpURLConnection
                conn.instanceFollowRedirects = true
                conn.connect()
                redirectCount++
            }

            val totalBytes = conn.contentLengthLong
            Log.i(TAG, "Download size: ${totalBytes / 1024 / 1024} MB")

            BufferedInputStream(conn.inputStream).use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalRead = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        onProgress(totalRead, totalBytes)
                    }
                }
            }

            Log.i(TAG, "Download complete, extracting...")

            // Extract tar.bz2
            destDir.mkdirs()
            BZip2CompressorInputStream(BufferedInputStream(tempFile.inputStream())).use { bzIn ->
                TarArchiveInputStream(bzIn).use { tarIn ->
                    var entry = tarIn.nextTarEntry
                    while (entry != null) {
                        val outFile = File(destDir, entry.name)
                        if (entry.isDirectory) {
                            outFile.mkdirs()
                        } else {
                            outFile.parentFile?.mkdirs()
                            FileOutputStream(outFile).use { out ->
                                tarIn.copyTo(out)
                            }
                        }
                        entry = tarIn.nextTarEntry
                    }
                }
            }

            Log.i(TAG, "Model extracted to: $modelDir")

        } finally {
            tempFile.delete()
        }
    }
}
