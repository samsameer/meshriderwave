/*
 * Mesh Rider Wave - NEW PTT Manager (Working Implementation)
 * Following OUSHTALK MCPTT specification
 * Uses Oboe + Simple Floor Control + Multicast RTP
 */

package com.doodlelabs.meshriderwave.ptt

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * NEW PTT Manager - Working implementation based on OUSHTALK MCPTT spec
 * Following developer.android, developer.samsung, developer.kotlin guidelines
 *
 * Architecture:
 * - Oboe (C++): Low-latency audio capture/playback
 * - Opus: Codec for bandwidth efficiency (6-24kbps vs 256kbps PCM)
 * - Multicast RTP: O(1) delivery to unlimited peers
 * - Simple Floor Control: UDP-based signaling
 *
 * Per OUSHTALK spec targets:
 * - PTT Access Time: < 200ms (95%)
 * - Mouth-to-Ear Latency: < 250ms (95%)
 */
class WorkingPttManager(private val context: Context) {

    companion object {
        private const val TAG = "MeshRider:WorkingPTT"

        // Multicast groups per OUSHTALK
        private const val AUDIO_MULTICAST = "239.255.0.1"  // Audio
        private const val AUDIO_PORT = 5004

        private const val FLOOR_MULTICAST = "239.255.0.1"  // Signaling
        private const val FLOOR_PORT = 5005
    }

    // Components
    private val audioEngine = PttAudioEngine(context)
    private val floorControl = FloorControlProtocol(FLOOR_MULTICAST, FLOOR_PORT)

    // PTT State
    private val _isTransmitting = MutableStateFlow(false)
    val isTransmitting: StateFlow<Boolean> = _isTransmitting

    private val _isReceiving = MutableStateFlow(false)
    val isReceiving: StateFlow<Boolean> = _isReceiving

    private val _currentSpeaker = MutableStateFlow<String?>(null)
    val currentSpeaker: StateFlow<String?> = _currentSpeaker

    // Scope for coroutines
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Own identity
    var ownId: String = "unknown"

    /**
     * Initialize PTT system
     * Following Android best practices for initialization
     */
    fun initialize(myId: String): Boolean {
        Log.i(TAG, "Initializing Working PTT Manager for $myId")

        ownId = myId

        // Initialize audio engine
        val audioInitialized = audioEngine.initialize(AUDIO_MULTICAST, AUDIO_PORT)
        if (!audioInitialized) {
            Log.e(TAG, "Failed to initialize audio engine")
            return false
        }

        // Initialize floor control
        val floorInitialized = floorControl.initialize(myId)
        if (!floorInitialized) {
            Log.e(TAG, "Failed to initialize floor control")
            return false
        }

        // Setup floor control callbacks
        setupFloorCallbacks()

        // Start playback to receive audio
        audioEngine.startPlayback()

        Log.i(TAG, "Working PTT Manager initialized successfully")
        return true
    }

    /**
     * Start PTT transmission
     * Per OUSHTALK: Target < 200ms access time
     */
    suspend fun startTransmission(): Boolean {
        Log.i(TAG, "Starting PTT transmission")

        // Request floor
        val granted = floorControl.requestFloor(priority = 0)

        if (granted) {
            // Start audio capture
            val started = audioEngine.startCapture()

            if (started) {
                _isTransmitting.value = true
                Log.i(TAG, "PTT transmission started successfully")

                // Safety timeout (60s max per OUSHTALK)
                scope.launch {
                    delay(60000)
                    if (_isTransmitting.value) {
                        Log.w(TAG, "Auto-stopping transmission after 60s")
                        stopTransmission()
                    }
                }

                return true
            } else {
                // Failed to start capture, release floor
                floorControl.releaseFloor()
                Log.e(TAG, "Failed to start audio capture")
            }
        } else {
            Log.w(TAG, "Floor request denied")
        }

        return false
    }

    /**
     * Stop PTT transmission
     */
    fun stopTransmission() {
        Log.i(TAG, "Stopping PTT transmission")

        audioEngine.stopCapture()
        floorControl.releaseFloor()
        _isTransmitting.value = false
    }

    /**
     * Get current latency
     * Per OUSHTALK: Target < 250ms mouth-to-ear
     */
    fun getLatency(): Int {
        return audioEngine.getLatency()
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
     * Cleanup resources
     * Following Android lifecycle best practices
     */
    fun cleanup() {
        Log.i(TAG, "Cleaning up Working PTT Manager")

        stopTransmission()
        audioEngine.stopPlayback()
        audioEngine.cleanup()
        floorControl.stop()

        scope.cancel()
    }

    /**
     * Setup floor control callbacks
     */
    private fun setupFloorCallbacks() {
        floorControl.onFloorDenied = { speakerId ->
            Log.w(TAG, "Floor denied by $speakerId")
            _currentSpeaker.value = speakerId
        }

        floorControl.onFloorGranted = {
            Log.i(TAG, "Floor granted")
            _currentSpeaker.value = ownId
        }

        floorControl.onFloorTaken = { speakerId ->
            Log.d(TAG, "Floor taken by $speakerId")
            _currentSpeaker.value = speakerId
        }

        floorControl.onFloorReleased = {
            Log.d(TAG, "Floor released")
            _currentSpeaker.value = null
        }
    }
}
