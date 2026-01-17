/*
 * Mesh Rider Wave - Group Repository Implementation
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 *
 * JSON-based persistence for groups with thread-safe operations
 * Follows Meshenger pattern for simplicity and reliability
 */

package com.doodlelabs.meshriderwave.data.repository

import android.content.Context
import android.util.Base64
import android.util.Log
import com.doodlelabs.meshriderwave.domain.model.group.EncryptionLevel
import com.doodlelabs.meshriderwave.domain.model.group.Group
import com.doodlelabs.meshriderwave.domain.model.group.GroupInvite
import com.doodlelabs.meshriderwave.domain.model.group.GroupMember
import com.doodlelabs.meshriderwave.domain.model.group.GroupSettings
import com.doodlelabs.meshriderwave.domain.model.group.GroupState
import com.doodlelabs.meshriderwave.domain.model.group.MemberRole
import com.doodlelabs.meshriderwave.domain.model.group.MemberStatus
import com.doodlelabs.meshriderwave.domain.model.group.MessageRetention
import com.doodlelabs.meshriderwave.domain.model.group.PTTPriority
import com.doodlelabs.meshriderwave.domain.repository.GroupRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GroupRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : GroupRepository {

    companion object {
        private const val TAG = "MeshRider:GroupRepo"
        private const val GROUPS_FILE = "groups.json"
        private const val INVITES_FILE = "group_invites.json"
    }

    private val mutex = Mutex()
    private val _groups = MutableStateFlow<List<Group>>(emptyList())
    private val _invites = MutableStateFlow<List<GroupInvite>>(emptyList())

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    private val groupsFile: File get() = File(context.filesDir, GROUPS_FILE)
    private val invitesFile: File get() = File(context.filesDir, INVITES_FILE)
    private val secureRandom = SecureRandom()

    // ========== Flows ==========

    override val groups: Flow<List<Group>> = _groups

    override val activeGroups: Flow<List<Group>> = _groups.map { groups ->
        groups.filter { it.groupState == GroupState.ACTIVE }
    }

    override val pendingInvites: Flow<List<GroupInvite>> = _invites

    init {
        loadFromDisk()
    }

    // ========== Group CRUD ==========

    override suspend fun createGroup(
        name: String,
        description: String,
        creatorPublicKey: ByteArray,
        settings: GroupSettings
    ): Result<Group> = mutex.withLock {
        try {
            // Validate name
            if (name.isBlank() || name.length > Group.MAX_NAME_LENGTH) {
                return Result.failure(IllegalArgumentException("Invalid group name"))
            }

            // Generate unique group ID (32 bytes)
            val groupId = ByteArray(32)
            secureRandom.nextBytes(groupId)

            // Create creator as owner
            val owner = GroupMember(
                publicKey = creatorPublicKey,
                name = "You",  // Will be updated with actual name
                role = MemberRole.OWNER,
                joinedAt = System.currentTimeMillis(),
                status = MemberStatus.ONLINE
            )

            val group = Group(
                groupId = groupId,
                name = name.trim(),
                description = description.trim().take(Group.MAX_DESCRIPTION_LENGTH),
                createdBy = creatorPublicKey,
                createdAt = System.currentTimeMillis(),
                members = listOf(owner),
                epoch = 0L,
                groupState = GroupState.ACTIVE,
                encryptionEnabled = settings.encryptionLevel != EncryptionLevel.NONE,
                settings = settings
            )

            val current = _groups.value.toMutableList()
            current.add(group)
            _groups.value = current
            saveToDisk()

            Log.d(TAG, "Created group: ${group.shortId} - $name")
            Result.success(group)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create group", e)
            Result.failure(e)
        }
    }

    override suspend fun getGroup(groupId: ByteArray): Group? {
        return _groups.value.find { it.groupId.contentEquals(groupId) }
    }

    override suspend fun updateGroup(group: Group): Result<Unit> = mutex.withLock {
        try {
            val current = _groups.value.toMutableList()
            val index = current.indexOfFirst { it.groupId.contentEquals(group.groupId) }

            if (index < 0) {
                return Result.failure(IllegalArgumentException("Group not found"))
            }

            current[index] = group
            _groups.value = current
            saveToDisk()

            Log.d(TAG, "Updated group: ${group.shortId}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update group", e)
            Result.failure(e)
        }
    }

    override suspend fun deleteGroup(groupId: ByteArray): Result<Unit> = mutex.withLock {
        try {
            val current = _groups.value.toMutableList()
            val index = current.indexOfFirst { it.groupId.contentEquals(groupId) }

            if (index < 0) {
                return Result.failure(IllegalArgumentException("Group not found"))
            }

            // Mark as deleted instead of removing (for audit trail)
            current[index] = current[index].copy(groupState = GroupState.DELETED)
            _groups.value = current
            saveToDisk()

            Log.d(TAG, "Deleted group: ${groupId.take(4).toByteArray().toHexString()}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete group", e)
            Result.failure(e)
        }
    }

    override suspend fun archiveGroup(groupId: ByteArray): Result<Unit> = mutex.withLock {
        try {
            val current = _groups.value.toMutableList()
            val index = current.indexOfFirst { it.groupId.contentEquals(groupId) }

            if (index < 0) {
                return Result.failure(IllegalArgumentException("Group not found"))
            }

            current[index] = current[index].copy(groupState = GroupState.ARCHIVED)
            _groups.value = current
            saveToDisk()

            Log.d(TAG, "Archived group: ${groupId.take(4).toByteArray().toHexString()}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to archive group", e)
            Result.failure(e)
        }
    }

    override suspend fun updateGroupSettings(
        groupId: ByteArray,
        settings: GroupSettings
    ): Result<Unit> = mutex.withLock {
        try {
            val current = _groups.value.toMutableList()
            val index = current.indexOfFirst { it.groupId.contentEquals(groupId) }

            if (index < 0) {
                return Result.failure(IllegalArgumentException("Group not found"))
            }

            current[index] = current[index].copy(settings = settings)
            _groups.value = current
            saveToDisk()

            Log.d(TAG, "Updated settings for group: ${groupId.take(4).toByteArray().toHexString()}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update group settings", e)
            Result.failure(e)
        }
    }

    // ========== Member Management ==========

    override suspend fun addMember(
        groupId: ByteArray,
        member: GroupMember
    ): Result<Unit> = mutex.withLock {
        try {
            val current = _groups.value.toMutableList()
            val index = current.indexOfFirst { it.groupId.contentEquals(groupId) }

            if (index < 0) {
                return Result.failure(IllegalArgumentException("Group not found"))
            }

            val group = current[index]

            // Check max members
            if (group.members.size >= Group.MAX_MEMBERS) {
                return Result.failure(IllegalStateException("Group is full"))
            }

            // Check if already member
            if (group.members.any { it.publicKey.contentEquals(member.publicKey) }) {
                return Result.failure(IllegalArgumentException("Already a member"))
            }

            val newMembers = group.members + member
            current[index] = group.copy(
                members = newMembers,
                epoch = group.epoch + 1  // Increment epoch for MLS
            )
            _groups.value = current
            saveToDisk()

            Log.d(TAG, "Added member ${member.name} to group ${group.shortId}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add member", e)
            Result.failure(e)
        }
    }

    override suspend fun removeMember(
        groupId: ByteArray,
        memberPublicKey: ByteArray
    ): Result<Unit> = mutex.withLock {
        try {
            val current = _groups.value.toMutableList()
            val index = current.indexOfFirst { it.groupId.contentEquals(groupId) }

            if (index < 0) {
                return Result.failure(IllegalArgumentException("Group not found"))
            }

            val group = current[index]

            // Can't remove owner
            val member = group.members.find { it.publicKey.contentEquals(memberPublicKey) }
            if (member?.role == MemberRole.OWNER) {
                return Result.failure(IllegalArgumentException("Cannot remove group owner"))
            }

            val newMembers = group.members.filter { !it.publicKey.contentEquals(memberPublicKey) }
            current[index] = group.copy(
                members = newMembers,
                epoch = group.epoch + 1
            )
            _groups.value = current
            saveToDisk()

            Log.d(TAG, "Removed member from group ${group.shortId}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove member", e)
            Result.failure(e)
        }
    }

    override suspend fun updateMemberRole(
        groupId: ByteArray,
        memberPublicKey: ByteArray,
        newRole: MemberRole
    ): Result<Unit> = mutex.withLock {
        try {
            val current = _groups.value.toMutableList()
            val index = current.indexOfFirst { it.groupId.contentEquals(groupId) }

            if (index < 0) {
                return Result.failure(IllegalArgumentException("Group not found"))
            }

            val group = current[index]
            val memberIndex = group.members.indexOfFirst { it.publicKey.contentEquals(memberPublicKey) }

            if (memberIndex < 0) {
                return Result.failure(IllegalArgumentException("Member not found"))
            }

            // Can't demote owner
            if (group.members[memberIndex].role == MemberRole.OWNER && newRole != MemberRole.OWNER) {
                return Result.failure(IllegalArgumentException("Cannot demote group owner"))
            }

            val newMembers = group.members.toMutableList()
            newMembers[memberIndex] = newMembers[memberIndex].copy(role = newRole)
            current[index] = group.copy(members = newMembers)
            _groups.value = current
            saveToDisk()

            Log.d(TAG, "Updated member role to $newRole in group ${group.shortId}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update member role", e)
            Result.failure(e)
        }
    }

    override suspend fun updateMemberStatus(
        groupId: ByteArray,
        memberPublicKey: ByteArray,
        status: MemberStatus
    ): Result<Unit> = mutex.withLock {
        try {
            val current = _groups.value.toMutableList()
            val index = current.indexOfFirst { it.groupId.contentEquals(groupId) }

            if (index < 0) return Result.success(Unit)  // Silently ignore if group not found

            val group = current[index]
            val memberIndex = group.members.indexOfFirst { it.publicKey.contentEquals(memberPublicKey) }

            if (memberIndex < 0) return Result.success(Unit)

            val newMembers = group.members.toMutableList()
            newMembers[memberIndex] = newMembers[memberIndex].copy(
                status = status,
                lastSeenAt = System.currentTimeMillis()
            )
            current[index] = group.copy(members = newMembers)
            _groups.value = current
            // Don't save to disk for status updates (too frequent)

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update member status", e)
            Result.failure(e)
        }
    }

    override suspend fun getMembers(groupId: ByteArray): List<GroupMember> {
        return getGroup(groupId)?.members ?: emptyList()
    }

    // ========== Invite Management ==========

    override suspend fun createInvite(
        groupId: ByteArray,
        invitedBy: ByteArray,
        expiresIn: Long
    ): Result<GroupInvite> = mutex.withLock {
        try {
            val group = getGroup(groupId)
                ?: return Result.failure(IllegalArgumentException("Group not found"))

            val invite = GroupInvite(
                groupId = groupId,
                name = group.name,
                creatorKey = group.createdBy,
                invitedBy = invitedBy,
                expiresAt = System.currentTimeMillis() + expiresIn
            )

            Log.d(TAG, "Created invite for group ${group.shortId}")
            Result.success(invite)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create invite", e)
            Result.failure(e)
        }
    }

    override suspend fun acceptInvite(
        invite: GroupInvite,
        joinerPublicKey: ByteArray,
        joinerName: String
    ): Result<Group> = mutex.withLock {
        try {
            // Check expiry
            if (invite.expiresAt != null && System.currentTimeMillis() > invite.expiresAt) {
                return Result.failure(IllegalStateException("Invite has expired"))
            }

            // Find or create group
            var group = _groups.value.find { it.groupId.contentEquals(invite.groupId) }

            if (group == null) {
                // Group doesn't exist locally, create minimal version
                // (Full sync will happen via MLS)
                group = Group(
                    groupId = invite.groupId,
                    name = invite.name,
                    createdBy = invite.creatorKey,
                    createdAt = System.currentTimeMillis(),
                    members = emptyList()
                )
            }

            // Add self as member
            val newMember = GroupMember(
                publicKey = joinerPublicKey,
                name = joinerName,
                role = MemberRole.MEMBER,
                joinedAt = System.currentTimeMillis(),
                status = MemberStatus.ONLINE
            )

            val updatedGroup = group.copy(
                members = group.members + newMember,
                epoch = group.epoch + 1
            )

            val current = _groups.value.toMutableList()
            val existingIndex = current.indexOfFirst { it.groupId.contentEquals(invite.groupId) }
            if (existingIndex >= 0) {
                current[existingIndex] = updatedGroup
            } else {
                current.add(updatedGroup)
            }
            _groups.value = current

            // Remove from pending invites
            _invites.value = _invites.value.filter { !it.groupId.contentEquals(invite.groupId) }

            saveToDisk()

            Log.d(TAG, "Accepted invite for group ${updatedGroup.shortId}")
            Result.success(updatedGroup)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to accept invite", e)
            Result.failure(e)
        }
    }

    override suspend fun declineInvite(invite: GroupInvite): Result<Unit> = mutex.withLock {
        try {
            _invites.value = _invites.value.filter { !it.groupId.contentEquals(invite.groupId) }
            saveInvitesToDisk()
            Log.d(TAG, "Declined invite for group ${invite.name}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decline invite", e)
            Result.failure(e)
        }
    }

    override suspend fun addPendingInvite(invite: GroupInvite): Result<Unit> = mutex.withLock {
        try {
            // Check if already pending
            if (_invites.value.any { it.groupId.contentEquals(invite.groupId) }) {
                return Result.success(Unit)
            }

            // Check if already member
            if (_groups.value.any { it.groupId.contentEquals(invite.groupId) }) {
                return Result.failure(IllegalStateException("Already a member of this group"))
            }

            _invites.value = _invites.value + invite
            saveInvitesToDisk()

            Log.d(TAG, "Added pending invite for group ${invite.name}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add pending invite", e)
            Result.failure(e)
        }
    }

    // ========== Epoch Management ==========

    override suspend fun incrementEpoch(groupId: ByteArray): Result<Long> = mutex.withLock {
        try {
            val current = _groups.value.toMutableList()
            val index = current.indexOfFirst { it.groupId.contentEquals(groupId) }

            if (index < 0) {
                return Result.failure(IllegalArgumentException("Group not found"))
            }

            val newEpoch = current[index].epoch + 1
            current[index] = current[index].copy(epoch = newEpoch)
            _groups.value = current
            saveToDisk()

            Result.success(newEpoch)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to increment epoch", e)
            Result.failure(e)
        }
    }

    override suspend fun getCurrentEpoch(groupId: ByteArray): Long {
        return getGroup(groupId)?.epoch ?: 0L
    }

    // ========== Sync ==========

    override suspend fun refresh() {
        mutex.withLock {
            loadFromDisk()
        }
    }

    override suspend fun isMember(groupId: ByteArray, publicKey: ByteArray): Boolean {
        return getGroup(groupId)?.isMember(publicKey) ?: false
    }

    override suspend fun isAdmin(groupId: ByteArray, publicKey: ByteArray): Boolean {
        return getGroup(groupId)?.isAdmin(publicKey) ?: false
    }

    // ========== Persistence ==========

    private fun loadFromDisk() {
        try {
            // Load groups
            if (groupsFile.exists()) {
                val jsonStr = groupsFile.readText()
                val groupDtos = json.decodeFromString<List<GroupDto>>(jsonStr)
                _groups.value = groupDtos.mapNotNull { it.toGroup() }
                Log.d(TAG, "Loaded ${_groups.value.size} groups from disk")
            }

            // Load invites
            if (invitesFile.exists()) {
                val jsonStr = invitesFile.readText()
                val inviteDtos = json.decodeFromString<List<GroupInviteDto>>(jsonStr)
                _invites.value = inviteDtos.mapNotNull { it.toInvite() }
                Log.d(TAG, "Loaded ${_invites.value.size} pending invites")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load from disk", e)
        }
    }

    private fun saveToDisk() {
        try {
            val dtos = _groups.value.map { GroupDto.fromGroup(it) }
            groupsFile.writeText(json.encodeToString(dtos))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save groups to disk", e)
        }
    }

    private fun saveInvitesToDisk() {
        try {
            val dtos = _invites.value.map { GroupInviteDto.fromInvite(it) }
            invitesFile.writeText(json.encodeToString(dtos))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save invites to disk", e)
        }
    }

    // ========== Helper Extension ==========

    private fun ByteArray.toHexString(): String =
        joinToString("") { "%02x".format(it) }

    // ========== DTOs for Serialization ==========

    @Serializable
    private data class GroupDto(
        val groupId: String,          // Base64
        val name: String,
        val description: String,
        val createdBy: String,        // Base64
        val createdAt: Long,
        val members: List<GroupMemberDto>,
        val epoch: Long,
        val groupState: String,
        val encryptionEnabled: Boolean,
        val settings: GroupSettingsDto
    ) {
        fun toGroup(): Group? {
            return try {
                Group(
                    groupId = Base64.decode(groupId, Base64.NO_WRAP),
                    name = name,
                    description = description,
                    createdBy = Base64.decode(createdBy, Base64.NO_WRAP),
                    createdAt = createdAt,
                    members = members.mapNotNull { it.toMember() },
                    epoch = epoch,
                    groupState = GroupState.valueOf(groupState),
                    encryptionEnabled = encryptionEnabled,
                    settings = settings.toSettings()
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse GroupDto", e)
                null
            }
        }

        companion object {
            fun fromGroup(group: Group): GroupDto {
                return GroupDto(
                    groupId = Base64.encodeToString(group.groupId, Base64.NO_WRAP),
                    name = group.name,
                    description = group.description,
                    createdBy = Base64.encodeToString(group.createdBy, Base64.NO_WRAP),
                    createdAt = group.createdAt,
                    members = group.members.map { GroupMemberDto.fromMember(it) },
                    epoch = group.epoch,
                    groupState = group.groupState.name,
                    encryptionEnabled = group.encryptionEnabled,
                    settings = GroupSettingsDto.fromSettings(group.settings)
                )
            }
        }
    }

    @Serializable
    private data class GroupMemberDto(
        val publicKey: String,        // Base64
        val name: String,
        val role: String,
        val joinedAt: Long,
        val lastSeenAt: Long?,
        val status: String
    ) {
        fun toMember(): GroupMember? {
            return try {
                GroupMember(
                    publicKey = Base64.decode(publicKey, Base64.NO_WRAP),
                    name = name,
                    role = MemberRole.valueOf(role),
                    joinedAt = joinedAt,
                    lastSeenAt = lastSeenAt,
                    status = MemberStatus.valueOf(status)
                )
            } catch (e: Exception) {
                null
            }
        }

        companion object {
            fun fromMember(member: GroupMember): GroupMemberDto {
                return GroupMemberDto(
                    publicKey = Base64.encodeToString(member.publicKey, Base64.NO_WRAP),
                    name = member.name,
                    role = member.role.name,
                    joinedAt = member.joinedAt,
                    lastSeenAt = member.lastSeenAt,
                    status = member.status.name
                )
            }
        }
    }

    @Serializable
    private data class GroupSettingsDto(
        val allowMemberInvites: Boolean,
        val allowVoiceCalls: Boolean,
        val allowVideoCalls: Boolean,
        val allowPTT: Boolean,
        val pttPriority: String,
        val maxCallParticipants: Int,
        val messageRetention: String,
        val requireApproval: Boolean,
        val encryptionLevel: String
    ) {
        fun toSettings(): GroupSettings {
            return GroupSettings(
                allowMemberInvites = allowMemberInvites,
                allowVoiceCalls = allowVoiceCalls,
                allowVideoCalls = allowVideoCalls,
                allowPTT = allowPTT,
                pttPriority = PTTPriority.valueOf(pttPriority),
                maxCallParticipants = maxCallParticipants,
                messageRetention = MessageRetention.valueOf(messageRetention),
                requireApproval = requireApproval,
                encryptionLevel = EncryptionLevel.valueOf(encryptionLevel)
            )
        }

        companion object {
            fun fromSettings(settings: GroupSettings): GroupSettingsDto {
                return GroupSettingsDto(
                    allowMemberInvites = settings.allowMemberInvites,
                    allowVoiceCalls = settings.allowVoiceCalls,
                    allowVideoCalls = settings.allowVideoCalls,
                    allowPTT = settings.allowPTT,
                    pttPriority = settings.pttPriority.name,
                    maxCallParticipants = settings.maxCallParticipants,
                    messageRetention = settings.messageRetention.name,
                    requireApproval = settings.requireApproval,
                    encryptionLevel = settings.encryptionLevel.name
                )
            }
        }
    }

    @Serializable
    private data class GroupInviteDto(
        val groupId: String,          // Base64
        val name: String,
        val creatorKey: String,       // Base64
        val invitedBy: String?,       // Base64
        val expiresAt: Long?
    ) {
        fun toInvite(): GroupInvite? {
            return try {
                GroupInvite(
                    groupId = Base64.decode(groupId, Base64.NO_WRAP),
                    name = name,
                    creatorKey = Base64.decode(creatorKey, Base64.NO_WRAP),
                    invitedBy = invitedBy?.let { Base64.decode(it, Base64.NO_WRAP) },
                    expiresAt = expiresAt
                )
            } catch (e: Exception) {
                null
            }
        }

        companion object {
            fun fromInvite(invite: GroupInvite): GroupInviteDto {
                return GroupInviteDto(
                    groupId = Base64.encodeToString(invite.groupId, Base64.NO_WRAP),
                    name = invite.name,
                    creatorKey = Base64.encodeToString(invite.creatorKey, Base64.NO_WRAP),
                    invitedBy = invite.invitedBy?.let { Base64.encodeToString(it, Base64.NO_WRAP) },
                    expiresAt = invite.expiresAt
                )
            }
        }
    }
}
