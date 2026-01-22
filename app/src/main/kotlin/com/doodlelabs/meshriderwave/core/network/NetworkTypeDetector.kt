/*
 * Mesh Rider Wave - Network Type Detector
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 *
 * Military-Grade Network Detection (Jan 2026)
 * Real-time detection of current network type for intelligent routing.
 *
 * Monitors network changes and classifies:
 * - MeshRider mesh (10.223.x.x via BATMAN-adv)
 * - WiFi Direct (192.168.49.x)
 * - Standard WiFi/Ethernet (192.168.x.x, 10.x.x.x)
 * - IPv6 Link-Local (fe80::%interface)
 */

package com.doodlelabs.meshriderwave.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import androidx.core.content.getSystemService
import com.doodlelabs.meshriderwave.domain.model.NetworkType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.NetworkInterface
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton service for detecting and monitoring current network type.
 *
 * Military-grade systems require instant awareness of network changes
 * to enable seamless handoff between WiFi and mesh networks.
 *
 * Features:
 * - Real-time network change detection via ConnectivityManager
 * - Classification of all local IP addresses by network type
 * - StateFlow for reactive UI/service updates
 * - Prioritized address list for connection attempts
 *
 * @see NetworkType
 */
@Singleton
class NetworkTypeDetector @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "NetworkTypeDetector"

        /** Mesh interface name patterns (MeshRider, OpenWRT) */
        private val MESH_INTERFACES = setOf("mesh", "bat0", "br-mesh", "wlan0", "eth0")

        /** Interfaces to ignore for address detection */
        private val IGNORE_INTERFACES = setOf("lo", "dummy", "rmnet", "ccmni", "clat", "tun", "tap")
    }

    // Safe null handling for ConnectivityManager
    private val connectivityManager: ConnectivityManager? =
        context.getSystemService()

    // Current primary network type
    private val _currentNetworkType = MutableStateFlow(NetworkType.UNKNOWN)
    val currentNetworkType: StateFlow<NetworkType> = _currentNetworkType.asStateFlow()

    // All detected local addresses by type
    private val _localAddressesByType = MutableStateFlow<Map<NetworkType, List<String>>>(emptyMap())
    val localAddressesByType: StateFlow<Map<NetworkType, List<String>>> = _localAddressesByType.asStateFlow()

    // All local addresses (flat list)
    private val _allLocalAddresses = MutableStateFlow<List<String>>(emptyList())
    val allLocalAddresses: StateFlow<List<String>> = _allLocalAddresses.asStateFlow()

    // Network callback for change detection
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    // Flag to track if started
    @Volatile
    private var isStarted = false

    /**
     * Start network monitoring.
     * Registers callback for network changes and performs initial detection.
     */
    fun start() {
        if (isStarted) {
            Log.d(TAG, "Already started, skipping")
            return
        }

        Log.i(TAG, "Starting network type detection")

        // Initial detection
        detectAndUpdate()

        // Register for network changes
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(TAG, "Network available: $network")
                detectAndUpdate()
            }

            override fun onLost(network: Network) {
                Log.d(TAG, "Network lost: $network")
                detectAndUpdate()
            }

            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                Log.d(TAG, "Link properties changed: ${linkProperties.interfaceName}")
                detectAndUpdate()
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                detectAndUpdate()
            }
        }

        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .build()

            connectivityManager?.registerNetworkCallback(request, callback)
            networkCallback = callback
            isStarted = true
            Log.i(TAG, "Network callback registered")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register network callback", e)
        }
    }

    /**
     * Stop network monitoring and release resources.
     */
    fun stop() {
        if (!isStarted) return

        Log.i(TAG, "Stopping network type detection")

        networkCallback?.let { callback ->
            try {
                connectivityManager?.unregisterNetworkCallback(callback)
            } catch (e: Exception) {
                Log.w(TAG, "Error unregistering network callback", e)
            }
        }
        networkCallback = null
        isStarted = false
    }

    /**
     * Force refresh of network detection.
     * Call this when you suspect network state may have changed.
     */
    fun refresh() {
        detectAndUpdate()
    }

    /**
     * Detect current network state and update flows.
     */
    private fun detectAndUpdate() {
        try {
            val addressesByType = mutableMapOf<NetworkType, MutableList<String>>()
            val allAddresses = mutableListOf<String>()

            // Enumerate all network interfaces
            val interfaces = NetworkInterface.getNetworkInterfaces()?.toList() ?: emptyList()

            for (iface in interfaces) {
                // Skip ignored interfaces
                if (shouldIgnoreInterface(iface.name)) continue
                if (!iface.isUp) continue

                for (ifAddr in iface.inetAddresses) {
                    if (ifAddr.isLoopbackAddress) continue

                    val address = formatAddress(ifAddr, iface.name)
                    val networkType = classifyAddress(address, iface.name)

                    // Add to type-specific list
                    addressesByType.getOrPut(networkType) { mutableListOf() }.add(address)

                    // Add to flat list
                    allAddresses.add(address)

                    Log.d(TAG, "Detected: $address (${networkType.displayName}) on ${iface.name}")
                }
            }

            // Update flows
            _localAddressesByType.value = addressesByType
            _allLocalAddresses.value = allAddresses

            // Determine primary network type (highest priority)
            val primaryType = determinePrimaryNetworkType(addressesByType.keys)
            _currentNetworkType.value = primaryType

            Log.i(TAG, "Network detection complete: $primaryType, ${allAddresses.size} addresses")

        } catch (e: Exception) {
            Log.e(TAG, "Error detecting network type", e)
        }
    }

    /**
     * Classify an address based on IP prefix and interface name.
     */
    private fun classifyAddress(address: String, interfaceName: String): NetworkType {
        // Interface-based classification (more reliable for mesh)
        if (interfaceName.startsWith("bat") || interfaceName == "br-mesh") {
            return NetworkType.MESH_RIDER
        }

        // IP-based classification
        return NetworkType.fromAddress(address)
    }

    /**
     * Format an InetAddress for storage.
     * Includes scope ID for IPv6 link-local addresses.
     */
    private fun formatAddress(addr: InetAddress, interfaceName: String): String {
        return when (addr) {
            is Inet6Address -> {
                if (addr.isLinkLocalAddress) {
                    // Include scope ID for link-local
                    "${addr.hostAddress?.substringBefore("%")}%$interfaceName"
                } else {
                    addr.hostAddress ?: addr.toString()
                }
            }
            is Inet4Address -> addr.hostAddress ?: addr.toString()
            else -> addr.hostAddress ?: addr.toString()
        }
    }

    /**
     * Determine primary network type from available types.
     * Returns highest priority (lowest priority value) type.
     */
    private fun determinePrimaryNetworkType(types: Set<NetworkType>): NetworkType {
        if (types.isEmpty()) return NetworkType.UNKNOWN

        return types.minByOrNull { it.priority } ?: NetworkType.UNKNOWN
    }

    /**
     * Check if an interface should be ignored.
     */
    private fun shouldIgnoreInterface(name: String): Boolean {
        return IGNORE_INTERFACES.any { name.startsWith(it) }
    }

    /**
     * Get local addresses for a specific network type.
     */
    fun getAddressesForType(type: NetworkType): List<String> {
        return _localAddressesByType.value[type] ?: emptyList()
    }

    /**
     * Get all mesh-capable addresses (mesh + wifi + ipv6-ll).
     * Prioritized by network type.
     */
    fun getMeshCapableAddresses(): List<String> {
        val result = mutableListOf<String>()

        // Add in priority order
        NetworkType.meshCapableTypes()
            .sortedBy { it.priority }
            .forEach { type ->
                result.addAll(getAddressesForType(type))
            }

        return result
    }

    /**
     * Check if currently on mesh network.
     */
    fun isOnMeshNetwork(): Boolean {
        return _currentNetworkType.value == NetworkType.MESH_RIDER
    }

    /**
     * Check if any network is available.
     */
    fun hasNetworkConnectivity(): Boolean {
        return _allLocalAddresses.value.isNotEmpty()
    }

    /**
     * Get the best local address for the current network.
     * Prefers mesh addresses when on mesh network.
     */
    fun getBestLocalAddress(): String? {
        val currentType = _currentNetworkType.value
        val addresses = getAddressesForType(currentType)

        if (addresses.isNotEmpty()) {
            // Prefer IPv4 over IPv6 for simplicity
            return addresses.firstOrNull { !it.contains(":") }
                ?: addresses.first()
        }

        // Fallback to any address
        return _allLocalAddresses.value.firstOrNull()
    }
}
