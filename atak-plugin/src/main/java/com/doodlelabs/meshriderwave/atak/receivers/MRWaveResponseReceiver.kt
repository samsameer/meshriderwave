/**
 * MR Wave Response Receiver
 *
 * Handles responses and state updates from the MR Wave app.
 * This receiver processes:
 * - Status updates (connection state, current channel)
 * - PTT state changes (transmitting/idle)
 * - Channel list responses
 * - Error notifications
 *
 * Copyright (C) 2024-2026 DoodleLabs. All Rights Reserved.
 */
package com.doodlelabs.meshriderwave.atak.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.doodlelabs.meshriderwave.atak.MRWavePlugin
import org.json.JSONArray
import org.json.JSONObject

class MRWaveResponseReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "MRWaveResponseReceiver"

        // Response Actions
        const val ACTION_RESPONSE = "com.doodlelabs.meshriderwave.atak.RESPONSE"
        const val ACTION_PTT_STATE_CHANGED = "com.doodlelabs.meshriderwave.atak.PTT_STATE_CHANGED"
        const val ACTION_CHANNEL_CHANGED = "com.doodlelabs.meshriderwave.atak.CHANNEL_CHANGED"

        // Extras
        const val EXTRA_SUCCESS = "success"
        const val EXTRA_ERROR_MESSAGE = "error_message"
        const val EXTRA_RESPONSE_TYPE = "response_type"
        const val EXTRA_DATA = "data"

        // Response Types
        const val RESPONSE_STATUS = "status"
        const val RESPONSE_CHANNELS = "channels"
        const val RESPONSE_ERROR = "error"

        // Status Extras
        const val EXTRA_IS_CONNECTED = "is_connected"
        const val EXTRA_IS_PTT_ACTIVE = "is_ptt_active"
        const val EXTRA_CHANNEL_ID = "channel_id"
        const val EXTRA_CHANNEL_NAME = "channel_name"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "onReceive: ${intent.action}")

        val plugin = MRWavePlugin.getInstance()
        if (plugin == null) {
            Log.w(TAG, "onReceive: Plugin not initialized")
            return
        }

        when (intent.action) {
            ACTION_RESPONSE -> handleResponse(plugin, intent)
            ACTION_PTT_STATE_CHANGED -> handlePTTStateChanged(plugin, intent)
            ACTION_CHANNEL_CHANGED -> handleChannelChanged(plugin, intent)
        }
    }

    /**
     * Handle general response from MR Wave app.
     */
    private fun handleResponse(plugin: MRWavePlugin, intent: Intent) {
        val success = intent.getBooleanExtra(EXTRA_SUCCESS, false)
        val responseType = intent.getStringExtra(EXTRA_RESPONSE_TYPE)

        Log.d(TAG, "handleResponse: type=$responseType, success=$success")

        if (!success) {
            val errorMessage = intent.getStringExtra(EXTRA_ERROR_MESSAGE)
            Log.e(TAG, "handleResponse: Error - $errorMessage")
            return
        }

        when (responseType) {
            RESPONSE_STATUS -> handleStatusResponse(plugin, intent)
            RESPONSE_CHANNELS -> handleChannelsResponse(plugin, intent)
            else -> Log.w(TAG, "handleResponse: Unknown response type: $responseType")
        }
    }

    /**
     * Handle status response from MR Wave app.
     */
    private fun handleStatusResponse(plugin: MRWavePlugin, intent: Intent) {
        val isConnected = intent.getBooleanExtra(EXTRA_IS_CONNECTED, false)
        val isPTTActive = intent.getBooleanExtra(EXTRA_IS_PTT_ACTIVE, false)
        val channelId = intent.getStringExtra(EXTRA_CHANNEL_ID)
        val channelName = intent.getStringExtra(EXTRA_CHANNEL_NAME)

        Log.d(TAG, "handleStatusResponse: connected=$isConnected, ptt=$isPTTActive, channel=$channelName")

        plugin.handleStatusUpdate(isConnected, isPTTActive, channelId, channelName)
    }

    /**
     * Handle channels list response from MR Wave app.
     */
    private fun handleChannelsResponse(plugin: MRWavePlugin, intent: Intent) {
        val dataJson = intent.getStringExtra(EXTRA_DATA) ?: return

        try {
            val channels = JSONArray(dataJson)
            val channelList = mutableListOf<ChannelInfo>()

            for (i in 0 until channels.length()) {
                val channel = channels.getJSONObject(i)
                channelList.add(
                    ChannelInfo(
                        id = channel.getString("id"),
                        name = channel.getString("name"),
                        isActive = channel.optBoolean("is_active", false),
                        memberCount = channel.optInt("member_count", 0)
                    )
                )
            }

            Log.d(TAG, "handleChannelsResponse: ${channelList.size} channels received")

            // TODO: Show channel selector dropdown with this list
            showChannelSelector(plugin, channelList)
        } catch (e: Exception) {
            Log.e(TAG, "handleChannelsResponse: Failed to parse channels", e)
        }
    }

    /**
     * Handle PTT state change notification.
     */
    private fun handlePTTStateChanged(plugin: MRWavePlugin, intent: Intent) {
        val isPTTActive = intent.getBooleanExtra(EXTRA_IS_PTT_ACTIVE, false)
        Log.d(TAG, "handlePTTStateChanged: $isPTTActive")
        plugin.handlePTTStateChanged(isPTTActive)
    }

    /**
     * Handle channel change notification.
     */
    private fun handleChannelChanged(plugin: MRWavePlugin, intent: Intent) {
        val channelId = intent.getStringExtra(EXTRA_CHANNEL_ID) ?: return
        val channelName = intent.getStringExtra(EXTRA_CHANNEL_NAME) ?: return
        Log.d(TAG, "handleChannelChanged: $channelName ($channelId)")
        plugin.handleChannelChanged(channelId, channelName)
    }

    /**
     * Show channel selector UI.
     */
    private fun showChannelSelector(plugin: MRWavePlugin, channels: List<ChannelInfo>) {
        val context = plugin.getContext() ?: return
        val activity = plugin.getActivity() ?: return

        // TODO: Implement ATAK dropdown for channel selection
        // This would use ATAK's DropDownReceiver to show a sliding panel
        Log.d(TAG, "showChannelSelector: Would show ${channels.size} channels")
    }

    /**
     * Channel information data class.
     */
    data class ChannelInfo(
        val id: String,
        val name: String,
        val isActive: Boolean,
        val memberCount: Int
    )
}
