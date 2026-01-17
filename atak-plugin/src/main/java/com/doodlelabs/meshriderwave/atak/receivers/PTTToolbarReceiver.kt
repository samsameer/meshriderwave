/**
 * PTT Toolbar Receiver
 *
 * Handles toolbar button events from ATAK's toolbar system.
 * This receiver is registered in the manifest and receives button press events.
 *
 * Copyright (C) 2024-2026 DoodleLabs. All Rights Reserved.
 */
package com.doodlelabs.meshriderwave.atak.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.doodlelabs.meshriderwave.atak.MRWavePlugin

class PTTToolbarReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "PTTToolbarReceiver"
        const val ACTION_PTT_TOOLBAR = "com.doodlelabs.meshriderwave.atak.PTT_TOOLBAR"

        // Extras
        const val EXTRA_ACTION = "action"
        const val ACTION_PRESS = "press"
        const val ACTION_RELEASE = "release"
        const val ACTION_TOGGLE = "toggle"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "onReceive: ${intent.action}")

        if (intent.action != ACTION_PTT_TOOLBAR) {
            return
        }

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
            else -> {
                Log.w(TAG, "onReceive: Unknown action")
            }
        }
    }
}
