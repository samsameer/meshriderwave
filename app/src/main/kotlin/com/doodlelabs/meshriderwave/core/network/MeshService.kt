/*
 * Mesh Rider Wave - Foreground Mesh Service
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 */

package com.doodlelabs.meshriderwave.core.network

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.doodlelabs.meshriderwave.BuildConfig
import com.doodlelabs.meshriderwave.MeshRiderApp
import com.doodlelabs.meshriderwave.R
import com.doodlelabs.meshriderwave.core.util.logD
import com.doodlelabs.meshriderwave.core.util.logE
import com.doodlelabs.meshriderwave.core.util.logI
import com.doodlelabs.meshriderwave.core.ptt.PTTManager
import com.doodlelabs.meshriderwave.domain.repository.ContactRepository
import com.doodlelabs.meshriderwave.domain.repository.SettingsRepository
import com.doodlelabs.meshriderwave.presentation.MainActivity
import com.doodlelabs.meshriderwave.presentation.ui.screens.call.CallActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@AndroidEntryPoint
class MeshService : Service() {

    @Inject
    lateinit var meshNetworkManager: MeshNetworkManager

    @Inject
    lateinit var peerDiscoveryManager: PeerDiscoveryManager

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var contactRepository: ContactRepository

    @Inject
    lateinit var pttManager: PTTManager

    private val binder = MeshBinder()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        logD("onCreate()")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        logD("onStartCommand() action=${intent?.action}")

        when (intent?.action) {
            ACTION_START -> {
                startForegroundWithNotification()
                startListening()
            }
            ACTION_STOP -> {
                stopListening()
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onDestroy() {
        logD("onDestroy()")
        stopListening()
        scope.cancel()
        super.onDestroy()
    }

    private fun startForegroundWithNotification() {
        val notification = createNotification("Listening for calls...")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
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

    private fun createNotification(text: String, peerCount: Int = 0): Notification {
        // Open app intent
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Stop service intent
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, MeshService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Build notification with Android 14+ compliance
        return NotificationCompat.Builder(this, MeshRiderApp.CHANNEL_SERVICE)
            .setContentTitle("Mesh Rider Wave Active")
            .setContentText(text)
            .setSubText(if (peerCount > 0) "$peerCount peers nearby" else "Searching for peers...")
            .setSmallIcon(R.drawable.ic_notification) // Custom icon
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(pendingIntent)
            // Stop action button - Android 14+ requires clear way to stop foreground service
            .addAction(
                R.drawable.ic_stop,
                "Stop",
                stopIntent
            )
            // Show when service started
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
            // Low battery impact indicator
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("$text\n\nThis service uses minimal battery. It only activates when receiving calls or PTT transmissions.")
                .setBigContentTitle("Mesh Rider Wave Active")
            )
            // Foreground service behavior for Android 14+
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    /**
     * Update notification with current peer count
     */
    fun updateNotification(peerCount: Int) {
        val notification = createNotification(
            if (peerCount > 0) "Connected to mesh network" else "Listening for calls...",
            peerCount
        )
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun startListening() {
        logI("startListening()")
        meshNetworkManager.start()

        // Start peer discovery (mDNS)
        scope.launch {
            try {
                val keyPair = settingsRepository.getOrCreateKeyPair()
                val username = settingsRepository.username.first()

                logI("Starting peer discovery: $username")
                peerDiscoveryManager.start(
                    publicKey = keyPair.publicKey,
                    displayName = username,
                    port = BuildConfig.SIGNALING_PORT
                )
            } catch (e: Exception) {
                logE("Failed to start peer discovery: ${e.message}")
            }
        }

        // Observe incoming calls
        scope.launch {
            meshNetworkManager.incomingCalls.collect { incomingCall ->
                logI("Incoming call from ${incomingCall.remoteAddress}")
                handleIncomingCall(incomingCall)
            }
        }

        // Observe discovered peers and update contact lastSeenAt + notification
        scope.launch {
            peerDiscoveryManager.discoveredPeers.collect { peers ->
                logD("Discovered ${peers.size} peers")

                // Update notification with peer count
                updateNotification(peers.size)

                peers.values.forEach { peer ->
                    try {
                        // Update lastSeenAt for matching contacts
                        contactRepository.updateLastSeen(
                            publicKey = peer.publicKey,
                            address = peer.primaryAddress
                        )

                        // Register peer addresses with PTTManager for broadcast delivery
                        peer.primaryAddress?.let { address ->
                            pttManager.registerMemberAddress(
                                peer.publicKey,
                                listOf(address)
                            )
                        }
                    } catch (e: Exception) {
                        // Contact may not exist, that's ok
                    }
                }
            }
        }

        // Observe PTT messages and route to PTTManager
        scope.launch {
            meshNetworkManager.pttMessages.collect { pttMessage ->
                logD("PTT message received: ${pttMessage.type}")
                handlePTTMessage(pttMessage)
            }
        }
    }

    /**
     * Route PTT messages to PTTManager
     */
    private suspend fun handlePTTMessage(message: MeshNetworkManager.PTTMessage) {
        try {
            val channelIdBytes = hexStringToByteArray(message.channelId)

            when (message.type) {
                "AUDIO", "EMERGENCY_AUDIO" -> {
                    // Decode audio data and play
                    if (message.audioData.isNotEmpty()) {
                        val audioBytes = android.util.Base64.decode(
                            message.audioData,
                            android.util.Base64.NO_WRAP
                        )
                        pttManager.handleIncomingTransmission(
                            channelId = channelIdBytes,
                            senderKey = message.senderPublicKey,
                            audioData = audioBytes,
                            isEmergency = message.isEmergency
                        )
                    }
                }
                "FLOOR_REQUEST" -> {
                    // Another member is requesting floor
                    val priority = 0 // Could extract from message data
                    pttManager.handleFloorRequest(
                        channelId = channelIdBytes,
                        senderKey = message.senderPublicKey,
                        priority = priority
                    )
                }
                "FLOOR_TAKEN" -> {
                    // Another member started transmitting
                    logI("Floor taken by ${message.senderPublicKey.take(4).joinToString("") { "%02x".format(it) }}")
                }
                "FLOOR_RELEASED" -> {
                    // Transmission ended
                    pttManager.handleTransmissionEnd(channelIdBytes)
                }
                "EMERGENCY" -> {
                    // Emergency broadcast notification
                    logI("EMERGENCY broadcast on channel ${message.channelId}")
                    // Could trigger visual/audio alert here
                }
                "JOIN", "LEAVE" -> {
                    // Member joined/left channel - could update UI
                    logD("Member ${message.type} on channel ${message.channelId}")
                }
            }
        } catch (e: Exception) {
            logE("handlePTTMessage failed: ${e.message}")
        }
    }

    /**
     * Convert hex string to ByteArray
     */
    private fun hexStringToByteArray(hex: String): ByteArray {
        val result = ByteArray(hex.length / 2)
        for (i in result.indices) {
            result[i] = hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
        return result
    }

    private fun stopListening() {
        logI("stopListening()")
        meshNetworkManager.stop()
        peerDiscoveryManager.stop()
    }

    private fun handleIncomingCall(call: MeshNetworkManager.IncomingCall) {
        // Store socket in static holder for CallActivity to retrieve
        setPendingCallSocket(call.socket)

        // Encode sender key as Base64 for Intent
        val senderKeyBase64 = android.util.Base64.encodeToString(
            call.senderPublicKey,
            android.util.Base64.NO_WRAP
        )

        // Launch CallActivity for incoming call
        val intent = Intent(this, CallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(CallActivity.EXTRA_IS_OUTGOING, false)
            putExtra(CallActivity.EXTRA_REMOTE_ADDRESS, call.remoteAddress)
            putExtra(CallActivity.EXTRA_OFFER, call.offer)
            putExtra(CallActivity.EXTRA_SENDER_KEY, senderKeyBase64)
        }
        startActivity(intent)
    }

    inner class MeshBinder : Binder() {
        fun getService(): MeshService = this@MeshService
    }

    companion object {
        const val ACTION_START = "com.doodlelabs.meshriderwave.START"
        const val ACTION_STOP = "com.doodlelabs.meshriderwave.STOP"
        const val NOTIFICATION_ID = 1001

        // Thread-safe socket holder using Object lock
        private val socketLock = Object()
        @Volatile
        private var pendingCallSocket: java.net.Socket? = null
        @Volatile
        private var socketSetTime: Long = 0

        // Socket timeout (10 seconds - if not retrieved, it's stale)
        private const val SOCKET_TIMEOUT_MS = 10000L

        /**
         * Store socket for CallActivity to retrieve
         */
        fun setPendingCallSocket(socket: java.net.Socket) {
            synchronized(socketLock) {
                // Close any existing stale socket
                pendingCallSocket?.let { oldSocket ->
                    try {
                        if (!oldSocket.isClosed) {
                            oldSocket.close()
                        }
                    } catch (e: Exception) {
                        // Ignore
                    }
                }
                pendingCallSocket = socket
                socketSetTime = System.currentTimeMillis()
            }
        }

        /**
         * Get pending call socket (doesn't clear it - socket lifecycle managed by CallActivity)
         */
        fun getPendingCallSocket(): java.net.Socket? {
            synchronized(socketLock) {
                val socket = pendingCallSocket
                // Check if socket is stale
                if (socket != null && (System.currentTimeMillis() - socketSetTime) > SOCKET_TIMEOUT_MS) {
                    try {
                        socket.close()
                    } catch (e: Exception) {
                        // Ignore
                    }
                    pendingCallSocket = null
                    return null
                }
                // Clear after retrieval to prevent reuse
                pendingCallSocket = null
                return socket
            }
        }

        /**
         * Clear any pending socket (called on error or when socket is used)
         */
        fun clearPendingCallSocket() {
            synchronized(socketLock) {
                pendingCallSocket?.let { socket ->
                    try {
                        if (!socket.isClosed) {
                            socket.close()
                        }
                    } catch (e: Exception) {
                        // Ignore
                    }
                }
                pendingCallSocket = null
            }
        }
    }
}
