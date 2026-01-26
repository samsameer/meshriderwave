/*
 * Mesh Rider Wave - QR Scan Screen
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 */

package com.doodlelabs.meshriderwave.presentation.ui.screens.qr

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.doodlelabs.meshriderwave.domain.model.Contact
import com.doodlelabs.meshriderwave.domain.model.group.Group
import com.doodlelabs.meshriderwave.domain.model.group.GroupInvite
import com.doodlelabs.meshriderwave.presentation.ui.theme.MeshRiderColors
import com.doodlelabs.meshriderwave.presentation.ui.theme.PremiumColors
import com.doodlelabs.meshriderwave.presentation.viewmodel.MainViewModel
import com.doodlelabs.meshriderwave.presentation.viewmodel.GroupsViewModel
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QRScanScreen(
    onNavigateBack: () -> Unit,
    onContactScanned: () -> Unit,
    viewModel: MainViewModel = hiltViewModel(),
    groupsViewModel: GroupsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }
    var scannedContact by remember { mutableStateOf<Contact?>(null) }
    var scannedGroup by remember { mutableStateOf<GroupInvite?>(null) }
    var showAddContactDialog by remember { mutableStateOf(false) }
    var showJoinGroupDialog by remember { mutableStateOf(false) }
    var barcodeView by remember { mutableStateOf<DecoratedBarcodeView?>(null) }
    val showAddDialog = showAddContactDialog || showJoinGroupDialog

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    // Lifecycle handling for camera
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    if (!showAddDialog) {
                        barcodeView?.resume()
                    }
                }
                Lifecycle.Event.ON_PAUSE -> {
                    barcodeView?.pause()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            barcodeView?.pause()
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan QR Code") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (hasCameraPermission) {
                // Camera preview with QR scanner - handles both contacts and groups
                AndroidView(
                    factory = { ctx ->
                        DecoratedBarcodeView(ctx).apply {
                            barcodeView = this
                            decodeContinuous(object : BarcodeCallback {
                                override fun barcodeResult(result: BarcodeResult?) {
                                    result?.text?.let { qrData ->
                                        // Try to parse as group invite first
                                        val groupInvite = Group.fromInviteData(qrData)
                                        if (groupInvite != null) {
                                            scannedGroup = groupInvite
                                            showJoinGroupDialog = true
                                            pause()
                                            return
                                        }

                                        // Try to parse as contact
                                        val contact = Contact.fromQrData(qrData)
                                        if (contact != null) {
                                            scannedContact = contact
                                            showAddContactDialog = true
                                            pause()
                                        }
                                    }
                                }
                            })
                            resume()
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    update = { view ->
                        barcodeView = view
                        if (!showAddDialog) {
                            view.resume()
                        }
                    }
                )

                // Scanning frame overlay
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(280.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(PremiumColors.GlassWhite)
                    )
                }

                // Instructions at bottom
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(PremiumColors.DeepSpace.copy(alpha = 0.8f))
                        .padding(24.dp)
                ) {
                    Text(
                        text = "Point the camera at a Contact or Group QR code",
                        style = MaterialTheme.typography.bodyLarge,
                        color = PremiumColors.TextPrimary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                // No camera permission
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.CameraAlt,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Camera permission required",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Please grant camera access to scan QR codes",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                        Text("Grant Permission")
                    }
                }
            }
        }

        // Add contact dialog
        if (showAddContactDialog && scannedContact != null) {
            AlertDialog(
                onDismissRequest = {
                    showAddContactDialog = false
                    scannedContact = null
                },
                title = { Text("Add Contact") },
                text = {
                    Column {
                        Text("Name: ${scannedContact?.name}")
                        Text("ID: ${scannedContact?.shortId}")
                        if (scannedContact?.addresses?.isNotEmpty() == true) {
                            Text("Address: ${scannedContact?.addresses?.first()}")
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            scannedContact?.let { viewModel.addContact(it) }
                            showAddContactDialog = false
                            onContactScanned()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PremiumColors.LaserLime
                        )
                    ) {
                        Text("Add")
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = {
                        showAddContactDialog = false
                        scannedContact = null
                    }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Join group dialog
        if (showJoinGroupDialog && scannedGroup != null) {
            AlertDialog(
                onDismissRequest = {
                    showJoinGroupDialog = false
                    scannedGroup = null
                },
                title = { Text("Join Group") },
                text = {
                    Column {
                        Text("Group: ${scannedGroup?.name}")
                        Text(
                            text = "This group uses end-to-end encryption (MLS)",
                            style = MaterialTheme.typography.bodySmall,
                            color = PremiumColors.OnlineGlow
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            scannedGroup?.let { invite ->
                                // Use the full invite URL to join
                                groupsViewModel.joinGroup("meshrider://group/${android.util.Base64.encodeToString(invite.groupId, android.util.Base64.NO_WRAP)}/${invite.name}/${android.util.Base64.encodeToString(invite.creatorKey, android.util.Base64.NO_WRAP)}")
                            }
                            showJoinGroupDialog = false
                            onContactScanned()  // Navigate back
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PremiumColors.ElectricCyan
                        )
                    ) {
                        Text("Join")
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = {
                        showJoinGroupDialog = false
                        scannedGroup = null
                    }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
