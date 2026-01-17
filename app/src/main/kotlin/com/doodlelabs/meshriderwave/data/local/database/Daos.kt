/*
 * Mesh Rider Wave - Room DAOs
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 *
 * Data Access Objects for database operations
 */

package com.doodlelabs.meshriderwave.data.local.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * DAO for offline message queue operations
 */
@Dao
interface OfflineMessageDao {

    // ========== Queries ==========

    @Query("SELECT * FROM offline_messages WHERE status = :status ORDER BY priority DESC, created_at ASC")
    fun getMessagesByStatus(status: Int): Flow<List<OfflineMessageEntity>>

    @Query("SELECT * FROM offline_messages WHERE status = 0 ORDER BY priority DESC, created_at ASC")
    fun getPendingMessages(): Flow<List<OfflineMessageEntity>>

    @Query("SELECT * FROM offline_messages WHERE recipient_key = :recipientKey AND status = 0 ORDER BY priority DESC, created_at ASC")
    fun getPendingMessagesForRecipient(recipientKey: String): Flow<List<OfflineMessageEntity>>

    @Query("SELECT * FROM offline_messages WHERE group_id = :groupId AND status = 0 ORDER BY priority DESC, created_at ASC")
    fun getPendingMessagesForGroup(groupId: String): Flow<List<OfflineMessageEntity>>

    @Query("SELECT * FROM offline_messages WHERE id = :id")
    suspend fun getMessageById(id: String): OfflineMessageEntity?

    @Query("SELECT COUNT(*) FROM offline_messages WHERE status = 0")
    fun getPendingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM offline_messages WHERE recipient_key = :recipientKey AND status = 0")
    suspend fun getPendingCountForRecipient(recipientKey: String): Int

    // ========== Modifications ==========

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: OfflineMessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<OfflineMessageEntity>)

    @Update
    suspend fun update(message: OfflineMessageEntity)

    @Delete
    suspend fun delete(message: OfflineMessageEntity)

    @Query("DELETE FROM offline_messages WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM offline_messages WHERE recipient_key = :recipientKey")
    suspend fun deleteAllForRecipient(recipientKey: String)

    @Query("DELETE FROM offline_messages WHERE status = :status")
    suspend fun deleteAllByStatus(status: Int)

    @Query("DELETE FROM offline_messages WHERE expires_at < :currentTime")
    suspend fun deleteExpired(currentTime: Long = System.currentTimeMillis())

    // ========== Status Updates ==========

    @Query("UPDATE offline_messages SET status = :status, last_attempt_at = :attemptTime WHERE id = :id")
    suspend fun updateStatus(id: String, status: Int, attemptTime: Long = System.currentTimeMillis())

    @Query("UPDATE offline_messages SET status = 2, delivered_at = :deliveredAt WHERE id = :id")
    suspend fun markDelivered(id: String, deliveredAt: Long = System.currentTimeMillis())

    @Query("UPDATE offline_messages SET status = 3, error_message = :error, retry_count = retry_count + 1 WHERE id = :id")
    suspend fun markFailed(id: String, error: String?)

    @Query("UPDATE offline_messages SET retry_count = retry_count + 1, last_attempt_at = :attemptTime WHERE id = :id")
    suspend fun incrementRetry(id: String, attemptTime: Long = System.currentTimeMillis())

    // ========== Cleanup ==========

    @Query("DELETE FROM offline_messages WHERE created_at < :cutoffTime")
    suspend fun deleteOlderThan(cutoffTime: Long)

    @Query("DELETE FROM offline_messages")
    suspend fun deleteAll()
}

/**
 * DAO for call history operations
 */
@Dao
interface CallHistoryDao {

    @Query("SELECT * FROM call_history ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentCalls(limit: Int = 50): Flow<List<CallHistoryEntity>>

    @Query("SELECT * FROM call_history WHERE contact_key = :contactKey ORDER BY timestamp DESC LIMIT :limit")
    fun getCallsForContact(contactKey: String, limit: Int = 20): Flow<List<CallHistoryEntity>>

    @Query("SELECT * FROM call_history WHERE group_id = :groupId ORDER BY timestamp DESC LIMIT :limit")
    fun getCallsForGroup(groupId: String, limit: Int = 20): Flow<List<CallHistoryEntity>>

    @Query("SELECT * FROM call_history WHERE direction = :direction ORDER BY timestamp DESC LIMIT :limit")
    fun getCallsByDirection(direction: Int, limit: Int = 50): Flow<List<CallHistoryEntity>>

    @Query("SELECT * FROM call_history WHERE status = 0 ORDER BY timestamp DESC LIMIT :limit")
    fun getMissedCalls(limit: Int = 50): Flow<List<CallHistoryEntity>>

    @Query("SELECT COUNT(*) FROM call_history WHERE status = 0 AND timestamp > :since")
    fun getMissedCallCount(since: Long = 0): Flow<Int>

    @Query("SELECT * FROM call_history WHERE id = :id")
    suspend fun getCallById(id: String): CallHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(call: CallHistoryEntity)

    @Update
    suspend fun update(call: CallHistoryEntity)

    @Delete
    suspend fun delete(call: CallHistoryEntity)

    @Query("DELETE FROM call_history WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM call_history WHERE contact_key = :contactKey")
    suspend fun deleteAllForContact(contactKey: String)

    @Query("DELETE FROM call_history WHERE timestamp < :cutoffTime")
    suspend fun deleteOlderThan(cutoffTime: Long)

    @Query("DELETE FROM call_history")
    suspend fun deleteAll()
}

/**
 * DAO for location history (Blue Force Tracking)
 */
@Dao
interface LocationHistoryDao {

    @Query("SELECT * FROM location_history WHERE member_key = :memberKey ORDER BY timestamp DESC LIMIT :limit")
    fun getLocationHistory(memberKey: String, limit: Int = 100): Flow<List<LocationHistoryEntity>>

    @Query("SELECT * FROM location_history WHERE member_key = :memberKey AND timestamp > :since ORDER BY timestamp ASC")
    fun getLocationHistorySince(memberKey: String, since: Long): Flow<List<LocationHistoryEntity>>

    @Query("SELECT * FROM location_history WHERE is_self = 1 ORDER BY timestamp DESC LIMIT :limit")
    fun getOwnLocationHistory(limit: Int = 100): Flow<List<LocationHistoryEntity>>

    @Query("SELECT * FROM location_history WHERE timestamp > :since ORDER BY timestamp DESC")
    fun getAllLocationsSince(since: Long): Flow<List<LocationHistoryEntity>>

    @Query("SELECT DISTINCT member_key, member_name FROM location_history")
    fun getTrackedMembers(): Flow<List<TrackedMember>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(location: LocationHistoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(locations: List<LocationHistoryEntity>)

    @Query("DELETE FROM location_history WHERE member_key = :memberKey")
    suspend fun deleteAllForMember(memberKey: String)

    @Query("DELETE FROM location_history WHERE timestamp < :cutoffTime")
    suspend fun deleteOlderThan(cutoffTime: Long)

    @Query("DELETE FROM location_history")
    suspend fun deleteAll()
}

/**
 * Helper data class for tracked members query
 */
data class TrackedMember(
    val member_key: String,
    val member_name: String
)

/**
 * DAO for SOS/Emergency alerts
 */
@Dao
interface SOSAlertDao {

    @Query("SELECT * FROM sos_alerts ORDER BY timestamp DESC LIMIT :limit")
    fun getAllAlerts(limit: Int = 100): Flow<List<SOSAlertEntity>>

    @Query("SELECT * FROM sos_alerts WHERE status = 0 ORDER BY timestamp DESC")
    fun getActiveAlerts(): Flow<List<SOSAlertEntity>>

    @Query("SELECT * FROM sos_alerts WHERE sender_key = :senderKey ORDER BY timestamp DESC LIMIT :limit")
    fun getAlertsFromSender(senderKey: String, limit: Int = 20): Flow<List<SOSAlertEntity>>

    @Query("SELECT * FROM sos_alerts WHERE id = :id")
    suspend fun getAlertById(id: String): SOSAlertEntity?

    @Query("SELECT COUNT(*) FROM sos_alerts WHERE status = 0")
    fun getActiveAlertCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(alert: SOSAlertEntity)

    @Update
    suspend fun update(alert: SOSAlertEntity)

    @Delete
    suspend fun delete(alert: SOSAlertEntity)

    @Query("DELETE FROM sos_alerts WHERE id = :id")
    suspend fun deleteById(id: String)

    // ========== Status Updates ==========

    @Query("UPDATE sos_alerts SET status = 1, acknowledged_by = :acknowledgedBy, acknowledged_at = :acknowledgedAt WHERE id = :id")
    suspend fun acknowledge(id: String, acknowledgedBy: String, acknowledgedAt: Long = System.currentTimeMillis())

    @Query("UPDATE sos_alerts SET status = 2 WHERE id = :id")
    suspend fun markResponding(id: String)

    @Query("UPDATE sos_alerts SET status = 3, resolved_at = :resolvedAt WHERE id = :id")
    suspend fun resolve(id: String, resolvedAt: Long = System.currentTimeMillis())

    @Query("UPDATE sos_alerts SET status = 4 WHERE id = :id")
    suspend fun cancel(id: String)

    // ========== Cleanup ==========

    @Query("DELETE FROM sos_alerts WHERE timestamp < :cutoffTime")
    suspend fun deleteOlderThan(cutoffTime: Long)

    @Query("DELETE FROM sos_alerts")
    suspend fun deleteAll()
}
