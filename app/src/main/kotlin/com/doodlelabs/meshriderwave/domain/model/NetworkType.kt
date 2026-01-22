/*
 * Mesh Rider Wave - Network Type Classification
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 *
 * Military-Grade Identity-First Architecture (Jan 2026)
 * Based on: Reticulum, BATMAN-adv, HIP RFC 7401, LISP RFC 6830
 *
 * Reference: https://reticulum.network/manual/understanding.html
 * "An address is a Hash of an Identity - WHO you are, not WHERE you are"
 */

package com.doodlelabs.meshriderwave.domain.model

import kotlinx.serialization.Serializable

/**
 * Network type classification for transport layer addresses.
 *
 * Military-grade systems separate Identity (permanent cryptographic key)
 * from Locator (ephemeral network address). This enum classifies locators
 * by network type to enable intelligent routing decisions.
 *
 * Design based on:
 * - IETF HIP (Host Identity Protocol) RFC 7401
 * - Cisco LISP (Locator/ID Separation Protocol) RFC 6830
 * - DoodleLabs MeshRider BATMAN-adv architecture
 *
 * @see AddressRecord
 * @see Contact
 */
@Serializable
enum class NetworkType(
    val displayName: String,
    val priority: Int,  // Lower = higher priority for same-type matching
    val ipPrefixes: List<String>
) {
    /**
     * MeshRider radio mesh network (10.223.x.x)
     * Highest priority - direct mesh connectivity via BATMAN-adv
     */
    MESH_RIDER(
        displayName = "Mesh",
        priority = 1,
        ipPrefixes = listOf("10.223.")
    ),

    /**
     * WiFi Direct peer-to-peer (192.168.49.x)
     * Android WiFi P2P creates this subnet
     */
    WIFI_DIRECT(
        displayName = "WiFi Direct",
        priority = 2,
        ipPrefixes = listOf("192.168.49.")
    ),

    /**
     * Standard WiFi/Ethernet private network
     * 192.168.x.x, 10.x.x.x (non-mesh), 172.16-31.x.x
     */
    WIFI_PRIVATE(
        displayName = "WiFi",
        priority = 3,
        ipPrefixes = listOf("192.168.", "172.16.", "172.17.", "172.18.", "172.19.",
            "172.20.", "172.21.", "172.22.", "172.23.", "172.24.", "172.25.",
            "172.26.", "172.27.", "172.28.", "172.29.", "172.30.", "172.31.")
    ),

    /**
     * IPv6 Link-Local address (fe80::%interface)
     * Always available on any interface, requires scope ID
     */
    IPV6_LINK_LOCAL(
        displayName = "IPv6 LL",
        priority = 4,
        ipPrefixes = listOf("fe80:")
    ),

    /**
     * Bluetooth PAN (future support)
     */
    BLUETOOTH(
        displayName = "Bluetooth",
        priority = 5,
        ipPrefixes = emptyList()
    ),

    /**
     * Cellular/Mobile data (public IP, NAT traversal needed)
     */
    CELLULAR(
        displayName = "Cellular",
        priority = 6,
        ipPrefixes = emptyList()
    ),

    /**
     * Unknown or unclassified network type
     */
    UNKNOWN(
        displayName = "Unknown",
        priority = 99,
        ipPrefixes = emptyList()
    );

    companion object {
        /**
         * Classify an IP address into a NetworkType.
         *
         * Uses prefix matching to determine the network type.
         * MeshRider (10.223.x.x) is checked before generic private (10.x.x.x).
         *
         * @param address IP address string (IPv4 or IPv6)
         * @return Classified NetworkType
         */
        fun fromAddress(address: String): NetworkType {
            val cleanAddress = address
                .removePrefix("[")
                .substringBefore("]")
                .substringBefore(":")
                .substringBefore("%")
                .lowercase()

            // Check in priority order (MESH_RIDER before WIFI_PRIVATE for 10.x)
            return when {
                // MeshRider specific subnet
                cleanAddress.startsWith("10.223.") -> MESH_RIDER

                // WiFi Direct
                cleanAddress.startsWith("192.168.49.") -> WIFI_DIRECT

                // IPv6 Link-Local
                cleanAddress.startsWith("fe80:") || cleanAddress.startsWith("fe80") -> IPV6_LINK_LOCAL

                // Private networks (RFC 1918)
                cleanAddress.startsWith("192.168.") -> WIFI_PRIVATE
                cleanAddress.startsWith("172.") && isPrivate172(cleanAddress) -> WIFI_PRIVATE
                cleanAddress.startsWith("10.") -> WIFI_PRIVATE  // Non-mesh 10.x.x.x

                // Default
                else -> UNKNOWN
            }
        }

        /**
         * Check if a 172.x.x.x address is in private range (172.16-31.x.x)
         */
        private fun isPrivate172(address: String): Boolean {
            return try {
                val parts = address.split(".")
                if (parts.size >= 2) {
                    val secondOctet = parts[1].toIntOrNull() ?: return false
                    secondOctet in 16..31
                } else {
                    false
                }
            } catch (e: Exception) {
                false
            }
        }

        /**
         * Get all mesh-capable network types (can route PTT traffic)
         */
        fun meshCapableTypes(): Set<NetworkType> = setOf(
            MESH_RIDER,
            WIFI_DIRECT,
            WIFI_PRIVATE,
            IPV6_LINK_LOCAL
        )
    }
}
