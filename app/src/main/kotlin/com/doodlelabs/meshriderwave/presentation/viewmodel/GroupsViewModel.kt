/*
 * Mesh Rider Wave - Groups ViewModel
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 *
 * ViewModel for managing encrypted groups with MLS protocol
 */

package com.doodlelabs.meshriderwave.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.doodlelabs.meshriderwave.core.crypto.CryptoManager
import com.doodlelabs.meshriderwave.domain.repository.GroupRepository
import com.doodlelabs.meshriderwave.domain.repository.SettingsRepository
import com.doodlelabs.meshriderwave.presentation.ui.screens.groups.GroupUiModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GroupsViewModel @Inject constructor(
    private val groupRepository: GroupRepository,
    private val settingsRepository: SettingsRepository,
    private val cryptoManager: CryptoManager
) : ViewModel() {

    companion object {
        private const val TAG = "MeshRider:GroupsVM"
    }

    private val _uiState = MutableStateFlow(GroupsUiState())
    val uiState: StateFlow<GroupsUiState> = _uiState.asStateFlow()

    init {
        observeGroups()
    }

    private fun observeGroups() {
        viewModelScope.launch {
            // Use activeGroups to only show ACTIVE groups (excludes DELETED and ARCHIVED)
            groupRepository.activeGroups.collect { groups ->
                val uiModels = groups.map { group ->
                    GroupUiModel(
                        id = group.groupId.toHexString(),
                        name = group.name,
                        memberCount = group.members.size,
                        unreadCount = 0,
                        hasActiveCall = false,
                        callParticipants = 0
                    )
                }
                _uiState.update { it.copy(groups = uiModels, isLoading = false) }
            }
        }
    }

    fun createGroup(name: String, description: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            try {
                val keyPair = settingsRepository.getOrCreateKeyPair()
                groupRepository.createGroup(
                    name = name,
                    description = description,
                    creatorPublicKey = keyPair.publicKey
                ).onSuccess { group ->
                    Log.i(TAG, "Created group: ${group.name}")
                }.onFailure { e ->
                    Log.e(TAG, "Failed to create group", e)
                    _uiState.update { it.copy(error = e.message) }
                }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun joinGroup(inviteCode: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            try {
                // Parse invite code/URL and join group
                Log.i(TAG, "Joining group with invite code: $inviteCode")

                val invite = com.doodlelabs.meshriderwave.domain.model.group.Group.fromInviteData(inviteCode)

                if (invite != null) {
                    val keyPair = settingsRepository.getOrCreateKeyPair()
                    val username = settingsRepository.username.first()

                    groupRepository.acceptInvite(
                        invite = invite,
                        joinerPublicKey = keyPair.publicKey,
                        joinerName = username
                    ).onSuccess { group ->
                        Log.i(TAG, "Successfully joined group: ${group.name}")
                    }.onFailure { e ->
                        Log.e(TAG, "Failed to join group", e)
                        _uiState.update { it.copy(error = e.message ?: "Failed to join group") }
                    }
                } else {
                    Log.e(TAG, "Invalid invite code format")
                    _uiState.update { it.copy(error = "Invalid invite code. Use QR scanner for best results.") }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to join group", e)
                _uiState.update { it.copy(error = e.message ?: "Failed to join group") }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun leaveGroup(groupId: String) {
        viewModelScope.launch {
            try {
                val keyPair = settingsRepository.getOrCreateKeyPair()
                groupRepository.removeMember(
                    groupId = groupId.hexToByteArray(),
                    memberPublicKey = keyPair.publicKey
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to leave group", e)
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun ByteArray.toHexString(): String =
        joinToString("") { "%02x".format(it) }

    private fun String.hexToByteArray(): ByteArray =
        chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}

data class GroupsUiState(
    val groups: List<GroupUiModel> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)
