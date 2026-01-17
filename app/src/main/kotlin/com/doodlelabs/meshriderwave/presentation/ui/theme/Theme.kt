/*
 * Mesh Rider Wave - Material 3 Theme
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 *
 * Military-grade tactical UI with DoodleLabs branding
 */

package com.doodlelabs.meshriderwave.presentation.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// DoodleLabs Brand Colors
object MeshRiderColors {
    // Primary - Tactical Blue
    val Primary = Color(0xFF1565C0)
    val PrimaryVariant = Color(0xFF0D47A1)
    val PrimaryLight = Color(0xFF5E92F3)

    // Secondary - Accent Orange (DoodleLabs)
    val Secondary = Color(0xFFFF6F00)
    val SecondaryVariant = Color(0xFFE65100)
    val SecondaryLight = Color(0xFFFFA040)

    // Tactical Greens
    val TacticalGreen = Color(0xFF2E7D32)
    val TacticalGreenLight = Color(0xFF4CAF50)

    // Status Colors
    val Online = Color(0xFF4CAF50)
    val Offline = Color(0xFF9E9E9E)
    val Busy = Color(0xFFF44336)
    val Connecting = Color(0xFFFFC107)

    // Call UI
    val CallAccept = Color(0xFF4CAF50)
    val CallDecline = Color(0xFFF44336)
    val CallBackground = Color(0xFF121212)

    // Surface Colors (Dark)
    val SurfaceDark = Color(0xFF121212)
    val SurfaceContainerDark = Color(0xFF1E1E1E)
    val SurfaceVariantDark = Color(0xFF2C2C2C)

    // Surface Colors (Light)
    val SurfaceLight = Color(0xFFFFFBFE)
    val SurfaceContainerLight = Color(0xFFF5F5F5)
    val SurfaceVariantLight = Color(0xFFE7E0EC)
}

private val DarkColorScheme = darkColorScheme(
    primary = MeshRiderColors.Primary,
    onPrimary = Color.White,
    primaryContainer = MeshRiderColors.PrimaryVariant,
    onPrimaryContainer = Color.White,

    secondary = MeshRiderColors.Secondary,
    onSecondary = Color.Black,
    secondaryContainer = MeshRiderColors.SecondaryVariant,
    onSecondaryContainer = Color.White,

    tertiary = MeshRiderColors.TacticalGreen,
    onTertiary = Color.White,

    background = MeshRiderColors.SurfaceDark,
    onBackground = Color.White,

    surface = MeshRiderColors.SurfaceDark,
    onSurface = Color.White,
    surfaceVariant = MeshRiderColors.SurfaceVariantDark,
    onSurfaceVariant = Color(0xFFCAC4D0),

    outline = Color(0xFF938F99),
    outlineVariant = Color(0xFF49454F),

    error = Color(0xFFCF6679),
    onError = Color.Black
)

private val LightColorScheme = lightColorScheme(
    primary = MeshRiderColors.Primary,
    onPrimary = Color.White,
    primaryContainer = MeshRiderColors.PrimaryLight,
    onPrimaryContainer = Color.White,

    secondary = MeshRiderColors.Secondary,
    onSecondary = Color.White,
    secondaryContainer = MeshRiderColors.SecondaryLight,
    onSecondaryContainer = Color.Black,

    tertiary = MeshRiderColors.TacticalGreen,
    onTertiary = Color.White,

    background = MeshRiderColors.SurfaceLight,
    onBackground = Color.Black,

    surface = MeshRiderColors.SurfaceLight,
    onSurface = Color.Black,
    surfaceVariant = MeshRiderColors.SurfaceVariantLight,
    onSurfaceVariant = Color(0xFF49454F),

    outline = Color(0xFF79747E),
    outlineVariant = Color(0xFFCAC4D0),

    error = Color(0xFFB3261E),
    onError = Color.White
)

@Composable
fun MeshRiderWaveTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Disable for consistent branding
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MeshRiderTypography,
        content = content
    )
}
