/*
 * Mesh Rider Wave - PTT Quality Visualization
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 *
 * Military-grade PTT quality indicators
 * - Real-time network health
 * - Audio quality metrics
 * - Transmission status
 * - Floor control state
 */

package com.doodlelabs.meshriderwave.ptt

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.doodlelabs.meshriderwave.presentation.ui.theme.PremiumColors

/**
 * PTT Quality Indicator - Shows real-time audio/network status
 *
 * Per Samsung Knox UI guidelines for outdoor visibility:
 * - High contrast colors
 * - Large text (min 12sp)
 * - Clear visual hierarchy
 */
@Composable
fun PttQualityIndicator(
    isTransmitting: Boolean,
    isReceiving: Boolean,
    currentSpeaker: String?,
    networkHealthy: Boolean,
    latencyMs: Int,
    packetsSent: Long,
    packetsReceived: Long,
    isUsingMulticast: Boolean,
    modifier: Modifier = Modifier
) {
    val qualityScore = remember(latencyMs, networkHealthy, packetsSent, packetsReceived) {
        calculateQualityScore(latencyMs, networkHealthy)
    }

    val (qualityColor, qualityLabel) = remember(qualityScore) {
        when {
            qualityScore >= 90 -> PremiumColors.AuroraGreen to "EXCELLENT"
            qualityScore >= 70 -> PremiumColors.LaserLime to "GOOD"
            qualityScore >= 50 -> PremiumColors.SolarGold to "FAIR"
            else -> PremiumColors.NeonMagenta to "POOR"
        }
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(PremiumColors.SpaceGray)
            .border(
                width = 1.dp,
                color = qualityColor.copy(alpha = 0.3f),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header with quality score
        QualityHeader(
            score = qualityScore,
            label = qualityLabel,
            color = qualityColor,
            isTransmitting = isTransmitting,
            isReceiving = isReceiving
        )

        // Network metrics row
        NetworkMetricsRow(
            latencyMs = latencyMs,
            networkHealthy = networkHealthy,
            isUsingMulticast = isUsingMulticast
        )

        // Signal strength bars (NEW - Feb 2026)
        SignalStrengthIndicator(
            qualityScore = qualityScore,
            latencyMs = latencyMs,
            networkHealthy = networkHealthy
        )

        // Current speaker (when receiving)
        if (isReceiving && currentSpeaker != null) {
            SpeakerIndicator(currentSpeaker)
        }

        // Packet statistics
        PacketStatsRow(
            packetsSent = packetsSent,
            packetsReceived = packetsReceived
        )

        // Audio waveform visualization
        AudioWaveform(
            isTransmitting = isTransmitting,
            isReceiving = isReceiving,
            quality = qualityScore
        )
    }
}

/**
 * Quality score header with animated ring
 */
@Composable
private fun QualityHeader(
    score: Int,
    label: String,
    color: Color,
    isTransmitting: Boolean,
    isReceiving: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "quality-pulse")

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Quality ring
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .scale(if (isTransmitting || isReceiving) pulseScale else 1f)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.2f))
                    .border(
                        width = 2.dp,
                        color = color,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$score",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            }

            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = color,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = when {
                        isTransmitting -> "TRANSMITTING"
                        isReceiving -> "RECEIVING"
                        else -> "STANDBY"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = PremiumColors.TextSecondary
                )
            }
        }

        // Status icon
        Icon(
            when {
                isTransmitting -> Icons.Default.Mic
                isReceiving -> Icons.AutoMirrored.Filled.VolumeUp
                else -> Icons.Default.Wifi
            },
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(24.dp)
        )
    }
}

/**
 * Network metrics row - latency, connection type
 */
@Composable
private fun NetworkMetricsRow(
    latencyMs: Int,
    networkHealthy: Boolean,
    isUsingMulticast: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        NetworkMetricItem(
            icon = Icons.Default.Speed,
            label = "LATENCY",
            value = "${latencyMs}ms",
            color = when {
                latencyMs < 50 -> PremiumColors.AuroraGreen
                latencyMs < 150 -> PremiumColors.SolarGold
                else -> PremiumColors.NeonMagenta
            }
        )

        NetworkMetricItem(
            icon = if (isUsingMulticast) Icons.Default.Hub else Icons.Default.Wifi,
            label = "CONNECTION",
            value = if (isUsingMulticast) "MULTICAST" else "UNICAST",
            color = if (networkHealthy) PremiumColors.AuroraGreen else PremiumColors.NeonMagenta
        )
    }
}

/**
 * Single network metric item
 */
@Composable
private fun NetworkMetricItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    color: Color
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(16.dp)
        )
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = PremiumColors.TextSecondary,
                fontSize = 10.sp
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp
            )
        }
    }
}

/**
 * Signal Strength Indicator - Real-time visual bars
 * NEW Feb 2026: 5-bar signal strength indicator with animated transitions
 *
 * Follows Android signal bar conventions:
 * - 5 bars: Excellent (>90% quality)
 * - 4 bars: Good (70-90% quality)
 * - 3 bars: Fair (50-70% quality)
 * - 2 bars: Poor (30-50% quality)
 * - 1 bar: Very Poor (<30% quality)
 * - 0 bars: No signal
 */
@Composable
private fun SignalStrengthIndicator(
    qualityScore: Int,
    latencyMs: Int,
    networkHealthy: Boolean
) {
    val numBars = when {
        !networkHealthy -> 0
        qualityScore >= 90 -> 5
        qualityScore >= 70 -> 4
        qualityScore >= 50 -> 3
        qualityScore >= 30 -> 2
        qualityScore > 0 -> 1
        else -> 0
    }

    val barColor = when {
        !networkHealthy -> PremiumColors.NeonMagenta
        qualityScore >= 70 -> PremiumColors.AuroraGreen
        qualityScore >= 50 -> PremiumColors.SolarGold
        else -> PremiumColors.NeonMagenta
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(PremiumColors.DeepSpace.copy(alpha = 0.5f))
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            // 5 signal bars - each progressively taller
            (0 until 5).forEach { index ->
                val isActive = index < numBars
                val barHeight = when (index) {
                    0 -> 8.dp
                    1 -> 12.dp
                    2 -> 16.dp
                    3 -> 20.dp
                    4 -> 24.dp
                    else -> 8.dp
                }

                val animatedHeight by animateDpAsState(
                    targetValue = barHeight,
                    animationSpec = tween(
                        durationMillis = 300,
                        easing = EaseOutCubic
                    ),
                    label = "bar-height-$index"
                )

                Box(
                    modifier = Modifier
                        .width(6.dp)
                        .height(animatedHeight)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            if (isActive) barColor
                            else PremiumColors.TextSecondary.copy(alpha = 0.2f)
                        )
                )
            }
        }

        // Signal text description
        Column(
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = when (numBars) {
                    5 -> "EXCELLENT"
                    4 -> "GOOD"
                    3 -> "FAIR"
                    2 -> "POOR"
                    1 -> "VERY POOR"
                    else -> "NO SIGNAL"
                },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = barColor
            )
            Text(
                text = "Signal Strength",
                style = MaterialTheme.typography.labelSmall,
                color = PremiumColors.TextSecondary,
                fontSize = 10.sp
            )
        }
    }
}

/**
 * Current speaker indicator (when receiving)
 */
@Composable
private fun SpeakerIndicator(speakerName: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(PremiumColors.ElectricCyan.copy(alpha = 0.1f))
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(PremiumColors.ElectricCyan)
        )
        Text(
            text = speakerName.take(20),
            style = MaterialTheme.typography.labelSmall,
            color = PremiumColors.ElectricCyan,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}

/**
 * Packet statistics row
 */
@Composable
private fun PacketStatsRow(packetsSent: Long, packetsReceived: Long) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        PacketStatItem(
            label = "SENT",
            value = formatPacketCount(packetsSent)
        )
        PacketStatItem(
            label = "RECEIVED",
            value = formatPacketCount(packetsReceived)
        )
    }
}

@Composable
private fun PacketStatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = PremiumColors.TextPrimary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = PremiumColors.TextSecondary,
            fontSize = 10.sp
        )
    }
}

/**
 * Audio waveform visualization
 * Shows simulated waveform based on transmission state
 */
@Composable
private fun AudioWaveform(
    isTransmitting: Boolean,
    isReceiving: Boolean,
    quality: Int
) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform")

    val animatedOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "offset"
    )

    val isActive = isTransmitting || isReceiving
    val waveHeight = if (isActive) 1f else 0.1f

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
    ) {
        val width = size.width
        val height = size.height
        val centerY = height / 2

        val waveColor = when {
            quality >= 70 -> PremiumColors.AuroraGreen
            quality >= 50 -> PremiumColors.SolarGold
            else -> PremiumColors.NeonMagenta
        }

        val pathPoints = mutableListOf<Offset>()

        for (i in 0..50) {
            val x = (i / 50f) * width
            val normalizedX = i / 50f + animatedOffset

            val amplitude = waveHeight * (height / 4) * (
                kotlin.math.sin(normalizedX * Math.PI * 4) * 0.5f +
                kotlin.math.sin(normalizedX * Math.PI * 2) * 0.3f +
                kotlin.math.sin(normalizedX * Math.PI * 8) * 0.2f
            ).toFloat()

            pathPoints.add(Offset(x, centerY + amplitude))
        }

        // Draw waveform path
        val path = Path().apply {
            if (pathPoints.isNotEmpty()) {
                moveTo(pathPoints.first().x, pathPoints.first().y)
                pathPoints.drop(1).forEach { point ->
                    lineTo(point.x, point.y)
                }
            }
        }

        drawPath(
            path = path,
            color = waveColor,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

/**
 * Calculate quality score (0-100)
 */
private fun calculateQualityScore(
    latencyMs: Int,
    networkHealthy: Boolean
): Int {
    var score = 100

    // Latency penalty
    when {
        latencyMs < 50 -> score -= 0
        latencyMs < 100 -> score -= 10
        latencyMs < 200 -> score -= 30
        latencyMs < 500 -> score -= 50
        else -> score -= 70
    }

    // Network health penalty
    if (!networkHealthy) {
        score -= 20
    }

    return score.coerceAtLeast(0)
}

/**
 * Format packet count for display
 */
private fun formatPacketCount(count: Long): String {
    return when {
        count >= 1_000_000 -> "${count / 1_000_000}M"
        count >= 1_000 -> "${count / 1_000}K"
        else -> count.toString()
    }
}
