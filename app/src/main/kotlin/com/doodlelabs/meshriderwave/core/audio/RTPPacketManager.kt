/*
 * Mesh Rider Wave - RTP Packet Manager
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 *
 * Real-time Transport Protocol (RFC 3550) implementation
 * for PTT voice streaming over multicast.
 *
 * Features:
 * - RTP header formatting per RFC 3550
 * - Opus payload type support (RFC 7587)
 * - Sequence numbering for loss detection
 * - Timestamp for jitter buffer synchronization
 * - SSRC for source identification
 * - DSCP marking for QoS (RFC 2474)
 */

package com.doodlelabs.meshriderwave.core.audio

import com.doodlelabs.meshriderwave.core.util.logD
import com.doodlelabs.meshriderwave.core.util.logE
import com.doodlelabs.meshriderwave.core.util.logW
import java.net.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * RTP packet manager for voice over multicast
 *
 * Implements RFC 3550 (RTP) and RFC 7587 (Opus RTP Payload)
 */
@Singleton
class RTPPacketManager @Inject constructor() {

    companion object {
        // RTP Header Constants
        const val RTP_VERSION = 2
        const val RTP_HEADER_SIZE = 12

        // Payload Types (RFC 7587 - Opus uses dynamic payload type)
        const val PAYLOAD_TYPE_OPUS = 111  // Dynamic PT for Opus (96-127)
        const val PAYLOAD_TYPE_PCMU = 0    // G.711 μ-law (fallback)
        const val PAYLOAD_TYPE_PCMA = 8    // G.711 A-law (fallback)

        // Clock rates
        const val CLOCK_RATE_OPUS = 48000  // Opus always uses 48kHz clock
        const val CLOCK_RATE_PCM = 8000

        // Multicast Groups (239.255.0.0/16 - Organization-Local Scope)
        const val MULTICAST_BASE = "239.255.0."
        const val MULTICAST_PORT = 5004

        // DSCP (Differentiated Services Code Point) for QoS
        const val DSCP_EF = 46         // Expedited Forwarding (voice)
        const val DSCP_AF41 = 34       // Assured Forwarding 41 (video)
        const val DSCP_DEFAULT = 0     // Best effort

        // TOS byte = DSCP << 2
        const val TOS_EF = DSCP_EF shl 2      // 0xB8 = 184
        const val TOS_AF41 = DSCP_AF41 shl 2  // 0x88 = 136

        // Jitter buffer settings
        const val JITTER_BUFFER_MIN_MS = 20
        const val JITTER_BUFFER_MAX_MS = 100
        const val JITTER_BUFFER_TARGET_MS = 40
    }

    // SSRC (Synchronization Source) - unique per sender
    private val ssrc: Long = SecureRandom().nextLong() and 0xFFFFFFFFL

    // Sequence number (16-bit, wraps)
    private val sequenceNumber = AtomicInteger(SecureRandom().nextInt(65536))

    // Timestamp (32-bit, wraps)
    private var rtpTimestamp = SecureRandom().nextLong() and 0xFFFFFFFFL

    // UDP sockets for send/receive
    private var sendSocket: MulticastSocket? = null
    private var receiveSocket: MulticastSocket? = null

    // Active multicast groups
    private val activeGroups = ConcurrentHashMap<String, InetAddress>()

    // Jitter buffers per SSRC (one per sender)
    private val jitterBuffers = ConcurrentHashMap<Long, JitterBuffer>()

    // Network interface for multicast
    private var networkInterface: NetworkInterface? = null

    /**
     * RTP Packet structure
     */
    data class RTPPacket(
        val version: Int = RTP_VERSION,
        val padding: Boolean = false,
        val extension: Boolean = false,
        val csrcCount: Int = 0,
        val marker: Boolean = false,       // Start of talk spurt
        val payloadType: Int = PAYLOAD_TYPE_OPUS,
        val sequenceNumber: Int,
        val timestamp: Long,
        val ssrc: Long,
        val payload: ByteArray
    ) {
        /**
         * Serialize to bytes for transmission
         */
        fun toBytes(): ByteArray {
            val buffer = ByteBuffer.allocate(RTP_HEADER_SIZE + payload.size)
                .order(ByteOrder.BIG_ENDIAN)

            // Byte 0: V=2, P, X, CC
            val byte0 = (version shl 6) or
                    ((if (padding) 1 else 0) shl 5) or
                    ((if (extension) 1 else 0) shl 4) or
                    (csrcCount and 0x0F)
            buffer.put(byte0.toByte())

            // Byte 1: M, PT
            val byte1 = ((if (marker) 1 else 0) shl 7) or (payloadType and 0x7F)
            buffer.put(byte1.toByte())

            // Bytes 2-3: Sequence number
            buffer.putShort(sequenceNumber.toShort())

            // Bytes 4-7: Timestamp
            buffer.putInt(timestamp.toInt())

            // Bytes 8-11: SSRC
            buffer.putInt(ssrc.toInt())

            // Payload
            buffer.put(payload)

            return buffer.array()
        }

        companion object {
            /**
             * Parse RTP packet from bytes
             */
            fun fromBytes(data: ByteArray): RTPPacket? {
                if (data.size < RTP_HEADER_SIZE) {
                    return null
                }

                val buffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)

                val byte0 = buffer.get().toInt() and 0xFF
                val version = (byte0 shr 6) and 0x03
                if (version != RTP_VERSION) {
                    return null
                }

                val padding = ((byte0 shr 5) and 0x01) == 1
                val extension = ((byte0 shr 4) and 0x01) == 1
                val csrcCount = byte0 and 0x0F

                val byte1 = buffer.get().toInt() and 0xFF
                val marker = ((byte1 shr 7) and 0x01) == 1
                val payloadType = byte1 and 0x7F

                val sequenceNumber = buffer.short.toInt() and 0xFFFF
                val timestamp = buffer.int.toLong() and 0xFFFFFFFFL
                val ssrc = buffer.int.toLong() and 0xFFFFFFFFL

                // Skip CSRC list
                val csrcBytes = csrcCount * 4
                if (data.size < RTP_HEADER_SIZE + csrcBytes) {
                    return null
                }
                buffer.position(buffer.position() + csrcBytes)

                // Extract payload
                val payloadOffset = RTP_HEADER_SIZE + csrcBytes
                val payload = data.copyOfRange(payloadOffset, data.size)

                return RTPPacket(
                    version = version,
                    padding = padding,
                    extension = extension,
                    csrcCount = csrcCount,
                    marker = marker,
                    payloadType = payloadType,
                    sequenceNumber = sequenceNumber,
                    timestamp = timestamp,
                    ssrc = ssrc,
                    payload = payload
                )
            }
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as RTPPacket
            return sequenceNumber == other.sequenceNumber && ssrc == other.ssrc
        }

        override fun hashCode(): Int {
            var result = sequenceNumber
            result = 31 * result + ssrc.hashCode()
            return result
        }
    }

    /**
     * Simple jitter buffer for reordering packets
     */
    inner class JitterBuffer(
        private val bufferSizeMs: Int = JITTER_BUFFER_TARGET_MS
    ) {
        private val buffer = sortedMapOf<Int, RTPPacket>()
        private var lastPlayedSeq = -1
        private var packetsReceived = 0L
        private var packetsLost = 0L

        fun put(packet: RTPPacket) {
            synchronized(buffer) {
                buffer[packet.sequenceNumber] = packet
                packetsReceived++

                // Remove old packets
                while (buffer.size > 20) {
                    buffer.remove(buffer.firstKey())
                }
            }
        }

        fun poll(): RTPPacket? {
            synchronized(buffer) {
                if (buffer.isEmpty()) return null

                val nextSeq = if (lastPlayedSeq < 0) {
                    buffer.firstKey()
                } else {
                    (lastPlayedSeq + 1) and 0xFFFF
                }

                val packet = buffer.remove(nextSeq)
                if (packet != null) {
                    lastPlayedSeq = nextSeq
                } else if (buffer.isNotEmpty() && buffer.size > 5) {
                    // Skip lost packet, take next available
                    val available = buffer.firstKey()
                    val lost = ((available - lastPlayedSeq - 1) and 0xFFFF)
                    packetsLost += lost
                    lastPlayedSeq = available
                    return buffer.remove(available)
                }

                return packet
            }
        }

        fun getLossRate(): Float {
            return if (packetsReceived > 0) {
                packetsLost.toFloat() / (packetsReceived + packetsLost)
            } else 0f
        }
    }

    /**
     * Initialize the RTP manager
     *
     * @param interfaceName Network interface (e.g., "wlan0", "eth0")
     */
    fun initialize(interfaceName: String? = null): Boolean {
        return try {
            // Find network interface
            networkInterface = if (interfaceName != null) {
                NetworkInterface.getByName(interfaceName)
            } else {
                findBestInterface()
            }

            if (networkInterface == null) {
                logE("No suitable network interface found")
                return false
            }

            logD("Using network interface: ${networkInterface?.name}")

            // Create send socket
            sendSocket = MulticastSocket().apply {
                reuseAddress = true
                networkInterface?.let { setNetworkInterface(it) }
                timeToLive = 32  // Mesh-local scope
                trafficClass = TOS_EF  // DSCP EF for voice QoS
            }

            // Create receive socket
            receiveSocket = MulticastSocket(MULTICAST_PORT).apply {
                reuseAddress = true
                networkInterface?.let { setNetworkInterface(it) }
                soTimeout = 100  // 100ms timeout for polling
            }

            logD("RTP manager initialized: SSRC=0x${ssrc.toString(16)}, DSCP=EF")
            true
        } catch (e: Exception) {
            logE("Failed to initialize RTP manager", e)
            false
        }
    }

    /**
     * Join a multicast group for a talkgroup
     */
    fun joinGroup(talkgroupId: Int): Boolean {
        if (talkgroupId !in 1..255) {
            logE("Invalid talkgroup ID: $talkgroupId (must be 1-255)")
            return false
        }

        val address = "$MULTICAST_BASE$talkgroupId"
        return joinGroup(address)
    }

    /**
     * Join a multicast group by address
     */
    fun joinGroup(address: String): Boolean {
        return try {
            val group = InetAddress.getByName(address)
            if (!group.isMulticastAddress) {
                logE("Not a multicast address: $address")
                return false
            }

            receiveSocket?.let { socket ->
                val socketAddress = InetSocketAddress(group, MULTICAST_PORT)
                if (networkInterface != null) {
                    socket.joinGroup(socketAddress, networkInterface)
                } else {
                    socket.joinGroup(group)
                }
            }

            activeGroups[address] = group
            logD("Joined multicast group: $address")
            true
        } catch (e: Exception) {
            logE("Failed to join multicast group: $address", e)
            false
        }
    }

    /**
     * Leave a multicast group
     */
    fun leaveGroup(address: String) {
        try {
            val group = activeGroups.remove(address) ?: return

            receiveSocket?.let { socket ->
                val socketAddress = InetSocketAddress(group, MULTICAST_PORT)
                if (networkInterface != null) {
                    socket.leaveGroup(socketAddress, networkInterface)
                } else {
                    socket.leaveGroup(group)
                }
            }

            logD("Left multicast group: $address")
        } catch (e: Exception) {
            logE("Error leaving multicast group: $address", e)
        }
    }

    /**
     * Send audio packet to a multicast group
     *
     * @param talkgroupId Talkgroup number (1-255)
     * @param opusData Opus-encoded audio frame
     * @param marker Set to true for first packet of transmission
     * @param frameSize Frame size in samples (for timestamp calculation)
     */
    fun sendAudio(
        talkgroupId: Int,
        opusData: ByteArray,
        marker: Boolean = false,
        frameSize: Int = 320  // 20ms at 16kHz
    ): Boolean {
        val address = "$MULTICAST_BASE$talkgroupId"
        return sendAudio(address, opusData, marker, frameSize)
    }

    /**
     * Send audio packet to a multicast address
     */
    fun sendAudio(
        address: String,
        opusData: ByteArray,
        marker: Boolean = false,
        frameSize: Int = 320
    ): Boolean {
        return try {
            val group = activeGroups[address] ?: InetAddress.getByName(address)

            val packet = RTPPacket(
                marker = marker,
                payloadType = PAYLOAD_TYPE_OPUS,
                sequenceNumber = sequenceNumber.getAndIncrement() and 0xFFFF,
                timestamp = rtpTimestamp,
                ssrc = ssrc,
                payload = opusData
            )

            // Advance timestamp by frame duration
            // Opus RTP uses 48kHz clock regardless of actual sample rate
            val timestampIncrement = (frameSize.toLong() * CLOCK_RATE_OPUS) / 16000
            rtpTimestamp = (rtpTimestamp + timestampIncrement) and 0xFFFFFFFFL

            val data = packet.toBytes()
            val datagramPacket = DatagramPacket(
                data, data.size,
                group, MULTICAST_PORT
            )

            sendSocket?.send(datagramPacket)
            true
        } catch (e: Exception) {
            logE("Failed to send RTP packet", e)
            false
        }
    }

    /**
     * Receive audio packets (call in a loop from a receiver thread)
     *
     * @return Received RTP packet or null on timeout/error
     */
    fun receivePacket(): RTPPacket? {
        return try {
            val buffer = ByteArray(2048)  // Max RTP packet size
            val datagramPacket = DatagramPacket(buffer, buffer.size)

            receiveSocket?.receive(datagramPacket)

            val data = buffer.copyOf(datagramPacket.length)
            val rtpPacket = RTPPacket.fromBytes(data)

            if (rtpPacket != null) {
                // Add to appropriate jitter buffer
                val jitterBuffer = jitterBuffers.getOrPut(rtpPacket.ssrc) {
                    JitterBuffer()
                }
                jitterBuffer.put(rtpPacket)
            }

            rtpPacket
        } catch (e: SocketTimeoutException) {
            null  // Normal timeout
        } catch (e: Exception) {
            logE("Error receiving RTP packet", e)
            null
        }
    }

    /**
     * Get next packet from jitter buffer for a specific sender
     */
    fun getBufferedPacket(ssrc: Long): RTPPacket? {
        return jitterBuffers[ssrc]?.poll()
    }

    /**
     * Set DSCP marking for QoS
     *
     * @param dscp DSCP value (0-63)
     */
    fun setDSCP(dscp: Int) {
        val tos = (dscp and 0x3F) shl 2
        try {
            sendSocket?.trafficClass = tos
            logD("Set DSCP to $dscp (TOS=0x${tos.toString(16)})")
        } catch (e: Exception) {
            logW("Failed to set DSCP: ${e.message}")
        }
    }

    /**
     * Release resources
     */
    fun release() {
        try {
            // Leave all groups
            activeGroups.keys.forEach { leaveGroup(it) }
            activeGroups.clear()

            // Close sockets
            sendSocket?.close()
            receiveSocket?.close()
            sendSocket = null
            receiveSocket = null

            // Clear jitter buffers
            jitterBuffers.clear()

            logD("RTP manager released")
        } catch (e: Exception) {
            logE("Error releasing RTP manager", e)
        }
    }

    /**
     * Get statistics
     */
    fun getStats(): RTPStats {
        var totalLoss = 0f
        var bufferCount = 0

        jitterBuffers.values.forEach {
            totalLoss += it.getLossRate()
            bufferCount++
        }

        return RTPStats(
            ssrc = ssrc,
            sequenceNumber = sequenceNumber.get(),
            timestamp = rtpTimestamp,
            activeGroups = activeGroups.keys.toList(),
            averageLossRate = if (bufferCount > 0) totalLoss / bufferCount else 0f
        )
    }

    /**
     * Find best network interface for multicast
     */
    private fun findBestInterface(): NetworkInterface? {
        val interfaces = NetworkInterface.getNetworkInterfaces()?.toList() ?: return null

        // Prefer: wlan > eth > any with multicast support
        val preferred = listOf("wlan", "eth", "en", "wifi")

        for (prefix in preferred) {
            val iface = interfaces.find {
                it.name.startsWith(prefix) &&
                        it.isUp &&
                        it.supportsMulticast() &&
                        !it.isLoopback
            }
            if (iface != null) return iface
        }

        // Fallback to any multicast-capable interface
        return interfaces.find {
            it.isUp && it.supportsMulticast() && !it.isLoopback
        }
    }

    /**
     * RTP statistics
     */
    data class RTPStats(
        val ssrc: Long,
        val sequenceNumber: Int,
        val timestamp: Long,
        val activeGroups: List<String>,
        val averageLossRate: Float
    )
}
