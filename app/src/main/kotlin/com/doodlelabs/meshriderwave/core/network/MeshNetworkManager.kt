/*
 * Mesh Rider Wave - Mesh Network Manager
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 *
 * Handles P2P signaling, peer discovery, and message routing
 */

package com.doodlelabs.meshriderwave.core.network

import android.content.Context
import com.doodlelabs.meshriderwave.BuildConfig
import com.doodlelabs.meshriderwave.core.crypto.CryptoManager
import com.doodlelabs.meshriderwave.core.util.logD
import com.doodlelabs.meshriderwave.core.util.logE
import com.doodlelabs.meshriderwave.core.util.logI
import com.doodlelabs.meshriderwave.domain.model.Contact
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mesh network orchestrator
 * - Listens for incoming connections on SERVER_PORT
 * - Handles signaling messages (offer/answer/hangup)
 * - Encrypts/decrypts all communication
 */
@Singleton
class MeshNetworkManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cryptoManager: CryptoManager,
    private val connector: Connector,
    private val ioDispatcher: CoroutineDispatcher
) {
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val scope = CoroutineScope(ioDispatcher + SupervisorJob())

    // State
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    private val _localAddresses = MutableStateFlow<List<String>>(emptyList())
    val localAddresses: StateFlow<List<String>> = _localAddresses

    // Events
    private val _incomingCalls = MutableSharedFlow<IncomingCall>(extraBufferCapacity = 1)
    val incomingCalls: SharedFlow<IncomingCall> = _incomingCalls

    private val _connectionEvents = MutableSharedFlow<ConnectionEvent>(extraBufferCapacity = 10)
    val connectionEvents: SharedFlow<ConnectionEvent> = _connectionEvents

    // PTT Messages - for routing to PTTManager
    private val _pttMessages = MutableSharedFlow<PTTMessage>(extraBufferCapacity = 50)
    val pttMessages: SharedFlow<PTTMessage> = _pttMessages

    // Keys (set from SettingsRepository)
    var ownPublicKey: ByteArray = ByteArray(0)
    var ownSecretKey: ByteArray = ByteArray(0)

    /**
     * Start listening for incoming connections
     */
    fun start() {
        if (_isRunning.value) {
            logD("start() already running")
            return
        }

        serverJob = scope.launch {
            try {
                serverSocket = ServerSocket(BuildConfig.SIGNALING_PORT)
                _isRunning.value = true
                logI("start() listening on port ${BuildConfig.SIGNALING_PORT}")

                updateLocalAddresses()

                // FIXED Jan 2026: Periodic address refresh for USB-C ethernet detection
                launch {
                    while (isActive) {
                        kotlinx.coroutines.delay(10_000) // Refresh every 10 seconds
                        updateLocalAddresses()
                    }
                }

                while (isActive) {
                    try {
                        // CRASH-FIX Jan 2026: Safe null check instead of !!
                        val server = serverSocket ?: break
                        val socket = server.accept()
                        logD("start() incoming connection from ${socket.remoteSocketAddress}")
                        handleIncomingConnection(socket)
                    } catch (e: SocketException) {
                        if (isActive) logE("start() socket error", e)
                    }
                }
            } catch (e: Exception) {
                logE("start() failed", e)
                _connectionEvents.emit(ConnectionEvent.Error(e.message ?: "Server error"))
            } finally {
                _isRunning.value = false
            }
        }
    }

    /**
     * Manually refresh local addresses
     * Call this when network configuration changes (e.g., USB-C ethernet connected)
     */
    fun refreshAddresses() {
        scope.launch {
            updateLocalAddresses()
        }
    }

    /**
     * Stop listening
     * FIXED Jan 2026: Cancel scope to prevent coroutine leaks
     */
    fun stop() {
        logD("stop()")
        serverJob?.cancel()
        serverJob = null
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            logE("Error closing server socket", e)
        }
        serverSocket = null
        _isRunning.value = false
        // Note: scope is not cancelled here as this is a singleton
        // and may be restarted. Scope coroutines are cancelled via serverJob.
    }

    /**
     * Initiate outgoing call
     */
    suspend fun initiateCall(contact: Contact, offer: String): CallResult {
        return withContext(ioDispatcher) {
            var socket: Socket? = null
            try {
                // Connect to peer
                socket = connector.connect(contact)
                    ?: return@withContext CallResult.Error("Could not connect to peer")

                // Encrypt offer
                val message = JSONObject().apply {
                    put("action", "call")
                    put("offer", offer)
                }

                val encrypted = cryptoManager.encryptMessage(
                    message.toString(),
                    contact.publicKey,
                    ownPublicKey,
                    ownSecretKey
                )

                if (encrypted == null) {
                    socket.close()
                    return@withContext CallResult.Error("Encryption failed")
                }

                // Send
                sendMessage(socket, encrypted)

                // Wait for response
                val responseBytes = readMessage(socket)
                if (responseBytes == null) {
                    socket.close()
                    return@withContext CallResult.Error("No response")
                }

                // Decrypt response
                val senderKey = ByteArray(32)
                val responseStr = cryptoManager.decryptMessage(
                    responseBytes,
                    senderKey,
                    ownPublicKey,
                    ownSecretKey
                )

                if (responseStr == null) {
                    socket.close()
                    return@withContext CallResult.Error("Decryption failed")
                }

                val response = JSONObject(responseStr)
                when (response.optString("action")) {
                    "connected" -> {
                        val answer = response.getString("answer")
                        CallResult.Connected(socket, answer)
                    }
                    "declined" -> {
                        socket.close()
                        CallResult.Declined
                    }
                    else -> {
                        socket.close()
                        CallResult.Error("Unknown response")
                    }
                }
            } catch (e: Exception) {
                logE("initiateCall() error", e)
                // Ensure socket is closed on exception
                try {
                    socket?.close()
                } catch (closeEx: Exception) {
                    logE("Failed to close socket in initiateCall error handler", closeEx)
                }
                CallResult.Error(e.message ?: "Connection error")
            }
        }
    }

    /**
     * Handle incoming connection
     */
    private suspend fun handleIncomingConnection(socket: Socket) {
        try {
            val data = readMessage(socket)
            if (data == null) {
                socket.close()
                return
            }

            // Decrypt message
            val senderPublicKey = ByteArray(32)
            val messageStr = cryptoManager.decryptMessage(
                data,
                senderPublicKey,
                ownPublicKey,
                ownSecretKey
            )

            if (messageStr == null) {
                logD("handleIncomingConnection() decryption failed")
                socket.close()
                return
            }

            val message = JSONObject(messageStr)
            val action = message.optString("action", "")

            logD("handleIncomingConnection() action=$action")

            when (action) {
                "call" -> {
                    val offer = message.getString("offer")
                    _incomingCalls.emit(
                        IncomingCall(
                            socket = socket,
                            senderPublicKey = senderPublicKey.copyOf(),
                            offer = offer,
                            remoteAddress = (socket.remoteSocketAddress as? InetSocketAddress)?.hostString
                        )
                    )
                }
                "ping" -> {
                    // Respond with pong
                    sendEncryptedMessage(socket, senderPublicKey, JSONObject().put("action", "pong"))
                    socket.close()
                }
                "status_change" -> {
                    val status = message.optString("status")
                    _connectionEvents.emit(ConnectionEvent.PeerStatusChange(senderPublicKey, status))
                    socket.close()
                }
                // PTT message types - route to PTTManager via pttMessages flow
                "FLOOR_REQUEST", "FLOOR_TAKEN", "FLOOR_RELEASED", "FLOOR_DENIED",
                "AUDIO", "EMERGENCY", "EMERGENCY_AUDIO", "JOIN", "LEAVE" -> {
                    val channelId = message.optString("channelId")
                    val pttType = message.optString("type", action)
                    val audioData = message.optString("audio", "")
                    val isEmergency = message.optBoolean("emergency", false) ||
                            action == "EMERGENCY" || action == "EMERGENCY_AUDIO"
                    val timestamp = message.optLong("timestamp", System.currentTimeMillis())

                    _pttMessages.emit(
                        PTTMessage(
                            type = pttType,
                            channelId = channelId,
                            senderPublicKey = senderPublicKey.copyOf(),
                            audioData = audioData,
                            isEmergency = isEmergency,
                            timestamp = timestamp,
                            remoteAddress = (socket.remoteSocketAddress as? InetSocketAddress)?.hostString
                        )
                    )
                    logD("handleIncomingConnection() PTT message: $pttType from ${senderPublicKey.take(4).toByteArraySafe().toHexString()}")
                    socket.close()
                }
                else -> {
                    logD("handleIncomingConnection() unknown action: $action")
                    socket.close()
                }
            }
        } catch (e: Exception) {
            logE("handleIncomingConnection() error", e)
            socket.close()
        }
    }

    /**
     * Send encrypted response for incoming call
     */
    suspend fun sendCallResponse(socket: Socket, recipientKey: ByteArray, answer: String?) {
        withContext(ioDispatcher) {
            try {
                val message = if (answer != null) {
                    JSONObject().put("action", "connected").put("answer", answer)
                } else {
                    JSONObject().put("action", "declined")
                }
                sendEncryptedMessage(socket, recipientKey, message)
            } catch (e: Exception) {
                logE("sendCallResponse() error", e)
            }
        }
    }

    private fun sendEncryptedMessage(socket: Socket, recipientKey: ByteArray, message: JSONObject) {
        val encrypted = cryptoManager.encryptMessage(
            message.toString(),
            recipientKey,
            ownPublicKey,
            ownSecretKey
        ) ?: throw Exception("Encryption failed")
        sendMessage(socket, encrypted)
    }

    private fun sendMessage(socket: Socket, data: ByteArray) {
        val out = BufferedOutputStream(socket.getOutputStream())
        // Simple length-prefixed protocol
        out.write((data.size shr 24) and 0xFF)
        out.write((data.size shr 16) and 0xFF)
        out.write((data.size shr 8) and 0xFF)
        out.write(data.size and 0xFF)
        out.write(data)
        out.flush()
    }

    private fun readMessage(socket: Socket): ByteArray? {
        val input = BufferedInputStream(socket.getInputStream())
        socket.soTimeout = 10000

        // Read length
        val lengthBytes = ByteArray(4)
        var read = 0
        while (read < 4) {
            val r = input.read(lengthBytes, read, 4 - read)
            if (r < 0) return null
            read += r
        }

        val length = ((lengthBytes[0].toInt() and 0xFF) shl 24) or
                ((lengthBytes[1].toInt() and 0xFF) shl 16) or
                ((lengthBytes[2].toInt() and 0xFF) shl 8) or
                (lengthBytes[3].toInt() and 0xFF)

        if (length <= 0 || length > 1024 * 1024) return null // Max 1MB

        // Read data
        val data = ByteArray(length)
        read = 0
        while (read < length) {
            val r = input.read(data, read, length - read)
            if (r < 0) return null
            read += r
        }

        return data
    }

    /**
     * Update local addresses - includes all routable private IPs
     * FIXED Jan 2026: Better support for USB-C Ethernet and mesh networks
     */
    private fun updateLocalAddresses() {
        val addresses = mutableListOf<String>()
        val priorityAddresses = mutableListOf<String>()  // Ethernet/mesh first

        try {
            for (iface in NetworkInterface.getNetworkInterfaces()) {
                if (iface.isLoopback || !iface.isUp) continue

                val ifaceName = iface.name.lowercase()
                val isEthernet = ifaceName.startsWith("eth") ||
                                 ifaceName.startsWith("usb") ||
                                 ifaceName.startsWith("enp") ||
                                 ifaceName.startsWith("rndis")
                val isMesh = ifaceName.contains("mesh") ||
                             ifaceName.startsWith("br-") ||
                             ifaceName.contains("smartradio")

                logD("Interface: ${iface.name} (up=${iface.isUp}, eth=$isEthernet, mesh=$isMesh)")

                for (addr in iface.interfaceAddresses) {
                    val ip = addr.address.hostAddress ?: continue
                    // Skip IPv6 link-local for now
                    if (ip.contains(":")) continue

                    // Include all private IP ranges:
                    // - 10.x.x.x (Class A private, includes MeshRider 10.223.x.x)
                    // - 172.16-31.x.x (Class B private)
                    // - 192.168.x.x (Class C private)
                    val isPrivate = ip.startsWith("10.") ||
                                   (ip.startsWith("172.") && isClass172Private(ip)) ||
                                   ip.startsWith("192.168.")

                    if (isPrivate) {
                        logD("  -> IP: $ip (${iface.name})")
                        // Prioritize ethernet/mesh interfaces
                        if (isEthernet || isMesh) {
                            priorityAddresses.add(ip)
                        } else {
                            addresses.add(ip)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logE("updateLocalAddresses() error", e)
        }

        // Ethernet/mesh addresses first, then others
        val allAddresses = priorityAddresses + addresses
        logI("Local addresses (priority first): $allAddresses")
        _localAddresses.value = allAddresses
    }

    /**
     * Check if 172.x.x.x address is in private range (172.16.0.0 - 172.31.255.255)
     */
    private fun isClass172Private(ip: String): Boolean {
        return try {
            val secondOctet = ip.split(".")[1].toInt()
            secondOctet in 16..31
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Send message to a specific peer by public key
     * Used for PTT, group messaging, etc.
     */
    suspend fun sendToPeer(
        recipientPublicKey: ByteArray,
        addresses: List<String>,
        messageJson: String
    ): Boolean {
        return withContext(ioDispatcher) {
            var socket: Socket? = null
            try {
                // Create a temporary contact for connection
                val tempContact = Contact(
                    publicKey = recipientPublicKey,
                    name = "temp",
                    addresses = addresses,
                    createdAt = System.currentTimeMillis()
                )

                socket = connector.connect(tempContact) ?: return@withContext false

                // Encrypt message
                val encrypted = cryptoManager.encryptMessage(
                    messageJson,
                    recipientPublicKey,
                    ownPublicKey,
                    ownSecretKey
                )

                if (encrypted == null) {
                    socket.close()
                    return@withContext false
                }

                // Send
                sendMessage(socket, encrypted)
                socket.close()

                logD("sendToPeer() success to ${recipientPublicKey.take(4).toByteArraySafe().toHexString()}")
                true
            } catch (e: Exception) {
                logE("sendToPeer() failed", e)
                // Ensure socket is closed on exception
                try {
                    socket?.close()
                } catch (closeEx: Exception) {
                    logE("Failed to close socket in sendToPeer error handler", closeEx)
                }
                false
            }
        }
    }

    /**
     * Broadcast message to multiple peers (for PTT/groups)
     * Returns number of successful sends
     * FIXED Jan 2026: Parallel sends for low-latency PTT
     */
    suspend fun broadcastToPeers(
        recipients: List<Pair<ByteArray, List<String>>>, // publicKey to addresses
        messageJson: String
    ): Int {
        return withContext(ioDispatcher) {
            // Send to all peers in parallel for low-latency PTT
            val results = recipients.map { (publicKey, addresses) ->
                async {
                    sendToPeer(publicKey, addresses, messageJson)
                }
            }

            // Wait for all and count successes
            val successCount = results.awaitAll().count { it }
            logD("broadcastToPeers() sent to $successCount/${recipients.size} peers")
            successCount
        }
    }

    private fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }

    /**
     * Convert List<Byte> to ByteArray safely
     * FIXED Jan 2026: Avoid infinite recursion with custom extension
     */
    private fun List<Byte>.toByteArraySafe(): ByteArray = ByteArray(size) { this[it] }

    // Data classes
    data class IncomingCall(
        val socket: Socket,
        val senderPublicKey: ByteArray,
        val offer: String,
        val remoteAddress: String?
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is IncomingCall) return false
            return socket == other.socket
        }
        override fun hashCode() = socket.hashCode()
    }

    sealed class CallResult {
        data class Connected(val socket: Socket, val answer: String) : CallResult()
        data object Declined : CallResult()
        data class Error(val message: String) : CallResult()
    }

    sealed class ConnectionEvent {
        data class PeerStatusChange(val publicKey: ByteArray, val status: String) : ConnectionEvent()
        data class Error(val message: String) : ConnectionEvent()
    }

    /**
     * PTT Message for routing to PTTManager
     */
    data class PTTMessage(
        val type: String,               // FLOOR_REQUEST, FLOOR_TAKEN, AUDIO, etc.
        val channelId: String,          // Hex string of channel ID
        val senderPublicKey: ByteArray, // Sender's public key
        val audioData: String,          // Base64-encoded audio (for AUDIO types)
        val isEmergency: Boolean,       // Emergency flag
        val timestamp: Long,            // Message timestamp
        val remoteAddress: String?      // Sender's IP address
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is PTTMessage) return false
            return type == other.type && channelId == other.channelId &&
                    senderPublicKey.contentEquals(other.senderPublicKey) &&
                    timestamp == other.timestamp
        }
        override fun hashCode() = arrayOf(type, channelId, timestamp).contentHashCode()
    }
}
