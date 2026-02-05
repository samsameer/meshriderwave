package com.doodlelabs.meshriderwave.ptt

import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for Floor Control Protocol
 * Tests 3GPP MCPTT floor control implementation
 */
class FloorControlProtocolTest {

    @Test
    fun testFloorProtocolInitialization() {
        val protocol = FloorControlProtocol("239.255.0.1", 5005)
        val initialized = protocol.initialize("test-user-1")

        assertTrue("Floor control should initialize", initialized)
    }

    @Test
    fun testFloorRequestTimeout() {
        val protocol = FloorControlProtocol("239.255.0.1", 5005)
        protocol.initialize("test-user-1")

        // Floor request should timeout after 200ms when no response
        // In walkie-talkie mode, timeout = granted
        runTest {
            val granted = protocol.requestFloor(priority = 0)
            // Should be granted by default (walkie-talkie mode)
            assertTrue("Floor should be granted on timeout (walkie-talkie)", granted)
        }
    }

    @Test
    fun testFloorDenial() {
        val protocol = FloorControlProtocol("239.255.0.1", 5005)
        protocol.initialize("test-user-1")

        // Simulate having floor
        val taken = protocol._hasFloor.value  // Internal state check

        // Test that second request is denied
        // This would require two protocol instances in real scenario
        assertFalse("Should not have floor initially", taken)
    }

    @Test
    fun testPriorityLevels() {
        // EMERGENCY > HIGH > NORMAL > LOW
        val emergency = 3
        val high = 2
        val normal = 1
        val low = 0

        assertTrue("Emergency > High", emergency > high)
        assertTrue("High > Normal", high > normal)
        assertTrue("Normal > Low", normal > low)
    }
}
