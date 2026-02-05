/*
 * Mesh Rider Wave - Identity Beacon
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 *
 * Military-Grade Multicast Peer Discovery (Jan 2026)
 *
 * Based on:
 * - ATAK CoT (Cursor on Target) multicast protocol
 * - Meshtastic periodic telemetry broadcasts
 * - Reticulum signed "announce" packets
 *
 * Key features:
 * - Signed with Ed25519 to prevent spoofing
 * - Contains public key (identity) NOT IP address
 * - Listeners derive sender address from packet source
 * - Works across any network type (WiFi, Mesh, etc.)
 */

package com.doodlelabs.meshriderwave.core.discovery

import android.util.Base64
import com.doodlelabs.meshriderwave.domain.model.NetworkType
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.ByteBuffer

/**
 * Signed identity beacon for multicast peer discovery.
 *
 * This beacon is broadcast periodically on the mesh network to announce
 * presence. Unlike mDNS (which is local-link only), multicast beacons
 * can traverse mesh hops via BATMAN-adv.
 *
 * Protocol:
 * 1. Node creates beacon with identity info
 * 2. Node signs beacon with Ed25519 private key
 * 3. Beacon sent to multicast group (239.255.77.1:7777)
 * 4. Receivers verify signature against embedded public key
 * 5. Receivers extract sender IP from UDP packet source
 * 6. Receivers update contact address registry
 *
 * @property version Protocol version (for future compatibility)
 * @property publicKey Ed25519 public key (32 bytes, Base64 encoded)
 * @property name Display name (max 32 chars)
 * @property timestamp Unix timestamp in milliseconds (prevents replay)
 * @property capabilities Set of supported features
 * @property networkType Current network type of sender
 * @property signature Ed25519 signature of payload (Base64 encoded)
 */
@Serializable
data class IdentityBeacon(
    val version: Int = CURRENT_VERSION,
    val publicKey: String,  // Base64 encoded
    val name: String,
    val timestamp: Long,
    val capabilities: Set<Capability>,
    val networkType: NetworkType,
    // Blue Force Tracking (Jan 2026) - GPS coordinates for situational awareness
    val latitude: Double? = null,
    val longitude: Double? = null,
    val altitude: Float? = null,
    val speed: Float? = null,        // m/s
    val bearing: Float? = null,      // degrees
    val locationAccuracy: Float? = null,  // meters
    val signature: String = ""  // Base64 encoded, empty before signing
) {
    companion object {
        const val CURRENT_VERSION = 1

        /** Multicast group for beacon discovery */
        const val MULTICAST_GROUP = "239.255.77.1"

        /** UDP port for beacon discovery */
        const val MULTICAST_PORT = 7777

        /** Beacon interval in milliseconds (30 seconds) */
        const val BEACON_INTERVAL_MS = 30_000L

        /** Maximum beacon age before considered stale (90 seconds = 3x interval) */
        const val MAX_BEACON_AGE_MS = 90_000L

        /** Maximum name length */
        const val MAX_NAME_LENGTH = 32

        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        /**
         * Create a beacon (unsigned).
         * Call sign() to add signature before broadcasting.
         *
         * @param latitude GPS latitude (optional, for Blue Force Tracking)
         * @param longitude GPS longitude (optional, for Blue Force Tracking)
         */
        fun create(
            publicKey: ByteArray,
            name: String,
            capabilities: Set<Capability>,
            networkType: NetworkType,
            // Blue Force Tracking location (Jan 2026)
            latitude: Double? = null,
            longitude: Double? = null,
            altitude: Float? = null,
            speed: Float? = null,
            bearing: Float? = null,
            locationAccuracy: Float? = null
        ): IdentityBeacon {
            return IdentityBeacon(
                publicKey = Base64.encodeToString(publicKey, Base64.NO_WRAP),
                name = name.take(MAX_NAME_LENGTH),
                timestamp = System.currentTimeMillis(),
                capabilities = capabilities,
                networkType = networkType,
                latitude = latitude,
                longitude = longitude,
                altitude = altitude,
                speed = speed,
                bearing = bearing,
                locationAccuracy = locationAccuracy
            )
        }

        /**
         * Parse beacon from JSON string.
         */
        fun fromJson(jsonString: String): IdentityBeacon? {
            return try {
                json.decodeFromString<IdentityBeacon>(jsonString)
            } catch (e: Exception) {
                null
            }
        }

        /**
         * Parse beacon from raw bytes.
         */
        fun fromBytes(bytes: ByteArray): IdentityBeacon? {
            return try {
                fromJson(String(bytes, Charsets.UTF_8))
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Get payload bytes for signing (excludes signature field).
     */
    fun getSigningPayload(): ByteArray {
        val payloadBeacon = copy(signature = "")
        return json.encodeToString(payloadBeacon).toByteArray(Charsets.UTF_8)
    }

    /**
     * Create a signed copy of this beacon.
     */
    fun withSignature(signatureBytes: ByteArray): IdentityBeacon {
        return copy(signature = Base64.encodeToString(signatureBytes, Base64.NO_WRAP))
    }

    /**
     * Get public key as bytes.
     */
    fun getPublicKeyBytes(): ByteArray {
        return Base64.decode(publicKey, Base64.NO_WRAP)
    }

    /**
     * Get signature as bytes.
     */
    fun getSignatureBytes(): ByteArray {
        return if (signature.isNotEmpty()) {
            Base64.decode(signature, Base64.NO_WRAP)
        } else {
            ByteArray(0)
        }
    }

    /**
     * Convert to JSON string.
     */
    fun toJson(): String {
        return json.encodeToString(this)
    }

    /**
     * Convert to bytes for transmission.
     */
    fun toBytes(): ByteArray {
        return toJson().toByteArray(Charsets.UTF_8)
    }

    /**
     * Check if beacon is still valid (not expired).
     */
    fun isValid(): Boolean {
        val age = System.currentTimeMillis() - timestamp
        // Allow up to 5s clock skew (negative age) between devices
        return age in -5_000L..MAX_BEACON_AGE_MS
    }

    /**
     * Check if beacon has a signature.
     */
    fun isSigned(): Boolean {
        return signature.isNotEmpty()
    }

    /**
     * Age of this beacon in milliseconds.
     */
    val ageMs: Long
        get() = System.currentTimeMillis() - timestamp

    /**
     * Check if beacon has valid location data.
     */
    fun hasLocation(): Boolean {
        return latitude != null && longitude != null
    }
}

/**
 * Capability flags for beacon.
 * Indicates what features this peer supports.
 */
@Serializable
enum class Capability {
    /** Voice calling (WebRTC audio) */
    VOICE,

    /** Video calling (WebRTC video) */
    VIDEO,

    /** Push-to-Talk channels */
    PTT,

    /** Group messaging (MLS encrypted) */
    GROUP,

    /** Location sharing (Blue Force Tracking) */
    LOCATION,

    /** SOS emergency beacon */
    SOS,

    /** File transfer */
    FILE_TRANSFER,

    /** ATAK/CoT integration */
    ATAK_COT
}

/**
 * Result of beacon verification.
 */
sealed class BeaconVerifyResult {
    /** Signature valid, beacon authentic */
    data class Valid(val beacon: IdentityBeacon) : BeaconVerifyResult()

    /** Signature invalid, beacon may be spoofed */
    data class InvalidSignature(val reason: String) : BeaconVerifyResult()

    /** Beacon expired (timestamp too old) */
    data class Expired(val ageMs: Long) : BeaconVerifyResult()

    /** Beacon malformed or unparseable */
    data class Malformed(val reason: String) : BeaconVerifyResult()
}
