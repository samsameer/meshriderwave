/*
 * Mesh Rider Wave - Premium Dialog Components
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 *
 * World-class glassmorphism dialogs with Apple/Google level polish
 * - Animated entrances/exits
 * - Glassmorphism backgrounds
 * - Premium typography
 * - Haptic feedback
 */

package com.doodlelabs.meshriderwave.presentation.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.doodlelabs.meshriderwave.presentation.ui.theme.PremiumColors

/**
 * Permission types for the permission dialog
 */
enum class PermissionType(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val accentColor: Color
) {
    MICROPHONE(
        icon = Icons.Outlined.Mic,
        title = "Microphone Access",
        description = "Required for voice calls and Push-to-Talk functionality",
        accentColor = PremiumColors.ElectricCyan
    ),
    CAMERA(
        icon = Icons.Outlined.Videocam,
        title = "Camera Access",
        description = "Required for video calls and QR code scanning",
        accentColor = PremiumColors.AuroraGreen
    ),
    LOCATION(
        icon = Icons.Outlined.LocationOn,
        title = "Location Access",
        description = "Required for Blue Force Tracking and team coordination",
        accentColor = PremiumColors.SolarGold
    ),
    NOTIFICATIONS(
        icon = Icons.Outlined.Notifications,
        title = "Notification Access",
        description = "Get notified about incoming calls, messages, and SOS alerts",
        accentColor = PremiumColors.NeonMagenta
    ),
    BLUETOOTH(
        icon = Icons.Outlined.Bluetooth,
        title = "Bluetooth Access",
        description = "Required for audio device connectivity and mesh discovery",
        accentColor = PremiumColors.ElectricCyan
    ),
    STORAGE(
        icon = Icons.Outlined.Folder,
        title = "Storage Access",
        description = "Required for saving messages and call recordings",
        accentColor = PremiumColors.AuroraGreen
    )
}

/**
 * Premium Permission Dialog
 *
 * Beautiful, animated permission request dialog with glassmorphism design.
 *
 * @param permissionType Type of permission being requested
 * @param onAllow Callback when user allows permission
 * @param onDeny Callback when user denies permission
 * @param onDismiss Callback when dialog is dismissed
 */
@Composable
fun PremiumPermissionDialog(
    permissionType: PermissionType,
    onAllow: () -> Unit,
    onDeny: () -> Unit,
    onDismiss: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    // Animation states
    var isVisible by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0.8f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "scale"
    )

    LaunchedEffect(Unit) {
        isVisible = true
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.Center
        ) {
            // Dialog content
            Box(
                modifier = Modifier
                    .padding(32.dp)
                    .scale(scale)
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                PremiumColors.SpaceGrayLight.copy(alpha = 0.95f),
                                PremiumColors.SpaceGray.copy(alpha = 0.98f)
                            )
                        )
                    )
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.2f),
                                Color.White.copy(alpha = 0.05f)
                            )
                        ),
                        shape = RoundedCornerShape(28.dp)
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { } // Prevent click-through
                    )
            ) {
                Column(
                    modifier = Modifier
                        .padding(28.dp)
                        .widthIn(max = 340.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Icon with glow
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        permissionType.accentColor.copy(alpha = 0.3f),
                                        permissionType.accentColor.copy(alpha = 0.1f),
                                        Color.Transparent
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(permissionType.accentColor.copy(alpha = 0.15f))
                                .border(
                                    width = 1.dp,
                                    color = permissionType.accentColor.copy(alpha = 0.3f),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = permissionType.icon,
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                                tint = permissionType.accentColor
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Title
                    Text(
                        text = permissionType.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = PremiumColors.TextPrimary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Description
                    Text(
                        text = permissionType.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = PremiumColors.TextSecondary,
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Privacy note
                    Row(
                        modifier = Modifier
                            .padding(vertical = 8.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(PremiumColors.AuroraGreen.copy(alpha = 0.1f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Shield,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = PremiumColors.AuroraGreen
                        )
                        Text(
                            text = "Data stays on your device",
                            style = MaterialTheme.typography.labelSmall,
                            color = PremiumColors.AuroraGreen,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    // Allow button (primary)
                    PremiumButton(
                        text = "Allow Access",
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onAllow()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = permissionType.accentColor
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Deny button (secondary)
                    TextButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onDeny()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Not Now",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = PremiumColors.TextSecondary
                        )
                    }
                }
            }
        }
    }
}

/**
 * Premium Confirmation Dialog
 *
 * For confirmations with destructive or important actions.
 */
@Composable
fun PremiumConfirmDialog(
    title: String,
    message: String,
    confirmText: String = "Confirm",
    cancelText: String = "Cancel",
    icon: ImageVector = Icons.Outlined.Info,
    isDestructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val accentColor = if (isDestructive) PremiumColors.NeonMagenta else PremiumColors.ElectricCyan

    var isVisible by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0.85f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "scale"
    )

    LaunchedEffect(Unit) { isVisible = true }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .padding(32.dp)
                    .scale(scale)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                PremiumColors.SpaceGrayLight.copy(alpha = 0.95f),
                                PremiumColors.SpaceGray.copy(alpha = 0.98f)
                            )
                        )
                    )
                    .border(
                        width = 1.dp,
                        color = PremiumColors.GlassBorder,
                        shape = RoundedCornerShape(24.dp)
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { }
                    )
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .widthIn(max = 320.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Icon
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(accentColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = accentColor
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = PremiumColors.TextPrimary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = PremiumColors.TextSecondary,
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    // Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Cancel
                        PremiumOutlineButton(
                            text = cancelText,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f)
                        )

                        // Confirm
                        PremiumButton(
                            text = confirmText,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onConfirm()
                            },
                            modifier = Modifier.weight(1f),
                            backgroundColor = accentColor
                        )
                    }
                }
            }
        }
    }
}

/**
 * Premium Input Dialog
 *
 * Beautiful dialog for text input with validation.
 */
@Composable
fun PremiumInputDialog(
    title: String,
    placeholder: String = "",
    initialValue: String = "",
    confirmText: String = "Confirm",
    cancelText: String = "Cancel",
    icon: ImageVector = Icons.Outlined.Edit,
    maxLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text,
    validator: (String) -> Boolean = { it.isNotBlank() },
    helperText: String? = null,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var value by remember { mutableStateOf(initialValue) }
    val isValid = validator(value)

    var isVisible by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0.85f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "scale"
    )

    LaunchedEffect(Unit) { isVisible = true }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .padding(32.dp)
                    .scale(scale)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                PremiumColors.SpaceGrayLight.copy(alpha = 0.95f),
                                PremiumColors.SpaceGray.copy(alpha = 0.98f)
                            )
                        )
                    )
                    .border(
                        width = 1.dp,
                        color = PremiumColors.GlassBorder,
                        shape = RoundedCornerShape(24.dp)
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { }
                    )
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .widthIn(max = 340.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Icon
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(PremiumColors.ElectricCyan.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = PremiumColors.ElectricCyan
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = PremiumColors.TextPrimary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Input field
                    PremiumTextField(
                        value = value,
                        onValueChange = { value = it },
                        placeholder = placeholder,
                        maxLines = maxLines,
                        keyboardType = keyboardType,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Helper text
                    helperText?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            color = PremiumColors.TextTertiary,
                            textAlign = TextAlign.Start,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        PremiumOutlineButton(
                            text = cancelText,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f)
                        )

                        PremiumButton(
                            text = confirmText,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onConfirm(value)
                            },
                            modifier = Modifier.weight(1f),
                            enabled = isValid
                        )
                    }
                }
            }
        }
    }
}

/**
 * Premium Create Group Dialog
 *
 * Specialized dialog for creating groups with MLS encryption.
 */
@Composable
fun PremiumCreateGroupDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, description: String) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    var isVisible by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0.85f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "scale"
    )

    LaunchedEffect(Unit) { isVisible = true }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .padding(32.dp)
                    .scale(scale)
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                PremiumColors.SpaceGrayLight.copy(alpha = 0.95f),
                                PremiumColors.SpaceGray.copy(alpha = 0.98f)
                            )
                        )
                    )
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.2f),
                                Color.White.copy(alpha = 0.05f)
                            )
                        ),
                        shape = RoundedCornerShape(28.dp)
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { }
                    )
            ) {
                Column(
                    modifier = Modifier
                        .padding(28.dp)
                        .widthIn(max = 360.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header icon
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        PremiumColors.ElectricCyan.copy(alpha = 0.3f),
                                        PremiumColors.ElectricCyan.copy(alpha = 0.1f),
                                        Color.Transparent
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(PremiumColors.ElectricCyan.copy(alpha = 0.15f))
                                .border(
                                    width = 1.dp,
                                    color = PremiumColors.ElectricCyan.copy(alpha = 0.3f),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.GroupAdd,
                                contentDescription = null,
                                modifier = Modifier.size(26.dp),
                                tint = PremiumColors.ElectricCyan
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "Create New Group",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = PremiumColors.TextPrimary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Groups are end-to-end encrypted using MLS protocol",
                        style = MaterialTheme.typography.bodySmall,
                        color = PremiumColors.TextSecondary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    // Name input
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Group Name",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = PremiumColors.TextSecondary
                        )
                        PremiumTextField(
                            value = name,
                            onValueChange = { name = it },
                            placeholder = "Enter group name",
                            maxLines = 1,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Description input
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Description (Optional)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = PremiumColors.TextSecondary
                        )
                        PremiumTextField(
                            value = description,
                            onValueChange = { description = it },
                            placeholder = "What's this group about?",
                            maxLines = 3,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Security badge
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(PremiumColors.AuroraGreen.copy(alpha = 0.1f))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Security,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = PremiumColors.AuroraGreen
                        )
                        Column {
                            Text(
                                text = "MLS Encrypted",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = PremiumColors.AuroraGreen
                            )
                            Text(
                                text = "Forward secrecy & post-compromise security",
                                style = MaterialTheme.typography.labelSmall,
                                color = PremiumColors.AuroraGreen.copy(alpha = 0.7f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    // Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        PremiumOutlineButton(
                            text = "Cancel",
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f)
                        )

                        PremiumButton(
                            text = "Create Group",
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onCreate(name, description)
                            },
                            modifier = Modifier.weight(1f),
                            enabled = name.isNotBlank()
                        )
                    }
                }
            }
        }
    }
}

/**
 * Premium SOS Confirm Dialog
 *
 * Critical dialog for SOS activation with countdown.
 */
@Composable
fun PremiumSOSConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    var isVisible by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0.85f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "scale"
    )

    // Pulsing animation for SOS
    val infiniteTransition = rememberInfiniteTransition(label = "sos")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    LaunchedEffect(Unit) { isVisible = true }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .padding(32.dp)
                    .scale(scale)
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                PremiumColors.SpaceGrayLight.copy(alpha = 0.95f),
                                PremiumColors.SpaceGray.copy(alpha = 0.98f)
                            )
                        )
                    )
                    .border(
                        width = 2.dp,
                        color = PremiumColors.NeonMagenta.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(28.dp)
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { }
                    )
            ) {
                Column(
                    modifier = Modifier
                        .padding(28.dp)
                        .widthIn(max = 340.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // SOS Icon with pulse
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .scale(pulse)
                            .clip(CircleShape)
                            .background(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        PremiumColors.NeonMagenta.copy(alpha = 0.4f),
                                        PremiumColors.NeonMagenta.copy(alpha = 0.1f),
                                        Color.Transparent
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                                .background(PremiumColors.NeonMagenta.copy(alpha = 0.2f))
                                .border(
                                    width = 2.dp,
                                    color = PremiumColors.NeonMagenta,
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "SOS",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Black,
                                color = PremiumColors.NeonMagenta
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "Activate Emergency Alert?",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = PremiumColors.TextPrimary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "This will immediately broadcast your location and distress signal to all team members",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PremiumColors.TextSecondary,
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    // Cancel button
                    PremiumOutlineButton(
                        text = "Cancel",
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Activate SOS button
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(PremiumColors.NeonMagenta)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onConfirm()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Warning,
                                contentDescription = null,
                                tint = Color.White
                            )
                            Text(
                                text = "ACTIVATE SOS",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Premium Button (filled)
 */
@Composable
fun PremiumButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    backgroundColor: Color = PremiumColors.ElectricCyan
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "scale"
    )

    Box(
        modifier = modifier
            .scale(scale)
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (enabled) backgroundColor else backgroundColor.copy(alpha = 0.3f)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) Color.White else Color.White.copy(alpha = 0.5f)
        )
    }
}

/**
 * Premium Outline Button
 */
@Composable
fun PremiumOutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "scale"
    )

    Box(
        modifier = modifier
            .scale(scale)
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Transparent)
            .border(
                width = 1.5.dp,
                color = if (enabled) PremiumColors.GlassBorder else PremiumColors.GlassBorder.copy(alpha = 0.3f),
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = if (enabled) PremiumColors.TextSecondary else PremiumColors.TextSecondary.copy(alpha = 0.5f)
        )
    }
}

/**
 * Premium Text Field
 */
@Composable
fun PremiumTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    maxLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(PremiumColors.SpaceGrayLight)
            .border(
                width = 1.dp,
                color = if (value.isNotEmpty()) PremiumColors.ElectricCyan.copy(alpha = 0.5f) else PremiumColors.GlassBorder,
                shape = RoundedCornerShape(14.dp)
            )
            .padding(16.dp),
        textStyle = TextStyle(
            color = PremiumColors.TextPrimary,
            fontSize = 16.sp
        ),
        maxLines = maxLines,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = visualTransformation,
        decorationBox = { innerTextField ->
            Box {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = TextStyle(
                            color = PremiumColors.TextTertiary,
                            fontSize = 16.sp
                        )
                    )
                }
                innerTextField()
            }
        }
    )
}

/**
 * Premium Create Channel Dialog
 *
 * Specialized dialog for creating PTT channels.
 * Jan 2026: Added scroll support and improved UI/UX
 */
@Composable
fun PremiumCreateChannelDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, frequency: String) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var name by remember { mutableStateOf("") }
    var frequency by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()

    var isVisible by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0.85f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "scale"
    )

    LaunchedEffect(Unit) { isVisible = true }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false  // Allow dialog to handle keyboard
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .imePadding()  // Handle keyboard padding
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .widthIn(max = 400.dp)
                    .scale(scale)
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                PremiumColors.SpaceGrayLight.copy(alpha = 0.98f),
                                PremiumColors.SpaceGray.copy(alpha = 0.99f)
                            )
                        )
                    )
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.2f),
                                Color.White.copy(alpha = 0.05f)
                            )
                        ),
                        shape = RoundedCornerShape(28.dp)
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { }  // Prevent click-through
                    )
            ) {
                // Scrollable content
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 600.dp)  // Max height for large screens
                        .verticalScroll(scrollState)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header icon with glow effect
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        PremiumColors.SolarGold.copy(alpha = 0.3f),
                                        PremiumColors.SolarGold.copy(alpha = 0.1f),
                                        Color.Transparent
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(PremiumColors.SolarGold.copy(alpha = 0.15f))
                                .border(
                                    width = 1.5.dp,
                                    color = PremiumColors.SolarGold.copy(alpha = 0.4f),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.RecordVoiceOver,
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                                tint = PremiumColors.SolarGold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "Create PTT Channel",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = PremiumColors.TextPrimary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Walkie-talkie style communication with low-latency Opus audio",
                        style = MaterialTheme.typography.bodySmall,
                        color = PremiumColors.TextSecondary,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Channel Name input
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Label,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = PremiumColors.TextSecondary
                            )
                            Text(
                                text = "Channel Name",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = PremiumColors.TextSecondary
                            )
                            Text(
                                text = "*",
                                style = MaterialTheme.typography.labelMedium,
                                color = PremiumColors.NeonMagenta
                            )
                        }
                        PremiumTextField(
                            value = name,
                            onValueChange = { if (it.length <= 30) name = it },
                            placeholder = "e.g., Alpha Squad, Team 1",
                            maxLines = 1,
                            modifier = Modifier.fillMaxWidth()
                        )
                        // Character count
                        Text(
                            text = "${name.length}/30",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (name.length >= 25) PremiumColors.ConnectingAmber else PremiumColors.TextTertiary,
                            modifier = Modifier.align(Alignment.End)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Channel ID input (optional)
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Tag,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = PremiumColors.TextSecondary
                            )
                            Text(
                                text = "Channel ID",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = PremiumColors.TextSecondary
                            )
                            Text(
                                text = "(Optional)",
                                style = MaterialTheme.typography.labelSmall,
                                color = PremiumColors.TextTertiary
                            )
                        }
                        PremiumTextField(
                            value = frequency,
                            onValueChange = { if (it.length <= 10) frequency = it },
                            placeholder = "e.g., CH-01, TAC-1",
                            maxLines = 1,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "Auto-generated if empty",
                            style = MaterialTheme.typography.labelSmall,
                            color = PremiumColors.TextTertiary
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Feature cards
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Low latency badge
                        FeatureBadge(
                            icon = Icons.Outlined.Speed,
                            title = "Low Latency",
                            subtitle = "< 50ms",
                            color = PremiumColors.ElectricCyan,
                            modifier = Modifier.weight(1f)
                        )
                        // Encrypted badge
                        FeatureBadge(
                            icon = Icons.Outlined.Lock,
                            title = "Encrypted",
                            subtitle = "E2E",
                            color = PremiumColors.AuroraGreen,
                            modifier = Modifier.weight(1f)
                        )
                        // Discovery badge
                        FeatureBadge(
                            icon = Icons.Outlined.Wifi,
                            title = "Auto Share",
                            subtitle = "Nearby",
                            color = PremiumColors.HoloPurple,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        PremiumOutlineButton(
                            text = "Cancel",
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f)
                        )

                        PremiumButton(
                            text = "Create",
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onCreate(name.trim(), frequency.trim())
                            },
                            modifier = Modifier.weight(1f),
                            enabled = name.isNotBlank(),
                            backgroundColor = PremiumColors.SolarGold
                        )
                    }
                }
            }
        }
    }
}

/**
 * Feature badge for dialog (compact info display)
 */
@Composable
private fun FeatureBadge(
    icon: ImageVector,
    title: String,
    subtitle: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.1f))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = color
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = color,
            maxLines = 1
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = color.copy(alpha = 0.7f),
            fontSize = 10.sp,
            maxLines = 1
        )
    }
}
