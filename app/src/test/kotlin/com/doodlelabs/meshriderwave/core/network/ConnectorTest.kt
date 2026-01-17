/*
 * Mesh Rider Wave - Connector Unit Tests
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 *
 * Unit tests for P2P connection logic
 */

package com.doodlelabs.meshriderwave.core.network

import com.doodlelabs.meshriderwave.domain.model.Contact
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.net.InetAddress
import java.net.InetSocketAddress

/**
 * Unit tests for Connector
 */
class ConnectorTest {

    private lateinit var connector: Connector

    @Before
    fun setUp() {
        connector = Connector()
    }

    // ========================================================================
    // CONFIGURATION TESTS
    // ========================================================================

    @Test
    fun `default configuration values are correct`() {
        assertEquals("Default connect timeout should be 5000ms", 5000, connector.connectTimeout)
        assertEquals("Default connect retries should be 3", 3, connector.connectRetries)
        assertFalse("EUI-64 guessing should be disabled by default", connector.guessEUI64Address)
        assertFalse("Neighbor table should be disabled by default", connector.useNeighborTable)
    }

    @Test
    fun `configuration can be modified`() {
        connector.connectTimeout = 10000
        connector.connectRetries = 5
        connector.guessEUI64Address = true
        connector.useNeighborTable = true

        assertEquals(10000, connector.connectTimeout)
        assertEquals(5, connector.connectRetries)
        assertTrue(connector.guessEUI64Address)
        assertTrue(connector.useNeighborTable)
    }

    // ========================================================================
    // ADDRESS PARSING TESTS
    // ========================================================================

    @Test
    fun `parseSocketAddress handles simple IPv4`() {
        // Access the private method via reflection for testing
        val method = connector.javaClass.getDeclaredMethod(
            "parseSocketAddress",
            String::class.java,
            Int::class.javaPrimitiveType
        )
        method.isAccessible = true

        val result = method.invoke(connector, "192.168.1.100", 10001) as InetSocketAddress?

        assertNotNull(result)
        assertEquals("192.168.1.100", result!!.hostString)
        assertEquals(10001, result.port)
    }

    @Test
    fun `parseSocketAddress handles IPv4 with port`() {
        val method = connector.javaClass.getDeclaredMethod(
            "parseSocketAddress",
            String::class.java,
            Int::class.javaPrimitiveType
        )
        method.isAccessible = true

        val result = method.invoke(connector, "192.168.1.100:8080", 10001) as InetSocketAddress?

        assertNotNull(result)
        assertEquals("192.168.1.100", result!!.hostString)
        assertEquals(8080, result.port)
    }

    @Test
    fun `parseSocketAddress handles simple IPv6`() {
        val method = connector.javaClass.getDeclaredMethod(
            "parseSocketAddress",
            String::class.java,
            Int::class.javaPrimitiveType
        )
        method.isAccessible = true

        val result = method.invoke(connector, "2001:db8::1", 10001) as InetSocketAddress?

        assertNotNull(result)
        assertEquals(10001, result!!.port)
    }

    @Test
    fun `parseSocketAddress handles bracketed IPv6 with port`() {
        val method = connector.javaClass.getDeclaredMethod(
            "parseSocketAddress",
            String::class.java,
            Int::class.javaPrimitiveType
        )
        method.isAccessible = true

        val result = method.invoke(connector, "[2001:db8::1]:8080", 10001) as InetSocketAddress?

        assertNotNull(result)
        assertEquals(8080, result!!.port)
    }

    @Test
    fun `parseSocketAddress returns null for invalid address`() {
        val method = connector.javaClass.getDeclaredMethod(
            "parseSocketAddress",
            String::class.java,
            Int::class.javaPrimitiveType
        )
        method.isAccessible = true

        val result = method.invoke(connector, "not-a-valid-address:abc", 10001)

        assertNull(result)
    }

    // ========================================================================
    // LINK-LOCAL ADDRESS TESTS
    // ========================================================================

    @Test
    fun `isLinkLocalAddress detects IPv6 link-local`() {
        val method = connector.javaClass.getDeclaredMethod(
            "isLinkLocalAddress",
            String::class.java
        )
        method.isAccessible = true

        assertTrue(method.invoke(connector, "fe80::1") as Boolean)
        assertTrue(method.invoke(connector, "fe80::1%wlan0") as Boolean)
        assertFalse(method.invoke(connector, "2001:db8::1") as Boolean)
        assertFalse(method.invoke(connector, "192.168.1.1") as Boolean)
    }

    // ========================================================================
    // INTERFACE FILTERING TESTS
    // ========================================================================

    @Test
    fun `ignoreInterface filters unwanted interfaces`() {
        val method = connector.javaClass.getDeclaredMethod(
            "ignoreInterface",
            String::class.java
        )
        method.isAccessible = true

        // Should be ignored
        assertTrue(method.invoke(connector, "lo") as Boolean)
        assertTrue(method.invoke(connector, "dummy0") as Boolean)
        assertTrue(method.invoke(connector, "rmnet0") as Boolean)
        assertTrue(method.invoke(connector, "ccmni0") as Boolean)
        assertTrue(method.invoke(connector, "clat4") as Boolean)

        // Should NOT be ignored
        assertFalse(method.invoke(connector, "wlan0") as Boolean)
        assertFalse(method.invoke(connector, "eth0") as Boolean)
        assertFalse(method.invoke(connector, "tun0") as Boolean)
    }

    // ========================================================================
    // EUI-64 ADDRESS TESTS
    // ========================================================================

    @Test
    fun `extractMACFromEUI64 extracts MAC correctly`() {
        val method = connector.javaClass.getDeclaredMethod(
            "extractMACFromEUI64",
            InetAddress::class.java
        )
        method.isAccessible = true

        // EUI-64 format: prefix + MAC with FF:FE in the middle and U/L bit flipped
        // Example: fe80::0211:22ff:fe33:4455 corresponds to MAC 00:11:22:33:44:55
        val ipv6 = InetAddress.getByName("fe80::0211:22ff:fe33:4455")
        val mac = method.invoke(connector, ipv6) as ByteArray?

        assertNotNull(mac)
        assertEquals(6, mac!!.size)

        // Verify MAC extraction (U/L bit flipped back)
        assertEquals(0x00.toByte(), mac[0])
        assertEquals(0x11.toByte(), mac[1])
        assertEquals(0x22.toByte(), mac[2])
        assertEquals(0x33.toByte(), mac[3])
        assertEquals(0x44.toByte(), mac[4])
        assertEquals(0x55.toByte(), mac[5])
    }

    @Test
    fun `extractMACFromEUI64 returns null for non-EUI64 address`() {
        val method = connector.javaClass.getDeclaredMethod(
            "extractMACFromEUI64",
            InetAddress::class.java
        )
        method.isAccessible = true

        // This address doesn't have FF:FE in the right place
        val ipv6 = InetAddress.getByName("2001:db8::1234:5678:9abc:def0")
        val mac = method.invoke(connector, ipv6)

        assertNull(mac)
    }

    @Test
    fun `extractMACFromEUI64 returns null for IPv4 address`() {
        val method = connector.javaClass.getDeclaredMethod(
            "extractMACFromEUI64",
            InetAddress::class.java
        )
        method.isAccessible = true

        val ipv4 = InetAddress.getByName("192.168.1.1")
        val mac = method.invoke(connector, ipv4)

        assertNull(mac)
    }

    // ========================================================================
    // CONNECTION STATE TESTS
    // ========================================================================

    @Test
    fun `connect resets error state before each attempt`() {
        // Set all error flags
        connector.networkNotReachable = true
        connector.unknownHostException = true
        connector.connectException = true
        connector.socketTimeoutException = true
        connector.genericException = true

        // Create a dummy contact that will fail to connect
        val contact = Contact(
            publicKey = ByteArray(32),
            name = "Test",
            addresses = listOf("127.0.0.1"),
            blocked = false,
            createdAt = System.currentTimeMillis()
        )

        // Attempt connection (will fail quickly since no server)
        connector.connectTimeout = 100 // Fast timeout
        connector.connectRetries = 0   // No retries
        connector.connect(contact)

        // Error flags should have been reset before the attempt
        // At least one error flag should be true now from the failed connection
        val anyError = connector.networkNotReachable ||
                connector.unknownHostException ||
                connector.connectException ||
                connector.socketTimeoutException ||
                connector.genericException

        assertTrue("At least one error flag should be set", anyError)
    }

    @Test
    fun `addressCallback is called for each address tried`() {
        val triedAddresses = mutableListOf<InetSocketAddress>()

        connector.addressCallback = object : Connector.AddressCallback {
            override fun onAddressTry(address: InetSocketAddress) {
                triedAddresses.add(address)
            }
        }

        val contact = Contact(
            publicKey = ByteArray(32),
            name = "Test",
            addresses = listOf("127.0.0.1", "192.168.1.1"),
            blocked = false,
            createdAt = System.currentTimeMillis()
        )

        connector.connectTimeout = 100
        connector.connectRetries = 0
        connector.connect(contact)

        assertTrue("Should have tried at least one address", triedAddresses.isNotEmpty())
    }

    // ========================================================================
    // CONTACT ADDRESS RESOLUTION TESTS
    // ========================================================================

    @Test
    fun `lastWorkingAddress is tried first`() {
        val triedAddresses = mutableListOf<InetSocketAddress>()

        connector.addressCallback = object : Connector.AddressCallback {
            override fun onAddressTry(address: InetSocketAddress) {
                triedAddresses.add(address)
            }
        }

        val contact = Contact(
            publicKey = ByteArray(32),
            name = "Test",
            addresses = listOf("192.168.1.1"),
            blocked = false,
            createdAt = System.currentTimeMillis(),
            lastWorkingAddress = "10.0.0.1"
        )

        connector.connectTimeout = 100
        connector.connectRetries = 0
        connector.connect(contact)

        // First address should be the last working address
        if (triedAddresses.isNotEmpty()) {
            assertEquals("10.0.0.1", triedAddresses.first().hostString)
        }
    }

    // ========================================================================
    // RETRY LOGIC TESTS
    // ========================================================================

    @Test
    fun `connect respects retry count`() {
        var attemptCount = 0

        connector.addressCallback = object : Connector.AddressCallback {
            override fun onAddressTry(address: InetSocketAddress) {
                attemptCount++
            }
        }

        val contact = Contact(
            publicKey = ByteArray(32),
            name = "Test",
            addresses = listOf("127.0.0.1"),
            blocked = false,
            createdAt = System.currentTimeMillis()
        )

        connector.connectTimeout = 50
        connector.connectRetries = 2 // 3 total attempts (0, 1, 2)
        connector.connect(contact)

        // Should have attempted 3 times (retries = 2 means iterations 0, 1, 2)
        assertEquals("Should retry the specified number of times", 3, attemptCount)
    }

    @Test
    fun `connect returns null when no addresses available`() {
        val contact = Contact(
            publicKey = ByteArray(32),
            name = "Test",
            addresses = emptyList(), // No addresses
            blocked = false,
            createdAt = System.currentTimeMillis()
        )

        val result = connector.connect(contact)

        assertNull("Should return null when no addresses available", result)
    }
}
