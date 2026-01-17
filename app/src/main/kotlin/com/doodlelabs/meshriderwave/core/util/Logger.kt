/*
 * Mesh Rider Wave - Logging Utility
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 */

package com.doodlelabs.meshriderwave.core.util

import android.content.Context
import android.util.Log
import com.doodlelabs.meshriderwave.BuildConfig

/**
 * Centralized logging with tag inference
 */
object Logger {
    private const val TAG_PREFIX = "MeshRider"
    private var isDebug = false

    fun init(context: Context) {
        isDebug = BuildConfig.DEBUG
    }

    fun d(any: Any, message: String) {
        if (isDebug) {
            Log.d(getTag(any), message)
        }
    }

    fun i(any: Any, message: String) {
        Log.i(getTag(any), message)
    }

    fun w(any: Any, message: String) {
        Log.w(getTag(any), message)
    }

    fun e(any: Any, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e(getTag(any), message, throwable)
        } else {
            Log.e(getTag(any), message)
        }
    }

    private fun getTag(any: Any): String {
        val className = any::class.java.simpleName.take(20)
        return "$TAG_PREFIX:$className"
    }
}

// Extension functions for cleaner syntax
fun Any.logD(message: String) = Logger.d(this, message)
fun Any.logI(message: String) = Logger.i(this, message)
fun Any.logW(message: String) = Logger.w(this, message)
fun Any.logE(message: String, throwable: Throwable? = null) = Logger.e(this, message, throwable)
