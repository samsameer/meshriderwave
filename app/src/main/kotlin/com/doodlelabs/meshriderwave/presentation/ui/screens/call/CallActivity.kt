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
import androidx.activity.ComponentActivity
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
import com.doodlelabs.meshriderwave.core.network.MeshService
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

    private var rtcCall: RTCCall? = null
    private var localRenderer: SurfaceViewRenderer? = null
    private var remoteRenderer: SurfaceViewRenderer? = null

    // Signaling socket for incoming calls
    private var signalingSocket: Socket? = null
    private var remoteSenderKey: ByteArray? = null

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

        val isOutgoing = intent.getBooleanExtra(EXTRA_IS_OUTGOING, true)
        val contactId = intent.getStringExtra(EXTRA_CONTACT_ID)
        val remoteAddress = intent.getStringExtra(EXTRA_REMOTE_ADDRESS)
        val offer = intent.getStringExtra(EXTRA_OFFER)
        val senderKeyBase64 = intent.getStringExtra(EXTRA_SENDER_KEY)

        // Parse sender key for incoming calls
        senderKeyBase64?.let {
            remoteSenderKey = android.util.Base64.decode(it, android.util.Base64.NO_WRAP)
        }

        // For incoming calls, retrieve signaling socket from MeshService
        if (!isOutgoing) {
            signalingSocket = MeshService.getPendingCallSocket()
            logD("Retrieved signaling socket for incoming call: ${signalingSocket != null}")
        }

        logI("CallActivity onCreate: isOutgoing=$isOutgoing contactId=$contactId")

        // Initialize WebRTC
        rtcCall = RTCCall(this, isOutgoing).apply {
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
            onRemoteVideoTrack = { videoTrack ->
                runOnUiThread {
                    try {
                        logD("Remote video track received")
                        remoteRenderer?.let { renderer ->
                            // Only add sink if renderer is initialized
                            try {
                                videoTrack.addSink(renderer)
                                logI("Remote video sink added successfully")
                            } catch (e: Exception) {
                                logE("Failed to add remote video sink", e)
                            }
                        } ?: logW("Remote renderer not ready yet")
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

                // Track video enabled state
                val showVideo = callState.isCameraEnabled || callState.isConnected

                PremiumCallScreen(
                    callState = callState,
                    isOutgoing = isOutgoing,
                    contactName = contactId ?: remoteAddress ?: "Unknown",
                    showVideo = showVideo,
                    eglContext = rtcCall?.eglBaseContext,
                    onLocalRendererReady = { renderer ->
                        localRenderer = renderer
                        rtcCall?.getLocalVideoTrack()?.addSink(renderer)
                    },
                    onRemoteRendererReady = { renderer ->
                        remoteRenderer = renderer
                        rtcCall?.setVideoRenderers(localRenderer, remoteRenderer)
                    },
                    onToggleMic = { rtcCall?.setMicrophoneEnabled(!callState.isMicEnabled) },
                    onToggleCamera = { rtcCall?.setCameraEnabled(!callState.isCameraEnabled) },
                    onToggleSpeaker = { rtcCall?.setSpeakerEnabled(!callState.isSpeakerEnabled) },
                    onSwitchCamera = { rtcCall?.switchCamera() },
                    onAccept = {
                        // Accept incoming call - create peer connection with received offer
                        rtcCall?.createPeerConnection(offer)
                    },
                    onDecline = {
                        // Decline incoming call - notify remote and close
                        lifecycleScope.launch {
                            declineCall()
                        }
                        finish()
                    },
                    onHangup = {
                        rtcCall?.hangup()
                        finish()
                    }
                )

                // Handle call ended
                LaunchedEffect(callState.status) {
                    if (callState.status == CallState.Status.ENDED) {
                        finish()
                    }
                }
            }
        }

        // Start call if outgoing
        if (isOutgoing) {
            rtcCall?.createPeerConnection()
        }
    }

    /**
     * Send SDP offer to contact via mesh network
     */
    private suspend fun sendOfferToContact(contactId: String?, offerSdp: String) {
        if (contactId == null) {
            logE("sendOfferToContact: contactId is null")
            return
        }

        // Look up contact
        val contact = contactRepository.getContactByDeviceId(contactId)
        if (contact == null) {
            logE("sendOfferToContact: contact not found for $contactId")
            return
        }

        logD("sendOfferToContact: initiating call to ${contact.name}")

        when (val result = meshNetworkManager.initiateCall(contact, offerSdp)) {
            is MeshNetworkManager.CallResult.Connected -> {
                logI("sendOfferToContact: call connected, received answer")
                signalingSocket = result.socket
                rtcCall?.handleAnswer(result.answer)
            }
            is MeshNetworkManager.CallResult.Declined -> {
                logI("sendOfferToContact: call declined")
                rtcCall?.hangup()
            }
            is MeshNetworkManager.CallResult.Error -> {
                logE("sendOfferToContact: error - ${result.message}")
                rtcCall?.hangup()
            }
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

    override fun onDestroy() {
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
        const val EXTRA_REMOTE_ADDRESS = "remote_address"
        const val EXTRA_OFFER = "offer"
        const val EXTRA_SENDER_KEY = "sender_key"
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
        if (showVideo && eglContext != null && callState.isCameraEnabled) {
            DraggableLocalVideo(
                eglContext = eglContext,
                onRendererReady = onLocalRendererReady,
                modifier = Modifier.align(Alignment.TopEnd)
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
        Spacer(modifier = Modifier.height(60.dp))

        // Encryption badge
        EncryptionBadge(isEncrypted = true)

        Spacer(modifier = Modifier.height(40.dp))

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

        // Control buttons (when connected or outgoing)
        AnimatedVisibility(
            visible = callState.isActive || isOutgoing,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it }
        ) {
            PremiumCallControls(
                callState = callState,
                showVideoControls = showVideoControls,
                onToggleMic = onToggleMic,
                onToggleCamera = onToggleCamera,
                onToggleSpeaker = onToggleSpeaker,
                onSwitchCamera = onSwitchCamera
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Accept/Decline or Hangup buttons
        CallActionButtons(
            callState = callState,
            isOutgoing = isOutgoing,
            onAccept = onAccept,
            onDecline = onDecline,
            onHangup = onHangup
        )

        Spacer(modifier = Modifier.height(48.dp))
    }
}

/**
 * Call status text with styling
 */
@Composable
private fun CallStatusText(
    callState: CallState,
    isOutgoing: Boolean
) {
    val (statusText, statusColor) = when (callState.status) {
        CallState.Status.IDLE -> {
            if (isOutgoing) "Calling..." to PremiumColors.TextSecondary
            else "Incoming Call" to PremiumColors.LaserLime
        }
        CallState.Status.INITIATING -> "Connecting..." to PremiumColors.ConnectingAmber
        CallState.Status.RINGING -> "Ringing..." to PremiumColors.ElectricCyan
        CallState.Status.CONNECTING -> "Establishing connection..." to PremiumColors.ConnectingAmber
        CallState.Status.CONNECTED -> "Connected" to PremiumColors.OnlineGlow
        CallState.Status.RECONNECTING -> "Reconnecting..." to PremiumColors.ConnectingAmber
        CallState.Status.ENDED -> "Call Ended" to PremiumColors.TextSecondary
        CallState.Status.ERROR -> (callState.errorMessage ?: "Error") to PremiumColors.BusyRed
    }

    Text(
        text = statusText,
        style = MaterialTheme.typography.titleMedium,
        color = statusColor,
        fontWeight = FontWeight.Medium
    )
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
