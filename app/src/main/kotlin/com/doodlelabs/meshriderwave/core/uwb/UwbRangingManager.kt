/*
 * Mesh Rider Wave - UWB Ranging Manager
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 *
 * Ultra-Wideband precise ranging for Blue Force Tracking.
 * Provides ~10cm accuracy positioning between UWB-enabled devices.
 *
 * Reference: https://developer.android.com/develop/connectivity/uwb
 *
 * Architecture:
 * - Controller: The device that creates the UWB channel (higher priority peer)
 * - Controlee: The device that responds (lower priority peer)
 * - Ranging params exchanged via existing beacon/mDNS discovery
 * - Uses Provisioned STS for security (API 34+)
 */

package com.doodlelabs.meshriderwave.core.uwb

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.uwb.RangingParameters
import androidx.core.uwb.RangingResult
import androidx.core.uwb.UwbAddress
import androidx.core.uwb.UwbClientSessionScope
import androidx.core.uwb.UwbControleeSessionScope
import androidx.core.uwb.UwbControllerSessionScope
import androidx.core.uwb.UwbManager
import com.doodlelabs.meshriderwave.core.util.logD
import com.doodlelabs.meshriderwave.core.util.logE
import com.doodlelabs.meshriderwave.core.util.logI
import com.doodlelabs.meshriderwave.core.util.logW
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * UWB ranging result for a single peer.
 */
data class UwbPeerRange(
    val peerPublicKey: ByteArray,
    val distanceCm: Float,
    val azimuthDegrees: Float?,
    val elevationDegrees: Float?,
    val timestampMs: Long = System.currentTimeMillis()
) {
    /** Distance in meters */
    val distanceM: Float get() = distanceCm / 100f

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UwbPeerRange) return false
        return peerPublicKey.contentEquals(other.peerPublicKey)
    }

    override fun hashCode() = peerPublicKey.contentHashCode()
}

@Singleton
class UwbRangingManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** Whether UWB hardware is available on this device */
    val isUwbSupported: Boolean by lazy {
        context.packageManager.hasSystemFeature("android.hardware.uwb")
    }

    /** Active ranging results per peer */
    private val _rangingResults = MutableStateFlow<Map<String, UwbPeerRange>>(emptyMap())
    val rangingResults: StateFlow<Map<String, UwbPeerRange>> = _rangingResults.asStateFlow()

    /** UWB availability state */
    private val _isAvailable = MutableStateFlow(false)
    val isAvailable: StateFlow<Boolean> = _isAvailable.asStateFlow()

    /** Active ranging sessions */
    private val activeSessions = mutableMapOf<String, Job>()

    private var uwbManager: UwbManager? = null

    /**
     * Initialize UWB manager. Call once on app start.
     */
    fun initialize() {
        if (!isUwbSupported) {
            logW("UWB hardware not available on this device")
            return
        }

        try {
            uwbManager = UwbManager.createInstance(context)
            _isAvailable.value = true
            logI("UWB initialized successfully")
        } catch (e: Exception) {
            logE("Failed to initialize UWB", e)
            _isAvailable.value = false
        }
    }

    /**
     * Get this device's UWB local address for exchange via beacon.
     * Must be called from a Controller session scope.
     */
    suspend fun getControllerSessionScope(): UwbControllerSessionScope? {
        return try {
            uwbManager?.controllerSessionScope()
        } catch (e: Exception) {
            logE("Failed to get controller session scope", e)
            null
        }
    }

    /**
     * Get this device's UWB local address for exchange via beacon.
     * Must be called from a Controlee session scope.
     */
    suspend fun getControleeSessionScope(): UwbControleeSessionScope? {
        return try {
            uwbManager?.controleeSessionScope()
        } catch (e: Exception) {
            logE("Failed to get controlee session scope", e)
            null
        }
    }

    /**
     * Start ranging with a peer as Controller.
     *
     * @param peerPublicKey The peer's public key (for identification)
     * @param peerUwbAddress The peer's UWB address (exchanged via beacon)
     * @param sessionKeyInfo Shared session key for secure ranging
     * @param complexChannel The UWB complex channel to use
     */
    fun startRangingAsController(
        peerPublicKey: ByteArray,
        peerUwbAddress: ByteArray,
        sessionKeyInfo: ByteArray,
        channelNumber: Int = 9,
        preambleIndex: Int = 10
    ) {
        val peerId = peerPublicKey.toHex()
        if (activeSessions.containsKey(peerId)) {
            logD("Already ranging with peer $peerId")
            return
        }

        activeSessions[peerId] = scope.launch {
            try {
                val controllerScope = getControllerSessionScope() ?: return@launch

                val rangingParams = RangingParameters(
                    uwbConfigType = RangingParameters.CONFIG_UNICAST_DS_TWR,
                    sessionId = peerPublicKey.take(4).fold(0) { acc, b -> acc * 256 + (b.toInt() and 0xFF) },
                    sessionKeyInfo = sessionKeyInfo,
                    complexChannel = controllerScope.uwbComplexChannel,
                    peerDevices = listOf(
                        androidx.core.uwb.UwbDevice(UwbAddress(peerUwbAddress))
                    ),
                    updateRateType = RangingParameters.RANGING_UPDATE_RATE_AUTOMATIC,
                    subSessionId = 0,
                    subSessionKeyInfo = null
                )

                controllerScope.prepareSession(rangingParams).collect { result ->
                    handleRangingResult(peerPublicKey, result)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logE("Ranging error with peer $peerId", e)
            } finally {
                activeSessions.remove(peerId)
            }
        }
    }

    /**
     * Start ranging with a peer as Controlee.
     */
    fun startRangingAsControlee(
        peerPublicKey: ByteArray,
        peerUwbAddress: ByteArray,
        sessionKeyInfo: ByteArray,
        channelNumber: Int = 9,
        preambleIndex: Int = 10
    ) {
        val peerId = peerPublicKey.toHex()
        if (activeSessions.containsKey(peerId)) {
            logD("Already ranging with peer $peerId")
            return
        }

        activeSessions[peerId] = scope.launch {
            try {
                val controleeScope = getControleeSessionScope() ?: return@launch

                val rangingParams = RangingParameters(
                    uwbConfigType = RangingParameters.CONFIG_UNICAST_DS_TWR,
                    sessionId = peerPublicKey.take(4).fold(0) { acc, b -> acc * 256 + (b.toInt() and 0xFF) },
                    sessionKeyInfo = sessionKeyInfo,
                    complexChannel = null,
                    peerDevices = listOf(
                        androidx.core.uwb.UwbDevice(UwbAddress(peerUwbAddress))
                    ),
                    updateRateType = RangingParameters.RANGING_UPDATE_RATE_AUTOMATIC,
                    subSessionId = 0,
                    subSessionKeyInfo = null
                )

                controleeScope.prepareSession(rangingParams).collect { result ->
                    handleRangingResult(peerPublicKey, result)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logE("Ranging error with peer $peerId", e)
            } finally {
                activeSessions.remove(peerId)
            }
        }
    }

    private fun handleRangingResult(peerPublicKey: ByteArray, result: RangingResult) {
        val peerId = peerPublicKey.toHex()

        when (result) {
            is RangingResult.RangingResultPosition -> {
                val position = result.position
                val distance = position.distance
                val azimuth = position.azimuth
                val elevation = position.elevation

                if (distance != null) {
                    val range = UwbPeerRange(
                        peerPublicKey = peerPublicKey,
                        distanceCm = distance.value * 100, // meters to cm
                        azimuthDegrees = azimuth?.value,
                        elevationDegrees = elevation?.value
                    )

                    _rangingResults.update { current ->
                        current + (peerId to range)
                    }

                    logD("UWB range: peer=$peerId dist=${range.distanceM}m az=${range.azimuthDegrees}")
                }
            }
            is RangingResult.RangingResultPeerDisconnected -> {
                logW("UWB peer disconnected: $peerId")
                _rangingResults.update { it - peerId }
            }
            else -> {
                logD("UWB ranging result: $result")
            }
        }
    }

    /**
     * Stop ranging with a specific peer.
     */
    fun stopRanging(peerPublicKey: ByteArray) {
        val peerId = peerPublicKey.toHex()
        activeSessions[peerId]?.cancel()
        activeSessions.remove(peerId)
        _rangingResults.update { it - peerId }
        logD("Stopped ranging with peer $peerId")
    }

    /**
     * Stop all ranging sessions.
     */
    fun stopAll() {
        activeSessions.values.forEach { it.cancel() }
        activeSessions.clear()
        _rangingResults.value = emptyMap()
        logI("All UWB ranging stopped")
    }

    /**
     * Get range for a specific peer.
     */
    fun getRangeForPeer(peerPublicKey: ByteArray): UwbPeerRange? {
        return _rangingResults.value[peerPublicKey.toHex()]
    }

    /**
     * Clean up resources.
     */
    fun cleanup() {
        stopAll()
        scope.cancel()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
