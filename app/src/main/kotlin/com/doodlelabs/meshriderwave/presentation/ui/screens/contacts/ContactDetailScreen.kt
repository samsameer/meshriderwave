/*
 * Mesh Rider Wave - Contact Detail Screen
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 *
 * Premium contact detail screen with glassmorphism
 */

package com.doodlelabs.meshriderwave.presentation.ui.screens.contacts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.doodlelabs.meshriderwave.presentation.ui.components.*
import com.doodlelabs.meshriderwave.presentation.ui.theme.PremiumColors
import com.doodlelabs.meshriderwave.presentation.viewmodel.ContactDetailViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactDetailScreen(
    publicKeyHex: String,
    onNavigateBack: () -> Unit,
    onStartCall: (String) -> Unit,
    onStartVideoCall: (String) -> Unit = {},
    onShowQR: () -> Unit = {},
    viewModel: ContactDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(publicKeyHex) {
        viewModel.loadContact(publicKeyHex)
    }

    DeepSpaceBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "Contact Details",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = PremiumColors.TextPrimary
                        )
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
                    actions = {
                        if (!uiState.isLoading && uiState.contact != null) {
                            IconButton(onClick = { viewModel.showDeleteConfirmation() }) {
                                Icon(
                                    Icons.Outlined.Delete,
                                    contentDescription = "Delete",
                                    tint = PremiumColors.BusyRed
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            }
        ) { padding ->
            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = PremiumColors.ElectricCyan)
                    }
                }
                uiState.contact == null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Outlined.PersonOff,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = PremiumColors.TextSecondary
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Contact not found",
                                style = MaterialTheme.typography.titleMedium,
                                color = PremiumColors.TextSecondary
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = onNavigateBack,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = PremiumColors.ElectricCyan
                                )
                            ) {
                                Text("Go Back")
                            }
                        }
                    }
                }
                else -> {
                    val contact = uiState.contact!!

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Avatar Card
                        GlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            cornerRadius = 24.dp
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Large Avatar
                                ContactAvatar(
                                    name = contact.name,
                                    status = if (uiState.isOnline) ContactStatus.ONLINE else ContactStatus.OFFLINE,
                                    size = 100.dp,
                                    fontSize = 36.sp
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                // Name
                                Text(
                                    text = contact.name,
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = PremiumColors.TextPrimary
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                // Device ID
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Filled.Key,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = PremiumColors.TextTertiary
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = contact.shortId,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = PremiumColors.TextTertiary
                                    )
                                }

                                // Online status
                                Spacer(modifier = Modifier.height(8.dp))
                                PremiumChip(
                                    text = if (uiState.isOnline) "Online" else "Offline",
                                    color = if (uiState.isOnline) PremiumColors.OnlineGlow else PremiumColors.TextTertiary,
                                    icon = Icons.Filled.Circle
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Call Actions
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Voice Call
                            PremiumActionButton(
                                modifier = Modifier.weight(1f),
                                icon = Icons.Filled.Call,
                                label = "Voice Call",
                                gradient = Brush.linearGradient(
                                    colors = listOf(PremiumColors.LaserLime, PremiumColors.ElectricCyan)
                                ),
                                onClick = { onStartCall(contact.deviceId) }
                            )

                            // Video Call
                            PremiumActionButton(
                                modifier = Modifier.weight(1f),
                                icon = Icons.Filled.Videocam,
                                label = "Video Call",
                                gradient = Brush.linearGradient(
                                    colors = listOf(PremiumColors.ElectricCyan, PremiumColors.HoloPurple)
                                ),
                                onClick = { onStartVideoCall(contact.deviceId) }
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Details Card
                        GlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            cornerRadius = 16.dp
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp)
                            ) {
                                Text(
                                    text = "Details",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = PremiumColors.TextPrimary
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                // IP Addresses
                                DetailRow(
                                    icon = Icons.Filled.Wifi,
                                    label = "IP Addresses",
                                    value = if (contact.addresses.isNotEmpty())
                                        contact.addresses.joinToString("\n")
                                    else "No addresses"
                                )

                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 12.dp),
                                    color = PremiumColors.GlassWhite
                                )

                                // Last Seen
                                DetailRow(
                                    icon = Icons.Filled.Schedule,
                                    label = "Last Seen",
                                    value = uiState.lastSeenFormatted
                                )

                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 12.dp),
                                    color = PremiumColors.GlassWhite
                                )

                                // Added On
                                DetailRow(
                                    icon = Icons.Filled.CalendarMonth,
                                    label = "Added On",
                                    value = uiState.addedOnFormatted
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Actions Card
                        GlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            cornerRadius = 16.dp
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp)
                            ) {
                                // Block/Unblock
                                ActionListItem(
                                    icon = if (contact.blocked) Icons.Outlined.CheckCircle else Icons.Outlined.Block,
                                    label = if (contact.blocked) "Unblock Contact" else "Block Contact",
                                    color = if (contact.blocked) PremiumColors.LaserLime else PremiumColors.SolarGold,
                                    onClick = { viewModel.toggleBlock() }
                                )

                                HorizontalDivider(color = PremiumColors.GlassWhite)

                                // Share Contact
                                ActionListItem(
                                    icon = Icons.Outlined.Share,
                                    label = "Share Contact",
                                    color = PremiumColors.ElectricCyan,
                                    onClick = { /* TODO: Share contact QR */ }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }
        }

        // Delete Confirmation Dialog
        if (uiState.showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.hideDeleteConfirmation() },
                containerColor = PremiumColors.SpaceGray,
                title = {
                    Text(
                        "Delete Contact",
                        color = PremiumColors.TextPrimary
                    )
                },
                text = {
                    Text(
                        "Are you sure you want to delete ${uiState.contact?.name}? This action cannot be undone.",
                        color = PremiumColors.TextSecondary
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.deleteContact()
                            onNavigateBack()
                        }
                    ) {
                        Text("Delete", color = PremiumColors.BusyRed)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.hideDeleteConfirmation() }) {
                        Text("Cancel", color = PremiumColors.TextSecondary)
                    }
                }
            )
        }
    }
}

@Composable
private fun PremiumActionButton(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    gradient: Brush,
    onClick: () -> Unit
) {
    GlassCard(
        modifier = modifier,
        cornerRadius = 16.dp,
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(gradient),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    modifier = Modifier.size(28.dp),
                    tint = Color.White
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = PremiumColors.TextPrimary
            )
        }
    }
}

@Composable
private fun DetailRow(
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = PremiumColors.ElectricCyan
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = PremiumColors.TextTertiary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = PremiumColors.TextPrimary
            )
        }
    }
}

@Composable
private fun ActionListItem(
    icon: ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = color
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = color
            )
        }
    }
}
