/*
 * Mesh Rider Wave ATAK Plugin - Military PTT Button
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 *
 * Large, tactile PTT button optimized for:
 * - Gloved operation
 * - Low-light visibility
 * - Instant visual feedback
 * - Haptic confirmation
 */

package com.doodlelabs.meshriderwave.atak.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator

/**
 * Military-grade PTT button for ATAK toolbar
 *
 * Design principles:
 * 1. Large touch target (min 60dp) for gloved use
 * 2. High contrast colors for low-light
 * 3. Clear visual state indication
 * 4. Haptic feedback on press/release
 * 5. Pulse animation when transmitting
 *
 * States:
 * - IDLE: Dark gray, ready to transmit
 * - PRESSED: Green glow, transmitting
 * - RECEIVING: Blue glow, someone else talking
 * - DENIED: Red flash, floor denied
 * - DISABLED: Dim, no channel selected
 */
class MilitaryPTTButton(context: Context) : View(context) {

    enum class State {
        IDLE,
        PRESSED,
        RECEIVING,
        DENIED,
        DISABLED
    }

    // Colors
    companion object {
        private const val COLOR_IDLE = 0xFF2A2A35.toInt()
        private const val COLOR_IDLE_BORDER = 0xFF3A3A45.toInt()
        private const val COLOR_PRESSED = 0xFF00E676.toInt()
        private const val COLOR_PRESSED_GLOW = 0x4000E676.toInt()
        private const val COLOR_RECEIVING = 0xFF00B8D4.toInt()
        private const val COLOR_RECEIVING_GLOW = 0x4000B8D4.toInt()
        private const val COLOR_DENIED = 0xFFFF1744.toInt()
        private const val COLOR_DISABLED = 0xFF1A1A20.toInt()
        private const val COLOR_TEXT = 0xFFFFFFFF.toInt()
        private const val COLOR_TEXT_DIM = 0xFF6B6B6B.toInt()
    }

    // State
    private var state: State = State.IDLE
    private var channelName: String = ""
    private var speakerName: String = ""
    private var pulseProgress: Float = 0f

    // Callbacks
    var onPTTPressed: (() -> Unit)? = null
    var onPTTReleased: (() -> Unit)? = null

    // Paints
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = COLOR_TEXT
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
    private val subTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = COLOR_TEXT_DIM
        textAlign = Paint.Align.CENTER
        typeface = Typeface.MONOSPACE
    }

    // Animation
    private var pulseAnimator: ValueAnimator? = null

    // Vibrator
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    init {
        // Minimum size for gloved operation
        minimumWidth = dpToPx(80)
        minimumHeight = dpToPx(80)

        isClickable = true
        isFocusable = true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredSize = dpToPx(80)
        val width = resolveSize(desiredSize, widthMeasureSpec)
        val height = resolveSize(desiredSize, heightMeasureSpec)
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f
        val cy = height / 2f
        val radius = minOf(width, height) / 2f - dpToPx(8)

        // Draw glow for active states
        if (state == State.PRESSED || state == State.RECEIVING) {
            val glowRadius = radius + dpToPx(12) * pulseProgress
            glowPaint.color = if (state == State.PRESSED) COLOR_PRESSED_GLOW else COLOR_RECEIVING_GLOW
            canvas.drawCircle(cx, cy, glowRadius, glowPaint)
        }

        // Draw main circle
        backgroundPaint.color = when (state) {
            State.IDLE -> COLOR_IDLE
            State.PRESSED -> COLOR_PRESSED
            State.RECEIVING -> COLOR_RECEIVING
            State.DENIED -> COLOR_DENIED
            State.DISABLED -> COLOR_DISABLED
        }
        canvas.drawCircle(cx, cy, radius, backgroundPaint)

        // Draw border
        borderPaint.color = when (state) {
            State.IDLE -> COLOR_IDLE_BORDER
            State.PRESSED -> COLOR_PRESSED
            State.RECEIVING -> COLOR_RECEIVING
            State.DENIED -> COLOR_DENIED
            State.DISABLED -> COLOR_IDLE_BORDER
        }
        canvas.drawCircle(cx, cy, radius, borderPaint)

        // Draw text
        textPaint.textSize = dpToPx(18).toFloat()
        textPaint.color = if (state == State.DISABLED) COLOR_TEXT_DIM else COLOR_TEXT

        val mainText = when (state) {
            State.IDLE -> "PTT"
            State.PRESSED -> "TX"
            State.RECEIVING -> "RX"
            State.DENIED -> "DENY"
            State.DISABLED -> "PTT"
        }
        canvas.drawText(mainText, cx, cy + dpToPx(6), textPaint)

        // Draw channel name or speaker
        subTextPaint.textSize = dpToPx(9).toFloat()
        val subText = when (state) {
            State.RECEIVING -> speakerName.take(8).uppercase()
            else -> if (channelName.isNotEmpty()) channelName.take(8).uppercase() else "NO CHAN"
        }
        canvas.drawText(subText, cx, cy + dpToPx(22), subTextPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (state == State.DISABLED) return false

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (state == State.IDLE) {
                    setState(State.PRESSED)
                    vibratePress()
                    onPTTPressed?.invoke()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (state == State.PRESSED) {
                    setState(State.IDLE)
                    vibrateRelease()
                    onPTTReleased?.invoke()
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    /**
     * Set button state
     */
    fun setState(newState: State) {
        val oldState = state
        state = newState

        // Start/stop pulse animation
        when (newState) {
            State.PRESSED, State.RECEIVING -> startPulseAnimation()
            else -> stopPulseAnimation()
        }

        invalidate()
    }

    /**
     * Set current channel name
     */
    fun setChannel(name: String) {
        channelName = name
        invalidate()
    }

    /**
     * Set current speaker (when receiving)
     */
    fun setSpeaker(name: String) {
        speakerName = name
        invalidate()
    }

    /**
     * Enable/disable button
     */
    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        if (!enabled) {
            setState(State.DISABLED)
        } else if (state == State.DISABLED) {
            setState(State.IDLE)
        }
    }

    /**
     * Flash denied state
     */
    fun flashDenied() {
        val previousState = state
        setState(State.DENIED)
        vibrateDenied()

        postDelayed({
            if (state == State.DENIED) {
                setState(State.IDLE)
            }
        }, 500)
    }

    private fun startPulseAnimation() {
        pulseAnimator?.cancel()
        pulseAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 800
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                pulseProgress = animator.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun stopPulseAnimation() {
        pulseAnimator?.cancel()
        pulseAnimator = null
        pulseProgress = 0f
    }

    private fun vibratePress() {
        vibrate(longArrayOf(0, 50))
    }

    private fun vibrateRelease() {
        vibrate(longArrayOf(0, 30))
    }

    private fun vibrateDenied() {
        vibrate(longArrayOf(0, 100, 50, 100))
    }

    private fun vibrate(pattern: LongArray) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, -1)
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        pulseAnimator?.cancel()
    }
}
