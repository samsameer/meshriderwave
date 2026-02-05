/*
 * Mesh Rider Wave - Telecom Call Manager
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 *
 * Integrates with Android's Core-Telecom Jetpack library (androidx.core.telecom)
 * to register calls with the OS. This enables:
 * - Proper audio focus management by the system
 * - Audio endpoint routing (speaker/earpiece/bluetooth) via CallControlScope
 * - Call notifications forwarded to Wear OS, Android Auto, Bluetooth headsets
 * - Interoperability with system telephony (call hold/swap)
 *
 * References:
 * - https://developer.android.com/develop/connectivity/telecom/selfManaged
 * - https://github.com/android/platform-samples/tree/main/samples/connectivity/telecom
 */

package com.doodlelabs.meshriderwave.core.telecom

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.core.telecom.CallAttributesCompat
import androidx.core.telecom.CallControlScope
import androidx.core.telecom.CallEndpointCompat
import androidx.core.telecom.CallsManager
import com.doodlelabs.meshriderwave.core.util.logD
import com.doodlelabs.meshriderwave.core.util.logE
import com.doodlelabs.meshriderwave.core.util.logI
import com.doodlelabs.meshriderwave.core.util.logW
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages call registration with Android Telecom framework via Core-Telecom Jetpack library.
 *
 * Key responsibilities:
 * - Register app capabilities with CallsManager
 * - Add incoming/outgoing calls via addCall()
 * - Expose audio endpoint flows for UI (speaker/earpiece/bluetooth)
 * - Handle system callbacks (answer, disconnect, active, inactive)
 */
@Singleton
class TelecomCallManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val callsManager = CallsManager(context)

    // Current CallControlScope — null when no call is registered
    @Volatile
    private var callControlScope: CallControlScope? = null

    // Audio endpoint state exposed to UI
    private val _currentEndpoint = MutableStateFlow<CallEndpointCompat?>(null)
    val currentEndpoint: StateFlow<CallEndpointCompat?> = _currentEndpoint

    private val _availableEndpoints = MutableStateFlow<List<CallEndpointCompat>>(emptyList())
    val availableEndpoints: StateFlow<List<CallEndpointCompat>> = _availableEndpoints

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted

    // Track if we have an active telecom call
    private val _hasActiveCall = MutableStateFlow(false)
    val hasActiveCall: StateFlow<Boolean> = _hasActiveCall

    init {
        registerApp()
    }

    /**
     * Register app capabilities with Telecom framework.
     * Must be called before addCall().
     */
    private fun registerApp() {
        try {
            val capabilities = CallsManager.CAPABILITY_BASELINE or
                    CallsManager.CAPABILITY_SUPPORTS_VIDEO_CALLING
            callsManager.registerAppWithTelecom(capabilities)
            logI("TelecomCallManager: registered with BASELINE + VIDEO capabilities")
        } catch (e: Exception) {
            logE("TelecomCallManager: registerApp failed", e)
        }
    }

    /**
     * Add an incoming call to the Telecom framework.
     *
     * @param displayName Caller display name
     * @param address Remote address URI
     * @param isVideo Whether this is a video call
     * @param onAnswer Called when system requests answering (e.g., Bluetooth headset button)
     * @param onDisconnect Called when system requests disconnect
     * @param onSetActive Called when system requests call to become active
     * @param onSetInactive Called when system requests call to become inactive (hold)
     */
    suspend fun addIncomingCall(
        displayName: String,
        address: String,
        isVideo: Boolean = false,
        onAnswer: suspend (callType: Int) -> Unit,
        onDisconnect: suspend (cause: android.telecom.DisconnectCause) -> Unit,
        onSetActive: suspend () -> Unit,
        onSetInactive: suspend () -> Unit
    ) {
        addCall(
            displayName = displayName,
            address = address,
            isVideo = isVideo,
            isIncoming = true,
            onAnswer = onAnswer,
            onDisconnect = onDisconnect,
            onSetActive = onSetActive,
            onSetInactive = onSetInactive
        )
    }

    /**
     * Add an outgoing call to the Telecom framework.
     */
    suspend fun addOutgoingCall(
        displayName: String,
        address: String,
        isVideo: Boolean = false,
        onAnswer: suspend (callType: Int) -> Unit,
        onDisconnect: suspend (cause: android.telecom.DisconnectCause) -> Unit,
        onSetActive: suspend () -> Unit,
        onSetInactive: suspend () -> Unit
    ) {
        addCall(
            displayName = displayName,
            address = address,
            isVideo = isVideo,
            isIncoming = false,
            onAnswer = onAnswer,
            onDisconnect = onDisconnect,
            onSetActive = onSetActive,
            onSetInactive = onSetInactive
        )
    }

    private suspend fun addCall(
        displayName: String,
        address: String,
        isVideo: Boolean,
        isIncoming: Boolean,
        onAnswer: suspend (callType: Int) -> Unit,
        onDisconnect: suspend (cause: android.telecom.DisconnectCause) -> Unit,
        onSetActive: suspend () -> Unit,
        onSetInactive: suspend () -> Unit
    ) {
        try {
            val callAttributes = CallAttributesCompat(
                displayName = displayName,
                address = Uri.parse("mesh://$address"),
                direction = if (isIncoming) {
                    CallAttributesCompat.DIRECTION_INCOMING
                } else {
                    CallAttributesCompat.DIRECTION_OUTGOING
                },
                callType = if (isVideo) {
                    CallAttributesCompat.CALL_TYPE_VIDEO_CALL
                } else {
                    CallAttributesCompat.CALL_TYPE_AUDIO_CALL
                },
                callCapabilities = CallAttributesCompat.SUPPORTS_SET_INACTIVE or
                        CallAttributesCompat.SUPPORTS_STREAM
            )

            logI("TelecomCallManager: addCall displayName=$displayName incoming=$isIncoming video=$isVideo")

            callsManager.addCall(
                callAttributes,
                onAnswer,
                onDisconnect,
                onSetActive,
                onSetInactive
            ) {
                // This block runs inside CallControlScope
                callControlScope = this
                _hasActiveCall.value = true

                logI("TelecomCallManager: CallControlScope active, monitoring audio endpoints")

                // Monitor audio endpoints
                launch {
                    currentCallEndpoint.collect { endpoint ->
                        _currentEndpoint.value = endpoint
                        logD("TelecomCallManager: current endpoint = ${endpoint.name}")
                    }
                }
                launch {
                    availableEndpoints.collect { endpoints ->
                        _availableEndpoints.value = endpoints
                        logD("TelecomCallManager: available endpoints = ${endpoints.map { it.name }}")
                    }
                }
                launch {
                    isMuted.collect { muted ->
                        _isMuted.value = muted
                        logD("TelecomCallManager: muted = $muted")
                    }
                }
            }
        } catch (e: Exception) {
            logE("TelecomCallManager: addCall failed", e)
        } finally {
            // Call ended (scope completed)
            callControlScope = null
            _hasActiveCall.value = false
            _currentEndpoint.value = null
            _availableEndpoints.value = emptyList()
            logI("TelecomCallManager: call ended, scope cleared")
        }
    }

    /**
     * Request audio endpoint change (speaker/earpiece/bluetooth).
     * Uses the Telecom framework's proper audio routing instead of AudioManager.
     */
    fun requestEndpointChange(endpoint: CallEndpointCompat) {
        val scope = callControlScope
        if (scope == null) {
            logW("TelecomCallManager: requestEndpointChange but no active call scope")
            return
        }
        this.scope.launch {
            try {
                scope.requestEndpointChange(endpoint)
                logI("TelecomCallManager: endpoint changed to ${endpoint.name}")
            } catch (e: Exception) {
                logE("TelecomCallManager: requestEndpointChange failed", e)
            }
        }
    }

    /**
     * Find endpoint by type from available endpoints.
     */
    fun findEndpointByType(type: Int): CallEndpointCompat? {
        return _availableEndpoints.value.find { it.type == type }
    }

    /**
     * Switch to speaker.
     */
    fun switchToSpeaker() {
        findEndpointByType(CallEndpointCompat.TYPE_SPEAKER)?.let {
            requestEndpointChange(it)
        } ?: logW("TelecomCallManager: speaker endpoint not available")
    }

    /**
     * Switch to earpiece.
     */
    fun switchToEarpiece() {
        findEndpointByType(CallEndpointCompat.TYPE_EARPIECE)?.let {
            requestEndpointChange(it)
        } ?: logW("TelecomCallManager: earpiece endpoint not available")
    }

    /**
     * Disconnect the call via Telecom framework.
     */
    fun disconnect(cause: android.telecom.DisconnectCause = android.telecom.DisconnectCause(android.telecom.DisconnectCause.LOCAL)) {
        val scope = callControlScope
        if (scope == null) {
            logW("TelecomCallManager: disconnect but no active call scope")
            return
        }
        this.scope.launch {
            try {
                scope.disconnect(cause)
                logI("TelecomCallManager: disconnected with cause=${cause.code}")
            } catch (e: Exception) {
                logE("TelecomCallManager: disconnect failed", e)
            }
        }
    }

    /**
     * Set call active (e.g., after answering).
     */
    fun setActive() {
        val scope = callControlScope
        if (scope == null) {
            logW("TelecomCallManager: setActive but no active call scope")
            return
        }
        this.scope.launch {
            try {
                scope.setActive()
                logI("TelecomCallManager: setActive")
            } catch (e: Exception) {
                logE("TelecomCallManager: setActive failed", e)
            }
        }
    }

    /**
     * Set call inactive (hold).
     */
    fun setInactive() {
        val scope = callControlScope
        if (scope == null) {
            logW("TelecomCallManager: setInactive but no active call scope")
            return
        }
        this.scope.launch {
            try {
                scope.setInactive()
                logI("TelecomCallManager: setInactive")
            } catch (e: Exception) {
                logE("TelecomCallManager: setInactive failed", e)
            }
        }
    }
}
