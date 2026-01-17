/*
 * Mesh Rider Wave - Status Badge Components
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 *
 * Reusable status badges and chips for displaying
 * connection states, network status, and indicators
 *
 * SOLID: Single Responsibility - Only status display
 * DRY: Reusable across all screens
 */

package com.doodlelabs.meshriderwave.presentation.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.doodlelabs.meshriderwave.presentation.ui.theme.PremiumColors

/**
 * Connection status types for the mesh network
 */
enum class ConnectionStatus {
    CONNECTED,
    CONNECTING,
    DISCONNECTED,
    ERROR
}

/**
 * Signal strength levels
 */
enum class SignalStrength {
    EXCELLENT,
    GOOD,
    FAIR,
    POOR,
    NONE
}

/**
 * Premium Connection Status Badge
 *
 * Displays the current connection status with icon, text, and animation.
 *
 * @param status Current connection status
 * @param modifier Modifier for customization
 * @param showText Show status text label
 */
@Composable
fun ConnectionStatusBadge(
    status: ConnectionStatus,
    modifier: Modifier = Modifier,
    showText: Boolean = true
) {
    val statusConfig = remember(status) {
        when (status) {
            ConnectionStatus.CONNECTED -> StatusConfig(
                color = PremiumColors.OnlineGlow,
                icon = Icons.Default.Wifi,
                text = "Connected",
                animate = false
            )
            ConnectionStatus.CONNECTING -> StatusConfig(
                color = PremiumColors.ConnectingAmber,
                icon = Icons.Default.WifiFind,
                text = "Connecting",
                animate = true
            )
            ConnectionStatus.DISCONNECTED -> StatusConfig(
                color = PremiumColors.OfflineGray,
                icon = Icons.Default.WifiOff,
                text = "Disconnected",
                animate = false
            )
            ConnectionStatus.ERROR -> StatusConfig(
                color = PremiumColors.BusyRed,
                icon = Icons.Default.Error,
                text = "Error",
                animate = true
            )
        }
    }

    val animatedColor by animateColorAsState(
        targetValue = statusConfig.color,
        animationSpec = tween(300),
        label = "statusColor"
    )

    // Pulse animation for connecting/error states
    val infiniteTransition = rememberInfiniteTransition(label = "statusPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (statusConfig.animate) 1.1f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(animatedColor.copy(alpha = 0.15f))
            .border(
                width = 1.dp,
                color = animatedColor.copy(alpha = 0.3f),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Status dot with pulse
        Box(
            modifier = Modifier
                .size(8.dp)
                .scale(pulseScale)
                .clip(CircleShape)
                .background(animatedColor)
        )

        // Icon
        Icon(
            imageVector = statusConfig.icon,
            contentDescription = statusConfig.text,
            modifier = Modifier.size(16.dp),
            tint = animatedColor
        )

        // Text
        if (showText) {
            Text(
                text = statusConfig.text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = animatedColor
            )
        }
    }
}

/**
 * Signal Strength Indicator
 *
 * Visual bars showing signal quality.
 */
@Composable
fun SignalStrengthIndicator(
    strength: SignalStrength,
    modifier: Modifier = Modifier,
    barCount: Int = 4,
    showLabel: Boolean = false
) {
    val (activeCount, color) = remember(strength) {
        when (strength) {
            SignalStrength.EXCELLENT -> 4 to PremiumColors.OnlineGlow
            SignalStrength.GOOD -> 3 to PremiumColors.LaserLime
            SignalStrength.FAIR -> 2 to PremiumColors.ConnectingAmber
            SignalStrength.POOR -> 1 to PremiumColors.BusyRed
            SignalStrength.NONE -> 0 to PremiumColors.OfflineGray
        }
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            for (i in 1..barCount) {
                val isActive = i <= activeCount
                val height = (8 + i * 4).dp

                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(height)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            if (isActive) color else PremiumColors.SpaceGrayLighter
                        )
                )
            }
        }

        if (showLabel) {
            Text(
                text = strength.name.lowercase()
                    .replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontSize = 10.sp
            )
        }
    }
}

/**
 * Network Info Badge
 *
 * Displays IP address, interface type, and connection info.
 */
@Composable
fun NetworkInfoBadge(
    ipAddress: String?,
    interfaceType: String? = null,
    modifier: Modifier = Modifier
) {
    val displayText = buildString {
        if (interfaceType != null) {
            append(interfaceType)
            append(" • ")
        }
        append(ipAddress ?: "No IP")
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(PremiumColors.SpaceGrayLight)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = when (interfaceType?.lowercase()) {
                "usb" -> Icons.Default.Usb
                "ethernet" -> Icons.Default.SettingsEthernet
                "wifi" -> Icons.Default.Wifi
                else -> Icons.Default.NetworkCell
            },
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = PremiumColors.TextSecondary
        )

        Text(
            text = displayText,
            style = MaterialTheme.typography.labelSmall,
            color = PremiumColors.TextSecondary,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Premium Chip/Tag
 *
 * Customizable chip for labels, categories, etc.
 */
@Composable
fun PremiumChip(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    color: Color = PremiumColors.ElectricCyan,
    variant: ChipVariant = ChipVariant.FILLED
) {
    val backgroundColor = when (variant) {
        ChipVariant.FILLED -> color.copy(alpha = 0.2f)
        ChipVariant.OUTLINED -> Color.Transparent
        ChipVariant.GRADIENT -> Color.Transparent
    }

    val borderModifier = when (variant) {
        ChipVariant.OUTLINED -> Modifier.border(
            width = 1.dp,
            color = color.copy(alpha = 0.5f),
            shape = RoundedCornerShape(16.dp)
        )
        ChipVariant.GRADIENT -> Modifier.border(
            width = 1.dp,
            brush = Brush.horizontalGradient(
                colors = listOf(PremiumColors.ElectricCyan, PremiumColors.HoloPurple)
            ),
            shape = RoundedCornerShape(16.dp)
        )
        else -> Modifier
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .then(borderModifier)
            .background(backgroundColor)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = color
            )
        }

        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = color
        )
    }
}

enum class ChipVariant {
    FILLED,
    OUTLINED,
    GRADIENT
}

/**
 * Peer Count Badge
 *
 * Shows number of connected peers with icon.
 */
@Composable
fun PeerCountBadge(
    count: Int,
    modifier: Modifier = Modifier
) {
    val color = when {
        count > 5 -> PremiumColors.OnlineGlow
        count > 0 -> PremiumColors.LaserLime
        else -> PremiumColors.OfflineGray
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = Icons.Default.People,
            contentDescription = "Peers",
            modifier = Modifier.size(14.dp),
            tint = color
        )

        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

/**
 * Encryption Status Badge
 *
 * Shows E2E encryption status.
 */
@Composable
fun EncryptionBadge(
    isEncrypted: Boolean,
    modifier: Modifier = Modifier
) {
    val color = if (isEncrypted) PremiumColors.OnlineGlow else PremiumColors.BusyRed
    val icon = if (isEncrypted) Icons.Default.Lock else Icons.Default.LockOpen
    val text = if (isEncrypted) "E2E Encrypted" else "Not Encrypted"

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            modifier = Modifier.size(12.dp),
            tint = color
        )

        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = color
        )
    }
}

/**
 * Call Duration Badge
 *
 * Displays call duration with timer icon.
 */
@Composable
fun CallDurationBadge(
    duration: String,
    modifier: Modifier = Modifier,
    isRecording: Boolean = false
) {
    val infiniteTransition = rememberInfiniteTransition(label = "recording")
    val recordingAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isRecording) 0.3f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "recordingAlpha"
    )

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(PremiumColors.SpaceGrayLight)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (isRecording) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(PremiumColors.BusyRed.copy(alpha = recordingAlpha))
            )
        }

        Icon(
            imageVector = Icons.Default.Timer,
            contentDescription = "Duration",
            modifier = Modifier.size(14.dp),
            tint = PremiumColors.TextSecondary
        )

        Text(
            text = duration,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = PremiumColors.TextPrimary,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
        )
    }
}

/**
 * Status config data class
 */
private data class StatusConfig(
    val color: Color,
    val icon: ImageVector,
    val text: String,
    val animate: Boolean
)
