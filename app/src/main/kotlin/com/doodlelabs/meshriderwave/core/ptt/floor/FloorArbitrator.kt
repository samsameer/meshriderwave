/*
 * Mesh Rider Wave - Floor Arbitrator
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 *
 * Centralized floor arbitration for mission-critical PTT operations.
 *
 * Features:
 * - Single point of authority for floor grants
 * - Fair queue management with priority ordering
 * - Emergency override handling
 * - Arbitrator election (if primary fails)
 * - Conflict-free floor assignment
 *
 * Use Cases:
 * - Mission-critical operations requiring guaranteed ordering
 * - Large groups (10+ members) where distributed arbitration has latency
 * - Command/control scenarios with strict hierarchy
 *
 * Reference:
 * - 3GPP TS 24.379: MCPTT Floor Control
 * - 3GPP TS 24.380: Floor Control Protocol
 */

package com.doodlelabs.meshriderwave.core.ptt.floor

import com.doodlelabs.meshriderwave.core.util.logD
import com.doodlelabs.meshriderwave.core.util.logE
import com.doodlelabs.meshriderwave.core.util.logI
import com.doodlelabs.meshriderwave.core.util.logW
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Centralized Floor Arbitrator
 *
 * Acts as the single authority for floor grants within a channel.
 * Implements fair, priority-based queue management.
 */
@Singleton
class FloorArbitrator @Inject constructor(
    private val floorProtocol: FloorControlProtocol
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // =========================================================================
    // ARBITRATOR STATE
    // =========================================================================

    /**
     * Arbitrator role
     */
    enum class Role {
        /** Not the arbitrator */
        PARTICIPANT,
        /** Primary arbitrator */
        PRIMARY,
        /** Backup arbitrator (takes over if primary fails) */
        BACKUP,
        /** Becoming arbitrator (election in progress) */
        CANDIDATE
    }

    /**
     * Channel arbitration state
     */
    data class ArbitrationState(
        val channelId: ByteArray,
        val role: Role = Role.PARTICIPANT,
        val primaryArbiter: ByteArray? = null,
        val backupArbiter: ByteArray? = null,
        val currentHolder: FloorControlManager.FloorHolder? = null,
        val queueSize: Int = 0,
        val lastHeartbeat: Long = 0,
        val isHealthy: Boolean = true
    ) {
        val isArbiter: Boolean get() = role == Role.PRIMARY || role == Role.BACKUP
        val hasPrimary: Boolean get() = primaryArbiter != null

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is ArbitrationState) return false
            return channelId.contentEquals(other.channelId) && role == other.role
        }

        override fun hashCode(): Int = channelId.contentHashCode()
    }

    /**
     * Pending floor request in queue
     */
    data class QueuedRequest(
        val request: FloorControlManager.FloorRequest,
        val receivedAt: Long = System.currentTimeMillis(),
        val retryCount: Int = 0
    ) : Comparable<QueuedRequest> {
        override fun compareTo(other: QueuedRequest): Int {
            return request.compareTo(other.request)
        }
    }

    // =========================================================================
    // CONFIGURATION
    // =========================================================================

    companion object {
        // Timing
        const val ARBITRATOR_HEARTBEAT_MS = 3_000L       // 3 seconds
        const val ARBITRATOR_TIMEOUT_MS = 10_000L        // 10 seconds without heartbeat
        const val ELECTION_TIMEOUT_MS = 5_000L           // 5 seconds for election
        const val GRANT_RESPONSE_TIMEOUT_MS = 1_000L     // 1 second for grant ACK

        // Limits
        const val MAX_QUEUE_SIZE = 20
        const val MAX_FLOOR_DURATION_MS = 60_000L        // 1 minute max
        const val EMERGENCY_FLOOR_DURATION_MS = 120_000L // 2 minutes for emergency

        // Election
        const val MIN_PARTICIPANTS_FOR_ELECTION = 2
    }

    // =========================================================================
    // INSTANCE STATE
    // =========================================================================

    // Per-channel state
    private val channelStates = ConcurrentHashMap<String, MutableStateFlow<ArbitrationState>>()

    // Per-channel request queues
    private val requestQueues = ConcurrentHashMap<String, PriorityBlockingQueue<QueuedRequest>>()

    // Active floor grants (channelId -> holder)
    private val activeGrants = ConcurrentHashMap<String, FloorControlManager.FloorHolder>()

    // Own identity
    var ownPublicKey: ByteArray = ByteArray(0)
    var ownName: String = "Unknown"
    var ownPriority: Int = 0 // For election (higher = more likely to become arbiter)

    // Running state
    private val isRunning = AtomicBoolean(false)

    // Active jobs
    private val heartbeatJobs = ConcurrentHashMap<String, Job>()
    private val monitorJobs = ConcurrentHashMap<String, Job>()

    // Callbacks
    var onBecameArbiter: ((channelId: ByteArray) -> Unit)? = null
    var onLostArbiter: ((channelId: ByteArray) -> Unit)? = null
    var onFloorGranted: ((channelId: ByteArray, holder: FloorControlManager.FloorHolder) -> Unit)? = null
    var onFloorReleased: ((channelId: ByteArray) -> Unit)? = null
    var onQueueUpdated: ((channelId: ByteArray, size: Int) -> Unit)? = null

    // =========================================================================
    // PUBLIC API
    // =========================================================================

    /**
     * Initialize arbitration for a channel
     *
     * @param channelId Channel to manage
     * @param beArbiter True to become the arbitrator
     */
    fun initChannel(channelId: ByteArray, beArbiter: Boolean = false) {
        val key = channelId.toHexString()

        if (!channelStates.containsKey(key)) {
            channelStates[key] = MutableStateFlow(ArbitrationState(channelId))
            requestQueues[key] = PriorityBlockingQueue()
            logI("Arbitrator initialized for channel ${key.take(8)}")
        }

        if (beArbiter) {
            becomeArbiter(channelId)
        }
    }

    /**
     * Get arbitration state for a channel
     */
    fun getState(channelId: ByteArray): StateFlow<ArbitrationState> {
        val key = channelId.toHexString()
        return channelStates.getOrPut(key) {
            MutableStateFlow(ArbitrationState(channelId))
        }
    }

    /**
     * Become the arbitrator for a channel
     */
    fun becomeArbiter(channelId: ByteArray) {
        val key = channelId.toHexString()
        val stateFlow = channelStates[key] ?: return

        logI("Becoming arbitrator for channel ${key.take(8)}")

        stateFlow.update { it.copy(
            role = Role.PRIMARY,
            primaryArbiter = ownPublicKey,
            lastHeartbeat = System.currentTimeMillis(),
            isHealthy = true
        )}

        // Start heartbeat
        startHeartbeat(channelId)

        // Announce arbitrator role
        scope.launch {
            broadcastArbiterAnnouncement(channelId)
        }

        onBecameArbiter?.invoke(channelId)
    }

    /**
     * Resign as arbitrator
     */
    fun resignArbiter(channelId: ByteArray) {
        val key = channelId.toHexString()
        val stateFlow = channelStates[key] ?: return

        if (stateFlow.value.role != Role.PRIMARY) return

        logI("Resigning as arbitrator for channel ${key.take(8)}")

        // Stop heartbeat
        heartbeatJobs.remove(key)?.cancel()

        stateFlow.update { it.copy(
            role = Role.PARTICIPANT,
            primaryArbiter = null
        )}

        // Announce resignation
        scope.launch {
            broadcastArbiterResignation(channelId)
        }

        onLostArbiter?.invoke(channelId)
    }

    /**
     * Handle incoming floor request (as arbitrator)
     */
    suspend fun handleFloorRequest(
        channelId: ByteArray,
        request: FloorControlManager.FloorRequest
    ): ArbitrationResult {
        val key = channelId.toHexString()
        val stateFlow = channelStates[key] ?: return ArbitrationResult.Error("Channel not initialized")
        val state = stateFlow.value

        // Only process if we're the arbitrator
        if (state.role != Role.PRIMARY) {
            return ArbitrationResult.NotArbiter
        }

        logD("Arbitrator received floor request from ${request.name}, priority=${request.priority}")

        // Check for emergency override
        if (request.isEmergency) {
            return handleEmergencyRequest(channelId, request)
        }

        // Check if floor is free
        val currentHolder = activeGrants[key]
        if (currentHolder == null) {
            // Floor free - grant immediately
            return grantFloor(channelId, request)
        }

        // Check priority preemption
        if (request.priority.level > currentHolder.priority.level) {
            // Preempt current holder
            return preemptAndGrant(channelId, request, currentHolder)
        }

        // Floor busy - add to queue
        return addToQueue(channelId, request)
    }

    /**
     * Handle floor release (as arbitrator)
     */
    suspend fun handleFloorRelease(channelId: ByteArray, releaserKey: ByteArray): Boolean {
        val key = channelId.toHexString()
        val stateFlow = channelStates[key] ?: return false

        if (stateFlow.value.role != Role.PRIMARY) return false

        val currentHolder = activeGrants[key] ?: return false

        // Verify releaser is current holder
        if (!currentHolder.publicKey.contentEquals(releaserKey)) {
            logW("Floor release from non-holder ignored")
            return false
        }

        logI("Floor released by ${currentHolder.name}")

        // Clear current holder
        activeGrants.remove(key)

        // Update state
        stateFlow.update { it.copy(currentHolder = null) }

        // Broadcast release
        floorProtocol.sendFloorRelease(channelId)

        onFloorReleased?.invoke(channelId)

        // Process queue - grant to next
        processQueue(channelId)

        return true
    }

    /**
     * Handle cancel request
     */
    fun handleCancelRequest(channelId: ByteArray, requestId: String): Boolean {
        val key = channelId.toHexString()
        val queue = requestQueues[key] ?: return false

        val removed = queue.removeIf { it.request.requestId == requestId }

        if (removed) {
            logD("Request $requestId cancelled")
            broadcastQueueUpdate(channelId)
        }

        return removed
    }

    // =========================================================================
    // FLOOR MANAGEMENT
    // =========================================================================

    /**
     * Grant floor to requester
     */
    private suspend fun grantFloor(
        channelId: ByteArray,
        request: FloorControlManager.FloorRequest
    ): ArbitrationResult {
        val key = channelId.toHexString()
        val stateFlow = channelStates[key] ?: return ArbitrationResult.Error("Channel not found")

        val duration = if (request.isEmergency) EMERGENCY_FLOOR_DURATION_MS else request.durationMs
        val expiresAt = System.currentTimeMillis() + duration

        val holder = FloorControlManager.FloorHolder(
            publicKey = request.publicKey,
            name = request.name,
            priority = request.priority,
            grantedAt = System.currentTimeMillis(),
            expiresAt = expiresAt,
            isEmergency = request.isEmergency
        )

        // Store grant
        activeGrants[key] = holder

        // Update state
        stateFlow.update { it.copy(currentHolder = holder) }

        // Send grant message
        floorProtocol.sendFloorGranted(channelId, request.requestId, expiresAt)

        // Broadcast floor taken
        floorProtocol.sendFloorTaken(channelId, holder)

        logI("Floor GRANTED to ${request.name}, expires in ${duration/1000}s")

        onFloorGranted?.invoke(channelId, holder)

        // Schedule auto-release
        scheduleAutoRelease(channelId, expiresAt)

        return ArbitrationResult.Granted(request.requestId, expiresAt)
    }

    /**
     * Handle emergency request - always preempts
     */
    private suspend fun handleEmergencyRequest(
        channelId: ByteArray,
        request: FloorControlManager.FloorRequest
    ): ArbitrationResult {
        val key = channelId.toHexString()
        val currentHolder = activeGrants[key]

        logW("EMERGENCY request from ${request.name}")

        // Revoke current holder if any (unless also emergency)
        if (currentHolder != null && !currentHolder.isEmergency) {
            // Send revoke
            floorProtocol.sendFloorRevoke(channelId, "Emergency override")
            activeGrants.remove(key)
        }

        // Grant to emergency
        return grantFloor(channelId, request)
    }

    /**
     * Preempt current holder and grant to higher priority
     */
    private suspend fun preemptAndGrant(
        channelId: ByteArray,
        request: FloorControlManager.FloorRequest,
        currentHolder: FloorControlManager.FloorHolder
    ): ArbitrationResult {
        val key = channelId.toHexString()

        logI("Preempting ${currentHolder.name} (${currentHolder.priority}) for ${request.name} (${request.priority})")

        // Send revoke to current holder
        floorProtocol.sendFloorRevoke(channelId, "Preempted by ${request.priority}")

        // Remove current grant
        activeGrants.remove(key)

        // Add preempted holder to front of queue (if they want to continue)
        // This is optional behavior - some systems just drop them

        // Grant to new requester
        return grantFloor(channelId, request)
    }

    /**
     * Add request to queue
     */
    private suspend fun addToQueue(
        channelId: ByteArray,
        request: FloorControlManager.FloorRequest
    ): ArbitrationResult {
        val key = channelId.toHexString()
        val queue = requestQueues[key] ?: return ArbitrationResult.Error("Queue not found")
        val stateFlow = channelStates[key] ?: return ArbitrationResult.Error("Channel not found")

        // Check queue limit
        if (queue.size >= MAX_QUEUE_SIZE) {
            floorProtocol.sendFloorDenied(channelId, request.requestId, "Queue full")
            return ArbitrationResult.Denied("Queue full")
        }

        // Add to queue
        queue.offer(QueuedRequest(request))

        // Update state
        stateFlow.update { it.copy(queueSize = queue.size) }

        // Calculate position
        val position = queue.count { it.request <= request }

        logD("Request queued at position $position/${queue.size}")

        // Send queue position
        floorProtocol.sendQueueUpdate(channelId, request.publicKey, position, queue.size)

        broadcastQueueUpdate(channelId)

        return ArbitrationResult.Queued(position, queue.size)
    }

    /**
     * Process queue - grant to next requester
     */
    private fun processQueue(channelId: ByteArray) {
        val key = channelId.toHexString()
        val queue = requestQueues[key] ?: return
        val stateFlow = channelStates[key] ?: return

        // Get next request
        val next = queue.poll() ?: run {
            logD("Queue empty, floor now idle")
            return
        }

        logD("Processing queue: granting to ${next.request.name}")

        // Grant floor
        scope.launch {
            grantFloor(channelId, next.request)
        }

        // Update state
        stateFlow.update { it.copy(queueSize = queue.size) }

        // Notify remaining queue members of new positions
        broadcastQueueUpdate(channelId)
    }

    /**
     * Broadcast queue position updates to all queued members
     */
    private fun broadcastQueueUpdate(channelId: ByteArray) {
        val key = channelId.toHexString()
        val queue = requestQueues[key] ?: return

        val queueList = queue.toList().sortedBy { it }
        queueList.forEachIndexed { index, queued ->
            scope.launch {
                floorProtocol.sendQueueUpdate(
                    channelId,
                    queued.request.publicKey,
                    index + 1,
                    queueList.size
                )
            }
        }

        onQueueUpdated?.invoke(channelId, queueList.size)
    }

    /**
     * Schedule automatic floor release
     */
    private fun scheduleAutoRelease(channelId: ByteArray, expiresAt: Long) {
        val key = channelId.toHexString()
        val delay = (expiresAt - System.currentTimeMillis()).coerceAtLeast(0)

        scope.launch {
            delay(delay)

            val currentHolder = activeGrants[key]
            if (currentHolder != null && currentHolder.expiresAt <= System.currentTimeMillis()) {
                logW("Auto-releasing floor (timeout)")
                handleFloorRelease(channelId, currentHolder.publicKey)
            }
        }
    }

    // =========================================================================
    // ARBITRATOR ELECTION
    // =========================================================================

    /**
     * Start arbitrator election
     */
    fun startElection(channelId: ByteArray) {
        val key = channelId.toHexString()
        val stateFlow = channelStates[key] ?: return

        if (stateFlow.value.role != Role.PARTICIPANT) {
            logD("Already in election or arbitrator")
            return
        }

        logI("Starting arbitrator election for channel ${key.take(8)}")

        stateFlow.update { it.copy(role = Role.CANDIDATE) }

        // Broadcast election message with our priority
        scope.launch {
            broadcastElectionMessage(channelId)

            // Wait for higher priority candidates
            delay(ELECTION_TIMEOUT_MS)

            // Check if we won
            val currentState = stateFlow.value
            if (currentState.role == Role.CANDIDATE) {
                // No higher priority candidate responded - we win
                logI("Election won - becoming arbitrator")
                becomeArbiter(channelId)
            }
        }
    }

    /**
     * Handle election message from another candidate
     */
    fun handleElectionMessage(
        channelId: ByteArray,
        candidateKey: ByteArray,
        candidatePriority: Int
    ) {
        val key = channelId.toHexString()
        val stateFlow = channelStates[key] ?: return

        if (stateFlow.value.role != Role.CANDIDATE) return

        // Higher priority wins
        if (candidatePriority > ownPriority) {
            logD("Yielding to higher priority candidate")
            stateFlow.update { it.copy(role = Role.PARTICIPANT) }
        } else if (candidatePriority == ownPriority) {
            // Tiebreaker: lower public key hash wins
            if (candidateKey.contentHashCode() < ownPublicKey.contentHashCode()) {
                logD("Yielding to tiebreaker winner")
                stateFlow.update { it.copy(role = Role.PARTICIPANT) }
            }
        }
        // Otherwise we maintain candidacy
    }

    /**
     * Handle arbitrator announcement
     */
    fun handleArbiterAnnouncement(channelId: ByteArray, arbiterKey: ByteArray) {
        val key = channelId.toHexString()
        val stateFlow = channelStates[key] ?: return

        logD("Received arbitrator announcement")

        // Accept new arbitrator
        stateFlow.update { it.copy(
            role = Role.PARTICIPANT,
            primaryArbiter = arbiterKey,
            lastHeartbeat = System.currentTimeMillis(),
            isHealthy = true
        )}

        // Start monitoring arbitrator health
        startArbiterMonitor(channelId)
    }

    // =========================================================================
    // HEARTBEAT & MONITORING
    // =========================================================================

    /**
     * Start heartbeat broadcasting (as arbitrator)
     */
    private fun startHeartbeat(channelId: ByteArray) {
        val key = channelId.toHexString()

        heartbeatJobs[key]?.cancel()
        heartbeatJobs[key] = scope.launch {
            while (isActive) {
                delay(ARBITRATOR_HEARTBEAT_MS)

                val state = channelStates[key]?.value
                if (state?.role == Role.PRIMARY) {
                    floorProtocol.sendHeartbeat(channelId)
                    logD("Arbitrator heartbeat sent")
                } else {
                    break
                }
            }
        }
    }

    /**
     * Start monitoring arbitrator health (as participant)
     */
    private fun startArbiterMonitor(channelId: ByteArray) {
        val key = channelId.toHexString()

        monitorJobs[key]?.cancel()
        monitorJobs[key] = scope.launch {
            while (isActive) {
                delay(ARBITRATOR_HEARTBEAT_MS)

                val stateFlow = channelStates[key] ?: break
                val state = stateFlow.value

                if (state.role == Role.PARTICIPANT && state.hasPrimary) {
                    val sinceHeartbeat = System.currentTimeMillis() - state.lastHeartbeat

                    if (sinceHeartbeat > ARBITRATOR_TIMEOUT_MS) {
                        logW("Arbitrator timeout - starting election")
                        stateFlow.update { it.copy(
                            primaryArbiter = null,
                            isHealthy = false
                        )}
                        startElection(channelId)
                        break
                    }
                } else {
                    break
                }
            }
        }
    }

    /**
     * Handle heartbeat from arbitrator
     */
    fun handleArbiterHeartbeat(channelId: ByteArray, arbiterKey: ByteArray) {
        val key = channelId.toHexString()
        val stateFlow = channelStates[key] ?: return

        if (stateFlow.value.primaryArbiter?.contentEquals(arbiterKey) == true) {
            stateFlow.update { it.copy(
                lastHeartbeat = System.currentTimeMillis(),
                isHealthy = true
            )}
        }
    }

    // =========================================================================
    // BROADCAST HELPERS
    // =========================================================================

    private suspend fun broadcastArbiterAnnouncement(channelId: ByteArray) {
        // Send via floor protocol
        // This would be a new message type in production
        logD("Broadcasting arbitrator announcement")
    }

    private suspend fun broadcastArbiterResignation(channelId: ByteArray) {
        logD("Broadcasting arbitrator resignation")
    }

    private suspend fun broadcastElectionMessage(channelId: ByteArray) {
        logD("Broadcasting election message, priority=$ownPriority")
    }

    // =========================================================================
    // UTILITIES
    // =========================================================================

    private fun ByteArray.toHexString(): String =
        joinToString("") { "%02x".format(it) }

    /**
     * Cleanup resources
     */
    fun cleanup() {
        heartbeatJobs.values.forEach { it.cancel() }
        monitorJobs.values.forEach { it.cancel() }
        heartbeatJobs.clear()
        monitorJobs.clear()
        channelStates.clear()
        requestQueues.clear()
        activeGrants.clear()
        scope.cancel()
    }

    // =========================================================================
    // RESULT TYPES
    // =========================================================================

    sealed class ArbitrationResult {
        data class Granted(val requestId: String, val expiresAt: Long) : ArbitrationResult()
        data class Queued(val position: Int, val total: Int) : ArbitrationResult()
        data class Denied(val reason: String) : ArbitrationResult()
        data class Error(val message: String) : ArbitrationResult()
        object NotArbiter : ArbitrationResult()
    }
}
