/*
 * Mesh Rider Wave - Samsung XCover Button Support
 * Following developer.samsung Knox guidelines
 * Handles programmable XCover/Active button for PTT
 */

package com.doodlelabs.meshriderwave.ptt

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

/**
 * Samsung XCover/Active PTT Button Receiver
 * Per Samsung Knox documentation:
 * - XCover Pro/XCover6 Pro/XCover7 Pro have programmable side buttons
 * - Button sends broadcast when pressed
 * - Intent action varies by device
 *
 * Tested on:
 * - Samsung XCover6 Pro
 * - Samsung XCover7 Pro
 */
class XCoverPttButtonReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "MeshRider:XCover"

        // Samsung XCover button intents
        // Per Knox SDK hardware key mappings
        private const val ACTION_XCOVER_KEY =
            "com.samsung.android.intent.XCOVER_KEY"
        private const val ACTION_ACTIVE_KEY =
            "com.samsung.android.intent.ACTION_KEY"
        private const val ACTION_PTT_KEY =
            "android.intent.action.PTT_BUTTON"

        // State extra keys
        private const val EXTRA_KEY_STATE = "state"
        private val EXTRA_KEY_INDEX = "index"
    }

    // PTT callback
    var pttManager: WorkingPttManager? = null

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val state = intent.getBooleanExtra(EXTRA_KEY_STATE, false)

        Log.d(TAG, "XCover button event: action=$action, state=$state")

        when (action) {
            ACTION_XCOVER_KEY,
            ACTION_ACTIVE_KEY,
            ACTION_PTT_KEY -> {
                if (state) {
                    // Button pressed
                    handlePress(context)
                } else {
                    // Button released
                    handleRelease(context)
                }
            }
        }
    }

    private fun handlePress(context: Context) {
        Log.i(TAG, "XCover button PTT press")

        // Vibrate feedback
        vibrate(context, longArrayOf(0, 100))

        // Start PTT transmission
        pttManager?.let { manager ->
            // We need to launch a coroutine since startTransmission is suspend
            // For BroadcastReceiver, we use a simple approach:
            // Start via service intent
            val intent = Intent(context, PttService::class.java).apply {
                action = PttService.ACTION_PTT_DOWN
            }
            context.startService(intent)
        }
    }

    private fun handleRelease(context: Context) {
        Log.i(TAG, "XCover button PTT release")

        // Vibrate feedback
        vibrate(context, longArrayOf(0, 50, 50, 50))

        // Stop PTT transmission
        pttManager?.let { _ ->
            val intent = Intent(context, PttService::class.java).apply {
                action = PttService.ACTION_PTT_UP
            }
            context.startService(intent)
        }
    }

    private fun vibrate(context: Context, pattern: LongArray) {
        val vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createWaveform(pattern, -1)
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, -1)
        }
    }
}
