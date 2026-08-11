package com.openmobiletts.app

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.UUID

/** Downloads, verifies, and activates the pinned on-device model archives. */
class ModelDownloader(context: Context) {

    private val catalog = ModelCatalog.load(context)
    internal val ttsModels = catalog.managedForRole("tts", "sherpa-onnx")
    internal val ttsModel = catalog.require(DEFAULT_TTS_MODEL_ID)
    internal val sttModel = catalog.requireRole("stt")

    init {
        check(ttsModels.isNotEmpty()) { "No Android TTS models are available" }
        check(ttsModels.all { it.minimumRuntimeVersion == AppMetadata.SHERPA_ONNX_VERSION })
        check(sttModel.minimumRuntimeVersion == AppMetadata.SHERPA_ONNX_VERSION)
    }

    companion object {
        private const val TAG = "ModelDownloader"
        private const val STORAGE_SAFETY_BYTES = 128L * 1024 * 1024
        const val DEFAULT_TTS_MODEL_ID = "kokoro-multi-lang-v1_0"

    }

    fun ttsModel(modelId: String): ModelCatalog.ModelSpec = catalog.require(modelId).also {
        check(it in ttsModels) { "Unsupported Android TTS model: $modelId" }
    }

    fun isTtsModelDownloaded(destDir: File, modelId: String = DEFAULT_TTS_MODEL_ID): Boolean =
        isComplete(destDir, ttsModel(modelId))

    fun minimumFirstRunFreeBytes(): Long =
        minimumTtsFreeBytes()

    fun minimumTtsFreeBytes(modelId: String = DEFAULT_TTS_MODEL_ID): Long =
        ttsModel(modelId).let { it.archiveBytes + it.installedBytes + STORAGE_SAFETY_BYTES }

    /** Legacy alias for [isTtsModelDownloaded]. */
    fun isModelDownloaded(destDir: File): Boolean = isTtsModelDownloaded(destDir)

    fun getTtsModelDir(destDir: File, modelId: String = DEFAULT_TTS_MODEL_ID): String =
        File(destDir, ttsModel(modelId).modelId).absolutePath

    /** Legacy alias for [getTtsModelDir]. */
    fun getModelDir(destDir: File): String = getTtsModelDir(destDir)

    suspend fun downloadTtsModel(
        destDir: File,
        modelId: String = DEFAULT_TTS_MODEL_ID,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
        beforeActivate: suspend (String) -> Unit = {},
    ) = downloadArchive(destDir, ttsModel(modelId), onProgress, beforeActivate)

    /** Legacy alias for [downloadTtsModel]. */
    suspend fun download(
        destDir: File,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
        beforeActivate: suspend (String) -> Unit = {},
    ) = downloadTtsModel(destDir, DEFAULT_TTS_MODEL_ID, onProgress, beforeActivate)

    fun removeExperimentalTtsModel(destDir: File, modelId: String): Boolean {
        val spec = ttsModel(modelId)
        check(spec.status == "experimental") { "Only experimental models can be removed" }
        return File(destDir, spec.modelId).let { !it.exists() || it.deleteRecursively() }
    }

    fun isSttModelDownloaded(destDir: File): Boolean = isComplete(destDir, sttModel)

    fun getSttModelDir(destDir: File): String = File(destDir, sttModel.modelId).absolutePath

    suspend fun downloadSttModel(
        destDir: File,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
        beforeActivate: suspend (String) -> Unit = {},
    ) = downloadArchive(destDir, sttModel, onProgress, beforeActivate)

    private fun isComplete(destDir: File, spec: ModelCatalog.ModelSpec): Boolean {
        val modelDir = File(destDir, spec.modelId)
        return modelDir.isDirectory && spec.requiredFiles.all { name ->
            File(modelDir, name).let { it.isFile && it.length() > 0L }
        } && spec.requiredDirectories.all { name ->
            File(modelDir, name).let { it.isDirectory && !it.list().isNullOrEmpty() }
        }
    }

    private suspend fun downloadArchive(
        destDir: File,
        spec: ModelCatalog.ModelSpec,
        onProgress: (Long, Long) -> Unit,
        beforeActivate: suspend (String) -> Unit,
    ) = withContext(Dispatchers.IO) {
        if (isComplete(destDir, spec)) {
            Log.i(TAG, "Model already verified at ${File(destDir, spec.modelId)}")
            return@withContext
        }

        check(destDir.exists() || destDir.mkdirs()) { "Unable to create model storage" }
        val downloadDir = File(destDir, ".model-downloads")
        check(downloadDir.exists() || downloadDir.mkdirs()) { "Unable to create download storage" }
        val archiveFile = File(downloadDir, "${spec.modelId}.part")
        if (archiveFile.length() > spec.archiveBytes) archiveFile.delete()
        val remainingArchiveBytes = (spec.archiveBytes - archiveFile.length()).coerceAtLeast(0L)
        val requiredSpace = remainingArchiveBytes + spec.installedBytes + STORAGE_SAFETY_BYTES
        check(destDir.usableSpace >= requiredSpace) {
            "Not enough storage. Free at least ${requiredSpace / 1024 / 1024} MB and retry."
        }

        val stagingDir = File(destDir, ".${spec.modelId}-staging-${UUID.randomUUID()}")
        val extractDir = File(stagingDir, "extracted")
        check(stagingDir.mkdirs()) { "Unable to create model staging directory" }

        try {
            downloadAndVerify(spec, archiveFile, onProgress)
            safeExtract(archiveFile, extractDir)

            val nestedCandidate = File(extractDir, spec.modelId)
            val candidate = if (nestedCandidate.isDirectory) nestedCandidate else extractDir
            check(spec.requiredFiles.all { File(candidate, it).let { file -> file.isFile && file.length() > 0L } }) {
                "Model archive is missing required files"
            }
            check(spec.requiredDirectories.all { File(candidate, it).let { dir -> dir.isDirectory && !dir.list().isNullOrEmpty() } }) {
                "Model archive is missing required directories"
            }

            currentCoroutineContext().ensureActive()
            beforeActivate(candidate.absolutePath)
            currentCoroutineContext().ensureActive()
            activate(destDir, spec, candidate)
            archiveFile.delete()
            Log.i(TAG, "Model verified and activated at ${File(destDir, spec.modelId)}")
        } finally {
            stagingDir.deleteRecursively()
        }
    }

    private suspend fun downloadAndVerify(
        spec: ModelCatalog.ModelSpec,
        destination: File,
        onProgress: (Long, Long) -> Unit,
    ) {
        var existingBytes = destination.length().coerceAtMost(spec.archiveBytes)
        if (existingBytes == spec.archiveBytes) {
            if (sha256(destination) == spec.sha256) {
                onProgress(existingBytes, spec.archiveBytes)
                return
            }
            destination.delete()
            existingBytes = 0L
        }

        val connection = (URL(spec.url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 30_000
            readTimeout = 60_000
            setRequestProperty("User-Agent", "Open-Mobile-TTS-model-installer")
            if (existingBytes > 0L) setRequestProperty("Range", "bytes=$existingBytes-")
        }

        try {
            val status = connection.responseCode
            val append = existingBytes > 0L && status == HttpURLConnection.HTTP_PARTIAL
            if (existingBytes > 0L && status == HttpURLConnection.HTTP_OK) {
                existingBytes = 0L
            } else {
                check(status == HttpURLConnection.HTTP_OK || status == HttpURLConnection.HTTP_PARTIAL) {
                    "Model download failed with HTTP $status"
                }
            }
            val contentType = connection.contentType.orEmpty().lowercase()
            check(!contentType.startsWith("text/html")) { "Model server returned an HTML page" }
            val reportedBytes = connection.contentLengthLong
            if (reportedBytes >= 0L) {
                val expectedResponseBytes = spec.archiveBytes - existingBytes
                check(reportedBytes == expectedResponseBytes) {
                    "Unexpected archive size: server reported $reportedBytes bytes"
                }
            }
            if (append) {
                val contentRange = connection.getHeaderField("Content-Range").orEmpty()
                check(contentRange.startsWith("bytes $existingBytes-")) {
                    "Model server returned an invalid resume range"
                }
            }

            var downloaded = existingBytes
            onProgress(downloaded, spec.archiveBytes)
            BufferedInputStream(connection.inputStream).use { input ->
                FileOutputStream(destination, append).use { output ->
                    val buffer = ByteArray(1024 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        downloaded += count
                        onProgress(downloaded, spec.archiveBytes)
                    }
                }
            }

            check(downloaded == spec.archiveBytes) {
                "Incomplete model download: expected ${spec.archiveBytes} bytes, got $downloaded"
            }
            if (sha256(destination) != spec.sha256) {
                destination.delete()
                error("Model archive checksum verification failed")
            }
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        BufferedInputStream(file.inputStream()).use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun safeExtract(archiveFile: File, destination: File) {
        check(destination.mkdirs()) { "Unable to create extraction directory" }
        val root = destination.canonicalFile
        val rootPrefix = root.path + File.separator

        BZip2CompressorInputStream(BufferedInputStream(archiveFile.inputStream())).use { bzInput ->
            TarArchiveInputStream(bzInput).use { tarInput ->
                var entry = tarInput.nextEntry
                while (entry != null) {
                    check(!entry.isSymbolicLink && !entry.isLink) {
                        "Model archive contains an unsupported link: ${entry.name}"
                    }
                    val output = File(destination, entry.name).canonicalFile
                    check(output == root || output.path.startsWith(rootPrefix)) {
                        "Unsafe path in model archive: ${entry.name}"
                    }
                    if (entry.isDirectory) {
                        check(output.exists() || output.mkdirs()) { "Unable to create ${entry.name}" }
                    } else {
                        check(output.parentFile?.let { it.exists() || it.mkdirs() } == true) {
                            "Unable to create parent directory for ${entry.name}"
                        }
                        FileOutputStream(output).use { tarInput.copyTo(it) }
                    }
                    entry = tarInput.nextEntry
                }
            }
        }
    }

    private fun activate(destDir: File, spec: ModelCatalog.ModelSpec, candidate: File) {
        val target = File(destDir, spec.modelId)
        val backup = File(destDir, ".${spec.modelId}-backup-${UUID.randomUUID()}")
        val hadPrevious = target.exists()

        if (hadPrevious) check(target.renameTo(backup)) { "Unable to preserve the current model" }
        try {
            if (!candidate.renameTo(target)) {
                candidate.copyRecursively(target, overwrite = false)
            }
            check(isComplete(destDir, spec)) { "Activated model failed validation" }
            backup.deleteRecursively()
        } catch (error: Exception) {
            target.deleteRecursively()
            if (hadPrevious && backup.exists()) backup.renameTo(target)
            throw error
        }
    }
}
