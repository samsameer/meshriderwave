/*
 * Mesh Rider Wave - Domain Model: Call State
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 *
 * Enterprise-grade call state management (Jan 2026)
 * Features: Reconnection, Network Quality, Adaptive Bitrate
 */

package com.doodlelabs.meshriderwave.domain.model

/**
 * Immutable call state for clean state management
 * Enterprise features like WhatsApp/Zoom/Teams
 */
data class CallState(
    val status: Status = Status.IDLE,
    val direction: Direction = Direction.OUTGOING,
    val type: Type = Type.VOICE,
    val contact: Contact? = null,
    val remoteAddress: String? = null,
    val isMicEnabled: Boolean = true,
    val isCameraEnabled: Boolean = false,
    val isSpeakerEnabled: Boolean = false,
    val isFrontCamera: Boolean = true,
    val startTime: Long? = null,
    val errorMessage: String? = null,

    // Enterprise Reconnection Features (Jan 2026)
    val reconnectAttempt: Int = 0,
    val maxReconnectAttempts: Int = MAX_RECONNECT_ATTEMPTS,
    val reconnectTimeoutSeconds: Int = RECONNECT_TIMEOUT_SECONDS,
    val lastDisconnectTime: Long? = null,

    // Network Quality Monitoring
    val networkQuality: NetworkQuality = NetworkQuality.UNKNOWN,
    val packetLossPercent: Float = 0f,
    val roundTripTimeMs: Int = 0,
    val jitterMs: Int = 0,
    val availableBandwidthKbps: Int = 0,

    // Adaptive Quality
    val isLowBandwidthMode: Boolean = false,
    val videoDisabledDueToNetwork: Boolean = false,

    // LOCAL VIDEO FIX Jan 2026: Track when video system is initialized
    // This triggers compose rerender when EGL context and tracks are ready
    val isVideoReady: Boolean = false
) {
    companion object {
        const val MAX_RECONNECT_ATTEMPTS = 5
        const val RECONNECT_TIMEOUT_SECONDS = 30
        const val POOR_QUALITY_THRESHOLD_PACKET_LOSS = 5f  // 5%
        const val BAD_QUALITY_THRESHOLD_PACKET_LOSS = 15f  // 15%
        const val POOR_QUALITY_THRESHOLD_RTT = 300  // 300ms
        const val BAD_QUALITY_THRESHOLD_RTT = 500   // 500ms
    }
    enum class Status {
        IDLE,
        INITIATING,
        RINGING,
        CONNECTING,
        CONNECTED,
        RECONNECTING,
        ENDED,
        ERROR
    }

    enum class Direction {
        INCOMING,
        OUTGOING
    }

    enum class Type {
        VOICE,
        VIDEO
    }

    /**
     * Network quality levels (like WhatsApp/Zoom signal bars)
     */
    enum class NetworkQuality {
        UNKNOWN,    // Not yet measured
        EXCELLENT,  // 4 bars - < 1% loss, < 100ms RTT
        GOOD,       // 3 bars - < 3% loss, < 200ms RTT
        FAIR,       // 2 bars - < 5% loss, < 300ms RTT
        POOR,       // 1 bar  - < 15% loss, < 500ms RTT
        BAD         // 0 bars - > 15% loss or > 500ms RTT
    }

    val isActive: Boolean
        get() = status in listOf(Status.INITIATING, Status.RINGING, Status.CONNECTING, Status.CONNECTED, Status.RECONNECTING)

    val isConnected: Boolean
        get() = status == Status.CONNECTED

    val isReconnecting: Boolean
        get() = status == Status.RECONNECTING

    val callDuration: Long
        get() = startTime?.let { System.currentTimeMillis() - it } ?: 0L

    /**
     * Formatted duration string (MM:SS)
     */
    val durationFormatted: String
        get() {
            val seconds = (callDuration / 1000) % 60
            val minutes = (callDuration / 1000 / 60) % 60
            val hours = callDuration / 1000 / 3600
            return if (hours > 0) {
                "%d:%02d:%02d".format(hours, minutes, seconds)
            } else {
                "%02d:%02d".format(minutes, seconds)
            }
        }

    /**
     * Seconds remaining before reconnection times out
     */
    val reconnectSecondsRemaining: Int
        get() {
            if (!isReconnecting || lastDisconnectTime == null) return 0
            val elapsed = (System.currentTimeMillis() - lastDisconnectTime) / 1000
            return maxOf(0, reconnectTimeoutSeconds - elapsed.toInt())
        }

    /**
     * Progress of reconnection (0.0 to 1.0)
     */
    val reconnectProgress: Float
        get() {
            if (!isReconnecting || lastDisconnectTime == null) return 0f
            val elapsed = (System.currentTimeMillis() - lastDisconnectTime) / 1000f
            return minOf(1f, elapsed / reconnectTimeoutSeconds)
        }

    /**
     * Check if network quality is poor (should show warning)
     */
    val shouldShowPoorConnectionWarning: Boolean
        get() = networkQuality in listOf(NetworkQuality.POOR, NetworkQuality.BAD)

    /**
     * Number of signal bars (0-4) for UI
     */
    val signalBars: Int
        get() = when (networkQuality) {
            NetworkQuality.EXCELLENT -> 4
            NetworkQuality.GOOD -> 3
            NetworkQuality.FAIR -> 2
            NetworkQuality.POOR -> 1
            NetworkQuality.BAD, NetworkQuality.UNKNOWN -> 0
        }
}
