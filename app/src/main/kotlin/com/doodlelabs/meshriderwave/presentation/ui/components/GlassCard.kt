/*
 * Mesh Rider Wave - Glassmorphism Card Component
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 *
 * Reusable glass card with blur effect
 * Following Single Responsibility Principle (SRP)
 */

package com.doodlelabs.meshriderwave.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.doodlelabs.meshriderwave.presentation.ui.theme.PremiumColors

/**
 * Premium Glassmorphism Card
 *
 * Features:
 * - Frosted glass effect with blur
 * - Subtle border highlight
 * - Customizable corner radius
 * - Optional gradient overlay
 * - Optional click handling
 *
 * @param modifier Modifier for customization
 * @param cornerRadius Corner radius of the card
 * @param backgroundColor Base glass color
 * @param borderColor Border highlight color
 * @param blurRadius Blur amount for glass effect
 * @param onClick Optional click handler
 * @param content Content inside the card
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    backgroundColor: Color = PremiumColors.GlassWhite,
    borderColor: Color = PremiumColors.GlassBorder,
    blurRadius: Dp = 0.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)

    Box(
        modifier = modifier
            .clip(shape)
            .background(backgroundColor)
            .then(
                if (blurRadius > 0.dp) Modifier.blur(blurRadius) else Modifier
            )
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
            )
            .border(
                width = 1.dp,
                color = borderColor,
                shape = shape
            )
    ) {
        content()
    }
}

/**
 * Premium Gradient Glass Card
 *
 * Glass card with gradient overlay for hero sections
 */
@Composable
fun GradientGlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    gradient: Brush = PremiumColors.PrimaryGradient,
    overlayOpacity: Float = 0.15f,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)

    Box(
        modifier = modifier
            .clip(shape)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = overlayOpacity),
                        Color.White.copy(alpha = overlayOpacity * 0.5f)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = gradient,
                shape = shape
            )
    ) {
        content()
    }
}

/**
 * Elevated Glass Card with shadow effect
 */
@Composable
fun ElevatedGlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    elevation: Dp = 8.dp,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
    ) {
        // Shadow layer
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(y = elevation / 2)
                .blur(elevation)
                .clip(RoundedCornerShape(cornerRadius))
                .background(Color.Black.copy(alpha = 0.3f))
        )

        // Glass card
        GlassCard(
            cornerRadius = cornerRadius,
            content = content
        )
    }
}
