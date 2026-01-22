/*
 * Mesh Rider Wave - Domain Model: Contact
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 *
 * Military-Grade Identity-First Contact Model (Jan 2026)
 *
 * Key Principle: "An address is WHO you are, not WHERE you are"
 * - Identity = Ed25519 Public Key (PERMANENT, cryptographic)
 * - Addresses = Discovered at runtime (EPHEMERAL, classified by network type)
 *
 * Design based on:
 * - Reticulum Network Stack identity model
 * - IETF HIP (Host Identity Protocol) RFC 7401
 * - Cisco LISP (Locator/ID Separation Protocol) RFC 6830
 */

package com.doodlelabs.meshriderwave.domain.model

import android.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * Contact identified by cryptographic public key (Ed25519).
 *
 * This is an Identity-First design where the public key is the permanent,
 * portable identity. Network addresses are secondary, ephemeral attributes
 * that are discovered at runtime and may change across network types.
 *
 * @property publicKey Ed25519 public key (32 bytes) - PERMANENT IDENTITY
 * @property name Human-readable display name
 * @property addressRegistry Rich address records with network type and metrics
 * @property addresses Legacy address list (deprecated, use addressRegistry)
 * @property lastWorkingAddress Quick-access cache for last successful address
 * @property blocked Whether this contact is blocked
 * @property createdAt Timestamp when contact was created
 * @property lastSeenAt Timestamp when contact was last discovered online
 * @property state Current online state (transient, not persisted)
 */
@Serializable
data class Contact(
    val publicKey: ByteArray,
    val name: String,

    // NEW: Military-grade address registry with network type classification
    val addressRegistry: List<AddressRecord> = emptyList(),

    // LEGACY: Kept for backward compatibility (will be migrated to addressRegistry)
    @Deprecated("Use addressRegistry for new code")
    val addresses: List<String> = emptyList(),

    val lastWorkingAddress: String? = null,
    val blocked: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val lastSeenAt: Long? = null
) {
    /**
     * Contact online state for UI display.
     */
    enum class State {
        UNKNOWN,
        ONLINE,
        OFFLINE,
        BUSY
    }

    /**
     * Transient online state (not serialized).
     * Updated by PeerDiscoveryManager/BeaconManager.
     */
    @Transient
    var state: State = State.UNKNOWN

    // =========================================================================
    // IDENTITY PROPERTIES
    // =========================================================================

    /**
     * Device ID derived from public key (first 8 bytes as hex).
     * Used as unique identifier across the mesh network.
     */
    val deviceId: String
        get() = publicKey.take(8).joinToString("") { "%02x".format(it) }

    /**
     * Short ID for UI display (first 4 bytes as uppercase hex).
     */
    val shortId: String
        get() = deviceId.take(8).uppercase()

    /**
     * Identity hash (like Reticulum addressing).
     * SHA-256 of public key, truncated to 16 bytes (128-bit).
     */
    val identityHash: String
        get() = try {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(publicKey)
            hash.take(16).joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            deviceId  // Fallback to deviceId
        }

    // =========================================================================
    // ADDRESS REGISTRY METHODS
    // =========================================================================

    /**
     * Get all active addresses from registry, sorted by reliability.
     */
    fun getActiveAddresses(): List<AddressRecord> {
        return addressRegistry
            .filter { it.isActive }
            .sortedByDescending { it.reliability }
    }

    /**
     * Get addresses for a specific network type.
     */
    fun getAddressesForNetwork(type: NetworkType): List<AddressRecord> {
        return addressRegistry
            .filter { it.networkType == type && it.isActive }
            .sortedByDescending { it.reliability }
    }

    /**
     * Get mesh addresses (MeshRider 10.223.x.x).
     */
    fun getMeshAddresses(): List<String> {
        return getAddressesForNetwork(NetworkType.MESH_RIDER)
            .map { it.address }
    }

    /**
     * Get WiFi addresses (192.168.x.x, 10.x.x.x non-mesh).
     */
    fun getWiFiAddresses(): List<String> {
        return getAddressesForNetwork(NetworkType.WIFI_PRIVATE)
            .map { it.address }
    }

    /**
     * Get all addresses as simple string list (for Connector compatibility).
     * Merges addressRegistry with legacy addresses.
     */
    fun getAllAddresses(): List<String> {
        val fromRegistry = addressRegistry
            .filter { it.isActive }
            .sortedByDescending { it.reliability }
            .map { it.address }

        @Suppress("DEPRECATION")
        val fromLegacy = addresses.filter { addr ->
            fromRegistry.none { it == addr }
        }

        return fromRegistry + fromLegacy
    }

    /**
     * Get best address for a given network type.
     * Returns most reliable address matching the type, or best overall.
     */
    fun getBestAddressForNetwork(type: NetworkType): String? {
        // Try matching network type first
        val matchingAddresses = getAddressesForNetwork(type)
        if (matchingAddresses.isNotEmpty()) {
            return matchingAddresses.first().address
        }

        // Fall back to lastWorkingAddress
        if (lastWorkingAddress != null) {
            return lastWorkingAddress
        }

        // Fall back to any active address
        return getActiveAddresses().firstOrNull()?.address
    }

    /**
     * Add or update an address in the registry.
     * Returns new Contact with updated registry.
     */
    fun withAddress(record: AddressRecord): Contact {
        val updatedRegistry = addressRegistry.toMutableList()

        // Find existing record for this address
        val existingIndex = updatedRegistry.indexOfFirst {
            it.address == record.address
        }

        if (existingIndex >= 0) {
            // Update existing
            updatedRegistry[existingIndex] = record.refresh(record.source)
        } else {
            // Add new (at front for recency)
            updatedRegistry.add(0, record)
        }

        return copy(addressRegistry = updatedRegistry)
    }

    /**
     * Remove stale addresses from registry.
     * Returns new Contact with pruned registry.
     */
    fun pruneStaleAddresses(maxAgeMs: Long = AddressRecord.PRUNE_AGE_MS): Contact {
        val prunedRegistry = addressRegistry.filter { !it.shouldPrune(maxAgeMs) }
        return copy(addressRegistry = prunedRegistry)
    }

    /**
     * Mark all addresses of a network type as inactive.
     * Used when network type becomes unavailable.
     */
    fun deactivateNetworkType(type: NetworkType): Contact {
        val updatedRegistry = addressRegistry.map { record ->
            if (record.networkType == type) {
                record.copy(isActive = false)
            } else {
                record
            }
        }
        return copy(addressRegistry = updatedRegistry)
    }

    /**
     * Record a successful connection to an address.
     */
    fun recordConnectionSuccess(address: String): Contact {
        val updatedRegistry = addressRegistry.map { record ->
            if (record.address == address) {
                record.recordSuccess()
            } else {
                record
            }
        }
        return copy(
            addressRegistry = updatedRegistry,
            lastWorkingAddress = address,
            lastSeenAt = System.currentTimeMillis()
        )
    }

    /**
     * Record a failed connection to an address.
     */
    fun recordConnectionFailure(address: String): Contact {
        val updatedRegistry = addressRegistry.map { record ->
            if (record.address == address) {
                record.recordFailure()
            } else {
                record
            }
        }
        return copy(addressRegistry = updatedRegistry)
    }

    // =========================================================================
    // EQUALITY (Identity-based, not address-based)
    // =========================================================================

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Contact) return false
        // Identity is ONLY the public key
        return publicKey.contentEquals(other.publicKey)
    }

    override fun hashCode(): Int {
        return publicKey.contentHashCode()
    }

    // =========================================================================
    // QR CODE SERIALIZATION
    // =========================================================================

    companion object {
        const val PUBLIC_KEY_SIZE = 32
        const val QR_VERSION_1 = "1"
        const val QR_VERSION_2 = "MRW2"

        /**
         * Create contact from QR code data.
         * Supports both legacy (v1) and new (v2) formats.
         *
         * V1 Format: name|base64PublicKey|address1,address2,...
         * V2 Format: MRW2|name|base64PublicKey|mesh:addr1,wifi:addr2,...
         */
        fun fromQrData(data: String): Contact? {
            return try {
                val parts = data.split("|")

                when {
                    // V2 format with typed addresses
                    parts.firstOrNull() == QR_VERSION_2 && parts.size >= 3 -> {
                        parseQrV2(parts)
                    }
                    // V1 legacy format
                    parts.size >= 2 -> {
                        parseQrV1(parts)
                    }
                    else -> null
                }
            } catch (e: Exception) {
                null
            }
        }

        /**
         * Parse legacy V1 QR format.
         */
        private fun parseQrV1(parts: List<String>): Contact? {
            val name = parts[0]
            val publicKey = Base64.decode(parts[1], Base64.NO_WRAP)

            if (publicKey.size != PUBLIC_KEY_SIZE) return null

            @Suppress("DEPRECATION")
            val addresses = if (parts.size > 2) {
                parts[2].split(",").filter { it.isNotBlank() }
            } else {
                emptyList()
            }

            // Convert legacy addresses to registry
            val registry = addresses.map { addr ->
                AddressRecord.fromAddress(addr, source = DiscoverySource.MANUAL)
            }

            return Contact(
                publicKey = publicKey,
                name = name,
                addressRegistry = registry,
                addresses = addresses  // Keep for backward compatibility
            )
        }

        /**
         * Parse V2 QR format with typed addresses.
         */
        private fun parseQrV2(parts: List<String>): Contact? {
            // MRW2|name|base64PublicKey|mesh:addr1,wifi:addr2,...
            val name = parts[1]
            val publicKey = Base64.decode(parts[2], Base64.NO_WRAP)

            if (publicKey.size != PUBLIC_KEY_SIZE) return null

            val registry = mutableListOf<AddressRecord>()
            val legacyAddresses = mutableListOf<String>()

            if (parts.size > 3) {
                val addressParts = parts[3].split(",")
                for (addrPart in addressParts) {
                    val colonIndex = addrPart.indexOf(":")
                    if (colonIndex > 0) {
                        val typeStr = addrPart.substring(0, colonIndex).lowercase()
                        val addr = addrPart.substring(colonIndex + 1)

                        val networkType = when (typeStr) {
                            "mesh" -> NetworkType.MESH_RIDER
                            "wifi" -> NetworkType.WIFI_PRIVATE
                            "wifid" -> NetworkType.WIFI_DIRECT
                            "ipv6" -> NetworkType.IPV6_LINK_LOCAL
                            else -> NetworkType.UNKNOWN
                        }

                        registry.add(AddressRecord(
                            address = addr,
                            networkType = networkType,
                            source = DiscoverySource.MANUAL
                        ))
                        legacyAddresses.add(addr)
                    } else {
                        // No type prefix - auto-classify
                        registry.add(AddressRecord.fromAddress(addrPart, source = DiscoverySource.MANUAL))
                        legacyAddresses.add(addrPart)
                    }
                }
            }

            return Contact(
                publicKey = publicKey,
                name = name,
                addressRegistry = registry,
                addresses = legacyAddresses
            )
        }
    }

    /**
     * Export to QR code data format (V2 with typed addresses).
     */
    fun toQrData(): String {
        val encodedKey = Base64.encodeToString(publicKey, Base64.NO_WRAP)

        // Build typed address string
        val typedAddresses = addressRegistry
            .filter { it.isActive }
            .take(5)  // Limit to 5 addresses for QR size
            .joinToString(",") { record ->
                val prefix = when (record.networkType) {
                    NetworkType.MESH_RIDER -> "mesh"
                    NetworkType.WIFI_PRIVATE -> "wifi"
                    NetworkType.WIFI_DIRECT -> "wifid"
                    NetworkType.IPV6_LINK_LOCAL -> "ipv6"
                    else -> "other"
                }
                "$prefix:${record.address}"
            }

        return "$QR_VERSION_2|$name|$encodedKey|$typedAddresses"
    }

    /**
     * Export to legacy V1 QR format (for compatibility).
     */
    fun toQrDataLegacy(): String {
        val encodedKey = Base64.encodeToString(publicKey, Base64.NO_WRAP)
        val addressList = getAllAddresses().take(5).joinToString(",")
        return "$name|$encodedKey|$addressList"
    }
}
