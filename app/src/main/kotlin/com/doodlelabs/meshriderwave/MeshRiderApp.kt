/*
 * Mesh Rider Wave - Military-Grade P2P Voice/Video Communication
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 *
 * Application entry point with Hilt DI
 */

package com.doodlelabs.meshriderwave

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import dagger.hilt.android.HiltAndroidApp
import com.doodlelabs.meshriderwave.core.crash.LocalCrashHandler
import com.doodlelabs.meshriderwave.core.util.Logger
import javax.inject.Inject

@HiltAndroidApp
class MeshRiderApp : Application() {

    @Inject
    lateinit var crashHandler: LocalCrashHandler

    override fun onCreate() {
        super.onCreate()
        instance = this
        Logger.init(this)

        // Install local crash handler (offline - no internet required)
        crashHandler.install(this)

        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService<NotificationManager>() ?: return

            // Service channel - Android 14+ compliant
            val serviceChannel = NotificationChannel(
                CHANNEL_SERVICE,
                "Mesh Network Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the app connected to receive calls and PTT. Uses minimal battery - only activates when needed."
                setShowBadge(false)
                // Don't show on lock screen for privacy
                lockscreenVisibility = NotificationCompat.VISIBILITY_SECRET
            }

            // Call channel
            val callChannel = NotificationChannel(
                CHANNEL_CALLS,
                "Incoming Calls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Incoming call notifications"
                setShowBadge(true)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
            }

            notificationManager.createNotificationChannels(listOf(serviceChannel, callChannel))
        }
    }

    companion object {
        const val CHANNEL_SERVICE = "mesh_rider_service"
        const val CHANNEL_CALLS = "mesh_rider_calls"

        lateinit var instance: MeshRiderApp
            private set
    }
}
