/*
 * Mesh Rider Wave - PTT Service
 * Foreground service for PTT functionality
 * Following Android 14+ foreground service guidelines
 */

package com.doodlelabs.meshriderwave.ptt

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

/**
 * PTT Foreground Service
 * Per Android 14+ requirements:
 * - Must declare foregroundServiceType
 * - Must have microphone type for audio capture
 * - Must show notification while active
 */
class PttService : Service() {

    companion object {
        private const val TAG = "MeshRider:PttService"
        const val ACTION_PTT_DOWN = "com.doodlelabs.meshriderwave.PTT_DOWN"
        const val ACTION_PTT_UP = "com.doodlelabs.meshriderwave.PTT_UP"
        const val ACTION_START = "com.doodlelabs.meshriderwave.PTT_START"
        const val ACTION_STOP = "com.doodlelabs.meshriderwave.PTT_STOP"

        private const val NOTIFICATION_ID = 2001
        private const val CHANNEL_ID = "ptt_service"
    }

    private val binder = PttBinder()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // PTT Manager instance
    private var pttManager: WorkingPttManager? = null

    // Current transmission state
    private var isTransmitting = false

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "PTT Service created")

        // Create notification channel
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startForegroundWithNotification()
            }
            ACTION_STOP -> {
                stopTransmit()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_PTT_DOWN -> {
                handlePttDown()
            }
            ACTION_PTT_UP -> {
                handlePttUp()
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onDestroy() {
        Log.i(TAG, "PTT Service destroyed")
        stopTransmit()
        pttManager?.cleanup()
        scope.cancel()
        super.onDestroy()
    }

    private fun startForegroundWithNotification() {
        val notification = createNotification("PTT Ready")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "PTT Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Push-to-Talk background service"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(text: String): Notification {
        val intent = Intent(this, PttService::class.java)

        val pendingIntent = PendingIntent.getService(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Mesh Rider Wave")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_media_pause)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun handlePttDown() {
        Log.i(TAG, "PTT DOWN received")

        scope.launch {
            if (!isTransmitting) {
                pttManager?.startTransmission()?.let { success ->
                    if (success) {
                        isTransmitting = true
                    }
                }
            }
        }
    }

    private fun handlePttUp() {
        Log.i(TAG, "PTT UP received")

        scope.launch {
            stopTransmit()
        }
    }

    private fun stopTransmit() {
        if (isTransmitting) {
            pttManager?.stopTransmission()
            isTransmitting = false
        }
    }

    fun setPttManager(manager: WorkingPttManager) {
        pttManager = manager
    }

    inner class PttBinder : Binder() {
        fun getService(): PttService = this@PttService
    }
}
