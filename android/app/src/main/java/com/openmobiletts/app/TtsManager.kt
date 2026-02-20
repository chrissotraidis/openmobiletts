package com.openmobiletts.app

import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Wraps Sherpa-ONNX OfflineTts with suspend functions for init/generate.
 *
 * Model files must be downloaded to [modelDir] before calling [init].
 */
class TtsManager {

    companion object {
        private const val TAG = "TtsManager"
        const val SAMPLE_RATE = 24000
    }

    private val initMutex = Mutex()

    @Volatile
    private var tts: OfflineTts? = null

    val isInitialized: Boolean
        get() = tts != null

    /**
     * Initialize the TTS engine with model files at [modelDir].
     * Must be called from a coroutine (runs on IO dispatcher).
     * Uses a Mutex to prevent double-init from concurrent HTTP requests.
     */
    suspend fun init(modelDir: String) {
        initMutex.withLock {
            if (tts != null) return  // Already initialized

            withContext(Dispatchers.IO) {
                AppLog.i(TAG, "Initializing Sherpa-ONNX TTS from: $modelDir")

                // Build lexicon paths — multi-lang models may have multiple lexicons
                val lexiconFiles = java.io.File(modelDir).listFiles { _, name -> name.startsWith("lexicon-") && name.endsWith(".txt") }
                val lexicon = lexiconFiles?.sorted()?.joinToString(",") { it.absolutePath } ?: ""

                // Build dict dir if present
                val dictDirFile = java.io.File("$modelDir/dict")
                val dictDir = if (dictDirFile.exists()) dictDirFile.absolutePath else ""

                // Build rule FSTs if present
                val fstFiles = java.io.File(modelDir).listFiles { _, name -> name.endsWith(".fst") }
                val ruleFsts = fstFiles?.sorted()?.joinToString(",") { it.absolutePath } ?: ""

                val config = OfflineTtsConfig(
                    model = OfflineTtsModelConfig(
                        kokoro = OfflineTtsKokoroModelConfig(
                            model = "$modelDir/model.onnx",
                            voices = "$modelDir/voices.bin",
                            tokens = "$modelDir/tokens.txt",
                            dataDir = "$modelDir/espeak-ng-data",
                            lexicon = lexicon,
                            dictDir = dictDir,
                        ),
                        numThreads = 4,
                    ),
                    ruleFsts = ruleFsts,
                )

                tts = OfflineTts(config = config)
                AppLog.i(TAG, "TTS engine initialized successfully")
            }
        }
    }

    /**
     * Generate speech audio for [text] using speaker [sid] at [speed].
     * Returns a FloatArray of PCM samples at [SAMPLE_RATE] Hz.
     */
    suspend fun generate(
        text: String,
        sid: Int = 3, // af_heart
        speed: Float = 1.0f,
    ): FloatArray = withContext(Dispatchers.IO) {
        val engine = tts ?: throw IllegalStateException("TTS not initialized")

        AppLog.i(TAG, "Generating speech: sid=$sid, speed=$speed, text=${text.take(50)}")
        val audio = engine.generate(text = text, sid = sid, speed = speed)
        AppLog.i(TAG, "Generated ${audio.samples.size} samples")

        audio.samples
    }

    fun release() {
        tts = null
        AppLog.i(TAG, "TTS engine released")
    }
}
