/*
 * Mesh Rider Wave - Radio Status ViewModel
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 *
 * ViewModel for radio status display on the Dashboard.
 *
 * Features:
 * - Radio discovery and connection status
 * - Wireless link quality monitoring
 * - Associated stations (mesh peers) list
 * - Auto-refresh with circuit breaker for error handling
 */

package com.doodlelabs.meshriderwave.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.doodlelabs.meshriderwave.core.radio.RadioApiClient
import com.doodlelabs.meshriderwave.core.radio.RadioDiscoveryService
import com.doodlelabs.meshriderwave.core.util.logD
import com.doodlelabs.meshriderwave.core.util.logE
import com.doodlelabs.meshriderwave.core.util.logI
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

/**
 * ViewModel for radio status monitoring and display.
 */
@HiltViewModel
class RadioStatusViewModel @Inject constructor(
    private val radioApiClient: RadioApiClient,
    private val radioDiscovery: RadioDiscoveryService
) : ViewModel() {

    companion object {
        // Refresh intervals
        const val STATUS_REFRESH_INTERVAL = 5000L  // 5 seconds
        const val DISCOVERY_REFRESH_INTERVAL = 30000L  // 30 seconds

        // Circuit breaker
        const val MAX_CONSECUTIVE_ERRORS = 3
        const val CIRCUIT_BREAKER_RESET_TIME = 30000L  // 30 seconds
    }

    // UI State
    private val _uiState = MutableStateFlow(RadioStatusUiState())
    val uiState: StateFlow<RadioStatusUiState> = _uiState.asStateFlow()

    // Refresh jobs
    private var statusRefreshJob: Job? = null
    private var discoveryRefreshJob: Job? = null

    // Circuit breaker state
    private var consecutiveErrors = 0
    private var circuitBreakerOpen = false
    private var circuitBreakerOpenTime = 0L

    /**
     * UI state for radio status display
     */
    data class RadioStatusUiState(
        // Connection
        val isConnected: Boolean = false,
        val connectedRadioIp: String? = null,
        val connectionError: String? = null,

        // Radio info
        val radioHostname: String = "",
        val radioModel: String = "",
        val firmwareVersion: String = "",

        // Wireless status
        val ssid: String = "",
        val channel: Int = 0,
        val frequency: Int = 0,
        val bandwidth: Int = 0,
        val signal: Int = -100,
        val noise: Int = -95,
        val snr: Int = 0,
        val linkQuality: Int = 0,
        val txPower: Int = 0,
        val bitrate: Int = 0,
        val mode: String = "",

        // Mesh peers
        val associatedStations: List<StationInfo> = emptyList(),

        // Discovered radios
        val discoveredRadios: List<DiscoveredRadioInfo> = emptyList(),

        // Status
        val isRefreshing: Boolean = false,
        val lastRefreshTime: Long = 0,
        val error: String? = null
    ) {
        val signalPercentage: Int
            get() = ((signal + 100).coerceIn(0, 100))

        val linkQualityLabel: String
            get() = when {
                linkQuality >= 80 -> "Excellent"
                linkQuality >= 60 -> "Good"
                linkQuality >= 40 -> "Fair"
                linkQuality >= 20 -> "Poor"
                else -> "Very Poor"
            }

        val peerCount: Int
            get() = associatedStations.size
    }

    /**
     * Associated station (mesh peer) info
     */
    data class StationInfo(
        val macAddress: String,
        val ipAddress: String?,
        val signal: Int,
        val snr: Int,
        val txBitrate: Int,
        val rxBitrate: Int,
        val inactive: Int
    ) {
        val isActive: Boolean
            get() = inactive < 10000  // Less than 10 seconds inactive

        val signalLabel: String
            get() = when {
                signal >= -50 -> "Excellent"
                signal >= -60 -> "Good"
                signal >= -70 -> "Fair"
                signal >= -80 -> "Poor"
                else -> "Weak"
            }
    }

    /**
     * Discovered radio info for UI
     */
    data class DiscoveredRadioInfo(
        val ipAddress: String,
        val hostname: String,
        val model: String,
        val isConnected: Boolean
    )

    init {
        // Start discovery service
        radioDiscovery.start(autoRefresh = true, refreshInterval = DISCOVERY_REFRESH_INTERVAL)

        // Observe discovered radios
        viewModelScope.launch {
            radioDiscovery.discoveredRadios.collect { radios ->
                val radioInfoList = radios.map { radio ->
                    DiscoveredRadioInfo(
                        ipAddress = radio.ipAddress,
                        hostname = radio.displayName,
                        model = radio.model,
                        isConnected = radio.ipAddress == _uiState.value.connectedRadioIp
                    )
                }
                _uiState.update { it.copy(discoveredRadios = radioInfoList) }
            }
        }

        // Observe connection state
        viewModelScope.launch {
            radioApiClient.connectionState.collect { state ->
                when (state) {
                    is RadioApiClient.ConnectionState.Connected -> {
                        _uiState.update {
                            it.copy(
                                isConnected = true,
                                connectedRadioIp = state.radioIp,
                                radioHostname = state.radioInfo?.hostname ?: "",
                                radioModel = state.radioInfo?.model ?: "",
                                firmwareVersion = state.radioInfo?.firmwareVersion ?: "",
                                connectionError = null
                            )
                        }
                        startStatusRefresh()
                    }

                    is RadioApiClient.ConnectionState.Disconnected -> {
                        _uiState.update {
                            it.copy(
                                isConnected = false,
                                connectedRadioIp = null,
                                connectionError = null
                            )
                        }
                        stopStatusRefresh()
                    }

                    is RadioApiClient.ConnectionState.Error -> {
                        _uiState.update {
                            it.copy(
                                isConnected = false,
                                connectionError = state.message
                            )
                        }
                        stopStatusRefresh()
                    }

                    is RadioApiClient.ConnectionState.Connecting -> {
                        _uiState.update { it.copy(isRefreshing = true) }
                    }
                }
            }
        }
    }

    /**
     * Connect to a radio.
     *
     * @param ipAddress Radio IP address
     * @param username Login username (default: root)
     * @param password Login password (default: doodle)
     */
    fun connectToRadio(
        ipAddress: String,
        username: String = RadioApiClient.DEFAULT_USERNAME,
        password: String = RadioApiClient.DEFAULT_PASSWORD
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, error = null) }

            try {
                val success = radioApiClient.connect(ipAddress, username, password)
                if (!success) {
                    _uiState.update {
                        it.copy(
                            isRefreshing = false,
                            error = "Failed to connect to radio"
                        )
                    }
                }
            } catch (e: Exception) {
                logE("Failed to connect to radio: ${e.message}")
                _uiState.update {
                    it.copy(
                        isRefreshing = false,
                        error = e.message
                    )
                }
            }
        }
    }

    /**
     * Disconnect from current radio.
     */
    fun disconnectFromRadio() {
        radioApiClient.disconnect()
        _uiState.update {
            it.copy(
                isConnected = false,
                connectedRadioIp = null,
                ssid = "",
                channel = 0,
                signal = -100,
                noise = -95,
                associatedStations = emptyList()
            )
        }
    }

    /**
     * Manually add a radio for connection.
     */
    fun addManualRadio(ipAddress: String, hostname: String = "") {
        radioDiscovery.addManualRadio(ipAddress, hostname)
    }

    /**
     * Trigger manual refresh of radio status.
     */
    fun refreshStatus() {
        viewModelScope.launch {
            refreshRadioStatus()
        }
    }

    /**
     * Trigger manual discovery scan.
     */
    fun refreshDiscovery() {
        viewModelScope.launch {
            radioDiscovery.refresh()
        }
    }

    /**
     * Start automatic status refresh.
     */
    private fun startStatusRefresh() {
        statusRefreshJob?.cancel()
        statusRefreshJob = viewModelScope.launch {
            while (isActive) {
                refreshRadioStatus()
                delay(STATUS_REFRESH_INTERVAL)
            }
        }
        logD("Status refresh started")
    }

    /**
     * Stop automatic status refresh.
     */
    private fun stopStatusRefresh() {
        statusRefreshJob?.cancel()
        statusRefreshJob = null
        logD("Status refresh stopped")
    }

    /**
     * Refresh radio status (with circuit breaker).
     */
    private suspend fun refreshRadioStatus() {
        if (!radioApiClient.isConnected()) return

        // Check circuit breaker
        if (circuitBreakerOpen) {
            if (System.currentTimeMillis() - circuitBreakerOpenTime > CIRCUIT_BREAKER_RESET_TIME) {
                circuitBreakerOpen = false
                consecutiveErrors = 0
                logI("Circuit breaker reset")
            } else {
                return
            }
        }

        _uiState.update { it.copy(isRefreshing = true) }

        try {
            // Get wireless status
            val status = radioApiClient.getWirelessStatus()

            // Get associated stations
            val stations = radioApiClient.getAssociatedStations()

            if (status != null) {
                _uiState.update {
                    it.copy(
                        ssid = status.ssid,
                        channel = status.channel,
                        frequency = status.frequency,
                        bandwidth = status.bandwidth,
                        signal = status.signal,
                        noise = status.noise,
                        snr = status.snr,
                        linkQuality = status.linkQuality,
                        txPower = status.txPower,
                        bitrate = status.bitrate,
                        mode = status.mode,
                        associatedStations = stations.map { sta ->
                            StationInfo(
                                macAddress = sta.macAddress,
                                ipAddress = sta.ipAddress,
                                signal = sta.signal,
                                snr = sta.snr,
                                txBitrate = sta.txBitrate,
                                rxBitrate = sta.rxBitrate,
                                inactive = sta.inactive
                            )
                        },
                        isRefreshing = false,
                        lastRefreshTime = System.currentTimeMillis(),
                        error = null
                    )
                }

                consecutiveErrors = 0
            } else {
                handleRefreshError("No response from radio")
            }
        } catch (e: Exception) {
            handleRefreshError(e.message ?: "Unknown error")
        }
    }

    /**
     * Handle refresh error with circuit breaker logic.
     */
    private fun handleRefreshError(error: String) {
        consecutiveErrors++
        logE("Status refresh error ($consecutiveErrors/$MAX_CONSECUTIVE_ERRORS): $error")

        _uiState.update {
            it.copy(
                isRefreshing = false,
                error = error
            )
        }

        if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
            circuitBreakerOpen = true
            circuitBreakerOpenTime = System.currentTimeMillis()
            logE("Circuit breaker opened due to consecutive errors")
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopStatusRefresh()
        radioDiscovery.stop()
        radioApiClient.release()
    }
}
