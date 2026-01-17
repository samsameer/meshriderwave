/*
 * Mesh Rider Wave - Group Domain Model
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 *
 * Military-grade group communication with MLS encryption
 * Reference: IETF RFC 9420 (Messaging Layer Security)
 */

package com.doodlelabs.meshriderwave.domain.model.group

import android.util.Base64
import com.doodlelabs.meshriderwave.domain.model.Contact

/**
 * Represents a secure communication group
 *
 * Groups support:
 * - End-to-end encryption using MLS protocol
 * - Up to 50,000 members (MLS specification)
 * - Forward secrecy and post-compromise security
 * - Async member addition via KeyPackages
 */
data class Group(
    val groupId: ByteArray,               // 32-byte unique identifier
    val name: String,
    val description: String = "",
    val createdBy: ByteArray,             // Creator's public key
    val createdAt: Long,
    val members: List<GroupMember> = emptyList(),
    val epoch: Long = 0,                  // MLS epoch counter
    val groupState: GroupState = GroupState.ACTIVE,
    val encryptionEnabled: Boolean = true,
    val avatar: ByteArray? = null,        // Optional group avatar
    val settings: GroupSettings = GroupSettings()
) {
    /**
     * Short ID for display (first 8 hex chars)
     */
    val shortId: String
        get() = groupId.take(4).joinToString("") { "%02X".format(it) }

    /**
     * Full hex ID
     */
    val hexId: String
        get() = groupId.joinToString("") { "%02x".format(it) }

    /**
     * Member count
     */
    val memberCount: Int
        get() = members.size

    /**
     * Check if user is admin
     */
    fun isAdmin(publicKey: ByteArray): Boolean =
        members.find { it.publicKey.contentEquals(publicKey) }?.role == MemberRole.ADMIN

    /**
     * Check if user is member
     */
    fun isMember(publicKey: ByteArray): Boolean =
        members.any { it.publicKey.contentEquals(publicKey) }

    /**
     * Get member by public key
     */
    fun getMember(publicKey: ByteArray): GroupMember? =
        members.find { it.publicKey.contentEquals(publicKey) }

    /**
     * Export group for QR code sharing
     */
    fun toInviteData(): String {
        val idBase64 = Base64.encodeToString(groupId, Base64.NO_WRAP)
        val creatorBase64 = Base64.encodeToString(createdBy, Base64.NO_WRAP)
        return "meshrider://group/$idBase64/$name/$creatorBase64"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Group) return false
        return groupId.contentEquals(other.groupId)
    }

    override fun hashCode(): Int = groupId.contentHashCode()

    companion object {
        const val MAX_MEMBERS = 50000  // MLS specification limit
        const val MAX_NAME_LENGTH = 100
        const val MAX_DESCRIPTION_LENGTH = 500

        /**
         * Parse group from invite URL
         */
        fun fromInviteData(data: String): GroupInvite? {
            return try {
                val parts = data.removePrefix("meshrider://group/").split("/")
                if (parts.size >= 3) {
                    GroupInvite(
                        groupId = Base64.decode(parts[0], Base64.NO_WRAP),
                        name = parts[1],
                        creatorKey = Base64.decode(parts[2], Base64.NO_WRAP)
                    )
                } else null
            } catch (e: Exception) {
                null
            }
        }
    }
}

/**
 * Group member with role and status
 */
data class GroupMember(
    val publicKey: ByteArray,
    val name: String,
    val role: MemberRole = MemberRole.MEMBER,
    val joinedAt: Long = System.currentTimeMillis(),
    val lastSeenAt: Long? = null,
    val status: MemberStatus = MemberStatus.OFFLINE,
    val keyPackage: ByteArray? = null     // MLS KeyPackage for async add
) {
    /**
     * Convert to Contact for 1:1 calls
     */
    fun toContact(): Contact = Contact(
        publicKey = publicKey,
        name = name,
        createdAt = joinedAt,
        lastSeenAt = lastSeenAt
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GroupMember) return false
        return publicKey.contentEquals(other.publicKey)
    }

    override fun hashCode(): Int = publicKey.contentHashCode()
}

/**
 * Member roles with permissions
 */
enum class MemberRole {
    OWNER,      // Full control, can delete group
    ADMIN,      // Can add/remove members, change settings
    MODERATOR,  // Can mute members, manage messages
    MEMBER      // Standard participant
}

/**
 * Member online status
 */
enum class MemberStatus {
    ONLINE,
    AWAY,
    BUSY,
    OFFLINE,
    IN_CALL
}

/**
 * Group lifecycle state
 */
enum class GroupState {
    CREATING,   // Initial creation
    ACTIVE,     // Normal operation
    SUSPENDED,  // Temporarily disabled
    ARCHIVED,   // Read-only historical
    DELETED     // Marked for deletion
}

/**
 * Group settings
 */
data class GroupSettings(
    val allowMemberInvites: Boolean = false,  // Only admins can invite by default
    val allowVoiceCalls: Boolean = true,
    val allowVideoCalls: Boolean = true,
    val allowPTT: Boolean = true,
    val pttPriority: PTTPriority = PTTPriority.NORMAL,
    val maxCallParticipants: Int = 20,
    val messageRetention: MessageRetention = MessageRetention.FOREVER,
    val requireApproval: Boolean = false,     // Admin approval for new members
    val encryptionLevel: EncryptionLevel = EncryptionLevel.MLS
)

/**
 * PTT channel priority for interruption
 */
enum class PTTPriority {
    LOW,        // Can be interrupted by any priority
    NORMAL,     // Standard priority
    HIGH,       // Can interrupt NORMAL and LOW
    EMERGENCY   // Can interrupt all, reserved for SOS
}

/**
 * Message retention policy
 */
enum class MessageRetention {
    HOURS_24,
    DAYS_7,
    DAYS_30,
    DAYS_90,
    FOREVER
}

/**
 * Encryption level options
 */
enum class EncryptionLevel {
    NONE,           // Unencrypted (not recommended)
    STANDARD,       // X25519 pairwise encryption
    MLS,            // Full MLS group encryption
    MLS_PQ          // MLS with post-quantum (ML-KEM-768)
}

/**
 * Group invitation data
 */
data class GroupInvite(
    val groupId: ByteArray,
    val name: String,
    val creatorKey: ByteArray,
    val invitedBy: ByteArray? = null,
    val expiresAt: Long? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GroupInvite) return false
        return groupId.contentEquals(other.groupId)
    }

    override fun hashCode(): Int = groupId.contentHashCode()
}
