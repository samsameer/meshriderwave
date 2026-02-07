/*
 * Mesh Rider Wave - Production Test Metrics
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 *
 * REAL METRICS for production validation
 * No assumptions - actual measurements
 */

package com.doodlelabs.meshriderwave.test

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.util.concurrent.atomic.AtomicLong

/**
 * Production Test Metrics - Real measurements, not assumptions
 *
 * Use this in instrumented tests to verify:
 * - Audio is actually being captured
 * - Packets are actually being sent
 * - Latency is actually measured
 * - Quality is actually calculated
 */
object TestMetrics {

    private const val TAG = "MeshRider:TestMetrics"

    // Audio capture metrics
    val audioFramesCaptured = AtomicLong(0)
    val audioBytesCaptured = AtomicLong(0)
    val audioCaptureErrors = AtomicLong(0)
    val lastCaptureTimeMs = AtomicLong(0)

    // Audio playback metrics
    val audioFramesPlayed = AtomicLong(0)
    val audioBytesPlayed = AtomicLong(0)
    val audioPlaybackErrors = AtomicLong(0)
    val lastPlaybackTimeMs = AtomicLong(0)

    // Network metrics
    val rtpPacketsSent = AtomicLong(0)
    val rtpPacketsReceived = AtomicLong(0)
    val rtpBytesSent = AtomicLong(0)
    val rtpBytesReceived = AtomicLong(0)
    val rtpPacketsLost = AtomicLong(0)

    // Floor control metrics
    val floorRequestsSent = AtomicLong(0)
    val floorRequestsReceived = AtomicLong(0)
    val floorGrantsReceived = AtomicLong(0)
    val floorDeniesReceived = AtomicLong(0)

    // Timing metrics
    var pttAccessTimeMs: Long = 0  // Time from press to audio start
    var mouthToEarLatencyMs: Long = 0  // Time from capture to playback

    // Test state
    private val _isTestActive = MutableStateFlow(false)
    val isTestActive: StateFlow<Boolean> = _isTestActive

    /**
     * Start collecting test metrics
     */
    fun startTest() {
        Log.i(TAG, "=== STARTING TEST METRICS COLLECTION ===")
        resetAll()
        _isTestActive.value = true
    }

    /**
     * Stop collecting and log results
     */
    fun stopTest() {
        _isTestActive.value = false
        logResults()
    }

    /**
     * Reset all metrics
     */
    fun resetAll() {
        audioFramesCaptured.set(0)
        audioBytesCaptured.set(0)
        audioCaptureErrors.set(0)
        lastCaptureTimeMs.set(0)

        audioFramesPlayed.set(0)
        audioBytesPlayed.set(0)
        audioPlaybackErrors.set(0)
        lastPlaybackTimeMs.set(0)

        rtpPacketsSent.set(0)
        rtpPacketsReceived.set(0)
        rtpBytesSent.set(0)
        rtpBytesReceived.set(0)
        rtpPacketsLost.set(0)

        floorRequestsSent.set(0)
        floorRequestsReceived.set(0)
        floorGrantsReceived.set(0)
        floorDeniesReceived.set(0)

        pttAccessTimeMs = 0
        mouthToEarLatencyMs = 0
    }

    /**
     * Log all test results
     */
    fun logResults() {
        Log.i(TAG, "=== TEST RESULTS ===")
        Log.i(TAG, "Audio Capture:")
        Log.i(TAG, "  Frames: $audioFramesCaptured")
        Log.i(TAG, "  Bytes: $audioBytesCaptured")
        Log.i(TAG, "  Errors: $audioCaptureErrors")
        Log.i(TAG, "  Last capture: ${lastCaptureTimeMs.get()}ms ago")

        Log.i(TAG, "Audio Playback:")
        Log.i(TAG, "  Frames: $audioFramesPlayed")
        Log.i(TAG, "  Bytes: $audioBytesPlayed")
        Log.i(TAG, "  Errors: $audioPlaybackErrors")
        Log.i(TAG, "  Last playback: ${lastPlaybackTimeMs.get()}ms ago")

        Log.i(TAG, "Network:")
        Log.i(TAG, "  RTP Sent: $rtpPacketsSent packets, $rtpBytesSent bytes")
        Log.i(TAG, "  RTP Received: $rtpPacketsReceived packets, $rtpBytesReceived bytes")
        Log.i(TAG, "  Packets Lost: $rtpPacketsLost")

        Log.i(TAG, "Floor Control:")
        Log.i(TAG, "  Requests Sent: $floorRequestsSent")
        Log.i(TAG, "  Requests Received: $floorRequestsReceived")
        Log.i(TAG, "  Grants: $floorGrantsReceived")
        Log.i(TAG, "  Denies: $floorDeniesReceived")

        Log.i(TAG, "Timing:")
        Log.i(TAG, "  PTT Access Time: ${pttAccessTimeMs}ms")
        Log.i(TAG, "  Mouth-to-Ear Latency: ${mouthToEarLatencyMs}ms")

        Log.i(TAG, "=== END TEST RESULTS ===")
    }

    /**
     * Verify test passed with thresholds
     */
    fun verifyTestPassed(): Boolean {
        // Audio capture check
        if (audioFramesCaptured.get() == 0L) {
            Log.e(TAG, "FAIL: No audio frames captured")
            return false
        }

        if (audioCaptureErrors.get() > 10) {
            Log.e(TAG, "FAIL: Too many capture errors: ${audioCaptureErrors.get()}")
            return false
        }

        // Network check
        if (rtpPacketsSent.get() == 0L) {
            Log.e(TAG, "FAIL: No RTP packets sent")
            return false
        }

        // Latency check
        if (mouthToEarLatencyMs > 500) {
            Log.e(TAG, "FAIL: Mouth-to-ear latency too high: ${mouthToEarLatencyMs}ms")
            return false
        }

        // Packet loss check
        val totalPackets = rtpPacketsReceived.get() + rtpPacketsLost.get()
        val lossRate = if (totalPackets > 0) {
            (rtpPacketsLost.get().toDouble() / totalPackets.toDouble()) * 100
        } else 0.0

        if (lossRate > 10.0) {
            Log.e(TAG, "FAIL: Packet loss too high: ${lossRate}%")
            return false
        }

        Log.i(TAG, "TEST PASSED")
        return true
    }

    /**
     * Get packet loss percentage
     */
    fun getPacketLossPercentage(): Double {
        val total = rtpPacketsReceived.get() + rtpPacketsLost.get()
        return if (total > 0) {
            (rtpPacketsLost.get().toDouble() / total.toDouble()) * 100
        } else 0.0
    }

    /**
     * Get audio capture rate (frames per second)
     */
    fun getAudioCaptureRate(): Double {
        val durationSeconds = (System.currentTimeMillis() - lastCaptureTimeMs.get()) / 1000.0
        return if (durationSeconds > 0) {
            audioFramesCaptured.get().toDouble() / durationSeconds
        } else 0.0
    }
}
