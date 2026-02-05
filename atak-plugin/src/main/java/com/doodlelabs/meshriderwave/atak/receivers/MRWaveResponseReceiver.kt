/**
 * MR Wave Response Receiver
 *
 * ATAK DropDownReceiver that handles responses and state updates from the MR Wave app.
 * Processes: status updates, PTT state changes, channel list responses, errors.
 *
 * Per ATAK architecture: extends DropDownReceiver, registered via
 * MapComponent.registerDropDownReceiver() with DocumentedIntentFilter.
 *
 * Copyright (C) 2024-2026 DoodleLabs. All Rights Reserved.
 */
package com.doodlelabs.meshriderwave.atak.receivers

import android.content.Context
import android.content.Intent
import android.util.Log
import com.atakmap.android.dropdown.DropDownReceiver
import com.atakmap.android.maps.MapView
import com.doodlelabs.meshriderwave.atak.MRWavePlugin
import org.json.JSONArray

class MRWaveResponseReceiver(mapView: MapView) : DropDownReceiver(mapView) {

    companion object {
        private const val TAG = "MRWave:ResponseReceiver"

        // Response Actions
        const val ACTION_RESPONSE = "${MRWavePlugin.PLUGIN_PACKAGE}.RESPONSE"
        const val ACTION_PTT_STATE_CHANGED = "${MRWavePlugin.PLUGIN_PACKAGE}.PTT_STATE_CHANGED"
        const val ACTION_CHANNEL_CHANGED = "${MRWavePlugin.PLUGIN_PACKAGE}.CHANNEL_CHANGED"

        // Extras
        const val EXTRA_SUCCESS = "success"
        const val EXTRA_ERROR_MESSAGE = "error_message"
        const val EXTRA_RESPONSE_TYPE = "response_type"
        const val EXTRA_DATA = "data"

        // Response Types
        const val RESPONSE_STATUS = "status"
        const val RESPONSE_CHANNELS = "channels"

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

    private fun handleStatusResponse(plugin: MRWavePlugin, intent: Intent) {
        val isConnected = intent.getBooleanExtra(EXTRA_IS_CONNECTED, false)
        val isPTTActive = intent.getBooleanExtra(EXTRA_IS_PTT_ACTIVE, false)
        val channelId = intent.getStringExtra(EXTRA_CHANNEL_ID)
        val channelName = intent.getStringExtra(EXTRA_CHANNEL_NAME)

        Log.d(TAG, "handleStatusResponse: connected=$isConnected, ptt=$isPTTActive, channel=$channelName")
        plugin.handleStatusUpdate(isConnected, isPTTActive, channelId, channelName)
    }

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
        } catch (e: Exception) {
            Log.e(TAG, "handleChannelsResponse: Failed to parse channels", e)
        }
    }

    private fun handlePTTStateChanged(plugin: MRWavePlugin, intent: Intent) {
        val isPTTActive = intent.getBooleanExtra(EXTRA_IS_PTT_ACTIVE, false)
        Log.d(TAG, "handlePTTStateChanged: $isPTTActive")
        plugin.handlePTTStateChanged(isPTTActive)
    }

    private fun handleChannelChanged(plugin: MRWavePlugin, intent: Intent) {
        val channelId = intent.getStringExtra(EXTRA_CHANNEL_ID) ?: return
        val channelName = intent.getStringExtra(EXTRA_CHANNEL_NAME) ?: return
        Log.d(TAG, "handleChannelChanged: $channelName ($channelId)")
        plugin.handleChannelChanged(channelId, channelName)
    }

    override fun disposeImpl() {
        Log.d(TAG, "disposeImpl: Cleaning up response receiver")
    }

    data class ChannelInfo(
        val id: String,
        val name: String,
        val isActive: Boolean,
        val memberCount: Int
    )
}
