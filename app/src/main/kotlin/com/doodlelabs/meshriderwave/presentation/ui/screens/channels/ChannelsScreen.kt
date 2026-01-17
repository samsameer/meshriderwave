/*
 * Mesh Rider Wave - PTT Channels Screen
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 *
 * Push-to-Talk / Walkie-Talkie channel management
 * - Create/join PTT channels
 * - One-touch PTT transmission
 * - Priority-based audio mixing
 * - VOX (voice-activated) mode
 */

package com.doodlelabs.meshriderwave.presentation.ui.screens.channels

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.doodlelabs.meshriderwave.presentation.ui.components.*
import com.doodlelabs.meshriderwave.presentation.ui.theme.PremiumColors
import com.doodlelabs.meshriderwave.presentation.viewmodel.ChannelsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelsScreen(
    onNavigateBack: () -> Unit,
    viewModel: ChannelsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    DeepSpaceBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "PTT Channels",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = PremiumColors.TextPrimary
                            )
                            Text(
                                text = "Push-to-Talk Communication",
                                style = MaterialTheme.typography.bodySmall,
                                color = PremiumColors.TextSecondary
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = PremiumColors.TextPrimary
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { showCreateDialog = true }) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "Create Channel",
                                tint = PremiumColors.ElectricCyan
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // Active channel with PTT button
                uiState.activeChannel?.let { channel ->
                    ActiveChannelCard(
                        channel = channel,
                        isTransmitting = uiState.isTransmitting,
                        onPTTPress = { viewModel.startTransmit() },
                        onPTTRelease = { viewModel.stopTransmit() },
                        onLeave = { viewModel.leaveChannel(channel.id) },
                        modifier = Modifier.padding(16.dp)
                    )
                }

                // Channel list
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (uiState.channels.isEmpty()) {
                        item {
                            EmptyChannelsState(
                                onCreateClick = { showCreateDialog = true }
                            )
                        }
                    } else {
                        item {
                            Text(
                                text = "Available Channels",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = PremiumColors.TextSecondary,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }

                        items(uiState.channels) { channel ->
                            ChannelCard(
                                channel = channel,
                                isJoined = uiState.joinedChannelIds.contains(channel.id),
                                isActive = uiState.activeChannel?.id == channel.id,
                                onJoin = { viewModel.joinChannel(channel.id) },
                                onLeave = { viewModel.leaveChannel(channel.id) },
                                onSetActive = { viewModel.setActiveChannel(channel.id) }
                            )
                        }
                    }

                    // Bottom spacing
                    item {
                        Spacer(modifier = Modifier.height(100.dp))
                    }
                }
            }
        }
    }

    // Create Channel Dialog (Premium)
    if (showCreateDialog) {
        PremiumCreateChannelDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name, frequency ->
                viewModel.createChannel(name, frequency)
                showCreateDialog = false
            }
        )
    }
}

/**
 * Active channel card with large PTT button
 */
@Composable
private fun ActiveChannelCard(
    channel: ChannelUiModel,
    isTransmitting: Boolean,
    onPTTPress: () -> Unit,
    onPTTRelease: () -> Unit,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var isPressing by remember { mutableStateOf(false) }

    val buttonScale by animateFloatAsState(
        targetValue = if (isPressing) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "pttScale"
    )

    val pttColor by animateColorAsState(
        targetValue = if (isTransmitting) PremiumColors.LaserLime else PremiumColors.NeonMagenta,
        animationSpec = tween(200),
        label = "pttColor"
    )

    GradientGlassCard(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 24.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Channel header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(PremiumColors.ElectricCyan, PremiumColors.HoloPurple)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.RecordVoiceOver,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = Color.White
                        )
                    }

                    Column {
                        Text(
                            text = channel.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = PremiumColors.TextPrimary
                        )
                        Text(
                            text = "${channel.onlineCount} online",
                            style = MaterialTheme.typography.labelSmall,
                            color = PremiumColors.TextSecondary
                        )
                    }
                }

                IconButton(
                    onClick = onLeave,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(PremiumColors.BusyRed.copy(alpha = 0.2f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Leave",
                        tint = PremiumColors.BusyRed,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Large PTT Button
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .scale(buttonScale)
                    .clip(CircleShape)
                    .background(
                        brush = if (isTransmitting) {
                            Brush.radialGradient(
                                colors = listOf(
                                    pttColor,
                                    pttColor.copy(alpha = 0.7f)
                                )
                            )
                        } else {
                            Brush.radialGradient(
                                colors = listOf(
                                    PremiumColors.SpaceGrayLight,
                                    PremiumColors.SpaceGray
                                )
                            )
                        }
                    )
                    .border(
                        width = 4.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(pttColor, pttColor.copy(alpha = 0.5f))
                        ),
                        shape = CircleShape
                    )
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                isPressing = true
                                onPTTPress()
                                try {
                                    awaitRelease()
                                } finally {
                                    isPressing = false
                                    onPTTRelease()
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = if (isTransmitting) Icons.Default.Mic else Icons.Default.MicNone,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = if (isTransmitting) Color.White else pttColor
                    )

                    if (isTransmitting) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "TRANSMITTING",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (isTransmitting) "Release to stop" else "Hold to talk",
                style = MaterialTheme.typography.bodyMedium,
                color = if (isTransmitting) PremiumColors.LaserLime else PremiumColors.TextSecondary
            )

            // Transmitting indicator animation
            if (isTransmitting) {
                Spacer(modifier = Modifier.height(16.dp))
                TransmittingWaveform()
            }
        }
    }
}

/**
 * Transmitting waveform animation
 */
@Composable
private fun TransmittingWaveform() {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform")

    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(5) { index ->
            val height by infiniteTransition.animateFloat(
                initialValue = 8f,
                targetValue = 24f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 300,
                        delayMillis = index * 50
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bar$index"
            )

            Box(
                modifier = Modifier
                    .width(6.dp)
                    .height(height.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(PremiumColors.LaserLime)
            )
        }
    }
}

/**
 * Channel card
 */
@Composable
private fun ChannelCard(
    channel: ChannelUiModel,
    isJoined: Boolean,
    isActive: Boolean,
    onJoin: () -> Unit,
    onLeave: () -> Unit,
    onSetActive: () -> Unit
) {
    val borderColor = when {
        isActive -> PremiumColors.ElectricCyan
        isJoined -> PremiumColors.LaserLime.copy(alpha = 0.5f)
        else -> PremiumColors.GlassBorder
    }

    val priorityColor = when (channel.priority) {
        "EMERGENCY" -> PremiumColors.BusyRed
        "HIGH" -> PremiumColors.ConnectingAmber
        "LOW" -> PremiumColors.TextSecondary
        else -> PremiumColors.ElectricCyan
    }

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = isJoined) { onSetActive() },
        cornerRadius = 16.dp,
        borderColor = borderColor
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Channel icon with priority indicator
            Box {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(priorityColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.RecordVoiceOver,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = priorityColor
                    )
                }

                // Online indicator
                if (channel.hasActivity) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(PremiumColors.LaserLime)
                            .border(2.dp, PremiumColors.SpaceGray, CircleShape)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = channel.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = PremiumColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (isActive) {
                        PremiumChip(
                            text = "Active",
                            color = PremiumColors.ElectricCyan
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Member count
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.People,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = PremiumColors.TextSecondary
                        )
                        Text(
                            text = "${channel.onlineCount} online",
                            style = MaterialTheme.typography.labelSmall,
                            color = PremiumColors.TextSecondary
                        )
                    }

                    // Priority badge
                    PremiumChip(
                        text = channel.priority,
                        color = priorityColor,
                        variant = ChipVariant.OUTLINED
                    )
                }
            }

            // Join/Leave button
            if (isJoined) {
                IconButton(
                    onClick = onLeave,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(PremiumColors.BusyRed.copy(alpha = 0.2f))
                ) {
                    Icon(
                        imageVector = Icons.Default.ExitToApp,
                        contentDescription = "Leave",
                        tint = PremiumColors.BusyRed,
                        modifier = Modifier.size(20.dp)
                    )
                }
            } else {
                Button(
                    onClick = onJoin,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PremiumColors.ElectricCyan
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text("Join", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/**
 * Empty state for no channels
 */
@Composable
private fun EmptyChannelsState(
    onCreateClick: () -> Unit
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 24.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(PremiumColors.SpaceGrayLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.RecordVoiceOver,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = PremiumColors.TextSecondary
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "No PTT Channels",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = PremiumColors.TextPrimary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Create a channel to start\npush-to-talk communication",
                style = MaterialTheme.typography.bodyMedium,
                color = PremiumColors.TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onCreateClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PremiumColors.ElectricCyan
                )
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Create Channel")
            }
        }
    }
}

// UI Model
data class ChannelUiModel(
    val id: String,
    val name: String,
    val frequency: String,
    val onlineCount: Int,
    val priority: String,
    val hasActivity: Boolean
)
