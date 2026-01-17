/*
 * Mesh Rider Wave - Contact Avatar Component
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 *
 * Reusable avatar with online indicator and animations
 * DRY Principle - Single source of truth for avatars
 */

package com.doodlelabs.meshriderwave.presentation.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.doodlelabs.meshriderwave.presentation.ui.theme.PremiumColors

/**
 * Contact status for avatar indicator
 */
enum class ContactStatus {
    ONLINE,
    OFFLINE,
    BUSY,
    CONNECTING
}

/**
 * Premium Contact Avatar with status indicator
 *
 * @param name Contact name for initials
 * @param status Online status
 * @param size Avatar size
 * @param showStatus Show/hide status indicator
 * @param avatarColors Gradient colors for background
 */
@Composable
fun ContactAvatar(
    name: String,
    modifier: Modifier = Modifier,
    status: ContactStatus = ContactStatus.OFFLINE,
    size: Dp = 56.dp,
    fontSize: TextUnit = 20.sp,
    showStatus: Boolean = true,
    avatarColors: List<Color> = listOf(
        PremiumColors.ElectricCyan,
        PremiumColors.HoloPurple
    )
) {
    val initials = remember(name) {
        name.split(" ")
            .take(2)
            .mapNotNull { it.firstOrNull()?.uppercaseChar() }
            .joinToString("")
            .ifEmpty { "?" }
    }

    Box(
        modifier = modifier.size(size + 8.dp),
        contentAlignment = Alignment.Center
    ) {
        // Avatar circle
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(
                    brush = Brush.linearGradient(avatarColors)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initials,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = fontSize,
                    fontWeight = FontWeight.Bold
                ),
                color = Color.White
            )
        }

        // Status indicator
        if (showStatus) {
            StatusIndicator(
                status = status,
                modifier = Modifier.align(Alignment.BottomEnd)
            )
        }
    }
}

/**
 * Status indicator dot with animation
 */
@Composable
fun StatusIndicator(
    status: ContactStatus,
    modifier: Modifier = Modifier,
    size: Dp = 16.dp
) {
    val statusColor by animateColorAsState(
        targetValue = when (status) {
            ContactStatus.ONLINE -> PremiumColors.OnlineGlow
            ContactStatus.OFFLINE -> PremiumColors.OfflineGray
            ContactStatus.BUSY -> PremiumColors.BusyRed
            ContactStatus.CONNECTING -> PremiumColors.ConnectingAmber
        },
        animationSpec = tween(300),
        label = "statusColor"
    )

    // Pulse animation for online/connecting
    val infiniteTransition = rememberInfiniteTransition(label = "statusPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (status == ContactStatus.CONNECTING) 1.3f else 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (status == ContactStatus.CONNECTING) 800 else 1500,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val shouldPulse = status == ContactStatus.ONLINE || status == ContactStatus.CONNECTING

    Box(
        modifier = modifier.size(size + 4.dp),
        contentAlignment = Alignment.Center
    ) {
        // Pulse glow
        if (shouldPulse) {
            Box(
                modifier = Modifier
                    .size(size)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(statusColor.copy(alpha = 0.4f))
            )
        }

        // Border (background color)
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(PremiumColors.SpaceGray)
                .padding(2.dp)
                .clip(CircleShape)
                .background(statusColor)
        )
    }
}

/**
 * Large Avatar for call screens
 */
@Composable
fun CallAvatar(
    name: String,
    modifier: Modifier = Modifier,
    isRinging: Boolean = false,
    isConnected: Boolean = false
) {
    // Ring animation
    val infiniteTransition = rememberInfiniteTransition(label = "callAvatar")

    val ringScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ringScale"
    )

    val ringAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ringAlpha"
    )

    Box(
        modifier = modifier.size(160.dp),
        contentAlignment = Alignment.Center
    ) {
        // Outer ring (pulsing when ringing)
        if (isRinging) {
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .scale(ringScale)
                    .clip(CircleShape)
                    .border(
                        width = 3.dp,
                        color = PremiumColors.ElectricCyan.copy(alpha = ringAlpha),
                        shape = CircleShape
                    )
            )
        }

        // Connected glow
        if (isConnected) {
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .clip(CircleShape)
                    .background(PremiumColors.LaserLimeGlow)
            )
        }

        // Avatar
        ContactAvatar(
            name = name,
            size = 120.dp,
            fontSize = 48.sp,
            showStatus = false,
            avatarColors = if (isConnected) {
                listOf(PremiumColors.LaserLime, PremiumColors.ElectricCyan)
            } else {
                listOf(PremiumColors.ElectricCyan, PremiumColors.HoloPurple)
            }
        )
    }
}

/**
 * Small Avatar for lists
 */
@Composable
fun SmallAvatar(
    name: String,
    modifier: Modifier = Modifier,
    status: ContactStatus = ContactStatus.OFFLINE
) {
    ContactAvatar(
        name = name,
        modifier = modifier,
        size = 44.dp,
        fontSize = 16.sp,
        status = status,
        showStatus = true
    )
}
