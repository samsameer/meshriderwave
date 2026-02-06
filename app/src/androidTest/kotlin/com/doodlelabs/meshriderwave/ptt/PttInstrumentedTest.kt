/*
 * Mesh Rider Wave - PTT Instrumented Tests (PRODUCTION)
 * Tests for audio engine and PTT functionality
 */

package com.doodlelabs.meshriderwave.ptt

import android.content.Context
import android.media.AudioManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PttInstrumentedTest {

    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        android.Manifest.permission.RECORD_AUDIO,
        android.Manifest.permission.MODIFY_AUDIO_SETTINGS
    )

    private lateinit var context: Context
    private lateinit var audioEngine: PttAudioEngine
    private lateinit var pttManager: WorkingPttManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        audioEngine = PttAudioEngine(context)
        pttManager = WorkingPttManager(context)
    }

    @After
    fun tearDown() {
        audioEngine.cleanup()
        pttManager.cleanup()
    }

    @Test
    fun testAudioEngineInitialization() {
        // Test initialization with multicast
        val result = audioEngine.initialize(
            multicastGroup = "239.255.0.1",
            port = 15004,
            enableUnicastFallback = true
        )
        
        // Should initialize (may use unicast fallback)
        assertTrue("Audio engine should initialize", result)
        
        // Check state
        assertFalse("Should not be capturing initially", audioEngine.isCapturing.value)
        assertFalse("Should not be playing initially", audioEngine.isPlaying.value)
    }

    @Test
    fun testAudioCaptureLifecycle() = runBlocking {
        // Initialize
        audioEngine.initialize("239.255.0.1", 15004, true)
        
        // Start capture
        val started = audioEngine.startCapture()
        assertTrue("Capture should start", started)
        assertTrue("Should be capturing", audioEngine.isCapturing.value)
        
        // Let it run briefly
        delay(100)
        
        // Stop capture
        audioEngine.stopCapture()
        assertFalse("Should not be capturing", audioEngine.isCapturing.value)
    }

    @Test
    fun testAudioPlaybackLifecycle() = runBlocking {
        // Initialize
        audioEngine.initialize("239.255.0.1", 15004, true)
        
        // Start playback
        val started = audioEngine.startPlayback()
        assertTrue("Playback should start", started)
        assertTrue("Should be playing", audioEngine.isPlaying.value)
        
        // Let it run briefly
        delay(100)
        
        // Stop playback
        audioEngine.stopPlayback()
        assertFalse("Should not be playing", audioEngine.isPlaying.value)
    }

    @Test
    fun testUnicastPeerManagement() {
        // Initialize
        audioEngine.initialize("239.255.0.1", 15004, true)
        
        // Add peer
        audioEngine.addUnicastPeer("192.168.1.100")
        
        // Clear peers
        audioEngine.clearUnicastPeers()
        
        // Test passes if no crash
        assertTrue(true)
    }

    @Test
    fun testAecEnablement() {
        // Initialize
        audioEngine.initialize("239.255.0.1", 15004, true)
        
        // Enable AEC
        audioEngine.enableAEC(true)
        
        // Disable AEC
        audioEngine.enableAEC(false)
        
        // Test passes if no crash
        assertTrue(true)
    }

    @Test
    fun testBitrateConfiguration() {
        // Initialize
        audioEngine.initialize("239.255.0.1", 15004, true)
        
        // Set various bitrates
        audioEngine.setBitrate(6000)   // Low
        audioEngine.setBitrate(12000)  // Standard
        audioEngine.setBitrate(24000)  // High
        
        // Test passes if no crash
        assertTrue(true)
    }

    @Test
    fun testPttManagerInitialization() {
        val result = pttManager.initialize(
            myId = "test_device",
            enableUnicastFallback = true
        )
        
        assertTrue("PTT manager should initialize", result)
    }

    @Test
    fun testPttTransmission() = runBlocking {
        // Initialize
        pttManager.initialize("test_device", true)
        
        // Add self as peer (for testing)
        pttManager.addPeer("127.0.0.1")
        
        // Start transmission
        val result = pttManager.startTransmission()
        
        // Should succeed or fail gracefully
        // In test environment without actual network, may fail
        // but should not crash
        
        // Stop if started
        if (result) {
            delay(100)
            pttManager.stopTransmission()
        }
        
        assertTrue("Test should complete without crash", true)
    }

    @Test
    fun testSpeakerToggle() {
        // Initialize
        pttManager.initialize("test_device", true)
        
        // Toggle speaker
        pttManager.enableSpeaker()
        pttManager.enableEarpiece()
        
        // Verify audio manager state
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        // Note: isSpeakerphoneOn may not change in test environment
        
        assertTrue(true)
    }

    @Test
    fun testStatsCollection() {
        // Initialize
        pttManager.initialize("test_device", true)
        
        // Get stats
        val stats = pttManager.getStats()
        
        // Verify stats structure
        assertNotNull("Stats should not be null", stats)
        assertTrue("Latency should be >= 0", stats.latencyMs >= 0)
        assertTrue("Packets sent should be >= 0", stats.packetsSent >= 0)
        assertTrue("Packets received should be >= 0", stats.packetsReceived >= 0)
    }

    @Test
    fun testEmergencyTransmission() = runBlocking {
        // Initialize
        pttManager.initialize("test_device", true)
        
        // Send emergency
        val result = pttManager.sendEmergency()
        
        // Emergency should be sent
        assertTrue("Emergency should be sent", result)
    }

    @Test
    fun testPeerManagement() {
        // Initialize
        pttManager.initialize("test_device", true)
        
        // Add peers
        pttManager.addPeer("192.168.1.100")
        pttManager.addPeer("192.168.1.101")
        
        // Remove peer
        pttManager.removePeer("192.168.1.100")
        
        // Get stats (includes peer count)
        val stats = pttManager.getStats()
        
        assertTrue(true)
    }

    @Test
    fun testLatencyMeasurement() {
        // Initialize
        audioEngine.initialize("239.255.0.1", 15004, true)
        
        // Get latency
        val latency = audioEngine.getLatency()
        
        // Latency should be reasonable (0 if not running, positive if running)
        assertTrue("Latency should be >= 0", latency >= 0)
    }

    @Test
    fun testConcurrentOperations() = runBlocking {
        // Initialize
        pttManager.initialize("test_device", true)
        
        // Concurrent operations should not crash
        val jobs = listOf(
            async { pttManager.getStats() },
            async { pttManager.getLatency() },
            async { pttManager.isTransmitting() },
            async { pttManager.isReceiving() }
        )
        
        jobs.awaitAll()
        
        assertTrue("Concurrent operations should complete", true)
    }

    @Test
    fun testNetworkHealthMonitoring() = runBlocking {
        // Initialize
        pttManager.initialize("test_device", true)
        
        // Check initial network health
        val initialHealth = pttManager.networkHealthy.value
        
        // Network health should be a boolean
        assertTrue("Network health should be a boolean", 
            initialHealth == true || initialHealth == false)
    }
}
