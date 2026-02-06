/*
 * Mesh Rider Wave - PRODUCTION PTT Button Support
 * Handles Samsung XCover button + standard KeyEvent fallback
 * 
 * FIXED (Feb 2026):
 * - Standard KeyEvent fallback (Volume Up long-press)
 * - BroadcastReceiver for Samsung XCover
 * - KeyEvent.Callback for standard Android
 * - Bluetooth headset button support
 * - Proper action handling
 */

package com.doodlelabs.meshriderwave.ptt

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.KeyEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * PRODUCTION PTT Button Handler
 * 
 * Supports multiple input methods:
 * 1. Samsung XCover/Active programmable button (via BroadcastReceiver)
 * 2. Volume Up long-press (standard Android)
 * 3. Bluetooth headset button
 * 4. Custom intent actions
 */
class XCoverPttButtonReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "MeshRider:XCover"

        // Samsung XCover button intents (device-specific)
        private const val ACTION_XCOVER_KEY =
            "com.samsung.android.intent.XCOVER_KEY"
        private const val ACTION_ACTIVE_KEY =
            "com.samsung.android.intent.ACTION_KEY"
        private const val ACTION_PTT_KEY =
            "android.intent.action.PTT_BUTTON"
        
        // Standard headset media button
        private const val ACTION_MEDIA_BUTTON =
            "android.intent.action.MEDIA_BUTTON"

        // State extra keys
        private const val EXTRA_KEY_STATE = "state"

        // Track PTT state
        @Volatile
        var isPttPressed = false
            private set
    }

    // PTT callback
    var pttManager: WorkingPttManager? = null

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        when (action) {
            ACTION_XCOVER_KEY,
            ACTION_ACTIVE_KEY,
            ACTION_PTT_KEY -> {
                // Samsung XCover button
                val state = intent.getBooleanExtra(EXTRA_KEY_STATE, false)
                Log.d(TAG, "XCover button event: state=$state")
                handlePttState(context, state)
            }
            
            ACTION_MEDIA_BUTTON -> {
                // Bluetooth headset button
                val keyEvent = intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
                keyEvent?.let { handleMediaButton(context, it) }
            }
            
            else -> {
                // Check for custom PTT intents from external apps
                if (action.contains("PTT") || action.contains("PUSH_TO_TALK")) {
                    val state = intent.getBooleanExtra("state", false)
                    handlePttState(context, state)
                }
            }
        }
    }

    private fun handleMediaButton(context: Context, event: KeyEvent) {
        when (event.keyCode) {
            KeyEvent.KEYCODE_HEADSETHOOK,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                val isPressed = event.action == KeyEvent.ACTION_DOWN
                Log.d(TAG, "Media button: isPressed=$isPressed")
                handlePttState(context, isPressed)
            }
        }
    }

    private fun handlePttState(context: Context, isPressed: Boolean) {
        if (isPressed && !isPttPressed) {
            // Button pressed
            isPttPressed = true
            handlePress(context)
        } else if (!isPressed && isPttPressed) {
            // Button released
            isPttPressed = false
            handleRelease(context)
        }
    }

    private fun handlePress(context: Context) {
        Log.i(TAG, "PTT button press")

        vibrate(context, longArrayOf(0, 100))

        // Start PTT via service
        val intent = Intent(context, PttService::class.java).apply {
            action = PttService.ACTION_PTT_DOWN
        }
        context.startService(intent)
    }

    private fun handleRelease(context: Context) {
        Log.i(TAG, "PTT button release")

        vibrate(context, longArrayOf(0, 50, 50, 50))

        // Stop PTT via service
        val intent = Intent(context, PttService::class.java).apply {
            action = PttService.ACTION_PTT_UP
        }
        context.startService(intent)
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
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, -1)
        }
    }
}

/**
 * KeyEvent handler for standard Android devices (Activity-level)
 * Use Volume Up long-press as PTT on non-Samsung devices
 */
class StandardPttKeyHandler(
    private val context: Context,
    private val onPttPress: () -> Unit,
    private val onPttRelease: () -> Unit
) {
    companion object {
        private const val TAG = "MeshRider:StdPTT"
        private const val LONG_PRESS_THRESHOLD_MS = 300
    }

    private var pressStartTime = 0L
    private var isPttActive = false

    fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                if (event?.repeatCount == 0) {
                    pressStartTime = System.currentTimeMillis()
                }
                true // Consume event
            }
            KeyEvent.KEYCODE_HEADSETHOOK,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                if (!isPttActive) {
                    isPttActive = true
                    onPttPress()
                }
                true
            }
            else -> false
        }
    }

    fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                val pressDuration = System.currentTimeMillis() - pressStartTime
                
                if (pressDuration >= LONG_PRESS_THRESHOLD_MS) {
                    // Long press - was PTT
                    if (isPttActive) {
                        isPttActive = false
                        onPttRelease()
                    }
                } else {
                    // Short press - normal volume up
                    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    audioManager.adjustStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_RAISE,
                        AudioManager.FLAG_SHOW_UI
                    )
                }
                true
            }
            KeyEvent.KEYCODE_HEADSETHOOK,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                if (isPttActive) {
                    isPttActive = false
                    onPttRelease()
                }
                true
            }
            else -> false
        }
    }

    /**
     * Check if Volume Up is currently held (for PTT)
     */
    fun isVolumeUpHeld(): Boolean {
        return System.currentTimeMillis() - pressStartTime >= LONG_PRESS_THRESHOLD_MS &&
               !isPttActive
    }

    /**
     * Activate PTT when Volume Up held long enough
     */
    fun checkLongPress() {
        if (isVolumeUpHeld() && !isPttActive) {
            isPttActive = true
            onPttPress()
        }
    }
}
