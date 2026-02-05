/**
 * PTT Toolbar Receiver
 *
 * ATAK DropDownReceiver for PTT toolbar button events.
 * Handles press/release/toggle from ATAK's toolbar system.
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

class PTTToolbarReceiver(mapView: MapView) : DropDownReceiver(mapView) {

    companion object {
        private const val TAG = "MRWave:PTTToolbar"
        const val ACTION_PTT_TOOLBAR = "${MRWavePlugin.PLUGIN_PACKAGE}.PTT_TOOLBAR"

        const val EXTRA_ACTION = "action"
        const val ACTION_PRESS = "press"
        const val ACTION_RELEASE = "release"
        const val ACTION_TOGGLE = "toggle"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "onReceive: ${intent.action}")

        val plugin = MRWavePlugin.getInstance()
        if (plugin == null) {
            Log.w(TAG, "onReceive: Plugin not initialized")
            return
        }

        when (intent.getStringExtra(EXTRA_ACTION)) {
            ACTION_PRESS -> {
                Log.d(TAG, "onReceive: PTT Press")
                plugin.startPTT()
            }
            ACTION_RELEASE -> {
                Log.d(TAG, "onReceive: PTT Release")
                plugin.stopPTT()
            }
            ACTION_TOGGLE -> {
                Log.d(TAG, "onReceive: PTT Toggle")
                if (plugin.isPTTActive()) {
                    plugin.stopPTT()
                } else {
                    plugin.startPTT()
                }
            }
            else -> Log.w(TAG, "onReceive: Unknown action")
        }
    }

    override fun disposeImpl() {
        Log.d(TAG, "disposeImpl: Cleaning up PTT toolbar receiver")
    }
}
