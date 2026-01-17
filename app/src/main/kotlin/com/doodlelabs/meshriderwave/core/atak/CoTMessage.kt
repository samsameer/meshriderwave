/*
 * Mesh Rider Wave - Cursor-on-Target (CoT) Message
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 *
 * Cursor-on-Target (CoT) protocol implementation for ATAK interoperability
 * Based on MIL-STD-2525 symbology and CoT Event Schema
 *
 * References:
 * - CoT Event Schema: https://www.mitre.org/sites/default/files/pdf/09_4937.pdf
 * - MIL-STD-2525D: Military symbology standard
 * - ATAK Developer Documentation
 *
 * Features:
 * - Full CoT event XML serialization/deserialization
 * - MIL-STD-2525 type codes for unit identification
 * - Support for point, detail, and contact elements
 * - RFC 3339 timestamp compliance
 */

package com.doodlelabs.meshriderwave.core.atak

import android.util.Log
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/**
 * Cursor-on-Target (CoT) Event Message
 *
 * CoT is a standardized XML format used by ATAK (Android Tactical Assault Kit)
 * and other military/tactical applications for sharing situational awareness data.
 *
 * Event Structure:
 * ```xml
 * <event version="2.0" uid="..." type="..." time="..." start="..." stale="..." how="...">
 *   <point lat="..." lon="..." hae="..." ce="..." le="..."/>
 *   <detail>
 *     <contact callsign="..."/>
 *     <__group name="..." role="..."/>
 *     <track course="..." speed="..."/>
 *     <precisionlocation geopointsrc="..." altsrc="..."/>
 *   </detail>
 * </event>
 * ```
 *
 * @property uid Unique identifier for this event (typically device-based)
 * @property type MIL-STD-2525 type code (e.g., "a-f-G-U-C" for friendly ground unit)
 * @property time When the event was generated (ISO 8601)
 * @property start When the event begins to be valid (usually same as time)
 * @property stale When the event expires (typically time + 5 minutes)
 * @property how How the event was generated (m-g = machine GPS, h-e = human estimated)
 * @property point Geographic location with accuracy
 * @property detail Optional detailed information
 */
data class CoTMessage(
    val uid: String,
    val type: String,
    val time: Long,
    val start: Long = time,
    val stale: Long = time + DEFAULT_STALE_DURATION_MS,
    val how: String = HOW_MACHINE_GPS,
    val point: CoTPoint,
    val detail: CoTDetail? = null,
    val version: String = COT_VERSION
) {
    companion object {
        private const val TAG = "MeshRider:CoT"

        // CoT Schema version
        const val COT_VERSION = "2.0"

        // Default stale duration (5 minutes)
        const val DEFAULT_STALE_DURATION_MS = 5 * 60 * 1000L

        // SOS stale duration (15 minutes - longer for emergencies)
        const val SOS_STALE_DURATION_MS = 15 * 60 * 1000L

        // ========== How Codes ==========
        // Machine-generated
        const val HOW_MACHINE_GPS = "m-g"          // Machine GPS
        const val HOW_MACHINE_PREDICTED = "m-p"    // Machine predicted
        const val HOW_MACHINE_FUSED = "m-f"        // Machine sensor fusion
        const val HOW_MACHINE_CONFIGURED = "m-c"   // Machine configured

        // Human-generated
        const val HOW_HUMAN_ESTIMATED = "h-e"      // Human estimated
        const val HOW_HUMAN_CALCULATED = "h-c"     // Human calculated
        const val HOW_HUMAN_TRANSCRIBED = "h-t"    // Human transcribed

        // ========== MIL-STD-2525 Type Codes ==========
        // Format: a-{affiliation}-G-{battle dimension}-{function}
        //
        // Affiliation:
        //   f = Friendly, h = Hostile, u = Unknown, n = Neutral
        //
        // Battle Dimension:
        //   G = Ground, A = Air, S = Sea Surface, U = Subsurface
        //
        // Function codes for ground units:
        //   U = Unit (general)
        //   C = Combat
        //   I = Infantry
        //   E = Engineer
        //   R = Recon

        // Friendly ground units
        const val TYPE_FRIENDLY_GROUND_UNIT = "a-f-G-U-C"       // Friendly ground unit combat
        const val TYPE_FRIENDLY_GROUND_INFANTRY = "a-f-G-U-C-I" // Friendly infantry
        const val TYPE_FRIENDLY_GROUND_RECON = "a-f-G-U-C-R"    // Friendly recon
        const val TYPE_FRIENDLY_GROUND_ENGINEER = "a-f-G-U-C-E" // Friendly engineer
        const val TYPE_FRIENDLY_GROUND_VEHICLE = "a-f-G-E-V"    // Friendly vehicle

        // Hostile ground units
        const val TYPE_HOSTILE_GROUND_UNIT = "a-h-G-U-C"        // Hostile ground unit
        const val TYPE_HOSTILE_GROUND_INFANTRY = "a-h-G-U-C-I"  // Hostile infantry

        // Unknown/Pending units
        const val TYPE_UNKNOWN_GROUND_UNIT = "a-u-G"            // Unknown ground
        const val TYPE_PENDING_GROUND_UNIT = "a-p-G"            // Pending ground

        // Neutral units
        const val TYPE_NEUTRAL_GROUND_UNIT = "a-n-G"            // Neutral ground

        // Special types
        const val TYPE_EMERGENCY_SOS = "a-f-G-U-C-E-M"          // Emergency/Medical
        const val TYPE_WAYPOINT = "b-m-p-w"                     // Waypoint
        const val TYPE_ROUTE = "b-m-r"                          // Route
        const val TYPE_BOUNDARY = "b-t-o"                       // Boundary/geofence

        // ATAK self-marker (special type for own position)
        const val TYPE_ATAK_SELF = "a-f-G-U-C"

        // ========== UID Prefixes ==========
        const val UID_PREFIX_MESH_RIDER = "MRWave"
        const val UID_PREFIX_SOS = "SOS"

        // ISO 8601 / RFC 3339 date format for CoT
        private val dateFormat: SimpleDateFormat by lazy {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
        }

        /**
         * Format timestamp as CoT-compliant ISO 8601 string
         */
        fun formatTimestamp(millis: Long): String {
            return dateFormat.format(Date(millis))
        }

        /**
         * Parse CoT timestamp to milliseconds
         */
        fun parseTimestamp(timestamp: String): Long {
            return try {
                dateFormat.parse(timestamp)?.time ?: System.currentTimeMillis()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse timestamp: $timestamp", e)
                System.currentTimeMillis()
            }
        }

        /**
         * Generate unique CoT UID for a device
         *
         * Format: MRWave-{shortId}
         * Example: MRWave-ABC12345
         *
         * @param deviceId Short device ID (typically first 8 hex chars of public key)
         */
        fun generateUid(deviceId: String): String {
            return "$UID_PREFIX_MESH_RIDER-$deviceId"
        }

        /**
         * Generate SOS UID
         */
        fun generateSosUid(deviceId: String): String {
            return "$UID_PREFIX_SOS-$deviceId-${System.currentTimeMillis()}"
        }

        /**
         * Parse CoT message from XML string
         *
         * @param xml Raw XML string
         * @return Parsed CoTMessage or null if parsing fails
         */
        fun fromXml(xml: String): CoTMessage? {
            return try {
                val factory = XmlPullParserFactory.newInstance()
                factory.isNamespaceAware = false
                val parser = factory.newPullParser()
                parser.setInput(StringReader(xml))

                var uid: String? = null
                var type: String? = null
                var time: Long? = null
                var start: Long? = null
                var stale: Long? = null
                var how: String? = null
                var version: String? = null
                var point: CoTPoint? = null
                var detail: CoTDetail? = null

                var eventType = parser.eventType
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    when (eventType) {
                        XmlPullParser.START_TAG -> {
                            when (parser.name) {
                                "event" -> {
                                    uid = parser.getAttributeValue(null, "uid")
                                    type = parser.getAttributeValue(null, "type")
                                    version = parser.getAttributeValue(null, "version") ?: COT_VERSION
                                    how = parser.getAttributeValue(null, "how") ?: HOW_MACHINE_GPS

                                    parser.getAttributeValue(null, "time")?.let {
                                        time = parseTimestamp(it)
                                    }
                                    parser.getAttributeValue(null, "start")?.let {
                                        start = parseTimestamp(it)
                                    }
                                    parser.getAttributeValue(null, "stale")?.let {
                                        stale = parseTimestamp(it)
                                    }
                                }
                                "point" -> {
                                    point = CoTPoint(
                                        latitude = parser.getAttributeValue(null, "lat")?.toDoubleOrNull() ?: 0.0,
                                        longitude = parser.getAttributeValue(null, "lon")?.toDoubleOrNull() ?: 0.0,
                                        hae = parser.getAttributeValue(null, "hae")?.toDoubleOrNull() ?: 0.0,
                                        ce = parser.getAttributeValue(null, "ce")?.toDoubleOrNull() ?: 9999999.0,
                                        le = parser.getAttributeValue(null, "le")?.toDoubleOrNull() ?: 9999999.0
                                    )
                                }
                                "detail" -> {
                                    detail = parseDetail(parser)
                                }
                            }
                        }
                    }
                    eventType = parser.next()
                }

                if (uid != null && type != null && time != null && point != null) {
                    CoTMessage(
                        uid = uid,
                        type = type,
                        time = time,
                        start = start ?: time,
                        stale = stale ?: (time + DEFAULT_STALE_DURATION_MS),
                        how = how ?: HOW_MACHINE_GPS,
                        point = point,
                        detail = detail,
                        version = version ?: COT_VERSION
                    )
                } else {
                    Log.w(TAG, "Missing required CoT fields: uid=$uid, type=$type, time=$time, point=$point")
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse CoT XML", e)
                null
            }
        }

        /**
         * Parse detail element and its children
         */
        private fun parseDetail(parser: XmlPullParser): CoTDetail {
            var callsign: String? = null
            var groupName: String? = null
            var groupRole: String? = null
            var course: Double? = null
            var speed: Double? = null
            var battery: Int? = null
            var remarks: String? = null
            val customAttributes = mutableMapOf<String, String>()

            var depth = 1
            while (depth > 0) {
                when (parser.next()) {
                    XmlPullParser.START_TAG -> {
                        depth++
                        when (parser.name) {
                            "contact" -> {
                                callsign = parser.getAttributeValue(null, "callsign")
                            }
                            "__group" -> {
                                groupName = parser.getAttributeValue(null, "name")
                                groupRole = parser.getAttributeValue(null, "role")
                            }
                            "track" -> {
                                course = parser.getAttributeValue(null, "course")?.toDoubleOrNull()
                                speed = parser.getAttributeValue(null, "speed")?.toDoubleOrNull()
                            }
                            "status" -> {
                                battery = parser.getAttributeValue(null, "battery")?.toIntOrNull()
                            }
                            "remarks" -> {
                                // Read text content
                                if (parser.next() == XmlPullParser.TEXT) {
                                    remarks = parser.text
                                }
                            }
                            else -> {
                                // Capture custom attributes
                                for (i in 0 until parser.attributeCount) {
                                    val key = "${parser.name}.${parser.getAttributeName(i)}"
                                    customAttributes[key] = parser.getAttributeValue(i)
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        depth--
                    }
                    XmlPullParser.END_DOCUMENT -> {
                        break
                    }
                }
            }

            return CoTDetail(
                callsign = callsign,
                groupName = groupName,
                groupRole = groupRole,
                course = course,
                speed = speed,
                battery = battery,
                remarks = remarks,
                customAttributes = customAttributes.takeIf { it.isNotEmpty() }
            )
        }

        /**
         * Get type code based on affiliation
         */
        fun getTypeForAffiliation(affiliation: Affiliation): String {
            return when (affiliation) {
                Affiliation.FRIENDLY -> TYPE_FRIENDLY_GROUND_UNIT
                Affiliation.HOSTILE -> TYPE_HOSTILE_GROUND_UNIT
                Affiliation.NEUTRAL -> TYPE_NEUTRAL_GROUND_UNIT
                Affiliation.UNKNOWN -> TYPE_UNKNOWN_GROUND_UNIT
            }
        }
    }

    /**
     * Serialize CoT message to XML string
     *
     * Produces RFC-compliant CoT XML:
     * ```xml
     * <?xml version="1.0" encoding="UTF-8"?>
     * <event version="2.0" uid="MRWave-ABC123" type="a-f-G-U-C"
     *        time="2026-01-17T10:30:00.000Z" start="2026-01-17T10:30:00.000Z"
     *        stale="2026-01-17T10:35:00.000Z" how="m-g">
     *   <point lat="37.7749" lon="-122.4194" hae="10.0" ce="5.0" le="5.0"/>
     *   <detail>
     *     <contact callsign="ALPHA-1"/>
     *     <__group name="Red Team" role="Team Member"/>
     *     <track course="90.0" speed="5.0"/>
     *     <precisionlocation geopointsrc="GPS" altsrc="GPS"/>
     *   </detail>
     * </event>
     * ```
     */
    fun toXml(): String {
        val writer = StringWriter()
        val serializer = Xml.newSerializer()

        serializer.setOutput(writer)
        serializer.startDocument("UTF-8", null)

        // <event> element
        serializer.startTag(null, "event")
        serializer.attribute(null, "version", version)
        serializer.attribute(null, "uid", uid)
        serializer.attribute(null, "type", type)
        serializer.attribute(null, "time", formatTimestamp(time))
        serializer.attribute(null, "start", formatTimestamp(start))
        serializer.attribute(null, "stale", formatTimestamp(stale))
        serializer.attribute(null, "how", how)

        // <point> element
        serializer.startTag(null, "point")
        serializer.attribute(null, "lat", point.latitude.toString())
        serializer.attribute(null, "lon", point.longitude.toString())
        serializer.attribute(null, "hae", point.hae.toString())
        serializer.attribute(null, "ce", point.ce.toString())
        serializer.attribute(null, "le", point.le.toString())
        serializer.endTag(null, "point")

        // <detail> element (optional but recommended)
        if (detail != null || true) { // Always include detail for ATAK compatibility
            serializer.startTag(null, "detail")

            // <contact> - callsign
            detail?.callsign?.let { callsign ->
                serializer.startTag(null, "contact")
                serializer.attribute(null, "callsign", callsign)
                serializer.endTag(null, "contact")
            }

            // <__group> - team/group info
            if (detail?.groupName != null) {
                serializer.startTag(null, "__group")
                serializer.attribute(null, "name", detail.groupName)
                detail.groupRole?.let { serializer.attribute(null, "role", it) }
                serializer.endTag(null, "__group")
            }

            // <track> - movement info
            if (detail?.course != null || detail?.speed != null) {
                serializer.startTag(null, "track")
                detail.course?.let { serializer.attribute(null, "course", it.toString()) }
                detail.speed?.let { serializer.attribute(null, "speed", it.toString()) }
                serializer.endTag(null, "track")
            }

            // <status> - device status
            detail?.battery?.let { battery ->
                serializer.startTag(null, "status")
                serializer.attribute(null, "battery", battery.toString())
                serializer.endTag(null, "status")
            }

            // <precisionlocation> - GPS source info
            serializer.startTag(null, "precisionlocation")
            serializer.attribute(null, "geopointsrc", if (how == HOW_MACHINE_GPS) "GPS" else "USER")
            serializer.attribute(null, "altsrc", if (how == HOW_MACHINE_GPS) "GPS" else "USER")
            serializer.endTag(null, "precisionlocation")

            // <remarks> - free text
            detail?.remarks?.let { remarks ->
                serializer.startTag(null, "remarks")
                serializer.text(remarks)
                serializer.endTag(null, "remarks")
            }

            // Mesh Rider Wave specific extensions
            serializer.startTag(null, "MeshRiderWave")
            serializer.attribute(null, "version", "2.2.0")
            serializer.attribute(null, "app", "com.doodlelabs.meshriderwave")
            serializer.endTag(null, "MeshRiderWave")

            serializer.endTag(null, "detail")
        }

        serializer.endTag(null, "event")
        serializer.endDocument()

        return writer.toString()
    }

    /**
     * Check if this message is expired (stale)
     */
    fun isStale(): Boolean {
        return System.currentTimeMillis() > stale
    }

    /**
     * Check if this message represents an SOS/emergency
     */
    fun isSos(): Boolean {
        return type == TYPE_EMERGENCY_SOS || uid.startsWith(UID_PREFIX_SOS)
    }

    /**
     * Get affiliation from type code
     */
    fun getAffiliation(): Affiliation {
        return when {
            type.startsWith("a-f") -> Affiliation.FRIENDLY
            type.startsWith("a-h") -> Affiliation.HOSTILE
            type.startsWith("a-n") -> Affiliation.NEUTRAL
            else -> Affiliation.UNKNOWN
        }
    }

    /**
     * Create a copy with updated timestamp (for refresh)
     */
    fun refresh(staleDurationMs: Long = DEFAULT_STALE_DURATION_MS): CoTMessage {
        val now = System.currentTimeMillis()
        return copy(
            time = now,
            start = now,
            stale = now + staleDurationMs
        )
    }
}

/**
 * CoT Point - Geographic location with accuracy
 *
 * @property latitude WGS84 latitude in decimal degrees
 * @property longitude WGS84 longitude in decimal degrees
 * @property hae Height Above Ellipsoid in meters (GPS altitude)
 * @property ce Circular Error (horizontal accuracy) in meters
 * @property le Linear Error (vertical accuracy) in meters
 */
data class CoTPoint(
    val latitude: Double,
    val longitude: Double,
    val hae: Double = 0.0,
    val ce: Double = 9999999.0,  // Unknown accuracy
    val le: Double = 9999999.0   // Unknown accuracy
) {
    companion object {
        // Maximum valid accuracy value (9999999 = unknown)
        const val ACCURACY_UNKNOWN = 9999999.0
    }

    /**
     * Check if accuracy is known/valid
     */
    fun hasValidAccuracy(): Boolean {
        return ce < ACCURACY_UNKNOWN && le < ACCURACY_UNKNOWN
    }

    /**
     * Get accuracy description
     */
    fun getAccuracyDescription(): String {
        return when {
            ce < 5 -> "Excellent"
            ce < 10 -> "Good"
            ce < 50 -> "Moderate"
            ce < 100 -> "Poor"
            ce < ACCURACY_UNKNOWN -> "Very Poor"
            else -> "Unknown"
        }
    }
}

/**
 * CoT Detail - Extended event information
 *
 * @property callsign Human-readable identifier (displayed on map)
 * @property groupName Team/group name
 * @property groupRole Role within group (Team Lead, Team Member, etc.)
 * @property course Direction of travel in degrees (0-360, 0=North)
 * @property speed Speed in meters per second
 * @property battery Battery percentage (0-100)
 * @property remarks Free-text remarks/notes
 * @property customAttributes Additional vendor-specific attributes
 */
data class CoTDetail(
    val callsign: String? = null,
    val groupName: String? = null,
    val groupRole: String? = null,
    val course: Double? = null,
    val speed: Double? = null,
    val battery: Int? = null,
    val remarks: String? = null,
    val customAttributes: Map<String, String>? = null
) {
    companion object {
        // Standard group roles
        const val ROLE_TEAM_LEAD = "Team Lead"
        const val ROLE_TEAM_MEMBER = "Team Member"
        const val ROLE_HQ = "HQ"
        const val ROLE_MEDIC = "Medic"
        const val ROLE_OBSERVER = "Observer"
    }
}

/**
 * MIL-STD-2525 Affiliation codes
 */
enum class Affiliation(val code: Char, val displayName: String) {
    FRIENDLY('f', "Friendly"),
    HOSTILE('h', "Hostile"),
    NEUTRAL('n', "Neutral"),
    UNKNOWN('u', "Unknown");

    companion object {
        fun fromCode(code: Char): Affiliation {
            return entries.find { it.code == code } ?: UNKNOWN
        }
    }
}

/**
 * Builder for creating CoT messages easily
 */
class CoTMessageBuilder {
    private var uid: String = ""
    private var type: String = CoTMessage.TYPE_FRIENDLY_GROUND_UNIT
    private var time: Long = System.currentTimeMillis()
    private var staleDuration: Long = CoTMessage.DEFAULT_STALE_DURATION_MS
    private var how: String = CoTMessage.HOW_MACHINE_GPS
    private var latitude: Double = 0.0
    private var longitude: Double = 0.0
    private var altitude: Double = 0.0
    private var accuracy: Double = CoTPoint.ACCURACY_UNKNOWN
    private var callsign: String? = null
    private var groupName: String? = null
    private var groupRole: String? = null
    private var course: Double? = null
    private var speed: Double? = null
    private var battery: Int? = null
    private var remarks: String? = null

    fun uid(uid: String) = apply { this.uid = uid }
    fun uid(deviceId: String, prefix: String = CoTMessage.UID_PREFIX_MESH_RIDER) =
        apply { this.uid = "$prefix-$deviceId" }

    fun type(type: String) = apply { this.type = type }
    fun affiliation(affiliation: Affiliation) =
        apply { this.type = CoTMessage.getTypeForAffiliation(affiliation) }

    fun time(timeMillis: Long) = apply { this.time = timeMillis }
    fun staleDuration(durationMs: Long) = apply { this.staleDuration = durationMs }
    fun how(how: String) = apply { this.how = how }

    fun location(lat: Double, lon: Double, alt: Double = 0.0, accuracy: Double = CoTPoint.ACCURACY_UNKNOWN) = apply {
        this.latitude = lat
        this.longitude = lon
        this.altitude = alt
        this.accuracy = accuracy
    }

    fun callsign(callsign: String) = apply { this.callsign = callsign }
    fun group(name: String, role: String = CoTDetail.ROLE_TEAM_MEMBER) = apply {
        this.groupName = name
        this.groupRole = role
    }

    fun movement(course: Double, speedMps: Double) = apply {
        this.course = course
        this.speed = speedMps
    }

    fun battery(percent: Int) = apply { this.battery = percent }
    fun remarks(text: String) = apply { this.remarks = text }

    fun build(): CoTMessage {
        require(uid.isNotBlank()) { "UID is required" }

        return CoTMessage(
            uid = uid,
            type = type,
            time = time,
            start = time,
            stale = time + staleDuration,
            how = how,
            point = CoTPoint(
                latitude = latitude,
                longitude = longitude,
                hae = altitude,
                ce = accuracy,
                le = accuracy
            ),
            detail = if (hasDetail()) {
                CoTDetail(
                    callsign = callsign,
                    groupName = groupName,
                    groupRole = groupRole,
                    course = course,
                    speed = speed,
                    battery = battery,
                    remarks = remarks
                )
            } else null
        )
    }

    private fun hasDetail(): Boolean {
        return callsign != null || groupName != null || course != null ||
                speed != null || battery != null || remarks != null
    }
}

/**
 * DSL builder function for CoT messages
 *
 * Usage:
 * ```kotlin
 * val message = cotMessage {
 *     uid("ABC12345")
 *     type(CoTMessage.TYPE_FRIENDLY_GROUND_UNIT)
 *     location(37.7749, -122.4194, 10.0)
 *     callsign("ALPHA-1")
 *     group("Red Team", CoTDetail.ROLE_TEAM_MEMBER)
 * }
 * ```
 */
inline fun cotMessage(block: CoTMessageBuilder.() -> Unit): CoTMessage {
    return CoTMessageBuilder().apply(block).build()
}
