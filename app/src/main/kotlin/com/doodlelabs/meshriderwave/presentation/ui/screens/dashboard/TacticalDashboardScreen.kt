/*
 * Mesh Rider Wave - Tactical Dashboard 2026
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 *
 * Military-Grade Tactical Dashboard - Starlink Inspired
 * For Defense Agency Deployment
 */

package com.doodlelabs.meshriderwave.presentation.ui.screens.dashboard

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.doodlelabs.meshriderwave.presentation.ui.theme.PremiumColors
import com.doodlelabs.meshriderwave.presentation.viewmodel.DashboardViewModel

// Starlink Professional Color Palette - Monochrome + Single Accent
object TacticalColors {
    // Base (Deep space black)
    val Background = Color(0xFF000000)
    val Surface = Color(0xFF0D0D0D)
    val SurfaceElevated = Color(0xFF1A1A1A)

    // Primary Accent (Starlink cyan-white)
    val Accent = Color(0xFF00B4D8)
    val AccentBright = Color(0xFF48CAE4)
    val AccentDim = Color(0xFF0077B6)

    // Critical Only (Reserved for SOS/Emergency)
    val Critical = Color(0xFFDC2626)

    // Text (Pure grayscale)
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFFA3A3A3)
    val TextMuted = Color(0xFF525252)

    // Borders & Surfaces
    val BorderSubtle = Color(0xFF262626)
    val BorderActive = Color(0xFF404040)

    // Semantic (all derived from accent)
    val Online = Accent
    val Offline = TextMuted
}

// Readiness Levels (Monochrome status)
enum class ReadinessLevel(val label: String, val color: Color, val description: String) {
    ALPHA("ONLINE", TacticalColors.Accent, "All Systems Operational"),
    BRAVO("READY", TacticalColors.AccentDim, "Limited Connectivity"),
    CHARLIE("STANDBY", TacticalColors.TextSecondary, "Degraded Operations"),
    DELTA("OFFLINE", TacticalColors.Critical, "Critical - Offline")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TacticalDashboardScreen(
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

    // Calculate readiness level
    val readiness = remember(uiState.isServiceRunning, uiState.discoveredPeers, uiState.isRadioConnected) {
        when {
            !uiState.isServiceRunning -> ReadinessLevel.DELTA
            uiState.isRadioConnected && uiState.discoveredPeers >= 3 -> ReadinessLevel.ALPHA
            uiState.discoveredPeers >= 1 -> ReadinessLevel.BRAVO
            uiState.isServiceRunning -> ReadinessLevel.CHARLIE
            else -> ReadinessLevel.DELTA
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TacticalColors.Background)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            // Tactical Header with Callsign
            item {
                TacticalHeader(
                    callsign = uiState.username.uppercase(),
                    readiness = readiness,
                    onSettingsClick = onNavigateToSettings,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }

            // Mission Status Panel
            item {
                MissionStatusPanel(
                    readiness = readiness,
                    isRadioConnected = uiState.isRadioConnected,
                    radioSignal = uiState.radioSignal,
                    meshPeers = uiState.meshPeerCount,
                    appPeers = uiState.discoveredPeers,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                )
            }

            // Tactical Metrics Grid
            item {
                TacticalMetricsGrid(
                    commsOnline = uiState.discoveredPeers,
                    nodesActive = uiState.meshPeerCount,
                    channelsJoined = uiState.activeChannels.size,
                    teamsTracked = uiState.trackedTeamMembers.size,
                    hasSOS = uiState.hasActiveSOS,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }

            // Quick Actions - Military Style
            item {
                Spacer(modifier = Modifier.height(24.dp))
                TacticalActionsGrid(
                    onSOSClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.activateSOS()
                    },
                    onMapClick = onNavigateToMap,
                    onPTTClick = onNavigateToChannels,
                    onContactsClick = onNavigateToContacts,
                    hasActiveAlert = uiState.hasActiveSOS,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }

            // Radio Status (when connected)
            if (uiState.isRadioConnected) {
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    RadioTelemetryCard(
                        ssid = uiState.radioSsid,
                        channel = uiState.radioChannel,
                        signal = uiState.radioSignal,
                        snr = uiState.radioSnr,
                        linkQuality = uiState.radioLinkQuality,
                        bitrate = uiState.radioBitrate,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }
            }

            // Blue Force Tracking Mini-Map
            item {
                Spacer(modifier = Modifier.height(24.dp))
                SectionLabel("SITUATIONAL AWARENESS", Modifier.padding(horizontal = 20.dp))
                Spacer(modifier = Modifier.height(12.dp))
                TacticalRadarCard(
                    trackedMembers = uiState.trackedTeamMembers,
                    myLatitude = uiState.myLatitude,
                    myLongitude = uiState.myLongitude,
                    onClick = onNavigateToMap,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }

            // Active Channels
            if (uiState.activeChannels.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    SectionLabel(
                        text = "COMMS CHANNELS (${uiState.activeChannels.size})",
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(horizontal = 20.dp)
                    ) {
                        items(uiState.activeChannels) { channel ->
                            TacticalChannelCard(
                                name = channel.name,
                                memberCount = channel.memberCount,
                                isLive = channel.isTransmitting,
                                priority = channel.priority,
                                onClick = onNavigateToChannels
                            )
                        }
                    }
                }
            }

            // Groups
            if (uiState.activeGroups.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    SectionLabel(
                        text = "UNIT GROUPS (${uiState.activeGroups.size})",
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
                items(uiState.activeGroups.take(3)) { group ->
                    TacticalGroupCard(
                        name = group.name,
                        memberCount = group.memberCount,
                        hasCall = group.hasActiveCall,
                        onClick = onNavigateToGroups,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

// ============================================================================
// TACTICAL HEADER
// ============================================================================

@Composable
private fun TacticalHeader(
    callsign: String,
    readiness: ReadinessLevel,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentTime = remember {
        java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date())
    }
    val currentDate = remember {
        java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault())
            .format(java.util.Date()).uppercase()
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 48.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column {
            // Callsign
            Text(
                text = callsign,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                color = TacticalColors.TextPrimary,
                letterSpacing = 2.sp
            )

            // Date/Time
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = currentDate,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = TacticalColors.TextMuted
                )
                Text("•", color = TacticalColors.TextMuted)
                Text(
                    text = "${currentTime}Z",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = TacticalColors.TextSecondary
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Readiness Badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(readiness.color.copy(alpha = 0.15f))
                    .border(1.dp, readiness.color.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = readiness.label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = readiness.color,
                    letterSpacing = 1.sp
                )
            }

            // Settings
            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(TacticalColors.Surface)
            ) {
                Icon(
                    Icons.Outlined.Settings,
                    contentDescription = "Settings",
                    modifier = Modifier.size(20.dp),
                    tint = TacticalColors.TextSecondary
                )
            }
        }
    }
}

// ============================================================================
// MISSION STATUS PANEL
// ============================================================================

@Composable
private fun MissionStatusPanel(
    readiness: ReadinessLevel,
    isRadioConnected: Boolean,
    radioSignal: Int,
    meshPeers: Int,
    appPeers: Int,
    modifier: Modifier = Modifier
) {
    // BATTERY OPTIMIZATION Jan 2026: Only animate when visible
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val isResumed = lifecycleOwner.lifecycle.currentState.isAtLeast(
        androidx.lifecycle.Lifecycle.State.RESUMED
    )

    // Pulse animation for active status - only when visible
    val pulseAlpha = if (isResumed && readiness == ReadinessLevel.ALPHA) {
        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        infiniteTransition.animateFloat(
            initialValue = 0.3f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulseAlpha"
        ).value
    } else {
        1f // Static when backgrounded or not ALPHA
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(TacticalColors.Surface)
            .border(1.dp, readiness.color.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            .padding(20.dp)
    ) {
        // Status Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Pulsing indicator (battery-optimized)
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .alpha(pulseAlpha)
                        .clip(CircleShape)
                        .background(readiness.color)
                )

                Column {
                    Text(
                        text = "MISSION STATUS",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = TacticalColors.TextMuted,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = readiness.description,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TacticalColors.TextPrimary
                    )
                }
            }

            // Signal Indicator
            if (isRadioConnected) {
                Column(horizontalAlignment = Alignment.End) {
                    SignalBars(signal = radioSignal)
                    Text(
                        text = "$radioSignal dBm",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = TacticalColors.TextMuted
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Connection Status Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatusIndicator(
                icon = Icons.Default.Router,
                label = "RADIO",
                value = if (isRadioConnected) "LINKED" else "OFFLINE",
                isActive = isRadioConnected,
                color = if (isRadioConnected) TacticalColors.Accent else TacticalColors.Offline
            )
            StatusIndicator(
                icon = Icons.Default.Hub,
                label = "MESH",
                value = "$meshPeers NODES",
                isActive = meshPeers > 0,
                color = if (meshPeers > 0) TacticalColors.Accent else TacticalColors.Offline
            )
            StatusIndicator(
                icon = Icons.Default.People,
                label = "COMMS",
                value = "$appPeers ONLINE",
                isActive = appPeers > 0,
                color = if (appPeers > 0) TacticalColors.Accent else TacticalColors.Offline
            )
        }
    }
}

@Composable
private fun StatusIndicator(
    icon: ImageVector,
    label: String,
    value: String,
    isActive: Boolean,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(color.copy(alpha = if (isActive) 0.15f else 0.05f))
                .border(
                    1.dp,
                    color.copy(alpha = if (isActive) 0.5f else 0.1f),
                    RoundedCornerShape(12.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = color
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = TacticalColors.TextMuted,
            fontSize = 10.sp
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = color,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun SignalBars(signal: Int) {
    val bars = when {
        signal >= -50 -> 4
        signal >= -60 -> 3
        signal >= -70 -> 2
        signal >= -80 -> 1
        else -> 0
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        repeat(4) { index ->
            val height = (index + 1) * 4
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(height.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(
                        if (index < bars) TacticalColors.Accent
                        else TacticalColors.TextMuted.copy(alpha = 0.3f)
                    )
            )
        }
    }
}

// ============================================================================
// TACTICAL METRICS GRID
// ============================================================================

@Composable
private fun TacticalMetricsGrid(
    commsOnline: Int,
    nodesActive: Int,
    channelsJoined: Int,
    teamsTracked: Int,
    hasSOS: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        MetricCard(
            modifier = Modifier.weight(1f),
            value = commsOnline.toString(),
            label = "COMMS",
            color = TacticalColors.Accent
        )
        MetricCard(
            modifier = Modifier.weight(1f),
            value = nodesActive.toString(),
            label = "NODES",
            color = TacticalColors.Accent
        )
        MetricCard(
            modifier = Modifier.weight(1f),
            value = channelsJoined.toString(),
            label = "CHANS",
            color = TacticalColors.Accent
        )
        MetricCard(
            modifier = Modifier.weight(1f),
            value = teamsTracked.toString(),
            label = "TRACK",
            color = if (hasSOS) TacticalColors.Critical else TacticalColors.Accent,
            isAlert = hasSOS
        )
    }
}

@Composable
private fun MetricCard(
    value: String,
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    isAlert: Boolean = false
) {
    // BATTERY OPTIMIZATION Jan 2026: Only animate alerts when visible
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val isResumed = lifecycleOwner.lifecycle.currentState.isAtLeast(
        androidx.lifecycle.Lifecycle.State.RESUMED
    )

    val alertAlpha = if (isAlert && isResumed) {
        val infiniteTransition = rememberInfiniteTransition(label = "alert")
        infiniteTransition.animateFloat(
            initialValue = 0.5f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(500),
                repeatMode = RepeatMode.Reverse
            ),
            label = "alertAlpha"
        ).value
    } else {
        1f // Static when no alert or backgrounded
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(TacticalColors.Surface)
            .border(
                1.dp,
                color.copy(alpha = if (isAlert) alertAlpha * 0.8f else 0.3f),
                RoundedCornerShape(12.dp)
            )
            .padding(vertical = 16.dp, horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                color = color
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = TacticalColors.TextMuted,
                letterSpacing = 1.sp,
                fontSize = 10.sp
            )
        }
    }
}

// ============================================================================
// TACTICAL ACTIONS GRID
// ============================================================================

@Composable
private fun TacticalActionsGrid(
    onSOSClick: () -> Unit,
    onMapClick: () -> Unit,
    onPTTClick: () -> Unit,
    onContactsClick: () -> Unit,
    hasActiveAlert: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TacticalActionButton(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Warning,
                label = "SOS",
                sublabel = if (hasActiveAlert) "ACTIVE" else "EMERGENCY",
                color = TacticalColors.Critical,
                isHighlighted = hasActiveAlert,
                onClick = onSOSClick
            )
            TacticalActionButton(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Map,
                label = "MAP",
                sublabel = "TRACKING",
                color = TacticalColors.Accent,
                onClick = onMapClick
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TacticalActionButton(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Mic,
                label = "PTT",
                sublabel = "CHANNELS",
                color = TacticalColors.Accent,
                onClick = onPTTClick
            )
            TacticalActionButton(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.People,
                label = "CONTACTS",
                sublabel = "ROSTER",
                color = TacticalColors.Accent,
                onClick = onContactsClick
            )
        }
    }
}

@Composable
private fun TacticalActionButton(
    icon: ImageVector,
    label: String,
    sublabel: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isHighlighted: Boolean = false
) {
    // BATTERY OPTIMIZATION Jan 2026: Only animate when highlighted AND visible
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val isResumed = lifecycleOwner.lifecycle.currentState.isAtLeast(
        androidx.lifecycle.Lifecycle.State.RESUMED
    )

    val highlightAlpha = if (isHighlighted && isResumed) {
        val infiniteTransition = rememberInfiniteTransition(label = "highlight")
        infiniteTransition.animateFloat(
            initialValue = 0.3f,
            targetValue = 0.8f,
            animationSpec = infiniteRepeatable(
                animation = tween(500),
                repeatMode = RepeatMode.Reverse
            ),
            label = "highlightAlpha"
        ).value
    } else {
        0.8f // Static when not highlighted or backgrounded
    }

    Box(
        modifier = modifier
            .height(90.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(TacticalColors.Surface)
            .border(
                width = if (isHighlighted) 2.dp else 1.dp,
                color = if (isHighlighted) color.copy(alpha = highlightAlpha) else TacticalColors.BorderSubtle,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    modifier = Modifier.size(24.dp),
                    tint = color
                )
            }

            // Labels
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = TacticalColors.TextPrimary,
                    letterSpacing = 1.sp
                )
                Text(
                    text = sublabel,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = color,
                    letterSpacing = 0.5.sp,
                    fontSize = 10.sp
                )
            }
        }
    }
}

// ============================================================================
// RADIO TELEMETRY CARD
// ============================================================================

@Composable
private fun RadioTelemetryCard(
    ssid: String,
    channel: Int,
    signal: Int,
    snr: Int,
    linkQuality: Int,
    bitrate: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(TacticalColors.Surface)
            .border(1.dp, TacticalColors.BorderSubtle, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "RADIO TELEMETRY",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                color = TacticalColors.TextMuted,
                letterSpacing = 1.sp
            )
            Text(
                text = ssid.ifEmpty { "MESHRIDER" },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = TacticalColors.Accent
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Telemetry Grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TelemetryItem("CHAN", "$channel", TacticalColors.Accent)
            TelemetryItem(
                "RSSI",
                "$signal dBm",
                when {
                    signal >= -60 -> TacticalColors.Accent
                    signal >= -75 -> TacticalColors.TextSecondary
                    else -> TacticalColors.Critical
                }
            )
            TelemetryItem(
                "SNR",
                "$snr dB",
                when {
                    snr >= 20 -> TacticalColors.Accent
                    snr >= 10 -> TacticalColors.TextSecondary
                    else -> TacticalColors.Critical
                }
            )
            TelemetryItem("LINK", "$linkQuality%", TacticalColors.Accent)
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Link Quality Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(TacticalColors.BorderSubtle)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(linkQuality / 100f)
                    .clip(RoundedCornerShape(2.dp))
                    .background(TacticalColors.Accent)
            )
        }
    }
}

@Composable
private fun TelemetryItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = TacticalColors.TextMuted,
            fontSize = 10.sp
        )
    }
}

// ============================================================================
// TACTICAL RADAR CARD
// ============================================================================

/**
 * Tactical Radar Card - REAL GPS POSITIONS
 * Military-grade situational awareness display
 *
 * Displays team member positions relative to own location.
 * Range: 500m radius (configurable via RADAR_RANGE_METERS)
 */
@Composable
private fun TacticalRadarCard(
    trackedMembers: List<TrackedMemberUiState>,
    myLatitude: Double,
    myLongitude: Double,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Radar range in meters (500m default)
    val radarRangeMeters = 500.0

    // BATTERY OPTIMIZATION Jan 2026: Only animate when screen is visible
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val isResumed = lifecycleOwner.lifecycle.currentState.isAtLeast(
        androidx.lifecycle.Lifecycle.State.RESUMED
    )

    // Use static angle when paused to save battery
    val sweepAngle = if (isResumed) {
        val infiniteTransition = rememberInfiniteTransition(label = "radar")
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(3000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "sweep"
        ).value
    } else {
        0f // Static when backgrounded
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(160.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(TacticalColors.Surface)
            .border(1.dp, TacticalColors.BorderSubtle, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        // Radar visualization with REAL GPS positions
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            val maxRadius = minOf(size.width, size.height) / 2 - 20

            // Concentric circles (range rings)
            listOf(0.33f, 0.66f, 1f).forEach { factor ->
                drawCircle(
                    color = TacticalColors.Accent.copy(alpha = 0.1f),
                    radius = maxRadius * factor,
                    center = center,
                    style = Stroke(width = 1f)
                )
            }

            // Cross lines (compass)
            drawLine(
                color = TacticalColors.Accent.copy(alpha = 0.1f),
                start = Offset(center.x - maxRadius, center.y),
                end = Offset(center.x + maxRadius, center.y),
                strokeWidth = 1f
            )
            drawLine(
                color = TacticalColors.Accent.copy(alpha = 0.1f),
                start = Offset(center.x, center.y - maxRadius),
                end = Offset(center.x, center.y + maxRadius),
                strokeWidth = 1f
            )

            // Center dot (my position)
            drawCircle(
                color = TacticalColors.Accent,
                radius = 6f,
                center = center
            )

            // REAL GPS POSITIONS - Calculate relative positions
            if (myLatitude != 0.0 && myLongitude != 0.0) {
                trackedMembers.forEach { member ->
                    // Calculate distance and bearing from my position
                    val deltaLat = member.latitude - myLatitude
                    val deltaLon = member.longitude - myLongitude

                    // Convert to meters (approximate)
                    val latMeters = deltaLat * 111320.0  // 1 degree lat ≈ 111.32 km
                    val lonMeters = deltaLon * 111320.0 * kotlin.math.cos(Math.toRadians(myLatitude))

                    // Calculate distance
                    val distance = kotlin.math.sqrt(latMeters * latMeters + lonMeters * lonMeters)

                    // Only show if within radar range
                    if (distance <= radarRangeMeters) {
                        // Scale to radar display (distance / range * maxRadius)
                        val scale = (distance / radarRangeMeters).toFloat().coerceIn(0f, 1f)
                        val displayRadius = maxRadius * scale

                        // Calculate angle (bearing)
                        val angle = kotlin.math.atan2(lonMeters, latMeters)

                        // Convert to screen coordinates
                        val memberX = center.x + (displayRadius * kotlin.math.sin(angle)).toFloat()
                        val memberY = center.y - (displayRadius * kotlin.math.cos(angle)).toFloat()

                        // Draw member dot
                        drawCircle(
                            color = when (member.status) {
                                "MOVING" -> TacticalColors.AccentBright
                                "ACTIVE" -> TacticalColors.Accent
                                else -> TacticalColors.AccentDim
                            },
                            radius = 5f,
                            center = Offset(memberX, memberY)
                        )
                    }
                }
            } else {
                // No GPS fix - show "NO GPS" indicator
                // Members will appear once GPS is acquired
            }
        }

        // Radar sweep line (rotating)
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .rotate(sweepAngle)
        ) {
            val center = Offset(size.width / 2, size.height / 2)
            val maxRadius = minOf(size.width, size.height) / 2 - 20

            drawLine(
                brush = Brush.linearGradient(
                    colors = listOf(
                        TacticalColors.Accent.copy(alpha = 0.8f),
                        TacticalColors.Accent.copy(alpha = 0f)
                    ),
                    start = center,
                    end = Offset(center.x, center.y - maxRadius)
                ),
                start = center,
                end = Offset(center.x, center.y - maxRadius),
                strokeWidth = 2f,
                cap = StrokeCap.Round
            )
        }

        // Labels - show actual tracked count
        Text(
            text = "${trackedMembers.size} TRACKED",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = TacticalColors.TextMuted,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp)
        )

        Icon(
            Icons.Default.Fullscreen,
            contentDescription = "Expand",
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .size(18.dp),
            tint = TacticalColors.TextMuted
        )
    }
}

// ============================================================================
// TACTICAL CHANNEL CARD
// ============================================================================

@Composable
private fun TacticalChannelCard(
    name: String,
    memberCount: Int,
    isLive: Boolean,
    priority: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(150.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(TacticalColors.Surface)
            .border(
                width = if (isLive) 2.dp else 1.dp,
                color = if (isLive) TacticalColors.Accent else TacticalColors.BorderSubtle,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Mic,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = if (isLive) TacticalColors.Accent else TacticalColors.TextMuted
                )
                if (isLive) {
                    Text(
                        text = "TX",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = TacticalColors.Accent,
                        fontSize = 10.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = name.uppercase(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = TacticalColors.TextPrimary,
                maxLines = 1,
                letterSpacing = 0.5.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "$memberCount ONLINE",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = TacticalColors.TextMuted,
                fontSize = 10.sp
            )
        }
    }
}

// ============================================================================
// TACTICAL GROUP CARD
// ============================================================================

@Composable
private fun TacticalGroupCard(
    name: String,
    memberCount: Int,
    hasCall: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(TacticalColors.Surface)
            .border(1.dp, TacticalColors.BorderSubtle, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(TacticalColors.Accent.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Groups,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = TacticalColors.Accent
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = name.uppercase(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = TacticalColors.TextPrimary,
                    letterSpacing = 0.5.sp
                )
                if (hasCall) {
                    Text(
                        text = "ACTIVE",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = TacticalColors.Accent,
                        fontSize = 9.sp
                    )
                }
            }
            Text(
                text = "$memberCount MEMBERS",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = TacticalColors.TextMuted,
                fontSize = 10.sp
            )
        }

        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = TacticalColors.TextMuted
        )
    }
}

// ============================================================================
// SECTION LABEL
// ============================================================================

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        color = TacticalColors.TextMuted,
        letterSpacing = 2.sp,
        modifier = modifier
    )
}
