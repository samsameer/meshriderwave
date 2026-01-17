/*
 * Mesh Rider Wave - Loading Indicator Components
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 *
 * Premium loading indicators and progress animations
 * - Pulsing dots
 * - Spinning arc
 * - Skeleton loaders
 *
 * SOLID: Single Responsibility - Only loading states
 * DRY: Reusable across all screens
 */

package com.doodlelabs.meshriderwave.presentation.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.doodlelabs.meshriderwave.presentation.ui.theme.PremiumColors

/**
 * Premium Pulsing Dots Loader
 *
 * Three dots that pulse in sequence, Apple-style.
 */
@Composable
fun PulsingDotsLoader(
    modifier: Modifier = Modifier,
    color: Color = PremiumColors.ElectricCyan,
    dotSize: Dp = 10.dp,
    spacing: Dp = 6.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulsingDots")

    val delays = listOf(0, 150, 300)

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        delays.forEachIndexed { index, delay ->
            val scale by infiniteTransition.animateFloat(
                initialValue = 0.6f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = 800
                        0.6f at 0
                        1f at 200
                        0.6f at 400
                        0.6f at 800
                    },
                    repeatMode = RepeatMode.Restart,
                    initialStartOffset = StartOffset(delay)
                ),
                label = "dot$index"
            )

            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.4f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = 800
                        0.4f at 0
                        1f at 200
                        0.4f at 400
                        0.4f at 800
                    },
                    repeatMode = RepeatMode.Restart,
                    initialStartOffset = StartOffset(delay)
                ),
                label = "dotAlpha$index"
            )

            Box(
                modifier = Modifier
                    .size(dotSize)
                    .scale(scale)
                    .alpha(alpha)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

/**
 * Premium Spinning Arc Loader
 *
 * Animated gradient arc spinner.
 */
@Composable
fun SpinningArcLoader(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    strokeWidth: Dp = 4.dp,
    colors: List<Color> = listOf(
        PremiumColors.ElectricCyan,
        PremiumColors.HoloPurple
    )
) {
    val infiniteTransition = rememberInfiniteTransition(label = "spinningArc")

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing)
        ),
        label = "rotation"
    )

    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 30f,
        targetValue = 270f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sweep"
    )

    Canvas(
        modifier = modifier
            .size(size)
            .rotate(rotation)
    ) {
        val diameter = size.toPx() - strokeWidth.toPx()
        val topLeft = Offset(strokeWidth.toPx() / 2, strokeWidth.toPx() / 2)

        drawArc(
            brush = Brush.sweepGradient(
                colors = colors + colors.first()
            ),
            startAngle = 0f,
            sweepAngle = sweepAngle,
            useCenter = false,
            topLeft = topLeft,
            size = Size(diameter, diameter),
            style = Stroke(
                width = strokeWidth.toPx(),
                cap = StrokeCap.Round
            )
        )
    }
}

/**
 * Premium Ripple Loader
 *
 * Expanding circles ripple effect.
 */
@Composable
fun RippleLoader(
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    color: Color = PremiumColors.ElectricCyan,
    rippleCount: Int = 3
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ripple")

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        repeat(rippleCount) { index ->
            val delay = (index * 400)

            val scale by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = rippleCount * 400,
                        easing = FastOutSlowInEasing
                    ),
                    repeatMode = RepeatMode.Restart,
                    initialStartOffset = StartOffset(delay)
                ),
                label = "rippleScale$index"
            )

            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.8f,
                targetValue = 0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = rippleCount * 400,
                        easing = FastOutSlowInEasing
                    ),
                    repeatMode = RepeatMode.Restart,
                    initialStartOffset = StartOffset(delay)
                ),
                label = "rippleAlpha$index"
            )

            Box(
                modifier = Modifier
                    .size(size)
                    .scale(scale)
                    .alpha(alpha)
                    .clip(CircleShape)
                    .background(Color.Transparent)
                    .drawBehind {
                        drawCircle(
                            color = color,
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }
            )
        }
    }
}

/**
 * Premium Skeleton Loader
 *
 * Shimmer effect placeholder for loading content.
 */
@Composable
fun SkeletonLoader(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(8.dp)
) {
    val infiniteTransition = rememberInfiniteTransition(label = "skeleton")

    val shimmerPosition by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing)
        ),
        label = "shimmer"
    )

    val shimmerBrush = Brush.linearGradient(
        colors = listOf(
            PremiumColors.SpaceGrayLight,
            PremiumColors.SpaceGrayLighter,
            PremiumColors.SpaceGrayLight
        ),
        start = Offset(shimmerPosition * 1000f - 200f, 0f),
        end = Offset(shimmerPosition * 1000f + 200f, 0f)
    )

    Box(
        modifier = modifier
            .clip(shape)
            .background(brush = shimmerBrush)
    )
}

/**
 * Skeleton Contact Card
 *
 * Placeholder for loading contact list items.
 */
@Composable
fun SkeletonContactCard(
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar skeleton
        SkeletonLoader(
            modifier = Modifier.size(56.dp),
            shape = CircleShape
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            // Name skeleton
            SkeletonLoader(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(16.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Status skeleton
            SkeletonLoader(
                modifier = Modifier
                    .fillMaxWidth(0.4f)
                    .height(12.dp)
            )
        }
    }
}

/**
 * Loading Overlay
 *
 * Full-screen loading overlay with dim background.
 */
@Composable
fun LoadingOverlay(
    isLoading: Boolean,
    modifier: Modifier = Modifier,
    message: String? = null
) {
    if (isLoading) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(PremiumColors.DeepSpace.copy(alpha = 0.8f)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SpinningArcLoader(size = 56.dp)

                if (message != null) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = PremiumColors.TextSecondary
                    )
                }
            }
        }
    }
}

/**
 * Call Connecting Animation
 *
 * Specific animation for call connecting state.
 */
@Composable
fun CallConnectingLoader(
    modifier: Modifier = Modifier,
    size: Dp = 120.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "callConnecting")

    val outerRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing)
        ),
        label = "outerRotation"
    )

    val innerRotation by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing)
        ),
        label = "innerRotation"
    )

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        // Outer ring
        Canvas(
            modifier = Modifier
                .size(size)
                .rotate(outerRotation)
        ) {
            val strokeWidth = 3.dp.toPx()
            val diameter = size.toPx() - strokeWidth

            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        PremiumColors.ElectricCyan,
                        PremiumColors.HoloPurple,
                        Color.Transparent,
                        PremiumColors.ElectricCyan
                    )
                ),
                startAngle = 0f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                size = Size(diameter, diameter),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }

        // Inner ring
        Canvas(
            modifier = Modifier
                .size(size * 0.7f)
                .rotate(innerRotation)
        ) {
            val strokeWidth = 2.dp.toPx()
            val diameter = (size * 0.7f).toPx() - strokeWidth

            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        PremiumColors.NeonMagenta,
                        PremiumColors.HoloPurple,
                        Color.Transparent,
                        PremiumColors.NeonMagenta
                    )
                ),
                startAngle = 0f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                size = Size(diameter, diameter),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }

        // Center pulse
        Box(
            modifier = Modifier
                .size(size * 0.3f)
                .scale(pulseScale)
                .clip(CircleShape)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            PremiumColors.ElectricCyan.copy(alpha = 0.6f),
                            Color.Transparent
                        )
                    )
                )
        )
    }
}

/**
 * Simple Material Progress Indicator (themed)
 *
 * For cases where simple indicator is preferred.
 */
@Composable
fun ThemedProgressIndicator(
    modifier: Modifier = Modifier,
    color: Color = PremiumColors.ElectricCyan
) {
    CircularProgressIndicator(
        modifier = modifier,
        color = color,
        trackColor = PremiumColors.SpaceGrayLight
    )
}
