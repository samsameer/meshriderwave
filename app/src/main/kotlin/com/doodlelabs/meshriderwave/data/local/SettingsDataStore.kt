/*
 * Mesh Rider Wave - Settings DataStore
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 *
 * SECURITY: Jan 2026 - Secret keys encrypted using Android Keystore
 * Public keys stored in DataStore (non-sensitive)
 * Secret keys encrypted with AES-256-GCM before storage
 */

package com.doodlelabs.meshriderwave.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import com.doodlelabs.meshriderwave.core.crypto.CryptoManager
import com.doodlelabs.meshriderwave.core.util.logD
import com.doodlelabs.meshriderwave.core.util.logE
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>,
    private val cryptoManager: CryptoManager
) {
    // Keys
    private object Keys {
        val USERNAME = stringPreferencesKey("username")
        val PUBLIC_KEY = stringPreferencesKey("public_key")
        val SECRET_KEY_ENCRYPTED = stringPreferencesKey("secret_key_encrypted")
        val SECRET_KEY_IV = stringPreferencesKey("secret_key_iv")
        // Legacy key for migration
        val SECRET_KEY_LEGACY = stringPreferencesKey("secret_key")
        val CONNECT_TIMEOUT = intPreferencesKey("connect_timeout")
        val CONNECT_RETRIES = intPreferencesKey("connect_retries")
        val GUESS_EUI64 = booleanPreferencesKey("guess_eui64")
        val USE_NEIGHBOR_TABLE = booleanPreferencesKey("use_neighbor_table")
        val VIDEO_HW_ACCEL = booleanPreferencesKey("video_hw_accel")
        val DISABLE_AUDIO_PROCESSING = booleanPreferencesKey("disable_audio_processing")
        val NIGHT_MODE = booleanPreferencesKey("night_mode")
        val VIBRATE_ON_CALL = booleanPreferencesKey("vibrate_on_call")
        val AUTO_ACCEPT_CALLS = booleanPreferencesKey("auto_accept_calls")
        val FIRST_RUN = booleanPreferencesKey("first_run")
        // Additional settings for full persistence
        val LOCATION_SHARING = booleanPreferencesKey("location_sharing")
        val SOS_ENABLED = booleanPreferencesKey("sos_enabled")
        val AUTO_RECONNECT = booleanPreferencesKey("auto_reconnect")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val PTT_VIBRATION = booleanPreferencesKey("ptt_vibration")
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "meshrider_key_encryption"
        private const val GCM_TAG_LENGTH = 128
    }

    // Username
    val username: Flow<String> = dataStore.data.map { it[Keys.USERNAME] ?: "User" }

    suspend fun setUsername(name: String) {
        dataStore.edit { it[Keys.USERNAME] = name }
    }

    // Keys
    val publicKey: Flow<ByteArray?> = dataStore.data.map { prefs ->
        prefs[Keys.PUBLIC_KEY]?.let { Base64.decode(it, Base64.NO_WRAP) }
    }

    /**
     * Get secret key (decrypted using Android Keystore)
     * SECURITY: Secret key never stored in plaintext
     */
    val secretKey: Flow<ByteArray?> = dataStore.data.map { prefs ->
        val encryptedKey = prefs[Keys.SECRET_KEY_ENCRYPTED]
        val iv = prefs[Keys.SECRET_KEY_IV]

        if (encryptedKey != null && iv != null) {
            try {
                decryptSecretKey(
                    Base64.decode(encryptedKey, Base64.NO_WRAP),
                    Base64.decode(iv, Base64.NO_WRAP)
                )
            } catch (e: Exception) {
                logE("Failed to decrypt secret key", e)
                null
            }
        } else {
            // Check for legacy unencrypted key and migrate
            prefs[Keys.SECRET_KEY_LEGACY]?.let { legacyKey ->
                logD("Migrating legacy secret key to encrypted storage")
                Base64.decode(legacyKey, Base64.NO_WRAP)
            }
        }
    }

    suspend fun getOrCreateKeyPair(): CryptoManager.KeyPair {
        val prefs = dataStore.data.first()
        val existingPublic = prefs[Keys.PUBLIC_KEY]
        val existingEncrypted = prefs[Keys.SECRET_KEY_ENCRYPTED]
        val existingIv = prefs[Keys.SECRET_KEY_IV]
        val legacySecret = prefs[Keys.SECRET_KEY_LEGACY]

        // Check for existing encrypted keys
        if (existingPublic != null && existingEncrypted != null && existingIv != null) {
            try {
                val decryptedSecret = decryptSecretKey(
                    Base64.decode(existingEncrypted, Base64.NO_WRAP),
                    Base64.decode(existingIv, Base64.NO_WRAP)
                )
                return CryptoManager.KeyPair(
                    Base64.decode(existingPublic, Base64.NO_WRAP),
                    decryptedSecret
                )
            } catch (e: Exception) {
                logE("Failed to decrypt existing key, regenerating", e)
            }
        }

        // Migrate legacy unencrypted key
        if (existingPublic != null && legacySecret != null) {
            logD("Migrating legacy key pair to encrypted storage")
            val secretKeyBytes = Base64.decode(legacySecret, Base64.NO_WRAP)
            val (encryptedKey, iv) = encryptSecretKey(secretKeyBytes)

            dataStore.edit { prefs ->
                prefs[Keys.SECRET_KEY_ENCRYPTED] = Base64.encodeToString(encryptedKey, Base64.NO_WRAP)
                prefs[Keys.SECRET_KEY_IV] = Base64.encodeToString(iv, Base64.NO_WRAP)
                prefs.remove(Keys.SECRET_KEY_LEGACY) // Remove legacy plaintext key
            }

            return CryptoManager.KeyPair(
                Base64.decode(existingPublic, Base64.NO_WRAP),
                secretKeyBytes
            )
        }

        // Generate new key pair
        val keyPair = cryptoManager.generateKeyPair()
        val (encryptedKey, iv) = encryptSecretKey(keyPair.secretKey)

        dataStore.edit { prefs ->
            prefs[Keys.PUBLIC_KEY] = Base64.encodeToString(keyPair.publicKey, Base64.NO_WRAP)
            prefs[Keys.SECRET_KEY_ENCRYPTED] = Base64.encodeToString(encryptedKey, Base64.NO_WRAP)
            prefs[Keys.SECRET_KEY_IV] = Base64.encodeToString(iv, Base64.NO_WRAP)
        }
        return keyPair
    }

    // =========================================================================
    // ANDROID KEYSTORE ENCRYPTION (Military-Grade Security)
    // =========================================================================

    /**
     * Get or create encryption key in Android Keystore
     * Key is hardware-backed on devices with Secure Element
     */
    private fun getOrCreateKeystoreKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

        // Return existing key if present
        keyStore.getKey(KEY_ALIAS, null)?.let { return it as SecretKey }

        // Generate new key in Keystore
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )

        val keySpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(false) // Don't require biometric (would lock out on reboot)
            .build()

        keyGenerator.init(keySpec)
        return keyGenerator.generateKey()
    }

    /**
     * Encrypt secret key using Android Keystore
     * Returns (encryptedData, iv)
     */
    private fun encryptSecretKey(secretKey: ByteArray): Pair<ByteArray, ByteArray> {
        val keystoreKey = getOrCreateKeystoreKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, keystoreKey)

        val encryptedData = cipher.doFinal(secretKey)
        val iv = cipher.iv

        return Pair(encryptedData, iv)
    }

    /**
     * Decrypt secret key using Android Keystore
     */
    private fun decryptSecretKey(encryptedData: ByteArray, iv: ByteArray): ByteArray {
        val keystoreKey = getOrCreateKeystoreKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, keystoreKey, spec)

        return cipher.doFinal(encryptedData)
    }

    // Network settings
    val connectTimeout: Flow<Int> = dataStore.data.map { it[Keys.CONNECT_TIMEOUT] ?: 5000 }
    val connectRetries: Flow<Int> = dataStore.data.map { it[Keys.CONNECT_RETRIES] ?: 3 }
    val guessEUI64: Flow<Boolean> = dataStore.data.map { it[Keys.GUESS_EUI64] ?: false }
    val useNeighborTable: Flow<Boolean> = dataStore.data.map { it[Keys.USE_NEIGHBOR_TABLE] ?: false }

    suspend fun setNetworkSettings(timeout: Int, retries: Int, eui64: Boolean, neighbor: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.CONNECT_TIMEOUT] = timeout
            prefs[Keys.CONNECT_RETRIES] = retries
            prefs[Keys.GUESS_EUI64] = eui64
            prefs[Keys.USE_NEIGHBOR_TABLE] = neighbor
        }
    }

    // Video/Audio settings
    val videoHwAccel: Flow<Boolean> = dataStore.data.map { it[Keys.VIDEO_HW_ACCEL] ?: true }
    val disableAudioProcessing: Flow<Boolean> = dataStore.data.map { it[Keys.DISABLE_AUDIO_PROCESSING] ?: false }

    // UI settings
    val nightMode: Flow<Boolean> = dataStore.data.map { it[Keys.NIGHT_MODE] ?: true }
    val vibrateOnCall: Flow<Boolean> = dataStore.data.map { it[Keys.VIBRATE_ON_CALL] ?: true }
    val autoAcceptCalls: Flow<Boolean> = dataStore.data.map { it[Keys.AUTO_ACCEPT_CALLS] ?: false }

    suspend fun setNightMode(enabled: Boolean) {
        dataStore.edit { it[Keys.NIGHT_MODE] = enabled }
    }

    suspend fun setVibrateOnCall(enabled: Boolean) {
        dataStore.edit { it[Keys.VIBRATE_ON_CALL] = enabled }
    }

    suspend fun setAutoAcceptCalls(enabled: Boolean) {
        dataStore.edit { it[Keys.AUTO_ACCEPT_CALLS] = enabled }
    }

    // Video/Audio settings setters
    suspend fun setVideoHwAccel(enabled: Boolean) {
        dataStore.edit { it[Keys.VIDEO_HW_ACCEL] = enabled }
    }

    suspend fun setDisableAudioProcessing(disabled: Boolean) {
        dataStore.edit { it[Keys.DISABLE_AUDIO_PROCESSING] = disabled }
    }

    // Additional settings flows
    val locationSharing: Flow<Boolean> = dataStore.data.map { it[Keys.LOCATION_SHARING] ?: true }
    val sosEnabled: Flow<Boolean> = dataStore.data.map { it[Keys.SOS_ENABLED] ?: true }
    val autoReconnect: Flow<Boolean> = dataStore.data.map { it[Keys.AUTO_RECONNECT] ?: true }
    val notificationsEnabled: Flow<Boolean> = dataStore.data.map { it[Keys.NOTIFICATIONS_ENABLED] ?: true }
    val pttVibration: Flow<Boolean> = dataStore.data.map { it[Keys.PTT_VIBRATION] ?: true }

    // Additional settings setters
    suspend fun setLocationSharing(enabled: Boolean) {
        dataStore.edit { it[Keys.LOCATION_SHARING] = enabled }
    }

    suspend fun setSosEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.SOS_ENABLED] = enabled }
    }

    suspend fun setAutoReconnect(enabled: Boolean) {
        dataStore.edit { it[Keys.AUTO_RECONNECT] = enabled }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.NOTIFICATIONS_ENABLED] = enabled }
    }

    suspend fun setPttVibration(enabled: Boolean) {
        dataStore.edit { it[Keys.PTT_VIBRATION] = enabled }
    }

    // First run
    val isFirstRun: Flow<Boolean> = dataStore.data.map { it[Keys.FIRST_RUN] ?: true }

    suspend fun setFirstRunComplete() {
        dataStore.edit { it[Keys.FIRST_RUN] = false }
    }
}
