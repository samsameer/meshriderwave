/**
 * ATAK CotEvent Stub
 *
 * Represents a Cursor-on-Target event.
 * CoT is the standard format for sharing tactical information in TAK.
 *
 * References:
 * - MIL-STD-2525D for type codes
 * - CoT schema: https://www.mitre.org/sites/default/files/pdf/09_4937.pdf
 */
package com.atakmap.android.cot

import com.atakmap.coremap.maps.time.CoordinatedTime

class CotEvent {
    var uid: String = ""
    var type: String = ""
    var version: String = "2.0"
    var how: String = "m-g"
    var time: CoordinatedTime = CoordinatedTime()
    var start: CoordinatedTime = CoordinatedTime()
    var stale: CoordinatedTime = CoordinatedTime()

    private var point: CotPoint? = null
    private var detail: CotDetail? = null

    fun setPoint(point: CotPoint) {
        this.point = point
    }

    fun getPoint(): CotPoint? = point

    fun setDetail(detail: CotDetail) {
        this.detail = detail
    }

    fun getDetail(): CotDetail? = detail

    fun isValid(): Boolean {
        return uid.isNotEmpty() && type.isNotEmpty() && point != null
    }

    override fun toString(): String {
        return "CotEvent(uid=$uid, type=$type)"
    }
}

class CotPoint(
    val lat: Double,
    val lon: Double,
    val hae: Double = 0.0,
    val ce: Double = 999999.0,
    val le: Double = 999999.0
)

class CotDetail {
    private val children = mutableMapOf<String, String>()
    private val childElements = mutableListOf<CotDetail>()

    var elementName: String = ""

    fun setAttribute(key: String, value: String) {
        children[key] = value
    }

    fun getAttribute(key: String): String? = children[key]

    fun addChild(child: CotDetail) {
        childElements.add(child)
    }

    fun getChildren(): List<CotDetail> = childElements
}
