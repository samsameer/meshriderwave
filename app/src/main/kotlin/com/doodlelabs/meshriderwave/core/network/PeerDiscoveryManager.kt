/*
 * Mesh Rider Wave - Zero-Config Peer Discovery Manager
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 *
 * mDNS/DNS-SD based peer discovery for local mesh networks
 * Uses Android NSD (Network Service Discovery) API
 *
 * Features:
 * - Zero-configuration peer discovery
 * - Automatic service registration
 * - Real-time peer availability updates
 * - Multi-interface support (WiFi, Mesh)
 */

package com.doodlelabs.meshriderwave.core.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import com.doodlelabs.meshriderwave.core.crypto.CryptoManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import com.doodlelabs.meshriderwave.core.di.IoDispatcher
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages peer discovery on local mesh networks using mDNS/DNS-SD
 *
 * Service Type: _meshrider._tcp
 * TXT Records:
 * - pk: Base64-encoded public key (identity)
 * - name: Device/user name
 * - ver: App version
 * - cap: Capabilities (voice,video,ptt,group)
 */
@Singleton
class PeerDiscoveryManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cryptoManager: CryptoManager,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    companion object {
        private const val TAG = "MeshRider:Discovery"

        // mDNS Service Configuration
        const val SERVICE_TYPE = "_meshrider._tcp."
        const val SERVICE_NAME_PREFIX = "MeshRider_"

        // TXT Record Keys
        const val TXT_PUBLIC_KEY = "pk"
        const val TXT_NAME = "name"
        const val TXT_VERSION = "ver"
        const val TXT_CAPABILITIES = "cap"
        const val TXT_STATUS = "status"

        // Discovery timeout
        const val RESOLVE_TIMEOUT_MS = 10_000L

        // Mesh network interface prefixes
        val MESH_INTERFACES = listOf("wlan", "mesh", "br-mesh", "smartradio")
    }

    private val nsdManager: NsdManager by lazy {
        context.getSystemService(Context.NSD_SERVICE) as NsdManager
    }

    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)

    // Discovered peers
    private val _discoveredPeers = MutableStateFlow<Map<String, DiscoveredPeer>>(emptyMap())
    val discoveredPeers: StateFlow<Map<String, DiscoveredPeer>> = _discoveredPeers.asStateFlow()

    // Service registration state
    private val _registrationState = MutableStateFlow<RegistrationState>(RegistrationState.Unregistered)
    val registrationState: StateFlow<RegistrationState> = _registrationState.asStateFlow()

    // Discovery state
    private val _discoveryState = MutableStateFlow<DiscoveryState>(DiscoveryState.Stopped)
    val discoveryState: StateFlow<DiscoveryState> = _discoveryState.asStateFlow()

    // Internal tracking
    private val pendingResolves = ConcurrentHashMap<String, NsdServiceInfo>()
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var registeredServiceName: String? = null

    // ========== Service Registration ==========

    /**
     * Register this device as a Mesh Rider service on the network
     */
    fun registerService(
        publicKey: ByteArray,
        displayName: String,
        port: Int,
        capabilities: Set<Capability> = Capability.all()
    ) {
        if (_registrationState.value == RegistrationState.Registered) {
            Log.d(TAG, "Service already registered")
            return
        }

        _registrationState.value = RegistrationState.Registering

        val serviceInfo = NsdServiceInfo().apply {
            serviceName = SERVICE_NAME_PREFIX + publicKey.take(4).toByteArray().toHexString()
            serviceType = SERVICE_TYPE
            setPort(port)

            // Set TXT records
            setAttribute(TXT_PUBLIC_KEY, android.util.Base64.encodeToString(publicKey, android.util.Base64.NO_WRAP))
            setAttribute(TXT_NAME, displayName.take(32))
            setAttribute(TXT_VERSION, getAppVersion())
            setAttribute(TXT_CAPABILITIES, capabilities.joinToString(",") { it.code })
            setAttribute(TXT_STATUS, "online")
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {
                registeredServiceName = info.serviceName
                _registrationState.value = RegistrationState.Registered
                Log.i(TAG, "Service registered: ${info.serviceName}")
            }

            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                _registrationState.value = RegistrationState.Failed(
                    "Registration failed: ${errorCodeToString(errorCode)}"
                )
                Log.e(TAG, "Service registration failed: $errorCode")
            }

            override fun onServiceUnregistered(info: NsdServiceInfo) {
                registeredServiceName = null
                _registrationState.value = RegistrationState.Unregistered
                Log.i(TAG, "Service unregistered")
            }

            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "Service unregistration failed: $errorCode")
            }
        }

        try {
            nsdManager.registerService(
                serviceInfo,
                NsdManager.PROTOCOL_DNS_SD,
                registrationListener
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register service", e)
            _registrationState.value = RegistrationState.Failed(e.message ?: "Unknown error")
        }
    }

    /**
     * Unregister this device from the network
     */
    fun unregisterService() {
        val listener = registrationListener
        registrationListener = null // Clear reference immediately to prevent double-unregister

        if (listener != null) {
            try {
                nsdManager.unregisterService(listener)
            } catch (e: IllegalArgumentException) {
                // Listener was already unregistered or never registered
                Log.w(TAG, "Service listener already unregistered")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to unregister service", e)
            }
        }
        registeredServiceName = null
        _registrationState.value = RegistrationState.Unregistered
    }

    /**
     * Update service status (online, away, busy)
     */
    fun updateStatus(status: PeerStatus) {
        // NSD doesn't support updating TXT records easily
        // We'd need to unregister and re-register
        // For now, log and skip
        Log.d(TAG, "Status update requested: $status (requires re-registration)")
    }

    // ========== Peer Discovery ==========

    /**
     * Start discovering peers on the network
     */
    fun startDiscovery() {
        if (_discoveryState.value == DiscoveryState.Discovering) {
            Log.d(TAG, "Discovery already running")
            return
        }

        _discoveryState.value = DiscoveryState.Starting

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                _discoveryState.value = DiscoveryState.Discovering
                Log.i(TAG, "Discovery started for $serviceType")
            }

            override fun onDiscoveryStopped(serviceType: String) {
                _discoveryState.value = DiscoveryState.Stopped
                Log.i(TAG, "Discovery stopped")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Service found: ${serviceInfo.serviceName}")

                // Don't resolve our own service
                if (serviceInfo.serviceName == registeredServiceName) {
                    return
                }

                // Skip if already pending
                if (pendingResolves.containsKey(serviceInfo.serviceName)) {
                    return
                }

                pendingResolves[serviceInfo.serviceName] = serviceInfo
                resolveService(serviceInfo)
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Service lost: ${serviceInfo.serviceName}")
                pendingResolves.remove(serviceInfo.serviceName)

                // Find and remove peer by service name
                val currentPeers = _discoveredPeers.value.toMutableMap()
                val peerToRemove = currentPeers.entries.find { (_, peer) ->
                    peer.serviceName == serviceInfo.serviceName
                }
                peerToRemove?.let {
                    currentPeers.remove(it.key)
                    _discoveredPeers.value = currentPeers
                    Log.i(TAG, "Peer removed: ${it.value.name}")
                }
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                _discoveryState.value = DiscoveryState.Failed(
                    "Discovery start failed: ${errorCodeToString(errorCode)}"
                )
                Log.e(TAG, "Discovery start failed: $errorCode")
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Discovery stop failed: $errorCode")
            }
        }

        try {
            nsdManager.discoverServices(
                SERVICE_TYPE,
                NsdManager.PROTOCOL_DNS_SD,
                discoveryListener
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start discovery", e)
            _discoveryState.value = DiscoveryState.Failed(e.message ?: "Unknown error")
        }
    }

    /**
     * Stop discovering peers
     */
    fun stopDiscovery() {
        val listener = discoveryListener
        discoveryListener = null // Clear reference immediately to prevent double-stop

        if (listener != null) {
            try {
                nsdManager.stopServiceDiscovery(listener)
            } catch (e: IllegalArgumentException) {
                // Listener was already stopped or never started
                Log.w(TAG, "Discovery listener already stopped")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop discovery", e)
            }
        }
        pendingResolves.clear()
        _discoveryState.value = DiscoveryState.Stopped
    }

    /**
     * Get peers as a Flow (for reactive updates)
     */
    fun observePeers(): Flow<List<DiscoveredPeer>> = callbackFlow {
        val job = scope.launch {
            _discoveredPeers.collect { peers ->
                trySend(peers.values.toList().sortedByDescending { it.lastSeenAt })
            }
        }

        awaitClose {
            job.cancel()
        }
    }

    /**
     * Find peer by public key
     */
    fun findPeerByPublicKey(publicKey: ByteArray): DiscoveredPeer? {
        val hexKey = publicKey.toHexString()
        return _discoveredPeers.value[hexKey]
    }

    /**
     * Get all reachable addresses for a peer
     */
    fun getPeerAddresses(publicKey: ByteArray): List<String> {
        return findPeerByPublicKey(publicKey)?.addresses ?: emptyList()
    }

    // ========== Internal Methods ==========

    private fun resolveService(serviceInfo: NsdServiceInfo) {
        val resolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                pendingResolves.remove(info.serviceName)
                Log.w(TAG, "Resolve failed for ${info.serviceName}: ${errorCodeToString(errorCode)}")
            }

            override fun onServiceResolved(info: NsdServiceInfo) {
                pendingResolves.remove(info.serviceName)
                handleResolvedService(info)
            }
        }

        try {
            nsdManager.resolveService(serviceInfo, resolveListener)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resolve service", e)
            pendingResolves.remove(serviceInfo.serviceName)
        }
    }

    private fun handleResolvedService(info: NsdServiceInfo) {
        try {
            // Extract TXT records
            val attributes = info.attributes
            val publicKeyB64 = attributes[TXT_PUBLIC_KEY]?.toString(Charsets.UTF_8)
            val name = attributes[TXT_NAME]?.toString(Charsets.UTF_8) ?: "Unknown"
            val version = attributes[TXT_VERSION]?.toString(Charsets.UTF_8) ?: ""
            val capStr = attributes[TXT_CAPABILITIES]?.toString(Charsets.UTF_8) ?: ""
            val statusStr = attributes[TXT_STATUS]?.toString(Charsets.UTF_8) ?: "online"

            if (publicKeyB64 == null) {
                Log.w(TAG, "No public key in service: ${info.serviceName}")
                return
            }

            val publicKey = android.util.Base64.decode(publicKeyB64, android.util.Base64.NO_WRAP)
            val hexKey = publicKey.toHexString()

            // Parse capabilities
            val capabilities = capStr.split(",")
                .mapNotNull { Capability.fromCode(it.trim()) }
                .toSet()

            // Parse status
            val status = PeerStatus.fromString(statusStr)

            // Build address list
            val addresses = mutableListOf<String>()
            info.host?.let { host ->
                val port = info.port
                when (host) {
                    is Inet6Address -> {
                        // Include scope ID for link-local
                        val scopeId = if (host.isLinkLocalAddress) {
                            val iface = findMeshInterface()
                            if (iface != null) "%$iface" else ""
                        } else ""
                        addresses.add("[${host.hostAddress}$scopeId]:$port")
                    }
                    is Inet4Address -> {
                        addresses.add("${host.hostAddress}:$port")
                    }
                    else -> {
                        addresses.add("${host.hostAddress}:$port")
                    }
                }
            }

            val peer = DiscoveredPeer(
                publicKey = publicKey,
                name = name,
                addresses = addresses,
                capabilities = capabilities,
                status = status,
                version = version,
                serviceName = info.serviceName,
                lastSeenAt = System.currentTimeMillis()
            )

            // Update peers map
            val currentPeers = _discoveredPeers.value.toMutableMap()
            currentPeers[hexKey] = peer
            _discoveredPeers.value = currentPeers

            Log.i(TAG, "Peer discovered: $name (${addresses.firstOrNull() ?: "no address"})")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to handle resolved service", e)
        }
    }

    private fun findMeshInterface(): String? {
        return try {
            Collections.list(NetworkInterface.getNetworkInterfaces())
                .filter { it.isUp && !it.isLoopback }
                .firstOrNull { iface ->
                    MESH_INTERFACES.any { prefix -> iface.name.startsWith(prefix) }
                }?.name
        } catch (e: Exception) {
            null
        }
    }

    private fun getAppVersion(): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }

    private fun errorCodeToString(code: Int): String = when (code) {
        NsdManager.FAILURE_ALREADY_ACTIVE -> "Already active"
        NsdManager.FAILURE_INTERNAL_ERROR -> "Internal error"
        NsdManager.FAILURE_MAX_LIMIT -> "Max limit reached"
        else -> "Unknown ($code)"
    }

    private fun ByteArray.toHexString(): String =
        joinToString("") { "%02x".format(it) }

    // ========== Lifecycle ==========

    /**
     * Start both registration and discovery
     */
    fun start(publicKey: ByteArray, displayName: String, port: Int) {
        registerService(publicKey, displayName, port)
        startDiscovery()
    }

    /**
     * Stop all discovery and unregister
     */
    fun stop() {
        stopDiscovery()
        unregisterService()
        _discoveredPeers.value = emptyMap()
    }

    /**
     * Clear discovered peers
     */
    fun clearPeers() {
        _discoveredPeers.value = emptyMap()
    }
}

// ========== Data Classes ==========

/**
 * Represents a discovered peer on the network
 */
data class DiscoveredPeer(
    val publicKey: ByteArray,
    val name: String,
    val addresses: List<String>,
    val capabilities: Set<Capability>,
    val status: PeerStatus,
    val version: String,
    val serviceName: String,
    val lastSeenAt: Long
) {
    val shortId: String
        get() = publicKey.take(4).joinToString("") { "%02X".format(it) }

    val primaryAddress: String?
        get() = addresses.firstOrNull()

    val isOnline: Boolean
        get() = status == PeerStatus.ONLINE

    val supportsVideo: Boolean
        get() = capabilities.contains(Capability.VIDEO)

    val supportsPTT: Boolean
        get() = capabilities.contains(Capability.PTT)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DiscoveredPeer) return false
        return publicKey.contentEquals(other.publicKey)
    }

    override fun hashCode(): Int = publicKey.contentHashCode()
}

/**
 * Peer capabilities
 */
enum class Capability(val code: String) {
    VOICE("voice"),
    VIDEO("video"),
    PTT("ptt"),
    GROUP("group"),
    LOCATION("loc"),
    SOS("sos");

    companion object {
        fun fromCode(code: String): Capability? =
            entries.find { it.code == code }

        fun all(): Set<Capability> = entries.toSet()

        fun basic(): Set<Capability> = setOf(VOICE, PTT)
    }
}

/**
 * Peer online status
 */
enum class PeerStatus(val code: String) {
    ONLINE("online"),
    AWAY("away"),
    BUSY("busy"),
    OFFLINE("offline");

    companion object {
        fun fromString(str: String): PeerStatus =
            entries.find { it.code == str.lowercase() } ?: OFFLINE
    }
}

/**
 * Service registration state
 */
sealed class RegistrationState {
    data object Unregistered : RegistrationState()
    data object Registering : RegistrationState()
    data object Registered : RegistrationState()
    data class Failed(val message: String) : RegistrationState()
}

/**
 * Discovery state
 */
sealed class DiscoveryState {
    data object Stopped : DiscoveryState()
    data object Starting : DiscoveryState()
    data object Discovering : DiscoveryState()
    data class Failed(val message: String) : DiscoveryState()
}
