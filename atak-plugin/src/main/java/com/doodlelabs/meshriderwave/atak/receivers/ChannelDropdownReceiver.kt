/**
 * Channel Dropdown Receiver
 *
 * ATAK DropDownReceiver for channel selection UI.
 * Shows a sliding panel with available PTT channels.
 *
 * Per ATAK architecture: DropDownReceiver is analogous to an Android Fragment.
 * Must extend DropDownReceiver (not BroadcastReceiver) and implement
 * onReceive() + disposeImpl().
 *
 * Copyright (C) 2024-2026 DoodleLabs. All Rights Reserved.
 */
package com.doodlelabs.meshriderwave.atak.receivers

import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.atakmap.android.dropdown.DropDownReceiver
import com.atakmap.android.maps.MapView
import com.doodlelabs.meshriderwave.atak.MRWavePlugin

class ChannelDropdownReceiver(mapView: MapView) : DropDownReceiver(mapView) {

    companion object {
        private const val TAG = "MRWave:ChannelDropdown"

        // Extras
        const val EXTRA_ACTION = "action"
        const val EXTRA_CHANNEL_ID = "channel_id"
        const val EXTRA_CHANNEL_NAME = "channel_name"

        // Actions
        const val ACTION_SHOW = "show"
        const val ACTION_SELECT = "select"
        const val ACTION_HIDE = "hide"

        // ATAK dropdown dimensions (fraction of screen)
        private const val HALF_WIDTH = 0.5
        private const val THIRD_HEIGHT = 0.33
    }

    private var dropdownView: View? = null

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "onReceive: ${intent.action}")

        val plugin = MRWavePlugin.getInstance()
        if (plugin == null) {
            Log.w(TAG, "onReceive: Plugin not initialized")
            return
        }

        when (intent.getStringExtra(EXTRA_ACTION)) {
            ACTION_SHOW -> {
                Log.d(TAG, "onReceive: Show channel selector")
                showChannelDropdown(context)
            }
            ACTION_SELECT -> {
                val channelId = intent.getStringExtra(EXTRA_CHANNEL_ID)
                val channelName = intent.getStringExtra(EXTRA_CHANNEL_NAME)

                if (channelId != null && channelName != null) {
                    Log.d(TAG, "onReceive: Select channel $channelName ($channelId)")
                    plugin.setChannel(channelId, channelName)
                    closeDropDown()
                } else {
                    Log.w(TAG, "onReceive: Missing channel ID or name")
                }
            }
            ACTION_HIDE -> {
                Log.d(TAG, "onReceive: Hide channel selector")
                closeDropDown()
            }
            else -> {
                // Default: toggle dropdown
                if (isVisible()) {
                    closeDropDown()
                } else {
                    showChannelDropdown(context)
                    plugin.showChannelSelector()
                }
            }
        }
    }

    /**
     * Show the channel selector as an ATAK dropdown panel.
     */
    private fun showChannelDropdown(context: Context) {
        if (isVisible()) return

        // Create a simple view for the dropdown
        // In production, use PluginLayoutInflater.inflate(pluginContext, R.layout.channel_selector, null)
        val view = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val label = TextView(context).apply {
                text = "MeshRider Wave Channels"
                textSize = 18f
                setPadding(16, 16, 16, 16)
            }
            addView(label)
        }

        dropdownView = view

        showDropDown(
            view,
            HALF_WIDTH,
            THIRD_HEIGHT,
            false,
            OnCloseListener {
                Log.d(TAG, "Channel dropdown closed")
            }
        )
    }

    override fun disposeImpl() {
        Log.d(TAG, "disposeImpl: Cleaning up channel dropdown")
        dropdownView = null
    }
}
