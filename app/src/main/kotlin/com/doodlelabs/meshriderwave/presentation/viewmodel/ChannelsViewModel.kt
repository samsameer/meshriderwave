/*
 * Mesh Rider Wave - Channels ViewModel
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 *
 * ViewModel for PTT channel management and transmission
 */

package com.doodlelabs.meshriderwave.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.doodlelabs.meshriderwave.core.ptt.PTTChannelBeacon
import com.doodlelabs.meshriderwave.core.ptt.PTTManager
import com.doodlelabs.meshriderwave.domain.model.group.PTTMember
import com.doodlelabs.meshriderwave.domain.model.group.PTTRole
import com.doodlelabs.meshriderwave.domain.repository.PTTChannelRepository
import com.doodlelabs.meshriderwave.domain.repository.SettingsRepository
import com.doodlelabs.meshriderwave.presentation.ui.screens.channels.ChannelUiModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChannelsViewModel @Inject constructor(
    private val pttChannelRepository: PTTChannelRepository,
    private val pttManager: PTTManager,
    private val pttChannelBeacon: PTTChannelBeacon,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    companion object {
        private const val TAG = "MeshRider:ChannelsVM"
    }

    private val _uiState = MutableStateFlow(ChannelsUiState())
    val uiState: StateFlow<ChannelsUiState> = _uiState.asStateFlow()

    init {
        // Initialize PTTManager for audio
        pttManager.initialize()

        // Feb 2026 FIX (Bug 16): Set PTTManager's ownPublicKey so broadcasts and member checks work
        viewModelScope.launch {
            val keyPair = settingsRepository.getOrCreateKeyPair()
            pttManager.ownPublicKey = keyPair.publicKey
            pttManager.ownSecretKey = keyPair.secretKey
            Log.d(TAG, "Set PTTManager keys from settings")
        }

        // Set up callback to register joiner's address when someone joins our channel
        pttChannelBeacon.onChannelJoinRequest = { channelId, peerKey, peerName, peerIp ->
            Log.i(TAG, "Join request received: $peerName ($peerIp) wants to join channel $channelId")
            // Register the joiner's IP for PTT audio delivery
            pttManager.registerMemberAddress(peerKey, listOf(peerIp))
            Log.d(TAG, "Registered joiner address: $peerName @ $peerIp")
        }

        observeChannels()
        observePTTState()
        observeDiscoveredChannels()
        startBeacon()
    }

    private fun startBeacon() {
        viewModelScope.launch {
            try {
                val keyPair = settingsRepository.getOrCreateKeyPair()
                val username = settingsRepository.username.first()
                pttChannelBeacon.start(keyPair.publicKey, keyPair.secretKey, username)
                Log.i(TAG, "Started PTT Channel Beacon")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start beacon", e)
            }
        }
    }

    private fun observeDiscoveredChannels() {
        viewModelScope.launch {
            // Combine discovered channels with local channels to filter duplicates
            combine(
                pttChannelBeacon.discoveredChannels,
                pttChannelRepository.channels
            ) { discovered, localChannels ->
                // Get IDs of channels we already have locally
                val localChannelIds = localChannels.map { it.shortId.uppercase() }.toSet()

                // Filter out channels we already have
                discovered.values
                    .filter { channel ->
                        !localChannelIds.contains(channel.shortId.uppercase())
                    }
                    .map { channel ->
                        ChannelUiModel(
                            id = channel.shortId,
                            name = channel.name,
                            frequency = channel.frequency,
                            onlineCount = channel.memberCount,
                            priority = channel.priority.name,
                            hasActivity = true,  // Discovered channels are active
                            isDiscovered = true,  // Mark as discovered from network
                            announcerName = channel.announcerName
                        )
                    }
            }.collect { discoveredUiModels ->
                _uiState.update { it.copy(discoveredChannels = discoveredUiModels) }
                if (discoveredUiModels.isNotEmpty()) {
                    Log.d(TAG, "Discovered ${discoveredUiModels.size} new channels from network")
                }
            }
        }
    }

    // Cache own public key for ownership checks
    private var ownPublicKey: ByteArray = ByteArray(0)

    private fun observeChannels() {
        viewModelScope.launch {
            // Load own key FIRST before observing channels (fixes isOwner race condition)
            ownPublicKey = settingsRepository.getOrCreateKeyPair().publicKey

            pttChannelRepository.channels.collect { channels ->
                val uiModels = channels.map { channel ->
                    ChannelUiModel(
                        id = channel.shortId,
                        name = channel.name,
                        frequency = channel.frequency,
                        onlineCount = channel.members.size,
                        priority = channel.priority.name,
                        hasActivity = false,
                        isOwner = channel.createdBy.contentEquals(ownPublicKey)
                    )
                }
                _uiState.update { it.copy(channels = uiModels, isLoading = false) }
            }
        }

        viewModelScope.launch {
            pttChannelRepository.joinedChannels.collect { joinedChannels ->
                val joinedIds = joinedChannels.map { it.shortId }.toSet()
                _uiState.update { it.copy(joinedChannelIds = joinedIds) }
            }
        }
    }

    private var transmitStateJob: kotlinx.coroutines.Job? = null

    private fun observePTTState() {
        viewModelScope.launch {
            pttManager.activeChannel.collect { channel ->
                channel?.let {
                    _uiState.update { state ->
                        state.copy(
                            activeChannel = ChannelUiModel(
                                id = it.shortId,
                                name = it.name,
                                frequency = it.frequency,
                                onlineCount = it.members.size,
                                priority = it.priority.name,
                                hasActivity = true
                            ),
                            memberCount = it.members.size
                        )
                    }
                    // Observe floor control state for the active channel
                    observeTransmitState(it)
                } ?: run {
                    transmitStateJob?.cancel()
                    _uiState.update { it.copy(activeChannel = null, floorState = "IDLE", activeSpeaker = null) }
                }
            }
        }
    }

    private fun observeTransmitState(channel: com.doodlelabs.meshriderwave.domain.model.group.PTTChannel) {
        transmitStateJob?.cancel()
        transmitStateJob = viewModelScope.launch {
            pttManager.getTransmitState(channel).collect { txState ->
                _uiState.update { state ->
                    state.copy(
                        floorState = txState.status.name,
                        activeSpeaker = txState.currentSpeaker?.name,
                        isTransmitting = txState.status == com.doodlelabs.meshriderwave.domain.model.group.PTTTransmitState.Status.TRANSMITTING
                    )
                }
            }
        }
    }

    fun createChannel(name: String, frequency: String) {
        viewModelScope.launch {
            try {
                val keyPair = settingsRepository.getOrCreateKeyPair()
                pttChannelRepository.createChannel(
                    name = name,
                    description = "PTT Channel",
                    creatorPublicKey = keyPair.publicKey,
                    groupId = null
                ).onSuccess { channel ->
                    Log.i(TAG, "Created channel: ${channel.name}")

                    // Jan 2026 FIX: Directly set on PTTManager to avoid race with UI flows
                    pttManager.setActiveChannel(channel)
                    Log.i(TAG, "PTTManager active channel set to: ${channel.name}")

                    // Also update UI state directly
                    val uiChannel = ChannelUiModel(
                        id = channel.shortId,
                        name = channel.name,
                        frequency = channel.displayFrequency,
                        onlineCount = channel.members.size,
                        priority = channel.priority.name,
                        hasActivity = true
                    )
                    _uiState.update { it.copy(activeChannel = uiChannel) }

                    // Jan 2026: Announce channel to other devices via beacon
                    pttChannelBeacon.announceChannel(channel)
                    Log.i(TAG, "Announced channel to network: ${channel.name}")
                }.onFailure { e ->
                    Log.e(TAG, "Failed to create channel", e)
                    _uiState.update { it.copy(error = e.message) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create channel", e)
            }
        }
    }

    /**
     * Join a channel discovered from the network
     * Note: channelId here is the shortId (first 4 bytes as hex uppercase)
     *
     * Jan 2026: Fixed - First add channel to local repo, then join
     */
    fun joinDiscoveredChannel(shortId: String) {
        viewModelScope.launch {
            try {
                // Find the discovered channel by shortId (map is keyed by full hex, case-insensitive)
                val discovered = pttChannelBeacon.discoveredChannels.value.values
                    .find { it.shortId.equals(shortId, ignoreCase = true) }
                    ?: run {
                        Log.e(TAG, "Discovered channel not found: $shortId")
                        _uiState.update { it.copy(error = "Channel no longer available") }
                        return@launch
                    }

                val keyPair = settingsRepository.getOrCreateKeyPair()
                val username = settingsRepository.username.first()

                Log.i(TAG, "Joining discovered channel: ${discovered.name} (${discovered.shortId})")

                // Step 1: Add the channel to local repository first
                // This is critical - we need to import the channel before we can join it
                val pttChannel = discovered.toPTTChannel()
                pttChannelRepository.addOrUpdateChannel(pttChannel).onFailure { e ->
                    Log.e(TAG, "Failed to add channel to local repo", e)
                    _uiState.update { it.copy(error = "Failed to add channel: ${e.message}") }
                    return@launch
                }
                Log.d(TAG, "Added channel to local repository: ${discovered.name}")

                // Step 2: Create member entry for self
                val member = PTTMember(
                    publicKey = keyPair.publicKey,
                    name = username,
                    role = PTTRole.MEMBER,
                    priority = 50,
                    canTransmit = true,
                    isMuted = false,
                    joinedAt = System.currentTimeMillis()
                )

                // Step 3: Register announcer's IP address for PTT audio delivery
                // CRITICAL: Without this, audio won't be delivered to the other device
                pttManager.registerMemberAddress(
                    discovered.announcerKey,
                    listOf(discovered.announcerIp)
                )
                Log.d(TAG, "Registered announcer address: ${discovered.announcerName} @ ${discovered.announcerIp}")

                // Step 4: Join the channel (now it exists in local repo)
                pttChannelRepository.joinChannel(
                    channelId = discovered.channelId,
                    member = member
                ).onSuccess {
                    Log.i(TAG, "Successfully joined discovered channel: ${discovered.name}")

                    // Step 5: Set as active channel automatically
                    // Jan 2026 FIX: Directly set on PTTManager since UI flows might not have updated yet
                    pttManager.setActiveChannel(pttChannel)
                    Log.i(TAG, "PTTManager active channel set to: ${pttChannel.name}")

                    // Also update UI state with the channel from discovered list
                    val uiChannel = ChannelUiModel(
                        id = pttChannel.shortId,
                        name = pttChannel.name,
                        frequency = pttChannel.displayFrequency,
                        onlineCount = pttChannel.members.size,
                        priority = pttChannel.priority.name,
                        hasActivity = true
                    )
                    _uiState.update { it.copy(activeChannel = uiChannel) }
                    Log.i(TAG, "UI active channel set to: ${uiChannel.name}")

                    // Step 6: Notify channel owner via beacon
                    pttChannelBeacon.requestJoinChannel(shortId)
                }.onFailure { e ->
                    Log.e(TAG, "Failed to join discovered channel", e)
                    _uiState.update { it.copy(error = "Failed to join: ${e.message}") }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to join discovered channel", e)
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    /**
     * Join a local channel (MY CHANNELS section)
     * Feb 2026 FIX (Bug 7): Find full channelId by shortId instead of using shortId as channelId
     */
    fun joinChannel(shortId: String) {
        viewModelScope.launch {
            try {
                // Find the full channel by shortId
                val channel = pttChannelRepository.channels.first().find { it.shortId == shortId }
                if (channel == null) {
                    Log.e(TAG, "joinChannel: Channel not found for shortId: $shortId")
                    _uiState.update { it.copy(error = "Channel not found") }
                    return@launch
                }

                val keyPair = settingsRepository.getOrCreateKeyPair()
                val username = settingsRepository.username.first()
                val member = PTTMember(
                    publicKey = keyPair.publicKey,
                    name = username,
                    role = PTTRole.MEMBER,
                    priority = 50,
                    canTransmit = true,
                    isMuted = false,
                    joinedAt = System.currentTimeMillis()
                )

                pttChannelRepository.joinChannel(
                    channelId = channel.channelId,  // Full 16-byte channelId
                    member = member
                ).onSuccess {
                    Log.i(TAG, "Joined channel: ${channel.name} ($shortId)")
                    // Auto-set as active channel
                    pttManager.setActiveChannel(channel)
                    _uiState.update { it.copy(
                        activeChannel = ChannelUiModel(
                            id = channel.shortId,
                            name = channel.name,
                            frequency = channel.displayFrequency,
                            onlineCount = channel.members.size,
                            priority = channel.priority.name,
                            hasActivity = true
                        )
                    )}
                }.onFailure { e ->
                    Log.e(TAG, "Failed to join channel", e)
                    _uiState.update { it.copy(error = "Failed to join: ${e.message}") }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to join channel", e)
            }
        }
    }

    /**
     * Leave/remove a channel
     * Feb 2026 FIX (Bug 8): Find full channelId by shortId
     */
    fun leaveChannel(shortId: String) {
        viewModelScope.launch {
            try {
                val channel = pttChannelRepository.channels.first().find { it.shortId == shortId }
                if (channel == null) {
                    Log.e(TAG, "leaveChannel: Channel not found for shortId: $shortId")
                    return@launch
                }

                val keyPair = settingsRepository.getOrCreateKeyPair()
                val isOwner = channel.createdBy.contentEquals(keyPair.publicKey)

                // Leave multicast talkgroup first
                pttManager.setActiveChannel(null)

                pttChannelRepository.leaveChannel(
                    channelId = channel.channelId,  // Full 16-byte channelId
                    memberPublicKey = keyPair.publicKey
                )

                // If we don't own the channel, also delete from local repo
                if (!isOwner) {
                    pttChannelRepository.deleteChannel(channel.channelId)
                    pttChannelBeacon.clearDiscoveredChannel(channel.channelId)
                    Log.i(TAG, "Left and removed non-owned channel: ${channel.name}")
                }

                // Clear active if leaving active channel
                if (_uiState.value.activeChannel?.id == shortId) {
                    _uiState.update { it.copy(activeChannel = null) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to leave channel", e)
            }
        }
    }

    fun setActiveChannel(channelId: String) {
        viewModelScope.launch {
            // Empty = clear active (back from talk screen)
            if (channelId.isEmpty()) {
                _uiState.update { it.copy(activeChannel = null) }
                Log.d(TAG, "setActiveChannel: cleared")
                return@launch
            }

            val channel = _uiState.value.channels.find { it.id == channelId }
                ?: _uiState.value.discoveredChannels.find { it.id == channelId }

            if (channel != null && _uiState.value.joinedChannelIds.contains(channelId)) {
                _uiState.update { it.copy(activeChannel = channel) }

                // Sync with PTTManager so audio works
                pttChannelRepository.channels.first().find { it.shortId == channelId }?.let { pttChannel ->
                    pttManager.setActiveChannel(pttChannel)
                    Log.i(TAG, "setActiveChannel: ${pttChannel.name}")
                }
            }
        }
    }

    /**
     * Delete a channel (owner only)
     * Note: channelId here is the shortId (first 4 bytes as hex)
     *
     * Jan 2026: Now broadcasts deletion to peers so they remove it from discovered list
     */
    fun deleteChannel(shortId: String) {
        viewModelScope.launch {
            try {
                // Find the full channel by shortId
                val channel = pttChannelRepository.channels.first().find { it.shortId == shortId }
                if (channel == null) {
                    Log.e(TAG, "Channel not found for deletion: $shortId")
                    return@launch
                }

                // Save channelId before deletion for broadcast
                val channelId = channel.channelId.copyOf()

                pttChannelRepository.deleteChannel(channel.channelId)
                    .onSuccess {
                        Log.i(TAG, "Deleted channel: ${channel.name} ($shortId)")

                        // Jan 2026 FIX: Broadcast deletion to peers so they remove from discovered list
                        pttChannelBeacon.announceChannelDeleted(channelId)

                        // Also clear from local discovered channels (in case we imported it from network)
                        pttChannelBeacon.clearDiscoveredChannel(channelId)

                        if (_uiState.value.activeChannel?.id == shortId) {
                            _uiState.update { it.copy(activeChannel = null) }
                            pttManager.setActiveChannel(null)
                        }
                    }
                    .onFailure { e ->
                        Log.e(TAG, "Failed to delete channel", e)
                        _uiState.update { it.copy(error = e.message) }
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete channel", e)
            }
        }
    }

    /**
     * Feb 2026 FIX (Bug 9): Sync active channel synchronously to avoid race condition
     */
    fun startTransmit() {
        viewModelScope.launch {
            try {
                var channel = pttManager.activeChannel.value

                if (channel == null) {
                    // Sync from UI state — find full channel in repo directly (no async)
                    val uiChannel = _uiState.value.activeChannel
                    if (uiChannel != null) {
                        val fullChannel = pttChannelRepository.channels.first()
                            .find { it.shortId == uiChannel.id }
                        if (fullChannel != null) {
                            pttManager.setActiveChannel(fullChannel)
                            channel = fullChannel
                        }
                    }
                    if (channel == null) {
                        _uiState.update { it.copy(error = "No active channel") }
                        return@launch
                    }
                }

                Log.i(TAG, "startTransmit: ${channel.name}")
                pttManager.requestFloor(channel)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start transmit", e)
                _uiState.update { it.copy(error = "PTT failed: ${e.message}", isTransmitting = false) }
            }
        }
    }

    fun stopTransmit() {
        Log.i(TAG, "stopTransmit: CALLED")
        viewModelScope.launch {
            try {
                val channel = pttManager.activeChannel.value
                Log.d(TAG, "stopTransmit: activeChannel=${channel?.name ?: "NULL"}")

                if (channel != null) {
                    pttManager.releaseFloor(channel)
                } else {
                    // Feb 2026 FIX: Even if active channel is null, FORCE stop audio
                    Log.w(TAG, "stopTransmit: No active channel — force stopping audio")
                    pttManager.forceStopAllTransmission()
                }
                _uiState.update { it.copy(isTransmitting = false) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop transmit", e)
                // Still force stop on error
                pttManager.forceStopAllTransmission()
                _uiState.update { it.copy(isTransmitting = false) }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    /**
     * Refresh channels from repository and beacon
     */
    fun refreshChannels() {
        viewModelScope.launch {
            try {
                pttChannelRepository.refresh()
                Log.d(TAG, "Refreshed channels")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to refresh channels", e)
            }
        }
    }

    private fun String.hexToByteArray(): ByteArray =
        chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    /**
     * Cleanup resources when ViewModel is destroyed
     * Jan 2026 CRITICAL FIX: Prevents memory leaks and crashes
     *
     * Without this:
     * - PTTChannelBeacon callbacks hold reference to destroyed ViewModel
     * - Screen rotation causes NPE when beacon receives data
     * - Singleton beacon outlives ViewModel, causing callback leaks
     */
    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "onCleared: Cleaning up PTT resources")

        // Clear all callbacks to prevent memory leaks
        // These singletons outlive the ViewModel
        pttChannelBeacon.onChannelJoinRequest = null
        pttChannelBeacon.onChannelDiscovered = null
        pttChannelBeacon.onChannelDeleted = null

        // Note: Don't stop beacon here - MeshService manages its lifecycle
        // pttChannelBeacon.stop() is called in MeshService.stopListening()

        Log.d(TAG, "onCleared: PTT resources cleaned up")
    }
}

data class ChannelsUiState(
    val channels: List<ChannelUiModel> = emptyList(),
    val discoveredChannels: List<ChannelUiModel> = emptyList(),
    val joinedChannelIds: Set<String> = emptySet(),
    val activeChannel: ChannelUiModel? = null,
    val isTransmitting: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null,
    val floorState: String = "IDLE",         // IDLE, REQUESTING, GRANTED, DENIED, QUEUED
    val activeSpeaker: String? = null,       // Name of current floor holder
    val memberCount: Int = 0                 // Members in active channel
)
