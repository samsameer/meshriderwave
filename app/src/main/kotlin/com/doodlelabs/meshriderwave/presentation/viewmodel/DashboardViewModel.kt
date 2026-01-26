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
        // Military-grade Jan 2026: Use both discovery sources + build nearby peers list
        viewModelScope.launch {
            combine(
                peerDiscoveryManager.discoveredPeers,
                beaconManager.discoveredPeersFlow
            ) { mdnsPeers, beaconPeers ->
                // Merge peers by public key (deduplicate)
                // Prefer beacon data as it has more info (signed, verified)
                val mergedPeers = mutableMapOf<String, NearbyPeerUiState>()

                // Add beacon peers first (higher priority)
                beaconPeers.values.forEach { peer ->
                    mergedPeers[peer.publicKeyHex] = NearbyPeerUiState(
                        publicKeyHex = peer.publicKeyHex,
                        publicKey = peer.publicKey,
                        name = peer.name,
                        ipAddress = peer.address,
                        shortId = peer.shortId,
                        networkType = peer.networkType.name,
                        isSavedContact = false,  // TODO: Check against contacts
                        lastSeenMs = peer.lastSeenAt
                    )
                }

                // Add mDNS peers (if not already from beacon)
                mdnsPeers.values.forEach { peer ->
                    if (!mergedPeers.containsKey(peer.publicKeyHex)) {
                        mergedPeers[peer.publicKeyHex] = NearbyPeerUiState(
                            publicKeyHex = peer.publicKeyHex,
                            publicKey = peer.publicKey,
                            name = peer.name,
                            ipAddress = peer.addresses.firstOrNull() ?: "",
                            shortId = peer.shortId,
                            networkType = peer.networkType.name,
                            isSavedContact = false,
                            lastSeenMs = peer.lastSeenAt
                        )
                    }
                }

                // Sort by last seen (most recent first)
                mergedPeers.values.sortedByDescending { it.lastSeenMs }
            }.collect { nearbyPeersList ->
                _uiState.update { state ->
                    state.copy(
                        discoveredPeers = nearbyPeersList.size,
                        nearbyPeers = nearbyPeersList,
                        meshNodes = nearbyPeersList.size,
                        signalStrength = calculateSignalStrength(nearbyPeersList.size)
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

        // Observe team member locations - ATAK-style Blue Force Tracking
        // Combined with peer discovery for IP addresses (Jan 2026)
        viewModelScope.launch {
            combine(
                locationSharingManager.teamLocations,
                peerDiscoveryManager.discoveredPeers,
                beaconManager.discoveredPeersFlow
            ) { locations, mdnsPeers, beaconPeers ->
                Log.d(TAG, "Team locations update: ${locations.size} members, ${mdnsPeers.size} mDNS, ${beaconPeers.size} beacon")
                val myLat = _uiState.value.myLatitude
                val myLon = _uiState.value.myLongitude
                val now = System.currentTimeMillis()

                // Build a map of public key hex -> IP address from both discovery sources
                val peerIpMap = mutableMapOf<String, String>()
                beaconPeers.values.forEach { peer ->
                    peerIpMap[peer.publicKeyHex] = peer.address
                }
                mdnsPeers.values.forEach { peer ->
                    if (!peerIpMap.containsKey(peer.publicKeyHex)) {
                        peer.addresses.firstOrNull()?.let { ip ->
                            peerIpMap[peer.publicKeyHex] = ip
                        }
                    }
                }

                locations.map { (hexKey, location) ->
                    val displayName = location.memberName ?: "Team ${hexKey.take(4).uppercase()}"

                    // Calculate distance and bearing from my position
                    val distanceMeters = if (myLat != 0.0 && myLon != 0.0) {
                        calculateDistance(myLat, myLon, location.latitude, location.longitude)
                    } else 0.0

                    val bearingDeg = if (myLat != 0.0 && myLon != 0.0) {
                        calculateBearing(myLat, myLon, location.latitude, location.longitude)
                    } else 0f

                    // Determine member status (ATAK-style)
                    val ageMs = now - location.timestamp
                    val status = when {
                        sosManager.isMemberInSOS(hexKey) -> MemberStatus.SOS
                        ageMs > 5 * 60 * 1000 -> MemberStatus.OFFLINE  // 5 min stale
                        ageMs > 2 * 60 * 1000 -> MemberStatus.STALE    // 2 min stale
                        location.speed > 2.0f -> MemberStatus.MOVING   // > 2 m/s
                        else -> MemberStatus.ACTIVE
                    }

                    // Get IP address from peer discovery (enables direct calling)
                    val ipAddress = peerIpMap[hexKey]

                    Log.d(TAG, "  Member $hexKey: $displayName status=$status dist=${distanceMeters.toInt()}m bearing=${bearingDeg.toInt()}° ip=$ipAddress")

                    TrackedMemberUiState(
                        id = hexKey,
                        name = displayName,
                        status = status,
                        latitude = location.latitude,
                        longitude = location.longitude,
                        distanceMeters = distanceMeters,
                        bearingDegrees = bearingDeg,
                        speedMps = location.speed.toDouble(),
                        lastSeenMs = location.timestamp,
                        hasSOS = sosManager.isMemberInSOS(hexKey),
                        ipAddress = ipAddress,  // Jan 2026: Now wired from discovery
                        publicKey = location.memberPublicKey
                    )
                }.sortedBy { it.status.priority }  // SOS first, then MOVING, ACTIVE, STALE, OFFLINE
            }.collect { trackedMembers ->
                _uiState.update { it.copy(trackedTeamMembers = trackedMembers) }
            }
        }
    }

    // Haversine formula for distance calculation
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371000.0 // Earth radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
                kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
                kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
        val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
        return R * c
    }

    // Bearing calculation
    private fun calculateBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val dLon = Math.toRadians(lon2 - lon1)
        val y = kotlin.math.sin(dLon) * kotlin.math.cos(Math.toRadians(lat2))
        val x = kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.sin(Math.toRadians(lat2)) -
                kotlin.math.sin(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) * kotlin.math.cos(dLon)
        val bearing = Math.toDegrees(kotlin.math.atan2(y, x))
        return ((bearing + 360) % 360).toFloat()
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
    val nearbyPeers: List<NearbyPeerUiState> = emptyList(),  // Jan 2026: Direct calling
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

/**
 * Nearby Peer UI State - Military Grade Direct Calling
 * Jan 2026: Enables one-tap calling to discovered peers without QR exchange
 */
data class NearbyPeerUiState(
    val publicKeyHex: String,      // For encryption & identification
    val publicKey: ByteArray,      // Raw key for calling
    val name: String,              // Display name (callsign)
    val ipAddress: String,         // For connection
    val shortId: String,           // 8-char identifier
    val networkType: String,       // MESHRIDER, WIFI, etc.
    val isSavedContact: Boolean,   // Already in contacts?
    val lastSeenMs: Long           // For freshness indicator
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is NearbyPeerUiState) return false
        return publicKeyHex == other.publicKeyHex
    }

    override fun hashCode(): Int = publicKeyHex.hashCode()
}
