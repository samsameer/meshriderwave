/*
 * Mesh Rider Wave - Beacon Manager
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 *
 * Military-Grade Multicast Beacon Discovery (Jan 2026)
 *
 * This manager broadcasts and receives signed identity beacons
 * over UDP multicast. Unlike mDNS, multicast works across mesh
 * hops via BATMAN-adv layer-2 bridging.
 *
 * Architecture:
 * - Sender: Periodically broadcast signed beacon (30s interval)
 * - Receiver: Listen on multicast group, verify signatures
 * - Integration: Update ContactAddressSync with discovered peers
 *
 * Based on ATAK CoT multicast model (239.2.3.1:6969)
 */

package com.doodlelabs.meshriderwave.core.discovery

import android.content.Context
import android.util.Log
import com.doodlelabs.meshriderwave.core.crypto.CryptoManager
import com.doodlelabs.meshriderwave.core.network.NetworkTypeDetector
import com.doodlelabs.meshriderwave.data.local.SettingsDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager for multicast identity beacon discovery.
 *
 * Features:
 * - Broadcasts signed beacons every 30 seconds
 * - Listens for beacons on multicast group
 * - Verifies Ed25519 signatures
 * - Extracts sender IP from packet source
 * - Emits discovered peers via Flow
 * - Thread-safe with coroutines
 *
 * @see IdentityBeacon
 * @see ContactAddressSync
 */
@Singleton
class BeaconManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cryptoManager: CryptoManager,
    private val settingsDataStore: SettingsDataStore,
    private val networkTypeDetector: NetworkTypeDetector
) {
    companion object {
        private const val TAG = "BeaconManager"
        private const val RECEIVE_BUFFER_SIZE = 4096
        // BATTERY OPTIMIZATION Jan 2026: Longer socket timeout to reduce CPU wakeups
        // 30s timeout means fewer context switches vs 5s
        private const val SOCKET_TIMEOUT_MS = 30_000
        // Adaptive beacon intervals
        private const val BEACON_INTERVAL_ACTIVE_MS = 30_000L    // 30s when active
        private const val BEACON_INTERVAL_IDLE_MS = 120_000L     // 2min when no activity
        private const val PEER_CLEANUP_INTERVAL_MS = 300_000L    // 5min (was 60s)
    }

    // Coroutine scope for beacon operations
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Jobs for sender and receiver
    private var senderJob: Job? = null
    private var receiverJob: Job? = null

    // Multicast socket
    private var multicastSocket: MulticastSocket? = null
    private val socketLock = Any()

    // Discovered peers cache: publicKeyHex -> DiscoveredPeer
    private val discoveredPeers = ConcurrentHashMap<String, DiscoveredPeer>()

    // State flows
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _discoveredPeersFlow = MutableStateFlow<Map<String, DiscoveredPeer>>(emptyMap())
    val discoveredPeersFlow: StateFlow<Map<String, DiscoveredPeer>> = _discoveredPeersFlow.asStateFlow()

    // Event flow for new discoveries (for immediate reactions)
    private val _peerDiscoveredEvent = MutableSharedFlow<DiscoveredPeer>(extraBufferCapacity = 16)
    val peerDiscoveredEvent: SharedFlow<DiscoveredPeer> = _peerDiscoveredEvent.asSharedFlow()

    // Cleanup flag
    @Volatile
    private var isCleanedUp = false

    /**
     * Start beacon broadcasting and listening.
     */
    fun start() {
        if (_isRunning.value || isCleanedUp) {
            Log.d(TAG, "Already running or cleaned up, skipping start")
            return
        }

        Log.i(TAG, "Starting beacon manager")
        _isRunning.value = true

        // Start network type detector
        networkTypeDetector.start()

        // Initialize multicast socket
        initializeSocket()

        // Start sender
        senderJob = scope.launch {
            runBeaconSender()
        }

        // Start receiver
        receiverJob = scope.launch {
            runBeaconReceiver()
        }

        // Start peer cleanup
        scope.launch {
            runPeerCleanup()
        }
    }

    /**
     * Stop beacon broadcasting and listening.
     */
    fun stop() {
        if (!_isRunning.value) return

        Log.i(TAG, "Stopping beacon manager")
        _isRunning.value = false

        senderJob?.cancel()
        receiverJob?.cancel()
        senderJob = null
        receiverJob = null

        closeSocket()
        networkTypeDetector.stop()
    }

    /**
     * Clean up resources.
     */
    fun cleanup() {
        if (isCleanedUp) return
        isCleanedUp = true

        stop()
        discoveredPeers.clear()
        _discoveredPeersFlow.value = emptyMap()
    }

    /**
     * Force broadcast a beacon immediately.
     */
    fun broadcastNow() {
        scope.launch {
            try {
                sendBeacon()
            } catch (e: Exception) {
                Log.e(TAG, "Error broadcasting beacon", e)
            }
        }
    }

    /**
     * Get discovered peer by public key.
     */
    fun getPeerByPublicKey(publicKey: ByteArray): DiscoveredPeer? {
        val keyHex = publicKey.toHexString()
        return discoveredPeers[keyHex]
    }

    /**
     * Get all discovered peers.
     */
    fun getAllPeers(): List<DiscoveredPeer> {
        return discoveredPeers.values.toList()
    }

    // =========================================================================
    // PRIVATE METHODS
    // =========================================================================

    private fun initializeSocket() {
        synchronized(socketLock) {
            try {
                closeSocket()

                val socket = MulticastSocket(IdentityBeacon.MULTICAST_PORT)
                socket.reuseAddress = true
                socket.soTimeout = SOCKET_TIMEOUT_MS

                // Join multicast group on all mesh-capable interfaces
                val group = InetAddress.getByName(IdentityBeacon.MULTICAST_GROUP)
                val interfaces = NetworkInterface.getNetworkInterfaces()?.toList() ?: emptyList()

                for (iface in interfaces) {
                    if (shouldJoinInterface(iface)) {
                        try {
                            socket.joinGroup(InetSocketAddress(group, IdentityBeacon.MULTICAST_PORT), iface)
                            Log.d(TAG, "Joined multicast on interface: ${iface.name}")
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to join multicast on ${iface.name}: ${e.message}")
                        }
                    }
                }

                multicastSocket = socket
                Log.i(TAG, "Multicast socket initialized on port ${IdentityBeacon.MULTICAST_PORT}")

            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize multicast socket", e)
            }
        }
    }

    private fun closeSocket() {
        synchronized(socketLock) {
            multicastSocket?.let { socket ->
                try {
                    if (!socket.isClosed) {
                        socket.close()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error closing socket", e)
                }
            }
            multicastSocket = null
        }
    }

    private fun shouldJoinInterface(iface: NetworkInterface): Boolean {
        if (iface.isLoopback) return false
        if (!iface.isUp) return false
        if (!iface.supportsMulticast()) return false

        val name = iface.name.lowercase()
        val ignorePatterns = listOf("lo", "dummy", "rmnet", "ccmni", "clat", "tun", "tap")
        if (ignorePatterns.any { name.startsWith(it) }) return false

        return true
    }

    // BATTERY OPTIMIZATION Jan 2026: Track last peer activity
    @Volatile
    private var lastPeerActivityTime = System.currentTimeMillis()

    private suspend fun runBeaconSender() {
        Log.d(TAG, "Beacon sender started")

        while (_isRunning.value) {
            try {
                sendBeacon()
            } catch (e: Exception) {
                Log.e(TAG, "Error sending beacon", e)
            }

            // BATTERY OPTIMIZATION Jan 2026: Adaptive beacon interval
            // If no peer activity for 2 minutes, slow down to conserve battery
            val timeSinceActivity = System.currentTimeMillis() - lastPeerActivityTime
            val interval = if (timeSinceActivity < 120_000L || discoveredPeers.isNotEmpty()) {
                BEACON_INTERVAL_ACTIVE_MS  // 30s when active
            } else {
                BEACON_INTERVAL_IDLE_MS    // 2min when idle
            }
            delay(interval)
        }

        Log.d(TAG, "Beacon sender stopped")
    }

    private suspend fun sendBeacon() {
        val socket = multicastSocket ?: return

        // Get own identity
        val publicKey = settingsDataStore.publicKey.first()
        val secretKey = settingsDataStore.secretKey.first()
        val username = settingsDataStore.username.first()

        if (publicKey == null || secretKey == null) {
            Log.w(TAG, "No keys configured, skipping beacon")
            return
        }

        // Build beacon
        val beacon = IdentityBeacon.create(
            publicKey = publicKey,
            name = username,
            capabilities = getLocalCapabilities(),
            networkType = networkTypeDetector.currentNetworkType.value
        )

        // Sign beacon
        val signature = cryptoManager.sign(beacon.getSigningPayload(), secretKey)
        if (signature == null) {
            Log.w(TAG, "Failed to sign beacon")
            return
        }
        val signedBeacon = beacon.withSignature(signature)

        // Send to multicast group
        val data = signedBeacon.toBytes()
        val address = InetAddress.getByName(IdentityBeacon.MULTICAST_GROUP)
        val packet = DatagramPacket(data, data.size, address, IdentityBeacon.MULTICAST_PORT)

        withContext(Dispatchers.IO) {
            try {
                socket.send(packet)
                Log.d(TAG, "Sent beacon: $username (${publicKey.toHexString().take(16)})")
            } catch (e: SocketException) {
                if (_isRunning.value) {
                    Log.w(TAG, "Socket error sending beacon: ${e.message}")
                    initializeSocket()  // Reinitialize socket
                }
            }
        }
    }

    private suspend fun runBeaconReceiver() {
        Log.d(TAG, "Beacon receiver started")

        val buffer = ByteArray(RECEIVE_BUFFER_SIZE)

        while (_isRunning.value) {
            val socket = multicastSocket
            if (socket == null || socket.isClosed) {
                delay(1000)
                continue
            }

            try {
                val packet = DatagramPacket(buffer, buffer.size)

                withContext(Dispatchers.IO) {
                    socket.receive(packet)
                }

                // Process received beacon
                val data = packet.data.copyOf(packet.length)
                val senderAddress = packet.address.hostAddress ?: continue

                processReceivedBeacon(data, senderAddress)

            } catch (e: SocketTimeoutException) {
                // Expected, continue listening
            } catch (e: SocketException) {
                if (_isRunning.value) {
                    Log.w(TAG, "Socket error receiving: ${e.message}")
                    delay(1000)
                    initializeSocket()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error receiving beacon", e)
                delay(100)
            }
        }

        Log.d(TAG, "Beacon receiver stopped")
    }

    private suspend fun processReceivedBeacon(data: ByteArray, senderAddress: String) {
        // Parse beacon
        val beacon = IdentityBeacon.fromBytes(data)
        if (beacon == null) {
            Log.w(TAG, "Failed to parse beacon from $senderAddress")
            return
        }

        // Skip our own beacons
        val ownPublicKey = settingsDataStore.publicKey.first()
        if (ownPublicKey != null && beacon.getPublicKeyBytes().contentEquals(ownPublicKey)) {
            return
        }

        // Verify signature
        val verifyResult = verifyBeacon(beacon)
        when (verifyResult) {
            is BeaconVerifyResult.Valid -> {
                // BATTERY OPTIMIZATION Jan 2026: Update activity timestamp
                lastPeerActivityTime = System.currentTimeMillis()

                // Update discovered peers
                val peer = DiscoveredPeer(
                    publicKey = beacon.getPublicKeyBytes(),
                    name = beacon.name,
                    address = senderAddress,
                    networkType = beacon.networkType,
                    capabilities = beacon.capabilities,
                    lastSeenAt = System.currentTimeMillis(),
                    beaconTimestamp = beacon.timestamp
                )

                val keyHex = peer.publicKey.toHexString()
                discoveredPeers[keyHex] = peer
                _discoveredPeersFlow.value = discoveredPeers.toMap()

                // Emit discovery event
                _peerDiscoveredEvent.tryEmit(peer)

                Log.d(TAG, "Discovered peer: ${peer.name} at $senderAddress (${keyHex.take(8)})")
            }
            is BeaconVerifyResult.InvalidSignature -> {
                Log.w(TAG, "Invalid beacon signature from $senderAddress: ${verifyResult.reason}")
            }
            is BeaconVerifyResult.Expired -> {
                Log.d(TAG, "Expired beacon from $senderAddress (${verifyResult.ageMs}ms old)")
            }
            is BeaconVerifyResult.Malformed -> {
                Log.w(TAG, "Malformed beacon from $senderAddress: ${verifyResult.reason}")
            }
        }
    }

    private fun verifyBeacon(beacon: IdentityBeacon): BeaconVerifyResult {
        // Check expiration
        if (!beacon.isValid()) {
            return BeaconVerifyResult.Expired(beacon.ageMs)
        }

        // Check signature present
        if (!beacon.isSigned()) {
            return BeaconVerifyResult.InvalidSignature("No signature")
        }

        // Verify Ed25519 signature
        val payload = beacon.getSigningPayload()
        val signature = beacon.getSignatureBytes()
        val publicKey = beacon.getPublicKeyBytes()

        val isValid = cryptoManager.verify(payload, signature, publicKey)
        return if (isValid) {
            BeaconVerifyResult.Valid(beacon)
        } else {
            BeaconVerifyResult.InvalidSignature("Signature verification failed")
        }
    }

    private suspend fun runPeerCleanup() {
        while (_isRunning.value) {
            // BATTERY OPTIMIZATION Jan 2026: 5min interval instead of 60s
            delay(PEER_CLEANUP_INTERVAL_MS)

            val now = System.currentTimeMillis()
            val expiredKeys = discoveredPeers.entries
                .filter { (_, peer) -> now - peer.lastSeenAt > IdentityBeacon.MAX_BEACON_AGE_MS * 2 }
                .map { it.key }

            if (expiredKeys.isNotEmpty()) {
                expiredKeys.forEach { discoveredPeers.remove(it) }
                _discoveredPeersFlow.value = discoveredPeers.toMap()
                Log.d(TAG, "Cleaned up ${expiredKeys.size} stale peers")
            }
        }
    }

    private fun getLocalCapabilities(): Set<Capability> {
        // Return all supported capabilities
        // TODO: Make this configurable based on app state
        return setOf(
            Capability.VOICE,
            Capability.VIDEO,
            Capability.PTT,
            Capability.GROUP,
            Capability.LOCATION,
            Capability.SOS
        )
    }

    private fun ByteArray.toHexString(): String {
        return joinToString("") { "%02x".format(it) }
    }
}

/**
 * Discovered peer from beacon.
 */
data class DiscoveredPeer(
    val publicKey: ByteArray,
    val name: String,
    val address: String,
    val networkType: com.doodlelabs.meshriderwave.domain.model.NetworkType,
    val capabilities: Set<Capability>,
    val lastSeenAt: Long,
    val beaconTimestamp: Long
) {
    val publicKeyHex: String
        get() = publicKey.joinToString("") { "%02x".format(it) }

    val shortId: String
        get() = publicKeyHex.take(8).uppercase()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DiscoveredPeer) return false
        return publicKey.contentEquals(other.publicKey)
    }

    override fun hashCode(): Int {
        return publicKey.contentHashCode()
    }
}
