/*
 * Mesh Rider Wave - PTT Channels Screen
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 *
 * Feb 2026: Complete UI rewrite following Zello/Motorola WAVE PTT patterns:
 * - Channel list view: tap channel to open talk screen
 * - Talk screen: full-screen with large circular PTT button (Zello-style)
 * - Clean flow: Create → Discover → Join → Talk → Leave/Delete
 * - Proper state feedback: floor state, speaker name, audio level
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.doodlelabs.meshriderwave.R
import com.doodlelabs.meshriderwave.presentation.ui.components.*
import com.doodlelabs.meshriderwave.presentation.ui.theme.PremiumColors
import com.doodlelabs.meshriderwave.presentation.viewmodel.ChannelsViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToPtt: () -> Unit = {},
    viewModel: ChannelsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Auto-refresh
    LaunchedEffect(Unit) {
        while (true) {
            delay(5000)
            viewModel.refreshChannels()
        }
    }

    // Show errors
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error, duration = SnackbarDuration.Short)
            viewModel.clearError()
        }
    }

    DeepSpaceBackground {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = {
                SnackbarHost(snackbarHostState) { data ->
                    Snackbar(
                        snackbarData = data,
                        containerColor = PremiumColors.SpaceGrayLight,
                        contentColor = PremiumColors.TextPrimary,
                        actionColor = PremiumColors.ElectricCyan,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = if (uiState.activeChannel != null) uiState.activeChannel!!.name
                                   else stringResource(R.string.channels_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = PremiumColors.TextPrimary
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (uiState.activeChannel != null) {
                                // Back from talk screen to channel list
                                viewModel.setActiveChannel("")
                            } else {
                                onNavigateBack()
                            }
                        }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.cd_back),
                                tint = PremiumColors.TextPrimary
                            )
                        }
                    },
                    actions = {
                        if (uiState.activeChannel == null) {
                            // Channel list: show create button
                            IconButton(onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                showCreateDialog = true
                            }) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = "Create Channel",
                                    tint = PremiumColors.ElectricCyan
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        ) { padding ->
            // Two views: Channel List OR Talk Screen
            if (uiState.activeChannel != null) {
                // ===== TALK SCREEN (Zello-style) =====
                TalkScreen(
                    channel = uiState.activeChannel!!,
                    isTransmitting = uiState.isTransmitting,
                    floorState = uiState.floorState,
                    activeSpeaker = uiState.activeSpeaker,
                    onPTTPress = { viewModel.startTransmit() },
                    onPTTRelease = { viewModel.stopTransmit() },
                    onLeave = {
                        viewModel.leaveChannel(uiState.activeChannel!!.id)
                    },
                    modifier = Modifier.padding(padding)
                )
            } else {
                // ===== CHANNEL LIST =====
                ChannelListView(
                    channels = uiState.channels,
                    discoveredChannels = uiState.discoveredChannels,
                    joinedChannelIds = uiState.joinedChannelIds,
                    onChannelTap = { viewModel.setActiveChannel(it.id) },
                    onJoinChannel = { viewModel.joinChannel(it.id) },
                    onJoinDiscovered = { viewModel.joinDiscoveredChannel(it.id) },
                    onLeaveChannel = { viewModel.leaveChannel(it.id) },
                    onDeleteChannel = { viewModel.deleteChannel(it.id) },
                    onCreateClick = { showCreateDialog = true },
                    modifier = Modifier.padding(padding)
                )
            }
        }
    }

    // Create Channel Dialog
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

// =============================================================================
// TALK SCREEN — Full screen Zello-style PTT view
// =============================================================================

@Composable
private fun TalkScreen(
    channel: ChannelUiModel,
    isTransmitting: Boolean,
    floorState: String,
    activeSpeaker: String?,
    onPTTPress: () -> Unit,
    onPTTRelease: () -> Unit,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var isPressing by remember { mutableStateOf(false) }

    val buttonScale by animateFloatAsState(
        targetValue = if (isPressing) 0.92f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "pttScale"
    )

    // Pulsing ring when transmitting
    val pulseScale by if (isTransmitting) {
        rememberInfiniteTransition(label = "pulse").animateFloat(
            initialValue = 1f, targetValue = 1.3f,
            animationSpec = infiniteRepeatable(
                tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse
            ), label = "ring"
        )
    } else {
        remember { mutableFloatStateOf(1f) }
    }

    val pttColor = when {
        isTransmitting -> PremiumColors.BusyRed
        isPressing -> PremiumColors.ConnectingAmber
        else -> PremiumColors.ElectricCyan
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        // Channel info
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(Icons.Default.Lock, null, Modifier.size(14.dp), tint = PremiumColors.LaserLime)
            Text("E2E Encrypted", style = MaterialTheme.typography.labelMedium,
                color = PremiumColors.LaserLime, fontWeight = FontWeight.SemiBold)
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            "${channel.onlineCount} members online",
            style = MaterialTheme.typography.bodyMedium,
            color = PremiumColors.TextSecondary
        )

        // Active speaker
        Spacer(modifier = Modifier.height(24.dp))
        AnimatedVisibility(visible = activeSpeaker != null && !isTransmitting) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.VolumeUp, null, Modifier.size(24.dp),
                    tint = PremiumColors.LaserLime)
                Spacer(Modifier.height(4.dp))
                Text(activeSpeaker ?: "", style = MaterialTheme.typography.titleMedium,
                    color = PremiumColors.LaserLime, fontWeight = FontWeight.Bold)
                Text("Speaking", style = MaterialTheme.typography.labelSmall,
                    color = PremiumColors.TextSecondary)
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // ===== LARGE CIRCULAR PTT BUTTON (Zello-style) =====
        Box(contentAlignment = Alignment.Center) {
            // Pulsing ring behind button when transmitting
            if (isTransmitting) {
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(PremiumColors.BusyRed.copy(alpha = 0.15f))
                )
            }

            // Main PTT circle
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .scale(buttonScale)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.radialGradient(
                            colors = if (isTransmitting) {
                                listOf(PremiumColors.BusyRed, PremiumColors.BusyRed.copy(alpha = 0.7f))
                            } else {
                                listOf(
                                    PremiumColors.SpaceGrayLight,
                                    PremiumColors.SpaceGray
                                )
                            }
                        )
                    )
                    .border(
                        width = 3.dp,
                        color = pttColor,
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
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = if (isTransmitting) Icons.Default.Mic else Icons.Default.MicNone,
                        contentDescription = "Push to Talk",
                        modifier = Modifier.size(48.dp),
                        tint = if (isTransmitting) Color.White else pttColor
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (isTransmitting) "RELEASE" else "HOLD",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Black,
                        color = if (isTransmitting) Color.White else pttColor,
                        letterSpacing = 2.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Status text below PTT
        Text(
            text = when {
                isTransmitting -> "Transmitting..."
                isPressing -> "Connecting..."
                else -> "Hold to talk"
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (isTransmitting) PremiumColors.BusyRed else PremiumColors.TextSecondary
        )

        // Waveform when transmitting
        AnimatedVisibility(
            visible = isTransmitting,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(8.dp))
                TransmittingWaveform()
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Bottom: Leave button
        TextButton(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onLeave()
            },
            modifier = Modifier.padding(bottom = 32.dp)
        ) {
            Icon(Icons.AutoMirrored.Outlined.ExitToApp, null,
                Modifier.size(18.dp), tint = PremiumColors.BusyRed)
            Spacer(Modifier.width(8.dp))
            Text("Leave Channel", color = PremiumColors.BusyRed,
                fontWeight = FontWeight.SemiBold)
        }
    }
}

// =============================================================================
// CHANNEL LIST VIEW
// =============================================================================

@Composable
private fun ChannelListView(
    channels: List<ChannelUiModel>,
    discoveredChannels: List<ChannelUiModel>,
    joinedChannelIds: Set<String>,
    onChannelTap: (ChannelUiModel) -> Unit,
    onJoinChannel: (ChannelUiModel) -> Unit,
    onJoinDiscovered: (ChannelUiModel) -> Unit,
    onLeaveChannel: (ChannelUiModel) -> Unit,
    onDeleteChannel: (ChannelUiModel) -> Unit,
    onCreateClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (channels.isEmpty() && discoveredChannels.isEmpty()) {
        EmptyChannelsState(onCreateClick = onCreateClick, modifier = modifier)
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // MY CHANNELS
        if (channels.isNotEmpty()) {
            item(key = "header_my") {
                SectionLabel("MY CHANNELS", channels.size)
            }

            items(items = channels, key = { "my_${it.id}" }) { channel ->
                val isJoined = joinedChannelIds.contains(channel.id)
                ChannelRow(
                    channel = channel,
                    isJoined = isJoined,
                    isOwner = channel.isOwner,
                    onTap = { if (isJoined) onChannelTap(channel) else onJoinChannel(channel) },
                    onJoin = { onJoinChannel(channel) },
                    onLeave = { onLeaveChannel(channel) },
                    onDelete = { onDeleteChannel(channel) }
                )
            }
        }

        // DISCOVERED
        if (discoveredChannels.isNotEmpty()) {
            item(key = "header_disc") {
                Spacer(Modifier.height(16.dp))
                SectionLabel("NEARBY", discoveredChannels.size, PremiumColors.ElectricCyan)
            }

            items(items = discoveredChannels, key = { "disc_${it.id}" }) { channel ->
                DiscoveredRow(
                    channel = channel,
                    onJoin = { onJoinDiscovered(channel) }
                )
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

// =============================================================================
// SECTION LABEL — Clean, minimal
// =============================================================================

@Composable
private fun SectionLabel(title: String, count: Int, color: Color = PremiumColors.TextSecondary) {
    Row(
        modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = color,
            letterSpacing = 1.5.sp
        )
        Text(
            text = "$count",
            style = MaterialTheme.typography.labelSmall,
            color = color.copy(alpha = 0.7f)
        )
    }
}

// =============================================================================
// CHANNEL ROW — Simple list item (Zello-style)
// =============================================================================

@Composable
private fun ChannelRow(
    channel: ChannelUiModel,
    isJoined: Boolean,
    isOwner: Boolean,
    onTap: () -> Unit,
    onJoin: () -> Unit,
    onLeave: () -> Unit,
    onDelete: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isJoined) PremiumColors.SpaceGrayLight.copy(alpha = 0.5f)
                else Color.Transparent
            )
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onTap()
            }
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Status dot
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(
                    if (isJoined) PremiumColors.LaserLime else PremiumColors.TextSecondary.copy(alpha = 0.4f)
                )
        )

        Spacer(Modifier.width(14.dp))

        // Channel info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = channel.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = PremiumColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "${channel.onlineCount} members",
                    style = MaterialTheme.typography.labelSmall,
                    color = PremiumColors.TextSecondary
                )
                if (isJoined) {
                    Text(
                        text = "Joined",
                        style = MaterialTheme.typography.labelSmall,
                        color = PremiumColors.LaserLime,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Lock icon
        Icon(
            Icons.Default.Lock,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = PremiumColors.LaserLime.copy(alpha = 0.6f)
        )

        Spacer(Modifier.width(8.dp))

        // Action: Join or Menu
        if (!isJoined) {
            TextButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onJoin()
                },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text("JOIN", fontWeight = FontWeight.Bold, fontSize = 12.sp,
                    color = PremiumColors.ElectricCyan, letterSpacing = 1.sp)
            }
        } else {
            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.MoreVert, null,
                        Modifier.size(18.dp), tint = PremiumColors.TextSecondary
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    containerColor = PremiumColors.SpaceGrayLight
                ) {
                    DropdownMenuItem(
                        text = { Text("Open", color = PremiumColors.TextPrimary) },
                        onClick = { showMenu = false; onTap() },
                        leadingIcon = { Icon(Icons.Default.Mic, null, tint = PremiumColors.ElectricCyan) }
                    )
                    DropdownMenuItem(
                        text = { Text("Leave", color = PremiumColors.BusyRed) },
                        onClick = { showMenu = false; onLeave() },
                        leadingIcon = { Icon(Icons.AutoMirrored.Outlined.ExitToApp, null, tint = PremiumColors.BusyRed) }
                    )
                    if (isOwner) {
                        DropdownMenuItem(
                            text = { Text("Delete", color = PremiumColors.BusyRed) },
                            onClick = { showMenu = false; onDelete() },
                            leadingIcon = { Icon(Icons.Default.Delete, null, tint = PremiumColors.BusyRed) }
                        )
                    }
                }
            }
        }
    }
}

// =============================================================================
// DISCOVERED ROW
// =============================================================================

@Composable
private fun DiscoveredRow(
    channel: ChannelUiModel,
    onJoin: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onJoin()
            }
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // WiFi icon
        Icon(
            Icons.Default.Wifi,
            contentDescription = null,
            modifier = Modifier.size(10.dp),
            tint = PremiumColors.ElectricCyan
        )

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = channel.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = PremiumColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                channel.announcerName?.let {
                    Text(
                        text = "from $it",
                        style = MaterialTheme.typography.labelSmall,
                        color = PremiumColors.ElectricCyan
                    )
                }
                Text(
                    text = "${channel.onlineCount} members",
                    style = MaterialTheme.typography.labelSmall,
                    color = PremiumColors.TextSecondary
                )
            }
        }

        Icon(
            Icons.Default.Lock, null,
            Modifier.size(14.dp), tint = PremiumColors.LaserLime.copy(alpha = 0.6f)
        )

        Spacer(Modifier.width(8.dp))

        TextButton(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onJoin()
            },
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Text("JOIN", fontWeight = FontWeight.Bold, fontSize = 12.sp,
                color = PremiumColors.ElectricCyan, letterSpacing = 1.sp)
        }
    }
}

// =============================================================================
// TRANSMITTING WAVEFORM
// =============================================================================

@Composable
private fun TransmittingWaveform() {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform")
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(9) { index ->
            val height by infiniteTransition.animateFloat(
                initialValue = 4f, targetValue = 20f,
                animationSpec = infiniteRepeatable(
                    tween(200, delayMillis = index * 30), RepeatMode.Reverse
                ), label = "bar$index"
            )
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(height.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(PremiumColors.BusyRed)
            )
        }
    }
}

// =============================================================================
// EMPTY STATE
// =============================================================================

@Composable
private fun EmptyChannelsState(onCreateClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Outlined.RecordVoiceOver, null,
            Modifier.size(64.dp), tint = PremiumColors.TextSecondary.copy(alpha = 0.5f)
        )

        Spacer(Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.channels_empty_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = PremiumColors.TextPrimary
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.channels_empty_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = PremiumColors.TextSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = onCreateClick,
            colors = ButtonDefaults.buttonColors(containerColor = PremiumColors.ElectricCyan),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Add, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.channels_create), fontWeight = FontWeight.SemiBold)
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
    val isDiscovered: Boolean = false,
    val announcerName: String? = null,
    val isOwner: Boolean = false
)
