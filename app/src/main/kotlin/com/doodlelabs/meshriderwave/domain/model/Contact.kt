/*
 * Mesh Rider Wave - Domain Model: Contact
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 *
 * Cryptographic identity-based contact (not phone number based)
 */

package com.doodlelabs.meshriderwave.domain.model

import kotlinx.serialization.Serializable

/**
 * Contact identified by cryptographic public key (Ed25519)
 * Based on Meshenger's proven identity model
 */
@Serializable
data class Contact(
    val publicKey: ByteArray,           // Ed25519 public key (32 bytes)
    val name: String,                   // Display name
    val addresses: List<String> = emptyList(),  // IP addresses / hostnames
    val blocked: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val lastSeenAt: Long? = null,
    val lastWorkingAddress: String? = null
) {
    /**
     * Contact state for UI
     */
    enum class State {
        UNKNOWN,
        ONLINE,
        OFFLINE,
        BUSY
    }

    /**
     * Derive device ID from public key (first 8 bytes hex)
     */
    val deviceId: String
        get() = publicKey.take(8).joinToString("") { "%02x".format(it) }

    /**
     * Short ID for display (first 4 bytes)
     */
    val shortId: String
        get() = deviceId.take(8).uppercase()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Contact) return false
        return publicKey.contentEquals(other.publicKey)
    }

    override fun hashCode(): Int {
        return publicKey.contentHashCode()
    }

    companion object {
        const val PUBLIC_KEY_SIZE = 32

        /**
         * Create contact from QR code data
         */
        fun fromQrData(data: String): Contact? {
            return try {
                // Format: name|base64PublicKey|address1,address2,...
                val parts = data.split("|")
                if (parts.size < 2) return null

                val name = parts[0]
                val publicKey = android.util.Base64.decode(parts[1], android.util.Base64.NO_WRAP)
                val addresses = if (parts.size > 2) parts[2].split(",") else emptyList()

                if (publicKey.size != PUBLIC_KEY_SIZE) return null

                Contact(
                    publicKey = publicKey,
                    name = name,
                    addresses = addresses
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Export to QR code data format
     */
    fun toQrData(): String {
        val encodedKey = android.util.Base64.encodeToString(publicKey, android.util.Base64.NO_WRAP)
        val addressList = addresses.joinToString(",")
        return "$name|$encodedKey|$addressList"
    }
}
