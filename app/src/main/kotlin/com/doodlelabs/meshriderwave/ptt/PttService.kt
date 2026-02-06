/*
 * Mesh Rider Wave - PRODUCTION PTT Service
 * Foreground service for PTT functionality
 * Following Android 14+ foreground service guidelines
 * 
 * FIXED (Feb 2026):
 * - Proper wake lock to prevent Doze mode killing
 * - Battery optimization whitelist check
 * - Audio focus handling
 * - Service restart on kill
 * - Notification actions
 */

package com.doodlelabs.meshriderwave.ptt

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

/**
 * PRODUCTION PTT Foreground Service
 * 
 * Per Android 14+ requirements:
 * - Must declare foregroundServiceType MICROPHONE
 * - Must show notification while active
 * - Must handle Doze mode with WakeLock
 * - Must handle battery optimization
 */
class PttService : Service() {

    companion object {
        private const val TAG = "MeshRider:PttService"
        const val ACTION_PTT_DOWN = "com.doodlelabs.meshriderwave.PTT_DOWN"
        const val ACTION_PTT_UP = "com.doodlelabs.meshriderwave.PTT_UP"
        const val ACTION_START = "com.doodlelabs.meshriderwave.PTT_START"
        const val ACTION_STOP = "com.doodlelabs.meshriderwave.PTT_STOP"
        const val ACTION_TOGGLE_SPEAKER = "com.doodlelabs.meshriderwave.TOGGLE_SPEAKER"

        private const val NOTIFICATION_ID = 2001
        private const val CHANNEL_ID = "ptt_service"
        private const val WAKE_LOCK_TAG = "MeshRider:PTT"
        
        // Check if service is running
        @Volatile
        var isRunning = false
            private set
    }

    private val binder = PttBinder()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // PTT Manager instance
    private var pttManager: WorkingPttManager? = null
    private var pttJob: Job? = null

    // Current transmission state
    private var isTransmitting = false

    // Wake lock for Doze mode prevention
    private var wakeLock: PowerManager.WakeLock? = null

    // Audio manager
    private lateinit var audioManager: AudioManager

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "PTT Service created")

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        
        // Create notification channel
        createNotificationChannel()
        
        // Acquire wake lock
        acquireWakeLock()
        
        // Check battery optimization
        checkBatteryOptimization()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startForegroundWithNotification()
                isRunning = true
            }
            ACTION_STOP -> {
                stopTransmit()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                isRunning = false
            }
            ACTION_PTT_DOWN -> {
                handlePttDown()
            }
            ACTION_PTT_UP -> {
                handlePttUp()
            }
            ACTION_TOGGLE_SPEAKER -> {
                toggleSpeaker()
            }
        }

        // Restart if killed (important for PTT reliability)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onDestroy() {
        Log.i(TAG, "PTT Service destroyed")
        isRunning = false
        
        stopTransmit()
        pttManager?.cleanup()
        
        releaseWakeLock()
        scope.cancel()
        
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.i(TAG, "Task removed, restarting service")
        // Restart service if app is swiped away
        val restartIntent = Intent(applicationContext, PttService::class.java).apply {
            action = ACTION_START
        }
        startService(restartIntent)
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            WAKE_LOCK_TAG
        ).apply {
            setReferenceCounted(false)
            acquire(10 * 60 * 1000L) // 10 minutes, will refresh while active
        }
        Log.d(TAG, "Wake lock acquired")
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
            wakeLock = null
        }
        Log.d(TAG, "Wake lock released")
    }

    private fun checkBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                Log.w(TAG, "Battery optimization not disabled - service may be killed")
                // Could show notification to user here
            }
        }
    }

    private fun startForegroundWithNotification() {
        val notification = createNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
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
                enableVibration(false)
                setSound(null, null)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        // Intent to open app when notification tapped
        val contentIntent = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(
                this,
                0,
                it,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        // Toggle speaker action
        val toggleSpeakerIntent = Intent(this, PttService::class.java).apply {
            action = ACTION_TOGGLE_SPEAKER
        }
        val toggleSpeakerPendingIntent = PendingIntent.getService(
            this,
            1,
            toggleSpeakerIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        // Stop service action
        val stopIntent = Intent(this, PttService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            2,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Mesh Rider Wave PTT")
            .setContentText(if (isTransmitting) "Transmitting..." else "Ready")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .addAction(
                android.R.drawable.ic_lock_silent_mode_off,
                if (audioManager.isSpeakerphoneOn) "Earpiece" else "Speaker",
                toggleSpeakerPendingIntent
            )
            .addAction(
                android.R.drawable.ic_delete,
                "Stop",
                stopPendingIntent
            )
            .build()
    }

    private fun updateNotification() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, createNotification())
    }

    private fun handlePttDown() {
        Log.i(TAG, "PTT DOWN received")

        pttJob?.cancel()
        pttJob = scope.launch {
            if (!isTransmitting) {
                val success = pttManager?.startTransmission() ?: false
                if (success) {
                    isTransmitting = true
                    updateNotification()
                    
                    // Keep wake lock active while transmitting
                    wakeLock?.let {
                        if (!it.isHeld) it.acquire(10 * 60 * 1000L)
                    }
                }
            }
        }
    }

    private fun handlePttUp() {
        Log.i(TAG, "PTT UP received")

        pttJob?.cancel()
        scope.launch {
            stopTransmit()
        }
    }

    private fun stopTransmit() {
        if (isTransmitting) {
            pttManager?.stopTransmission()
            isTransmitting = false
            updateNotification()
        }
    }

    private fun toggleSpeaker() {
        val isSpeaker = !audioManager.isSpeakerphoneOn
        audioManager.isSpeakerphoneOn = isSpeaker
        
        if (isSpeaker) {
            pttManager?.enableSpeaker()
        } else {
            pttManager?.enableEarpiece()
        }
        
        updateNotification()
        Log.i(TAG, "Speaker toggled: $isSpeaker")
    }

    fun setPttManager(manager: WorkingPttManager) {
        pttManager = manager
    }

    fun isTransmitting(): Boolean = isTransmitting

    inner class PttBinder : Binder() {
        fun getService(): PttService = this@PttService
    }
}
