/*
 * Mesh Rider Wave - Address Record Model
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 *
 * Military-Grade Address Registry (Jan 2026)
 * Implements Identity/Locator Separation per IETF HIP RFC 7401
 *
 * Key Principle: Addresses are DISCOVERED at runtime, not stored statically.
 * Each AddressRecord tracks metadata for intelligent routing decisions.
 */

package com.doodlelabs.meshriderwave.domain.model

import kotlinx.serialization.Serializable

/**
 * A single address record in the contact's address registry.
 *
 * Unlike traditional contact lists that store static IP addresses,
 * AddressRecord tracks dynamic transport endpoints with metadata
 * for reliability-based routing.
 *
 * Design influenced by:
 * - Reticulum Network Stack path caching
 * - ATAK CoT multicast discovery
 * - Meshtastic neighbor info module
 *
 * @property address The IP address or hostname (without port)
 * @property networkType Classified network type for routing priority
 * @property port Optional port override (default: SIGNALING_PORT)
 * @property discoveredAt Timestamp when this address was first/last discovered
 * @property lastSuccessAt Timestamp of last successful connection (null if never)
 * @property successCount Number of successful connections via this address
 * @property failureCount Number of failed connection attempts
 * @property isActive Whether this address should be tried (false = stale/pruned)
 * @property source How this address was discovered
 */
@Serializable
data class AddressRecord(
    val address: String,
    val networkType: NetworkType,
    val port: Int? = null,
    val discoveredAt: Long = System.currentTimeMillis(),
    val lastSuccessAt: Long? = null,
    val successCount: Int = 0,
    val failureCount: Int = 0,
    val isActive: Boolean = true,
    val source: DiscoverySource = DiscoverySource.UNKNOWN
) {
    /**
     * Reliability score (0.0 to 1.0) based on success/failure ratio.
     * Used for prioritizing addresses during connection attempts.
     *
     * Formula: successCount / (successCount + failureCount)
     * Returns 0.5 if no attempts recorded (neutral score).
     */
    val reliability: Float
        get() {
            val total = successCount + failureCount
            return when {
                total == 0 -> 0.5f  // Neutral - no history
                else -> successCount.toFloat() / total
            }
        }

    /**
     * Age of this record in milliseconds since discovery.
     */
    val ageMs: Long
        get() = System.currentTimeMillis() - discoveredAt

    /**
     * Time since last successful connection in milliseconds.
     * Returns Long.MAX_VALUE if never successful.
     */
    val timeSinceSuccessMs: Long
        get() = lastSuccessAt?.let { System.currentTimeMillis() - it } ?: Long.MAX_VALUE

    /**
     * Full address with port for socket connection.
     * Format: "address:port" for IPv4, "[address]:port" for IPv6
     */
    fun toSocketAddress(defaultPort: Int): String {
        val effectivePort = port ?: defaultPort
        return if (address.contains(":") && !address.startsWith("[")) {
            // IPv6 without brackets
            "[$address]:$effectivePort"
        } else if (address.startsWith("[")) {
            // IPv6 already bracketed
            "${address.substringBefore("]")}]:$effectivePort"
        } else {
            // IPv4 or hostname
            "$address:$effectivePort"
        }
    }

    /**
     * Create a copy with updated success metrics.
     */
    fun recordSuccess(): AddressRecord = copy(
        lastSuccessAt = System.currentTimeMillis(),
        successCount = successCount + 1,
        isActive = true
    )

    /**
     * Create a copy with updated failure metrics.
     */
    fun recordFailure(): AddressRecord = copy(
        failureCount = failureCount + 1
    )

    /**
     * Create a copy marking this address as refreshed (seen again).
     */
    fun refresh(newSource: DiscoverySource? = null): AddressRecord = copy(
        discoveredAt = System.currentTimeMillis(),
        isActive = true,
        source = newSource ?: source
    )

    /**
     * Check if this record should be pruned (too old and never successful).
     *
     * @param maxAgeMs Maximum age for records with no successful connections
     * @return true if record should be pruned
     */
    fun shouldPrune(maxAgeMs: Long = PRUNE_AGE_MS): Boolean {
        // Keep if ever successful
        if (successCount > 0) return false

        // Keep if recently discovered
        if (ageMs < maxAgeMs) return false

        // Prune old, never-successful records
        return true
    }

    companion object {
        /** Default prune age: 7 days */
        const val PRUNE_AGE_MS = 7L * 24 * 60 * 60 * 1000

        /** Create from raw address string with auto-classification */
        fun fromAddress(
            address: String,
            port: Int? = null,
            source: DiscoverySource = DiscoverySource.UNKNOWN
        ): AddressRecord {
            val cleanAddress = address
                .substringBefore(":")  // Remove port if present
                .removePrefix("[")
                .removeSuffix("]")

            return AddressRecord(
                address = cleanAddress,
                networkType = NetworkType.fromAddress(cleanAddress),
                port = port,
                source = source
            )
        }
    }
}

/**
 * How an address was discovered.
 * Used for debugging and prioritization.
 */
@Serializable
enum class DiscoverySource {
    /** Manually entered or from QR code */
    MANUAL,

    /** Discovered via mDNS/DNS-SD */
    MDNS,

    /** Discovered via multicast beacon */
    BEACON,

    /** Discovered via BATMAN-adv originator table */
    BATMAN,

    /** Learned from successful incoming connection */
    INCOMING,

    /** Extracted from WebRTC ICE candidate */
    ICE_CANDIDATE,

    /** Unknown or legacy source */
    UNKNOWN
}
