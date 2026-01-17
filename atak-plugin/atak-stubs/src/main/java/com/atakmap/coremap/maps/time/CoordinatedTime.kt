/**
 * ATAK CoordinatedTime Stub
 *
 * Time utility class used throughout ATAK for synchronized timestamps.
 */
package com.atakmap.coremap.maps.time

class CoordinatedTime(private val millis: Long = System.currentTimeMillis()) {

    companion object {
        @JvmStatic
        fun currentTimeMillis(): Long = System.currentTimeMillis()
    }

    fun getMilliseconds(): Long = millis

    override fun toString(): String = millis.toString()
}
