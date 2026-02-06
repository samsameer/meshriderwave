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

    // ========================================================================
    // SIGNATURE AND VERIFICATION TESTS (Military-Grade Jan 2026)
    // ========================================================================

    @Test
    fun `sign and verify detached signature successfully`() {
        val data = "Important tactical message".toByteArray()

        val signature = cryptoManager.sign(data, aliceKeyPair.secretKey)
        assertNotNull("Signature should not be null", signature)
        assertEquals("Signature should be 64 bytes (Ed25519)", 64, signature!!.size)

        // Verify with Alice's public key
        val isValid = cryptoManager.verify(data, signature, aliceKeyPair.publicKey)
        assertTrue("Signature should be valid", isValid)
    }

    @Test
    fun `verify fails with tampered data`() {
        val data = "Original message".toByteArray()

        val signature = cryptoManager.sign(data, aliceKeyPair.secretKey)

        // Tamper with data
        val tamperedData = data.copyOf()
        tamperedData[0] = (tamperedData[0].toInt() + 1).toByte()

        val isValid = cryptoManager.verify(tamperedData, signature!!, aliceKeyPair.publicKey)
        assertFalse("Tampered data should fail verification", isValid)
    }

    @Test
    fun `verify fails with wrong public key`() {
        val data = "Message".toByteArray()

        val signature = cryptoManager.sign(data, aliceKeyPair.secretKey)

        // Verify with Bob's public key instead of Alice's
        val isValid = cryptoManager.verify(data, signature!!, bobKeyPair.publicKey)
        assertFalse("Wrong public key should fail verification", isValid)
    }

    @Test
    fun `verify fails with wrong signature size`() {
        val data = "Message".toByteArray()
        val wrongSizeSignature = ByteArray(32) // Should be 64 bytes

        val isValid = cryptoManager.verify(data, wrongSizeSignature, aliceKeyPair.publicKey)
        assertFalse("Wrong signature size should fail verification", isValid)
    }

    @Test
    fun `sign fails with wrong secret key size`() {
        val data = "Message".toByteArray()
        val wrongSizeKey = ByteArray(32) // Should be 64 bytes

        val signature = cryptoManager.sign(data, wrongSizeKey)
        assertNull("Wrong secret key size should fail signing", signature)
    }

    @Test
    fun `sign and verify empty data`() {
        val emptyData = ByteArray(0)

        val signature = cryptoManager.sign(emptyData, aliceKeyPair.secretKey)
        assertNotNull("Should be able to sign empty data", signature)

        val isValid = cryptoManager.verify(emptyData, signature!!, aliceKeyPair.publicKey)
        assertTrue("Empty data signature should verify", isValid)
    }

    @Test
    fun `sign and verify large data`() {
        val largeData = ByteArray(1024 * 1024) { it.toByte() } // 1MB

        val signature = cryptoManager.sign(largeData, aliceKeyPair.secretKey)
        assertNotNull("Should be able to sign large data", signature)

        val isValid = cryptoManager.verify(largeData, signature!!, aliceKeyPair.publicKey)
        assertTrue("Large data signature should verify", isValid)
    }

    // ========================================================================
    // BROADCAST ENCRYPTION TESTS (Military-Grade Jan 2026)
    // ========================================================================

    @Test
    fun `encryptBroadcast and decryptBroadcast roundtrip succeeds`() {
        val data = "Broadcast to all units".toByteArray()
        val channelKey = ByteArray(32) { it.toByte() }

        val encrypted = cryptoManager.encryptBroadcast(data, channelKey)
        assertNotNull("Broadcast encryption should succeed", encrypted)
        // Format: 24 bytes nonce + data + 16 bytes MAC
        assertEquals("Encrypted size should be nonce + data + MAC",
            24 + data.size + 16, encrypted!!.size)

        val decrypted = cryptoManager.decryptBroadcast(encrypted, channelKey)
        assertNotNull("Broadcast decryption should succeed", decrypted)
        assertTrue("Decrypted data should match original", data.contentEquals(decrypted))
    }

    @Test
    fun `encryptBroadcast produces unique ciphertexts each time`() {
        val data = "Broadcast".toByteArray()
        val channelKey = ByteArray(32) { it.toByte() }

        val encrypted1 = cryptoManager.encryptBroadcast(data, channelKey)
        val encrypted2 = cryptoManager.encryptBroadcast(data, channelKey)

        assertNotNull(encrypted1)
        assertNotNull(encrypted2)

        // Different nonces should produce different ciphertexts
        assertFalse("Broadcast encryption should use random nonce",
            encrypted1!!.contentEquals(encrypted2!!))
    }

    @Test
    fun `decryptBroadcast fails with wrong channel key`() {
        val data = "Secret broadcast".toByteArray()
        val channelKey = ByteArray(32) { it.toByte() }
        val wrongKey = ByteArray(32) { (it + 1).toByte() }

        val encrypted = cryptoManager.encryptBroadcast(data, channelKey)

        val decrypted = cryptoManager.decryptBroadcast(encrypted!!, wrongKey)
        assertNull("Decryption with wrong channel key should fail", decrypted)
    }

    @Test
    fun `decryptBroadcast fails with tampered data`() {
        val data = "Broadcast".toByteArray()
        val channelKey = ByteArray(32) { it.toByte() }

        val encrypted = cryptoManager.encryptBroadcast(data, channelKey)!!

        // Tamper with ciphertext (breaks Poly1305 authentication)
        encrypted[30] = (encrypted[30].toInt() + 1).toByte()

        val decrypted = cryptoManager.decryptBroadcast(encrypted, channelKey)
        assertNull("Tampered broadcast should fail decryption", decrypted)
    }

    @Test
    fun `encryptBroadcast fails with wrong key size`() {
        val data = "Data".toByteArray()
        val wrongSizeKey = ByteArray(16) // Should be 32 bytes

        val encrypted = cryptoManager.encryptBroadcast(data, wrongSizeKey)
        assertNull("Wrong key size should fail encryption", encrypted)
    }

    @Test
    fun `decryptBroadcast fails with truncated data`() {
        val channelKey = ByteArray(32) { it.toByte() }
        val truncatedData = ByteArray(10) // Too short for nonce + MAC

        val decrypted = cryptoManager.decryptBroadcast(truncatedData, channelKey)
        assertNull("Truncated data should fail decryption", decrypted)
    }

    @Test
    fun `broadcast to multiple recipients`() {
        val data = "Emergency alert!".toByteArray()
        val channelKey = cryptoManager.deriveChannelKey("emergency-channel")

        val encrypted = cryptoManager.encryptBroadcast(data, channelKey)

        // Multiple recipients with same channel key can decrypt
        val decrypted1 = cryptoManager.decryptBroadcast(encrypted!!, channelKey)
        val decrypted2 = cryptoManager.decryptBroadcast(encrypted, channelKey)
        val decrypted3 = cryptoManager.decryptBroadcast(encrypted, channelKey)

        assertTrue("Recipient 1 should decrypt", data.contentEquals(decrypted1))
        assertTrue("Recipient 2 should decrypt", data.contentEquals(decrypted2))
        assertTrue("Recipient 3 should decrypt", data.contentEquals(decrypted3))
    }

    // ========================================================================
    // CHANNEL KEY DERIVATION TESTS (Military-Grade Jan 2026)
    // ========================================================================

    @Test
    fun `deriveChannelKey produces valid 32-byte key`() {
        val channelId = "tactical-channel-1"

        val channelKey = cryptoManager.deriveChannelKey(channelId)
        assertNotNull("Channel key should be derived", channelKey)
        assertEquals("Channel key should be 32 bytes", 32, channelKey.size)
    }

    @Test
    fun `deriveChannelKey is deterministic`() {
        val channelId = "test-channel-123"

        val key1 = cryptoManager.deriveChannelKey(channelId)
        val key2 = cryptoManager.deriveChannelKey(channelId)

        assertTrue("Same channel ID should derive same key",
            key1.contentEquals(key2))
    }

    @Test
    fun `deriveChannelKey differs for different channels`() {
        val channel1 = "channel-alpha"
        val channel2 = "channel-bravo"

        val key1 = cryptoManager.deriveChannelKey(channel1)
        val key2 = cryptoManager.deriveChannelKey(channel2)

        assertFalse("Different channels should derive different keys",
            key1.contentEquals(key2))
    }

    @Test
    fun `deriveChannelKey with master key differs from without`() {
        val channelId = "secure-channel"
        val masterKey = ByteArray(32) { it.toByte() }

        val keyWithoutMaster = cryptoManager.deriveChannelKey(channelId)
        val keyWithMaster = cryptoManager.deriveChannelKey(channelId, masterKey)

        assertFalse("Master key should change derived key",
            keyWithoutMaster.contentEquals(keyWithMaster))
    }

    @Test
    fun `deriveChannelKey with master key is deterministic`() {
        val channelId = "test-channel"
        val masterKey = ByteArray(32) { it.toByte() }

        val key1 = cryptoManager.deriveChannelKey(channelId, masterKey)
        val key2 = cryptoManager.deriveChannelKey(channelId, masterKey)

        assertTrue("Derivation with master key should be deterministic",
            key1.contentEquals(key2))
    }

    @Test
    fun `deriveChannelKey with wrong master key size falls back`() {
        val channelId = "test-channel"
        val wrongSizeMasterKey = ByteArray(16) // Should be 32 bytes

        // Should fall back to derivation without master key
        val key = cryptoManager.deriveChannelKey(channelId, wrongSizeMasterKey)
        assertNotNull("Should derive key (fallback to no master)", key)
        assertEquals("Key should be 32 bytes", 32, key.size)
    }

    @Test
    fun `deriveChannelKey works with broadcast encryption`() {
        val channelId = "ptt-channel-1"
        val channelKey = cryptoManager.deriveChannelKey(channelId)

        val message = "PTT broadcast audio".toByteArray()

        val encrypted = cryptoManager.encryptBroadcast(message, channelKey)
        val decrypted = cryptoManager.decryptBroadcast(encrypted!!, channelKey)

        assertTrue("Should encrypt/decrypt broadcast with derived key",
            message.contentEquals(decrypted))
    }

    // ========================================================================
    // INTEGRATION TESTS
    // ========================================================================

    @Test
    fun `end-to-end encrypted communication with signature verification`() {
        // Alice sends message to Bob
        val message = "Hi Bob, this is Alice!"

        val senderPubKeyOut = ByteArray(32)
        val encrypted = cryptoManager.encryptMessage(
            message = message,
            recipientPublicKey = bobKeyPair.publicKey,
            ownPublicKey = aliceKeyPair.publicKey,
            ownSecretKey = aliceKeyPair.secretKey
        )

        // Bob decrypts and extracts Alice's public key
        val decrypted = cryptoManager.decryptMessage(
            ciphertext = encrypted!!,
            senderPublicKeyOut = senderPubKeyOut,
            ownPublicKey = bobKeyPair.publicKey,
            ownSecretKey = bobKeyPair.secretKey
        )

        assertEquals("Message should be intact", message, decrypted)
        assertTrue("Bob should verify message is from Alice",
            senderPubKeyOut.contentEquals(aliceKeyPair.publicKey))

        // Bob can verify the signature separately
        val messageData = message.toByteArray()
        val signature = cryptoManager.sign(messageData, aliceKeyPair.secretKey)
        val isValid = cryptoManager.verify(messageData, signature!!, senderPubKeyOut)
        assertTrue("Signature should verify", isValid)
    }

    @Test
    fun `sign beacon for identity discovery`() {
        val identityKeys = cryptoManager.generateKeyPair()

        val beaconData = """
            {"id":"unit-alpha","name":"Team Alpha","type":"infantry","timestamp":1234567890}
        """.trimIndent().toByteArray()

        // Sign beacon (as device would)
        val signature = cryptoManager.sign(beaconData, identityKeys.secretKey)
        assertNotNull("Beacon signing should succeed", signature)

        // Verify beacon (as other devices would)
        val isValid = cryptoManager.verify(beaconData, signature!!, identityKeys.publicKey)
        assertTrue("Beacon signature should be valid", isValid)
    }

    @Test
    fun `encrypt and decrypt beacon with broadcast encryption`() {
        val channelId = "discovery-channel"
        val channelKey = cryptoManager.deriveChannelKey(channelId)

        val beaconData = """
            {"type":"beacon","id":"unit123","lat":1.2345,"lon":6.7890}
        """.trimIndent().toByteArray()

        val encrypted = cryptoManager.encryptBroadcast(beaconData, channelKey)
        val decrypted = cryptoManager.decryptBroadcast(encrypted!!, channelKey)

        assertTrue("Beacon should encrypt/decrypt for channel",
            beaconData.contentEquals(decrypted))
    }

    // ========================================================================
    // SECURITY PROPERTY TESTS
    // ========================================================================

    @Test
    fun `encryption provides semantic security`() {
        val message1 = "Yes"
        val message2 = "No"

        val encrypted1 = cryptoManager.encryptMessage(
            message = message1,
            recipientPublicKey = bobKeyPair.publicKey,
            ownPublicKey = aliceKeyPair.publicKey,
            ownSecretKey = aliceKeyPair.secretKey
        )

        val encrypted2 = cryptoManager.encryptMessage(
            message = message2,
            recipientPublicKey = bobKeyPair.publicKey,
            ownPublicKey = aliceKeyPair.publicKey,
            ownSecretKey = aliceKeyPair.secretKey
        )

        // Same-length messages should produce different ciphertexts (random nonce)
        assertNotNull(encrypted1)
        assertNotNull(encrypted2)
        assertFalse("Encryption should be probabilistic (semantic security)",
            encrypted1!!.contentEquals(encrypted2!!))
    }

    @Test
    fun `encryption uses ephemeral keys for each message`() {
        val message = "Test message"

        val encrypted1 = cryptoManager.encryptMessage(
            message = message,
            recipientPublicKey = bobKeyPair.publicKey,
            ownPublicKey = aliceKeyPair.publicKey,
            ownSecretKey = aliceKeyPair.secretKey
        )

        val encrypted2 = cryptoManager.encryptMessage(
            message = message,
            recipientPublicKey = bobKeyPair.publicKey,
            ownPublicKey = aliceKeyPair.publicKey,
            ownSecretKey = aliceKeyPair.secretKey
        )

        // Same message encrypted twice should be different (ephemeral keys)
        assertNotNull(encrypted1)
        assertNotNull(encrypted2)
        assertFalse("Should use ephemeral keys (different ciphertext each time)",
            encrypted1!!.contentEquals(encrypted2!!))
    }
}
