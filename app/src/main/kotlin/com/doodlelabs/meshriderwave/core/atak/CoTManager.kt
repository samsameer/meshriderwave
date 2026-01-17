/*
 * Mesh Rider Wave - Cursor-on-Target (CoT) Manager
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 *
 * ATAK (Android Tactical Assault Kit) integration via CoT protocol
 * Enables Blue Force Tracking interoperability with military/tactical systems
 *
 * Features:
 * - Send peer locations to ATAK via multicast/broadcast
 * - Receive ATAK position updates (PLI - Position Location Information)
 * - Convert between MR Wave TrackedLocation and CoT format
 * - Multicast CoT to mesh network peers
 * - SOS/emergency alert broadcasting
 *
 * Network Configuration:
 * - Multicast Address: 239.2.3.1 (SA multicast - ATAK default)
 * - Port: 6969 (ATAK CoT port)
 * - Protocol: UDP
 *
 * References:
 * - ATAK SDK Documentation
 * - CoT Working Group specifications
 * - MIL-STD-2525D for symbology
 */

package com.doodlelabs.meshriderwave.core.atak

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.BatteryManager
import android.util.Log
import com.doodlelabs.meshriderwave.core.di.IoDispatcher
import com.doodlelabs.meshriderwave.core.location.LocationSharingManager
import com.doodlelabs.meshriderwave.core.location.TrackedLocation
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * CoT Manager for ATAK interoperability
 *
 * Manages bidirectional CoT communication:
 * 1. Sends local device positions to ATAK (outbound)
 * 2. Receives ATAK positions and updates (inbound)
 * 3. Multicasts peer locations to mesh network
 *
 * Usage:
 * ```kotlin
 * @Inject lateinit var cotManager: CoTManager
 *
 * // Start CoT services
 * cotManager.start(deviceId, callsign)
 *
 * // Listen for ATAK updates
 * cotManager.receivedMessages.collect { message ->
 *     // Handle incoming CoT
 * }
 *
 * // Broadcast location
 * cotManager.broadcastLocation(trackedLocation)
 *
 * // Stop services
 * cotManager.stop()
 * ```
 */
@Singleton
class CoTManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val locationSharingManager: LocationSharingManager,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    companion object {
        private const val TAG = "MeshRider:CoT"

        // ========== Network Configuration ==========

        // ATAK SA (Situational Awareness) Multicast Group
        const val ATAK_MULTICAST_ADDRESS = "239.2.3.1"
        const val ATAK_MULTICAST_PORT = 6969

        // Mesh Rider internal multicast (for mesh-only broadcasts)
        const val MESH_MULTICAST_ADDRESS = "239.255.77.82"  // 'MR' in hex
        const val MESH_MULTICAST_PORT = 6970

        // Receive buffer size (CoT messages can be up to 64KB but typically <4KB)
        const val RECEIVE_BUFFER_SIZE = 65536

        // Socket timeouts
        const val RECEIVE_TIMEOUT_MS = 1000
        const val RECONNECT_DELAY_MS = 5000L

        // Position update intervals
        const val DEFAULT_BROADCAST_INTERVAL_MS = 15_000L  // 15 seconds (ATAK default)
        const val FAST_BROADCAST_INTERVAL_MS = 5_000L      // 5 seconds (moving/emergency)
        const val SLOW_BROADCAST_INTERVAL_MS = 60_000L     // 1 minute (stationary)

        // TTL for multicast (how many network hops)
        const val MULTICAST_TTL = 32

        // Mesh network interface patterns
        private val MESH_INTERFACES = listOf("wlan", "mesh", "br-mesh", "smartradio", "eth")
    }

    // Coroutine management
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val mutex = Mutex()

    // Network sockets
    private var multicastSocket: MulticastSocket? = null
    private var unicastSocket: DatagramSocket? = null

    // Jobs
    private var receiveJob: Job? = null
    private var broadcastJob: Job? = null
    private var networkMonitorJob: Job? = null

    // Device identity
    private var deviceId: String = ""
    private var callsign: String = ""
    private var groupName: String? = null
    private var groupRole: String = CoTDetail.ROLE_TEAM_MEMBER

    // ========== State Flows ==========

    // Manager state
    private val _state = MutableStateFlow<CoTState>(CoTState.Stopped)
    val state: StateFlow<CoTState> = _state.asStateFlow()

    // Received CoT messages from ATAK/network
    private val _receivedMessages = MutableSharedFlow<CoTMessage>(
        replay = 0,
        extraBufferCapacity = 100,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val receivedMessages: SharedFlow<CoTMessage> = _receivedMessages.asSharedFlow()

    // ATAK devices seen on network (uid -> last message)
    private val _atakDevices = MutableStateFlow<Map<String, CoTMessage>>(emptyMap())
    val atakDevices: StateFlow<Map<String, CoTMessage>> = _atakDevices.asStateFlow()

    // Statistics
    private val _stats = MutableStateFlow(CoTStats())
    val stats: StateFlow<CoTStats> = _stats.asStateFlow()

    // Internal tracking
    private val seenDevices = ConcurrentHashMap<String, CoTMessage>()
    private var lastBroadcastLocation: TrackedLocation? = null
    private var broadcastInterval = DEFAULT_BROADCAST_INTERVAL_MS

    // Network monitoring
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    // ========== Public API ==========

    /**
     * Start CoT services
     *
     * @param deviceId Unique device identifier (typically from public key)
     * @param callsign Human-readable name shown on ATAK map
     * @param groupName Optional team/group name
     * @param groupRole Role within group (default: Team Member)
     */
    fun start(
        deviceId: String,
        callsign: String,
        groupName: String? = null,
        groupRole: String = CoTDetail.ROLE_TEAM_MEMBER
    ) {
        if (_state.value == CoTState.Running) {
            Log.w(TAG, "CoT Manager already running")
            return
        }

        this.deviceId = deviceId
        this.callsign = callsign
        this.groupName = groupName
        this.groupRole = groupRole

        _state.value = CoTState.Starting

        scope.launch {
            try {
                // Initialize sockets
                initializeSockets()

                // Start receive loop
                startReceiveLoop()

                // Start network monitoring
                startNetworkMonitoring()

                // Start automatic position broadcasting
                startAutoBroadcast()

                _state.value = CoTState.Running
                Log.i(TAG, "CoT Manager started: callsign=$callsign, group=$groupName")

            } catch (e: Exception) {
                Log.e(TAG, "Failed to start CoT Manager", e)
                _state.value = CoTState.Error(e.message ?: "Failed to start")
                cleanup()
            }
        }
    }

    /**
     * Stop CoT services
     */
    fun stop() {
        scope.launch {
            cleanup()
            _state.value = CoTState.Stopped
            Log.i(TAG, "CoT Manager stopped")
        }
    }

    /**
     * Broadcast current location to ATAK
     *
     * @param location Current GPS location
     * @param isSos Whether this is an SOS/emergency broadcast
     */
    suspend fun broadcastLocation(
        location: TrackedLocation,
        isSos: Boolean = false
    ) = withContext(ioDispatcher) {
        if (_state.value != CoTState.Running) {
            Log.w(TAG, "Cannot broadcast: CoT Manager not running")
            return@withContext
        }

        try {
            val message = createCoTMessage(location, isSos)
            val xml = message.toXml()
            val data = xml.toByteArray(Charsets.UTF_8)

            // Send to ATAK multicast
            sendMulticast(data, ATAK_MULTICAST_ADDRESS, ATAK_MULTICAST_PORT)

            // Also send to mesh multicast for Mesh Rider peers
            sendMulticast(data, MESH_MULTICAST_ADDRESS, MESH_MULTICAST_PORT)

            lastBroadcastLocation = location
            updateStats { copy(messagesSent = messagesSent + 1) }

            Log.d(TAG, "Broadcasted ${if (isSos) "SOS" else "position"}: ${location.latitude}, ${location.longitude}")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to broadcast location", e)
            updateStats { copy(sendErrors = sendErrors + 1) }
        }
    }

    /**
     * Send SOS emergency broadcast
     *
     * Sends with emergency type code and extended stale time
     */
    suspend fun broadcastSOS(location: TrackedLocation, message: String? = null) {
        broadcastLocation(location.copy(), isSos = true)

        // Also broadcast text message if provided
        message?.let { remarks ->
            scope.launch {
                val sosMessage = createCoTMessage(location, isSos = true).copy(
                    detail = CoTDetail(
                        callsign = callsign,
                        groupName = groupName,
                        groupRole = groupRole,
                        remarks = "SOS: $remarks"
                    )
                )
                sendMulticast(
                    sosMessage.toXml().toByteArray(Charsets.UTF_8),
                    ATAK_MULTICAST_ADDRESS,
                    ATAK_MULTICAST_PORT
                )
            }
        }
    }

    /**
     * Send CoT message to specific peer (unicast)
     */
    suspend fun sendToPeer(
        message: CoTMessage,
        peerAddress: String,
        port: Int = ATAK_MULTICAST_PORT
    ) = withContext(ioDispatcher) {
        try {
            val xml = message.toXml()
            val data = xml.toByteArray(Charsets.UTF_8)

            val socket = unicastSocket ?: DatagramSocket().also { unicastSocket = it }
            val address = InetAddress.getByName(peerAddress)
            val packet = DatagramPacket(data, data.size, address, port)

            socket.send(packet)
            updateStats { copy(messagesSent = messagesSent + 1) }

            Log.d(TAG, "Sent CoT to $peerAddress:$port")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to send CoT to $peerAddress", e)
            updateStats { copy(sendErrors = sendErrors + 1) }
        }
    }

    /**
     * Send raw CoT XML to ATAK multicast
     */
    suspend fun sendRawXml(xml: String) = withContext(ioDispatcher) {
        try {
            val data = xml.toByteArray(Charsets.UTF_8)
            sendMulticast(data, ATAK_MULTICAST_ADDRESS, ATAK_MULTICAST_PORT)
            updateStats { copy(messagesSent = messagesSent + 1) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send raw XML", e)
            updateStats { copy(sendErrors = sendErrors + 1) }
        }
    }

    /**
     * Update broadcast interval based on movement
     */
    fun setBroadcastInterval(intervalMs: Long) {
        broadcastInterval = intervalMs.coerceIn(
            FAST_BROADCAST_INTERVAL_MS,
            SLOW_BROADCAST_INTERVAL_MS
        )
        Log.d(TAG, "Broadcast interval set to ${broadcastInterval}ms")
    }

    /**
     * Update callsign
     */
    fun setCallsign(newCallsign: String) {
        callsign = newCallsign
    }

    /**
     * Update group
     */
    fun setGroup(name: String, role: String = CoTDetail.ROLE_TEAM_MEMBER) {
        groupName = name
        groupRole = role
    }

    /**
     * Convert TrackedLocation to CoTMessage
     */
    fun trackedLocationToCoT(
        location: TrackedLocation,
        peerDeviceId: String,
        peerCallsign: String,
        peerGroupName: String? = null
    ): CoTMessage {
        return cotMessage {
            uid(peerDeviceId, CoTMessage.UID_PREFIX_MESH_RIDER)
            type(CoTMessage.TYPE_FRIENDLY_GROUND_UNIT)
            location(
                lat = location.latitude,
                lon = location.longitude,
                alt = location.altitude.toDouble(),
                accuracy = location.accuracy.toDouble()
            )
            callsign(peerCallsign)
            peerGroupName?.let { group(it, CoTDetail.ROLE_TEAM_MEMBER) }
            if (location.speed > 0) {
                movement(location.bearing.toDouble(), location.speed.toDouble())
            }
        }
    }

    /**
     * Convert CoTMessage to TrackedLocation
     */
    fun cotToTrackedLocation(message: CoTMessage): TrackedLocation {
        return TrackedLocation(
            latitude = message.point.latitude,
            longitude = message.point.longitude,
            altitude = message.point.hae.toFloat(),
            accuracy = message.point.ce.toFloat().coerceAtMost(9999f),
            speed = message.detail?.speed?.toFloat() ?: 0f,
            bearing = message.detail?.course?.toFloat() ?: 0f,
            timestamp = message.time,
            provider = if (message.how == CoTMessage.HOW_MACHINE_GPS) "gps" else "network",
            memberName = message.detail?.callsign
        )
    }

    /**
     * Get list of online ATAK devices (non-stale)
     */
    fun getOnlineAtakDevices(): List<CoTMessage> {
        return seenDevices.values.filter { !it.isStale() }
    }

    /**
     * Clear seen devices
     */
    fun clearSeenDevices() {
        seenDevices.clear()
        _atakDevices.value = emptyMap()
    }

    // ========== Internal Methods ==========

    private suspend fun initializeSockets() = withContext(ioDispatcher) {
        mutex.withLock {
            try {
                // Create multicast socket
                multicastSocket = MulticastSocket(ATAK_MULTICAST_PORT).apply {
                    reuseAddress = true
                    soTimeout = RECEIVE_TIMEOUT_MS
                    timeToLive = MULTICAST_TTL
                }

                // Join multicast groups on all mesh interfaces
                val meshInterface = findMeshInterface()
                val atakGroup = InetAddress.getByName(ATAK_MULTICAST_ADDRESS)
                val meshGroup = InetAddress.getByName(MESH_MULTICAST_ADDRESS)

                if (meshInterface != null) {
                    multicastSocket?.networkInterface = meshInterface
                    try {
                        multicastSocket?.joinGroup(
                            InetSocketAddress(atakGroup, ATAK_MULTICAST_PORT),
                            meshInterface
                        )
                        multicastSocket?.joinGroup(
                            InetSocketAddress(meshGroup, MESH_MULTICAST_PORT),
                            meshInterface
                        )
                        Log.i(TAG, "Joined multicast groups on ${meshInterface.name}")
                    } catch (e: Exception) {
                        // Try legacy join
                        @Suppress("DEPRECATION")
                        multicastSocket?.joinGroup(atakGroup)
                        @Suppress("DEPRECATION")
                        multicastSocket?.joinGroup(meshGroup)
                        Log.i(TAG, "Joined multicast groups (legacy mode)")
                    }
                } else {
                    // Join on all interfaces
                    @Suppress("DEPRECATION")
                    multicastSocket?.joinGroup(atakGroup)
                    @Suppress("DEPRECATION")
                    multicastSocket?.joinGroup(meshGroup)
                    Log.i(TAG, "Joined multicast groups on default interface")
                }

                // Create unicast socket for peer-to-peer
                unicastSocket = DatagramSocket()

                Log.i(TAG, "Sockets initialized")

            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize sockets", e)
                throw e
            }
        }
    }

    private fun startReceiveLoop() {
        receiveJob = scope.launch {
            val buffer = ByteArray(RECEIVE_BUFFER_SIZE)

            while (isActive && _state.value == CoTState.Starting || _state.value == CoTState.Running) {
                try {
                    val socket = multicastSocket ?: break

                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)

                    val data = packet.data.copyOfRange(0, packet.length)
                    val xml = String(data, Charsets.UTF_8)

                    processReceivedCoT(xml, packet.address.hostAddress ?: "unknown")

                } catch (e: SocketTimeoutException) {
                    // Normal timeout, continue loop
                    continue
                } catch (e: SocketException) {
                    if (isActive) {
                        Log.w(TAG, "Socket error in receive loop", e)
                        // Try to reconnect
                        delay(RECONNECT_DELAY_MS)
                        try {
                            initializeSockets()
                        } catch (e2: Exception) {
                            Log.e(TAG, "Failed to reconnect", e2)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error receiving CoT", e)
                    updateStats { copy(receiveErrors = receiveErrors + 1) }
                }
            }
        }
    }

    private suspend fun processReceivedCoT(xml: String, sourceAddress: String) {
        try {
            val message = CoTMessage.fromXml(xml)

            if (message == null) {
                Log.w(TAG, "Failed to parse CoT from $sourceAddress")
                updateStats { copy(parseErrors = parseErrors + 1) }
                return
            }

            // Ignore our own messages
            if (message.uid.contains(deviceId)) {
                return
            }

            // Update seen devices
            seenDevices[message.uid] = message
            _atakDevices.value = seenDevices.toMap()

            // Emit to listeners
            _receivedMessages.emit(message)

            // Update location sharing if this is a Mesh Rider device
            if (message.uid.startsWith(CoTMessage.UID_PREFIX_MESH_RIDER)) {
                val location = cotToTrackedLocation(message)
                // Extract device ID from UID (MRWave-ABC12345 -> ABC12345)
                val peerDeviceId = message.uid.removePrefix("${CoTMessage.UID_PREFIX_MESH_RIDER}-")
                val peerCallsign = message.detail?.callsign ?: peerDeviceId

                locationSharingManager.updateTeamLocation(
                    publicKey = peerDeviceId.toByteArray(), // Simplified - real implementation needs proper key
                    name = peerCallsign,
                    location = location
                )
            }

            updateStats {
                copy(
                    messagesReceived = messagesReceived + 1,
                    uniqueDevicesSeen = seenDevices.size
                )
            }

            Log.d(TAG, "Received CoT from ${message.detail?.callsign ?: message.uid} @ $sourceAddress")

        } catch (e: Exception) {
            Log.e(TAG, "Error processing CoT", e)
            updateStats { copy(parseErrors = parseErrors + 1) }
        }
    }

    private fun startAutoBroadcast() {
        broadcastJob = scope.launch {
            while (isActive && _state.value == CoTState.Running) {
                try {
                    // Get current location
                    locationSharingManager.myLocation.value?.let { location ->
                        broadcastLocation(location)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in auto-broadcast", e)
                }

                delay(broadcastInterval)
            }
        }
    }

    private fun startNetworkMonitoring() {
        connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(TAG, "Network available")
                // Rejoin multicast groups
                scope.launch {
                    try {
                        initializeSockets()
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to reinitialize on network change", e)
                    }
                }
            }

            override fun onLost(network: Network) {
                Log.d(TAG, "Network lost")
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
            .build()

        try {
            connectivityManager?.registerNetworkCallback(request, networkCallback!!)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register network callback", e)
        }
    }

    private fun createCoTMessage(location: TrackedLocation, isSos: Boolean): CoTMessage {
        val now = System.currentTimeMillis()

        val type = if (isSos) {
            CoTMessage.TYPE_EMERGENCY_SOS
        } else {
            CoTMessage.TYPE_FRIENDLY_GROUND_UNIT
        }

        val uid = if (isSos) {
            CoTMessage.generateSosUid(deviceId)
        } else {
            CoTMessage.generateUid(deviceId)
        }

        val staleDuration = if (isSos) {
            CoTMessage.SOS_STALE_DURATION_MS
        } else {
            CoTMessage.DEFAULT_STALE_DURATION_MS
        }

        return CoTMessage(
            uid = uid,
            type = type,
            time = now,
            start = now,
            stale = now + staleDuration,
            how = if (location.provider == "gps") {
                CoTMessage.HOW_MACHINE_GPS
            } else {
                CoTMessage.HOW_MACHINE_FUSED
            },
            point = CoTPoint(
                latitude = location.latitude,
                longitude = location.longitude,
                hae = location.altitude.toDouble(),
                ce = location.accuracy.toDouble().coerceAtLeast(1.0),
                le = location.accuracy.toDouble().coerceAtLeast(1.0)
            ),
            detail = CoTDetail(
                callsign = callsign,
                groupName = groupName,
                groupRole = groupRole,
                course = if (location.speed > 0) location.bearing.toDouble() else null,
                speed = if (location.speed > 0) location.speed.toDouble() else null,
                battery = getBatteryLevel(),
                remarks = if (isSos) "EMERGENCY SOS" else null
            )
        )
    }

    private suspend fun sendMulticast(
        data: ByteArray,
        address: String,
        port: Int
    ) = withContext(ioDispatcher) {
        try {
            val socket = multicastSocket ?: return@withContext
            val group = InetAddress.getByName(address)
            val packet = DatagramPacket(data, data.size, group, port)
            socket.send(packet)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send multicast to $address:$port", e)
            throw e
        }
    }

    private fun findMeshInterface(): NetworkInterface? {
        return try {
            Collections.list(NetworkInterface.getNetworkInterfaces())
                .filter { it.isUp && !it.isLoopback && it.supportsMulticast() }
                .firstOrNull { iface ->
                    MESH_INTERFACES.any { prefix -> iface.name.startsWith(prefix) }
                }
        } catch (e: Exception) {
            Log.w(TAG, "Error finding mesh interface", e)
            null
        }
    }

    private fun getBatteryLevel(): Int {
        return try {
            val batteryIntent = context.registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )
            val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            if (level >= 0 && scale > 0) {
                (level * 100) / scale
            } else {
                -1
            }
        } catch (e: Exception) {
            -1
        }
    }

    private suspend fun updateStats(update: CoTStats.() -> CoTStats) {
        _stats.value = _stats.value.update()
    }

    private suspend fun cleanup() {
        mutex.withLock {
            // Cancel jobs
            receiveJob?.cancel()
            broadcastJob?.cancel()
            networkMonitorJob?.cancel()

            receiveJob = null
            broadcastJob = null
            networkMonitorJob = null

            // Unregister network callback
            networkCallback?.let { callback ->
                try {
                    connectivityManager?.unregisterNetworkCallback(callback)
                } catch (e: Exception) {
                    Log.w(TAG, "Error unregistering network callback", e)
                }
            }
            networkCallback = null

            // Close sockets
            try {
                multicastSocket?.close()
            } catch (e: Exception) {
                Log.w(TAG, "Error closing multicast socket", e)
            }
            multicastSocket = null

            try {
                unicastSocket?.close()
            } catch (e: Exception) {
                Log.w(TAG, "Error closing unicast socket", e)
            }
            unicastSocket = null

            // Clear state
            seenDevices.clear()
            _atakDevices.value = emptyMap()
        }
    }
}

/**
 * CoT Manager state
 */
sealed class CoTState {
    data object Stopped : CoTState()
    data object Starting : CoTState()
    data object Running : CoTState()
    data class Error(val message: String) : CoTState()
}

/**
 * CoT statistics
 */
data class CoTStats(
    val messagesSent: Long = 0,
    val messagesReceived: Long = 0,
    val sendErrors: Long = 0,
    val receiveErrors: Long = 0,
    val parseErrors: Long = 0,
    val uniqueDevicesSeen: Int = 0,
    val startTime: Long = System.currentTimeMillis()
) {
    val uptimeMs: Long
        get() = System.currentTimeMillis() - startTime

    val sendSuccessRate: Double
        get() = if (messagesSent > 0) {
            ((messagesSent - sendErrors).toDouble() / messagesSent) * 100
        } else 100.0

    val receiveSuccessRate: Double
        get() = if (messagesReceived > 0) {
            ((messagesReceived - parseErrors).toDouble() / messagesReceived) * 100
        } else 100.0
}

/**
 * Extension to convert peer public key to device ID for CoT
 */
fun ByteArray.toCoTDeviceId(): String {
    return this.take(8).joinToString("") { "%02X".format(it) }
}
