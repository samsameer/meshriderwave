/*
 * Mesh Rider Wave - PTT Channel Repository Implementation
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 *
 * JSON-based persistence for PTT/Walkie-Talkie channels
 */

package com.doodlelabs.meshriderwave.data.repository

import android.content.Context
import android.util.Base64
import android.util.Log
import com.doodlelabs.meshriderwave.domain.model.group.AudioCodec
import com.doodlelabs.meshriderwave.domain.model.group.ChannelPriority
import com.doodlelabs.meshriderwave.domain.model.group.PTTChannel
import com.doodlelabs.meshriderwave.domain.model.group.PTTMember
import com.doodlelabs.meshriderwave.domain.model.group.PTTRole
import com.doodlelabs.meshriderwave.domain.model.group.PTTSettings
import com.doodlelabs.meshriderwave.domain.repository.PTTChannelRepository
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
class PTTChannelRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : PTTChannelRepository {

    companion object {
        private const val TAG = "MeshRider:PTTRepo"
        private const val CHANNELS_FILE = "ptt_channels.json"
    }

    private val mutex = Mutex()
    private val _channels = MutableStateFlow<List<PTTChannel>>(emptyList())

    // Track joined channels (local state only)
    private val _joinedChannelIds = MutableStateFlow<Set<String>>(emptySet())

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    private val channelsFile: File get() = File(context.filesDir, CHANNELS_FILE)
    private val secureRandom = SecureRandom()

    // ========== Flows ==========

    override val channels: Flow<List<PTTChannel>> = _channels

    override val joinedChannels: Flow<List<PTTChannel>> = _channels.map { channels ->
        val joinedIds = _joinedChannelIds.value
        channels.filter { channel ->
            joinedIds.contains(channel.channelId.toHexString())
        }
    }

    init {
        loadFromDisk()
    }

    // ========== Channel CRUD ==========

    override suspend fun createChannel(
        name: String,
        description: String,
        creatorPublicKey: ByteArray,
        groupId: ByteArray?
    ): Result<PTTChannel> = mutex.withLock {
        try {
            // Validate name
            if (name.isBlank() || name.length > PTTChannel.MAX_NAME_LENGTH) {
                return Result.failure(IllegalArgumentException("Invalid channel name"))
            }

            // Generate unique channel ID (16 bytes)
            val channelId = ByteArray(16)
            secureRandom.nextBytes(channelId)

            // Create owner as first member
            val owner = PTTMember(
                publicKey = creatorPublicKey,
                name = "You",
                role = PTTRole.OWNER,
                priority = 100,  // High priority for owner
                canTransmit = true,
                isMuted = false,
                joinedAt = System.currentTimeMillis()
            )

            val channel = PTTChannel(
                channelId = channelId,
                name = name.trim(),
                description = description.trim(),
                frequency = "",  // Auto-generated based on shortId
                groupId = groupId,
                members = listOf(owner),
                priority = ChannelPriority.NORMAL,
                settings = PTTSettings(),
                createdAt = System.currentTimeMillis(),
                createdBy = creatorPublicKey
            )

            val current = _channels.value.toMutableList()
            current.add(channel)
            _channels.value = current

            // Auto-join created channel
            _joinedChannelIds.value = _joinedChannelIds.value + channelId.toHexString()

            saveToDisk()

            Log.d(TAG, "Created PTT channel: ${channel.shortId} - $name")
            Result.success(channel)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create channel", e)
            Result.failure(e)
        }
    }

    override suspend fun getChannel(channelId: ByteArray): PTTChannel? {
        return _channels.value.find { it.channelId.contentEquals(channelId) }
    }

    override suspend fun updateChannel(channel: PTTChannel): Result<Unit> = mutex.withLock {
        try {
            val current = _channels.value.toMutableList()
            val index = current.indexOfFirst { it.channelId.contentEquals(channel.channelId) }

            if (index < 0) {
                return Result.failure(IllegalArgumentException("Channel not found"))
            }

            current[index] = channel
            _channels.value = current
            saveToDisk()

            Log.d(TAG, "Updated channel: ${channel.shortId}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update channel", e)
            Result.failure(e)
        }
    }

    override suspend fun deleteChannel(channelId: ByteArray): Result<Unit> = mutex.withLock {
        try {
            val hexId = channelId.toHexString()

            _channels.value = _channels.value.filter { !it.channelId.contentEquals(channelId) }
            _joinedChannelIds.value = _joinedChannelIds.value - hexId

            saveToDisk()

            Log.d(TAG, "Deleted channel: ${channelId.take(4).toByteArray().toHexString()}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete channel", e)
            Result.failure(e)
        }
    }

    /**
     * Add or update a channel (for importing discovered channels from network)
     * Jan 2026: Added for PTT channel discovery between devices
     */
    override suspend fun addOrUpdateChannel(channel: PTTChannel): Result<Unit> = mutex.withLock {
        try {
            val current = _channels.value.toMutableList()
            val existingIndex = current.indexOfFirst { it.channelId.contentEquals(channel.channelId) }

            if (existingIndex >= 0) {
                // Update existing channel
                current[existingIndex] = channel
                Log.d(TAG, "Updated existing channel: ${channel.shortId}")
            } else {
                // Add new channel
                current.add(channel)
                Log.d(TAG, "Added new channel from network: ${channel.shortId} - ${channel.name}")
            }

            _channels.value = current
            saveToDisk()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add/update channel", e)
            Result.failure(e)
        }
    }

    // ========== Membership ==========

    override suspend fun joinChannel(
        channelId: ByteArray,
        member: PTTMember
    ): Result<Unit> = mutex.withLock {
        try {
            val current = _channels.value.toMutableList()
            val index = current.indexOfFirst { it.channelId.contentEquals(channelId) }

            if (index < 0) {
                return Result.failure(IllegalArgumentException("Channel not found"))
            }

            val channel = current[index]

            // Check max members
            if (channel.members.size >= PTTChannel.MAX_MEMBERS) {
                return Result.failure(IllegalStateException("Channel is full"))
            }

            // Check if already member
            if (channel.members.any { it.publicKey.contentEquals(member.publicKey) }) {
                // Already member, just mark as joined
                _joinedChannelIds.value = _joinedChannelIds.value + channelId.toHexString()
                return Result.success(Unit)
            }

            current[index] = channel.copy(members = channel.members + member)
            _channels.value = current
            _joinedChannelIds.value = _joinedChannelIds.value + channelId.toHexString()

            saveToDisk()

            Log.d(TAG, "Joined channel: ${channel.shortId}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to join channel", e)
            Result.failure(e)
        }
    }

    override suspend fun leaveChannel(
        channelId: ByteArray,
        memberPublicKey: ByteArray
    ): Result<Unit> = mutex.withLock {
        try {
            val hexId = channelId.toHexString()

            val current = _channels.value.toMutableList()
            val index = current.indexOfFirst { it.channelId.contentEquals(channelId) }

            if (index >= 0) {
                val channel = current[index]

                // Check if owner is leaving
                val member = channel.members.find { it.publicKey.contentEquals(memberPublicKey) }
                if (member?.role == PTTRole.OWNER) {
                    // Transfer ownership or delete channel
                    val newOwner = channel.members.firstOrNull {
                        !it.publicKey.contentEquals(memberPublicKey) &&
                                it.role in listOf(PTTRole.DISPATCHER, PTTRole.MEMBER)
                    }

                    if (newOwner != null) {
                        // Transfer ownership
                        val newMembers = channel.members
                            .filter { !it.publicKey.contentEquals(memberPublicKey) }
                            .map { m ->
                                if (m.publicKey.contentEquals(newOwner.publicKey)) {
                                    m.copy(role = PTTRole.OWNER)
                                } else m
                            }
                        current[index] = channel.copy(members = newMembers)
                    } else {
                        // Delete channel if no one left
                        current.removeAt(index)
                    }
                } else {
                    // Just remove member
                    current[index] = channel.copy(
                        members = channel.members.filter { !it.publicKey.contentEquals(memberPublicKey) }
                    )
                }

                _channels.value = current
            }

            _joinedChannelIds.value = _joinedChannelIds.value - hexId
            saveToDisk()

            Log.d(TAG, "Left channel: ${channelId.take(4).toByteArray().toHexString()}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to leave channel", e)
            Result.failure(e)
        }
    }

    override suspend fun getMembers(channelId: ByteArray): List<PTTMember> {
        return getChannel(channelId)?.members ?: emptyList()
    }

    // ========== Sync ==========

    override suspend fun refresh() {
        mutex.withLock {
            loadFromDisk()
        }
    }

    // ========== Persistence ==========

    private fun loadFromDisk() {
        try {
            if (channelsFile.exists()) {
                val jsonStr = channelsFile.readText()
                val dto = json.decodeFromString<PTTDataDto>(jsonStr)
                _channels.value = dto.channels.mapNotNull { it.toChannel() }
                _joinedChannelIds.value = dto.joinedChannelIds.toSet()
                Log.d(TAG, "Loaded ${_channels.value.size} PTT channels from disk")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load from disk", e)
        }
    }

    private fun saveToDisk() {
        try {
            val dto = PTTDataDto(
                channels = _channels.value.map { PTTChannelDto.fromChannel(it) },
                joinedChannelIds = _joinedChannelIds.value.toList()
            )
            channelsFile.writeText(json.encodeToString(dto))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save to disk", e)
        }
    }

    // ========== Helper Extension ==========

    private fun ByteArray.toHexString(): String =
        joinToString("") { "%02x".format(it) }

    // ========== DTOs for Serialization ==========

    @Serializable
    private data class PTTDataDto(
        val channels: List<PTTChannelDto>,
        val joinedChannelIds: List<String>
    )

    @Serializable
    private data class PTTChannelDto(
        val channelId: String,        // Base64
        val name: String,
        val description: String,
        val frequency: String,
        val groupId: String?,         // Base64
        val members: List<PTTMemberDto>,
        val priority: String,
        val settings: PTTSettingsDto,
        val createdAt: Long,
        val createdBy: String         // Base64
    ) {
        fun toChannel(): PTTChannel? {
            return try {
                PTTChannel(
                    channelId = Base64.decode(channelId, Base64.NO_WRAP),
                    name = name,
                    description = description,
                    frequency = frequency,
                    groupId = groupId?.let { Base64.decode(it, Base64.NO_WRAP) },
                    members = members.mapNotNull { it.toMember() },
                    priority = ChannelPriority.valueOf(priority),
                    settings = settings.toSettings(),
                    createdAt = createdAt,
                    createdBy = Base64.decode(createdBy, Base64.NO_WRAP)
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse PTTChannelDto", e)
                null
            }
        }

        companion object {
            fun fromChannel(channel: PTTChannel): PTTChannelDto {
                return PTTChannelDto(
                    channelId = Base64.encodeToString(channel.channelId, Base64.NO_WRAP),
                    name = channel.name,
                    description = channel.description,
                    frequency = channel.frequency,
                    groupId = channel.groupId?.let { Base64.encodeToString(it, Base64.NO_WRAP) },
                    members = channel.members.map { PTTMemberDto.fromMember(it) },
                    priority = channel.priority.name,
                    settings = PTTSettingsDto.fromSettings(channel.settings),
                    createdAt = channel.createdAt,
                    createdBy = Base64.encodeToString(channel.createdBy, Base64.NO_WRAP)
                )
            }
        }
    }

    @Serializable
    private data class PTTMemberDto(
        val publicKey: String,        // Base64
        val name: String,
        val role: String,
        val priority: Int,
        val canTransmit: Boolean,
        val isMuted: Boolean,
        val joinedAt: Long
    ) {
        fun toMember(): PTTMember? {
            return try {
                PTTMember(
                    publicKey = Base64.decode(publicKey, Base64.NO_WRAP),
                    name = name,
                    role = PTTRole.valueOf(role),
                    priority = priority,
                    canTransmit = canTransmit,
                    isMuted = isMuted,
                    joinedAt = joinedAt
                )
            } catch (e: Exception) {
                null
            }
        }

        companion object {
            fun fromMember(member: PTTMember): PTTMemberDto {
                return PTTMemberDto(
                    publicKey = Base64.encodeToString(member.publicKey, Base64.NO_WRAP),
                    name = member.name,
                    role = member.role.name,
                    priority = member.priority,
                    canTransmit = member.canTransmit,
                    isMuted = member.isMuted,
                    joinedAt = member.joinedAt
                )
            }
        }
    }

    @Serializable
    private data class PTTSettingsDto(
        val transmitTimeout: Long,
        val cooldownPeriod: Long,
        val allowEmergencyOverride: Boolean,
        val playToneOnTransmit: Boolean,
        val playToneOnReceive: Boolean,
        val recordTransmissions: Boolean,
        val encryptionEnabled: Boolean,
        val voxEnabled: Boolean,
        val voxThreshold: Float,
        val audioCodec: String
    ) {
        fun toSettings(): PTTSettings {
            return PTTSettings(
                transmitTimeout = transmitTimeout,
                cooldownPeriod = cooldownPeriod,
                allowEmergencyOverride = allowEmergencyOverride,
                playToneOnTransmit = playToneOnTransmit,
                playToneOnReceive = playToneOnReceive,
                recordTransmissions = recordTransmissions,
                encryptionEnabled = encryptionEnabled,
                voxEnabled = voxEnabled,
                voxThreshold = voxThreshold,
                audioCodec = AudioCodec.valueOf(audioCodec)
            )
        }

        companion object {
            fun fromSettings(settings: PTTSettings): PTTSettingsDto {
                return PTTSettingsDto(
                    transmitTimeout = settings.transmitTimeout,
                    cooldownPeriod = settings.cooldownPeriod,
                    allowEmergencyOverride = settings.allowEmergencyOverride,
                    playToneOnTransmit = settings.playToneOnTransmit,
                    playToneOnReceive = settings.playToneOnReceive,
                    recordTransmissions = settings.recordTransmissions,
                    encryptionEnabled = settings.encryptionEnabled,
                    voxEnabled = settings.voxEnabled,
                    voxThreshold = settings.voxThreshold,
                    audioCodec = settings.audioCodec.name
                )
            }
        }
    }
}
