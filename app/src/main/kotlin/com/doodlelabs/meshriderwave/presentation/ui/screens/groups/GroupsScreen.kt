/*
 * Mesh Rider Wave - Groups Screen
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 *
 * View and manage encrypted groups with MLS protocol
 * - Create/join groups
 * - Group video/audio calls
 * - Encrypted messaging
 * - Member management
 */

package com.doodlelabs.meshriderwave.presentation.ui.screens.groups

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.doodlelabs.meshriderwave.presentation.ui.components.*
import com.doodlelabs.meshriderwave.presentation.ui.theme.PremiumColors
import com.doodlelabs.meshriderwave.presentation.viewmodel.GroupsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupsScreen(
    onNavigateBack: () -> Unit,
    onGroupClick: (String) -> Unit,
    onStartGroupCall: (String) -> Unit,
    onNavigateToQRScan: () -> Unit = {},
    viewModel: GroupsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var showJoinDialog by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Show error in Snackbar
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(
                message = error,
                actionLabel = "Dismiss",
                duration = SnackbarDuration.Short
            )
            viewModel.clearError()
        }
    }

    DeepSpaceBackground {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "Groups",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = PremiumColors.TextPrimary
                            )
                            Text(
                                text = "${uiState.groups.size} groups • MLS encrypted",
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
                        // Scan QR to join group
                        IconButton(onClick = onNavigateToQRScan) {
                            Icon(
                                Icons.Default.QrCodeScanner,
                                contentDescription = "Scan QR to Join",
                                tint = PremiumColors.ElectricCyan
                            )
                        }
                        // Manual code entry
                        IconButton(onClick = { showJoinDialog = true }) {
                            Icon(
                                Icons.Default.Input,
                                contentDescription = "Enter Code",
                                tint = PremiumColors.TextSecondary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showCreateDialog = true
                    },
                    containerColor = PremiumColors.ElectricCyan,
                    contentColor = PremiumColors.DeepSpace
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Create Group")
                }
            }
        ) { padding ->
            if (uiState.groups.isEmpty()) {
                EmptyGroupsState(
                    onCreateClick = { showCreateDialog = true },
                    onJoinClick = onNavigateToQRScan,  // Use QR scanner for joining
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Active calls section
                    val groupsWithCalls = uiState.groups.filter { it.hasActiveCall }
                    if (groupsWithCalls.isNotEmpty()) {
                        item {
                            Text(
                                text = "Active Calls",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = PremiumColors.LaserLime,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }

                        items(groupsWithCalls) { group ->
                            GroupCardWithCall(
                                group = group,
                                onJoinCall = { onStartGroupCall(group.id) },
                                onClick = { onGroupClick(group.id) }
                            )
                        }

                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }

                    // All groups section
                    item {
                        Text(
                            text = "All Groups",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = PremiumColors.TextSecondary,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }

                    items(uiState.groups.filterNot { it.hasActiveCall }) { group ->
                        GroupCard(
                            group = group,
                            onClick = { onGroupClick(group.id) },
                            onStartCall = { onStartGroupCall(group.id) }
                        )
                    }

                    // Bottom spacing for FAB
                    item {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }
    }

    // Create Group Dialog (Premium)
    if (showCreateDialog) {
        PremiumCreateGroupDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name, description ->
                viewModel.createGroup(name, description)
                showCreateDialog = false
            }
        )
    }

    // Join Group Dialog (Premium)
    if (showJoinDialog) {
        PremiumInputDialog(
            title = "Join Group",
            placeholder = "Enter invite code",
            confirmText = "Join",
            cancelText = "Cancel",
            icon = Icons.Outlined.QrCodeScanner,
            helperText = "You can also scan a QR code to join",
            onConfirm = { inviteCode ->
                viewModel.joinGroup(inviteCode)
                showJoinDialog = false
            },
            onDismiss = { showJoinDialog = false }
        )
    }
}

/**
 * Empty state for no groups
 */
@Composable
private fun EmptyGroupsState(
    onCreateClick: () -> Unit,
    onJoinClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            cornerRadius = 24.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(PremiumColors.ElectricCyan, PremiumColors.HoloPurple)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Groups,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = PremiumColors.TextPrimary
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "No Groups Yet",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = PremiumColors.TextPrimary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Create a new group or join an existing one\nto start secure communication",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PremiumColors.TextSecondary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onJoinClick,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = PremiumColors.ElectricCyan
                        ),
                        border = ButtonDefaults.outlinedButtonBorder(
                            enabled = true
                        ).copy(
                            brush = Brush.linearGradient(
                                colors = listOf(PremiumColors.ElectricCyan, PremiumColors.HoloPurple)
                            )
                        )
                    ) {
                        Icon(
                            Icons.Default.QrCodeScanner,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Join")
                    }

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
                        Text("Create")
                    }
                }
            }
        }
    }
}

/**
 * Group card with active call indicator
 */
@Composable
private fun GroupCardWithCall(
    group: GroupUiModel,
    onJoinCall: () -> Unit,
    onClick: () -> Unit
) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        cornerRadius = 16.dp,
        borderColor = PremiumColors.LaserLime
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Group Avatar with call indicator
            Box {
                GroupAvatar(name = group.name)

                // Call indicator
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(PremiumColors.LaserLime),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = PremiumColors.DeepSpace
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = group.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = PremiumColors.TextPrimary
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PulsingDot(color = PremiumColors.LaserLime)
                    Text(
                        text = "Call in progress • ${group.callParticipants} participants",
                        style = MaterialTheme.typography.labelSmall,
                        color = PremiumColors.LaserLime
                    )
                }
            }

            Button(
                onClick = onJoinCall,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PremiumColors.LaserLime
                ),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text("Join", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/**
 * Standard group card
 */
@Composable
private fun GroupCard(
    group: GroupUiModel,
    onClick: () -> Unit,
    onStartCall: () -> Unit
) {
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
            GroupAvatar(name = group.name)

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = group.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = PremiumColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (group.unreadCount > 0) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(PremiumColors.NeonMagenta),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (group.unreadCount > 9) "9+" else group.unreadCount.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = PremiumColors.TextPrimary,
                                fontSize = 10.sp
                            )
                        }
                    }
                }

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
                        text = "${group.memberCount} members",
                        style = MaterialTheme.typography.labelSmall,
                        color = PremiumColors.TextSecondary
                    )

                    Text(
                        text = "•",
                        color = PremiumColors.TextSecondary
                    )

                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = PremiumColors.OnlineGlow
                    )
                    Text(
                        text = "E2E",
                        style = MaterialTheme.typography.labelSmall,
                        color = PremiumColors.OnlineGlow
                    )
                }
            }

            // Action buttons
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(
                    onClick = onStartCall,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(PremiumColors.LaserLime.copy(alpha = 0.2f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = "Start Call",
                        tint = PremiumColors.LaserLime,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = PremiumColors.TextSecondary
                )
            }
        }
    }
}

/**
 * Group avatar with gradient
 */
@Composable
private fun GroupAvatar(name: String) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(PremiumColors.ElectricCyan, PremiumColors.HoloPurple)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name.take(2).uppercase(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = PremiumColors.TextPrimary
        )
    }
}

/**
 * Pulsing dot indicator
 */
@Composable
private fun PulsingDot(color: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = alpha))
    )
}

// UI Model
data class GroupUiModel(
    val id: String,
    val name: String,
    val memberCount: Int,
    val unreadCount: Int,
    val hasActiveCall: Boolean,
    val callParticipants: Int = 0
)
