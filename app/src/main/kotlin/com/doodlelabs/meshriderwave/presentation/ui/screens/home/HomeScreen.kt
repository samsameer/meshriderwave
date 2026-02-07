/*
 * Mesh Rider Wave - Premium Home Screen 2026
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 *
 * Ultra-premium home screen with glassmorphism and modern animations
 * Following 2026 UI/UX trends from Behance, Figma, and Dribbble
 */

package com.doodlelabs.meshriderwave.presentation.ui.screens.home

import androidx.compose.animation.*
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.doodlelabs.meshriderwave.domain.model.Contact
import com.doodlelabs.meshriderwave.presentation.ui.components.*
import com.doodlelabs.meshriderwave.presentation.ui.theme.MeshRiderPremiumTheme
import com.doodlelabs.meshriderwave.presentation.ui.theme.PremiumColors
import com.doodlelabs.meshriderwave.presentation.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToGroups: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToChannels: () -> Unit = {},
    onNavigateToMap: () -> Unit = {},
    onNavigateToPtt: () -> Unit = {},
    onNavigateToContacts: () -> Unit = {},
    onNavigateToQRScan: () -> Unit = {},
    onStartCall: (String) -> Unit,
    onStartVideoCall: (String) -> Unit = {},
    onActivateSOS: () -> Unit = {},
    viewModel: MainViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedNavItem by remember { mutableStateOf(NavItem.HOME) }

    // Animated mesh background with content
    AnimatedMeshBackground(
        animationSpeed = 0.5f // Subtle, slow animation
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                PremiumTopBar(
                    username = uiState.username,
                    onSettingsClick = onNavigateToSettings
                )
            },
            bottomBar = {
                PremiumBottomNavBar(
                    selectedItem = selectedNavItem,
                    onItemSelected = { item ->
                        selectedNavItem = item
                        when (item) {
                            NavItem.GROUPS -> onNavigateToGroups()
                            NavItem.CHANNELS -> onNavigateToChannels()
                            NavItem.MAP -> onNavigateToMap()
                            NavItem.SETTINGS -> onNavigateToSettings()
                            else -> {}
                        }
                    }
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Premium Hero Status Card
                item {
                    PremiumStatusCard(
                        isServiceRunning = uiState.isServiceRunning,
                        localAddresses = uiState.localAddresses,
                        username = uiState.username,
                        peerCount = uiState.contacts.count { /* isOnline */ true }
                    )
                }

                // PRIMARY ACTION CARDS - Main tactical features
                item {
                    PrimaryActionCards(
                        onPTT = onNavigateToPtt,
                        onCallTeam = onNavigateToGroups,
                        onSOS = onActivateSOS,
                        onMap = onNavigateToMap,
                        isServiceRunning = uiState.isServiceRunning
                    )
                }

                // Quick Actions Grid
                item {
                    PremiumQuickActions(
                        onScanQR = onNavigateToQRScan,
                        onAddContact = onNavigateToContacts,
                        onViewNetwork = { /* Navigate to Network Status */ }
                    )
                }

                // Recent Contacts Section
                if (uiState.contacts.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = "Recent Contacts",
                            actionText = "See All",
                            onAction = onNavigateToContacts
                        )
                    }

                    items(uiState.contacts.take(5)) { contact ->
                        PremiumContactCard(
                            contact = contact,
                            isOnline = uiState.isContactOnline(contact),  // REAL status (FIXED Jan 2026)
                            onCall = { onStartCall(contact.deviceId) },
                            onVideoCall = { onStartVideoCall(contact.deviceId) }
                        )
                    }
                } else {
                    item {
                        PremiumEmptyState(
                            onAddContact = onNavigateToContacts
                        )
                    }
                }

                // Bottom spacing for nav bar
                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }
    }
}

/**
 * Premium Top App Bar with glassmorphism
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PremiumTopBar(
    username: String,
    onSettingsClick: () -> Unit
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = "Mesh Rider Wave",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = PremiumColors.TextPrimary
                )
                Text(
                    text = "Comms Without Compromise",
                    style = MaterialTheme.typography.bodySmall,
                    color = PremiumColors.TextSecondary,
                    letterSpacing = 1.sp
                )
            }
        },
        actions = {
            IconButton(onClick = onSettingsClick) {
                Icon(
                    Icons.Outlined.Settings,
                    contentDescription = "Settings",
                    tint = PremiumColors.TextSecondary
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent
        )
    )
}

/**
 * Premium Hero Status Card with glass effect
 */
@Composable
private fun PremiumStatusCard(
    isServiceRunning: Boolean,
    localAddresses: List<String>,
    username: String,
    peerCount: Int
) {
    val connectionStatus = if (isServiceRunning) ConnectionStatus.CONNECTED else ConnectionStatus.DISCONNECTED

    GradientGlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 24.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            // Status row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ConnectionStatusBadge(status = connectionStatus)
                PeerCountBadge(count = peerCount)
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Greeting
            Text(
                text = "Hello, $username",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = PremiumColors.TextPrimary
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Network info
            if (localAddresses.isNotEmpty()) {
                NetworkInfoBadge(
                    ipAddress = localAddresses.firstOrNull(),
                    interfaceType = "Mesh"
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Encryption badge
            EncryptionBadge(isEncrypted = true)
        }
    }
}

/**
 * Primary Action Cards - Main tactical features
 * These are the most important actions for a tactical communication app
 */
@Composable
private fun PrimaryActionCards(
    onPTT: () -> Unit,
    onCallTeam: () -> Unit,
    onSOS: () -> Unit,
    onMap: () -> Unit,
    isServiceRunning: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Row 1: PTT (Primary) + Call Team
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // PTT - Primary action, larger
            PrimaryActionCard(
                modifier = Modifier.weight(1.5f),
                icon = Icons.Filled.Mic,
                title = "PUSH TO TALK",
                subtitle = if (isServiceRunning) "Ready to transmit" else "Start service first",
                color = PremiumColors.ElectricCyan,
                isEnabled = isServiceRunning,
                isPrimary = true,
                onClick = onPTT
            )
            // Call Team
            PrimaryActionCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.Groups,
                title = "CALL TEAM",
                subtitle = "Group call",
                color = PremiumColors.LaserLime,
                isEnabled = true,
                isPrimary = false,
                onClick = onCallTeam
            )
        }
        // Row 2: SOS + Map
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // SOS - Emergency
            PrimaryActionCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.Warning,
                title = "SOS",
                subtitle = "Emergency alert",
                color = PremiumColors.CriticalRed,
                isEnabled = true,
                isPrimary = false,
                onClick = onSOS
            )
            // Map - Situational awareness
            PrimaryActionCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.Map,
                title = "MAP",
                subtitle = "Team tracking",
                color = PremiumColors.HoloPurple,
                isEnabled = true,
                isPrimary = false,
                onClick = onMap
            )
        }
    }
}

/**
 * Primary Action Card - High visibility button for main features
 */
@Composable
private fun PrimaryActionCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    subtitle: String,
    color: Color,
    isEnabled: Boolean,
    isPrimary: Boolean,
    onClick: () -> Unit
) {
    val cardHeight = if (isPrimary) 90.dp else 70.dp
    val iconSize = if (isPrimary) 28.dp else 24.dp
    val titleFontSize = if (isPrimary) 14.sp else 12.sp

    Box(
        modifier = modifier
            .height(cardHeight)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isEnabled) {
                    color.copy(alpha = 0.15f)
                } else {
                    PremiumColors.SpaceGrayLight
                }
            )
            .border(
                width = if (isPrimary) 2.dp else 1.dp,
                color = if (isEnabled) {
                    color.copy(alpha = 0.5f)
                } else {
                    PremiumColors.SpaceGrayLighter
                },
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(enabled = isEnabled, onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(if (isPrimary) 48.dp else 40.dp)
                    .clip(CircleShape)
                    .background(
                        if (isEnabled) color.copy(alpha = 0.2f)
                        else PremiumColors.SpaceGrayLighter
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    modifier = Modifier.size(iconSize),
                    tint = if (isEnabled) color else PremiumColors.TextSecondary
                )
            }
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isPrimary) FontWeight.Bold else FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                    color = if (isEnabled) PremiumColors.TextPrimary else PremiumColors.TextSecondary,
                    fontSize = titleFontSize,
                    letterSpacing = 1.sp
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isEnabled) color.copy(alpha = 0.8f) else PremiumColors.TextTertiary,
                    fontSize = 10.sp
                )
            }
        }
    }
}

/**
 * Premium Quick Actions Grid
 */
@Composable
private fun PremiumQuickActions(
    onScanQR: () -> Unit,
    onAddContact: () -> Unit,
    onViewNetwork: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PremiumQuickActionCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Filled.QrCodeScanner,
            title = "Scan QR",
            gradient = Brush.linearGradient(
                colors = listOf(PremiumColors.NeonMagenta, PremiumColors.HoloPurple)
            ),
            onClick = onScanQR
        )
        PremiumQuickActionCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Filled.PersonAdd,
            title = "Add Contact",
            gradient = Brush.linearGradient(
                colors = listOf(PremiumColors.LaserLime, PremiumColors.ElectricCyan)
            ),
            onClick = onAddContact
        )
        PremiumQuickActionCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Filled.Wifi,
            title = "Network",
            gradient = Brush.linearGradient(
                colors = listOf(PremiumColors.ElectricCyan, PremiumColors.HoloPurple)
            ),
            onClick = onViewNetwork
        )
    }
}

/**
 * Premium Quick Action Card with gradient and glow
 */
@Composable
private fun PremiumQuickActionCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    gradient: Brush,
    onClick: () -> Unit
) {
    GlassCard(
        modifier = modifier
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        cornerRadius = 16.dp
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(brush = gradient),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        modifier = Modifier.size(24.dp),
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = PremiumColors.TextPrimary
                )
            }
        }
    }
}

/**
 * Section Header with optional action
 */
@Composable
private fun SectionHeader(
    title: String,
    actionText: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = PremiumColors.TextPrimary
        )

        if (actionText != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(
                    text = actionText,
                    style = MaterialTheme.typography.labelMedium,
                    color = PremiumColors.ElectricCyan
                )
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

/**
 * Premium Contact Card with avatar and call buttons
 * FIXED Jan 2026: Now uses REAL online status from peer discovery
 */
@Composable
private fun PremiumContactCard(
    contact: Contact,
    isOnline: Boolean,  // REAL status from peer discovery
    onCall: () -> Unit,
    onVideoCall: () -> Unit
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 16.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Premium Avatar with REAL status
            ContactAvatar(
                name = contact.name,
                status = if (isOnline) ContactStatus.ONLINE else ContactStatus.OFFLINE,
                size = 52.dp,
                fontSize = 18.sp
            )

            Spacer(modifier = Modifier.width(16.dp))

            // Contact info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = contact.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = PremiumColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = contact.shortId,
                    style = MaterialTheme.typography.bodySmall,
                    color = PremiumColors.TextTertiary
                )
            }

            // Call buttons
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Voice call button
                NeumorphicToggleButton(
                    onClick = onCall,
                    icon = Icons.Filled.Call,
                    size = 44.dp,
                    isActive = false,
                    activeColor = PremiumColors.LaserLime,
                    inactiveColor = PremiumColors.SpaceGrayLight
                )

                // Video call button
                NeumorphicToggleButton(
                    onClick = onVideoCall,
                    icon = Icons.Filled.Videocam,
                    size = 44.dp,
                    isActive = false,
                    activeColor = PremiumColors.ElectricCyan,
                    inactiveColor = PremiumColors.SpaceGrayLight
                )
            }
        }
    }
}

/**
 * Premium Empty State with illustration
 */
@Composable
private fun PremiumEmptyState(
    onAddContact: () -> Unit
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
            // Animated icon
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(PremiumColors.SpaceGrayLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.PersonAdd,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = PremiumColors.TextSecondary
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "No contacts yet",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = PremiumColors.TextPrimary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Add contacts by scanning their QR code\nor sharing yours",
                style = MaterialTheme.typography.bodyMedium,
                color = PremiumColors.TextSecondary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Premium CTA button
            PremiumButton(
                onClick = onAddContact,
                text = "Add Contact",
                icon = Icons.Filled.Add,
                gradient = PremiumColors.PrimaryGradient,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
