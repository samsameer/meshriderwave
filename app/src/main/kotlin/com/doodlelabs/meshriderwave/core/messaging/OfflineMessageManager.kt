/*
 * Mesh Rider Wave - Offline Message Manager
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 *
 * Store-and-forward message delivery for offline peers
 *
 * Features:
 * - Queue messages when recipient is offline
 * - Automatic delivery when peer comes online
 * - Priority-based delivery ordering
 * - Configurable TTL and retry policies
 * - Encrypted message storage
 */

package com.doodlelabs.meshriderwave.core.messaging

import android.content.Context
import android.util.Log
import com.doodlelabs.meshriderwave.core.crypto.CryptoManager
import com.doodlelabs.meshriderwave.data.local.database.AppDatabase
import com.doodlelabs.meshriderwave.data.local.database.MessageStatus
import com.doodlelabs.meshriderwave.data.local.database.MessageType
import com.doodlelabs.meshriderwave.data.local.database.OfflineMessageDao
import com.doodlelabs.meshriderwave.data.local.database.OfflineMessageEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.nio.ByteBuffer
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages offline message queue with store-and-forward delivery
 *
 * Messages are encrypted and stored locally until the recipient
 * becomes reachable, then delivered automatically
 */
@Singleton
class OfflineMessageManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cryptoManager: CryptoManager,
    private val ioDispatcher: CoroutineDispatcher
) {
    companion object {
        private const val TAG = "MeshRider:OfflineMsg"

        // Default configuration
        const val DEFAULT_TTL_MS = 24 * 60 * 60 * 1000L      // 24 hours
        const val DEFAULT_MAX_RETRIES = 10
        const val RETRY_DELAY_MS = 5_000L                    // 5 seconds between retries
        const val MAX_MESSAGE_SIZE = 1024 * 1024             // 1MB max

        // Priority levels
        const val PRIORITY_LOW = 0
        const val PRIORITY_NORMAL = 1
        const val PRIORITY_HIGH = 2
        const val PRIORITY_URGENT = 3
        const val PRIORITY_SOS = 10  // Always highest
    }

    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val mutex = Mutex()

    private val database: AppDatabase by lazy { AppDatabase.getInstance(context) }
    private val messageDao: OfflineMessageDao by lazy { database.offlineMessageDao() }

    // Delivery callbacks
    private val _deliveryResults = MutableSharedFlow<DeliveryResult>(extraBufferCapacity = 50)
    val deliveryResults: SharedFlow<DeliveryResult> = _deliveryResults.asSharedFlow()

    // Processing state
    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    // Queue stats
    val pendingCount: Flow<Int> = messageDao.getPendingCount()

    // ========== Queue Operations ==========

    /**
     * Queue a message for offline delivery
     */
    suspend fun queueMessage(
        recipientPublicKey: ByteArray,
        content: ByteArray,
        messageType: MessageType = MessageType.TEXT,
        senderPublicKey: ByteArray,
        senderSecretKey: ByteArray,
        groupId: ByteArray? = null,
        priority: Int = PRIORITY_NORMAL,
        ttlMs: Long = DEFAULT_TTL_MS
    ): Result<String> = mutex.withLock {
        try {
            if (content.size > MAX_MESSAGE_SIZE) {
                return Result.failure(IllegalArgumentException("Message exceeds max size"))
            }

            // Encrypt the content
            val encrypted = cryptoManager.encryptMessage(
                message = String(content, Charsets.UTF_8),  // Simplified for demo
                recipientPublicKey = recipientPublicKey,
                ownPublicKey = senderPublicKey,
                ownSecretKey = senderSecretKey
            ) ?: return Result.failure(SecurityException("Encryption failed"))

            val messageId = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()

            val entity = OfflineMessageEntity(
                id = messageId,
                senderKey = senderPublicKey.toBase64(),
                recipientKey = recipientPublicKey.toBase64(),
                groupId = groupId?.toBase64(),
                messageType = messageType.ordinal,
                content = encrypted,
                priority = priority,
                status = MessageStatus.PENDING.ordinal,
                retryCount = 0,
                maxRetries = DEFAULT_MAX_RETRIES,
                ttlMs = ttlMs,
                createdAt = now,
                expiresAt = now + ttlMs
            )

            messageDao.insert(entity)
            Log.d(TAG, "Queued message $messageId for ${recipientPublicKey.take(4).toByteArray().toHexString()}")

            Result.success(messageId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to queue message", e)
            Result.failure(e)
        }
    }

    /**
     * Queue a raw (pre-encrypted) message
     */
    suspend fun queueRawMessage(
        messageId: String,
        recipientPublicKey: ByteArray,
        senderPublicKey: ByteArray,
        encryptedContent: ByteArray,
        messageType: MessageType,
        groupId: ByteArray? = null,
        priority: Int = PRIORITY_NORMAL,
        ttlMs: Long = DEFAULT_TTL_MS
    ): Result<Unit> = mutex.withLock {
        try {
            val now = System.currentTimeMillis()

            val entity = OfflineMessageEntity(
                id = messageId,
                senderKey = senderPublicKey.toBase64(),
                recipientKey = recipientPublicKey.toBase64(),
                groupId = groupId?.toBase64(),
                messageType = messageType.ordinal,
                content = encryptedContent,
                priority = priority,
                status = MessageStatus.PENDING.ordinal,
                retryCount = 0,
                maxRetries = DEFAULT_MAX_RETRIES,
                ttlMs = ttlMs,
                createdAt = now,
                expiresAt = now + ttlMs
            )

            messageDao.insert(entity)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to queue raw message", e)
            Result.failure(e)
        }
    }

    /**
     * Get pending messages for a recipient
     */
    fun getPendingMessagesFor(recipientPublicKey: ByteArray): Flow<List<OfflineMessageEntity>> {
        return messageDao.getPendingMessagesForRecipient(recipientPublicKey.toBase64())
    }

    /**
     * Get pending messages for a group
     */
    fun getPendingMessagesForGroup(groupId: ByteArray): Flow<List<OfflineMessageEntity>> {
        return messageDao.getPendingMessagesForGroup(groupId.toBase64())
    }

    /**
     * Get all pending messages
     */
    fun getAllPendingMessages(): Flow<List<OfflineMessageEntity>> {
        return messageDao.getPendingMessages()
    }

    // ========== Delivery Operations ==========

    /**
     * Process delivery for a specific recipient (when they come online)
     */
    suspend fun processDeliveryFor(
        recipientPublicKey: ByteArray,
        deliveryFunction: suspend (OfflineMessageEntity) -> Boolean
    ) {
        val recipientKey = recipientPublicKey.toBase64()

        scope.launch {
            _isProcessing.value = true

            try {
                // Get pending messages for this recipient
                val count = messageDao.getPendingCountForRecipient(recipientKey)
                Log.d(TAG, "Processing $count pending messages for ${recipientPublicKey.take(4).toByteArray().toHexString()}")

                // Collect messages once (not as flow)
                messageDao.getPendingMessagesForRecipient(recipientKey)
                    .map { messages ->
                        messages.forEach { message ->
                            processMessage(message, deliveryFunction)
                        }
                    }

            } catch (e: Exception) {
                Log.e(TAG, "Error processing delivery", e)
            } finally {
                _isProcessing.value = false
            }
        }
    }

    /**
     * Process a single message delivery
     */
    private suspend fun processMessage(
        message: OfflineMessageEntity,
        deliveryFunction: suspend (OfflineMessageEntity) -> Boolean
    ) {
        // Check if expired
        if (System.currentTimeMillis() > message.expiresAt) {
            messageDao.updateStatus(message.id, MessageStatus.EXPIRED.ordinal)
            emitResult(message.id, DeliveryStatus.EXPIRED, "Message expired")
            return
        }

        // Check max retries
        if (message.retryCount >= message.maxRetries) {
            messageDao.updateStatus(message.id, MessageStatus.FAILED.ordinal)
            emitResult(message.id, DeliveryStatus.FAILED, "Max retries exceeded")
            return
        }

        // Mark as sending
        messageDao.updateStatus(message.id, MessageStatus.SENDING.ordinal)

        try {
            val success = deliveryFunction(message)

            if (success) {
                messageDao.markDelivered(message.id)
                emitResult(message.id, DeliveryStatus.DELIVERED)
                Log.d(TAG, "Message ${message.id} delivered successfully")
            } else {
                messageDao.incrementRetry(message.id)
                messageDao.updateStatus(message.id, MessageStatus.PENDING.ordinal)
                emitResult(message.id, DeliveryStatus.RETRY, "Delivery failed, will retry")
            }
        } catch (e: Exception) {
            messageDao.markFailed(message.id, e.message)
            emitResult(message.id, DeliveryStatus.FAILED, e.message)
            Log.e(TAG, "Message ${message.id} delivery failed", e)
        }
    }

    /**
     * Mark a message as delivered (external confirmation)
     */
    suspend fun confirmDelivery(messageId: String): Result<Unit> {
        return try {
            messageDao.markDelivered(messageId)
            emitResult(messageId, DeliveryStatus.DELIVERED)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Cancel a queued message
     */
    suspend fun cancelMessage(messageId: String): Result<Unit> {
        return try {
            messageDao.updateStatus(messageId, MessageStatus.CANCELLED.ordinal)
            emitResult(messageId, DeliveryStatus.CANCELLED)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Delete a message from queue
     */
    suspend fun deleteMessage(messageId: String): Result<Unit> {
        return try {
            messageDao.deleteById(messageId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ========== Maintenance ==========

    /**
     * Clean up expired and old messages
     */
    suspend fun cleanup() {
        try {
            messageDao.deleteExpired()
            Log.d(TAG, "Cleaned up expired messages")
        } catch (e: Exception) {
            Log.e(TAG, "Cleanup failed", e)
        }
    }

    /**
     * Delete all messages older than specified time
     */
    suspend fun deleteOlderThan(ageMs: Long) {
        try {
            val cutoff = System.currentTimeMillis() - ageMs
            messageDao.deleteOlderThan(cutoff)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete old messages", e)
        }
    }

    /**
     * Clear all pending messages for a recipient
     */
    suspend fun clearMessagesFor(recipientPublicKey: ByteArray) {
        try {
            messageDao.deleteAllForRecipient(recipientPublicKey.toBase64())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear messages", e)
        }
    }

    /**
     * Clear entire queue
     */
    suspend fun clearAll() {
        try {
            messageDao.deleteAll()
            Log.d(TAG, "Cleared all offline messages")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear queue", e)
        }
    }

    // ========== Helpers ==========

    private suspend fun emitResult(
        messageId: String,
        status: DeliveryStatus,
        error: String? = null
    ) {
        _deliveryResults.emit(DeliveryResult(
            messageId = messageId,
            status = status,
            timestamp = System.currentTimeMillis(),
            error = error
        ))
    }

    private fun ByteArray.toBase64(): String =
        android.util.Base64.encodeToString(this, android.util.Base64.NO_WRAP)

    private fun ByteArray.toHexString(): String =
        joinToString("") { "%02x".format(it) }
}

// ========== Data Classes ==========

/**
 * Result of a delivery attempt
 */
data class DeliveryResult(
    val messageId: String,
    val status: DeliveryStatus,
    val timestamp: Long,
    val error: String? = null
)

enum class DeliveryStatus {
    PENDING,
    SENDING,
    DELIVERED,
    RETRY,
    FAILED,
    EXPIRED,
    CANCELLED
}

/**
 * Queued message info for display
 */
data class QueuedMessageInfo(
    val id: String,
    val recipientName: String,
    val messageType: MessageType,
    val priority: Int,
    val createdAt: Long,
    val expiresAt: Long,
    val retryCount: Int,
    val status: MessageStatus
)
