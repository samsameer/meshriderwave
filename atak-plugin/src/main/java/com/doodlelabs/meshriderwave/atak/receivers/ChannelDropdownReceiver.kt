/**
 * Channel Dropdown Receiver
 *
 * Handles channel selection dropdown events.
 * Displays available PTT channels and allows switching between them.
 *
 * Copyright (C) 2024-2026 DoodleLabs. All Rights Reserved.
 */
package com.doodlelabs.meshriderwave.atak.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.doodlelabs.meshriderwave.atak.MRWavePlugin

class ChannelDropdownReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ChannelDropdownReceiver"
        const val ACTION_CHANNEL_DROPDOWN = "com.doodlelabs.meshriderwave.atak.CHANNEL_DROPDOWN"

        // Extras
        const val EXTRA_ACTION = "action"
        const val EXTRA_CHANNEL_ID = "channel_id"
        const val EXTRA_CHANNEL_NAME = "channel_name"

        // Actions
        const val ACTION_SHOW = "show"
        const val ACTION_SELECT = "select"
        const val ACTION_HIDE = "hide"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "onReceive: ${intent.action}")

        if (intent.action != ACTION_CHANNEL_DROPDOWN) {
            return
        }

        val plugin = MRWavePlugin.getInstance()
        if (plugin == null) {
            Log.w(TAG, "onReceive: Plugin not initialized")
            return
        }

        when (intent.getStringExtra(EXTRA_ACTION)) {
            ACTION_SHOW -> {
                Log.d(TAG, "onReceive: Show channel selector")
                plugin.showChannelSelector()
            }
            ACTION_SELECT -> {
                val channelId = intent.getStringExtra(EXTRA_CHANNEL_ID)
                val channelName = intent.getStringExtra(EXTRA_CHANNEL_NAME)

                if (channelId != null && channelName != null) {
                    Log.d(TAG, "onReceive: Select channel $channelName ($channelId)")
                    plugin.setChannel(channelId, channelName)
                } else {
                    Log.w(TAG, "onReceive: Missing channel ID or name")
                }
            }
            ACTION_HIDE -> {
                Log.d(TAG, "onReceive: Hide channel selector")
                // Dropdown will be hidden automatically
            }
            else -> {
                Log.w(TAG, "onReceive: Unknown action")
            }
        }
    }
}
