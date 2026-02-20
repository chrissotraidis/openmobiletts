package com.openmobiletts.app

import java.io.ByteArrayOutputStream
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
        val numChannels = 1
        val bitsPerSample = 16
        val byteRate = sampleRate * numChannels * bitsPerSample / 8
        val blockAlign = numChannels * bitsPerSample / 8
        val dataSize = samples.size * blockAlign
        val fileSize = 36 + dataSize  // 44-byte header minus 8 for RIFF header

        val buffer = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)

        // RIFF header
        buffer.put("RIFF".toByteArray())
        buffer.putInt(fileSize)
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
        buffer.putInt(dataSize)

        // Convert float samples to 16-bit PCM
        for (sample in samples) {
            val clamped = sample.coerceIn(-1.0f, 1.0f)
            val pcm16 = (clamped * 32767.0f).toInt().toShort()
            buffer.putShort(pcm16)
        }

        return buffer.array()
    }
}
