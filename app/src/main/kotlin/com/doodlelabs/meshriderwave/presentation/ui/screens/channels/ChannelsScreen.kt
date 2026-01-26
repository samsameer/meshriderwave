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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelsScreen(
    onNavigateBack: () -> Unit,
    viewModel: ChannelsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    // Auto-refresh discovered channels periodically
    LaunchedEffect(Unit) {
        while (true) {
            delay(5000)  // Refresh UI every 5 seconds
            viewModel.refreshChannels()
        }
    }

    // Refresh animation
    val refreshRotation by rememberInfiniteTransition(label = "refresh").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing)
        ),
        label = "rotation"
    )

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
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Push-to-Talk Communication",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = PremiumColors.TextSecondary
                                )
                                // Show discovered count
                                if (uiState.discoveredChannels.isNotEmpty()) {
                                    Text(
                                        text = "• ${uiState.discoveredChannels.size} nearby",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = PremiumColors.ElectricCyan
                                    )
                                }
                            }
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
                        // Refresh button
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                scope.launch {
                                    isRefreshing = true
                                    viewModel.refreshChannels()
                                    delay(1000)
                                    isRefreshing = false
                                }
                            }
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                tint = PremiumColors.TextSecondary,
                                modifier = if (isRefreshing) Modifier.rotate(refreshRotation) else Modifier
                            )
                        }
                        // Create button
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

                // Channel list - Scrollable with proper sections
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Show empty state only if no channels at all
                    if (uiState.channels.isEmpty() && uiState.discoveredChannels.isEmpty()) {
                        item {
                            EmptyChannelsState(
                                onCreateClick = { showCreateDialog = true }
                            )
                        }
                    }

                    // My Channels Section
                    if (uiState.channels.isNotEmpty()) {
                        item {
                            Text(
                                text = "MY CHANNELS",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = PremiumColors.TextSecondary,
                                letterSpacing = 1.sp,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }

                        items(
                            items = uiState.channels,
                            key = { "my_${it.id}" }
                        ) { channel ->
                            ChannelCard(
                                channel = channel,
                                isJoined = uiState.joinedChannelIds.contains(channel.id),
                                isActive = uiState.activeChannel?.id == channel.id,
                                onJoin = { viewModel.joinChannel(channel.id) },
                                onLeave = { viewModel.leaveChannel(channel.id) },
                                onSetActive = { viewModel.setActiveChannel(channel.id) },
                                onDelete = { viewModel.deleteChannel(channel.id) }
                            )
                        }
                    }

                    // Discovered Channels Section (from other devices)
                    if (uiState.discoveredChannels.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "DISCOVERED CHANNELS",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = PremiumColors.ElectricCyan,
                                letterSpacing = 1.sp,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                            Text(
                                text = "Channels shared by nearby devices",
                                style = MaterialTheme.typography.bodySmall,
                                color = PremiumColors.TextSecondary,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }

                        items(
                            items = uiState.discoveredChannels,
                            key = { "disc_${it.id}" }
                        ) { channel ->
                            DiscoveredChannelCard(
                                channel = channel,
                                onJoin = { viewModel.joinDiscoveredChannel(channel.id) }
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
                            tint = PremiumColors.TextPrimary
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
                        tint = if (isTransmitting) PremiumColors.DeepSpace else pttColor
                    )

                    if (isTransmitting) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "TRANSMITTING",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = PremiumColors.DeepSpace,
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
 * Channel card with delete support
 */
@Composable
private fun ChannelCard(
    channel: ChannelUiModel,
    isJoined: Boolean,
    isActive: Boolean,
    onJoin: () -> Unit,
    onLeave: () -> Unit,
    onSetActive: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
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

            // Action buttons
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Delete button (owner only, long press to confirm)
                if (onDelete != null && isJoined) {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            showDeleteConfirm = true
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(PremiumColors.BusyRed.copy(alpha = 0.1f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = PremiumColors.BusyRed.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp)
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

    // Delete confirmation dialog
    if (showDeleteConfirm && onDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = {
                Text(
                    "Delete Channel",
                    color = PremiumColors.TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    "Are you sure you want to delete \"${channel.name}\"? This action cannot be undone.",
                    color = PremiumColors.TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PremiumColors.BusyRed
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", color = PremiumColors.TextSecondary)
                }
            },
            containerColor = PremiumColors.SpaceGray,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

/**
 * Discovered channel card - shows channels from other devices
 */
@Composable
private fun DiscoveredChannelCard(
    channel: ChannelUiModel,
    onJoin: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 16.dp,
        borderColor = PremiumColors.ElectricCyan.copy(alpha = 0.3f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Channel icon with network indicator
            Box {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(PremiumColors.ElectricCyan.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Wifi,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = PremiumColors.ElectricCyan
                    )
                }

                // Network badge
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(PremiumColors.LaserLime)
                        .border(2.dp, PremiumColors.SpaceGray, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SignalCellularAlt,
                        contentDescription = null,
                        modifier = Modifier.size(10.dp),
                        tint = PremiumColors.DeepSpace
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = channel.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = PremiumColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Announcer name
                    channel.announcerName?.let { announcer ->
                        Text(
                            text = "from $announcer",
                            style = MaterialTheme.typography.labelSmall,
                            color = PremiumColors.ElectricCyan
                        )
                    }

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
                }
            }

            // Join button
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onJoin()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = PremiumColors.ElectricCyan
                ),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Join", fontWeight = FontWeight.SemiBold)
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
    val hasActivity: Boolean,
    // Jan 2026: Network discovery fields
    val isDiscovered: Boolean = false,  // true if discovered from another device
    val announcerName: String? = null   // name of peer who announced this channel
)
