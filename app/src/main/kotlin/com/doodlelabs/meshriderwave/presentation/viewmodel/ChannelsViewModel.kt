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

    private fun observeChannels() {
        viewModelScope.launch {
            pttChannelRepository.channels.collect { channels ->
                val uiModels = channels.map { channel ->
                    ChannelUiModel(
                        id = channel.shortId,
                        name = channel.name,
                        frequency = channel.frequency,
                        onlineCount = channel.members.size,
                        priority = channel.priority.name,
                        hasActivity = false
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
                            )
                        )
                    }
                } ?: run {
                    _uiState.update { it.copy(activeChannel = null) }
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

    fun joinChannel(channelId: String) {
        viewModelScope.launch {
            try {
                val keyPair = settingsRepository.getOrCreateKeyPair()
                settingsRepository.username.first().let { username ->
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
                        channelId = channelId.hexToByteArray(),
                        member = member
                    ).onSuccess {
                        Log.i(TAG, "Joined channel: $channelId")
                    }.onFailure { e ->
                        Log.e(TAG, "Failed to join channel", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to join channel", e)
            }
        }
    }

    fun leaveChannel(channelId: String) {
        viewModelScope.launch {
            try {
                val keyPair = settingsRepository.getOrCreateKeyPair()
                pttChannelRepository.leaveChannel(
                    channelId = channelId.hexToByteArray(),
                    memberPublicKey = keyPair.publicKey
                )

                // Clear active if leaving active channel
                if (_uiState.value.activeChannel?.id == channelId) {
                    _uiState.update { it.copy(activeChannel = null) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to leave channel", e)
            }
        }
    }

    fun setActiveChannel(channelId: String) {
        viewModelScope.launch {
            // Jan 2026 FIX: Search in BOTH local channels AND discovered channels
            // Previously only searched local channels, causing discovered channel joins to fail
            val channel = _uiState.value.channels.find { it.id == channelId }
                ?: _uiState.value.discoveredChannels.find { it.id == channelId }

            Log.d(TAG, "setActiveChannel: $channelId, found=${channel?.name ?: "null"}, joined=${_uiState.value.joinedChannelIds.contains(channelId)}")

            if (channel != null && _uiState.value.joinedChannelIds.contains(channelId)) {
                _uiState.update { it.copy(activeChannel = channel) }
                Log.d(TAG, "setActiveChannel: UI state updated to ${channel.name}")

                // CRITICAL: Sync with PTTManager so audio transmission works
                pttChannelRepository.channels.first().find { it.shortId == channelId }?.let { pttChannel ->
                    pttManager.setActiveChannel(pttChannel)
                    Log.i(TAG, "setActiveChannel: PTTManager synced to ${pttChannel.name}")
                } ?: run {
                    Log.e(TAG, "setActiveChannel: Channel not found in repository! channelId=$channelId")
                }
            } else {
                Log.w(TAG, "setActiveChannel: Failed - channel=${channel?.name}, joined=${_uiState.value.joinedChannelIds}")
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

    fun startTransmit() {
        viewModelScope.launch {
            try {
                val channel = pttManager.activeChannel.value
                Log.d(TAG, "startTransmit: activeChannel=${channel?.name ?: "NULL"}")

                if (channel == null) {
                    Log.w(TAG, "startTransmit: No active channel in PTTManager!")
                    // Try to get from UI state and set it
                    _uiState.value.activeChannel?.let { uiChannel ->
                        Log.d(TAG, "startTransmit: Setting active channel from UI: ${uiChannel.name}")
                        setActiveChannel(uiChannel.id)
                    }
                    return@launch
                }

                Log.i(TAG, "startTransmit: Requesting floor for ${channel.name}")
                pttManager.requestFloor(channel)
                _uiState.update { it.copy(isTransmitting = true) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start transmit", e)
            }
        }
    }

    fun stopTransmit() {
        viewModelScope.launch {
            try {
                val channel = pttManager.activeChannel.value
                Log.d(TAG, "stopTransmit: activeChannel=${channel?.name ?: "NULL"}")

                channel?.let {
                    Log.i(TAG, "stopTransmit: Releasing floor for ${it.name}")
                    pttManager.releaseFloor(it)
                }
                _uiState.update { it.copy(isTransmitting = false) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop transmit", e)
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
    val discoveredChannels: List<ChannelUiModel> = emptyList(),  // Jan 2026: Channels from other devices
    val joinedChannelIds: Set<String> = emptySet(),
    val activeChannel: ChannelUiModel? = null,
    val isTransmitting: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null
)
