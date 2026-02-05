/*
 * Mesh Rider Wave - Simple Floor Control Protocol
 * Based on 3GPP MCPTT but simplified for local use
 * Following OUSHTALK specification
 */

package com.doodlelabs.meshriderwave.ptt

import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first

/**
 * Floor Control Protocol for PTT
 * Simple UDP-based signaling following 3GPP MCPTT principles
 *
 * Messages:
 * - FLOOR_REQUEST: Request permission to transmit
 * - FLOOR_GRANTED: Permission granted
 * - FLOOR_DENIED: Permission denied (someone else has floor)
 * - FLOOR_RELEASE: Releasing floor
 * - FLOOR_TAKEN: Someone started transmitting
 * - EMERGENCY: Emergency preemption
 */
class FloorControlProtocol(
    private val multicastGroup: String = "239.255.0.1",
    private val port: Int = 5005  // Separate from audio port
) {

    companion object {
        private const val TAG = "MeshRider:FloorControl"

        // Message types
        const val MSG_FLOOR_REQUEST = 1
        const val MSG_FLOOR_GRANTED = 2
        const val MSG_FLOOR_DENIED = 3
        const val MSG_FLOOR_RELEASE = 4
        const val MSG_FLOOR_TAKEN = 5
        const val MSG_EMERGENCY = 6

        // Timeout for floor request (ms)
        private const val FLOOR_TIMEOUT_MS = 200L  // 200ms for low-latency PTT
    }

    // Floor state
    private val _hasFloor = MutableStateFlow(false)
    val hasFloor: StateFlow<Boolean> = _hasFloor

    private val _currentSpeaker = MutableStateFlow<String?>(null)
    val currentSpeaker: StateFlow<String?> = _currentSpeaker

    private val _floorGranted = MutableStateFlow(false)
    val floorGranted: StateFlow<Boolean> = _floorGranted

    // UDP socket for signaling
    private var socket: DatagramSocket? = null
    private var isRunning = AtomicBoolean(false)

    // Receive coroutine scope
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Callbacks
    var onFloorDenied: ((String) -> Unit)? = null
    var onFloorGranted: (() -> Unit)? = null
    var onFloorTaken: ((String) -> Unit)? = null
    var onFloorReleased: (() -> Unit)? = null

    // Own identity
    var ownId: String = "unknown"

    /**
     * Initialize the floor control protocol
     */
    fun initialize(myId: String): Boolean {
        ownId = myId

        return try {
            socket = DatagramSocket(port)
            socket?.broadcast = true
            isRunning.set(true)

            // Start receive loop
            startReceiveLoop()

            Log.i(TAG, "Floor control initialized: group=$multicastGroup, port=$port, id=$ownId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize floor control: ${e.message}")
            false
        }
    }

    /**
     * Request floor (transmit permission)
     * Returns true if granted
     */
    suspend fun requestFloor(priority: Int = 0): Boolean {
        Log.i(TAG, "Requesting floor with priority=$priority")

        // Send FLOOR_REQUEST
        sendMessage(MSG_FLOOR_REQUEST, priority = priority)

        // Wait for timeout or early response
        var granted = false
        var denied = false

        try {
            withTimeout(FLOOR_TIMEOUT_MS) {
                _floorGranted.first { it }
            }
            granted = true
        } catch (_: TimeoutCancellationException) {
            // Timeout - check if someone else took floor
            val speaker = _currentSpeaker.value
            if (speaker != null && speaker != ownId) {
                denied = true
                Log.w(TAG, "Floor denied: $speaker has floor")
            } else {
                // No one responded, assume granted (walkie-talkie mode)
                granted = true
                Log.d(TAG, "Floor granted by default (timeout)")
            }
        }

        if (granted) {
            _hasFloor.value = true
            broadcastMessage(MSG_FLOOR_TAKEN)
        } else if (denied) {
            onFloorDenied?.invoke(_currentSpeaker.value ?: "unknown")
        }

        return granted
    }

    /**
     * Release floor
     */
    fun releaseFloor() {
        Log.i(TAG, "Releasing floor")

        _hasFloor.value = false
        _floorGranted.value = false
        broadcastMessage(MSG_FLOOR_RELEASE)

        onFloorReleased?.invoke()
    }

    /**
     * Handle incoming floor request from another user
     */
    private fun handleFloorRequest(senderId: String, priority: Int) {
        if (_hasFloor.value) {
            // We have floor, deny unless they have higher priority
            if (priority > 0) {
                // Emergency priority - yield floor
                Log.w(TAG, "Yielding floor to emergency: $senderId")
                releaseFloor()
                sendDirectMessage(MSG_FLOOR_GRANTED, senderId)
            } else {
                // Deny request
                sendDirectMessage(MSG_FLOOR_DENIED, senderId)
                Log.d(TAG, "Denied floor request from $senderId")
            }
        } else {
            // Grant floor (we don't have it)
            sendDirectMessage(MSG_FLOOR_GRANTED, senderId)
            Log.d(TAG, "Granted floor to $senderId")
        }
    }

    /**
     * Start receive loop for floor control messages
     */
    private fun startReceiveLoop() {
        scope.launch {
            val buffer = ByteArray(1024)
            val packet = DatagramPacket(buffer, buffer.size)

            while (isRunning.get()) {
                try {
                    socket?.receive(packet)

                    // Parse message
                    val data = packet.data.copyOf(packet.length)
                    parseMessage(data, packet.address.hostAddress)
                } catch (e: Exception) {
                    if (isRunning.get()) {
                        Log.e(TAG, "Error receiving floor control message: ${e.message}")
                    }
                }
            }
        }
    }

    /**
     * Parse incoming floor control message
     */
    private fun parseMessage(data: ByteArray, senderAddress: String) {
        if (data.size < 2) return

        val msgType = data[0].toInt() and 0xFF
        val priority = data[1].toInt() and 0xFF
        val senderId = String(data, 2, data.size - 2)

        when (msgType) {
            MSG_FLOOR_REQUEST -> {
                Log.d(TAG, "Floor request from $senderId (priority=$priority)")
                handleFloorRequest(senderId, priority)
            }
            MSG_FLOOR_GRANTED -> {
                Log.d(TAG, "Floor granted by $senderId")
                _floorGranted.value = true
                _hasFloor.value = true
                onFloorGranted?.invoke()
            }
            MSG_FLOOR_DENIED -> {
                Log.d(TAG, "Floor denied by $senderId")
                _floorGranted.value = false
                _hasFloor.value = false
                _currentSpeaker.value = senderId
                onFloorDenied?.invoke(senderId)
            }
            MSG_FLOOR_RELEASE -> {
                Log.d(TAG, "Floor released by $senderId")
                _currentSpeaker.value = null
            }
            MSG_FLOOR_TAKEN -> {
                Log.d(TAG, "Floor taken by $senderId")
                _currentSpeaker.value = senderId
                onFloorTaken?.invoke(senderId)
            }
            MSG_EMERGENCY -> {
                Log.w(TAG, "EMERGENCY from $senderId")
                // Emergency preemption - yield floor immediately
                if (_hasFloor.value) {
                    releaseFloor()
                }
            }
        }
    }

    /**
     * Broadcast message to multicast group
     */
    private fun broadcastMessage(msgType: Int, priority: Int = 0) {
        sendMessage(msgType, priority = priority)
    }

    /**
     * Send message to multicast group
     */
    private fun sendMessage(msgType: Int, priority: Int = 0) {
        try {
            val data = ByteArray(2 + ownId.length)
            data[0] = msgType.toByte()
            data[1] = priority.toByte()
            System.arraycopy(ownId.toByteArray(), 0, data, 2, ownId.length)

            val address = InetAddress.getByName(multicastGroup)
            val packet = DatagramPacket(data, data.size, address, port)
            socket?.send(packet)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send floor control message: ${e.message}")
        }
    }

    /**
     * Send direct message to specific peer (for GRANT/DENIED)
     */
    private fun sendDirectMessage(msgType: Int, recipientId: String) {
        // For simplicity, use multicast but target specific recipient
        sendMessage(msgType)
    }

    /**
     * Stop the floor control protocol
     */
    fun stop() {
        Log.i(TAG, "Stopping floor control")

        isRunning.set(false)
        socket?.close()
        socket = null
        scope.cancel()
    }
}
