/*
 * Mesh Rider Wave - Domain Model: Call State
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 */

package com.doodlelabs.meshriderwave.domain.model

/**
 * Immutable call state for clean state management
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
    val errorMessage: String? = null
) {
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

    val isActive: Boolean
        get() = status in listOf(Status.INITIATING, Status.RINGING, Status.CONNECTING, Status.CONNECTED, Status.RECONNECTING)

    val isConnected: Boolean
        get() = status == Status.CONNECTED

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
}
