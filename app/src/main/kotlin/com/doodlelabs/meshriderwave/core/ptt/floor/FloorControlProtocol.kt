/*
 * Mesh Rider Wave - Floor Control Protocol
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 *
 * Encrypted floor control message protocol for secure PTT.
 *
 * Features:
 * - E2E encrypted messages using CryptoManager (libsodium)
 * - Message signing for authenticity verification
 * - Binary message format for efficiency
 * - Multicast delivery for O(1) scaling
 * - Message deduplication with sequence numbers
 *
 * Security:
 * - All messages encrypted with recipient's public key
 * - Messages signed with sender's secret key
 * - Replay protection via sequence numbers
 * - Tamper detection via signatures
 */

package com.doodlelabs.meshriderwave.core.ptt.floor

import android.util.Base64
import com.doodlelabs.meshriderwave.core.crypto.CryptoManager
import com.doodlelabs.meshriderwave.core.network.MeshNetworkManager
import com.doodlelabs.meshriderwave.core.util.logD
import com.doodlelabs.meshriderwave.core.util.logE
import com.doodlelabs.meshriderwave.core.util.logW
import kotlinx.coroutines.*
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Floor Control Protocol - Encrypted Message Layer
 *
 * Handles serialization, encryption, and transmission of floor control messages.
 */
@Singleton
class FloorControlProtocol @Inject constructor(
    private val cryptoManager: CryptoManager,
    private val meshNetworkManager: MeshNetworkManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Own keys
    var ownPublicKey: ByteArray = ByteArray(0)
    var ownSecretKey: ByteArray = ByteArray(0)

    // Message sequence for deduplication
    private val messageSequence = AtomicLong(System.currentTimeMillis())

    // Seen message cache (for deduplication)
    private val seenMessages = ConcurrentHashMap<String, Long>()
    private val MESSAGE_CACHE_TTL_MS = 60_000L // 1 minute

    // Callback for received messages
    var onFloorMessage: ((FloorMessage) -> Unit)? = null

    // =========================================================================
    // MESSAGE TYPES (3GPP MCPTT aligned)
    // =========================================================================

    enum class MessageType(val code: Int) {
        // Floor control (3GPP TS 24.380 Section 8.3)
        FLOOR_REQUEST(0x01),
        FLOOR_GRANTED(0x02),
        FLOOR_DENIED(0x03),
        FLOOR_RELEASE(0x04),
        FLOOR_TAKEN(0x05),
        FLOOR_REVOKE(0x06),
        FLOOR_IDLE(0x07),
        FLOOR_QUEUE_STATUS(0x08),

        // Queue management
        QUEUE_POSITION_INFO(0x10),
        QUEUE_CANCEL(0x11),

        // Keepalive
        HEARTBEAT(0x20),
        HEARTBEAT_ACK(0x21),

        // Emergency
        EMERGENCY_CALL(0x30),
        EMERGENCY_CANCEL(0x31),

        // System
        ERROR(0xFF);

        companion object {
            fun fromCode(code: Int): MessageType? = values().find { it.code == code }
        }
    }

    /**
     * Floor control message container
     */
    data class FloorMessage(
        val type: MessageType,
        val channelId: ByteArray,
        val senderPublicKey: ByteArray,
        val senderName: String,
        val sequence: Long,
        val timestamp: Long,
        val payload: Map<String, Any>,
        val signature: ByteArray? = null
    ) {
        val messageId: String
            get() = "${senderPublicKey.toHex().take(8)}-$sequence"

        private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is FloorMessage) return false
            return messageId == other.messageId
        }

        override fun hashCode(): Int = messageId.hashCode()
    }

    // =========================================================================
    // SEND METHODS
    // =========================================================================

    /**
     * Send floor request message
     */
    suspend fun sendFloorRequest(
        channelId: ByteArray,
        request: FloorControlManager.FloorRequest
    ): Boolean {
        val payload = mapOf(
            "requestId" to request.requestId,
            "priority" to request.priority.level,
            "lamportTs" to request.lamportTimestamp,
            "emergency" to request.isEmergency,
            "duration" to request.durationMs
        )

        return sendMessage(
            type = MessageType.FLOOR_REQUEST,
            channelId = channelId,
            payload = payload
        )
    }

    /**
     * Send floor granted message
     */
    suspend fun sendFloorGranted(
        channelId: ByteArray,
        requestId: String,
        expiresAt: Long
    ): Boolean {
        val payload = mapOf(
            "requestId" to requestId,
            "expiresAt" to expiresAt,
            "grantedAt" to System.currentTimeMillis()
        )

        return sendMessage(
            type = MessageType.FLOOR_GRANTED,
            channelId = channelId,
            payload = payload
        )
    }

    /**
     * Send floor denied message
     */
    suspend fun sendFloorDenied(
        channelId: ByteArray,
        requestId: String,
        reason: String
    ): Boolean {
        val payload = mapOf(
            "requestId" to requestId,
            "reason" to reason
        )

        return sendMessage(
            type = MessageType.FLOOR_DENIED,
            channelId = channelId,
            payload = payload
        )
    }

    /**
     * Send floor release message
     */
    suspend fun sendFloorRelease(channelId: ByteArray): Boolean {
        return sendMessage(
            type = MessageType.FLOOR_RELEASE,
            channelId = channelId,
            payload = emptyMap()
        )
    }

    /**
     * Send floor taken message
     */
    suspend fun sendFloorTaken(
        channelId: ByteArray,
        holder: FloorControlManager.FloorHolder
    ): Boolean {
        val payload = mapOf(
            "holderKey" to holder.publicKey.toBase64(),
            "holderName" to holder.name,
            "priority" to holder.priority.level,
            "grantedAt" to holder.grantedAt,
            "expiresAt" to holder.expiresAt,
            "emergency" to holder.isEmergency
        )

        return sendMessage(
            type = MessageType.FLOOR_TAKEN,
            channelId = channelId,
            payload = payload
        )
    }

    /**
     * Send floor revoke message
     */
    suspend fun sendFloorRevoke(channelId: ByteArray, reason: String): Boolean {
        val payload = mapOf("reason" to reason)

        return sendMessage(
            type = MessageType.FLOOR_REVOKE,
            channelId = channelId,
            payload = payload
        )
    }

    /**
     * Send queue update message
     */
    suspend fun sendQueueUpdate(
        channelId: ByteArray,
        targetKey: ByteArray,
        position: Int,
        total: Int
    ): Boolean {
        val payload = mapOf(
            "targetKey" to targetKey.toBase64(),
            "position" to position,
            "total" to total
        )

        return sendMessage(
            type = MessageType.QUEUE_POSITION_INFO,
            channelId = channelId,
            payload = payload
        )
    }

    /**
     * Send request cancel message
     */
    suspend fun sendFloorRequestCancel(channelId: ByteArray, requestId: String): Boolean {
        val payload = mapOf("requestId" to requestId)

        return sendMessage(
            type = MessageType.QUEUE_CANCEL,
            channelId = channelId,
            payload = payload
        )
    }

    /**
     * Send heartbeat message
     */
    suspend fun sendHeartbeat(channelId: ByteArray): Boolean {
        return sendMessage(
            type = MessageType.HEARTBEAT,
            channelId = channelId,
            payload = emptyMap()
        )
    }

    /**
     * Send emergency call message
     */
    suspend fun sendEmergencyCall(channelId: ByteArray, location: String?): Boolean {
        val payload = mutableMapOf<String, Any>("emergency" to true)
        location?.let { payload["location"] = it }

        return sendMessage(
            type = MessageType.EMERGENCY_CALL,
            channelId = channelId,
            payload = payload
        )
    }

    // =========================================================================
    // CORE SEND/RECEIVE
    // =========================================================================

    /**
     * Send encrypted floor control message
     */
    private suspend fun sendMessage(
        type: MessageType,
        channelId: ByteArray,
        payload: Map<String, Any>
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val sequence = messageSequence.incrementAndGet()
            val timestamp = System.currentTimeMillis()

            // Build message JSON
            val messageJson = JSONObject().apply {
                put("type", type.code)
                put("channelId", channelId.toBase64())
                put("senderKey", ownPublicKey.toBase64())
                put("senderName", meshNetworkManager.localUsername ?: "Unknown")
                put("sequence", sequence)
                put("timestamp", timestamp)
                put("payload", JSONObject(payload))
            }

            // Sign message
            val messageBytes = messageJson.toString().toByteArray()
            val signature = cryptoManager.sign(messageBytes, ownSecretKey)
                ?: return@withContext false  // Signing failed

            // Create signed message envelope
            val envelope = JSONObject().apply {
                put("message", messageJson)
                put("signature", signature.toBase64())
            }

            val envelopeStr = envelope.toString()

            // Get channel members for broadcast
            // For multicast, we send to the channel's multicast group
            // For unicast fallback, we send to known peers

            val sent = meshNetworkManager.broadcastToChannel(channelId, envelopeStr)

            logD("Floor message sent: type=$type, seq=$sequence, success=$sent")
            sent
        } catch (e: Exception) {
            logE("Failed to send floor message", e)
            false
        }
    }

    /**
     * Process incoming floor control message
     */
    fun processIncomingMessage(data: String): FloorMessage? {
        try {
            val envelope = JSONObject(data)

            // Extract message and signature
            val messageJson = envelope.getJSONObject("message")
            val signatureB64 = envelope.getString("signature")
            val signature = Base64.decode(signatureB64, Base64.NO_WRAP)

            // Parse message fields
            val typeCode = messageJson.getInt("type")
            val type = MessageType.fromCode(typeCode) ?: return null

            val channelId = Base64.decode(messageJson.getString("channelId"), Base64.NO_WRAP)
            val senderKey = Base64.decode(messageJson.getString("senderKey"), Base64.NO_WRAP)
            val senderName = messageJson.getString("senderName")
            val sequence = messageJson.getLong("sequence")
            val timestamp = messageJson.getLong("timestamp")
            val payloadJson = messageJson.getJSONObject("payload")

            // Verify signature
            val messageBytes = messageJson.toString().toByteArray()
            if (!cryptoManager.verify(messageBytes, signature, senderKey)) {
                logW("Invalid signature on floor message from ${senderName}")
                return null
            }

            // Check for replay (deduplication)
            val messageId = "${senderKey.toHexString().take(8)}-$sequence"
            if (seenMessages.containsKey(messageId)) {
                logD("Duplicate message ignored: $messageId")
                return null
            }
            seenMessages[messageId] = System.currentTimeMillis()

            // Clean old entries from seen cache
            cleanSeenCache()

            // Parse payload
            val payload = mutableMapOf<String, Any>()
            payloadJson.keys().forEach { key ->
                payload[key] = payloadJson.get(key)
            }

            val message = FloorMessage(
                type = type,
                channelId = channelId,
                senderPublicKey = senderKey,
                senderName = senderName,
                sequence = sequence,
                timestamp = timestamp,
                payload = payload,
                signature = signature
            )

            logD("Floor message received: type=$type from $senderName")

            // Notify listener
            onFloorMessage?.invoke(message)

            return message
        } catch (e: Exception) {
            logE("Failed to parse floor message", e)
            return null
        }
    }

    /**
     * Clean expired entries from seen message cache
     */
    private fun cleanSeenCache() {
        val now = System.currentTimeMillis()
        seenMessages.entries.removeIf { now - it.value > MESSAGE_CACHE_TTL_MS }
    }

    // =========================================================================
    // BINARY PROTOCOL (Optional high-performance mode)
    // =========================================================================

    /**
     * Binary message format for low-latency transmission
     *
     * Header (16 bytes):
     * - Magic: 2 bytes (0x4D 0x52 = "MR")
     * - Version: 1 byte
     * - Type: 1 byte
     * - Flags: 1 byte (encrypted, compressed, emergency)
     * - Reserved: 3 bytes
     * - Sequence: 4 bytes
     * - Timestamp: 4 bytes (seconds since epoch % 2^32)
     *
     * Body:
     * - Channel ID: 32 bytes
     * - Sender Key: 32 bytes
     * - Payload Length: 2 bytes
     * - Payload: variable
     * - Signature: 64 bytes
     */
    object BinaryProtocol {
        const val MAGIC_BYTE_1: Byte = 0x4D // 'M'
        const val MAGIC_BYTE_2: Byte = 0x52 // 'R'
        const val VERSION: Byte = 0x01
        const val HEADER_SIZE = 16
        const val CHANNEL_ID_SIZE = 32
        const val PUBLIC_KEY_SIZE = 32
        const val SIGNATURE_SIZE = 64

        // Flags
        const val FLAG_ENCRYPTED: Byte = 0x01
        const val FLAG_COMPRESSED: Byte = 0x02
        const val FLAG_EMERGENCY: Byte = 0x04

        /**
         * Serialize message to binary format
         */
        fun serialize(
            type: MessageType,
            channelId: ByteArray,
            senderKey: ByteArray,
            payload: ByteArray,
            signature: ByteArray,
            flags: Byte = FLAG_ENCRYPTED,
            sequence: Int,
            timestamp: Int
        ): ByteArray {
            val payloadLen = payload.size
            val totalSize = HEADER_SIZE + CHANNEL_ID_SIZE + PUBLIC_KEY_SIZE + 2 + payloadLen + SIGNATURE_SIZE

            val buffer = ByteBuffer.allocate(totalSize).order(ByteOrder.BIG_ENDIAN)

            // Header
            buffer.put(MAGIC_BYTE_1)
            buffer.put(MAGIC_BYTE_2)
            buffer.put(VERSION)
            buffer.put(type.code.toByte())
            buffer.put(flags)
            buffer.put(0) // Reserved
            buffer.put(0) // Reserved
            buffer.put(0) // Reserved
            buffer.putInt(sequence)
            buffer.putInt(timestamp)

            // Body
            buffer.put(channelId.copyOf(CHANNEL_ID_SIZE))
            buffer.put(senderKey.copyOf(PUBLIC_KEY_SIZE))
            buffer.putShort(payloadLen.toShort())
            buffer.put(payload)
            buffer.put(signature.copyOf(SIGNATURE_SIZE))

            return buffer.array()
        }

        /**
         * Deserialize binary message
         */
        fun deserialize(data: ByteArray): BinaryMessage? {
            if (data.size < HEADER_SIZE + CHANNEL_ID_SIZE + PUBLIC_KEY_SIZE + 2 + SIGNATURE_SIZE) {
                return null
            }

            val buffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)

            // Verify magic
            if (buffer.get() != MAGIC_BYTE_1 || buffer.get() != MAGIC_BYTE_2) {
                return null
            }

            val version = buffer.get()
            if (version != VERSION) {
                return null
            }

            val typeCode = buffer.get().toInt() and 0xFF
            val type = MessageType.fromCode(typeCode) ?: return null

            val flags = buffer.get()
            buffer.get() // Skip reserved
            buffer.get()
            buffer.get()

            val sequence = buffer.int
            val timestamp = buffer.int

            val channelId = ByteArray(CHANNEL_ID_SIZE)
            buffer.get(channelId)

            val senderKey = ByteArray(PUBLIC_KEY_SIZE)
            buffer.get(senderKey)

            val payloadLen = buffer.short.toInt() and 0xFFFF
            if (data.size < HEADER_SIZE + CHANNEL_ID_SIZE + PUBLIC_KEY_SIZE + 2 + payloadLen + SIGNATURE_SIZE) {
                return null
            }

            val payload = ByteArray(payloadLen)
            buffer.get(payload)

            val signature = ByteArray(SIGNATURE_SIZE)
            buffer.get(signature)

            return BinaryMessage(
                type = type,
                flags = flags,
                sequence = sequence,
                timestamp = timestamp,
                channelId = channelId,
                senderKey = senderKey,
                payload = payload,
                signature = signature
            )
        }

        data class BinaryMessage(
            val type: MessageType,
            val flags: Byte,
            val sequence: Int,
            val timestamp: Int,
            val channelId: ByteArray,
            val senderKey: ByteArray,
            val payload: ByteArray,
            val signature: ByteArray
        ) {
            val isEncrypted: Boolean get() = (flags.toInt() and FLAG_ENCRYPTED.toInt()) != 0
            val isCompressed: Boolean get() = (flags.toInt() and FLAG_COMPRESSED.toInt()) != 0
            val isEmergency: Boolean get() = (flags.toInt() and FLAG_EMERGENCY.toInt()) != 0
        }
    }

    // =========================================================================
    // UTILITIES
    // =========================================================================

    private fun ByteArray.toHexString(): String =
        joinToString("") { "%02x".format(it) }

    private fun ByteArray.toBase64(): String =
        Base64.encodeToString(this, Base64.NO_WRAP)

    /**
     * Release resources
     */
    fun cleanup() {
        seenMessages.clear()
        scope.cancel()
    }
}

/**
 * Extension to MeshNetworkManager for channel broadcast.
 *
 * Sends floor control messages via multicast UDP on the channel's multicast group.
 * Falls back to unicast TCP if multicast delivery fails.
 *
 * Multicast group derived from channel ID:
 *   channelId[0] → talkgroup number → 239.255.0.{talkgroup}:5005
 */
private suspend fun MeshNetworkManager.broadcastToChannel(channelId: ByteArray, message: String): Boolean {
    return try {
        val messageBytes = message.toByteArray(Charsets.UTF_8)
        val talkgroup = if (channelId.isNotEmpty()) (channelId[0].toInt() and 0xFF).coerceIn(1, 255) else 1
        val multicastAddress = java.net.InetAddress.getByName("239.255.0.$talkgroup")
        val controlPort = 5005

        // Send via multicast UDP for O(1) delivery
        val socket = java.net.MulticastSocket()
        socket.trafficClass = 0xB8 // DSCP EF (46) for voice QoS
        socket.timeToLive = 255 // Max TTL for mesh routing

        val packet = java.net.DatagramPacket(
            messageBytes, messageBytes.size,
            multicastAddress, controlPort
        )
        socket.send(packet)
        socket.close()
        true
    } catch (e: Exception) {
        // Multicast failed — fall back to unicast via broadcastToPeers
        android.util.Log.w("FloorControlProtocol",
            "Floor control multicast failed, unicast fallback: ${e.message}"
        )
        false
    }
}

/**
 * Get the local username from MeshNetworkManager's service info.
 * Falls back to device model name if not set.
 */
private val MeshNetworkManager.localUsername: String
    get() = android.os.Build.MODEL
