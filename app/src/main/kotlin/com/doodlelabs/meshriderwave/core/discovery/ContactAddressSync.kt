/*
 * Mesh Rider Wave - Contact Address Sync
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 *
 * Military-Grade Contact Address Synchronization (Jan 2026)
 *
 * This service bridges the gap between runtime peer discovery
 * and persistent contact storage. When a peer is discovered
 * (via mDNS or beacon), this service updates the contact's
 * address registry with the new address.
 *
 * Key Principle: Addresses are DISCOVERED, not STORED statically.
 *
 * Data Flow:
 * 1. BeaconManager/PeerDiscoveryManager discovers peer
 * 2. ContactAddressSync receives discovery event
 * 3. Looks up contact by public key (identity match)
 * 4. Updates contact's addressRegistry with new address
 * 5. Persists updated contact to storage
 */

package com.doodlelabs.meshriderwave.core.discovery

import android.util.Log
import com.doodlelabs.meshriderwave.core.network.NetworkTypeDetector
import com.doodlelabs.meshriderwave.core.network.PeerDiscoveryManager
import com.doodlelabs.meshriderwave.domain.model.AddressRecord
import com.doodlelabs.meshriderwave.domain.model.DiscoverySource
import com.doodlelabs.meshriderwave.domain.model.NetworkType
import com.doodlelabs.meshriderwave.domain.repository.ContactRepository
// Type aliases to disambiguate the two DiscoveredPeer types
import com.doodlelabs.meshriderwave.core.discovery.DiscoveredPeer as BeaconPeer
import com.doodlelabs.meshriderwave.core.network.DiscoveredPeer as MdnsPeer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Synchronizes discovered peer addresses to contact storage.
 *
 * This service listens to multiple discovery sources:
 * - BeaconManager: Multicast identity beacons
 * - PeerDiscoveryManager: mDNS/DNS-SD discovery
 *
 * When a peer is discovered, if they are a saved contact,
 * their address registry is updated with the new address.
 *
 * @see BeaconManager
 * @see PeerDiscoveryManager
 * @see ContactRepository
 */
@Singleton
class ContactAddressSync @Inject constructor(
    private val contactRepository: ContactRepository,
    private val beaconManager: BeaconManager,
    private val peerDiscoveryManager: PeerDiscoveryManager,
    private val networkTypeDetector: NetworkTypeDetector
) {
    companion object {
        private const val TAG = "ContactAddressSync"

        /** Maximum addresses to keep per contact */
        private const val MAX_ADDRESSES_PER_CONTACT = 10

        /** Prune addresses older than this (7 days) */
        private const val PRUNE_AGE_MS = 7L * 24 * 60 * 60 * 1000
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var syncJob: Job? = null

    @Volatile
    private var isStarted = false

    /**
     * Start synchronization.
     * Subscribes to discovery events from beacon and mDNS.
     */
    fun start() {
        if (isStarted) {
            Log.d(TAG, "Already started")
            return
        }

        Log.i(TAG, "Starting contact address sync")
        isStarted = true

        syncJob = scope.launch {
            // Subscribe to beacon discoveries
            beaconManager.peerDiscoveredEvent
                .onEach { peer -> syncFromBeaconPeer(peer) }
                .catch { e -> Log.e(TAG, "Error in beacon sync", e) }
                .launchIn(this)

            // Subscribe to mDNS discoveries
            peerDiscoveryManager.discoveredPeers
                .onEach { peers -> syncFromMdnsPeers(peers) }
                .catch { e -> Log.e(TAG, "Error in mDNS sync", e) }
                .launchIn(this)

            // Subscribe to network changes to update address priorities
            networkTypeDetector.currentNetworkType
                .onEach { networkType -> onNetworkTypeChanged(networkType) }
                .catch { e -> Log.e(TAG, "Error in network change handler", e) }
                .launchIn(this)
        }
    }

    /**
     * Stop synchronization.
     */
    fun stop() {
        if (!isStarted) return

        Log.i(TAG, "Stopping contact address sync")
        isStarted = false
        syncJob?.cancel()
        syncJob = null
    }

    /**
     * Force sync a specific contact's addresses from all discovery sources.
     */
    suspend fun syncContact(publicKey: ByteArray) {
        // Check beacon cache
        val beaconPeerResult = beaconManager.getPeerByPublicKey(publicKey)
        if (beaconPeerResult != null) {
            syncFromBeaconPeer(beaconPeerResult)
        }

        // Check mDNS cache
        val mdnsPeerResult = peerDiscoveryManager.findPeerByPublicKey(publicKey)
        if (mdnsPeerResult != null) {
            syncMdnsPeerToContact(mdnsPeerResult.publicKey, mdnsPeerResult.addresses, DiscoverySource.MDNS)
        }
    }

    /**
     * Manually add an address to a contact.
     * Used when address is learned from incoming connection.
     */
    suspend fun addAddressToContact(
        publicKey: ByteArray,
        address: String,
        source: DiscoverySource
    ) {
        val contact = contactRepository.getContactByPublicKey(publicKey) ?: return

        val networkType = NetworkType.fromAddress(address)
        val record = AddressRecord(
            address = cleanAddress(address),
            networkType = networkType,
            source = source,
            discoveredAt = System.currentTimeMillis()
        )

        val updatedContact = contact.withAddress(record)
            .pruneAndLimit()

        contactRepository.updateContact(updatedContact)
        Log.d(TAG, "Added address $address to ${contact.name} via $source")
    }

    /**
     * Record successful connection to update metrics.
     */
    suspend fun recordConnectionSuccess(publicKey: ByteArray, address: String) {
        val contact = contactRepository.getContactByPublicKey(publicKey) ?: return

        val updatedContact = contact.recordConnectionSuccess(cleanAddress(address))
        contactRepository.updateContact(updatedContact)

        Log.d(TAG, "Recorded success for ${contact.name} at $address")
    }

    /**
     * Record failed connection to update metrics.
     */
    suspend fun recordConnectionFailure(publicKey: ByteArray, address: String) {
        val contact = contactRepository.getContactByPublicKey(publicKey) ?: return

        val updatedContact = contact.recordConnectionFailure(cleanAddress(address))
        contactRepository.updateContact(updatedContact)

        Log.d(TAG, "Recorded failure for ${contact.name} at $address")
    }

    // =========================================================================
    // PRIVATE METHODS
    // =========================================================================

    /**
     * Sync a peer discovered via multicast beacon.
     * BeaconPeer has a single address field.
     */
    private suspend fun syncFromBeaconPeer(peer: BeaconPeer) {
        val contact = contactRepository.getContactByPublicKey(peer.publicKey)
        if (contact == null) {
            // Not a saved contact, ignore
            Log.d(TAG, "Beacon peer ${peer.name} is not a saved contact")
            return
        }

        val record = AddressRecord(
            address = cleanAddress(peer.address),
            networkType = peer.networkType,
            source = DiscoverySource.BEACON,
            discoveredAt = peer.lastSeenAt
        )

        val updatedContact = contact
            .withAddress(record)
            .copy(
                lastSeenAt = peer.lastSeenAt,
                lastWorkingAddress = peer.address
            )
            .pruneAndLimit()

        contactRepository.updateContact(updatedContact)
        Log.d(TAG, "Synced beacon: ${contact.name} at ${peer.address} (${peer.networkType})")
    }

    /**
     * Sync all peers discovered via mDNS.
     * MdnsPeer has an addresses list.
     */
    private suspend fun syncFromMdnsPeers(peers: Map<String, MdnsPeer>) {
        for ((_, peer) in peers) {
            syncMdnsPeerToContact(peer.publicKey, peer.addresses, DiscoverySource.MDNS)
        }
    }

    private suspend fun syncMdnsPeerToContact(
        publicKey: ByteArray,
        addresses: List<String>,
        source: DiscoverySource
    ) {
        val contact = contactRepository.getContactByPublicKey(publicKey) ?: return

        var updatedContact: com.doodlelabs.meshriderwave.domain.model.Contact = contact
        val now = System.currentTimeMillis()

        // Add all discovered addresses
        for (address in addresses) {
            val cleanAddr = cleanAddress(address)
            val networkType = NetworkType.fromAddress(cleanAddr)

            val record = AddressRecord(
                address = cleanAddr,
                networkType = networkType,
                source = source,
                discoveredAt = now
            )

            updatedContact = updatedContact.withAddress(record)
        }

        // Update lastSeen and lastWorkingAddress
        val primaryAddress = addresses.firstOrNull()
        if (primaryAddress != null) {
            updatedContact = updatedContact.copy(
                lastSeenAt = now,
                lastWorkingAddress = cleanAddress(primaryAddress)
            )
        }

        updatedContact = updatedContact.pruneAndLimit()
        contactRepository.updateContact(updatedContact)

        Log.d(TAG, "Synced mDNS: ${contact.name} with ${addresses.size} addresses")
    }

    private suspend fun onNetworkTypeChanged(newType: NetworkType) {
        Log.d(TAG, "Network type changed to: ${newType.displayName}")

        // When network type changes, we could optionally:
        // 1. Re-prioritize addresses in contacts
        // 2. Deactivate addresses for the old network type
        // 3. Trigger a beacon broadcast

        // For now, just log the change
        // The Connector will handle prioritization at connection time
    }

    /**
     * Clean address by removing port and brackets.
     */
    private fun cleanAddress(address: String): String {
        return address
            .removePrefix("[")
            .substringBefore("]:")
            .substringBefore(":")
            .removeSuffix("]")
    }

    /**
     * Prune stale addresses and limit to max count.
     */
    private fun com.doodlelabs.meshriderwave.domain.model.Contact.pruneAndLimit(): com.doodlelabs.meshriderwave.domain.model.Contact {
        val pruned = this.pruneStaleAddresses(PRUNE_AGE_MS)

        // Limit to max addresses (keep most reliable)
        if (pruned.addressRegistry.size > MAX_ADDRESSES_PER_CONTACT) {
            val limited = pruned.addressRegistry
                .sortedByDescending { it.reliability }
                .take(MAX_ADDRESSES_PER_CONTACT)
            return pruned.copy(addressRegistry = limited)
        }

        return pruned
    }
}
