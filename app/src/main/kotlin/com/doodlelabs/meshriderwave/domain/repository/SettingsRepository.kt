/*
 * Mesh Rider Wave - Settings Repository Interface
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 */

package com.doodlelabs.meshriderwave.domain.repository

import com.doodlelabs.meshriderwave.core.crypto.CryptoManager
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val username: Flow<String>
    val publicKey: Flow<ByteArray?>
    val secretKey: Flow<ByteArray?>
    val nightMode: Flow<Boolean>
    val isFirstRun: Flow<Boolean>

    // Video/Audio settings
    val videoHwAccel: Flow<Boolean>
    val disableAudioProcessing: Flow<Boolean>

    // UI settings
    val vibrateOnCall: Flow<Boolean>
    val autoAcceptCalls: Flow<Boolean>

    // Additional settings
    val locationSharing: Flow<Boolean>
    val sosEnabled: Flow<Boolean>
    val autoReconnect: Flow<Boolean>
    val notificationsEnabled: Flow<Boolean>
    val pttVibration: Flow<Boolean>

    suspend fun getOrCreateKeyPair(): CryptoManager.KeyPair
    suspend fun setUsername(name: String)
    suspend fun setFirstRunComplete()

    // Settings setters
    suspend fun setNightMode(enabled: Boolean)
    suspend fun setVideoHwAccel(enabled: Boolean)
    suspend fun setDisableAudioProcessing(disabled: Boolean)
    suspend fun setVibrateOnCall(enabled: Boolean)
    suspend fun setAutoAcceptCalls(enabled: Boolean)
    suspend fun setLocationSharing(enabled: Boolean)
    suspend fun setSosEnabled(enabled: Boolean)
    suspend fun setAutoReconnect(enabled: Boolean)
    suspend fun setNotificationsEnabled(enabled: Boolean)
    suspend fun setPttVibration(enabled: Boolean)
}
