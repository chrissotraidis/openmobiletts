package com.openmobiletts.app

import android.content.Context
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.io.DataInputStream
import java.io.InputStream
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
     * Splits text into sentences, generates audio for each, and streams
     * using the TIMING/AUDIO protocol matching the Python server.
     *
     * Uses a bounded QueueInputStream (50 entries) with backpressure:
     * the generation thread blocks when the queue is full, preventing OOM
     * for very long texts when the client reads slowly.
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

                val modelFile = java.io.File("$modelDir/model.onnx")
                val voicesFile = java.io.File("$modelDir/voices.bin")
                val tokensFile = java.io.File("$modelDir/tokens.txt")
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

        // Bounded stream with backpressure: generation thread blocks when
        // queue is full (50 entries), preventing OOM for long texts.
        val stream = QueueInputStream()

        // Send chunk count so the client can show accurate progress
        stream.enqueue("CHUNKS:${chunks.size}\n".toByteArray(Charsets.UTF_8))

        // Generate in a background thread
        Thread({
            try {
                var cumulativeTime = 0.0

                for ((chunkIndex, chunkText) in chunks.withIndex()) {
                    if (chunkText.isBlank()) continue
                    if (stream.cancelled) {
                        AppLog.i(TAG, "Stream cancelled at chunk $chunkIndex")
                        break
                    }

                    AppLog.i(TAG, "Generating chunk $chunkIndex/${chunks.size}: '${chunkText.take(50)}...'")
                    val samples = runBlocking {
                        ttsManager.generate(text = chunkText, sid = sid, speed = speed)
                    }
                    AppLog.i(TAG, "Chunk $chunkIndex: got ${samples.size} samples")

                    if (samples.isEmpty()) {
                        AppLog.w(TAG, "Chunk $chunkIndex returned empty samples, skipping")
                        continue
                    }

                    val duration = samples.size.toDouble() / TtsManager.SAMPLE_RATE

                    // Encode as AAC (6x smaller than WAV) with WAV fallback
                    val audioBytes = try {
                        AacEncoder.encode(samples)
                    } catch (e: Exception) {
                        AppLog.w(TAG, "AAC encoding failed, falling back to WAV: ${e.message}")
                        WavEncoder.encode(samples)
                    }

                    val timing = JSONObject().apply {
                        put("text", chunkText)
                        put("start", cumulativeTime)
                        put("end", cumulativeTime + duration)
                        put("chunk_index", chunkIndex)
                        put("starts_paragraph", chunkIndex == 0)
                    }

                    stream.enqueue("TIMING:${timing}\n".toByteArray(Charsets.UTF_8))
                    stream.enqueue("AUDIO:${audioBytes.size}\n".toByteArray(Charsets.UTF_8))
                    stream.enqueue(audioBytes)

                    cumulativeTime += duration
                    AppLog.i(TAG, "Streamed chunk $chunkIndex: ${audioBytes.size} bytes, duration=${String.format("%.2f", duration)}s")
                }

                stream.finish()
                AppLog.i(TAG, "TTS stream complete: ${chunks.size} chunks, total=${String.format("%.2f", cumulativeTime)}s")
            } catch (e: Exception) {
                AppLog.e(TAG, "TTS generation error", e)
                stream.finishWithError(e)
            }
        }, "tts-generate").start()

        return newChunkedResponse(Response.Status.OK, "application/octet-stream", stream).apply {
            addHeader("Cache-Control", "no-cache")
            addHeader("X-Content-Type-Options", "nosniff")
            addHeader("X-Accel-Buffering", "no")
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
            "txt" -> "text/plain"
            "xml" -> "application/xml"
            else -> "application/octet-stream"
        }
    }
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
