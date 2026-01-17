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
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    companion object {
        private const val TAG = "MeshRider:ChannelsVM"
    }

    private val _uiState = MutableStateFlow(ChannelsUiState())
    val uiState: StateFlow<ChannelsUiState> = _uiState.asStateFlow()

    init {
        observeChannels()
        observePTTState()
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
                    setActiveChannel(channel.shortId)
                }.onFailure { e ->
                    Log.e(TAG, "Failed to create channel", e)
                    _uiState.update { it.copy(error = e.message) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create channel", e)
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
            val channel = _uiState.value.channels.find { it.id == channelId }
            if (channel != null && _uiState.value.joinedChannelIds.contains(channelId)) {
                _uiState.update { it.copy(activeChannel = channel) }
            }
        }
    }

    fun startTransmit() {
        viewModelScope.launch {
            try {
                pttManager.activeChannel.value?.let { channel ->
                    pttManager.requestFloor(channel)
                }
                _uiState.update { it.copy(isTransmitting = true) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start transmit", e)
            }
        }
    }

    fun stopTransmit() {
        viewModelScope.launch {
            try {
                pttManager.activeChannel.value?.let { channel ->
                    pttManager.releaseFloor(channel)
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

    private fun String.hexToByteArray(): ByteArray =
        chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}

data class ChannelsUiState(
    val channels: List<ChannelUiModel> = emptyList(),
    val joinedChannelIds: Set<String> = emptySet(),
    val activeChannel: ChannelUiModel? = null,
    val isTransmitting: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null
)
