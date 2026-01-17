/*
 * Mesh Rider Wave - Animated Background Components
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 *
 * Premium animated backgrounds for 2026 UI
 * - Mesh gradient animation
 * - Aurora borealis effect
 * - Particle system (optional)
 */

package com.doodlelabs.meshriderwave.presentation.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.doodlelabs.meshriderwave.presentation.ui.theme.PremiumColors
import kotlin.math.cos
import kotlin.math.sin

/**
 * Premium Animated Mesh Gradient Background
 *
 * Creates a slowly moving mesh gradient effect that adds depth
 * to the app without being distracting.
 *
 * @param modifier Modifier for customization
 * @param animationSpeed Speed multiplier (0.5 = half speed, 2.0 = double speed)
 * @param colors Gradient colors to use
 * @param content Content to display on top
 */
@Composable
fun AnimatedMeshBackground(
    modifier: Modifier = Modifier,
    animationSpeed: Float = 1f,
    colors: List<Color> = listOf(
        PremiumColors.ElectricCyanGlow,
        PremiumColors.HoloPurple.copy(alpha = 0.3f),
        PremiumColors.NeonMagentaGlow
    ),
    content: @Composable BoxScope.() -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "meshBackground")

    val offset1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = (8000 / animationSpeed).toInt(),
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "offset1"
    )

    val offset2 by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = (12000 / animationSpeed).toInt(),
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "offset2"
    )

    val offset3 by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = (10000 / animationSpeed).toInt(),
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "offset3"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PremiumColors.DeepSpace)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawMeshGradient(colors, offset1, offset2, offset3)
        }
        content()
    }
}

private fun DrawScope.drawMeshGradient(
    colors: List<Color>,
    offset1: Float,
    offset2: Float,
    offset3: Float
) {
    val width = size.width
    val height = size.height

    // First gradient blob
    val center1 = Offset(
        x = width * (0.2f + offset1 * 0.3f),
        y = height * (0.3f + offset2 * 0.2f)
    )

    // Second gradient blob
    val center2 = Offset(
        x = width * (0.7f - offset2 * 0.2f),
        y = height * (0.6f + offset3 * 0.2f)
    )

    // Third gradient blob
    val center3 = Offset(
        x = width * (0.5f + offset3 * 0.2f),
        y = height * (0.8f - offset1 * 0.3f)
    )

    // Draw radial gradients for each blob
    colors.forEachIndexed { index, color ->
        val center = when (index % 3) {
            0 -> center1
            1 -> center2
            else -> center3
        }

        val radius = minOf(width, height) * (0.5f + (index * 0.1f))

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(color, Color.Transparent),
                center = center,
                radius = radius
            ),
            center = center,
            radius = radius
        )
    }
}

/**
 * Aurora Borealis Animated Background
 *
 * Creates a northern lights effect with flowing colors.
 */
@Composable
fun AuroraBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "aurora")

    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 20000,
                easing = LinearEasing
            )
        ),
        label = "auroraPhase"
    )

    val intensity by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 5000,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "auroraIntensity"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        PremiumColors.DeepSpace,
                        Color(0xFF0D1117),
                        Color(0xFF0A0E14),
                        PremiumColors.DeepSpace
                    )
                )
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawAurora(phase, intensity)
        }
        content()
    }
}

private fun DrawScope.drawAurora(phase: Float, intensity: Float) {
    val width = size.width
    val height = size.height

    val colors = listOf(
        PremiumColors.ElectricCyan.copy(alpha = intensity * 0.5f),
        PremiumColors.HoloPurple.copy(alpha = intensity * 0.4f),
        PremiumColors.LaserLime.copy(alpha = intensity * 0.3f)
    )

    colors.forEachIndexed { index, color ->
        val phaseOffset = phase + (index * 120)
        val rad = Math.toRadians(phaseOffset.toDouble())

        val points = mutableListOf<Offset>()
        for (x in 0..100) {
            val normalizedX = x / 100f
            val y = (sin(rad + normalizedX * 4) * 0.1f +
                    cos(rad * 0.5 + normalizedX * 2) * 0.05f +
                    0.2f + (index * 0.15f)).toFloat()

            points.add(Offset(width * normalizedX, height * y))
        }

        // Draw gradient wave
        for (i in 0 until points.size - 1) {
            val startPoint = points[i]
            val endPoint = points[i + 1]

            drawLine(
                brush = Brush.verticalGradient(
                    colors = listOf(color, Color.Transparent),
                    startY = startPoint.y,
                    endY = startPoint.y + height * 0.3f
                ),
                start = startPoint,
                end = endPoint,
                strokeWidth = height * 0.15f,
                cap = StrokeCap.Round
            )
        }
    }
}

/**
 * Static Deep Space Background
 *
 * Simple gradient background without animation for lower-end devices.
 */
@Composable
fun DeepSpaceBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(brush = PremiumColors.AuroraGradient)
    ) {
        content()
    }
}

/**
 * Pulsing Glow Background
 *
 * Subtle pulsing glow effect behind content.
 */
@Composable
fun PulsingGlowBackground(
    modifier: Modifier = Modifier,
    glowColor: Color = PremiumColors.ElectricCyan,
    content: @Composable BoxScope.() -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulseGlow")

    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    val glowScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowScale"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PremiumColors.DeepSpace)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerX = size.width / 2
            val centerY = size.height * 0.4f
            val baseRadius = minOf(size.width, size.height) * 0.4f

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        glowColor.copy(alpha = glowAlpha),
                        Color.Transparent
                    ),
                    center = Offset(centerX, centerY),
                    radius = baseRadius * glowScale
                ),
                center = Offset(centerX, centerY),
                radius = baseRadius * glowScale
            )
        }
        content()
    }
}

/**
 * Gradient Overlay
 *
 * Applies a gradient overlay on top of content for visual depth.
 */
@Composable
fun GradientOverlay(
    modifier: Modifier = Modifier,
    gradient: Brush = Brush.verticalGradient(
        colors = listOf(
            Color.Transparent,
            PremiumColors.DeepSpace.copy(alpha = 0.8f),
            PremiumColors.DeepSpace
        )
    ),
    content: @Composable BoxScope.() -> Unit
) {
    Box(modifier = modifier.fillMaxSize()) {
        content()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(brush = gradient)
        )
    }
}
