/*
 * Mesh Rider Wave - Premium Dashboard Screen 2026
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 *
 * World-class tactical dashboard - Optimized for performance
 */

package com.doodlelabs.meshriderwave.presentation.ui.screens.dashboard

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.doodlelabs.meshriderwave.presentation.ui.components.*
import com.doodlelabs.meshriderwave.presentation.ui.theme.PremiumColors
import com.doodlelabs.meshriderwave.presentation.viewmodel.DashboardViewModel
import com.doodlelabs.meshriderwave.presentation.viewmodel.DiscoveredRadioUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToGroups: () -> Unit,
    onNavigateToChannels: () -> Unit,
    onNavigateToMap: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToContacts: () -> Unit,
    onStartCall: (String) -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val haptic = LocalHapticFeedback.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PremiumColors.DeepSpace)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            // Premium Header
            item {
                PremiumHeader(
                    username = uiState.username,
                    onSettingsClick = onNavigateToSettings
                )
            }

            // Network Status Hero
            item {
                NetworkStatusHero(
                    isConnected = uiState.isServiceRunning,
                    peerCount = uiState.discoveredPeers,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }

            // Quick Stats
            item {
                QuickStatsRow(
                    peerCount = uiState.discoveredPeers,
                    meshNodes = uiState.meshNodes,
                    latency = uiState.networkLatency,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp)
                )
            }

            // Radio Status Section
            item {
                RadioStatusCard(
                    isConnected = uiState.isRadioConnected,
                    isConnecting = uiState.isRadioConnecting,
                    radioIp = uiState.connectedRadioIp,
                    radioHostname = uiState.radioHostname,
                    radioModel = uiState.radioModel,
                    ssid = uiState.radioSsid,
                    channel = uiState.radioChannel,
                    signal = uiState.radioSignal,
                    snr = uiState.radioSnr,
                    linkQuality = uiState.radioLinkQuality,
                    meshPeerCount = uiState.meshPeerCount,
                    discoveredRadios = uiState.discoveredRadios,
                    error = uiState.radioError,
                    onConnectRadio = { ip -> viewModel.connectToRadio(ip) },
                    onDisconnect = { viewModel.disconnectFromRadio() },
                    onRefresh = { viewModel.refreshRadioDiscovery() },
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp)
                )
            }

            // Action Buttons - Row 1
            item {
                ActionButtonsRow(
                    onSOSClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.activateSOS()
                    },
                    onMapClick = onNavigateToMap,
                    onGroupsClick = onNavigateToGroups,
                    onChannelsClick = onNavigateToChannels,
                    hasActiveAlert = uiState.hasActiveSOS,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }

            // Action Buttons - Row 2 (Contacts)
            item {
                Spacer(modifier = Modifier.height(10.dp))
                SecondaryActionsRow(
                    onContactsClick = onNavigateToContacts,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }

            // Blue Force Tracking Section
            item {
                Spacer(modifier = Modifier.height(28.dp))
                SectionHeader(
                    title = "Blue Force Tracking",
                    subtitle = "${uiState.trackedTeamMembers.size} nearby",
                    actionText = "Full Map",
                    onAction = onNavigateToMap,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }

            item {
                TrackingCard(
                    memberCount = uiState.trackedTeamMembers.size,
                    onClick = onNavigateToMap,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }

            // Channels Section
            if (uiState.activeChannels.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(28.dp))
                    SectionHeader(
                        title = "PTT Channels",
                        subtitle = "${uiState.activeChannels.size} active",
                        actionText = "All",
                        onAction = onNavigateToChannels,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }

                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(horizontal = 24.dp)
                    ) {
                        items(uiState.activeChannels) { channel ->
                            ChannelCard(
                                name = channel.name,
                                memberCount = channel.memberCount,
                                isLive = channel.isTransmitting,
                                onClick = onNavigateToChannels
                            )
                        }
                    }
                }
            }

            // Groups Section
            if (uiState.activeGroups.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(28.dp))
                    SectionHeader(
                        title = "Your Groups",
                        subtitle = "${uiState.activeGroups.size} groups",
                        actionText = "All",
                        onAction = onNavigateToGroups,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }

                items(uiState.activeGroups.take(3)) { group ->
                    GroupCard(
                        name = group.name,
                        memberCount = group.memberCount,
                        hasCall = group.hasActiveCall,
                        unreadCount = group.unreadCount,
                        onClick = onNavigateToGroups,
                        onCallClick = { onStartCall(group.id) },
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

// ============================================================================
// PREMIUM HEADER
// ============================================================================

@Composable
private fun PremiumHeader(
    username: String,
    onSettingsClick: () -> Unit
) {
    val hour = remember { java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) }
    val greeting = when {
        hour < 12 -> "Good Morning"
        hour < 17 -> "Good Afternoon"
        else -> "Good Evening"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
            .statusBarsPadding(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = greeting,
                style = MaterialTheme.typography.bodyMedium,
                color = PremiumColors.TextSecondary
            )
            Text(
                text = username,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = PremiumColors.TextPrimary
            )
        }

        IconButton(
            onClick = onSettingsClick,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(PremiumColors.GlassWhite)
        ) {
            Icon(
                Icons.Outlined.Settings,
                contentDescription = "Settings",
                tint = PremiumColors.TextSecondary
            )
        }
    }
}

// ============================================================================
// NETWORK STATUS HERO
// ============================================================================

@Composable
private fun NetworkStatusHero(
    isConnected: Boolean,
    peerCount: Int,
    modifier: Modifier = Modifier
) {
    val primaryColor = if (isConnected) PremiumColors.ElectricCyan else PremiumColors.OfflineGray

    // Simple pulse animation
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Main orb
        Box(
            modifier = Modifier
                .size(200.dp)
                .scale(if (isConnected) scale else 1f),
            contentAlignment = Alignment.Center
        ) {
            // Outer glow ring
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .clip(CircleShape)
                    .background(primaryColor.copy(alpha = 0.1f))
            )

            // Middle ring
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .clip(CircleShape)
                    .background(primaryColor.copy(alpha = 0.15f))
                    .border(
                        width = 1.dp,
                        color = primaryColor.copy(alpha = 0.3f),
                        shape = CircleShape
                    )
            )

            // Core orb
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                primaryColor.copy(alpha = 0.4f),
                                primaryColor.copy(alpha = 0.2f)
                            )
                        )
                    )
                    .border(
                        width = 2.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(primaryColor, PremiumColors.HoloPurple)
                        ),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = if (isConnected) Icons.Filled.Wifi else Icons.Filled.WifiOff,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = primaryColor
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isConnected) peerCount.toString() else "—",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = PremiumColors.TextPrimary
                    )
                    Text(
                        text = if (isConnected) "PEERS" else "OFFLINE",
                        style = MaterialTheme.typography.labelSmall,
                        color = PremiumColors.TextSecondary,
                        letterSpacing = 1.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Status badge
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(if (isConnected) PremiumColors.OnlineGlow.copy(alpha = 0.15f) else PremiumColors.GlassWhite)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (isConnected) PremiumColors.OnlineGlow else PremiumColors.OfflineGray)
            )
            Text(
                text = if (isConnected) "Mesh Network Active" else "Searching...",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = PremiumColors.TextPrimary
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Encryption badge
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(PremiumColors.GlassWhite)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = PremiumColors.LaserLime
            )
            Text(
                text = "E2E Encrypted • MLS",
                style = MaterialTheme.typography.labelSmall,
                color = PremiumColors.TextSecondary
            )
        }
    }
}

// ============================================================================
// QUICK STATS ROW
// ============================================================================

@Composable
private fun QuickStatsRow(
    peerCount: Int,
    meshNodes: Int,
    latency: Long,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.People,
            value = peerCount.toString(),
            label = "Peers",
            color = PremiumColors.ElectricCyan
        )
        StatCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.Hub,
            value = meshNodes.toString(),
            label = "Nodes",
            color = PremiumColors.HoloPurple
        )
        StatCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.Speed,
            value = "${latency}ms",
            label = "Latency",
            color = when {
                latency < 50 -> PremiumColors.LaserLime
                latency < 150 -> PremiumColors.ConnectingAmber
                else -> PremiumColors.BusyRed
            }
        )
    }
}

@Composable
private fun StatCard(
    icon: ImageVector,
    value: String,
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(PremiumColors.GlassWhite)
            .border(1.dp, PremiumColors.GlassBorder, RoundedCornerShape(16.dp))
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = color
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = PremiumColors.TextPrimary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = PremiumColors.TextSecondary
        )
    }
}

// ============================================================================
// ACTION BUTTONS
// ============================================================================

@Composable
private fun ActionButtonsRow(
    onSOSClick: () -> Unit,
    onMapClick: () -> Unit,
    onGroupsClick: () -> Unit,
    onChannelsClick: () -> Unit,
    hasActiveAlert: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ActionButton(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.Warning,
            label = "SOS",
            colors = listOf(PremiumColors.NeonMagenta, PremiumColors.BusyRed),
            isHighlighted = hasActiveAlert,
            onClick = onSOSClick
        )
        ActionButton(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.Map,
            label = "Track",
            colors = listOf(PremiumColors.ElectricCyan, PremiumColors.HoloPurple),
            onClick = onMapClick
        )
        ActionButton(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.Groups,
            label = "Groups",
            colors = listOf(PremiumColors.LaserLime, PremiumColors.ElectricCyan),
            onClick = onGroupsClick
        )
        ActionButton(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.RecordVoiceOver,
            label = "PTT",
            colors = listOf(PremiumColors.HoloPurple, PremiumColors.NeonMagenta),
            onClick = onChannelsClick
        )
    }
}

@Composable
private fun ActionButton(
    icon: ImageVector,
    label: String,
    colors: List<Color>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isHighlighted: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.6f),
        label = "scale"
    )

    Column(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(20.dp))
            .background(PremiumColors.GlassWhite)
            .border(
                width = if (isHighlighted) 2.dp else 1.dp,
                color = if (isHighlighted) colors[0] else PremiumColors.GlassBorder,
                shape = RoundedCornerShape(20.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(colors)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(22.dp),
                tint = Color.White
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = PremiumColors.TextPrimary
        )
    }
}

// ============================================================================
// SECONDARY ACTIONS (Contacts)
// ============================================================================

@Composable
private fun SecondaryActionsRow(
    onContactsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Contacts Button - Full width prominent button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(PremiumColors.GlassWhite)
                .border(1.dp, PremiumColors.ElectricCyan.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                .clickable(onClick = onContactsClick)
                .padding(vertical = 14.dp, horizontal = 20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(PremiumColors.ElectricCyan, PremiumColors.LaserLime)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Contacts,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Contacts",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = PremiumColors.TextPrimary
                    )
                    Text(
                        text = "Add & manage contacts • Scan QR",
                        style = MaterialTheme.typography.labelSmall,
                        color = PremiumColors.TextSecondary
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = PremiumColors.ElectricCyan
                )
            }
        }
    }
}

// ============================================================================
// SECTION HEADER
// ============================================================================

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = PremiumColors.TextPrimary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = PremiumColors.TextSecondary
            )
        }
        if (actionText != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(actionText, color = PremiumColors.ElectricCyan)
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = PremiumColors.ElectricCyan
                )
            }
        }
    }
}

// ============================================================================
// TRACKING CARD
// ============================================================================

@Composable
private fun TrackingCard(
    memberCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(140.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(PremiumColors.SpaceGray)
            .border(1.dp, PremiumColors.GlassBorder, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
    ) {
        // Simple radar visualization
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // Rings
            listOf(100.dp, 70.dp, 40.dp).forEach { size ->
                Box(
                    modifier = Modifier
                        .size(size)
                        .clip(CircleShape)
                        .border(1.dp, PremiumColors.ElectricCyan.copy(alpha = 0.2f), CircleShape)
                )
            }

            // Center dot
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(PremiumColors.ElectricCyan)
            )

            // Member indicators
            if (memberCount > 0) {
                Box(
                    modifier = Modifier
                        .offset(x = 30.dp, y = (-20).dp)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(PremiumColors.LaserLime)
                )
            }
            if (memberCount > 1) {
                Box(
                    modifier = Modifier
                        .offset(x = (-25).dp, y = 15.dp)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(PremiumColors.LaserLime)
                )
            }
        }

        // Expand icon
        Icon(
            Icons.Default.Fullscreen,
            contentDescription = "Expand",
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .size(20.dp),
            tint = PremiumColors.TextSecondary
        )

        // Member count
        Text(
            text = "$memberCount team members",
            style = MaterialTheme.typography.labelSmall,
            color = PremiumColors.TextSecondary,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp)
        )
    }
}

// ============================================================================
// CHANNEL CARD
// ============================================================================

@Composable
private fun ChannelCard(
    name: String,
    memberCount: Int,
    isLive: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(160.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(PremiumColors.GlassWhite)
            .border(
                width = if (isLive) 2.dp else 1.dp,
                color = if (isLive) PremiumColors.LaserLime else PremiumColors.GlassBorder,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(PremiumColors.ElectricCyan, PremiumColors.HoloPurple)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.RecordVoiceOver,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = Color.White
                    )
                }

                if (isLive) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(PremiumColors.LaserLime)
                        )
                        Text(
                            text = "LIVE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = PremiumColors.LaserLime,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = PremiumColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "$memberCount online",
                style = MaterialTheme.typography.labelSmall,
                color = PremiumColors.TextSecondary
            )
        }
    }
}

// ============================================================================
// GROUP CARD
// ============================================================================

@Composable
private fun GroupCard(
    name: String,
    memberCount: Int,
    hasCall: Boolean,
    unreadCount: Int,
    onClick: () -> Unit,
    onCallClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(PremiumColors.GlassWhite)
            .border(1.dp, PremiumColors.GlassBorder, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    Brush.linearGradient(
                        listOf(PremiumColors.ElectricCyan, PremiumColors.HoloPurple)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Groups,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = Color.White
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = PremiumColors.TextPrimary
                )
                if (hasCall) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(PremiumColors.LaserLime.copy(alpha = 0.2f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Call",
                            style = MaterialTheme.typography.labelSmall,
                            color = PremiumColors.LaserLime,
                            fontSize = 10.sp
                        )
                    }
                }
            }
            Text(
                text = "$memberCount members",
                style = MaterialTheme.typography.bodySmall,
                color = PremiumColors.TextSecondary
            )
        }

        if (unreadCount > 0) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(PremiumColors.NeonMagenta),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (unreadCount > 9) "9+" else unreadCount.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 10.sp
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Call button
        IconButton(
            onClick = onCallClick,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(PremiumColors.LaserLime.copy(alpha = 0.2f))
        ) {
            Icon(
                Icons.Default.Call,
                contentDescription = "Start Group Call",
                modifier = Modifier.size(18.dp),
                tint = PremiumColors.LaserLime
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = PremiumColors.TextSecondary
        )
    }
}

// ============================================================================
// DATA CLASSES
// ============================================================================

data class PTTChannelUiState(
    val id: String,
    val name: String,
    val memberCount: Int,
    val isTransmitting: Boolean,
    val priority: String
)

data class GroupUiState(
    val id: String,
    val name: String,
    val memberCount: Int,
    val hasActiveCall: Boolean,
    val unreadCount: Int
)

data class TrackedMemberUiState(
    val id: String,
    val name: String,
    val status: String,
    val latitude: Double,
    val longitude: Double
)

data class ActivityUiState(
    val type: String,
    val description: String,
    val timestamp: String
)

// ============================================================================
// RADIO STATUS CARD
// ============================================================================

@Composable
private fun RadioStatusCard(
    isConnected: Boolean,
    isConnecting: Boolean,
    radioIp: String?,
    radioHostname: String,
    radioModel: String,
    ssid: String,
    channel: Int,
    signal: Int,
    snr: Int,
    linkQuality: Int,
    meshPeerCount: Int,
    discoveredRadios: List<DiscoveredRadioUiState>,
    error: String?,
    onConnectRadio: (String) -> Unit,
    onDisconnect: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showRadioSelector by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(PremiumColors.SpaceGray)
            .border(
                width = 1.dp,
                color = if (isConnected) PremiumColors.LaserLime.copy(alpha = 0.3f) else PremiumColors.GlassBorder,
                shape = RoundedCornerShape(20.dp)
            )
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            if (isConnected)
                                Brush.linearGradient(listOf(PremiumColors.LaserLime, PremiumColors.ElectricCyan))
                            else
                                Brush.linearGradient(listOf(PremiumColors.OfflineGray, PremiumColors.SpaceGrayLight))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Router,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = Color.White
                    )
                }
                Column {
                    Text(
                        text = "MeshRider Radio",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = PremiumColors.TextPrimary
                    )
                    Text(
                        text = if (isConnected) radioHostname.ifEmpty { radioIp ?: "Connected" } else "Not Connected",
                        style = MaterialTheme.typography.labelSmall,
                        color = PremiumColors.TextSecondary
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Connection indicator
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isConnecting -> PremiumColors.ConnectingAmber
                                isConnected -> PremiumColors.LaserLime
                                else -> PremiumColors.OfflineGray
                            }
                        )
                )

                // Refresh button
                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        modifier = Modifier.size(18.dp),
                        tint = PremiumColors.TextSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isConnected) {
            // Connected state - show status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                RadioStatItem(
                    icon = Icons.Default.Wifi,
                    value = "$signal dBm",
                    label = "Signal",
                    color = when {
                        signal >= -60 -> PremiumColors.LaserLime
                        signal >= -75 -> PremiumColors.ConnectingAmber
                        else -> PremiumColors.BusyRed
                    }
                )
                RadioStatItem(
                    icon = Icons.Default.SignalCellularAlt,
                    value = "$snr dB",
                    label = "SNR",
                    color = when {
                        snr >= 20 -> PremiumColors.LaserLime
                        snr >= 10 -> PremiumColors.ConnectingAmber
                        else -> PremiumColors.BusyRed
                    }
                )
                RadioStatItem(
                    icon = Icons.Default.Hub,
                    value = meshPeerCount.toString(),
                    label = "Peers",
                    color = PremiumColors.ElectricCyan
                )
                RadioStatItem(
                    icon = Icons.Default.SettingsInputAntenna,
                    value = "CH $channel",
                    label = "Channel",
                    color = PremiumColors.HoloPurple
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // SSID and Quality bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = ssid.ifEmpty { "MeshRider" },
                    style = MaterialTheme.typography.labelSmall,
                    color = PremiumColors.TextSecondary
                )
                Text(
                    text = "$linkQuality% Link Quality",
                    style = MaterialTheme.typography.labelSmall,
                    color = PremiumColors.TextSecondary
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Link quality progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(PremiumColors.GlassWhite)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(linkQuality / 100f)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            when {
                                linkQuality >= 70 -> PremiumColors.LaserLime
                                linkQuality >= 40 -> PremiumColors.ConnectingAmber
                                else -> PremiumColors.BusyRed
                            }
                        )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Disconnect button
            TextButton(
                onClick = onDisconnect,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(
                    text = "Disconnect",
                    style = MaterialTheme.typography.labelMedium,
                    color = PremiumColors.NeonMagenta
                )
            }
        } else if (isConnecting) {
            // Connecting state
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = PremiumColors.ElectricCyan,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Connecting to radio...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PremiumColors.TextSecondary
                )
            }
        } else {
            // Disconnected state - show discovered radios
            if (error != null) {
                Text(
                    text = error,
                    style = MaterialTheme.typography.labelSmall,
                    color = PremiumColors.BusyRed,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            if (discoveredRadios.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.SearchOff,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = PremiumColors.TextSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No radios found",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PremiumColors.TextSecondary
                    )
                    Text(
                        text = "Make sure you're connected to the mesh network",
                        style = MaterialTheme.typography.labelSmall,
                        color = PremiumColors.TextTertiary
                    )
                }
            } else {
                Text(
                    text = "${discoveredRadios.size} radio(s) found",
                    style = MaterialTheme.typography.labelSmall,
                    color = PremiumColors.TextSecondary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                discoveredRadios.take(3).forEach { radio ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(PremiumColors.GlassWhite)
                            .clickable { onConnectRadio(radio.ipAddress) }
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = radio.hostname,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = PremiumColors.TextPrimary
                            )
                            Text(
                                text = "${radio.ipAddress} • ${radio.model}",
                                style = MaterialTheme.typography.labelSmall,
                                color = PremiumColors.TextSecondary
                            )
                        }
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = "Connect",
                            modifier = Modifier.size(20.dp),
                            tint = PremiumColors.ElectricCyan
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun RadioStatItem(
    icon: ImageVector,
    value: String,
    label: String,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = color
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = PremiumColors.TextPrimary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = PremiumColors.TextSecondary
        )
    }
}
