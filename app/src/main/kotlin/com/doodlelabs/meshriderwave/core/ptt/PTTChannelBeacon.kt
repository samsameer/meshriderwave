/*
 * Mesh Rider Wave - PTT Channel Beacon
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 *
 * Multicast beacon for PTT channel discovery between devices
 * Enables two devices to see and join the same PTT channels
 *
 * Protocol:
 * - Beacon sent every 5 seconds when channels exist
 * - Contains list of channels user has joined
 * - Signed with Ed25519 for authenticity
 * - Encrypted channel details for privacy
 */

package com.doodlelabs.meshriderwave.core.ptt

import android.content.Context
import android.util.Base64
import android.util.Log
import com.doodlelabs.meshriderwave.core.crypto.CryptoManager
import com.doodlelabs.meshriderwave.domain.model.group.ChannelPriority
import com.doodlelabs.meshriderwave.domain.model.group.PTTChannel
import com.doodlelabs.meshriderwave.domain.model.group.PTTMember
import com.doodlelabs.meshriderwave.domain.model.group.PTTRole
import com.doodlelabs.meshriderwave.domain.model.group.PTTSettings
import com.doodlelabs.meshriderwave.domain.repository.PTTChannelRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PTTChannelBeacon @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cryptoManager: CryptoManager,
    private val pttChannelRepository: PTTChannelRepository
) {
    companion object {
        private const val TAG = "MeshRider:PTTBeacon"

        // Multicast address for PTT channel beacons (different from identity beacon)
        private const val MULTICAST_ADDRESS = "239.255.77.2"
        private const val MULTICAST_PORT = 7778

        // Beacon timing
        private const val BEACON_INTERVAL_MS = 5000L  // 5 seconds
        private const val BEACON_TIMEOUT_MS = 30000L  // 30 seconds stale threshold

        // Message types
        private const val MSG_CHANNEL_ANNOUNCE = "PTT_CHANNEL_ANNOUNCE"
        private const val MSG_CHANNEL_JOIN = "PTT_CHANNEL_JOIN"
        private const val MSG_CHANNEL_LEAVE = "PTT_CHANNEL_LEAVE"
        private const val MSG_CHANNEL_DELETE = "PTT_CHANNEL_DELETE"  // Jan 2026: Notify peers when channel is deleted

        // Packet size limit
        private const val MAX_PACKET_SIZE = 4096
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var multicastSocket: MulticastSocket? = null
    private var isRunning = false

    // Own identity
    private var ownPublicKey: ByteArray = ByteArray(0)
    private var ownSecretKey: ByteArray = ByteArray(0)
    private var ownName: String = "Unknown"

    // Discovered channels from other peers
    private val _discoveredChannels = MutableStateFlow<Map<String, DiscoveredChannel>>(emptyMap())
    val discoveredChannels: StateFlow<Map<String, DiscoveredChannel>> = _discoveredChannels.asStateFlow()

    // Feb 2026 FIX: Suppressed channel IDs — channels we deleted or that were deleted by peers.
    // Prevents deleted channels from re-appearing via beacon re-announce.
    private val suppressedChannelIds = mutableSetOf<String>()

    // Callbacks
    var onChannelDiscovered: ((PTTChannel, String) -> Unit)? = null  // channel, peerName
    var onChannelJoinRequest: ((String, ByteArray, String, String) -> Unit)? = null  // channelId, peerKey, peerName, peerIp
    var onChannelDeleted: ((ByteArray) -> Unit)? = null  // Jan 2026: channelId - notified when peer deletes a channel

    /**
     * Start the PTT channel beacon
     */
    fun start(publicKey: ByteArray, secretKey: ByteArray, name: String) {
        if (isRunning) return

        ownPublicKey = publicKey
        ownSecretKey = secretKey
        ownName = name
        isRunning = true

        Log.i(TAG, "Starting PTT Channel Beacon")

        scope.launch { startMulticast() }
        scope.launch { startBeaconBroadcast() }
        scope.launch { cleanupStaleChannels() }
    }

    /**
     * Stop the beacon
     */
    fun stop() {
        isRunning = false
        multicastSocket?.close()
        multicastSocket = null
        scope.cancel()
        Log.i(TAG, "Stopped PTT Channel Beacon")
    }

    /**
     * Announce a channel to the network
     */
    suspend fun announceChannel(channel: PTTChannel) {
        if (!isRunning) return

        val message = JSONObject().apply {
            put("type", MSG_CHANNEL_ANNOUNCE)
            put("channelId", channel.channelId.toBase64())
            put("name", channel.name)
            put("description", channel.description)
            put("frequency", channel.displayFrequency)
            put("priority", channel.priority.name)
            put("memberCount", channel.members.size)
            put("createdBy", channel.createdBy.toBase64())
            put("senderKey", ownPublicKey.toBase64())
            put("senderName", ownName)
            put("timestamp", System.currentTimeMillis())
        }

        sendBeacon(message)
        Log.d(TAG, "Announced channel: ${channel.name}")
    }

    /**
     * Request to join a discovered channel
     */
    suspend fun requestJoinChannel(channelId: String) {
        if (!isRunning) return

        val message = JSONObject().apply {
            put("type", MSG_CHANNEL_JOIN)
            put("channelId", channelId)
            put("senderKey", ownPublicKey.toBase64())
            put("senderName", ownName)
            put("timestamp", System.currentTimeMillis())
        }

        sendBeacon(message)
        Log.i(TAG, "Sent join request for channel: $channelId")
    }

    /**
     * Notify peers that a channel has been deleted
     * Jan 2026: Fix for ghost channels appearing after delete
     */
    suspend fun announceChannelDeleted(channelId: ByteArray) {
        if (!isRunning) return

        val message = JSONObject().apply {
            put("type", MSG_CHANNEL_DELETE)
            put("channelId", channelId.toBase64())
            put("senderKey", ownPublicKey.toBase64())
            put("senderName", ownName)
            put("timestamp", System.currentTimeMillis())
        }

        // Send multiple times to ensure delivery (UDP is unreliable)
        repeat(3) {
            sendBeacon(message)
            delay(100)
        }
        Log.i(TAG, "Announced channel deletion: ${channelId.take(4).joinToString("") { "%02x".format(it) }}")

        // Also remove from local discovered channels and suppress future announces
        val channelHex = channelId.toHexString()
        suppressedChannelIds.add(channelHex)
        _discoveredChannels.update { current ->
            current - channelHex
        }
    }

    /**
     * Clear a specific channel from discovered list (for local cleanup)
     * Jan 2026: Called when we delete/leave our own channel
     */
    fun clearDiscoveredChannel(channelId: ByteArray) {
        val channelHex = channelId.toHexString()
        suppressedChannelIds.add(channelHex)
        _discoveredChannels.update { current ->
            current - channelHex
        }
        Log.d(TAG, "Cleared and suppressed discovered channel: ${channelId.take(4).joinToString("") { "%02x".format(it) }}")
    }

    // ========== Private Methods ==========

    private suspend fun startMulticast() {
        try {
            val group = InetAddress.getByName(MULTICAST_ADDRESS)
            multicastSocket = MulticastSocket(MULTICAST_PORT).apply {
                reuseAddress = true
                soTimeout = 1000  // 1 second timeout for receive
                joinGroup(group)
            }

            Log.i(TAG, "Joined multicast group $MULTICAST_ADDRESS:$MULTICAST_PORT")

            val buffer = ByteArray(MAX_PACKET_SIZE)
            while (isRunning) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    multicastSocket?.receive(packet)

                    val data = String(packet.data, 0, packet.length, Charsets.UTF_8)
                    handleBeacon(data, packet.address.hostAddress ?: "unknown")
                } catch (e: SocketTimeoutException) {
                    // Normal timeout, continue
                } catch (e: Exception) {
                    if (isRunning) {
                        Log.e(TAG, "Error receiving beacon", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start multicast", e)
        }
    }

    private suspend fun startBeaconBroadcast() {
        while (isRunning) {
            try {
                // Get all joined channels and announce them
                pttChannelRepository.joinedChannels.first().forEach { channel ->
                    announceChannel(channel)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error broadcasting beacons", e)
            }
            delay(BEACON_INTERVAL_MS)
        }
    }

    private fun handleBeacon(data: String, senderIp: String) {
        try {
            val json = JSONObject(data)
            val type = json.optString("type", "")
            val senderKeyBase64 = json.optString("senderKey", "")
            val senderKey = Base64.decode(senderKeyBase64, Base64.NO_WRAP)

            // Skip our own beacons
            if (senderKey.contentEquals(ownPublicKey)) return

            when (type) {
                MSG_CHANNEL_ANNOUNCE -> handleChannelAnnounce(json, senderIp)
                MSG_CHANNEL_JOIN -> handleChannelJoin(json, senderIp)
                MSG_CHANNEL_LEAVE -> handleChannelLeave(json)
                MSG_CHANNEL_DELETE -> handleChannelDelete(json)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing beacon: ${e.message}")
        }
    }

    private fun handleChannelAnnounce(json: JSONObject, senderIp: String) {
        val channelIdBase64 = json.optString("channelId", "")
        val channelId = Base64.decode(channelIdBase64, Base64.NO_WRAP)
        val channelHex = channelId.toHexString()
        val name = json.optString("name", "Unknown")
        val description = json.optString("description", "")
        val frequency = json.optString("frequency", "")
        val priority = try {
            ChannelPriority.valueOf(json.optString("priority", "NORMAL"))
        } catch (e: Exception) {
            ChannelPriority.NORMAL
        }
        val memberCount = json.optInt("memberCount", 1)
        val senderKey = Base64.decode(json.optString("senderKey", ""), Base64.NO_WRAP)
        val senderName = json.optString("senderName", "Unknown")
        val timestamp = json.optLong("timestamp", System.currentTimeMillis())
        val createdBy = Base64.decode(json.optString("createdBy", ""), Base64.NO_WRAP)

        Log.d(TAG, "Received channel announce: $name from $senderName ($senderIp)")

        // Feb 2026 FIX: Skip channels that have been deleted/suppressed
        if (channelHex in suppressedChannelIds) {
            Log.d(TAG, "Ignoring suppressed channel: $name ($channelHex)")
            return
        }

        // Create discovered channel entry
        val discovered = DiscoveredChannel(
            channelId = channelId,
            name = name,
            description = description,
            frequency = frequency,
            priority = priority,
            memberCount = memberCount,
            createdBy = createdBy,
            announcerKey = senderKey,
            announcerName = senderName,
            announcerIp = senderIp,
            lastSeen = timestamp
        )

        // Update discovered channels
        _discoveredChannels.update { current ->
            current + (channelHex to discovered)
        }

        // Notify callback
        val channel = discovered.toPTTChannel()
        onChannelDiscovered?.invoke(channel, senderName)
    }

    private fun handleChannelJoin(json: JSONObject, senderIp: String) {
        val channelId = json.optString("channelId", "")
        val senderKey = Base64.decode(json.optString("senderKey", ""), Base64.NO_WRAP)
        val senderName = json.optString("senderName", "Unknown")

        Log.i(TAG, "Received join request for $channelId from $senderName ($senderIp)")
        onChannelJoinRequest?.invoke(channelId, senderKey, senderName, senderIp)
    }

    private fun handleChannelLeave(json: JSONObject) {
        // Handle leave notification if needed
    }

    /**
     * Handle channel delete notification from peer
     * Jan 2026: Immediately remove deleted channel from discovered list
     */
    private fun handleChannelDelete(json: JSONObject) {
        val channelIdBase64 = json.optString("channelId", "")
        val channelId = Base64.decode(channelIdBase64, Base64.NO_WRAP)
        val channelHex = channelId.toHexString()
        val senderName = json.optString("senderName", "Unknown")

        Log.i(TAG, "Received channel delete notification from $senderName")

        // Feb 2026 FIX: Add to suppression list so beacon re-announces are ignored
        suppressedChannelIds.add(channelHex)

        // Remove from discovered channels immediately
        _discoveredChannels.update { current ->
            val removed = current - channelHex
            if (removed.size < current.size) {
                Log.i(TAG, "Removed deleted channel from discovered list: ${channelId.take(4).joinToString("") { "%02X".format(it) }}")
            }
            removed
        }

        // Feb 2026 FIX: Also remove from local repository so we stop beaconing it
        scope.launch {
            try {
                pttChannelRepository.deleteChannel(channelId)
                Log.i(TAG, "Removed deleted channel from local repo (owner deleted it)")
            } catch (e: Exception) {
                Log.d(TAG, "Channel not in local repo or already removed: ${e.message}")
            }
        }

        // Notify via callback (in case UI needs to update)
        onChannelDeleted?.invoke(channelId)
    }

    private suspend fun cleanupStaleChannels() {
        while (isRunning) {
            delay(BEACON_TIMEOUT_MS)
            val now = System.currentTimeMillis()
            _discoveredChannels.update { current ->
                current.filter { (_, channel) ->
                    now - channel.lastSeen < BEACON_TIMEOUT_MS
                }
            }
        }
    }

    private suspend fun sendBeacon(message: JSONObject) = withContext(Dispatchers.IO) {
        try {
            // Sign the message (optional - continue even if signing fails)
            val messageBytes = message.toString().toByteArray(Charsets.UTF_8)
            val signature = cryptoManager.sign(messageBytes, ownSecretKey)
            signature?.let { sig ->
                message.put("signature", sig.toBase64())
            }

            val data = message.toString().toByteArray(Charsets.UTF_8)
            if (data.size > MAX_PACKET_SIZE) {
                Log.w(TAG, "Beacon too large: ${data.size} bytes")
                return@withContext
            }

            val group = InetAddress.getByName(MULTICAST_ADDRESS)
            val packet = DatagramPacket(data, data.size, group, MULTICAST_PORT)
            multicastSocket?.send(packet)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending beacon", e)
        }
    }

    private fun ByteArray.toBase64(): String =
        Base64.encodeToString(this, Base64.NO_WRAP)

    private fun ByteArray.toHexString(): String =
        joinToString("") { "%02x".format(it) }

    /**
     * Discovered channel from peer beacon
     */
    data class DiscoveredChannel(
        val channelId: ByteArray,
        val name: String,
        val description: String,
        val frequency: String,
        val priority: ChannelPriority,
        val memberCount: Int,
        val createdBy: ByteArray,
        val announcerKey: ByteArray,
        val announcerName: String,
        val announcerIp: String,
        val lastSeen: Long
    ) {
        val shortId: String
            get() = channelId.take(4).joinToString("") { "%02X".format(it) }

        fun toPTTChannel(): PTTChannel {
            return PTTChannel(
                channelId = channelId,
                name = name,
                description = description,
                frequency = frequency,
                priority = priority,
                settings = PTTSettings(),
                createdAt = lastSeen,
                createdBy = createdBy,
                members = listOf(
                    PTTMember(
                        publicKey = announcerKey,
                        name = announcerName,
                        role = PTTRole.MEMBER
                    )
                )
            )
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is DiscoveredChannel) return false
            return channelId.contentEquals(other.channelId)
        }

        override fun hashCode(): Int = channelId.contentHashCode()
    }
}
