// Sherpa-ONNX Kotlin API for Offline Speech Recognition
// Ported from https://github.com/k2-fsa/sherpa-onnx/blob/master/sherpa-onnx/kotlin-api/
package com.k2fsa.sherpa.onnx

import android.content.res.AssetManager

data class FeatureConfig(
    var sampleRate: Int = 16000,
    var featureDim: Int = 80,
    var dither: Float = 0.0f,
)

data class HomophoneReplacerConfig(
    var dictDir: String = "",
    var lexicon: String = "",
    var ruleFsts: String = "",
)

data class OfflineMoonshineModelConfig(
    var preprocessor: String = "",
    var encoder: String = "",
    var uncachedDecoder: String = "",
    var cachedDecoder: String = "",
    var mergedDecoder: String = "",
)

data class OfflineTransducerModelConfig(
    var encoder: String = "",
    var decoder: String = "",
    var joiner: String = "",
)

data class OfflineWhisperModelConfig(
    var encoder: String = "",
    var decoder: String = "",
    var language: String = "",
    var task: String = "",
    var tailPaddings: Int = -1,
)

data class OfflineModelConfig(
    var transducer: OfflineTransducerModelConfig = OfflineTransducerModelConfig(),
    var moonshine: OfflineMoonshineModelConfig = OfflineMoonshineModelConfig(),
    var whisper: OfflineWhisperModelConfig = OfflineWhisperModelConfig(),
    var numThreads: Int = 1,
    var debug: Boolean = false,
    var provider: String = "cpu",
    var modelType: String = "",
    var tokens: String = "",
)

data class OfflineRecognizerConfig(
    var featConfig: FeatureConfig = FeatureConfig(),
    var modelConfig: OfflineModelConfig = OfflineModelConfig(),
    var hr: HomophoneReplacerConfig = HomophoneReplacerConfig(),
    var decodingMethod: String = "greedy_search",
    var maxActivePaths: Int = 4,
    var hotwordsFile: String = "",
    var hotwordsScore: Float = 1.5f,
    var ruleFsts: String = "",
    var ruleFars: String = "",
    var blankPenalty: Float = 0.0f,
)

data class OfflineRecognizerResult(
    val text: String,
    val tokens: Array<String>,
    val timestamps: FloatArray,
    val lang: String,
    val emotion: String,
    val event: String,
    val durations: FloatArray,
)

class OfflineStream(var ptr: Long) {
    fun acceptWaveform(samples: FloatArray, sampleRate: Int) =
        acceptWaveform(ptr, samples, sampleRate)

    protected fun finalize() {
        if (ptr != 0L) {
            delete(ptr)
            ptr = 0
        }
    }

    fun release() = finalize()

    private external fun acceptWaveform(ptr: Long, samples: FloatArray, sampleRate: Int)
    private external fun delete(ptr: Long)

    companion object {
        init {
            System.loadLibrary("sherpa-onnx-jni")
        }
    }
}

class OfflineRecognizer(
    assetManager: AssetManager? = null,
    val config: OfflineRecognizerConfig,
) {
    private var ptr: Long

    init {
        ptr = if (assetManager != null) {
            newFromAsset(assetManager, config)
        } else {
            newFromFile(config)
        }
    }

    protected fun finalize() {
        if (ptr != 0L) {
            delete(ptr)
            ptr = 0
        }
    }

    fun release() = finalize()

    fun createStream(): OfflineStream {
        val p = createStream(ptr)
        return OfflineStream(p)
    }

    fun getResult(stream: OfflineStream): OfflineRecognizerResult {
        return getResult(stream.ptr)
    }

    fun decode(stream: OfflineStream) = decode(ptr, stream.ptr)

    private external fun delete(ptr: Long)
    private external fun createStream(ptr: Long): Long

    private external fun newFromAsset(
        assetManager: AssetManager,
        config: OfflineRecognizerConfig,
    ): Long

    private external fun newFromFile(
        config: OfflineRecognizerConfig,
    ): Long

    private external fun decode(ptr: Long, streamPtr: Long)
    private external fun getResult(streamPtr: Long): OfflineRecognizerResult

    companion object {
        init {
            System.loadLibrary("sherpa-onnx-jni")
        }
    }
}
