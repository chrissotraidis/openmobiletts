package com.openmobiletts.app

import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import kotlinx.coroutines.Dispatchers
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

    private var tts: OfflineTts? = null

    val isInitialized: Boolean
        get() = tts != null

    /**
     * Initialize the TTS engine with model files at [modelDir].
     * Must be called from a coroutine (runs on IO dispatcher).
     */
    suspend fun init(modelDir: String) = withContext(Dispatchers.IO) {
        Log.i(TAG, "Initializing Sherpa-ONNX TTS from: $modelDir")

        // Build lexicon path — multi-lang models require it
        val lexiconFile = java.io.File("$modelDir/lexicon-us-en.txt")
        val lexicon = if (lexiconFile.exists()) lexiconFile.absolutePath else ""

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
                numThreads = 2,
            ),
            ruleFsts = ruleFsts,
        )

        tts = OfflineTts(config = config)
        Log.i(TAG, "TTS engine initialized successfully")
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

        Log.i(TAG, "Generating speech: sid=$sid, speed=$speed, text=${text.take(50)}")
        val audio = engine.generate(text = text, sid = sid, speed = speed)
        Log.i(TAG, "Generated ${audio.samples.size} samples")

        audio.samples
    }

    fun release() {
        tts = null
        Log.i(TAG, "TTS engine released")
    }
}
