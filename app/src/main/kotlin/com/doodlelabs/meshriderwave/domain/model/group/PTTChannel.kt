/*
 * Mesh Rider Wave - Push-to-Talk Channel Model
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 *
 * Walkie-talkie style half-duplex voice communication
 * Supports priority override for emergency broadcasts
 */

package com.doodlelabs.meshriderwave.domain.model.group

import android.util.Base64

/**
 * Push-to-Talk channel for walkie-talkie communication
 *
 * Features:
 * - Half-duplex voice (one speaker at a time)
 * - Priority-based floor control
 * - Emergency broadcast override
 * - Hardware PTT button support
 */
data class PTTChannel(
    val channelId: ByteArray,
    val name: String,
    val description: String = "",
    val frequency: String = "",           // Virtual frequency display (e.g., "CH-01")
    val groupId: ByteArray? = null,       // Associated group (null = standalone channel)
    val members: List<PTTMember> = emptyList(),
    val priority: ChannelPriority = ChannelPriority.NORMAL,
    val settings: PTTSettings = PTTSettings(),
    val createdAt: Long = System.currentTimeMillis(),
    val createdBy: ByteArray = ByteArray(0)
) {
    /**
     * Short ID for display
     */
    val shortId: String
        get() = channelId.take(4).joinToString("") { "%02X".format(it) }

    /**
     * Display frequency (e.g., "CH-01" or custom)
     */
    val displayFrequency: String
        get() = frequency.ifEmpty { "CH-${shortId.take(2)}" }

    /**
     * Export channel for sharing
     */
    fun toShareData(): String {
        val idBase64 = Base64.encodeToString(channelId, Base64.NO_WRAP)
        return "meshrider://ptt/$idBase64/$name"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PTTChannel) return false
        return channelId.contentEquals(other.channelId)
    }

    override fun hashCode(): Int = channelId.contentHashCode()

    companion object {
        const val MAX_MEMBERS = 6000  // Like Zello channels
        const val MAX_NAME_LENGTH = 50

        /**
         * Parse channel from share URL
         */
        fun fromShareData(data: String): PTTChannelInvite? {
            return try {
                val parts = data.removePrefix("meshrider://ptt/").split("/")
                if (parts.size >= 2) {
                    PTTChannelInvite(
                        channelId = Base64.decode(parts[0], Base64.NO_WRAP),
                        name = parts[1]
                    )
                } else null
            } catch (e: Exception) {
                null
            }
        }
    }
}

/**
 * Channel member
 */
data class PTTMember(
    val publicKey: ByteArray,
    val name: String,
    val role: PTTRole = PTTRole.MEMBER,
    val priority: Int = 0,              // Higher = can interrupt lower
    val canTransmit: Boolean = true,    // Listen-only if false
    val isMuted: Boolean = false,
    val joinedAt: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PTTMember) return false
        return publicKey.contentEquals(other.publicKey)
    }
    override fun hashCode(): Int = publicKey.contentHashCode()
}

/**
 * PTT member roles
 */
enum class PTTRole {
    OWNER,          // Channel owner
    DISPATCHER,     // Can always transmit, override others
    MEMBER,         // Standard member
    LISTENER        // Listen-only
}

/**
 * Channel priority for emergency handling
 */
enum class ChannelPriority(val level: Int) {
    LOW(0),         // Background channel
    NORMAL(1),      // Standard operation
    HIGH(2),        // Priority channel
    EMERGENCY(3);   // SOS/Emergency (always highest)

    fun canInterrupt(other: ChannelPriority): Boolean = this.level > other.level
}

/**
 * PTT channel settings
 */
data class PTTSettings(
    val transmitTimeout: Long = 60_000,     // Max transmission time (ms)
    val cooldownPeriod: Long = 500,         // Time between transmissions (ms)
    val allowEmergencyOverride: Boolean = true,
    val playToneOnTransmit: Boolean = true,
    val playToneOnReceive: Boolean = true,
    val recordTransmissions: Boolean = false,
    val encryptionEnabled: Boolean = true,
    val voxEnabled: Boolean = false,        // Voice-activated transmission
    val voxThreshold: Float = 0.3f,         // VOX sensitivity (0-1)
    val audioCodec: AudioCodec = AudioCodec.OPUS
)

/**
 * Audio codec options for PTT
 */
enum class AudioCodec(val bitrate: Int, val description: String) {
    CODEC2_700(700, "Ultra-low bandwidth (700 bps)"),
    CODEC2_1200(1200, "Very low bandwidth (1.2 kbps)"),
    CODEC2_3200(3200, "Low bandwidth (3.2 kbps)"),
    OPUS_VOICE(6000, "Standard voice (6 kbps)"),
    OPUS(24000, "High quality (24 kbps)")
}

/**
 * PTT transmission state
 */
data class PTTTransmitState(
    val channelId: ByteArray,
    val status: Status = Status.IDLE,
    val currentSpeaker: PTTMember? = null,
    val queuePosition: Int = 0,
    val transmissionStart: Long? = null,
    val remainingTime: Long? = null
) {
    enum class Status {
        IDLE,           // No active transmission
        REQUESTING,     // Requesting floor from peers
        TRANSMITTING,   // Local user is speaking
        RECEIVING,      // Receiving from another user
        QUEUED,         // Waiting for floor
        COOLDOWN,       // Brief pause between transmissions
        BLOCKED         // Higher priority transmission in progress
    }

    val isTransmitting: Boolean
        get() = status == Status.TRANSMITTING

    val isReceiving: Boolean
        get() = status == Status.RECEIVING

    val canRequestFloor: Boolean
        get() = status in listOf(Status.IDLE, Status.COOLDOWN)

    /**
     * Duration of current transmission
     */
    val transmissionDuration: Long
        get() = transmissionStart?.let { System.currentTimeMillis() - it } ?: 0

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PTTTransmitState) return false
        return channelId.contentEquals(other.channelId)
    }
    override fun hashCode(): Int = channelId.contentHashCode()
}

/**
 * PTT channel invitation
 */
data class PTTChannelInvite(
    val channelId: ByteArray,
    val name: String,
    val invitedBy: ByteArray? = null,
    val expiresAt: Long? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PTTChannelInvite) return false
        return channelId.contentEquals(other.channelId)
    }
    override fun hashCode(): Int = channelId.contentHashCode()
}

/**
 * PTT event for history tracking
 */
data class PTTEvent(
    val eventId: String,
    val channelId: ByteArray,
    val type: EventType,
    val speaker: ByteArray,
    val speakerName: String,
    val timestamp: Long,
    val duration: Long = 0,
    val audioData: ByteArray? = null  // Optional recorded audio
) {
    enum class EventType {
        TRANSMISSION,       // Normal voice transmission
        EMERGENCY,          // Emergency broadcast
        CHANNEL_JOIN,       // User joined channel
        CHANNEL_LEAVE,      // User left channel
        PRIORITY_OVERRIDE   // Higher priority interrupted
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PTTEvent) return false
        return eventId == other.eventId
    }
    override fun hashCode(): Int = eventId.hashCode()
}
