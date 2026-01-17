/**
 * ATAK MapView Stub
 *
 * Core ATAK map view class. At runtime, the real ATAK implementation is used.
 */
package com.atakmap.android.maps

import android.content.Context
import android.view.View

abstract class MapView(context: Context) : View(context) {

    companion object {
        @JvmStatic
        fun getMapView(): MapView? = null
    }

    abstract fun getSelfMarker(): Marker?

    abstract fun getRootGroup(): MapGroup?

    // Note: getContext() and post() are inherited from View
}

abstract class Marker {
    abstract fun getUID(): String
    abstract fun getPoint(): GeoPoint
    abstract fun getTitle(): String
    abstract fun setTitle(title: String)
}

abstract class MapGroup {
    abstract fun addItem(item: MapItem)
    abstract fun removeItem(item: MapItem)
    abstract fun findItem(predicate: String, value: String): MapItem?
}

abstract class MapItem {
    abstract fun getUID(): String
    abstract fun setVisible(visible: Boolean)
}

data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double = Double.NaN
) {
    companion object {
        @JvmField
        val ZERO = GeoPoint(0.0, 0.0)
    }
}
