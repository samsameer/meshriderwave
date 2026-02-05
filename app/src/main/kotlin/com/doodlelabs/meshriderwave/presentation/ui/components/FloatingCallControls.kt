/*
 * Mesh Rider Wave - Floating Call Controls
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 *
 * WhatsApp 2025-style floating island call controls.
 * Glassmorphic pill-shaped bar floating above video feed.
 *
 * Reference: https://www.androidpolice.com/whatsapp-fresh-redesign-call-screen-ui/
 */

package com.doodlelabs.meshriderwave.presentation.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.doodlelabs.meshriderwave.domain.model.CallState
import com.doodlelabs.meshriderwave.presentation.ui.theme.PremiumColors

/**
 * Floating island call controls — WhatsApp 2025 style.
 *
 * Glass background, circular control buttons with outlines,
 * positioned at bottom of screen floating above video.
 */
@Composable
fun FloatingCallControls(
    callState: CallState,
    showVideoControls: Boolean,
    onToggleMic: () -> Unit,
    onToggleCamera: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onSwitchCamera: () -> Unit,
    onHangup: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Controls island
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(32.dp))
                .background(PremiumColors.GlassWhite)
                .border(1.dp, PremiumColors.GlassBorder, RoundedCornerShape(32.dp))
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mic toggle
            FloatingControlButton(
                icon = if (callState.isMicEnabled) Icons.Filled.Mic else Icons.Filled.MicOff,
                label = if (callState.isMicEnabled) "Mute" else "Unmute",
                isActive = !callState.isMicEnabled,
                activeColor = PremiumColors.NeonMagenta,
                onClick = onToggleMic
            )

            // Camera toggle
            FloatingControlButton(
                icon = if (callState.isCameraEnabled) Icons.Filled.Videocam else Icons.Filled.VideocamOff,
                label = "Camera",
                isActive = callState.isCameraEnabled,
                activeColor = PremiumColors.ElectricCyan,
                onClick = onToggleCamera
            )

            // Speaker toggle
            FloatingControlButton(
                icon = if (callState.isSpeakerEnabled)
                    Icons.AutoMirrored.Filled.VolumeUp
                else
                    Icons.AutoMirrored.Filled.VolumeDown,
                label = "Speaker",
                isActive = callState.isSpeakerEnabled,
                activeColor = PremiumColors.ElectricCyan,
                onClick = onToggleSpeaker
            )

            // Switch camera (only when camera is on)
            AnimatedVisibility(
                visible = callState.isCameraEnabled && showVideoControls,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                FloatingControlButton(
                    icon = Icons.Filled.Cameraswitch,
                    label = "Flip",
                    isActive = false,
                    onClick = onSwitchCamera
                )
            }
        }

        // Hangup button (separate, below island)
        FloatingHangupButton(onClick = onHangup)
    }
}

/**
 * Individual floating control button with circular outline.
 */
@Composable
private fun FloatingControlButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    activeColor: Color = PremiumColors.ElectricCyan,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(
                    if (isActive) activeColor.copy(alpha = 0.2f)
                    else Color.Transparent
                )
                .border(
                    width = 1.5.dp,
                    color = if (isActive) activeColor else PremiumColors.TextSecondary.copy(alpha = 0.4f),
                    shape = CircleShape
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = null
                ) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isActive) activeColor else PremiumColors.TextPrimary,
                modifier = Modifier.size(22.dp)
            )
        }
        Text(
            text = label,
            color = if (isActive) activeColor else PremiumColors.TextSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Floating hangup button — red, larger, always visible.
 */
@Composable
private fun FloatingHangupButton(onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(PremiumColors.NeonMagenta)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.CallEnd,
            contentDescription = "End Call",
            tint = Color.White,
            modifier = Modifier.size(28.dp)
        )
    }
}
