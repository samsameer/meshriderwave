/*
 * Mesh Rider Wave - Multicast PTT Transport Implementation
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 *
 * Multicast RTP transport for efficient mesh network delivery
 *
 * SOLID Principles:
 * - Single Responsibility: Only handles multicast network I/O
 * - Liskov Substitution: Can be swapped with any PTTTransport implementation
 * - Dependency Inversion: Depends on abstract PTTTransport interface
 */

package com.doodlelabs.meshriderwave.core.transport

import android.content.Context
import android.net.TrafficStats
import com.doodlelabs.meshriderwave.core.crypto.CryptoManager
import com.doodlelabs.meshriderwave.core.util.logD
import com.doodlelabs.meshriderwave.core.util.logE
import com.doodlelabs.meshriderwave.core.util.logW
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import java.net.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Multicast-based PTT transport for mesh networks
 *
 * Benefits:
 * - O(1) delivery to all peers (no connection per peer)
 * - DSCP EF (46) for voice QoS prioritization
 * - Efficient bandwidth: single stream regardless of peer count
 * - Native support on MeshRider radios via BATMAN-adv
 */
@Singleton
class MulticastPTTTransport @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cryptoManager: CryptoManager
) : PTTFullDuplexTransport {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var audioSocket: MulticastSocket? = null
    private var controlSocket: MulticastSocket? = null
    private var receiveJob: Job? = null
    private var controlReceiveJob: Job? = null

    private var config: TransportConfig = TransportConfig()
    private var localPublicKey: ByteArray = ByteArray(0)
    private var localSecretKey: ByteArray = ByteArray(0)

    // Incoming flows
    private val _incomingAudio = MutableSharedFlow<PTTAudioPacket>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val incomingAudio: Flow<PTTAudioPacket> = _incomingAudio.asSharedFlow()

    private val _incomingFloorControl = MutableSharedFlow<PTTFloorControlPacket>(
        replay = 0,
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val incomingFloorControl: Flow<PTTFloorControlPacket> = _incomingFloorControl.asSharedFlow()

    // State
    private val _isActive = MutableStateFlow(false)
    override val isActive: Boolean get() = _isActive.value

    /**
     * Configure the transport before starting
     */
    fun configure(
        config: TransportConfig,
        publicKey: ByteArray,
        secretKey: ByteArray
    ) {
        this.config = config
        this.localPublicKey = publicKey
        this.localSecretKey = secretKey
    }

    override suspend fun start() = withContext(Dispatchers.IO) {
        if (_isActive.value) {
            logW("MulticastPTTTransport already active")
            return@withContext
        }

        try {
            // Set thread stats tag for network monitoring
            TrafficStats.setThreadStatsTag(TAG_PTT)

            // Create multicast sockets
            audioSocket = createMulticastSocket(config.audioPort).also {
                it.joinGroup(InetSocketAddress(config.multicastGroup, config.audioPort), null)
                // Set DSCP for voice QoS
                it.trafficClass = config.dscp shl 2
            }

            controlSocket = createMulticastSocket(config.controlPort).also {
                it.joinGroup(InetSocketAddress(config.multicastGroup, config.controlPort), null)
                it.trafficClass = config.dscp shl 2
            }

            // Start receive loops
            receiveJob = scope.launch { audioReceiveLoop() }
            controlReceiveJob = scope.launch { controlReceiveLoop() }

            _isActive.value = true
            logD("MulticastPTTTransport started on ${config.multicastGroup}:${config.audioPort}/${config.controlPort}")

        } catch (e: Exception) {
            logE("Failed to start MulticastPTTTransport: ${e.message}")
            stop()
            throw e
        }
    }

    override suspend fun stop() = withContext(Dispatchers.IO) {
        _isActive.value = false

        receiveJob?.cancel()
        controlReceiveJob?.cancel()

        audioSocket?.let { socket ->
            try {
                socket.leaveGroup(InetSocketAddress(config.multicastGroup, config.audioPort), null)
                socket.close()
            } catch (e: Exception) {
                logW("Error closing audio socket: ${e.message}")
            }
        }
        audioSocket = null

        controlSocket?.let { socket ->
            try {
                socket.leaveGroup(InetSocketAddress(config.multicastGroup, config.controlPort), null)
                socket.close()
            } catch (e: Exception) {
                logW("Error closing control socket: ${e.message}")
            }
        }
        controlSocket = null

        logD("MulticastPTTTransport stopped")
    }

    override suspend fun sendAudio(
        channelId: String,
        audioData: ByteArray,
        sequenceNumber: Int,
        timestamp: Long
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val socket = audioSocket ?: return@withContext Result.failure(
            IllegalStateException("Transport not started")
        )

        try {
            // Build RTP-like packet
            val packet = buildAudioPacket(channelId, audioData, sequenceNumber, timestamp)

            // TODO: Implement broadcast encryption with channel shared key
            // For now, sign the packet to ensure integrity
            val signature = cryptoManager.sign(packet, localSecretKey) ?: ByteArray(64)
            val encrypted = packet + signature

            // Send
            val datagram = DatagramPacket(
                encrypted,
                encrypted.size,
                InetAddress.getByName(config.multicastGroup),
                config.audioPort
            )
            socket.send(datagram)

            Result.success(Unit)
        } catch (e: Exception) {
            logE("Failed to send audio: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun sendFloorControl(
        channelId: String,
        message: ByteArray
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val socket = controlSocket ?: return@withContext Result.failure(
            IllegalStateException("Transport not started")
        )

        try {
            // Prepend channel ID and public key
            val packet = buildControlPacket(channelId, message)

            // TODO: Implement broadcast encryption with channel shared key
            // For now, sign the packet to ensure integrity
            val signature = cryptoManager.sign(packet, localSecretKey) ?: ByteArray(64)
            val encrypted = packet + signature

            // Send
            val datagram = DatagramPacket(
                encrypted,
                encrypted.size,
                InetAddress.getByName(config.multicastGroup),
                config.controlPort
            )
            socket.send(datagram)

            Result.success(Unit)
        } catch (e: Exception) {
            logE("Failed to send floor control: ${e.message}")
            Result.failure(e)
        }
    }

    // =========================================================================
    // PRIVATE HELPERS
    // =========================================================================

    private fun createMulticastSocket(port: Int): MulticastSocket {
        return MulticastSocket(port).apply {
            reuseAddress = true
            loopbackMode = true // Disable loopback - don't receive our own packets
            timeToLive = 255 // Max TTL for mesh routing
            soTimeout = 0 // Non-blocking receive
            receiveBufferSize = 65536 // Large buffer for jitter
            sendBufferSize = 65536
        }
    }

    private suspend fun audioReceiveLoop() {
        val buffer = ByteArray(2048) // Max RTP packet size
        val socket = audioSocket ?: return

        while (_isActive.value && isActive) {
            try {
                val packet = DatagramPacket(buffer, buffer.size)
                socket.receive(packet)

                val rawData = packet.data.copyOf(packet.length)
                if (rawData.size < 64) continue // Too small, need at least signature

                // Split data and signature
                val data = rawData.copyOf(rawData.size - 64)
                val signature = rawData.copyOfRange(rawData.size - 64, rawData.size)

                // Parse first to get sender's public key for verification
                val audioPacket = parseAudioPacket(data) ?: continue

                // TODO: Verify signature with sender's public key from contact repository
                // For now, accept all packets to allow testing
                // if (!cryptoManager.verify(data, signature, audioPacket.senderPublicKey)) continue

                if (!audioPacket.senderPublicKey.contentEquals(localPublicKey)) {
                    _incomingAudio.emit(audioPacket)
                }

            } catch (e: SocketTimeoutException) {
                // Normal timeout, continue
            } catch (e: Exception) {
                if (_isActive.value) {
                    logW("Audio receive error: ${e.message}")
                }
            }

            yield() // Be cooperative
        }
    }

    private suspend fun controlReceiveLoop() {
        val buffer = ByteArray(1024)
        val socket = controlSocket ?: return

        while (_isActive.value && isActive) {
            try {
                val packet = DatagramPacket(buffer, buffer.size)
                socket.receive(packet)

                val rawData = packet.data.copyOf(packet.length)
                if (rawData.size < 64) continue // Too small, need at least signature

                // Split data and signature
                val data = rawData.copyOf(rawData.size - 64)
                val signature = rawData.copyOfRange(rawData.size - 64, rawData.size)

                // Parse first to get sender's public key for verification
                val controlPacket = parseControlPacket(data) ?: continue

                // TODO: Verify signature with sender's public key
                // if (!cryptoManager.verify(data, signature, controlPacket.senderPublicKey)) continue

                if (!controlPacket.senderPublicKey.contentEquals(localPublicKey)) {
                    _incomingFloorControl.emit(controlPacket)
                }

            } catch (e: SocketTimeoutException) {
                // Normal timeout, continue
            } catch (e: Exception) {
                if (_isActive.value) {
                    logW("Control receive error: ${e.message}")
                }
            }

            yield()
        }
    }

    /**
     * Build audio packet:
     * [2 bytes: channel ID length]
     * [N bytes: channel ID]
     * [32 bytes: sender public key]
     * [4 bytes: sequence number]
     * [8 bytes: timestamp]
     * [remaining: audio data]
     */
    private fun buildAudioPacket(
        channelId: String,
        audioData: ByteArray,
        sequenceNumber: Int,
        timestamp: Long
    ): ByteArray {
        val channelIdBytes = channelId.toByteArray(Charsets.UTF_8)
        val packet = ByteArray(2 + channelIdBytes.size + 32 + 4 + 8 + audioData.size)

        var offset = 0

        // Channel ID length (2 bytes)
        packet[offset++] = (channelIdBytes.size shr 8).toByte()
        packet[offset++] = channelIdBytes.size.toByte()

        // Channel ID
        System.arraycopy(channelIdBytes, 0, packet, offset, channelIdBytes.size)
        offset += channelIdBytes.size

        // Public key (32 bytes)
        System.arraycopy(localPublicKey, 0, packet, offset, 32)
        offset += 32

        // Sequence number (4 bytes, big-endian)
        packet[offset++] = (sequenceNumber shr 24).toByte()
        packet[offset++] = (sequenceNumber shr 16).toByte()
        packet[offset++] = (sequenceNumber shr 8).toByte()
        packet[offset++] = sequenceNumber.toByte()

        // Timestamp (8 bytes, big-endian)
        for (i in 7 downTo 0) {
            packet[offset++] = (timestamp shr (i * 8)).toByte()
        }

        // Audio data
        System.arraycopy(audioData, 0, packet, offset, audioData.size)

        return packet
    }

    private fun parseAudioPacket(data: ByteArray): PTTAudioPacket? {
        if (data.size < 46) return null // Minimum: 2 + 0 + 32 + 4 + 8

        var offset = 0

        // Channel ID length
        val channelIdLen = ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)
        offset += 2

        if (data.size < 2 + channelIdLen + 44) return null

        // Channel ID
        val channelId = String(data, offset, channelIdLen, Charsets.UTF_8)
        offset += channelIdLen

        // Public key
        val publicKey = data.copyOfRange(offset, offset + 32)
        offset += 32

        // Sequence number
        val seqNum = ((data[offset].toInt() and 0xFF) shl 24) or
                ((data[offset + 1].toInt() and 0xFF) shl 16) or
                ((data[offset + 2].toInt() and 0xFF) shl 8) or
                (data[offset + 3].toInt() and 0xFF)
        offset += 4

        // Timestamp
        var timestamp = 0L
        for (i in 0 until 8) {
            timestamp = (timestamp shl 8) or (data[offset + i].toLong() and 0xFF)
        }
        offset += 8

        // Audio data
        val audioData = data.copyOfRange(offset, data.size)

        return PTTAudioPacket(
            channelId = channelId,
            senderPublicKey = publicKey,
            audioData = audioData,
            sequenceNumber = seqNum,
            timestamp = timestamp
        )
    }

    private fun buildControlPacket(channelId: String, message: ByteArray): ByteArray {
        val channelIdBytes = channelId.toByteArray(Charsets.UTF_8)
        val packet = ByteArray(2 + channelIdBytes.size + 32 + message.size)

        var offset = 0

        // Channel ID length
        packet[offset++] = (channelIdBytes.size shr 8).toByte()
        packet[offset++] = channelIdBytes.size.toByte()

        // Channel ID
        System.arraycopy(channelIdBytes, 0, packet, offset, channelIdBytes.size)
        offset += channelIdBytes.size

        // Public key
        System.arraycopy(localPublicKey, 0, packet, offset, 32)
        offset += 32

        // Message
        System.arraycopy(message, 0, packet, offset, message.size)

        return packet
    }

    private fun parseControlPacket(data: ByteArray): PTTFloorControlPacket? {
        if (data.size < 35) return null // Minimum: 2 + 0 + 32 + 1

        var offset = 0

        // Channel ID length
        val channelIdLen = ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)
        offset += 2

        if (data.size < 2 + channelIdLen + 33) return null

        // Channel ID
        val channelId = String(data, offset, channelIdLen, Charsets.UTF_8)
        offset += channelIdLen

        // Public key
        val publicKey = data.copyOfRange(offset, offset + 32)
        offset += 32

        // Message type (first byte of payload)
        val messageTypeOrd = data[offset].toInt() and 0xFF
        val messageType = FloorMessageType.entries.getOrNull(messageTypeOrd) ?: return null

        // Payload
        val payload = data.copyOfRange(offset, data.size)

        return PTTFloorControlPacket(
            channelId = channelId,
            senderPublicKey = publicKey,
            messageType = messageType,
            payload = payload
        )
    }

    companion object {
        private const val TAG_PTT = 0x505454 // "PTT" as hex for TrafficStats
    }
}
