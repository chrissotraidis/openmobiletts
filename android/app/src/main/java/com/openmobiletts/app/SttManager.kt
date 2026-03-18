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
 * Wraps Sherpa-ONNX OfflineRecognizer (Moonshine v2) with suspend functions.
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
    }

    private val initMutex = Mutex()
    private val transcribeMutex = Mutex()

    @Volatile
    private var recognizer: OfflineRecognizer? = null

    val isInitialized: Boolean
        get() = recognizer != null

    /**
     * Initialize the STT engine with Moonshine v2 model files at [modelDir].
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
                AppLog.i(TAG, "STT engine initialized successfully (Moonshine v2)")
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

            AppLog.i(TAG, "Transcribing ${samples.size} samples (${samples.size / sampleRate}s audio)")

            val stream = engine.createStream()
            try {
                stream.acceptWaveform(samples, sampleRate)
                engine.decode(stream)
                val result = engine.getResult(stream)

                AppLog.i(TAG, "Transcription result: ${result.text.take(100)}")
                result.text.trim()
            } finally {
                stream.release()
            }
        }
    }

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
