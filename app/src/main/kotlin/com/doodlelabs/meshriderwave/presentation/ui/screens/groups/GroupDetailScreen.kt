/*
 * Mesh Rider Wave - Group Detail Screen
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 *
 * View group details, members, and share invite
 */

package com.doodlelabs.meshriderwave.presentation.ui.screens.groups

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.doodlelabs.meshriderwave.presentation.ui.components.*
import com.doodlelabs.meshriderwave.presentation.ui.theme.PremiumColors
import com.doodlelabs.meshriderwave.presentation.viewmodel.GroupDetailViewModel
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDetailScreen(
    groupId: String,
    onNavigateBack: () -> Unit,
    onStartGroupCall: (String) -> Unit,
    viewModel: GroupDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showShareDialog by remember { mutableStateOf(false) }
    var showMembersSheet by remember { mutableStateOf(false) }
    var showLeaveDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }

    LaunchedEffect(groupId) {
        viewModel.loadGroup(groupId)
    }

    DeepSpaceBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = uiState.group?.name ?: "Group",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = PremiumColors.TextPrimary
                            )
                            Text(
                                text = "${uiState.memberCount} members • MLS encrypted",
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
                        IconButton(onClick = { showShareDialog = true }) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = "Share",
                                tint = PremiumColors.ElectricCyan
                            )
                        }
                        IconButton(onClick = { showEditDialog = true }) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Edit Group",
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
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Group Avatar & Info
                item {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 24.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Group Avatar
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
                                Text(
                                    text = (uiState.group?.name ?: "G").take(2).uppercase(),
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = uiState.group?.name ?: "Group",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = PremiumColors.TextPrimary
                            )

                            if (!uiState.group?.description.isNullOrEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = uiState.group?.description ?: "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = PremiumColors.TextSecondary,
                                    textAlign = TextAlign.Center
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Security badge
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.Default.Lock,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = PremiumColors.OnlineGlow
                                )
                                Text(
                                    text = "End-to-End Encrypted (MLS)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = PremiumColors.OnlineGlow
                                )
                            }
                        }
                    }
                }

                // Quick Actions
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Start Call
                        GlassCard(
                            modifier = Modifier.weight(1f),
                            cornerRadius = 16.dp,
                            onClick = { onStartGroupCall(groupId) }
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(PremiumColors.LaserLime.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Call,
                                        contentDescription = null,
                                        tint = PremiumColors.LaserLime
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Call",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = PremiumColors.TextPrimary
                                )
                            }
                        }

                        // Video Call
                        GlassCard(
                            modifier = Modifier.weight(1f),
                            cornerRadius = 16.dp,
                            onClick = { onStartGroupCall(groupId) }
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(PremiumColors.ElectricCyan.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Videocam,
                                        contentDescription = null,
                                        tint = PremiumColors.ElectricCyan
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Video",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = PremiumColors.TextPrimary
                                )
                            }
                        }

                        // Share
                        GlassCard(
                            modifier = Modifier.weight(1f),
                            cornerRadius = 16.dp,
                            onClick = { showShareDialog = true }
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(PremiumColors.HoloPurple.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Share,
                                        contentDescription = null,
                                        tint = PremiumColors.HoloPurple
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Share",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = PremiumColors.TextPrimary
                                )
                            }
                        }
                    }
                }

                // Members Section
                item {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 16.dp,
                        onClick = { showMembersSheet = true }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Outlined.People,
                                contentDescription = null,
                                tint = PremiumColors.ElectricCyan
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Members",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = PremiumColors.TextPrimary
                                )
                                Text(
                                    text = "${uiState.memberCount} members in this group",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = PremiumColors.TextSecondary
                                )
                            }
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = PremiumColors.TextSecondary
                            )
                        }
                    }
                }

                // Group Info
                item {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 16.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text(
                                text = "Group Info",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = PremiumColors.TextPrimary
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            InfoRow(
                                icon = Icons.Outlined.Tag,
                                label = "Group ID",
                                value = groupId.take(8).uppercase()
                            )

                            InfoRow(
                                icon = Icons.Outlined.CalendarToday,
                                label = "Created",
                                value = "Just now"
                            )

                            InfoRow(
                                icon = Icons.Outlined.Shield,
                                label = "Encryption",
                                value = "MLS Protocol"
                            )
                        }
                    }
                }

                // Leave Group
                item {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 16.dp,
                        onClick = { showLeaveDialog = true }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Outlined.ExitToApp,
                                contentDescription = null,
                                tint = PremiumColors.SolarGold
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Leave Group",
                                style = MaterialTheme.typography.titleMedium,
                                color = PremiumColors.SolarGold
                            )
                        }
                    }
                }

                // Delete Group (for admins)
                if (uiState.isAdmin) {
                    item {
                        GlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            cornerRadius = 16.dp,
                            onClick = { showDeleteDialog = true }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Outlined.Delete,
                                    contentDescription = null,
                                    tint = PremiumColors.NeonMagenta
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Delete Group",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = PremiumColors.NeonMagenta
                                )
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }

    // Share Dialog
    if (showShareDialog) {
        ShareGroupDialog(
            groupName = uiState.group?.name ?: "Group",
            inviteCode = uiState.inviteCode,
            onDismiss = { showShareDialog = false }
        )
    }

    // Leave Group Confirmation Dialog
    if (showLeaveDialog) {
        AlertDialog(
            onDismissRequest = { showLeaveDialog = false },
            containerColor = PremiumColors.SpaceGray,
            title = {
                Text(
                    "Leave Group",
                    color = PremiumColors.TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    "Are you sure you want to leave \"${uiState.group?.name}\"? You'll need an invite to rejoin.",
                    color = PremiumColors.TextSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.leaveGroup()
                        showLeaveDialog = false
                        onNavigateBack()
                    }
                ) {
                    Text("Leave", color = PremiumColors.SolarGold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveDialog = false }) {
                    Text("Cancel", color = PremiumColors.TextSecondary)
                }
            }
        )
    }

    // Delete Group Confirmation Dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor = PremiumColors.SpaceGray,
            title = {
                Text(
                    "Delete Group",
                    color = PremiumColors.TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    "Are you sure you want to delete \"${uiState.group?.name}\"? This action cannot be undone and all members will be removed.",
                    color = PremiumColors.TextSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteGroup()
                        showDeleteDialog = false
                        onNavigateBack()
                    }
                ) {
                    Text("Delete", color = PremiumColors.NeonMagenta)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel", color = PremiumColors.TextSecondary)
                }
            }
        )
    }

    // Edit Group Dialog
    if (showEditDialog) {
        var editedName by remember { mutableStateOf(uiState.group?.name ?: "") }
        var editedDescription by remember { mutableStateOf(uiState.group?.description ?: "") }

        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            containerColor = PremiumColors.SpaceGray,
            title = {
                Text(
                    "Edit Group",
                    color = PremiumColors.TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    OutlinedTextField(
                        value = editedName,
                        onValueChange = { editedName = it },
                        label = { Text("Group Name") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = PremiumColors.TextPrimary,
                            unfocusedTextColor = PremiumColors.TextPrimary,
                            focusedLabelColor = PremiumColors.ElectricCyan,
                            unfocusedLabelColor = PremiumColors.TextSecondary,
                            focusedBorderColor = PremiumColors.ElectricCyan,
                            unfocusedBorderColor = PremiumColors.TextTertiary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = editedDescription,
                        onValueChange = { editedDescription = it },
                        label = { Text("Description (optional)") },
                        maxLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = PremiumColors.TextPrimary,
                            unfocusedTextColor = PremiumColors.TextPrimary,
                            focusedLabelColor = PremiumColors.ElectricCyan,
                            unfocusedLabelColor = PremiumColors.TextSecondary,
                            focusedBorderColor = PremiumColors.ElectricCyan,
                            unfocusedBorderColor = PremiumColors.TextTertiary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.updateGroup(editedName, editedDescription)
                        showEditDialog = false
                    },
                    enabled = editedName.isNotBlank()
                ) {
                    Text("Save", color = PremiumColors.ElectricCyan)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("Cancel", color = PremiumColors.TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun InfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = PremiumColors.TextSecondary
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = PremiumColors.TextSecondary,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = PremiumColors.TextPrimary
        )
    }
}

@Composable
private fun ShareGroupDialog(
    groupName: String,
    inviteCode: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val qrBitmap = remember(inviteCode) {
        generateQRCode(inviteCode)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PremiumColors.SpaceGray,
        title = {
            Text(
                text = "Share Group",
                color = PremiumColors.TextPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Share this QR code to invite others to $groupName",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PremiumColors.TextSecondary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                // QR Code
                qrBitmap?.let { bitmap ->
                    Box(
                        modifier = Modifier
                            .size(200.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White)
                            .padding(8.dp)
                    ) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Group QR Code",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Invite Code
                Text(
                    text = "Invite Code",
                    style = MaterialTheme.typography.labelSmall,
                    color = PremiumColors.TextSecondary
                )
                Text(
                    text = inviteCode.take(12).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = PremiumColors.ElectricCyan
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "Join my group \"$groupName\" on Mesh Rider Wave!\n\nInvite code: $inviteCode")
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share Group"))
                }
            ) {
                Text("Share Link", color = PremiumColors.ElectricCyan)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = PremiumColors.TextSecondary)
            }
        }
    )
}

private fun generateQRCode(content: String): Bitmap? {
    return try {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, 512, 512)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        bitmap
    } catch (e: Exception) {
        null
    }
}
