/*
 * Mesh Rider Wave - Cryptography Manager
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 *
 * Based on Meshenger's proven libsodium implementation
 * Ed25519 signatures + X25519 encryption (via crypto_box_seal)
 * Using Lazysodium-android library
 */

package com.doodlelabs.meshriderwave.core.crypto

import com.goterl.lazysodium.LazySodiumAndroid
import com.goterl.lazysodium.SodiumAndroid
import com.goterl.lazysodium.interfaces.Box
import com.goterl.lazysodium.interfaces.PwHash
import com.goterl.lazysodium.interfaces.SecretBox
import com.goterl.lazysodium.interfaces.Sign
import com.goterl.lazysodium.utils.Key
import com.goterl.lazysodium.utils.KeyPair as LazySodiumKeyPair
import java.nio.charset.StandardCharsets
import java.util.Arrays
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thread-safe cryptography manager using Lazysodium
 *
 * Key types:
 * - Ed25519 signing keys (crypto_sign) for identity
 * - X25519 keys (derived from Ed25519) for encryption
 */
@Singleton
class CryptoManager @Inject constructor() {

    private val sodium: SodiumAndroid = SodiumAndroid()
    private val lazySodium: LazySodiumAndroid = LazySodiumAndroid(sodium)

    /**
     * Generate new Ed25519 key pair
     */
    fun generateKeyPair(): KeyPair {
        val publicKey = ByteArray(Sign.PUBLICKEYBYTES)
        val secretKey = ByteArray(Sign.SECRETKEYBYTES)
        sodium.crypto_sign_keypair(publicKey, secretKey)
        return KeyPair(publicKey, secretKey)
    }

    /**
     * Encrypt message for recipient using anonymous box (sealed box)
     * - Signs message with own secret key
     * - Prepends own public key
     * - Encrypts with recipient's public key
     */
    fun encryptMessage(
        message: String,
        recipientPublicKey: ByteArray,
        ownPublicKey: ByteArray,
        ownSecretKey: ByteArray
    ): ByteArray? {
        val messageBytes = message.toByteArray(StandardCharsets.UTF_8)
        val signed = sign(messageBytes, ownSecretKey) ?: return null

        // Prepend sender's public key to signed message
        val data = ByteArray(ownPublicKey.size + signed.size)
        System.arraycopy(ownPublicKey, 0, data, 0, ownPublicKey.size)
        System.arraycopy(signed, 0, data, ownPublicKey.size, signed.size)

        return encrypt(data, recipientPublicKey)
    }

    /**
     * Decrypt message from sender
     * - Decrypts with own keys
     * - Extracts sender's public key
     * - Verifies signature
     */
    fun decryptMessage(
        ciphertext: ByteArray,
        senderPublicKeyOut: ByteArray,
        ownPublicKey: ByteArray,
        ownSecretKey: ByteArray
    ): String? {
        if (senderPublicKeyOut.size != Sign.PUBLICKEYBYTES) {
            return null
        }

        // Zero the output buffer
        Arrays.fill(senderPublicKeyOut, 0.toByte())

        val decrypted = decrypt(ciphertext, ownPublicKey, ownSecretKey) ?: return null
        if (decrypted.size <= senderPublicKeyOut.size) return null

        // Split: sender public key + signed content
        val signedContent = ByteArray(decrypted.size - senderPublicKeyOut.size)
        System.arraycopy(decrypted, 0, senderPublicKeyOut, 0, senderPublicKeyOut.size)
        System.arraycopy(decrypted, senderPublicKeyOut.size, signedContent, 0, signedContent.size)

        // Verify signature
        val unsigned = unsign(signedContent, senderPublicKeyOut) ?: return null
        return String(unsigned, StandardCharsets.UTF_8)
    }

    /**
     * Encrypt data for database storage using password
     */
    fun encryptDatabase(data: ByteArray, password: ByteArray): ByteArray? {
        // Generate salt
        val salt = ByteArray(PwHash.ARGON2ID_SALTBYTES)
        sodium.randombytes_buf(salt, salt.size)

        // Derive key from password using Argon2id
        val key = ByteArray(SecretBox.KEYBYTES)
        val rc1 = sodium.crypto_pwhash(
            key, key.size.toLong(),
            password, password.size.toLong(),
            salt,
            PwHash.OPSLIMIT_INTERACTIVE,
            PwHash.MEMLIMIT_INTERACTIVE,
            PwHash.Alg.PWHASH_ALG_ARGON2ID13.value
        )

        if (rc1 != 0) return null

        // Generate nonce
        val nonce = ByteArray(SecretBox.NONCEBYTES)
        sodium.randombytes_buf(nonce, nonce.size)

        // Encrypt
        val encrypted = ByteArray(SecretBox.MACBYTES + data.size)
        val rc2 = sodium.crypto_secretbox_easy(encrypted, data, data.size.toLong(), nonce, key)

        if (rc2 != 0) {
            Arrays.fill(key, 0.toByte())
            return null
        }

        // Format: header (4 bytes) + salt + nonce + ciphertext
        val header = byteArrayOf(0, 0, 0, 0) // Version 0
        val result = ByteArray(header.size + salt.size + nonce.size + encrypted.size)
        System.arraycopy(header, 0, result, 0, header.size)
        System.arraycopy(salt, 0, result, header.size, salt.size)
        System.arraycopy(nonce, 0, result, header.size + salt.size, nonce.size)
        System.arraycopy(encrypted, 0, result, header.size + salt.size + nonce.size, encrypted.size)

        // Zero sensitive data
        Arrays.fill(key, 0.toByte())
        Arrays.fill(nonce, 0.toByte())

        return result
    }

    /**
     * Decrypt database using password
     */
    fun decryptDatabase(encryptedData: ByteArray, password: ByteArray): ByteArray? {
        val headerSize = 4
        val saltSize = PwHash.ARGON2ID_SALTBYTES
        val nonceSize = SecretBox.NONCEBYTES
        val macSize = SecretBox.MACBYTES

        if (encryptedData.size <= headerSize + saltSize + nonceSize + macSize) {
            return null
        }

        // Parse components
        val header = encryptedData.copyOfRange(0, headerSize)
        val salt = encryptedData.copyOfRange(headerSize, headerSize + saltSize)
        val nonce = encryptedData.copyOfRange(headerSize + saltSize, headerSize + saltSize + nonceSize)
        val ciphertext = encryptedData.copyOfRange(headerSize + saltSize + nonceSize, encryptedData.size)

        // Check version
        if (header.any { it != 0.toByte() }) return null

        // Derive key
        val key = ByteArray(SecretBox.KEYBYTES)
        val rc1 = sodium.crypto_pwhash(
            key, key.size.toLong(),
            password, password.size.toLong(),
            salt,
            PwHash.OPSLIMIT_INTERACTIVE,
            PwHash.MEMLIMIT_INTERACTIVE,
            PwHash.Alg.PWHASH_ALG_ARGON2ID13.value
        )

        if (rc1 != 0) return null

        // Decrypt
        val decrypted = ByteArray(ciphertext.size - macSize)
        val rc2 = sodium.crypto_secretbox_open_easy(decrypted, ciphertext, ciphertext.size.toLong(), nonce, key)

        Arrays.fill(key, 0.toByte())

        return if (rc2 == 0) decrypted else null
    }

    // --- Private helper methods ---

    private fun sign(data: ByteArray, secretKey: ByteArray): ByteArray? {
        if (secretKey.size != Sign.SECRETKEYBYTES) return null

        // Use Lazysodium high-level API
        val signed = ByteArray(Sign.BYTES + data.size)

        // crypto_sign: combine signature + message
        val rc = sodium.crypto_sign(
            signed,
            null, // signed_len output pointer (not needed, we know the size)
            data,
            data.size.toLong(),
            secretKey
        )

        return if (rc == 0) signed else null
    }

    private fun unsign(signedMessage: ByteArray, publicKey: ByteArray): ByteArray? {
        if (signedMessage.size < Sign.BYTES) return null
        if (publicKey.size != Sign.PUBLICKEYBYTES) return null

        val unsigned = ByteArray(signedMessage.size - Sign.BYTES)

        val rc = sodium.crypto_sign_open(
            unsigned,
            null, // unsigned_len output pointer (not needed)
            signedMessage,
            signedMessage.size.toLong(),
            publicKey
        )

        return if (rc == 0) unsigned else null
    }

    private fun encrypt(data: ByteArray, recipientPublicKeySign: ByteArray): ByteArray? {
        if (recipientPublicKeySign.size != Sign.PUBLICKEYBYTES) return null

        // Convert Ed25519 signing key to X25519 box key
        val publicKeyBox = ByteArray(Box.PUBLICKEYBYTES)
        val rc1 = sodium.crypto_sign_ed25519_pk_to_curve25519(publicKeyBox, recipientPublicKeySign)
        if (rc1 != 0) return null

        // Sealed box encryption (anonymous sender)
        val ciphertext = ByteArray(Box.SEALBYTES + data.size)
        val rc = sodium.crypto_box_seal(ciphertext, data, data.size.toLong(), publicKeyBox)

        return if (rc == 0) ciphertext else null
    }

    private fun decrypt(ciphertext: ByteArray, ownPublicKeySign: ByteArray, ownSecretKeySign: ByteArray): ByteArray? {
        if (ciphertext.size < Box.SEALBYTES) return null
        if (ownPublicKeySign.size != Sign.PUBLICKEYBYTES) return null
        if (ownSecretKeySign.size != Sign.SECRETKEYBYTES) return null

        // Convert Ed25519 keys to X25519 box keys
        val publicKeyBox = ByteArray(Box.PUBLICKEYBYTES)
        val secretKeyBox = ByteArray(Box.SECRETKEYBYTES)

        val rc1 = sodium.crypto_sign_ed25519_pk_to_curve25519(publicKeyBox, ownPublicKeySign)
        val rc2 = sodium.crypto_sign_ed25519_sk_to_curve25519(secretKeyBox, ownSecretKeySign)
        if (rc1 != 0 || rc2 != 0) return null

        // Open sealed box
        val decrypted = ByteArray(ciphertext.size - Box.SEALBYTES)
        val rc = sodium.crypto_box_seal_open(decrypted, ciphertext, ciphertext.size.toLong(), publicKeyBox, secretKeyBox)

        Arrays.fill(secretKeyBox, 0.toByte())

        return if (rc == 0) decrypted else null
    }

    /**
     * Key pair container
     */
    data class KeyPair(
        val publicKey: ByteArray,
        val secretKey: ByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is KeyPair) return false
            return publicKey.contentEquals(other.publicKey)
        }

        override fun hashCode(): Int = publicKey.contentHashCode()

        /**
         * Zero secret key from memory
         */
        fun zeroSecretKey() {
            Arrays.fill(secretKey, 0.toByte())
        }
    }
}
