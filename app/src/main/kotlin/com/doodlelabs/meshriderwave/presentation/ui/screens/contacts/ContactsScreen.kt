/*
 * Mesh Rider Wave - Premium Contacts Screen 2026
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 *
 * Ultra-premium contacts screen with glassmorphism and animations
 * Following 2026 UI/UX trends
 */

package com.doodlelabs.meshriderwave.presentation.ui.screens.contacts

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.doodlelabs.meshriderwave.domain.model.Contact
import com.doodlelabs.meshriderwave.presentation.ui.components.*
import com.doodlelabs.meshriderwave.presentation.ui.theme.PremiumColors
import com.doodlelabs.meshriderwave.presentation.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToQRShow: () -> Unit,
    onNavigateToQRScan: () -> Unit,
    onStartCall: (String) -> Unit,
    onStartVideoCall: (String) -> Unit = {},
    onContactClick: (String) -> Unit = {},
    viewModel: MainViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }

    val filteredContacts = remember(uiState.contacts, searchQuery) {
        if (searchQuery.isBlank()) {
            uiState.contacts
        } else {
            uiState.contacts.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                        it.shortId.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    // Deep space background
    DeepSpaceBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                PremiumContactsTopBar(
                    searchQuery = searchQuery,
                    isSearching = isSearching,
                    onSearchQueryChange = { searchQuery = it },
                    onToggleSearch = { isSearching = !isSearching },
                    onNavigateBack = onNavigateBack,
                    onNavigateToQRShow = onNavigateToQRShow,
                    onNavigateToQRScan = onNavigateToQRScan
                )
            },
            floatingActionButton = {
                GlowingIconButton(
                    onClick = onNavigateToQRScan,
                    icon = Icons.Filled.PersonAdd,
                    size = 64.dp,
                    backgroundColor = PremiumColors.NeonMagenta,
                    glowColor = PremiumColors.NeonMagentaGlow
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // Search Bar (animated)
                AnimatedVisibility(
                    visible = isSearching,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    PremiumSearchBar(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        onClear = { searchQuery = "" },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                // Contact count badge
                if (filteredContacts.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${filteredContacts.size} contacts",
                            style = MaterialTheme.typography.labelMedium,
                            color = PremiumColors.TextSecondary
                        )

                        // Online count - use real discovery data from MainViewModel
                        val onlineCount = filteredContacts.count { contact ->
                            uiState.isContactOnline(contact)
                        }
                        if (onlineCount > 0) {
                            PremiumChip(
                                text = "$onlineCount online",
                                color = PremiumColors.OnlineGlow,
                                icon = Icons.Filled.Circle
                            )
                        }
                    }
                }

                // Content
                if (filteredContacts.isEmpty()) {
                    PremiumEmptyContactsState(
                        isSearchResult = searchQuery.isNotEmpty(),
                        onAddContact = onNavigateToQRScan
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(filteredContacts, key = { it.deviceId }) { contact ->
                            val publicKeyHex = contact.publicKey.joinToString("") { "%02x".format(it) }
                            // Use real discovery data for online status
                            val isOnline = uiState.isContactOnline(contact)
                            PremiumContactListItem(
                                contact = contact,
                                isOnline = isOnline,
                                onCall = { onStartCall(contact.deviceId) },
                                onVideoCall = { onStartVideoCall(contact.deviceId) },
                                onClick = { onContactClick(publicKeyHex) }
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
    }
}

/**
 * Premium Contacts Top Bar with search toggle
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PremiumContactsTopBar(
    searchQuery: String,
    isSearching: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onToggleSearch: () -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToQRShow: () -> Unit,
    onNavigateToQRScan: () -> Unit
) {
    TopAppBar(
        title = {
            Text(
                text = "Contacts",
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
            // Search toggle
            IconButton(onClick = onToggleSearch) {
                Icon(
                    if (isSearching) Icons.Filled.Close else Icons.Filled.Search,
                    contentDescription = if (isSearching) "Close Search" else "Search",
                    tint = if (isSearching) PremiumColors.ElectricCyan else PremiumColors.TextSecondary
                )
            }

            // My QR code
            IconButton(onClick = onNavigateToQRShow) {
                Icon(
                    Icons.Filled.QrCode,
                    contentDescription = "My QR Code",
                    tint = PremiumColors.TextSecondary
                )
            }

            // Scan QR
            IconButton(onClick = onNavigateToQRScan) {
                Icon(
                    Icons.Filled.QrCodeScanner,
                    contentDescription = "Scan QR",
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
 * Premium Search Bar with glass effect
 */
@Composable
private fun PremiumSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassCard(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 16.dp
    ) {
        TextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    "Search contacts...",
                    color = PremiumColors.TextTertiary
                )
            },
            leadingIcon = {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = null,
                    tint = PremiumColors.TextSecondary
                )
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = onClear) {
                        Icon(
                            Icons.Filled.Clear,
                            contentDescription = "Clear",
                            tint = PremiumColors.TextSecondary
                        )
                    }
                }
            },
            singleLine = true,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedTextColor = PremiumColors.TextPrimary,
                unfocusedTextColor = PremiumColors.TextPrimary,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = PremiumColors.ElectricCyan
            )
        )
    }
}

/**
 * Premium Contact List Item
 * FIXED Jan 2026: Use real discovery-based online status instead of timestamp
 */
@Composable
private fun PremiumContactListItem(
    contact: Contact,
    isOnline: Boolean,
    onCall: () -> Unit,
    onVideoCall: () -> Unit,
    onClick: () -> Unit
) {
    val contactStatus = if (isOnline) ContactStatus.ONLINE else ContactStatus.OFFLINE

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 16.dp,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Premium Avatar with status
            ContactAvatar(
                name = contact.name,
                status = contactStatus,
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

                // Device ID row
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Key,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = PremiumColors.TextTertiary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = contact.shortId,
                        style = MaterialTheme.typography.bodySmall,
                        color = PremiumColors.TextTertiary
                    )
                }

                // IP Address
                if (contact.addresses.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = contact.addresses.first(),
                        style = MaterialTheme.typography.bodySmall,
                        color = PremiumColors.TextTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
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
                    inactiveColor = PremiumColors.SpaceGrayLighter
                )

                // Video call button
                NeumorphicToggleButton(
                    onClick = onVideoCall,
                    icon = Icons.Filled.Videocam,
                    size = 44.dp,
                    isActive = false,
                    activeColor = PremiumColors.ElectricCyan,
                    inactiveColor = PremiumColors.SpaceGrayLighter
                )
            }
        }
    }
}

/**
 * Premium Empty State
 */
@Composable
private fun PremiumEmptyContactsState(
    isSearchResult: Boolean,
    onAddContact: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
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
                    .padding(40.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Icon
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(PremiumColors.SpaceGrayLight),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isSearchResult) Icons.Filled.SearchOff
                        else Icons.Outlined.People,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = PremiumColors.TextSecondary
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = if (isSearchResult) "No contacts found" else "No contacts yet",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = PremiumColors.TextPrimary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (isSearchResult) "Try a different search term"
                    else "Scan a QR code to add your first contact",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PremiumColors.TextSecondary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                if (!isSearchResult) {
                    Spacer(modifier = Modifier.height(24.dp))

                    PremiumButton(
                        onClick = onAddContact,
                        text = "Scan QR Code",
                        icon = Icons.Filled.QrCodeScanner,
                        gradient = PremiumColors.PrimaryGradient,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
