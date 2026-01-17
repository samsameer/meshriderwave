/*
 * Mesh Rider Wave - Group Detail ViewModel
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 *
 * ViewModel for group detail screen with member management
 */

package com.doodlelabs.meshriderwave.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.doodlelabs.meshriderwave.domain.model.group.Group
import com.doodlelabs.meshriderwave.domain.model.group.GroupMember
import com.doodlelabs.meshriderwave.domain.model.group.MemberRole
import com.doodlelabs.meshriderwave.domain.repository.GroupRepository
import com.doodlelabs.meshriderwave.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GroupDetailViewModel @Inject constructor(
    private val groupRepository: GroupRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    companion object {
        private const val TAG = "MeshRider:GroupDetailVM"
    }

    private val _uiState = MutableStateFlow(GroupDetailUiState())
    val uiState: StateFlow<GroupDetailUiState> = _uiState.asStateFlow()

    private var currentGroupId: String? = null

    fun loadGroup(groupId: String) {
        if (currentGroupId == groupId && _uiState.value.group != null) {
            return // Already loaded
        }
        currentGroupId = groupId

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            try {
                val groupIdBytes = groupId.hexToByteArray()
                val group = groupRepository.getGroup(groupIdBytes)

                if (group != null) {
                    Log.i(TAG, "Loaded group: ${group.name} with ${group.members.size} members")
                    val keyPair = settingsRepository.getOrCreateKeyPair()
                    val myMember = group.members.find { it.publicKey.contentEquals(keyPair.publicKey) }
                    // Admin OR Owner can manage group (owner is creator)
                    val isAdmin = myMember?.role == MemberRole.ADMIN || myMember?.role == MemberRole.OWNER

                    _uiState.update {
                        it.copy(
                            group = group,
                            memberCount = group.members.size,
                            members = group.members,
                            inviteCode = generateInviteCode(group),
                            isAdmin = isAdmin,
                            isLoading = false
                        )
                    }

                    // Observe group changes
                    observeGroupUpdates(groupIdBytes)
                } else {
                    Log.w(TAG, "Group not found: $groupId")
                    _uiState.update {
                        it.copy(
                            error = "Group not found",
                            isLoading = false
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load group", e)
                _uiState.update {
                    it.copy(
                        error = e.message ?: "Failed to load group",
                        isLoading = false
                    )
                }
            }
        }
    }

    private fun observeGroupUpdates(groupIdBytes: ByteArray) {
        viewModelScope.launch {
            groupRepository.groups.collect { groups ->
                val group = groups.find { it.groupId.contentEquals(groupIdBytes) }
                if (group != null) {
                    _uiState.update {
                        it.copy(
                            group = group,
                            memberCount = group.members.size,
                            members = group.members,
                            inviteCode = generateInviteCode(group)
                        )
                    }
                }
            }
        }
    }

    private fun generateInviteCode(group: Group): String {
        // Generate invite code from group ID and epoch
        // Format: GROUPID_EPOCH (simplified for now)
        return "${group.hexId.take(12).uppercase()}-${group.epoch}"
    }

    fun leaveGroup() {
        val group = _uiState.value.group ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            try {
                val keyPair = settingsRepository.getOrCreateKeyPair()
                groupRepository.removeMember(
                    groupId = group.groupId,
                    memberPublicKey = keyPair.publicKey
                )
                Log.i(TAG, "Left group: ${group.name}")
                _uiState.update { it.copy(leftGroup = true, isLoading = false) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to leave group", e)
                _uiState.update {
                    it.copy(
                        error = e.message ?: "Failed to leave group",
                        isLoading = false
                    )
                }
            }
        }
    }

    fun addMember(memberPublicKey: ByteArray, memberName: String) {
        val group = _uiState.value.group ?: return

        viewModelScope.launch {
            try {
                val member = GroupMember(
                    publicKey = memberPublicKey,
                    name = memberName
                )
                groupRepository.addMember(
                    groupId = group.groupId,
                    member = member
                )
                Log.i(TAG, "Added member: $memberName")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add member", e)
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun removeMember(memberPublicKey: ByteArray) {
        val group = _uiState.value.group ?: return

        viewModelScope.launch {
            try {
                groupRepository.removeMember(
                    groupId = group.groupId,
                    memberPublicKey = memberPublicKey
                )
                Log.i(TAG, "Removed member")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to remove member", e)
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun deleteGroup() {
        val group = _uiState.value.group ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            try {
                groupRepository.deleteGroup(group.groupId)
                Log.i(TAG, "Deleted group: ${group.name}")
                _uiState.update { it.copy(leftGroup = true, isLoading = false) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete group", e)
                _uiState.update {
                    it.copy(
                        error = e.message ?: "Failed to delete group",
                        isLoading = false
                    )
                }
            }
        }
    }

    fun updateGroup(name: String, description: String) {
        val group = _uiState.value.group ?: return

        viewModelScope.launch {
            try {
                val updatedGroup = group.copy(
                    name = name,
                    description = description
                )
                groupRepository.updateGroup(updatedGroup)
                Log.i(TAG, "Updated group: $name")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update group", e)
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun String.hexToByteArray(): ByteArray {
        return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}

data class GroupDetailUiState(
    val group: Group? = null,
    val memberCount: Int = 0,
    val members: List<GroupMember> = emptyList(),
    val inviteCode: String = "",
    val isAdmin: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null,
    val leftGroup: Boolean = false
)
