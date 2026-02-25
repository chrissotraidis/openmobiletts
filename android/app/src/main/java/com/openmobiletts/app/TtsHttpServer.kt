package com.openmobiletts.app

import android.content.Context
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.DataInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Embedded HTTP server that mirrors the FastAPI server's dual role:
 * serves the SvelteKit static build AND the TTS API endpoints.
 *
 * Binds to 127.0.0.1 only — not accessible from the network.
 */
class TtsHttpServer(
    private val context: Context,
    private val ttsManager: TtsManager,
    port: Int = PORT,
) : NanoHTTPD("127.0.0.1", port) {

    companion object {
        private const val TAG = "TtsHttpServer"
        const val PORT = 8080
        private const val JOB_TTL_MS = 2 * 60 * 60 * 1000L // 2 hours
    }

    /** Active and recently completed generation jobs. */
    private val jobs = ConcurrentHashMap<String, TtsJob>()

    init {
        // Clean up leftover job files from previous sessions
        val jobsDir = File(context.filesDir, "tts_jobs")
        if (jobsDir.exists()) {
            jobsDir.deleteRecursively()
        }
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        val method = session.method
        AppLog.d(TAG, "Request: $method $uri")

        return try {
            when {
                uri == "/api/voices" -> handleVoices()
                uri == "/api/health" -> handleHealth()
                uri == "/api/engines" -> handleEngines()
                uri == "/api/engine" -> handleEngine()
                uri == "/api/engine/switch" && method == Method.POST -> handleEngineSwitch(session)
                uri == "/api/tts/stream" && method == Method.POST -> handleTtsStream(session)
                uri.startsWith("/api/tts/jobs/") -> handleJobRequest(session, uri)
                uri.startsWith("/api/logs") -> handleLogs()
                uri == "/api/documents/upload" && method == Method.POST -> handleDocumentUpload(session)
                else -> serveStaticAsset(uri)
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "Error handling $method $uri", e)
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                MIME_JSON,
                """{"detail":"${e.message?.replace("\"", "\\\"")}"}"""
            )
        }
    }

    // ---------- API endpoints ----------

    private fun handleVoices(): Response {
        return newFixedLengthResponse(Response.Status.OK, MIME_JSON, VoiceRegistry.toJsonArray())
    }

    private fun handleHealth(): Response {
        return newFixedLengthResponse(
            Response.Status.OK, MIME_JSON,
            """{"status":"healthy","version":"2.0.0","engine":"sherpa-onnx"}"""
        )
    }

    private fun handleEngines(): Response {
        val json = """[{"name":"sherpa-onnx","label":"Sherpa-ONNX (On-Device)","available":true,"active":true}]"""
        return newFixedLengthResponse(Response.Status.OK, MIME_JSON, json)
    }

    private fun handleEngine(): Response {
        return newFixedLengthResponse(
            Response.Status.OK, MIME_JSON,
            """{"engine":"sherpa-onnx"}"""
        )
    }

    private fun handleEngineSwitch(session: IHTTPSession): Response {
        return newFixedLengthResponse(
            Response.Status.OK, MIME_JSON,
            """{"engine":"sherpa-onnx","voices":${VoiceRegistry.voices.size}}"""
        )
    }

    private fun handleLogs(): Response {
        return newFixedLengthResponse(Response.Status.OK, MIME_JSON, AppLog.exportJson())
    }

    private fun handleDocumentUpload(session: IHTTPSession): Response {
        try {
            val files = HashMap<String, String>()
            session.parseBody(files)

            val tmpFilePath = files["file"]
                ?: return newFixedLengthResponse(
                    Response.Status.BAD_REQUEST, MIME_JSON,
                    """{"detail":"No file uploaded"}"""
                )

            val originalFilename = session.parms["file"] ?: "upload.txt"
            val tmpFile = java.io.File(tmpFilePath)

            val text = try {
                DocumentExtractor.extract(tmpFile, originalFilename)
            } catch (e: IllegalArgumentException) {
                return newFixedLengthResponse(
                    Response.Status.BAD_REQUEST, MIME_JSON,
                    """{"detail":"${e.message?.replace("\"", "\\\"")}"}"""
                )
            }

            if (text.isBlank()) {
                return newFixedLengthResponse(
                    Response.Status.BAD_REQUEST, MIME_JSON,
                    """{"detail":"File is empty"}"""
                )
            }

            AppLog.i(TAG, "Document uploaded: $originalFilename (${text.length} chars)")

            val response = JSONObject().apply {
                put("filename", originalFilename)
                put("text", text)
                put("chunk_count", 0)
            }

            return newFixedLengthResponse(Response.Status.OK, MIME_JSON, response.toString())
        } catch (e: Exception) {
            AppLog.e(TAG, "Document upload failed", e)
            return newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR, MIME_JSON,
                """{"detail":"Upload failed: ${e.message?.replace("\"", "\\\"")}"}"""
            )
        }
    }

    /**
     * Core TTS streaming endpoint.
     *
     * Creates a background generation job that writes audio directly to disk
     * as chunks are produced. The HTTP stream provides real-time progress,
     * but generation continues even if the stream drops (e.g. WebView
     * throttled when backgrounded). The client can recover the completed
     * audio via /api/tts/jobs/{id}/audio.
     *
     * Stream protocol:
     *   JOB:{id}\n              — job ID for recovery
     *   CHUNKS:{total}\n        — total chunk count
     *   TIMING:{json}\n         — per-chunk timing metadata
     *   AUDIO:{length}\n        — audio byte count
     *   {audio bytes}           — encoded audio data
     */
    private fun handleTtsStream(session: IHTTPSession): Response {
        // Parse JSON body (cap at 1MB to prevent OOM from malicious Content-Length)
        val maxBody = 1 * 1024 * 1024
        val contentLength = (session.headers["content-length"]?.toIntOrNull() ?: 0).coerceAtMost(maxBody)
        val bodyBytes = ByteArray(contentLength)
        DataInputStream(session.inputStream).readFully(bodyBytes)
        val body = JSONObject(String(bodyBytes))

        val text = body.optString("text", "").trim()
        val voiceName = body.optString("voice", "af_heart")
        val speed = body.optDouble("speed", 1.0).toFloat()

        if (text.isEmpty()) {
            return newFixedLengthResponse(
                Response.Status.BAD_REQUEST, MIME_JSON,
                """{"detail":"Text cannot be empty"}"""
            )
        }

        val sid = VoiceRegistry.sidForName(voiceName) ?: 3
        AppLog.i(TAG, "TTS request: voice=$voiceName (sid=$sid), speed=$speed, text_length=${text.length}")

        // Ensure TTS engine is initialized BEFORE starting the stream
        try {
            if (!ttsManager.isInitialized) {
                val modelDir = ModelDownloader().getModelDir(context.filesDir)
                AppLog.i(TAG, "Initializing TTS engine from: $modelDir")

                val modelFile = File("$modelDir/model.onnx")
                val voicesFile = File("$modelDir/voices.bin")
                val tokensFile = File("$modelDir/tokens.txt")
                AppLog.i(TAG, "Model files: model=${modelFile.exists()} (${modelFile.length()}), voices=${voicesFile.exists()}, tokens=${tokensFile.exists()}")

                if (!modelFile.exists()) {
                    return newFixedLengthResponse(
                        Response.Status.INTERNAL_ERROR, MIME_JSON,
                        """{"detail":"Model file not found: ${modelFile.absolutePath}"}"""
                    )
                }

                runBlocking {
                    ttsManager.init(modelDir)
                }
                AppLog.i(TAG, "TTS engine initialized successfully")
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "TTS engine initialization failed", e)
            return newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR, MIME_JSON,
                """{"detail":"TTS engine failed to initialize: ${e.message?.replace("\"", "\\\"")}"}"""
            )
        }

        // Split text into sentence-sized chunks
        val chunks = splitIntoSentences(text)
        AppLog.i(TAG, "Split into ${chunks.size} chunks")

        // Create a job for disk-based recovery
        cleanupOldJobs()
        val jobId = java.util.UUID.randomUUID().toString().take(8)
        val jobDir = File(context.filesDir, "tts_jobs/$jobId")
        jobDir.mkdirs()
        val job = TtsJob(id = jobId, jobDir = jobDir, totalChunks = chunks.size)
        jobs[jobId] = job
        AppLog.i(TAG, "Created job $jobId with ${chunks.size} chunks")

        // Bounded stream with backpressure for real-time progress
        val stream = QueueInputStream()

        // Send job ID and chunk count so the client can recover if the stream drops
        stream.enqueue("JOB:$jobId\n".toByteArray(Charsets.UTF_8))
        stream.enqueue("CHUNKS:${chunks.size}\n".toByteArray(Charsets.UTF_8))

        // Generate in a background thread — writes to disk AND streams.
        // Streaming is non-blocking: if the client is too slow (WebView
        // backgrounded), the stream is abandoned and generation continues
        // to disk. The client recovers via /api/tts/jobs/{id}/audio.
        Thread({
            var audioOutputStream: FileOutputStream? = null
            try {
                audioOutputStream = FileOutputStream(job.audioFile)
                var cumulativeTime = 0.0
                var aacAvailable = true
                var streamAlive = true // False once streaming is abandoned

                for ((chunkIndex, chunkText) in chunks.withIndex()) {
                    if (chunkText.isBlank()) continue
                    if (job.cancelled) {
                        AppLog.i(TAG, "Job $jobId cancelled at chunk $chunkIndex")
                        break
                    }

                    AppLog.i(TAG, "Job $jobId: generating chunk $chunkIndex/${chunks.size}: '${chunkText.take(50)}...'")
                    val samples = runBlocking {
                        ttsManager.generate(text = chunkText, sid = sid, speed = speed)
                    }
                    AppLog.i(TAG, "Job $jobId: chunk $chunkIndex got ${samples.size} samples")

                    if (samples.isEmpty()) {
                        AppLog.w(TAG, "Job $jobId: chunk $chunkIndex returned empty samples, skipping")
                        continue
                    }

                    val duration = samples.size.toDouble() / TtsManager.SAMPLE_RATE

                    // Encode as AAC (concatenatable ADTS frames) with WAV fallback
                    val audioBytes: ByteArray
                    if (aacAvailable) {
                        audioBytes = try {
                            AacEncoder.encode(samples)
                        } catch (e: Exception) {
                            AppLog.w(TAG, "Job $jobId: AAC encoding failed, falling back to WAV: ${e.message}")
                            aacAvailable = false
                            job.audioFormat = "audio/wav"
                            WavEncoder.encode(samples)
                        }
                    } else {
                        audioBytes = WavEncoder.encode(samples)
                    }

                    val timing = JSONObject().apply {
                        put("text", chunkText)
                        put("start", cumulativeTime)
                        put("end", cumulativeTime + duration)
                        put("chunk_index", chunkIndex)
                        put("starts_paragraph", chunkIndex == 0)
                    }

                    // Always write to disk first (survives stream drops)
                    audioOutputStream.write(audioBytes)
                    audioOutputStream.flush()

                    // Record timing
                    job.timingEntries.add(timing)
                    job.completedChunks = chunkIndex + 1

                    // Non-blocking stream to client. If the queue is full (client
                    // too slow / WebView throttled), abandon the stream entirely
                    // and continue writing to disk only. The client detects the
                    // incomplete stream and recovers via the job endpoint.
                    if (streamAlive && !stream.cancelled) {
                        val offered = stream.tryEnqueue("TIMING:$timing\n".toByteArray(Charsets.UTF_8))
                                && stream.tryEnqueue("AUDIO:${audioBytes.size}\n".toByteArray(Charsets.UTF_8))
                                && stream.tryEnqueue(audioBytes)
                        if (!offered) {
                            streamAlive = false
                            AppLog.w(TAG, "Job $jobId: stream queue full at chunk $chunkIndex, " +
                                    "continuing disk-only (client can recover via job endpoint)")
                        }
                    }

                    cumulativeTime += duration
                    AppLog.i(TAG, "Job $jobId: chunk $chunkIndex done — ${audioBytes.size} bytes, ${String.format("%.2f", duration)}s" +
                            if (!streamAlive || stream.cancelled) " (disk-only)" else "")
                }

                audioOutputStream.close()
                audioOutputStream = null

                // Write timing data to disk (snapshot under lock to avoid ConcurrentModificationException)
                val timingSnapshot: List<JSONObject>
                synchronized(job.timingEntries) {
                    timingSnapshot = ArrayList(job.timingEntries)
                }
                val timingJson = JSONArray(timingSnapshot)
                job.timingFile.writeText(timingJson.toString())

                if (job.cancelled) {
                    job.status = TtsJob.Status.CANCELLED
                    job.completedAt = System.currentTimeMillis()
                    AppLog.i(TAG, "Job $jobId cancelled: ${job.completedChunks}/${job.totalChunks} chunks")
                } else {
                    job.status = TtsJob.Status.COMPLETE
                    job.completedAt = System.currentTimeMillis()
                    AppLog.i(TAG, "Job $jobId complete: ${job.completedChunks}/${job.totalChunks} chunks, " +
                            "audio=${job.audioFile.length()} bytes")
                }

                // Terminate the HTTP stream. If streaming was abandoned (client
                // too slow), cancel to free the queue; otherwise finish cleanly.
                if (streamAlive && !stream.cancelled) {
                    stream.finish()
                } else {
                    stream.cancel()
                }
            } catch (e: Exception) {
                AppLog.e(TAG, "Job $jobId error", e)
                job.error = e.message
                job.status = TtsJob.Status.ERROR
                job.completedAt = System.currentTimeMillis()
                stream.finishWithError(e)
            } finally {
                audioOutputStream?.close()
            }
        }, "tts-generate-$jobId").start()

        return newChunkedResponse(Response.Status.OK, "application/octet-stream", stream).apply {
            addHeader("Cache-Control", "no-cache")
            addHeader("X-Content-Type-Options", "nosniff")
            addHeader("X-Accel-Buffering", "no")
        }
    }

    // ---------- Job recovery endpoints ----------

    private fun handleJobRequest(session: IHTTPSession, uri: String): Response {
        // Parse: /api/tts/jobs/{id}/{action}
        val parts = uri.removePrefix("/api/tts/jobs/").split("/", limit = 2)
        val jobId = parts[0]
        val action = parts.getOrElse(1) { "status" }

        val job = jobs[jobId]
            ?: return newFixedLengthResponse(
                Response.Status.NOT_FOUND, MIME_JSON,
                """{"detail":"Job not found"}"""
            )

        return when (action) {
            "status" -> handleJobStatus(job)
            "audio" -> handleJobAudio(job)
            "timing" -> handleJobTiming(job)
            "cancel" -> handleJobCancel(job)
            else -> newFixedLengthResponse(
                Response.Status.NOT_FOUND, MIME_JSON,
                """{"detail":"Unknown job action: $action"}"""
            )
        }
    }

    private fun handleJobStatus(job: TtsJob): Response {
        val json = JSONObject().apply {
            put("id", job.id)
            put("status", job.status.name.lowercase())
            put("completed", job.completedChunks)
            put("total", job.totalChunks)
            put("format", job.audioFormat)
            if (job.error != null) put("error", job.error)
        }
        return newFixedLengthResponse(Response.Status.OK, MIME_JSON, json.toString())
    }

    private fun handleJobAudio(job: TtsJob): Response {
        if (job.status != TtsJob.Status.COMPLETE) {
            return newFixedLengthResponse(
                Response.Status.BAD_REQUEST, MIME_JSON,
                """{"detail":"Job not complete (status: ${job.status.name.lowercase()})"}"""
            )
        }
        if (!job.audioFile.exists()) {
            return newFixedLengthResponse(
                Response.Status.NOT_FOUND, MIME_JSON,
                """{"detail":"Audio file not found on disk"}"""
            )
        }

        val bytes = job.audioFile.readBytes()
        return newFixedLengthResponse(
            Response.Status.OK, job.audioFormat,
            bytes.inputStream(), bytes.size.toLong()
        )
    }

    private fun handleJobTiming(job: TtsJob): Response {
        if (job.status != TtsJob.Status.COMPLETE) {
            // Return partial timing data if still generating (snapshot under lock)
            val snapshot: List<JSONObject>
            synchronized(job.timingEntries) {
                snapshot = ArrayList(job.timingEntries)
            }
            return newFixedLengthResponse(Response.Status.OK, MIME_JSON, JSONArray(snapshot).toString())
        }
        if (job.timingFile.exists()) {
            val json = job.timingFile.readText()
            return newFixedLengthResponse(Response.Status.OK, MIME_JSON, json)
        }
        val snapshot: List<JSONObject>
        synchronized(job.timingEntries) {
            snapshot = ArrayList(job.timingEntries)
        }
        return newFixedLengthResponse(Response.Status.OK, MIME_JSON, JSONArray(snapshot).toString())
    }

    private fun handleJobCancel(job: TtsJob): Response {
        job.cancelled = true
        AppLog.i(TAG, "Job ${job.id} cancel requested")
        return newFixedLengthResponse(
            Response.Status.OK, MIME_JSON,
            """{"cancelled":true}"""
        )
    }

    /** Remove jobs older than JOB_TTL_MS. */
    private fun cleanupOldJobs() {
        val now = System.currentTimeMillis()
        val expired = jobs.entries.filter { (_, job) ->
            job.status != TtsJob.Status.GENERATING &&
                    job.completedAt > 0 &&
                    (now - job.completedAt) > JOB_TTL_MS
        }
        for ((id, job) in expired) {
            job.jobDir.deleteRecursively()
            jobs.remove(id)
            AppLog.d(TAG, "Cleaned up expired job $id")
        }
    }

    // ---------- Text chunking ----------

    private fun splitIntoSentences(text: String): List<String> {
        val sentences = mutableListOf<String>()
        val current = StringBuilder()
        val chars = text.toCharArray()
        var i = 0

        while (i < chars.size) {
            current.append(chars[i])

            if (chars[i] in ".!?" && current.length > 1) {
                // Heuristic: sentence boundary if followed by space+uppercase or end of text.
                // This avoids breaking on abbreviations like "Dr." or "U.S."
                var j = i + 1
                // Skip trailing quotes/brackets that belong to this sentence
                while (j < chars.size && chars[j] in "\"')\u201D") j++

                val isEnd = j >= chars.size ||
                        (j < chars.size && chars[j] in " \t\n" &&
                                j + 1 < chars.size && (chars[j + 1].isUpperCase() || chars[j + 1] in "\"\u201C")) ||
                        (j < chars.size && chars[j] == '\n')

                if (isEnd) {
                    // Include any trailing quotes
                    while (i + 1 < chars.size && chars[i + 1] in "\"')\u201D") {
                        i++
                        current.append(chars[i])
                    }
                    val trimmed = current.toString().trim()
                    if (trimmed.isNotEmpty()) {
                        sentences.add(trimmed)
                        current.clear()
                    }
                }
            }
            i++
        }

        val remaining = current.toString().trim()
        if (remaining.isNotEmpty()) {
            sentences.add(remaining)
        }

        if (sentences.isEmpty() && text.isNotBlank()) {
            sentences.add(text.trim())
        }

        return groupAndSplitChunks(sentences, targetLength = 200, maxLength = 300)
    }

    /**
     * Group short sentences together (up to targetLength) and split
     * any single sentence exceeding maxLength at clause/word boundaries.
     */
    private fun groupAndSplitChunks(
        sentences: List<String>,
        targetLength: Int,
        maxLength: Int,
    ): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()

        for (sentence in sentences) {
            val parts = if (sentence.length > maxLength) {
                splitLongText(sentence, maxLength)
            } else {
                listOf(sentence)
            }

            for (part in parts) {
                if (current.isEmpty()) {
                    current.append(part)
                } else if (current.length + part.length + 1 <= targetLength) {
                    current.append(" ").append(part)
                } else {
                    result.add(current.toString())
                    current.clear()
                    current.append(part)
                }
            }
        }

        if (current.isNotEmpty()) {
            result.add(current.toString())
        }

        return result
    }

    /**
     * Split text exceeding maxLength at clause boundaries
     * (commas, semicolons, colons) or word boundaries.
     */
    private fun splitLongText(text: String, maxLength: Int): List<String> {
        if (text.length <= maxLength) return listOf(text)

        val result = mutableListOf<String>()
        var remaining = text

        while (remaining.length > maxLength) {
            var breakAt = -1

            // Prefer clause-level breaks (comma, semicolon, colon)
            for (i in maxLength downTo maxLength / 2) {
                if (i < remaining.length && remaining[i] in ",;:\u2014\u2013") {
                    breakAt = i + 1
                    break
                }
            }

            // Fall back to word boundary
            if (breakAt == -1) {
                for (i in maxLength downTo maxLength / 2) {
                    if (i < remaining.length && remaining[i] == ' ') {
                        breakAt = i
                        break
                    }
                }
            }

            // Last resort: hard break
            if (breakAt == -1) breakAt = maxLength

            val chunk = remaining.substring(0, breakAt).trim()
            if (chunk.isNotEmpty()) result.add(chunk)
            val newRemaining = remaining.substring(breakAt).trim()
            if (newRemaining.length >= remaining.length) break // safety: ensure forward progress
            remaining = newRemaining
        }

        if (remaining.isNotEmpty()) result.add(remaining)

        return result
    }

    // ---------- Static file serving ----------

    private fun serveStaticAsset(uri: String): Response {
        var path = uri.trimStart('/')
        if (path.isEmpty()) path = "index.html"

        return try {
            val input = context.assets.open("webapp/$path")
            val mimeType = guessMimeType(path)
            newChunkedResponse(Response.Status.OK, mimeType, input)
        } catch (e: Exception) {
            // SPA fallback — serve index.html for unmatched routes
            try {
                val input = context.assets.open("webapp/index.html")
                newChunkedResponse(Response.Status.OK, "text/html", input)
            } catch (e2: Exception) {
                newFixedLengthResponse(
                    Response.Status.NOT_FOUND, MIME_PLAINTEXT,
                    "Not found: $uri"
                )
            }
        }
    }

    private fun guessMimeType(path: String): String {
        val ext = path.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "html" -> "text/html"
            "js" -> "application/javascript"
            "css" -> "text/css"
            "json" -> "application/json"
            "png" -> "image/png"
            "svg" -> "image/svg+xml"
            "ico" -> "image/x-icon"
            "woff" -> "font/woff"
            "woff2" -> "font/woff2"
            "ttf" -> "font/ttf"
            "webmanifest" -> "application/manifest+json"
            "webp" -> "image/webp"
            "wav" -> "audio/wav"
            "mp3" -> "audio/mpeg"
            "aac" -> "audio/aac"
            "txt" -> "text/plain"
            "xml" -> "application/xml"
            else -> "application/octet-stream"
        }
    }
}

/**
 * Tracks a background TTS generation job that writes audio directly to disk.
 * Generation continues even if the WebView HTTP stream drops — the client
 * can recover the completed audio via /api/tts/jobs/{id}/audio.
 */
class TtsJob(
    val id: String,
    val jobDir: File,
    val totalChunks: Int,
) {
    val audioFile: File = File(jobDir, "audio.aac")
    val timingFile: File = File(jobDir, "timing.json")

    @Volatile var completedChunks: Int = 0
    @Volatile var status: Status = Status.GENERATING
    @Volatile var cancelled: Boolean = false
    @Volatile var error: String? = null
    @Volatile var audioFormat: String = "audio/aac"
    @Volatile var completedAt: Long = 0

    val timingEntries: MutableList<JSONObject> =
        java.util.Collections.synchronizedList(mutableListOf())

    enum class Status { GENERATING, COMPLETE, ERROR, CANCELLED }
}

/**
 * Backpressure-aware InputStream backed by a bounded queue.
 *
 * The writer (generation thread) calls [enqueue] which blocks if the queue
 * is full (50 entries) — this prevents OOM for very long texts when the
 * client reads slowly (e.g. WebView throttled when backgrounded).
 *
 * The reader (NanoHTTPD thread) calls [read] which blocks only until
 * data is available.
 */
private class QueueInputStream : InputStream() {
    private val queue = LinkedBlockingQueue<ByteArray>(50)

    @Volatile
    var cancelled = false
        private set

    @Volatile
    private var finished = false

    @Volatile
    private var error: Throwable? = null

    private var current: ByteArray? = null
    private var pos = 0

    /** Enqueue data for the reader. Blocks if the queue is full until space is available or cancelled. */
    fun enqueue(data: ByteArray) {
        while (!cancelled) {
            if (queue.offer(data, 500, TimeUnit.MILLISECONDS)) return
        }
    }

    /**
     * Non-blocking enqueue — returns false if the queue is full after [timeoutMs].
     * Used by the generation thread so it can fall back to disk-only mode
     * instead of blocking indefinitely when the client reads too slowly.
     */
    fun tryEnqueue(data: ByteArray, timeoutMs: Long = 5000): Boolean {
        if (cancelled) return false
        return queue.offer(data, timeoutMs, TimeUnit.MILLISECONDS)
    }

    /** Signal that no more data will be produced. */
    fun finish() {
        finished = true
        // Use blocking offer loop since bounded queue may be full
        while (!queue.offer(SENTINEL, 500, TimeUnit.MILLISECONDS)) {
            if (cancelled) return
        }
    }

    /** Signal an error — reader will throw IOException. */
    fun finishWithError(e: Throwable) {
        error = e
        finish()
    }

    /** Cancel from the reader side (e.g. connection closed). */
    fun cancel() {
        cancelled = true
        queue.clear() // Free memory immediately
        queue.offer(SENTINEL) // Unblock any waiting enqueue
    }

    override fun read(): Int {
        val buf = ByteArray(1)
        val n = read(buf, 0, 1)
        return if (n == -1) -1 else buf[0].toInt() and 0xFF
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        while (true) {
            error?.let { throw java.io.IOException(it.message, it) }

            val c = current
            if (c != null && pos < c.size) {
                val avail = c.size - pos
                val n = minOf(avail, len)
                System.arraycopy(c, pos, b, off, n)
                pos += n
                if (pos >= c.size) {
                    current = null
                    pos = 0
                }
                return n
            }

            val next = queue.poll(500, TimeUnit.MILLISECONDS)
            if (next == null) {
                if (finished || cancelled) return -1
                continue
            }
            if (next.isEmpty()) {
                if (finished) return -1
                continue
            }
            current = next
            pos = 0
        }
    }

    override fun close() {
        if (!finished) cancel()
        super.close()
    }

    companion object {
        private val SENTINEL = ByteArray(0)
    }
}

private const val MIME_JSON = "application/json"
