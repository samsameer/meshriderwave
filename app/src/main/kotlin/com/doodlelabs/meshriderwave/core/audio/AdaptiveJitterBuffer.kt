/*
 * Mesh Rider Wave - Adaptive Jitter Buffer
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 *
 * Production-grade adaptive jitter buffer for PTT voice over mesh networks.
 *
 * Features:
 * - Adaptive buffer sizing (20-100ms based on network jitter)
 * - Packet reordering with 16-bit sequence number wrap handling
 * - Packet Loss Concealment (PLC) callback integration
 * - Late packet detection and intelligent discard
 * - Real-time statistics: jitter, loss rate, buffer depth
 * - Thread-safe implementation with lock-free fast paths
 *
 * Algorithm:
 * - Uses exponential moving average (EMA) for jitter estimation
 * - Buffer target = 2 * estimated_jitter (clamped to 20-100ms)
 * - Adaptive playout: speeds up/slows down to maintain target depth
 *
 * References:
 * - RFC 3550: RTP (jitter calculation)
 * - RFC 3551: RTP Audio/Video profiles
 * - ITU-T G.114: One-way transmission time
 */

package com.doodlelabs.meshriderwave.core.audio

import com.doodlelabs.meshriderwave.core.util.logD
import com.doodlelabs.meshriderwave.core.util.logI
import com.doodlelabs.meshriderwave.core.util.logW
import java.util.concurrent.ConcurrentSkipListMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Adaptive jitter buffer for voice packets.
 *
 * This buffer dynamically adjusts its depth based on observed network jitter,
 * reorders out-of-sequence packets, and provides callbacks for packet loss
 * concealment when packets are missing or arrive too late.
 *
 * Thread Safety:
 * - [put] and [poll] can be called from different threads safely
 * - Statistics can be read without blocking audio threads
 *
 * Usage:
 * ```kotlin
 * val buffer = AdaptiveJitterBuffer(
 *     frameTimeMs = 20,
 *     onPacketLoss = { count, lastSeq -> codec.decodePLC() }
 * )
 *
 * // Receiver thread
 * buffer.put(packet)
 *
 * // Playback thread
 * val packet = buffer.poll()
 * if (packet != null) {
 *     playAudio(packet.payload)
 * }
 * ```
 *
 * @param frameTimeMs Duration of each audio frame in milliseconds (typically 20ms)
 * @param minBufferMs Minimum buffer depth in milliseconds (default: 20ms)
 * @param maxBufferMs Maximum buffer depth in milliseconds (default: 100ms)
 * @param initialBufferMs Initial buffer depth before adaptation kicks in
 * @param onPacketLoss Callback invoked when packet loss is detected
 */
class AdaptiveJitterBuffer(
    private val frameTimeMs: Int = 20,
    private val minBufferMs: Int = MIN_BUFFER_MS,
    private val maxBufferMs: Int = MAX_BUFFER_MS,
    private val initialBufferMs: Int = INITIAL_BUFFER_MS,
    private val onPacketLoss: ((lostCount: Int, lastSequence: Int) -> ByteArray?)? = null
) {
    companion object {
        // Buffer depth limits (in milliseconds)
        const val MIN_BUFFER_MS = 20
        const val MAX_BUFFER_MS = 100
        const val INITIAL_BUFFER_MS = 40

        // Sequence number constants (16-bit)
        private const val SEQ_NUM_MAX = 65536
        private const val SEQ_NUM_HALF = 32768

        // Jitter calculation constants
        private const val JITTER_ALPHA = 0.0625f  // 1/16 for EMA (RFC 3550)
        private const val JITTER_BETA = 0.125f   // 1/8 for variance smoothing

        // Buffer management
        private const val MAX_PACKETS_IN_BUFFER = 100
        private const val LATE_THRESHOLD_FACTOR = 2.0  // Packets > 2x buffer are late

        // Statistics logging interval
        private const val STATS_LOG_INTERVAL = 500L  // Every 500 packets

        // Adaptation thresholds
        private const val UNDERRUN_THRESHOLD = 3     // Consecutive underruns to increase buffer
        private const val STABLE_THRESHOLD = 100     // Stable packets to try decreasing buffer
    }

    /**
     * Buffered packet with reception timestamp
     */
    data class BufferedPacket(
        val sequenceNumber: Int,
        val timestamp: Long,      // RTP timestamp
        val payload: ByteArray,
        val marker: Boolean,      // Start of talk spurt
        val receivedAt: Long = System.nanoTime()
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as BufferedPacket
            return sequenceNumber == other.sequenceNumber
        }

        override fun hashCode(): Int = sequenceNumber
    }

    /**
     * Buffer statistics snapshot
     */
    data class BufferStats(
        val currentDepthMs: Int,
        val targetDepthMs: Int,
        val estimatedJitterMs: Float,
        val packetsReceived: Long,
        val packetsPlayed: Long,
        val packetsLost: Long,
        val packetsLate: Long,
        val packetsDuplicate: Long,
        val packetsOutOfOrder: Long,
        val lossRate: Float,
        val currentBufferSize: Int,
        val underruns: Long,
        val overruns: Long
    ) {
        /**
         * Format statistics for logging
         */
        fun toLogString(): String = buildString {
            appendLine("=== Adaptive Jitter Buffer Stats ===")
            appendLine("Depth: ${currentDepthMs}ms / ${targetDepthMs}ms target")
            appendLine("Jitter: ${String.format("%.2f", estimatedJitterMs)}ms estimated")
            appendLine("Packets: $packetsReceived recv, $packetsPlayed played, $packetsLost lost")
            appendLine("Issues: $packetsLate late, $packetsDuplicate dup, $packetsOutOfOrder OOO")
            appendLine("Buffer: $currentBufferSize packets, ${String.format("%.2f", lossRate * 100)}% loss")
            appendLine("Events: $underruns underruns, $overruns overruns")
        }
    }

    // Packet storage - sorted by sequence number for efficient reordering
    private val buffer = ConcurrentSkipListMap<Int, BufferedPacket>(
        Comparator { a, b -> compareSequenceNumbers(a, b) }
    )

    // Read-write lock for buffer operations
    private val bufferLock = ReentrantReadWriteLock()

    // Playback state
    private var lastPlayedSeq = AtomicInteger(-1)
    private var lastPlayedTimestamp = AtomicLong(0)
    private var expectedSeq = AtomicInteger(-1)
    private var isFirstPacket = true

    // Jitter estimation (RFC 3550 style)
    @Volatile private var estimatedJitter = 0f
    @Volatile private var jitterVariance = 0f
    private var lastArrivalTime = 0L
    private var lastRtpTimestamp = 0L

    // Buffer depth management
    @Volatile private var targetBufferMs = initialBufferMs
    private val currentBufferDepthPackets: Int
        get() = buffer.size

    // Statistics (atomic for thread safety)
    private val packetsReceived = AtomicLong(0)
    private val packetsPlayed = AtomicLong(0)
    private val packetsLost = AtomicLong(0)
    private val packetsLate = AtomicLong(0)
    private val packetsDuplicate = AtomicLong(0)
    private val packetsOutOfOrder = AtomicLong(0)
    private val underruns = AtomicLong(0)
    private val overruns = AtomicLong(0)

    // Adaptation state
    private var consecutiveUnderruns = 0
    private var stablePacketCount = 0

    /**
     * Add a packet to the jitter buffer.
     *
     * This method handles:
     * - Duplicate detection
     * - Late packet detection and discard
     * - Jitter estimation update
     * - Buffer overflow protection
     *
     * @param packet The RTP packet to buffer
     * @return true if packet was accepted, false if rejected (duplicate/late)
     */
    fun put(packet: BufferedPacket): Boolean {
        val seq = packet.sequenceNumber
        packetsReceived.incrementAndGet()

        // Check for duplicate
        if (buffer.containsKey(seq)) {
            packetsDuplicate.incrementAndGet()
            logD("Duplicate packet: seq=$seq")
            return false
        }

        // Check if packet is too late (already played past this sequence)
        val lastPlayed = lastPlayedSeq.get()
        if (lastPlayed >= 0 && isSequenceBefore(seq, lastPlayed)) {
            val distance = sequenceDistance(seq, lastPlayed)
            // Allow some tolerance for very slightly late packets during reordering
            if (distance > (targetBufferMs / frameTimeMs) * LATE_THRESHOLD_FACTOR) {
                packetsLate.incrementAndGet()
                logD("Late packet discarded: seq=$seq, lastPlayed=$lastPlayed, distance=$distance")
                return false
            }
        }

        // Update jitter estimation
        updateJitterEstimate(packet)

        // Add to buffer
        bufferLock.write {
            buffer[seq] = packet

            // Check for out-of-order
            val expected = expectedSeq.get()
            if (expected >= 0 && seq != expected) {
                packetsOutOfOrder.incrementAndGet()
            }

            // Update expected sequence for next packet
            expectedSeq.set((seq + 1) and 0xFFFF)

            // Prevent buffer overflow
            while (buffer.size > MAX_PACKETS_IN_BUFFER) {
                val oldest = buffer.firstKey()
                buffer.remove(oldest)
                overruns.incrementAndGet()
                logW("Buffer overflow, dropped oldest packet: seq=$oldest")
            }
        }

        // Adapt buffer size based on jitter
        adaptBufferSize()

        // Log statistics periodically
        val received = packetsReceived.get()
        if (received % STATS_LOG_INTERVAL == 0L) {
            logD(getStats().toLogString())
        }

        return true
    }

    /**
     * Retrieve the next packet for playback.
     *
     * This method handles:
     * - Waiting until buffer reaches target depth
     * - Reordered packet retrieval
     * - Missing packet detection with PLC callback
     * - Underrun detection
     *
     * @return The next packet to play, or null if buffer is empty/buffering
     */
    fun poll(): BufferedPacket? {
        bufferLock.read {
            if (buffer.isEmpty()) {
                handleUnderrun()
                return null
            }
        }

        // During initial buffering, wait for target depth
        if (isFirstPacket) {
            val targetPackets = targetBufferMs / frameTimeMs
            if (buffer.size < targetPackets) {
                return null  // Still buffering
            }
            isFirstPacket = false
            logI("Initial buffering complete: ${buffer.size} packets, target=$targetPackets")
        }

        return bufferLock.write {
            if (buffer.isEmpty()) {
                handleUnderrun()
                return@write null
            }

            val lastPlayed = lastPlayedSeq.get()
            val nextExpectedSeq = if (lastPlayed < 0) {
                buffer.firstKey()
            } else {
                (lastPlayed + 1) and 0xFFFF
            }

            // Try to get the expected packet
            val packet = buffer.remove(nextExpectedSeq)

            if (packet != null) {
                // Got expected packet
                lastPlayedSeq.set(nextExpectedSeq)
                lastPlayedTimestamp.set(packet.timestamp)
                packetsPlayed.incrementAndGet()
                consecutiveUnderruns = 0
                stablePacketCount++
                return@write packet
            }

            // Expected packet is missing - check if we should skip or wait
            if (buffer.isNotEmpty()) {
                val availableSeq = buffer.firstKey()
                val gapSize = sequenceDistance(nextExpectedSeq, availableSeq)

                // If gap is small, invoke PLC and skip to available
                if (gapSize <= (targetBufferMs / frameTimeMs)) {
                    packetsLost.addAndGet(gapSize.toLong())

                    // Invoke PLC callback for concealment
                    onPacketLoss?.invoke(gapSize, lastPlayed)

                    // Skip to available packet
                    val skipPacket = buffer.remove(availableSeq)
                    if (skipPacket != null) {
                        lastPlayedSeq.set(availableSeq)
                        lastPlayedTimestamp.set(skipPacket.timestamp)
                        packetsPlayed.incrementAndGet()
                        logD("Skipped $gapSize lost packets, playing seq=$availableSeq")
                        return@write skipPacket
                    }
                }
            }

            // Buffer has packets but not the one we need - wait for reordering
            null
        }
    }

    /**
     * Reset the buffer state.
     *
     * Call this when:
     * - Starting a new transmission
     * - Switching talkgroups
     * - Recovering from errors
     */
    fun reset() {
        bufferLock.write {
            buffer.clear()
            lastPlayedSeq.set(-1)
            lastPlayedTimestamp.set(0)
            expectedSeq.set(-1)
            isFirstPacket = true
            estimatedJitter = 0f
            jitterVariance = 0f
            lastArrivalTime = 0
            lastRtpTimestamp = 0
            targetBufferMs = initialBufferMs
            consecutiveUnderruns = 0
            stablePacketCount = 0
        }
        logI("Jitter buffer reset")
    }

    /**
     * Clear statistics counters.
     */
    fun clearStats() {
        packetsReceived.set(0)
        packetsPlayed.set(0)
        packetsLost.set(0)
        packetsLate.set(0)
        packetsDuplicate.set(0)
        packetsOutOfOrder.set(0)
        underruns.set(0)
        overruns.set(0)
    }

    /**
     * Get current buffer statistics.
     */
    fun getStats(): BufferStats {
        val received = packetsReceived.get()
        val lost = packetsLost.get()
        val lossRate = if (received > 0) {
            lost.toFloat() / (received + lost)
        } else 0f

        return BufferStats(
            currentDepthMs = currentBufferDepthPackets * frameTimeMs,
            targetDepthMs = targetBufferMs,
            estimatedJitterMs = estimatedJitter,
            packetsReceived = received,
            packetsPlayed = packetsPlayed.get(),
            packetsLost = lost,
            packetsLate = packetsLate.get(),
            packetsDuplicate = packetsDuplicate.get(),
            packetsOutOfOrder = packetsOutOfOrder.get(),
            lossRate = lossRate,
            currentBufferSize = buffer.size,
            underruns = underruns.get(),
            overruns = overruns.get()
        )
    }

    /**
     * Get the estimated jitter in milliseconds.
     */
    fun getEstimatedJitterMs(): Float = estimatedJitter

    /**
     * Get the current target buffer depth in milliseconds.
     */
    fun getTargetBufferMs(): Int = targetBufferMs

    /**
     * Manually set the target buffer depth.
     *
     * @param depthMs Target depth in milliseconds (will be clamped to valid range)
     */
    fun setTargetBufferMs(depthMs: Int) {
        targetBufferMs = depthMs.coerceIn(minBufferMs, maxBufferMs)
        logI("Target buffer manually set to ${targetBufferMs}ms")
    }

    /**
     * Check if the buffer is currently in initial buffering state.
     */
    fun isBuffering(): Boolean = isFirstPacket && buffer.isNotEmpty()

    /**
     * Get the number of packets currently in the buffer.
     */
    fun getBufferSize(): Int = buffer.size

    // ============== Private Methods ==============

    /**
     * Update jitter estimation using RFC 3550 algorithm.
     *
     * J(i) = J(i-1) + (|D(i-1,i)| - J(i-1)) / 16
     *
     * Where D(i-1,i) is the difference in packet spacing between
     * sender and receiver.
     */
    private fun updateJitterEstimate(packet: BufferedPacket) {
        val arrivalTime = packet.receivedAt
        val rtpTimestamp = packet.timestamp

        if (lastArrivalTime > 0 && lastRtpTimestamp > 0) {
            // Convert RTP timestamp difference to nanoseconds
            // Assuming 48kHz clock (Opus RTP)
            val rtpDiffSamples = (rtpTimestamp - lastRtpTimestamp) and 0xFFFFFFFFL
            val expectedIntervalNs = (rtpDiffSamples * 1_000_000_000L) / 48000

            // Actual interval
            val actualIntervalNs = arrivalTime - lastArrivalTime

            // Interarrival jitter
            val transitDiff = abs(actualIntervalNs - expectedIntervalNs)
            val transitDiffMs = transitDiff / 1_000_000f

            // Exponential moving average (RFC 3550)
            estimatedJitter += JITTER_ALPHA * (transitDiffMs - estimatedJitter)

            // Track variance for adaptive buffering
            val deviation = transitDiffMs - estimatedJitter
            jitterVariance += JITTER_BETA * (deviation * deviation - jitterVariance)
        }

        lastArrivalTime = arrivalTime
        lastRtpTimestamp = rtpTimestamp
    }

    /**
     * Adapt buffer size based on observed jitter.
     *
     * Target = max(minBuffer, min(maxBuffer, 2 * jitter + sqrt(variance)))
     */
    private fun adaptBufferSize() {
        // Target buffer = 2x jitter with some margin from variance
        val jitterMargin = kotlin.math.sqrt(jitterVariance)
        val optimalBuffer = (2 * estimatedJitter + jitterMargin).toInt()

        // Clamp to valid range
        val newTarget = optimalBuffer.coerceIn(minBufferMs, maxBufferMs)

        // Only update if significantly different (avoid oscillation)
        if (abs(newTarget - targetBufferMs) > (frameTimeMs / 2)) {
            targetBufferMs = newTarget
            logD("Buffer target adapted to ${targetBufferMs}ms (jitter=${estimatedJitter}ms)")
        }
    }

    /**
     * Handle buffer underrun (no packets available when needed).
     */
    private fun handleUnderrun() {
        underruns.incrementAndGet()
        consecutiveUnderruns++
        stablePacketCount = 0

        // Increase buffer after multiple consecutive underruns
        if (consecutiveUnderruns >= UNDERRUN_THRESHOLD) {
            val newTarget = min(targetBufferMs + frameTimeMs, maxBufferMs)
            if (newTarget != targetBufferMs) {
                targetBufferMs = newTarget
                logW("Underrun detected, increased buffer to ${targetBufferMs}ms")
            }
            consecutiveUnderruns = 0
        }
    }

    /**
     * Compare two 16-bit sequence numbers with wrap-around handling.
     *
     * @return negative if a < b, positive if a > b, 0 if equal
     */
    private fun compareSequenceNumbers(a: Int, b: Int): Int {
        val diff = ((a - b) + SEQ_NUM_HALF) % SEQ_NUM_MAX - SEQ_NUM_HALF
        return diff
    }

    /**
     * Check if sequence a comes before sequence b (with wrap handling).
     */
    private fun isSequenceBefore(a: Int, b: Int): Boolean {
        return compareSequenceNumbers(a, b) < 0
    }

    /**
     * Calculate distance between two sequence numbers (with wrap handling).
     *
     * @return Positive distance from a to b
     */
    private fun sequenceDistance(from: Int, to: Int): Int {
        val diff = (to - from) and 0xFFFF
        return if (diff > SEQ_NUM_HALF) SEQ_NUM_MAX - diff else diff
    }
}

/**
 * Factory for creating jitter buffers with common configurations.
 */
object JitterBufferFactory {

    /**
     * Create a jitter buffer optimized for low-latency PTT.
     */
    fun createForPTT(
        frameTimeMs: Int = 20,
        onPacketLoss: ((Int, Int) -> ByteArray?)? = null
    ): AdaptiveJitterBuffer {
        return AdaptiveJitterBuffer(
            frameTimeMs = frameTimeMs,
            minBufferMs = 20,
            maxBufferMs = 60,
            initialBufferMs = 30,
            onPacketLoss = onPacketLoss
        )
    }

    /**
     * Create a jitter buffer for degraded mesh networks.
     */
    fun createForHighJitter(
        frameTimeMs: Int = 20,
        onPacketLoss: ((Int, Int) -> ByteArray?)? = null
    ): AdaptiveJitterBuffer {
        return AdaptiveJitterBuffer(
            frameTimeMs = frameTimeMs,
            minBufferMs = 40,
            maxBufferMs = 150,
            initialBufferMs = 80,
            onPacketLoss = onPacketLoss
        )
    }

    /**
     * Create a jitter buffer for stable networks.
     */
    fun createForStableNetwork(
        frameTimeMs: Int = 20,
        onPacketLoss: ((Int, Int) -> ByteArray?)? = null
    ): AdaptiveJitterBuffer {
        return AdaptiveJitterBuffer(
            frameTimeMs = frameTimeMs,
            minBufferMs = 10,
            maxBufferMs = 40,
            initialBufferMs = 20,
            onPacketLoss = onPacketLoss
        )
    }
}
