/**
 * CoT Receiver
 *
 * Handles Cursor-on-Target (CoT) messages from ATAK.
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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.doodlelabs.meshriderwave.atak.MRWavePlugin

class CoTReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "CoTReceiver"

        // ATAK CoT Actions
        const val ACTION_COT_REMOTE_INPUT = "com.atakmap.android.cot.REMOTE_INPUT"
        const val ACTION_COT_DISPATCH = "com.atakmap.android.cot.DISPATCH"

        // MR Wave Actions
        const val ACTION_FORWARD_COT = "com.doodlelabs.meshriderwave.action.FORWARD_COT"

        // Extras
        const val EXTRA_COT_XML = "cot_xml"
        const val EXTRA_COT_UID = "uid"
        const val EXTRA_COT_TYPE = "type"

        // CoT type prefixes
        const val TYPE_FRIENDLY = "a-f-"
        const val TYPE_HOSTILE = "a-h-"
        const val TYPE_UNKNOWN = "a-u-"
        const val TYPE_NEUTRAL = "a-n-"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "onReceive: ${intent.action}")

        when (intent.action) {
            ACTION_COT_REMOTE_INPUT -> handleRemoteInput(context, intent)
            ACTION_COT_DISPATCH -> handleDispatch(context, intent)
        }
    }

    /**
     * Handle incoming CoT from remote sources (network).
     */
    private fun handleRemoteInput(context: Context, intent: Intent) {
        val cotXml = intent.getStringExtra(EXTRA_COT_XML)
        if (cotXml.isNullOrEmpty()) {
            Log.w(TAG, "handleRemoteInput: Empty CoT XML")
            return
        }

        Log.d(TAG, "handleRemoteInput: CoT received (${cotXml.length} chars)")

        // Forward to MR Wave app
        forwardToMRWave(context, cotXml)
    }

    /**
     * Handle CoT dispatch (outgoing or internal).
     */
    private fun handleDispatch(context: Context, intent: Intent) {
        val cotXml = intent.getStringExtra(EXTRA_COT_XML)
        if (cotXml.isNullOrEmpty()) {
            Log.w(TAG, "handleDispatch: Empty CoT XML")
            return
        }

        Log.d(TAG, "handleDispatch: CoT dispatched (${cotXml.length} chars)")

        // Forward to MR Wave app for mesh broadcast
        forwardToMRWave(context, cotXml)
    }

    /**
     * Forward CoT message to MR Wave app.
     */
    private fun forwardToMRWave(context: Context, cotXml: String) {
        val plugin = MRWavePlugin.getInstance() ?: return

        try {
            val intent = Intent(ACTION_FORWARD_COT).apply {
                setPackage(MRWavePlugin.MRWAVE_PACKAGE)
                putExtra(EXTRA_COT_XML, cotXml)
            }
            context.sendBroadcast(intent)
            Log.d(TAG, "forwardToMRWave: CoT forwarded")
        } catch (e: Exception) {
            Log.e(TAG, "forwardToMRWave: Failed to forward CoT", e)
        }
    }

    /**
     * Check if CoT type is a friendly unit (BFT).
     */
    private fun isFriendlyType(type: String): Boolean {
        return type.startsWith(TYPE_FRIENDLY)
    }

    /**
     * Check if CoT type should be forwarded to MR Wave.
     */
    private fun shouldForward(type: String): Boolean {
        return isFriendlyType(type) ||
               type.startsWith(TYPE_HOSTILE) ||
               type.startsWith("b-m-") // Map markers
    }
}
