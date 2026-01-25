/*
 * Mesh Rider Wave ATAK Plugin - Team Marker Manager
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 *
 * Manages Blue Force Tracking markers on ATAK map
 * Shows team positions from MeshRider Wave app
 */

package com.doodlelabs.meshriderwave.atak.map

import android.graphics.Color
import android.util.Log

/**
 * Team member position data
 */
data class TeamPosition(
    val uid: String,              // Unique identifier (public key hex)
    val callsign: String,         // Display name
    val latitude: Double,
    val longitude: Double,
    val altitude: Double = 0.0,
    val course: Double = 0.0,     // Heading in degrees
    val speed: Double = 0.0,      // Speed in m/s
    val timestamp: Long,          // Last update time
    val status: Status = Status.ACTIVE,
    val role: Role = Role.TEAM_MEMBER,
    val hasSOS: Boolean = false
) {
    enum class Status {
        ACTIVE,      // Recently updated (< 60s)
        STALE,       // Old position (60s - 5min)
        OFFLINE      // Very old (> 5min)
    }

    enum class Role {
        TEAM_LEAD,
        TEAM_MEMBER,
        OBSERVER
    }

    /**
     * Generate CoT (Cursor on Target) XML for ATAK
     */
    fun toCoTXml(): String {
        val now = System.currentTimeMillis()
        val staleTime = now + 300_000 // 5 minutes
        val timeStr = formatISO8601(now)
        val staleStr = formatISO8601(staleTime)

        // MIL-STD-2525 type code
        val typeCode = when {
            hasSOS -> "a-f-G-U-C-E-M"  // Emergency
            role == Role.TEAM_LEAD -> "a-f-G-U-C-I"  // Infantry leader
            else -> "a-f-G-U-C"  // Ground unit
        }

        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <event version="2.0"
                   uid="MRWave-$uid"
                   type="$typeCode"
                   time="$timeStr"
                   start="$timeStr"
                   stale="$staleStr"
                   how="m-g">
                <point lat="$latitude"
                       lon="$longitude"
                       hae="$altitude"
                       ce="10.0"
                       le="10.0"/>
                <detail>
                    <contact callsign="$callsign"/>
                    <__group name="MeshRider Wave" role="${role.name}"/>
                    <track course="$course" speed="$speed"/>
                    <precisionlocation geopointsrc="GPS" altsrc="GPS"/>
                    <status readiness="true"/>
                    <MeshRiderWave
                        version="2.3.0"
                        app="com.doodlelabs.meshriderwave"
                        hasSOS="$hasSOS"/>
                </detail>
            </event>
        """.trimIndent()
    }

    /**
     * Generate CoT removal message (for offline contacts)
     */
    fun toCoTRemovalXml(): String {
        val now = System.currentTimeMillis()
        val timeStr = formatISO8601(now)

        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <event version="2.0"
                   uid="MRWave-$uid"
                   type="t-x-d-d"
                   time="$timeStr"
                   start="$timeStr"
                   stale="$timeStr"
                   how="h-g-i-g-o">
                <point lat="0" lon="0" hae="0" ce="0" le="0"/>
                <detail/>
            </event>
        """.trimIndent()
    }

    private fun formatISO8601(timestamp: Long): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
        sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
        return sdf.format(java.util.Date(timestamp))
    }
}

/**
 * Manages team member markers on ATAK map
 *
 * Features:
 * - Converts MeshRider Wave positions to CoT for ATAK
 * - Tracks active/stale/offline status
 * - Handles SOS highlighting
 * - Auto-removes stale markers
 */
class TeamMarkerManager {

    companion object {
        private const val TAG = "MRWave:TeamMarkers"

        // Timing thresholds (milliseconds)
        const val STALE_THRESHOLD = 60_000L      // 1 minute
        const val OFFLINE_THRESHOLD = 300_000L   // 5 minutes
        const val CLEANUP_INTERVAL = 30_000L     // 30 seconds
    }

    // Active team positions
    private val positions = mutableMapOf<String, TeamPosition>()

    // Callback for sending CoT to ATAK
    var onCoTGenerated: ((String) -> Unit)? = null

    // Callback for marker removal
    var onMarkerRemoved: ((String) -> Unit)? = null

    /**
     * Update or add a team member position
     */
    fun updatePosition(
        uid: String,
        callsign: String,
        latitude: Double,
        longitude: Double,
        altitude: Double = 0.0,
        course: Double = 0.0,
        speed: Double = 0.0,
        role: TeamPosition.Role = TeamPosition.Role.TEAM_MEMBER,
        hasSOS: Boolean = false
    ) {
        val position = TeamPosition(
            uid = uid,
            callsign = callsign,
            latitude = latitude,
            longitude = longitude,
            altitude = altitude,
            course = course,
            speed = speed,
            timestamp = System.currentTimeMillis(),
            status = TeamPosition.Status.ACTIVE,
            role = role,
            hasSOS = hasSOS
        )

        positions[uid] = position

        // Generate and send CoT
        val cot = position.toCoTXml()
        onCoTGenerated?.invoke(cot)

        Log.d(TAG, "Updated position for $callsign: ($latitude, $longitude)")
    }

    /**
     * Remove a team member
     */
    fun removePosition(uid: String) {
        positions[uid]?.let { position ->
            // Send removal CoT
            val removalCot = position.toCoTRemovalXml()
            onCoTGenerated?.invoke(removalCot)
            onMarkerRemoved?.invoke(uid)
        }
        positions.remove(uid)
        Log.d(TAG, "Removed position for $uid")
    }

    /**
     * Update SOS status for a team member
     */
    fun setSOSActive(uid: String, active: Boolean) {
        positions[uid]?.let { position ->
            val updated = position.copy(hasSOS = active, timestamp = System.currentTimeMillis())
            positions[uid] = updated

            // Immediately send updated CoT
            val cot = updated.toCoTXml()
            onCoTGenerated?.invoke(cot)

            Log.i(TAG, "SOS ${if (active) "ACTIVATED" else "CLEARED"} for ${position.callsign}")
        }
    }

    /**
     * Get all active positions
     */
    fun getActivePositions(): List<TeamPosition> {
        return positions.values.filter { it.status == TeamPosition.Status.ACTIVE }
    }

    /**
     * Get all positions (including stale)
     */
    fun getAllPositions(): List<TeamPosition> {
        return positions.values.toList()
    }

    /**
     * Check and update stale status for all positions
     * Call this periodically
     */
    fun checkStalePositions() {
        val now = System.currentTimeMillis()
        val toRemove = mutableListOf<String>()

        positions.forEach { (uid, position) ->
            val age = now - position.timestamp

            when {
                age > OFFLINE_THRESHOLD -> {
                    // Remove very old positions
                    toRemove.add(uid)
                }
                age > STALE_THRESHOLD -> {
                    // Mark as stale
                    if (position.status != TeamPosition.Status.STALE) {
                        positions[uid] = position.copy(status = TeamPosition.Status.STALE)
                        Log.d(TAG, "${position.callsign} is now STALE")
                    }
                }
            }
        }

        // Remove offline positions
        toRemove.forEach { removePosition(it) }
    }

    /**
     * Clear all positions
     */
    fun clearAll() {
        positions.keys.toList().forEach { removePosition(it) }
        positions.clear()
    }

    /**
     * Get position count by status
     */
    fun getStatusCounts(): Map<TeamPosition.Status, Int> {
        return positions.values.groupingBy { it.status }.eachCount()
    }

    /**
     * Check if any team member has active SOS
     */
    fun hasActiveSOS(): Boolean {
        return positions.values.any { it.hasSOS }
    }

    /**
     * Get members with active SOS
     */
    fun getSOSMembers(): List<TeamPosition> {
        return positions.values.filter { it.hasSOS }
    }
}

/**
 * Extension: Get color for marker based on status and role
 */
fun TeamPosition.getMarkerColor(): Int {
    return when {
        hasSOS -> Color.RED
        status == TeamPosition.Status.OFFLINE -> Color.GRAY
        status == TeamPosition.Status.STALE -> Color.YELLOW
        role == TeamPosition.Role.TEAM_LEAD -> Color.BLUE
        else -> Color.GREEN
    }
}

/**
 * Extension: Get icon resource name for ATAK
 */
fun TeamPosition.getIconName(): String {
    return when {
        hasSOS -> "sos_alert"
        role == TeamPosition.Role.TEAM_LEAD -> "team_lead"
        else -> "team_member"
    }
}
