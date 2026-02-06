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
import com.doodlelabs.meshriderwave.core.util.logW
import com.doodlelabs.meshriderwave.domain.model.Contact
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicInteger
import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream
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
    // RACE CONDITION FIX Feb 2026: Use mutex for server socket lifecycle
    private val socketLock = Mutex()
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val scope = CoroutineScope(ioDispatcher + SupervisorJob())

    // State - use atomic compare-and-set for state transitions
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    private val _localAddresses = MutableStateFlow<List<String>>(emptyList())
    val localAddresses: StateFlow<List<String>> = _localAddresses

    // Track active connection handlers for clean shutdown
    private val activeConnections = AtomicInteger(0)

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
     * RACE CONDITION FIX Feb 2026: Use mutex to prevent concurrent start/stop
     */
    fun start() {
        // TOCTOU FIX: Use mutex for check-then-act pattern
        if (!socketLock.tryLock()) {
            logD("start() already starting/stopping, skipping")
            return
        }

        try {
            if (_isRunning.value) {
                logD("start() already running")
                return
            }

            serverJob = scope.launch {
                try {
                    socketLock.withLock {
                        serverSocket = ServerSocket(BuildConfig.SIGNALING_PORT)
                    }
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
                            val server = socketLock.withLock { serverSocket } ?: break
                            val socket = server.accept()
                            logD("start() incoming connection from ${socket.remoteSocketAddress}")
                            // FIX Jan 2026: Launch each connection handler in its own coroutine
                            // so the accept loop isn't blocked waiting for handleIncomingConnection
                            // to complete. This was causing the accept loop to stall when a call
                            // connection was being processed (readMessage blocks for up to 10s).
                            activeConnections.incrementAndGet()
                            launch {
                                try {
                                    handleIncomingConnection(socket)
                                } finally {
                                    activeConnections.decrementAndGet()
                                }
                            }
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
        } finally {
            socketLock.unlock()
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
     * RACE CONDITION FIX Feb 2026: Wait for active connections to complete
     */
    fun stop() {
        logD("stop()")

        // Cancel server job first
        serverJob?.cancel()
        serverJob = null

        // Close server socket under mutex - run in scope since withLock is suspend
        scope.launch {
            socketLock.withLock {
                try {
                    serverSocket?.close()
                } catch (e: Exception) {
                    logE("Error closing server socket", e)
                }
                serverSocket = null
            }
        }.invokeOnCompletion {
            _isRunning.value = false
        }

        // Wait for active connection handlers to complete
        scope.launch {
            var attempts = 0
            while (activeConnections.get() > 0 && attempts < 50) {
                kotlinx.coroutines.delay(100)
                attempts++
            }
            if (activeConnections.get() > 0) {
                logW("stop() ${activeConnections.get()} connections still active after 5s")
            }
        }

        // Note: scope is not cancelled here as this is a singleton
        // and may be restarted. Scope coroutines are cancelled via serverJob.
    }

    /**
     * Initiate outgoing call
     * RACE CONDITION FIX Feb 2026: Ensure socket is closed on all error paths
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
                    socket = null
                    return@withContext CallResult.Error("Encryption failed")
                }

                // Configure socket for call signaling
                socket.keepAlive = true
                socket.tcpNoDelay = true
                logI("initiateCall: sending offer to ${socket.inetAddress?.hostAddress}, socket connected=${socket.isConnected}")

                // CRITICAL: Use single stream pair for entire socket lifecycle
                val rawOut = socket.getOutputStream()
                val rawIn = socket.getInputStream()
                val outputStream = DataOutputStream(rawOut)
                val inputStream = DataInputStream(rawIn)

                // Send offer
                outputStream.writeInt(encrypted.size)
                outputStream.write(encrypted)
                outputStream.flush()
                logD("initiateCall: offer sent (${encrypted.size} bytes), waiting for answer with 30s timeout...")

                // Wait for response — 30s timeout because receiver needs to:
                // 1. Decrypt offer  2. Show incoming call UI  3. User taps Accept
                // 4. Create WebRTC answer  5. Encrypt and send back
                socket.soTimeout = 30000

                // DEBUG: Check what the raw input stream reports
                logD("initiateCall: socket state: closed=${socket.isClosed} connected=${socket.isConnected} inputShutdown=${socket.isInputShutdown}")
                logD("initiateCall: rawIn.available()=${rawIn.available()}")

                val responseBytes = try {
                    logD("initiateCall: calling readInt() on DataInputStream...")
                    val length = inputStream.readInt()
                    logD("initiateCall: response length=$length")
                    if (length <= 0 || length > 1024 * 1024) {
                        logW("initiateCall: invalid response length=$length")
                        null
                    } else {
                        val data = ByteArray(length)
                        inputStream.readFully(data)
                        logD("initiateCall: response received ${data.size} bytes")
                        data
                    }
                } catch (e: SocketTimeoutException) {
                    logW("initiateCall: TIMEOUT waiting for answer (30s)")
                    null
                } catch (e: java.io.EOFException) {
                    // Debug: try raw read to see what's really on the stream
                    logW("initiateCall: EOF from readInt()! closed=${socket.isClosed} connected=${socket.isConnected} available=${try { rawIn.available() } catch (_: Exception) { -1 }}")
                    null
                } catch (e: Exception) {
                    logE("initiateCall: error reading answer: ${e.javaClass.simpleName}: ${e.message}")
                    null
                }

                if (responseBytes == null) {
                    logE("initiateCall: NO RESPONSE")
                    socket.close()
                    socket = null
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
                    socket = null
                    return@withContext CallResult.Error("Decryption failed")
                }

                val response = JSONObject(responseStr)
                val result = when (response.optString("action")) {
                    "connected" -> {
                        val answer = response.getString("answer")
                        // Transfer socket ownership to caller
                        val connectedSocket = socket
                        socket = null
                        CallResult.Connected(connectedSocket, answer)
                    }
                    "declined" -> {
                        socket.close()
                        socket = null
                        CallResult.Declined
                    }
                    else -> {
                        socket.close()
                        socket = null
                        CallResult.Error("Unknown response")
                    }
                }

                result
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
            // Enable keepalive and disable Nagle for signaling
            socket.keepAlive = true
            socket.tcpNoDelay = true
            logD("handleIncomingConnection: from ${socket.remoteSocketAddress}, connected=${socket.isConnected}")

            val data = readMessage(socket)
            if (data == null) {
                logW("handleIncomingConnection: readMessage returned null, closing socket")
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
                    logI("handleIncomingConnection: CALL received, socket open=${!socket.isClosed} connected=${socket.isConnected}")

                    // Store pending call info for notification decline action
                    // RACE CONDITION FIX Feb 2026: Use suspend function
                    PendingCallStore.setPendingCall(socket, senderPublicKey.copyOf())

                    _incomingCalls.emit(
                        IncomingCall(
                            socket = socket,
                            senderPublicKey = senderPublicKey.copyOf(),
                            offer = offer,
                            remoteAddress = (socket.remoteSocketAddress as? InetSocketAddress)?.hostString
                        )
                    )
                    logI("handleIncomingConnection: IncomingCall emitted, socket still open=${!socket.isClosed}")
                    // IMPORTANT: Do NOT close socket here - it stays open for sendCallResponse later
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
                logI("sendCallResponse: socket closed=${socket.isClosed} connected=${socket.isConnected} action=${if (answer != null) "connected" else "declined"}")
                val message = if (answer != null) {
                    JSONObject().put("action", "connected").put("answer", answer)
                } else {
                    JSONObject().put("action", "declined")
                }
                sendEncryptedMessage(socket, recipientKey, message)
            } catch (e: java.net.SocketException) {
                logW("sendCallResponse: socket closed")
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

    /**
     * Send length-prefixed message using DataOutputStream.
     * Uses the socket's raw output stream directly — no BufferedOutputStream wrapper.
     *
     * CRITICAL FIX Jan 2026: Previously created a new BufferedOutputStream per call,
     * violating SEI CERT FIO06-J. BufferedOutputStream eagerly buffers and creating
     * multiple wrappers can cause data corruption. DataOutputStream writes directly
     * to the underlying stream with no internal buffering issues.
     *
     * @see <a href="https://wiki.sei.cmu.edu/confluence/display/java/FIO06-J">SEI CERT FIO06-J</a>
     */
    private fun sendMessage(socket: Socket, data: ByteArray) {
        val out = DataOutputStream(socket.getOutputStream())
        out.writeInt(data.size)
        out.write(data)
        out.flush()
    }

    /**
     * Read length-prefixed message using DataInputStream.
     * Uses the socket's raw input stream directly — no BufferedInputStream wrapper.
     *
     * CRITICAL FIX Jan 2026: Previously created a new BufferedInputStream per call.
     * BufferedInputStream reads ahead into an 8KB internal buffer. When the wrapper
     * was discarded, buffered-but-unread bytes were LOST from the socket stream.
     * This caused the caller's readMessage() to get EOF because the receiver's
     * BufferedInputStream had consumed bytes beyond the first message.
     * DataInputStream.readFully() reads exactly the requested bytes with no buffering.
     *
     * @see <a href="https://wiki.sei.cmu.edu/confluence/display/java/FIO06-J">SEI CERT FIO06-J</a>
     */
    private fun readMessage(socket: Socket, timeoutMs: Int = 10000): ByteArray? {
        logD("readMessage: START timeout=${timeoutMs}ms closed=${socket.isClosed} connected=${socket.isConnected} addr=${socket.inetAddress?.hostAddress}")
        return try {
            socket.soTimeout = timeoutMs
            val input = DataInputStream(socket.getInputStream())

            logD("readMessage: waiting for length int...")
            val length = input.readInt()
            logD("readMessage: length=$length from ${socket.inetAddress?.hostAddress}")

            if (length <= 0 || length > 1024 * 1024) {
                logW("readMessage: invalid length=$length")
                return null
            }

            val data = ByteArray(length)
            input.readFully(data)
            logD("readMessage: SUCCESS read ${data.size} bytes")
            data
        } catch (e: SocketTimeoutException) {
            logW("readMessage: TIMEOUT after ${timeoutMs}ms, socket=${socket.inetAddress?.hostAddress}")
            null
        } catch (e: java.io.EOFException) {
            logW("readMessage: EOF from ${socket.inetAddress?.hostAddress} closed=${socket.isClosed} connected=${socket.isConnected}")
            null
        } catch (e: Exception) {
            logE("readMessage: EXCEPTION ${e.javaClass.simpleName}: ${e.message} closed=${socket.isClosed}")
            null
        }
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
     * RACE CONDITION FIX Feb 2026: Proper socket lifecycle management
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

                socket = connector.connect(tempContact)
                if (socket == null) {
                    return@withContext false
                }

                // Encrypt message
                val encrypted = cryptoManager.encryptMessage(
                    messageJson,
                    recipientPublicKey,
                    ownPublicKey,
                    ownSecretKey
                )

                if (encrypted == null) {
                    socket.close()
                    socket = null
                    return@withContext false
                }

                // Send
                sendMessage(socket, encrypted)
                socket.close()
                socket = null

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

    /**
     * PendingCallStore - Stores pending incoming call info for notification actions.
     *
     * CallActionReceiver needs access to the pending call's socket and sender key
     * to send the decline response. This static store bridges that gap.
     *
     * Per developer.android.com, static storage is acceptable for short-lived
     * pending operations (notification actions must complete within 10 seconds).
     *
     * RACE CONDITION FIX Feb 2026: Use mutex for thread-safe access
     */
    object PendingCallStore {
        private val lock = Mutex()
        @Volatile
        private var pendingCall: PendingCallInfo? = null

        /**
         * Store pending call info when incoming call is received.
         * Thread-safe: ensures old socket is closed before overwriting.
         */
        suspend fun setPendingCall(socket: Socket, senderPublicKey: ByteArray) {
            lock.withLock {
                // Close any existing pending socket
                pendingCall?.socket?.close()
                pendingCall = PendingCallInfo(socket, senderPublicKey.copyOf())
            }
        }

        /**
         * Get pending call info for decline action.
         * Clears the pending call atomically.
         */
        suspend fun getAndClearPendingCall(): PendingCallInfo? {
            return lock.withLock {
                val call = pendingCall
                pendingCall = null
                call
            }
        }

        /**
         * Get pending call info without clearing (for read-only access).
         */
        fun peekPendingCall(): PendingCallInfo? = pendingCall

        /**
         * Clear pending call info after answer/decline/timeout.
         * Thread-safe: ensures socket is closed.
         */
        suspend fun clearPendingCall() {
            lock.withLock {
                pendingCall?.socket?.close()
                pendingCall = null
            }
        }

        data class PendingCallInfo(
            val socket: Socket,
            val senderPublicKey: ByteArray
        )
    }

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
