/*
 * Mesh Rider Wave - Room Type Converters
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 */

package com.doodlelabs.meshriderwave.data.local.database

import androidx.room.TypeConverter

/**
 * Type converters for Room database
 */
class Converters {

    @TypeConverter
    fun fromByteArray(bytes: ByteArray?): String? {
        return bytes?.let { android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP) }
    }

    @TypeConverter
    fun toByteArray(base64: String?): ByteArray? {
        return base64?.let { android.util.Base64.decode(it, android.util.Base64.NO_WRAP) }
    }

    @TypeConverter
    fun fromStringList(list: List<String>?): String? {
        return list?.joinToString(",")
    }

    @TypeConverter
    fun toStringList(value: String?): List<String>? {
        return value?.split(",")?.filter { it.isNotBlank() }
    }
}
