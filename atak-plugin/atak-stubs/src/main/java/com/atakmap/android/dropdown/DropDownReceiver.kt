/**
 * ATAK DropDownReceiver Stub
 *
 * Base class for dropdown panel receivers in ATAK plugins.
 * Dropdowns appear as sliding panels from the right side of the screen.
 */
package com.atakmap.android.dropdown

import android.content.Context
import android.content.Intent
import android.view.View
import com.atakmap.android.maps.MapView

abstract class DropDownReceiver(protected val mapView: MapView) {

    companion object {
        const val DROPDOWN_STATE_NONE = 0
        const val DROPDOWN_STATE_PENDING = 1
        const val DROPDOWN_STATE_OPENING = 2
        const val DROPDOWN_STATE_OPEN = 3
        const val DROPDOWN_STATE_CLOSING = 4
        const val DROPDOWN_STATE_CLOSED = 5
    }

    abstract fun disposeImpl()

    abstract fun onReceive(context: Context, intent: Intent)

    protected fun showDropDown(
        contentView: View,
        width: Double,
        height: Double,
        fullscreen: Boolean,
        closeListener: OnCloseListener? = null
    ) {
        // Stub implementation
    }

    protected fun closeDropDown() {
        // Stub implementation
    }

    protected fun isVisible(): Boolean = false

    fun interface OnCloseListener {
        fun onDropDownClose()
    }
}

object DropDownManager {
    fun getInstance(): DropDownManager = this

    fun unregisterReceiver(receiver: DropDownReceiver) {
        // Stub implementation
    }
}
