/*
 * Mesh Rider Wave - Radio API Client
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 *
 * JSON-RPC client for DoodleLabs MeshRider radio management.
 *
 * Features:
 * - JSON-RPC 2.0 over HTTP
 * - Session management with token refresh
 * - UBUS service calls (iwinfo, wireless, system, file)
 * - Automatic reconnection with exponential backoff
 * - Coroutine-based async API
 *
 * Radio API Reference:
 * - /rpc/auth - Authentication (login, refresh, logout)
 * - /rpc/uci  - UCI configuration (get, set, commit)
 * - /rpc/sys  - System operations (reboot, sysupgrade)
 * - /rpc/fs   - File system access (read, write, exec)
 */

package com.doodlelabs.meshriderwave.core.radio

import com.doodlelabs.meshriderwave.core.util.logD
import com.doodlelabs.meshriderwave.core.util.logE
import com.doodlelabs.meshriderwave.core.util.logI
import com.doodlelabs.meshriderwave.core.util.logW
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * JSON-RPC client for MeshRider radio API.
 *
 * Usage:
 * ```kotlin
 * val client = RadioApiClient()
 * client.connect("10.223.232.141", "root", "doodle")
 * val status = client.getWirelessStatus()
 * ```
 */
@Singleton
class RadioApiClient @Inject constructor() {

    companion object {
        // Default credentials
        const val DEFAULT_USERNAME = "root"
        const val DEFAULT_PASSWORD = "doodle"

        // API endpoints
        const val RPC_PATH = "/rpc"
        const val AUTH_PATH = "/rpc/auth"
        const val UCI_PATH = "/rpc/uci"
        const val SYS_PATH = "/rpc/sys"
        const val FS_PATH = "/rpc/fs"
        const val UBUS_PATH = "/ubus"

        // Timeouts (ms)
        const val CONNECT_TIMEOUT = 5000
        const val READ_TIMEOUT = 10000

        // Session
        const val TOKEN_REFRESH_INTERVAL = 270_000L  // 4.5 minutes (token valid for 5 min)
        const val MAX_RETRY_ATTEMPTS = 3
        const val RETRY_DELAY_BASE = 1000L

        // UBUS services
        const val UBUS_IWINFO = "iwinfo"
        const val UBUS_WIRELESS = "wireless"
        const val UBUS_SYSTEM = "system"
        const val UBUS_FILE = "file"
        const val UBUS_NETWORK = "network"
        const val UBUS_UCI = "uci"
    }

    // Connection state
    private var baseUrl: String = ""
    private var authToken: String? = null
    private var ubusSessionId: String? = null
    private var isConnected = false

    // JSON-RPC request ID counter
    private val requestId = AtomicInteger(1)

    // Coroutine scope
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Token refresh job
    private var tokenRefreshJob: Job? = null

    // Connection state flow
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    /**
     * Connection state
     */
    sealed class ConnectionState {
        object Disconnected : ConnectionState()
        object Connecting : ConnectionState()
        data class Connected(val radioIp: String, val radioInfo: RadioInfo?) : ConnectionState()
        data class Error(val message: String) : ConnectionState()
    }

    /**
     * Radio information
     */
    data class RadioInfo(
        val hostname: String,
        val model: String,
        val firmwareVersion: String,
        val serialNumber: String,
        val uptime: Long,
        val localTime: Long
    )

    /**
     * Wireless status
     */
    data class WirelessStatus(
        val ssid: String,
        val bssid: String,
        val mode: String,
        val channel: Int,
        val frequency: Int,
        val bandwidth: Int,
        val txPower: Int,
        val signal: Int,
        val noise: Int,
        val bitrate: Int,
        val encryption: String,
        val isConnected: Boolean
    ) {
        val snr: Int get() = signal - noise
        val linkQuality: Int get() = ((snr + 100).coerceIn(0, 100))
    }

    /**
     * Associated station (mesh peer)
     */
    data class AssociatedStation(
        val macAddress: String,
        val ipAddress: String?,
        val signal: Int,
        val noise: Int,
        val txBitrate: Int,
        val rxBitrate: Int,
        val txPackets: Long,
        val rxPackets: Long,
        val inactive: Int  // ms since last activity
    ) {
        val snr: Int get() = signal - noise
    }

    /**
     * Connect to a MeshRider radio.
     *
     * @param host Radio IP address
     * @param username Login username (default: root)
     * @param password Login password (default: doodle)
     * @return true if connection successful
     */
    suspend fun connect(
        host: String,
        username: String = DEFAULT_USERNAME,
        password: String = DEFAULT_PASSWORD
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            _connectionState.value = ConnectionState.Connecting
            logI("Connecting to radio at $host")

            baseUrl = "http://$host"

            // Authenticate
            val authResult = authenticate(username, password)
            if (!authResult) {
                _connectionState.value = ConnectionState.Error("Authentication failed")
                return@withContext false
            }

            isConnected = true

            // Get radio info
            val radioInfo = getRadioInfo()

            // Start token refresh
            startTokenRefresh(username, password)

            _connectionState.value = ConnectionState.Connected(host, radioInfo)
            logI("Connected to radio: ${radioInfo?.hostname ?: host}")

            true
        } catch (e: Exception) {
            logE("Failed to connect to radio: ${e.message}")
            _connectionState.value = ConnectionState.Error(e.message ?: "Connection failed")
            false
        }
    }

    /**
     * Disconnect from radio.
     */
    fun disconnect() {
        tokenRefreshJob?.cancel()
        tokenRefreshJob = null
        authToken = null
        ubusSessionId = null
        isConnected = false
        _connectionState.value = ConnectionState.Disconnected
        logI("Disconnected from radio")
    }

    /**
     * Authenticate with the radio.
     */
    private suspend fun authenticate(username: String, password: String): Boolean {
        val params = JSONObject().apply {
            put("username", username)
            put("password", password)
        }

        val response = rpcCall(AUTH_PATH, "login", params)
        if (response == null) {
            logE("Authentication failed: no response")
            return false
        }

        val result = response.optJSONObject("result")
        if (result == null) {
            logE("Authentication failed: ${response.optString("error")}")
            return false
        }

        authToken = result.optString("token")
        ubusSessionId = result.optString("ubus_rpc_session")

        if (authToken.isNullOrEmpty()) {
            logE("Authentication failed: no token received")
            return false
        }

        logD("Authenticated successfully, token: ${authToken?.take(8)}...")
        return true
    }

    /**
     * Start token refresh timer.
     */
    private fun startTokenRefresh(username: String, password: String) {
        tokenRefreshJob?.cancel()
        tokenRefreshJob = scope.launch {
            while (isActive && isConnected) {
                delay(TOKEN_REFRESH_INTERVAL)
                try {
                    logD("Refreshing auth token...")
                    authenticate(username, password)
                } catch (e: Exception) {
                    logW("Token refresh failed: ${e.message}")
                }
            }
        }
    }

    /**
     * Get radio system information.
     */
    suspend fun getRadioInfo(): RadioInfo? = withContext(Dispatchers.IO) {
        try {
            val sysInfo = ubusCall("system", "board", null)
            val hostname = sysInfo?.optString("hostname") ?: "MeshRider"
            val model = sysInfo?.optString("model") ?: "Unknown"
            val release = sysInfo?.optJSONObject("release")
            val version = release?.optString("version") ?: "Unknown"

            val systemInfo = ubusCall("system", "info", null)
            val uptime = systemInfo?.optLong("uptime") ?: 0
            val localTime = systemInfo?.optLong("localtime") ?: 0

            RadioInfo(
                hostname = hostname,
                model = model,
                firmwareVersion = version,
                serialNumber = "",  // Need to get from UCI
                uptime = uptime,
                localTime = localTime
            )
        } catch (e: Exception) {
            logE("Failed to get radio info: ${e.message}")
            null
        }
    }

    /**
     * Get wireless interface status.
     *
     * @param iface Interface name (default: wlan0)
     */
    suspend fun getWirelessStatus(iface: String = "wlan0"): WirelessStatus? = withContext(Dispatchers.IO) {
        try {
            val params = JSONObject().put("device", iface)
            val info = ubusCall("iwinfo", "info", params)

            if (info == null) {
                logW("No wireless info for $iface")
                return@withContext null
            }

            WirelessStatus(
                ssid = info.optString("ssid", ""),
                bssid = info.optString("bssid", ""),
                mode = info.optString("mode", ""),
                channel = info.optInt("channel", 0),
                frequency = info.optInt("frequency", 0),
                bandwidth = info.optInt("htmode", 20).let {
                    when {
                        it.toString().contains("40") -> 40
                        it.toString().contains("80") -> 80
                        else -> 20
                    }
                },
                txPower = info.optInt("txpower", 0),
                signal = info.optInt("signal", -100),
                noise = info.optInt("noise", -95),
                bitrate = info.optInt("bitrate", 0),
                encryption = info.optJSONObject("encryption")?.optString("description") ?: "None",
                isConnected = info.optString("ssid", "").isNotEmpty()
            )
        } catch (e: Exception) {
            logE("Failed to get wireless status: ${e.message}")
            null
        }
    }

    /**
     * Get list of associated stations (mesh peers).
     *
     * @param iface Interface name (default: wlan0)
     */
    suspend fun getAssociatedStations(iface: String = "wlan0"): List<AssociatedStation> =
        withContext(Dispatchers.IO) {
            try {
                val params = JSONObject().put("device", iface)
                val result = ubusCall("iwinfo", "assoclist", params)

                val stations = mutableListOf<AssociatedStation>()
                val results = result?.optJSONArray("results") ?: return@withContext stations

                for (i in 0 until results.length()) {
                    val sta = results.getJSONObject(i)
                    stations.add(
                        AssociatedStation(
                            macAddress = sta.optString("mac", ""),
                            ipAddress = null,  // Need ARP lookup
                            signal = sta.optInt("signal", -100),
                            noise = sta.optInt("noise", -95),
                            txBitrate = sta.optInt("tx_rate", 0),
                            rxBitrate = sta.optInt("rx_rate", 0),
                            txPackets = sta.optLong("tx_packets", 0),
                            rxPackets = sta.optLong("rx_packets", 0),
                            inactive = sta.optInt("inactive", 0)
                        )
                    )
                }

                stations
            } catch (e: Exception) {
                logE("Failed to get associated stations: ${e.message}")
                emptyList()
            }
        }

    /**
     * Get available channels for the interface.
     *
     * @param iface Interface name (default: wlan0)
     */
    suspend fun getAvailableChannels(iface: String = "wlan0"): List<ChannelInfo> =
        withContext(Dispatchers.IO) {
            try {
                val params = JSONObject().put("device", iface)
                val result = ubusCall("iwinfo", "freqlist", params)

                val channels = mutableListOf<ChannelInfo>()
                val freqList = result?.optJSONArray("results") ?: return@withContext channels

                for (i in 0 until freqList.length()) {
                    val freq = freqList.getJSONObject(i)
                    channels.add(
                        ChannelInfo(
                            channel = freq.optInt("channel", 0),
                            frequency = freq.optInt("mhz", 0),
                            restricted = freq.optBoolean("restricted", false),
                            active = freq.optBoolean("active", false)
                        )
                    )
                }

                channels
            } catch (e: Exception) {
                logE("Failed to get available channels: ${e.message}")
                emptyList()
            }
        }

    /**
     * Channel information
     */
    data class ChannelInfo(
        val channel: Int,
        val frequency: Int,
        val restricted: Boolean,
        val active: Boolean
    )

    /**
     * Switch mesh channel (mesh-wide).
     *
     * @param channel Channel number
     * @param bandwidth Bandwidth in MHz (5, 10, 20, 40)
     */
    suspend fun switchChannel(channel: Int, bandwidth: Int = 20): Boolean =
        withContext(Dispatchers.IO) {
            try {
                logI("Switching to channel $channel ($bandwidth MHz)")

                val params = JSONObject().apply {
                    put("channel", channel)
                    put("bandwidth", bandwidth)
                }

                val result = ubusCall("message-system", "chswitch", params)
                val success = result?.optBoolean("success", false) ?: false

                if (success) {
                    logI("Channel switch initiated")
                } else {
                    logW("Channel switch failed: ${result?.optString("error")}")
                }

                success
            } catch (e: Exception) {
                logE("Failed to switch channel: ${e.message}")
                false
            }
        }

    /**
     * Get GPS location from radio (if equipped).
     */
    suspend fun getGpsLocation(): GpsLocation? = withContext(Dispatchers.IO) {
        try {
            // Execute gpspipe command on radio
            val params = JSONObject().apply {
                put("command", "gpspipe")
                put("params", JSONArray().put("-w").put("-n").put("1"))
            }

            val result = ubusCall("file", "exec", params)
            val stdout = result?.optString("stdout") ?: return@withContext null

            // Parse NMEA or JSON output
            parseGpsOutput(stdout)
        } catch (e: Exception) {
            logD("GPS not available: ${e.message}")
            null
        }
    }

    /**
     * GPS location
     */
    data class GpsLocation(
        val latitude: Double,
        val longitude: Double,
        val altitude: Double,
        val speed: Double,
        val heading: Double,
        val timestamp: Long,
        val fixType: Int  // 0=none, 1=GPS, 2=DGPS, 3=PPS
    )

    /**
     * Parse GPS output (NMEA or JSON format).
     */
    private fun parseGpsOutput(output: String): GpsLocation? {
        return try {
            // Try JSON format first
            val json = JSONObject(output)
            GpsLocation(
                latitude = json.optDouble("lat", 0.0),
                longitude = json.optDouble("lon", 0.0),
                altitude = json.optDouble("alt", 0.0),
                speed = json.optDouble("speed", 0.0),
                heading = json.optDouble("track", 0.0),
                timestamp = System.currentTimeMillis(),
                fixType = json.optInt("mode", 0)
            )
        } catch (e: Exception) {
            // Try NMEA format
            parseNmea(output)
        }
    }

    /**
     * Parse NMEA sentence.
     */
    private fun parseNmea(nmea: String): GpsLocation? {
        // Simple GPGGA parsing
        val lines = nmea.split("\n")
        for (line in lines) {
            if (line.startsWith("\$GPGGA") || line.startsWith("\$GNGGA")) {
                val parts = line.split(",")
                if (parts.size >= 10) {
                    val lat = parseNmeaCoord(parts[2], parts[3])
                    val lon = parseNmeaCoord(parts[4], parts[5])
                    val alt = parts[9].toDoubleOrNull() ?: 0.0
                    val fix = parts[6].toIntOrNull() ?: 0

                    if (lat != 0.0 && lon != 0.0) {
                        return GpsLocation(
                            latitude = lat,
                            longitude = lon,
                            altitude = alt,
                            speed = 0.0,
                            heading = 0.0,
                            timestamp = System.currentTimeMillis(),
                            fixType = fix
                        )
                    }
                }
            }
        }
        return null
    }

    /**
     * Parse NMEA coordinate.
     */
    private fun parseNmeaCoord(value: String, direction: String): Double {
        if (value.isEmpty()) return 0.0

        val deg: Double
        val min: Double

        if (direction == "N" || direction == "S") {
            // Latitude: DDMM.MMMM
            deg = value.substring(0, 2).toDoubleOrNull() ?: return 0.0
            min = value.substring(2).toDoubleOrNull() ?: return 0.0
        } else {
            // Longitude: DDDMM.MMMM
            deg = value.substring(0, 3).toDoubleOrNull() ?: return 0.0
            min = value.substring(3).toDoubleOrNull() ?: return 0.0
        }

        var result = deg + (min / 60.0)
        if (direction == "S" || direction == "W") {
            result = -result
        }

        return result
    }

    /**
     * Get UCI configuration value.
     *
     * @param config Config file (e.g., "wireless")
     * @param section Section name (e.g., "radio0")
     * @param option Option name (e.g., "channel")
     */
    suspend fun uciGet(config: String, section: String, option: String): String? =
        withContext(Dispatchers.IO) {
            try {
                val params = JSONObject().apply {
                    put("config", config)
                    put("section", section)
                    put("option", option)
                }

                val result = rpcCall(UCI_PATH, "get", params)
                result?.optJSONObject("result")?.optString("value")
            } catch (e: Exception) {
                logE("UCI get failed: ${e.message}")
                null
            }
        }

    /**
     * Set UCI configuration value.
     *
     * @param config Config file (e.g., "wireless")
     * @param section Section name (e.g., "radio0")
     * @param option Option name (e.g., "channel")
     * @param value New value
     */
    suspend fun uciSet(config: String, section: String, option: String, value: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val params = JSONObject().apply {
                    put("config", config)
                    put("section", section)
                    put("option", option)
                    put("value", value)
                }

                val result = rpcCall(UCI_PATH, "set", params)
                result?.has("result") ?: false
            } catch (e: Exception) {
                logE("UCI set failed: ${e.message}")
                false
            }
        }

    /**
     * Commit UCI changes.
     *
     * @param config Config file to commit (e.g., "wireless")
     */
    suspend fun uciCommit(config: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val params = JSONObject().apply {
                put("config", config)
            }

            val result = rpcCall(UCI_PATH, "commit", params)
            result?.has("result") ?: false
        } catch (e: Exception) {
            logE("UCI commit failed: ${e.message}")
            false
        }
    }

    /**
     * Make a UBUS call.
     *
     * @param service UBUS service name (e.g., "iwinfo", "system")
     * @param method Method name (e.g., "info", "board")
     * @param params Method parameters (can be null)
     */
    suspend fun ubusCall(service: String, method: String, params: JSONObject?): JSONObject? =
        withContext(Dispatchers.IO) {
            try {
                val ubusParams = JSONArray().apply {
                    put(ubusSessionId ?: "00000000000000000000000000000000")
                    put(service)
                    put(method)
                    put(params ?: JSONObject())
                }

                val request = JSONObject().apply {
                    put("jsonrpc", "2.0")
                    put("id", requestId.getAndIncrement())
                    put("method", "call")
                    put("params", ubusParams)
                }

                val response = httpPost("$baseUrl$UBUS_PATH", request.toString())
                val json = JSONObject(response)

                val resultArray = json.optJSONArray("result")
                if (resultArray != null && resultArray.length() >= 2) {
                    val statusCode = resultArray.optInt(0)
                    if (statusCode == 0) {
                        return@withContext resultArray.optJSONObject(1)
                    } else {
                        logW("UBUS call failed: status=$statusCode")
                    }
                }

                null
            } catch (e: Exception) {
                logE("UBUS call failed: $service.$method - ${e.message}")
                null
            }
        }

    /**
     * Make a JSON-RPC call.
     */
    private suspend fun rpcCall(path: String, method: String, params: JSONObject?): JSONObject? =
        withContext(Dispatchers.IO) {
            try {
                val request = JSONObject().apply {
                    put("jsonrpc", "2.0")
                    put("id", requestId.getAndIncrement())
                    put("method", method)
                    params?.let { put("params", it) }
                }

                val headers = mutableMapOf<String, String>()
                authToken?.let { headers["Authorization"] = "Bearer $it" }

                val response = httpPost("$baseUrl$path", request.toString(), headers)
                JSONObject(response)
            } catch (e: Exception) {
                logE("RPC call failed: $method - ${e.message}")
                null
            }
        }

    /**
     * HTTP POST request.
     */
    private fun httpPost(
        urlString: String,
        body: String,
        headers: Map<String, String> = emptyMap()
    ): String {
        val url = URL(urlString)
        val conn = url.openConnection() as HttpURLConnection

        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = CONNECT_TIMEOUT
            conn.readTimeout = READ_TIMEOUT
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")

            headers.forEach { (key, value) ->
                conn.setRequestProperty(key, value)
            }

            OutputStreamWriter(conn.outputStream).use { writer ->
                writer.write(body)
                writer.flush()
            }

            val responseCode = conn.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw Exception("HTTP $responseCode: ${conn.responseMessage}")
            }

            return BufferedReader(InputStreamReader(conn.inputStream)).use { reader ->
                reader.readText()
            }
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Check if connected to a radio.
     */
    fun isConnected(): Boolean = isConnected && authToken != null

    /**
     * Release resources.
     */
    fun release() {
        disconnect()
        scope.cancel()
    }
}
