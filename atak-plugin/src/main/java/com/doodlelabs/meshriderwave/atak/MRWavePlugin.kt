/**
 * MeshRider Wave ATAK Plugin
 *
 * Main plugin class that integrates MR Wave PTT functionality into ATAK.
 * This plugin provides:
 * - PTT button on ATAK toolbar for push-to-talk voice transmission
 * - Channel selector dropdown for switching PTT channels
 * - CoT (Cursor-on-Target) message synchronization with MR Wave app
 * - Blue Force Tracking integration - peer locations on ATAK map
 *
 * Architecture:
 * ┌─────────────────────────────────────────────────────────────┐
 * │                        ATAK HOST                             │
 * │  ┌─────────────────┐  ┌──────────────────────────────────┐  │
 * │  │ MR Wave Plugin  │  │         ATAK Core                │  │
 * │  │                 │  │                                  │  │
 * │  │ ┌─────────────┐ │  │ ┌────────────┐  ┌─────────────┐ │  │
 * │  │ │ PTT Button  │─┼──┼→│ Toolbar    │  │ Map View    │ │  │
 * │  │ └─────────────┘ │  │ └────────────┘  └─────────────┘ │  │
 * │  │                 │  │                       ↑          │  │
 * │  │ ┌─────────────┐ │  │                       │          │  │
 * │  │ │ CoT Bridge  │─┼──┼───────────────────────┘          │  │
 * │  │ └─────────────┘ │  │                                  │  │
 * │  └────────┬────────┘  └──────────────────────────────────┘  │
 * │           │                                                  │
 * │           │ Intent Bridge (Signature Protected)              │
 * │           ↓                                                  │
 * │  ┌─────────────────┐                                        │
 * │  │  MR Wave App    │ (Separate APK)                         │
 * │  │  ATAKBridge.kt  │                                        │
 * │  └─────────────────┘                                        │
 * └─────────────────────────────────────────────────────────────┘
 *
 * Copyright (C) 2024-2026 DoodleLabs. All Rights Reserved.
 */
package com.doodlelabs.meshriderwave.atak

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.util.Log
import com.doodlelabs.meshriderwave.atak.receivers.ChannelDropdownReceiver
import com.doodlelabs.meshriderwave.atak.receivers.CoTReceiver
import com.doodlelabs.meshriderwave.atak.receivers.MRWaveResponseReceiver
import com.doodlelabs.meshriderwave.atak.receivers.PTTToolbarReceiver
import com.doodlelabs.meshriderwave.atak.toolbar.PTTToolbarComponent
import transapps.maps.plugin.lifecycle.Lifecycle
import transapps.mapi.MapView

/**
 * Main ATAK Plugin implementation for MeshRider Wave.
 *
 * Lifecycle:
 * 1. onCreate - Initialize plugin components, register receivers
 * 2. onStart - Register toolbar button, start CoT sync
 * 3. onPause - Pause CoT sync (battery optimization)
 * 4. onResume - Resume CoT sync
 * 5. onDestroy - Unregister receivers, cleanup resources
 */
class MRWavePlugin : Lifecycle {

    companion object {
        private const val TAG = "MRWavePlugin"

        // Plugin identification
        const val PLUGIN_PACKAGE = "com.doodlelabs.meshriderwave.atak"
        const val PLUGIN_NAME = "MeshRider Wave"
        const val PLUGIN_VERSION = "1.0.0"

        // MR Wave app package
        const val MRWAVE_PACKAGE = "com.doodlelabs.meshriderwave"

        // Intent actions for communication with MR Wave app
        const val ACTION_PTT_START = "$MRWAVE_PACKAGE.action.PTT_START"
        const val ACTION_PTT_STOP = "$MRWAVE_PACKAGE.action.PTT_STOP"
        const val ACTION_GET_CHANNELS = "$MRWAVE_PACKAGE.action.GET_CHANNELS"
        const val ACTION_SET_CHANNEL = "$MRWAVE_PACKAGE.action.SET_CHANNEL"
        const val ACTION_GET_STATUS = "$MRWAVE_PACKAGE.action.GET_STATUS"

        // Extras
        const val EXTRA_CHANNEL_ID = "$MRWAVE_PACKAGE.extra.CHANNEL_ID"
        const val EXTRA_PRIORITY = "$MRWAVE_PACKAGE.extra.PRIORITY"
        const val EXTRA_CALLBACK_ACTION = "$MRWAVE_PACKAGE.extra.CALLBACK_ACTION"

        // Singleton instance for access from receivers
        @Volatile
        private var instance: MRWavePlugin? = null

        fun getInstance(): MRWavePlugin? = instance
    }

    // Context references
    private var pluginContext: Context? = null
    private var activity: Activity? = null
    private var mapView: MapView? = null

    // Plugin components
    private var pttToolbarComponent: PTTToolbarComponent? = null
    private var pttToolbarReceiver: PTTToolbarReceiver? = null
    private var channelDropdownReceiver: ChannelDropdownReceiver? = null
    private var cotReceiver: CoTReceiver? = null
    private var mrWaveResponseReceiver: MRWaveResponseReceiver? = null

    // Plugin state
    private var isActive = false
    private var isPTTActive = false
    private var currentChannelId: String? = null
    private var currentChannelName: String = "Default"

    override fun onCreate(activity: Activity, mapView: MapView) {
        Log.i(TAG, "onCreate: Initializing MeshRider Wave ATAK Plugin v$PLUGIN_VERSION")

        this.activity = activity
        this.mapView = mapView
        this.pluginContext = activity.applicationContext

        instance = this

        try {
            // Initialize plugin components
            initializeToolbar()
            registerReceivers()

            // Request initial status from MR Wave app
            requestMRWaveStatus()

            Log.i(TAG, "onCreate: Plugin initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "onCreate: Failed to initialize plugin", e)
        }
    }

    override fun onStart() {
        Log.d(TAG, "onStart")
        isActive = true

        // Refresh status from MR Wave app
        requestMRWaveStatus()
    }

    override fun onPause() {
        Log.d(TAG, "onPause")
        // Keep running but reduce update frequency
    }

    override fun onResume() {
        Log.d(TAG, "onResume")
        isActive = true

        // Refresh UI state
        pttToolbarComponent?.updateState(isPTTActive, currentChannelName)
    }

    override fun onDestroy() {
        Log.i(TAG, "onDestroy: Cleaning up plugin resources")

        isActive = false

        try {
            // Stop any active PTT transmission
            if (isPTTActive) {
                stopPTT()
            }

            // Unregister receivers
            unregisterReceivers()

            // Cleanup components
            pttToolbarComponent?.dispose()
            pttToolbarComponent = null

            // Clear references
            pluginContext = null
            activity = null
            mapView = null
            instance = null

            Log.i(TAG, "onDestroy: Plugin cleanup complete")
        } catch (e: Exception) {
            Log.e(TAG, "onDestroy: Error during cleanup", e)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        Log.d(TAG, "onConfigurationChanged: ${newConfig.orientation}")
        // Re-layout toolbar if needed
        pttToolbarComponent?.onConfigurationChanged(newConfig)
    }

    override fun onFinish() {
        Log.d(TAG, "onFinish")
    }

    // ========== Toolbar Management ==========

    private fun initializeToolbar() {
        val context = pluginContext ?: return

        pttToolbarComponent = PTTToolbarComponent(context).apply {
            onPTTPressed = { startPTT() }
            onPTTReleased = { stopPTT() }
            onChannelClicked = { showChannelSelector() }
        }

        Log.d(TAG, "initializeToolbar: PTT toolbar component created")
    }

    // ========== Receiver Registration ==========

    private fun registerReceivers() {
        val context = pluginContext ?: return

        // PTT Toolbar Receiver
        pttToolbarReceiver = PTTToolbarReceiver().also { receiver ->
            val filter = IntentFilter().apply {
                addAction("$PLUGIN_PACKAGE.PTT_TOOLBAR")
            }
            context.registerReceiver(receiver, filter)
        }

        // Channel Dropdown Receiver
        channelDropdownReceiver = ChannelDropdownReceiver().also { receiver ->
            val filter = IntentFilter().apply {
                addAction("$PLUGIN_PACKAGE.CHANNEL_DROPDOWN")
            }
            context.registerReceiver(receiver, filter)
        }

        // CoT Receiver (for ATAK's CoT messages)
        cotReceiver = CoTReceiver().also { receiver ->
            val filter = IntentFilter().apply {
                addAction("com.atakmap.android.cot.REMOTE_INPUT")
                addAction("com.atakmap.android.cot.DISPATCH")
            }
            context.registerReceiver(receiver, filter)
        }

        // MR Wave Response Receiver
        mrWaveResponseReceiver = MRWaveResponseReceiver().also { receiver ->
            val filter = IntentFilter().apply {
                addAction("$PLUGIN_PACKAGE.RESPONSE")
                addAction("$PLUGIN_PACKAGE.PTT_STATE_CHANGED")
                addAction("$PLUGIN_PACKAGE.CHANNEL_CHANGED")
            }
            context.registerReceiver(receiver, filter)
        }

        Log.d(TAG, "registerReceivers: All receivers registered")
    }

    private fun unregisterReceivers() {
        val context = pluginContext ?: return

        listOf(
            pttToolbarReceiver,
            channelDropdownReceiver,
            cotReceiver,
            mrWaveResponseReceiver
        ).forEach { receiver ->
            try {
                receiver?.let { context.unregisterReceiver(it) }
            } catch (e: Exception) {
                Log.w(TAG, "unregisterReceivers: Receiver already unregistered", e)
            }
        }

        pttToolbarReceiver = null
        channelDropdownReceiver = null
        cotReceiver = null
        mrWaveResponseReceiver = null

        Log.d(TAG, "unregisterReceivers: All receivers unregistered")
    }

    // ========== PTT Control ==========

    /**
     * Start PTT transmission.
     * Sends intent to MR Wave app to begin voice transmission on current channel.
     */
    fun startPTT() {
        if (isPTTActive) {
            Log.w(TAG, "startPTT: Already transmitting")
            return
        }

        Log.i(TAG, "startPTT: Starting transmission on channel '$currentChannelName'")

        val intent = Intent(ACTION_PTT_START).apply {
            setPackage(MRWAVE_PACKAGE)
            putExtra(EXTRA_CHANNEL_ID, currentChannelId)
            putExtra(EXTRA_PRIORITY, 5) // Normal priority
            putExtra(EXTRA_CALLBACK_ACTION, "$PLUGIN_PACKAGE.PTT_STATE_CHANGED")
        }

        try {
            pluginContext?.sendBroadcast(intent)
            isPTTActive = true
            pttToolbarComponent?.updateState(isPTTActive, currentChannelName)
        } catch (e: Exception) {
            Log.e(TAG, "startPTT: Failed to send intent", e)
        }
    }

    /**
     * Stop PTT transmission.
     * Sends intent to MR Wave app to end voice transmission.
     */
    fun stopPTT() {
        if (!isPTTActive) {
            Log.w(TAG, "stopPTT: Not currently transmitting")
            return
        }

        Log.i(TAG, "stopPTT: Stopping transmission")

        val intent = Intent(ACTION_PTT_STOP).apply {
            setPackage(MRWAVE_PACKAGE)
            putExtra(EXTRA_CALLBACK_ACTION, "$PLUGIN_PACKAGE.PTT_STATE_CHANGED")
        }

        try {
            pluginContext?.sendBroadcast(intent)
            isPTTActive = false
            pttToolbarComponent?.updateState(isPTTActive, currentChannelName)
        } catch (e: Exception) {
            Log.e(TAG, "stopPTT: Failed to send intent", e)
        }
    }

    // ========== Channel Management ==========

    /**
     * Show channel selector dropdown.
     * Requests channel list from MR Wave app and displays selection UI.
     */
    fun showChannelSelector() {
        Log.d(TAG, "showChannelSelector: Requesting channels from MR Wave")

        val intent = Intent(ACTION_GET_CHANNELS).apply {
            setPackage(MRWAVE_PACKAGE)
            putExtra(EXTRA_CALLBACK_ACTION, "$PLUGIN_PACKAGE.RESPONSE")
        }

        try {
            pluginContext?.sendBroadcast(intent)
        } catch (e: Exception) {
            Log.e(TAG, "showChannelSelector: Failed to request channels", e)
        }
    }

    /**
     * Set active PTT channel.
     *
     * @param channelId Channel ID to switch to
     * @param channelName Display name of the channel
     */
    fun setChannel(channelId: String, channelName: String) {
        Log.i(TAG, "setChannel: Switching to channel '$channelName' ($channelId)")

        val intent = Intent(ACTION_SET_CHANNEL).apply {
            setPackage(MRWAVE_PACKAGE)
            putExtra(EXTRA_CHANNEL_ID, channelId)
            putExtra(EXTRA_CALLBACK_ACTION, "$PLUGIN_PACKAGE.CHANNEL_CHANGED")
        }

        try {
            pluginContext?.sendBroadcast(intent)
            currentChannelId = channelId
            currentChannelName = channelName
            pttToolbarComponent?.updateState(isPTTActive, currentChannelName)
        } catch (e: Exception) {
            Log.e(TAG, "setChannel: Failed to set channel", e)
        }
    }

    // ========== Status Updates ==========

    /**
     * Request current status from MR Wave app.
     * Called on start and resume to sync state.
     */
    private fun requestMRWaveStatus() {
        Log.d(TAG, "requestMRWaveStatus: Requesting status from MR Wave")

        val intent = Intent(ACTION_GET_STATUS).apply {
            setPackage(MRWAVE_PACKAGE)
            putExtra(EXTRA_CALLBACK_ACTION, "$PLUGIN_PACKAGE.RESPONSE")
        }

        try {
            pluginContext?.sendBroadcast(intent)
        } catch (e: Exception) {
            Log.w(TAG, "requestMRWaveStatus: MR Wave app may not be running", e)
        }
    }

    /**
     * Handle status update from MR Wave app.
     * Called by MRWaveResponseReceiver when status response is received.
     */
    fun handleStatusUpdate(
        isConnected: Boolean,
        isPTTActive: Boolean,
        channelId: String?,
        channelName: String?
    ) {
        Log.d(TAG, "handleStatusUpdate: connected=$isConnected, ptt=$isPTTActive, channel=$channelName")

        this.isPTTActive = isPTTActive
        this.currentChannelId = channelId
        this.currentChannelName = channelName ?: "Default"

        pttToolbarComponent?.apply {
            setConnected(isConnected)
            updateState(isPTTActive, this@MRWavePlugin.currentChannelName)
        }
    }

    /**
     * Handle PTT state change from MR Wave app.
     */
    fun handlePTTStateChanged(isActive: Boolean) {
        Log.d(TAG, "handlePTTStateChanged: $isActive")
        this.isPTTActive = isActive
        pttToolbarComponent?.updateState(isPTTActive, currentChannelName)
    }

    /**
     * Handle channel change from MR Wave app.
     */
    fun handleChannelChanged(channelId: String, channelName: String) {
        Log.d(TAG, "handleChannelChanged: $channelName ($channelId)")
        this.currentChannelId = channelId
        this.currentChannelName = channelName
        pttToolbarComponent?.updateState(isPTTActive, currentChannelName)
    }

    // ========== Accessors ==========

    fun getContext(): Context? = pluginContext
    fun getActivity(): Activity? = activity
    fun isPTTActive(): Boolean = isPTTActive
    fun getCurrentChannelId(): String? = currentChannelId
    fun getCurrentChannelName(): String = currentChannelName
}
