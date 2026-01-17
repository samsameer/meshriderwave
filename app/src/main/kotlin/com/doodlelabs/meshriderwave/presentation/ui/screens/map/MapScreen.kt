/*
 * Mesh Rider Wave - Blue Force Tracking Map Screen
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 *
 * Tactical map with real-time team locations
 * - Blue Force Tracking (friendly positions)
 * - Geofencing alerts
 * - Track history trails
 * - SOS alert locations
 */

package com.doodlelabs.meshriderwave.presentation.ui.screens.map

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.doodlelabs.meshriderwave.presentation.ui.components.*
import com.doodlelabs.meshriderwave.presentation.ui.theme.PremiumColors
import com.doodlelabs.meshriderwave.presentation.viewmodel.MapViewModel
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    onNavigateBack: () -> Unit,
    viewModel: MapViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showTeamList by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    DeepSpaceBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "Blue Force Tracking",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = PremiumColors.TextPrimary
                            )
                            Text(
                                text = "${uiState.trackedMembers.size} team members tracked",
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
                        IconButton(onClick = { showTeamList = !showTeamList }) {
                            Icon(
                                if (showTeamList) Icons.Default.Map else Icons.Default.List,
                                contentDescription = "Toggle View",
                                tint = PremiumColors.ElectricCyan
                            )
                        }
                        IconButton(onClick = { viewModel.refreshLocations() }) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                tint = PremiumColors.TextSecondary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                if (showTeamList) {
                    // Team list view
                    TeamListView(
                        members = uiState.trackedMembers,
                        onMemberClick = { /* Focus on member */ },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // Map view
                    TacticalMapView(
                        myLocation = uiState.myLocation,
                        teamMembers = uiState.trackedMembers,
                        geofences = uiState.geofences,
                        sosAlerts = uiState.sosAlerts,
                        onMemberClick = { /* Focus on member */ },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Bottom controls
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                ) {
                    // Location sharing toggle
                    LocationSharingCard(
                        isSharing = uiState.isSharingLocation,
                        onToggle = { viewModel.toggleLocationSharing() }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Quick actions
                    MapQuickActions(
                        onCenterOnMe = { viewModel.centerOnMyLocation() },
                        onAddGeofence = { /* Open geofence dialog */ },
                        onSOSAlert = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.activateSOS()
                        }
                    )
                }

                // Legend
                MapLegend(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                )
            }
        }
    }
}

/**
 * Tactical map view with team positions
 */
@Composable
private fun TacticalMapView(
    myLocation: LocationUiModel?,
    teamMembers: List<TeamMemberLocationUiModel>,
    geofences: List<GeofenceUiModel>,
    sosAlerts: List<SOSAlertUiModel>,
    onMemberClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "map")
    val radarSweep by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing)
        ),
        label = "radar"
    )

    GlassCard(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        cornerRadius = 24.dp
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Tactical grid background
            Canvas(modifier = Modifier.fillMaxSize()) {
                val gridSize = 40.dp.toPx()
                val gridColor = PremiumColors.SpaceGrayLighter

                // Horizontal lines
                var y = 0f
                while (y < size.height) {
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1f
                    )
                    y += gridSize
                }

                // Vertical lines
                var x = 0f
                while (x < size.width) {
                    drawLine(
                        color = gridColor,
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = 1f
                    )
                    x += gridSize
                }

                // Center cross
                val centerX = size.width / 2
                val centerY = size.height / 2

                drawLine(
                    color = PremiumColors.ElectricCyan.copy(alpha = 0.3f),
                    start = Offset(centerX, 0f),
                    end = Offset(centerX, size.height),
                    strokeWidth = 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
                )

                drawLine(
                    color = PremiumColors.ElectricCyan.copy(alpha = 0.3f),
                    start = Offset(0f, centerY),
                    end = Offset(size.width, centerY),
                    strokeWidth = 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
                )

                // Radar sweep effect
                val sweepRad = Math.toRadians(radarSweep.toDouble())
                val sweepEndX = centerX + (size.width / 2) * cos(sweepRad).toFloat()
                val sweepEndY = centerY + (size.height / 2) * sin(sweepRad).toFloat()

                drawLine(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            PremiumColors.ElectricCyan.copy(alpha = 0.5f),
                            Color.Transparent
                        ),
                        start = Offset(centerX, centerY),
                        end = Offset(sweepEndX, sweepEndY)
                    ),
                    start = Offset(centerX, centerY),
                    end = Offset(sweepEndX, sweepEndY),
                    strokeWidth = 4f,
                    cap = StrokeCap.Round
                )

                // Range circles
                val ranges = listOf(0.25f, 0.5f, 0.75f, 1f)
                ranges.forEach { ratio ->
                    drawCircle(
                        color = PremiumColors.ElectricCyan.copy(alpha = 0.1f),
                        radius = minOf(size.width, size.height) * ratio / 2,
                        center = Offset(centerX, centerY),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = 1f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f))
                        )
                    )
                }
            }

            // Geofences
            geofences.forEach { geofence ->
                GeofenceMarker(
                    geofence = geofence,
                    modifier = Modifier
                        .offset(
                            x = (geofence.x * 3).dp,
                            y = (geofence.y * 3).dp
                        )
                )
            }

            // SOS Alerts (pulsing red)
            sosAlerts.forEach { alert ->
                SOSMarker(
                    alert = alert,
                    modifier = Modifier
                        .offset(
                            x = (50 + alert.x * 2).dp,
                            y = (50 + alert.y * 2).dp
                        )
                )
            }

            // Team members
            teamMembers.forEachIndexed { index, member ->
                TeamMemberMapMarker(
                    member = member,
                    onClick = { onMemberClick(member.id) },
                    modifier = Modifier
                        .offset(
                            x = (60 + index * 50).dp,
                            y = (60 + (index % 3) * 40).dp
                        )
                )
            }

            // My location (center)
            myLocation?.let {
                MyLocationMarker(
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            // Compass
            CompassIndicator(
                heading = myLocation?.heading ?: 0f,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
            )

            // Scale bar
            ScaleBar(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
            )
        }
    }
}

/**
 * My location marker with direction
 */
@Composable
private fun MyLocationMarker(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "myLoc")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Pulse ring
        Box(
            modifier = Modifier
                .size((32 * pulseScale).dp)
                .clip(CircleShape)
                .background(PremiumColors.ElectricCyan.copy(alpha = 0.2f))
        )

        // Inner marker
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(PremiumColors.ElectricCyan)
                .border(3.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Navigation,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = Color.White
            )
        }
    }
}

/**
 * Team member marker on map
 */
@Composable
private fun TeamMemberMapMarker(
    member: TeamMemberLocationUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val markerColor = when (member.status) {
        "ACTIVE" -> PremiumColors.OnlineGlow
        "MOVING" -> PremiumColors.ConnectingAmber
        "ALERT" -> PremiumColors.BusyRed
        "OFFLINE" -> PremiumColors.OfflineGray
        else -> PremiumColors.ElectricCyan
    }

    val infiniteTransition = rememberInfiniteTransition(label = "member")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = if (member.status == "ALERT") 1f else 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (member.status == "ALERT") 500 else 1500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Marker with pulse
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(markerColor.copy(alpha = pulseAlpha))
            )

            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(markerColor)
                    .border(2.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = member.name.take(1).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 10.sp
                )
            }
        }

        // Name label
        Text(
            text = member.name,
            style = MaterialTheme.typography.labelSmall,
            color = PremiumColors.TextPrimary,
            fontSize = 9.sp
        )
    }
}

/**
 * SOS alert marker
 */
@Composable
private fun SOSMarker(
    alert: SOSAlertUiModel,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "sos")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sosScale"
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Outer pulse
        Box(
            modifier = Modifier
                .size((40 * scale).dp)
                .clip(CircleShape)
                .background(PremiumColors.BusyRed.copy(alpha = 0.3f))
        )

        // Inner marker
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(PremiumColors.BusyRed)
                .border(3.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = Color.White
            )
        }
    }
}

/**
 * Geofence marker
 */
@Composable
private fun GeofenceMarker(
    geofence: GeofenceUiModel,
    modifier: Modifier = Modifier
) {
    val color = when (geofence.type) {
        "SAFE" -> PremiumColors.OnlineGlow
        "RESTRICTED" -> PremiumColors.BusyRed
        "ALERT" -> PremiumColors.ConnectingAmber
        else -> PremiumColors.HoloPurple
    }

    Box(
        modifier = modifier
            .size(geofence.radius.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.1f))
            .border(
                width = 2.dp,
                color = color.copy(alpha = 0.5f),
                shape = CircleShape
            )
    )
}

/**
 * Compass indicator
 */
@Composable
private fun CompassIndicator(
    heading: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(PremiumColors.SpaceGrayLight)
            .border(1.dp, PremiumColors.GlassBorder, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        // N indicator
        Text(
            text = "N",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = PremiumColors.BusyRed,
            modifier = Modifier.offset(y = (-14).dp)
        )

        // Compass needle
        Box(
            modifier = Modifier
                .size(24.dp)
                .rotate(heading)
        ) {
            Icon(
                imageVector = Icons.Default.Navigation,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = PremiumColors.ElectricCyan
            )
        }
    }
}

/**
 * Scale bar
 */
@Composable
private fun ScaleBar(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(60.dp)
                .height(4.dp)
                .background(PremiumColors.TextSecondary)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "100m",
            style = MaterialTheme.typography.labelSmall,
            color = PremiumColors.TextSecondary
        )
    }
}

/**
 * Map legend
 */
@Composable
private fun MapLegend(modifier: Modifier = Modifier) {
    GlassCard(
        modifier = modifier,
        cornerRadius = 12.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            LegendRow(color = PremiumColors.ElectricCyan, label = "You")
            LegendRow(color = PremiumColors.OnlineGlow, label = "Active")
            LegendRow(color = PremiumColors.ConnectingAmber, label = "Moving")
            LegendRow(color = PremiumColors.BusyRed, label = "SOS")
            LegendRow(color = PremiumColors.OfflineGray, label = "Offline")
        }
    }
}

@Composable
private fun LegendRow(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = PremiumColors.TextSecondary,
            fontSize = 10.sp
        )
    }
}

/**
 * Team list view
 */
@Composable
private fun TeamListView(
    members: List<TeamMemberLocationUiModel>,
    onMemberClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(members) { member ->
            TeamMemberCard(
                member = member,
                onClick = { onMemberClick(member.id) }
            )
        }
    }
}

@Composable
private fun TeamMemberCard(
    member: TeamMemberLocationUiModel,
    onClick: () -> Unit
) {
    val statusColor = when (member.status) {
        "ACTIVE" -> PremiumColors.OnlineGlow
        "MOVING" -> PremiumColors.ConnectingAmber
        "ALERT" -> PremiumColors.BusyRed
        else -> PremiumColors.OfflineGray
    }

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        cornerRadius = 16.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(PremiumColors.ElectricCyan, PremiumColors.HoloPurple)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = member.name.take(2).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = member.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = PremiumColors.TextPrimary
                    )

                    PremiumChip(
                        text = member.status,
                        color = statusColor
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = PremiumColors.TextSecondary
                    )
                    Text(
                        text = "${member.distance}m away • ${member.lastUpdate}",
                        style = MaterialTheme.typography.labelSmall,
                        color = PremiumColors.TextSecondary
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = PremiumColors.TextSecondary
            )
        }
    }
}

/**
 * Location sharing toggle card
 */
@Composable
private fun LocationSharingCard(
    isSharing: Boolean,
    onToggle: () -> Unit
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 16.dp,
        borderColor = if (isSharing) PremiumColors.LaserLime else PremiumColors.GlassBorder
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isSharing) Icons.Default.LocationOn else Icons.Default.LocationOff,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = if (isSharing) PremiumColors.LaserLime else PremiumColors.TextSecondary
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isSharing) "Sharing Location" else "Location Sharing Off",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = PremiumColors.TextPrimary
                )
                Text(
                    text = if (isSharing) "Team can see your position" else "Tap to enable",
                    style = MaterialTheme.typography.labelSmall,
                    color = PremiumColors.TextSecondary
                )
            }

            Switch(
                checked = isSharing,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = PremiumColors.LaserLime,
                    checkedTrackColor = PremiumColors.LaserLime.copy(alpha = 0.3f)
                )
            )
        }
    }
}

/**
 * Quick action buttons
 */
@Composable
private fun MapQuickActions(
    onCenterOnMe: () -> Unit,
    onAddGeofence: () -> Unit,
    onSOSAlert: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        GlassCard(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onCenterOnMe),
            cornerRadius = 12.dp
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = PremiumColors.ElectricCyan
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Center",
                    style = MaterialTheme.typography.labelMedium,
                    color = PremiumColors.TextPrimary
                )
            }
        }

        GlassCard(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onAddGeofence),
            cornerRadius = 12.dp
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AddLocation,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = PremiumColors.HoloPurple
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Geofence",
                    style = MaterialTheme.typography.labelMedium,
                    color = PremiumColors.TextPrimary
                )
            }
        }

        GlassCard(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onSOSAlert),
            cornerRadius = 12.dp,
            borderColor = PremiumColors.BusyRed.copy(alpha = 0.5f)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = PremiumColors.BusyRed
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "SOS",
                    style = MaterialTheme.typography.labelMedium,
                    color = PremiumColors.BusyRed
                )
            }
        }
    }
}

// Extension for rotation
private fun Modifier.rotate(degrees: Float): Modifier = this.then(
    Modifier.graphicsLayer { rotationZ = degrees }
)

// UI Models
data class LocationUiModel(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val heading: Float
)

data class TeamMemberLocationUiModel(
    val id: String,
    val name: String,
    val status: String,
    val latitude: Double,
    val longitude: Double,
    val distance: Int,
    val lastUpdate: String
)

data class GeofenceUiModel(
    val id: String,
    val name: String,
    val type: String,
    val x: Float,
    val y: Float,
    val radius: Float
)

data class SOSAlertUiModel(
    val id: String,
    val senderName: String,
    val x: Float,
    val y: Float,
    val timestamp: Long
)
