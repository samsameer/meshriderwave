/*
 * Mesh Rider Wave - P2P Connector
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 *
 * Based on Meshenger's proven connection logic
 * Handles IPv4, IPv6, link-local, EUI-64 address discovery
 */

package com.doodlelabs.meshriderwave.core.network

import com.doodlelabs.meshriderwave.BuildConfig
import com.doodlelabs.meshriderwave.core.util.logD
import com.doodlelabs.meshriderwave.core.util.logW
import com.doodlelabs.meshriderwave.domain.model.Contact
import java.net.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.experimental.xor
import kotlin.math.max
import kotlin.math.min

/**
 * P2P Connection manager
 * - Tries multiple addresses with retries
 * - Supports IPv6 link-local with interface names
 * - Can guess EUI-64 addresses from MAC
 */
@Singleton
class Connector @Inject constructor() {

    var connectTimeout: Int = 5000
    var connectRetries: Int = 3
    var guessEUI64Address: Boolean = false
    var useNeighborTable: Boolean = false

    // Connection state for error reporting
    var networkNotReachable = false
    var unknownHostException = false
    var connectException = false
    var socketTimeoutException = false
    var genericException = false

    interface AddressCallback {
        fun onAddressTry(address: InetSocketAddress)
    }

    var addressCallback: AddressCallback? = null

    /**
     * Connect to contact, trying all known addresses
     */
    fun connect(contact: Contact): Socket? {
        resetState()

        for (iteration in 0..max(0, min(connectRetries, 4))) {
            logD("connect() iteration $iteration")

            for (address in getAllSocketAddresses(contact)) {
                logD("connect() trying: $address")
                addressCallback?.onAddressTry(address)

                try {
                    return createSocket(address)
                } catch (e: SocketTimeoutException) {
                    logD("connect() timeout: $address")
                    socketTimeoutException = true
                } catch (e: ConnectException) {
                    logD("connect() connection refused: $address")
                    if ("ENETUNREACH" in e.toString()) {
                        networkNotReachable = true
                    } else {
                        connectException = true
                    }
                } catch (e: UnknownHostException) {
                    logD("connect() unknown host: $address")
                    unknownHostException = true
                } catch (e: Exception) {
                    logW("connect() error: $address - ${e.message}")
                    genericException = true
                }
            }
        }

        return null
    }

    private fun resetState() {
        networkNotReachable = false
        unknownHostException = false
        connectException = false
        socketTimeoutException = false
        genericException = false
    }

    private fun createSocket(address: InetSocketAddress): Socket {
        val socket = Socket()
        try {
            socket.connect(address, connectTimeout)
            return socket
        } catch (e: Exception) {
            socket.close()
            throw e
        }
    }

    private fun getAllSocketAddresses(contact: Contact): List<InetSocketAddress> {
        val port = BuildConfig.SIGNALING_PORT
        val addresses = mutableListOf<InetSocketAddress>()
        val interfaces = NetworkInterface.getNetworkInterfaces()?.toList() ?: emptyList()

        // Try last working address first
        contact.lastWorkingAddress?.let { lastAddr ->
            parseSocketAddress(lastAddr, port)?.let { addresses.add(it) }
        }

        // Add all known addresses
        for (addr in contact.addresses) {
            val socketAddr = parseSocketAddress(addr, port) ?: continue

            if (isLinkLocalAddress(socketAddr.hostString)) {
                // For link-local, try with each interface
                for (iface in getValidInterfaceNames(interfaces)) {
                    addresses.add(InetSocketAddress("${socketAddr.hostString}%$iface", port))
                }
            } else {
                addresses.add(socketAddr)
            }

            // EUI-64 address guessing
            if (guessEUI64Address) {
                val mac = extractMACFromEUI64(InetAddress.getByName(addr))
                if (mac != null) {
                    addresses.addAll(generateEUI64Addresses(interfaces, mac, port))
                }
            }
        }

        return addresses.distinctBy { it.hostString }
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