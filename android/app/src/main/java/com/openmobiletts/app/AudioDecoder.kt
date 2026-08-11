package com.openmobiletts.app

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Decodes audio files (mp3, aac, ogg, wav) to PCM FloatArray for STT input.
 *
 * Uses Android's MediaExtractor (demux) + MediaCodec (decode) — zero external
 * dependencies. Resamples to 16kHz mono as required by Moonshine.
 */
object AudioDecoder {

    private const val TAG = "AudioDecoder"
    private const val TARGET_SAMPLE_RATE = 16000
    private const val TIMEOUT_US = 10_000L // 10ms codec timeout
    const val MAX_DURATION_SECONDS = 15 * 60
    const val MAX_SOURCE_BYTES = 256L * 1024 * 1024

    /**
     * Decode an audio file to 16kHz mono PCM float samples.
     * Supports mp3, aac, ogg, wav, m4a, webm — anything MediaExtractor handles.
     *
     * @param file The audio file to decode
     * @return FloatArray of PCM samples normalized to [-1, 1] at 16kHz mono
     * @throws IllegalArgumentException if no audio track found
     * @throws Exception on decode errors
     */
    fun decode(file: File): FloatArray {
        AppLog.i(TAG, "Decoding audio file: ${file.name} (${file.length() / 1024} KB)")
        require(file.length() in 1..MAX_SOURCE_BYTES) {
            "Audio file must be between 1 byte and ${MAX_SOURCE_BYTES / 1024 / 1024} MB"
        }

        // Fast path: parse WAV files directly without MediaExtractor
        // (MediaExtractor doesn't reliably support WAV on all Android versions)
        if (isWavFile(file)) {
            AppLog.i(TAG, "WAV detected — using direct PCM parser")
            return decodeWavDirect(file)
        }

        val extractor = MediaExtractor()
        extractor.setDataSource(file.absolutePath)

        // Find the first audio track
        val audioTrackIndex = findAudioTrack(extractor)
            ?: throw IllegalArgumentException("No audio track found in ${file.name}")

        extractor.selectTrack(audioTrackIndex)
        val format = extractor.getTrackFormat(audioTrackIndex)
        val mime = format.getString(MediaFormat.KEY_MIME)
            ?: throw IllegalArgumentException("No MIME type for audio track")

        val inputSampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE))
            format.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 44100
        val inputChannels = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT))
            format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 1
        if (format.containsKey(MediaFormat.KEY_DURATION)) {
            val durationUs = format.getLong(MediaFormat.KEY_DURATION)
            require(durationUs <= MAX_DURATION_SECONDS * 1_000_000L) {
                "Audio is longer than the ${MAX_DURATION_SECONDS / 60}-minute Android limit"
            }
        }

        AppLog.i(TAG, "Audio track: mime=$mime, sampleRate=$inputSampleRate, channels=$inputChannels")

        // For raw PCM WAV files, MediaCodec isn't needed — read directly
        if (mime == MediaFormat.MIMETYPE_AUDIO_RAW) {
            val pcmData = readRawPcm(extractor, format)
            extractor.release()
            return resampleAndMix(pcmData, inputSampleRate, inputChannels)
        }

        // Decode compressed audio via MediaCodec
        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()

        val pcmChunks = mutableListOf<ShortArray>()
        var totalSamples = 0
        var inputDone = false
        val bufferInfo = MediaCodec.BufferInfo()
        var actualSampleRate = inputSampleRate
        var actualChannels = inputChannels

        try {
            while (true) {
                // Feed input buffers
                if (!inputDone) {
                    val inputIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputIndex)!!
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inputIndex, 0, 0, 0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            val pts = extractor.sampleTime
                            codec.queueInputBuffer(inputIndex, 0, sampleSize, pts, 0)
                            extractor.advance()
                        }
                    }
                }

                // Drain output buffers
                val outputIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                if (outputIndex >= 0) {
                    if (bufferInfo.size > 0) {
                        val outputBuffer = codec.getOutputBuffer(outputIndex)!!
                        val chunk = extractPcmShorts(outputBuffer, bufferInfo)
                        pcmChunks.add(chunk)
                        totalSamples += chunk.size
                        ensureWithinDuration(totalSamples, actualSampleRate, actualChannels)
                    }
                    codec.releaseOutputBuffer(outputIndex, false)

                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        break
                    }
                } else if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    // Capture actual output format (may differ from input)
                    val newFormat = codec.outputFormat
                    actualSampleRate = newFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    actualChannels = newFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    AppLog.d(TAG, "Output format changed: ${actualSampleRate}Hz, ${actualChannels}ch")
                }
            }
        } finally {
            codec.stop()
            codec.release()
            extractor.release()
        }

        AppLog.i(TAG, "Decoded $totalSamples PCM samples from ${file.name}")

        // Merge chunks into a single ShortArray
        val allSamples = ShortArray(totalSamples)
        var offset = 0
        for (chunk in pcmChunks) {
            System.arraycopy(chunk, 0, allSamples, offset, chunk.size)
            offset += chunk.size
        }

        return resampleAndMix(allSamples, actualSampleRate, actualChannels)
    }

    private fun findAudioTrack(extractor: MediaExtractor): Int? {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) return i
        }
        return null
    }

    private fun extractPcmShorts(buffer: ByteBuffer, info: MediaCodec.BufferInfo): ShortArray {
        buffer.position(info.offset)
        buffer.limit(info.offset + info.size)

        val shortBuffer = buffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val shorts = ShortArray(shortBuffer.remaining())
        shortBuffer.get(shorts)
        return shorts
    }

    private fun readRawPcm(extractor: MediaExtractor, format: MediaFormat): ShortArray {
        val chunks = mutableListOf<ShortArray>()
        val buffer = ByteBuffer.allocate(65536)
        var total = 0

        while (true) {
            buffer.clear()
            val size = extractor.readSampleData(buffer, 0)
            if (size < 0) break

            buffer.flip()
            buffer.order(ByteOrder.LITTLE_ENDIAN)
            val shortBuffer = buffer.asShortBuffer()
            val shorts = ShortArray(shortBuffer.remaining())
            shortBuffer.get(shorts)
            chunks.add(shorts)
            total += shorts.size
            val sampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE))
                format.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 44100
            val channels = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT))
                format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 1
            ensureWithinDuration(total, sampleRate, channels)
            extractor.advance()
        }

        val result = ShortArray(total)
        var offset = 0
        for (chunk in chunks) {
            System.arraycopy(chunk, 0, result, offset, chunk.size)
            offset += chunk.size
        }
        return result
    }

    /**
     * Resample to 16kHz and mix to mono.
     * Input is 16-bit PCM shorts, output is float [-1, 1].
     */
    private fun resampleAndMix(
        samples: ShortArray,
        sampleRate: Int,
        channels: Int,
    ): FloatArray {
        require(sampleRate > 0 && channels > 0) { "Invalid audio format" }
        ensureWithinDuration(samples.size, sampleRate, channels)
        // Step 1: Mix to mono if multi-channel
        val mono = if (channels > 1) {
            val monoLength = samples.size / channels
            FloatArray(monoLength) { i ->
                var sum = 0f
                for (ch in 0 until channels) {
                    sum += samples[i * channels + ch].toFloat() / 32768f
                }
                sum / channels
            }
        } else {
            FloatArray(samples.size) { i -> samples[i].toFloat() / 32768f }
        }

        // Step 2: Resample to 16kHz if needed
        if (sampleRate == TARGET_SAMPLE_RATE) return mono

        val ratio = TARGET_SAMPLE_RATE.toDouble() / sampleRate
        val newLength = (mono.size * ratio).toInt()
        require(newLength <= MAX_DURATION_SECONDS * TARGET_SAMPLE_RATE) {
            "Audio is longer than the ${MAX_DURATION_SECONDS / 60}-minute Android limit"
        }
        val resampled = FloatArray(newLength)

        for (i in 0 until newLength) {
            val srcIndex = i / ratio
            val idx = srcIndex.toInt()
            val frac = (srcIndex - idx).toFloat()
            resampled[i] = if (idx + 1 < mono.size) {
                mono[idx] * (1f - frac) + mono[idx + 1] * frac
            } else {
                mono[idx.coerceAtMost(mono.size - 1)]
            }
        }

        AppLog.i(TAG, "Resampled: ${sampleRate}Hz → ${TARGET_SAMPLE_RATE}Hz, ${mono.size} → ${resampled.size} samples")
        return resampled
    }

    private fun ensureWithinDuration(sampleCount: Int, sampleRate: Int, channels: Int) {
        require(sampleRate > 0 && channels > 0) { "Invalid audio format" }
        val maxSamples = MAX_DURATION_SECONDS.toLong() * sampleRate * channels
        require(sampleCount.toLong() <= maxSamples) {
            "Audio is longer than the ${MAX_DURATION_SECONDS / 60}-minute Android limit"
        }
    }

    /**
     * Check if a file is a WAV file by reading the RIFF header.
     */
    private fun isWavFile(file: File): Boolean {
        if (file.length() < 44) return false
        val header = ByteArray(4)
        file.inputStream().use { it.read(header) }
        return header[0] == 'R'.code.toByte() &&
               header[1] == 'I'.code.toByte() &&
               header[2] == 'F'.code.toByte() &&
               header[3] == 'F'.code.toByte()
    }

    /**
     * Decode a WAV file directly without MediaExtractor.
     * Parses the RIFF/WAV header, extracts PCM data, resamples to 16kHz mono.
     */
    private fun decodeWavDirect(file: File): FloatArray {
        var sampleRate = 16000
        var channels = 1
        var bitsPerSample = 16
        var dataOffset = 0L
        var dataSize = 0L

        RandomAccessFile(file, "r").use { input ->
            require(input.length() >= 44) { "Invalid WAV file" }
            input.seek(12)
            while (input.filePointer + 8 <= input.length()) {
                val chunkId = ByteArray(4)
                input.readFully(chunkId)
                val chunkSize = Integer.toUnsignedLong(Integer.reverseBytes(input.readInt()))
                val chunkStart = input.filePointer
                require(chunkStart + chunkSize <= input.length()) { "Invalid WAV chunk size" }

                when (String(chunkId, Charsets.US_ASCII)) {
                    "fmt " -> {
                        require(chunkSize >= 16) { "Invalid WAV format chunk" }
                        val audioFormat = java.lang.Short.toUnsignedInt(java.lang.Short.reverseBytes(input.readShort()))
                        require(audioFormat == 1) { "Unsupported WAV encoding: $audioFormat" }
                        channels = java.lang.Short.toUnsignedInt(java.lang.Short.reverseBytes(input.readShort()))
                        sampleRate = Integer.reverseBytes(input.readInt())
                        input.skipBytes(6) // byte rate + block align
                        bitsPerSample = java.lang.Short.toUnsignedInt(java.lang.Short.reverseBytes(input.readShort()))
                    }
                    "data" -> {
                        dataOffset = chunkStart
                        dataSize = chunkSize
                    }
                }
                if (dataOffset > 0L) break
                input.seek(chunkStart + chunkSize + (chunkSize % 2))
            }

            require(dataOffset > 0L && dataSize > 0L) { "Invalid WAV file — no data chunk found" }
            require(bitsPerSample == 16) {
                "Unsupported WAV format: ${bitsPerSample}-bit samples (only 16-bit PCM supported)"
            }
            val numSamples = dataSize / 2L
            require(numSamples <= Int.MAX_VALUE) { "WAV file is too large" }
            ensureWithinDuration(numSamples.toInt(), sampleRate, channels)

            AppLog.i(TAG, "WAV: ${sampleRate}Hz, ${channels}ch, ${bitsPerSample}bit, ${dataSize} bytes PCM")
            val samples = ShortArray(numSamples.toInt())
            input.seek(dataOffset)
            for (index in samples.indices) {
                samples[index] = java.lang.Short.reverseBytes(input.readShort())
            }
            return resampleAndMix(samples, sampleRate, channels)
        }
    }
}
