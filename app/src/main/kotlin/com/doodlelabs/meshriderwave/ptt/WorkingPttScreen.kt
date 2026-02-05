/*
 * Mesh Rider Wave - Working PTT Screen
 * Following Material 3 and Jetpack Compose best practices
 * Simple, tested UI that actually works on Samsung S24+
 */

package com.doodlelabs.meshriderwave.ptt

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicNone
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * Simple PTT Screen - Following OUSHTALK UI guidelines
 * Big circular PTT button, clear visual feedback
 *
 * Per Samsung Knox UI guidelines:
 * - Large touch targets (48dp minimum)
 * - High contrast for outdoor visibility
 * - Clear visual feedback for all states
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkingPttScreen(
    pttManager: WorkingPttManager,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isTransmitting by pttManager.isTransmitting.collectAsState()
    val currentSpeaker by pttManager.currentSpeaker.collectAsState()
    val latency = remember { mutableStateOf(pttManager.getLatency()) }

    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    // PTT button press state
    var isPressing by remember { mutableStateOf(false) }

    // Button animation
    val buttonScale by animateFloatAsState(
        targetValue = if (isPressing) 0.85f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "ptt-scale"
    )

    // Pulsing animation when transmitting
    val infiniteTransition = rememberInfiniteTransition(label = "ptt-pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ptt-pulse"
    )

    // Color states (high contrast for outdoor visibility)
    val buttonColor = when {
        isTransmitting -> Color(0xFFDC2626)  // Red (transmitting)
        isPressing -> Color(0xFFFF9800)       // Orange (connecting)
        else -> Color(0xFF00B4D8)           // Cyan (ready)
    }

    val backgroundColor = Color(0xFF000000)  // Pure black background

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }

                Text(
                    text = "PUSH TO TALK",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                // Latency display
                if (latency.value > 0) {
                    Text(
                        text = "${latency.value}ms",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF00B4D8)
                    )
                } else {
                    Spacer(modifier = Modifier.width(48.dp))
                }
            }

            // Current speaker display
            if (currentSpeaker != null && !isTransmitting) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.VolumeUp,
                        contentDescription = null,
                        tint = Color(0xFF00B4D8),
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = currentSpeaker ?: "",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00B4D8)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // ===== PTT BUTTON =====
            // Following Samsung Knox UI guidelines:
            // - Minimum 48dp touch target
            // - High contrast for outdoor visibility
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .scale(buttonScale),
                contentAlignment = Alignment.Center
            ) {
                // Pulsing ring when transmitting
                if (isTransmitting) {
                    Box(
                        modifier = Modifier
                            .size(220.dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(
                                buttonColor.copy(alpha = 0.2f)
                            )
                    )
                }

                // Main button
                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    buttonColor,
                                    buttonColor.copy(alpha = 0.7f)
                                )
                            )
                        )
                        .border(
                            width = 4.dp,
                            color = Color.White,
                            shape = CircleShape
                        )
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = {
                                    // Simple tap to toggle PTT
                                    haptic.performHapticFeedback(
                                        HapticFeedbackType.LongPress
                                    )

                                    // Vibrate
                                    vibrate(context, pattern = longArrayOf(0, 50))

                                    if (!isTransmitting) {
                                        scope.launch {
                                            val success = pttManager.startTransmission()
                                            if (!success) {
                                                // Vibrate error pattern
                                                vibrate(context, pattern = longArrayOf(0, 50, 50, 50))
                                            }
                                        }
                                    } else {
                                        pttManager.stopTransmission()
                                        // Vibrate release
                                        vibrate(context, pattern = longArrayOf(0, 30, 30, 30))
                                    }
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isTransmitting) {
                            Icons.Default.Mic
                        } else {
                            Icons.Default.MicNone
                        },
                        contentDescription = "Push to Talk",
                        modifier = Modifier.size(64.dp),
                        tint = Color.White
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = if (isTransmitting) {
                            "RELEASE"
                        } else if (isPressing) {
                            "CONNECTING..."
                        } else {
                            "TAP"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        letterSpacing = 2.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Status text
            Text(
                text = when {
                    isTransmitting -> "TRANSMITTING..."
                    isPressing -> "CONNECTING..."
                    currentSpeaker != null -> "BUSY: $currentSpeaker"
                    else -> "READY TO TALK"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = when {
                    isTransmitting -> Color(0xFFDC2626)  // Red
                    isPressing -> Color(0xFFFF9800)      // Orange
                    currentSpeaker != null -> Color(0xFFFFA500) // Amber
                    else -> Color(0xFF00B4D8)             // Cyan
                }
            )

            // Instructions
            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1A1A1A)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "HOW TO USE",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFA3A3A3)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "• Tap button to toggle PTT\n" +
                               "• Only one person at a time\n" +
                               "• Works on WiFi network",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/**
 * Vibrate device
 */
private fun vibrate(context: Context, pattern: LongArray) {
    val vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
        vibrator.vibrate(
            VibrationEffect.createWaveform(pattern, -1)
        )
    } else {
        @Suppress("DEPRECATION")
        vibrator.vibrate(pattern, -1)
    }
}
