/**
 * ATAK PluginLayoutInflater Stub
 *
 * Used to inflate layouts from the plugin's context within ATAK.
 * Required because plugins run in ATAK's process but have their own resources.
 */
package com.atakmap.android.maps

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

object PluginLayoutInflater {

    /**
     * Inflate a layout from the plugin context.
     *
     * @param pluginContext The plugin's context (NOT ATAK's context)
     * @param layoutResId The layout resource ID from the plugin
     * @param root Optional root ViewGroup
     * @return The inflated view
     */
    @JvmStatic
    fun inflate(pluginContext: Context, layoutResId: Int, root: ViewGroup?): View {
        val inflater = LayoutInflater.from(pluginContext)
        return inflater.inflate(layoutResId, root, false)
    }
}
