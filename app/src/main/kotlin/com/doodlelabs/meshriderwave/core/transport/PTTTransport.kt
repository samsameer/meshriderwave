/*
 * Mesh Rider Wave - PTT Transport Abstraction
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 *
 * SOLID Principles:
 * - Interface Segregation: Separate interfaces for send/receive
 * - Dependency Inversion: Depend on abstractions, not concretions
 * - Open/Closed: Open for extension (new transports) without modification
 */

package com.doodlelabs.meshriderwave.core.transport

import kotlinx.coroutines.flow.Flow

/**
 * Abstraction for PTT audio transport
 * Follows Interface Segregation Principle - separate send/receive capabilities
 */
interface PTTTransport {
    /** Start the transport */
    suspend fun start()

    /** Stop the transport */
    suspend fun stop()

    /** Check if transport is active */
    val isActive: Boolean
}

/**
 * Interface for sending PTT audio
 */
interface PTTSender : PTTTransport {
    /**
     * Send encoded audio packet to channel
     * @param channelId Target PTT channel
     * @param audioData Encoded audio data (Opus)
     * @param sequenceNumber RTP sequence number
     * @param timestamp RTP timestamp
     */
    suspend fun sendAudio(
        channelId: String,
        audioData: ByteArray,
        sequenceNumber: Int,
        timestamp: Long
    ): Result<Unit>

    /**
     * Send floor control message
     */
    suspend fun sendFloorControl(
        channelId: String,
        message: ByteArray
    ): Result<Unit>
}

/**
 * Interface for receiving PTT audio
 */
interface PTTReceiver : PTTTransport {
    /**
     * Flow of incoming audio packets
     */
    val incomingAudio: Flow<PTTAudioPacket>

    /**
     * Flow of incoming floor control messages
     */
    val incomingFloorControl: Flow<PTTFloorControlPacket>
}

/**
 * Full-duplex transport combining send/receive
 */
interface PTTFullDuplexTransport : PTTSender, PTTReceiver

/**
 * Audio packet received from network
 */
data class PTTAudioPacket(
    val channelId: String,
    val senderPublicKey: ByteArray,
    val audioData: ByteArray,
    val sequenceNumber: Int,
    val timestamp: Long,
    val receivedAt: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as PTTAudioPacket
        return channelId == other.channelId &&
                senderPublicKey.contentEquals(other.senderPublicKey) &&
                sequenceNumber == other.sequenceNumber
    }

    override fun hashCode(): Int {
        var result = channelId.hashCode()
        result = 31 * result + senderPublicKey.contentHashCode()
        result = 31 * result + sequenceNumber
        return result
    }
}

/**
 * Floor control packet received from network
 */
data class PTTFloorControlPacket(
    val channelId: String,
    val senderPublicKey: ByteArray,
    val messageType: FloorMessageType,
    val payload: ByteArray,
    val receivedAt: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as PTTFloorControlPacket
        return channelId == other.channelId &&
                senderPublicKey.contentEquals(other.senderPublicKey) &&
                messageType == other.messageType
    }

    override fun hashCode(): Int {
        var result = channelId.hashCode()
        result = 31 * result + senderPublicKey.contentHashCode()
        result = 31 * result + messageType.hashCode()
        return result
    }
}

/**
 * Floor control message types (3GPP MCPTT compliant)
 */
enum class FloorMessageType {
    FLOOR_REQUEST,
    FLOOR_GRANTED,
    FLOOR_DENIED,
    FLOOR_RELEASE,
    FLOOR_TAKEN,
    FLOOR_IDLE,
    FLOOR_REVOKED,
    FLOOR_ACK
}

/**
 * Transport configuration
 */
data class TransportConfig(
    val mode: TransportMode = TransportMode.MULTICAST,
    val multicastGroup: String = "239.255.0.1",
    val audioPort: Int = 5004,
    val controlPort: Int = 5005,
    val dscp: Int = 46 // Expedited Forwarding for voice
)

/**
 * Transport modes available
 */
enum class TransportMode {
    /** Multicast RTP - Efficient for mesh networks */
    MULTICAST,
    /** Unicast TCP - Fallback for networks without multicast */
    UNICAST,
    /** WebRTC Data Channels - Browser compatible */
    WEBRTC
}
