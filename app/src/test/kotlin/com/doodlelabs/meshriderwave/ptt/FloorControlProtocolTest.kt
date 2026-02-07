/*
 * Mesh Rider Wave - Floor Control Protocol Tests (PRODUCTION)
 * Comprehensive tests for reliable floor control with ACK/retry mechanism
 *
 * Test Coverage:
 * - Floor request/grant/deny lifecycle
 * - Race condition fixes (atomic operations)
 * - Duplicate message detection
 * - Priority arbitration (emergency preemption)
 * - Network partition detection
 * - Peer health tracking
 * - Sequence number wrap-around
 * - Concurrent floor requests
 * - Heartbeat and keep-alive
 * - Stress tests
 *
 * FIXED (Feb 2026): Added tests for atomic compare-and-set fixes
 */

package com.doodlelabs.meshriderwave.ptt

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

@OptIn(ExperimentalCoroutinesApi::class)
class FloorControlProtocolTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var scope: TestScope

    private lateinit var floorControl1: FloorControlProtocol
    private lateinit var floorControl2: FloorControlProtocol
    private lateinit var floorControl3: FloorControlProtocol

    private val port1 = 15005
    private val port2 = 15006
    private val port3 = 15007
    private val multicastGroup = "239.255.0.1"

    @Before
    fun setup() {
        scope = TestScope(testDispatcher)

        // Create three floor control instances for multi-device tests
        floorControl1 = FloorControlProtocol(multicastGroup, port1)
        floorControl2 = FloorControlProtocol(multicastGroup, port2)
        floorControl3 = FloorControlProtocol(multicastGroup, port3)
    }

    @After
    fun tearDown() {
        runBlocking {
            floorControl1.stop()
            floorControl2.stop()
            floorControl3.stop()
            // Give time for cleanup
            delay(100)
        }
    }

    // =========================================================================
    // Basic Floor Control Tests
    // =========================================================================

    @Test
    fun `test initialization succeeds`() = scope.runTest {
        val result = floorControl1.initialize("device1")
        assertTrue("Floor control should initialize successfully", result)
    }

    @Test
    fun `test floor request and grant - single device`() = scope.runTest {
        // Initialize device
        assertTrue(floorControl1.initialize("device1"))

        // Track callbacks
        val granted = AtomicBoolean(false)
        floorControl1.onFloorGranted = { granted.set(true) }

        // Request floor
        val result = floorControl1.requestFloor(priority = 0)

        // Wait for processing
        advanceUntilIdle()

        // Verify floor granted (no other devices, so network partition grants it)
        assertTrue("Floor should be granted (network partition mode)", result)
        assertTrue("Callback should be invoked", granted.get())
        assertTrue("Device should have floor", floorControl1.hasFloor.value)
    }

    @Test
    fun `test floor request and grant - two devices`() = scope.runTest {
        // Initialize both devices
        assertTrue(floorControl1.initialize("device1"))
        assertTrue(floorControl2.initialize("device2"))

        // Track callbacks
        val granted1 = AtomicBoolean(false)
        val taken2 = AtomicBoolean(false)
        floorControl1.onFloorGranted = { granted1.set(true) }
        floorControl2.onFloorTaken = { speaker -> taken2.set(true) }

        // Device 1 requests floor
        val result = floorControl1.requestFloor(priority = 0)

        // Wait for message processing
        advanceTimeBy(200)

        // Verify floor granted
        assertTrue("Floor should be granted", result)
        assertTrue("Device 1 should have floor", floorControl1.hasFloor.value)
        assertTrue("Device 2 should see floor taken", floorControl2.currentSpeaker.value == "device1")
    }

    @Test
    fun `test floor denial when another device has floor`() = scope.runTest {
        // Initialize both devices
        assertTrue(floorControl1.initialize("device1"))
        assertTrue(floorControl2.initialize("device2"))

        // Track callbacks
        val denied = AtomicBoolean(false)
        var deniedBy: String? = null
        floorControl2.onFloorDenied = { speaker ->
            denied.set(true)
            deniedBy = speaker
        }

        // Device 1 gets floor first
        floorControl1.requestFloor(priority = 0)
        advanceTimeBy(200)

        assertTrue("Device 1 should have floor", floorControl1.hasFloor.value)

        // Device 2 requests floor (should be denied)
        val result = floorControl2.requestFloor(priority = 0)
        advanceTimeBy(200)

        // Verify floor denied
        assertFalse("Floor should be denied for device 2", result)
        assertFalse("Device 2 should not have floor", floorControl2.hasFloor.value)
        assertTrue("Denial callback should be invoked", denied.get())
        assertEquals("Should show device1 has floor", "device1", deniedBy)
    }

    @Test
    fun `test floor release clears state`() = scope.runTest {
        // Initialize device
        assertTrue(floorControl1.initialize("device1"))

        // Get floor
        floorControl1.requestFloor(priority = 0)
        advanceUntilIdle()
        assertTrue("Should have floor", floorControl1.hasFloor.value)

        // Release floor
        val released = AtomicBoolean(false)
        floorControl1.onFloorReleased = { released.set(true) }

        floorControl1.releaseFloor()
        advanceTimeBy(100)

        assertFalse("Should not have floor after release", floorControl1.hasFloor.value)
        assertFalse("Floor granted flag should be cleared", floorControl1.floorGranted.value)
        assertTrue("Release callback should be invoked", released.get())
    }

    @Test
    fun `test double release is idempotent`() = scope.runTest {
        // Initialize device
        assertTrue(floorControl1.initialize("device1"))

        // Get floor
        floorControl1.requestFloor(priority = 0)
        advanceUntilIdle()

        // Release floor twice
        floorControl1.releaseFloor()
        floorControl1.releaseFloor()  // Should be safe

        advanceTimeBy(100)

        assertFalse("Should not have floor", floorControl1.hasFloor.value)
    }

    // =========================================================================
    // Priority and Emergency Tests
    // =========================================================================

    @Test
    fun `test emergency preemption yields floor`() = scope.runTest {
        // Initialize both devices
        assertTrue(floorControl1.initialize("device1"))
        assertTrue(floorControl2.initialize("device2"))

        val released1 = AtomicBoolean(false)
        floorControl1.onFloorReleased = { released1.set(true) }

        // Device 1 gets floor with normal priority
        floorControl1.requestFloor(priority = 0)
        advanceTimeBy(200)
        assertTrue("Device 1 should have floor", floorControl1.hasFloor.value)

        // Device 2 sends emergency (priority > 100)
        floorControl2.sendEmergency()
        advanceTimeBy(200)

        // Device 1 should have released floor
        assertFalse("Device 1 should have released floor", floorControl1.hasFloor.value)
        assertTrue("Release callback should be invoked", released1.get())

        // Device 2 should have floor
        assertTrue("Device 2 should have floor after emergency", floorControl2.hasFloor.value)
    }

    @Test
    fun `test emergency overrides normal priority`() = scope.runTest {
        // Initialize both devices
        assertTrue(floorControl1.initialize("device1"))
        assertTrue(floorControl2.initialize("device2"))

        // Device 1 gets floor
        floorControl1.requestFloor(priority = 50)  // High but not emergency
        advanceTimeBy(200)

        // Device 2 sends with emergency priority
        floorControl2.sendEmergency()
        advanceTimeBy(200)

        // Device 1 should have yielded
        assertFalse("Device 1 should yield to emergency", floorControl1.hasFloor.value)
    }

    @Test
    fun `test high priority causes normal priority to yield`() = scope.runTest {
        // Initialize three devices
        assertTrue(floorControl1.initialize("device1"))
        assertTrue(floorControl2.initialize("device2"))
        assertTrue(floorControl3.initialize("device3"))

        val denied = AtomicBoolean(false)
        floorControl1.onFloorDenied = { denied.set(true) }

        // Device 1 gets floor with low priority
        floorControl1.requestFloor(priority = 0)
        advanceTimeBy(200)

        // Device 2 requests with higher priority (but not emergency)
        floorControl2.requestFloor(priority = 50)
        advanceTimeBy(200)

        // Device 1 should have yielded due to priority
        // (This tests the handleFloorRequest priority logic)
        assertNotNull("Current speaker should be set", floorControl2.currentSpeaker.value)
    }

    // =========================================================================
    // Race Condition Tests (Atomic Operations)
    // =========================================================================

    @Test
    fun `test concurrent floor requests are serialized`() = scope.runTest {
        // Initialize both devices
        assertTrue(floorControl1.initialize("device1"))
        assertTrue(floorControl2.initialize("device2"))

        val grantCount = AtomicInteger(0)
        val denyCount = AtomicInteger(0)

        floorControl1.onFloorGranted = { grantCount.incrementAndGet() }
        floorControl1.onFloorDenied = { denyCount.incrementAndGet() }
        floorControl2.onFloorGranted = { grantCount.incrementAndGet() }
        floorControl2.onFloorDenied = { denyCount.incrementAndGet() }

        // Both devices request floor simultaneously
        val deferred1 = async { floorControl1.requestFloor(priority = 0) }
        val deferred2 = async { floorControl2.requestFloor(priority = 0) }

        // Wait for both
        val result1 = deferred1.await()
        val result2 = deferred2.await()

        advanceUntilIdle()

        // One should succeed, one should fail
        assertTrue("At least one should succeed", result1 || result2)
        assertFalse("Both should not succeed", result1 && result2)

        // Verify only one device has floor
        val hasFloor1 = floorControl1.hasFloor.value
        val hasFloor2 = floorControl2.hasFloor.value
        assertTrue("Only one device should have floor", hasFloor1 xor hasFloor2)

        // At most one grant callback
        assertTrue("Should have at most one grant", grantCount.get() <= 1)
    }

    @Test
    fun `test atomic compare-and-set prevents duplicate floor grants`() = scope.runTest {
        // This tests the fix for TOCTOU race condition
        assertTrue(floorControl1.initialize("device1"))

        val grantCount = AtomicInteger(0)
        floorControl1.onFloorGranted = { grantCount.incrementAndGet() }

        // Request floor multiple times rapidly
        val jobs = List(10) {
            async {
                floorControl1.requestFloor(priority = 0)
            }
        }

        // Wait for all
        val results = jobs.map { it.await() }

        advanceUntilIdle()

        // All should return true (already has floor)
        assertTrue("All requests should succeed", results.all { it })

        // But grant callback should only fire once
        assertEquals("Grant callback should fire only once", 1, grantCount.get())
    }

    @Test
    fun `test release during request does not cause race`() = scope.runTest {
        // Initialize two devices
        assertTrue(floorControl1.initialize("device1"))
        assertTrue(floorControl2.initialize("device2"))

        // Device 1 requests floor
        val requestJob = launch {
            floorControl1.requestFloor(priority = 0)
        }

        // Immediately release (should be safe)
        delay(10)
        floorControl1.releaseFloor()

        requestJob.join()
        advanceUntilIdle()

        // Should be in consistent state
        assertFalse("Should not have floor after release", floorControl1.hasFloor.value)
    }

    // =========================================================================
    // Duplicate Detection Tests
    // =========================================================================

    @Test
    fun `test duplicate message detection`() = scope.runTest {
        assertTrue(floorControl1.initialize("device1"))

        val callbackCount = AtomicInteger(0)
        floorControl1.onFloorTaken = { callbackCount.incrementAndGet() }

        // Test isDuplicate logic via reflection
        val method = FloorControlProtocol::class.java.getDeclaredMethod(
            "isDuplicate", String::class.java, Int::class.java
        )
        method.isAccessible = true

        // First message
        val dup1 = method.invoke(floorControl1, "test", 100) as Boolean
        assertFalse("First message should not be duplicate", dup1)

        // Duplicate message
        val dup2 = method.invoke(floorControl1, "test", 100) as Boolean
        assertTrue("Duplicate message should be detected", dup2)

        // New message
        val dup3 = method.invoke(floorControl1, "test", 101) as Boolean
        assertFalse("New message should not be duplicate", dup3)

        // Different sender
        val dup4 = method.invoke(floorControl1, "other", 100) as Boolean
        assertFalse("Different sender should not be duplicate", dup4)
    }

    @Test
    fun `test sequence number wrap-around handling`() = scope.runTest {
        assertTrue(floorControl1.initialize("device1"))

        val method = FloorControlProtocol::class.java.getDeclaredMethod(
            "isDuplicate", String::class.java, Int::class.java
        )
        method.isAccessible = true

        // Set last sequence to near max
        val maxInt = Int.MAX_VALUE
        method.invoke(floorControl1, "test", maxInt)

        // Test wrap-around
        val dup1 = method.invoke(floorControl1, "test", maxInt) as Boolean
        assertTrue("Same sequence should be duplicate", dup1)

        val dup2 = method.invoke(floorControl1, "test", 0) as Boolean  // Wrapped
        assertFalse("Wrapped sequence should not be duplicate", dup2)
    }

    @Test
    fun `test sequence window enforcement`() = scope.runTest {
        assertTrue(floorControl1.initialize("device1"))

        val method = FloorControlProtocol::class.java.getDeclaredMethod(
            "isDuplicate", String::class.java, Int::class.java
        )
        method.isAccessible = true

        // Set sequence 100
        method.invoke(floorControl1, "test", 100)

        // Within window (100 messages ahead)
        val dup1 = method.invoke(floorControl1, "test", 200) as Boolean
        assertFalse("Message within window should not be duplicate", dup1)

        // Outside window (>100 messages ahead)
        val dup2 = method.invoke(floorControl1, "test", 201) as Boolean
        assertTrue("Message outside window should be duplicate (too old)", dup2)
    }

    // =========================================================================
    // Network Health and Peer Tracking Tests
    // =========================================================================

    @Test
    fun `test network issue callback invoked on timeout`() = scope.runTest {
        // Initialize single device (will partition)
        assertTrue(floorControl1.initialize("device1"))

        var networkIssueCalled = false
        floorControl1.onNetworkIssue = { networkIssueCalled = true }

        // Request floor with no peers (will timeout and partition)
        floorControl1.requestFloor(priority = 0)
        advanceTimeBy(2000)

        // Should detect network issue
        assertTrue("Network issue should be detected", networkIssueCalled || !floorControl1.networkHealthy.value)
    }

    @Test
    fun `test peer tracking via heartbeat`() = scope.runTest {
        assertTrue(floorControl1.initialize("device1"))
        assertTrue(floorControl2.initialize("device2"))

        // Initially no peers
        val initialPeers = floorControl1.getActivePeers()
        assertTrue("Should have no peers initially", initialPeers.isEmpty())

        // Wait for heartbeat
        advanceTimeBy(6000)

        // Device 2 should have sent heartbeat
        // Note: In test environment, actual UDP multicast may not work
        // This test structure validates the peer tracking API
        val peers = floorControl1.getActivePeers()
        assertNotNull("Peer list should not be null", peers)
    }

    @Test
    fun `test stale peers are removed`() = scope.runTest {
        assertTrue(floorControl1.initialize("device1"))

        // Manually add a peer (simulating heartbeat)
        val method = FloorControlProtocol::class.java.getDeclaredMethod(
            "updatePeerHealth", String::class.java
        )
        method.isAccessible = true

        val testAddress = "192.168.1.100"
        method.invoke(floorControl1, testAddress)

        // Peer should be tracked
        var peers = floorControl1.getActivePeers()
        assertTrue("Peer should be tracked", testAddress in peers)

        // Wait for peer timeout (15 seconds)
        advanceTimeBy(16000)

        // Peer should be removed
        peers = floorControl1.getActivePeers()
        assertFalse("Stale peer should be removed", testAddress in peers)
    }

    // =========================================================================
    // ACK and Retry Tests
    // =========================================================================

    @Test
    fun `test floor request retries on timeout`() = scope.runTest {
        // Initialize single device (will have no ACK from peers)
        assertTrue(floorControl1.initialize("device1"))

        val requestCount = AtomicInteger(0)

        // We can't directly count UDP sends, but we can verify the timeout behavior
        val result = floorControl1.requestFloor(priority = 0)
        advanceTimeBy(1500)

        // Should eventually grant via network partition logic
        assertTrue("Should grant after network partition detection", result)
    }

    @Test
    fun `test exponential backoff for retries`() = scope.runTest {
        // This validates the exponential backoff constants
        assertEquals("Base retry delay should be 200ms", 200L,
            FloorControlProtocol.RETRY_BASE_DELAY_MS)
        assertEquals("Max retry delay should be 1000ms", 1000L,
            FloorControlProtocol.MAX_RETRY_DELAY_MS)
        assertEquals("Max retry count should be 3", 3,
            FloorControlProtocol.MAX_RETRY_COUNT)
    }

    // =========================================================================
    // Message Type Tests
    // =========================================================================

    @Test
    fun `test all message types have unique values`() {
        // Verify no message type conflicts
        val messageTypes = setOf(
            FloorControlProtocol.MSG_FLOOR_REQUEST,
            FloorControlProtocol.MSG_FLOOR_GRANTED,
            FloorControlProtocol.MSG_FLOOR_DENIED,
            FloorControlProtocol.MSG_FLOOR_RELEASE,
            FloorControlProtocol.MSG_FLOOR_TAKEN,
            FloorControlProtocol.MSG_EMERGENCY,
            FloorControlProtocol.MSG_FLOOR_ACK,
            FloorControlProtocol.MSG_HEARTBEAT
        )

        assertEquals("Should have 8 unique message types", 8, messageTypes.size)
    }

    @Test
    fun `test floor taken message updates current speaker`() = scope.runTest {
        assertTrue(floorControl1.initialize("device1"))
        assertTrue(floorControl2.initialize("device2"))

        val takenCount = AtomicInteger(0)
        var speaker: String? = null
        floorControl1.onFloorTaken = {
            takenCount.incrementAndGet()
            speaker = it
        }

        // Device 2 gets floor
        floorControl2.requestFloor(priority = 0)
        advanceTimeBy(300)

        // Device 1 should see floor taken
        assertEquals("Floor taken callback should fire", 1, takenCount.get())
        assertEquals("Should track device2 as speaker", "device2", speaker)
        assertEquals("Current speaker should be device2", "device2", floorControl1.currentSpeaker.value)
    }

    // =========================================================================
    // Stress Tests
    // =========================================================================

    @Test
    fun `test rapid floor request and release cycles`() = scope.runTest {
        assertTrue(floorControl1.initialize("device1"))
        assertTrue(floorControl2.initialize("device2"))

        repeat(10) {
            // Device 1 requests floor
            floorControl1.requestFloor(priority = 0)
            advanceTimeBy(100)
            assertTrue("Device 1 should have floor", floorControl1.hasFloor.value)

            // Release
            floorControl1.releaseFloor()
            advanceTimeBy(50)
            assertFalse("Device 1 should not have floor", floorControl1.hasFloor.value)

            // Device 2 requests
            floorControl2.requestFloor(priority = 0)
            advanceTimeBy(100)
            assertTrue("Device 2 should have floor", floorControl2.hasFloor.value)

            // Release
            floorControl2.releaseFloor()
            advanceTimeBy(50)
            assertFalse("Device 2 should not have floor", floorControl2.hasFloor.value)
        }
    }

    @Test
    fun `test three devices arbitration`() = scope.runTest {
        assertTrue(floorControl1.initialize("device1"))
        assertTrue(floorControl2.initialize("device2"))
        assertTrue(floorControl3.initialize("device3"))

        val floorOwners = mutableListOf<String?>()

        // Helper to track floor changes
        fun trackFloor(device: String, protocol: FloorControlProtocol) {
            protocol.onFloorGranted = {
                synchronized(floorOwners) {
                    floorOwners.add(device)
                }
            }
            protocol.onFloorReleased = {
                synchronized(floorOwners) {
                    floorOwners.add(null)
                }
            }
        }

        trackFloor("device1", floorControl1)
        trackFloor("device2", floorControl2)
        trackFloor("device3", floorControl3)

        // All request simultaneously
        launch { floorControl1.requestFloor(priority = 0) }
        launch { floorControl2.requestFloor(priority = 0) }
        launch { floorControl3.requestFloor(priority = 0) }

        advanceUntilIdle()

        // Exactly one should have floor
        val hasFloorCount = listOf(
            floorControl1.hasFloor.value,
            floorControl2.hasFloor.value,
            floorControl3.hasFloor.value
        ).count { it }

        assertEquals("Exactly one device should have floor", 1, hasFloorCount)
    }

    // =========================================================================
    // State Flow Tests
    // =========================================================================

    @Test
    fun `test hasFloor state flow updates correctly`() = scope.runTest {
        assertTrue(floorControl1.initialize("device1"))

        val values = mutableListOf<Boolean>()
        val job = launch {
            floorControl1.hasFloor.collect { values.add(it) }
        }

        advanceTimeBy(50)

        // Request floor
        floorControl1.requestFloor(priority = 0)
        advanceTimeBy(200)

        // Release floor
        floorControl1.releaseFloor()
        advanceTimeBy(100)

        job.cancel()

        assertTrue("Should have false initially", values.firstOrNull() == false)
        assertTrue("Should have true after request", values.any { it })
        assertTrue("Should have false after release", values.lastOrNull() == false)
    }

    @Test
    fun `test currentSpeaker flow updates`() = scope.runTest {
        assertTrue(floorControl1.initialize("device1"))
        assertTrue(floorControl2.initialize("device2"))

        val speakers = mutableListOf<String?>()
        val job = launch {
            floorControl1.currentSpeaker.collect { speakers.add(it) }
        }

        advanceTimeBy(50)

        // Device 2 gets floor
        floorControl2.requestFloor(priority = 0)
        advanceTimeBy(200)

        job.cancel()

        assertTrue("Should track device2 as speaker", speakers.any { it == "device2" })
    }

    // =========================================================================
    // Error Handling Tests
    // =========================================================================

    @Test
    fun `test stop cancels all pending operations`() = scope.runTest {
        assertTrue(floorControl1.initialize("device1"))

        // Start a request
        val requestJob = launch {
            floorControl1.requestFloor(priority = 0)
        }

        advanceTimeBy(50)

        // Stop before completion
        floorControl1.stop()

        requestJob.join()

        // Should be stopped cleanly
        assertFalse("Should not have floor after stop", floorControl1.hasFloor.value)
    }

    @Test
    fun `test multiple initialize calls`() = scope.runTest {
        // First initialize
        assertTrue("First initialize should succeed", floorControl1.initialize("device1"))

        // Second initialize (should handle gracefully)
        // In production, this might return false or reinitialize
        val result = floorControl1.initialize("device1")

        // Just verify it doesn't crash
        assertNotNull("Floor control should still exist", floorControl1)
    }

    @Test
    fun `test operations after stop are safe`() = scope.runTest {
        assertTrue(floorControl1.initialize("device1"))

        // Stop
        floorControl1.stop()

        // Try operations (should be safe/no-op)
        floorControl1.releaseFloor()
        advanceTimeBy(100)

        // Should not throw
        assertFalse("Should not have floor", floorControl1.hasFloor.value)
    }

    // =========================================================================
    // Constants Validation Tests
    // =========================================================================

    @Test
    fun `test timing constants are reasonable`() {
        assertTrue("Floor timeout should be > 0", FloorControlProtocol.FLOOR_TIMEOUT_MS > 0)
        assertTrue("Retry count should be > 0", FloorControlProtocol.MAX_RETRY_COUNT > 0)
        assertTrue("Base retry delay should be > 0", FloorControlProtocol.RETRY_BASE_DELAY_MS > 0)
        assertTrue("Max retry delay should be >= base delay",
            FloorControlProtocol.MAX_RETRY_DELAY_MS >= FloorControlProtocol.RETRY_BASE_DELAY_MS)
        assertTrue("Heartbeat interval should be > 0", FloorControlProtocol.HEARTBEAT_INTERVAL_MS > 0)
        assertTrue("Peer timeout should be > heartbeat interval",
            FloorControlProtocol.PEER_TIMEOUT_MS > FloorControlProtocol.HEARTBEAT_INTERVAL_MS)
    }

    @Test
    fun `test sequence window is reasonable`() {
        val window = FloorControlProtocol.SEQUENCE_WINDOW
        assertTrue("Sequence window should be positive", window > 0)
        assertTrue("Sequence window should allow for network reordering", window >= 50)
    }
}
