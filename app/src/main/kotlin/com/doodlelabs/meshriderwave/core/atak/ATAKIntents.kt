/*
 * Mesh Rider Wave - ATAK Integration Intent Constants
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 *
 * Defines all intent actions, extras, and helper functions for ATAK integration.
 * This file provides a contract for communication between ATAK plugins and MR Wave.
 *
 * Integration Architecture:
 * ┌─────────────────────────────────────────────────────────────────────────────┐
 * │                              ATAK                                            │
 * │  ┌─────────────────┐    ┌─────────────────┐    ┌─────────────────────────┐  │
 * │  │  ATAK Plugin    │───▶│  sendBroadcast  │───▶│  Intent (PTT_START)     │  │
 * │  │  (TAK Server)   │    │                 │    │                         │  │
 * │  └─────────────────┘    └─────────────────┘    └───────────┬─────────────┘  │
 * └────────────────────────────────────────────────────────────┼────────────────┘
 *                                                              │
 *                                                              ▼
 * ┌─────────────────────────────────────────────────────────────────────────────┐
 * │                           MESH RIDER WAVE                                    │
 * │  ┌─────────────────────────┐    ┌─────────────────┐    ┌─────────────────┐  │
 * │  │  ATAKBridge             │───▶│  PTTManager     │───▶│  Audio          │  │
 * │  │  (BroadcastReceiver)    │    │  (Floor Ctrl)   │    │  Transmission   │  │
 * │  └───────────┬─────────────┘    └─────────────────┘    └─────────────────┘  │
 * │              │                                                               │
 * │              ▼                                                               │
 * │  ┌─────────────────────────┐                                                │
 * │  │  sendBroadcast          │───▶ Intent (PTT_STATUS, PEER_UPDATE)           │
 * │  │  (Back to ATAK)         │                                                │
 * │  └─────────────────────────┘                                                │
 * └─────────────────────────────────────────────────────────────────────────────┘
 *
 * Security: All intents use signature-level permissions to prevent spoofing.
 */

package com.doodlelabs.meshriderwave.core.atak

import android.content.Context
import android.content.Intent
import android.os.Bundle
import org.json.JSONArray
import org.json.JSONObject

/**
 * ATAK Integration Intent Constants
 *
 * This object provides all intent actions and extras for bidirectional
 * communication between ATAK (Android Tactical Assault Kit) and MeshRider Wave.
 *
 * ## Usage from ATAK Plugin
 *
 * ```kotlin
 * // Start PTT transmission
 * val intent = Intent(ATAKIntents.ACTION_PTT_START).apply {
 *     putExtra(ATAKIntents.EXTRA_CHANNEL_ID, "channel123")
 *     putExtra(ATAKIntents.EXTRA_PRIORITY, ATAKIntents.PRIORITY_NORMAL)
 * }
 * sendBroadcast(intent)
 *
 * // Listen for PTT status updates
 * registerReceiver(receiver, IntentFilter(ATAKIntents.ACTION_PTT_STATUS))
 * ```
 *
 * ## Permissions
 *
 * All intents require signature-level permission:
 * - `com.doodlelabs.meshriderwave.permission.ATAK_BRIDGE`
 *
 * @since 1.0.0
 * @author Jabbir Basha P
 */
object ATAKIntents {

    // =========================================================================
    // PACKAGE & PERMISSION
    // =========================================================================

    /**
     * MeshRider Wave package name
     */
    const val PACKAGE_NAME = "com.doodlelabs.meshriderwave"

    /**
     * Permission required for ATAK bridge communication.
     * This is a signature-level permission, meaning only apps signed with
     * the same certificate can send/receive these intents.
     */
    const val PERMISSION_ATAK_BRIDGE = "$PACKAGE_NAME.permission.ATAK_BRIDGE"

    // =========================================================================
    // INBOUND ACTIONS (ATAK → MeshRider Wave)
    // =========================================================================

    /**
     * Start PTT transmission on specified channel.
     *
     * Extras:
     * - [EXTRA_CHANNEL_ID] (required): Target channel ID
     * - [EXTRA_PRIORITY] (optional): Transmission priority (default: NORMAL)
     * - [EXTRA_EMERGENCY] (optional): Emergency broadcast flag (default: false)
     * - [EXTRA_CALLER_UID] (optional): Calling app UID for verification
     *
     * Response: [ACTION_PTT_STATUS] with floor grant/deny result
     */
    const val ACTION_PTT_START = "$PACKAGE_NAME.action.PTT_START"

    /**
     * Stop PTT transmission.
     *
     * Extras:
     * - [EXTRA_CHANNEL_ID] (optional): Specific channel, or current active channel
     *
     * Response: [ACTION_PTT_STATUS] with IDLE status
     */
    const val ACTION_PTT_STOP = "$PACKAGE_NAME.action.PTT_STOP"

    /**
     * Request list of available PTT channels.
     *
     * Extras: None required
     *
     * Response: [ACTION_CHANNEL_LIST] with available channels
     */
    const val ACTION_GET_CHANNELS = "$PACKAGE_NAME.action.GET_CHANNELS"

    /**
     * Join a PTT channel.
     *
     * Extras:
     * - [EXTRA_CHANNEL_ID] (required): Channel to join
     * - [EXTRA_CHANNEL_NAME] (optional): Display name for new channels
     *
     * Response: [ACTION_CHANNEL_JOINED] on success
     */
    const val ACTION_JOIN_CHANNEL = "$PACKAGE_NAME.action.JOIN_CHANNEL"

    /**
     * Leave a PTT channel.
     *
     * Extras:
     * - [EXTRA_CHANNEL_ID] (required): Channel to leave
     *
     * Response: [ACTION_CHANNEL_LEFT] on success
     */
    const val ACTION_LEAVE_CHANNEL = "$PACKAGE_NAME.action.LEAVE_CHANNEL"

    /**
     * Set the active channel for transmission.
     *
     * Extras:
     * - [EXTRA_CHANNEL_ID] (required): Channel to activate
     *
     * Response: [ACTION_ACTIVE_CHANNEL_CHANGED]
     */
    const val ACTION_SET_ACTIVE_CHANNEL = "$PACKAGE_NAME.action.SET_ACTIVE_CHANNEL"

    /**
     * Request list of discovered peers on the mesh network.
     *
     * Extras:
     * - [EXTRA_CHANNEL_ID] (optional): Filter by channel, or all peers
     *
     * Response: [ACTION_PEER_UPDATE] with peer list
     */
    const val ACTION_GET_PEERS = "$PACKAGE_NAME.action.GET_PEERS"

    /**
     * Request current PTT status.
     *
     * Extras:
     * - [EXTRA_CHANNEL_ID] (optional): Specific channel, or active channel
     *
     * Response: [ACTION_PTT_STATUS]
     */
    const val ACTION_GET_PTT_STATUS = "$PACKAGE_NAME.action.GET_PTT_STATUS"

    /**
     * Send SOS/Emergency broadcast.
     *
     * Extras:
     * - [EXTRA_CHANNEL_ID] (optional): Target channel, or broadcast to all
     * - [EXTRA_LOCATION_LAT] (optional): Latitude
     * - [EXTRA_LOCATION_LON] (optional): Longitude
     * - [EXTRA_SOS_MESSAGE] (optional): Text message
     *
     * Response: [ACTION_SOS_ACKNOWLEDGED]
     */
    const val ACTION_SEND_SOS = "$PACKAGE_NAME.action.SEND_SOS"

    /**
     * Cancel active SOS.
     *
     * Extras: None required
     *
     * Response: [ACTION_SOS_CANCELLED]
     */
    const val ACTION_CANCEL_SOS = "$PACKAGE_NAME.action.CANCEL_SOS"

    /**
     * Share location to channel members.
     *
     * Extras:
     * - [EXTRA_CHANNEL_ID] (optional): Target channel
     * - [EXTRA_LOCATION_LAT] (required): Latitude
     * - [EXTRA_LOCATION_LON] (required): Longitude
     * - [EXTRA_LOCATION_ALT] (optional): Altitude in meters
     * - [EXTRA_LOCATION_ACCURACY] (optional): Accuracy in meters
     * - [EXTRA_CALLSIGN] (optional): Tactical callsign
     *
     * Response: [ACTION_LOCATION_SHARED]
     */
    const val ACTION_SHARE_LOCATION = "$PACKAGE_NAME.action.SHARE_LOCATION"

    /**
     * Request Blue Force Tracking data.
     *
     * Extras:
     * - [EXTRA_CHANNEL_ID] (optional): Filter by channel
     *
     * Response: [ACTION_BFT_UPDATE] with all tracked locations
     */
    const val ACTION_GET_BFT = "$PACKAGE_NAME.action.GET_BFT"

    /**
     * Configure MeshRider Wave settings from ATAK.
     *
     * Extras:
     * - [EXTRA_AUDIO_CODEC] (optional): Audio codec setting
     * - [EXTRA_BITRATE] (optional): Codec bitrate in bps
     * - [EXTRA_VOX_ENABLED] (optional): Voice-activated transmission
     * - [EXTRA_VOX_THRESHOLD] (optional): VOX sensitivity (0.0-1.0)
     *
     * Response: [ACTION_CONFIG_UPDATED]
     */
    const val ACTION_CONFIGURE = "$PACKAGE_NAME.action.CONFIGURE"

    // =========================================================================
    // OUTBOUND ACTIONS (MeshRider Wave → ATAK)
    // =========================================================================

    /**
     * PTT status update broadcast.
     *
     * Extras:
     * - [EXTRA_CHANNEL_ID]: Channel ID
     * - [EXTRA_PTT_STATE]: Current state (IDLE, TRANSMITTING, RECEIVING, etc.)
     * - [EXTRA_CURRENT_SPEAKER]: Public key of current speaker (if any)
     * - [EXTRA_CURRENT_SPEAKER_NAME]: Display name of speaker
     * - [EXTRA_FLOOR_GRANTED]: Whether floor was granted (for request responses)
     * - [EXTRA_FLOOR_DENIED_REASON]: Reason if floor denied
     * - [EXTRA_TRANSMISSION_DURATION]: Duration in milliseconds
     */
    const val ACTION_PTT_STATUS = "$PACKAGE_NAME.action.PTT_STATUS"

    /**
     * Peer discovery update broadcast.
     *
     * Extras:
     * - [EXTRA_PEER_LIST_JSON]: JSON array of discovered peers
     * - [EXTRA_CHANNEL_ID] (optional): Channel filter applied
     * - [EXTRA_PEER_COUNT]: Number of peers
     */
    const val ACTION_PEER_UPDATE = "$PACKAGE_NAME.action.PEER_UPDATE"

    /**
     * Channel list response.
     *
     * Extras:
     * - [EXTRA_CHANNEL_LIST_JSON]: JSON array of available channels
     * - [EXTRA_ACTIVE_CHANNEL_ID]: Currently active channel ID
     */
    const val ACTION_CHANNEL_LIST = "$PACKAGE_NAME.action.CHANNEL_LIST"

    /**
     * Channel joined confirmation.
     *
     * Extras:
     * - [EXTRA_CHANNEL_ID]: Joined channel ID
     * - [EXTRA_CHANNEL_NAME]: Channel display name
     * - [EXTRA_MEMBER_COUNT]: Number of members in channel
     */
    const val ACTION_CHANNEL_JOINED = "$PACKAGE_NAME.action.CHANNEL_JOINED"

    /**
     * Channel left confirmation.
     *
     * Extras:
     * - [EXTRA_CHANNEL_ID]: Left channel ID
     */
    const val ACTION_CHANNEL_LEFT = "$PACKAGE_NAME.action.CHANNEL_LEFT"

    /**
     * Active channel changed notification.
     *
     * Extras:
     * - [EXTRA_CHANNEL_ID]: New active channel ID
     * - [EXTRA_CHANNEL_NAME]: Channel display name
     */
    const val ACTION_ACTIVE_CHANNEL_CHANGED = "$PACKAGE_NAME.action.ACTIVE_CHANNEL_CHANGED"

    /**
     * SOS acknowledged by peers.
     *
     * Extras:
     * - [EXTRA_SOS_ID]: Unique SOS identifier
     * - [EXTRA_ACKNOWLEDGED_BY]: JSON array of acknowledging peers
     */
    const val ACTION_SOS_ACKNOWLEDGED = "$PACKAGE_NAME.action.SOS_ACKNOWLEDGED"

    /**
     * SOS cancelled.
     *
     * Extras:
     * - [EXTRA_SOS_ID]: Cancelled SOS identifier
     */
    const val ACTION_SOS_CANCELLED = "$PACKAGE_NAME.action.SOS_CANCELLED"

    /**
     * SOS received from another user.
     *
     * Extras:
     * - [EXTRA_SOS_ID]: SOS identifier
     * - [EXTRA_SENDER_PUBLIC_KEY]: Sender's public key
     * - [EXTRA_SENDER_NAME]: Sender's display name
     * - [EXTRA_LOCATION_LAT]: Latitude (if provided)
     * - [EXTRA_LOCATION_LON]: Longitude (if provided)
     * - [EXTRA_SOS_MESSAGE]: Message (if provided)
     * - [EXTRA_TIMESTAMP]: Unix timestamp
     */
    const val ACTION_SOS_RECEIVED = "$PACKAGE_NAME.action.SOS_RECEIVED"

    /**
     * Location shared confirmation.
     *
     * Extras:
     * - [EXTRA_SUCCESS]: Whether share was successful
     */
    const val ACTION_LOCATION_SHARED = "$PACKAGE_NAME.action.LOCATION_SHARED"

    /**
     * Blue Force Tracking update.
     *
     * Extras:
     * - [EXTRA_BFT_DATA_JSON]: JSON array of tracked positions
     * - [EXTRA_TIMESTAMP]: Update timestamp
     */
    const val ACTION_BFT_UPDATE = "$PACKAGE_NAME.action.BFT_UPDATE"

    /**
     * Configuration updated confirmation.
     *
     * Extras:
     * - [EXTRA_SUCCESS]: Whether update was successful
     * - [EXTRA_ERROR_MESSAGE]: Error message if failed
     */
    const val ACTION_CONFIG_UPDATED = "$PACKAGE_NAME.action.CONFIG_UPDATED"

    /**
     * MeshRider Wave service started.
     *
     * Extras:
     * - [EXTRA_VERSION]: App version string
     * - [EXTRA_SERVICE_RUNNING]: Always true
     */
    const val ACTION_SERVICE_STARTED = "$PACKAGE_NAME.action.SERVICE_STARTED"

    /**
     * MeshRider Wave service stopped.
     *
     * Extras:
     * - [EXTRA_SERVICE_RUNNING]: Always false
     */
    const val ACTION_SERVICE_STOPPED = "$PACKAGE_NAME.action.SERVICE_STOPPED"

    /**
     * Incoming transmission notification.
     * Broadcast when another user starts transmitting.
     *
     * Extras:
     * - [EXTRA_CHANNEL_ID]: Channel ID
     * - [EXTRA_CURRENT_SPEAKER]: Speaker's public key
     * - [EXTRA_CURRENT_SPEAKER_NAME]: Speaker's display name
     * - [EXTRA_EMERGENCY]: Whether this is an emergency transmission
     */
    const val ACTION_TRANSMISSION_START = "$PACKAGE_NAME.action.TRANSMISSION_START"

    /**
     * Transmission ended notification.
     *
     * Extras:
     * - [EXTRA_CHANNEL_ID]: Channel ID
     * - [EXTRA_TRANSMISSION_DURATION]: Duration in milliseconds
     */
    const val ACTION_TRANSMISSION_END = "$PACKAGE_NAME.action.TRANSMISSION_END"

    // =========================================================================
    // EXTRA KEYS
    // =========================================================================

    // Channel & PTT
    const val EXTRA_CHANNEL_ID = "channel_id"
    const val EXTRA_CHANNEL_NAME = "channel_name"
    const val EXTRA_CHANNEL_LIST_JSON = "channel_list_json"
    const val EXTRA_ACTIVE_CHANNEL_ID = "active_channel_id"
    const val EXTRA_MEMBER_COUNT = "member_count"
    const val EXTRA_PRIORITY = "priority"
    const val EXTRA_EMERGENCY = "emergency"
    const val EXTRA_PTT_STATE = "ptt_state"
    const val EXTRA_FLOOR_GRANTED = "floor_granted"
    const val EXTRA_FLOOR_DENIED_REASON = "floor_denied_reason"
    const val EXTRA_TRANSMISSION_DURATION = "transmission_duration"

    // Speaker
    const val EXTRA_CURRENT_SPEAKER = "current_speaker"
    const val EXTRA_CURRENT_SPEAKER_NAME = "current_speaker_name"
    const val EXTRA_SENDER_PUBLIC_KEY = "sender_public_key"
    const val EXTRA_SENDER_NAME = "sender_name"

    // Peers
    const val EXTRA_PEER_LIST_JSON = "peer_list_json"
    const val EXTRA_PEER_COUNT = "peer_count"

    // Location / BFT
    const val EXTRA_LOCATION_LAT = "location_lat"
    const val EXTRA_LOCATION_LON = "location_lon"
    const val EXTRA_LOCATION_ALT = "location_alt"
    const val EXTRA_LOCATION_ACCURACY = "location_accuracy"
    const val EXTRA_CALLSIGN = "callsign"
    const val EXTRA_BFT_DATA_JSON = "bft_data_json"

    // SOS
    const val EXTRA_SOS_ID = "sos_id"
    const val EXTRA_SOS_MESSAGE = "sos_message"
    const val EXTRA_ACKNOWLEDGED_BY = "acknowledged_by"

    // Configuration
    const val EXTRA_AUDIO_CODEC = "audio_codec"
    const val EXTRA_BITRATE = "bitrate"
    const val EXTRA_VOX_ENABLED = "vox_enabled"
    const val EXTRA_VOX_THRESHOLD = "vox_threshold"

    // General
    const val EXTRA_SUCCESS = "success"
    const val EXTRA_ERROR_MESSAGE = "error_message"
    const val EXTRA_TIMESTAMP = "timestamp"
    const val EXTRA_VERSION = "version"
    const val EXTRA_SERVICE_RUNNING = "service_running"
    const val EXTRA_CALLER_UID = "caller_uid"

    // =========================================================================
    // PRIORITY CONSTANTS
    // =========================================================================

    const val PRIORITY_LOW = 0
    const val PRIORITY_NORMAL = 1
    const val PRIORITY_HIGH = 2
    const val PRIORITY_EMERGENCY = 3

    // =========================================================================
    // PTT STATE CONSTANTS
    // =========================================================================

    const val PTT_STATE_IDLE = "IDLE"
    const val PTT_STATE_REQUESTING = "REQUESTING"
    const val PTT_STATE_TRANSMITTING = "TRANSMITTING"
    const val PTT_STATE_RECEIVING = "RECEIVING"
    const val PTT_STATE_QUEUED = "QUEUED"
    const val PTT_STATE_COOLDOWN = "COOLDOWN"
    const val PTT_STATE_BLOCKED = "BLOCKED"

    // =========================================================================
    // AUDIO CODEC CONSTANTS
    // =========================================================================

    const val CODEC_OPUS_HIGH = "OPUS_24K"
    const val CODEC_OPUS_VOICE = "OPUS_6K"
    const val CODEC_CODEC2_3200 = "CODEC2_3200"
    const val CODEC_CODEC2_1200 = "CODEC2_1200"
    const val CODEC_CODEC2_700 = "CODEC2_700"

    // =========================================================================
    // HELPER FUNCTIONS - Intent Builders
    // =========================================================================

    /**
     * Create PTT start intent.
     *
     * @param channelId Target channel ID
     * @param priority Transmission priority (default: [PRIORITY_NORMAL])
     * @param emergency Emergency broadcast flag (default: false)
     * @return Intent ready to broadcast
     */
    @JvmStatic
    fun createPttStartIntent(
        channelId: String,
        priority: Int = PRIORITY_NORMAL,
        emergency: Boolean = false
    ): Intent {
        return Intent(ACTION_PTT_START).apply {
            setPackage(PACKAGE_NAME)
            putExtra(EXTRA_CHANNEL_ID, channelId)
            putExtra(EXTRA_PRIORITY, priority)
            putExtra(EXTRA_EMERGENCY, emergency)
            putExtra(EXTRA_TIMESTAMP, System.currentTimeMillis())
        }
    }

    /**
     * Create PTT stop intent.
     *
     * @param channelId Optional channel ID (null = active channel)
     * @return Intent ready to broadcast
     */
    @JvmStatic
    fun createPttStopIntent(channelId: String? = null): Intent {
        return Intent(ACTION_PTT_STOP).apply {
            setPackage(PACKAGE_NAME)
            channelId?.let { putExtra(EXTRA_CHANNEL_ID, it) }
            putExtra(EXTRA_TIMESTAMP, System.currentTimeMillis())
        }
    }

    /**
     * Create get channels intent.
     *
     * @return Intent ready to broadcast
     */
    @JvmStatic
    fun createGetChannelsIntent(): Intent {
        return Intent(ACTION_GET_CHANNELS).apply {
            setPackage(PACKAGE_NAME)
        }
    }

    /**
     * Create join channel intent.
     *
     * @param channelId Channel to join
     * @param channelName Optional display name
     * @return Intent ready to broadcast
     */
    @JvmStatic
    fun createJoinChannelIntent(
        channelId: String,
        channelName: String? = null
    ): Intent {
        return Intent(ACTION_JOIN_CHANNEL).apply {
            setPackage(PACKAGE_NAME)
            putExtra(EXTRA_CHANNEL_ID, channelId)
            channelName?.let { putExtra(EXTRA_CHANNEL_NAME, it) }
        }
    }

    /**
     * Create leave channel intent.
     *
     * @param channelId Channel to leave
     * @return Intent ready to broadcast
     */
    @JvmStatic
    fun createLeaveChannelIntent(channelId: String): Intent {
        return Intent(ACTION_LEAVE_CHANNEL).apply {
            setPackage(PACKAGE_NAME)
            putExtra(EXTRA_CHANNEL_ID, channelId)
        }
    }

    /**
     * Create set active channel intent.
     *
     * @param channelId Channel to activate
     * @return Intent ready to broadcast
     */
    @JvmStatic
    fun createSetActiveChannelIntent(channelId: String): Intent {
        return Intent(ACTION_SET_ACTIVE_CHANNEL).apply {
            setPackage(PACKAGE_NAME)
            putExtra(EXTRA_CHANNEL_ID, channelId)
        }
    }

    /**
     * Create get peers intent.
     *
     * @param channelId Optional channel filter
     * @return Intent ready to broadcast
     */
    @JvmStatic
    fun createGetPeersIntent(channelId: String? = null): Intent {
        return Intent(ACTION_GET_PEERS).apply {
            setPackage(PACKAGE_NAME)
            channelId?.let { putExtra(EXTRA_CHANNEL_ID, it) }
        }
    }

    /**
     * Create get PTT status intent.
     *
     * @param channelId Optional channel filter
     * @return Intent ready to broadcast
     */
    @JvmStatic
    fun createGetPttStatusIntent(channelId: String? = null): Intent {
        return Intent(ACTION_GET_PTT_STATUS).apply {
            setPackage(PACKAGE_NAME)
            channelId?.let { putExtra(EXTRA_CHANNEL_ID, it) }
        }
    }

    /**
     * Create SOS intent.
     *
     * @param channelId Optional target channel
     * @param latitude Optional latitude
     * @param longitude Optional longitude
     * @param message Optional SOS message
     * @return Intent ready to broadcast
     */
    @JvmStatic
    fun createSosIntent(
        channelId: String? = null,
        latitude: Double? = null,
        longitude: Double? = null,
        message: String? = null
    ): Intent {
        return Intent(ACTION_SEND_SOS).apply {
            setPackage(PACKAGE_NAME)
            channelId?.let { putExtra(EXTRA_CHANNEL_ID, it) }
            latitude?.let { putExtra(EXTRA_LOCATION_LAT, it) }
            longitude?.let { putExtra(EXTRA_LOCATION_LON, it) }
            message?.let { putExtra(EXTRA_SOS_MESSAGE, it) }
            putExtra(EXTRA_TIMESTAMP, System.currentTimeMillis())
        }
    }

    /**
     * Create cancel SOS intent.
     *
     * @return Intent ready to broadcast
     */
    @JvmStatic
    fun createCancelSosIntent(): Intent {
        return Intent(ACTION_CANCEL_SOS).apply {
            setPackage(PACKAGE_NAME)
        }
    }

    /**
     * Create share location intent.
     *
     * @param latitude Latitude
     * @param longitude Longitude
     * @param altitude Optional altitude in meters
     * @param accuracy Optional accuracy in meters
     * @param callsign Optional tactical callsign
     * @param channelId Optional target channel
     * @return Intent ready to broadcast
     */
    @JvmStatic
    fun createShareLocationIntent(
        latitude: Double,
        longitude: Double,
        altitude: Double? = null,
        accuracy: Float? = null,
        callsign: String? = null,
        channelId: String? = null
    ): Intent {
        return Intent(ACTION_SHARE_LOCATION).apply {
            setPackage(PACKAGE_NAME)
            putExtra(EXTRA_LOCATION_LAT, latitude)
            putExtra(EXTRA_LOCATION_LON, longitude)
            altitude?.let { putExtra(EXTRA_LOCATION_ALT, it) }
            accuracy?.let { putExtra(EXTRA_LOCATION_ACCURACY, it) }
            callsign?.let { putExtra(EXTRA_CALLSIGN, it) }
            channelId?.let { putExtra(EXTRA_CHANNEL_ID, it) }
            putExtra(EXTRA_TIMESTAMP, System.currentTimeMillis())
        }
    }

    /**
     * Create get BFT intent.
     *
     * @param channelId Optional channel filter
     * @return Intent ready to broadcast
     */
    @JvmStatic
    fun createGetBftIntent(channelId: String? = null): Intent {
        return Intent(ACTION_GET_BFT).apply {
            setPackage(PACKAGE_NAME)
            channelId?.let { putExtra(EXTRA_CHANNEL_ID, it) }
        }
    }

    /**
     * Create configure intent.
     *
     * @param audioCodec Optional audio codec
     * @param bitrate Optional bitrate in bps
     * @param voxEnabled Optional VOX enable flag
     * @param voxThreshold Optional VOX threshold (0.0-1.0)
     * @return Intent ready to broadcast
     */
    @JvmStatic
    fun createConfigureIntent(
        audioCodec: String? = null,
        bitrate: Int? = null,
        voxEnabled: Boolean? = null,
        voxThreshold: Float? = null
    ): Intent {
        return Intent(ACTION_CONFIGURE).apply {
            setPackage(PACKAGE_NAME)
            audioCodec?.let { putExtra(EXTRA_AUDIO_CODEC, it) }
            bitrate?.let { putExtra(EXTRA_BITRATE, it) }
            voxEnabled?.let { putExtra(EXTRA_VOX_ENABLED, it) }
            voxThreshold?.let { putExtra(EXTRA_VOX_THRESHOLD, it) }
        }
    }

    // =========================================================================
    // HELPER FUNCTIONS - Response Builders (for ATAKBridge)
    // =========================================================================

    /**
     * Create PTT status response intent.
     *
     * @param channelId Channel ID
     * @param state PTT state (use PTT_STATE_* constants)
     * @param floorGranted Whether floor was granted (for request responses)
     * @param floorDeniedReason Reason if denied
     * @param currentSpeaker Current speaker's public key
     * @param currentSpeakerName Current speaker's name
     * @param transmissionDuration Duration in milliseconds
     * @return Intent ready to broadcast
     */
    @JvmStatic
    fun createPttStatusResponse(
        channelId: String,
        state: String,
        floorGranted: Boolean? = null,
        floorDeniedReason: String? = null,
        currentSpeaker: String? = null,
        currentSpeakerName: String? = null,
        transmissionDuration: Long? = null
    ): Intent {
        return Intent(ACTION_PTT_STATUS).apply {
            putExtra(EXTRA_CHANNEL_ID, channelId)
            putExtra(EXTRA_PTT_STATE, state)
            floorGranted?.let { putExtra(EXTRA_FLOOR_GRANTED, it) }
            floorDeniedReason?.let { putExtra(EXTRA_FLOOR_DENIED_REASON, it) }
            currentSpeaker?.let { putExtra(EXTRA_CURRENT_SPEAKER, it) }
            currentSpeakerName?.let { putExtra(EXTRA_CURRENT_SPEAKER_NAME, it) }
            transmissionDuration?.let { putExtra(EXTRA_TRANSMISSION_DURATION, it) }
            putExtra(EXTRA_TIMESTAMP, System.currentTimeMillis())
        }
    }

    /**
     * Create peer update response intent.
     *
     * @param peers List of peer data as JSON array
     * @param channelId Optional channel filter applied
     * @return Intent ready to broadcast
     */
    @JvmStatic
    fun createPeerUpdateResponse(
        peers: JSONArray,
        channelId: String? = null
    ): Intent {
        return Intent(ACTION_PEER_UPDATE).apply {
            putExtra(EXTRA_PEER_LIST_JSON, peers.toString())
            putExtra(EXTRA_PEER_COUNT, peers.length())
            channelId?.let { putExtra(EXTRA_CHANNEL_ID, it) }
            putExtra(EXTRA_TIMESTAMP, System.currentTimeMillis())
        }
    }

    /**
     * Create channel list response intent.
     *
     * @param channels JSON array of channels
     * @param activeChannelId Currently active channel
     * @return Intent ready to broadcast
     */
    @JvmStatic
    fun createChannelListResponse(
        channels: JSONArray,
        activeChannelId: String?
    ): Intent {
        return Intent(ACTION_CHANNEL_LIST).apply {
            putExtra(EXTRA_CHANNEL_LIST_JSON, channels.toString())
            activeChannelId?.let { putExtra(EXTRA_ACTIVE_CHANNEL_ID, it) }
            putExtra(EXTRA_TIMESTAMP, System.currentTimeMillis())
        }
    }

    /**
     * Create BFT update response intent.
     *
     * @param bftData JSON array of tracked positions
     * @return Intent ready to broadcast
     */
    @JvmStatic
    fun createBftUpdateResponse(bftData: JSONArray): Intent {
        return Intent(ACTION_BFT_UPDATE).apply {
            putExtra(EXTRA_BFT_DATA_JSON, bftData.toString())
            putExtra(EXTRA_TIMESTAMP, System.currentTimeMillis())
        }
    }

    /**
     * Create SOS received broadcast intent.
     *
     * @param sosId SOS identifier
     * @param senderPublicKey Sender's public key
     * @param senderName Sender's display name
     * @param latitude Optional latitude
     * @param longitude Optional longitude
     * @param message Optional SOS message
     * @return Intent ready to broadcast
     */
    @JvmStatic
    fun createSosReceivedBroadcast(
        sosId: String,
        senderPublicKey: String,
        senderName: String,
        latitude: Double? = null,
        longitude: Double? = null,
        message: String? = null
    ): Intent {
        return Intent(ACTION_SOS_RECEIVED).apply {
            putExtra(EXTRA_SOS_ID, sosId)
            putExtra(EXTRA_SENDER_PUBLIC_KEY, senderPublicKey)
            putExtra(EXTRA_SENDER_NAME, senderName)
            latitude?.let { putExtra(EXTRA_LOCATION_LAT, it) }
            longitude?.let { putExtra(EXTRA_LOCATION_LON, it) }
            message?.let { putExtra(EXTRA_SOS_MESSAGE, it) }
            putExtra(EXTRA_TIMESTAMP, System.currentTimeMillis())
        }
    }

    /**
     * Create transmission start broadcast intent.
     *
     * @param channelId Channel ID
     * @param speakerPublicKey Speaker's public key
     * @param speakerName Speaker's display name
     * @param emergency Whether this is an emergency transmission
     * @return Intent ready to broadcast
     */
    @JvmStatic
    fun createTransmissionStartBroadcast(
        channelId: String,
        speakerPublicKey: String,
        speakerName: String,
        emergency: Boolean = false
    ): Intent {
        return Intent(ACTION_TRANSMISSION_START).apply {
            putExtra(EXTRA_CHANNEL_ID, channelId)
            putExtra(EXTRA_CURRENT_SPEAKER, speakerPublicKey)
            putExtra(EXTRA_CURRENT_SPEAKER_NAME, speakerName)
            putExtra(EXTRA_EMERGENCY, emergency)
            putExtra(EXTRA_TIMESTAMP, System.currentTimeMillis())
        }
    }

    /**
     * Create transmission end broadcast intent.
     *
     * @param channelId Channel ID
     * @param durationMs Transmission duration in milliseconds
     * @return Intent ready to broadcast
     */
    @JvmStatic
    fun createTransmissionEndBroadcast(
        channelId: String,
        durationMs: Long
    ): Intent {
        return Intent(ACTION_TRANSMISSION_END).apply {
            putExtra(EXTRA_CHANNEL_ID, channelId)
            putExtra(EXTRA_TRANSMISSION_DURATION, durationMs)
            putExtra(EXTRA_TIMESTAMP, System.currentTimeMillis())
        }
    }

    /**
     * Create service started broadcast intent.
     *
     * @param version App version string
     * @return Intent ready to broadcast
     */
    @JvmStatic
    fun createServiceStartedBroadcast(version: String): Intent {
        return Intent(ACTION_SERVICE_STARTED).apply {
            putExtra(EXTRA_VERSION, version)
            putExtra(EXTRA_SERVICE_RUNNING, true)
            putExtra(EXTRA_TIMESTAMP, System.currentTimeMillis())
        }
    }

    /**
     * Create service stopped broadcast intent.
     *
     * @return Intent ready to broadcast
     */
    @JvmStatic
    fun createServiceStoppedBroadcast(): Intent {
        return Intent(ACTION_SERVICE_STOPPED).apply {
            putExtra(EXTRA_SERVICE_RUNNING, false)
            putExtra(EXTRA_TIMESTAMP, System.currentTimeMillis())
        }
    }

    // =========================================================================
    // HELPER FUNCTIONS - Parsing
    // =========================================================================

    /**
     * Parse peer list JSON from intent.
     *
     * @param intent Intent with EXTRA_PEER_LIST_JSON
     * @return List of peer JSONObjects, or empty list
     */
    @JvmStatic
    fun parsePeerList(intent: Intent): List<JSONObject> {
        val json = intent.getStringExtra(EXTRA_PEER_LIST_JSON) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { array.getJSONObject(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Parse channel list JSON from intent.
     *
     * @param intent Intent with EXTRA_CHANNEL_LIST_JSON
     * @return List of channel JSONObjects, or empty list
     */
    @JvmStatic
    fun parseChannelList(intent: Intent): List<JSONObject> {
        val json = intent.getStringExtra(EXTRA_CHANNEL_LIST_JSON) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { array.getJSONObject(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Parse BFT data JSON from intent.
     *
     * @param intent Intent with EXTRA_BFT_DATA_JSON
     * @return List of position JSONObjects, or empty list
     */
    @JvmStatic
    fun parseBftData(intent: Intent): List<JSONObject> {
        val json = intent.getStringExtra(EXTRA_BFT_DATA_JSON) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { array.getJSONObject(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
