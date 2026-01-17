/*
 * Mesh Rider Wave - Main Activity
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 */

package com.doodlelabs.meshriderwave.presentation

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
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

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        logD("Permissions granted: $allGranted")
        if (allGranted) {
            startMeshService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        requestPermissions()

        setContent {
            val viewModel: MainViewModel = hiltViewModel()
            val uiState by viewModel.uiState.collectAsState()

            MeshRiderWaveTheme(darkTheme = uiState.nightMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()

                    MeshRiderNavGraph(
                        navController = navController,
                        onStartCall = { contactId ->
                            startCallActivity(contactId)
                        }
                    )
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

    private fun startCallActivity(contactId: String) {
        val intent = Intent(this, CallActivity::class.java).apply {
            putExtra(CallActivity.EXTRA_CONTACT_ID, contactId)
            putExtra(CallActivity.EXTRA_IS_OUTGOING, true)
        }
        startActivity(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Service continues running
    }
}
