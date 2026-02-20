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
 * Downloads the Sherpa-ONNX Kokoro multi-lang model on first launch.
 *
 * Downloads to [destDir]/kokoro-multi-lang-v1_0/ (~350 MB).
 * Uses v1_0 which has all 53 voices with correct SID mapping.
 * (v1_1 is Chinese-focused with only 3 English voices and different SIDs.)
 */
class ModelDownloader {

    companion object {
        private const val TAG = "ModelDownloader"
        private const val MODEL_NAME = "kokoro-multi-lang-v1_0"
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

        val openedConnections = mutableListOf<HttpURLConnection>()
        try {
            // Download — follow redirects manually to handle GitHub CDN
            val url = URL(MODEL_URL)
            var conn = url.openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = true
            conn.connect()
            openedConnections.add(conn)
            var redirectCount = 0
            while (conn.responseCode in 301..302 && redirectCount < 5) {
                val newUrl = conn.getHeaderField("Location")
                conn = URL(newUrl).openConnection() as HttpURLConnection
                conn.instanceFollowRedirects = true
                conn.connect()
                openedConnections.add(conn)
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
            val canonicalDest = destDir.canonicalFile
            destDir.mkdirs()
            BZip2CompressorInputStream(BufferedInputStream(tempFile.inputStream())).use { bzIn ->
                TarArchiveInputStream(bzIn).use { tarIn ->
                    var entry = tarIn.nextTarEntry
                    while (entry != null) {
                        val outFile = File(destDir, entry.name).canonicalFile
                        // Guard against path traversal (Zip Slip)
                        if (!outFile.path.startsWith(canonicalDest.path)) {
                            Log.w(TAG, "Skipping tar entry outside target dir: ${entry.name}")
                            entry = tarIn.nextTarEntry
                            continue
                        }
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
            openedConnections.forEach { it.disconnect() }
            tempFile.delete()
        }
    }
}
