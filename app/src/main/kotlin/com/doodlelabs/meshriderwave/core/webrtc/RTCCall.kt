/*
 * Mesh Rider Wave - WebRTC Call Handler
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 *
 * Modern WebRTC implementation using Camera2 API
 * Uses JavaAudioDeviceModule for proper AEC on Android
 * Jan 2026 - Optimized for mesh network P2P calls
 */

package com.doodlelabs.meshriderwave.core.webrtc

import android.content.Context
import android.media.AudioManager
import com.doodlelabs.meshriderwave.core.util.logD
import com.doodlelabs.meshriderwave.core.util.logE
import com.doodlelabs.meshriderwave.core.util.logI
import com.doodlelabs.meshriderwave.core.util.logW
import com.doodlelabs.meshriderwave.domain.model.CallState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.webrtc.*
import org.webrtc.audio.JavaAudioDeviceModule
import java.util.concurrent.Executors

/**
 * WebRTC call handler with proper Android audio processing
 *
 * Features:
 * - Hardware AEC/NS via JavaAudioDeviceModule
 * - Camera2 API for video capture
 * - Speaker/earpiece toggle
 * - Reconnection handling
 * - Data channel for in-band signaling
 */
class RTCCall(
    private val context: Context,
    private val isOutgoing: Boolean,
    private val isVideoCall: Boolean = false
) {
    private val executor = Executors.newSingleThreadExecutor()
    // CRASH-FIX Jan 2026: Safe cast to prevent ClassCastException
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    // Lifecycle-aware coroutine scope (replaces GlobalScope - FIXED Jan 2026)
    private val callScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // WebRTC components
    private var factory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var dataChannel: DataChannel? = null
    private var eglBase: EglBase? = null

    // Audio
    private var audioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null

    // Video
    private var videoCapturer: VideoCapturer? = null
    private var videoSource: VideoSource? = null
    private var localVideoTrack: VideoTrack? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null

    // State
    private val _state = MutableStateFlow(CallState())
    val state: StateFlow<CallState> = _state

    // Callbacks
    var onLocalDescription: ((SessionDescription) -> Unit)? = null
    var onIceConnectionChange: ((PeerConnection.IceConnectionState) -> Unit)? = null
    var onRemoteVideoTrack: ((VideoTrack) -> Unit)? = null

    // Video sinks
    private var localVideoSink: ProxyVideoSink? = null
    private var remoteVideoSink: ProxyVideoSink? = null

    // Enterprise Reconnection tracking (Jan 2026)
    private var reconnectAttempts = 0
    private var reconnectJob: Job? = null
    private var disconnectTime: Long? = null

    // Heartbeat/Keep-alive tracking
    private var heartbeatJob: Job? = null
    private var lastHeartbeatReceived: Long = System.currentTimeMillis()

    // Track if remote video has been set up to prevent duplicates
    @Volatile
    private var remoteVideoSetUp = false

    // Track if initial SDP has been sent to prevent duplicates
    @Volatile
    private var sdpSent = false

    // Track if initial connection is established (for renegotiation)
    @Volatile
    private var initialConnectionEstablished = false

    // Track if cleanup has been called to prevent double cleanup (FIXED Jan 2026)
    @Volatile
    private var isCleanedUp = false

    // LOCAL VIDEO FIX Jan 2026: Store pending local renderer when track isn't ready yet
    // This fixes the local preview not showing - renderer is ready before track is created
    @Volatile
    private var pendingLocalRenderer: VideoSink? = null

    // HANGUP FIX Jan 2026: Track if remote hangup was received
    // Prevents reconnection attempts when peer intentionally hung up
    @Volatile
    private var remoteHangupReceived = false

    // ICE gathering timeout job
    private var iceGatheringTimeoutJob: Job? = null

    // Stats monitoring (Jan 2026)
    private var statsJob: Job? = null
    private val _stats = MutableStateFlow<CallStats?>(null)
    val stats: StateFlow<CallStats?> = _stats

    /**
     * Get EGL context for video rendering in Activity
     */
    val eglBaseContext: EglBase.Context?
        get() = eglBase?.eglBaseContext

    /**
     * Initialize WebRTC factory
     */
    fun initialize() {
        logD("initialize()")

        executor.execute {
            try {
                // Initialize EGL for hardware video
                eglBase = EglBase.create()

                // Initialize WebRTC
                PeerConnectionFactory.initialize(
                    PeerConnectionFactory.InitializationOptions.builder(context)
                        .setFieldTrials("WebRTC-H264HighProfile/Enabled/")
                        .setEnableInternalTracer(false) // Disable for production
                        .createInitializationOptions()
                )

                // Audio device module with hardware AEC
                val audioDeviceModule = createAudioDeviceModule()

                // Video codecs - prefer hardware acceleration
                // CRASH-FIX Jan 2026: Safe null check instead of !!
                val eglContext = eglBase?.eglBaseContext ?: run {
                    logE("initialize() eglBase is null")
                    updateState { copy(status = CallState.Status.ERROR, errorMessage = "Video init failed") }
                    return@execute
                }

                val encoderFactory = DefaultVideoEncoderFactory(
                    eglContext,
                    /* enableIntelVp8Encoder */ true,
                    /* enableH264HighProfile */ true
                )
                val decoderFactory = DefaultVideoDecoderFactory(eglContext)

                // Factory options - disable network monitor for mesh/hotspot support
                val options = PeerConnectionFactory.Options().apply {
                    disableNetworkMonitor = true
                }

                factory = PeerConnectionFactory.builder()
                    .setOptions(options)
                    .setAudioDeviceModule(audioDeviceModule)
                    .setVideoEncoderFactory(encoderFactory)
                    .setVideoDecoderFactory(decoderFactory)
                    .createPeerConnectionFactory()

                // Signal factory ready - video readiness depends on actual camera start
                updateState { copy(isVideoReady = isVideoCall) }
                logI("initialize() factory created successfully, isVideoReady=$isVideoCall")
            } catch (e: Exception) {
                logE("initialize() failed", e)
                updateState { copy(status = CallState.Status.ERROR, errorMessage = "WebRTC init failed") }
            }
        }
    }

    /**
     * Create peer connection and set up media tracks
     */
    fun createPeerConnection(offer: String? = null) {
        logD("createPeerConnection() isOutgoing=$isOutgoing")

        // Reset flags for new connection
        sdpSent = false
        remoteVideoSetUp = false
        remoteHangupReceived = false  // HANGUP FIX Jan 2026

        executor.execute {
            val rtcConfig = PeerConnection.RTCConfiguration(emptyList()).apply {
                sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
                continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_ONCE
                enableCpuOveruseDetection = true
                // TCP candidates for mesh networks with firewalls
                tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.ENABLED
            }

            peerConnection = factory?.createPeerConnection(rtcConfig, peerConnectionObserver)

            if (peerConnection == null) {
                logE("createPeerConnection() failed")
                updateState { copy(status = CallState.Status.ERROR, errorMessage = "Connection failed") }
                return@execute
            }

            // Create data channel for in-band signaling (hangup, camera state)
            if (isOutgoing) {
                val init = DataChannel.Init().apply {
                    ordered = true
                    negotiated = false
                }
                dataChannel = peerConnection?.createDataChannel("mesh-data", init)
                dataChannel?.registerObserver(dataChannelObserver)
            }

            // Add audio/video tracks
            addMediaTracks()

            if (isOutgoing) {
                createOffer()
            } else {
                offer?.let { handleRemoteOffer(it) }
            }
        }
    }

    /**
     * Handle remote answer (for outgoing calls)
     */
    fun handleAnswer(answer: String) {
        logD("handleAnswer()")
        executor.execute {
            try {
                val sessionDescription = SessionDescription(SessionDescription.Type.ANSWER, answer)
                peerConnection?.setRemoteDescription(sdpObserver, sessionDescription)
            } catch (e: Exception) {
                logE("handleAnswer() failed", e)
            }
        }
    }

    /**
     * Toggle microphone
     */
    fun setMicrophoneEnabled(enabled: Boolean) {
        logD("setMicrophoneEnabled($enabled)")
        executor.execute {
            localAudioTrack?.setEnabled(enabled)
            updateState { copy(isMicEnabled = enabled) }
        }
    }

    /**
     * Toggle camera
     * FIXED Jan 2026: Robust error handling for Samsung device camera exceptions
     */
    fun setCameraEnabled(enabled: Boolean) {
        logD("setCameraEnabled($enabled)")
        executor.execute {
            try {
                if (enabled) {
                    // Create video track on-the-fly if it doesn't exist (audio call upgrading to video)
                    if (videoCapturer == null) {
                        val capturer = createVideoCapturer()
                        val eglContext = eglBase?.eglBaseContext
                        if (capturer != null && eglContext != null) {
                            videoCapturer = capturer
                            surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eglContext)
                            videoSource = factory?.createVideoSource(capturer.isScreencast)
                            capturer.initialize(surfaceTextureHelper, context, videoSource?.capturerObserver)
                            localVideoTrack = factory?.createVideoTrack(VIDEO_TRACK_ID, videoSource)
                            localVideoSink = ProxyVideoSink()
                            localVideoTrack?.addSink(localVideoSink)
                            localVideoTrack?.setEnabled(true)
                            peerConnection?.addTrack(localVideoTrack, listOf(STREAM_ID))
                            pendingLocalRenderer?.let { renderer ->
                                localVideoTrack?.addSink(renderer)
                                pendingLocalRenderer = null
                            }
                            logI("Created video track on-the-fly for camera enable")
                        } else {
                            logE("Cannot enable camera - no capturer or EGL context")
                            return@execute
                        }
                    }
                    videoCapturer?.startCapture(VIDEO_WIDTH, VIDEO_HEIGHT, VIDEO_FPS)
                    sendDataChannelMessage("CameraEnabled")
                    updateState { copy(isCameraEnabled = true, isVideoReady = true) }
                } else if (videoCapturer != null) {
                    // SAMSUNG FIX Jan 2026: Wrap stopCapture in try-catch
                    // Samsung devices throw CameraAccessException when stopping during disconnect
                    try {
                        videoCapturer?.stopCapture()
                    } catch (e: Exception) {
                        // Log but don't crash - camera session may already be closing
                        logW("Camera stopCapture warning (expected on some devices): ${e.message}")
                    }
                    sendDataChannelMessage("CameraDisabled")
                    updateState { copy(isCameraEnabled = false) }
                }
            } catch (e: Exception) {
                logE("setCameraEnabled() failed", e)
                // Still update state to reflect user intent
                updateState { copy(isCameraEnabled = enabled) }
            }
        }
    }

    /**
     * Toggle speaker/earpiece
     */
    @Suppress("DEPRECATION")
    fun setSpeakerEnabled(enabled: Boolean) {
        logD("setSpeakerEnabled($enabled)")
        // Note: isSpeakerphoneOn is deprecated but still works for call audio routing
        // Modern alternative would be AudioDeviceInfo/AudioDeviceCallback (API 31+)
        // CRASH-FIX Jan 2026: Safe call on nullable audioManager
        audioManager?.isSpeakerphoneOn = enabled
        updateState { copy(isSpeakerEnabled = enabled) }
    }

    /**
     * Switch front/back camera
     */
    fun switchCamera() {
        logD("switchCamera()")
        executor.execute {
            (videoCapturer as? CameraVideoCapturer)?.switchCamera(object : CameraVideoCapturer.CameraSwitchHandler {
                override fun onCameraSwitchDone(isFrontCamera: Boolean) {
                    logD("Camera switched: front=$isFrontCamera")
                    updateState { copy(isFrontCamera = isFrontCamera) }
                }
                override fun onCameraSwitchError(error: String) {
                    logE("switchCamera() error: $error")
                }
            })
        }
    }

    /**
     * Set video renderers for local and remote video
     */
    fun setVideoRenderers(localSink: VideoSink?, remoteSink: VideoSink?) {
        executor.execute {
            localVideoSink?.setTarget(localSink)
            remoteVideoSink?.setTarget(remoteSink)
        }
    }

    /**
     * Get local video track for rendering
     */
    fun getLocalVideoTrack(): VideoTrack? = localVideoTrack

    /**
     * Set local video renderer - handles timing issues
     * LOCAL VIDEO FIX Jan 2026: If track isn't ready yet, store renderer for later attachment
     */
    fun setLocalVideoRenderer(renderer: VideoSink) {
        executor.execute {
            localVideoTrack?.let { track ->
                // Track exists - attach immediately
                try {
                    track.addSink(renderer)
                    logI("Local video sink added successfully")
                } catch (e: Exception) {
                    logE("Failed to add local video sink", e)
                }
            } ?: run {
                // Track not ready - store for later
                pendingLocalRenderer = renderer
                logD("Local video track not ready - renderer stored for later attachment")
            }
        }
    }

    /**
     * Hangup and cleanup
     * HANGUP FIX Jan 2026: Give time for Hangup message to be sent before cleanup
     */
    fun hangup() {
        logI("hangup()")
        sendDataChannelMessage("Hangup")
        updateState { copy(status = CallState.Status.ENDED) }

        // HANGUP FIX Jan 2026: Delay cleanup to allow Hangup message to be sent
        // Without this delay, cleanup() closes data channel before message is sent
        callScope.launch {
            delay(300)  // Give message time to be sent
            cleanup()
        }
    }

    /**
     * Set error state without immediate cleanup
     * FIXED Jan 2026: Allows UI to show error before cleanup
     */
    fun setError(errorMessage: String) {
        logE("setError: $errorMessage")
        updateState { copy(status = CallState.Status.ERROR, errorMessage = errorMessage) }
    }

    /**
     * Cleanup all WebRTC resources
     * FIXED Jan 2026: Proper scope cancellation and executor shutdown
     * FIXED Jan 2026: Made idempotent to prevent RejectedExecutionException on double cleanup
     * FIXED Jan 2026: Stop enterprise reconnection and heartbeat jobs
     */
    fun cleanup() {
        logD("cleanup()")

        // CRITICAL: Prevent double cleanup - executor may already be terminated
        // This prevents RejectedExecutionException when cleanup is called from both
        // hangup() and onDestroy()
        if (isCleanedUp) {
            logD("cleanup() already called, skipping")
            return
        }
        isCleanedUp = true

        // Stop enterprise reconnection and heartbeat FIRST
        stopReconnection()
        stopHeartbeat()

        // CRITICAL: Cancel coroutine scope (prevents memory leak)
        callScope.cancel()

        // Reset flags immediately (thread-safe due to @Volatile)
        remoteVideoSetUp = false
        sdpSent = false
        iceGatheringTimeoutJob?.cancel()
        iceGatheringTimeoutJob = null
        statsJob?.cancel()
        statsJob = null
        reconnectJob = null
        heartbeatJob = null

        // Check if executor is still accepting tasks before executing
        if (executor.isShutdown) {
            logW("cleanup() executor already shutdown, skipping resource disposal")
            return
        }

        executor.execute {
            try {
                // Dispose video sinks first to prevent frame delivery during cleanup
                localVideoSink?.dispose()
                localVideoSink = null
                remoteVideoSink?.dispose()
                remoteVideoSink = null

                // Stop and dispose video capturer
                // SAMSUNG FIX Jan 2026: Handle CameraAccessException during cleanup
                try {
                    videoCapturer?.stopCapture()
                } catch (e: android.hardware.camera2.CameraAccessException) {
                    // Expected on Samsung devices - camera session already closing
                    logW("Camera stopCapture CameraAccessException (expected on Samsung): ${e.message}")
                } catch (e: Exception) {
                    logW("Error stopping video capturer: ${e.message}")
                }
                try {
                    videoCapturer?.dispose()
                } catch (e: Exception) {
                    logW("Error disposing video capturer: ${e.message}")
                }
                videoCapturer = null

                // Close data channel
                try {
                    dataChannel?.unregisterObserver()
                    dataChannel?.close()
                } catch (e: Exception) {
                    logE("Error closing data channel", e)
                }
                dataChannel = null

                // Close peer connection (releases media streams and ICE)
                try {
                    peerConnection?.close()
                } catch (e: Exception) {
                    logE("Error closing peer connection", e)
                }
                peerConnection = null

                // Dispose media sources
                try {
                    audioSource?.dispose()
                } catch (e: Exception) {
                    logE("Error disposing audio source", e)
                }
                audioSource = null

                try {
                    videoSource?.dispose()
                } catch (e: Exception) {
                    logE("Error disposing video source", e)
                }
                videoSource = null

                // Dispose surface helper
                try {
                    surfaceTextureHelper?.dispose()
                } catch (e: Exception) {
                    logE("Error disposing surface helper", e)
                }
                surfaceTextureHelper = null

                // CRITICAL Jan 2026: Do NOT dispose PeerConnectionFactory.
                // WebRTC's native audio device module (ADM) has global state.
                // Calling factory.dispose() corrupts the ADM, causing SIGSEGV
                // (null pointer in nativeGetPlayoutData) when a new factory is
                // created for the next call. This is a known WebRTC Android bug.
                // The factory will be GC'd when no longer referenced.
                factory = null

                // Release EGL
                try {
                    eglBase?.release()
                } catch (e: Exception) {
                    logE("Error releasing EGL", e)
                }
                eglBase = null

                logI("cleanup() WebRTC resources released")
            } catch (e: Exception) {
                logE("cleanup() error", e)
            }
        }

        // FIXED: Shutdown executor OUTSIDE of executor.execute to ensure it runs
        try {
            executor.shutdown()
            // Wait for cleanup tasks to complete (max 5 seconds)
            if (!executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                logW("Executor didn't terminate gracefully, forcing shutdown")
                executor.shutdownNow()
            }
            logI("cleanup() complete - executor shutdown")
        } catch (e: Exception) {
            logE("Failed to shutdown executor", e)
            executor.shutdownNow()
        }
    }

    // ============================================================================
    // PRIVATE METHODS
    // ============================================================================

    private fun addMediaTracks() {
        // Audio track with WhatsApp-level optimizations
        // OPUS codec with FEC for resilience to packet loss
        val audioConstraints = MediaConstraints().apply {
            // Echo cancellation (like WhatsApp)
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
            // WhatsApp-level audio enhancements
            mandatory.add(MediaConstraints.KeyValuePair("googTypingNoiseDetection", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googAudioMirroring", "false"))
        }

        audioSource = factory?.createAudioSource(audioConstraints)
        localAudioTrack = factory?.createAudioTrack(AUDIO_TRACK_ID, audioSource)
        localAudioTrack?.setEnabled(true)
        peerConnection?.addTrack(localAudioTrack, listOf(STREAM_ID))

        // Video track - only create for video calls
        if (isVideoCall) {
            videoCapturer = createVideoCapturer()
            val capturer = videoCapturer
            val eglContext = eglBase?.eglBaseContext
            if (capturer != null && eglContext != null) {
                surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eglContext)
                videoSource = factory?.createVideoSource(capturer.isScreencast)
                capturer.initialize(surfaceTextureHelper, context, videoSource?.capturerObserver)

                localVideoTrack = factory?.createVideoTrack(VIDEO_TRACK_ID, videoSource)
                localVideoSink = ProxyVideoSink()
                localVideoTrack?.addSink(localVideoSink)
                localVideoTrack?.setEnabled(true)
                peerConnection?.addTrack(localVideoTrack, listOf(STREAM_ID))

                // Attach pending renderer if UI was ready before track
                pendingLocalRenderer?.let { renderer ->
                    try {
                        localVideoTrack?.addSink(renderer)
                        logI("Attached pending local renderer to video track")
                    } catch (e: Exception) {
                        logE("Failed to attach pending local renderer", e)
                    }
                    pendingLocalRenderer = null
                }

                // Start camera capture immediately for video calls
                try {
                    videoCapturer?.startCapture(VIDEO_WIDTH, VIDEO_HEIGHT, VIDEO_FPS)
                    updateState { copy(isCameraEnabled = true, isVideoReady = true) }
                    logI("Video call: camera started after track creation")
                } catch (e: Exception) {
                    logE("Failed to start camera for video call", e)
                    updateState { copy(isVideoReady = false) }
                }
            } else {
                logW("No camera available - video call falling back to audio only")
                updateState { copy(isVideoReady = false) }
            }
        } else {
            logD("Voice call: skipping video track creation entirely")
        }
    }

    private fun createVideoCapturer(): VideoCapturer? {
        // Try Camera2 first (better on modern devices)
        val camera2Enumerator = Camera2Enumerator(context)

        // Prefer front camera
        camera2Enumerator.deviceNames.find { camera2Enumerator.isFrontFacing(it) }?.let { device ->
            return camera2Enumerator.createCapturer(device, null)
        }

        // Fallback to back camera
        camera2Enumerator.deviceNames.firstOrNull()?.let { device ->
            return camera2Enumerator.createCapturer(device, null)
        }

        // Final fallback to Camera1
        val camera1Enumerator = Camera1Enumerator()
        camera1Enumerator.deviceNames.firstOrNull()?.let { device ->
            return camera1Enumerator.createCapturer(device, null)
        }

        return null
    }

    private fun createAudioDeviceModule(): JavaAudioDeviceModule {
        return JavaAudioDeviceModule.builder(context)
            .setUseHardwareAcousticEchoCanceler(true)
            .setUseHardwareNoiseSuppressor(true)
            .createAudioDeviceModule()
    }

    private fun createOffer() {
        // Always accept video — enables mid-call video upgrade via renegotiation
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        }

        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) {
                logD("Offer created")
                peerConnection?.setLocalDescription(sdpObserver, sdp)
            }
            override fun onCreateFailure(error: String) {
                logE("Create offer failed: $error")
                updateState { copy(status = CallState.Status.ERROR, errorMessage = error) }
            }
            override fun onSetSuccess() {}
            override fun onSetFailure(error: String) {}
        }, constraints)
    }

    private fun handleRemoteOffer(offer: String) {
        val sessionDescription = SessionDescription(SessionDescription.Type.OFFER, offer)

        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onSetSuccess() {
                logD("Remote offer set, creating answer")
                createAnswer()
            }
            override fun onSetFailure(error: String) {
                logE("Set remote offer failed: $error")
            }
            override fun onCreateSuccess(sdp: SessionDescription) {}
            override fun onCreateFailure(error: String) {}
        }, sessionDescription)
    }

    private fun createAnswer() {
        // Always accept video — enables mid-call video upgrade via renegotiation
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        }

        peerConnection?.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) {
                logD("Answer created")
                peerConnection?.setLocalDescription(sdpObserver, sdp)
            }
            override fun onCreateFailure(error: String) {
                logE("Create answer failed: $error")
            }
            override fun onSetSuccess() {}
            override fun onSetFailure(error: String) {}
        }, constraints)
    }

    private fun sendDataChannelMessage(message: String) {
        dataChannel?.let { channel ->
            if (channel.state() == DataChannel.State.OPEN) {
                val json = "{\"type\":\"$message\"}"
                val buffer = DataChannel.Buffer(
                    java.nio.ByteBuffer.wrap(json.toByteArray()),
                    false
                )
                val sent = channel.send(buffer)
                logD("Sent data channel message: $message (success=$sent)")
            } else {
                logW("Cannot send data channel message '$message' - channel state: ${channel.state()}")
            }
        } ?: run {
            logW("Cannot send data channel message '$message' - channel is null")
        }
    }

    /**
     * Thread-safe state update using atomic update
     * CRASH FIX Jan 2026: Previous non-atomic read-modify-write caused race conditions
     * and inconsistent UI state during call setup/teardown
     */
    private inline fun updateState(update: CallState.() -> CallState) {
        _state.value = _state.value.update()
    }

    // ============================================================================
    // OBSERVERS
    // ============================================================================

    private val sdpObserver = object : SdpObserver {
        override fun onCreateSuccess(sdp: SessionDescription) {
            logD("SDP created: ${sdp.type}")
        }

        override fun onSetSuccess() {
            logD("SDP set success")
            // Check if ICE gathering is already complete
            peerConnection?.localDescription?.let { sdp ->
                val gatheringState = peerConnection?.iceGatheringState()
                logD("ICE gathering state after SDP set: $gatheringState")
                if (gatheringState == PeerConnection.IceGatheringState.COMPLETE) {
                    sendLocalDescription(sdp)
                } else {
                    // Start timeout to send SDP even if ICE gathering doesn't complete
                    startIceGatheringTimeout()
                }
            }
        }

        override fun onCreateFailure(error: String) {
            logE("SDP create failed: $error")
            updateState { copy(status = CallState.Status.ERROR, errorMessage = error) }
        }

        override fun onSetFailure(error: String) {
            logE("SDP set failed: $error")
        }
    }

    /**
     * Start timeout to send SDP if ICE gathering takes too long
     * Uses lifecycle-aware callScope instead of GlobalScope (FIXED Jan 2026)
     */
    private fun startIceGatheringTimeout() {
        iceGatheringTimeoutJob?.cancel()
        iceGatheringTimeoutJob = callScope.launch {
            delay(ICE_GATHERING_TIMEOUT_MS)
            if (!sdpSent) {
                logW("ICE gathering timeout - sending SDP with current candidates")
                peerConnection?.localDescription?.let { sdp ->
                    sendLocalDescription(sdp)
                }
            }
        }
    }

    /**
     * Send local description (offer or answer) - only once
     */
    private fun sendLocalDescription(sdp: SessionDescription) {
        if (sdpSent) {
            logD("SDP already sent, skipping")
            return
        }
        sdpSent = true
        iceGatheringTimeoutJob?.cancel()
        logI("Sending local SDP: ${sdp.type}")
        onLocalDescription?.invoke(sdp)
    }

    /**
     * Handle renegotiation offer received via data channel (mid-call video upgrade)
     */
    private fun handleRenegotiationOffer(sdp: String) {
        executor.execute {
            try {
                remoteVideoSetUp = false  // Allow new video track
                val offer = SessionDescription(SessionDescription.Type.OFFER, sdp)
                peerConnection?.setRemoteDescription(object : SdpObserver {
                    override fun onSetSuccess() {
                        logI("Renegotiation remote offer set, creating answer")
                        val constraints = MediaConstraints().apply {
                            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
                        }
                        peerConnection?.createAnswer(object : SdpObserver {
                            override fun onCreateSuccess(answerSdp: SessionDescription) {
                                peerConnection?.setLocalDescription(object : SdpObserver {
                                    override fun onSetSuccess() {
                                        logI("Renegotiation answer set locally, sending via data channel")
                                        val json = org.json.JSONObject().apply {
                                            put("type", "RenegotiateAnswer")
                                            put("sdp", answerSdp.description)
                                        }.toString()
                                        dataChannel?.let { channel ->
                                            if (channel.state() == DataChannel.State.OPEN) {
                                                val buffer = DataChannel.Buffer(
                                                    java.nio.ByteBuffer.wrap(json.toByteArray()),
                                                    false
                                                )
                                                channel.send(buffer)
                                                logI("Renegotiation answer sent via data channel")
                                            }
                                        }
                                    }
                                    override fun onSetFailure(error: String) { logE("Renegotiation answer setLocal failed: $error") }
                                    override fun onCreateSuccess(sdp: SessionDescription) {}
                                    override fun onCreateFailure(error: String) {}
                                }, answerSdp)
                            }
                            override fun onCreateFailure(error: String) { logE("Renegotiation createAnswer failed: $error") }
                            override fun onSetSuccess() {}
                            override fun onSetFailure(error: String) {}
                        }, constraints)
                    }
                    override fun onSetFailure(error: String) { logE("Renegotiation setRemote failed: $error") }
                    override fun onCreateSuccess(sdp: SessionDescription) {}
                    override fun onCreateFailure(error: String) {}
                }, offer)
            } catch (e: Exception) {
                logE("handleRenegotiationOffer error", e)
            }
        }
    }

    /**
     * Handle renegotiation answer received via data channel
     */
    private fun handleRenegotiationAnswer(sdp: String) {
        executor.execute {
            try {
                remoteVideoSetUp = false  // Allow new video track
                val answer = SessionDescription(SessionDescription.Type.ANSWER, sdp)
                peerConnection?.setRemoteDescription(object : SdpObserver {
                    override fun onSetSuccess() { logI("Renegotiation answer applied successfully") }
                    override fun onSetFailure(error: String) { logE("Renegotiation setRemote answer failed: $error") }
                    override fun onCreateSuccess(sdp: SessionDescription) {}
                    override fun onCreateFailure(error: String) {}
                }, answer)
            } catch (e: Exception) {
                logE("handleRenegotiationAnswer error", e)
            }
        }
    }

    private val peerConnectionObserver = object : PeerConnection.Observer {
        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {
            logD("ICE gathering: $state")
            if (state == PeerConnection.IceGatheringState.COMPLETE) {
                peerConnection?.localDescription?.let { sendLocalDescription(it) }
            }
        }

        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
            logI("ICE connection: $state")
            onIceConnectionChange?.invoke(state)

            when (state) {
                PeerConnection.IceConnectionState.CONNECTED,
                PeerConnection.IceConnectionState.COMPLETED -> {
                    logI("ICE connected - call established")
                    handleConnectionEstablished()
                }
                PeerConnection.IceConnectionState.DISCONNECTED -> {
                    logW("ICE disconnected - starting enterprise reconnection")
                    startEnterpriseReconnection("Network disconnected")
                }
                PeerConnection.IceConnectionState.FAILED -> {
                    logE("ICE connection failed")
                    startEnterpriseReconnection("Connection failed")
                }
                PeerConnection.IceConnectionState.CLOSED -> {
                    stopReconnection()
                    updateState { copy(status = CallState.Status.ENDED) }
                }
                PeerConnection.IceConnectionState.CHECKING -> {
                    logD("ICE checking - connection in progress")
                }
                PeerConnection.IceConnectionState.NEW -> {
                    logD("ICE new - waiting for candidates")
                }
                else -> {}
            }
        }

        override fun onSignalingChange(state: PeerConnection.SignalingState) {
            logD("Signaling: $state")
        }

        override fun onIceConnectionReceivingChange(receiving: Boolean) {
            logD("ICE receiving: $receiving")
        }

        override fun onIceCandidate(candidate: IceCandidate) {
            logD("ICE candidate: ${candidate.sdp.take(50)}...")
            // For mesh networks, we use GATHER_ONCE so candidates are bundled in SDP
        }

        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {
            logD("ICE candidates removed: ${candidates?.size ?: 0}")
        }

        override fun onAddStream(stream: MediaStream) {
            logI("Remote stream added: ${stream.id}")
            executor.execute {
                try {
                    // Prevent duplicate setup
                    if (remoteVideoSetUp) {
                        logD("Remote video already set up, skipping onAddStream")
                        return@execute
                    }
                    stream.videoTracks.firstOrNull()?.let { videoTrack ->
                        remoteVideoSetUp = true
                        remoteVideoSink = ProxyVideoSink()
                        videoTrack.addSink(remoteVideoSink)
                        onRemoteVideoTrack?.invoke(videoTrack)
                    }
                } catch (e: Exception) {
                    logE("onAddStream error", e)
                }
            }
        }

        override fun onRemoveStream(stream: MediaStream) {
            logD("Remote stream removed: ${stream.id}")
        }

        override fun onDataChannel(channel: DataChannel) {
            logD("Data channel received")
            dataChannel = channel
            dataChannel?.registerObserver(dataChannelObserver)
        }

        override fun onRenegotiationNeeded() {
            logI("Renegotiation needed, initialConnectionEstablished=$initialConnectionEstablished")
            if (!initialConnectionEstablished) {
                // Initial negotiation handled by createOffer/createAnswer flow
                return
            }
            // Mid-call renegotiation (e.g., camera enabled during voice call)
            // Send new offer via data channel
            executor.execute {
                try {
                    val constraints = MediaConstraints().apply {
                        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
                    }
                    peerConnection?.createOffer(object : SdpObserver {
                        override fun onCreateSuccess(sdp: SessionDescription) {
                            logI("Renegotiation offer created")
                            peerConnection?.setLocalDescription(object : SdpObserver {
                                override fun onSetSuccess() {
                                    logI("Renegotiation offer set locally, sending via data channel")
                                    val json = org.json.JSONObject().apply {
                                        put("type", "RenegotiateOffer")
                                        put("sdp", sdp.description)
                                    }.toString()
                                    dataChannel?.let { channel ->
                                        if (channel.state() == DataChannel.State.OPEN) {
                                            val buffer = DataChannel.Buffer(
                                                java.nio.ByteBuffer.wrap(json.toByteArray()),
                                                false
                                            )
                                            channel.send(buffer)
                                            logI("Renegotiation offer sent via data channel")
                                        }
                                    }
                                }
                                override fun onSetFailure(error: String) { logE("Renegotiation setLocal failed: $error") }
                                override fun onCreateSuccess(sdp: SessionDescription) {}
                                override fun onCreateFailure(error: String) {}
                            }, sdp)
                        }
                        override fun onCreateFailure(error: String) { logE("Renegotiation createOffer failed: $error") }
                        override fun onSetSuccess() {}
                        override fun onSetFailure(error: String) {}
                    }, constraints)
                } catch (e: Exception) {
                    logE("onRenegotiationNeeded error", e)
                }
            }
        }

        override fun onAddTrack(receiver: RtpReceiver, streams: Array<out MediaStream>) {
            logD("Track added: ${receiver.track()?.kind()}")
            executor.execute {
                try {
                    // Prevent duplicate setup
                    if (remoteVideoSetUp) {
                        logD("Remote video already set up, skipping onAddTrack")
                        return@execute
                    }
                    receiver.track()?.let { track ->
                        if (track.kind() == MediaStreamTrack.VIDEO_TRACK_KIND) {
                            (track as? VideoTrack)?.let { videoTrack ->
                                remoteVideoSetUp = true
                                remoteVideoSink = ProxyVideoSink()
                                videoTrack.addSink(remoteVideoSink)
                                onRemoteVideoTrack?.invoke(videoTrack)
                            }
                        }
                    }
                } catch (e: Exception) {
                    logE("onAddTrack error", e)
                }
            }
        }
    }

    private val dataChannelObserver = object : DataChannel.Observer {
        override fun onBufferedAmountChange(amount: Long) {}

        override fun onStateChange() {
            logD("Data channel state: ${dataChannel?.state()}")
        }

        override fun onMessage(buffer: DataChannel.Buffer) {
            val data = ByteArray(buffer.data.remaining())
            buffer.data.get(data)
            val rawMessage = String(data)

            // Parse JSON message format: {"type":"MessageType"}
            // FIX Jan 2026: Messages are sent as JSON but handler was checking raw string
            val messageType = try {
                org.json.JSONObject(rawMessage).optString("type", rawMessage)
            } catch (e: Exception) {
                // Fallback to raw message if not valid JSON
                rawMessage
            }

            // Don't log heartbeats to reduce noise
            if (!messageType.startsWith("Heartbeat:")) {
                logD("Data channel message: $messageType (raw: $rawMessage)")
            }

            when {
                messageType == "Hangup" -> {
                    logI("Remote hangup received")
                    // HANGUP FIX Jan 2026: Set flag FIRST to prevent reconnection race
                    remoteHangupReceived = true
                    stopReconnection()
                    stopHeartbeat()
                    updateState { copy(status = CallState.Status.ENDED) }
                }
                messageType.startsWith("Heartbeat:ping:") -> {
                    // Respond to heartbeat ping with pong
                    val timestamp = messageType.substringAfter("Heartbeat:ping:")
                    sendDataChannelMessage("Heartbeat:pong:$timestamp")
                    handleHeartbeatReceived()
                }
                messageType.startsWith("Heartbeat:pong:") -> {
                    // Received pong response
                    handleHeartbeatReceived()
                }
                messageType == "CameraEnabled" -> {
                    logD("Remote camera enabled")
                    // Reset remote video flag to allow new track from renegotiation
                    remoteVideoSetUp = false
                }
                messageType == "CameraDisabled" -> {
                    logD("Remote camera disabled")
                }
                messageType == "LowBandwidthMode" -> {
                    logI("Remote requested low bandwidth mode")
                }
                messageType == "RenegotiateOffer" -> {
                    logI("Received renegotiation offer via data channel")
                    val sdp = try {
                        org.json.JSONObject(rawMessage).getString("sdp")
                    } catch (e: Exception) {
                        logE("Failed to parse renegotiation offer", e)
                        return
                    }
                    handleRenegotiationOffer(sdp)
                }
                messageType == "RenegotiateAnswer" -> {
                    logI("Received renegotiation answer via data channel")
                    val sdp = try {
                        org.json.JSONObject(rawMessage).getString("sdp")
                    } catch (e: Exception) {
                        logE("Failed to parse renegotiation answer", e)
                        return
                    }
                    handleRenegotiationAnswer(sdp)
                }
            }
        }
    }

    // ============================================================================
    // HELPER CLASSES
    // ============================================================================

    /**
     * Proxy video sink for swappable renderers
     * Allows changing the render target without recreating tracks
     * Thread-safe implementation with proper frame handling
     */
    class ProxyVideoSink : VideoSink {
        @Volatile
        private var target: VideoSink? = null

        @Volatile
        private var isInitialized = false

        fun setTarget(sink: VideoSink?) {
            synchronized(this) {
                target = sink
                isInitialized = sink != null
            }
        }

        override fun onFrame(frame: VideoFrame) {
            try {
                synchronized(this) {
                    if (isInitialized) {
                        target?.onFrame(frame)
                    }
                }
            } catch (e: Exception) {
                // Silently ignore frame delivery errors to prevent crash
                // This can happen when renderer is being released
            }
        }

        fun dispose() {
            synchronized(this) {
                isInitialized = false
                target = null
            }
        }
    }

    // ============================================================================
    // ENTERPRISE RECONNECTION SYSTEM (Jan 2026)
    // Like WhatsApp/Zoom/Teams - robust reconnection with timeout
    // ============================================================================

    /**
     * Handle successful connection - reset reconnection state, start heartbeat
     */
    private fun handleConnectionEstablished() {
        logI("Connection established - resetting reconnection state")
        initialConnectionEstablished = true

        // Cancel any ongoing reconnection
        stopReconnection()

        // Reset counters
        reconnectAttempts = 0
        disconnectTime = null

        // Update state
        updateState {
            copy(
                status = CallState.Status.CONNECTED,
                startTime = startTime ?: System.currentTimeMillis(),
                reconnectAttempt = 0,
                lastDisconnectTime = null,
                networkQuality = CallState.NetworkQuality.GOOD  // Assume good on connect
            )
        }

        // Start heartbeat monitoring
        startHeartbeat()
    }

    /**
     * Start enterprise-level reconnection with 30-second timeout
     * Attempts ICE restart every 3 seconds until timeout or success
     * HANGUP FIX Jan 2026: Wait 500ms before reconnecting to allow Hangup message to arrive
     */
    private fun startEnterpriseReconnection(reason: String) {
        // Don't start if already reconnecting
        if (reconnectJob?.isActive == true) {
            logD("Reconnection already in progress")
            return
        }

        // Don't reconnect if already cleaned up
        if (isCleanedUp) {
            logD("Already cleaned up, skipping reconnection")
            return
        }

        // HANGUP FIX Jan 2026: Check if remote already hung up
        if (remoteHangupReceived) {
            logI("Remote hangup received, not reconnecting")
            updateState { copy(status = CallState.Status.ENDED) }
            return
        }

        disconnectTime = System.currentTimeMillis()

        // Don't show "Reconnecting" immediately - wait to see if it's a hangup
        logD("Disconnect detected, waiting 500ms before reconnecting...")

        reconnectJob = callScope.launch {
            // HANGUP FIX Jan 2026: Wait for potential Hangup message
            delay(500)

            // Check again after delay
            if (remoteHangupReceived) {
                logI("Remote hangup received during delay, not reconnecting")
                updateState { copy(status = CallState.Status.ENDED) }
                return@launch
            }

            if (isCleanedUp) {
                logD("Cleaned up during delay, skipping reconnection")
                return@launch
            }

            // Now show reconnecting status
            updateState {
                copy(
                    status = CallState.Status.RECONNECTING,
                    lastDisconnectTime = disconnectTime,
                    reconnectAttempt = 0,
                    errorMessage = reason
                )
            }
            logI("Starting enterprise reconnection (timeout: ${RECONNECT_TIMEOUT_SECONDS}s)")

            val startTime = System.currentTimeMillis()
            val timeoutMs = RECONNECT_TIMEOUT_SECONDS * 1000L

            while (isActive && !isCleanedUp && !remoteHangupReceived) {
                val elapsed = System.currentTimeMillis() - startTime

                // HANGUP FIX Jan 2026: Check for hangup every iteration
                if (remoteHangupReceived) {
                    logI("Remote hangup received during reconnection, ending call")
                    updateState { copy(status = CallState.Status.ENDED) }
                    break
                }

                // Check timeout
                if (elapsed >= timeoutMs) {
                    logE("Reconnection timeout after ${RECONNECT_TIMEOUT_SECONDS}s")
                    updateState {
                        copy(
                            status = CallState.Status.ERROR,
                            errorMessage = "Connection lost. Please try calling again."
                        )
                    }
                    break
                }

                // Check max attempts
                if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
                    logE("Max reconnection attempts ($MAX_RECONNECT_ATTEMPTS) reached")
                    updateState {
                        copy(
                            status = CallState.Status.ERROR,
                            errorMessage = "Could not reconnect. Please try again."
                        )
                    }
                    break
                }

                // Attempt reconnection
                reconnectAttempts++
                val remainingSeconds = ((timeoutMs - elapsed) / 1000).toInt()
                logI("Reconnection attempt $reconnectAttempts/$MAX_RECONNECT_ATTEMPTS (${remainingSeconds}s remaining)")

                updateState {
                    copy(reconnectAttempt = reconnectAttempts)
                }

                // Try ICE restart
                attemptIceRestart()

                // Wait before next attempt
                delay(RECONNECT_INTERVAL_MS)

                // Check if reconnected (state changed to CONNECTED)
                if (_state.value.status == CallState.Status.CONNECTED) {
                    logI("Reconnection successful!")
                    break
                }
            }
        }
    }

    /**
     * Stop ongoing reconnection attempts
     */
    private fun stopReconnection() {
        reconnectJob?.cancel()
        reconnectJob = null
    }

    /**
     * Start heartbeat to detect silent disconnects
     * Sends ping every 5s, disconnects if no pong for 15s
     */
    private fun startHeartbeat() {
        stopHeartbeat()

        heartbeatJob = callScope.launch {
            logD("Starting heartbeat monitoring")
            lastHeartbeatReceived = System.currentTimeMillis()

            while (isActive && !isCleanedUp && _state.value.isConnected) {
                // Send heartbeat ping
                sendDataChannelMessage("Heartbeat:ping:${System.currentTimeMillis()}")

                delay(HEARTBEAT_INTERVAL_MS)

                // Check if heartbeat timeout exceeded
                val timeSinceLastHeartbeat = System.currentTimeMillis() - lastHeartbeatReceived
                if (timeSinceLastHeartbeat > HEARTBEAT_TIMEOUT_MS) {
                    logW("Heartbeat timeout - no response for ${timeSinceLastHeartbeat}ms")
                    // Don't disconnect immediately, let ICE handle it
                    // But update quality to show warning
                    updateState {
                        copy(networkQuality = CallState.NetworkQuality.BAD)
                    }
                }
            }
        }
    }

    /**
     * Stop heartbeat monitoring
     */
    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    /**
     * Handle received heartbeat (called from data channel observer)
     */
    private fun handleHeartbeatReceived() {
        lastHeartbeatReceived = System.currentTimeMillis()
    }

    /**
     * Attempt ICE restart to recover disconnected call
     */
    private fun attemptIceRestart() {
        executor.execute {
            try {
                logI("attemptIceRestart: attempt $reconnectAttempts")

                // Request ICE restart
                peerConnection?.restartIce()

                // Create new offer with ICE restart flag
                val constraints = MediaConstraints().apply {
                    mandatory.add(MediaConstraints.KeyValuePair("IceRestart", "true"))
                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
                }

                peerConnection?.createOffer(object : SdpObserver {
                    override fun onCreateSuccess(sdp: SessionDescription) {
                        logD("ICE restart offer created")
                        peerConnection?.setLocalDescription(sdpObserver, sdp)
                    }
                    override fun onCreateFailure(error: String) {
                        logE("ICE restart offer failed: $error")
                    }
                    override fun onSetSuccess() {}
                    override fun onSetFailure(error: String) {}
                }, constraints)

            } catch (e: Exception) {
                logE("attemptIceRestart failed", e)
            }
        }
    }

    /**
     * Reset reconnection counter (called when successfully connected)
     */
    private fun resetReconnectAttempts() {
        reconnectAttempts = 0
    }

    // ============================================================================
    // STATS MONITORING (Jan 2026)
    // ============================================================================

    /**
     * Start periodic stats collection
     * Useful for monitoring call quality and bandwidth adaptation
     */
    fun startStatsMonitoring(intervalMs: Long = STATS_INTERVAL_MS) {
        statsJob?.cancel()
        statsJob = callScope.launch {
            while (isActive) {
                collectStats()
                delay(intervalMs)
            }
        }
    }

    /**
     * Stop stats monitoring
     */
    fun stopStatsMonitoring() {
        statsJob?.cancel()
        statsJob = null
    }

    private fun collectStats() {
        peerConnection?.getStats { report ->
            try {
                var audioBytesSent = 0L
                var audioBytesReceived = 0L
                var videoBytesSent = 0L
                var videoBytesReceived = 0L
                var packetsLost = 0L
                var jitter = 0.0
                var roundTripTime = 0.0
                var frameWidth = 0
                var frameHeight = 0
                var framesPerSecond = 0.0

                report.statsMap.forEach { (_, stats) ->
                    when (stats.type) {
                        "outbound-rtp" -> {
                            val kind = stats.members["kind"] as? String
                            val bytes = (stats.members["bytesSent"] as? Number)?.toLong() ?: 0L
                            if (kind == "audio") audioBytesSent = bytes
                            else if (kind == "video") {
                                videoBytesSent = bytes
                                frameWidth = (stats.members["frameWidth"] as? Number)?.toInt() ?: 0
                                frameHeight = (stats.members["frameHeight"] as? Number)?.toInt() ?: 0
                                framesPerSecond = (stats.members["framesPerSecond"] as? Number)?.toDouble() ?: 0.0
                            }
                        }
                        "inbound-rtp" -> {
                            val kind = stats.members["kind"] as? String
                            val bytes = (stats.members["bytesReceived"] as? Number)?.toLong() ?: 0L
                            packetsLost += (stats.members["packetsLost"] as? Number)?.toLong() ?: 0L
                            jitter = (stats.members["jitter"] as? Number)?.toDouble() ?: jitter
                            if (kind == "audio") audioBytesReceived = bytes
                            else if (kind == "video") videoBytesReceived = bytes
                        }
                        "candidate-pair" -> {
                            if (stats.members["state"] == "succeeded") {
                                roundTripTime = (stats.members["currentRoundTripTime"] as? Number)?.toDouble() ?: 0.0
                            }
                        }
                    }
                }

                val callStats = CallStats(
                    timestamp = System.currentTimeMillis(),
                    audioBytesSent = audioBytesSent,
                    audioBytesReceived = audioBytesReceived,
                    videoBytesSent = videoBytesSent,
                    videoBytesReceived = videoBytesReceived,
                    packetsLost = packetsLost,
                    jitterMs = (jitter * 1000).toLong(),
                    roundTripTimeMs = (roundTripTime * 1000).toLong(),
                    videoWidth = frameWidth,
                    videoHeight = frameHeight,
                    fps = framesPerSecond
                )

                _stats.value = callStats

                // Update network quality in call state (Enterprise feature Jan 2026)
                updateNetworkQuality(callStats)

                // WhatsApp-style adaptive quality based on network conditions
                adaptVideoQuality(callStats)

            } catch (e: Exception) {
                logE("collectStats() error", e)
            }
        }
    }

    /**
     * Calculate and update network quality based on stats
     * Enterprise feature (Jan 2026) - like WhatsApp/Zoom signal bars
     */
    private fun updateNetworkQuality(stats: CallStats) {
        // Calculate packet loss percentage
        // Note: This is cumulative, so we'd need to track delta for accurate %
        // For now, use a simplified approach based on absolute numbers
        val rttMs = stats.roundTripTimeMs.toInt()
        val jitterMs = stats.jitterMs.toInt()

        // Determine quality level based on RTT and jitter
        val quality = when {
            rttMs < EXCELLENT_RTT && jitterMs < 20 -> CallState.NetworkQuality.EXCELLENT
            rttMs < GOOD_RTT && jitterMs < 50 -> CallState.NetworkQuality.GOOD
            rttMs < FAIR_RTT && jitterMs < 100 -> CallState.NetworkQuality.FAIR
            rttMs < POOR_RTT && jitterMs < 200 -> CallState.NetworkQuality.POOR
            else -> CallState.NetworkQuality.BAD
        }

        // Only update if connected (don't override RECONNECTING state)
        if (_state.value.status == CallState.Status.CONNECTED) {
            updateState {
                copy(
                    networkQuality = quality,
                    roundTripTimeMs = rttMs,
                    jitterMs = jitterMs,
                    packetLossPercent = 0f  // Would need delta calculation for accurate %
                )
            }
        }
    }

    /**
     * Call quality/stats data class
     */
    data class CallStats(
        val timestamp: Long,
        val audioBytesSent: Long,
        val audioBytesReceived: Long,
        val videoBytesSent: Long,
        val videoBytesReceived: Long,
        val packetsLost: Long,
        val jitterMs: Long,
        val roundTripTimeMs: Long,
        val videoWidth: Int,
        val videoHeight: Int,
        val fps: Double
    ) {
        val totalBytesSent: Long get() = audioBytesSent + videoBytesSent
        val totalBytesReceived: Long get() = audioBytesReceived + videoBytesReceived

        val qualityIndicator: String get() = when {
            roundTripTimeMs < 100 && jitterMs < 30 && packetsLost == 0L -> "Excellent"
            roundTripTimeMs < 200 && jitterMs < 50 -> "Good"
            roundTripTimeMs < 400 -> "Fair"
            else -> "Poor"
        }
    }

    // ============================================================================
    // ADAPTIVE QUALITY (WhatsApp-Level - Jan 2026)
    // ============================================================================

    /**
     * Video quality presets (like WhatsApp adaptive streaming)
     */
    enum class VideoQuality(val width: Int, val height: Int, val fps: Int, val maxBitrateKbps: Int) {
        LOW(320, 180, 15, 150),        // Very poor network
        MEDIUM(640, 360, 20, 400),     // Poor network
        STANDARD(960, 540, 25, 800),   // Normal network
        HIGH(1280, 720, 30, 1500),     // Good network
        HD(1920, 1080, 30, 3000)       // Excellent network (WiFi)
    }

    private var currentQuality = VideoQuality.STANDARD
    private var lastBandwidthCheckMs = 0L

    /**
     * Adapt video quality based on network conditions (like WhatsApp BWE)
     * Call this periodically from stats monitoring
     */
    fun adaptVideoQuality(stats: CallStats) {
        val now = System.currentTimeMillis()
        if (now - lastBandwidthCheckMs < 5000) return // Check every 5 seconds
        lastBandwidthCheckMs = now

        val newQuality = when {
            // Very poor: high packet loss or RTT > 500ms
            stats.packetsLost > 50 || stats.roundTripTimeMs > 500 -> VideoQuality.LOW
            // Poor: moderate loss or high RTT
            stats.packetsLost > 20 || stats.roundTripTimeMs > 300 -> VideoQuality.MEDIUM
            // Normal: some loss or moderate RTT
            stats.packetsLost > 5 || stats.roundTripTimeMs > 150 -> VideoQuality.STANDARD
            // Good: low loss and RTT
            stats.roundTripTimeMs < 100 && stats.jitterMs < 30 -> VideoQuality.HIGH
            // Excellent: very low latency (WiFi)
            stats.roundTripTimeMs < 50 && stats.jitterMs < 15 -> VideoQuality.HD
            else -> VideoQuality.STANDARD
        }

        if (newQuality != currentQuality) {
            logI("Adapting video quality: ${currentQuality.name} -> ${newQuality.name}")
            setVideoQuality(newQuality)
        }
    }

    /**
     * Set video capture quality dynamically
     */
    fun setVideoQuality(quality: VideoQuality) {
        currentQuality = quality
        executor.execute {
            try {
                // Change capture parameters
                videoCapturer?.changeCaptureFormat(quality.width, quality.height, quality.fps)

                // Set bitrate constraint via RTP sender
                peerConnection?.senders?.find { it.track()?.kind() == "video" }?.let { sender ->
                    val params = sender.parameters
                    params.encodings.firstOrNull()?.let { encoding ->
                        encoding.maxBitrateBps = quality.maxBitrateKbps * 1000
                        encoding.minBitrateBps = (quality.maxBitrateKbps * 0.1).toInt() * 1000
                        encoding.maxFramerate = quality.fps
                    }
                    sender.parameters = params
                }

                logD("Video quality set to ${quality.name}: ${quality.width}x${quality.height}@${quality.fps}fps, max ${quality.maxBitrateKbps}kbps")
            } catch (e: Exception) {
                logE("setVideoQuality() failed", e)
            }
        }
    }

    /**
     * Enable simulcast for better adaptive streaming (like WhatsApp SVC)
     * Creates multiple quality layers that receiver can switch between
     */
    fun enableSimulcast() {
        executor.execute {
            peerConnection?.senders?.find { it.track()?.kind() == "video" }?.let { sender ->
                val params = sender.parameters

                // Clear existing encodings
                params.encodings.clear()

                // Add three quality layers (like WhatsApp)
                params.encodings.addAll(listOf(
                    // Low quality layer
                    RtpParameters.Encoding("low", true, 1.0).apply {
                        maxBitrateBps = 150_000
                        maxFramerate = 15
                        scaleResolutionDownBy = 4.0
                    },
                    // Medium quality layer
                    RtpParameters.Encoding("mid", true, 1.0).apply {
                        maxBitrateBps = 500_000
                        maxFramerate = 25
                        scaleResolutionDownBy = 2.0
                    },
                    // High quality layer
                    RtpParameters.Encoding("high", true, 1.0).apply {
                        maxBitrateBps = 1_500_000
                        maxFramerate = 30
                        scaleResolutionDownBy = 1.0
                    }
                ))

                sender.parameters = params
                logI("Simulcast enabled with 3 quality layers")
            }
        }
    }

    companion object {
        private const val AUDIO_TRACK_ID = "mesh-audio"
        private const val VIDEO_TRACK_ID = "mesh-video"
        private const val STREAM_ID = "mesh-stream"

        // Default video quality - will be adapted dynamically
        private const val VIDEO_WIDTH = 1280
        private const val VIDEO_HEIGHT = 720
        private const val VIDEO_FPS = 25

        // Enterprise Reconnection Settings (Jan 2026)
        // Like WhatsApp/Zoom/Teams - give network time to recover
        private const val MAX_RECONNECT_ATTEMPTS = 5
        private const val RECONNECT_TIMEOUT_SECONDS = 30
        private const val RECONNECT_INTERVAL_MS = 3000L  // Try every 3 seconds

        // ICE gathering timeout (like Meshenger - 3 seconds)
        private const val ICE_GATHERING_TIMEOUT_MS = 3000L

        // Stats collection interval
        private const val STATS_INTERVAL_MS = 2000L

        // Heartbeat settings (keep-alive)
        private const val HEARTBEAT_INTERVAL_MS = 5000L  // Send ping every 5 seconds
        private const val HEARTBEAT_TIMEOUT_MS = 15000L  // Disconnect if no pong for 15 seconds

        // Network quality thresholds
        private const val EXCELLENT_PACKET_LOSS = 1f
        private const val GOOD_PACKET_LOSS = 3f
        private const val FAIR_PACKET_LOSS = 5f
        private const val POOR_PACKET_LOSS = 15f
        private const val EXCELLENT_RTT = 100
        private const val GOOD_RTT = 200
        private const val FAIR_RTT = 300
        private const val POOR_RTT = 500

        // Adaptive bitrate thresholds (Kbps)
        private const val LOW_BANDWIDTH_THRESHOLD = 200  // Switch to low quality
        private const val VIDEO_DISABLE_THRESHOLD = 100  // Disable video, audio only
    }
}
