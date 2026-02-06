/*
 * Mesh Rider Wave - PTT WebRTC Half-Duplex Mode
 * Following VideoSDK 2025 pattern: muteMic/unmuteMic for PTT
 * Per 3GPP MCPTT requirements for half-duplex communication
 *
 * PTT Flow:
 * 1. PTT DOWN → unmuteMic() → Take floor → Start transmitting
 * 2. PTT UP   → muteMic() → Release floor → Stop transmitting
 */

package com.doodlelabs.meshriderwave.ptt

import android.content.Context
import com.doodlelabs.meshriderwave.core.webrtc.RTCCall
import com.doodlelabs.meshriderwave.core.util.logD
import com.doodlelabs.meshriderwave.core.util.logE
import com.doodlelabs.meshriderwave.core.util.logI
import com.doodlelabs.meshriderwave.core.util.logW
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.webrtc.SessionDescription

/**
 * PTT WebRTC Manager - Half-duplex PTT over WebRTC
 *
 * This implements the modern 2025 PTT pattern using WebRTC mute/unmute:
 * - PTT press = unmuteMic (take floor, start transmitting)
 * - PTT release = muteMic (release floor, stop transmitting)
 *
 * References:
 * - VideoSDK 2025: muteMic/unmuteMic pattern
 * - 3GPP TS 24.379: MCPTT call control
 * - 3GPP TS 24.380: MCPTT media plane control
 */
class PttWebRtcManager(private val context: Context) {

    companion object {
        private const val TAG = "MeshRider:PttWebRTC"

        // PTT timeout (60s max per 3GPP MCPTT)
        private const val MAX_PTT_DURATION_MS = 60000L

        // Floor control timeout (ms)
        private const val FLOOR_TIMEOUT_MS = 200L
    }

    // WebRTC call (voice only for PTT)
    private var rtcCall: RTCCall? = null

    // Coroutine scope
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // PTT State
    private val _isPttPressed = MutableStateFlow(false)
    val isPttPressed: StateFlow<Boolean> = _isPttPressed.asStateFlow()

    private val _isTransmitting = MutableStateFlow(false)
    val isTransmitting: StateFlow<Boolean> = _isTransmitting.asStateFlow()

    private val _isReceiving = MutableStateFlow(false)
    val isReceiving: StateFlow<Boolean> = _isReceiving.asStateFlow()

    private val _floorOwner = MutableStateFlow<String?>(null)
    val floorOwner: StateFlow<String?> = _floorOwner.asStateFlow()

    // Own identity
    var ownId: String = "unknown"

    // Floor control callback
    var onFloorDenied: ((String) -> Unit)? = null
    var onFloorGranted: (() -> Unit)? = null
    var onFloorReleased: (() -> Unit)? = null

    /**
     * Initialize PTT WebRTC session
     * Creates one-way audio stream for PTT (receive-only until PTT pressed)
     */
    fun initialize(myId: String): Boolean {
        logI("Initializing PTT WebRTC for $myId")
        ownId = myId

        // Create voice-only WebRTC call
        rtcCall = RTCCall(context, isOutgoing = true, isVideoCall = false)

        // Initialize WebRTC factory
        rtcCall?.initialize()

        // Start with microphone MUTED (receive-only mode)
        rtcCall?.setMicrophoneEnabled(false)

        logI("PTT WebRTC initialized successfully")
        return true
    }

    /**
     * Start PTT transmission (half-duplex)
     * Following VideoSDK 2025 pattern: unmuteMic on PTT press
     *
     * This is equivalent to VideoSDK's unmuteMic() method
     */
    fun startPtt(): Boolean {
        logI("startPtt() - Requesting floor")

        // Check if floor is already taken by someone else
        val currentOwner = _floorOwner.value
        if (currentOwner != null && currentOwner != ownId) {
            logW("Floor denied: $currentOwner has floor")
            onFloorDenied?.invoke(currentOwner)
            return false
        }

        // Take the floor
        _isPttPressed.value = true
        _floorOwner.value = ownId

        // Unmute microphone (VideoSDK pattern)
        rtcCall?.setMicrophoneEnabled(true)

        _isTransmitting.value = true
        logI("PTT transmission started")

        onFloorGranted?.invoke()

        // Auto-stop after max duration (3GPP MCPTT requirement)
        scope.launch {
            delay(MAX_PTT_DURATION_MS)
            if (_isTransmitting.value) {
                logW("Auto-stopping PTT after ${MAX_PTT_DURATION_MS}ms")
                stopPtt()
            }
        }

        return true
    }

    /**
     * Stop PTT transmission
     * Following VideoSDK 2025 pattern: muteMic on PTT release
     *
     * This is equivalent to VideoSDK's muteMic() method
     */
    fun stopPtt() {
        logI("stopPtt() - Releasing floor")

        _isPttPressed.value = false
        _floorOwner.value = null

        // Mute microphone (VideoSDK pattern)
        rtcCall?.setMicrophoneEnabled(false)

        _isTransmitting.value = false
        logI("PTT transmission stopped")

        onFloorReleased?.invoke()
    }

    /**
     * Handle remote PTT state changes
     * Called when someone else starts/stops transmitting
     */
    fun onRemotePttStart(peerId: String) {
        logI("Remote PTT start: $peerId")

        // If we're not transmitting, let them have the floor
        if (!_isTransmitting.value) {
            _floorOwner.value = peerId
            _isReceiving.value = true
        }
    }

    fun onRemotePttStop(peerId: String) {
        logI("Remote PTT stop: $peerId")

        if (_floorOwner.value == peerId) {
            _floorOwner.value = null
            _isReceiving.value = false
        }
    }

    /**
     * Create peer connection for PTT session
     */
    fun createPeerConnection(offer: String? = null) {
        logD("createPeerConnection()")
        rtcCall?.createPeerConnection(offer)
    }

    /**
     * Handle remote SDP offer/answer
     */
    fun handleRemoteSdp(sdp: String, type: String) {
        logD("handleRemoteSdp() type=$type")
        when (type) {
            "offer" -> {
                // For incoming PTT calls, handle as answer
                // rtcCall will handle this internally
            }
            "answer" -> {
                rtcCall?.handleAnswer(sdp)
            }
        }
    }

    /**
     * Get local SDP offer
     */
    fun setOnLocalDescription(callback: (SessionDescription) -> Unit) {
        rtcCall?.onLocalDescription = callback
    }

    /**
     * Enable/disable speaker for PTT audio output
     */
    fun setSpeakerEnabled(enabled: Boolean) {
        logD("setSpeakerEnabled($enabled)")
        rtcCall?.setSpeakerEnabled(enabled)
    }

    /**
     * Check if currently transmitting
     */
    fun isTransmitting(): Boolean = _isTransmitting.value

    /**
     * Check if currently receiving
     */
    fun isReceiving(): Boolean = _isReceiving.value

    /**
     * Check if PTT button is pressed
     */
    fun isPttPressed(): Boolean = _isPttPressed.value

    /**
     * Cleanup PTT WebRTC resources
     */
    fun cleanup() {
        logI("Cleaning up PTT WebRTC")

        stopPtt()
        rtcCall?.cleanup()
        rtcCall = null

        scope.cancel()

        _isTransmitting.value = false
        _isReceiving.value = false
        _isPttPressed.value = false
        _floorOwner.value = null
    }

    /**
     * Get underlying RTCCall for advanced operations
     */
    fun getRtcCall(): RTCCall? = rtcCall

    /**
     * Connection state from underlying WebRTC
     */
    fun getConnectionState() = rtcCall?.state

    /**
     * PTT Audio Mode - Half-duplex configuration
     *
     * This implements the modern 2025 approach used by VideoSDK:
     *
     * ```kotlin
     * // VideoSDK-style PTT implementation:
     * pttButton.setOnTouchListener { _, event ->
     *     when (event.action) {
     *         MotionEvent.ACTION_DOWN -> pttWebRtcManager.startPtt()  // unmuteMic
     *         MotionEvent.ACTION_UP   -> pttWebRtcManager.stopPtt()   // muteMic
     *     }
     * }
     * ```
     *
     * Key differences from full-duplex calls:
     * 1. Microphone starts MUTED (receive-only)
     * 2. Only one person can transmit at a time (floor control)
     * 3. Fast toggle (200ms target per 3GPP MCPTT)
     * 4. Opus codec at 12kbps (vs 256kbps PCM)
     */
    enum class PttMode {
        /** Voice-only PTT (lowest latency) */
        VOICE_ONLY,

        /** PTT with video (future) */
        VIDEO_PTT
    }

    /**
     * Get PTT statistics
     */
    fun getPttStats(): PttStats {
        val callStats = rtcCall?.stats?.value
        return PttStats(
            isTransmitting = _isTransmitting.value,
            isReceiving = _isReceiving.value,
            floorOwner = _floorOwner.value,
            roundTripTimeMs = callStats?.roundTripTimeMs ?: 0,
            jitterMs = callStats?.jitterMs ?: 0,
            packetsLost = callStats?.packetsLost ?: 0
        )
    }

    /**
     * PTT Statistics data class
     */
    data class PttStats(
        val isTransmitting: Boolean,
        val isReceiving: Boolean,
        val floorOwner: String?,
        val roundTripTimeMs: Long,
        val jitterMs: Long,
        val packetsLost: Long
    ) {
        val latencyMs: Long get() = roundTripTimeMs / 2  // One-way latency
    }
}
