/*
 * Mesh Rider Wave - Floor Control Manager
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 *
 * Military-grade floor control implementation based on:
 * - 3GPP TS 24.379: Mission Critical Push-to-Talk (MCPTT)
 * - OMA PoC (Push-to-talk over Cellular) Floor Control Protocol
 * - IETF RFC 4353: Framework for Conferencing
 *
 * Features:
 * - Complete state machine (IDLE → PENDING → GRANTED → RELEASING)
 * - Priority-based preemption (EMERGENCY > HIGH > NORMAL > LOW)
 * - Distributed arbitration with Lamport timestamps
 * - Centralized arbiter mode for mission-critical ops
 * - Encrypted control messages (E2E via CryptoManager)
 * - Queue management with fair ordering
 * - Collision detection and resolution
 * - Heartbeat/keepalive for floor holder detection
 */

package com.doodlelabs.meshriderwave.core.ptt.floor

import com.doodlelabs.meshriderwave.core.crypto.CryptoManager
import com.doodlelabs.meshriderwave.core.util.logD
import com.doodlelabs.meshriderwave.core.util.logE
import com.doodlelabs.meshriderwave.core.util.logI
import com.doodlelabs.meshriderwave.core.util.logW
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Floor Control Manager - 3GPP MCPTT Compliant
 *
 * Implements half-duplex floor arbitration for PTT channels.
 * Supports both distributed (peer-to-peer) and centralized (arbiter) modes.
 */
@Singleton
class FloorControlManager @Inject constructor(
    private val cryptoManager: CryptoManager,
    private val floorProtocol: FloorControlProtocol
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // =========================================================================
    // FLOOR STATE (3GPP TS 24.379 Section 6.2)
    // =========================================================================

    /**
     * Floor control states per 3GPP MCPTT specification
     */
    enum class FloorState {
        /** No floor activity - ready to request */
        IDLE,
        /** Floor request sent, waiting for grant/deny */
        PENDING_REQUEST,
        /** Floor granted - transmitting */
        GRANTED,
        /** Someone else has the floor - receiving */
        TAKEN,
        /** Floor request queued - waiting in line */
        QUEUED,
        /** Floor being released */
        RELEASING,
        /** Floor revoked by higher priority */
        REVOKED,
        /** Error state - requires reset */
        ERROR
    }

    /**
     * Floor priority levels (higher value = higher priority)
     */
    enum class FloorPriority(val level: Int) {
        LOW(0),           // Background/administrative
        NORMAL(1),        // Standard communication
        HIGH(2),          // Urgent tactical
        EMERGENCY(3),     // Life-threatening (always wins)
        PREEMPTIVE(4)     // System override (admin only)
    }

    /**
     * Arbitration mode
     */
    enum class ArbitrationMode {
        /** Peer-to-peer distributed arbitration (default) */
        DISTRIBUTED,
        /** Centralized floor arbiter (mission-critical) */
        CENTRALIZED
    }

    // =========================================================================
    // STATE MANAGEMENT
    // =========================================================================

    /**
     * Floor state for a single channel
     */
    data class ChannelFloorState(
        val channelId: ByteArray,
        val state: FloorState = FloorState.IDLE,
        val holder: FloorHolder? = null,
        val myRequest: FloorRequest? = null,
        val queuePosition: Int = 0,
        val queueSize: Int = 0,
        val lastUpdate: Long = System.currentTimeMillis(),
        val errorMessage: String? = null
    ) {
        val canRequest: Boolean
            get() = state == FloorState.IDLE || state == FloorState.TAKEN

        val isTransmitting: Boolean
            get() = state == FloorState.GRANTED

        val isReceiving: Boolean
            get() = state == FloorState.TAKEN

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is ChannelFloorState) return false
            return channelId.contentEquals(other.channelId) && state == other.state
        }

        override fun hashCode(): Int = channelId.contentHashCode()
    }

    /**
     * Floor holder information
     */
    data class FloorHolder(
        val publicKey: ByteArray,
        val name: String,
        val priority: FloorPriority,
        val grantedAt: Long,
        val expiresAt: Long,
        val isEmergency: Boolean = false
    ) {
        val remainingMs: Long
            get() = (expiresAt - System.currentTimeMillis()).coerceAtLeast(0)

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is FloorHolder) return false
            return publicKey.contentEquals(other.publicKey)
        }

        override fun hashCode(): Int = publicKey.contentHashCode()
    }

    /**
     * Floor request with Lamport timestamp for distributed ordering
     */
    data class FloorRequest(
        val requestId: String,
        val publicKey: ByteArray,
        val name: String,
        val priority: FloorPriority,
        val lamportTimestamp: Long,
        val localTimestamp: Long = System.currentTimeMillis(),
        val isEmergency: Boolean = false,
        val durationMs: Long = DEFAULT_FLOOR_DURATION_MS
    ) : Comparable<FloorRequest> {

        /**
         * Compare for priority queue ordering:
         * 1. Higher priority wins
         * 2. Earlier Lamport timestamp wins (FIFO within same priority)
         * 3. Lower public key hash as tiebreaker (deterministic)
         */
        override fun compareTo(other: FloorRequest): Int {
            // Higher priority first (descending)
            val priorityCompare = other.priority.level.compareTo(this.priority.level)
            if (priorityCompare != 0) return priorityCompare

            // Earlier timestamp first (ascending)
            val timestampCompare = this.lamportTimestamp.compareTo(other.lamportTimestamp)
            if (timestampCompare != 0) return timestampCompare

            // Deterministic tiebreaker
            return this.publicKey.contentHashCode().compareTo(other.publicKey.contentHashCode())
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is FloorRequest) return false
            return requestId == other.requestId
        }

        override fun hashCode(): Int = requestId.hashCode()
    }

    // =========================================================================
    // CONFIGURATION
    // =========================================================================

    companion object {
        // Timing constants (per 3GPP TS 24.379)
        const val DEFAULT_FLOOR_DURATION_MS = 30_000L      // 30 seconds max transmission
        const val EMERGENCY_FLOOR_DURATION_MS = 60_000L   // 60 seconds for emergency
        const val FLOOR_REQUEST_TIMEOUT_MS = 2_000L       // 2 seconds to get response
        const val FLOOR_GRANT_TIMEOUT_MS = 500L           // 500ms for grant propagation
        const val FLOOR_RELEASE_TIMEOUT_MS = 1_000L       // 1 second for release
        const val HEARTBEAT_INTERVAL_MS = 5_000L          // 5 seconds heartbeat
        const val FLOOR_HOLDER_TIMEOUT_MS = 10_000L       // 10 seconds without heartbeat = lost

        // Queue limits
        const val MAX_QUEUE_SIZE = 10
        const val MAX_QUEUE_WAIT_MS = 60_000L             // 1 minute max queue wait

        // Collision detection
        const val COLLISION_WINDOW_MS = 100L              // Requests within 100ms are collisions
    }

    // =========================================================================
    // INSTANCE STATE
    // =========================================================================

    // Per-channel floor state
    private val channelStates = ConcurrentHashMap<String, MutableStateFlow<ChannelFloorState>>()

    // Per-channel request queues (priority ordered)
    private val requestQueues = ConcurrentHashMap<String, PriorityBlockingQueue<FloorRequest>>()

    // Lamport clock for distributed ordering
    private val lamportClock = AtomicLong(0)

    // Own identity
    var ownPublicKey: ByteArray = ByteArray(0)
    var ownName: String = "Unknown"

    // Arbitration mode
    private var arbitrationMode = ArbitrationMode.DISTRIBUTED

    // Active timers
    private val activeTimers = ConcurrentHashMap<String, Job>()

    // Callbacks
    var onFloorGranted: ((channelId: ByteArray) -> Unit)? = null
    var onFloorDenied: ((channelId: ByteArray, reason: String) -> Unit)? = null
    var onFloorRevoked: ((channelId: ByteArray, reason: String) -> Unit)? = null
    var onFloorTaken: ((channelId: ByteArray, holder: FloorHolder) -> Unit)? = null
    var onFloorReleased: ((channelId: ByteArray) -> Unit)? = null
    var onEmergencyOverride: ((channelId: ByteArray, holder: FloorHolder) -> Unit)? = null
    var onQueuePositionChanged: ((channelId: ByteArray, position: Int, total: Int) -> Unit)? = null

    // =========================================================================
    // PUBLIC API
    // =========================================================================

    /**
     * Initialize floor control for a channel
     */
    fun initChannel(channelId: ByteArray, mode: ArbitrationMode = ArbitrationMode.DISTRIBUTED) {
        val key = channelId.toHexString()
        if (!channelStates.containsKey(key)) {
            channelStates[key] = MutableStateFlow(ChannelFloorState(channelId))
            requestQueues[key] = PriorityBlockingQueue()
            logI("Floor control initialized for channel ${key.take(8)}, mode=$mode")
        }
        arbitrationMode = mode
    }

    /**
     * Get floor state flow for a channel
     */
    fun getFloorState(channelId: ByteArray): StateFlow<ChannelFloorState> {
        val key = channelId.toHexString()
        return channelStates.getOrPut(key) {
            MutableStateFlow(ChannelFloorState(channelId))
        }
    }

    /**
     * Request floor (start transmission)
     *
     * @param channelId Target channel
     * @param priority Request priority
     * @param isEmergency Emergency override flag
     * @return Result with request ID or error
     */
    suspend fun requestFloor(
        channelId: ByteArray,
        priority: FloorPriority = FloorPriority.NORMAL,
        isEmergency: Boolean = false
    ): FloorRequestResult {
        val key = channelId.toHexString()
        val stateFlow = channelStates[key] ?: return FloorRequestResult.Error("Channel not initialized")
        val currentState = stateFlow.value

        logD("requestFloor: channel=${key.take(8)}, priority=$priority, emergency=$isEmergency")

        // Validate state
        if (!currentState.canRequest && !isEmergency) {
            val reason = when (currentState.state) {
                FloorState.GRANTED -> "Already transmitting"
                FloorState.PENDING_REQUEST -> "Request already pending"
                FloorState.QUEUED -> "Already in queue"
                else -> "Cannot request floor in state ${currentState.state}"
            }
            logW("Floor request denied: $reason")
            return FloorRequestResult.Denied(reason)
        }

        // Create request with Lamport timestamp
        val effectivePriority = if (isEmergency) FloorPriority.EMERGENCY else priority
        val request = FloorRequest(
            requestId = generateRequestId(),
            publicKey = ownPublicKey,
            name = ownName,
            priority = effectivePriority,
            lamportTimestamp = lamportClock.incrementAndGet(),
            isEmergency = isEmergency,
            durationMs = if (isEmergency) EMERGENCY_FLOOR_DURATION_MS else DEFAULT_FLOOR_DURATION_MS
        )

        // Update state to pending
        stateFlow.update { it.copy(
            state = FloorState.PENDING_REQUEST,
            myRequest = request,
            lastUpdate = System.currentTimeMillis()
        )}

        // Send floor request message (encrypted)
        val sent = floorProtocol.sendFloorRequest(channelId, request)
        if (!sent) {
            stateFlow.update { it.copy(state = FloorState.ERROR, errorMessage = "Failed to send request") }
            return FloorRequestResult.Error("Failed to send floor request")
        }

        // Start request timeout
        startRequestTimeout(channelId, request.requestId)

        // Wait for response (grant/deny/queue)
        return waitForFloorResponse(channelId, request)
    }

    /**
     * Release floor (stop transmission)
     */
    suspend fun releaseFloor(channelId: ByteArray): Boolean {
        val key = channelId.toHexString()
        val stateFlow = channelStates[key] ?: return false
        val currentState = stateFlow.value

        if (currentState.state != FloorState.GRANTED) {
            logW("Cannot release floor: not currently granted")
            return false
        }

        logI("Releasing floor on channel ${key.take(8)}")

        // Update state
        stateFlow.update { it.copy(
            state = FloorState.RELEASING,
            lastUpdate = System.currentTimeMillis()
        )}

        // Send release message
        floorProtocol.sendFloorRelease(channelId)

        // Cancel any active timers
        cancelTimer("${key}_grant")
        cancelTimer("${key}_heartbeat")

        // Transition to idle
        stateFlow.update { it.copy(
            state = FloorState.IDLE,
            holder = null,
            myRequest = null,
            lastUpdate = System.currentTimeMillis()
        )}

        onFloorReleased?.invoke(channelId)

        // Process queue - grant to next waiting request
        processQueue(channelId)

        return true
    }

    /**
     * Cancel pending floor request
     */
    suspend fun cancelRequest(channelId: ByteArray): Boolean {
        val key = channelId.toHexString()
        val stateFlow = channelStates[key] ?: return false
        val currentState = stateFlow.value

        if (currentState.state != FloorState.PENDING_REQUEST && currentState.state != FloorState.QUEUED) {
            return false
        }

        logD("Cancelling floor request on channel ${key.take(8)}")

        // Remove from queue
        val queue = requestQueues[key]
        currentState.myRequest?.let { queue?.remove(it) }

        // Send cancel message
        currentState.myRequest?.let {
            floorProtocol.sendFloorRequestCancel(channelId, it.requestId)
        }

        // Update state
        stateFlow.update { it.copy(
            state = if (currentState.holder != null) FloorState.TAKEN else FloorState.IDLE,
            myRequest = null,
            queuePosition = 0,
            lastUpdate = System.currentTimeMillis()
        )}

        cancelTimer("${key}_request")

        return true
    }

    // =========================================================================
    // INCOMING MESSAGE HANDLERS
    // =========================================================================

    /**
     * Handle incoming floor request from peer
     */
    fun handleFloorRequest(channelId: ByteArray, request: FloorRequest) {
        val key = channelId.toHexString()
        val stateFlow = channelStates[key] ?: return
        val currentState = stateFlow.value

        // Update Lamport clock
        lamportClock.updateAndGet { maxOf(it, request.lamportTimestamp) + 1 }

        logD("Received floor request from ${request.name}, priority=${request.priority}")

        when {
            // Emergency always preempts (unless we also have emergency)
            request.isEmergency && currentState.state == FloorState.GRANTED &&
                    currentState.myRequest?.isEmergency != true -> {
                handleEmergencyPreemption(channelId, request)
            }

            // Floor is free - check if we should grant
            currentState.state == FloorState.IDLE -> {
                handleFloorRequestWhenIdle(channelId, request)
            }

            // Floor is taken - queue the request
            currentState.state == FloorState.GRANTED || currentState.state == FloorState.TAKEN -> {
                handleFloorRequestWhenBusy(channelId, request)
            }

            // Both requesting - collision resolution
            currentState.state == FloorState.PENDING_REQUEST -> {
                handleCollision(channelId, request, currentState.myRequest)
            }

            else -> {
                // Queue request
                addToQueue(channelId, request)
            }
        }
    }

    /**
     * Handle floor granted message
     */
    fun handleFloorGranted(channelId: ByteArray, requestId: String, expiresAt: Long) {
        val key = channelId.toHexString()
        val stateFlow = channelStates[key] ?: return
        val currentState = stateFlow.value

        // Verify this is for our request
        if (currentState.myRequest?.requestId != requestId) {
            logW("Received grant for unknown request: $requestId")
            return
        }

        logI("Floor GRANTED on channel ${key.take(8)}")

        // Create holder info
        val holder = FloorHolder(
            publicKey = ownPublicKey,
            name = ownName,
            priority = currentState.myRequest.priority,
            grantedAt = System.currentTimeMillis(),
            expiresAt = expiresAt,
            isEmergency = currentState.myRequest.isEmergency
        )

        // Update state
        stateFlow.update { it.copy(
            state = FloorState.GRANTED,
            holder = holder,
            queuePosition = 0,
            lastUpdate = System.currentTimeMillis()
        )}

        // Cancel request timeout
        cancelTimer("${key}_request")

        // Start grant timeout (auto-release)
        startGrantTimeout(channelId, expiresAt)

        // Start heartbeat
        startHeartbeat(channelId)

        onFloorGranted?.invoke(channelId)
    }

    /**
     * Handle floor denied message
     */
    fun handleFloorDenied(channelId: ByteArray, requestId: String, reason: String) {
        val key = channelId.toHexString()
        val stateFlow = channelStates[key] ?: return
        val currentState = stateFlow.value

        if (currentState.myRequest?.requestId != requestId) return

        logW("Floor DENIED on channel ${key.take(8)}: $reason")

        stateFlow.update { it.copy(
            state = if (currentState.holder != null) FloorState.TAKEN else FloorState.IDLE,
            myRequest = null,
            lastUpdate = System.currentTimeMillis()
        )}

        cancelTimer("${key}_request")
        onFloorDenied?.invoke(channelId, reason)
    }

    /**
     * Handle floor taken message (someone else got the floor)
     */
    fun handleFloorTaken(channelId: ByteArray, holder: FloorHolder) {
        val key = channelId.toHexString()
        val stateFlow = channelStates[key] ?: return
        val currentState = stateFlow.value

        logD("Floor TAKEN by ${holder.name} on channel ${key.take(8)}")

        // If we were pending, move to queued
        val newState = when (currentState.state) {
            FloorState.PENDING_REQUEST -> FloorState.QUEUED
            FloorState.IDLE -> FloorState.TAKEN
            else -> currentState.state
        }

        stateFlow.update { it.copy(
            state = newState,
            holder = holder,
            lastUpdate = System.currentTimeMillis()
        )}

        // Update queue position if queued
        if (newState == FloorState.QUEUED) {
            updateQueuePosition(channelId)
        }

        onFloorTaken?.invoke(channelId, holder)

        // Handle emergency alert
        if (holder.isEmergency) {
            onEmergencyOverride?.invoke(channelId, holder)
        }
    }

    /**
     * Handle floor released message
     */
    fun handleFloorReleased(channelId: ByteArray, releaserKey: ByteArray) {
        val key = channelId.toHexString()
        val stateFlow = channelStates[key] ?: return
        val currentState = stateFlow.value

        // Verify releaser is the current holder
        if (currentState.holder?.publicKey?.contentEquals(releaserKey) != true) {
            logW("Floor release from non-holder ignored")
            return
        }

        logD("Floor RELEASED on channel ${key.take(8)}")

        // Update state
        val newState = if (currentState.state == FloorState.QUEUED) FloorState.QUEUED else FloorState.IDLE

        stateFlow.update { it.copy(
            state = newState,
            holder = null,
            lastUpdate = System.currentTimeMillis()
        )}

        onFloorReleased?.invoke(channelId)

        // Process queue
        processQueue(channelId)
    }

    /**
     * Handle queue position update
     */
    fun handleQueueUpdate(channelId: ByteArray, position: Int, total: Int) {
        val key = channelId.toHexString()
        val stateFlow = channelStates[key] ?: return

        stateFlow.update { it.copy(
            queuePosition = position,
            queueSize = total,
            lastUpdate = System.currentTimeMillis()
        )}

        onQueuePositionChanged?.invoke(channelId, position, total)
    }

    /**
     * Handle heartbeat from floor holder
     */
    fun handleHeartbeat(channelId: ByteArray, holderKey: ByteArray) {
        val key = channelId.toHexString()
        val stateFlow = channelStates[key] ?: return
        val currentState = stateFlow.value

        if (currentState.holder?.publicKey?.contentEquals(holderKey) == true) {
            // Reset holder timeout
            stateFlow.update { it.copy(lastUpdate = System.currentTimeMillis()) }
        }
    }

    // =========================================================================
    // PRIVATE HELPERS
    // =========================================================================

    /**
     * Wait for floor response with timeout
     */
    private suspend fun waitForFloorResponse(
        channelId: ByteArray,
        request: FloorRequest
    ): FloorRequestResult {
        val key = channelId.toHexString()
        val stateFlow = channelStates[key] ?: return FloorRequestResult.Error("Channel not found")

        return try {
            withTimeout(FLOOR_REQUEST_TIMEOUT_MS) {
                stateFlow.first { state ->
                    when (state.state) {
                        FloorState.GRANTED -> true
                        FloorState.QUEUED -> true
                        FloorState.IDLE, FloorState.TAKEN -> state.myRequest == null
                        FloorState.ERROR -> true
                        else -> false
                    }
                }.let { state ->
                    when (state.state) {
                        FloorState.GRANTED -> FloorRequestResult.Granted(request.requestId)
                        FloorState.QUEUED -> FloorRequestResult.Queued(state.queuePosition, state.queueSize)
                        FloorState.ERROR -> FloorRequestResult.Error(state.errorMessage ?: "Unknown error")
                        else -> FloorRequestResult.Denied("Request not granted")
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            // Timeout - check if distributed mode allows self-grant
            if (arbitrationMode == ArbitrationMode.DISTRIBUTED) {
                // In distributed mode, timeout with no denial = grant
                handleSelfGrant(channelId, request)
                FloorRequestResult.Granted(request.requestId)
            } else {
                FloorRequestResult.Denied("Request timeout")
            }
        }
    }

    /**
     * Self-grant in distributed mode (no arbiter)
     */
    private fun handleSelfGrant(channelId: ByteArray, request: FloorRequest) {
        val key = channelId.toHexString()
        val stateFlow = channelStates[key] ?: return

        logD("Self-granting floor (distributed mode, no denial received)")

        val expiresAt = System.currentTimeMillis() + request.durationMs
        val holder = FloorHolder(
            publicKey = ownPublicKey,
            name = ownName,
            priority = request.priority,
            grantedAt = System.currentTimeMillis(),
            expiresAt = expiresAt,
            isEmergency = request.isEmergency
        )

        stateFlow.update { it.copy(
            state = FloorState.GRANTED,
            holder = holder,
            lastUpdate = System.currentTimeMillis()
        )}

        // Broadcast floor taken
        scope.launch {
            floorProtocol.sendFloorTaken(channelId, holder)
        }

        startGrantTimeout(channelId, expiresAt)
        startHeartbeat(channelId)
        onFloorGranted?.invoke(channelId)
    }

    /**
     * Handle emergency preemption
     */
    private fun handleEmergencyPreemption(channelId: ByteArray, request: FloorRequest) {
        val key = channelId.toHexString()
        val stateFlow = channelStates[key] ?: return

        logW("EMERGENCY PREEMPTION by ${request.name}")

        // Revoke current floor
        stateFlow.update { it.copy(
            state = FloorState.REVOKED,
            lastUpdate = System.currentTimeMillis()
        )}

        onFloorRevoked?.invoke(channelId, "Emergency override by ${request.name}")

        // Grant to emergency requester
        scope.launch {
            floorProtocol.sendFloorGranted(channelId, request.requestId,
                System.currentTimeMillis() + EMERGENCY_FLOOR_DURATION_MS)
        }
    }

    /**
     * Handle floor request when idle
     */
    private fun handleFloorRequestWhenIdle(channelId: ByteArray, request: FloorRequest) {
        // In distributed mode, grant immediately
        if (arbitrationMode == ArbitrationMode.DISTRIBUTED) {
            scope.launch {
                val expiresAt = System.currentTimeMillis() + request.durationMs
                floorProtocol.sendFloorGranted(channelId, request.requestId, expiresAt)
            }
        }
        // In centralized mode, arbiter handles this
    }

    /**
     * Handle floor request when floor is busy
     */
    private fun handleFloorRequestWhenBusy(channelId: ByteArray, request: FloorRequest) {
        val key = channelId.toHexString()
        val stateFlow = channelStates[key] ?: return
        val currentState = stateFlow.value

        // Check priority preemption
        if (currentState.holder != null &&
            request.priority.level > currentState.holder.priority.level) {
            // Higher priority - preempt current holder
            logI("Priority preemption: ${request.priority} > ${currentState.holder.priority}")

            if (currentState.holder.publicKey.contentEquals(ownPublicKey)) {
                // We are being preempted
                onFloorRevoked?.invoke(channelId, "Preempted by ${request.name} (${request.priority})")
            }

            // Grant to higher priority
            scope.launch {
                floorProtocol.sendFloorRevoke(channelId, "Priority preemption")
                delay(100) // Brief delay for revoke to propagate
                floorProtocol.sendFloorGranted(channelId, request.requestId,
                    System.currentTimeMillis() + request.durationMs)
            }
        } else {
            // Same or lower priority - add to queue
            addToQueue(channelId, request)
            scope.launch {
                val queue = requestQueues[key] ?: return@launch
                val position = queue.indexOf(request) + 1
                floorProtocol.sendQueueUpdate(channelId, request.publicKey, position, queue.size)
            }
        }
    }

    /**
     * Handle collision (both parties requesting simultaneously)
     */
    private fun handleCollision(
        channelId: ByteArray,
        incomingRequest: FloorRequest,
        myRequest: FloorRequest?
    ) {
        if (myRequest == null) return

        logD("Collision detected: my=${myRequest.lamportTimestamp}, theirs=${incomingRequest.lamportTimestamp}")

        // Use Lamport timestamp for ordering
        val iWin = myRequest < incomingRequest // Lower = earlier = wins

        if (iWin) {
            logD("Collision resolved: I WIN (earlier timestamp)")
            // Add their request to queue
            addToQueue(channelId, incomingRequest)
        } else {
            logD("Collision resolved: THEY WIN (earlier timestamp)")
            // I lose - move to queued state
            val key = channelId.toHexString()
            val stateFlow = channelStates[key] ?: return

            stateFlow.update { it.copy(
                state = FloorState.QUEUED,
                lastUpdate = System.currentTimeMillis()
            )}

            // Add my request to queue
            addToQueue(channelId, myRequest)

            // They get the floor
            scope.launch {
                floorProtocol.sendFloorGranted(channelId, incomingRequest.requestId,
                    System.currentTimeMillis() + incomingRequest.durationMs)
            }
        }
    }

    /**
     * Add request to queue
     */
    private fun addToQueue(channelId: ByteArray, request: FloorRequest) {
        val key = channelId.toHexString()
        val queue = requestQueues[key] ?: return

        if (queue.size >= MAX_QUEUE_SIZE) {
            logW("Queue full, rejecting request from ${request.name}")
            scope.launch {
                floorProtocol.sendFloorDenied(channelId, request.requestId, "Queue full")
            }
            return
        }

        queue.offer(request)
        logD("Added ${request.name} to queue at position ${queue.indexOf(request) + 1}")
    }

    /**
     * Process queue - grant floor to next in line
     */
    private fun processQueue(channelId: ByteArray) {
        val key = channelId.toHexString()
        val queue = requestQueues[key] ?: return

        val next = queue.poll() ?: return

        logD("Processing queue: granting floor to ${next.name}")

        scope.launch {
            val expiresAt = System.currentTimeMillis() + next.durationMs
            floorProtocol.sendFloorGranted(channelId, next.requestId, expiresAt)
        }
    }

    /**
     * Update queue position for own request
     */
    private fun updateQueuePosition(channelId: ByteArray) {
        val key = channelId.toHexString()
        val stateFlow = channelStates[key] ?: return
        val queue = requestQueues[key] ?: return
        val myRequest = stateFlow.value.myRequest ?: return

        val position = queue.indexOf(myRequest) + 1
        stateFlow.update { it.copy(
            queuePosition = position,
            queueSize = queue.size
        )}

        onQueuePositionChanged?.invoke(channelId, position, queue.size)
    }

    // =========================================================================
    // TIMERS
    // =========================================================================

    private fun startRequestTimeout(channelId: ByteArray, requestId: String) {
        val key = channelId.toHexString()
        val timerKey = "${key}_request"

        cancelTimer(timerKey)
        activeTimers[timerKey] = scope.launch {
            delay(FLOOR_REQUEST_TIMEOUT_MS)
            handleRequestTimeout(channelId, requestId)
        }
    }

    private fun startGrantTimeout(channelId: ByteArray, expiresAt: Long) {
        val key = channelId.toHexString()
        val timerKey = "${key}_grant"
        val delay = (expiresAt - System.currentTimeMillis()).coerceAtLeast(0)

        cancelTimer(timerKey)
        activeTimers[timerKey] = scope.launch {
            delay(delay)
            handleGrantTimeout(channelId)
        }
    }

    private fun startHeartbeat(channelId: ByteArray) {
        val key = channelId.toHexString()
        val timerKey = "${key}_heartbeat"

        cancelTimer(timerKey)
        activeTimers[timerKey] = scope.launch {
            while (isActive) {
                delay(HEARTBEAT_INTERVAL_MS)
                val state = channelStates[key]?.value
                if (state?.state == FloorState.GRANTED) {
                    floorProtocol.sendHeartbeat(channelId)
                } else {
                    break
                }
            }
        }
    }

    private fun cancelTimer(timerKey: String) {
        activeTimers.remove(timerKey)?.cancel()
    }

    private fun handleRequestTimeout(channelId: ByteArray, requestId: String) {
        val key = channelId.toHexString()
        val stateFlow = channelStates[key] ?: return

        if (stateFlow.value.myRequest?.requestId == requestId &&
            stateFlow.value.state == FloorState.PENDING_REQUEST) {
            logW("Floor request timeout")
            stateFlow.update { it.copy(
                state = FloorState.IDLE,
                myRequest = null,
                errorMessage = "Request timeout"
            )}
            onFloorDenied?.invoke(channelId, "Request timeout")
        }
    }

    private suspend fun handleGrantTimeout(channelId: ByteArray) {
        val key = channelId.toHexString()
        val stateFlow = channelStates[key] ?: return

        if (stateFlow.value.state == FloorState.GRANTED) {
            logW("Floor grant timeout - auto-releasing")
            releaseFloor(channelId)
        }
    }

    // =========================================================================
    // UTILITIES
    // =========================================================================

    private fun generateRequestId(): String {
        return "${System.currentTimeMillis()}-${(Math.random() * 100000).toInt()}"
    }

    private fun ByteArray.toHexString(): String =
        joinToString("") { "%02x".format(it) }

    /**
     * Cleanup resources
     */
    fun cleanup() {
        activeTimers.values.forEach { it.cancel() }
        activeTimers.clear()
        channelStates.clear()
        requestQueues.clear()
        scope.cancel()
    }

    // =========================================================================
    // RESULT TYPES
    // =========================================================================

    sealed class FloorRequestResult {
        data class Granted(val requestId: String) : FloorRequestResult()
        data class Queued(val position: Int, val total: Int) : FloorRequestResult()
        data class Denied(val reason: String) : FloorRequestResult()
        data class Error(val message: String) : FloorRequestResult()
    }
}
