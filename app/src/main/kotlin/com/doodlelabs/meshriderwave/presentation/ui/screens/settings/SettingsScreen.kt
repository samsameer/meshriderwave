/*
 * Mesh Rider Wave - Premium Settings Screen
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 *
 * World-class settings interface matching DoodleLabs premium branding
 * - Glassmorphism design language
 * - Comprehensive tactical settings
 * - MLS encryption controls
 * - PTT configuration
 * - Blue Force Tracking options
 */

package com.doodlelabs.meshriderwave.presentation.ui.screens.settings

import com.doodlelabs.meshriderwave.BuildConfig
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.doodlelabs.meshriderwave.presentation.ui.components.*
import com.doodlelabs.meshriderwave.presentation.ui.theme.PremiumColors
import com.doodlelabs.meshriderwave.presentation.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: MainViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showNameDialog by remember { mutableStateOf(false) }
    var showPublicKeyDialog by remember { mutableStateOf(false) }
    var showSecurityInfoDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showDeveloperDialog by remember { mutableStateOf(false) }
    var showRadioIpDialog by remember { mutableStateOf(false) }

    // Success message function
    fun showSuccess(message: String) {
        scope.launch {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Short,
                withDismissAction = true
            )
        }
    }

    DeepSpaceBackground {
        Scaffold(
            snackbarHost = {
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.padding(16.dp)
                ) { data ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = PremiumColors.ElectricCyan.copy(alpha = 0.9f)
                        ),
                        modifier = Modifier.padding(4.dp)
                    ) {
                        Snackbar(
                            snackbarData = data,
                            containerColor = Color.Transparent,
                            contentColor = PremiumColors.DeepSpace,
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            },
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "Settings",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = PremiumColors.TextPrimary
                            )
                            Text(
                                text = "Configure your Mesh Rider experience",
                                style = MaterialTheme.typography.bodySmall,
                                color = PremiumColors.TextSecondary
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = PremiumColors.TextPrimary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Profile Card
                item {
                    ProfileCard(
                        username = uiState.username,
                        deviceId = uiState.deviceId.take(8).uppercase(),
                        isOnline = uiState.isServiceRunning,
                        onEditName = { showNameDialog = true },
                        onViewPublicKey = { showPublicKeyDialog = true }
                    )
                }

                // Security Section
                item {
                    SettingsSection(title = "Security & Privacy") {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            PremiumSettingsItem(
                                icon = Icons.Outlined.Security,
                                iconColor = PremiumColors.AuroraGreen,
                                title = "Encryption Status",
                                subtitle = "MLS Protocol Active",
                                badge = "Secure",
                                badgeColor = PremiumColors.AuroraGreen,
                                onClick = { showSecurityInfoDialog = true }
                            )
                            PremiumSettingsItem(
                                icon = Icons.Outlined.Key,
                                iconColor = PremiumColors.ElectricCyan,
                                title = "Key Exchange",
                                subtitle = "Ed25519 + X25519",
                                onClick = { }
                            )
                            PremiumSettingsItem(
                                icon = Icons.Outlined.Shield,
                                iconColor = PremiumColors.HoloPurple,
                                title = "Forward Secrecy",
                                subtitle = "Enabled - Keys rotate per session",
                                onClick = { }
                            )
                        }
                    }
                }

                // Push-to-Talk Section
                item {
                    SettingsSection(title = "Push-to-Talk") {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            PremiumSettingsToggle(
                                icon = Icons.Outlined.Vibration,
                                iconColor = PremiumColors.SolarGold,
                                title = "PTT Vibration",
                                subtitle = "Vibrate when transmitting",
                                checked = uiState.pttVibration,
                                onCheckedChange = {
                                    viewModel.setPttVibration(it)
                                    showSuccess("PTT Vibration ${if (it) "enabled" else "disabled"}")
                                }
                            )
                            PremiumSettingsItem(
                                icon = Icons.Outlined.Mic,
                                iconColor = PremiumColors.ElectricCyan,
                                title = "Audio Codec",
                                subtitle = "Opus - Low Latency",
                                onClick = { }
                            )
                            PremiumSettingsItem(
                                icon = Icons.Outlined.Speed,
                                iconColor = PremiumColors.LaserLime,
                                title = "Transmission Delay",
                                subtitle = "< 50ms",
                                badge = "Fast",
                                badgeColor = PremiumColors.LaserLime,
                                onClick = { }
                            )
                        }
                    }
                }

                // Calls Section (Core-Telecom integrated)
                item {
                    SettingsSection(title = "Calls") {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            PremiumSettingsToggle(
                                icon = Icons.Outlined.Vibration,
                                iconColor = PremiumColors.ElectricCyan,
                                title = "Vibrate on Incoming Call",
                                subtitle = "Haptic feedback for incoming calls",
                                checked = uiState.vibrateOnCall,
                                onCheckedChange = {
                                    viewModel.setVibrateOnCall(it)
                                    showSuccess("Call vibration ${if (it) "enabled" else "disabled"}")
                                }
                            )
                            PremiumSettingsToggle(
                                icon = Icons.Outlined.PhoneForwarded,
                                iconColor = PremiumColors.AuroraGreen,
                                title = "Auto-Accept Calls",
                                subtitle = "Answer incoming calls automatically",
                                checked = uiState.autoAcceptCalls,
                                onCheckedChange = {
                                    viewModel.setAutoAcceptCalls(it)
                                    showSuccess("Auto-accept ${if (it) "enabled" else "disabled"}")
                                }
                            )
                            PremiumSettingsItem(
                                icon = Icons.Outlined.PhoneInTalk,
                                iconColor = PremiumColors.SolarGold,
                                title = "Telecom Integration",
                                subtitle = "Core-Telecom (CallStyle notifications)",
                                badge = "Active",
                                badgeColor = PremiumColors.AuroraGreen,
                                onClick = { }
                            )
                            PremiumSettingsItem(
                                icon = Icons.Outlined.SettingsVoice,
                                iconColor = PremiumColors.HoloPurple,
                                title = "Audio Routing",
                                subtitle = "Managed by Android Telecom framework",
                                onClick = { }
                            )
                            PremiumSettingsItem(
                                icon = Icons.Outlined.Bluetooth,
                                iconColor = PremiumColors.ElectricCyan,
                                title = "Bluetooth Audio",
                                subtitle = "Auto-route to connected headset",
                                onClick = { }
                            )
                        }
                    }
                }

                // Location & Tracking Section
                item {
                    SettingsSection(title = "Blue Force Tracking") {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            PremiumSettingsToggle(
                                icon = Icons.Outlined.LocationOn,
                                iconColor = PremiumColors.ElectricCyan,
                                title = "Location Sharing",
                                subtitle = "Share position with team",
                                checked = uiState.locationSharing,
                                onCheckedChange = {
                                    viewModel.setLocationSharing(it)
                                    showSuccess("Location sharing ${if (it) "enabled" else "disabled"}")
                                }
                            )
                            PremiumSettingsItem(
                                icon = Icons.Outlined.Update,
                                iconColor = PremiumColors.SolarGold,
                                title = "Update Interval",
                                subtitle = "5 seconds",
                                onClick = { }
                            )
                            PremiumSettingsItem(
                                icon = Icons.Outlined.Map,
                                iconColor = PremiumColors.HoloPurple,
                                title = "Map Provider",
                                subtitle = "OpenStreetMap (Offline)",
                                onClick = { }
                            )
                        }
                    }
                }

                // Emergency Section
                item {
                    SettingsSection(title = "Emergency") {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            PremiumSettingsToggle(
                                icon = Icons.Outlined.Warning,
                                iconColor = PremiumColors.NeonMagenta,
                                title = "SOS System",
                                subtitle = "Enable emergency alerts",
                                checked = uiState.sosEnabled,
                                onCheckedChange = {
                                    viewModel.setSosEnabled(it)
                                    showSuccess("SOS system ${if (it) "enabled" else "disabled"}")
                                }
                            )
                            PremiumSettingsItem(
                                icon = Icons.Outlined.NotificationsActive,
                                iconColor = PremiumColors.NeonMagenta,
                                title = "SOS Sound",
                                subtitle = "High Priority Alert",
                                onClick = { }
                            )
                            PremiumSettingsItem(
                                icon = Icons.Outlined.Fence,
                                iconColor = PremiumColors.SolarGold,
                                title = "Geofence Alerts",
                                subtitle = "3 active zones",
                                onClick = { }
                            )
                        }
                    }
                }

                // Mesh Network Section
                item {
                    SettingsSection(title = "Mesh Network") {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            PremiumSettingsToggle(
                                icon = Icons.Outlined.Autorenew,
                                iconColor = PremiumColors.ElectricCyan,
                                title = "Auto Reconnect",
                                subtitle = "Automatically reconnect to mesh",
                                checked = uiState.autoReconnect,
                                onCheckedChange = {
                                    viewModel.setAutoReconnect(it)
                                    showSuccess("Auto-reconnect ${if (it) "enabled" else "disabled"}")
                                }
                            )
                            PremiumSettingsItem(
                                icon = Icons.Outlined.Wifi,
                                iconColor = PremiumColors.AuroraGreen,
                                title = "Local Address",
                                subtitle = uiState.localAddresses.firstOrNull() ?: "Not connected",
                                onClick = { }
                            )
                            PremiumSettingsItem(
                                icon = Icons.Outlined.Timer,
                                iconColor = PremiumColors.SolarGold,
                                title = "Connection Timeout",
                                subtitle = "5 seconds",
                                onClick = { }
                            )
                            PremiumSettingsItem(
                                icon = Icons.Outlined.Hub,
                                iconColor = PremiumColors.HoloPurple,
                                title = "Discovery Mode",
                                subtitle = "mDNS + Broadcast",
                                onClick = { }
                            )
                        }
                    }
                }

                // DoodleLabs Radio Section (Jan 2026)
                item {
                    SettingsSection(title = "DoodleLabs Radio") {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            // Radio IP
                            PremiumSettingsItem(
                                icon = Icons.Outlined.Router,
                                iconColor = PremiumColors.ElectricCyan,
                                title = "Radio IP Address",
                                subtitle = uiState.radioIp.ifEmpty { "Not configured" },
                                onClick = { showRadioIpDialog = true }
                            )
                            // Connection Status
                            PremiumSettingsItem(
                                icon = if (uiState.radioConnected) Icons.Outlined.CheckCircle else Icons.Outlined.Cancel,
                                iconColor = if (uiState.radioConnected) PremiumColors.AuroraGreen
                                           else if (uiState.radioConnecting) PremiumColors.SolarGold
                                           else PremiumColors.OfflineGray,
                                title = "Connection Status",
                                subtitle = when {
                                    uiState.radioConnecting -> "Connecting..."
                                    uiState.radioConnected -> "Connected to ${uiState.radioHostname.ifEmpty { uiState.radioIp }}"
                                    uiState.radioError != null -> "Error: ${uiState.radioError}"
                                    else -> "Disconnected"
                                },
                                badge = if (uiState.radioConnected) "Online" else null,
                                badgeColor = PremiumColors.AuroraGreen,
                                onClick = { }
                            )
                            // Radio Model (when connected)
                            if (uiState.radioConnected && uiState.radioModel.isNotEmpty()) {
                                PremiumSettingsItem(
                                    icon = Icons.Outlined.Memory,
                                    iconColor = PremiumColors.HoloPurple,
                                    title = "Radio Model",
                                    subtitle = uiState.radioModel,
                                    onClick = { }
                                )
                            }
                            // Signal Quality (when connected)
                            if (uiState.radioConnected && uiState.radioSignal != 0) {
                                val snr = uiState.radioSignal - uiState.radioNoise
                                PremiumSettingsItem(
                                    icon = Icons.Outlined.SignalCellularAlt,
                                    iconColor = when {
                                        snr >= 25 -> PremiumColors.AuroraGreen
                                        snr >= 15 -> PremiumColors.SolarGold
                                        else -> PremiumColors.NeonMagenta
                                    },
                                    title = "Signal Quality",
                                    subtitle = "Signal: ${uiState.radioSignal} dBm | SNR: ${snr} dB",
                                    badge = when {
                                        snr >= 25 -> "Excellent"
                                        snr >= 15 -> "Good"
                                        snr >= 10 -> "Fair"
                                        else -> "Poor"
                                    },
                                    badgeColor = when {
                                        snr >= 25 -> PremiumColors.AuroraGreen
                                        snr >= 15 -> PremiumColors.SolarGold
                                        else -> PremiumColors.NeonMagenta
                                    },
                                    onClick = { }
                                )
                            }
                            // Connect/Disconnect Button
                            RadioConnectionButton(
                                isConnected = uiState.radioConnected,
                                isConnecting = uiState.radioConnecting,
                                onConnect = { viewModel.connectToRadio() },
                                onDisconnect = { viewModel.disconnectFromRadio() }
                            )
                        }
                    }
                }

                // Audio & Video Section
                item {
                    SettingsSection(title = "Audio & Video") {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            PremiumSettingsToggle(
                                icon = Icons.Outlined.GraphicEq,
                                iconColor = PremiumColors.ElectricCyan,
                                title = "Hardware Echo Cancellation",
                                subtitle = "Use device AEC/NS for clear audio",
                                checked = uiState.hardwareAEC,
                                onCheckedChange = {
                                    viewModel.setHardwareAEC(it)
                                    showSuccess("Audio processing ${if (it) "enabled" else "disabled"}")
                                }
                            )
                            PremiumSettingsToggle(
                                icon = Icons.Outlined.Videocam,
                                iconColor = PremiumColors.SolarGold,
                                title = "Hardware Video Encoding",
                                subtitle = "H.264 High Profile acceleration",
                                checked = uiState.videoHwAccel,
                                onCheckedChange = {
                                    viewModel.setVideoHwAccel(it)
                                    showSuccess("Video acceleration ${if (it) "enabled" else "disabled"}")
                                }
                            )
                            PremiumSettingsItem(
                                icon = Icons.Outlined.HighQuality,
                                iconColor = PremiumColors.HoloPurple,
                                title = "Video Quality",
                                subtitle = "Adaptive: 180p-1080p (auto based on network)",
                                badge = "Auto",
                                badgeColor = PremiumColors.ElectricCyan,
                                onClick = { }
                            )
                            PremiumSettingsItem(
                                icon = Icons.Outlined.CameraFront,
                                iconColor = PremiumColors.LaserLime,
                                title = "Default Camera",
                                subtitle = "Front camera",
                                onClick = { }
                            )
                            PremiumSettingsItem(
                                icon = Icons.Outlined.Speed,
                                iconColor = PremiumColors.ElectricCyan,
                                title = "WebRTC Engine",
                                subtitle = "libwebrtc 119.0.0 (Unified Plan)",
                                onClick = { }
                            )
                        }
                    }
                }

                // Notifications Section
                item {
                    SettingsSection(title = "Notifications") {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            PremiumSettingsToggle(
                                icon = Icons.Outlined.Notifications,
                                iconColor = PremiumColors.ElectricCyan,
                                title = "Push Notifications",
                                subtitle = "Calls, messages, SOS alerts",
                                checked = uiState.notificationsEnabled,
                                onCheckedChange = {
                                    viewModel.setNotificationsEnabled(it)
                                    showSuccess("Notifications ${if (it) "enabled" else "disabled"}")
                                }
                            )
                            PremiumSettingsItem(
                                icon = Icons.Outlined.RingVolume,
                                iconColor = PremiumColors.AuroraGreen,
                                title = "Incoming Calls",
                                subtitle = "High priority with ringtone + vibration",
                                badge = "CallStyle",
                                badgeColor = PremiumColors.AuroraGreen,
                                onClick = {
                                    // Open system notification channel settings
                                    val intent = Intent(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                                        putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                                        putExtra(android.provider.Settings.EXTRA_CHANNEL_ID, "mesh_rider_calls_incoming")
                                    }
                                    context.startActivity(intent)
                                }
                            )
                            PremiumSettingsItem(
                                icon = Icons.Outlined.PhoneInTalk,
                                iconColor = PremiumColors.SolarGold,
                                title = "Ongoing Calls",
                                subtitle = "Silent persistent notification",
                                onClick = {
                                    val intent = Intent(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                                        putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                                        putExtra(android.provider.Settings.EXTRA_CHANNEL_ID, "mesh_rider_calls_ongoing")
                                    }
                                    context.startActivity(intent)
                                }
                            )
                            PremiumSettingsItem(
                                icon = Icons.Outlined.MusicNote,
                                iconColor = PremiumColors.HoloPurple,
                                title = "Ringtone",
                                subtitle = "System default ringtone",
                                onClick = { }
                            )
                        }
                    }
                }

                // Appearance Section
                item {
                    SettingsSection(title = "Appearance") {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            PremiumSettingsToggle(
                                icon = Icons.Outlined.DarkMode,
                                iconColor = PremiumColors.HoloPurple,
                                title = "Dark Mode",
                                subtitle = "Always dark for tactical use",
                                checked = uiState.nightMode,
                                onCheckedChange = {
                                    viewModel.setNightMode(it)
                                    showSuccess("Theme updated")
                                }
                            )
                            PremiumSettingsItem(
                                icon = Icons.Outlined.Language,
                                iconColor = PremiumColors.ElectricCyan,
                                title = "Language",
                                subtitle = "English, Deutsch",
                                onClick = { /* Opens system language settings */
                                    val intent = android.content.Intent(android.provider.Settings.ACTION_LOCALE_SETTINGS)
                                    context.startActivity(intent)
                                }
                            )
                        }
                    }
                }

                // Data & Storage Section
                item {
                    SettingsSection(title = "Data & Storage") {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            PremiumSettingsItem(
                                icon = Icons.Outlined.Storage,
                                iconColor = PremiumColors.ElectricCyan,
                                title = "Offline Messages",
                                subtitle = "12 pending • 2.3 MB",
                                onClick = { }
                            )
                            PremiumSettingsItem(
                                icon = Icons.Outlined.Map,
                                iconColor = PremiumColors.SolarGold,
                                title = "Offline Maps",
                                subtitle = "Download area maps",
                                onClick = { }
                            )
                            PremiumSettingsItem(
                                icon = Icons.Outlined.DeleteOutline,
                                iconColor = PremiumColors.NeonMagenta,
                                title = "Clear Cache",
                                subtitle = "Free up storage space",
                                onClick = { }
                            )
                        }
                    }
                }

                // About Section
                item {
                    SettingsSection(title = "About") {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            PremiumSettingsItem(
                                icon = Icons.Outlined.Info,
                                iconColor = PremiumColors.ElectricCyan,
                                title = "Mesh Rider Wave",
                                subtitle = "Version ${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
                                onClick = { showAboutDialog = true }
                            )
                            PremiumSettingsItem(
                                icon = Icons.Outlined.Business,
                                iconColor = PremiumColors.SolarGold,
                                title = "Doodle Labs",
                                subtitle = "Connectivity that counts",
                                onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.doodlelabs.com"))
                                    context.startActivity(intent)
                                }
                            )
                            PremiumSettingsItem(
                                icon = Icons.Outlined.Code,
                                iconColor = PremiumColors.HoloPurple,
                                title = "Developer",
                                subtitle = "Jabbir Basha P",
                                onClick = { showDeveloperDialog = true }
                            )
                            PremiumSettingsItem(
                                icon = Icons.Outlined.Description,
                                iconColor = PremiumColors.TextSecondary,
                                title = "Open Source Licenses",
                                subtitle = "View third-party licenses",
                                onClick = { }
                            )
                        }
                    }
                }

                // Bottom spacing
                item {
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }

    // Dialogs
    if (showNameDialog) {
        PremiumInputDialog(
            title = "Display Name",
            placeholder = "Enter your name",
            initialValue = uiState.username,
            confirmText = "Save",
            icon = Icons.Outlined.Person,
            helperText = "This name is visible to other team members",
            onConfirm = { name ->
                viewModel.setUsername(name)
                showNameDialog = false
            },
            onDismiss = { showNameDialog = false }
        )
    }

    if (showPublicKeyDialog) {
        PublicKeyDialog(
            publicKey = uiState.publicKeyBase64,
            deviceId = uiState.deviceId.take(8).uppercase(),
            onDismiss = { showPublicKeyDialog = false }
        )
    }

    if (showSecurityInfoDialog) {
        SecurityInfoDialog(
            onDismiss = { showSecurityInfoDialog = false }
        )
    }

    if (showAboutDialog) {
        AboutAppDialog(
            onDismiss = { showAboutDialog = false }
        )
    }

    if (showDeveloperDialog) {
        DeveloperDialog(
            onDismiss = { showDeveloperDialog = false },
            onOpenLinkedIn = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.linkedin.com/in/jabbir-basha/"))
                context.startActivity(intent)
            }
        )
    }

    // Radio IP Dialog
    if (showRadioIpDialog) {
        PremiumInputDialog(
            title = "Radio IP Address",
            placeholder = "e.g. 10.223.232.1",
            initialValue = uiState.radioIp,
            confirmText = "Save",
            icon = Icons.Outlined.Router,
            helperText = "Enter the IP address of your DoodleLabs MeshRider radio",
            onConfirm = { ip ->
                viewModel.setRadioIp(ip)
                showRadioIpDialog = false
            },
            onDismiss = { showRadioIpDialog = false }
        )
    }
}

// ============================================================================
// COMPONENTS
// ============================================================================

@Composable
private fun ProfileCard(
    username: String,
    deviceId: String,
    isOnline: Boolean,
    onEditName: () -> Unit,
    onViewPublicKey: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        PremiumColors.SpaceGrayLight,
                        PremiumColors.SpaceGray
                    )
                )
            )
            .border(
                width = 1.dp,
                color = PremiumColors.GlassBorder,
                shape = RoundedCornerShape(24.dp)
            )
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Avatar with status
            Box(contentAlignment = Alignment.BottomEnd) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    PremiumColors.ElectricCyan,
                                    PremiumColors.HoloPurple
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = username.firstOrNull()?.uppercase() ?: "U",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = PremiumColors.TextPrimary
                    )
                }
                // Status indicator
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(PremiumColors.DeepSpace)
                        .padding(3.dp)
                        .clip(CircleShape)
                        .background(if (isOnline) PremiumColors.AuroraGreen else PremiumColors.OfflineGray)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Username
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = username,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = PremiumColors.TextPrimary
                )
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onEditName()
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Outlined.Edit,
                        contentDescription = "Edit",
                        modifier = Modifier.size(16.dp),
                        tint = PremiumColors.ElectricCyan
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Device ID
            Text(
                text = "Device ID: $deviceId",
                style = MaterialTheme.typography.bodySmall,
                color = PremiumColors.TextSecondary
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Status badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isOnline) PremiumColors.AuroraGreen.copy(alpha = 0.15f)
                        else PremiumColors.OfflineGray.copy(alpha = 0.15f)
                    )
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (isOnline) "Online • Mesh Connected" else "Offline",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isOnline) PremiumColors.AuroraGreen else PremiumColors.OfflineGray
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // View Public Key button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(PremiumColors.ElectricCyan.copy(alpha = 0.1f))
                    .border(
                        width = 1.dp,
                        color = PremiumColors.ElectricCyan.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onViewPublicKey()
                    }
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Outlined.Key,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = PremiumColors.ElectricCyan
                    )
                    Text(
                        text = "View Public Key",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = PremiumColors.ElectricCyan
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = PremiumColors.TextSecondary,
            modifier = Modifier.padding(start = 4.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(PremiumColors.SpaceGray)
                .border(
                    width = 1.dp,
                    color = PremiumColors.GlassBorder.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(16.dp)
                )
        ) {
            content()
        }
    }
}

@Composable
private fun PremiumSettingsItem(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    subtitle: String,
    badge: String? = null,
    badgeColor: Color = PremiumColors.ElectricCyan,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon container
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = iconColor
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Text content
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = PremiumColors.TextPrimary
                )
                badge?.let {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(badgeColor.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = badgeColor,
                            fontSize = 10.sp
                        )
                    }
                }
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = PremiumColors.TextSecondary
            )
        }

        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = PremiumColors.TextTertiary
        )
    }
}

@Composable
private fun PremiumSettingsToggle(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val haptic = LocalHapticFeedback.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onCheckedChange(!checked)
            }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon container
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = iconColor
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Text content
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = PremiumColors.TextPrimary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = PremiumColors.TextSecondary
            )
        }

        // Premium Switch
        PremiumSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun PremiumSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val animatedOffset by animateDpAsState(
        targetValue = if (checked) 20.dp else 0.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "offset"
    )

    Box(
        modifier = Modifier
            .width(48.dp)
            .height(28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (checked) PremiumColors.ElectricCyan
                else PremiumColors.SpaceGrayLighter
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onCheckedChange(!checked) }
            .padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .offset(x = animatedOffset)
                .size(20.dp)
                .clip(CircleShape)
                .background(PremiumColors.TextPrimary)
        )
    }
}

/**
 * Radio Connect/Disconnect button (Jan 2026)
 */
@Composable
private fun RadioConnectionButton(
    isConnected: Boolean,
    isConnecting: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(
                    when {
                        isConnecting -> PremiumColors.SolarGold.copy(alpha = 0.15f)
                        isConnected -> PremiumColors.NeonMagenta.copy(alpha = 0.15f)
                        else -> PremiumColors.AuroraGreen.copy(alpha = 0.15f)
                    }
                )
                .border(
                    width = 1.dp,
                    color = when {
                        isConnecting -> PremiumColors.SolarGold.copy(alpha = 0.3f)
                        isConnected -> PremiumColors.NeonMagenta.copy(alpha = 0.3f)
                        else -> PremiumColors.AuroraGreen.copy(alpha = 0.3f)
                    },
                    shape = RoundedCornerShape(12.dp)
                )
                .clickable(enabled = !isConnecting) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (isConnected) onDisconnect() else onConnect()
                }
                .padding(14.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isConnecting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = PremiumColors.SolarGold,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = if (isConnected) Icons.Outlined.LinkOff else Icons.Outlined.Link,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = if (isConnected) PremiumColors.NeonMagenta else PremiumColors.AuroraGreen
                    )
                }
                Text(
                    text = when {
                        isConnecting -> "Connecting..."
                        isConnected -> "Disconnect from Radio"
                        else -> "Connect to Radio"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = when {
                        isConnecting -> PremiumColors.SolarGold
                        isConnected -> PremiumColors.NeonMagenta
                        else -> PremiumColors.AuroraGreen
                    }
                )
            }
        }
    }
}

// ============================================================================
// DIALOGS
// ============================================================================

@Composable
private fun PublicKeyDialog(
    publicKey: String,
    deviceId: String,
    onDismiss: () -> Unit
) {
    PremiumConfirmDialog(
        title = "Your Public Key",
        message = "Share this key with team members for secure communication.\n\nDevice: $deviceId\n\nKey: ${publicKey.take(32)}...",
        confirmText = "Copy",
        cancelText = "Close",
        icon = Icons.Outlined.Key,
        onConfirm = { onDismiss() },
        onDismiss = onDismiss
    )
}

@Composable
private fun SecurityInfoDialog(
    onDismiss: () -> Unit
) {
    PremiumConfirmDialog(
        title = "MLS Encryption",
        message = "Mesh Rider Wave uses Message Layer Security (MLS) protocol for group encryption.\n\n• Forward Secrecy: Past messages stay secure\n• Post-Compromise Security: Recovers from key leaks\n• Efficient Key Management: Scales to large groups",
        confirmText = "Got it",
        cancelText = "",
        icon = Icons.Outlined.Security,
        onConfirm = { onDismiss() },
        onDismiss = onDismiss
    )
}

@Composable
private fun AboutAppDialog(
    onDismiss: () -> Unit
) {
    PremiumConfirmDialog(
        title = "Mesh Rider Wave",
        message = "Military-grade tactical communication app for Android.\n\n• P2P Voice/Video Calls\n• Push-to-Talk Channels\n• Blue Force Tracking\n• MLS Group Encryption\n• Offline Mesh Networking\n\nBuilt for Doodle Labs Mesh Rider radios.\n\nVersion 1.0.0 (2026)",
        confirmText = "Close",
        cancelText = "",
        icon = Icons.Outlined.Info,
        onConfirm = { onDismiss() },
        onDismiss = onDismiss
    )
}

@Composable
private fun DeveloperDialog(
    onDismiss: () -> Unit,
    onOpenLinkedIn: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(PremiumColors.DeepSpace.copy(alpha = 0.6f))
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
                        color = PremiumColors.GlassBorder,
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
                    // Avatar
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        PremiumColors.ElectricCyan,
                                        PremiumColors.HoloPurple
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "JP",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = PremiumColors.TextPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "Jabbir Basha P",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = PremiumColors.TextPrimary
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Lead Software Engineer",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PremiumColors.ElectricCyan,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Doodle Labs • Singapore",
                        style = MaterialTheme.typography.bodySmall,
                        color = PremiumColors.TextSecondary
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Skills
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val skills = listOf("Android", "Flutter", "AI/ML", "WebRTC", "Crypto")
                        items(skills.size) { index ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(PremiumColors.ElectricCyan.copy(alpha = 0.1f))
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = skills[index],
                                    style = MaterialTheme.typography.labelSmall,
                                    color = PremiumColors.ElectricCyan,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "15+ years of experience in mobile development, specializing in real-time communication, mesh networking, and secure protocols.",
                        style = MaterialTheme.typography.bodySmall,
                        color = PremiumColors.TextSecondary,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // LinkedIn button
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF0A66C2))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onOpenLinkedIn()
                            }
                            .padding(14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "View LinkedIn Profile",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = PremiumColors.TextPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    TextButton(onClick = onDismiss) {
                        Text(
                            text = "Close",
                            color = PremiumColors.TextSecondary
                        )
                    }
                }
            }
        }
    }
}
