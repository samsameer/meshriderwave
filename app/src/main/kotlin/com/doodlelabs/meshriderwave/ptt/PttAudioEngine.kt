/*
 * Mesh Rider Wave - PTT Audio Engine (Kotlin Wrapper)
 * Following Kotlin/Android best practices
 * Uses native C++ Oboe audio engine
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
 * PTT Audio Engine - Low latency audio using Oboe
 * Following developer.android Oboe guidelines
 *
 * Per official documentation:
 * - Oboe: https://developer.android.com/games/sdk/oboe
 * - AAudio: https://developer.android.com/ndk/guides/audio/aaudio/aaudio
 * - Low Latency: https://developer.android.com/games/sdk/oboe/low-latency-audio
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

    // Audio focus for Android compatibility
    private var audioFocusRequest: AudioFocusRequest? = null
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    /**
     * Initialize the audio engine
     * @param multicastGroup Multicast group address (default: 239.255.0.1)
     * @param port RTP port (default: 5004)
     * @return true if successful
     */
    fun initialize(
        multicastGroup: String = DEFAULT_MULTICAST_GROUP,
        port: Int = DEFAULT_PORT
    ): Boolean {
        Log.i(TAG, "Initializing PTT audio engine: group=$multicastGroup, port=$port")

        val success = nativeInitialize(multicastGroup, port)

        if (success) {
            Log.i(TAG, "PTT audio engine initialized successfully")
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
        return nativeGetLatencyMillis()
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
                AudioManager.STREAM_MUSIC,
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
        port: Int
    ): Boolean

    private external fun nativeStartCapture(): Boolean
    private external fun nativeStopCapture()
    private external fun nativeStartPlayback(): Boolean
    private external fun nativeStopPlayback()
    private external fun nativeIsCapturing(): Boolean
    private external fun nativeIsPlaying(): Boolean
    private external fun nativeGetLatencyMillis(): Int
    private external fun nativeCleanup()
    private external fun nativeSendAudio(audioData: ByteArray, isMarker: Boolean): Boolean
}
