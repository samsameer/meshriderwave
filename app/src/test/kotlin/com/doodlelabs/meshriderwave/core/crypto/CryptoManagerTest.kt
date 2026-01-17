/*
 * Mesh Rider Wave - CryptoManager Unit Tests
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 *
 * Comprehensive unit tests for the cryptographic operations
 */

package com.doodlelabs.meshriderwave.core.crypto

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for CryptoManager
 *
 * Note: These tests require Lazysodium native library to be available.
 * Run on an Android device or use Robolectric for full coverage.
 *
 * For CI/CD, consider using the pure Java implementation or
 * running these as instrumented tests on Android.
 */
class CryptoManagerTest {

    private lateinit var cryptoManager: CryptoManager
    private lateinit var aliceKeyPair: CryptoManager.KeyPair
    private lateinit var bobKeyPair: CryptoManager.KeyPair

    @Before
    fun setUp() {
        // Initialize CryptoManager
        // Note: This requires native library, may fail in pure JUnit
        try {
            cryptoManager = CryptoManager()
            aliceKeyPair = cryptoManager.generateKeyPair()
            bobKeyPair = cryptoManager.generateKeyPair()
        } catch (e: UnsatisfiedLinkError) {
            // Skip tests if native library not available
            org.junit.Assume.assumeNoException("Native library not available", e)
        }
    }

    // ========================================================================
    // KEY GENERATION TESTS
    // ========================================================================

    @Test
    fun `generateKeyPair returns valid Ed25519 key pair`() {
        val keyPair = cryptoManager.generateKeyPair()

        // Ed25519 public key is 32 bytes
        assertEquals("Public key should be 32 bytes", 32, keyPair.publicKey.size)

        // Ed25519 secret key is 64 bytes (seed + public key)
        assertEquals("Secret key should be 64 bytes", 64, keyPair.secretKey.size)

        // Keys should not be all zeros
        assertFalse("Public key should not be all zeros", keyPair.publicKey.all { it == 0.toByte() })
        assertFalse("Secret key should not be all zeros", keyPair.secretKey.all { it == 0.toByte() })
    }

    @Test
    fun `generateKeyPair creates unique keys each time`() {
        val keyPair1 = cryptoManager.generateKeyPair()
        val keyPair2 = cryptoManager.generateKeyPair()

        assertFalse(
            "Each key pair should be unique",
            keyPair1.publicKey.contentEquals(keyPair2.publicKey)
        )
        assertFalse(
            "Each key pair should be unique",
            keyPair1.secretKey.contentEquals(keyPair2.secretKey)
        )
    }

    @Test
    fun `keyPair equals works correctly`() {
        val keyPair1 = cryptoManager.generateKeyPair()
        val keyPair2 = CryptoManager.KeyPair(
            publicKey = keyPair1.publicKey.copyOf(),
            secretKey = keyPair1.secretKey.copyOf()
        )

        assertEquals("Same public key should be equal", keyPair1, keyPair2)
    }

    @Test
    fun `keyPair zeroSecretKey clears secret key`() {
        val keyPair = cryptoManager.generateKeyPair()
        keyPair.zeroSecretKey()

        assertTrue("Secret key should be zeroed", keyPair.secretKey.all { it == 0.toByte() })
    }

    // ========================================================================
    // MESSAGE ENCRYPTION/DECRYPTION TESTS
    // ========================================================================

    @Test
    fun `encryptMessage and decryptMessage roundtrip succeeds`() {
        val originalMessage = "Hello, secure world!"

        // Alice encrypts message for Bob
        val encrypted = cryptoManager.encryptMessage(
            message = originalMessage,
            recipientPublicKey = bobKeyPair.publicKey,
            ownPublicKey = aliceKeyPair.publicKey,
            ownSecretKey = aliceKeyPair.secretKey
        )

        assertNotNull("Encryption should succeed", encrypted)
        assertFalse(
            "Encrypted data should differ from plaintext",
            String(encrypted!!).contains(originalMessage)
        )

        // Bob decrypts message from Alice
        val senderPublicKey = ByteArray(32)
        val decrypted = cryptoManager.decryptMessage(
            ciphertext = encrypted,
            senderPublicKeyOut = senderPublicKey,
            ownPublicKey = bobKeyPair.publicKey,
            ownSecretKey = bobKeyPair.secretKey
        )

        assertNotNull("Decryption should succeed", decrypted)
        assertEquals("Decrypted message should match original", originalMessage, decrypted)
        assertTrue(
            "Sender public key should match Alice's key",
            senderPublicKey.contentEquals(aliceKeyPair.publicKey)
        )
    }

    @Test
    fun `encryptMessage handles empty message`() {
        val encrypted = cryptoManager.encryptMessage(
            message = "",
            recipientPublicKey = bobKeyPair.publicKey,
            ownPublicKey = aliceKeyPair.publicKey,
            ownSecretKey = aliceKeyPair.secretKey
        )

        assertNotNull("Empty message encryption should succeed", encrypted)

        val senderPublicKey = ByteArray(32)
        val decrypted = cryptoManager.decryptMessage(
            ciphertext = encrypted!!,
            senderPublicKeyOut = senderPublicKey,
            ownPublicKey = bobKeyPair.publicKey,
            ownSecretKey = bobKeyPair.secretKey
        )

        assertEquals("Empty message should decrypt correctly", "", decrypted)
    }

    @Test
    fun `encryptMessage handles long message`() {
        val longMessage = "A".repeat(10000)

        val encrypted = cryptoManager.encryptMessage(
            message = longMessage,
            recipientPublicKey = bobKeyPair.publicKey,
            ownPublicKey = aliceKeyPair.publicKey,
            ownSecretKey = aliceKeyPair.secretKey
        )

        assertNotNull("Long message encryption should succeed", encrypted)

        val senderPublicKey = ByteArray(32)
        val decrypted = cryptoManager.decryptMessage(
            ciphertext = encrypted!!,
            senderPublicKeyOut = senderPublicKey,
            ownPublicKey = bobKeyPair.publicKey,
            ownSecretKey = bobKeyPair.secretKey
        )

        assertEquals("Long message should decrypt correctly", longMessage, decrypted)
    }

    @Test
    fun `encryptMessage handles unicode message`() {
        val unicodeMessage = "Hello 世界! مرحبا 🌍🔐"

        val encrypted = cryptoManager.encryptMessage(
            message = unicodeMessage,
            recipientPublicKey = bobKeyPair.publicKey,
            ownPublicKey = aliceKeyPair.publicKey,
            ownSecretKey = aliceKeyPair.secretKey
        )

        assertNotNull("Unicode message encryption should succeed", encrypted)

        val senderPublicKey = ByteArray(32)
        val decrypted = cryptoManager.decryptMessage(
            ciphertext = encrypted!!,
            senderPublicKeyOut = senderPublicKey,
            ownPublicKey = bobKeyPair.publicKey,
            ownSecretKey = bobKeyPair.secretKey
        )

        assertEquals("Unicode message should decrypt correctly", unicodeMessage, decrypted)
    }

    @Test
    fun `decryptMessage fails with wrong recipient keys`() {
        val message = "Secret message"
        val charlieKeyPair = cryptoManager.generateKeyPair()

        // Alice encrypts for Bob
        val encrypted = cryptoManager.encryptMessage(
            message = message,
            recipientPublicKey = bobKeyPair.publicKey,
            ownPublicKey = aliceKeyPair.publicKey,
            ownSecretKey = aliceKeyPair.secretKey
        )

        // Charlie tries to decrypt (should fail)
        val senderPublicKey = ByteArray(32)
        val decrypted = cryptoManager.decryptMessage(
            ciphertext = encrypted!!,
            senderPublicKeyOut = senderPublicKey,
            ownPublicKey = charlieKeyPair.publicKey,
            ownSecretKey = charlieKeyPair.secretKey
        )

        assertNull("Decryption with wrong keys should fail", decrypted)
    }

    @Test
    fun `decryptMessage fails with tampered ciphertext`() {
        val message = "Secret message"

        val encrypted = cryptoManager.encryptMessage(
            message = message,
            recipientPublicKey = bobKeyPair.publicKey,
            ownPublicKey = aliceKeyPair.publicKey,
            ownSecretKey = aliceKeyPair.secretKey
        )!!

        // Tamper with ciphertext
        encrypted[encrypted.size / 2] = (encrypted[encrypted.size / 2] + 1).toByte()

        val senderPublicKey = ByteArray(32)
        val decrypted = cryptoManager.decryptMessage(
            ciphertext = encrypted,
            senderPublicKeyOut = senderPublicKey,
            ownPublicKey = bobKeyPair.publicKey,
            ownSecretKey = bobKeyPair.secretKey
        )

        assertNull("Decryption of tampered ciphertext should fail", decrypted)
    }

    @Test
    fun `decryptMessage fails with invalid sender key buffer size`() {
        val message = "Secret message"

        val encrypted = cryptoManager.encryptMessage(
            message = message,
            recipientPublicKey = bobKeyPair.publicKey,
            ownPublicKey = aliceKeyPair.publicKey,
            ownSecretKey = aliceKeyPair.secretKey
        )!!

        // Wrong size buffer
        val wrongSizeBuffer = ByteArray(16) // Should be 32

        val decrypted = cryptoManager.decryptMessage(
            ciphertext = encrypted,
            senderPublicKeyOut = wrongSizeBuffer,
            ownPublicKey = bobKeyPair.publicKey,
            ownSecretKey = bobKeyPair.secretKey
        )

        assertNull("Decryption with wrong buffer size should fail", decrypted)
    }

    // ========================================================================
    // DATABASE ENCRYPTION TESTS
    // ========================================================================

    @Test
    fun `encryptDatabase and decryptDatabase roundtrip succeeds`() {
        val originalData = "Database content with sensitive information".toByteArray()
        val password = "SecurePassword123!".toByteArray()

        val encrypted = cryptoManager.encryptDatabase(originalData, password)
        assertNotNull("Database encryption should succeed", encrypted)

        val decrypted = cryptoManager.decryptDatabase(encrypted!!, password)
        assertNotNull("Database decryption should succeed", decrypted)
        assertTrue(
            "Decrypted data should match original",
            originalData.contentEquals(decrypted)
        )
    }

    @Test
    fun `encryptDatabase produces different output each time`() {
        val data = "Same data".toByteArray()
        val password = "Password".toByteArray()

        val encrypted1 = cryptoManager.encryptDatabase(data, password)
        val encrypted2 = cryptoManager.encryptDatabase(data, password)

        assertNotNull(encrypted1)
        assertNotNull(encrypted2)

        // Due to random salt and nonce, outputs should differ
        assertFalse(
            "Each encryption should produce different output (random salt/nonce)",
            encrypted1!!.contentEquals(encrypted2!!)
        )
    }

    @Test
    fun `decryptDatabase fails with wrong password`() {
        val data = "Secret data".toByteArray()
        val correctPassword = "CorrectPassword".toByteArray()
        val wrongPassword = "WrongPassword".toByteArray()

        val encrypted = cryptoManager.encryptDatabase(data, correctPassword)
        val decrypted = cryptoManager.decryptDatabase(encrypted!!, wrongPassword)

        assertNull("Decryption with wrong password should fail", decrypted)
    }

    @Test
    fun `encryptDatabase handles empty data`() {
        val emptyData = ByteArray(0)
        val password = "Password".toByteArray()

        val encrypted = cryptoManager.encryptDatabase(emptyData, password)
        assertNotNull("Empty data encryption should succeed", encrypted)

        val decrypted = cryptoManager.decryptDatabase(encrypted!!, password)
        assertNotNull("Empty data decryption should succeed", decrypted)
        assertEquals("Decrypted empty data size should be 0", 0, decrypted!!.size)
    }

    @Test
    fun `encryptDatabase handles large data`() {
        val largeData = ByteArray(1024 * 1024) { it.toByte() } // 1MB
        val password = "Password".toByteArray()

        val encrypted = cryptoManager.encryptDatabase(largeData, password)
        assertNotNull("Large data encryption should succeed", encrypted)

        val decrypted = cryptoManager.decryptDatabase(encrypted!!, password)
        assertNotNull("Large data decryption should succeed", decrypted)
        assertTrue(
            "Large data should decrypt correctly",
            largeData.contentEquals(decrypted)
        )
    }

    @Test
    fun `decryptDatabase fails with truncated ciphertext`() {
        val data = "Secret data".toByteArray()
        val password = "Password".toByteArray()

        val encrypted = cryptoManager.encryptDatabase(data, password)!!

        // Truncate ciphertext
        val truncated = encrypted.copyOf(encrypted.size / 2)

        val decrypted = cryptoManager.decryptDatabase(truncated, password)
        assertNull("Decryption of truncated ciphertext should fail", decrypted)
    }

    @Test
    fun `decryptDatabase fails with too short ciphertext`() {
        val tooShort = ByteArray(10) // Way too short for valid encrypted data
        val password = "Password".toByteArray()

        val decrypted = cryptoManager.decryptDatabase(tooShort, password)
        assertNull("Decryption of too-short ciphertext should fail", decrypted)
    }

    @Test
    fun `decryptDatabase fails with invalid header version`() {
        val data = "Secret data".toByteArray()
        val password = "Password".toByteArray()

        val encrypted = cryptoManager.encryptDatabase(data, password)!!

        // Corrupt the version header (first 4 bytes should be zeros)
        encrypted[0] = 1

        val decrypted = cryptoManager.decryptDatabase(encrypted, password)
        assertNull("Decryption with invalid header should fail", decrypted)
    }

    // ========================================================================
    // EDGE CASE TESTS
    // ========================================================================

    @Test
    fun `encryptMessage with invalid public key returns null`() {
        val invalidPublicKey = ByteArray(16) // Should be 32 bytes

        val result = cryptoManager.encryptMessage(
            message = "Test",
            recipientPublicKey = invalidPublicKey,
            ownPublicKey = aliceKeyPair.publicKey,
            ownSecretKey = aliceKeyPair.secretKey
        )

        assertNull("Encryption with invalid public key should return null", result)
    }

    @Test
    fun `encryptMessage with invalid secret key returns null`() {
        val invalidSecretKey = ByteArray(32) // Should be 64 bytes

        val result = cryptoManager.encryptMessage(
            message = "Test",
            recipientPublicKey = bobKeyPair.publicKey,
            ownPublicKey = aliceKeyPair.publicKey,
            ownSecretKey = invalidSecretKey
        )

        assertNull("Encryption with invalid secret key should return null", result)
    }

    // ========================================================================
    // CONCURRENT ACCESS TESTS
    // ========================================================================

    @Test
    fun `cryptoManager is thread-safe for concurrent operations`() {
        val threads = (1..10).map { threadNum ->
            Thread {
                repeat(100) { iteration ->
                    val message = "Thread $threadNum, iteration $iteration"
                    val encrypted = cryptoManager.encryptMessage(
                        message = message,
                        recipientPublicKey = bobKeyPair.publicKey,
                        ownPublicKey = aliceKeyPair.publicKey,
                        ownSecretKey = aliceKeyPair.secretKey
                    )

                    assertNotNull("Concurrent encryption should succeed", encrypted)

                    val senderKey = ByteArray(32)
                    val decrypted = cryptoManager.decryptMessage(
                        ciphertext = encrypted!!,
                        senderPublicKeyOut = senderKey,
                        ownPublicKey = bobKeyPair.publicKey,
                        ownSecretKey = bobKeyPair.secretKey
                    )

                    assertEquals("Concurrent decryption should succeed", message, decrypted)
                }
            }
        }

        threads.forEach { it.start() }
        threads.forEach { it.join() }
    }
}
