/*
 * Mesh Rider Wave - Opus Fallback Codec
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 *
 * Pure Kotlin/Java fallback codecs for Android API < 29 devices
 * where MediaCodec Opus is not available.
 *
 * Codec Hierarchy (best to worst):
 * 1. MediaCodec Opus (API 29+) - 6-24 kbps    - Handled by OpusCodecManager
 * 2. IMA ADPCM (this file)     - 32 kbps      - 8x compression, good quality
 * 3. G.711 mu-law (this file)  - 64 kbps      - 4x compression, telephony standard
 * 4. Raw PCM (fallback)        - 256 kbps     - No compression
 *
 * Technical Details:
 * - G.711 mu-law: ITU-T G.711 standard, 8-bit samples, logarithmic companding
 * - IMA ADPCM: 4-bit differential encoding, 8:1 compression ratio
 * - Both codecs work on 16kHz 16-bit mono PCM input
 *
 * For tactical voice over mesh networks:
 * - G.711 mu-law: Acceptable quality, 4x bandwidth reduction
 * - IMA ADPCM: Good quality, 8x bandwidth reduction (recommended fallback)
 */

package com.doodlelabs.meshriderwave.core.audio

import com.doodlelabs.meshriderwave.core.util.logD
import com.doodlelabs.meshriderwave.core.util.logE
import com.doodlelabs.meshriderwave.core.util.logI
import com.doodlelabs.meshriderwave.core.util.logW
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.sign

/**
 * Fallback codec manager for devices without MediaCodec Opus support
 *
 * Provides pure Kotlin implementations of:
 * - G.711 mu-law (64 kbps) - ITU-T telephony standard
 * - IMA ADPCM (32 kbps) - Better compression with acceptable quality
 *
 * Usage:
 * ```kotlin
 * val fallback = OpusFallbackCodec()
 * fallback.initialize(FallbackCodecType.ADPCM)
 *
 * // Encode PCM to compressed format
 * val encoded = fallback.encode(pcmData)
 *
 * // Decode back to PCM
 * val decoded = fallback.decode(encoded.data)
 * ```
 */
@Singleton
class OpusFallbackCodec @Inject constructor() {

    // Configuration
    private var isInitialized = false
    private var codecType = FallbackCodecType.ADPCM
    private var config = CodecConfig()

    // Statistics
    private var totalFramesEncoded = 0L
    private var totalFramesDecoded = 0L
    private var totalBytesIn = 0L
    private var totalBytesOut = 0L
    private var sequenceNumber = 0

    // ADPCM state (persists across frames for better quality)
    private var adpcmEncoderState = ADPCMState()
    private var adpcmDecoderState = ADPCMState()

    companion object {
        // Sample configuration (matches OpusCodecManager)
        const val SAMPLE_RATE = 16000
        const val CHANNELS = 1
        const val BITS_PER_SAMPLE = 16
        const val FRAME_SIZE_SAMPLES = 320  // 20ms at 16kHz
        const val FRAME_SIZE_BYTES = FRAME_SIZE_SAMPLES * 2  // 640 bytes PCM

        // G.711 mu-law constants
        private const val MULAW_BIAS = 0x84
        private const val MULAW_MAX = 0x7FFF
        private const val MULAW_CLIP = 32635

        // IMA ADPCM step table (88 entries)
        private val ADPCM_STEP_TABLE = intArrayOf(
            7, 8, 9, 10, 11, 12, 13, 14,
            16, 17, 19, 21, 23, 25, 28, 31,
            34, 37, 41, 45, 50, 55, 60, 66,
            73, 80, 88, 97, 107, 118, 130, 143,
            157, 173, 190, 209, 230, 253, 279, 307,
            337, 371, 408, 449, 494, 544, 598, 658,
            724, 796, 876, 963, 1060, 1166, 1282, 1411,
            1552, 1707, 1878, 2066, 2272, 2499, 2749, 3024,
            3327, 3660, 4026, 4428, 4871, 5358, 5894, 6484,
            7132, 7845, 8630, 9493, 10442, 11487, 12635, 13899,
            15289, 16818, 18500, 20350, 22385, 24623, 27086, 29794,
            32767
        )

        // IMA ADPCM index adjustment table
        private val ADPCM_INDEX_TABLE = intArrayOf(
            -1, -1, -1, -1, 2, 4, 6, 8,
            -1, -1, -1, -1, 2, 4, 6, 8
        )
    }

    /**
     * Available fallback codec types
     */
    enum class FallbackCodecType(val displayName: String, val bitrate: Int) {
        G711_MULAW("G.711 mu-law", 128000),  // 64 kbps at 8kHz, 128 kbps at 16kHz
        ADPCM("IMA ADPCM", 64000),           // 32 kbps at 8kHz, 64 kbps at 16kHz
        PASSTHROUGH("Raw PCM", 256000)       // No compression
    }

    /**
     * Codec configuration
     */
    data class CodecConfig(
        val sampleRate: Int = SAMPLE_RATE,
        val channels: Int = CHANNELS,
        val frameSize: Int = FRAME_SIZE_SAMPLES
    ) {
        val frameSizeBytes: Int get() = frameSize * channels * 2
        val frameTimeMs: Int get() = (frameSize * 1000) / sampleRate
    }

    /**
     * Encoded frame with metadata (matches OpusCodecManager.EncodedFrame)
     */
    data class EncodedFrame(
        val data: ByteArray,
        val timestamp: Long,
        val sequenceNumber: Int,
        val codecType: FallbackCodecType,
        val frameSize: Int
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as EncodedFrame
            return data.contentEquals(other.data) &&
                    timestamp == other.timestamp &&
                    sequenceNumber == other.sequenceNumber
        }

        override fun hashCode(): Int {
            var result = data.contentHashCode()
            result = 31 * result + timestamp.hashCode()
            result = 31 * result + sequenceNumber
            return result
        }

        /**
         * Convert to OpusCodecManager.EncodedFrame for compatibility
         */
        fun toOpusEncodedFrame(): OpusCodecManager.EncodedFrame {
            return OpusCodecManager.EncodedFrame(
                data = data,
                timestamp = timestamp,
                sequenceNumber = sequenceNumber,
                isVoice = true,
                frameSize = frameSize
            )
        }
    }

    /**
     * ADPCM encoder/decoder state
     */
    private data class ADPCMState(
        var predictedSample: Int = 0,
        var stepIndex: Int = 0
    ) {
        fun reset() {
            predictedSample = 0
            stepIndex = 0
        }
    }

    /**
     * Initialize the fallback codec
     *
     * @param type Codec type to use (ADPCM recommended)
     * @param config Audio configuration
     */
    fun initialize(
        type: FallbackCodecType = FallbackCodecType.ADPCM,
        config: CodecConfig = CodecConfig()
    ): Boolean {
        if (isInitialized) {
            logW("FallbackCodec already initialized, resetting")
            reset()
        }

        this.codecType = type
        this.config = config

        // Reset ADPCM state
        adpcmEncoderState.reset()
        adpcmDecoderState.reset()

        // Reset statistics
        totalFramesEncoded = 0L
        totalFramesDecoded = 0L
        totalBytesIn = 0L
        totalBytesOut = 0L
        sequenceNumber = 0

        isInitialized = true

        val compressionRatio = when (type) {
            FallbackCodecType.ADPCM -> 4
            FallbackCodecType.G711_MULAW -> 2
            FallbackCodecType.PASSTHROUGH -> 1
        }

        logI("FallbackCodec initialized: ${type.displayName}, ${type.bitrate / 1000}kbps, ${compressionRatio}x compression")
        return true
    }

    /**
     * Encode PCM audio to compressed format
     *
     * @param pcmData Raw PCM audio (16-bit signed, little-endian, mono)
     * @return Encoded frame or null on error
     */
    fun encode(pcmData: ByteArray): EncodedFrame? {
        if (!isInitialized) {
            logE("Cannot encode: codec not initialized")
            return null
        }

        if (pcmData.size != config.frameSizeBytes) {
            logE("Invalid PCM size: ${pcmData.size}, expected ${config.frameSizeBytes}")
            return null
        }

        return try {
            val encodedData = when (codecType) {
                FallbackCodecType.ADPCM -> encodeADPCM(pcmData)
                FallbackCodecType.G711_MULAW -> encodeMuLaw(pcmData)
                FallbackCodecType.PASSTHROUGH -> pcmData.copyOf()
            }

            // Update statistics
            totalFramesEncoded++
            totalBytesIn += pcmData.size
            totalBytesOut += encodedData.size

            val frame = EncodedFrame(
                data = encodedData,
                timestamp = System.currentTimeMillis(),
                sequenceNumber = sequenceNumber++,
                codecType = codecType,
                frameSize = config.frameSize
            )

            // Log compression stats periodically
            if (totalFramesEncoded % 500 == 0L) {
                val ratio = if (totalBytesOut > 0) totalBytesIn.toFloat() / totalBytesOut else 0f
                logD("FallbackCodec encode stats: ${codecType.displayName}, $totalFramesEncoded frames, ${ratio.format(1)}x compression")
            }

            frame
        } catch (e: Exception) {
            logE("Exception during encode", e)
            null
        }
    }

    /**
     * Decode compressed audio to PCM
     *
     * @param encodedData Compressed audio data
     * @param type Codec type used for encoding (auto-detected if null)
     * @return Decoded PCM audio or null on error
     */
    fun decode(encodedData: ByteArray, type: FallbackCodecType? = null): ByteArray? {
        if (!isInitialized) {
            logE("Cannot decode: codec not initialized")
            return null
        }

        return try {
            val actualType = type ?: detectCodecType(encodedData)

            val decodedData = when (actualType) {
                FallbackCodecType.ADPCM -> decodeADPCM(encodedData)
                FallbackCodecType.G711_MULAW -> decodeMuLaw(encodedData)
                FallbackCodecType.PASSTHROUGH -> encodedData.copyOf()
            }

            totalFramesDecoded++
            decodedData
        } catch (e: Exception) {
            logE("Exception during decode", e)
            null
        }
    }

    /**
     * Decode an EncodedFrame
     */
    fun decode(frame: EncodedFrame): ByteArray? {
        return decode(frame.data, frame.codecType)
    }

    /**
     * Generate a silence/comfort noise frame for packet loss concealment
     */
    fun generatePLCFrame(): ByteArray {
        // Return silence frame
        return ByteArray(config.frameSizeBytes)
    }

    // =========================================================================
    // G.711 mu-law Implementation (ITU-T Standard)
    // =========================================================================

    /**
     * Encode 16-bit PCM to 8-bit mu-law
     *
     * Compression: 2:1 (16-bit -> 8-bit per sample)
     * Output size: input.size / 2
     */
    private fun encodeMuLaw(pcmData: ByteArray): ByteArray {
        val numSamples = pcmData.size / 2
        val encoded = ByteArray(numSamples)
        val buffer = ByteBuffer.wrap(pcmData).order(ByteOrder.LITTLE_ENDIAN)

        for (i in 0 until numSamples) {
            val sample = buffer.getShort().toInt()
            encoded[i] = linearToMuLaw(sample)
        }

        return encoded
    }

    /**
     * Decode 8-bit mu-law to 16-bit PCM
     */
    private fun decodeMuLaw(encodedData: ByteArray): ByteArray {
        val numSamples = encodedData.size
        val decoded = ByteArray(numSamples * 2)
        val buffer = ByteBuffer.wrap(decoded).order(ByteOrder.LITTLE_ENDIAN)

        for (i in 0 until numSamples) {
            val sample = muLawToLinear(encodedData[i])
            buffer.putShort(sample.toShort())
        }

        return decoded
    }

    /**
     * Convert linear 16-bit sample to mu-law 8-bit
     *
     * mu-law formula: F(x) = sgn(x) * ln(1 + mu * |x|) / ln(1 + mu)
     * where mu = 255 for telephony
     *
     * This implementation uses the standard ITU-T G.711 lookup approach
     */
    private fun linearToMuLaw(sample: Int): Byte {
        // Get the sign bit
        val sign = if (sample < 0) 0x80 else 0x00

        // Get absolute value with bias
        var absValue = if (sample < 0) -sample else sample
        if (absValue > MULAW_CLIP) absValue = MULAW_CLIP
        absValue += MULAW_BIAS

        // Find the segment number (exponent)
        var exponent = 7
        var expMask = 0x4000
        while (exponent > 0 && (absValue and expMask) == 0) {
            exponent--
            expMask = expMask shr 1
        }

        // Extract mantissa
        val mantissa = (absValue shr (exponent + 3)) and 0x0F

        // Combine sign, exponent, and mantissa
        // Invert all bits for mu-law (standard specifies inverted output)
        return (sign or (exponent shl 4) or mantissa xor 0xFF).toByte()
    }

    /**
     * Convert mu-law 8-bit to linear 16-bit sample
     */
    private fun muLawToLinear(muLawByte: Byte): Int {
        // Invert all bits first (mu-law is stored inverted)
        val muLaw = (muLawByte.toInt() and 0xFF) xor 0xFF

        // Extract sign, exponent, and mantissa
        val sign = muLaw and 0x80
        val exponent = (muLaw shr 4) and 0x07
        val mantissa = muLaw and 0x0F

        // Reconstruct the linear value
        var sample = ((mantissa shl 3) + MULAW_BIAS) shl (exponent + 1)
        sample -= MULAW_BIAS

        return if (sign != 0) -sample else sample
    }

    // =========================================================================
    // IMA ADPCM Implementation (4-bit differential encoding)
    // =========================================================================

    /**
     * Encode 16-bit PCM to 4-bit IMA ADPCM
     *
     * Compression: 4:1 (16-bit -> 4-bit per sample)
     * Output size: input.size / 4 + 4 bytes header
     *
     * Header format (4 bytes):
     * - Bytes 0-1: Initial predicted sample (little-endian)
     * - Byte 2: Initial step index
     * - Byte 3: Reserved (0)
     */
    private fun encodeADPCM(pcmData: ByteArray): ByteArray {
        val numSamples = pcmData.size / 2
        val outputSize = 4 + (numSamples + 1) / 2  // 4-byte header + nibbles
        val encoded = ByteArray(outputSize)
        val inputBuffer = ByteBuffer.wrap(pcmData).order(ByteOrder.LITTLE_ENDIAN)

        // Write header with initial state
        val headerBuffer = ByteBuffer.wrap(encoded).order(ByteOrder.LITTLE_ENDIAN)
        headerBuffer.putShort(adpcmEncoderState.predictedSample.toShort())
        headerBuffer.put(adpcmEncoderState.stepIndex.toByte())
        headerBuffer.put(0)  // Reserved

        var outputIndex = 4
        var highNibble = false

        for (i in 0 until numSamples) {
            val sample = inputBuffer.getShort().toInt()
            val nibble = encodeADPCMSample(sample)

            if (highNibble) {
                encoded[outputIndex] = (encoded[outputIndex].toInt() or (nibble shl 4)).toByte()
                outputIndex++
            } else {
                encoded[outputIndex] = (nibble and 0x0F).toByte()
            }
            highNibble = !highNibble
        }

        return encoded
    }

    /**
     * Encode a single sample to 4-bit ADPCM nibble
     */
    private fun encodeADPCMSample(sample: Int): Int {
        val step = ADPCM_STEP_TABLE[adpcmEncoderState.stepIndex]
        var diff = sample - adpcmEncoderState.predictedSample

        // Determine sign bit
        var nibble = 0
        if (diff < 0) {
            nibble = 8
            diff = -diff
        }

        // Quantize the difference
        var mask = 4
        var tempStep = step
        for (bit in 2 downTo 0) {
            if (diff >= tempStep) {
                nibble = nibble or mask
                diff -= tempStep
            }
            tempStep = tempStep shr 1
            mask = mask shr 1
        }

        // Update predictor using the quantized value
        updateADPCMPredictor(adpcmEncoderState, nibble, step)

        return nibble
    }

    /**
     * Decode 4-bit IMA ADPCM to 16-bit PCM
     */
    private fun decodeADPCM(encodedData: ByteArray): ByteArray {
        if (encodedData.size < 4) {
            logE("ADPCM data too short: ${encodedData.size}")
            return ByteArray(config.frameSizeBytes)
        }

        // Read header
        val headerBuffer = ByteBuffer.wrap(encodedData).order(ByteOrder.LITTLE_ENDIAN)
        adpcmDecoderState.predictedSample = headerBuffer.getShort().toInt()
        adpcmDecoderState.stepIndex = headerBuffer.get().toInt() and 0xFF
        headerBuffer.get()  // Skip reserved byte

        // Validate step index
        adpcmDecoderState.stepIndex = adpcmDecoderState.stepIndex.coerceIn(0, 88)

        val numNibbles = (encodedData.size - 4) * 2
        val numSamples = minOf(numNibbles, config.frameSize)
        val decoded = ByteArray(numSamples * 2)
        val outputBuffer = ByteBuffer.wrap(decoded).order(ByteOrder.LITTLE_ENDIAN)

        var inputIndex = 4
        var highNibble = false

        for (i in 0 until numSamples) {
            val nibble = if (highNibble) {
                val result = (encodedData[inputIndex].toInt() shr 4) and 0x0F
                inputIndex++
                result
            } else {
                encodedData[inputIndex].toInt() and 0x0F
            }
            highNibble = !highNibble

            val sample = decodeADPCMSample(nibble)
            outputBuffer.putShort(sample.toShort())
        }

        // Pad with silence if needed
        while (outputBuffer.position() < config.frameSizeBytes) {
            outputBuffer.putShort(0)
        }

        return decoded
    }

    /**
     * Decode a single 4-bit ADPCM nibble to sample
     */
    private fun decodeADPCMSample(nibble: Int): Int {
        val step = ADPCM_STEP_TABLE[adpcmDecoderState.stepIndex]
        updateADPCMPredictor(adpcmDecoderState, nibble, step)
        return adpcmDecoderState.predictedSample
    }

    /**
     * Update ADPCM predictor state
     */
    private fun updateADPCMPredictor(state: ADPCMState, nibble: Int, step: Int) {
        // Calculate difference
        var diff = step shr 3
        if (nibble and 4 != 0) diff += step
        if (nibble and 2 != 0) diff += step shr 1
        if (nibble and 1 != 0) diff += step shr 2

        // Apply sign
        if (nibble and 8 != 0) {
            state.predictedSample -= diff
        } else {
            state.predictedSample += diff
        }

        // Clamp to 16-bit range
        state.predictedSample = state.predictedSample.coerceIn(-32768, 32767)

        // Update step index
        state.stepIndex += ADPCM_INDEX_TABLE[nibble]
        state.stepIndex = state.stepIndex.coerceIn(0, 88)
    }

    // =========================================================================
    // Utility Methods
    // =========================================================================

    /**
     * Auto-detect codec type from encoded data size
     */
    private fun detectCodecType(encodedData: ByteArray): FallbackCodecType {
        val expectedPCMSize = config.frameSizeBytes
        val expectedMuLawSize = expectedPCMSize / 2
        val expectedADPCMSize = 4 + expectedPCMSize / 4

        return when {
            encodedData.size == expectedPCMSize -> FallbackCodecType.PASSTHROUGH
            encodedData.size == expectedMuLawSize -> FallbackCodecType.G711_MULAW
            encodedData.size <= expectedADPCMSize + 10 -> FallbackCodecType.ADPCM
            else -> codecType  // Use configured type
        }
    }

    /**
     * Reset codec state (useful for new streams)
     */
    fun reset() {
        adpcmEncoderState.reset()
        adpcmDecoderState.reset()
        sequenceNumber = 0
        logD("FallbackCodec state reset")
    }

    /**
     * Release codec resources
     */
    fun release() {
        reset()
        isInitialized = false
        logI("FallbackCodec released")
    }

    /**
     * Get codec statistics
     */
    fun getStats(): CodecStats {
        val ratio = if (totalBytesOut > 0) totalBytesIn.toFloat() / totalBytesOut else 1f
        val inputBps = if (totalFramesEncoded > 0) {
            (totalBytesIn * 8.0 * 1000) / (totalFramesEncoded * config.frameTimeMs)
        } else 0.0
        val outputBps = if (totalFramesEncoded > 0) {
            (totalBytesOut * 8.0 * 1000) / (totalFramesEncoded * config.frameTimeMs)
        } else 0.0

        return CodecStats(
            codecType = codecType,
            framesEncoded = totalFramesEncoded,
            framesDecoded = totalFramesDecoded,
            bytesIn = totalBytesIn,
            bytesOut = totalBytesOut,
            compressionRatio = ratio,
            inputBitrate = inputBps,
            outputBitrate = outputBps
        )
    }

    /**
     * Check if codec is initialized
     */
    fun isReady(): Boolean = isInitialized

    /**
     * Get current codec type
     */
    fun getCodecType(): FallbackCodecType = codecType

    private fun Float.format(decimals: Int): String = "%.${decimals}f".format(this)

    /**
     * Codec statistics
     */
    data class CodecStats(
        val codecType: FallbackCodecType,
        val framesEncoded: Long,
        val framesDecoded: Long,
        val bytesIn: Long,
        val bytesOut: Long,
        val compressionRatio: Float,
        val inputBitrate: Double,
        val outputBitrate: Double
    ) {
        fun toLogString(): String {
            return """
                FallbackCodec Stats:
                - Type: ${codecType.displayName}
                - Frames: $framesEncoded encoded, $framesDecoded decoded
                - Compression: ${compressionRatio.format(1)}x (${inputBitrate.format(0)} bps -> ${outputBitrate.format(0)} bps)
                - Total: ${bytesIn / 1024}KB in, ${bytesOut / 1024}KB out
            """.trimIndent()
        }

        private fun Float.format(decimals: Int): String = "%.${decimals}f".format(this)
        private fun Double.format(decimals: Int): String = "%.${decimals}f".format(this)
    }
}

// =============================================================================
// Extension Functions for Integration with OpusCodecManager
// =============================================================================

/**
 * Convert OpusFallbackCodec.EncodedFrame to OpusCodecManager.EncodedFrame
 */
fun OpusFallbackCodec.EncodedFrame.toOpusFrame(): OpusCodecManager.EncodedFrame {
    return OpusCodecManager.EncodedFrame(
        data = this.data,
        timestamp = this.timestamp,
        sequenceNumber = this.sequenceNumber,
        isVoice = true,
        frameSize = this.frameSize
    )
}
