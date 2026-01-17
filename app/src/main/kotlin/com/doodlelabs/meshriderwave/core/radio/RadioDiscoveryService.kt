/*
 * Mesh Rider Wave - Radio Discovery Service
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 *
 * UDP-based discovery service for finding MeshRider radios on the network.
 *
 * Protocol:
 * - Broadcast "Hello" packet to 10.223.255.255:11111
 * - Radios respond with JSON status including IP, hostname, version
 * - Service collects responses and builds list of available radios
 *
 * Features:
 * - Automatic discovery on startup
 * - Periodic refresh (configurable interval)
 * - Manual refresh trigger
 * - Flow-based radio list updates
 */

package com.doodlelabs.meshriderwave.core.radio

import com.doodlelabs.meshriderwave.core.util.logD
import com.doodlelabs.meshriderwave.core.util.logE
import com.doodlelabs.meshriderwave.core.util.logI
import com.doodlelabs.meshriderwave.core.util.logW
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONObject
import java.net.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for discovering MeshRider radios on the local network.
 *
 * Uses UDP broadcast to find radios and maintains a list of discovered devices.
 *
 * Usage:
 * ```kotlin
 * val discoveryService = RadioDiscoveryService()
 * discoveryService.start()
 *
 * // Observe discovered radios
 * discoveryService.discoveredRadios.collect { radios ->
 *     updateRadioList(radios)
 * }
 *
 * // Manual refresh
 * discoveryService.refresh()
 *
 * // Cleanup
 * discoveryService.stop()
 * ```
 */
@Singleton
class RadioDiscoveryService @Inject constructor() {

    companion object {
        // Discovery protocol
        const val DISCOVERY_PORT = 11111
        const val BROADCAST_ADDRESS = "10.223.255.255"
        const val HELLO_MESSAGE = "Hello"

        // Alternative broadcast addresses for different subnets
        val BROADCAST_ADDRESSES = listOf(
            "10.223.255.255",   // Default MeshRider subnet
            "192.168.20.255",   // Alternative subnet
            "192.168.1.255",    // Common home network
            "255.255.255.255"   // Global broadcast
        )

        // Timing
        const val RECEIVE_TIMEOUT_MS = 3000
        const val DISCOVERY_INTERVAL_MS = 30000L  // 30 seconds
        const val STALE_THRESHOLD_MS = 120000L    // 2 minutes
    }

    // Coroutine scope
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Discovery socket
    private var socket: DatagramSocket? = null

    // State
    private var isRunning = false
    private var discoveryJob: Job? = null

    // Discovered radios
    private val _discoveredRadios = MutableStateFlow<List<DiscoveredRadio>>(emptyList())
    val discoveredRadios: StateFlow<List<DiscoveredRadio>> = _discoveredRadios.asStateFlow()

    // Last discovery event
    private val _lastDiscoveryEvent = MutableSharedFlow<DiscoveryEvent>()
    val lastDiscoveryEvent: SharedFlow<DiscoveryEvent> = _lastDiscoveryEvent.asSharedFlow()

    /**
     * Discovered radio information
     */
    data class DiscoveredRadio(
        val ipAddress: String,
        val hostname: String,
        val model: String,
        val firmwareVersion: String,
        val macAddress: String,
        val signalStrength: Int?,
        val discoveredAt: Long,
        val lastSeenAt: Long
    ) {
        val isStale: Boolean
            get() = System.currentTimeMillis() - lastSeenAt > STALE_THRESHOLD_MS

        val displayName: String
            get() = if (hostname.isNotEmpty()) hostname else ipAddress
    }

    /**
     * Discovery events
     */
    sealed class DiscoveryEvent {
        object Started : DiscoveryEvent()
        object Completed : DiscoveryEvent()
        data class RadioFound(val radio: DiscoveredRadio) : DiscoveryEvent()
        data class RadioLost(val ipAddress: String) : DiscoveryEvent()
        data class Error(val message: String) : DiscoveryEvent()
    }

    /**
     * Start the discovery service.
     *
     * @param autoRefresh Enable automatic periodic refresh
     * @param refreshInterval Interval between refreshes in milliseconds
     */
    fun start(autoRefresh: Boolean = true, refreshInterval: Long = DISCOVERY_INTERVAL_MS) {
        if (isRunning) {
            logW("Discovery service already running")
            return
        }

        try {
            socket = DatagramSocket().apply {
                broadcast = true
                soTimeout = RECEIVE_TIMEOUT_MS
                reuseAddress = true
            }

            isRunning = true
            logI("Radio discovery service started")

            // Initial discovery
            scope.launch {
                refresh()
            }

            // Periodic refresh
            if (autoRefresh) {
                discoveryJob = scope.launch {
                    while (isActive) {
                        delay(refreshInterval)
                        refresh()
                    }
                }
            }
        } catch (e: Exception) {
            logE("Failed to start discovery service: ${e.message}")
            scope.launch {
                _lastDiscoveryEvent.emit(DiscoveryEvent.Error("Failed to start: ${e.message}"))
            }
        }
    }

    /**
     * Stop the discovery service.
     */
    fun stop() {
        isRunning = false
        discoveryJob?.cancel()
        discoveryJob = null

        try {
            socket?.close()
        } catch (e: Exception) {
            logW("Error closing discovery socket: ${e.message}")
        }
        socket = null

        logI("Radio discovery service stopped")
    }

    /**
     * Trigger a manual discovery refresh.
     */
    suspend fun refresh() {
        if (!isRunning) {
            logW("Discovery service not running")
            return
        }

        _lastDiscoveryEvent.emit(DiscoveryEvent.Started)
        logD("Starting discovery scan...")

        val startTime = System.currentTimeMillis()
        val foundRadios = mutableMapOf<String, DiscoveredRadio>()

        try {
            // Send discovery packet to all broadcast addresses
            for (broadcastAddr in BROADCAST_ADDRESSES) {
                sendDiscoveryPacket(broadcastAddr)
            }

            // Collect responses
            val responses = receiveResponses()

            // Process responses
            for ((sourceIp, responseJson) in responses) {
                try {
                    val radio = parseDiscoveryResponse(sourceIp, responseJson)
                    if (radio != null) {
                        foundRadios[radio.ipAddress] = radio
                        _lastDiscoveryEvent.emit(DiscoveryEvent.RadioFound(radio))
                        logD("Discovered radio: ${radio.displayName} at ${radio.ipAddress}")
                    }
                } catch (e: Exception) {
                    logW("Failed to parse response from $sourceIp: ${e.message}")
                }
            }

            // Update radio list, preserving existing radios that weren't refreshed
            val existingRadios = _discoveredRadios.value.associateBy { it.ipAddress }
            val updatedList = mutableListOf<DiscoveredRadio>()

            // Add newly found radios
            foundRadios.values.forEach { updatedList.add(it) }

            // Keep existing radios that weren't re-discovered but aren't stale
            existingRadios.values.forEach { existing ->
                if (!foundRadios.containsKey(existing.ipAddress) && !existing.isStale) {
                    updatedList.add(existing)
                } else if (existing.isStale && foundRadios.containsKey(existing.ipAddress).not()) {
                    _lastDiscoveryEvent.emit(DiscoveryEvent.RadioLost(existing.ipAddress))
                }
            }

            _discoveredRadios.value = updatedList.sortedBy { it.ipAddress }

            val duration = System.currentTimeMillis() - startTime
            logI("Discovery complete: ${foundRadios.size} radios found in ${duration}ms")

            _lastDiscoveryEvent.emit(DiscoveryEvent.Completed)
        } catch (e: Exception) {
            logE("Discovery failed: ${e.message}")
            _lastDiscoveryEvent.emit(DiscoveryEvent.Error("Discovery failed: ${e.message}"))
        }
    }

    /**
     * Send discovery broadcast packet.
     */
    private fun sendDiscoveryPacket(broadcastAddress: String) {
        try {
            val address = InetAddress.getByName(broadcastAddress)
            val data = HELLO_MESSAGE.toByteArray()
            val packet = DatagramPacket(data, data.size, address, DISCOVERY_PORT)

            socket?.send(packet)
            logD("Sent discovery packet to $broadcastAddress:$DISCOVERY_PORT")
        } catch (e: Exception) {
            logW("Failed to send discovery to $broadcastAddress: ${e.message}")
        }
    }

    /**
     * Receive discovery responses.
     *
     * @return Map of source IP to response JSON
     */
    private fun receiveResponses(): Map<String, String> {
        val responses = mutableMapOf<String, String>()
        val buffer = ByteArray(2048)
        val endTime = System.currentTimeMillis() + RECEIVE_TIMEOUT_MS

        while (System.currentTimeMillis() < endTime) {
            try {
                val packet = DatagramPacket(buffer, buffer.size)
                socket?.receive(packet)

                val sourceIp = packet.address.hostAddress ?: continue
                val response = String(packet.data, 0, packet.length)

                // Skip our own "Hello" echo
                if (response.trim() == HELLO_MESSAGE) continue

                responses[sourceIp] = response
            } catch (e: SocketTimeoutException) {
                // Normal timeout, continue to check if we have more time
                continue
            } catch (e: Exception) {
                logD("Error receiving response: ${e.message}")
                break
            }
        }

        return responses
    }

    /**
     * Parse discovery response JSON.
     */
    private fun parseDiscoveryResponse(sourceIp: String, response: String): DiscoveredRadio? {
        return try {
            val json = JSONObject(response)
            val now = System.currentTimeMillis()

            DiscoveredRadio(
                ipAddress = json.optString("ip", sourceIp),
                hostname = json.optString("hostname", ""),
                model = json.optString("model", "MeshRider"),
                firmwareVersion = json.optString("version", ""),
                macAddress = json.optString("mac", ""),
                signalStrength = json.optInt("signal").takeIf { it != 0 },
                discoveredAt = now,
                lastSeenAt = now
            )
        } catch (e: Exception) {
            // Response might not be JSON - could be a simple text response
            val now = System.currentTimeMillis()
            DiscoveredRadio(
                ipAddress = sourceIp,
                hostname = response.take(32).filter { it.isLetterOrDigit() || it == '-' },
                model = "MeshRider",
                firmwareVersion = "",
                macAddress = "",
                signalStrength = null,
                discoveredAt = now,
                lastSeenAt = now
            )
        }
    }

    /**
     * Manually add a radio by IP address.
     *
     * Useful for radios that don't respond to broadcast discovery.
     */
    fun addManualRadio(ipAddress: String, hostname: String = "") {
        val now = System.currentTimeMillis()
        val radio = DiscoveredRadio(
            ipAddress = ipAddress,
            hostname = hostname.ifEmpty { ipAddress },
            model = "MeshRider",
            firmwareVersion = "",
            macAddress = "",
            signalStrength = null,
            discoveredAt = now,
            lastSeenAt = now
        )

        val currentList = _discoveredRadios.value.toMutableList()
        val existingIndex = currentList.indexOfFirst { it.ipAddress == ipAddress }

        if (existingIndex >= 0) {
            currentList[existingIndex] = radio
        } else {
            currentList.add(radio)
        }

        _discoveredRadios.value = currentList.sortedBy { it.ipAddress }
        logI("Manually added radio: $ipAddress")
    }

    /**
     * Remove a radio from the discovered list.
     */
    fun removeRadio(ipAddress: String) {
        _discoveredRadios.value = _discoveredRadios.value.filter { it.ipAddress != ipAddress }
        logD("Removed radio: $ipAddress")
    }

    /**
     * Clear all discovered radios.
     */
    fun clearAll() {
        _discoveredRadios.value = emptyList()
        logD("Cleared all discovered radios")
    }

    /**
     * Get a specific radio by IP address.
     */
    fun getRadio(ipAddress: String): DiscoveredRadio? {
        return _discoveredRadios.value.find { it.ipAddress == ipAddress }
    }

    /**
     * Check if any radios have been discovered.
     */
    fun hasRadios(): Boolean = _discoveredRadios.value.isNotEmpty()

    /**
     * Release resources.
     */
    fun release() {
        stop()
        scope.cancel()
    }
}
