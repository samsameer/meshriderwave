/*
 * Mesh Rider Wave - Group Call Manager
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 *
 * Military-grade multi-party calling with adaptive topology
 *
 * Architecture:
 * - 2-4 participants: P2P Mesh (direct connections)
 * - 5-20 participants: Distributed SFU (elected from peers)
 * - 20+ participants: Multi-SFU cascade
 *
 * Reference: arXiv:2206.07685 (Decentralized WebRTC P2P using Kademlia)
 */

package com.doodlelabs.meshriderwave.core.group

import android.content.Context
import android.os.BatteryManager
import com.doodlelabs.meshriderwave.core.crypto.CryptoManager
import com.doodlelabs.meshriderwave.core.network.Connector
import com.doodlelabs.meshriderwave.core.util.logD
import com.doodlelabs.meshriderwave.core.util.logE
import com.doodlelabs.meshriderwave.core.util.logI
import com.doodlelabs.meshriderwave.core.util.logW
import com.doodlelabs.meshriderwave.core.webrtc.RTCCall
import com.doodlelabs.meshriderwave.domain.model.Contact
import com.doodlelabs.meshriderwave.domain.model.group.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.webrtc.PeerConnection
import org.webrtc.SessionDescription
import java.net.Socket
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages multi-party group calls with adaptive topology
 */
@Singleton
class GroupCallManager @Inject constructor(
    private val context: Context,
    private val cryptoManager: CryptoManager,
    private val connector: Connector
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Call state
    private val _callState = MutableStateFlow(GroupCallState())
    val callState: StateFlow<GroupCallState> = _callState.asStateFlow()

    // Peer connections (keyed by participant public key hex)
    private val peerConnections = ConcurrentHashMap<String, RTCCall>()

    // SFU role flag
    private var isActingSFU = false

    // Own keys
    var ownPublicKey: ByteArray = ByteArray(0)
    var ownSecretKey: ByteArray = ByteArray(0)

    // Callbacks
    var onParticipantJoined: ((GroupCallState.CallParticipant) -> Unit)? = null
    var onParticipantLeft: ((ByteArray) -> Unit)? = null
    var onTopologyChanged: ((GroupCallState.CallTopology) -> Unit)? = null
    var onActiveSpeakerChanged: ((ByteArray?) -> Unit)? = null

    companion object {
        private const val P2P_MAX_PARTICIPANTS = 4
        private const val SFU_MAX_PARTICIPANTS = 20
        private const val TOPOLOGY_CHECK_INTERVAL = 5000L  // ms
        private const val ELECTION_TIMEOUT = 10000L        // ms
    }

    // =========================================================================
    // PUBLIC API
    // =========================================================================

    /**
     * Start a new group call
     */
    suspend fun startGroupCall(
        group: Group,
        initialParticipants: List<Contact>,
        videoEnabled: Boolean = false
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val callId = UUID.randomUUID().toString()

            logI("startGroupCall: callId=$callId, participants=${initialParticipants.size}")

            // Initialize call state
            _callState.update {
                GroupCallState(
                    callId = callId,
                    groupId = group.groupId,
                    status = GroupCallState.Status.INITIATING,
                    topology = selectInitialTopology(initialParticipants.size),
                    localState = GroupCallState.LocalParticipantState(
                        isCameraEnabled = videoEnabled
                    )
                )
            }

            // Connect to initial participants
            initialParticipants.forEach { contact ->
                connectToParticipant(contact)
            }

            // Start topology monitoring
            startTopologyMonitor()

            Result.success(callId)
        } catch (e: Exception) {
            logE("startGroupCall failed", e)
            _callState.update { it.copy(status = GroupCallState.Status.ERROR, errorMessage = e.message) }
            Result.failure(e)
        }
    }

    /**
     * Join an existing group call
     */
    suspend fun joinGroupCall(
        callId: String,
        group: Group,
        inviteFrom: Contact
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            logI("joinGroupCall: callId=$callId")

            _callState.update {
                GroupCallState(
                    callId = callId,
                    groupId = group.groupId,
                    status = GroupCallState.Status.CONNECTING
                )
            }

            // Connect to inviter first
            connectToParticipant(inviteFrom)

            // Request participant list from inviter
            requestParticipantList(inviteFrom)

            Result.success(Unit)
        } catch (e: Exception) {
            logE("joinGroupCall failed", e)
            Result.failure(e)
        }
    }

    /**
     * Add participant to active call
     */
    suspend fun addParticipant(contact: Contact): Result<Unit> {
        if (!_callState.value.isActive) {
            return Result.failure(IllegalStateException("No active call"))
        }

        return try {
            connectToParticipant(contact)
            checkTopologyUpgrade()
            Result.success(Unit)
        } catch (e: Exception) {
            logE("addParticipant failed", e)
            Result.failure(e)
        }
    }

    /**
     * Leave the current call
     */
    suspend fun leaveCall() {
        logI("leaveCall")

        // Notify peers
        broadcastLeaveMessage()

        // If acting as SFU, trigger re-election
        if (isActingSFU) {
            triggerSFUReelection()
        }

        // Cleanup all connections
        peerConnections.values.forEach { it.cleanup() }
        peerConnections.clear()

        _callState.update { GroupCallState() }
        isActingSFU = false
    }

    /**
     * Toggle local microphone
     */
    fun setMicrophoneEnabled(enabled: Boolean) {
        peerConnections.values.forEach { it.setMicrophoneEnabled(enabled) }
        _callState.update {
            it.copy(localState = it.localState.copy(isMicEnabled = enabled))
        }
    }

    /**
     * Toggle local camera
     */
    fun setCameraEnabled(enabled: Boolean) {
        peerConnections.values.forEach { it.setCameraEnabled(enabled) }
        _callState.update {
            it.copy(localState = it.localState.copy(isCameraEnabled = enabled))
        }
    }

    /**
     * Toggle speaker
     */
    fun setSpeakerEnabled(enabled: Boolean) {
        peerConnections.values.forEach { it.setSpeakerEnabled(enabled) }
        _callState.update {
            it.copy(localState = it.localState.copy(isSpeakerEnabled = enabled))
        }
    }

    /**
     * Get participant connection for video rendering
     */
    fun getParticipantConnection(publicKey: ByteArray): RTCCall? {
        return peerConnections[publicKey.toHexString()]
    }

    // =========================================================================
    // TOPOLOGY MANAGEMENT
    // =========================================================================

    /**
     * Select initial topology based on participant count
     */
    private fun selectInitialTopology(participantCount: Int): GroupCallState.CallTopology {
        return when {
            participantCount <= P2P_MAX_PARTICIPANTS -> GroupCallState.CallTopology.P2PMesh
            participantCount <= SFU_MAX_PARTICIPANTS -> {
                // Will elect SFU node after connections established
                GroupCallState.CallTopology.P2PMesh
            }
            else -> {
                // Multi-SFU for large groups
                GroupCallState.CallTopology.MultiSFU(emptyList())
            }
        }
    }

    /**
     * Check if topology upgrade is needed
     */
    private suspend fun checkTopologyUpgrade() {
        val state = _callState.value
        val participantCount = state.participantCount + 1 // Include self

        when {
            // Upgrade from P2P to SFU
            state.topology is GroupCallState.CallTopology.P2PMesh &&
                    participantCount > P2P_MAX_PARTICIPANTS -> {
                logI("Upgrading topology: P2P -> SFU (participants=$participantCount)")
                electSFUNode()
            }

            // Upgrade from single SFU to Multi-SFU
            state.topology is GroupCallState.CallTopology.DistributedSFU &&
                    participantCount > SFU_MAX_PARTICIPANTS -> {
                logI("Upgrading topology: SFU -> Multi-SFU (participants=$participantCount)")
                expandToMultiSFU()
            }
        }
    }

    /**
     * Elect the best SFU node from participants
     */
    private suspend fun electSFUNode() {
        logD("electSFUNode: Starting election")

        val candidates = mutableListOf<Pair<ByteArray, SFUElectionCriteria>>()

        // Gather election criteria from all participants
        candidates.add(ownPublicKey to getLocalElectionCriteria())

        // Request criteria from peers
        peerConnections.forEach { (keyHex, _) ->
            // In real implementation, request criteria via data channel
            // For now, use placeholder
        }

        // Find best candidate
        val winner = candidates.maxByOrNull { it.second.calculateScore() }

        if (winner != null) {
            val winnerKey = winner.first

            if (winnerKey.contentEquals(ownPublicKey)) {
                // We are the SFU
                becomeSFU()
            } else {
                // Connect to winner as SFU
                connectToSFU(winnerKey)
            }

            _callState.update {
                it.copy(
                    topology = GroupCallState.CallTopology.DistributedSFU(
                        sfuNodeId = winnerKey,
                        sfuAddress = "", // Will be set after connection
                        backupNodes = candidates
                            .filter { c -> !c.first.contentEquals(winnerKey) }
                            .sortedByDescending { c -> c.second.calculateScore() }
                            .take(2)
                            .map { c -> c.first }
                    )
                )
            }

            onTopologyChanged?.invoke(_callState.value.topology)
        }
    }

    /**
     * Become the SFU node for this call
     */
    private fun becomeSFU() {
        logI("becomeSFU: Acting as SFU for this call")
        isActingSFU = true

        // In SFU mode:
        // - Receive streams from all participants
        // - Forward streams selectively to others
        // - Handle simulcast layer selection
    }

    /**
     * Connect to the elected SFU node
     */
    private suspend fun connectToSFU(sfuNodeKey: ByteArray) {
        logI("connectToSFU: Connecting to SFU ${sfuNodeKey.toHexString().take(8)}")

        // In SFU topology:
        // - Send our streams to SFU only
        // - Receive forwarded streams from SFU
    }

    /**
     * Expand to multi-SFU topology for large groups
     */
    private suspend fun expandToMultiSFU() {
        logW("expandToMultiSFU: Not yet implemented for >$SFU_MAX_PARTICIPANTS participants")
        // Future: Implement multi-SFU cascade
    }

    /**
     * Trigger SFU re-election when current SFU leaves
     */
    private suspend fun triggerSFUReelection() {
        logI("triggerSFUReelection: Current SFU leaving, triggering re-election")
        broadcastMessage("SFU_REELECT", mapOf("reason" to "SFU_LEAVING"))
    }

    /**
     * Start topology monitoring coroutine
     */
    private fun startTopologyMonitor() {
        scope.launch {
            while (_callState.value.isActive) {
                delay(TOPOLOGY_CHECK_INTERVAL)

                // Check network quality
                updateNetworkQuality()

                // Check if topology adjustment needed
                if (_callState.value.shouldUpgradeTopology()) {
                    checkTopologyUpgrade()
                }

                // Detect and update active speaker
                detectActiveSpeaker()
            }
        }
    }

    // =========================================================================
    // PEER CONNECTION MANAGEMENT
    // =========================================================================

    /**
     * Connect to a participant
     */
    private suspend fun connectToParticipant(contact: Contact) {
        val keyHex = contact.publicKey.toHexString()

        if (peerConnections.containsKey(keyHex)) {
            logD("connectToParticipant: Already connected to ${keyHex.take(8)}")
            return
        }

        logI("connectToParticipant: Connecting to ${contact.name} (${keyHex.take(8)})")

        val rtcCall = RTCCall(context, isOutgoing = true).apply {
            initialize()

            onLocalDescription = { sdp ->
                scope.launch {
                    sendOffer(contact, sdp)
                }
            }

            onIceConnectionChange = { state ->
                handleConnectionStateChange(contact.publicKey, state)
            }

            onRemoteVideoTrack = { track ->
                // Notify UI about remote video
            }
        }

        peerConnections[keyHex] = rtcCall
        rtcCall.createPeerConnection()

        // Add to participants list
        _callState.update { state ->
            val participant = GroupCallState.CallParticipant(
                publicKey = contact.publicKey,
                name = contact.name,
                connectionState = GroupCallState.CallParticipant.ConnectionState.CONNECTING
            )
            state.copy(participants = state.participants + participant)
        }
    }

    /**
     * Handle connection state changes
     */
    private fun handleConnectionStateChange(
        publicKey: ByteArray,
        state: PeerConnection.IceConnectionState
    ) {
        val keyHex = publicKey.toHexString()
        logD("handleConnectionStateChange: ${keyHex.take(8)} -> $state")

        val connectionState = when (state) {
            PeerConnection.IceConnectionState.CONNECTED,
            PeerConnection.IceConnectionState.COMPLETED ->
                GroupCallState.CallParticipant.ConnectionState.CONNECTED

            PeerConnection.IceConnectionState.DISCONNECTED ->
                GroupCallState.CallParticipant.ConnectionState.RECONNECTING

            PeerConnection.IceConnectionState.FAILED ->
                GroupCallState.CallParticipant.ConnectionState.FAILED

            else -> GroupCallState.CallParticipant.ConnectionState.CONNECTING
        }

        _callState.update { callState ->
            callState.copy(
                participants = callState.participants.map { p ->
                    if (p.publicKey.contentEquals(publicKey)) {
                        p.copy(connectionState = connectionState)
                    } else p
                },
                status = if (callState.connectedParticipants.isNotEmpty()) {
                    GroupCallState.Status.CONNECTED
                } else {
                    callState.status
                }
            )
        }

        if (connectionState == GroupCallState.CallParticipant.ConnectionState.CONNECTED) {
            _callState.value.participants.find { it.publicKey.contentEquals(publicKey) }?.let {
                onParticipantJoined?.invoke(it)
            }
        }
    }

    /**
     * Handle participant leaving
     */
    private fun handleParticipantLeft(publicKey: ByteArray) {
        val keyHex = publicKey.toHexString()
        logI("handleParticipantLeft: ${keyHex.take(8)}")

        peerConnections.remove(keyHex)?.cleanup()

        _callState.update { state ->
            state.copy(
                participants = state.participants.filter {
                    !it.publicKey.contentEquals(publicKey)
                }
            )
        }

        onParticipantLeft?.invoke(publicKey)

        // Check if SFU left
        val topology = _callState.value.topology
        if (topology is GroupCallState.CallTopology.DistributedSFU &&
            topology.sfuNodeId.contentEquals(publicKey)
        ) {
            scope.launch { electSFUNode() }
        }
    }

    // =========================================================================
    // SIGNALING
    // =========================================================================

    /**
     * Send offer to participant
     */
    private suspend fun sendOffer(contact: Contact, sdp: SessionDescription) {
        val socket = connector.connect(contact) ?: return

        val message = mapOf(
            "action" to "group_call",
            "callId" to _callState.value.callId,
            "groupId" to _callState.value.groupId.toHexString(),
            "offer" to sdp.description,
            "type" to sdp.type.canonicalForm()
        )

        sendSignalingMessage(socket, message)
        socket.close()
    }

    /**
     * Handle incoming answer
     */
    suspend fun handleAnswer(fromPublicKey: ByteArray, answer: String) {
        val keyHex = fromPublicKey.toHexString()
        peerConnections[keyHex]?.handleAnswer(answer)
    }

    /**
     * Send signaling message
     */
    private suspend fun sendSignalingMessage(socket: Socket, message: Map<String, Any>) {
        // Encrypt and send via existing signaling protocol
        // Implementation would use MeshNetworkManager's message format
    }

    /**
     * Broadcast message to all participants
     */
    private suspend fun broadcastMessage(type: String, data: Map<String, Any>) {
        val message = mapOf("type" to type) + data
        peerConnections.values.forEach { connection ->
            // Send via data channel
        }
    }

    /**
     * Broadcast leave message
     */
    private suspend fun broadcastLeaveMessage() {
        broadcastMessage("PARTICIPANT_LEAVE", mapOf(
            "publicKey" to ownPublicKey.toHexString()
        ))
    }

    /**
     * Request participant list from existing member
     */
    private suspend fun requestParticipantList(contact: Contact) {
        // Request list via signaling
    }

    // =========================================================================
    // AUDIO ANALYSIS
    // =========================================================================

    /**
     * Detect active speaker based on audio levels
     */
    private fun detectActiveSpeaker() {
        val speaking = _callState.value.participants
            .filter { it.isSpeaking }
            .maxByOrNull { it.speakingLevel }

        val previousSpeaker = _callState.value.activeSpeaker

        if (speaking?.publicKey?.contentEquals(previousSpeaker ?: ByteArray(0)) != true) {
            _callState.update { it.copy(activeSpeaker = speaking?.publicKey) }
            onActiveSpeakerChanged?.invoke(speaking?.publicKey)
        }
    }

    /**
     * Update network quality metrics
     */
    private fun updateNetworkQuality() {
        // Aggregate quality from all connections
        val qualities = peerConnections.values.mapNotNull { connection ->
            // Get stats from WebRTC
            null // Placeholder
        }

        // Calculate overall quality
        // Update state
    }

    // =========================================================================
    // UTILITIES
    // =========================================================================

    /**
     * Get local device criteria for SFU election
     */
    private fun getLocalElectionCriteria(): SFUElectionCriteria {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) / 100f
        val isPluggedIn = batteryManager.isCharging

        val runtime = Runtime.getRuntime()
        val memoryAvailable = runtime.maxMemory() - runtime.totalMemory() + runtime.freeMemory()

        return SFUElectionCriteria(
            cpuScore = 0.7f, // Would need actual CPU measurement
            memoryAvailable = memoryAvailable,
            bandwidthUp = 10_000_000, // 10 Mbps placeholder
            bandwidthDown = 50_000_000, // 50 Mbps placeholder
            batteryLevel = batteryLevel,
            isPluggedIn = isPluggedIn,
            networkLatency = 50 // ms placeholder
        )
    }

    /**
     * Convert ByteArray to hex string
     */
    private fun ByteArray.toHexString(): String =
        joinToString("") { "%02x".format(it) }

    /**
     * Cleanup resources
     */
    fun cleanup() {
        scope.cancel()
        peerConnections.values.forEach { it.cleanup() }
        peerConnections.clear()
    }
}
