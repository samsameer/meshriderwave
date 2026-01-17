/*
 * Mesh Rider Wave - Opus Codec Manager Unit Tests
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 */

package com.doodlelabs.meshriderwave.core.audio

import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for OpusCodecManager
 *
 * Note: MediaCodec-based Opus encoding is only available on API 29+.
 * These tests verify the codec configuration and passthrough behavior
 * for devices without MediaCodec Opus support.
 */
class OpusCodecManagerTest {

    private lateinit var codecManager: OpusCodecManager

    @Before
    fun setUp() {
        codecManager = OpusCodecManager()
    }

    @After
    fun tearDown() {
        codecManager.release()
    }

    // =========================================================================
    // Configuration Tests
    // =========================================================================

    @Test
    fun `default config has correct values`() {
        val config = OpusCodecManager.DEFAULT_CONFIG
        assertEquals(16000, config.sampleRate)
        assertEquals(1, config.channels)
        assertEquals(12000, config.bitrate)
        assertEquals(320, config.frameSize)
        assertEquals(640, config.frameSizeBytes)  // 320 samples * 2 bytes
        assertEquals(20, config.frameTimeMs)  // 320 samples / 16000 Hz * 1000
    }

    @Test
    fun `low bandwidth config has 6kbps`() {
        val config = OpusCodecManager.LOW_BANDWIDTH_CONFIG
        assertEquals(6000, config.bitrate)
    }

    @Test
    fun `high quality config has 24kbps`() {
        val config = OpusCodecManager.HIGH_QUALITY_CONFIG
        assertEquals(24000, config.bitrate)
    }

    @Test
    fun `frame size bytes calculated correctly for mono`() {
        val config = OpusCodecManager.CodecConfig(
            sampleRate = 16000,
            channels = 1,
            bitrate = 12000,
            frameSize = 320
        )
        assertEquals(640, config.frameSizeBytes)  // 320 * 1 * 2
    }

    @Test
    fun `frame size bytes calculated correctly for stereo`() {
        val config = OpusCodecManager.CodecConfig(
            sampleRate = 16000,
            channels = 2,
            bitrate = 12000,
            frameSize = 320
        )
        assertEquals(1280, config.frameSizeBytes)  // 320 * 2 * 2
    }

    @Test
    fun `frame time calculated correctly for different rates`() {
        // 16kHz with 320 samples = 20ms
        val config16k = OpusCodecManager.CodecConfig(
            sampleRate = 16000,
            frameSize = 320
        )
        assertEquals(20, config16k.frameTimeMs)

        // 48kHz with 960 samples = 20ms
        val config48k = OpusCodecManager.CodecConfig(
            sampleRate = 48000,
            frameSize = 960
        )
        assertEquals(20, config48k.frameTimeMs)

        // 8kHz with 160 samples = 20ms
        val config8k = OpusCodecManager.CodecConfig(
            sampleRate = 8000,
            frameSize = 160
        )
        assertEquals(20, config8k.frameTimeMs)
    }

    // =========================================================================
    // Initialization Tests
    // =========================================================================

    @Test
    fun `initialize returns true`() {
        // Should always return true (falls back to passthrough)
        val result = codecManager.initialize()
        assertTrue(result)
    }

    @Test
    fun `initialize with custom config`() {
        val config = OpusCodecManager.CodecConfig(
            sampleRate = 8000,
            channels = 1,
            bitrate = 8000,
            frameSize = 160
        )
        val result = codecManager.initialize(config)
        assertTrue(result)
    }

    @Test
    fun `getStats returns correct config after initialize`() {
        val config = OpusCodecManager.CodecConfig(
            sampleRate = 16000,
            channels = 1,
            bitrate = 12000,
            frameSize = 320
        )
        codecManager.initialize(config)

        val stats = codecManager.getStats()
        assertEquals(config.sampleRate, stats.config.sampleRate)
        assertEquals(config.channels, stats.config.channels)
        assertEquals(config.bitrate, stats.config.bitrate)
        assertEquals(config.frameSize, stats.config.frameSize)
    }

    // =========================================================================
    // Encode/Decode Tests (Passthrough Mode)
    // =========================================================================

    @Test
    fun `encode returns null when not initialized`() {
        val pcmData = ByteArray(640)
        val result = codecManager.encode(pcmData)
        assertEquals(null, result)
    }

    @Test
    fun `encode rejects wrong size input`() {
        codecManager.initialize()
        val wrongSizePcm = ByteArray(100)  // Should be 640
        val result = codecManager.encode(wrongSizePcm)
        assertEquals(null, result)
    }

    @Test
    fun `encode in passthrough mode returns same size`() {
        codecManager.initialize()
        val pcmData = ByteArray(640) { it.toByte() }
        val result = codecManager.encode(pcmData)

        assertNotNull(result)
        // In passthrough mode (no MediaCodec), size should be same
        // Note: On API 29+ with MediaCodec, size would be smaller
        assertEquals(640, result!!.data.size)
    }

    @Test
    fun `encode returns frame with metadata`() {
        codecManager.initialize()
        val pcmData = ByteArray(640)
        val result = codecManager.encode(pcmData)

        assertNotNull(result)
        assertTrue(result!!.timestamp > 0)
        assertTrue(result.isVoice)
        assertEquals(320, result.frameSize)
    }

    @Test
    fun `encode increments sequence number`() {
        codecManager.initialize()
        val pcmData = ByteArray(640)

        val frame1 = codecManager.encode(pcmData)
        val frame2 = codecManager.encode(pcmData)
        val frame3 = codecManager.encode(pcmData)

        assertNotNull(frame1)
        assertNotNull(frame2)
        assertNotNull(frame3)
        assertEquals(frame1!!.sequenceNumber + 1, frame2!!.sequenceNumber)
        assertEquals(frame2.sequenceNumber + 1, frame3!!.sequenceNumber)
    }

    @Test
    fun `decode returns null when not initialized`() {
        val encodedData = ByteArray(60)
        val result = codecManager.decode(encodedData)
        assertEquals(null, result)
    }

    @Test
    fun `decode in passthrough mode returns input`() {
        codecManager.initialize()
        val pcmData = ByteArray(640) { it.toByte() }

        // In passthrough mode, encode returns PCM, decode returns same
        val encoded = codecManager.encode(pcmData)
        assertNotNull(encoded)

        val decoded = codecManager.decode(encoded!!.data)
        assertNotNull(decoded)
        assertTrue(decoded!!.contentEquals(encoded.data))
    }

    @Test
    fun `decodePLC returns silence frame`() {
        codecManager.initialize()
        val result = codecManager.decodePLC()

        assertNotNull(result)
        assertEquals(640, result!!.size)
        assertTrue(result.all { it == 0.toByte() })
    }

    // =========================================================================
    // Statistics Tests
    // =========================================================================

    @Test
    fun `stats start at zero`() {
        codecManager.initialize()
        val stats = codecManager.getStats()

        assertEquals(0L, stats.framesEncoded)
        assertEquals(0L, stats.framesDecoded)
        assertEquals(0L, stats.bytesIn)
        assertEquals(0L, stats.bytesOut)
    }

    @Test
    fun `stats updated after encode`() {
        codecManager.initialize()
        val pcmData = ByteArray(640)

        codecManager.encode(pcmData)
        codecManager.encode(pcmData)
        codecManager.encode(pcmData)

        val stats = codecManager.getStats()
        assertEquals(3L, stats.framesEncoded)
        assertEquals(1920L, stats.bytesIn)  // 3 * 640
    }

    @Test
    fun `stats updated after decode`() {
        codecManager.initialize()
        val pcmData = ByteArray(640)

        val encoded = codecManager.encode(pcmData)
        assertNotNull(encoded)

        codecManager.decode(encoded!!.data)
        codecManager.decode(encoded.data)

        val stats = codecManager.getStats()
        assertEquals(2L, stats.framesDecoded)
    }

    @Test
    fun `compression ratio is 1x in passthrough mode`() {
        codecManager.initialize()
        val pcmData = ByteArray(640)

        repeat(10) { codecManager.encode(pcmData) }

        val stats = codecManager.getStats()
        // In passthrough mode, compression ratio should be 1.0
        assertEquals(1.0f, stats.compressionRatio, 0.01f)
    }

    // =========================================================================
    // Bitrate Tests
    // =========================================================================

    @Test
    fun `setBitrate updates config`() {
        codecManager.initialize()

        codecManager.setBitrate(24000)
        assertEquals(24000, codecManager.getStats().config.bitrate)

        codecManager.setBitrate(6000)
        assertEquals(6000, codecManager.getStats().config.bitrate)
    }

    @Test
    fun `setBitrate clamps to valid range`() {
        codecManager.initialize()

        codecManager.setBitrate(1000)  // Below minimum
        assertEquals(6000, codecManager.getStats().config.bitrate)

        codecManager.setBitrate(200000)  // Above maximum
        assertEquals(128000, codecManager.getStats().config.bitrate)
    }

    // =========================================================================
    // Resource Management Tests
    // =========================================================================

    @Test
    fun `release clears state`() {
        codecManager.initialize()
        val pcmData = ByteArray(640)
        codecManager.encode(pcmData)

        codecManager.release()

        // After release, encode should fail
        val result = codecManager.encode(pcmData)
        assertEquals(null, result)
    }

    @Test
    fun `re-initialize after release works`() {
        codecManager.initialize()
        codecManager.release()

        val result = codecManager.initialize()
        assertTrue(result)

        val pcmData = ByteArray(640)
        val encoded = codecManager.encode(pcmData)
        assertNotNull(encoded)
    }

    // =========================================================================
    // EncodedFrame Tests
    // =========================================================================

    @Test
    fun `encoded frames with same data are equal`() {
        val data = ByteArray(60) { it.toByte() }
        val frame1 = OpusCodecManager.EncodedFrame(
            data = data,
            timestamp = 1000L,
            sequenceNumber = 1,
            isVoice = true,
            frameSize = 320
        )
        val frame2 = OpusCodecManager.EncodedFrame(
            data = data.copyOf(),
            timestamp = 1000L,
            sequenceNumber = 1,
            isVoice = true,
            frameSize = 320
        )

        assertEquals(frame1, frame2)
        assertEquals(frame1.hashCode(), frame2.hashCode())
    }

    @Test
    fun `encoded frames with different sequence are not equal`() {
        val data = ByteArray(60) { it.toByte() }
        val frame1 = OpusCodecManager.EncodedFrame(
            data = data,
            timestamp = 1000L,
            sequenceNumber = 1,
            isVoice = true,
            frameSize = 320
        )
        val frame2 = OpusCodecManager.EncodedFrame(
            data = data.copyOf(),
            timestamp = 1000L,
            sequenceNumber = 2,  // Different sequence
            isVoice = true,
            frameSize = 320
        )

        assertTrue(frame1 != frame2)
    }

    // =========================================================================
    // CodecStats Tests
    // =========================================================================

    @Test
    fun `stats toLogString contains key info`() {
        codecManager.initialize()
        repeat(5) { codecManager.encode(ByteArray(640)) }

        val stats = codecManager.getStats()
        val logString = stats.toLogString()

        assertTrue(logString.contains("Opus Codec Stats"))
        assertTrue(logString.contains("Mode:"))
        assertTrue(logString.contains("Frames:"))
        assertTrue(logString.contains("Compression:"))
        assertTrue(logString.contains("Config:"))
    }
}
