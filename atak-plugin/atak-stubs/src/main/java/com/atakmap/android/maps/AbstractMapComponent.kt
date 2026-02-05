/**
 * ATAK AbstractMapComponent Stub
 *
 * Base class for all ATAK MapComponents.
 * MapComponent is the building block for plugin functionality within ATAK.
 * Analogous to an Android Activity class.
 *
 * References:
 * - https://toyon.github.io/LearnATAK/docs/setup/atak_plugin/
 * - https://www.riis.com/blog/atak-plugins-part-1
 */
package com.atakmap.android.maps

import android.content.Context
import android.content.Intent
import com.atakmap.android.dropdown.DropDownReceiver

abstract class AbstractMapComponent {

    /**
     * Called when the component is created.
     * Initialize DropDownReceivers, preferences, and other components here.
     *
     * @param context The ATAK application context (use for UI)
     * @param intent The intent that started the component
     * @param view The MapView instance
     */
    abstract fun onCreate(context: Context, intent: Intent, view: MapView)

    /**
     * Called when the component is being destroyed.
     * Clean up receivers, listeners, and resources.
     */
    abstract fun onDestroy(context: Context, view: MapView)

    /**
     * Register a DropDownReceiver with ATAK's intent system.
     * This is the proper way to register receivers in ATAK plugins.
     */
    protected fun registerDropDownReceiver(
        receiver: DropDownReceiver,
        filter: DocumentedIntentFilter
    ) {
        // Stub — ATAK core registers the receiver
    }

    /**
     * Unregister a DropDownReceiver.
     */
    protected fun unregisterReceiver(receiver: DropDownReceiver) {
        // Stub — ATAK core unregisters
    }
}

/**
 * Documented intent filter for ATAK receiver registration.
 * Wraps Android's IntentFilter with documentation support.
 */
class DocumentedIntentFilter {
    private val actions = mutableListOf<String>()

    fun addAction(action: String, description: String = "") {
        actions.add(action)
    }

    fun getActions(): List<String> = actions
}
