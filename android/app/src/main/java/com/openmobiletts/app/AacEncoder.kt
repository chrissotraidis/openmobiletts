package com.openmobiletts.app

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Encodes PCM FloatArray to AAC with ADTS headers using Android's hardware-accelerated MediaCodec.
 *
 * ADTS (Audio Data Transport Stream) wrapping makes the output:
 *   - Concatenatable: multiple encode() calls produce a valid stream when joined
 *   - Streamable: each frame is self-contained
 *   - Playable: WebView Audio element and Android music players handle .aac natively
 *
 * At 64kbps mono, output is ~6x smaller than 16-bit PCM WAV.
 */
object AacEncoder {

    private const val TAG = "AacEncoder"
    private const val BIT_RATE = 64_000   // 64kbps, matching desktop MP3 settings
    private const val CHANNELS = 1        // Mono

    /**
     * Encode float PCM samples to AAC with ADTS headers.
     * @param samples PCM float samples in [-1.0, 1.0] range
     * @param sampleRate Sample rate in Hz (default 24000 for Sherpa-ONNX Kokoro)
     * @return ADTS-wrapped AAC data as ByteArray
     */
    fun encode(samples: FloatArray, sampleRate: Int = TtsManager.SAMPLE_RATE): ByteArray {
        // Convert float samples to 16-bit PCM bytes
        val pcmBuffer = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (sample in samples) {
            val clamped = sample.coerceIn(-1.0f, 1.0f)
            pcmBuffer.putShort((clamped * 32767f).toInt().toShort())
        }
        val pcmBytes = pcmBuffer.array()

        // Configure AAC-LC encoder
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, CHANNELS)
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
        format.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, pcmBytes.size)

        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        val output = ByteArrayOutputStream()
        val bufferInfo = MediaCodec.BufferInfo()
        var inputOffset = 0
        var inputDone = false
        val freqIndex = sampleRateToFreqIndex(sampleRate)

        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            while (true) {
                // Feed PCM input
                if (!inputDone) {
                    val inputIdx = codec.dequeueInputBuffer(10_000)
                    if (inputIdx >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputIdx)!!
                        val remaining = pcmBytes.size - inputOffset
                        if (remaining <= 0) {
                            codec.queueInputBuffer(inputIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            val size = minOf(remaining, inputBuffer.capacity())
                            inputBuffer.clear()
                            inputBuffer.put(pcmBytes, inputOffset, size)
                            codec.queueInputBuffer(inputIdx, 0, size, 0, 0)
                            inputOffset += size
                        }
                    }
                }

                // Drain encoded output
                val outputIdx = codec.dequeueOutputBuffer(bufferInfo, 10_000)
                if (outputIdx >= 0) {
                    // Skip codec-specific data (CSD)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                        codec.releaseOutputBuffer(outputIdx, false)
                        continue
                    }

                    if (bufferInfo.size > 0) {
                        val outputBuffer = codec.getOutputBuffer(outputIdx)!!
                        val aacFrame = ByteArray(bufferInfo.size)
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.get(aacFrame)

                        // Write ADTS header (7 bytes) + raw AAC frame
                        output.write(buildAdtsHeader(aacFrame.size, freqIndex))
                        output.write(aacFrame)
                    }

                    codec.releaseOutputBuffer(outputIdx, false)

                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        break
                    }
                } else if (outputIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    // Format changed mid-stream — continue draining
                    continue
                } else if (outputIdx == MediaCodec.INFO_TRY_AGAIN_LATER && inputDone) {
                    // All input consumed but no output yet — keep polling
                    continue
                }
            }
        } finally {
            codec.stop()
            codec.release()
        }

        return output.toByteArray()
    }

    /**
     * Build a 7-byte ADTS header for one AAC frame.
     * See ISO 14496-3 for the ADTS header format.
     */
    private fun buildAdtsHeader(frameLength: Int, freqIndex: Int): ByteArray {
        val packetLen = frameLength + 7  // AAC frame + 7-byte header
        val profile = 1    // AAC-LC (encoded as profile-1 in ADTS)
        val chanConfig = CHANNELS

        return byteArrayOf(
            0xFF.toByte(),                                                      // Sync word high byte
            0xF1.toByte(),                                                      // Sync word low nibble + MPEG-4 + Layer 0 + no CRC
            ((profile shl 6) or (freqIndex shl 2) or (chanConfig shr 2)).toByte(),
            (((chanConfig and 3) shl 6) or (packetLen shr 11)).toByte(),
            ((packetLen shr 3) and 0xFF).toByte(),
            (((packetLen and 7) shl 5) or 0x1F).toByte(),
            0xFC.toByte(),
        )
    }

    /**
     * Map sample rate to MPEG-4 Audio frequency index.
     */
    private fun sampleRateToFreqIndex(sampleRate: Int): Int = when (sampleRate) {
        96000 -> 0; 88200 -> 1; 64000 -> 2; 48000 -> 3
        44100 -> 4; 32000 -> 5; 24000 -> 6; 22050 -> 7
        16000 -> 8; 12000 -> 9; 11025 -> 10; 8000 -> 11
        7350 -> 12
        else -> 6 // Default to 24kHz index
    }
}
