/*
 * Mesh Rider Wave - Ultra Premium Theme 2026
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 *
 * Design Trends Implemented:
 * - Apple Liquid Glass / Glassmorphism
 * - Material 3 Expressive
 * - Neumorphic elements
 * - Gradient Mesh backgrounds
 * - Microinteractions
 */

package com.doodlelabs.meshriderwave.presentation.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

// ============================================================================
// ULTRA PREMIUM COLOR PALETTE 2026
// ============================================================================

object PremiumColors {
    // Primary - Electric Cyan (Futuristic)
    val ElectricCyan = Color(0xFF00D9FF)
    val ElectricCyanDark = Color(0xFF00A8CC)
    val ElectricCyanLight = Color(0xFF80ECFF)
    val ElectricCyanGlow = Color(0x4000D9FF) // 25% opacity for glow

    // Secondary - Neon Magenta (Energy)
    val NeonMagenta = Color(0xFFFF006E)
    val NeonMagentaDark = Color(0xFFCC0058)
    val NeonMagentaLight = Color(0xFFFF4D94)
    val NeonMagentaGlow = Color(0x40FF006E)

    // Tertiary - Holographic Purple
    val HoloPurple = Color(0xFF8B5CF6)
    val HoloPurpleDark = Color(0xFF6D28D9)
    val HoloPurpleLight = Color(0xFFA78BFA)

    // Accent - Laser Lime (Call Accept)
    val LaserLime = Color(0xFF39FF14)
    val LaserLimeDark = Color(0xFF2ECC0F)
    val LaserLimeGlow = Color(0x4039FF14)

    // Status Colors
    val OnlineGlow = Color(0xFF00FF88)
    val OfflineGray = Color(0xFF4A4A4A)
    val BusyRed = Color(0xFFFF3366)
    val ConnectingAmber = Color(0xFFFFAA00)

    // Premium Accent Colors (New)
    val AuroraGreen = Color(0xFF00FF88)  // Alias for OnlineGlow
    val SolarGold = Color(0xFFFFB800)    // Warm gold for warnings/PTT

    // Surface Colors - Deep Space
    val DeepSpace = Color(0xFF0A0A0F)
    val SpaceGray = Color(0xFF12121A)
    val SpaceGrayLight = Color(0xFF1A1A24)
    val SpaceGrayLighter = Color(0xFF22222E)

    // Glass Colors
    val GlassWhite = Color(0x1AFFFFFF) // 10% white
    val GlassBorder = Color(0x33FFFFFF) // 20% white
    val GlassDark = Color(0x0DFFFFFF) // 5% white

    // Text
    val TextPrimary = Color(0xFFF5F5F7)
    val TextSecondary = Color(0xFFB0B0B8)
    val TextTertiary = Color(0xFF707078)

    // Gradients
    val PrimaryGradient = Brush.linearGradient(
        colors = listOf(ElectricCyan, HoloPurple)
    )

    val SecondaryGradient = Brush.linearGradient(
        colors = listOf(NeonMagenta, HoloPurple)
    )

    val CallAcceptGradient = Brush.linearGradient(
        colors = listOf(LaserLime, Color(0xFF00D68F))
    )

    val CallDeclineGradient = Brush.linearGradient(
        colors = listOf(NeonMagenta, BusyRed)
    )

    val MeshGradient = Brush.radialGradient(
        colors = listOf(
            ElectricCyanGlow,
            HoloPurple.copy(alpha = 0.3f),
            NeonMagentaGlow,
            Color.Transparent
        )
    )

    // Aurora Background Gradient
    val AuroraGradient = Brush.verticalGradient(
        colors = listOf(
            DeepSpace,
            Color(0xFF0D1117),
            Color(0xFF0A0E14),
            DeepSpace
        )
    )
}

// ============================================================================
// PREMIUM DARK COLOR SCHEME
// ============================================================================

private val PremiumDarkColorScheme = darkColorScheme(
    // Primary
    primary = PremiumColors.ElectricCyan,
    onPrimary = PremiumColors.DeepSpace,
    primaryContainer = PremiumColors.ElectricCyanDark,
    onPrimaryContainer = PremiumColors.TextPrimary,

    // Secondary
    secondary = PremiumColors.NeonMagenta,
    onSecondary = Color.White,
    secondaryContainer = PremiumColors.NeonMagentaDark,
    onSecondaryContainer = Color.White,

    // Tertiary
    tertiary = PremiumColors.HoloPurple,
    onTertiary = Color.White,
    tertiaryContainer = PremiumColors.HoloPurpleDark,
    onTertiaryContainer = Color.White,

    // Background & Surface
    background = PremiumColors.DeepSpace,
    onBackground = PremiumColors.TextPrimary,
    surface = PremiumColors.SpaceGray,
    onSurface = PremiumColors.TextPrimary,
    surfaceVariant = PremiumColors.SpaceGrayLight,
    onSurfaceVariant = PremiumColors.TextSecondary,

    // Surface tones
    surfaceTint = PremiumColors.ElectricCyan,
    inverseSurface = PremiumColors.TextPrimary,
    inverseOnSurface = PremiumColors.DeepSpace,

    // Outline
    outline = PremiumColors.GlassBorder,
    outlineVariant = PremiumColors.GlassWhite,

    // Error
    error = PremiumColors.BusyRed,
    onError = Color.White,
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

// ============================================================================
// PREMIUM LIGHT COLOR SCHEME (Minimal, Clean)
// ============================================================================

private val PremiumLightColorScheme = lightColorScheme(
    primary = Color(0xFF0066CC),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E4FF),
    onPrimaryContainer = Color(0xFF001A40),

    secondary = Color(0xFFD6336C),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFD9E3),
    onSecondaryContainer = Color(0xFF3E0021),

    tertiary = Color(0xFF7048E8),
    onTertiary = Color.White,

    background = Color(0xFFFAFAFC),
    onBackground = Color(0xFF1A1A1F),
    surface = Color.White,
    onSurface = Color(0xFF1A1A1F),
    surfaceVariant = Color(0xFFF0F0F5),
    onSurfaceVariant = Color(0xFF45454A),

    outline = Color(0xFFD0D0D5),
    outlineVariant = Color(0xFFE5E5EA)
)

// ============================================================================
// PREMIUM SHAPES
// ============================================================================

val PremiumShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

// ============================================================================
// ANIMATION SPECS - Smooth & Premium Feel
// ============================================================================

object PremiumAnimations {
    // Spring animations for natural feel
    val BouncySpring = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessLow
    )

    val SmoothSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    // Tween for controlled animations
    val FastEase = tween<Float>(
        durationMillis = 200,
        easing = FastOutSlowInEasing
    )

    val MediumEase = tween<Float>(
        durationMillis = 350,
        easing = FastOutSlowInEasing
    )

    val SlowEase = tween<Float>(
        durationMillis = 500,
        easing = FastOutSlowInEasing
    )

    // Infinite animations for pulsing effects
    val PulseSpec = infiniteRepeatable<Float>(
        animation = tween(1500, easing = FastOutSlowInEasing),
        repeatMode = RepeatMode.Reverse
    )

    val GlowSpec = infiniteRepeatable<Float>(
        animation = tween(2000, easing = LinearEasing),
        repeatMode = RepeatMode.Reverse
    )
}

// ============================================================================
// PREMIUM THEME COMPOSABLE
// ============================================================================

@Composable
fun MeshRiderPremiumTheme(
    darkTheme: Boolean = true, // Premium look is best in dark
    dynamicColor: Boolean = false, // Disable for brand consistency
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = androidx.compose.ui.platform.LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> PremiumDarkColorScheme
        else -> PremiumLightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            // CRASH-FIX Jan 2026: Safe cast to prevent ClassCastException if context isn't Activity
            val activity = view.context as? Activity
            activity?.window?.let { window ->
                window.statusBarColor = Color.Transparent.toArgb()
                window.navigationBarColor = Color.Transparent.toArgb()
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = PremiumTypography,
        shapes = PremiumShapes,
        content = content
    )
}

// ============================================================================
// HELPER COMPOSABLES FOR PREMIUM EFFECTS
// ============================================================================

/**
 * Animated glow color for pulsing effects
 */
@Composable
fun animatedGlowColor(
    baseColor: Color,
    glowColor: Color = baseColor.copy(alpha = 0.6f)
): State<Color> {
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = PremiumAnimations.GlowSpec,
        label = "glowAlpha"
    )
    return remember(alpha, baseColor, glowColor) {
        derivedStateOf {
            glowColor.copy(alpha = alpha)
        }
    }
}

/**
 * Animated scale for press effect
 */
@Composable
fun animatedPressScale(pressed: Boolean): State<Float> {
    return animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = PremiumAnimations.BouncySpring,
        label = "pressScale"
    )
}

