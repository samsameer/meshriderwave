/**
 * MeshRider Wave ATAK Plugin — Lifecycle
 *
 * Per ATAK plugin architecture (PluginLifecycle → MapComponent → DropDownReceiver):
 * - This class is the PluginLifecycle (1st core class)
 * - Creates and delegates to MRWaveMapComponent (2nd core class)
 * - MapComponent registers DropDownReceivers (3rd core class)
 *
 * References:
 * - https://toyon.github.io/LearnATAK
 * - https://www.riis.com/blog/atak-plugins-part-1
 * - https://github.com/deptofdefense/AndroidTacticalAssaultKit-CIV
 *
 * Copyright (C) 2024-2026 DoodleLabs. All Rights Reserved.
 */
package com.doodlelabs.meshriderwave.atak

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.util.Log
import com.atakmap.android.maps.MapView
import com.doodlelabs.meshriderwave.atak.toolbar.PTTToolbarComponent
import transapps.maps.plugin.lifecycle.Lifecycle

class MRWavePlugin : Lifecycle {

    companion object {
        private const val TAG = "MRWave:Plugin"

        const val PLUGIN_PACKAGE = "com.doodlelabs.meshriderwave.atak"
        const val PLUGIN_NAME = "MeshRider Wave"
        const val PLUGIN_VERSION = "1.0.0"
        const val MRWAVE_PACKAGE = "com.doodlelabs.meshriderwave"

        // Intent actions for communication with MR Wave app
        const val ACTION_PTT_START = "$MRWAVE_PACKAGE.action.PTT_START"
        const val ACTION_PTT_STOP = "$MRWAVE_PACKAGE.action.PTT_STOP"
        const val ACTION_GET_CHANNELS = "$MRWAVE_PACKAGE.action.GET_CHANNELS"
        const val ACTION_SET_CHANNEL = "$MRWAVE_PACKAGE.action.SET_CHANNEL"
        const val ACTION_GET_STATUS = "$MRWAVE_PACKAGE.action.GET_STATUS"

        const val EXTRA_CHANNEL_ID = "$MRWAVE_PACKAGE.extra.CHANNEL_ID"
        const val EXTRA_PRIORITY = "$MRWAVE_PACKAGE.extra.PRIORITY"
        const val EXTRA_CALLBACK_ACTION = "$MRWAVE_PACKAGE.extra.CALLBACK_ACTION"

        @Volatile
        private var instance: MRWavePlugin? = null

        fun getInstance(): MRWavePlugin? = instance
    }

    private var pluginContext: Context? = null
    private var activity: Activity? = null

    // MapComponent — 2nd core class (handles receiver registration + CoT dispatching)
    private var mapComponent: MRWaveMapComponent? = null

    // Toolbar
    private var pttToolbarComponent: PTTToolbarComponent? = null

    // State
    private var isActive = false
    private var isPTTActive = false
    private var currentChannelId: String? = null
    private var currentChannelName: String = "Default"

    override fun onCreate(activity: Activity, mapView: transapps.mapi.MapView) {
        Log.i(TAG, "onCreate: Initializing MeshRider Wave ATAK Plugin v$PLUGIN_VERSION")

        this.activity = activity
        this.pluginContext = activity.applicationContext
        instance = this

        try {
            // Get ATAK's MapView (the real one, not transapps stub)
            val atakMapView = MapView.getMapView()
            if (atakMapView != null) {
                // Create MapComponent — this registers all DropDownReceivers
                mapComponent = MRWaveMapComponent().also { component ->
                    component.onCreate(
                        activity,
                        Intent(),
                        atakMapView
                    )
                    component.setPluginContext(activity.applicationContext)
                }
                Log.i(TAG, "onCreate: MapComponent created and initialized")
            } else {
                Log.w(TAG, "onCreate: ATAK MapView not available (running in stub mode)")
            }

            initializeToolbar()
            requestMRWaveStatus()

            Log.i(TAG, "onCreate: Plugin initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "onCreate: Failed to initialize plugin", e)
        }
    }

    override fun onStart() {
        Log.d(TAG, "onStart")
        isActive = true
        requestMRWaveStatus()
    }

    override fun onPause() {
        Log.d(TAG, "onPause")
    }

    override fun onResume() {
        Log.d(TAG, "onResume")
        isActive = true
        pttToolbarComponent?.updateState(isPTTActive, currentChannelName)
    }

    override fun onDestroy() {
        Log.i(TAG, "onDestroy: Cleaning up plugin resources")
        isActive = false

        try {
            if (isPTTActive) stopPTT()

            // Destroy MapComponent (unregisters all DropDown receivers)
            val atakMapView = MapView.getMapView()
            if (atakMapView != null && mapComponent != null) {
                mapComponent?.onDestroy(activity as Context, atakMapView)
            }
            mapComponent = null

            pttToolbarComponent?.dispose()
            pttToolbarComponent = null

            pluginContext = null
            activity = null
            instance = null

            Log.i(TAG, "onDestroy: Plugin cleanup complete")
        } catch (e: Exception) {
            Log.e(TAG, "onDestroy: Error during cleanup", e)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        Log.d(TAG, "onConfigurationChanged: ${newConfig.orientation}")
        pttToolbarComponent?.onConfigurationChanged(newConfig)
    }

    override fun onFinish() {
        Log.d(TAG, "onFinish")
    }

    // ========== Toolbar ==========

    private fun initializeToolbar() {
        val context = pluginContext ?: return
        pttToolbarComponent = PTTToolbarComponent(context).apply {
            onPTTPressed = { startPTT() }
            onPTTReleased = { stopPTT() }
            onChannelClicked = { showChannelSelector() }
        }
    }

    // ========== PTT Control ==========

    fun startPTT() {
        if (isPTTActive) return
        Log.i(TAG, "startPTT: Starting transmission on '$currentChannelName'")

        val intent = Intent(ACTION_PTT_START).apply {
            setPackage(MRWAVE_PACKAGE)
            putExtra(EXTRA_CHANNEL_ID, currentChannelId)
            putExtra(EXTRA_PRIORITY, 5)
            putExtra(EXTRA_CALLBACK_ACTION, "$PLUGIN_PACKAGE.PTT_STATE_CHANGED")
        }

        try {
            pluginContext?.sendBroadcast(intent)
            isPTTActive = true
            pttToolbarComponent?.updateState(isPTTActive, currentChannelName)
        } catch (e: Exception) {
            Log.e(TAG, "startPTT: Failed", e)
        }
    }

    fun stopPTT() {
        if (!isPTTActive) return
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
            Log.e(TAG, "stopPTT: Failed", e)
        }
    }

    // ========== Channel Management ==========

    fun showChannelSelector() {
        Log.d(TAG, "showChannelSelector: Requesting channels")
        val intent = Intent(ACTION_GET_CHANNELS).apply {
            setPackage(MRWAVE_PACKAGE)
            putExtra(EXTRA_CALLBACK_ACTION, "$PLUGIN_PACKAGE.RESPONSE")
        }
        try {
            pluginContext?.sendBroadcast(intent)
        } catch (e: Exception) {
            Log.e(TAG, "showChannelSelector: Failed", e)
        }
    }

    fun setChannel(channelId: String, channelName: String) {
        Log.i(TAG, "setChannel: '$channelName' ($channelId)")
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
            Log.e(TAG, "setChannel: Failed", e)
        }
    }

    // ========== Status Updates ==========

    private fun requestMRWaveStatus() {
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

    fun handleStatusUpdate(isConnected: Boolean, isPTTActive: Boolean, channelId: String?, channelName: String?) {
        this.isPTTActive = isPTTActive
        this.currentChannelId = channelId
        this.currentChannelName = channelName ?: "Default"
        pttToolbarComponent?.apply {
            setConnected(isConnected)
            updateState(isPTTActive, this@MRWavePlugin.currentChannelName)
        }
    }

    fun handlePTTStateChanged(isActive: Boolean) {
        this.isPTTActive = isActive
        pttToolbarComponent?.updateState(isPTTActive, currentChannelName)
    }

    fun handleChannelChanged(channelId: String, channelName: String) {
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
    fun getMapComponent(): MRWaveMapComponent? = mapComponent
}
