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
    private val isOutgoing: Boolean
) {
    private val executor = Executors.newSingleThreadExecutor()
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

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

    // Reconnection tracking
    private var reconnectAttempts = 0

    // Track if remote video has been set up to prevent duplicates
    @Volatile
    private var remoteVideoSetUp = false

    // Track if SDP has been sent to prevent duplicates
    @Volatile
    private var sdpSent = false

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
                val encoderFactory = DefaultVideoEncoderFactory(
                    eglBase!!.eglBaseContext,
                    /* enableIntelVp8Encoder */ true,
                    /* enableH264HighProfile */ true
                )
                val decoderFactory = DefaultVideoDecoderFactory(eglBase!!.eglBaseContext)

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

                logI("initialize() factory created successfully")
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

        // Reset SDP sent flag for new connection
        sdpSent = false
        remoteVideoSetUp = false

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
     */
    fun setCameraEnabled(enabled: Boolean) {
        logD("setCameraEnabled($enabled)")
        executor.execute {
            try {
                if (enabled && videoCapturer != null) {
                    videoCapturer?.startCapture(VIDEO_WIDTH, VIDEO_HEIGHT, VIDEO_FPS)
                    sendDataChannelMessage("CameraEnabled")
                } else if (!enabled && videoCapturer != null) {
                    videoCapturer?.stopCapture()
                    sendDataChannelMessage("CameraDisabled")
                }
                updateState { copy(isCameraEnabled = enabled) }
            } catch (e: Exception) {
                logE("setCameraEnabled() failed", e)
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
        audioManager.isSpeakerphoneOn = enabled
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
     * Hangup and cleanup
     */
    fun hangup() {
        logI("hangup()")
        sendDataChannelMessage("Hangup")
        updateState { copy(status = CallState.Status.ENDED) }
        cleanup()
    }

    /**
     * Cleanup all WebRTC resources
     * FIXED Jan 2026: Proper scope cancellation and executor shutdown
     */
    fun cleanup() {
        logD("cleanup()")

        // CRITICAL: Cancel coroutine scope FIRST (prevents memory leak)
        callScope.cancel()

        // Reset flags immediately (thread-safe due to @Volatile)
        remoteVideoSetUp = false
        sdpSent = false
        iceGatheringTimeoutJob?.cancel()
        iceGatheringTimeoutJob = null
        statsJob?.cancel()
        statsJob = null

        executor.execute {
            try {
                // Dispose video sinks first to prevent frame delivery during cleanup
                localVideoSink?.dispose()
                localVideoSink = null
                remoteVideoSink?.dispose()
                remoteVideoSink = null

                // Stop and dispose video capturer
                try {
                    videoCapturer?.stopCapture()
                } catch (e: Exception) {
                    logE("Error stopping video capturer", e)
                }
                try {
                    videoCapturer?.dispose()
                } catch (e: Exception) {
                    logE("Error disposing video capturer", e)
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

                // Close peer connection
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

                // Dispose factory
                try {
                    factory?.dispose()
                } catch (e: Exception) {
                    logE("Error disposing factory", e)
                }
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

        // Video track
        videoCapturer = createVideoCapturer()
        if (videoCapturer != null) {
            surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eglBase!!.eglBaseContext)
            videoSource = factory?.createVideoSource(videoCapturer!!.isScreencast)
            videoCapturer?.initialize(surfaceTextureHelper, context, videoSource?.capturerObserver)

            localVideoTrack = factory?.createVideoTrack(VIDEO_TRACK_ID, videoSource)
            localVideoSink = ProxyVideoSink()
            localVideoTrack?.addSink(localVideoSink)
            localVideoTrack?.setEnabled(true)
            peerConnection?.addTrack(localVideoTrack, listOf(STREAM_ID))

            // Start video capture immediately (like Meshenger)
            // Camera can be disabled later with setCameraEnabled(false)
            try {
                videoCapturer?.startCapture(VIDEO_WIDTH, VIDEO_HEIGHT, VIDEO_FPS)
                updateState { copy(isCameraEnabled = true) }
                logD("Video track added and camera started successfully")
            } catch (e: Exception) {
                logE("Failed to start camera", e)
            }
        } else {
            logW("No camera available - audio only call")
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
                channel.send(buffer)
                logD("Sent data channel message: $message")
            }
        }
    }

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
                    resetReconnectAttempts() // Reset counter on successful connection
                    updateState {
                        copy(
                            status = CallState.Status.CONNECTED,
                            startTime = startTime ?: System.currentTimeMillis()
                        )
                    }
                }
                PeerConnection.IceConnectionState.DISCONNECTED -> {
                    logW("ICE disconnected - attempting reconnection")
                    updateState { copy(status = CallState.Status.RECONNECTING) }
                    attemptIceRestart()
                }
                PeerConnection.IceConnectionState.FAILED -> {
                    logE("ICE connection failed")
                    if (reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
                        logI("Attempting reconnection (attempt ${reconnectAttempts + 1}/$MAX_RECONNECT_ATTEMPTS)")
                        updateState { copy(status = CallState.Status.RECONNECTING) }
                        attemptIceRestart()
                    } else {
                        logE("Max reconnection attempts reached")
                        updateState { copy(status = CallState.Status.ERROR, errorMessage = "Connection failed") }
                    }
                }
                PeerConnection.IceConnectionState.CLOSED -> {
                    updateState { copy(status = CallState.Status.ENDED) }
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
            logD("Renegotiation needed")
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
            val message = String(data)
            logD("Data channel message: $message")

            when {
                "Hangup" in message -> {
                    logI("Remote hangup received")
                    updateState { copy(status = CallState.Status.ENDED) }
                }
                "CameraEnabled" in message -> {
                    logD("Remote camera enabled")
                }
                "CameraDisabled" in message -> {
                    logD("Remote camera disabled")
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

    /**
     * Attempt ICE restart to recover disconnected call
     */
    private fun attemptIceRestart() {
        executor.execute {
            try {
                reconnectAttempts++
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

                // WhatsApp-style adaptive quality based on network conditions
                adaptVideoQuality(callStats)

            } catch (e: Exception) {
                logE("collectStats() error", e)
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

        // Reconnection settings
        private const val MAX_RECONNECT_ATTEMPTS = 3

        // ICE gathering timeout (like Meshenger - 3 seconds)
        private const val ICE_GATHERING_TIMEOUT_MS = 3000L

        // Stats collection interval
        private const val STATS_INTERVAL_MS = 2000L
    }
}
