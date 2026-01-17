# MeshRider Wave Android - API Reference

**Version:** 2.2.0 | **Last Updated:** January 2026 | **Classification:** Technical Reference

---

## Table of Contents

1. [Core Modules](#core-modules)
   - [CryptoManager](#cryptomanager)
   - [MLSManager](#mlsmanager)
   - [PTTManager](#pttmanager)
   - [MulticastAudioManager](#multicastaudiomanager)
   - [RadioApiClient](#radioapiclient)
   - [RadioDiscoveryService](#radiodiscoveryservice)
2. [Network Modules](#network-modules)
   - [MeshNetworkManager](#meshnetworkmanager)
   - [MeshService](#meshservice)
   - [Connector](#connector)
   - [PeerDiscoveryManager](#peerdiscoverymanager)
3. [Audio Modules](#audio-modules)
   - [OpusCodecManager](#opuscodecmanager)
   - [AdaptiveJitterBuffer](#adaptivejitterbuffer)
   - [RTPPacketManager](#rtppacketmanager)
4. [Location & Safety](#location--safety)
   - [LocationSharingManager](#locationsharingmanager)
   - [SOSManager](#sosmanager)
5. [ATAK Integration](#atak-integration)
   - [ATAKBridge](#atakbridge)
   - [CoTManager](#cotmanager)
6. [Data Models](#data-models)
7. [ViewModels](#viewmodels)

---

## Core Modules

### CryptoManager

**Location:** `core/crypto/CryptoManager.kt`

Handles all cryptographic operations using libsodium (lazysodium-android).

#### Class Definition

```kotlin
@Singleton
class CryptoManager @Inject constructor() {
    // Key generation
    fun generateKeyPair(): KeyPair

    // Message encryption (E2E)
    fun encryptMessage(
        message: ByteArray,
        recipientPublicKey: ByteArray,
        ownPublicKey: ByteArray,
        ownSecretKey: ByteArray
    ): ByteArray

    // Message decryption
    fun decryptMessage(
        ciphertext: ByteArray,
        senderPublicKeyOut: ByteArray,
        ownPublicKey: ByteArray,
        ownSecretKey: ByteArray
    ): ByteArray?

    // Database encryption (password-based)
    fun encryptDatabase(data: ByteArray, password: String): ByteArray
    fun decryptDatabase(ciphertext: ByteArray, password: String): ByteArray?

    // Signing
    fun sign(message: ByteArray, secretKey: ByteArray): ByteArray
    fun verify(message: ByteArray, signature: ByteArray, publicKey: ByteArray): Boolean
}
```

#### Key Types

```kotlin
data class KeyPair(
    val publicKey: ByteArray,  // Ed25519, 32 bytes
    val secretKey: ByteArray   // Ed25519, 64 bytes
)
```

#### Usage Examples

```kotlin
// Generate identity keypair
val cryptoManager = CryptoManager()
val keyPair = cryptoManager.generateKeyPair()

// Encrypt message for recipient
val ciphertext = cryptoManager.encryptMessage(
    message = "Hello, World!".toByteArray(),
    recipientPublicKey = contact.publicKey,
    ownPublicKey = myKeyPair.publicKey,
    ownSecretKey = myKeyPair.secretKey
)

// Decrypt received message
val senderKey = ByteArray(32)
val plaintext = cryptoManager.decryptMessage(
    ciphertext = receivedData,
    senderPublicKeyOut = senderKey,  // Filled with sender's public key
    ownPublicKey = myKeyPair.publicKey,
    ownSecretKey = myKeyPair.secretKey
)

// Password-based encryption for storage
val encrypted = cryptoManager.encryptDatabase(data, "user_password")
val decrypted = cryptoManager.decryptDatabase(encrypted, "user_password")
```

#### Cryptographic Primitives

| Operation | Algorithm | Key Size |
|-----------|-----------|----------|
| Signing | Ed25519 | 256-bit |
| Key Exchange | X25519 | 256-bit |
| Symmetric | XSalsa20-Poly1305 | 256-bit |
| KDF | Argon2id | - |

---

### MLSManager

**Location:** `core/crypto/MLSManager.kt`

Implements Messaging Layer Security (RFC 9420) for group encryption.

#### Class Definition

```kotlin
@Singleton
class MLSManager @Inject constructor(
    private val cryptoManager: CryptoManager
) {
    // Group management
    suspend fun createGroup(groupId: String, adminKeyPair: KeyPair): MLSGroup
    suspend fun joinGroup(groupId: String, welcomeMessage: ByteArray): MLSGroup?
    suspend fun leaveGroup(groupId: String)

    // Member operations
    suspend fun addMember(groupId: String, memberPublicKey: ByteArray): ByteArray  // Welcome message
    suspend fun removeMember(groupId: String, memberPublicKey: ByteArray)

    // Encryption
    suspend fun encrypt(groupId: String, plaintext: ByteArray): ByteArray
    suspend fun decrypt(groupId: String, ciphertext: ByteArray): ByteArray?

    // Key updates
    suspend fun updateKeys(groupId: String)

    // State
    fun getGroup(groupId: String): MLSGroup?
    fun getGroups(): List<MLSGroup>
}

data class MLSGroup(
    val groupId: String,
    val epoch: Long,
    val members: List<ByteArray>,  // Public keys
    val isAdmin: Boolean,
    val createdAt: Long
)
```

#### Usage Examples

```kotlin
// Create a new encrypted group
val group = mlsManager.createGroup("team-alpha", myKeyPair)

// Add a member (returns welcome message to send)
val welcome = mlsManager.addMember("team-alpha", newMemberPublicKey)
sendToNewMember(welcome)

// Encrypt message for group
val ciphertext = mlsManager.encrypt("team-alpha", "Secret message".toByteArray())

// Decrypt group message
val plaintext = mlsManager.decrypt("team-alpha", receivedCiphertext)
```

---

### PTTManager

**Location:** `core/ptt/PTTManager.kt`

Manages Push-to-Talk floor control and voice transmission.

#### Class Definition

```kotlin
@Singleton
class PTTManager @Inject constructor(
    private val audioManager: MulticastAudioManager,
    private val cryptoManager: CryptoManager
) {
    // State
    val transmissionState: StateFlow<TransmissionState>
    val currentChannel: StateFlow<PTTChannel?>
    val floorHolder: StateFlow<String?>  // Peer ID holding floor

    // Channel operations
    suspend fun joinChannel(channel: PTTChannel)
    suspend fun leaveChannel()
    fun getCurrentChannel(): PTTChannel?

    // Transmission
    suspend fun startTransmission(
        priority: PTTPriority = PTTPriority.NORMAL,
        isEmergency: Boolean = false
    ): Boolean

    suspend fun stopTransmission()

    // Floor control
    fun requestFloor(): Boolean
    fun releaseFloor()
    fun isFloorAvailable(): Boolean
    fun getFloorHolder(): String?

    // Configuration
    fun setTxPower(level: TxPowerLevel)
    fun setCodecBitrate(bitrate: Int)  // 6000-24000
}

enum class TransmissionState {
    IDLE,
    REQUESTING_FLOOR,
    TRANSMITTING,
    RECEIVING,
    ERROR
}

enum class PTTPriority {
    LOW,
    NORMAL,
    HIGH,
    EMERGENCY
}

data class PTTChannel(
    val id: String,
    val name: String,
    val multicastAddress: String,  // e.g., "239.255.0.1"
    val port: Int = 5004,
    val encryptionKey: ByteArray?,
    val priority: Int = 0
)
```

#### Usage Examples

```kotlin
// Join a talkgroup
val channel = PTTChannel(
    id = "tg-001",
    name = "Alpha Team",
    multicastAddress = "239.255.0.1",
    encryptionKey = groupKey
)
pttManager.joinChannel(channel)

// Start transmission (press PTT)
viewModelScope.launch {
    val acquired = pttManager.startTransmission()
    if (acquired) {
        // Floor acquired, now transmitting
    } else {
        // Floor busy, show indicator
    }
}

// Stop transmission (release PTT)
pttManager.stopTransmission()

// Emergency transmission (overrides normal)
pttManager.startTransmission(
    priority = PTTPriority.EMERGENCY,
    isEmergency = true
)

// Observe state
pttManager.transmissionState.collect { state ->
    when (state) {
        TransmissionState.TRANSMITTING -> showTxIndicator()
        TransmissionState.RECEIVING -> showRxIndicator()
        TransmissionState.IDLE -> hideIndicators()
    }
}
```

#### Floor Control Protocol

```
Floor Request:
┌─────────────────────────────────────────┐
│ Type: FLOOR_REQUEST                     │
│ Sender: device_id                       │
│ Priority: 0-3                           │
│ Timestamp: unix_millis                  │
│ Signature: ed25519_sig                  │
└─────────────────────────────────────────┘

Floor Grant:
┌─────────────────────────────────────────┐
│ Type: FLOOR_GRANT                       │
│ Holder: device_id                       │
│ Expiry: unix_millis + 60000            │
│ Signature: ed25519_sig                  │
└─────────────────────────────────────────┘

Floor Release:
┌─────────────────────────────────────────┐
│ Type: FLOOR_RELEASE                     │
│ Holder: device_id                       │
│ Signature: ed25519_sig                  │
└─────────────────────────────────────────┘
```

---

### MulticastAudioManager

**Location:** `core/audio/MulticastAudioManager.kt`

Manages multicast audio transmission and reception.

#### Class Definition

```kotlin
@Singleton
class MulticastAudioManager @Inject constructor(
    private val codecManager: OpusCodecManager,
    private val jitterBuffer: AdaptiveJitterBuffer,
    private val rtpManager: RTPPacketManager
) {
    // State
    val isTransmitting: StateFlow<Boolean>
    val isReceiving: StateFlow<Boolean>
    val audioLevel: StateFlow<Float>  // 0.0-1.0

    // Lifecycle
    fun start()
    fun stop()

    // Transmission
    fun startTransmit(multicastGroup: String, port: Int)
    fun stopTransmit()

    // Reception
    fun startReceive(multicastGroup: String, port: Int)
    fun stopReceive()

    // Configuration
    fun setMicrophoneGain(gain: Float)
    fun setSpeakerVolume(volume: Float)
    fun enableAEC(enabled: Boolean)
    fun enableNoiseSuppression(enabled: Boolean)
}
```

#### Usage Examples

```kotlin
// Start the audio manager
audioManager.start()

// Join multicast group for receiving
audioManager.startReceive("239.255.0.1", 5004)

// Start transmitting
audioManager.startTransmit("239.255.0.1", 5004)

// Monitor audio levels
audioManager.audioLevel.collect { level ->
    updateVuMeter(level)
}

// Stop everything
audioManager.stop()
```

---

### RadioApiClient

**Location:** `core/radio/RadioApiClient.kt`

JSON-RPC/UBUS client for MeshRider radio control.

#### Class Definition

```kotlin
@Singleton
class RadioApiClient @Inject constructor() {
    companion object {
        const val DEFAULT_USERNAME = "root"
        const val DEFAULT_PASSWORD = "doodle"
        const val DEFAULT_PORT = 80
    }

    // Connection state
    val connectionState: StateFlow<ConnectionState>

    // Connection management
    suspend fun connect(
        host: String,
        username: String = DEFAULT_USERNAME,
        password: String = DEFAULT_PASSWORD
    ): Boolean

    fun disconnect()
    fun isConnected(): Boolean

    // Wireless status
    suspend fun getWirelessStatus(iface: String = "wlan0"): WirelessStatus?
    suspend fun getAssociatedStations(iface: String = "wlan0"): List<AssociatedStation>
    suspend fun getAvailableChannels(iface: String = "wlan0"): List<ChannelInfo>

    // Configuration
    suspend fun switchChannel(channel: Int, bandwidth: Int = 20): Boolean
    suspend fun setTxPower(power: Int): Boolean

    // GPS
    suspend fun getGpsLocation(): GpsLocation?

    // System
    suspend fun getSystemInfo(): SystemInfo?
    suspend fun reboot(): Boolean

    // Low-level UBUS
    suspend fun ubusCall(
        service: String,
        method: String,
        params: JSONObject? = null
    ): JSONObject?

    // UCI configuration
    suspend fun uciGet(config: String, section: String, option: String): String?
    suspend fun uciSet(config: String, section: String, option: String, value: String): Boolean
    suspend fun uciCommit(config: String): Boolean

    // Cleanup
    fun release()
}

sealed class ConnectionState {
    object Disconnected : ConnectionState()
    object Connecting : ConnectionState()
    data class Connected(
        val radioIp: String,
        val radioInfo: RadioInfo?
    ) : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

data class WirelessStatus(
    val ssid: String,
    val channel: Int,
    val frequency: Int,      // MHz
    val bandwidth: Int,      // MHz
    val signal: Int,         // dBm
    val noise: Int,          // dBm
    val snr: Int,            // dB
    val linkQuality: Int,    // 0-100
    val txPower: Int,        // dBm
    val bitrate: Int,        // Mbps
    val mode: String         // "Master", "Client", "Ad-Hoc"
)

data class AssociatedStation(
    val macAddress: String,
    val ipAddress: String?,
    val signal: Int,
    val snr: Int,
    val txBitrate: Int,
    val rxBitrate: Int,
    val inactive: Int        // ms since last activity
)

data class GpsLocation(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val speed: Float,
    val heading: Float,
    val timestamp: Long,
    val isValid: Boolean
)
```

#### Usage Examples

```kotlin
// Connect to radio
val success = radioApiClient.connect("10.223.232.141")
if (success) {
    // Get wireless status
    val status = radioApiClient.getWirelessStatus()
    println("SSID: ${status?.ssid}, Signal: ${status?.signal} dBm")

    // Get mesh peers
    val stations = radioApiClient.getAssociatedStations()
    stations.forEach { sta ->
        println("Peer: ${sta.macAddress}, Signal: ${sta.signal} dBm")
    }

    // Switch channel (mesh-wide)
    radioApiClient.switchChannel(channel = 149, bandwidth = 20)

    // Get GPS from radio
    val gps = radioApiClient.getGpsLocation()
    if (gps?.isValid == true) {
        println("Location: ${gps.latitude}, ${gps.longitude}")
    }
}

// Observe connection state
radioApiClient.connectionState.collect { state ->
    when (state) {
        is ConnectionState.Connected -> showConnected(state.radioIp)
        is ConnectionState.Error -> showError(state.message)
        ConnectionState.Connecting -> showConnecting()
        ConnectionState.Disconnected -> showDisconnected()
    }
}
```

#### UBUS Methods Reference

| Service | Method | Description |
|---------|--------|-------------|
| `iwinfo` | `info` | Wireless interface status |
| `iwinfo` | `assoclist` | Associated stations |
| `iwinfo` | `freqlist` | Available frequencies |
| `wireless` | `status` | Radio status |
| `system` | `info` | System information |
| `system` | `reboot` | Reboot radio |
| `file` | `exec` | Execute command |
| `file` | `read` | Read file |
| `message-system` | `chswitch` | Mesh-wide channel switch |

---

### RadioDiscoveryService

**Location:** `core/radio/RadioDiscoveryService.kt`

Discovers MeshRider radios on the local network via UDP broadcast.

#### Class Definition

```kotlin
@Singleton
class RadioDiscoveryService @Inject constructor() {
    companion object {
        const val DISCOVERY_PORT = 11111
        val BROADCAST_ADDRESSES = listOf(
            "10.223.255.255",
            "192.168.20.255",
            "10.0.0.255"
        )
    }

    // Discovered radios
    val discoveredRadios: StateFlow<List<DiscoveredRadio>>

    // Lifecycle
    fun start(autoRefresh: Boolean = true, refreshInterval: Long = 30000)
    fun stop()

    // Manual operations
    suspend fun refresh()
    fun addManualRadio(ipAddress: String, hostname: String = "")
    fun removeRadio(ipAddress: String)
    fun clearAll()
}

data class DiscoveredRadio(
    val ipAddress: String,
    val hostname: String,
    val model: String,
    val firmwareVersion: String,
    val displayName: String,
    val lastSeen: Long,
    val isOnline: Boolean
)
```

#### Usage Examples

```kotlin
// Start discovery
radioDiscovery.start(autoRefresh = true, refreshInterval = 30000)

// Observe discovered radios
radioDiscovery.discoveredRadios.collect { radios ->
    radios.forEach { radio ->
        println("Found: ${radio.displayName} at ${radio.ipAddress}")
    }
}

// Manual refresh
radioDiscovery.refresh()

// Add radio manually (if discovery fails)
radioDiscovery.addManualRadio("10.223.232.141", "Base Station")

// Stop discovery
radioDiscovery.stop()
```

#### Discovery Protocol

```
Request (broadcast to 255.255.255.255:11111):
┌─────────────────────────────────────────┐
│ "Hello"                                 │
└─────────────────────────────────────────┘

Response (from radio):
┌─────────────────────────────────────────┐
│ {                                       │
│   "hostname": "MeshRider-XXXX",         │
│   "model": "2450-8",                    │
│   "firmware": "2.4.0",                  │
│   "ip": "10.223.232.141"               │
│ }                                       │
└─────────────────────────────────────────┘
```

---

## Network Modules

### MeshNetworkManager

**Location:** `core/network/MeshNetworkManager.kt`

Manages P2P signaling and connection lifecycle.

#### Class Definition

```kotlin
@Singleton
class MeshNetworkManager @Inject constructor(
    private val cryptoManager: CryptoManager,
    private val connector: Connector,
    private val peerDiscovery: PeerDiscoveryManager
) {
    // State
    val connectionStatus: StateFlow<ConnectionStatus>
    val peers: StateFlow<List<Peer>>
    val incomingCalls: SharedFlow<IncomingCall>

    // Lifecycle
    fun start()
    fun stop()

    // Call operations
    suspend fun initiateCall(contact: Contact, sdpOffer: String): CallResult
    suspend fun answerCall(incomingCall: IncomingCall, sdpAnswer: String)
    suspend fun declineCall(incomingCall: IncomingCall)
    suspend fun hangup()

    // Messaging
    suspend fun sendMessage(contact: Contact, message: ByteArray): Boolean

    // Peer operations
    fun getPeer(publicKey: ByteArray): Peer?
    fun updatePeerAddress(publicKey: ByteArray, address: String)
}

enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

data class Peer(
    val publicKey: ByteArray,
    val name: String,
    val address: String?,
    val isOnline: Boolean,
    val lastSeen: Long
)

data class IncomingCall(
    val contact: Contact,
    val sdpOffer: String,
    val socket: Socket
)

sealed class CallResult {
    data class Connected(val sdpAnswer: String) : CallResult()
    object Declined : CallResult()
    data class Error(val message: String) : CallResult()
}
```

---

### MeshService

**Location:** `core/network/MeshService.kt`

Android foreground service for persistent operation.

#### Class Definition

```kotlin
class MeshService : Service() {
    companion object {
        const val ACTION_START = "com.doodlelabs.meshriderwave.START"
        const val ACTION_STOP = "com.doodlelabs.meshriderwave.STOP"
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "mesh_service"
    }

    // Bound service interface
    inner class LocalBinder : Binder() {
        fun getService(): MeshService = this@MeshService
    }

    // Service lifecycle
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int
    override fun onBind(intent: Intent): IBinder
    override fun onDestroy()

    // Injected components (via Hilt)
    @Inject lateinit var meshNetworkManager: MeshNetworkManager
    @Inject lateinit var pttManager: PTTManager
    @Inject lateinit var locationManager: LocationSharingManager
}
```

#### Usage Examples

```kotlin
// Start service
val intent = Intent(context, MeshService::class.java).apply {
    action = MeshService.ACTION_START
}
context.startForegroundService(intent)

// Stop service
val intent = Intent(context, MeshService::class.java).apply {
    action = MeshService.ACTION_STOP
}
context.startService(intent)

// Bind to service
val connection = object : ServiceConnection {
    override fun onServiceConnected(name: ComponentName, binder: IBinder) {
        val service = (binder as MeshService.LocalBinder).getService()
        // Use service
    }
    override fun onServiceDisconnected(name: ComponentName) {}
}
context.bindService(
    Intent(context, MeshService::class.java),
    connection,
    Context.BIND_AUTO_CREATE
)
```

---

### Connector

**Location:** `core/network/Connector.kt`

Low-level socket connection with retry logic.

#### Class Definition

```kotlin
class Connector {
    var connectTimeout: Int = 5000       // 5 seconds
    var connectRetries: Int = 3          // 3 attempts
    var guessEUI64Address: Boolean = true
    var useNeighborTable: Boolean = false

    suspend fun connect(contact: Contact): Socket?
    suspend fun connectToAddress(address: String): Socket?
    fun getLastError(): String?
}
```

---

### PeerDiscoveryManager

**Location:** `core/network/PeerDiscoveryManager.kt`

Discovers peers via mDNS (NSD).

#### Class Definition

```kotlin
@Singleton
class PeerDiscoveryManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val discoveredPeers: StateFlow<List<DiscoveredPeer>>

    fun startDiscovery()
    fun stopDiscovery()
    fun registerService(name: String, port: Int, publicKey: ByteArray)
    fun unregisterService()
}

data class DiscoveredPeer(
    val name: String,
    val address: String,
    val port: Int,
    val publicKey: ByteArray?
)
```

---

## Audio Modules

### OpusCodecManager

**Location:** `core/audio/OpusCodecManager.kt`

Manages Opus audio encoding and decoding.

#### Class Definition

```kotlin
@Singleton
class OpusCodecManager @Inject constructor() {
    companion object {
        const val SAMPLE_RATE = 16000        // 16 kHz
        const val CHANNELS = 1                // Mono
        const val FRAME_SIZE_MS = 20          // 20 ms frames
        const val FRAME_SIZE = 320            // samples per frame
        const val MIN_BITRATE = 6000          // 6 kbps
        const val MAX_BITRATE = 24000         // 24 kbps
        const val DEFAULT_BITRATE = 16000     // 16 kbps
    }

    // Lifecycle
    fun initialize(): Boolean
    fun release()

    // Encoding
    fun encode(pcmData: ShortArray): ByteArray?
    fun encode(pcmData: ByteArray): ByteArray?

    // Decoding
    fun decode(opusData: ByteArray): ShortArray?
    fun decodePLC(): ShortArray?  // Packet Loss Concealment

    // Configuration
    fun setBitrate(bitrate: Int)
    fun getBitrate(): Int
    fun setComplexity(complexity: Int)  // 0-10

    // Statistics
    fun getEncodedBytes(): Long
    fun getDecodedBytes(): Long
    fun getPacketLossRate(): Float
}
```

#### Usage Examples

```kotlin
// Initialize codec
codecManager.initialize()
codecManager.setBitrate(16000)  // 16 kbps

// Encode audio frame (20ms of 16kHz mono PCM)
val pcmFrame = ShortArray(320)  // Filled from AudioRecord
val opusFrame = codecManager.encode(pcmFrame)

// Decode received frame
val decodedPcm = codecManager.decode(opusFrame!!)

// Handle packet loss
if (packetLost) {
    val plcFrame = codecManager.decodePLC()
    // Play PLC frame to mask loss
}

// Cleanup
codecManager.release()
```

---

### AdaptiveJitterBuffer

**Location:** `core/audio/AdaptiveJitterBuffer.kt`

RFC 3550 compliant jitter buffer with adaptive depth.

#### Class Definition

```kotlin
class AdaptiveJitterBuffer(
    private val frameTimeMs: Int = 20,
    private val minDepthMs: Int = 20,
    private val maxDepthMs: Int = 100,
    private val onPacketLoss: ((count: Int, lastSeq: Int) -> Unit)? = null
) {
    // Packet operations
    fun put(packet: RTPPacket)
    fun poll(): RTPPacket?
    fun peek(): RTPPacket?

    // State
    fun getDepth(): Int           // Current buffer depth in ms
    fun getJitter(): Float        // Current jitter estimate in ms
    fun getPacketCount(): Int
    fun isEmpty(): Boolean

    // Statistics
    fun getPacketsReceived(): Long
    fun getPacketsDropped(): Long
    fun getPacketsLost(): Long
    fun getReorderCount(): Long

    // Control
    fun clear()
    fun reset()
}

data class RTPPacket(
    val sequenceNumber: Int,
    val timestamp: Long,
    val ssrc: Long,
    val payload: ByteArray,
    val arrivalTime: Long = System.currentTimeMillis()
)

// Factory for common configurations
object JitterBufferFactory {
    fun createForPTT(
        frameTimeMs: Int = 20,
        onPacketLoss: ((Int, Int) -> Unit)? = null
    ): AdaptiveJitterBuffer

    fun createForVoIP(
        frameTimeMs: Int = 20,
        onPacketLoss: ((Int, Int) -> Unit)? = null
    ): AdaptiveJitterBuffer
}
```

#### Usage Examples

```kotlin
// Create jitter buffer with PLC callback
val jitterBuffer = JitterBufferFactory.createForPTT(
    frameTimeMs = 20,
    onPacketLoss = { count, lastSeq ->
        // Generate PLC frames
        repeat(count) {
            val plcFrame = codecManager.decodePLC()
            audioTrack.write(plcFrame, 0, plcFrame.size)
        }
    }
)

// Receiver thread
while (receiving) {
    val rtpPacket = receiveFromNetwork()
    jitterBuffer.put(rtpPacket)
}

// Playback thread
while (playing) {
    val packet = jitterBuffer.poll()
    if (packet != null) {
        val pcm = codecManager.decode(packet.payload)
        audioTrack.write(pcm, 0, pcm.size)
    } else {
        // Buffer underrun, wait
        Thread.sleep(5)
    }
}

// Monitor statistics
println("Jitter: ${jitterBuffer.getJitter()} ms")
println("Depth: ${jitterBuffer.getDepth()} ms")
println("Lost: ${jitterBuffer.getPacketsLost()}")
```

---

### RTPPacketManager

**Location:** `core/audio/RTPPacketManager.kt`

RTP packetization per RFC 3550 with DSCP QoS.

#### Class Definition

```kotlin
class RTPPacketManager(
    private val ssrc: Long = Random.nextLong()
) {
    companion object {
        const val DSCP_EF = 46           // Expedited Forwarding
        const val TOS_EF = 0xB8          // TOS byte for DSCP EF
        const val PAYLOAD_TYPE_OPUS = 111
        const val RTP_HEADER_SIZE = 12
    }

    // Packetization
    fun createPacket(payload: ByteArray, timestamp: Long): ByteArray
    fun parsePacket(data: ByteArray): RTPPacket?

    // Sequence management
    fun getSequenceNumber(): Int
    fun resetSequence()

    // Socket configuration
    fun configureSocket(socket: DatagramSocket)
}
```

#### RTP Packet Format

```
 0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|V=2|P|X|  CC   |M|     PT      |       Sequence Number         |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                           Timestamp                           |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                             SSRC                              |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                         Opus Payload                          |
|                             ...                               |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```

---

## Location & Safety

### LocationSharingManager

**Location:** `core/location/LocationSharingManager.kt`

Blue Force Tracking with GPS location sharing.

#### Class Definition

```kotlin
@Singleton
class LocationSharingManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // State
    val currentLocation: StateFlow<Location?>
    val peerLocations: StateFlow<Map<String, PeerLocation>>
    val isSharing: StateFlow<Boolean>

    // Lifecycle
    fun startSharing(intervalMs: Long = 5000)
    fun stopSharing()

    // Manual location update
    fun updateLocation(location: Location)

    // Peer location management
    fun updatePeerLocation(peerId: String, location: PeerLocation)
    fun removePeer(peerId: String)

    // Configuration
    fun setShareInterval(intervalMs: Long)
    fun setAccuracyFilter(minAccuracyMeters: Float)
}

data class PeerLocation(
    val peerId: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double?,
    val heading: Float?,
    val speed: Float?,
    val accuracy: Float?,
    val timestamp: Long,
    val isStale: Boolean
)
```

---

### SOSManager

**Location:** `core/sos/SOSManager.kt`

Emergency SOS system with geofencing.

#### Class Definition

```kotlin
@Singleton
class SOSManager @Inject constructor(
    private val locationManager: LocationSharingManager,
    private val meshNetworkManager: MeshNetworkManager
) {
    // State
    val isSOSActive: StateFlow<Boolean>
    val sosEvents: StateFlow<List<SOSEvent>>

    // SOS operations
    suspend fun activateSOS(message: String = "EMERGENCY"): Boolean
    suspend fun deactivateSOS()
    suspend fun acknowledgeSOSEvent(eventId: String)

    // Geofencing
    fun addGeofence(geofence: Geofence)
    fun removeGeofence(geofenceId: String)
    fun checkGeofences(location: Location): List<GeofenceEvent>

    // Configuration
    fun setAutoDeactivateTimeout(timeoutMs: Long)
}

data class SOSEvent(
    val id: String,
    val senderId: String,
    val senderName: String,
    val latitude: Double,
    val longitude: Double,
    val message: String,
    val timestamp: Long,
    val isAcknowledged: Boolean
)

data class Geofence(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float,
    val alertOnExit: Boolean = true,
    val alertOnEnter: Boolean = false
)
```

---

## ATAK Integration

### ATAKBridge

**Location:** `core/atak/ATAKBridge.kt`

Intent-based bridge between MR Wave and ATAK.

#### Class Definition

```kotlin
@Singleton
class ATAKBridge @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val ACTION_PTT_START = "com.doodlelabs.meshriderwave.PTT_START"
        const val ACTION_PTT_STOP = "com.doodlelabs.meshriderwave.PTT_STOP"
        const val ACTION_CHANNEL_CHANGE = "com.doodlelabs.meshriderwave.CHANNEL_CHANGE"
        const val ACTION_COT_RECEIVED = "com.doodlelabs.meshriderwave.COT_RECEIVED"
    }

    // Registration
    fun register()
    fun unregister()

    // Send to ATAK
    fun sendPTTStatus(isTransmitting: Boolean)
    fun sendChannelInfo(channel: PTTChannel)
    fun sendLocation(location: Location)

    // Callbacks
    var onPTTRequested: ((Boolean) -> Unit)?
    var onChannelChangeRequested: ((String) -> Unit)?
}
```

---

### CoTManager

**Location:** `core/atak/CoTManager.kt`

Cursor-on-Target (CoT) multicast messaging.

#### Class Definition

```kotlin
@Singleton
class CoTManager @Inject constructor() {
    companion object {
        const val SA_MULTICAST_ADDRESS = "239.2.3.1"
        const val SA_PORT = 6969
        const val MR_PORT = 6970
    }

    // Lifecycle
    fun start()
    fun stop()

    // Sending
    fun sendSA(location: Location, callsign: String)
    fun sendCoT(message: CoTMessage)

    // Receiving
    val incomingCoT: SharedFlow<CoTMessage>

    // Configuration
    fun setCallsign(callsign: String)
    fun setTeam(team: String)  // "Cyan", "Green", etc.
    fun setRole(role: String)  // "Team Lead", "Sniper", etc.
}

data class CoTMessage(
    val uid: String,
    val type: String,          // "a-f-G-U-C" (friendly ground unit combat)
    val latitude: Double,
    val longitude: Double,
    val altitude: Double?,
    val callsign: String,
    val team: String?,
    val role: String?,
    val timestamp: Long,
    val staleTime: Long
) {
    fun toXml(): String

    companion object {
        fun fromXml(xml: String): CoTMessage?
    }
}
```

#### CoT XML Format

```xml
<?xml version="1.0" encoding="UTF-8"?>
<event version="2.0"
       uid="MRWave-DEVICE-ID"
       type="a-f-G-U-C"
       time="2026-01-17T12:00:00Z"
       start="2026-01-17T12:00:00Z"
       stale="2026-01-17T12:05:00Z"
       how="h-g-i-g-o">
    <point lat="37.7749" lon="-122.4194" hae="10" ce="10" le="10"/>
    <detail>
        <contact callsign="Alpha-1"/>
        <__group name="Cyan" role="Team Lead"/>
        <track course="45.0" speed="0.0"/>
    </detail>
</event>
```

---

## Data Models

### Contact

```kotlin
data class Contact(
    val publicKey: ByteArray,
    val name: String,
    val addresses: List<String> = emptyList(),
    val blocked: Boolean = false,
    val createdAt: Long,
    val lastSeenAt: Long? = null,
    val lastWorkingAddress: String? = null
) {
    val deviceId: String
        get() = publicKey.take(8).joinToString("") { "%02x".format(it) }

    val shortId: String
        get() = publicKey.take(4).joinToString("") { "%02X".format(it) }

    fun toQrData(): String

    companion object {
        fun fromQrData(data: String): Contact?
    }
}
```

### CallState

```kotlin
data class CallState(
    val status: Status,
    val direction: Direction,
    val type: Type,
    val contact: Contact?,
    val isMicEnabled: Boolean = true,
    val isCameraEnabled: Boolean = false,
    val isSpeakerEnabled: Boolean = false,
    val isFrontCamera: Boolean = true,
    val startTime: Long? = null,
    val errorMessage: String? = null
) {
    enum class Status {
        IDLE, INITIATING, RINGING, CONNECTING, CONNECTED, ENDED, ERROR
    }

    enum class Direction { INCOMING, OUTGOING }
    enum class Type { VOICE, VIDEO }

    val isActive: Boolean
    val isConnected: Boolean
    val callDuration: Long
    val durationFormatted: String
}
```

---

## ViewModels

### RadioStatusViewModel

**Location:** `presentation/viewmodel/RadioStatusViewModel.kt`

```kotlin
@HiltViewModel
class RadioStatusViewModel @Inject constructor(
    private val radioApiClient: RadioApiClient,
    private val radioDiscovery: RadioDiscoveryService
) : ViewModel() {

    val uiState: StateFlow<RadioStatusUiState>

    fun connectToRadio(ipAddress: String, username: String, password: String)
    fun disconnectFromRadio()
    fun addManualRadio(ipAddress: String, hostname: String)
    fun refreshStatus()
    fun refreshDiscovery()
}

data class RadioStatusUiState(
    val isConnected: Boolean = false,
    val connectedRadioIp: String? = null,
    val ssid: String = "",
    val channel: Int = 0,
    val signal: Int = -100,
    val noise: Int = -95,
    val snr: Int = 0,
    val linkQuality: Int = 0,
    val associatedStations: List<StationInfo> = emptyList(),
    val discoveredRadios: List<DiscoveredRadioInfo> = emptyList(),
    val isRefreshing: Boolean = false,
    val error: String? = null
)
```

---

## Error Handling

### Standard Error Types

```kotlin
sealed class MeshError : Exception() {
    data class NetworkError(override val message: String) : MeshError()
    data class CryptoError(override val message: String) : MeshError()
    data class AudioError(override val message: String) : MeshError()
    data class RadioError(override val message: String) : MeshError()
    data class TimeoutError(override val message: String) : MeshError()
}
```

### Result Pattern

```kotlin
sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val error: MeshError) : Result<Nothing>()

    inline fun <R> map(transform: (T) -> R): Result<R>
    inline fun onSuccess(action: (T) -> Unit): Result<T>
    inline fun onError(action: (MeshError) -> Unit): Result<T>
}
```

---

## Thread Safety

### Dispatcher Qualifiers

```kotlin
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MainDispatcher
```

### Coroutine Scopes

```kotlin
// ViewModel scope (auto-cancelled)
viewModelScope.launch { ... }

// Application scope (long-running)
@ApplicationScope
class AppScopeProvider @Inject constructor() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
```

---

**Document Control:**

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | Jan 2026 | Jabbir Basha | Initial release |
| 2.0 | Jan 2026 | Claude Code | Complete rewrite |

---

*Copyright (C) 2024-2026 DoodleLabs Singapore Pte Ltd. All Rights Reserved.*
