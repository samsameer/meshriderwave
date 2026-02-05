/*
 * Mesh Rider Wave - Multicast Audio Manager
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 *
 * High-level manager for PTT audio over multicast RTP.
 *
 * Pipeline:
 *   TX: Microphone → Opus Encoder → RTP Packetizer → Multicast UDP
 *   RX: Multicast UDP → RTP Depacketizer → Jitter Buffer → Opus Decoder → Speaker
 *
 * Bandwidth:
 *   Raw PCM: 256 kbps per stream
 *   Opus:    6-24 kbps per stream (10-40x reduction)
 *
 * Transport:
 *   Unicast TCP: O(n) connections for n peers (old approach)
 *   Multicast UDP: O(1) for any number of peers (new approach)
 *
 * QoS:
 *   DSCP EF (46) for expedited forwarding
 *   Voice traffic prioritized over data
 */

package com.doodlelabs.meshriderwave.core.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.*
import android.os.Build
import com.doodlelabs.meshriderwave.core.util.logD
import com.doodlelabs.meshriderwave.core.util.logE
import com.doodlelabs.meshriderwave.core.util.logI
import com.doodlelabs.meshriderwave.core.util.logW
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages PTT audio transmission and reception over multicast RTP
 */
@Singleton
class MulticastAudioManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val opusCodec: OpusCodecManager,
    private val rtpManager: RTPPacketManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // State
    private val isTransmitting = AtomicBoolean(false)
    private val isReceiving = AtomicBoolean(false)
    private var isInitialized = false

    // Audio components
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null

    // Current talkgroup
    private var activeTalkgroup: Int = 0

    // Jobs for TX/RX loops
    private var transmitJob: Job? = null
    private var receiveJob: Job? = null

    // Events
    private val _audioEvents = MutableSharedFlow<AudioEvent>()
    val audioEvents: SharedFlow<AudioEvent> = _audioEvents.asSharedFlow()

    // Statistics
    private var txFrameCount = 0L
    private var rxFrameCount = 0L
    private var txStartTime = 0L

    companion object {
        // Audio configuration
        const val SAMPLE_RATE = 16000
        const val CHANNELS = 1
        const val FRAME_SIZE_MS = 20
        const val FRAME_SIZE_SAMPLES = SAMPLE_RATE * FRAME_SIZE_MS / 1000  // 320 samples
        const val FRAME_SIZE_BYTES = FRAME_SIZE_SAMPLES * 2  // 640 bytes (16-bit)

        // Voice Activity Detection thresholds
        const val VAD_SILENCE_THRESHOLD = 500   // Below this = silence
        const val VAD_VOICE_THRESHOLD = 2000    // Above this = definitely voice

        // Maximum talkgroups supported
        const val MAX_TALKGROUPS = 255
    }

    /**
     * Audio events for UI updates
     */
    sealed class AudioEvent {
        data class TransmitStarted(val talkgroupId: Int) : AudioEvent()
        data class TransmitEnded(val talkgroupId: Int, val durationMs: Long, val frames: Long) : AudioEvent()
        data class ReceiveStarted(val talkgroupId: Int, val ssrc: Long) : AudioEvent()
        data class ReceiveEnded(val talkgroupId: Int) : AudioEvent()
        data class VoiceActivity(val level: Float) : AudioEvent()
        data class Error(val message: String) : AudioEvent()
        data class Stats(val stats: AudioStats) : AudioEvent()
    }

    /**
     * Audio statistics
     */
    data class AudioStats(
        val isTransmitting: Boolean,
        val isReceiving: Boolean,
        val activeTalkgroup: Int,
        val txFrameCount: Long,
        val rxFrameCount: Long,
        val codecStats: OpusCodecManager.CodecStats?,
        val rtpStats: RTPPacketManager.RTPStats?
    )

    /**
     * Initialize the audio manager
     *
     * @param codecBitrate Opus bitrate (6000-24000)
     * @param networkInterface Network interface name (e.g., "wlan0")
     */
    fun initialize(
        codecBitrate: Int = 24000,  // Feb 2026: Bumped from 12kbps to 24kbps for clearer voice
        networkInterface: String? = null
    ): Boolean {
        if (isInitialized) {
            logW("MulticastAudioManager already initialized")
            return true
        }

        try {
            logI("Initializing MulticastAudioManager: ${codecBitrate/1000}kbps")

            // Initialize Opus codec
            val codecConfig = OpusCodecManager.CodecConfig(
                sampleRate = SAMPLE_RATE,
                channels = CHANNELS,
                bitrate = codecBitrate,
                frameSize = FRAME_SIZE_SAMPLES
            )

            if (!opusCodec.initialize(codecConfig)) {
                logE("Failed to initialize Opus codec")
                return false
            }

            // Initialize RTP manager
            if (!rtpManager.initialize(networkInterface)) {
                logE("Failed to initialize RTP manager")
                opusCodec.release()
                return false
            }

            isInitialized = true
            logI("MulticastAudioManager initialized successfully")
            return true
        } catch (e: Exception) {
            logE("Exception initializing MulticastAudioManager", e)
            return false
        }
    }

    /**
     * Join a talkgroup
     *
     * @param talkgroupId Talkgroup number (1-255)
     */
    fun joinTalkgroup(talkgroupId: Int): Boolean {
        if (!isInitialized) {
            logE("Cannot join talkgroup: not initialized")
            return false
        }

        if (talkgroupId !in 1..MAX_TALKGROUPS) {
            logE("Invalid talkgroup ID: $talkgroupId")
            return false
        }

        val success = rtpManager.joinGroup(talkgroupId)
        if (success) {
            activeTalkgroup = talkgroupId
            startReceiveLoop()
            logI("Joined talkgroup $talkgroupId")
        }
        return success
    }

    /**
     * Leave current talkgroup
     */
    fun leaveTalkgroup() {
        if (activeTalkgroup > 0) {
            stopTransmit()
            stopReceiveLoop()
            rtpManager.leaveGroup("${RTPPacketManager.MULTICAST_BASE}$activeTalkgroup")
            logI("Left talkgroup $activeTalkgroup")
            activeTalkgroup = 0
        }
    }

    /**
     * Start PTT transmission
     */
    @SuppressLint("MissingPermission")
    fun startTransmit(): Boolean {
        if (!isInitialized) {
            logE("Cannot transmit: not initialized")
            return false
        }

        if (activeTalkgroup <= 0) {
            logE("Cannot transmit: no active talkgroup")
            return false
        }

        if (isTransmitting.get()) {
            logD("Already transmitting")
            return true
        }

        try {
            // Create AudioRecord
            val bufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            // Feb 2026 FIX: MIC not VOICE_COMMUNICATION — Samsung DSP makes voice scary/robotic
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize * 2
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                logE("AudioRecord failed to initialize")
                audioRecord?.release()
                audioRecord = null
                return false
            }

            audioRecord?.startRecording()
            isTransmitting.set(true)
            txFrameCount = 0
            txStartTime = System.currentTimeMillis()

            // Feb 2026: Half-duplex — mute speaker while transmitting (like real walkie-talkie)
            audioTrack?.let { it.setVolume(0f) }
            logD("Half-duplex: speaker muted during TX")

            // Start transmit loop with 60s safety timeout
            transmitJob = scope.launch {
                transmitLoop()
            }

            // Feb 2026: Safety timeout — auto-stop after 60s to prevent infinite TX
            scope.launch {
                kotlinx.coroutines.delay(60_000)
                if (isTransmitting.get()) {
                    logW("SAFETY TIMEOUT: Auto-stopping transmission after 60s")
                    stopTransmit()
                }
            }

            scope.launch {
                _audioEvents.emit(AudioEvent.TransmitStarted(activeTalkgroup))
            }

            logI("Started transmitting on talkgroup $activeTalkgroup")
            return true
        } catch (e: Exception) {
            logE("Failed to start transmission", e)
            scope.launch { _audioEvents.emit(AudioEvent.Error("Failed to start: ${e.message}")) }
            return false
        }
    }

    /**
     * Stop PTT transmission
     */
    fun stopTransmit() {
        if (!isTransmitting.get()) return

        isTransmitting.set(false)
        transmitJob?.cancel()
        transmitJob = null

        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null

        val durationMs = System.currentTimeMillis() - txStartTime

        scope.launch {
            _audioEvents.emit(AudioEvent.TransmitEnded(activeTalkgroup, durationMs, txFrameCount))
        }

        // Feb 2026: Half-duplex — unmute speaker after TX
        audioTrack?.let { it.setVolume(1f) }
        logD("Half-duplex: speaker unmuted after TX")

        logI("Stopped transmitting: $txFrameCount frames in ${durationMs}ms")
    }

    /**
     * Transmit loop - captures, encodes, and sends audio
     */
    private suspend fun transmitLoop() = withContext(Dispatchers.IO) {
        val pcmBuffer = ByteArray(FRAME_SIZE_BYTES)
        var isFirstPacket = true

        logD("Transmit loop started")

        try {
            while (isTransmitting.get() && isActive) {
                val bytesRead = audioRecord?.read(pcmBuffer, 0, FRAME_SIZE_BYTES) ?: 0

                if (bytesRead == FRAME_SIZE_BYTES) {
                    // Check voice activity
                    val level = calculateAudioLevel(pcmBuffer)

                    // Encode to Opus
                    val encodedFrame = opusCodec.encode(pcmBuffer)

                    if (encodedFrame != null) {
                        // Send via RTP multicast
                        val sent = rtpManager.sendAudio(
                            talkgroupId = activeTalkgroup,
                            opusData = encodedFrame.data,
                            marker = isFirstPacket,
                            frameSize = FRAME_SIZE_SAMPLES
                        )

                        if (sent) {
                            txFrameCount++
                            isFirstPacket = false
                        }
                    }

                    // Emit voice activity event periodically
                    if (txFrameCount % 10 == 0L) {
                        _audioEvents.emit(AudioEvent.VoiceActivity(level))
                    }
                } else if (bytesRead < 0) {
                    logE("AudioRecord read error: $bytesRead")
                    break
                }
            }
        } catch (e: CancellationException) {
            logD("Transmit loop cancelled")
        } catch (e: Exception) {
            logE("Error in transmit loop", e)
            _audioEvents.emit(AudioEvent.Error("Transmit error: ${e.message}"))
        }

        logD("Transmit loop ended: $txFrameCount frames")
    }

    /**
     * Start receive loop
     */
    private fun startReceiveLoop() {
        if (isReceiving.get()) return

        isReceiving.set(true)
        rxFrameCount = 0

        // Create AudioTrack for playback
        val bufferSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        // Feb 2026 FIX: Use USAGE_MEDIA to play through SPEAKER (not earpiece).
        // USAGE_VOICE_COMMUNICATION routes to earpiece by default — user can't hear PTT audio.
        // Walkie-talkie apps (Zello, VoicePing) all use speaker output.
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize * 4)  // Larger buffer for smoother playback
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        // Feb 2026 FIX (Bug 25): Log volume level but don't override user preference
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        if (curVol < maxVol / 5) {
            logW("Media volume very low ($curVol/$maxVol) — PTT audio may be inaudible")
        }

        audioTrack?.play()
        logI("AudioTrack started: USAGE_MEDIA, speaker output, buffer=${bufferSize * 4}")

        receiveJob = scope.launch {
            receiveLoop()
        }

        logD("Receive loop started")
    }

    /**
     * Stop receive loop
     */
    private fun stopReceiveLoop() {
        isReceiving.set(false)
        receiveJob?.cancel()
        receiveJob = null

        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null

        logD("Receive loop stopped: $rxFrameCount frames")
    }

    /**
     * Receive loop - receives, decodes, and plays audio
     */
    private suspend fun receiveLoop() = withContext(Dispatchers.IO) {
        var lastSsrc: Long? = null
        var consecutiveSilence = 0

        logD("Receive loop started")

        try {
            while (isReceiving.get() && isActive) {
                val packet = rtpManager.receivePacket()

                if (packet != null) {
                    // Skip own packets (SSRC filter + half-duplex)
                    if (packet.ssrc == rtpManager.getOwnSSRC()) {
                        continue
                    }
                    // Half-duplex: don't play audio while we're transmitting
                    if (isTransmitting.get()) {
                        continue
                    }

                    // New speaker detection
                    if (packet.ssrc != lastSsrc) {
                        if (lastSsrc != null) {
                            _audioEvents.emit(AudioEvent.ReceiveEnded(activeTalkgroup))
                        }
                        lastSsrc = packet.ssrc
                        logI("New speaker detected: SSRC=0x${packet.ssrc.toString(16)}")
                        _audioEvents.emit(AudioEvent.ReceiveStarted(activeTalkgroup, packet.ssrc))
                        consecutiveSilence = 0
                    }

                    // Decode Opus to PCM
                    val pcmData = opusCodec.decode(packet.payload)

                    if (pcmData != null) {
                        // Play audio
                        audioTrack?.write(pcmData, 0, pcmData.size)
                        rxFrameCount++
                        if (rxFrameCount % 50 == 0L) {
                            logD("RX audio: $rxFrameCount frames decoded and played")
                        }
                    } else {
                        logW("Opus decode returned null for ${packet.payload.size}B payload")
                    }
                } else {
                    // No packet received (timeout)
                    consecutiveSilence++

                    // After 10 silent frames (~200ms), consider transmission ended
                    if (consecutiveSilence > 10 && lastSsrc != null) {
                        _audioEvents.emit(AudioEvent.ReceiveEnded(activeTalkgroup))
                        lastSsrc = null
                    }
                }
            }
        } catch (e: CancellationException) {
            logD("Receive loop cancelled")
        } catch (e: Exception) {
            logE("Error in receive loop", e)
        }

        logD("Receive loop ended: $rxFrameCount frames")
    }

    /**
     * Calculate audio level (0.0 - 1.0) for VAD and UI
     */
    private fun calculateAudioLevel(pcmData: ByteArray): Float {
        var sum = 0L
        for (i in pcmData.indices step 2) {
            val sample = (pcmData[i].toInt() and 0xFF) or
                    ((pcmData[i + 1].toInt()) shl 8)
            sum += kotlin.math.abs(sample).toLong()
        }
        val average = sum / (pcmData.size / 2)
        return (average.toFloat() / Short.MAX_VALUE).coerceIn(0f, 1f)
    }

    /**
     * Set codec bitrate dynamically
     */
    fun setBitrate(bitrate: Int) {
        opusCodec.setBitrate(bitrate)
    }

    /**
     * Set DSCP for QoS
     */
    fun setDSCP(dscp: Int) {
        rtpManager.setDSCP(dscp)
    }

    /**
     * Get current statistics
     */
    fun getStats(): AudioStats {
        return AudioStats(
            isTransmitting = isTransmitting.get(),
            isReceiving = isReceiving.get(),
            activeTalkgroup = activeTalkgroup,
            txFrameCount = txFrameCount,
            rxFrameCount = rxFrameCount,
            codecStats = if (isInitialized) opusCodec.getStats() else null,
            rtpStats = if (isInitialized) rtpManager.getStats() else null
        )
    }

    // Track if release has been called (FIXED Jan 2026)
    @Volatile
    private var isReleased = false

    /**
     * Release all resources
     * FIXED Jan 2026: Made idempotent to prevent double release issues
     */
    fun release() {
        if (isReleased) {
            logD("MulticastAudioManager already released, skipping")
            return
        }
        isReleased = true

        logI("Releasing MulticastAudioManager")

        leaveTalkgroup()
        stopTransmit()
        stopReceiveLoop()

        try {
            opusCodec.release()
        } catch (e: Exception) {
            logE("Error releasing opus codec", e)
        }

        try {
            rtpManager.release()
        } catch (e: Exception) {
            logE("Error releasing RTP manager", e)
        }

        scope.cancel()
        isInitialized = false
    }
}
