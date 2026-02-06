/*
 * Mesh Rider Wave - Foreground Mesh Service
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 *
 * Military-Grade Discovery Service (Jan 2026)
 *
 * Starts and manages all discovery components:
 * - MeshNetworkManager: P2P signaling (TCP)
 * - PeerDiscoveryManager: mDNS/DNS-SD discovery (link-local)
 * - BeaconManager: Multicast beacon discovery (mesh-wide)
 * - ContactAddressSync: Address synchronization
 * - NetworkTypeDetector: Network type monitoring
 */

package com.doodlelabs.meshriderwave.core.network

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.doodlelabs.meshriderwave.BuildConfig
import com.doodlelabs.meshriderwave.MeshRiderApp
import com.doodlelabs.meshriderwave.R
import com.doodlelabs.meshriderwave.core.discovery.BeaconManager
import com.doodlelabs.meshriderwave.core.discovery.ContactAddressSync
import com.doodlelabs.meshriderwave.core.util.logD
import com.doodlelabs.meshriderwave.core.util.logE
import com.doodlelabs.meshriderwave.core.util.logI
import com.doodlelabs.meshriderwave.core.util.logW
import com.doodlelabs.meshriderwave.core.ptt.PTTManager
import com.doodlelabs.meshriderwave.core.telecom.CallNotificationManager
import com.doodlelabs.meshriderwave.core.telecom.TelecomCallManager
import com.doodlelabs.meshriderwave.domain.repository.ContactRepository
import com.doodlelabs.meshriderwave.domain.repository.SettingsRepository
import com.doodlelabs.meshriderwave.presentation.MainActivity
import com.doodlelabs.meshriderwave.presentation.ui.screens.call.CallActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

@AndroidEntryPoint
class MeshService : Service() {

    @Inject
    lateinit var meshNetworkManager: MeshNetworkManager

    @Inject
    lateinit var peerDiscoveryManager: PeerDiscoveryManager

    @Inject
    lateinit var beaconManager: BeaconManager

    @Inject
    lateinit var contactAddressSync: ContactAddressSync

    @Inject
    lateinit var networkTypeDetector: NetworkTypeDetector

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var contactRepository: ContactRepository

    @Inject
    lateinit var pttManager: PTTManager

    @Inject
    lateinit var callNotificationManager: CallNotificationManager

    @Inject
    lateinit var telecomCallManager: TelecomCallManager

    private val binder = MeshBinder()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // RACE CONDITION FIX Feb 2026: Track service lifecycle state atomically
    private val serviceStateLock = Mutex()
    @Volatile
    private var isServiceDestroyed = false

    // Feb 2026 CRITICAL: WiFi MulticastLock — without this, Android discards
    // all multicast packets to save battery. Required for PTT beacon discovery
    // AND multicast RTP audio. Every PTT app (Zello, VoicePing) needs this.
    private var multicastLock: WifiManager.MulticastLock? = null

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
        scope.launch {
            serviceStateLock.withLock {
                isServiceDestroyed = true
            }
        }
        stopListening()
        scope.cancel()
        super.onDestroy()
    }

    private fun startForegroundWithNotification() {
        val notification = createNotification("Listening for calls...")

        // CRITICAL FIX Jan 2026: Android 14+ requires RECORD_AUDIO permission BEFORE
        // starting foreground service with MICROPHONE type. Check permission first.
        val hasMicPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        val hasCameraPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Build service type based on granted permissions
            var serviceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            if (hasMicPermission) {
                serviceType = serviceType or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }
            if (hasCameraPermission) {
                serviceType = serviceType or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            }

            logD("startForeground: mic=$hasMicPermission, camera=$hasCameraPermission, type=$serviceType")
            startForeground(NOTIFICATION_ID, notification, serviceType)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (hasMicPermission) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                // Fallback without microphone type
                startForeground(NOTIFICATION_ID, notification)
            }
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

    private var isListening = false

    /**
     * Start listening for incoming connections and peer discovery
     * RACE CONDITION FIX Feb 2026: Check if service is destroyed before starting
     */
    private fun startListening() {
        if (isListening) {
            logI("startListening() already listening, skipping")
            return
        }

        // Check if service is being destroyed (volatile read, no mutex needed)
        if (isServiceDestroyed) {
            logW("startListening() service is destroyed, skipping")
            return
        }

        isListening = true
        logI("startListening()")

        // Feb 2026 CRITICAL: Acquire WiFi MulticastLock FIRST
        // Without this, Android's WiFi driver drops all multicast packets.
        // This affects both PTT channel beacon (239.255.77.2:7778) and
        // multicast RTP audio (239.255.0.x:5004).
        try {
            val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
            multicastLock = wifiManager.createMulticastLock("MeshRiderPTT").apply {
                setReferenceCounted(false)
                acquire()
            }
            logI("WiFi MulticastLock acquired — multicast reception enabled")
        } catch (e: Exception) {
            logE("Failed to acquire MulticastLock: ${e.message}")
        }

        // Start P2P signaling server
        meshNetworkManager.start()

        // Start network type detection
        networkTypeDetector.start()
        logI("NetworkTypeDetector started")

        // Start contact address synchronization
        contactAddressSync.start()
        logI("ContactAddressSync started")

        // Start multicast beacon discovery
        beaconManager.start()
        logI("BeaconManager started")

        // Start mDNS peer discovery
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
                // RACE CONDITION FIX: Don't handle calls if service is destroyed
                if (!serviceStateLock.withLock { isServiceDestroyed }) {
                    logI("Incoming call from ${incomingCall.remoteAddress}")
                    handleIncomingCall(incomingCall)
                }
            }
        }

        // Combine mDNS and beacon discoveries for unified peer count
        // Military-grade Jan 2026: Multi-source peer discovery
        scope.launch {
            combine(
                peerDiscoveryManager.discoveredPeers,
                beaconManager.discoveredPeersFlow
            ) { mdnsPeers, beaconPeers ->
                // Merge peers, deduplicate by public key
                val allPeers = mutableMapOf<String, Any>()
                mdnsPeers.forEach { (key, _) -> allPeers[key] = Unit }
                beaconPeers.forEach { (key, _) -> allPeers[key] = Unit }
                allPeers.size to (mdnsPeers.values.toList() to beaconPeers.values.toList())
            }.collect { (totalPeers, peers) ->
                // RACE CONDITION FIX: Don't update if service is destroyed
                if (!serviceStateLock.withLock { isServiceDestroyed }) {
                    val (mdnsPeers, _) = peers
                    logD("Total peers: $totalPeers (mDNS: ${mdnsPeers.size})")

                    // Update notification with combined peer count
                    updateNotification(totalPeers)

                    // Register mDNS peer addresses with PTTManager
                    mdnsPeers.forEach { peer ->
                        try {
                            peer.primaryAddress?.let { address ->
                                pttManager.registerMemberAddress(
                                    peer.publicKey,
                                    listOf(address)
                                )
                            }
                        } catch (e: Exception) {
                            // Peer may not be in a PTT channel
                        }
                    }
                }
            }
        }

        // Register beacon peer addresses with PTTManager
        scope.launch {
            beaconManager.peerDiscoveredEvent.collect { peer ->
                // RACE CONDITION FIX: Don't register if service is destroyed
                if (!serviceStateLock.withLock { isServiceDestroyed }) {
                    try {
                        pttManager.registerMemberAddress(
                            peer.publicKey,
                            listOf(peer.address)
                        )
                    } catch (e: Exception) {
                        // Peer may not be in a PTT channel
                    }
                }
            }
        }

        // Observe PTT messages and route to PTTManager
        scope.launch {
            meshNetworkManager.pttMessages.collect { pttMessage ->
                // RACE CONDITION FIX: Don't handle if service is destroyed
                if (!serviceStateLock.withLock { isServiceDestroyed }) {
                    logD("PTT message received: ${pttMessage.type}")
                    handlePTTMessage(pttMessage)
                }
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
        isListening = false
        logI("stopListening()")

        // Stop all discovery components in reverse order
        contactAddressSync.stop()
        logI("ContactAddressSync stopped")

        beaconManager.stop()
        logI("BeaconManager stopped")

        networkTypeDetector.stop()
        logI("NetworkTypeDetector stopped")

        peerDiscoveryManager.stop()
        logI("PeerDiscoveryManager stopped")

        meshNetworkManager.stop()
        logI("MeshNetworkManager stopped")

        // Jan 2026 CRITICAL FIX: Clean up PTTManager resources
        // Without this, audio threads/sockets leak after service stop
        pttManager.cleanup()
        logI("PTTManager stopped")

        // Feb 2026: Release WiFi MulticastLock
        multicastLock?.let {
            if (it.isHeld) {
                it.release()
                logI("WiFi MulticastLock released")
            }
        }
        multicastLock = null
    }

    /**
     * Handle incoming call
     * RACE CONDITION FIX Feb 2026: Check service state before handling
     */
    private fun handleIncomingCall(call: MeshNetworkManager.IncomingCall) {
        // Store socket in static holder for CallActivity to retrieve
        setPendingCallSocket(call.socket)

        // Encode sender key as Base64 for Intent
        val senderKeyBase64 = android.util.Base64.encodeToString(
            call.senderPublicKey,
            android.util.Base64.NO_WRAP
        )

        val callerName = call.remoteAddress ?: "Unknown Caller"

        // Register incoming call with Android Telecom framework FIRST
        // Per developer.android.com: must post notification within 5 seconds of addCall()
        scope.launch {
            try {
                // RACE CONDITION FIX: Check service is still alive
                if (serviceStateLock.withLock { isServiceDestroyed }) {
                    logW("handleIncomingCall: service destroyed, skipping Telecom registration")
                    return@launch
                }

                telecomCallManager.addIncomingCall(
                    displayName = callerName,
                    address = call.remoteAddress ?: "unknown",
                    isVideo = false,
                    onAnswer = { callType ->
                        logI("Telecom: onAnswer callType=$callType")
                        // System requested answer (e.g., Bluetooth headset button)
                        // CallActivity handles the actual WebRTC answer
                    },
                    onDisconnect = { cause ->
                        logI("Telecom: onDisconnect cause=${cause.code}")
                        callNotificationManager.cancelAll()
                    },
                    onSetActive = {
                        logI("Telecom: onSetActive")
                    },
                    onSetInactive = {
                        logI("Telecom: onSetInactive")
                    }
                )
            } catch (e: Exception) {
                logE("Failed to register incoming call with Telecom", e)
            }
        }

        // Post CallStyle notification (within 5 seconds of addCall)
        callNotificationManager.showIncomingCallNotification(
            callerName = callerName,
            remoteAddress = call.remoteAddress,
            offer = call.offer,
            senderKeyBase64 = senderKeyBase64
        )

        // Launch CallActivity for incoming call
        try {
            val intent = Intent(this, CallActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(CallActivity.EXTRA_IS_OUTGOING, false)
                putExtra(CallActivity.EXTRA_REMOTE_ADDRESS, call.remoteAddress)
                putExtra(CallActivity.EXTRA_OFFER, call.offer)
                putExtra(CallActivity.EXTRA_SENDER_KEY, senderKeyBase64)
            }
            startActivity(intent)
        } catch (e: Exception) {
            logE("Failed to launch CallActivity", e)
        }
    }

    inner class MeshBinder : Binder() {
        fun getService(): MeshService = this@MeshService
    }

    companion object {
        const val ACTION_START = "com.doodlelabs.meshriderwave.START"
        const val ACTION_STOP = "com.doodlelabs.meshriderwave.STOP"
        // Feb 2026 FIX: PTT actions for hardware button support (XCover)
        const val ACTION_PTT_DOWN = "com.doodlelabs.meshriderwave.PTT_DOWN"
        const val ACTION_PTT_UP = "com.doodlelabs.meshriderwave.PTT_UP"
        const val NOTIFICATION_ID = 1001

        // Thread-safe socket holder using Object lock
        private val socketLock = Object()
        @Volatile
        private var pendingCallSocket: java.net.Socket? = null
        @Volatile
        private var socketSetTime: Long = 0

        // Socket timeout (30 seconds - if not retrieved, it's stale)
        // Increased from 10s: Activity launch + render can take time on slower devices
        private const val SOCKET_TIMEOUT_MS = 30000L

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
