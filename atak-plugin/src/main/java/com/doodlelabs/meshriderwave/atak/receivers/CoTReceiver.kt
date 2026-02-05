/**
 * CoT Receiver
 *
 * ATAK DropDownReceiver that handles Cursor-on-Target messages.
 * Forwards relevant CoT messages to MR Wave app for Blue Force Tracking.
 *
 * CoT Types Handled:
 * - a-f-G-* : Friendly ground units (Blue Force Tracking)
 * - a-h-G-* : Hostile ground units
 * - b-m-p-* : Map points/markers
 * - b-m-r-* : Routes
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

class CoTReceiver(mapView: MapView) : DropDownReceiver(mapView) {

    companion object {
        private const val TAG = "MRWave:CoTReceiver"

        // ATAK CoT Actions
        const val ACTION_COT_REMOTE_INPUT = "com.atakmap.android.cot.REMOTE_INPUT"
        const val ACTION_COT_DISPATCH = "com.atakmap.android.cot.DISPATCH"

        // MR Wave Actions
        const val ACTION_FORWARD_COT = "com.doodlelabs.meshriderwave.action.FORWARD_COT"

        // Extras
        const val EXTRA_COT_XML = "cot_xml"

        // CoT type prefixes
        const val TYPE_FRIENDLY = "a-f-"
        const val TYPE_HOSTILE = "a-h-"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "onReceive: ${intent.action}")

        when (intent.action) {
            ACTION_COT_REMOTE_INPUT -> handleRemoteInput(context, intent)
            ACTION_COT_DISPATCH -> handleDispatch(context, intent)
        }
    }

    private fun handleRemoteInput(context: Context, intent: Intent) {
        val cotXml = intent.getStringExtra(EXTRA_COT_XML)
        if (cotXml.isNullOrEmpty()) {
            Log.w(TAG, "handleRemoteInput: Empty CoT XML")
            return
        }

        Log.d(TAG, "handleRemoteInput: CoT received (${cotXml.length} chars)")
        forwardToMRWave(context, cotXml)
    }

    private fun handleDispatch(context: Context, intent: Intent) {
        val cotXml = intent.getStringExtra(EXTRA_COT_XML)
        if (cotXml.isNullOrEmpty()) {
            Log.w(TAG, "handleDispatch: Empty CoT XML")
            return
        }

        Log.d(TAG, "handleDispatch: CoT dispatched (${cotXml.length} chars)")
        forwardToMRWave(context, cotXml)
    }

    private fun forwardToMRWave(context: Context, cotXml: String) {
        MRWavePlugin.getInstance() ?: return

        try {
            val fwdIntent = Intent(ACTION_FORWARD_COT).apply {
                setPackage(MRWavePlugin.MRWAVE_PACKAGE)
                putExtra(EXTRA_COT_XML, cotXml)
            }
            context.sendBroadcast(fwdIntent)
            Log.d(TAG, "forwardToMRWave: CoT forwarded")
        } catch (e: Exception) {
            Log.e(TAG, "forwardToMRWave: Failed to forward CoT", e)
        }
    }

    override fun disposeImpl() {
        Log.d(TAG, "disposeImpl: Cleaning up CoT receiver")
    }
}
