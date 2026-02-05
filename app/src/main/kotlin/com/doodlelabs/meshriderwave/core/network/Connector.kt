/*
 * Mesh Rider Wave - P2P Connector
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 *
 * Military-Grade Smart Address Resolution (Jan 2026)
 *
 * Based on:
 * - Meshenger's proven connection logic (IPv4, IPv6, EUI-64)
 * - Reticulum Network Stack identity-first addressing
 * - IETF HIP (Host Identity Protocol) RFC 7401
 * - LISP (Locator/ID Separation Protocol) RFC 6830
 *
 * Key Features:
 * - Network-type-aware address prioritization
 * - Reliability-based address ordering
 * - Automatic metrics collection for adaptive routing
 * - Seamless failover between WiFi and mesh networks
 */

package com.doodlelabs.meshriderwave.core.network

import com.doodlelabs.meshriderwave.BuildConfig
import com.doodlelabs.meshriderwave.core.util.logD
import com.doodlelabs.meshriderwave.core.util.logI
import com.doodlelabs.meshriderwave.core.util.logW
import com.doodlelabs.meshriderwave.domain.model.AddressRecord
import com.doodlelabs.meshriderwave.domain.model.Contact
import com.doodlelabs.meshriderwave.domain.model.NetworkType
import java.net.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.experimental.xor
import kotlin.math.max
import kotlin.math.min

/**
 * Military-grade P2P Connection manager with smart address resolution.
 *
 * This connector intelligently prioritizes addresses based on:
 * 1. Current network type (mesh addresses preferred when on mesh)
 * 2. Historical reliability (success/failure ratio)
 * 3. Recency (recently discovered addresses first)
 * 4. Last working address (cached for quick reconnection)
 *
 * Architecture:
 * - Uses NetworkTypeDetector to determine current network
 * - Reads from Contact's addressRegistry for rich metadata
 * - Reports success/failure for adaptive routing
 *
 * @see NetworkTypeDetector
 * @see Contact
 * @see AddressRecord
 */
@Singleton
class Connector @Inject constructor(
    private val networkTypeDetector: NetworkTypeDetector
) {
    companion object {
        private const val TAG = "Connector"

        /** Default connection timeout in milliseconds */
        private const val DEFAULT_TIMEOUT_MS = 5000

        /** Maximum retries before giving up */
        private const val MAX_RETRIES = 4

        /** Minimum reliability threshold for address selection */
        private const val MIN_RELIABILITY_THRESHOLD = 0.1f
    }

    var connectTimeout: Int = DEFAULT_TIMEOUT_MS
    var connectRetries: Int = 3
    var guessEUI64Address: Boolean = false
    var useNeighborTable: Boolean = false

    // Connection state for error reporting
    @Volatile var networkNotReachable = false
    @Volatile var unknownHostException = false
    @Volatile var connectException = false
    @Volatile var socketTimeoutException = false
    @Volatile var genericException = false

    // CALLS NOT WORKING FIX Jan 2026: Track when contact has no addresses
    // This allows caller to show a specific error message to user
    @Volatile var noAddressesError = false

    // Track the address that successfully connected (for metrics)
    @Volatile var lastConnectedAddress: String? = null
        private set

    /**
     * Callback for address connection attempts.
     * Used for UI feedback and debugging.
     */
    interface AddressCallback {
        fun onAddressTry(address: InetSocketAddress)
        fun onAddressSuccess(address: InetSocketAddress) {}
        fun onAddressFailed(address: InetSocketAddress, reason: String) {}
    }

    var addressCallback: AddressCallback? = null

    /**
     * Connect to contact using smart address resolution.
     *
     * Military-Grade Jan 2026 Enhancement:
     * - Prioritizes addresses matching current network type
     * - Uses reliability scores from address registry
     * - Reports success/failure for adaptive routing
     * - Handles seamless WiFi ↔ Mesh transitions
     *
     * @param contact The contact to connect to
     * @return Connected socket, or null if all addresses failed
     */
    fun connect(contact: Contact): Socket? {
        resetState()
        lastConnectedAddress = null

        // Get current network type for smart prioritization
        val currentNetworkType = networkTypeDetector.currentNetworkType.value
        logI("$TAG connect() to ${contact.name} | Network: ${currentNetworkType.displayName}")

        // Get prioritized addresses using new smart resolution
        val prioritizedAddresses = getPrioritizedSocketAddresses(contact, currentNetworkType)

        logD("$TAG connect() ${prioritizedAddresses.size} addresses to try")
        logD("$TAG connect() addressRegistry size: ${contact.addressRegistry.size}")
        logD("$TAG connect() lastWorkingAddress: ${contact.lastWorkingAddress}")

        if (prioritizedAddresses.isEmpty()) {
            // CALLS NOT WORKING FIX Jan 2026: Set flag so caller can show specific error
            noAddressesError = true
            logW("$TAG connect() NO ADDRESSES for ${contact.name}!")
            logW("$TAG This usually means the contact was added via QR but hasn't been discovered on the network yet")
            logW("$TAG Try: 1) Ensure both devices are on the same network 2) Re-scan QR code")
            return null
        }

        // Track failed addresses for reporting
        val failedAddresses = mutableListOf<Pair<String, String>>()

        for (iteration in 0..max(0, min(connectRetries, MAX_RETRIES))) {
            logD("$TAG connect() iteration $iteration")

            for ((socketAddress, originalAddress) in prioritizedAddresses) {
                logD("$TAG connect() trying: $socketAddress")
                addressCallback?.onAddressTry(socketAddress)

                try {
                    val socket = createSocket(socketAddress)

                    // Success! Record for metrics
                    lastConnectedAddress = originalAddress
                    addressCallback?.onAddressSuccess(socketAddress)
                    logI("$TAG connect() SUCCESS: ${contact.name} at $originalAddress")

                    return socket

                } catch (e: SocketTimeoutException) {
                    logD("$TAG connect() timeout: $socketAddress")
                    socketTimeoutException = true
                    failedAddresses.add(originalAddress to "timeout")
                    addressCallback?.onAddressFailed(socketAddress, "timeout")

                } catch (e: ConnectException) {
                    val reason = if ("ENETUNREACH" in e.toString()) {
                        networkNotReachable = true
                        "network unreachable"
                    } else {
                        connectException = true
                        "connection refused"
                    }
                    logD("$TAG connect() $reason: $socketAddress")
                    failedAddresses.add(originalAddress to reason)
                    addressCallback?.onAddressFailed(socketAddress, reason)

                } catch (e: UnknownHostException) {
                    logD("$TAG connect() unknown host: $socketAddress")
                    unknownHostException = true
                    failedAddresses.add(originalAddress to "unknown host")
                    addressCallback?.onAddressFailed(socketAddress, "unknown host")

                } catch (e: Exception) {
                    logW("$TAG connect() error: $socketAddress - ${e.message}")
                    genericException = true
                    failedAddresses.add(originalAddress to (e.message ?: "unknown error"))
                    addressCallback?.onAddressFailed(socketAddress, e.message ?: "error")
                }
            }
        }

        logW("$TAG connect() FAILED for ${contact.name} after ${failedAddresses.size} attempts")
        return null
    }

    /**
     * Get prioritized socket addresses for connection.
     *
     * Prioritization order:
     * 1. Addresses matching current network type (sorted by reliability)
     * 2. Last working address (if different from above)
     * 3. Other active addresses (sorted by reliability)
     * 4. Legacy addresses (for backward compatibility)
     *
     * @param contact The contact with address registry
     * @param currentNetworkType The current network type
     * @return List of (SocketAddress, OriginalAddress) pairs
     */
    private fun getPrioritizedSocketAddresses(
        contact: Contact,
        currentNetworkType: NetworkType
    ): List<Pair<InetSocketAddress, String>> {
        val port = BuildConfig.SIGNALING_PORT
        val result = mutableListOf<Pair<InetSocketAddress, String>>()
        val addedAddresses = mutableSetOf<String>()
        val interfaces = NetworkInterface.getNetworkInterfaces()?.toList() ?: emptyList()

        // Helper to add address with deduplication
        fun addAddress(address: String) {
            if (address in addedAddresses) return
            addedAddresses.add(address)

            val socketAddresses = expandAddress(address, port, interfaces)
            for (socketAddr in socketAddresses) {
                result.add(socketAddr to address)
            }
        }

        // 1. Last working address first (quick reconnection)
        contact.lastWorkingAddress?.let { addAddress(it) }

        // 2. Addresses matching current network type (highest priority)
        val matchingAddresses = contact.addressRegistry
            .filter { it.networkType == currentNetworkType && it.isActive }
            .filter { it.reliability >= MIN_RELIABILITY_THRESHOLD }
            .sortedByDescending { it.reliability }

        for (record in matchingAddresses) {
            addAddress(record.address)
        }

        // 3. Compatible network types (e.g., mesh-capable when on mesh)
        if (currentNetworkType in NetworkType.meshCapableTypes()) {
            val meshCompatible = contact.addressRegistry
                .filter { it.networkType in NetworkType.meshCapableTypes() }
                .filter { it.networkType != currentNetworkType }
                .filter { it.isActive && it.reliability >= MIN_RELIABILITY_THRESHOLD }
                .sortedByDescending { it.reliability }

            for (record in meshCompatible) {
                addAddress(record.address)
            }
        }

        // 4. All other active addresses by reliability
        val otherAddresses = contact.addressRegistry
            .filter { it.networkType != currentNetworkType }
            .filter { it.isActive && it.reliability >= MIN_RELIABILITY_THRESHOLD }
            .sortedByDescending { it.reliability }

        for (record in otherAddresses) {
            addAddress(record.address)
        }

        // 5. Legacy addresses (backward compatibility)
        @Suppress("DEPRECATION")
        for (addr in contact.addresses) {
            addAddress(addr)
        }

        // 6. EUI-64 guessing (if enabled)
        if (guessEUI64Address) {
            for (addr in addedAddresses.toList()) {
                val mac = extractMACFromEUI64(InetAddress.getByName(addr))
                if (mac != null) {
                    val eui64Addresses = generateEUI64Addresses(interfaces, mac, port)
                    for (socketAddr in eui64Addresses) {
                        val addrStr = socketAddr.hostString
                        if (addrStr !in addedAddresses) {
                            addedAddresses.add(addrStr)
                            result.add(socketAddr to addrStr)
                        }
                    }
                }
            }
        }

        return result
    }

    /**
     * Expand an address into socket addresses, handling link-local interfaces.
     */
    private fun expandAddress(
        address: String,
        port: Int,
        interfaces: List<NetworkInterface>
    ): List<InetSocketAddress> {
        val socketAddr = parseSocketAddress(address, port) ?: return emptyList()

        return if (isLinkLocalAddress(socketAddr.hostString)) {
            // For link-local, expand to all valid interfaces
            getValidInterfaceNames(interfaces).map { iface ->
                InetSocketAddress("${socketAddr.hostString}%$iface", port)
            }
        } else {
            listOf(socketAddr)
        }
    }

    private fun resetState() {
        networkNotReachable = false
        unknownHostException = false
        connectException = false
        socketTimeoutException = false
        genericException = false
        noAddressesError = false  // CALLS NOT WORKING FIX Jan 2026
    }

    private fun createSocket(address: InetSocketAddress): Socket {
        val socket = Socket()
        try {
            socket.keepAlive = true
            socket.tcpNoDelay = true
            socket.connect(address, connectTimeout)
            return socket
        } catch (e: Exception) {
            socket.close()
            throw e
        }
    }


    private fun parseSocketAddress(address: String, defaultPort: Int): InetSocketAddress? {
        return try {
            // Handle [IPv6]:port or IPv4:port or just address
            when {
                address.startsWith("[") -> {
                    val end = address.indexOf("]")
                    val host = address.substring(1, end)
                    val port = if (address.length > end + 2) {
                        address.substring(end + 2).toIntOrNull() ?: defaultPort
                    } else defaultPort
                    InetSocketAddress(host, port)
                }
                address.count { it == ':' } == 1 -> {
                    val parts = address.split(":")
                    InetSocketAddress(parts[0], parts[1].toIntOrNull() ?: defaultPort)
                }
                else -> InetSocketAddress(address, defaultPort)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun isLinkLocalAddress(address: String): Boolean {
        return try {
            InetAddress.getByName(address.split("%")[0]).isLinkLocalAddress
        } catch (e: Exception) {
            false
        }
    }

    private fun getValidInterfaceNames(interfaces: List<NetworkInterface>): List<String> {
        return interfaces
            .filter { !it.isLoopback && !ignoreInterface(it.name) }
            .map { it.name }
    }

    private fun ignoreInterface(name: String): Boolean {
        val ignorePatterns = listOf("lo", "dummy", "rmnet", "ccmni", "clat")
        return ignorePatterns.any { name.startsWith(it) }
    }

    /**
     * Extract MAC address from IPv6 EUI-64 address
     * EUI-64 has FF:FE in the middle
     */
    private fun extractMACFromEUI64(address: InetAddress?): ByteArray? {
        val bytes = address?.address ?: return null
        if (bytes.size != 16) return null

        // Check for FF:FE marker at bytes 11-12
        if (bytes[11] != 0xFF.toByte() || bytes[12] != 0xFE.toByte()) return null

        return byteArrayOf(
            bytes[8] xor 2,  // Flip universal/local bit
            bytes[9],
            bytes[10],
            bytes[13],
            bytes[14],
            bytes[15]
        )
    }

    /**
     * Generate EUI-64 addresses by combining MAC with local prefixes
     */
    private fun generateEUI64Addresses(
        interfaces: List<NetworkInterface>,
        mac: ByteArray,
        port: Int
    ): List<InetSocketAddress> {
        val addresses = mutableListOf<InetSocketAddress>()

        for (iface in interfaces) {
            if (iface.isLoopback || ignoreInterface(iface.name)) continue

            for (ifAddr in iface.interfaceAddresses) {
                val addr = ifAddr.address as? Inet6Address ?: continue
                if (addr.isLoopbackAddress) continue

                // If our address has EUI-64 or is link-local, create equivalent for target MAC
                if (extractMACFromEUI64(addr) != null || addr.isLinkLocalAddress) {
                    val newAddr = createEUI64Address(addr, mac)
                    newAddr?.let {
                        addresses.add(InetSocketAddress(it.hostAddress, port))
                    }
                }
            }
        }

        return addresses
    }

    private fun createEUI64Address(template: Inet6Address, mac: ByteArray): Inet6Address? {
        if (mac.size != 6) return null

        val bytes = template.address.copyOf()
        // Set interface ID from MAC using EUI-64
        bytes[8] = (mac[0] xor 2)  // Flip universal/local bit
        bytes[9] = mac[1]
        bytes[10] = mac[2]
        bytes[11] = 0xFF.toByte()  // FF:FE filler
        bytes[12] = 0xFE.toByte()
        bytes[13] = mac[3]
        bytes[14] = mac[4]
        bytes[15] = mac[5]

        return try {
            Inet6Address.getByAddress(null, bytes, template.scopeId) as Inet6Address
        } catch (e: Exception) {
            null
        }
    }
}