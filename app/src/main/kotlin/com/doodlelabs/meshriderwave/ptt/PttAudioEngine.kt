/*
 * Mesh Rider Wave - PTT Audio Engine (PRODUCTION-READY)
 * Following Kotlin/Android best practices
 * Uses native C++ Oboe audio engine with Opus codec
 * 
 * FIXED (Feb 2026):
 * - Proper pipeline: AudioRecord -> Opus -> RTP -> Network
 * - Unicast fallback for networks that block multicast
 * - AEC (Acoustic Echo Cancellation) support
 * - Comprehensive error handling
 * - Network statistics
 */

package com.doodlelabs.meshriderwave.ptt

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * PTT Audio Engine - Production-ready low latency audio
 * 
 * Architecture:
 * - Oboe (C++): Low-latency audio capture/playback
 * - Opus: 10-40x bandwidth reduction (256kbps -> 6-24kbps)
 * - RTP/UDP: Multicast with unicast fallback
 * - AEC: Acoustic Echo Cancellation for speakerphone
 * 
 * Per OUSHTALK spec targets:
 * - PTT Access Time: < 200ms (95%)
 * - Mouth-to-Ear Latency: < 250ms (95%)
 */
class PttAudioEngine(private val context: Context) {

    companion object {
        private const val TAG = "MeshRider:PTT-Audio"

        // Multicast RTP configuration
        private const val DEFAULT_MULTICAST_GROUP = "239.255.0.1"
        private const val DEFAULT_PORT = 5004

        // Load native library
        init {
            try {
                System.loadLibrary("meshriderptt")
                Log.i(TAG, "Native library loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load native library: ${e.message}")
            }
        }
    }

    // Audio state
    private val _isCapturing = MutableStateFlow(false)
    val isCapturing: StateFlow<Boolean> = _isCapturing

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _latency = MutableStateFlow(0)
    val latency: StateFlow<Int> = _latency

    // Network state
    private val _isUsingMulticast = MutableStateFlow(true)
    val isUsingMulticast: StateFlow<Boolean> = _isUsingMulticast

    private val _packetsSent = MutableStateFlow(0L)
    val packetsSent: StateFlow<Long> = _packetsSent

    private val _packetsReceived = MutableStateFlow(0L)
    val packetsReceived: StateFlow<Long> = _packetsReceived

    // Audio focus for Android compatibility
    private var audioFocusRequest: AudioFocusRequest? = null
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    // Current configuration
    private var currentMulticastGroup: String = DEFAULT_MULTICAST_GROUP
    private var currentPort: Int = DEFAULT_PORT
    private var unicastPeers = mutableListOf<String>()

    /**
     * Initialize the audio engine
     * @param multicastGroup Multicast group address (default: 239.255.0.1)
     * @param port RTP port (default: 5004)
     * @param enableUnicastFallback Enable unicast fallback if multicast fails
     * @return true if successful
     */
    fun initialize(
        multicastGroup: String = DEFAULT_MULTICAST_GROUP,
        port: Int = DEFAULT_PORT,
        enableUnicastFallback: Boolean = true
    ): Boolean {
        Log.i(TAG, "Initializing PTT audio engine: group=$multicastGroup, port=$port, fallback=$enableUnicastFallback")

        currentMulticastGroup = multicastGroup
        currentPort = port

        val success = nativeInitialize(multicastGroup, port, enableUnicastFallback)

        if (success) {
            _isUsingMulticast.value = nativeIsUsingMulticast()
            Log.i(TAG, "PTT audio engine initialized successfully (multicast=${_isUsingMulticast.value})")
            
            // Start playback immediately to receive audio
            startPlayback()
        } else {
            Log.e(TAG, "Failed to initialize PTT audio engine")
        }

        return success
    }

    /**
     * Start audio capture (PTT TX)
     * Following Android audio focus guidelines
     */
    fun startCapture(): Boolean {
        Log.i(TAG, "Requesting audio capture")

        // Request audio focus for PTT
        requestAudioFocus()

        val success = nativeStartCapture()

        if (success) {
            _isCapturing.value = true
            Log.i(TAG, "Audio capture started successfully")
        } else {
            Log.e(TAG, "Failed to start audio capture")
            abandonAudioFocus()
        }

        return success
    }

    /**
     * Stop audio capture (PTT TX release)
     */
    fun stopCapture() {
        Log.i(TAG, "Stopping audio capture")

        nativeStopCapture()
        _isCapturing.value = false
        abandonAudioFocus()

        // Update stats
        _packetsSent.value = nativeGetPacketsSent().toLong()

        Log.i(TAG, "Audio capture stopped")
    }

    /**
     * Start audio playback (PTT RX)
     */
    fun startPlayback(): Boolean {
        Log.i(TAG, "Starting audio playback")

        val success = nativeStartPlayback()

        if (success) {
            _isPlaying.value = true
            Log.i(TAG, "Audio playback started successfully")
        } else {
            Log.e(TAG, "Failed to start audio playback")
        }

        return success
    }

    /**
     * Stop audio playback
     */
    fun stopPlayback() {
        Log.i(TAG, "Stopping audio playback")

        nativeStopPlayback()
        _isPlaying.value = false

        Log.i(TAG, "Audio playback stopped")
    }

    /**
     * Get current latency in milliseconds
     */
    fun getLatency(): Int {
        val lat = nativeGetLatencyMillis()
        _latency.value = lat
        return lat
    }

    /**
     * Add unicast peer for fallback mode
     * Use when multicast is not available (e.g., some WiFi networks)
     */
    fun addUnicastPeer(ipAddress: String) {
        Log.i(TAG, "Adding unicast peer: $ipAddress")
        nativeAddUnicastPeer(ipAddress)
        unicastPeers.add(ipAddress)
    }

    /**
     * Clear all unicast peers
     */
    fun clearUnicastPeers() {
        Log.i(TAG, "Clearing unicast peers")
        nativeClearUnicastPeers()
        unicastPeers.clear()
    }

    /**
     * Update network statistics
     */
    fun updateStats() {
        _packetsSent.value = nativeGetPacketsSent().toLong()
        _packetsReceived.value = nativeGetPacketsReceived().toLong()
        _latency.value = nativeGetLatencyMillis()
    }

    /**
     * Enable/disable Acoustic Echo Cancellation
     * Should be enabled when using speakerphone
     */
    fun enableAEC(enable: Boolean) {
        Log.i(TAG, "Setting AEC: $enable")
        nativeEnableAEC(enable)
    }

    /**
     * Set Opus bitrate
     * @param bitrate Bitrate in bps (6000-24000)
     */
    fun setBitrate(bitrate: Int) {
        val clampedBitrate = bitrate.coerceIn(6000, 24000)
        Log.i(TAG, "Setting bitrate to $clampedBitrate bps")
        nativeSetBitrate(clampedBitrate)
    }

    /**
     * Release native resources
     * Following Android lifecycle best practices
     */
    fun cleanup() {
        Log.i(TAG, "Cleaning up PTT audio engine")

        stopCapture()
        stopPlayback()
        nativeCleanup()

        _isCapturing.value = false
        _isPlaying.value = false
    }

    // Audio focus management per Android guidelines
    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAcceptsDelayedFocusGain(true)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setOnAudioFocusChangeListener { focusChange ->
                    when (focusChange) {
                        AudioManager.AUDIOFOCUS_LOSS -> {
                            Log.w(TAG, "Audio focus lost")
                            stopCapture()
                            stopPlayback()
                        }
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                            Log.w(TAG, "Transient audio focus lost")
                            stopCapture()
                        }
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                            Log.d(TAG, "Focus loss transient can duck")
                        }
                    }
                }
                .build()

            val result = audioManager.requestAudioFocus(audioFocusRequest!!)
            Log.d(TAG, "Audio focus request result: $result")
        } else {
            @Suppress("DEPRECATION")
            val result = audioManager.requestAudioFocus(
                { focusChange ->
                    when (focusChange) {
                        AudioManager.AUDIOFOCUS_LOSS,
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                            stopCapture()
                            stopPlayback()
                        }
                    }
                },
                AudioManager.STREAM_VOICE_CALL,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            )
            Log.d(TAG, "Audio focus request result (legacy): $result")
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let {
                audioManager.abandonAudioFocusRequest(it)
                audioFocusRequest = null
            }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus { }
        }
    }

    // Native methods
    private external fun nativeInitialize(
        multicastGroup: String,
        port: Int,
        enableUnicastFallback: Boolean
    ): Boolean

    private external fun nativeStartCapture(): Boolean
    private external fun nativeStopCapture()
    private external fun nativeStartPlayback(): Boolean
    private external fun nativeStopPlayback()
    private external fun nativeIsCapturing(): Boolean
    private external fun nativeIsPlaying(): Boolean
    private external fun nativeGetLatencyMillis(): Int
    private external fun nativeCleanup()
    
    // New native methods for production
    private external fun nativeAddUnicastPeer(peerAddress: String)
    private external fun nativeClearUnicastPeers()
    private external fun nativeGetPacketsSent(): Int
    private external fun nativeGetPacketsReceived(): Int
    private external fun nativeIsUsingMulticast(): Boolean
    private external fun nativeSetBitrate(bitrate: Int)
    private external fun nativeEnableAEC(enable: Boolean)

    /**
     * Enqueue received audio data from the network
     * This is called when RTP audio is received and needs to be played
     *
     * Note: The native C++ layer automatically handles RTP receive via the
     * internal callback in JniBridge.cpp. This method is provided for
     * scenarios where audio needs to be enqueued from Kotlin.
     *
     * @param data Opus-encoded audio data (or PCM if Opus is disabled)
     */
    external fun nativeEnqueueAudio(data: ByteArray)

    /**
     * Public method to enqueue received audio from Kotlin
     * Can be used when audio is received through a custom transport
     */
    fun enqueueAudio(data: ByteArray) {
        if (_isPlaying.value) {
            nativeEnqueueAudio(data)
        }
    }
}
