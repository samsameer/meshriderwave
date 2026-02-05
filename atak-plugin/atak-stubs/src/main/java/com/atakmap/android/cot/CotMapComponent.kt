/**
 * ATAK CotMapComponent Stub
 *
 * Manages CoT dispatching within ATAK.
 * Internal dispatcher puts markers on local map.
 * External dispatcher sends CoT over network to other ATAK clients.
 */
package com.atakmap.android.cot

import android.content.Intent

abstract class CotMapComponent {

    companion object {
        @JvmStatic
        fun getInternalDispatcher(): CotDispatcher = CotDispatcher.INTERNAL

        @JvmStatic
        fun getExternalDispatcher(): CotDispatcher = CotDispatcher.EXTERNAL
    }
}

class CotDispatcher private constructor(private val name: String) {
    companion object {
        val INTERNAL = CotDispatcher("internal")
        val EXTERNAL = CotDispatcher("external")
    }

    fun dispatch(event: CotEvent) {
        // Stub — ATAK core handles actual dispatch
    }

    fun dispatch(event: CotEvent, extra: android.os.Bundle?) {
        // Stub — ATAK core handles actual dispatch
    }
}
