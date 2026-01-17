/*
 * Mesh Rider Wave - Domain Model: Event (Call History)
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 */

package com.doodlelabs.meshriderwave.domain.model

import kotlinx.serialization.Serializable

/**
 * Call event for history
 */
@Serializable
data class Event(
    val type: Type,
    val publicKey: ByteArray,
    val address: String?,
    val timestamp: Long = System.currentTimeMillis()
) {
    enum class Type {
        OUTGOING_ACCEPTED,
        OUTGOING_DECLINED,
        OUTGOING_MISSED,
        OUTGOING_ERROR,
        INCOMING_ACCEPTED,
        INCOMING_DECLINED,
        INCOMING_MISSED,
        INCOMING_ERROR
    }

    val isMissed: Boolean
        get() = type in listOf(Type.INCOMING_MISSED, Type.OUTGOING_MISSED)

    val isIncoming: Boolean
        get() = type.name.startsWith("INCOMING")

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Event) return false
        return timestamp == other.timestamp && publicKey.contentEquals(other.publicKey)
    }

    override fun hashCode(): Int {
        var result = publicKey.contentHashCode()
        result = 31 * result + timestamp.hashCode()
        return result
    }
}
