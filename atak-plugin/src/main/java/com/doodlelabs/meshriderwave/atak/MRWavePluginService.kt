/**
 * MR Wave Plugin Service
 *
 * Background service for the ATAK plugin.
 * Handles background CoT synchronization and connection monitoring.
 *
 * Copyright (C) 2024-2026 DoodleLabs. All Rights Reserved.
 */
package com.doodlelabs.meshriderwave.atak

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log

/**
 * Background service for the MR Wave ATAK plugin.
 *
 * Responsibilities:
 * - Monitor connection to MR Wave app
 * - Handle CoT synchronization in background
 * - Maintain plugin state when ATAK is backgrounded
 */
class MRWavePluginService : Service() {

    companion object {
        private const val TAG = "MRWavePluginService"
        const val ACTION_START = "com.doodlelabs.meshriderwave.atak.START_SERVICE"
        const val ACTION_STOP = "com.doodlelabs.meshriderwave.atak.STOP_SERVICE"
    }

    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): MRWavePluginService = this@MRWavePluginService
    }

    override fun onBind(intent: Intent?): IBinder {
        Log.d(TAG, "onBind")
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: ${intent?.action}")

        when (intent?.action) {
            ACTION_START -> {
                Log.i(TAG, "Starting MR Wave plugin service")
                // Start background monitoring
            }
            ACTION_STOP -> {
                Log.i(TAG, "Stopping MR Wave plugin service")
                stopSelf()
            }
        }

        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "onCreate: Service created")
    }

    override fun onDestroy() {
        Log.i(TAG, "onDestroy: Service destroyed")
        super.onDestroy()
    }
}
