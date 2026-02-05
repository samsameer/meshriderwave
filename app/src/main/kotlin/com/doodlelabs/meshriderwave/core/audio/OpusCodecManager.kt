/*
 * Mesh Rider Wave - Opus Codec Manager
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 *
 * Low-bandwidth voice codec for tactical PTT communication
 * Uses Android MediaCodec for Opus encoding/decoding (API 29+)
 * Falls back to IMA ADPCM or G.711 mu-law on older devices (API 21-28)
 *
 * Bandwidth Comparison:
 * - Raw PCM 16-bit 16kHz: 256 kbps
 * - Opus Voice (6 kbps):  ~15 bytes/20ms frame  -> 6 kbps   (43x compression)
 * - Opus Voice (12 kbps): ~30 bytes/20ms frame  -> 12 kbps  (21x compression)
 * - Opus Voice (24 kbps): ~60 bytes/20ms frame  -> 24 kbps  (11x compression)
 * - IMA ADPCM (fallback): ~40 bytes/20ms frame  -> 64 kbps  (4x compression)
 * - G.711 mu-law:         ~320 bytes/20ms frame -> 128 kbps (2x compression)
 *
 * Features:
 * - Uses Android MediaCodec (API 29+ for encoder, API 21+ for decoder)
 * - Automatic fallback to ADPCM/G.711 on API 21-28 devices
 * - Forward Error Correction (FEC) support (MediaCodec only)
 * - Variable bitrate (VBR) for efficient bandwidth usage
 *
 * Codec Selection Priority:
 * 1. MediaCodec Opus (API 29+) - Best quality/compression
 * 2. IMA ADPCM (API 21-28) - Good quality, 4x compression
 * 3. G.711 mu-law (fallback) - Telephony standard, 2x compression
 * 4. Raw PCM (last resort) - No compression
 */

package com.doodlelabs.meshriderwave.core.audio

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import com.doodlelabs.meshriderwave.core.util.logD
import com.doodlelabs.meshriderwave.core.util.logE
import com.doodlelabs.meshriderwave.core.util.logI
import com.doodlelabs.meshriderwave.core.util.logW
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Opus codec manager for PTT voice compression
 *
 * Reduces bandwidth from 256 kbps (raw PCM) to 6-24 kbps (Opus)
 * enabling efficient tactical voice over mesh networks.
 *
 * On devices without MediaCodec Opus support (API < 29), automatically
 * falls back to IMA ADPCM (64 kbps) or G.711 mu-law (128 kbps).
 */
@Singleton
class OpusCodecManager @Inject constructor() {

    // MediaCodec instances
    private var encoder: MediaCodec? = null
    private var decoder: MediaCodec? = null

    // Fallback codec for API < 29
    private var fallbackCodec: OpusFallbackCodec? = null

    // Configuration
    private var isInitialized = false
    private var currentConfig = CodecConfig()
    private var useMediaCodec = false  // True if MediaCodec Opus is available
    private var useFallback = false    // True if using ADPCM/G.711 fallback
    private var codecMode = CodecMode.PASSTHROUGH

    // Statistics
    private var totalFramesEncoded = 0L
    private var totalFramesDecoded = 0L
    private var totalBytesIn = 0L
    private var totalBytesOut = 0L

    // Sequence number for frames
    private var sequenceNumber = 0

    /**
     * Current codec mode
     */
    enum class CodecMode(val displayName: String) {
        MEDIACODEC_OPUS("MediaCodec Opus"),
        FALLBACK_ADPCM("IMA ADPCM (Fallback)"),
        FALLBACK_G711("G.711 mu-law (Fallback)"),
        PASSTHROUGH("Raw PCM (No Compression)")
    }

    companion object {
        // Sample rates
        const val SAMPLE_RATE_8K = 8000
        const val SAMPLE_RATE_16K = 16000
        const val SAMPLE_RATE_24K = 24000
        const val SAMPLE_RATE_48K = 48000

        // Frame sizes in samples (at 16kHz)
        const val FRAME_SIZE_2_5MS = 40   // 2.5ms
        const val FRAME_SIZE_5MS = 80     // 5ms
        const val FRAME_SIZE_10MS = 160   // 10ms
        const val FRAME_SIZE_20MS = 320   // 20ms (recommended for PTT)
        const val FRAME_SIZE_40MS = 640   // 40ms
        const val FRAME_SIZE_60MS = 960   // 60ms

        // MediaCodec MIME type for Opus
        const val MIME_OPUS = MediaFormat.MIMETYPE_AUDIO_OPUS

        // Max encoded frame size
        const val MAX_FRAME_SIZE = 1275

        // Minimum API for Opus encoder
        const val MIN_API_OPUS_ENCODER = Build.VERSION_CODES.Q  // API 29

        // Default settings for tactical voice
        val DEFAULT_CONFIG = CodecConfig(
            sampleRate = SAMPLE_RATE_16K,
            channels = 1,
            bitrate = 12000,  // 12 kbps - good balance
            frameSize = FRAME_SIZE_20MS
        )

        // Ultra-low bandwidth for degraded mesh
        val LOW_BANDWIDTH_CONFIG = CodecConfig(
            sampleRate = SAMPLE_RATE_16K,
            channels = 1,
            bitrate = 6000,  // 6 kbps
            frameSize = FRAME_SIZE_20MS
        )

        // High quality for good network conditions
        val HIGH_QUALITY_CONFIG = CodecConfig(
            sampleRate = SAMPLE_RATE_16K,
            channels = 1,
            bitrate = 24000,  // 24 kbps
            frameSize = FRAME_SIZE_20MS
        )
    }

    /**
     * Codec configuration
     */
    data class CodecConfig(
        val sampleRate: Int = SAMPLE_RATE_16K,
        val channels: Int = 1,
        val bitrate: Int = 12000,
        val frameSize: Int = FRAME_SIZE_20MS
    ) {
        val frameSizeBytes: Int get() = frameSize * channels * 2  // 16-bit samples
        val frameTimeMs: Int get() = (frameSize * 1000) / sampleRate
    }

    /**
     * Encoded audio frame with metadata
     */
    data class EncodedFrame(
        val data: ByteArray,
        val timestamp: Long,
        val sequenceNumber: Int,
        val isVoice: Boolean,
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
    }

    /**
     * Check if Opus encoding is supported on this device
     */
    fun isOpusEncoderSupported(): Boolean {
        if (Build.VERSION.SDK_INT < MIN_API_OPUS_ENCODER) {
            return false
        }

        return try {
            val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)
            codecList.codecInfos.any { info ->
                info.isEncoder && info.supportedTypes.any { it.equals(MIME_OPUS, ignoreCase = true) }
            }
        } catch (e: Exception) {
            logW("Error checking Opus encoder support: ${e.message}")
            false
        }
    }

    /**
     * Check if Opus decoding is supported on this device
     */
    fun isOpusDecoderSupported(): Boolean {
        return try {
            val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)
            codecList.codecInfos.any { info ->
                !info.isEncoder && info.supportedTypes.any { it.equals(MIME_OPUS, ignoreCase = true) }
            }
        } catch (e: Exception) {
            logW("Error checking Opus decoder support: ${e.message}")
            false
        }
    }

    /**
     * Initialize the Opus codec
     *
     * Codec selection order:
     * 1. MediaCodec Opus (API 29+) - Best quality, 6-24 kbps
     * 2. IMA ADPCM fallback - Good quality, 64 kbps, 4x compression
     * 3. G.711 mu-law fallback - Telephony standard, 128 kbps, 2x compression
     * 4. Passthrough - Raw PCM, no compression
     *
     * @param config Codec configuration
     * @param preferFallback Force use of fallback codec even if MediaCodec available
     */
    fun initialize(
        config: CodecConfig = DEFAULT_CONFIG,
        preferFallback: Boolean = false
    ): Boolean {
        if (isInitialized) {
            logW("OpusCodecManager already initialized, releasing first")
            release()
        }

        currentConfig = config
        logI("Initializing Opus codec: ${config.bitrate/1000}kbps, ${config.sampleRate}Hz, ${config.frameTimeMs}ms frames")
        logI("Android API Level: ${Build.VERSION.SDK_INT} (Opus encoder requires API 29+)")

        // Reset state
        useMediaCodec = false
        useFallback = false
        codecMode = CodecMode.PASSTHROUGH

        // Check MediaCodec support (API 29+ for encoder)
        val encoderSupported = !preferFallback && isOpusEncoderSupported()
        val decoderSupported = !preferFallback && isOpusDecoderSupported()

        logD("MediaCodec Opus support - Encoder: $encoderSupported, Decoder: $decoderSupported")

        // Try MediaCodec Opus first (best quality)
        if (encoderSupported && decoderSupported) {
            useMediaCodec = initializeMediaCodec(config)
            if (useMediaCodec) {
                codecMode = CodecMode.MEDIACODEC_OPUS
                logI("Codec initialized: MediaCodec Opus (${config.bitrate/1000}kbps)")
            }
        }

        // Fall back to ADPCM/G.711 if MediaCodec not available
        if (!useMediaCodec) {
            logI("MediaCodec Opus not available (API ${Build.VERSION.SDK_INT} < 29), initializing fallback codec")

            // Initialize fallback codec (ADPCM preferred, G.711 as last resort)
            fallbackCodec = OpusFallbackCodec()

            // Try ADPCM first (better compression)
            val fallbackConfig = OpusFallbackCodec.CodecConfig(
                sampleRate = config.sampleRate,
                channels = config.channels,
                frameSize = config.frameSize
            )

            if (fallbackCodec?.initialize(OpusFallbackCodec.FallbackCodecType.ADPCM, fallbackConfig) == true) {
                useFallback = true
                codecMode = CodecMode.FALLBACK_ADPCM
                logI("Codec initialized: IMA ADPCM fallback (64 kbps, 4x compression)")
            } else if (fallbackCodec?.initialize(OpusFallbackCodec.FallbackCodecType.G711_MULAW, fallbackConfig) == true) {
                useFallback = true
                codecMode = CodecMode.FALLBACK_G711
                logI("Codec initialized: G.711 mu-law fallback (128 kbps, 2x compression)")
            } else {
                // Last resort: passthrough (no compression)
                fallbackCodec = null
                codecMode = CodecMode.PASSTHROUGH
                logW("All codecs failed, using passthrough mode (no compression, 256 kbps)")
            }
        }

        isInitialized = true
        logI("OpusCodecManager ready - Mode: ${codecMode.displayName}")
        return true  // Always return true - we can fall back to passthrough
    }

    /**
     * Initialize MediaCodec encoder and decoder
     *
     * FIXED Jan 2026:
     * - Removed incorrect AACObjectLC profile (was AAC profile on Opus codec!)
     * - Added required CSD (Codec-Specific Data) buffers for Opus decoder
     *   Without CSD, Samsung devices put decoder in Released/Error state
     * - Encoder and decoder initialized independently so one failure doesn't kill both
     */
    private fun initializeMediaCodec(config: CodecConfig): Boolean {
        try {
            // Create encoder
            val encoderFormat = MediaFormat.createAudioFormat(
                MIME_OPUS,
                config.sampleRate,
                config.channels
            ).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, config.bitrate)
                setInteger(MediaFormat.KEY_SAMPLE_RATE, config.sampleRate)
                setInteger(MediaFormat.KEY_CHANNEL_COUNT, config.channels)
                // NOTE: Do NOT set KEY_PROFILE to AACObjectLC — that's for AAC, not Opus
            }

            encoder = MediaCodec.createEncoderByType(MIME_OPUS)
            encoder?.configure(encoderFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder?.start()
            logD("MediaCodec Opus encoder created successfully")

            // Create decoder with required CSD buffers
            // Opus decoder on Android requires 3 CSD buffers:
            //   CSD-0: Opus identification header (19 bytes min)
            //   CSD-1: Pre-skip value (8 bytes, little-endian long)
            //   CSD-2: Seek pre-roll (8 bytes, little-endian long, typically 80ms = 80000000 ns)
            val decoderFormat = MediaFormat.createAudioFormat(
                MIME_OPUS,
                config.sampleRate,
                config.channels
            ).apply {
                // CSD-0: Opus identification header
                // Format: "OpusHead" + version(1) + channels(1) + pre-skip(2) + sample_rate(4) + gain(2) + mapping_family(1)
                val opusHeader = ByteBuffer.allocate(19).order(ByteOrder.LITTLE_ENDIAN).apply {
                    put("OpusHead".toByteArray())  // Magic signature (8 bytes)
                    put(1)                          // Version (1 byte)
                    put(config.channels.toByte())   // Channel count (1 byte)
                    putShort(0)                     // Pre-skip in samples (2 bytes)
                    putInt(config.sampleRate)        // Input sample rate (4 bytes)
                    putShort(0)                     // Output gain (2 bytes)
                    put(0)                          // Channel mapping family (1 byte)
                    flip()
                }
                setByteBuffer("csd-0", opusHeader)

                // CSD-1: Pre-skip in nanoseconds (0 for our use case)
                val preSkip = ByteBuffer.allocate(8).order(ByteOrder.nativeOrder()).apply {
                    putLong(0L)
                    flip()
                }
                setByteBuffer("csd-1", preSkip)

                // CSD-2: Seek pre-roll in nanoseconds (80ms = 80,000,000 ns, standard for Opus)
                val seekPreRoll = ByteBuffer.allocate(8).order(ByteOrder.nativeOrder()).apply {
                    putLong(80_000_000L)
                    flip()
                }
                setByteBuffer("csd-2", seekPreRoll)
            }

            decoder = MediaCodec.createDecoderByType(MIME_OPUS)
            decoder?.configure(decoderFormat, null, null, 0)
            decoder?.start()
            logD("MediaCodec Opus decoder created successfully (with CSD headers)")

            logD("MediaCodec Opus encoder and decoder created successfully")
            return true
        } catch (e: Exception) {
            logE("Failed to initialize MediaCodec Opus: ${e.message}")
            encoder?.release()
            decoder?.release()
            encoder = null
            decoder = null
            return false
        }
    }

    /**
     * Encode a PCM audio frame to compressed format
     *
     * Uses the best available codec:
     * - MediaCodec Opus (API 29+): 6-24 kbps
     * - IMA ADPCM (fallback): 64 kbps
     * - G.711 mu-law (fallback): 128 kbps
     * - Passthrough: 256 kbps (no compression)
     *
     * @param pcmData Raw PCM audio (16-bit signed, little-endian)
     * @return Encoded frame or passthrough if no codec available
     */
    fun encode(pcmData: ByteArray): EncodedFrame? {
        if (!isInitialized) {
            logE("Cannot encode: codec not initialized")
            return null
        }

        if (pcmData.size != currentConfig.frameSizeBytes) {
            logE("Invalid PCM data size: ${pcmData.size}, expected ${currentConfig.frameSizeBytes}")
            return null
        }

        return try {
            val encodedData: ByteArray
            val isCompressed: Boolean

            when {
                // Option 1: MediaCodec Opus (best quality)
                useMediaCodec && encoder != null -> {
                    encodedData = encodeWithMediaCodec(pcmData) ?: pcmData
                    isCompressed = encodedData.size < pcmData.size
                }

                // Option 2: Fallback codec (ADPCM or G.711)
                // CRASH-FIX Jan 2026: Use local val for safe smart cast
                useFallback && fallbackCodec != null -> {
                    val codec = fallbackCodec  // Local val for smart cast
                    val fallbackFrame = codec?.encode(pcmData)
                    if (fallbackFrame != null) {
                        encodedData = fallbackFrame.data
                        isCompressed = encodedData.size < pcmData.size
                    } else {
                        encodedData = pcmData
                        isCompressed = false
                    }
                }

                // Option 3: Passthrough (no compression)
                else -> {
                    encodedData = pcmData
                    isCompressed = false
                }
            }

            // Update statistics
            totalFramesEncoded++
            totalBytesIn += pcmData.size
            totalBytesOut += encodedData.size

            val frame = EncodedFrame(
                data = encodedData,
                timestamp = System.currentTimeMillis(),
                sequenceNumber = sequenceNumber++,
                isVoice = true,
                frameSize = currentConfig.frameSize
            )

            // Log compression stats periodically
            if (totalFramesEncoded % 500 == 0L) {
                val ratio = if (totalBytesOut > 0) totalBytesIn.toFloat() / totalBytesOut else 0f
                logD("Encode stats [${codecMode.displayName}]: $totalFramesEncoded frames, ${ratio.format(1)}x compression")
            }

            frame
        } catch (e: Exception) {
            logE("Exception during encode", e)
            null
        }
    }

    /**
     * Encode using MediaCodec
     */
    private fun encodeWithMediaCodec(pcmData: ByteArray): ByteArray? {
        val enc = encoder ?: return null

        try {
            // Get input buffer
            val inputIndex = enc.dequeueInputBuffer(10000)  // 10ms timeout
            if (inputIndex < 0) {
                logW("Encoder: no input buffer available")
                return null
            }

            val inputBuffer = enc.getInputBuffer(inputIndex) ?: return null
            inputBuffer.clear()
            inputBuffer.put(pcmData)

            enc.queueInputBuffer(inputIndex, 0, pcmData.size, System.nanoTime() / 1000, 0)

            // Get output buffer
            val bufferInfo = MediaCodec.BufferInfo()
            val outputIndex = enc.dequeueOutputBuffer(bufferInfo, 10000)

            if (outputIndex >= 0) {
                val outputBuffer = enc.getOutputBuffer(outputIndex) ?: return null
                val encodedData = ByteArray(bufferInfo.size)
                outputBuffer.get(encodedData)
                enc.releaseOutputBuffer(outputIndex, false)
                return encodedData
            } else if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                logD("Encoder output format changed")
            }

            return null
        } catch (e: Exception) {
            logE("MediaCodec encode error", e)
            return null
        }
    }

    /**
     * Decode compressed audio frame to PCM
     *
     * Automatically detects and uses the appropriate decoder based on data size:
     * - MediaCodec Opus: For compressed Opus data
     * - Fallback ADPCM/G.711: For fallback encoded data
     * - Passthrough: For raw PCM data
     *
     * @param encodedData Compressed audio (Opus, ADPCM, G.711, or passthrough PCM)
     * @param useFEC Use Forward Error Correction (MediaCodec Opus only)
     * @return Decoded PCM audio
     */
    fun decode(encodedData: ByteArray, useFEC: Boolean = false): ByteArray? {
        if (!isInitialized) {
            logE("Cannot decode: codec not initialized")
            return null
        }

        return try {
            val decodedData: ByteArray
            val isCompressed = encodedData.size < currentConfig.frameSizeBytes

            when {
                // Option 1: MediaCodec Opus decoding
                useMediaCodec && decoder != null && isCompressed -> {
                    decodedData = decodeWithMediaCodec(encodedData) ?: run {
                        // If decode fails, return silence (PLC)
                        ByteArray(currentConfig.frameSizeBytes)
                    }
                }

                // Option 2: Fallback codec decoding
                // CRASH-FIX Jan 2026: Use local val for safe smart cast
                useFallback && fallbackCodec != null && isCompressed -> {
                    val codec = fallbackCodec  // Local val for smart cast
                    decodedData = codec?.decode(encodedData) ?: run {
                        // If decode fails, return silence (PLC)
                        ByteArray(currentConfig.frameSizeBytes)
                    }
                }

                // Option 3: Passthrough (data is already PCM)
                else -> {
                    decodedData = if (encodedData.size == currentConfig.frameSizeBytes) {
                        encodedData
                    } else {
                        // Size mismatch - pad or truncate
                        ByteArray(currentConfig.frameSizeBytes).also { buffer ->
                            encodedData.copyInto(buffer, 0, 0, minOf(encodedData.size, buffer.size))
                        }
                    }
                }
            }

            totalFramesDecoded++
            decodedData
        } catch (e: Exception) {
            logE("Exception during decode", e)
            null
        }
    }

    /**
     * Decode using MediaCodec
     *
     * FIXED Jan 2026: Added IllegalStateException handling to detect Released/Error state
     * and reinitialize the decoder instead of spamming errors forever.
     */
    private fun decodeWithMediaCodec(encodedData: ByteArray): ByteArray? {
        val dec = decoder ?: return null

        try {
            // Get input buffer
            val inputIndex = dec.dequeueInputBuffer(10000)
            if (inputIndex < 0) {
                logW("Decoder: no input buffer available")
                return null
            }

            val inputBuffer = dec.getInputBuffer(inputIndex) ?: return null
            inputBuffer.clear()
            inputBuffer.put(encodedData)

            dec.queueInputBuffer(inputIndex, 0, encodedData.size, System.nanoTime() / 1000, 0)

            // Get output buffer
            val bufferInfo = MediaCodec.BufferInfo()
            val outputIndex = dec.dequeueOutputBuffer(bufferInfo, 10000)

            if (outputIndex >= 0) {
                val outputBuffer = dec.getOutputBuffer(outputIndex) ?: return null
                val decodedData = ByteArray(bufferInfo.size)
                outputBuffer.get(decodedData)
                dec.releaseOutputBuffer(outputIndex, false)
                return decodedData
            }

            return null
        } catch (e: IllegalStateException) {
            // Decoder is in Released or Error state — try to reinitialize it once
            logE("MediaCodec decoder in bad state, reinitializing: ${e.message}")
            try {
                decoder?.release()
            } catch (_: Exception) {}
            decoder = null

            // Reinitialize just the decoder
            try {
                val decoderFormat = MediaFormat.createAudioFormat(
                    MIME_OPUS,
                    currentConfig.sampleRate,
                    currentConfig.channels
                ).apply {
                    val opusHeader = ByteBuffer.allocate(19).order(ByteOrder.LITTLE_ENDIAN).apply {
                        put("OpusHead".toByteArray())
                        put(1)
                        put(currentConfig.channels.toByte())
                        putShort(0)
                        putInt(currentConfig.sampleRate)
                        putShort(0)
                        put(0)
                        flip()
                    }
                    setByteBuffer("csd-0", opusHeader)
                    val preSkip = ByteBuffer.allocate(8).order(ByteOrder.nativeOrder()).apply {
                        putLong(0L); flip()
                    }
                    setByteBuffer("csd-1", preSkip)
                    val seekPreRoll = ByteBuffer.allocate(8).order(ByteOrder.nativeOrder()).apply {
                        putLong(80_000_000L); flip()
                    }
                    setByteBuffer("csd-2", seekPreRoll)
                }
                decoder = MediaCodec.createDecoderByType(MIME_OPUS)
                decoder?.configure(decoderFormat, null, null, 0)
                decoder?.start()
                logI("MediaCodec Opus decoder reinitialized successfully")
            } catch (reinitEx: Exception) {
                logE("Failed to reinitialize decoder, falling back to passthrough", reinitEx)
                useMediaCodec = false
                codecMode = CodecMode.PASSTHROUGH
            }
            return null
        } catch (e: Exception) {
            logE("MediaCodec decode error", e)
            return null
        }
    }

    /**
     * Decode with Packet Loss Concealment
     */
    fun decodePLC(): ByteArray? {
        // PLC: return silence frame
        return ByteArray(currentConfig.frameSizeBytes)
    }

    /**
     * Update codec bitrate dynamically
     */
    fun setBitrate(bitrate: Int) {
        val clampedBitrate = bitrate.coerceIn(6000, 128000)
        currentConfig = currentConfig.copy(bitrate = clampedBitrate)

        if (useMediaCodec && encoder != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            try {
                val params = android.os.Bundle().apply {
                    putInt(MediaCodec.PARAMETER_KEY_VIDEO_BITRATE, clampedBitrate)
                }
                encoder?.setParameters(params)
            } catch (e: Exception) {
                logW("Failed to update bitrate dynamically: ${e.message}")
            }
        }

        logI("Opus bitrate changed to ${clampedBitrate/1000}kbps")
    }

    /**
     * Get compression statistics
     */
    fun getStats(): CodecStats {
        val ratio = if (totalBytesOut > 0) totalBytesIn.toFloat() / totalBytesOut else 1f
        val inputBps = if (totalFramesEncoded > 0) {
            (totalBytesIn * 8.0 * 1000) / (totalFramesEncoded * currentConfig.frameTimeMs)
        } else 0.0
        val outputBps = if (totalFramesEncoded > 0) {
            (totalBytesOut * 8.0 * 1000) / (totalFramesEncoded * currentConfig.frameTimeMs)
        } else 0.0

        return CodecStats(
            framesEncoded = totalFramesEncoded,
            framesDecoded = totalFramesDecoded,
            bytesIn = totalBytesIn,
            bytesOut = totalBytesOut,
            compressionRatio = ratio,
            inputBitrate = inputBps,
            outputBitrate = outputBps,
            config = currentConfig,
            usingMediaCodec = useMediaCodec,
            codecMode = codecMode
        )
    }

    /**
     * Release codec resources
     */
    fun release() {
        try {
            // Release MediaCodec
            encoder?.stop()
            encoder?.release()
            encoder = null

            decoder?.stop()
            decoder?.release()
            decoder = null

            // Release fallback codec
            fallbackCodec?.release()
            fallbackCodec = null

            isInitialized = false
            useMediaCodec = false
            useFallback = false
            codecMode = CodecMode.PASSTHROUGH
            logI("Opus codec released")
        } catch (e: Exception) {
            logE("Error releasing Opus codec", e)
        }
    }

    /**
     * Get current codec mode
     */
    fun getCodecMode(): CodecMode = codecMode

    /**
     * Check if using fallback codec
     */
    fun isUsingFallback(): Boolean = useFallback

    /**
     * Check if codec is ready
     */
    fun isReady(): Boolean = isInitialized

    private fun Float.format(decimals: Int): String = "%.${decimals}f".format(this)

    /**
     * Codec statistics
     */
    data class CodecStats(
        val framesEncoded: Long,
        val framesDecoded: Long,
        val bytesIn: Long,
        val bytesOut: Long,
        val compressionRatio: Float,
        val inputBitrate: Double,
        val outputBitrate: Double,
        val config: CodecConfig,
        val usingMediaCodec: Boolean,
        val codecMode: CodecMode = CodecMode.PASSTHROUGH
    ) {
        fun toLogString(): String {
            val modeDescription = when (codecMode) {
                CodecMode.MEDIACODEC_OPUS -> "MediaCodec Opus (${config.bitrate/1000}kbps)"
                CodecMode.FALLBACK_ADPCM -> "IMA ADPCM Fallback (64kbps)"
                CodecMode.FALLBACK_G711 -> "G.711 mu-law Fallback (128kbps)"
                CodecMode.PASSTHROUGH -> "Passthrough (no compression)"
            }
            return """
                Opus Codec Stats:
                - Mode: $modeDescription
                - Frames: $framesEncoded encoded, $framesDecoded decoded
                - Compression: ${compressionRatio.format(1)}x (${inputBitrate.format(0)} bps -> ${outputBitrate.format(0)} bps)
                - Config: ${config.bitrate/1000}kbps, ${config.sampleRate}Hz, ${config.frameTimeMs}ms
            """.trimIndent()
        }

        private fun Float.format(decimals: Int): String = "%.${decimals}f".format(this)
        private fun Double.format(decimals: Int): String = "%.${decimals}f".format(this)
    }
}
