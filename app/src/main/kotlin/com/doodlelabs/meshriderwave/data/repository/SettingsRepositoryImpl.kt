/*
 * Mesh Rider Wave - Settings Repository Implementation
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 */

package com.doodlelabs.meshriderwave.data.repository

import com.doodlelabs.meshriderwave.core.crypto.CryptoManager
import com.doodlelabs.meshriderwave.data.local.SettingsDataStore
import com.doodlelabs.meshriderwave.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val settingsDataStore: SettingsDataStore
) : SettingsRepository {

    override val username: Flow<String> = settingsDataStore.username
    override val publicKey: Flow<ByteArray?> = settingsDataStore.publicKey
    override val secretKey: Flow<ByteArray?> = settingsDataStore.secretKey
    override val nightMode: Flow<Boolean> = settingsDataStore.nightMode
    override val isFirstRun: Flow<Boolean> = settingsDataStore.isFirstRun

    // Video/Audio settings
    override val videoHwAccel: Flow<Boolean> = settingsDataStore.videoHwAccel
    override val disableAudioProcessing: Flow<Boolean> = settingsDataStore.disableAudioProcessing

    // UI settings
    override val vibrateOnCall: Flow<Boolean> = settingsDataStore.vibrateOnCall
    override val autoAcceptCalls: Flow<Boolean> = settingsDataStore.autoAcceptCalls

    // Additional settings
    override val locationSharing: Flow<Boolean> = settingsDataStore.locationSharing
    override val sosEnabled: Flow<Boolean> = settingsDataStore.sosEnabled
    override val autoReconnect: Flow<Boolean> = settingsDataStore.autoReconnect
    override val notificationsEnabled: Flow<Boolean> = settingsDataStore.notificationsEnabled
    override val pttVibration: Flow<Boolean> = settingsDataStore.pttVibration

    override suspend fun getOrCreateKeyPair(): CryptoManager.KeyPair {
        return settingsDataStore.getOrCreateKeyPair()
    }

    override suspend fun setUsername(name: String) {
        settingsDataStore.setUsername(name)
    }

    override suspend fun setFirstRunComplete() {
        settingsDataStore.setFirstRunComplete()
    }

    // Settings setters
    override suspend fun setNightMode(enabled: Boolean) {
        settingsDataStore.setNightMode(enabled)
    }

    override suspend fun setVideoHwAccel(enabled: Boolean) {
        settingsDataStore.setVideoHwAccel(enabled)
    }

    override suspend fun setDisableAudioProcessing(disabled: Boolean) {
        settingsDataStore.setDisableAudioProcessing(disabled)
    }

    override suspend fun setVibrateOnCall(enabled: Boolean) {
        settingsDataStore.setVibrateOnCall(enabled)
    }

    override suspend fun setAutoAcceptCalls(enabled: Boolean) {
        settingsDataStore.setAutoAcceptCalls(enabled)
    }

    override suspend fun setLocationSharing(enabled: Boolean) {
        settingsDataStore.setLocationSharing(enabled)
    }

    override suspend fun setSosEnabled(enabled: Boolean) {
        settingsDataStore.setSosEnabled(enabled)
    }

    override suspend fun setAutoReconnect(enabled: Boolean) {
        settingsDataStore.setAutoReconnect(enabled)
    }

    override suspend fun setNotificationsEnabled(enabled: Boolean) {
        settingsDataStore.setNotificationsEnabled(enabled)
    }

    override suspend fun setPttVibration(enabled: Boolean) {
        settingsDataStore.setPttVibration(enabled)
    }
}
