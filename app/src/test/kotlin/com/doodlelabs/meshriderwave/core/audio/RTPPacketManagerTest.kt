/*
 * Mesh Rider Wave - RTP Packet Manager Unit Tests
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 */

package com.doodlelabs.meshriderwave.core.audio

import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for RTPPacketManager
 *
 * Tests RTP packet serialization, parsing, and configuration.
 * Network tests require actual network interfaces and are skipped in unit tests.
 */
class RTPPacketManagerTest {

    private lateinit var rtpManager: RTPPacketManager

    @Before
    fun setUp() {
        rtpManager = RTPPacketManager()
    }

    @After
    fun tearDown() {
        rtpManager.release()
    }

    // =========================================================================
    // Constants Tests
    // =========================================================================

    @Test
    fun `RTP constants have correct values`() {
        assertEquals(2, RTPPacketManager.RTP_VERSION)
        assertEquals(12, RTPPacketManager.RTP_HEADER_SIZE)
        assertEquals(111, RTPPacketManager.PAYLOAD_TYPE_OPUS)
        assertEquals(48000, RTPPacketManager.CLOCK_RATE_OPUS)
        assertEquals(5004, RTPPacketManager.MULTICAST_PORT)
    }

    @Test
    fun `DSCP values are correct per RFC 2474`() {
        assertEquals(46, RTPPacketManager.DSCP_EF)        // Expedited Forwarding
        assertEquals(34, RTPPacketManager.DSCP_AF41)      // Assured Forwarding 41
        assertEquals(0, RTPPacketManager.DSCP_DEFAULT)    // Best Effort
    }

    @Test
    fun `TOS values calculated correctly from DSCP`() {
        // TOS = DSCP << 2
        assertEquals(184, RTPPacketManager.TOS_EF)   // 46 << 2 = 0xB8
        assertEquals(136, RTPPacketManager.TOS_AF41) // 34 << 2 = 0x88
    }

    @Test
    fun `multicast base address is in organization-local scope`() {
        val base = RTPPacketManager.MULTICAST_BASE
        assertTrue(base.startsWith("239.255."))  // 239.255.0.0/16
    }

    @Test
    fun `jitter buffer settings are reasonable`() {
        assertTrue(RTPPacketManager.JITTER_BUFFER_MIN_MS < RTPPacketManager.JITTER_BUFFER_TARGET_MS)
        assertTrue(RTPPacketManager.JITTER_BUFFER_TARGET_MS < RTPPacketManager.JITTER_BUFFER_MAX_MS)
        assertEquals(20, RTPPacketManager.JITTER_BUFFER_MIN_MS)
        assertEquals(40, RTPPacketManager.JITTER_BUFFER_TARGET_MS)
        assertEquals(100, RTPPacketManager.JITTER_BUFFER_MAX_MS)
    }

    // =========================================================================
    // RTPPacket Serialization Tests
    // =========================================================================

    @Test
    fun `RTPPacket serializes to correct size`() {
        val payload = ByteArray(60) { it.toByte() }
        val packet = RTPPacketManager.RTPPacket(
            sequenceNumber = 1234,
            timestamp = 5678L,
            ssrc = 0xDEADBEEF,
            payload = payload
        )

        val bytes = packet.toBytes()
        assertEquals(RTPPacketManager.RTP_HEADER_SIZE + payload.size, bytes.size)
        assertEquals(72, bytes.size)  // 12 + 60
    }

    @Test
    fun `RTPPacket header has correct version`() {
        val packet = RTPPacketManager.RTPPacket(
            sequenceNumber = 0,
            timestamp = 0,
            ssrc = 0,
            payload = ByteArray(10)
        )

        val bytes = packet.toBytes()
        val version = (bytes[0].toInt() ushr 6) and 0x03
        assertEquals(2, version)
    }

    @Test
    fun `RTPPacket marker bit is set correctly`() {
        val packetWithMarker = RTPPacketManager.RTPPacket(
            marker = true,
            sequenceNumber = 0,
            timestamp = 0,
            ssrc = 0,
            payload = ByteArray(10)
        )
        val packetWithoutMarker = RTPPacketManager.RTPPacket(
            marker = false,
            sequenceNumber = 0,
            timestamp = 0,
            ssrc = 0,
            payload = ByteArray(10)
        )

        val bytesWithMarker = packetWithMarker.toBytes()
        val bytesWithoutMarker = packetWithoutMarker.toBytes()

        val markerBitWith = (bytesWithMarker[1].toInt() ushr 7) and 0x01
        val markerBitWithout = (bytesWithoutMarker[1].toInt() ushr 7) and 0x01

        assertEquals(1, markerBitWith)
        assertEquals(0, markerBitWithout)
    }

    @Test
    fun `RTPPacket payload type is encoded correctly`() {
        val packet = RTPPacketManager.RTPPacket(
            payloadType = RTPPacketManager.PAYLOAD_TYPE_OPUS,
            sequenceNumber = 0,
            timestamp = 0,
            ssrc = 0,
            payload = ByteArray(10)
        )

        val bytes = packet.toBytes()
        val payloadType = bytes[1].toInt() and 0x7F
        assertEquals(111, payloadType)
    }

    @Test
    fun `RTPPacket sequence number is big-endian`() {
        val packet = RTPPacketManager.RTPPacket(
            sequenceNumber = 0x1234,
            timestamp = 0,
            ssrc = 0,
            payload = ByteArray(10)
        )

        val bytes = packet.toBytes()
        // Bytes 2-3 are sequence number in big-endian
        assertEquals(0x12.toByte(), bytes[2])
        assertEquals(0x34.toByte(), bytes[3])
    }

    @Test
    fun `RTPPacket timestamp is big-endian`() {
        val packet = RTPPacketManager.RTPPacket(
            sequenceNumber = 0,
            timestamp = 0x12345678L,
            ssrc = 0,
            payload = ByteArray(10)
        )

        val bytes = packet.toBytes()
        // Bytes 4-7 are timestamp in big-endian
        assertEquals(0x12.toByte(), bytes[4])
        assertEquals(0x34.toByte(), bytes[5])
        assertEquals(0x56.toByte(), bytes[6])
        assertEquals(0x78.toByte(), bytes[7])
    }

    @Test
    fun `RTPPacket SSRC is big-endian`() {
        val packet = RTPPacketManager.RTPPacket(
            sequenceNumber = 0,
            timestamp = 0,
            ssrc = 0xDEADBEEF,
            payload = ByteArray(10)
        )

        val bytes = packet.toBytes()
        // Bytes 8-11 are SSRC in big-endian
        assertEquals(0xDE.toByte(), bytes[8])
        assertEquals(0xAD.toByte(), bytes[9])
        assertEquals(0xBE.toByte(), bytes[10])
        assertEquals(0xEF.toByte(), bytes[11])
    }

    @Test
    fun `RTPPacket payload is appended correctly`() {
        val payload = byteArrayOf(1, 2, 3, 4, 5)
        val packet = RTPPacketManager.RTPPacket(
            sequenceNumber = 0,
            timestamp = 0,
            ssrc = 0,
            payload = payload
        )

        val bytes = packet.toBytes()
        // Payload starts at byte 12
        assertTrue(payload.contentEquals(bytes.sliceArray(12 until 17)))
    }

    // =========================================================================
    // RTPPacket Parsing Tests
    // =========================================================================

    @Test
    fun `fromBytes returns null for too short data`() {
        val shortData = ByteArray(10)  // Less than 12 bytes header
        val packet = RTPPacketManager.RTPPacket.fromBytes(shortData)
        assertNull(packet)
    }

    @Test
    fun `fromBytes returns null for wrong version`() {
        val wrongVersion = ByteArray(12)
        wrongVersion[0] = 0xC0.toByte()  // Version 3 instead of 2
        val packet = RTPPacketManager.RTPPacket.fromBytes(wrongVersion)
        assertNull(packet)
    }

    @Test
    fun `fromBytes correctly parses valid packet`() {
        val original = RTPPacketManager.RTPPacket(
            marker = true,
            payloadType = RTPPacketManager.PAYLOAD_TYPE_OPUS,
            sequenceNumber = 12345,
            timestamp = 67890L,
            ssrc = 0xABCDEF01,
            payload = byteArrayOf(10, 20, 30, 40, 50)
        )

        val bytes = original.toBytes()
        val parsed = RTPPacketManager.RTPPacket.fromBytes(bytes)

        assertNotNull(parsed)
        assertEquals(original.marker, parsed!!.marker)
        assertEquals(original.payloadType, parsed.payloadType)
        assertEquals(original.sequenceNumber, parsed.sequenceNumber)
        assertEquals(original.timestamp, parsed.timestamp)
        assertEquals(original.ssrc, parsed.ssrc)
        assertTrue(original.payload.contentEquals(parsed.payload))
    }

    @Test
    fun `roundtrip serialization preserves all fields`() {
        val packets = listOf(
            RTPPacketManager.RTPPacket(
                marker = true,
                payloadType = 0,
                sequenceNumber = 0,
                timestamp = 0L,
                ssrc = 0L,
                payload = ByteArray(0)
            ),
            RTPPacketManager.RTPPacket(
                marker = false,
                payloadType = 127,
                sequenceNumber = 65535,
                timestamp = 0xFFFFFFFFL,
                ssrc = 0xFFFFFFFFL,
                payload = ByteArray(1000) { (it % 256).toByte() }
            )
        )

        for (original in packets) {
            val bytes = original.toBytes()
            val parsed = RTPPacketManager.RTPPacket.fromBytes(bytes)

            assertNotNull(parsed)
            assertEquals(original.marker, parsed!!.marker)
            assertEquals(original.payloadType, parsed.payloadType)
            assertEquals(original.sequenceNumber, parsed.sequenceNumber)
            assertEquals(original.timestamp, parsed.timestamp)
            assertEquals(original.ssrc, parsed.ssrc)
            assertTrue(original.payload.contentEquals(parsed.payload))
        }
    }

    // =========================================================================
    // RTPPacket Equality Tests
    // =========================================================================

    @Test
    fun `packets with same seq and ssrc are equal`() {
        val packet1 = RTPPacketManager.RTPPacket(
            sequenceNumber = 100,
            timestamp = 1000L,
            ssrc = 12345L,
            payload = ByteArray(10)
        )
        val packet2 = RTPPacketManager.RTPPacket(
            sequenceNumber = 100,
            timestamp = 2000L,  // Different timestamp
            ssrc = 12345L,
            payload = ByteArray(20)  // Different payload
        )

        assertEquals(packet1, packet2)
        assertEquals(packet1.hashCode(), packet2.hashCode())
    }

    @Test
    fun `packets with different seq are not equal`() {
        val packet1 = RTPPacketManager.RTPPacket(
            sequenceNumber = 100,
            timestamp = 1000L,
            ssrc = 12345L,
            payload = ByteArray(10)
        )
        val packet2 = RTPPacketManager.RTPPacket(
            sequenceNumber = 101,
            timestamp = 1000L,
            ssrc = 12345L,
            payload = ByteArray(10)
        )

        assertFalse(packet1 == packet2)
    }

    @Test
    fun `packets with different ssrc are not equal`() {
        val packet1 = RTPPacketManager.RTPPacket(
            sequenceNumber = 100,
            timestamp = 1000L,
            ssrc = 12345L,
            payload = ByteArray(10)
        )
        val packet2 = RTPPacketManager.RTPPacket(
            sequenceNumber = 100,
            timestamp = 1000L,
            ssrc = 54321L,
            payload = ByteArray(10)
        )

        assertFalse(packet1 == packet2)
    }

    // =========================================================================
    // Talkgroup ID Validation Tests
    // =========================================================================

    @Test
    fun `valid talkgroup IDs are 1-255`() {
        // Valid IDs
        for (id in listOf(1, 128, 255)) {
            val address = "${RTPPacketManager.MULTICAST_BASE}$id"
            assertTrue(address.matches(Regex("239\\.255\\.0\\.\\d+")))
        }
    }

    @Test
    fun `multicast address format is correct`() {
        val talkgroupId = 42
        val expectedAddress = "239.255.0.42"
        val actualAddress = "${RTPPacketManager.MULTICAST_BASE}$talkgroupId"
        assertEquals(expectedAddress, actualAddress)
    }

    // =========================================================================
    // RTPStats Tests
    // =========================================================================

    @Test
    fun `initial stats have zero values`() {
        val stats = rtpManager.getStats()

        assertTrue(stats.ssrc > 0)  // SSRC is randomly generated
        assertTrue(stats.activeGroups.isEmpty())
        assertEquals(0f, stats.averageLossRate)
    }

    @Test
    fun `stats ssrc is consistent`() {
        val stats1 = rtpManager.getStats()
        val stats2 = rtpManager.getStats()

        assertEquals(stats1.ssrc, stats2.ssrc)
    }
}
