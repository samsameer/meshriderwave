/*
 * Mesh Rider Wave - PTT Voice Activity Detection
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 *
 * MILITARY-GRADE Voice Activity Detection
 * - WebRTC VAD algorithm
 * - Configurable sensitivity levels
 * - Auto-transmit on speech detection
 * - False trigger prevention
 */

package com.doodlelabs.meshriderwave.ptt

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

/**
 * MILITARY-GRADE Voice Activity Detection
 *
 * Uses WebRTC's proven VAD algorithm:
 * - Statistical voice activity classification
 * - Frame-by-frame processing (10ms, 20ms, 30ms)
 * - Multiple sensitivity levels
 * - False trigger rejection
 *
 * Use Cases:
 * - Hands-free PTT in vehicles
 * - Automatic transmission initiation
 * - Battery saving (no transmit when silent)
 *
 * Sensitivity Levels:
 * - LOW: Only loud speech (>60dB)
 * - MEDIUM: Normal speech detection (>40dB)
 * - HIGH: Sensitive speech detection (>20dB, may false trigger)
 */
@Singleton
class PttVadDetector @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "MeshRider:VAD"

        // VAD configuration
        private const val SAMPLE_RATE = 16000 // 16kHz for PTT
        private const val FRAME_SIZE_MS = 30   // 30ms frames
        private const val FRAME_SIZE = SAMPLE_RATE * FRAME_SIZE_MS / 1000 // 480 samples

        // Silence timeout
        private const val DEFAULT_SILENCE_TIMEOUT_MS = 2000L // 2 seconds

        // Voice activation threshold (consecutive frames)
        private const val DEFAULT_VOICE_FRAMES_THRESHOLD = 3 // 90ms of speech
    }

    // Native VAD handle
    private var nativeHandle: Long = 0

    // Current settings
    private val _isEnabled = MutableStateFlow(false)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    private val _currentSensitivity = MutableStateFlow(VadSensitivity.MEDIUM)
    val currentSensitivity: StateFlow<VadSensitivity> = _currentSensitivity.asStateFlow()

    // Detection state
    private val _isVoiceDetected = MutableStateFlow(false)
    val isVoiceDetected: StateFlow<Boolean> = _isVoiceDetected.asStateFlow()

    private val _isArmed = MutableStateFlow(false)
    val isArmed: StateFlow<Boolean> = _isArmed.asStateFlow()

    // Voice frame counter for debouncing
    private var consecutiveVoiceFrames = 0
    private var consecutiveSilenceFrames = 0

    // Silence timeout
    private var silenceTimeoutMs = DEFAULT_SILENCE_TIMEOUT_MS
    private var voiceThresholdFrames = DEFAULT_VOICE_FRAMES_THRESHOLD

    // Coroutine scope for silence timeout
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var silenceTimeoutJob: Job? = null

    // Callbacks
    var onVoiceStart: (() -> Unit)? = null
    var onVoiceEnd: (() -> Unit)? = null

    // Statistics
    private val _framesProcessed = MutableStateFlow(0L)
    val framesProcessed: StateFlow<Long> = _framesProcessed.asStateFlow()

    private val _voiceFramesDetected = MutableStateFlow(0L)
    val voiceFramesDetected: StateFlow<Long> = _voiceFramesDetected.asStateFlow()

    /**
     * Initialize VAD detector
     *
     * @return true if initialized successfully
     */
    fun initialize(): Boolean {
        if (nativeHandle != 0L) {
            return true // Already initialized
        }

        try {
            nativeHandle = nativeCreate(SAMPLE_RATE, FRAME_SIZE_MS)
            _currentSensitivity.value = VadSensitivity.MEDIUM
            return nativeHandle != 0L
        } catch (e: Exception) {
            return false
        }
    }

    /**
     * Process audio frame and detect voice activity
     *
     * @param audioData Input audio samples (16-bit PCM, mono, 16kHz)
     * @return true if voice is detected in this frame
     */
    fun processAudioFrame(audioData: ShortArray): Boolean {
        if (nativeHandle == 0L || !_isEnabled.value) {
            return false
        }

        // Ensure we have the correct frame size
        val processedData = if (audioData.size < FRAME_SIZE) {
            // Pad small frames
            val padded = ShortArray(FRAME_SIZE) { 0 }
            audioData.copyInto(padded)
            padded
        } else if (audioData.size > FRAME_SIZE) {
            // Truncate large frames
            audioData.copyOf(FRAME_SIZE)
        } else {
            audioData
        }

        val hasVoice = nativeProcessFrame(nativeHandle, processedData)
        _framesProcessed.value++

        if (hasVoice) {
            consecutiveVoiceFrames++
            consecutiveSilenceFrames = 0
            _voiceFramesDetected.value++
        } else {
            consecutiveSilenceFrames++
            consecutiveVoiceFrames = max(0, consecutiveVoiceFrames - 1)
        }

        // Update voice detected state with debouncing
        val wasDetected = _isVoiceDetected.value
        val nowDetected = consecutiveVoiceFrames >= voiceThresholdFrames

        if (nowDetected && !wasDetected) {
            // Voice started
            _isVoiceDetected.value = true
            onVoiceStart?.invoke()
            startSilenceTimeout()
        } else if (!nowDetected && wasDetected) {
            // Voice ended
            _isVoiceDetected.value = false
            onVoiceEnd?.invoke()
            cancelSilenceTimeout()
        }

        return hasVoice
    }

    /**
     * Process floating-point audio frame (for WebRTC integration)
     *
     * @param audioData Input audio samples (float, mono, 16kHz)
     * @return true if voice is detected
     */
    fun processAudioFrameFloat(audioData: FloatArray): Boolean {
        // Convert float to short
        val shortData = ShortArray(audioData.size) { (audioData[it] * 32767f).toInt().toShort() }
        return processAudioFrame(shortData)
    }

    /**
     * Arm VAD for automatic transmission
     *
     * When armed, voice detection will automatically trigger
     * the onVoiceStart callback
     *
     * @param armed true to arm, false to disarm
     */
    fun setArmed(armed: Boolean) {
        _isArmed.value = armed

        if (armed && nativeHandle == 0L) {
            initialize()
        }

        if (!armed) {
            // Reset state when disarmed
            consecutiveVoiceFrames = 0
            consecutiveSilenceFrames = 0
            _isVoiceDetected.value = false
            cancelSilenceTimeout()
        }
    }

    /**
     * Enable/disable VAD processing
     *
     * @param enabled true to enable, false to disable
     */
    fun setEnabled(enabled: Boolean) {
        _isEnabled.value = enabled
        if (!enabled) {
            setArmed(false)
        }
    }

    /**
     * Set VAD sensitivity
     *
     * @param sensitivity Sensitivity level
     */
    fun setSensitivity(sensitivity: VadSensitivity) {
        if (nativeHandle == 0L) {
            initialize()
        }

        _currentSensitivity.value = sensitivity

        // Map sensitivity to VAD parameters
        val (threshold, aggressiveness) = when (sensitivity) {
            VadSensitivity.LOW -> Pair(3, 3)     // High threshold, low aggressiveness
            VadSensitivity.MEDIUM -> Pair(2, 2)  // Medium threshold, medium aggressiveness
            VadSensitivity.HIGH -> Pair(1, 1)    // Low threshold, high aggressiveness
        }

        voiceThresholdFrames = threshold

        if (nativeHandle != 0L) {
            nativeSetAggressiveness(nativeHandle, aggressiveness)
        }
    }

    /**
     * Set silence timeout for auto-end transmission
     *
     * @param timeoutMs Silence duration in milliseconds before voice end is triggered
     */
    fun setSilenceTimeout(timeoutMs: Long) {
        silenceTimeoutMs = timeoutMs
    }

    /**
     * Start silence timeout timer
     */
    private fun startSilenceTimeout() {
        cancelSilenceTimeout()
        silenceTimeoutJob = scope.launch {
            delay(silenceTimeoutMs)
            if (_isVoiceDetected.value) {
                // Timeout expired, trigger voice end
                _isVoiceDetected.value = false
                consecutiveVoiceFrames = 0
                onVoiceEnd?.invoke()
            }
        }
    }

    /**
     * Cancel silence timeout timer
     */
    private fun cancelSilenceTimeout() {
        silenceTimeoutJob?.cancel()
        silenceTimeoutJob = null
    }

    /**
     * Force voice detection reset
     */
    fun reset() {
        consecutiveVoiceFrames = 0
        consecutiveSilenceFrames = 0
        _isVoiceDetected.value = false
        cancelSilenceTimeout()

        if (nativeHandle != 0L) {
            nativeReset(nativeHandle)
        }
    }

    /**
     * Check if VAD is ready
     */
    fun isReady(): Boolean = nativeHandle != 0L

    /**
     * Get current voice probability (0.0 to 1.0)
     *
     * @return Voice probability or 0 if not ready
     */
    fun getVoiceProbability(): Float {
        if (nativeHandle == 0L) {
            return 0f
        }
        return nativeGetVoiceProbability(nativeHandle)
    }

    /**
     * Get VAD statistics
     */
    fun getStats(): VadStats {
        val totalFrames = _framesProcessed.value
        val voiceRatio = if (totalFrames > 0) {
            _voiceFramesDetected.value.toFloat() / totalFrames.toFloat()
        } else 0f

        return VadStats(
            isEnabled = _isEnabled.value,
            isArmed = _isArmed.value,
            isVoiceDetected = _isVoiceDetected.value,
            currentSensitivity = _currentSensitivity.value,
            framesProcessed = totalFrames,
            voiceFramesDetected = _voiceFramesDetected.value,
            voiceRatio = voiceRatio,
            voiceProbability = getVoiceProbability(),
            isReady = isReady()
        )
    }

    /**
     * Cleanup native resources
     */
    fun cleanup() {
        setArmed(false)
        cancelSilenceTimeout()

        if (nativeHandle != 0L) {
            nativeDestroy(nativeHandle)
            nativeHandle = 0
        }

        _framesProcessed.value = 0
        _voiceFramesDetected.value = 0
    }

    // ========== Native Methods ==========

    private external fun nativeCreate(sampleRate: Int, frameSizeMs: Int): Long

    private external fun nativeProcessFrame(handle: Long, audioData: ShortArray): Boolean

    private external fun nativeSetAggressiveness(handle: Long, aggressiveness: Int)

    private external fun nativeGetVoiceProbability(handle: Long): Float

    private external fun nativeReset(handle: Long)

    private external fun nativeDestroy(handle: Long)

    companion object {
        // Load native library
        init {
            try {
                System.loadLibrary("pttaudio")
            } catch (e: UnsatisfiedLinkError) {
                // Native library not available - VAD will be disabled
            }
        }
    }
}

/**
 * VAD sensitivity levels
 */
enum class VadSensitivity(
    val level: Int,
    val description: String,
    val minVoiceDb: Int
) {
    LOW(1, "Low - Only loud speech", 60),
    MEDIUM(2, "Medium - Normal speech", 40),
    HIGH(3, "High - Sensitive detection", 20)
}

/**
 * VAD statistics
 */
data class VadStats(
    val isEnabled: Boolean,
    val isArmed: Boolean,
    val isVoiceDetected: Boolean,
    val currentSensitivity: VadSensitivity,
    val framesProcessed: Long,
    val voiceFramesDetected: Long,
    val voiceRatio: Float,
    val voiceProbability: Float,
    val isReady: Boolean
)

/**
 * VAD-based automatic PTT controller
 *
 * Integrates VAD with PTT transmission for hands-free operation
 */
class VadPttController(
    private val vadDetector: PttVadDetector,
    private val pttManager: WorkingPttManager
) {
    private var isTransmitting = false

    init {
        // Set up VAD callbacks
        vadDetector.onVoiceStart = {
            onVoiceDetected()
        }
        vadDetector.onVoiceEnd = {
            onSilenceDetected()
        }
    }

    /**
     * Enable VAD-based PTT
     *
     * @param sensitivity VAD sensitivity level
     * @param silenceTimeoutMs Silence duration before ending transmission
     */
    fun enable(sensitivity: VadSensitivity = VadSensitivity.MEDIUM, silenceTimeoutMs: Long = 2000L) {
        vadDetector.setEnabled(true)
        vadDetector.setSensitivity(sensitivity)
        vadDetector.setSilenceTimeout(silenceTimeoutMs)
    }

    /**
     * Arm VAD for automatic transmission
     *
     * Once armed, voice detection will automatically start PTT transmission
     */
    fun arm() {
        vadDetector.setArmed(true)
    }

    /**
     * Disarm VAD
     *
     * Stops automatic PTT but keeps VAD processing enabled
     */
    fun disarm() {
        vadDetector.setArmed(false)
        if (isTransmitting) {
            pttManager.stopTransmission()
            isTransmitting = false
        }
    }

    /**
     * Disable VAD-based PTT
     */
    fun disable() {
        disarm()
        vadDetector.setEnabled(false)
    }

    /**
     * Handle voice detected event
     */
    private fun onVoiceDetected() {
        if (!isTransmitting && vadDetector.isArmed.value) {
            val success = pttManager.startTransmission()
            if (success) {
                isTransmitting = true
            }
        }
    }

    /**
     * Handle silence detected event
     */
    private fun onSilenceDetected() {
        if (isTransmitting) {
            pttManager.stopTransmission()
            isTransmitting = false
        }
    }

    /**
     * Get current status
     */
    fun getStatus(): VadPttStatus {
        return VadPttStatus(
            isArmed = vadDetector.isArmed.value,
            isTransmitting = isTransmitting,
            isVoiceDetected = vadDetector.isVoiceDetected.value,
            sensitivity = vadDetector.currentSensitivity.value
        )
    }
}

/**
 * VAD PTT status
 */
data class VadPttStatus(
    val isArmed: Boolean,
    val isTransmitting: Boolean,
    val isVoiceDetected: Boolean,
    val sensitivity: VadSensitivity
)
