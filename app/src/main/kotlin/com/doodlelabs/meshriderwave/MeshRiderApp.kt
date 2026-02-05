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
import android.media.AudioAttributes
import android.media.RingtoneManager
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

            // Incoming calls channel - IMPORTANCE_HIGH with ringtone
            // Per developer.android.com/develop/connectivity/telecom/voip-app/notifications
            val incomingChannel = NotificationChannel(
                CHANNEL_CALLS_INCOMING,
                "Incoming Calls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Incoming call notifications with ringtone"
                setShowBadge(true)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setLegacyStreamType(android.media.AudioManager.STREAM_RING)
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .build()
                )
            }

            // Ongoing calls channel - IMPORTANCE_DEFAULT (no sound)
            val ongoingChannel = NotificationChannel(
                CHANNEL_CALLS_ONGOING,
                "Ongoing Calls",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Ongoing call notifications"
                setShowBadge(false)
                setSound(null, null)
            }

            // Delete legacy channel from previous versions
            @Suppress("DEPRECATION")
            notificationManager.deleteNotificationChannel(CHANNEL_CALLS)

            notificationManager.createNotificationChannels(
                listOf(serviceChannel, incomingChannel, ongoingChannel)
            )
        }
    }

    companion object {
        const val CHANNEL_SERVICE = "mesh_rider_service"
        const val CHANNEL_CALLS_INCOMING = "mesh_rider_calls_incoming"
        const val CHANNEL_CALLS_ONGOING = "mesh_rider_calls_ongoing"

        // Legacy channel ID — keep for migration (old channel gets deleted)
        @Deprecated("Use CHANNEL_CALLS_INCOMING or CHANNEL_CALLS_ONGOING")
        const val CHANNEL_CALLS = "mesh_rider_calls"

        lateinit var instance: MeshRiderApp
            private set
    }
}
