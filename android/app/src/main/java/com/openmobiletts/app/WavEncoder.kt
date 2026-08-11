package com.openmobiletts.app

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Converts PCM FloatArray (from Sherpa-ONNX TtsManager) to WAV byte array.
 * WebView's Audio element plays WAV natively — no MP3 encoding needed.
 */
object WavEncoder {

    /**
     * Encode float PCM samples to a complete WAV file.
     * @param samples PCM float samples in [-1.0, 1.0] range
     * @param sampleRate Sample rate in Hz (default 24000 for Sherpa-ONNX Kokoro)
     * @return Complete WAV file as ByteArray
     */
    fun encode(samples: FloatArray, sampleRate: Int = TtsManager.SAMPLE_RATE): ByteArray {
        val pcm = encodePcm(samples)
        return header(pcm.size.toLong(), sampleRate) + pcm
    }

    fun encodePcm(samples: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (sample in samples) {
            val clamped = sample.coerceIn(-1.0f, 1.0f)
            buffer.putShort((clamped * 32767.0f).toInt().toShort())
        }
        return buffer.array()
    }

    fun header(dataSize: Long, sampleRate: Int = TtsManager.SAMPLE_RATE): ByteArray {
        require(dataSize in 0..Int.MAX_VALUE.toLong()) { "WAV data is too large" }
        val numChannels = 1
        val bitsPerSample = 16
        val byteRate = sampleRate * numChannels * bitsPerSample / 8
        val blockAlign = numChannels * bitsPerSample / 8
        val fileSize = 36L + dataSize

        val buffer = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)

        // RIFF header
        buffer.put("RIFF".toByteArray())
        buffer.putInt(fileSize.toInt())
        buffer.put("WAVE".toByteArray())

        // fmt sub-chunk
        buffer.put("fmt ".toByteArray())
        buffer.putInt(16)                  // Sub-chunk size
        buffer.putShort(1)                 // PCM format
        buffer.putShort(numChannels.toShort())
        buffer.putInt(sampleRate)
        buffer.putInt(byteRate)
        buffer.putShort(blockAlign.toShort())
        buffer.putShort(bitsPerSample.toShort())

        // data sub-chunk
        buffer.put("data".toByteArray())
        buffer.putInt(dataSize.toInt())

        return buffer.array()
    }

    fun patchHeader(file: File, dataSize: Long, sampleRate: Int = TtsManager.SAMPLE_RATE) {
        val replacement = header(dataSize, sampleRate)
        RandomAccessFile(file, "rw").use { output ->
            output.seek(0)
            output.write(replacement)
        }
    }
}
