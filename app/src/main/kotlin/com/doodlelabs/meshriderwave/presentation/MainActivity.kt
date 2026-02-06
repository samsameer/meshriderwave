/*
 * Mesh Rider Wave - Main Activity
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 */

package com.doodlelabs.meshriderwave.presentation

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.rememberNavController
import com.doodlelabs.meshriderwave.core.network.MeshService
import com.doodlelabs.meshriderwave.core.util.logD
import com.doodlelabs.meshriderwave.presentation.navigation.MeshRiderNavGraph
import com.doodlelabs.meshriderwave.presentation.ui.screens.call.CallActivity
import com.doodlelabs.meshriderwave.presentation.ui.theme.MeshRiderWaveTheme
import com.doodlelabs.meshriderwave.presentation.viewmodel.MainViewModel
import dagger.hilt.android.AndroidEntryPoint

/**
 * CompositionLocal for WindowWidthSizeClass - enables responsive layouts throughout the app
 * Usage: val widthSizeClass = LocalWindowWidthSizeClass.current
 */
val LocalWindowWidthSizeClass = compositionLocalOf { WindowWidthSizeClass.Compact }

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // Feb 2026 FIX: Nearby WiFi permission for Android 13+ (Samsung S24+)
    // Without this, mDNS peer discovery fails silently
    private val nearbyWifiPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            // Show dialog explaining why this is needed
            AlertDialog.Builder(this)
                .setTitle("Local Network Access Required")
                .setMessage("PTT needs to discover other devices on WiFi. Please allow local network access for peer discovery to work.")
                .setPositiveButton("Settings") { _, _ ->
                    val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:com.doodlelabs.meshriderwave")
                    }
                    startActivity(intent)
                }
                .setNegativeButton("Cancel", null)
                .show()
        } else {
            logD("NEARBY_WIFI_DEVICES permission granted")
            startMeshService()
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        logD("Permissions granted: $allGranted")
        if (allGranted) {
            // Feb 2026 FIX: Request nearby WiFi permission after basic permissions
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val hasNearbyWifi = ContextCompat.checkSelfPermission(
                    this, Manifest.permission.NEARBY_WIFI_DEVICES
                ) == PackageManager.PERMISSION_GRANTED
                if (!hasNearbyWifi) {
                    nearbyWifiPermissionLauncher.launch(Manifest.permission.NEARBY_WIFI_DEVICES)
                    return@registerForActivityResult
                }
            }
            startMeshService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        requestPermissions()

        // Feb 2026 FIX: If permissions were already granted (e.g., via adb),
        // the permission callback never fires and MeshService never starts.
        // Check and start immediately if already granted.
        if (hasRequiredPermissions()) {
            startMeshService()
        }

        setContent {
            // Calculate WindowSizeClass for responsive layouts
            val windowSizeClass = calculateWindowSizeClass(this)
            val widthSizeClass = windowSizeClass.widthSizeClass

            val viewModel: MainViewModel = hiltViewModel()
            val uiState by viewModel.uiState.collectAsState()

            // Provide WindowSizeClass to all composables via CompositionLocal
            CompositionLocalProvider(LocalWindowWidthSizeClass provides widthSizeClass) {
                MeshRiderWaveTheme(darkTheme = uiState.nightMode) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        val navController = rememberNavController()

                        MeshRiderNavGraph(
                            navController = navController,
                            onStartCall = { contactId ->
                                startCallActivity(contactId, isVideoCall = false)
                            },
                            onStartVideoCall = { contactId ->
                                startCallActivity(contactId, isVideoCall = true)
                            },
                            // Direct peer calling (Jan 2026) - one-tap calling from NEARBY PEERS
                            onStartCallToPeer = { publicKey, ipAddress, name ->
                                startDirectPeerCall(publicKey, ipAddress, name, isVideoCall = false)
                            },
                            onStartVideoCallToPeer = { publicKey, ipAddress, name ->
                                startDirectPeerCall(publicKey, ipAddress, name, isVideoCall = true)
                            },
                            // SOS activation (Feb 2026) - navigate to Map for SOS activation
                            onActivateSOS = {
                                navController.navigate("map")
                            }
                        )
                    }
                }
            }
        }
    }

    private fun requestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }

        // Background location requires separate request after foreground granted
        // Will be requested when enabling Blue Force Tracking

        permissionLauncher.launch(permissions.toTypedArray())
    }

    private fun hasRequiredPermissions(): Boolean {
        val hasBasic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

        // Feb 2026 FIX: Also check NEARBY_WIFI_DEVICES on Android 13+
        val hasNearbyWifi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.NEARBY_WIFI_DEVICES) ==
                    PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        return hasBasic && hasNearbyWifi
    }

    private fun startMeshService() {
        logD("Starting MeshService")
        val intent = Intent(this, MeshService::class.java).apply {
            action = MeshService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun startCallActivity(contactId: String, isVideoCall: Boolean = false) {
        val intent = Intent(this, CallActivity::class.java).apply {
            putExtra(CallActivity.EXTRA_CONTACT_ID, contactId)
            putExtra(CallActivity.EXTRA_IS_OUTGOING, true)
            putExtra(CallActivity.EXTRA_IS_VIDEO_CALL, isVideoCall)
        }
        startActivity(intent)
    }

    private fun startVideoCallActivity(contactId: String) {
        startCallActivity(contactId, isVideoCall = true)
    }

    /**
     * Direct peer calling (Jan 2026) - for discovered peers without saved contact
     * Enables one-tap calling from NEARBY PEERS section
     */
    private fun startDirectPeerCall(
        publicKey: ByteArray,
        ipAddress: String,
        name: String,
        isVideoCall: Boolean = false
    ) {
        logD("Starting direct peer call to $name at $ipAddress")
        val intent = Intent(this, CallActivity::class.java).apply {
            putExtra(CallActivity.EXTRA_IS_OUTGOING, true)
            putExtra(CallActivity.EXTRA_IS_VIDEO_CALL, isVideoCall)
            putExtra(
                CallActivity.EXTRA_PEER_PUBLIC_KEY,
                android.util.Base64.encodeToString(publicKey, android.util.Base64.NO_WRAP)
            )
            putExtra(CallActivity.EXTRA_PEER_IP_ADDRESS, ipAddress)
            putExtra(CallActivity.EXTRA_PEER_NAME, name)
        }
        startActivity(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Service continues running
    }
}
