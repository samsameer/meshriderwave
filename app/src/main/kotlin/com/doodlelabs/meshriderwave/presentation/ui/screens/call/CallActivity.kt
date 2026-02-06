/*
 * Mesh Rider Wave - Premium Call Activity 2026
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 *
 * Ultra-premium full-screen call UI with WebRTC video rendering
 * Following 2026 UI/UX trends with SurfaceViewRenderer integration
 */

package com.doodlelabs.meshriderwave.presentation.ui.screens.call

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.doodlelabs.meshriderwave.core.telecom.CallActionReceiver
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import com.doodlelabs.meshriderwave.core.network.MeshNetworkManager
import com.doodlelabs.meshriderwave.core.network.MeshNetworkManager.PendingCallStore
import com.doodlelabs.meshriderwave.core.network.MeshService
import com.doodlelabs.meshriderwave.core.telecom.CallNotificationManager
import com.doodlelabs.meshriderwave.core.telecom.TelecomCallManager
import com.doodlelabs.meshriderwave.core.util.logD
import com.doodlelabs.meshriderwave.core.util.logE
import com.doodlelabs.meshriderwave.core.util.logI
import com.doodlelabs.meshriderwave.core.util.logW
import com.doodlelabs.meshriderwave.core.webrtc.RTCCall
import com.doodlelabs.meshriderwave.data.repository.ContactRepositoryImpl
import com.doodlelabs.meshriderwave.domain.model.CallState
import com.doodlelabs.meshriderwave.domain.repository.ContactRepository
import com.doodlelabs.meshriderwave.presentation.ui.components.*
import com.doodlelabs.meshriderwave.presentation.ui.theme.MeshRiderPremiumTheme
import com.doodlelabs.meshriderwave.presentation.ui.theme.PremiumColors
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.webrtc.EglBase
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack
import java.net.Socket
import javax.inject.Inject
import kotlin.math.roundToInt

@AndroidEntryPoint
class CallActivity : ComponentActivity() {

    @Inject
    lateinit var meshNetworkManager: MeshNetworkManager

    @Inject
    lateinit var contactRepository: ContactRepository

    @Inject
    lateinit var callNotificationManager: CallNotificationManager

    @Inject
    lateinit var telecomCallManager: TelecomCallManager

    private var rtcCall: RTCCall? = null
    private var localRenderer: SurfaceViewRenderer? = null
    private var remoteRenderer: SurfaceViewRenderer? = null

    // VIDEO FIX Jan 2026: Store pending video track when renderer isn't ready
    // This fixes the one-way video bug on Samsung tablets
    @Volatile
    private var pendingRemoteVideoTrack: VideoTrack? = null

    // Signaling socket for incoming calls
    private var signalingSocket: Socket? = null
    private var remoteSenderKey: ByteArray? = null

    // Direct peer calling (Jan 2026) - for discovered peers without saved contact
    private var directPeerPublicKey: ByteArray? = null
    private var directPeerIpAddress: String? = null
    private var directPeerName: String? = null

    // Broadcast receiver for hangup action from notification
    private val hangupReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == CallActionReceiver.ACTION_HANGUP_BROADCAST) {
                logI("hangupReceiver: received hangup broadcast")
                rtcCall?.hangup()
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Keep screen on during call and show over lock screen
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Modern API for lock screen behavior (API 27+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

        // Register hangup broadcast receiver
        registerReceiver(
            hangupReceiver,
            IntentFilter(CallActionReceiver.ACTION_HANGUP_BROADCAST).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
            }
        )

        val isOutgoing = intent.getBooleanExtra(EXTRA_IS_OUTGOING, true)
        val isVideoCall = intent.getBooleanExtra(EXTRA_IS_VIDEO_CALL, false)
        val contactId = intent.getStringExtra(EXTRA_CONTACT_ID)
        val remoteAddress = intent.getStringExtra(EXTRA_REMOTE_ADDRESS)
        val offer = intent.getStringExtra(EXTRA_OFFER)
        val senderKeyBase64 = intent.getStringExtra(EXTRA_SENDER_KEY)

        // Parse sender key for incoming calls
        senderKeyBase64?.let {
            remoteSenderKey = android.util.Base64.decode(it, android.util.Base64.NO_WRAP)
        }

        // Direct peer calling (Jan 2026) - for discovered peers without saved contact
        val peerPublicKeyBase64 = intent.getStringExtra(EXTRA_PEER_PUBLIC_KEY)
        val peerIpAddress = intent.getStringExtra(EXTRA_PEER_IP_ADDRESS)
        val peerName = intent.getStringExtra(EXTRA_PEER_NAME)

        peerPublicKeyBase64?.let {
            directPeerPublicKey = android.util.Base64.decode(it, android.util.Base64.NO_WRAP)
            directPeerIpAddress = peerIpAddress
            directPeerName = peerName
            logI("Direct peer call: name=$peerName ip=$peerIpAddress")
        }

        // For incoming calls, retrieve signaling socket from MeshService
        if (!isOutgoing) {
            signalingSocket = MeshService.getPendingCallSocket()
            logD("Retrieved signaling socket for incoming call: ${signalingSocket != null}")
        }

        logI("CallActivity onCreate: isOutgoing=$isOutgoing isVideoCall=$isVideoCall contactId=$contactId")

        // Initialize WebRTC
        // FIXED Jan 2026: Pass isVideoCall to RTCCall so it can enable camera at the right time
        rtcCall = RTCCall(this, isOutgoing, isVideoCall).apply {
            initialize()

            // Set up SDP callback for signaling
            onLocalDescription = { sdp ->
                logD("Local SDP ready: ${sdp.type}")
                if (isOutgoing) {
                    // For outgoing: send offer via mesh network
                    lifecycleScope.launch {
                        sendOfferToContact(contactId, sdp.description)
                    }
                } else {
                    // For incoming: send answer back via signaling socket
                    lifecycleScope.launch {
                        sendAnswerToRemote(sdp.description)
                    }
                }
            }

            // Set up remote video callback
            // VIDEO FIX Jan 2026: Store track if renderer not ready, attach later
            onRemoteVideoTrack = { videoTrack ->
                runOnUiThread {
                    try {
                        logD("Remote video track received")
                        // Always store the track (may need it later)
                        pendingRemoteVideoTrack = videoTrack

                        remoteRenderer?.let { renderer ->
                            // Renderer is ready - attach immediately
                            try {
                                videoTrack.addSink(renderer)
                                logI("Remote video sink added successfully")
                            } catch (e: Exception) {
                                logE("Failed to add remote video sink", e)
                            }
                        } ?: run {
                            // VIDEO FIX: Don't lose the track - it's stored in pendingRemoteVideoTrack
                            // Will be attached when onRemoteRendererReady is called
                            logW("Remote renderer not ready - track stored for later attachment")
                        }
                    } catch (e: Exception) {
                        logE("Error handling remote video track", e)
                    }
                }
            }
        }

        setContent {
            MeshRiderPremiumTheme(darkTheme = true) {
                val callState by rtcCall?.state?.collectAsState() ?: remember {
                    mutableStateOf(CallState())
                }

                // TIMER FIX Jan 2026: Force recomposition every second for timer display
                var timerTick by remember { mutableStateOf(0L) }
                LaunchedEffect(callState.isConnected) {
                    if (callState.isConnected) {
                        while (true) {
                            kotlinx.coroutines.delay(1000)
                            timerTick = System.currentTimeMillis()
                        }
                    }
                }
                // Use timerTick to trigger recomposition (read to force dependency)
                @Suppress("UNUSED_VARIABLE")
                val tick = timerTick

                // Show video only when camera is actually enabled and producing frames
                val showVideo = callState.isCameraEnabled && callState.isVideoReady

                val contactDisplayName = directPeerName ?: contactId ?: remoteAddress ?: "Unknown"

                // Predictive back gesture — confirm end call if connected
                var showEndCallDialog by remember { mutableStateOf(false) }
                BackHandler {
                    if (callState.isConnected) {
                        showEndCallDialog = true
                    } else {
                        // Not connected — just hang up
                        rtcCall?.hangup()
                        finish()
                    }
                }

                if (showEndCallDialog) {
                    AlertDialog(
                        onDismissRequest = { showEndCallDialog = false },
                        title = { Text("End Call?", color = PremiumColors.TextPrimary) },
                        text = { Text("Are you sure you want to end this call?", color = PremiumColors.TextSecondary) },
                        confirmButton = {
                            TextButton(onClick = {
                                showEndCallDialog = false
                                rtcCall?.hangup()
                                finish()
                            }) { Text("End Call", color = PremiumColors.NeonMagenta) }
                        },
                        dismissButton = {
                            TextButton(onClick = { showEndCallDialog = false }) {
                                Text("Cancel", color = PremiumColors.TextSecondary)
                            }
                        },
                        containerColor = PremiumColors.SpaceGray
                    )
                }

                // Notification lifecycle: show ongoing on connect, cancel incoming on answer
                LaunchedEffect(callState.isConnected) {
                    if (callState.isConnected) {
                        callNotificationManager.cancelIncomingNotification()
                        callNotificationManager.showOngoingCallNotification(contactDisplayName)
                    }
                }

                PremiumCallScreen(
                    callState = callState,
                    isOutgoing = isOutgoing,
                    contactName = contactDisplayName,
                    showVideo = showVideo,
                    eglContext = rtcCall?.eglBaseContext,
                    onLocalRendererReady = { renderer ->
                        localRenderer = renderer
                        rtcCall?.setLocalVideoRenderer(renderer)
                    },
                    onRemoteRendererReady = { renderer ->
                        remoteRenderer = renderer
                        pendingRemoteVideoTrack?.let { track ->
                            try {
                                track.addSink(renderer)
                                logI("Attached pending remote video track to renderer")
                            } catch (e: Exception) {
                                logE("Failed to attach pending remote video track", e)
                            }
                        }
                        rtcCall?.setVideoRenderers(localRenderer, remoteRenderer)
                    },
                    onToggleMic = { rtcCall?.setMicrophoneEnabled(!callState.isMicEnabled) },
                    onToggleCamera = { rtcCall?.setCameraEnabled(!callState.isCameraEnabled) },
                    onToggleSpeaker = {
                        // Use Telecom framework audio routing (proper API)
                        if (callState.isSpeakerEnabled) {
                            telecomCallManager.switchToEarpiece()
                        } else {
                            telecomCallManager.switchToSpeaker()
                        }
                        // Also update RTCCall state for UI
                        rtcCall?.setSpeakerEnabled(!callState.isSpeakerEnabled)
                    },
                    onSwitchCamera = { rtcCall?.switchCamera() },
                    onAccept = {
                        callNotificationManager.cancelIncomingNotification()
                        lifecycleScope.launch {
                            // RACE CONDITION FIX Feb 2026: Use suspend clear function
                            com.doodlelabs.meshriderwave.core.network.MeshNetworkManager.PendingCallStore.clearPendingCall()
                        }
                        rtcCall?.createPeerConnection(offer)
                    },
                    onDecline = {
                        callNotificationManager.cancelIncomingNotification()
                        lifecycleScope.launch {
                            // RACE CONDITION FIX Feb 2026: Use suspend clear function
                            com.doodlelabs.meshriderwave.core.network.MeshNetworkManager.PendingCallStore.clearPendingCall()
                            declineCall()
                        }
                        finish()
                    },
                    onHangup = {
                        rtcCall?.hangup()
                        finish()
                    }
                )

                // Handle call ended or error
                // FIXED Jan 2026: Also handle ERROR status to properly close activity
                LaunchedEffect(callState.status) {
                    when (callState.status) {
                        CallState.Status.ENDED -> {
                            finish()
                        }
                        CallState.Status.ERROR -> {
                            // Error is shown in UI, activity will be finished by handleCallError()
                            // after a delay to let user see the error message
                            logD("Call error state: ${callState.errorMessage}")
                        }
                        else -> { /* Continue with call */ }
                    }
                }
            }
        }

        // Start call if outgoing
        if (isOutgoing) {
            rtcCall?.createPeerConnection()
            // Camera is now enabled inside RTCCall.addMediaTracks() for video calls
        }
    }

    /**
     * Send SDP offer to contact via mesh network
     * FIXED Jan 2026: Proper error handling to prevent crashes
     * UPDATED Jan 2026: Support direct peer calling without saved contact
     */
    private suspend fun sendOfferToContact(contactId: String?, offerSdp: String) {
        // Determine contact - either from saved contacts or create temporary for direct peer
        val contact: com.doodlelabs.meshriderwave.domain.model.Contact

        if (directPeerPublicKey != null && directPeerIpAddress != null) {
            // Direct peer calling - create temporary Contact object
            logI("sendOfferToContact: DIRECT PEER CALL to ${directPeerName}")
            contact = com.doodlelabs.meshriderwave.domain.model.Contact(
                publicKey = directPeerPublicKey!!,
                name = directPeerName ?: "Unknown Peer",
                addresses = listOf(directPeerIpAddress!!),
                lastWorkingAddress = directPeerIpAddress,
                createdAt = System.currentTimeMillis(),
                lastSeenAt = System.currentTimeMillis()
            )
        } else if (contactId != null) {
            // Traditional contact-based calling
            val savedContact = contactRepository.getContactByDeviceId(contactId)
            if (savedContact == null) {
                logE("sendOfferToContact: contact not found for $contactId")
                handleCallError("Contact not found")
                return
            }
            contact = savedContact
        } else {
            logE("sendOfferToContact: no contact ID or direct peer info")
            handleCallError("Invalid contact")
            return
        }

        // Debug logging for connection issues
        logI("sendOfferToContact: initiating call to ${contact.name}")
        logI("sendOfferToContact: contact addresses = ${contact.addresses}")
        logI("sendOfferToContact: lastWorkingAddress = ${contact.lastWorkingAddress}")

        try {
            when (val result = meshNetworkManager.initiateCall(contact, offerSdp)) {
                is MeshNetworkManager.CallResult.Connected -> {
                    logI("sendOfferToContact: call connected, received answer")
                    signalingSocket = result.socket
                    rtcCall?.handleAnswer(result.answer)
                }
                is MeshNetworkManager.CallResult.Declined -> {
                    logI("sendOfferToContact: call declined")
                    handleCallError("Call declined")
                }
                is MeshNetworkManager.CallResult.Error -> {
                    logE("sendOfferToContact: error - ${result.message}")
                    handleCallError(result.message)
                }
            }
        } catch (e: Exception) {
            logE("sendOfferToContact: exception", e)
            handleCallError("Connection failed: ${e.message}")
        }
    }

    /**
     * Handle call error - update state and finish activity
     * FIXED Jan 2026: Ensures proper cleanup without crashing
     */
    private fun handleCallError(errorMessage: String) {
        logE("handleCallError: $errorMessage")
        runOnUiThread {
            // Update state to show error briefly, then finish
            rtcCall?.setError(errorMessage)
            // Give UI time to show error message before finishing
            window.decorView.postDelayed({
                rtcCall?.cleanup()
                finish()
            }, 2000) // Show error for 2 seconds
        }
    }

    /**
     * Send SDP answer back to caller via signaling socket
     */
    private suspend fun sendAnswerToRemote(answerSdp: String) {
        val socket = signalingSocket
        val senderKey = remoteSenderKey

        if (socket != null && senderKey != null) {
            meshNetworkManager.sendCallResponse(socket, senderKey, answerSdp)
            logI("sendAnswerToRemote: answer sent")
        } else {
            logE("sendAnswerToRemote: no signaling socket or sender key")
        }
    }

    /**
     * Decline incoming call
     */
    private suspend fun declineCall() {
        val socket = signalingSocket
        val senderKey = remoteSenderKey

        if (socket != null && senderKey != null) {
            meshNetworkManager.sendCallResponse(socket, senderKey, null) // null = declined
            logI("declineCall: sent decline response")
        }
    }

    /**
     * Set signaling socket for incoming call (called from MeshService)
     */
    fun setSignalingSocket(socket: Socket) {
        signalingSocket = socket
    }

    /**
     * Handle new intent from notification actions (answer button).
     *
     * Per developer.android.com, onNewIntent is called when activity
     * is already running and receives a new intent (e.g., from notification).
     *
     * This handles the case where user taps "Answer" on incoming notification
     * while CallActivity is already displayed (showing the incoming call UI).
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        logI("onNewIntent: action=${intent.action}")

        // Handle ACTION_ANSWER from notification
        if (intent.action == CallNotificationManager.ACTION_ANSWER) {
            logI("onNewIntent: processing answer action from notification")
            handleAnswerAction(intent)
        }
    }

    /**
     * Process answer action from notification.
     * Extracts offer and creates peer connection to answer the call.
     */
    private fun handleAnswerAction(intent: Intent) {
        val offer = intent.getStringExtra(EXTRA_OFFER)
        val remoteAddress = intent.getStringExtra(EXTRA_REMOTE_ADDRESS)
        val senderKeyBase64 = intent.getStringExtra(EXTRA_SENDER_KEY)

        logI("handleAnswerAction: offer=${offer != null} remoteAddress=$remoteAddress")

        // Update sender key if provided
        senderKeyBase64?.let {
            remoteSenderKey = android.util.Base64.decode(it, android.util.Base64.NO_WRAP)
        }

        // Cancel incoming notification
        callNotificationManager.cancelIncomingNotification()

        // RACE CONDITION FIX Feb 2026: Use suspend clear function
        lifecycleScope.launch {
            com.doodlelabs.meshriderwave.core.network.MeshNetworkManager.PendingCallStore.clearPendingCall()
        }

        // Create peer connection with the offer
        if (offer != null) {
            rtcCall?.createPeerConnection(offer)
        } else {
            logE("handleAnswerAction: no offer in intent")
        }
    }

    override fun onDestroy() {
        // Unregister hangup broadcast receiver
        try {
            unregisterReceiver(hangupReceiver)
        } catch (e: Exception) {
            logE("Error unregistering hangup receiver", e)
        }

        // Cancel all call notifications
        callNotificationManager.cancelAll()

        // FIXED Jan 2026: Proper cleanup order - RTCCall first, then renderers
        // This prevents "frame delivered to released renderer" crashes

        // 1. Cleanup RTCCall first (stops delivering frames to sinks)
        rtcCall?.cleanup()
        rtcCall = null

        // 2. Then release renderers (safe now that no frames are being delivered)
        try {
            localRenderer?.release()
        } catch (e: Exception) {
            logE("Error releasing local renderer", e)
        }
        try {
            remoteRenderer?.release()
        } catch (e: Exception) {
            logE("Error releasing remote renderer", e)
        }
        localRenderer = null
        remoteRenderer = null

        // 3. Close signaling socket
        try {
            signalingSocket?.close()
        } catch (e: Exception) {
            logE("Error closing signaling socket", e)
        }
        signalingSocket = null

        super.onDestroy()
    }

    companion object {
        const val EXTRA_CONTACT_ID = "contact_id"
        const val EXTRA_IS_OUTGOING = "is_outgoing"
        const val EXTRA_IS_VIDEO_CALL = "is_video_call"
        const val EXTRA_REMOTE_ADDRESS = "remote_address"
        const val EXTRA_OFFER = "offer"
        const val EXTRA_SENDER_KEY = "sender_key"
        // Direct peer calling (Jan 2026) - for discovered peers without saved contact
        const val EXTRA_PEER_PUBLIC_KEY = "peer_public_key"
        const val EXTRA_PEER_IP_ADDRESS = "peer_ip_address"
        const val EXTRA_PEER_NAME = "peer_name"
    }
}

/**
 * Premium Call Screen with video rendering and glassmorphism
 */
@Composable
fun PremiumCallScreen(
    callState: CallState,
    isOutgoing: Boolean,
    contactName: String,
    showVideo: Boolean,
    eglContext: EglBase.Context?,
    onLocalRendererReady: (SurfaceViewRenderer) -> Unit,
    onRemoteRendererReady: (SurfaceViewRenderer) -> Unit,
    onToggleMic: () -> Unit,
    onToggleCamera: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onSwitchCamera: () -> Unit,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onHangup: () -> Unit
) {
    val isConnecting = callState.status in listOf(
        CallState.Status.INITIATING,
        CallState.Status.RINGING,
        CallState.Status.CONNECTING
    )

    val isRinging = callState.status == CallState.Status.RINGING ||
            (!isOutgoing && callState.status == CallState.Status.IDLE)

    Box(modifier = Modifier.fillMaxSize()) {
        // Background layer
        if (callState.isConnected && showVideo) {
            // Remote video as background
            RemoteVideoRenderer(
                eglContext = eglContext,
                onRendererReady = onRemoteRendererReady,
                modifier = Modifier.fillMaxSize()
            )
        } else if (callState.isConnected) {
            AuroraBackground { }
        } else {
            PulsingGlowBackground(
                glowColor = if (isRinging) PremiumColors.LaserLime else PremiumColors.ElectricCyan
            ) { }
        }

        // Local video PiP (draggable)
        // VIDEO PREVIEW FIX Jan 2026: Show preview when video is ready (don't wait for camera enable)
        // showVideo is already true for video calls, just need isVideoReady and eglContext
        if (showVideo && callState.isVideoReady && eglContext != null) {
            DraggableLocalVideo(
                eglContext = eglContext,
                onRendererReady = onLocalRendererReady,
                modifier = Modifier  // No alignment - DraggableLocalVideo calculates its own position
            )
        }

        // Call UI overlay
        CallContent(
            callState = callState,
            isOutgoing = isOutgoing,
            contactName = contactName,
            isConnecting = isConnecting,
            isRinging = isRinging,
            showVideoControls = showVideo,
            onToggleMic = onToggleMic,
            onToggleCamera = onToggleCamera,
            onToggleSpeaker = onToggleSpeaker,
            onSwitchCamera = onSwitchCamera,
            onAccept = onAccept,
            onDecline = onDecline,
            onHangup = onHangup
        )
    }
}

/**
 * Remote video renderer (full screen background)
 */
@Composable
fun RemoteVideoRenderer(
    eglContext: EglBase.Context?,
    onRendererReady: (SurfaceViewRenderer) -> Unit,
    modifier: Modifier = Modifier
) {
    if (eglContext == null) return

    var rendererInitialized by remember { mutableStateOf(false) }

    AndroidView(
        factory = { context ->
            SurfaceViewRenderer(context).apply {
                try {
                    setMirror(false)
                    setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                    setEnableHardwareScaler(true)
                    init(eglContext, object : RendererCommon.RendererEvents {
                        override fun onFirstFrameRendered() {
                            // Frame successfully rendered
                        }
                        override fun onFrameResolutionChanged(videoWidth: Int, videoHeight: Int, rotation: Int) {
                            // Resolution changed
                        }
                    })
                    rendererInitialized = true
                    onRendererReady(this)
                } catch (e: Exception) {
                    // Log but don't crash
                    e.printStackTrace()
                }
            }
        },
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    )
}

/**
 * Draggable local video PiP window
 */
@Composable
fun DraggableLocalVideo(
    eglContext: EglBase.Context,
    onRendererReady: (SurfaceViewRenderer) -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current

    val screenWidth = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeight = with(density) { configuration.screenHeightDp.dp.toPx() }

    val pipWidth = 120.dp
    val pipHeight = 160.dp

    var offsetX by remember { mutableFloatStateOf(screenWidth - with(density) { pipWidth.toPx() } - 24f) }
    var offsetY by remember { mutableFloatStateOf(100f) }

    Box(
        modifier = modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .size(pipWidth, pipHeight)
            .clip(RoundedCornerShape(16.dp))
            .border(2.dp, PremiumColors.ElectricCyan.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    offsetX = (offsetX + dragAmount.x).coerceIn(
                        0f,
                        screenWidth - with(density) { pipWidth.toPx() }
                    )
                    offsetY = (offsetY + dragAmount.y).coerceIn(
                        0f,
                        screenHeight - with(density) { pipHeight.toPx() } - 200f
                    )
                }
            }
    ) {
        AndroidView(
            factory = { context ->
                SurfaceViewRenderer(context).apply {
                    try {
                        setMirror(true) // Mirror for selfie view
                        setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                        setEnableHardwareScaler(true)
                        init(eglContext, object : RendererCommon.RendererEvents {
                            override fun onFirstFrameRendered() {}
                            override fun onFrameResolutionChanged(videoWidth: Int, videoHeight: Int, rotation: Int) {}
                        })
                        onRendererReady(this)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun CallContent(
    callState: CallState,
    isOutgoing: Boolean,
    contactName: String,
    isConnecting: Boolean,
    isRinging: Boolean,
    showVideoControls: Boolean,
    onToggleMic: () -> Unit,
    onToggleCamera: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onSwitchCamera: () -> Unit,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onHangup: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .systemBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(80.dp))

        // Avatar with animation (hide when video is showing)
        AnimatedVisibility(
            visible = !callState.isConnected || !callState.isCameraEnabled,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (isConnecting) {
                    Box(contentAlignment = Alignment.Center) {
                        CallConnectingLoader(size = 160.dp)
                        CallAvatar(
                            name = contactName,
                            isRinging = false,
                            isConnected = false
                        )
                    }
                } else {
                    CallAvatar(
                        name = contactName,
                        isRinging = isRinging,
                        isConnected = callState.isConnected
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Contact name
                Text(
                    text = contactName,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = PremiumColors.TextPrimary
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Call status
                CallStatusText(callState = callState, isOutgoing = isOutgoing)
            }
        }

        // Duration badge (when connected)
        AnimatedVisibility(
            visible = callState.isConnected,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(modifier = Modifier.height(16.dp))
                CallDurationBadge(duration = callState.durationFormatted)
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Floating call controls (WhatsApp 2025 style)
        AnimatedVisibility(
            visible = callState.isActive || isOutgoing,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it }
        ) {
            FloatingCallControls(
                callState = callState,
                showVideoControls = showVideoControls,
                onToggleMic = onToggleMic,
                onToggleCamera = onToggleCamera,
                onToggleSpeaker = onToggleSpeaker,
                onSwitchCamera = onSwitchCamera,
                onHangup = onHangup
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Incoming call: Swipe to answer/decline
        // Outgoing/connected: no action buttons (hangup is in FloatingCallControls)
        if (!isOutgoing && callState.status == CallState.Status.IDLE) {
            SwipeToAnswerButton(
                onAccept = onAccept,
                onDecline = onDecline,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        Spacer(modifier = Modifier.height(48.dp))
    }
}

/**
 * Call status text with styling
 * UPDATED Jan 2026: Enterprise reconnection info with countdown
 */
@Composable
private fun CallStatusText(
    callState: CallState,
    isOutgoing: Boolean
) {
    // For reconnecting state, show countdown timer
    var reconnectSecondsRemaining by remember { mutableStateOf(callState.reconnectSecondsRemaining) }

    // Update countdown every second when reconnecting
    LaunchedEffect(callState.isReconnecting) {
        if (callState.isReconnecting) {
            while (true) {
                reconnectSecondsRemaining = callState.reconnectSecondsRemaining
                kotlinx.coroutines.delay(1000)
            }
        }
    }

    val (statusText, statusColor) = when (callState.status) {
        CallState.Status.IDLE -> {
            if (isOutgoing) "Calling..." to PremiumColors.TextSecondary
            else "Incoming Call" to PremiumColors.LaserLime
        }
        CallState.Status.INITIATING -> "Connecting..." to PremiumColors.ConnectingAmber
        CallState.Status.RINGING -> "Ringing..." to PremiumColors.ElectricCyan
        CallState.Status.CONNECTING -> "Establishing connection..." to PremiumColors.ConnectingAmber
        CallState.Status.CONNECTED -> "Connected" to PremiumColors.OnlineGlow
        CallState.Status.RECONNECTING -> {
            val attemptText = if (callState.reconnectAttempt > 0) {
                " (Attempt ${callState.reconnectAttempt}/${callState.maxReconnectAttempts})"
            } else ""
            "Reconnecting...$attemptText" to PremiumColors.ConnectingAmber
        }
        CallState.Status.ENDED -> "Call Ended" to PremiumColors.TextSecondary
        CallState.Status.ERROR -> (callState.errorMessage ?: "Error") to PremiumColors.BusyRed
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Main status text
        Text(
            text = statusText,
            style = MaterialTheme.typography.titleMedium,
            color = statusColor,
            fontWeight = FontWeight.Medium
        )

        // Show countdown when reconnecting (Enterprise feature Jan 2026)
        if (callState.isReconnecting && reconnectSecondsRemaining > 0) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Call will end in ${reconnectSecondsRemaining}s",
                style = MaterialTheme.typography.bodySmall,
                color = PremiumColors.TextSecondary
            )
        }

        // Show network quality indicator when connected (like WhatsApp signal bars)
        if (callState.isConnected) {
            Spacer(modifier = Modifier.height(8.dp))
            NetworkQualityIndicator(quality = callState.networkQuality)
        }

        // Show poor connection warning
        if (callState.shouldShowPoorConnectionWarning && callState.isConnected) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Poor connection",
                style = MaterialTheme.typography.bodySmall,
                color = PremiumColors.ConnectingAmber
            )
        }
    }
}

/**
 * Network quality indicator with signal bars (like WhatsApp/Zoom)
 * Enterprise feature Jan 2026
 */
@Composable
private fun NetworkQualityIndicator(quality: CallState.NetworkQuality) {
    val bars = when (quality) {
        CallState.NetworkQuality.EXCELLENT -> 4
        CallState.NetworkQuality.GOOD -> 3
        CallState.NetworkQuality.FAIR -> 2
        CallState.NetworkQuality.POOR -> 1
        CallState.NetworkQuality.BAD, CallState.NetworkQuality.UNKNOWN -> 0
    }

    val color = when (quality) {
        CallState.NetworkQuality.EXCELLENT, CallState.NetworkQuality.GOOD -> PremiumColors.OnlineGlow
        CallState.NetworkQuality.FAIR -> PremiumColors.ConnectingAmber
        CallState.NetworkQuality.POOR, CallState.NetworkQuality.BAD -> PremiumColors.BusyRed
        CallState.NetworkQuality.UNKNOWN -> PremiumColors.TextSecondary
    }

    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        repeat(4) { index ->
            val barHeight = (8 + index * 4).dp
            val isActive = index < bars
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(barHeight)
                    .background(
                        color = if (isActive) color else color.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(2.dp)
                    )
            )
        }
    }
}

/**
 * Premium call control buttons
 */
@Composable
private fun PremiumCallControls(
    callState: CallState,
    showVideoControls: Boolean,
    onToggleMic: () -> Unit,
    onToggleCamera: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onSwitchCamera: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        // Mute button
        PremiumCallControlButton(
            icon = if (callState.isMicEnabled) Icons.Filled.Mic else Icons.Filled.MicOff,
            label = if (callState.isMicEnabled) "Mute" else "Unmute",
            isActive = !callState.isMicEnabled,
            onClick = onToggleMic
        )

        // Camera button
        PremiumCallControlButton(
            icon = if (callState.isCameraEnabled) Icons.Filled.Videocam else Icons.Filled.VideocamOff,
            label = "Camera",
            isActive = callState.isCameraEnabled,
            onClick = onToggleCamera
        )

        // Speaker button
        PremiumCallControlButton(
            icon = if (callState.isSpeakerEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeDown,
            label = "Speaker",
            isActive = callState.isSpeakerEnabled,
            onClick = onToggleSpeaker
        )

        // Switch camera (only if camera is on)
        if (callState.isCameraEnabled) {
            PremiumCallControlButton(
                icon = Icons.Filled.Cameraswitch,
                label = "Switch",
                isActive = false,
                onClick = onSwitchCamera
            )
        }
    }
}

/**
 * Premium call control button
 */
@Composable
private fun PremiumCallControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isActive: Boolean = false,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        NeumorphicToggleButton(
            onClick = onClick,
            icon = icon,
            size = 56.dp,
            isActive = isActive,
            activeColor = PremiumColors.ElectricCyan,
            inactiveColor = PremiumColors.SpaceGrayLighter
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isActive) PremiumColors.ElectricCyan else PremiumColors.TextSecondary,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Call action buttons (accept/decline/hangup)
 */
@Composable
private fun CallActionButtons(
    callState: CallState,
    isOutgoing: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onHangup: () -> Unit
) {
    if (!isOutgoing && callState.status == CallState.Status.IDLE) {
        // Incoming call - show accept/decline
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Decline button
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CallActionButton(
                    onClick = onDecline,
                    icon = Icons.Filled.CallEnd,
                    isAccept = false
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Decline",
                    style = MaterialTheme.typography.labelMedium,
                    color = PremiumColors.NeonMagenta,
                    fontWeight = FontWeight.Medium
                )
            }

            // Accept button
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CallActionButton(
                    onClick = onAccept,
                    icon = Icons.Filled.Call,
                    isAccept = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Accept",
                    style = MaterialTheme.typography.labelMedium,
                    color = PremiumColors.LaserLime,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    } else {
        // Outgoing or connected - show hangup
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CallActionButton(
                onClick = onHangup,
                icon = Icons.Filled.CallEnd,
                isAccept = false,
                size = 72.dp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "End Call",
                style = MaterialTheme.typography.labelMedium,
                color = PremiumColors.NeonMagenta,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
