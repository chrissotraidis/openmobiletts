package com.openmobiletts.app

import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineMoonshineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Wraps Sherpa-ONNX OfflineRecognizer (Moonshine v1 Base English INT8).
 *
 * Mirrors TtsManager's structure: two Mutex locks for init and transcribe,
 * coroutine-based, IO dispatcher. Model files must be downloaded before [init].
 *
 * Thread safety: [transcribe] is serialized by [transcribeMutex] because the
 * underlying Sherpa-ONNX JNI object is NOT thread-safe for concurrent calls.
 */
class SttManager {

    companion object {
        private const val TAG = "SttManager"
        const val SAMPLE_RATE = 16000
        private const val WINDOW_SECONDS = 25
        private const val OVERLAP_SECONDS = 1
    }

    private val initMutex = Mutex()
    private val transcribeMutex = Mutex()

    @Volatile
    private var recognizer: OfflineRecognizer? = null

    val isInitialized: Boolean
        get() = recognizer != null

    /**
     * Initialize the STT engine with Moonshine v1 Base model files at [modelDir].
     * Must be called from a coroutine (runs on IO dispatcher).
     * Uses a Mutex to prevent double-init from concurrent requests.
     */
    suspend fun init(modelDir: String) {
        initMutex.withLock {
            if (recognizer != null) return  // Already initialized

            withContext(Dispatchers.IO) {
                AppLog.i(TAG, "Initializing Sherpa-ONNX STT (Moonshine) from: $modelDir")

                // Discover actual model file names — INT8 models use ".int8.onnx" suffix
                val dir = java.io.File(modelDir)
                val files = dir.listFiles()?.map { it.name } ?: emptyList()

                // Find encoder file (encode.onnx or encode.int8.onnx)
                val encoderFile = files.firstOrNull { it.startsWith("encode") && it.endsWith(".onnx") }
                    ?: throw IllegalStateException("No encoder file found in $modelDir")

                // Find preprocessor file
                val preprocessorFile = files.firstOrNull { it.startsWith("preprocess") && it.endsWith(".onnx") }
                    ?: throw IllegalStateException("No preprocessor file found in $modelDir")

                // Find split decoder files (this .so version only supports split decoder)
                val uncachedDecoderFile = files.firstOrNull { it.startsWith("uncached_decode") && it.endsWith(".onnx") }
                val cachedDecoderFile = files.firstOrNull { it.startsWith("cached_decode") && it.endsWith(".onnx") }

                AppLog.i(TAG, "Model files: encoder=$encoderFile, preprocessor=$preprocessorFile, " +
                    "uncached=${uncachedDecoderFile}, cached=${cachedDecoderFile}")

                val moonshineConfig = if (uncachedDecoderFile != null && cachedDecoderFile != null) {
                    OfflineMoonshineModelConfig(
                        preprocessor = "$modelDir/$preprocessorFile",
                        encoder = "$modelDir/$encoderFile",
                        uncachedDecoder = "$modelDir/$uncachedDecoderFile",
                        cachedDecoder = "$modelDir/$cachedDecoderFile",
                    )
                } else {
                    throw IllegalStateException("No decoder files found in $modelDir. Files: $files")
                }

                val config = OfflineRecognizerConfig(
                    featConfig = FeatureConfig(
                        sampleRate = SAMPLE_RATE,
                        featureDim = 80,
                    ),
                    modelConfig = OfflineModelConfig(
                        moonshine = moonshineConfig,
                        numThreads = 4,
                        tokens = "$modelDir/tokens.txt",
                        modelType = "moonshine",
                    ),
                    decodingMethod = "greedy_search",
                )

                recognizer = OfflineRecognizer(config = config)
                AppLog.i(TAG, "STT engine initialized successfully (Moonshine v1 Base)")
            }
        }
    }

    /**
     * Transcribe PCM audio [samples] at [sampleRate] Hz to text.
     * Returns the recognized text string.
     *
     * Input audio should be mono float samples normalized to [-1, 1].
     * If [sampleRate] differs from 16000, the caller should resample first.
     *
     * Serialized by [transcribeMutex] — Sherpa-ONNX is not thread-safe.
     */
    suspend fun transcribe(
        samples: FloatArray,
        sampleRate: Int = SAMPLE_RATE,
    ): String = withContext(Dispatchers.IO) {
        transcribeMutex.withLock {
            val engine = recognizer ?: throw IllegalStateException("STT not initialized")

            require(sampleRate == SAMPLE_RATE) { "STT input must be 16 kHz mono PCM" }
            require(samples.size <= AudioDecoder.MAX_DURATION_SECONDS * sampleRate) {
                "Audio is longer than the ${AudioDecoder.MAX_DURATION_SECONDS / 60}-minute Android limit"
            }

            AppLog.i(TAG, "Transcribing ${samples.size} samples (${samples.size / sampleRate}s audio)")
            val windowSamples = WINDOW_SECONDS * sampleRate
            val overlapSamples = OVERLAP_SECONDS * sampleRate
            var start = 0
            var transcript = ""
            var windowCount = 0
            while (start < samples.size) {
                val end = minOf(start + windowSamples, samples.size)
                val window = if (start == 0 && end == samples.size) {
                    samples
                } else {
                    samples.copyOfRange(start, end)
                }
                transcript = mergeTranscript(transcript, transcribeWindow(engine, window, sampleRate))
                windowCount += 1
                if (end == samples.size) break
                start = end - overlapSamples
            }
            AppLog.i(TAG, "Transcription completed: windows=$windowCount, characters=${transcript.length}")
            transcript
        }
    }

    private fun transcribeWindow(
        engine: OfflineRecognizer,
        samples: FloatArray,
        sampleRate: Int,
    ): String {
        val stream = engine.createStream()
        return try {
            stream.acceptWaveform(samples, sampleRate)
            engine.decode(stream)
            engine.getResult(stream).text.trim()
        } finally {
            stream.release()
        }
    }

    internal fun mergeTranscript(existing: String, next: String): String {
        if (existing.isBlank()) return next.trim()
        if (next.isBlank()) return existing.trim()
        val left = existing.trim().split(Regex("\\s+"))
        val right = next.trim().split(Regex("\\s+"))
        val maxOverlap = minOf(12, left.size, right.size)
        var overlap = 0
        for (size in maxOverlap downTo 1) {
            val leftTail = left.takeLast(size).map(::normalizeWord)
            val rightHead = right.take(size).map(::normalizeWord)
            if (leftTail == rightHead) {
                overlap = size
                break
            }
        }
        return (left + right.drop(overlap)).joinToString(" ").trim()
    }

    private fun normalizeWord(value: String): String =
        value.lowercase().replace(Regex("[^\\p{L}\\p{N}']"), "")

    fun release() {
        // Block until any in-progress transcription completes before freeing native memory
        kotlinx.coroutines.runBlocking {
            transcribeMutex.withLock {
                val r = recognizer
                recognizer = null
                r?.release()
            }
        }
        AppLog.i(TAG, "STT engine released")
    }
}
