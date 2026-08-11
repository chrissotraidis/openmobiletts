package com.openmobiletts.app

import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKittenModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.GenerationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Wraps Sherpa-ONNX OfflineTts with suspend functions for init/generate.
 *
 * Model files must be downloaded to [modelDir] before calling [init].
 *
 * Thread safety: [generate] is serialized by [generateMutex] because the
 * underlying Sherpa-ONNX OfflineTts JNI object is NOT thread-safe for
 * concurrent calls. If a new generation starts while an old one is still
 * running (user re-generates), the new one waits for the old to finish.
 */
class TtsManager {

    companion object {
        private const val TAG = "TtsManager"
        const val SAMPLE_RATE = 24000
    }

    private val initMutex = Mutex()
    private val generateMutex = Mutex()

    @Volatile
    private var tts: OfflineTts? = null

    @Volatile
    var activeModelId: String? = null
        private set

    val isInitialized: Boolean
        get() = tts != null

    /**
     * Initialize the TTS engine with model files at [modelDir].
     * Must be called from a coroutine (runs on IO dispatcher).
     * Uses a Mutex to prevent double-init from concurrent HTTP requests.
     */
    suspend fun init(modelDir: String, modelId: String = ModelDownloader.DEFAULT_TTS_MODEL_ID) {
        initMutex.withLock {
            if (tts != null) {
                check(activeModelId == modelId) {
                    "Changing the TTS model requires a clean app restart"
                }
                return
            }

            val candidate = withContext(Dispatchers.IO) {
                AppLog.i(TAG, "Initializing Sherpa-ONNX TTS model $modelId from: $modelDir")

                // Build lexicon paths — multi-lang models may have multiple lexicons
                val lexiconFiles = java.io.File(modelDir).listFiles { _, name -> name.startsWith("lexicon-") && name.endsWith(".txt") }
                val lexicon = lexiconFiles?.sorted()?.joinToString(",") { it.absolutePath } ?: ""

                // Build dict dir if present
                val dictDirFile = java.io.File("$modelDir/dict")
                val dictDir = if (dictDirFile.exists()) dictDirFile.absolutePath else ""

                // Build rule FSTs if present
                val fstFiles = java.io.File(modelDir).listFiles { _, name -> name.endsWith(".fst") }
                val ruleFsts = fstFiles?.sorted()?.joinToString(",") { it.absolutePath } ?: ""

                val modelConfig = if (modelId.startsWith("kitten-")) {
                    OfflineTtsModelConfig(
                        kitten = OfflineTtsKittenModelConfig(
                            model = "$modelDir/model.onnx",
                            voices = "$modelDir/voices.bin",
                            tokens = "$modelDir/tokens.txt",
                            dataDir = "$modelDir/espeak-ng-data",
                        ),
                        numThreads = 4,
                    )
                } else {
                    OfflineTtsModelConfig(
                        kokoro = OfflineTtsKokoroModelConfig(
                            model = "$modelDir/model.onnx",
                            voices = "$modelDir/voices.bin",
                            tokens = "$modelDir/tokens.txt",
                            dataDir = "$modelDir/espeak-ng-data",
                            lexicon = lexicon,
                            dictDir = dictDir,
                        ),
                        numThreads = 4,
                    )
                }

                val config = OfflineTtsConfig(
                    model = modelConfig,
                    ruleFsts = ruleFsts,
                )

                OfflineTts(config = config)
            }

            generateMutex.withLock {
                tts = candidate
                activeModelId = modelId
            }
            AppLog.i(TAG, "TTS engine initialized successfully with $modelId")
        }
    }

    /**
     * Generate speech audio for [text] using speaker [sid] at [speed].
     * Returns a FloatArray of PCM samples at [SAMPLE_RATE] Hz.
     *
     * Serialized by [generateMutex] — Sherpa-ONNX OfflineTts is not
     * thread-safe for concurrent generate() calls. If a previous job's
     * generation is still running when a new one starts, the new call
     * waits for it to complete.
     */
    suspend fun generate(
        text: String,
        sid: Int = 3, // af_heart
        speed: Float = 1.0f,
    ): FloatArray = withContext(Dispatchers.IO) {
        generateMutex.withLock {
            val engine = tts ?: throw IllegalStateException("TTS not initialized")

            AppLog.i(TAG, "Generating speech: sid=$sid, speed=$speed, characters=${text.length}")
            val audio = if (activeModelId?.startsWith("kitten-") == true) {
                // Match sherpa-onnx's current Kitten Java/Android path. The
                // generation-config API carries the v0.8 silence and speaker
                // settings explicitly instead of relying on the legacy call.
                engine.generateWithConfig(
                    text,
                    GenerationConfig(
                        silenceScale = 0.2f,
                        speed = speed,
                        sid = sid,
                    ),
                )
            } else {
                engine.generate(text = text, sid = sid, speed = speed)
            }
            AppLog.i(TAG, "Generated ${audio.samples.size} samples at ${audio.sampleRate} Hz")

            audio.samples
        }
    }

    fun release() {
        // Acquire the generate mutex to ensure no generation is in progress
        // before releasing the native TTS object (use-after-free protection).
        kotlinx.coroutines.runBlocking {
            generateMutex.withLock {
                tts?.release()
                tts = null
                activeModelId = null
            }
        }
        AppLog.i(TAG, "TTS engine released")
    }
}
