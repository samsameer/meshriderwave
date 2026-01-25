/*
 * Mesh Rider Wave - Dashboard ViewModel
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 *
 * ViewModel for tactical dashboard with network status,
 * groups, channels, location tracking, and SOS
 */

package com.doodlelabs.meshriderwave.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.doodlelabs.meshriderwave.core.discovery.BeaconManager
import com.doodlelabs.meshriderwave.core.emergency.SOSManager
import com.doodlelabs.meshriderwave.core.emergency.SOSState
import com.doodlelabs.meshriderwave.core.location.LocationSharingManager
import com.doodlelabs.meshriderwave.core.network.MeshNetworkManager
import com.doodlelabs.meshriderwave.core.network.NetworkTypeDetector
import com.doodlelabs.meshriderwave.core.network.PeerDiscoveryManager
import com.doodlelabs.meshriderwave.core.radio.RadioApiClient
import com.doodlelabs.meshriderwave.core.radio.RadioDiscoveryService
import com.doodlelabs.meshriderwave.data.local.database.SOSType
import com.doodlelabs.meshriderwave.domain.repository.GroupRepository
import com.doodlelabs.meshriderwave.domain.repository.PTTChannelRepository
import com.doodlelabs.meshriderwave.domain.repository.SettingsRepository
import com.doodlelabs.meshriderwave.presentation.ui.components.SignalStrength
import com.doodlelabs.meshriderwave.presentation.ui.screens.dashboard.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val groupRepository: GroupRepository,
    private val pttChannelRepository: PTTChannelRepository,
    private val meshNetworkManager: MeshNetworkManager,
    private val peerDiscoveryManager: PeerDiscoveryManager,
    private val beaconManager: BeaconManager,
    private val networkTypeDetector: NetworkTypeDetector,
    private val locationSharingManager: LocationSharingManager,
    private val sosManager: SOSManager,
    private val radioApiClient: RadioApiClient,
    private val radioDiscoveryService: RadioDiscoveryService
) : ViewModel() {

    companion object {
        private const val TAG = "MeshRider:DashboardVM"
        // BATTERY OPTIMIZATION Jan 2026: Adaptive polling intervals
        // Foreground: 10s, Background: 60s, Screen off: 5min
        private const val RADIO_STATUS_INTERVAL_ACTIVE = 10_000L      // 10s when active
        private const val RADIO_STATUS_INTERVAL_BACKGROUND = 60_000L  // 60s when backgrounded
        private const val RADIO_STATUS_INTERVAL_DOZE = 300_000L       // 5min in doze
    }

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        observeSettings()
        observePeers()
        observeGroups()
        observeChannels()
        observeLocation()
        observeSOS()
        observeRadioStatus()
        startRadioDiscovery()
    }

    private fun observeSettings() {
        viewModelScope.launch {
            settingsRepository.username.collect { username ->
                _uiState.update { it.copy(username = username) }
            }
        }
    }

    private fun observePeers() {
        // Observe service running state
        viewModelScope.launch {
            meshNetworkManager.isRunning.collect { isRunning ->
                _uiState.update { it.copy(isServiceRunning = isRunning) }
            }
        }

        // Observe current network type
        viewModelScope.launch {
            networkTypeDetector.currentNetworkType.collect { networkType ->
                _uiState.update { it.copy(currentNetworkType = networkType.displayName) }
            }
        }

        // Combine mDNS and beacon discoveries for accurate peer count
        // Military-grade Jan 2026: Use both discovery sources
        viewModelScope.launch {
            combine(
                peerDiscoveryManager.discoveredPeers,
                beaconManager.discoveredPeersFlow
            ) { mdnsPeers, beaconPeers ->
                // Merge peers by public key (deduplicate)
                val allPeerKeys = mutableSetOf<String>()

                // Add mDNS peers
                mdnsPeers.keys.forEach { allPeerKeys.add(it) }

                // Add beacon peers
                beaconPeers.keys.forEach { allPeerKeys.add(it) }

                allPeerKeys.size
            }.collect { totalPeers ->
                _uiState.update { state ->
                    state.copy(
                        discoveredPeers = totalPeers,
                        meshNodes = totalPeers,
                        signalStrength = calculateSignalStrength(totalPeers)
                    )
                }
            }
        }

        // Log individual discovery sources for debugging
        viewModelScope.launch {
            peerDiscoveryManager.discoveredPeers.collect { mdnsPeers ->
                Log.d(TAG, "mDNS peers: ${mdnsPeers.size}")
            }
        }

        viewModelScope.launch {
            beaconManager.discoveredPeersFlow.collect { beaconPeers ->
                Log.d(TAG, "Beacon peers: ${beaconPeers.size}")
            }
        }
    }

    private fun observeGroups() {
        viewModelScope.launch {
            groupRepository.groups.collect { groups ->
                val groupUiStates = groups.map { group ->
                    GroupUiState(
                        id = group.groupId.toHexString(),
                        name = group.name,
                        memberCount = group.members.size,
                        hasActiveCall = false,
                        unreadCount = 0
                    )
                }
                _uiState.update { it.copy(activeGroups = groupUiStates) }
            }
        }
    }

    private fun observeChannels() {
        viewModelScope.launch {
            pttChannelRepository.joinedChannels.collect { channels ->
                val channelUiStates = channels.map { channel ->
                    PTTChannelUiState(
                        id = channel.shortId,
                        name = channel.name,
                        memberCount = channel.members.size,
                        isTransmitting = false,
                        priority = channel.priority.name
                    )
                }
                _uiState.update { it.copy(activeChannels = channelUiStates) }
            }
        }
    }

    private fun observeLocation() {
        // Observe my own location for radar center
        viewModelScope.launch {
            locationSharingManager.myLocation.collect { myLocation ->
                myLocation?.let { loc ->
                    _uiState.update {
                        it.copy(
                            myLatitude = loc.latitude,
                            myLongitude = loc.longitude
                        )
                    }
                }
            }
        }

        // Observe team member locations
        viewModelScope.launch {
            locationSharingManager.teamLocations.collect { locations ->
                val trackedMembers = locations.map { (hexKey, location) ->
                    TrackedMemberUiState(
                        id = hexKey,
                        name = location.memberName ?: "Team ${hexKey.take(4)}",
                        status = when {
                            location.speed > 2.0 -> "MOVING"
                            else -> "ACTIVE"
                        },
                        latitude = location.latitude,
                        longitude = location.longitude
                    )
                }
                _uiState.update { it.copy(trackedTeamMembers = trackedMembers) }
            }
        }
    }

    private fun observeSOS() {
        viewModelScope.launch {
            sosManager.sosState.collect { state ->
                val isActive = state is SOSState.Active
                _uiState.update { it.copy(hasActiveSOS = isActive) }

                if (isActive) {
                    addActivity(ActivityUiState(
                        type = "SOS",
                        description = "Emergency alert activated",
                        timestamp = formatTimestamp(System.currentTimeMillis())
                    ))
                }
            }
        }
    }

    fun activateSOS() {
        viewModelScope.launch {
            try {
                val keyPair = settingsRepository.getOrCreateKeyPair()
                sosManager.activateSOS(
                    myPublicKey = keyPair.publicKey,
                    myName = _uiState.value.username,
                    type = SOSType.GENERAL,
                    message = "Emergency assistance needed"
                )
                Log.i(TAG, "SOS activated from dashboard")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to activate SOS", e)
            }
        }
    }

    fun deactivateSOS() {
        viewModelScope.launch {
            val keyPair = settingsRepository.getOrCreateKeyPair()
            sosManager.cancelSOS(keyPair.publicKey)
        }
    }

    private fun addActivity(activity: ActivityUiState) {
        _uiState.update { state ->
            val currentActivities = state.recentActivity.toMutableList()
            currentActivities.add(0, activity)
            state.copy(recentActivity = currentActivities.take(20))
        }
    }

    private fun calculateSignalStrength(peerCount: Int): SignalStrength {
        return when {
            peerCount >= 10 -> SignalStrength.EXCELLENT
            peerCount >= 5 -> SignalStrength.GOOD
            peerCount >= 2 -> SignalStrength.FAIR
            peerCount >= 1 -> SignalStrength.POOR
            else -> SignalStrength.NONE
        }
    }

    private fun formatTimestamp(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        return when {
            diff < 60_000 -> "Just now"
            diff < 3600_000 -> "${diff / 60_000}m ago"
            diff < 86400_000 -> "${diff / 3600_000}h ago"
            else -> "${diff / 86400_000}d ago"
        }
    }

    private fun ByteArray.toHexString(): String =
        joinToString("") { "%02x".format(it) }

    // ============================================================================
    // RADIO STATUS INTEGRATION
    // ============================================================================

    private fun startRadioDiscovery() {
        radioDiscoveryService.start(autoRefresh = true, refreshInterval = 30000L)
    }

    private fun observeRadioStatus() {
        // Observe discovered radios
        viewModelScope.launch {
            radioDiscoveryService.discoveredRadios.collect { radios ->
                val radioList = radios.map { radio ->
                    DiscoveredRadioUiState(
                        ipAddress = radio.ipAddress,
                        hostname = radio.displayName,
                        model = radio.model,
                        isConnected = radio.ipAddress == _uiState.value.connectedRadioIp
                    )
                }
                _uiState.update { it.copy(discoveredRadios = radioList) }

                // Auto-connect to first discovered radio if not connected
                if (_uiState.value.connectedRadioIp == null && radios.isNotEmpty()) {
                    connectToRadio(radios.first().ipAddress)
                }
            }
        }

        // Observe connection state
        viewModelScope.launch {
            radioApiClient.connectionState.collect { state ->
                when (state) {
                    is RadioApiClient.ConnectionState.Connected -> {
                        _uiState.update {
                            it.copy(
                                isRadioConnected = true,
                                connectedRadioIp = state.radioIp,
                                radioHostname = state.radioInfo?.hostname ?: "",
                                radioModel = state.radioInfo?.model ?: ""
                            )
                        }
                        // Start polling wireless status
                        startRadioStatusPolling()
                    }
                    is RadioApiClient.ConnectionState.Disconnected -> {
                        _uiState.update {
                            it.copy(
                                isRadioConnected = false,
                                connectedRadioIp = null,
                                radioSignal = -100,
                                radioSnr = 0,
                                meshPeerCount = 0
                            )
                        }
                    }
                    is RadioApiClient.ConnectionState.Error -> {
                        Log.e(TAG, "Radio connection error: ${state.message}")
                        _uiState.update {
                            it.copy(
                                isRadioConnected = false,
                                radioError = state.message
                            )
                        }
                    }
                    is RadioApiClient.ConnectionState.Connecting -> {
                        _uiState.update { it.copy(isRadioConnecting = true) }
                    }
                }
            }
        }
    }

    // MEMORY LEAK FIX Jan 2026: Track polling job for proper cancellation
    private var radioPollingJob: kotlinx.coroutines.Job? = null

    // BATTERY OPTIMIZATION Jan 2026: Track app lifecycle state
    @Volatile
    private var isAppInForeground = true

    /**
     * Call from Activity/Fragment onResume/onPause to adjust polling rate
     */
    fun onLifecycleStateChanged(isInForeground: Boolean) {
        isAppInForeground = isInForeground
        Log.d(TAG, "Lifecycle state changed: foreground=$isInForeground")
    }

    private fun startRadioStatusPolling() {
        // Cancel any existing polling job first
        radioPollingJob?.cancel()

        radioPollingJob = viewModelScope.launch {
            // MEMORY LEAK FIX Jan 2026: Check job cancellation to stop loop when ViewModel is cleared
            try {
                while (radioApiClient.isConnected()) {
                    val status = radioApiClient.getWirelessStatus()
                    val stations = radioApiClient.getAssociatedStations()

                    if (status != null) {
                        _uiState.update {
                            it.copy(
                                radioSsid = status.ssid,
                                radioChannel = status.channel,
                                radioSignal = status.signal,
                                radioNoise = status.noise,
                                radioSnr = status.snr,
                                radioLinkQuality = status.linkQuality,
                                radioBitrate = status.bitrate,
                                meshPeerCount = stations.size,
                                meshPeers = stations.map { sta ->
                                    MeshPeerUiState(
                                        macAddress = sta.macAddress,
                                        ipAddress = sta.ipAddress ?: "",
                                        signal = sta.signal,
                                        snr = sta.snr,
                                        isActive = sta.inactive < 10000
                                    )
                                },
                                radioError = null,
                                isRadioConnecting = false
                            )
                        }
                    }

                    // BATTERY OPTIMIZATION Jan 2026: Adaptive polling interval
                    // Use longer intervals when app is backgrounded to save battery
                    val pollingInterval = if (isAppInForeground) {
                        RADIO_STATUS_INTERVAL_ACTIVE
                    } else {
                        RADIO_STATUS_INTERVAL_BACKGROUND
                    }
                    kotlinx.coroutines.delay(pollingInterval)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Normal cancellation when ViewModel is cleared - just exit
                Log.d(TAG, "Radio polling cancelled")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to poll radio status", e)
            }
        }
    }

    fun connectToRadio(ipAddress: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isRadioConnecting = true, radioError = null) }
            try {
                val success = radioApiClient.connect(ipAddress)
                if (!success) {
                    _uiState.update {
                        it.copy(
                            isRadioConnecting = false,
                            radioError = "Failed to connect to radio"
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to connect to radio", e)
                _uiState.update {
                    it.copy(
                        isRadioConnecting = false,
                        radioError = e.message
                    )
                }
            }
        }
    }

    fun disconnectFromRadio() {
        radioApiClient.disconnect()
    }

    fun refreshRadioDiscovery() {
        viewModelScope.launch {
            radioDiscoveryService.refresh()
        }
    }

    override fun onCleared() {
        super.onCleared()
        // MEMORY LEAK FIX Jan 2026: Cancel polling job explicitly
        radioPollingJob?.cancel()
        radioPollingJob = null
        radioDiscoveryService.stop()
        radioApiClient.release()
    }
}

data class DashboardUiState(
    val username: String = "Operator",
    val isServiceRunning: Boolean = false,
    val discoveredPeers: Int = 0,
    val meshNodes: Int = 0,
    val signalStrength: SignalStrength = SignalStrength.NONE,
    val networkLatency: Long = 0,
    val currentNetworkType: String = "Unknown",
    val activeChannels: List<PTTChannelUiState> = emptyList(),
    val activeGroups: List<GroupUiState> = emptyList(),
    val trackedTeamMembers: List<TrackedMemberUiState> = emptyList(),
    val recentActivity: List<ActivityUiState> = emptyList(),
    val hasActiveSOS: Boolean = false,

    // My GPS location for radar
    val myLatitude: Double = 0.0,
    val myLongitude: Double = 0.0,

    // Radio Status
    val isRadioConnected: Boolean = false,
    val isRadioConnecting: Boolean = false,
    val connectedRadioIp: String? = null,
    val radioHostname: String = "",
    val radioModel: String = "",
    val radioSsid: String = "",
    val radioChannel: Int = 0,
    val radioSignal: Int = -100,
    val radioNoise: Int = -95,
    val radioSnr: Int = 0,
    val radioLinkQuality: Int = 0,
    val radioBitrate: Int = 0,
    val meshPeerCount: Int = 0,
    val meshPeers: List<MeshPeerUiState> = emptyList(),
    val discoveredRadios: List<DiscoveredRadioUiState> = emptyList(),
    val radioError: String? = null
) {
    val radioSignalLabel: String
        get() = when {
            radioSignal >= -50 -> "Excellent"
            radioSignal >= -60 -> "Good"
            radioSignal >= -70 -> "Fair"
            radioSignal >= -80 -> "Poor"
            else -> "Weak"
        }
}

data class DiscoveredRadioUiState(
    val ipAddress: String,
    val hostname: String,
    val model: String,
    val isConnected: Boolean
)

data class MeshPeerUiState(
    val macAddress: String,
    val ipAddress: String,
    val signal: Int,
    val snr: Int,
    val isActive: Boolean
)
