/*
 * Mesh Rider Wave - Location Sharing & Blue Force Tracking
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 *
 * Military-grade location sharing for tactical mesh networks
 *
 * Features:
 * - Real-time GPS location sharing
 * - Blue Force Tracking (friendly positions)
 * - Track history with configurable retention
 * - Geofencing alerts
 * - Encrypted location broadcasts
 * - Battery-efficient updates
 */

package com.doodlelabs.meshriderwave.core.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.doodlelabs.meshriderwave.core.crypto.CryptoManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Granularity
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import com.doodlelabs.meshriderwave.core.di.IoDispatcher
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Manages location sharing and Blue Force Tracking
 *
 * Blue Force Tracking (BFT) provides:
 * - Real-time friendly force positions
 * - Track history for movement analysis
 * - Geofencing for area monitoring
 * - Battery-efficient update intervals
 */
@Singleton
class LocationSharingManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cryptoManager: CryptoManager,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    companion object {
        private const val TAG = "MeshRider:Location"

        // Update intervals (milliseconds)
        const val INTERVAL_HIGH_ACCURACY = 5_000L      // 5 seconds - active tracking
        const val INTERVAL_BALANCED = 15_000L          // 15 seconds - normal use
        const val INTERVAL_LOW_POWER = 60_000L         // 1 minute - battery saving
        const val INTERVAL_BACKGROUND = 300_000L      // 5 minutes - background

        // Track history limits
        const val MAX_TRACK_POINTS = 1000
        const val TRACK_RETENTION_MS = 24 * 60 * 60 * 1000L  // 24 hours

        // Geofence defaults
        const val DEFAULT_GEOFENCE_RADIUS_M = 100f

        // Location message type
        const val MSG_TYPE_LOCATION: Byte = 0x10
        const val MSG_TYPE_TRACK: Byte = 0x11
        const val MSG_TYPE_GEOFENCE_ALERT: Byte = 0x12
        const val MSG_TYPE_SOS_LOCATION: Byte = 0x13
    }

    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val mutex = Mutex()

    // Fused location provider
    private val fusedLocationClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    // Location callback
    private var locationCallback: LocationCallback? = null

    // Current location
    private val _myLocation = MutableStateFlow<TrackedLocation?>(null)
    val myLocation: StateFlow<TrackedLocation?> = _myLocation.asStateFlow()

    // Team member locations (publicKeyHex -> TrackedLocation)
    private val _teamLocations = MutableStateFlow<Map<String, TrackedLocation>>(emptyMap())
    val teamLocations: StateFlow<Map<String, TrackedLocation>> = _teamLocations.asStateFlow()

    // Location history for each member
    private val trackHistory = ConcurrentHashMap<String, MutableList<TrackPoint>>()

    // Geofences
    private val _geofences = MutableStateFlow<List<Geofence>>(emptyList())
    val geofences: StateFlow<List<Geofence>> = _geofences.asStateFlow()

    // Geofence alerts
    private val _geofenceAlerts = MutableSharedFlow<GeofenceAlert>(extraBufferCapacity = 10)
    val geofenceAlerts: SharedFlow<GeofenceAlert> = _geofenceAlerts.asSharedFlow()

    // Sharing state
    private val _sharingState = MutableStateFlow<SharingState>(SharingState.Stopped)
    val sharingState: StateFlow<SharingState> = _sharingState.asStateFlow()

    // Location updates for broadcasting
    private val _locationUpdates = MutableSharedFlow<LocationBroadcast>(extraBufferCapacity = 10)
    val locationUpdates: SharedFlow<LocationBroadcast> = _locationUpdates.asSharedFlow()

    // Current update interval
    private var currentInterval = INTERVAL_BALANCED

    // ========== Location Sharing ==========

    /**
     * Start sharing location with configured interval
     */
    fun startSharing(
        interval: Long = INTERVAL_BALANCED,
        accuracy: LocationAccuracy = LocationAccuracy.BALANCED
    ): Boolean {
        if (!hasLocationPermission()) {
            Log.w(TAG, "Location permission not granted")
            return false
        }

        if (_sharingState.value == SharingState.Active) {
            Log.d(TAG, "Already sharing location")
            return true
        }

        currentInterval = interval
        _sharingState.value = SharingState.Starting

        val priority = when (accuracy) {
            LocationAccuracy.HIGH -> Priority.PRIORITY_HIGH_ACCURACY
            LocationAccuracy.BALANCED -> Priority.PRIORITY_BALANCED_POWER_ACCURACY
            LocationAccuracy.LOW_POWER -> Priority.PRIORITY_LOW_POWER
        }

        val locationRequest = LocationRequest.Builder(priority, interval)
            .setMinUpdateIntervalMillis(interval / 2)
            .setWaitForAccurateLocation(accuracy == LocationAccuracy.HIGH)
            .setGranularity(Granularity.GRANULARITY_FINE)
            .build()

        // CRASH-FIX Jan 2026: Use local val to ensure non-null without !!
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    handleLocationUpdate(location)
                }
            }
        }
        locationCallback = callback

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                callback,
                Looper.getMainLooper()
            )
            _sharingState.value = SharingState.Active
            Log.i(TAG, "Location sharing started with ${interval}ms interval")
            return true
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception starting location updates", e)
            _sharingState.value = SharingState.Error("Permission denied")
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start location updates", e)
            _sharingState.value = SharingState.Error(e.message ?: "Unknown error")
            return false
        }
    }

    /**
     * Stop sharing location
     */
    fun stopSharing() {
        locationCallback?.let { callback ->
            try {
                fusedLocationClient.removeLocationUpdates(callback)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to remove location updates", e)
            }
            locationCallback = null
        }
        _sharingState.value = SharingState.Stopped
        Log.i(TAG, "Location sharing stopped")
    }

    /**
     * Get last known location (fast, no GPS update)
     */
    @android.annotation.SuppressLint("MissingPermission")
    suspend fun getLastKnownLocation(): TrackedLocation? {
        if (!hasLocationPermission()) return null

        return try {
            val task = fusedLocationClient.lastLocation
            // Note: In production, use kotlinx-coroutines-play-services for await()
            _myLocation.value
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get last location", e)
            null
        }
    }

    /**
     * Request single high-accuracy location update
     */
    fun requestSingleUpdate(callback: (TrackedLocation?) -> Unit) {
        if (!hasLocationPermission()) {
            callback(null)
            return
        }

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
            .setMaxUpdates(1)
            .build()

        val singleCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    callback(location.toTrackedLocation())
                } ?: callback(null)
                fusedLocationClient.removeLocationUpdates(this)
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                request,
                singleCallback,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied for single update", e)
            callback(null)
        }
    }

    // ========== Team Location Handling ==========

    /**
     * Update team member's location from received broadcast
     */
    fun updateTeamLocation(
        publicKey: ByteArray,
        name: String,
        location: TrackedLocation
    ) {
        scope.launch {
            mutex.withLock {
                val hexKey = publicKey.toHexString()

                // Update current location
                val current = _teamLocations.value.toMutableMap()
                current[hexKey] = location.copy(memberName = name)
                _teamLocations.value = current

                // Add to track history
                val history = trackHistory.getOrPut(hexKey) { mutableListOf() }
                history.add(TrackPoint(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    altitude = location.altitude,
                    timestamp = location.timestamp
                ))

                // Trim old history
                trimTrackHistory(hexKey)

                // Check geofences
                checkGeofences(publicKey, name, location)
            }
        }
    }

    /**
     * Get track history for a team member
     */
    fun getTrackHistory(publicKey: ByteArray): List<TrackPoint> {
        val hexKey = publicKey.toHexString()
        return trackHistory[hexKey]?.toList() ?: emptyList()
    }

    /**
     * Clear track history for a member
     */
    fun clearTrackHistory(publicKey: ByteArray) {
        val hexKey = publicKey.toHexString()
        trackHistory.remove(hexKey)
    }

    /**
     * Remove team member location
     */
    fun removeTeamMember(publicKey: ByteArray) {
        val hexKey = publicKey.toHexString()
        _teamLocations.value = _teamLocations.value - hexKey
        trackHistory.remove(hexKey)
    }

    // ========== Geofencing ==========

    /**
     * Add a geofence
     */
    fun addGeofence(geofence: Geofence) {
        _geofences.value = _geofences.value + geofence
        Log.d(TAG, "Added geofence: ${geofence.name}")
    }

    /**
     * Remove a geofence
     */
    fun removeGeofence(geofenceId: String) {
        _geofences.value = _geofences.value.filter { it.id != geofenceId }
    }

    /**
     * Create a geofence around current location
     */
    fun createGeofenceHere(
        name: String,
        radiusMeters: Float = DEFAULT_GEOFENCE_RADIUS_M,
        type: GeofenceType = GeofenceType.ALERT_ON_EXIT
    ): Geofence? {
        val location = _myLocation.value ?: return null

        return Geofence(
            id = java.util.UUID.randomUUID().toString(),
            name = name,
            latitude = location.latitude,
            longitude = location.longitude,
            radiusMeters = radiusMeters,
            type = type,
            createdAt = System.currentTimeMillis()
        ).also { addGeofence(it) }
    }

    // ========== Location Serialization ==========

    /**
     * Serialize location for network transmission
     * Format: type(1) + lat(8) + lon(8) + alt(4) + accuracy(4) + speed(4) + bearing(4) + timestamp(8)
     */
    fun serializeLocation(location: TrackedLocation, type: Byte = MSG_TYPE_LOCATION): ByteArray {
        val buffer = ByteBuffer.allocate(41)
        buffer.put(type)
        buffer.putDouble(location.latitude)
        buffer.putDouble(location.longitude)
        buffer.putFloat(location.altitude)
        buffer.putFloat(location.accuracy)
        buffer.putFloat(location.speed)
        buffer.putFloat(location.bearing)
        buffer.putLong(location.timestamp)
        return buffer.array()
    }

    /**
     * Deserialize location from network
     */
    fun deserializeLocation(data: ByteArray): Pair<Byte, TrackedLocation>? {
        if (data.size < 41) return null

        return try {
            val buffer = ByteBuffer.wrap(data)
            val type = buffer.get()
            val latitude = buffer.double
            val longitude = buffer.double
            val altitude = buffer.float
            val accuracy = buffer.float
            val speed = buffer.float
            val bearing = buffer.float
            val timestamp = buffer.long

            type to TrackedLocation(
                latitude = latitude,
                longitude = longitude,
                altitude = altitude,
                accuracy = accuracy,
                speed = speed,
                bearing = bearing,
                timestamp = timestamp
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to deserialize location", e)
            null
        }
    }

    // ========== Internal Methods ==========

    private fun handleLocationUpdate(location: Location) {
        val trackedLocation = location.toTrackedLocation()

        // Update my location
        _myLocation.value = trackedLocation

        // Add to own track history
        val myHistory = trackHistory.getOrPut("self") { mutableListOf() }
        myHistory.add(TrackPoint(
            latitude = trackedLocation.latitude,
            longitude = trackedLocation.longitude,
            altitude = trackedLocation.altitude,
            timestamp = trackedLocation.timestamp
        ))
        trimTrackHistory("self")

        // Emit for broadcasting
        scope.launch {
            _locationUpdates.emit(LocationBroadcast(
                location = trackedLocation,
                serialized = serializeLocation(trackedLocation)
            ))
        }

        Log.v(TAG, "Location: ${trackedLocation.latitude}, ${trackedLocation.longitude}")
    }

    private fun checkGeofences(publicKey: ByteArray, name: String, location: TrackedLocation) {
        for (geofence in _geofences.value) {
            val distance = haversineDistance(
                location.latitude, location.longitude,
                geofence.latitude, geofence.longitude
            )

            val isInside = distance <= geofence.radiusMeters

            // Check for entry/exit based on previous state
            val hexKey = publicKey.toHexString()
            val wasInside = geofence.membersInside.contains(hexKey)

            when (geofence.type) {
                GeofenceType.ALERT_ON_ENTRY -> {
                    if (isInside && !wasInside) {
                        emitGeofenceAlert(geofence, name, publicKey, GeofenceEvent.ENTERED)
                    }
                }
                GeofenceType.ALERT_ON_EXIT -> {
                    if (!isInside && wasInside) {
                        emitGeofenceAlert(geofence, name, publicKey, GeofenceEvent.EXITED)
                    }
                }
                GeofenceType.ALERT_BOTH -> {
                    if (isInside && !wasInside) {
                        emitGeofenceAlert(geofence, name, publicKey, GeofenceEvent.ENTERED)
                    } else if (!isInside && wasInside) {
                        emitGeofenceAlert(geofence, name, publicKey, GeofenceEvent.EXITED)
                    }
                }
            }

            // Update inside state
            val updatedGeofence = if (isInside) {
                geofence.copy(membersInside = geofence.membersInside + hexKey)
            } else {
                geofence.copy(membersInside = geofence.membersInside - hexKey)
            }

            if (updatedGeofence != geofence) {
                _geofences.value = _geofences.value.map {
                    if (it.id == geofence.id) updatedGeofence else it
                }
            }
        }
    }

    private fun emitGeofenceAlert(
        geofence: Geofence,
        memberName: String,
        publicKey: ByteArray,
        event: GeofenceEvent
    ) {
        scope.launch {
            _geofenceAlerts.emit(GeofenceAlert(
                geofence = geofence,
                memberName = memberName,
                memberPublicKey = publicKey,
                event = event,
                timestamp = System.currentTimeMillis()
            ))
        }
        Log.i(TAG, "Geofence alert: $memberName ${event.name} ${geofence.name}")
    }

    private fun trimTrackHistory(key: String) {
        val history = trackHistory[key] ?: return
        val now = System.currentTimeMillis()

        // Remove old entries
        history.removeAll { now - it.timestamp > TRACK_RETENTION_MS }

        // Limit count
        while (history.size > MAX_TRACK_POINTS) {
            history.removeAt(0)
        }
    }

    /**
     * Haversine formula for distance calculation
     */
    private fun haversineDistance(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Float {
        val r = 6371000.0  // Earth radius in meters

        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return (r * c).toFloat()
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun Location.toTrackedLocation(): TrackedLocation {
        return TrackedLocation(
            latitude = latitude,
            longitude = longitude,
            altitude = altitude.toFloat(),
            accuracy = accuracy,
            speed = speed,
            bearing = bearing,
            timestamp = time,
            provider = provider ?: "unknown"
        )
    }

    private fun ByteArray.toHexString(): String =
        joinToString("") { "%02x".format(it) }

    // ========== Lifecycle ==========

    // Track if cleanup has been called (FIXED Jan 2026)
    @Volatile
    private var isCleanedUp = false

    /**
     * Cleanup resources
     * FIXED Jan 2026: Made idempotent and added scope cancellation
     */
    fun cleanup() {
        if (isCleanedUp) {
            Log.d(TAG, "LocationSharingManager already cleaned up, skipping")
            return
        }
        isCleanedUp = true

        stopSharing()
        trackHistory.clear()
        _teamLocations.value = emptyMap()
        _geofences.value = emptyList()
        // Note: scope not cancelled as this is a singleton that may be reused
    }
}

// ========== Data Classes ==========

/**
 * Tracked location with metadata
 */
data class TrackedLocation(
    val latitude: Double,
    val longitude: Double,
    val altitude: Float = 0f,
    val accuracy: Float = 0f,
    val speed: Float = 0f,
    val bearing: Float = 0f,
    val timestamp: Long = System.currentTimeMillis(),
    val provider: String = "gps",
    val memberName: String? = null,
    val memberPublicKey: ByteArray? = null
) {
    /**
     * Format as degrees, minutes, seconds
     */
    fun toDMS(): String {
        val latDir = if (latitude >= 0) "N" else "S"
        val lonDir = if (longitude >= 0) "E" else "W"

        val latDeg = Math.abs(latitude).toInt()
        val latMin = ((Math.abs(latitude) - latDeg) * 60).toInt()
        val latSec = ((Math.abs(latitude) - latDeg - latMin / 60.0) * 3600).toFloat()

        val lonDeg = Math.abs(longitude).toInt()
        val lonMin = ((Math.abs(longitude) - lonDeg) * 60).toInt()
        val lonSec = ((Math.abs(longitude) - lonDeg - lonMin / 60.0) * 3600).toFloat()

        return "$latDeg°$latMin'${String.format("%.1f", latSec)}\"$latDir, " +
                "$lonDeg°$lonMin'${String.format("%.1f", lonSec)}\"$lonDir"
    }

    /**
     * Format as MGRS (Military Grid Reference System) - simplified
     */
    fun toMGRS(): String {
        // Simplified - real implementation would use proper MGRS conversion
        return "%.4f, %.4f".format(latitude, longitude)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TrackedLocation) return false
        return latitude == other.latitude && longitude == other.longitude && timestamp == other.timestamp
    }

    override fun hashCode(): Int = 31 * latitude.hashCode() + longitude.hashCode()
}

/**
 * Point in track history
 */
data class TrackPoint(
    val latitude: Double,
    val longitude: Double,
    val altitude: Float = 0f,
    val timestamp: Long
)

/**
 * Geofence definition
 */
data class Geofence(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float,
    val type: GeofenceType = GeofenceType.ALERT_ON_EXIT,
    val createdAt: Long = System.currentTimeMillis(),
    val membersInside: Set<String> = emptySet()  // Public key hex strings
)

enum class GeofenceType {
    ALERT_ON_ENTRY,
    ALERT_ON_EXIT,
    ALERT_BOTH
}

enum class GeofenceEvent {
    ENTERED,
    EXITED
}

/**
 * Geofence alert
 */
data class GeofenceAlert(
    val geofence: Geofence,
    val memberName: String,
    val memberPublicKey: ByteArray,
    val event: GeofenceEvent,
    val timestamp: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GeofenceAlert) return false
        return geofence.id == other.geofence.id &&
                memberPublicKey.contentEquals(other.memberPublicKey) &&
                event == other.event
    }

    override fun hashCode(): Int = geofence.id.hashCode()
}

/**
 * Location broadcast for network transmission
 */
data class LocationBroadcast(
    val location: TrackedLocation,
    val serialized: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LocationBroadcast) return false
        return location == other.location
    }

    override fun hashCode(): Int = location.hashCode()
}

/**
 * Location accuracy modes
 */
enum class LocationAccuracy {
    HIGH,       // GPS + Network, high battery usage
    BALANCED,   // Balanced accuracy and power
    LOW_POWER   // Network only, low battery usage
}

/**
 * Location sharing state
 */
sealed class SharingState {
    data object Stopped : SharingState()
    data object Starting : SharingState()
    data object Active : SharingState()
    data class Error(val message: String) : SharingState()
}
