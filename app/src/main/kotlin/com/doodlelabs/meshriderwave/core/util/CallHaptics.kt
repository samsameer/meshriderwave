/*
 * Mesh Rider Wave - Call Haptic Feedback Patterns
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 *
 * Enhanced haptic patterns per call state, following Android haptic UX guidelines.
 * Reference: https://source.android.com/docs/core/interaction/haptics/haptics-ux-design
 */

package com.doodlelabs.meshriderwave.core.util

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

/**
 * Centralized haptic feedback for call-related actions.
 * Uses Android's HapticFeedbackConstants for rich feedback on API 30+,
 * falls back to Compose's HapticFeedbackType on older APIs.
 */
object CallHaptics {

    /**
     * Perform haptic feedback using View-based constants (richer patterns).
     */
    fun View.performCallHaptic(type: CallHapticType) {
        val constant = when (type) {
            CallHapticType.CALL_ACCEPTED -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                    HapticFeedbackConstants.CONFIRM
                else HapticFeedbackConstants.LONG_PRESS
            }
            CallHapticType.CALL_DECLINED -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                    HapticFeedbackConstants.REJECT
                else HapticFeedbackConstants.LONG_PRESS
            }
            CallHapticType.CALL_CONNECTED -> {
                HapticFeedbackConstants.CONTEXT_CLICK
            }
            CallHapticType.CALL_ENDED -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                    HapticFeedbackConstants.GESTURE_END
                else HapticFeedbackConstants.LONG_PRESS
            }
            CallHapticType.TOGGLE_CONTROL -> {
                HapticFeedbackConstants.KEYBOARD_TAP
            }
            CallHapticType.SWIPE_THRESHOLD -> {
                HapticFeedbackConstants.CLOCK_TICK
            }
            CallHapticType.INCOMING_RING -> {
                HapticFeedbackConstants.LONG_PRESS
            }
        }
        performHapticFeedback(constant)
    }

    /**
     * Perform haptic feedback using Compose's HapticFeedback API.
     */
    fun HapticFeedback.performCallHaptic(type: CallHapticType) {
        val feedbackType = when (type) {
            CallHapticType.CALL_ACCEPTED,
            CallHapticType.CALL_DECLINED,
            CallHapticType.INCOMING_RING,
            CallHapticType.CALL_ENDED -> HapticFeedbackType.LongPress
            CallHapticType.CALL_CONNECTED,
            CallHapticType.TOGGLE_CONTROL,
            CallHapticType.SWIPE_THRESHOLD -> HapticFeedbackType.TextHandleMove
        }
        performHapticFeedback(feedbackType)
    }
}

enum class CallHapticType {
    INCOMING_RING,
    CALL_ACCEPTED,
    CALL_DECLINED,
    CALL_CONNECTED,
    CALL_ENDED,
    TOGGLE_CONTROL,
    SWIPE_THRESHOLD
}
