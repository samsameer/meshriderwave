/**
 * PTT Toolbar Component
 *
 * Provides the PTT button and channel indicator for the ATAK toolbar.
 * The button supports both tap-to-talk and press-and-hold modes.
 *
 * UI Layout:
 * ┌─────────────────────────────────────┐
 * │  [PTT]  │  Channel: Alpha          │
 * │  🎤     │  ▼ Tap to change         │
 * └─────────────────────────────────────┘
 *
 * States:
 * - Idle: Gray microphone icon
 * - Transmitting: Red pulsing microphone
 * - Receiving: Green microphone
 * - Disconnected: Grayed out, disabled
 *
 * Copyright (C) 2024-2026 DoodleLabs. All Rights Reserved.
 */
package com.doodlelabs.meshriderwave.atak.toolbar

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import com.doodlelabs.meshriderwave.atak.R

/**
 * PTT Toolbar Component for ATAK integration.
 *
 * @param context Application context
 */
class PTTToolbarComponent(private val context: Context) {

    companion object {
        private const val TAG = "PTTToolbarComponent"

        // Colors
        private const val COLOR_IDLE = 0xFF6B7280.toInt()      // Gray
        private const val COLOR_TX = 0xFFDC2626.toInt()         // Red (transmitting)
        private const val COLOR_RX = 0xFF16A34A.toInt()         // Green (receiving)
        private const val COLOR_DISABLED = 0xFF9CA3AF.toInt()   // Light gray
        private const val COLOR_BACKGROUND = 0xFF1F2937.toInt() // Dark gray
        private const val COLOR_TEXT = 0xFFFFFFFF.toInt()       // White

        // Button configuration
        private const val BUTTON_SIZE_DP = 48
        private const val LONG_PRESS_TIMEOUT_MS = 500L
    }

    // UI Components
    private var rootView: LinearLayout? = null
    private var pttButton: ImageButton? = null
    private var channelTextView: TextView? = null

    // State
    private var isConnected = true
    private var isTransmitting = false
    private var isLongPressMode = false
    private var channelName = "Default"

    // Callbacks
    var onPTTPressed: (() -> Unit)? = null
    var onPTTReleased: (() -> Unit)? = null
    var onChannelClicked: (() -> Unit)? = null

    // Handlers
    private val mainHandler = Handler(Looper.getMainLooper())
    private var longPressRunnable: Runnable? = null

    init {
        createViews()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createViews() {
        // Root container
        rootView = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(8.dp, 4.dp, 8.dp, 4.dp)
            background = createRoundedBackground(COLOR_BACKGROUND, 8.dp)
        }

        // PTT Button
        pttButton = ImageButton(context).apply {
            layoutParams = LinearLayout.LayoutParams(BUTTON_SIZE_DP.dp, BUTTON_SIZE_DP.dp)
            background = createCircleBackground(COLOR_IDLE)
            setImageResource(R.drawable.ic_mic)
            contentDescription = "Push to Talk"
            isClickable = true
            isFocusable = true

            // Touch handling for PTT
            setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        if (isConnected) {
                            handlePTTDown()
                        }
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (isConnected) {
                            handlePTTUp()
                        }
                        true
                    }
                    else -> false
                }
            }
        }

        // Channel indicator
        channelTextView = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = 8.dp
            }
            text = "Ch: $channelName"
            setTextColor(COLOR_TEXT)
            textSize = 12f
            maxLines = 1
            setOnClickListener {
                if (isConnected) {
                    onChannelClicked?.invoke()
                }
            }
        }

        // Add views
        rootView?.addView(pttButton)
        rootView?.addView(channelTextView)

        Log.d(TAG, "createViews: Toolbar component created")
    }

    private fun handlePTTDown() {
        Log.d(TAG, "handlePTTDown")

        // Start long-press detection
        longPressRunnable = Runnable {
            isLongPressMode = true
            startTransmitting()
        }
        mainHandler.postDelayed(longPressRunnable!!, LONG_PRESS_TIMEOUT_MS)
    }

    private fun handlePTTUp() {
        Log.d(TAG, "handlePTTUp: longPressMode=$isLongPressMode")

        // Cancel long-press detection
        longPressRunnable?.let { mainHandler.removeCallbacks(it) }
        longPressRunnable = null

        if (isLongPressMode) {
            // Long press mode: release stops TX
            isLongPressMode = false
            stopTransmitting()
        } else {
            // Tap mode: toggle TX
            if (isTransmitting) {
                stopTransmitting()
            } else {
                startTransmitting()
            }
        }
    }

    private fun startTransmitting() {
        if (isTransmitting) return

        Log.i(TAG, "startTransmitting")
        isTransmitting = true
        updateButtonState()
        onPTTPressed?.invoke()
    }

    private fun stopTransmitting() {
        if (!isTransmitting) return

        Log.i(TAG, "stopTransmitting")
        isTransmitting = false
        updateButtonState()
        onPTTReleased?.invoke()
    }

    /**
     * Update component state from MR Wave app.
     *
     * @param isPTTActive Whether PTT is currently active
     * @param channelName Current channel name
     */
    fun updateState(isPTTActive: Boolean, channelName: String) {
        mainHandler.post {
            this.isTransmitting = isPTTActive
            this.channelName = channelName
            channelTextView?.text = "Ch: $channelName"
            updateButtonState()
        }
    }

    /**
     * Set connection state.
     *
     * @param connected Whether MR Wave app is connected
     */
    fun setConnected(connected: Boolean) {
        mainHandler.post {
            this.isConnected = connected
            pttButton?.isEnabled = connected
            pttButton?.alpha = if (connected) 1.0f else 0.5f
            updateButtonState()
        }
    }

    private fun updateButtonState() {
        val color = when {
            !isConnected -> COLOR_DISABLED
            isTransmitting -> COLOR_TX
            else -> COLOR_IDLE
        }

        pttButton?.background = createCircleBackground(color)

        // Pulse animation when transmitting
        if (isTransmitting) {
            startPulseAnimation()
        } else {
            stopPulseAnimation()
        }
    }

    private fun startPulseAnimation() {
        pttButton?.animate()
            ?.scaleX(1.1f)
            ?.scaleY(1.1f)
            ?.setDuration(500)
            ?.withEndAction {
                pttButton?.animate()
                    ?.scaleX(1.0f)
                    ?.scaleY(1.0f)
                    ?.setDuration(500)
                    ?.withEndAction {
                        if (isTransmitting) {
                            startPulseAnimation()
                        }
                    }
                    ?.start()
            }
            ?.start()
    }

    private fun stopPulseAnimation() {
        pttButton?.animate()?.cancel()
        pttButton?.scaleX = 1.0f
        pttButton?.scaleY = 1.0f
    }

    /**
     * Get the root view for adding to ATAK toolbar.
     */
    fun getView(): View? = rootView

    /**
     * Handle configuration changes (e.g., rotation).
     */
    fun onConfigurationChanged(newConfig: Configuration) {
        Log.d(TAG, "onConfigurationChanged")
        // Re-layout if needed
    }

    /**
     * Clean up resources.
     */
    fun dispose() {
        Log.d(TAG, "dispose")
        longPressRunnable?.let { mainHandler.removeCallbacks(it) }
        stopPulseAnimation()
        rootView?.removeAllViews()
        rootView = null
        pttButton = null
        channelTextView = null
    }

    // ========== Helper Functions ==========

    private val Int.dp: Int
        get() = (this * context.resources.displayMetrics.density).toInt()

    private fun createCircleBackground(color: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }
    }

    private fun createRoundedBackground(color: Int, radius: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = radius.toFloat()
        }
    }
}
