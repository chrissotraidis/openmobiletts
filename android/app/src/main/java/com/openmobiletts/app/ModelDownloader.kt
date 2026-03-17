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

        // TTS model
        private const val TTS_MODEL_NAME = "kokoro-multi-lang-v1_0"
        private const val TTS_MODEL_URL =
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/$TTS_MODEL_NAME.tar.bz2"

        // STT model (Moonshine v2 Base / "Medium" — default)
        // Note: sherpa-onnx uses "base" for the larger model, "tiny" for the smaller
        private const val STT_MODEL_NAME = "sherpa-onnx-moonshine-base-en-int8"
        private const val STT_MODEL_URL =
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/$STT_MODEL_NAME.tar.bz2"

        // Legacy alias
        @Suppress("unused")
        private const val MODEL_NAME = TTS_MODEL_NAME
    }

    // ── TTS Model ──────────────────────────────────────────

    /**
     * Check if the TTS model is already downloaded.
     */
    fun isTtsModelDownloaded(destDir: File): Boolean {
        val modelDir = File(destDir, TTS_MODEL_NAME)
        return File(modelDir, "model.onnx").exists()
    }

    /** Legacy alias for [isTtsModelDownloaded]. */
    fun isModelDownloaded(destDir: File): Boolean = isTtsModelDownloaded(destDir)

    /**
     * Get the TTS model directory path.
     */
    fun getTtsModelDir(destDir: File): String {
        return File(destDir, TTS_MODEL_NAME).absolutePath
    }

    /** Legacy alias for [getTtsModelDir]. */
    fun getModelDir(destDir: File): String = getTtsModelDir(destDir)

    /**
     * Download and extract the TTS model.
     * [onProgress] is called with (bytesDownloaded, totalBytes) — totalBytes may be -1 if unknown.
     */
    suspend fun downloadTtsModel(
        destDir: File,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
    ) = downloadArchive(destDir, TTS_MODEL_NAME, TTS_MODEL_URL, "model.onnx", onProgress)

    /** Legacy alias for [downloadTtsModel]. */
    suspend fun download(
        destDir: File,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
    ) = downloadTtsModel(destDir, onProgress)

    // ── STT Model ──────────────────────────────────────────

    /**
     * Check if the STT model (Moonshine v2) is already downloaded.
     */
    fun isSttModelDownloaded(destDir: File): Boolean {
        val modelDir = File(destDir, STT_MODEL_NAME)
        // Moonshine v2 uses preprocess.onnx as marker
        return File(modelDir, "preprocess.onnx").exists()
    }

    /**
     * Get the STT model directory path.
     */
    fun getSttModelDir(destDir: File): String {
        return File(destDir, STT_MODEL_NAME).absolutePath
    }

    /**
     * Download and extract the STT model (Moonshine v2 Medium).
     */
    suspend fun downloadSttModel(
        destDir: File,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
    ) = downloadArchive(destDir, STT_MODEL_NAME, STT_MODEL_URL, "preprocess.onnx", onProgress)

    // ── Shared Download Logic ──────────────────────────────

    /**
     * Download and extract a tar.bz2 model archive.
     * [markerFile] is checked to determine if already downloaded.
     */
    private suspend fun downloadArchive(
        destDir: File,
        modelName: String,
        modelUrl: String,
        markerFile: String,
        onProgress: (Long, Long) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val modelDir = File(destDir, modelName)
        if (File(modelDir, markerFile).exists()) {
            Log.i(TAG, "Model already exists at: $modelDir")
            return@withContext
        }

        Log.i(TAG, "Downloading model from: $modelUrl")
        val tempFile = File(destDir, "$modelName.tar.bz2")

        val openedConnections = mutableListOf<HttpURLConnection>()
        try {
            // Download — follow redirects manually to handle GitHub CDN
            val url = URL(modelUrl)
            var conn = url.openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = true
            conn.connect()
            openedConnections.add(conn)
            var redirectCount = 0
            while (conn.responseCode in 301..308 && redirectCount < 5) {
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

            Log.i(TAG, "Model extracted to: ${File(destDir, modelName)}")
        } finally {
            openedConnections.forEach { it.disconnect() }
            tempFile.delete()
        }
    }
}
