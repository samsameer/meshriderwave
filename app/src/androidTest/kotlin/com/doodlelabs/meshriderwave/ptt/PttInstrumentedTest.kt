package com.doodlelabs.meshriderwave.ptt

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

/**
 * Instrumented tests for PTT functionality
 * Requires two devices for full testing
 */
@RunWith(AndroidJUnit4::class)
class PttInstrumentedTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
    }

    @After
    fun tearDown() {
        // Cleanup
    }

    @Test
    fun testPttScreenExists() {
        // Verify PTT screen can be launched
        val intent = android.content.Intent(context, WorkingPttScreen::class.java)
        val resolveInfo = context.packageManager.resolveActivity(intent, 0)

        assertNotNull("PTT screen should exist", resolveInfo)
    }

    @Test
    fun testPttServiceExists() {
        val intent = android.content.Intent(context, PttService::class.java)
        val resolveInfo = context.packageManager.resolveService(intent, 0)

        assertNotNull("PTT service should exist", resolveInfo)
    }

    @Test
    fun testMicrophonePermission() {
        val result = context.checkCallingPermission("android.permission.RECORD_AUDIO")
        assertEquals("Should have mic permission", android.content.pm.PackageManager.PERMISSION_GRANTED, result)
    }

    @Test
    fun testForegroundServiceType() {
        // Verify PTT service uses microphone foreground service type (Android 14+)
        val packageInfo = context.packageManager.getPackageInfo(context.packageName,
            android.content.pm.PackageManager.GET_SERVICES)

        val services = packageInfo.services
        val pttService = services?.find { it.name.contains("PttService") }

        assertNotNull("PTT service should be registered", pttService)

        // Check for foregroundServiceType
        val serviceInfo = pttService?.flags ?: 0
        val hasMicrophoneType = (serviceInfo and android.content.pm.ServiceInfo.FLAG_FOREGROUND_SERVICE_TYPE_MICROPHONE) != 0

        assertTrue("PTT service should have MICROPHONE type", hasMicrophoneType)
    }

    @Test
    fun testXCoverButtonReceiverExists() {
        val intent = android.content.Intent()
        intent.action = "com.samsung.android.intent.XCOVER_KEY"

        val receivers = context.packageManager.queryBroadcastReceivers(intent, 0)
        val hasReceiver = receivers.iterator().hasNext()

        assertTrue("XCover button receiver should be registered", hasReceiver)
    }
}
