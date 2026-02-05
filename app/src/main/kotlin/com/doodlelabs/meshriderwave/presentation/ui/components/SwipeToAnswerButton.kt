/*
 * Mesh Rider Wave - Swipe to Answer/Decline Button
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 *
 * WhatsApp/Signal-style swipe gesture for incoming calls.
 * Swipe right to answer, left to decline.
 * Uses spring animation and haptic feedback at threshold crossing.
 *
 * References:
 * - https://developer.android.com/develop/ui/compose/touch-input/user-interactions/handling-interactions
 * - https://source.android.com/docs/core/interaction/haptics/haptics-ux-design
 */

package com.doodlelabs.meshriderwave.presentation.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.doodlelabs.meshriderwave.presentation.ui.theme.PremiumColors
import kotlinx.coroutines.launch
import androidx.compose.ui.layout.onSizeChanged
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Swipe-to-answer/decline component for incoming calls.
 *
 * Center handle that user drags left (decline) or right (answer).
 * 75% threshold triggers action. Spring animation snaps back if released early.
 */
@Composable
fun SwipeToAnswerButton(
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val handleSize = 64.dp
    val trackHeight = 72.dp
    val trackPadding = 4.dp

    // Track width in px — will be set on layout
    var trackWidthPx by remember { mutableFloatStateOf(0f) }
    val handleSizePx = with(density) { handleSize.toPx() }
    val paddingPx = with(density) { trackPadding.toPx() }

    // Max drag distance from center
    val maxDrag = remember(trackWidthPx) {
        if (trackWidthPx > 0f) (trackWidthPx / 2f - handleSizePx / 2f - paddingPx)
        else 300f
    }

    // Threshold at 75% of max drag
    val threshold = maxDrag * 0.75f

    val offsetX = remember { Animatable(0f) }
    var hasTriggeredHaptic by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }

    // Normalized progress: -1 (full left/decline) to +1 (full right/accept)
    val progress = if (maxDrag > 0f) (offsetX.value / maxDrag).coerceIn(-1f, 1f) else 0f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(trackHeight)
            .onSizeChanged { size -> trackWidthPx = size.width.toFloat() }
            .clip(RoundedCornerShape(36.dp))
            .background(PremiumColors.SpaceGrayLight.copy(alpha = 0.8f)),
        contentAlignment = Alignment.Center
    ) {
        // Decline indicator (left)
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 16.dp)
                .alpha(if (progress < -0.3f) (-progress * 1.3f).coerceIn(0f, 1f) else 0.4f)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.CallEnd,
                    contentDescription = "Decline",
                    tint = PremiumColors.NeonMagenta,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Decline",
                    color = PremiumColors.NeonMagenta,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Accept indicator (right)
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp)
                .alpha(if (progress > 0.3f) (progress * 1.3f).coerceIn(0f, 1f) else 0.4f)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Answer",
                    color = PremiumColors.LaserLime,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Filled.Call,
                    contentDescription = "Answer",
                    tint = PremiumColors.LaserLime,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        // Hint text (center, fades out on drag)
        if (!isDragging && abs(offsetX.value) < 10f) {
            Text(
                text = "← Slide →",
                color = PremiumColors.TextSecondary.copy(alpha = 0.6f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }

        // Draggable handle
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .size(handleSize)
                .clip(CircleShape)
                .background(
                    when {
                        progress > 0.5f -> PremiumColors.LaserLime
                        progress < -0.5f -> PremiumColors.NeonMagenta
                        else -> PremiumColors.ElectricCyan
                    }
                )
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            isDragging = true
                            hasTriggeredHaptic = false
                        },
                        onDragEnd = {
                            isDragging = false
                            scope.launch {
                                if (offsetX.value > threshold) {
                                    // Accepted
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onAccept()
                                } else if (offsetX.value < -threshold) {
                                    // Declined
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onDecline()
                                } else {
                                    // Snap back to center
                                    offsetX.animateTo(
                                        0f,
                                        animationSpec = spring(
                                            dampingRatio = 0.6f,
                                            stiffness = 400f
                                        )
                                    )
                                }
                            }
                        },
                        onDragCancel = {
                            isDragging = false
                            scope.launch {
                                offsetX.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 400f))
                            }
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            scope.launch {
                                val newValue = (offsetX.value + dragAmount)
                                    .coerceIn(-maxDrag, maxDrag)
                                offsetX.snapTo(newValue)

                                // Haptic tick at threshold crossing
                                val crossedThreshold = abs(newValue) > threshold
                                if (crossedThreshold && !hasTriggeredHaptic) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    hasTriggeredHaptic = true
                                } else if (!crossedThreshold) {
                                    hasTriggeredHaptic = false
                                }
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Call,
                contentDescription = "Drag to answer or decline",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

