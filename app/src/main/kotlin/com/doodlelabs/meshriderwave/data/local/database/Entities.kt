/*
 * Mesh Rider Wave - Room Database Entities
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 *
 * Database entities for offline message queue, call history, and SOS alerts
 */

package com.doodlelabs.meshriderwave.data.local.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Offline message for store-and-forward delivery
 *
 * Messages are queued when recipient is offline and delivered
 * when connectivity is restored
 */
@Entity(
    tableName = "offline_messages",
    indices = [
        Index(value = ["recipient_key"]),
        Index(value = ["status"]),
        Index(value = ["created_at"])
    ]
)
data class OfflineMessageEntity(
    @PrimaryKey
    val id: String,

    @ColumnInfo(name = "sender_key")
    val senderKey: String,  // Base64

    @ColumnInfo(name = "recipient_key")
    val recipientKey: String,  // Base64

    @ColumnInfo(name = "group_id")
    val groupId: String? = null,  // Base64, null for direct messages

    @ColumnInfo(name = "message_type")
    val messageType: Int,  // MessageType ordinal

    @ColumnInfo(name = "content", typeAffinity = ColumnInfo.BLOB)
    val content: ByteArray,  // Encrypted message content

    @ColumnInfo(name = "priority")
    val priority: Int = 0,  // Higher = more important

    @ColumnInfo(name = "status")
    val status: Int = 0,  // MessageStatus ordinal

    @ColumnInfo(name = "retry_count")
    val retryCount: Int = 0,

    @ColumnInfo(name = "max_retries")
    val maxRetries: Int = 10,

    @ColumnInfo(name = "ttl_ms")
    val ttlMs: Long = 24 * 60 * 60 * 1000,  // Time-to-live (24h default)

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "expires_at")
    val expiresAt: Long = System.currentTimeMillis() + ttlMs,

    @ColumnInfo(name = "last_attempt_at")
    val lastAttemptAt: Long? = null,

    @ColumnInfo(name = "delivered_at")
    val deliveredAt: Long? = null,

    @ColumnInfo(name = "error_message")
    val errorMessage: String? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is OfflineMessageEntity) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}

/**
 * Message types for offline queue
 */
enum class MessageType {
    TEXT,           // Text message
    LOCATION,       // Location update
    PTT_AUDIO,      // PTT voice clip
    FILE,           // File attachment
    CALL_SIGNAL,    // Call signaling (offer/answer)
    GROUP_UPDATE,   // Group membership change
    SOS_ALERT       // Emergency broadcast
}

/**
 * Message delivery status
 */
enum class MessageStatus {
    PENDING,        // Waiting to be sent
    SENDING,        // Currently attempting delivery
    DELIVERED,      // Successfully delivered
    FAILED,         // Delivery failed after max retries
    EXPIRED,        // TTL exceeded
    CANCELLED       // Manually cancelled
}

/**
 * Call history entry
 */
@Entity(
    tableName = "call_history",
    indices = [
        Index(value = ["contact_key"]),
        Index(value = ["group_id"]),
        Index(value = ["timestamp"])
    ]
)
data class CallHistoryEntity(
    @PrimaryKey
    val id: String,

    @ColumnInfo(name = "contact_key")
    val contactKey: String? = null,  // Base64, null for group calls

    @ColumnInfo(name = "contact_name")
    val contactName: String,

    @ColumnInfo(name = "group_id")
    val groupId: String? = null,  // Base64

    @ColumnInfo(name = "group_name")
    val groupName: String? = null,

    @ColumnInfo(name = "call_type")
    val callType: Int,  // CallType ordinal

    @ColumnInfo(name = "direction")
    val direction: Int,  // CallDirection ordinal

    @ColumnInfo(name = "status")
    val status: Int,  // CallStatus ordinal

    @ColumnInfo(name = "duration_seconds")
    val durationSeconds: Int = 0,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "ended_at")
    val endedAt: Long? = null,

    @ColumnInfo(name = "participant_count")
    val participantCount: Int = 2,

    @ColumnInfo(name = "is_video")
    val isVideo: Boolean = false,

    @ColumnInfo(name = "is_ptt")
    val isPTT: Boolean = false,

    @ColumnInfo(name = "recording_path")
    val recordingPath: String? = null
)

enum class CallType {
    VOICE,
    VIDEO,
    PTT,
    GROUP_VOICE,
    GROUP_VIDEO
}

enum class CallDirection {
    INCOMING,
    OUTGOING
}

enum class CallStatus {
    MISSED,
    ANSWERED,
    DECLINED,
    FAILED,
    CANCELLED
}

/**
 * Location history for Blue Force Tracking
 */
@Entity(
    tableName = "location_history",
    indices = [
        Index(value = ["member_key"]),
        Index(value = ["timestamp"])
    ]
)
data class LocationHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "member_key")
    val memberKey: String,  // Base64

    @ColumnInfo(name = "member_name")
    val memberName: String,

    @ColumnInfo(name = "latitude")
    val latitude: Double,

    @ColumnInfo(name = "longitude")
    val longitude: Double,

    @ColumnInfo(name = "altitude")
    val altitude: Float = 0f,

    @ColumnInfo(name = "accuracy")
    val accuracy: Float = 0f,

    @ColumnInfo(name = "speed")
    val speed: Float = 0f,

    @ColumnInfo(name = "bearing")
    val bearing: Float = 0f,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "is_self")
    val isSelf: Boolean = false
)

/**
 * SOS/Emergency alert
 */
@Entity(
    tableName = "sos_alerts",
    indices = [
        Index(value = ["sender_key"]),
        Index(value = ["timestamp"]),
        Index(value = ["status"])
    ]
)
data class SOSAlertEntity(
    @PrimaryKey
    val id: String,

    @ColumnInfo(name = "sender_key")
    val senderKey: String,  // Base64

    @ColumnInfo(name = "sender_name")
    val senderName: String,

    @ColumnInfo(name = "alert_type")
    val alertType: Int,  // SOSType ordinal

    @ColumnInfo(name = "message")
    val message: String? = null,

    @ColumnInfo(name = "latitude")
    val latitude: Double? = null,

    @ColumnInfo(name = "longitude")
    val longitude: Double? = null,

    @ColumnInfo(name = "altitude")
    val altitude: Float? = null,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "status")
    val status: Int = 0,  // SOSStatus ordinal

    @ColumnInfo(name = "acknowledged_by")
    val acknowledgedBy: String? = null,  // Base64

    @ColumnInfo(name = "acknowledged_at")
    val acknowledgedAt: Long? = null,

    @ColumnInfo(name = "resolved_at")
    val resolvedAt: Long? = null,

    @ColumnInfo(name = "audio_path")
    val audioPath: String? = null  // Optional voice clip
)

enum class SOSType {
    GENERAL,        // General emergency
    MEDICAL,        // Medical emergency
    SECURITY,       // Security threat
    TACTICAL,       // Tactical alert
    EXTRACTION,     // Extraction required
    CHECK_IN        // Welfare check
}

enum class SOSStatus {
    ACTIVE,         // SOS is active
    ACKNOWLEDGED,   // Someone acknowledged
    RESPONDING,     // Help is on the way
    RESOLVED,       // SOS resolved
    CANCELLED       // Cancelled by sender
}
