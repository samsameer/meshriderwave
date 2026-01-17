/*
 * Mesh Rider Wave - Main ViewModel
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 */

package com.doodlelabs.meshriderwave.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.doodlelabs.meshriderwave.core.network.MeshNetworkManager
import com.doodlelabs.meshriderwave.domain.model.Contact
import com.doodlelabs.meshriderwave.domain.repository.ContactRepository
import com.doodlelabs.meshriderwave.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MainUiState(
    val username: String = "User",
    val nightMode: Boolean = true,
    val isServiceRunning: Boolean = false,
    val localAddresses: List<String> = emptyList(),
    val contacts: List<Contact> = emptyList(),
    val isLoading: Boolean = true,
    val publicKeyBase64: String = "",
    val deviceId: String = "",
    // Video/Audio settings
    val videoHwAccel: Boolean = true,
    val hardwareAEC: Boolean = true,
    // UI settings
    val vibrateOnCall: Boolean = true,
    val autoAcceptCalls: Boolean = false,
    // Additional settings
    val locationSharing: Boolean = true,
    val sosEnabled: Boolean = true,
    val autoReconnect: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val pttVibration: Boolean = true
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val contactRepository: ContactRepository,
    private val meshNetworkManager: MeshNetworkManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
        observeContacts()
        observeNetworkState()
        initializeKeys()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            combine(
                settingsRepository.username,
                settingsRepository.nightMode,
                settingsRepository.videoHwAccel,
                settingsRepository.disableAudioProcessing,
                settingsRepository.vibrateOnCall,
                settingsRepository.autoAcceptCalls,
                settingsRepository.locationSharing,
                settingsRepository.sosEnabled,
                settingsRepository.autoReconnect,
                settingsRepository.notificationsEnabled,
                settingsRepository.pttVibration
            ) { values ->
                _uiState.update {
                    it.copy(
                        username = values[0] as String,
                        nightMode = values[1] as Boolean,
                        videoHwAccel = values[2] as Boolean,
                        hardwareAEC = !(values[3] as Boolean), // disableAudioProcessing -> inverse for AEC
                        vibrateOnCall = values[4] as Boolean,
                        autoAcceptCalls = values[5] as Boolean,
                        locationSharing = values[6] as Boolean,
                        sosEnabled = values[7] as Boolean,
                        autoReconnect = values[8] as Boolean,
                        notificationsEnabled = values[9] as Boolean,
                        pttVibration = values[10] as Boolean,
                        isLoading = false
                    )
                }
            }.collect()
        }
    }

    private fun observeContacts() {
        viewModelScope.launch {
            contactRepository.contacts.collect { contacts ->
                _uiState.update { it.copy(contacts = contacts) }
            }
        }
    }

    private fun observeNetworkState() {
        viewModelScope.launch {
            combine(
                meshNetworkManager.isRunning,
                meshNetworkManager.localAddresses
            ) { running, addresses ->
                _uiState.update {
                    it.copy(
                        isServiceRunning = running,
                        localAddresses = addresses
                    )
                }
            }.collect()
        }
    }

    private fun initializeKeys() {
        viewModelScope.launch {
            val keyPair = settingsRepository.getOrCreateKeyPair()
            // Pass keys to network manager
            meshNetworkManager.ownPublicKey = keyPair.publicKey
            meshNetworkManager.ownSecretKey = keyPair.secretKey

            // Update UI state with key info
            val publicKeyBase64 = android.util.Base64.encodeToString(
                keyPair.publicKey,
                android.util.Base64.NO_WRAP
            )
            val deviceId = keyPair.publicKey.take(8).joinToString("") {
                String.format("%02X", it)
            }

            _uiState.update {
                it.copy(
                    publicKeyBase64 = publicKeyBase64,
                    deviceId = deviceId
                )
            }
        }
    }

    fun setUsername(name: String) {
        viewModelScope.launch {
            settingsRepository.setUsername(name)
        }
    }

    // Settings setters
    fun setNightMode(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setNightMode(enabled)
        }
    }

    fun setVideoHwAccel(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setVideoHwAccel(enabled)
        }
    }

    fun setHardwareAEC(enabled: Boolean) {
        viewModelScope.launch {
            // Inverse: AEC enabled = audio processing NOT disabled
            settingsRepository.setDisableAudioProcessing(!enabled)
        }
    }

    fun setVibrateOnCall(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setVibrateOnCall(enabled)
        }
    }

    fun setAutoAcceptCalls(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAutoAcceptCalls(enabled)
        }
    }

    fun setLocationSharing(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setLocationSharing(enabled)
        }
    }

    fun setSosEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setSosEnabled(enabled)
        }
    }

    fun setAutoReconnect(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAutoReconnect(enabled)
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setNotificationsEnabled(enabled)
        }
    }

    fun setPttVibration(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setPttVibration(enabled)
        }
    }

    fun addContact(contact: Contact) {
        viewModelScope.launch {
            contactRepository.addContact(contact)
        }
    }

    fun deleteContact(publicKey: ByteArray) {
        viewModelScope.launch {
            contactRepository.deleteContact(publicKey)
        }
    }
}
