/*
 * Mesh Rider Wave - Group Repository Interface
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 *
 * Repository for managing groups and PTT channels with persistence
 */

package com.doodlelabs.meshriderwave.domain.repository

import com.doodlelabs.meshriderwave.domain.model.group.Group
import com.doodlelabs.meshriderwave.domain.model.group.GroupInvite
import com.doodlelabs.meshriderwave.domain.model.group.GroupMember
import com.doodlelabs.meshriderwave.domain.model.group.GroupSettings
import com.doodlelabs.meshriderwave.domain.model.group.GroupState
import com.doodlelabs.meshriderwave.domain.model.group.MemberRole
import com.doodlelabs.meshriderwave.domain.model.group.MemberStatus
import com.doodlelabs.meshriderwave.domain.model.group.PTTChannel
import com.doodlelabs.meshriderwave.domain.model.group.PTTMember
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for Group operations
 *
 * Provides:
 * - CRUD operations for groups
 * - Member management
 * - Group settings persistence
 * - Invite handling
 */
interface GroupRepository {

    // ========== Group Flows ==========

    /**
     * Flow of all groups the user is a member of
     */
    val groups: Flow<List<Group>>

    /**
     * Flow of active groups (not archived/deleted)
     */
    val activeGroups: Flow<List<Group>>

    /**
     * Flow of pending invites
     */
    val pendingInvites: Flow<List<GroupInvite>>

    // ========== Group CRUD ==========

    /**
     * Create a new group
     */
    suspend fun createGroup(
        name: String,
        description: String = "",
        creatorPublicKey: ByteArray,
        settings: GroupSettings = GroupSettings()
    ): Result<Group>

    /**
     * Get group by ID
     */
    suspend fun getGroup(groupId: ByteArray): Group?

    /**
     * Update group details
     */
    suspend fun updateGroup(group: Group): Result<Unit>

    /**
     * Delete group (only owner can delete)
     */
    suspend fun deleteGroup(groupId: ByteArray): Result<Unit>

    /**
     * Archive group (read-only mode)
     */
    suspend fun archiveGroup(groupId: ByteArray): Result<Unit>

    /**
     * Update group settings
     */
    suspend fun updateGroupSettings(groupId: ByteArray, settings: GroupSettings): Result<Unit>

    // ========== Member Management ==========

    /**
     * Add member to group
     */
    suspend fun addMember(
        groupId: ByteArray,
        member: GroupMember
    ): Result<Unit>

    /**
     * Remove member from group
     */
    suspend fun removeMember(
        groupId: ByteArray,
        memberPublicKey: ByteArray
    ): Result<Unit>

    /**
     * Update member role
     */
    suspend fun updateMemberRole(
        groupId: ByteArray,
        memberPublicKey: ByteArray,
        newRole: MemberRole
    ): Result<Unit>

    /**
     * Update member status (online/offline/away)
     */
    suspend fun updateMemberStatus(
        groupId: ByteArray,
        memberPublicKey: ByteArray,
        status: MemberStatus
    ): Result<Unit>

    /**
     * Get all members of a group
     */
    suspend fun getMembers(groupId: ByteArray): List<GroupMember>

    // ========== Invite Management ==========

    /**
     * Create an invite for a group
     */
    suspend fun createInvite(
        groupId: ByteArray,
        invitedBy: ByteArray,
        expiresIn: Long = 7 * 24 * 60 * 60 * 1000  // 7 days default
    ): Result<GroupInvite>

    /**
     * Accept a group invite
     */
    suspend fun acceptInvite(invite: GroupInvite, joinerPublicKey: ByteArray, joinerName: String): Result<Group>

    /**
     * Decline a group invite
     */
    suspend fun declineInvite(invite: GroupInvite): Result<Unit>

    /**
     * Add received invite to pending list
     */
    suspend fun addPendingInvite(invite: GroupInvite): Result<Unit>

    // ========== Epoch Management (MLS) ==========

    /**
     * Increment epoch after group change
     */
    suspend fun incrementEpoch(groupId: ByteArray): Result<Long>

    /**
     * Get current epoch
     */
    suspend fun getCurrentEpoch(groupId: ByteArray): Long

    // ========== Sync ==========

    /**
     * Force refresh groups from storage
     */
    suspend fun refresh()

    /**
     * Check if user is member of group
     */
    suspend fun isMember(groupId: ByteArray, publicKey: ByteArray): Boolean

    /**
     * Check if user is admin of group
     */
    suspend fun isAdmin(groupId: ByteArray, publicKey: ByteArray): Boolean
}

/**
 * Repository interface for PTT Channel operations
 */
interface PTTChannelRepository {

    // ========== Channel Flows ==========

    /**
     * Flow of all PTT channels
     */
    val channels: Flow<List<PTTChannel>>

    /**
     * Flow of joined channels
     */
    val joinedChannels: Flow<List<PTTChannel>>

    // ========== Channel CRUD ==========

    /**
     * Create a new PTT channel
     */
    suspend fun createChannel(
        name: String,
        description: String = "",
        creatorPublicKey: ByteArray,
        groupId: ByteArray? = null
    ): Result<PTTChannel>

    /**
     * Get channel by ID
     */
    suspend fun getChannel(channelId: ByteArray): PTTChannel?

    /**
     * Update channel
     */
    suspend fun updateChannel(channel: PTTChannel): Result<Unit>

    /**
     * Delete channel
     */
    suspend fun deleteChannel(channelId: ByteArray): Result<Unit>

    /**
     * Add or update a channel (for importing discovered channels)
     * Jan 2026: Added for network channel discovery
     */
    suspend fun addOrUpdateChannel(channel: PTTChannel): Result<Unit>

    // ========== Membership ==========

    /**
     * Join a channel
     */
    suspend fun joinChannel(channelId: ByteArray, member: PTTMember): Result<Unit>

    /**
     * Leave a channel
     */
    suspend fun leaveChannel(channelId: ByteArray, memberPublicKey: ByteArray): Result<Unit>

    /**
     * Get channel members
     */
    suspend fun getMembers(channelId: ByteArray): List<PTTMember>

    // ========== Sync ==========

    /**
     * Force refresh channels from storage
     */
    suspend fun refresh()
}
