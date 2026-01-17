/*
 * Mesh Rider Wave - Group Call State Model
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 *
 * State management for multi-party group calls
 * Supports P2P mesh, SFU, and hybrid topologies
 */

package com.doodlelabs.meshriderwave.domain.model.group

/**
 * Represents the state of a group call
 *
 * Topology selection:
 * - 2-4 participants: P2P Mesh (no server)
 * - 5-20 participants: Distributed SFU (elected node)
 * - 20+ participants: Multi-SFU cascade
 */
data class GroupCallState(
    val callId: String = "",
    val groupId: ByteArray = ByteArray(0),
    val status: Status = Status.IDLE,
    val topology: CallTopology = CallTopology.P2PMesh,
    val participants: List<CallParticipant> = emptyList(),
    val activeSpeaker: ByteArray? = null,
    val localState: LocalParticipantState = LocalParticipantState(),
    val startTime: Long? = null,
    val networkQuality: NetworkQuality = NetworkQuality.GOOD,
    val errorMessage: String? = null
) {
    /**
     * Call status
     */
    enum class Status {
        IDLE,           // No active call
        INITIATING,     // Creating call
        RINGING,        // Waiting for participants
        CONNECTING,     // Establishing connections
        CONNECTED,      // Call active
        RECONNECTING,   // Recovering from disconnect
        ON_HOLD,        // Call paused
        ENDED,          // Call finished
        ERROR           // Call failed
    }

    /**
     * Network topology for the call
     */
    sealed class CallTopology {
        /**
         * Full mesh - each peer connects to all others
         * Best for 2-4 participants
         */
        object P2PMesh : CallTopology()

        /**
         * Single SFU node elected from participants
         * Best for 5-20 participants
         */
        data class DistributedSFU(
            val sfuNodeId: ByteArray,     // Public key of SFU node
            val sfuAddress: String,        // Network address
            val backupNodes: List<ByteArray> = emptyList()
        ) : CallTopology() {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (other !is DistributedSFU) return false
                return sfuNodeId.contentEquals(other.sfuNodeId)
            }
            override fun hashCode(): Int = sfuNodeId.contentHashCode()
        }

        /**
         * Multiple SFU nodes in cascade
         * Best for 20+ participants
         */
        data class MultiSFU(
            val sfuNodes: List<SFUNode>
        ) : CallTopology()
    }

    /**
     * SFU node information
     */
    data class SFUNode(
        val nodeId: ByteArray,
        val address: String,
        val region: String,
        val participants: List<ByteArray>,
        val load: Float  // 0.0 - 1.0
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is SFUNode) return false
            return nodeId.contentEquals(other.nodeId)
        }
        override fun hashCode(): Int = nodeId.contentHashCode()
    }

    /**
     * Individual participant state
     */
    data class CallParticipant(
        val publicKey: ByteArray,
        val name: String,
        val connectionState: ConnectionState = ConnectionState.CONNECTING,
        val audioEnabled: Boolean = true,
        val videoEnabled: Boolean = false,
        val screenSharing: Boolean = false,
        val isSpeaking: Boolean = false,
        val speakingLevel: Float = 0f,    // Audio level 0.0 - 1.0
        val networkQuality: NetworkQuality = NetworkQuality.UNKNOWN,
        val videoTrackId: String? = null,
        val raisedHand: Boolean = false,
        val joinedAt: Long = System.currentTimeMillis()
    ) {
        enum class ConnectionState {
            CONNECTING,
            CONNECTED,
            RECONNECTING,
            DISCONNECTED,
            FAILED
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is CallParticipant) return false
            return publicKey.contentEquals(other.publicKey)
        }
        override fun hashCode(): Int = publicKey.contentHashCode()
    }

    /**
     * Local participant state (microphone, camera, etc.)
     */
    data class LocalParticipantState(
        val isMicEnabled: Boolean = true,
        val isCameraEnabled: Boolean = false,
        val isScreenSharing: Boolean = false,
        val isSpeakerEnabled: Boolean = true,
        val isFrontCamera: Boolean = true,
        val selectedVideoQuality: VideoQuality = VideoQuality.HD,
        val selectedAudioMode: AudioMode = AudioMode.VOICE
    )

    /**
     * Video quality presets
     */
    enum class VideoQuality(val width: Int, val height: Int, val fps: Int, val bitrate: Int) {
        LOW(320, 240, 15, 150_000),
        SD(640, 480, 25, 500_000),
        HD(1280, 720, 30, 1_500_000),
        FULL_HD(1920, 1080, 30, 3_000_000)
    }

    /**
     * Audio mode presets
     */
    enum class AudioMode {
        VOICE,      // Optimized for speech (narrow bandwidth)
        MUSIC,      // Full spectrum (wider bandwidth)
        LOW_LATENCY // Minimal processing for real-time
    }

    /**
     * Network quality indicator
     */
    enum class NetworkQuality {
        UNKNOWN,
        POOR,       // >300ms latency or >10% packet loss
        FAIR,       // 150-300ms or 5-10% loss
        GOOD,       // 50-150ms or 1-5% loss
        EXCELLENT   // <50ms and <1% loss
    }

    // Computed properties

    val isActive: Boolean
        get() = status in listOf(Status.CONNECTING, Status.CONNECTED, Status.RECONNECTING)

    val isConnected: Boolean
        get() = status == Status.CONNECTED

    val participantCount: Int
        get() = participants.size

    val connectedParticipants: List<CallParticipant>
        get() = participants.filter { it.connectionState == CallParticipant.ConnectionState.CONNECTED }

    val videoParticipants: List<CallParticipant>
        get() = participants.filter { it.videoEnabled }

    val callDuration: Long
        get() = startTime?.let { System.currentTimeMillis() - it } ?: 0

    val durationFormatted: String
        get() {
            val seconds = callDuration / 1000
            val hours = seconds / 3600
            val minutes = (seconds % 3600) / 60
            val secs = seconds % 60
            return if (hours > 0) {
                "%d:%02d:%02d".format(hours, minutes, secs)
            } else {
                "%02d:%02d".format(minutes, secs)
            }
        }

    /**
     * Get display name for active speaker
     */
    fun getActiveSpeakerName(): String? =
        activeSpeaker?.let { key ->
            participants.find { it.publicKey.contentEquals(key) }?.name
        }

    /**
     * Check if topology upgrade is recommended
     */
    fun shouldUpgradeTopology(): Boolean = when (topology) {
        is CallTopology.P2PMesh -> participantCount > 4
        is CallTopology.DistributedSFU -> participantCount > 20
        is CallTopology.MultiSFU -> false
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GroupCallState) return false
        return callId == other.callId && groupId.contentEquals(other.groupId)
    }

    override fun hashCode(): Int = 31 * callId.hashCode() + groupId.contentHashCode()
}

/**
 * SFU election criteria
 */
data class SFUElectionCriteria(
    val cpuScore: Float,           // 0-1, higher is better
    val memoryAvailable: Long,     // bytes
    val bandwidthUp: Long,         // bps
    val bandwidthDown: Long,       // bps
    val batteryLevel: Float,       // 0-1
    val isPluggedIn: Boolean,
    val networkLatency: Long       // ms to other peers
) {
    /**
     * Calculate overall score for SFU election
     * Higher score = better SFU candidate
     */
    fun calculateScore(): Float {
        var score = 0f

        // CPU weight: 25%
        score += cpuScore * 0.25f

        // Memory weight: 15%
        score += (memoryAvailable.toFloat() / (4L * 1024 * 1024 * 1024)).coerceAtMost(1f) * 0.15f

        // Bandwidth weight: 30% (up is more important for SFU)
        val bwScore = ((bandwidthUp / 10_000_000f).coerceAtMost(1f) * 0.7f +
                (bandwidthDown / 10_000_000f).coerceAtMost(1f) * 0.3f)
        score += bwScore * 0.30f

        // Battery weight: 20%
        val batteryScore = if (isPluggedIn) 1f else batteryLevel
        score += batteryScore * 0.20f

        // Latency weight: 10% (lower is better)
        score += ((500 - networkLatency) / 500f).coerceIn(0f, 1f) * 0.10f

        return score
    }
}
