/*
 * Mesh Rider Wave - Map ViewModel
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 *
 * ViewModel for Blue Force Tracking map view
 */

package com.doodlelabs.meshriderwave.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.doodlelabs.meshriderwave.core.emergency.SOSManager
import com.doodlelabs.meshriderwave.core.emergency.SOSState
import com.doodlelabs.meshriderwave.core.location.LocationSharingManager
import com.doodlelabs.meshriderwave.core.location.SharingState
import com.doodlelabs.meshriderwave.data.local.database.SOSType
import com.doodlelabs.meshriderwave.domain.repository.SettingsRepository
import com.doodlelabs.meshriderwave.presentation.ui.screens.map.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MapViewModel @Inject constructor(
    private val locationSharingManager: LocationSharingManager,
    private val sosManager: SOSManager,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    companion object {
        private const val TAG = "MeshRider:MapVM"
    }

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    init {
        observeMyLocation()
        observeTeamLocations()
        observeGeofences()
        observeSOSAlerts()
    }

    private fun observeMyLocation() {
        viewModelScope.launch {
            locationSharingManager.myLocation.collect { location ->
                location?.let {
                    _uiState.update { state ->
                        state.copy(
                            myLocation = LocationUiModel(
                                latitude = it.latitude,
                                longitude = it.longitude,
                                accuracy = it.accuracy,
                                heading = it.bearing
                            )
                        )
                    }
                }
            }
        }

        viewModelScope.launch {
            locationSharingManager.sharingState.collect { sharingState ->
                val isSharing = sharingState is SharingState.Active
                _uiState.update { it.copy(isSharingLocation = isSharing) }
            }
        }
    }

    private fun observeTeamLocations() {
        viewModelScope.launch {
            locationSharingManager.teamLocations.collect { locations ->
                val members = locations.map { (hexKey, location) ->
                    val distance = _uiState.value.myLocation?.let { myLoc ->
                        calculateDistance(
                            myLoc.latitude, myLoc.longitude,
                            location.latitude, location.longitude
                        ).toInt()
                    } ?: 0

                    TeamMemberLocationUiModel(
                        id = hexKey,
                        name = location.memberName ?: "Team ${hexKey.take(4).uppercase()}",
                        status = when {
                            location.speed > 2.0 -> "MOVING"
                            else -> "ACTIVE"
                        },
                        latitude = location.latitude,
                        longitude = location.longitude,
                        distance = distance,
                        lastUpdate = formatTimestamp(location.timestamp)
                    )
                }
                _uiState.update { it.copy(trackedMembers = members) }
            }
        }
    }

    private fun observeGeofences() {
        viewModelScope.launch {
            locationSharingManager.geofences.collect { geofences ->
                val uiGeofences = geofences.map { geofence ->
                    GeofenceUiModel(
                        id = geofence.id,
                        name = geofence.name,
                        type = geofence.type.name,
                        x = ((geofence.longitude + 180) % 360).toFloat(),
                        y = ((geofence.latitude + 90) % 180).toFloat(),
                        radius = geofence.radiusMeters / 10f // Scale for display
                    )
                }
                _uiState.update { it.copy(geofences = uiGeofences) }
            }
        }
    }

    private fun observeSOSAlerts() {
        viewModelScope.launch {
            sosManager.sosState.collect { state ->
                if (state is SOSState.Active) {
                    val alert = SOSAlertUiModel(
                        id = state.alertId,
                        senderName = "You",
                        x = 100f,
                        y = 100f,
                        timestamp = System.currentTimeMillis()
                    )
                    _uiState.update { it.copy(sosAlerts = listOf(alert)) }
                } else {
                    _uiState.update { it.copy(sosAlerts = emptyList()) }
                }
            }
        }
    }

    fun toggleLocationSharing() {
        viewModelScope.launch {
            if (_uiState.value.isSharingLocation) {
                locationSharingManager.stopSharing()
            } else {
                locationSharingManager.startSharing()
            }
        }
    }

    fun refreshLocations() {
        viewModelScope.launch {
            Log.d(TAG, "Refreshing locations")
        }
    }

    fun centerOnMyLocation() {
        Log.d(TAG, "Center on my location")
    }

    /**
     * Select a team member to show details
     * Jan 2026: Shows bottom sheet with member info and call actions
     */
    fun selectMember(memberId: String) {
        val member = _uiState.value.trackedMembers.find { it.id == memberId }
        if (member != null) {
            Log.d(TAG, "Selected member: ${member.name}")
            _uiState.update { it.copy(selectedMember = member) }
        }
    }

    /**
     * Clear member selection (dismiss detail sheet)
     */
    fun clearSelection() {
        _uiState.update { it.copy(selectedMember = null) }
    }

    fun activateSOS() {
        viewModelScope.launch {
            try {
                val keyPair = settingsRepository.getOrCreateKeyPair()
                settingsRepository.username.first().let { username ->
                    sosManager.activateSOS(
                        myPublicKey = keyPair.publicKey,
                        myName = username,
                        type = SOSType.GENERAL,
                        message = "Emergency at current location"
                    )
                }
                Log.i(TAG, "SOS activated from map")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to activate SOS", e)
            }
        }
    }

    private fun calculateDistance(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val r = 6371000.0 // Earth's radius in meters
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val deltaPhi = Math.toRadians(lat2 - lat1)
        val deltaLambda = Math.toRadians(lon2 - lon1)

        val a = Math.sin(deltaPhi / 2) * Math.sin(deltaPhi / 2) +
                Math.cos(phi1) * Math.cos(phi2) *
                Math.sin(deltaLambda / 2) * Math.sin(deltaLambda / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))

        return r * c
    }

    private fun formatTimestamp(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        return when {
            diff < 60_000 -> "now"
            diff < 3600_000 -> "${diff / 60_000}m ago"
            else -> "${diff / 3600_000}h ago"
        }
    }
}

data class MapUiState(
    val myLocation: LocationUiModel? = null,
    val trackedMembers: List<TeamMemberLocationUiModel> = emptyList(),
    val geofences: List<GeofenceUiModel> = emptyList(),
    val sosAlerts: List<SOSAlertUiModel> = emptyList(),
    val isSharingLocation: Boolean = false,
    // Jan 2026: Selected member for detail sheet
    val selectedMember: TeamMemberLocationUiModel? = null
)
