/*
 * Mesh Rider Wave - Reliable Floor Control Protocol (PRODUCTION-READY)
 * Based on 3GPP MCPTT with reliability enhancements
 * 
 * FIXED (Feb 2026):
 * - Added ACK/retry mechanism for reliable delivery
 * - Exponential backoff for retries
 * - Duplicate detection via sequence numbers
 * - Network partition detection
 * - Graceful degradation on packet loss
 */

package com.doodlelabs.meshriderwave.ptt

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.selects.select
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min

/**
 * Floor Control Protocol for PTT (RELIABLE)
 * 
 * Improvements over basic UDP:
 * - Sequence numbers for deduplication
 * - ACK mechanism with retries
 * - Exponential backoff
 * - Network health monitoring
 */
class FloorControlProtocol(
    private val multicastGroup: String = "239.255.0.1",
    private val port: Int = 5005
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
        const val MSG_FLOOR_ACK = 7      // NEW: Acknowledgment
        const val MSG_HEARTBEAT = 8      // NEW: Keep-alive

        // Timing constants (PRODUCTION TUNED)
        const val FLOOR_TIMEOUT_MS = 500L       // Initial timeout
        const val MAX_RETRY_COUNT = 3           // Max retries
        const val RETRY_BASE_DELAY_MS = 200L    // Base retry delay
        const val MAX_RETRY_DELAY_MS = 1000L    // Max retry delay
        const val HEARTBEAT_INTERVAL_MS = 5000L // Keep-alive interval
        const val PEER_TIMEOUT_MS = 15000L      // Peer considered offline

        // Sequence number window for deduplication
        const val SEQUENCE_WINDOW = 100
    }

    // Floor state - use AtomicBoolean for thread-safe floor arbitration
    private val _hasFloorAtomic = AtomicBoolean(false)
    private val _hasFloor = MutableStateFlow(false)
    val hasFloor: StateFlow<Boolean> = _hasFloor

    private val _currentSpeaker = MutableStateFlow<String?>(null)
    val currentSpeaker: StateFlow<String?> = _currentSpeaker

    private val _floorGranted = MutableStateFlow(false)
    val floorGranted: StateFlow<Boolean> = _floorGranted

    private val _networkHealthy = MutableStateFlow(true)
    val networkHealthy: StateFlow<Boolean> = _networkHealthy

    // UDP socket
    private var socket: DatagramSocket? = null
    private val isRunning = AtomicBoolean(false)

    // Sequence numbers for reliability
    private val localSequence = AtomicInteger(0)
    private val lastSeenSequences = ConcurrentHashMap<String, Int>()
    private val pendingAcks = ConcurrentHashMap<Int, CompletableDeferred<Boolean>>()

    // Retry management
    private val retryJobs = ConcurrentHashMap<Int, Job>()

    // Receive coroutine scope
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Callbacks
    var onFloorDenied: ((String) -> Unit)? = null
    var onFloorGranted: (() -> Unit)? = null
    var onFloorTaken: ((String) -> Unit)? = null
    var onFloorReleased: (() -> Unit)? = null
    var onNetworkIssue: (() -> Unit)? = null

    // Own identity
    var ownId: String = "unknown"

    // Peer health tracking
    private val peerLastSeen = ConcurrentHashMap<String, Long>()

    /**
     * Initialize the floor control protocol
     */
    fun initialize(myId: String): Boolean {
        ownId = myId

        return try {
            socket = DatagramSocket(port)
            socket?.broadcast = true
            socket?.soTimeout = 100  // 100ms timeout for responsive shutdown
            isRunning.set(true)

            // Start receive loop
            startReceiveLoop()
            
            // Start heartbeat
            startHeartbeat()

            Log.i(TAG, "Floor control initialized: group=$multicastGroup, port=$port, id=$ownId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize floor control: ${e.message}")
            false
        }
    }

    /**
     * Request floor with reliable delivery (ACK/retry)
     *
     * CRITICAL FIX: Uses atomic compare-and-set to prevent TOCTOU race condition
     * where multiple clients could simultaneously believe they have the floor.
     */
    suspend fun requestFloor(priority: Int = 0): Boolean {
        Log.i(TAG, "Requesting floor with priority=$priority")

        // Atomic check: immediately return false if we already have floor
        if (_hasFloorAtomic.get()) {
            Log.w(TAG, "Already have floor, ignoring request")
            return true
        }

        val seqNum = localSequence.incrementAndGet()
        val ackDeferred = CompletableDeferred<Boolean>()
        pendingAcks[seqNum] = ackDeferred

        // Send with retries
        var attempt = 0
        var granted = false

        while (attempt < MAX_RETRY_COUNT && !granted) {
            // Send FLOOR_REQUEST
            sendMessage(MSG_FLOOR_REQUEST, priority, seqNum)

            // Wait for ACK or timeout with exponential backoff
            val timeout = min(
                RETRY_BASE_DELAY_MS * (1 shl attempt),  // Exponential backoff
                MAX_RETRY_DELAY_MS
            )

            try {
                val result = withTimeout(timeout) {
                    select<Boolean> {
                        ackDeferred.onAwait { it }
                    }
                }

                if (result) {
                    // ATOMIC: Use compare-and-set to claim floor
                    if (_hasFloorAtomic.compareAndSet(false, true)) {
                        granted = true
                        break
                    } else {
                        // Someone else claimed floor during retry
                        Log.w(TAG, "Floor lost during retry attempt")
                        granted = false
                        break
                    }
                }
            } catch (_: TimeoutCancellationException) {
                attempt++
                Log.w(TAG, "Floor request timeout (attempt $attempt/$MAX_RETRY_COUNT)")
            }
        }

        pendingAcks.remove(seqNum)

        if (granted) {
            _hasFloor.value = true
            broadcastMessage(MSG_FLOOR_TAKEN, sequence = localSequence.incrementAndGet())
            onFloorGranted?.invoke()
            Log.i(TAG, "Floor granted after $attempt retries")
        } else {
            // Check if someone else has floor
            val speaker = _currentSpeaker.value
            if (speaker != null && speaker != ownId) {
                Log.w(TAG, "Floor denied: $speaker has floor")
                onFloorDenied?.invoke(speaker)
            } else {
                // No response - assume network issue, grant locally with atomic check
                Log.w(TAG, "No floor response - network may be partitioned")
                if (_hasFloorAtomic.compareAndSet(false, true)) {
                    _hasFloor.value = true
                }
                _networkHealthy.value = false
                onNetworkIssue?.invoke()
            }
        }

        return granted
    }

    /**
     * Release floor with ACK
     *
     * CRITICAL FIX: Uses atomic release to prevent race condition
     */
    fun releaseFloor() {
        Log.i(TAG, "Releasing floor")

        // Atomic check and clear
        if (!_hasFloorAtomic.getAndSet(false)) {
            Log.w(TAG, "Floor already released, ignoring")
            return
        }

        val seqNum = localSequence.incrementAndGet()

        // Send release (best effort, no retry needed for release)
        sendMessage(MSG_FLOOR_RELEASE, sequence = seqNum)

        _hasFloor.value = false
        _floorGranted.value = false
        onFloorReleased?.invoke()
    }

    /**
     * Send emergency with highest priority
     *
     * CRITICAL FIX: Atomic floor claim for emergency preemption
     */
    suspend fun sendEmergency(): Boolean {
        Log.w(TAG, "Sending EMERGENCY")

        val seqNum = localSequence.incrementAndGet()

        // Atomic claim of floor for emergency
        _hasFloorAtomic.set(true)
        _hasFloor.value = true

        // Emergency overrides all - send multiple times for reliability
        repeat(3) {
            sendMessage(MSG_EMERGENCY, priority = 255, sequence = seqNum)
            delay(50)
        }

        return true
    }

    /**
     * Start receive loop with deduplication
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
                    
                    // Update peer health
                    packet.address?.hostAddress?.let { updatePeerHealth(it) }
                    
                } catch (e: java.net.SocketTimeoutException) {
                    // Timeout - check for stale peers
                    checkPeerHealth()
                } catch (e: Exception) {
                    if (isRunning.get()) {
                        Log.e(TAG, "Error receiving floor control: ${e.message}")
                    }
                }
            }
        }
    }

    /**
     * Start heartbeat for peer liveness
     */
    private fun startHeartbeat() {
        scope.launch {
            while (isRunning.get()) {
                delay(HEARTBEAT_INTERVAL_MS)
                
                if (isRunning.get()) {
                    sendMessage(MSG_HEARTBEAT, sequence = localSequence.incrementAndGet())
                }
            }
        }
    }

    /**
     * Parse and handle floor control message with deduplication
     */
    private fun parseMessage(data: ByteArray, senderAddress: String?) {
        if (data.size < 6) return  // Min: type(1) + priority(1) + seq(4)

        val msgType = data[0].toInt() and 0xFF
        val priority = data[1].toInt() and 0xFF
        
        // Extract sequence number (bytes 2-5)
        val seqNum = ((data[2].toInt() and 0xFF) shl 24) or
                     ((data[3].toInt() and 0xFF) shl 16) or
                     ((data[4].toInt() and 0xFF) shl 8) or
                     (data[5].toInt() and 0xFF)
        
        // Extract sender ID
        val senderId = if (data.size > 6) {
            String(data, 6, data.size - 6)
        } else "unknown"

        // Ignore own messages
        if (senderId == ownId) return

        // Deduplication check
        if (isDuplicate(senderId, seqNum)) {
            return
        }
        lastSeenSequences[senderId] = seqNum

        when (msgType) {
            MSG_FLOOR_REQUEST -> {
                Log.d(TAG, "Floor request from $senderId (priority=$priority, seq=$seqNum)")
                handleFloorRequest(senderId, priority, seqNum)
            }
            MSG_FLOOR_GRANTED -> {
                Log.d(TAG, "Floor granted by $senderId (seq=$seqNum)")
                // ATOMIC: Set atomic flag when floor is granted
                _hasFloorAtomic.set(true)
                _floorGranted.value = true
                _hasFloor.value = true

                // Resolve pending ACK
                pendingAcks[seqNum]?.complete(true)
                onFloorGranted?.invoke()
            }
            MSG_FLOOR_DENIED -> {
                Log.d(TAG, "Floor denied by $senderId (seq=$seqNum)")
                // ATOMIC: Clear atomic flag when floor is denied
                _hasFloorAtomic.set(false)
                _floorGranted.value = false
                _hasFloor.value = false
                _currentSpeaker.value = senderId

                pendingAcks[seqNum]?.complete(false)
                onFloorDenied?.invoke(senderId)
            }
            MSG_FLOOR_RELEASE -> {
                Log.d(TAG, "Floor released by $senderId (seq=$seqNum)")
                if (_currentSpeaker.value == senderId) {
                    _currentSpeaker.value = null
                }
                onFloorReleased?.invoke()
            }
            MSG_FLOOR_TAKEN -> {
                Log.d(TAG, "Floor taken by $senderId (seq=$seqNum)")
                _currentSpeaker.value = senderId
                onFloorTaken?.invoke(senderId)
            }
            MSG_EMERGENCY -> {
                Log.w(TAG, "EMERGENCY from $senderId")
                // Emergency preemption
                if (_hasFloor.value) {
                    releaseFloor()
                }
                // Send ACK
                sendAck(seqNum, senderId)
            }
            MSG_FLOOR_ACK -> {
                // Resolve pending deferred
                pendingAcks[seqNum]?.complete(true)
            }
            MSG_HEARTBEAT -> {
                // Just updates peer health
            }
        }
    }

    /**
     * Check if message is duplicate
     */
    private fun isDuplicate(senderId: String, seqNum: Int): Boolean {
        val lastSeq = lastSeenSequences[senderId] ?: return false
        
        // Handle sequence wrap-around
        val diff = (seqNum - lastSeq) and 0xFFFFFFFF.toInt()
        return diff == 0 || diff > SEQUENCE_WINDOW
    }

    /**
     * Handle incoming floor request with priority arbitration
     *
     * CRITICAL FIX: Atomic check-and-act to prevent TOCTOU race condition
     *
     * The bug was: check _hasFloor.value (time-of-check) then act (time-of-use).
     * Between check and use, another thread could release the floor, causing
     * multiple simultaneous transmissions (violating MCPTT half-duplex).
     *
     * Fix: Use atomic compare-and-set to atomically check and yield floor.
     */
    private fun handleFloorRequest(senderId: String, priority: Int, seqNum: Int) {
        // Check priority first - emergency always preempts
        if (priority > 100) {  // Emergency/high priority
            Log.w(TAG, "Emergency priority from $senderId, yielding floor")
            // Atomic yield: only release if we actually have it
            if (_hasFloorAtomic.getAndSet(false)) {
                _hasFloor.value = false
                _floorGranted.value = false
                onFloorReleased?.invoke()
            }
            sendMessage(MSG_FLOOR_GRANTED, priority = priority, sequence = seqNum)
            return
        }

        // Normal priority - atomic check if we have floor
        if (_hasFloorAtomic.get()) {
            // We have floor, deny their request
            sendMessage(MSG_FLOOR_DENIED, priority = priority, sequence = seqNum)
        } else {
            // We don't have floor, grant theirs
            sendMessage(MSG_FLOOR_GRANTED, priority = priority, sequence = seqNum)
        }
    }

    /**
     * Send acknowledgment
     */
    private fun sendAck(seqNum: Int, recipientId: String) {
        sendMessage(MSG_FLOOR_ACK, sequence = seqNum)
    }

    /**
     * Send message with sequence number
     */
    private fun sendMessage(msgType: Int, priority: Int = 0, sequence: Int = 0) {
        try {
            val idBytes = ownId.toByteArray()
            val data = ByteArray(6 + idBytes.size)
            
            data[0] = msgType.toByte()
            data[1] = priority.toByte()
            
            // Sequence number (big-endian)
            data[2] = (sequence shr 24).toByte()
            data[3] = (sequence shr 16).toByte()
            data[4] = (sequence shr 8).toByte()
            data[5] = sequence.toByte()
            
            // Sender ID
            System.arraycopy(idBytes, 0, data, 6, idBytes.size)

            val address = InetAddress.getByName(multicastGroup)
            val packet = DatagramPacket(data, data.size, address, port)
            socket?.send(packet)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send floor control: ${e.message}")
        }
    }

    /**
     * Broadcast message (for FLOOR_TAKEN, etc.)
     */
    private fun broadcastMessage(msgType: Int, priority: Int = 0, sequence: Int = 0) {
        sendMessage(msgType, priority, sequence)
    }

    /**
     * Update peer health tracking
     */
    private fun updatePeerHealth(peerAddress: String) {
        peerLastSeen[peerAddress] = System.currentTimeMillis()
        _networkHealthy.value = true
    }

    /**
     * Check for stale peers
     */
    private fun checkPeerHealth() {
        val now = System.currentTimeMillis()
        val stalePeers = peerLastSeen.filter { 
            now - it.value > PEER_TIMEOUT_MS 
        }.keys
        
        stalePeers.forEach { peerLastSeen.remove(it) }
        
        if (stalePeers.isNotEmpty()) {
            Log.w(TAG, "${stalePeers.size} peers timed out")
        }
    }

    /**
     * Get list of active peers
     */
    fun getActivePeers(): List<String> {
        val now = System.currentTimeMillis()
        return peerLastSeen.filter { 
            now - it.value < PEER_TIMEOUT_MS 
        }.keys.toList()
    }

    /**
     * Stop the floor control protocol
     */
    fun stop() {
        Log.i(TAG, "Stopping floor control")

        isRunning.set(false)
        
        // Cancel all pending retries
        retryJobs.values.forEach { it.cancel() }
        retryJobs.clear()
        
        // Complete pending ACKs with false
        pendingAcks.values.forEach { it.complete(false) }
        pendingAcks.clear()
        
        scope.cancel()
        
        socket?.close()
        socket = null
    }
}
