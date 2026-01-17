/**
 * ATAK Plugin Lifecycle Stubs
 *
 * Core plugin lifecycle interfaces from the ATAK Plugin SDK.
 * All ATAK plugins must implement these interfaces.
 */
package transapps.maps.plugin.lifecycle

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import transapps.mapi.MapView

/**
 * Main plugin lifecycle interface.
 * Implement this to create an ATAK plugin.
 */
interface Lifecycle {
    /**
     * Called when the plugin is first created.
     * Initialize your plugin components here.
     */
    fun onCreate(activity: Activity, mapView: MapView)

    /**
     * Called when the plugin is started (resumed).
     */
    fun onStart()

    /**
     * Called when the plugin is paused.
     */
    fun onPause()

    /**
     * Called when the plugin is resumed.
     */
    fun onResume()

    /**
     * Called when the plugin is being destroyed.
     * Clean up resources here.
     */
    fun onDestroy()

    /**
     * Called when device configuration changes (e.g., rotation).
     */
    fun onConfigurationChanged(newConfig: Configuration)

    /**
     * Called when the plugin should finish.
     */
    fun onFinish()
}

/**
 * Context wrapper for ATAK plugins.
 */
class PluginContext(
    private val baseContext: Context,
    val pluginPackage: String
) : android.content.ContextWrapper(baseContext) {

    fun getPluginClassLoader(): ClassLoader = this.classLoader
}
