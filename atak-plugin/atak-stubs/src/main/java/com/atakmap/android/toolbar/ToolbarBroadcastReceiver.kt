/**
 * ATAK ToolbarBroadcastReceiver Stub
 *
 * Base class for toolbar button receivers in ATAK plugins.
 */
package com.atakmap.android.toolbar

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

abstract class ToolbarBroadcastReceiver : BroadcastReceiver() {

    companion object {
        const val TOGGLE_TOOLBAR = "com.atakmap.android.toolbar.TOGGLE_TOOLBAR"
    }

    abstract override fun onReceive(context: Context, intent: Intent)
}
