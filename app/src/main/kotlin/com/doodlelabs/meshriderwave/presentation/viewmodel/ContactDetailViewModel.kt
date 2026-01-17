/*
 * Mesh Rider Wave - Contact Detail ViewModel
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 *
 * ViewModel for contact details screen
 */

package com.doodlelabs.meshriderwave.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.doodlelabs.meshriderwave.core.network.PeerDiscoveryManager
import com.doodlelabs.meshriderwave.domain.model.Contact
import com.doodlelabs.meshriderwave.domain.repository.ContactRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@HiltViewModel
class ContactDetailViewModel @Inject constructor(
    private val contactRepository: ContactRepository,
    private val peerDiscoveryManager: PeerDiscoveryManager
) : ViewModel() {

    companion object {
        private const val TAG = "MeshRider:ContactDetail"
    }

    private val _uiState = MutableStateFlow(ContactDetailUiState())
    val uiState: StateFlow<ContactDetailUiState> = _uiState.asStateFlow()

    private var publicKeyHex: String = ""

    fun loadContact(hexKey: String) {
        publicKeyHex = hexKey
        _uiState.update { it.copy(isLoading = true) }

        viewModelScope.launch {
            try {
                // Convert hex to ByteArray
                val publicKey = hexKey.chunked(2)
                    .map { it.toInt(16).toByte() }
                    .toByteArray()

                // Find contact
                val contact = contactRepository.getContactByPublicKey(publicKey)

                if (contact != null) {
                    // Check if peer is online
                    val peer = peerDiscoveryManager.findPeerByPublicKey(publicKey)
                    val isOnline = peer?.isOnline == true

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            contact = contact,
                            isOnline = isOnline,
                            lastSeenFormatted = formatLastSeen(contact.lastSeenAt),
                            addedOnFormatted = formatDate(contact.createdAt)
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(isLoading = false, contact = null)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load contact", e)
                _uiState.update {
                    it.copy(isLoading = false, contact = null)
                }
            }
        }

        // Observe peer status changes
        viewModelScope.launch {
            peerDiscoveryManager.discoveredPeers.collect { peers ->
                val contact = _uiState.value.contact ?: return@collect
                val hexKey = contact.publicKey.joinToString("") { "%02x".format(it) }
                val peer = peers[hexKey]
                _uiState.update { it.copy(isOnline = peer?.isOnline == true) }
            }
        }
    }

    fun toggleBlock() {
        val contact = _uiState.value.contact ?: return

        viewModelScope.launch {
            try {
                contactRepository.setBlocked(contact.publicKey, !contact.blocked)

                // Reload contact
                val updatedContact = contactRepository.getContactByPublicKey(contact.publicKey)
                if (updatedContact != null) {
                    _uiState.update { it.copy(contact = updatedContact) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to toggle block", e)
            }
        }
    }

    fun showDeleteConfirmation() {
        _uiState.update { it.copy(showDeleteDialog = true) }
    }

    fun hideDeleteConfirmation() {
        _uiState.update { it.copy(showDeleteDialog = false) }
    }

    fun deleteContact() {
        val contact = _uiState.value.contact ?: return

        viewModelScope.launch {
            try {
                contactRepository.deleteContact(contact.publicKey)
                Log.i(TAG, "Contact deleted: ${contact.name}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete contact", e)
            }
        }
    }

    private fun formatLastSeen(timestamp: Long?): String {
        if (timestamp == null) return "Never"

        val now = System.currentTimeMillis()
        val diff = now - timestamp

        return when {
            diff < 60_000 -> "Just now"
            diff < 3600_000 -> "${diff / 60_000} minutes ago"
            diff < 86400_000 -> "${diff / 3600_000} hours ago"
            diff < 604800_000 -> "${diff / 86400_000} days ago"
            else -> formatDate(timestamp)
        }
    }

    private fun formatDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}

data class ContactDetailUiState(
    val isLoading: Boolean = true,
    val contact: Contact? = null,
    val isOnline: Boolean = false,
    val lastSeenFormatted: String = "",
    val addedOnFormatted: String = "",
    val showDeleteDialog: Boolean = false
)
