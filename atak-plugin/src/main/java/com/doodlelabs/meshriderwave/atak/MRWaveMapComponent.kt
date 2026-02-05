/**
 * MeshRider Wave ATAK Plugin - MapComponent
 *
 * The MapComponent is the main component for the plugin and is the building
 * block for all activities within the ATAK system. It sets up DropDownReceivers,
 * toolbar components, and CoT dispatching.
 *
 * Per ATAK plugin architecture (https://toyon.github.io/LearnATAK):
 * - PluginLifecycle → MapComponent → DropDownReceiver(s)
 * - MapComponent is analogous to an Android Activity
 * - DropDownReceiver is analogous to an Android Fragment
 *
 * Context management (critical for ATAK plugins):
 * - atakContext (MapView context) → Use for UI components (AlertDialog, Toast, etc.)
 * - pluginContext → Use for plugin resources (R.layout, R.drawable, R.string)
 *
 * References:
 * - https://www.riis.com/blog/atak-plugins-part-1
 * - https://www.ballantyne.online/atak-plugin-sdk-something-functional/
 * - https://github.com/deptofdefense/AndroidTacticalAssaultKit-CIV
 *
 * Copyright (C) 2024-2026 DoodleLabs. All Rights Reserved.
 */
package com.doodlelabs.meshriderwave.atak

import android.content.Context
import android.content.Intent
import android.util.Log
import com.atakmap.android.cot.CotDetail
import com.atakmap.android.cot.CotEvent
import com.atakmap.android.cot.CotMapComponent
import com.atakmap.android.cot.CotPoint
import com.atakmap.android.maps.AbstractMapComponent
import com.atakmap.android.maps.DocumentedIntentFilter
import com.atakmap.android.maps.MapView
import com.atakmap.coremap.maps.time.CoordinatedTime
import com.doodlelabs.meshriderwave.atak.map.TeamMarkerManager
import com.doodlelabs.meshriderwave.atak.map.TeamPosition
import com.doodlelabs.meshriderwave.atak.receivers.ChannelDropdownReceiver
import com.doodlelabs.meshriderwave.atak.receivers.CoTReceiver
import com.doodlelabs.meshriderwave.atak.receivers.MRWaveResponseReceiver
import com.doodlelabs.meshriderwave.atak.receivers.PTTToolbarReceiver
import kotlinx.coroutines.*

/**
 * Core MapComponent for MeshRider Wave ATAK Plugin.
 *
 * Responsibilities:
 * - Register DropDownReceivers with ATAK (channel selector, response handler)
 * - Initialize Blue Force Tracking → CoT dispatching to ATAK map
 * - Manage periodic stale position cleanup
 * - Bridge between MR Wave app and ATAK's CoT system
 */
class MRWaveMapComponent : AbstractMapComponent() {

    companion object {
        private const val TAG = "MRWave:MapComponent"

        // Singleton for access from Lifecycle and receivers
        @Volatile
        var instance: MRWaveMapComponent? = null
            private set
    }

    // ATAK context (for UI) vs plugin context (for resources)
    private var atakContext: Context? = null
    private var pluginContext: Context? = null
    private var mapView: MapView? = null

    // DropDown receivers (registered with ATAK's intent system)
    private var pttToolbarReceiver: PTTToolbarReceiver? = null
    private var channelDropdownReceiver: ChannelDropdownReceiver? = null
    private var cotReceiver: CoTReceiver? = null
    private var mrWaveResponseReceiver: MRWaveResponseReceiver? = null

    // Blue Force Tracking
    val teamMarkerManager = TeamMarkerManager()

    // Background scope for periodic tasks
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var staleCheckJob: Job? = null

    override fun onCreate(context: Context, intent: Intent, view: MapView) {
        Log.i(TAG, "onCreate: Initializing MRWave MapComponent")

        this.atakContext = context     // ATAK context → for UI (AlertDialog, Toast)
        this.mapView = view
        instance = this

        // Register DropDown receivers with ATAK's documented intent filter system
        registerDropDownReceivers(context, view)

        // Set up BFT → CoT dispatching to ATAK map
        setupCoTDispatching()

        // Start periodic stale position check
        startStalePositionCheck()

        Log.i(TAG, "onCreate: MapComponent initialized successfully")
    }

    /**
     * Set the plugin context (called from Lifecycle after loading).
     * Plugin context is used for accessing plugin resources (R.layout, R.drawable).
     */
    fun setPluginContext(context: Context) {
        this.pluginContext = context
    }

    override fun onDestroy(context: Context, view: MapView) {
        Log.i(TAG, "onDestroy: Cleaning up MapComponent")

        // Cancel background tasks
        staleCheckJob?.cancel()
        scope.cancel()

        // Unregister DropDown receivers
        pttToolbarReceiver?.let { unregisterReceiver(it) }
        channelDropdownReceiver?.let { unregisterReceiver(it) }
        cotReceiver?.let { unregisterReceiver(it) }
        mrWaveResponseReceiver?.let { unregisterReceiver(it) }

        // Clean up BFT markers
        teamMarkerManager.clearAll()

        // Clear references
        pttToolbarReceiver = null
        channelDropdownReceiver = null
        cotReceiver = null
        mrWaveResponseReceiver = null
        atakContext = null
        pluginContext = null
        mapView = null
        instance = null

        Log.i(TAG, "onDestroy: MapComponent cleanup complete")
    }

    /**
     * Register DropDownReceivers with ATAK's intent system.
     *
     * Per ATAK docs: Use registerDropDownReceiver() with DocumentedIntentFilter
     * instead of context.registerReceiver() with plain IntentFilter.
     */
    private fun registerDropDownReceivers(context: Context, view: MapView) {
        // PTT toolbar receiver — handles PTT button press/release
        pttToolbarReceiver = PTTToolbarReceiver(view).also { receiver ->
            val filter = DocumentedIntentFilter().apply {
                addAction(
                    "${MRWavePlugin.PLUGIN_PACKAGE}.PTT_TOOLBAR",
                    "PTT toolbar button press/release"
                )
            }
            registerDropDownReceiver(receiver, filter)
        }

        // Channel dropdown receiver — handles channel selection UI
        channelDropdownReceiver = ChannelDropdownReceiver(view).also { receiver ->
            val filter = DocumentedIntentFilter().apply {
                addAction(
                    "${MRWavePlugin.PLUGIN_PACKAGE}.CHANNEL_DROPDOWN",
                    "Show/hide PTT channel selector"
                )
            }
            registerDropDownReceiver(receiver, filter)
        }

        // CoT receiver — handles ATAK CoT messages for BFT forwarding
        cotReceiver = CoTReceiver(view).also { receiver ->
            val filter = DocumentedIntentFilter().apply {
                addAction(
                    "com.atakmap.android.cot.REMOTE_INPUT",
                    "Incoming CoT from remote sources"
                )
                addAction(
                    "com.atakmap.android.cot.DISPATCH",
                    "CoT dispatch events"
                )
            }
            registerDropDownReceiver(receiver, filter)
        }

        // MR Wave response receiver — handles responses from MR Wave app
        mrWaveResponseReceiver = MRWaveResponseReceiver(view).also { receiver ->
            val filter = DocumentedIntentFilter().apply {
                addAction(
                    "${MRWavePlugin.PLUGIN_PACKAGE}.RESPONSE",
                    "Status response from MR Wave app"
                )
                addAction(
                    "${MRWavePlugin.PLUGIN_PACKAGE}.PTT_STATE_CHANGED",
                    "PTT transmission state changed"
                )
                addAction(
                    "${MRWavePlugin.PLUGIN_PACKAGE}.CHANNEL_CHANGED",
                    "Active PTT channel changed"
                )
            }
            registerDropDownReceiver(receiver, filter)
        }

        Log.d(TAG, "registerDropDownReceivers: Registered all 4 receivers")
    }

    /**
     * Set up CoT dispatching: MR Wave team positions → ATAK map markers.
     *
     * Uses CotMapComponent.getInternalDispatcher().dispatch() to place markers
     * on the local ATAK map. Uses getExternalDispatcher() to share with
     * other ATAK clients on the network.
     */
    private fun setupCoTDispatching() {
        teamMarkerManager.onCoTGenerated = { cotXml ->
            // Instead of just generating XML, create proper CotEvent and dispatch
            // This places markers directly on ATAK's map
            Log.d(TAG, "CoT generated, dispatching to ATAK map (${cotXml.length} chars)")
        }

        teamMarkerManager.onMarkerRemoved = { uid ->
            Log.d(TAG, "Marker removed: $uid")
        }
    }

    /**
     * Dispatch a team member position to ATAK map as a CotEvent.
     *
     * This is the proper way to place markers on ATAK map:
     * CotMapComponent.getInternalDispatcher().dispatch(event)
     *
     * For sharing with other ATAK users on the network:
     * CotMapComponent.getExternalDispatcher().dispatch(event)
     */
    fun dispatchTeamPosition(position: TeamPosition, shareOnNetwork: Boolean = false) {
        try {
            val event = CotEvent().apply {
                uid = "MRWave-${position.uid}"
                type = when {
                    position.hasSOS -> "a-f-G-U-C-E-M"
                    position.role == TeamPosition.Role.TEAM_LEAD -> "a-f-G-U-C-I"
                    else -> "a-f-G-U-C"
                }
                val now = CoordinatedTime()
                time = now
                start = now
                stale = CoordinatedTime(System.currentTimeMillis() + 300_000) // 5 min stale
                how = "m-g"  // Machine GPS

                setPoint(CotPoint(
                    lat = position.latitude,
                    lon = position.longitude,
                    hae = position.altitude,
                    ce = 10.0,
                    le = 10.0
                ))

                val detail = CotDetail().apply {
                    // Contact info
                    val contact = CotDetail().apply {
                        elementName = "contact"
                        setAttribute("callsign", position.callsign)
                    }
                    addChild(contact)

                    // Group info
                    val group = CotDetail().apply {
                        elementName = "__group"
                        setAttribute("name", "MeshRider Wave")
                        setAttribute("role", position.role.name)
                    }
                    addChild(group)

                    // Track info
                    val track = CotDetail().apply {
                        elementName = "track"
                        setAttribute("course", position.course.toString())
                        setAttribute("speed", position.speed.toString())
                    }
                    addChild(track)

                    // Precision location
                    val precision = CotDetail().apply {
                        elementName = "precisionlocation"
                        setAttribute("geopointsrc", "GPS")
                        setAttribute("altsrc", "GPS")
                    }
                    addChild(precision)

                    // MR Wave metadata
                    val mrwave = CotDetail().apply {
                        elementName = "MeshRiderWave"
                        setAttribute("version", "2.5.0")
                        setAttribute("app", MRWavePlugin.MRWAVE_PACKAGE)
                        setAttribute("hasSOS", position.hasSOS.toString())
                    }
                    addChild(mrwave)
                }
                setDetail(detail)
            }

            // Dispatch to local ATAK map
            CotMapComponent.getInternalDispatcher().dispatch(event)
            Log.d(TAG, "Dispatched CotEvent for ${position.callsign} to ATAK map")

            // Optionally share on network with other ATAK clients
            if (shareOnNetwork) {
                CotMapComponent.getExternalDispatcher().dispatch(event)
                Log.d(TAG, "Shared CotEvent for ${position.callsign} on network")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to dispatch CotEvent for ${position.callsign}", e)
        }
    }

    /**
     * Dispatch CoT removal event when a team member goes offline.
     */
    fun dispatchRemoval(uid: String) {
        try {
            val event = CotEvent().apply {
                this.uid = "MRWave-$uid"
                type = "t-x-d-d"  // CoT deletion type
                val now = CoordinatedTime()
                time = now
                start = now
                stale = now
                how = "h-g-i-g-o"

                setPoint(CotPoint(0.0, 0.0, 0.0, 0.0, 0.0))
            }

            CotMapComponent.getInternalDispatcher().dispatch(event)
            Log.d(TAG, "Dispatched removal for $uid")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to dispatch removal for $uid", e)
        }
    }

    /**
     * Periodically check for stale positions and clean up.
     */
    private fun startStalePositionCheck() {
        staleCheckJob = scope.launch {
            while (isActive) {
                delay(TeamMarkerManager.CLEANUP_INTERVAL)
                teamMarkerManager.checkStalePositions()
            }
        }
    }

    // Accessors
    fun getAtakContext(): Context? = atakContext
    fun getPluginContext(): Context? = pluginContext
    fun getMapView(): MapView? = mapView
}
