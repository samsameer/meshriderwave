# Mesh Rider Wave - Advanced Feature Roadmap 2026

**Based on Latest Research: arXiv, IEEE, IETF, NIST, Google, and Industry Leaders**
**Compiled: January 2026**

---

## Executive Summary

This document outlines cutting-edge features for Mesh Rider Wave Android based on the latest academic research, industry standards, and technology trends from 2025-2026.

---

## 1. Group Calling Architecture (SFU-Hybrid)

### Research Source
- [WebRTC SFU Architecture Guide](https://bloggeek.me/webrtcglossary/sfu/)
- [P2P-MCU Approach](https://www.researchgate.net/publication/283775278_A_P2P-MCU_Approach_to_Multi-Party_Video_Conference_with_WebRTC)
- [arXiv: Decentralized WebRTC P2P using Kademlia](https://arxiv.org/abs/2206.07685)

### Implementation Plan

```kotlin
/**
 * Hybrid P2P-SFU Architecture for Mesh Networks
 *
 * Strategy:
 * - 2-4 participants: Pure P2P mesh (current implementation)
 * - 5-20 participants: Distributed SFU on most capable device
 * - 20+ participants: Multi-SFU cascade
 */
sealed class CallTopology {
    data class P2PMesh(val participants: List<Participant>) : CallTopology()
    data class DistributedSFU(val sfuNode: Participant, val clients: List<Participant>) : CallTopology()
    data class MultiSFU(val sfuNodes: List<SFUCluster>) : CallTopology()
}

class TopologyManager {
    fun selectOptimalTopology(participants: Int, networkConditions: NetworkMetrics): CallTopology
    fun electSFUNode(candidates: List<Participant>): Participant
    fun handleNodeFailover(failedNode: Participant)
}
```

### Features
- **Automatic topology switching** based on participant count
- **SFU election algorithm** - selects device with best CPU/battery/bandwidth
- **Simulcast support** - multiple quality streams for adaptive bandwidth
- **Active speaker detection** - prioritize video for current speaker

---

## 2. MLS Protocol (Group E2E Encryption)

### Research Source
- [IETF RFC 9420: MLS Protocol](https://datatracker.ietf.org/doc/html/rfc9420)
- [EUROCRYPT 2025: Analyzing Group Chat Encryption](https://dl.acm.org/doi/10.1007/978-3-031-91101-9_10)
- [MLS Architecture](https://messaginglayersecurity.rocks/)

### Why MLS?
- **Scales to 50,000+ participants** (vs Signal's 1000 limit)
- **Forward secrecy & post-compromise security**
- **IETF standard** - adopted by Discord, RCS, WhatsApp (2025)
- **Designed for groups** (unlike Signal protocol)

### Implementation Plan

```kotlin
/**
 * MLS Group Encryption Manager
 *
 * Uses TreeKEM for efficient key distribution
 * Reference: RFC 9420 (July 2023)
 */
class MLSGroupManager {
    // Group state
    private var epoch: Long = 0
    private var treeState: RatchetTree? = null
    private var members: List<MLSMember> = emptyList()

    // Operations
    suspend fun createGroup(creator: MLSMember): GroupState
    suspend fun addMember(proposal: AddProposal): Commit
    suspend fun removeMember(proposal: RemoveProposal): Commit
    suspend fun updateKeys(): Commit  // Forward secrecy

    // Encryption
    fun encryptMessage(plaintext: ByteArray): MLSCiphertext
    fun decryptMessage(ciphertext: MLSCiphertext): ByteArray
}

data class MLSMember(
    val identityKey: ByteArray,      // Ed25519
    val signatureKey: ByteArray,     // For group signatures
    val keyPackage: KeyPackage       // Pre-shared keys for async join
)
```

### Security Properties
| Property | Description |
|----------|-------------|
| Message Confidentiality | Only group members can decrypt |
| Message Authentication | Sender identity verified |
| Membership Authentication | Only invited users join |
| Forward Secrecy | Past messages safe if keys compromised |
| Post-Compromise Security | Future messages safe after recovery |

---

## 3. Post-Quantum Cryptography (ML-KEM/Kyber)

### Research Source
- [NIST FIPS 203: ML-KEM Standard](https://nvlpubs.nist.gov/nistpubs/fips/nist.fips.203.pdf)
- [Cloudflare: State of PQ Internet 2025](https://blog.cloudflare.com/pq-2025/)
- [Mobile PQC Implementation](https://csrc.nist.gov/CSRC/media/Events/third-pqc-standardization-conference/documents/accepted-papers/ribeiro-evaluating-kyber-pqc2021.pdf)

### Why Post-Quantum Now?
- **"Harvest now, decrypt later"** attacks are real
- **Signal, WhatsApp, Telegram** already implemented (2024-2025)
- **NIST standardized** ML-KEM in August 2024
- **Only 5-10% CPU overhead** on modern mobile devices

### Implementation Plan

```kotlin
/**
 * Hybrid Post-Quantum Key Exchange
 *
 * Combines X25519 (classical) + ML-KEM-768 (quantum-resistant)
 * Following Signal's PQXDH approach
 */
class HybridPQKeyExchange {
    // Classical keys (current)
    private val x25519KeyPair: X25519KeyPair

    // Post-quantum keys (new)
    private val mlKemKeyPair: MLKEMKeyPair  // ML-KEM-768

    /**
     * Hybrid key encapsulation
     * Security: MAX(X25519, ML-KEM)
     */
    fun encapsulate(recipientBundle: HybridPublicBundle): SharedSecret {
        val classicalSecret = x25519.keyExchange(recipientBundle.x25519)
        val pqSecret = mlKem.encapsulate(recipientBundle.mlKem)

        // Combine secrets with HKDF
        return hkdf.derive(
            ikm = classicalSecret + pqSecret.sharedSecret,
            info = "MeshRider-PQXDH-v1"
        )
    }
}

// ML-KEM-768 parameters (NIST recommendation)
object MLKEMParams {
    const val PUBLIC_KEY_SIZE = 1184   // bytes
    const val SECRET_KEY_SIZE = 2400   // bytes
    const val CIPHERTEXT_SIZE = 1088   // bytes
    const val SHARED_SECRET_SIZE = 32  // bytes
    const val SECURITY_LEVEL = 192     // bits (AES-192 equivalent)
}
```

### Library Options
- **Bouncy Castle 1.79+** - Full ML-KEM support
- **liboqs** - NIST reference implementations
- **Signal libsignal-android** - Already has Kyber support

---

## 4. Push-to-Talk (PTT) / Walkie-Talkie Mode

### Research Source
- [Zello PTT Architecture](https://zello.com/)
- [EVO PTT Open Source](https://github.com/Theofilos-Chamalis/EVO-PTT)
- [MirrorFly PTT SDK](https://www.mirrorfly.com/blog/push-to-talk-sdk-for-android-ios-app/)

### Features

```kotlin
/**
 * Push-to-Talk Channel System
 *
 * Half-duplex voice communication with priority handling
 */
class PTTChannel(
    val channelId: String,
    val name: String,
    val members: List<Contact>,
    val priority: ChannelPriority
) {
    // States
    sealed class TransmitState {
        object Idle : TransmitState()
        data class Transmitting(val speaker: Contact) : TransmitState()
        data class Receiving(val speaker: Contact) : TransmitState()
        object Queued : TransmitState()  // Waiting for floor
    }

    // Priority override (emergency)
    enum class ChannelPriority {
        NORMAL,
        HIGH,
        EMERGENCY  // Interrupts all other transmissions
    }
}

class PTTManager {
    // Start transmitting (request floor)
    suspend fun requestFloor(channel: PTTChannel): FloorGrant

    // Release transmission
    fun releaseFloor()

    // Emergency broadcast (highest priority)
    suspend fun emergencyBroadcast(message: AudioStream)

    // Callbacks
    var onTransmissionStart: ((Contact) -> Unit)?
    var onTransmissionEnd: (() -> Unit)?
    var onFloorDenied: ((reason: String) -> Unit)?
}
```

### UI Integration
```kotlin
// Hardware button mapping
class PTTButtonHandler {
    fun mapHardwareButton(keyCode: Int) {
        when (keyCode) {
            KeyEvent.KEYCODE_HEADSETHOOK -> togglePTT()
            KeyEvent.KEYCODE_VOLUME_DOWN -> togglePTT()  // Configurable
        }
    }
}

// Screen-off operation for dedicated PTT devices
class ScreenOffPTTService : Service() {
    // Uses WakeLock for transmission during screen-off
    // Battery optimization exemption required
}
```

---

## 5. Network Service Discovery (mDNS/NSD)

### Research Source
- [Android NSD Documentation](https://developer.android.com/develop/connectivity/wifi/use-nsd)
- [Android mDNS .local Resolution](https://www.esper.io/blog/android-dessert-bites-26-mdns-local-47912385)

### Zero-Config Peer Discovery

```kotlin
/**
 * Automatic peer discovery on local mesh network
 * No internet required - pure local discovery
 */
class MeshPeerDiscovery(context: Context) {
    private val nsdManager = context.getSystemService(NsdManager::class.java)

    companion object {
        const val SERVICE_TYPE = "_meshrider._tcp."
        const val SERVICE_NAME = "MeshRiderWave"
    }

    // Register this device
    fun registerService(port: Int, publicKey: ByteArray) {
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "$SERVICE_NAME-${publicKey.toHex().take(8)}"
            serviceType = SERVICE_TYPE
            setPort(port)
            setAttribute("pk", publicKey.toBase64())
            setAttribute("name", userName)
            setAttribute("version", BuildConfig.VERSION_NAME)
        }
        nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
    }

    // Discover peers
    fun startDiscovery() {
        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }

    // Resolve peer address
    suspend fun resolvePeer(serviceInfo: NsdServiceInfo): Contact {
        return suspendCoroutine { cont ->
            nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                override fun onServiceResolved(resolved: NsdServiceInfo) {
                    val contact = Contact(
                        publicKey = resolved.attributes["pk"]!!.toByteArray(),
                        name = resolved.attributes["name"] ?: "Unknown",
                        addresses = listOf(resolved.host.hostAddress),
                        createdAt = System.currentTimeMillis()
                    )
                    cont.resume(contact)
                }
                override fun onResolveFailed(info: NsdServiceInfo, code: Int) {
                    cont.resumeWithException(ResolutionException(code))
                }
            })
        }
    }
}
```

---

## 6. AR Video Effects with ML Kit

### Research Source
- [CameraX ML Kit Integration](https://developers.google.com/android/reference/com/google/mlkit/vision/camera/CameraXSource)
- [CameraX 1.4.0 Effects Framework](https://android-developers.googleblog.com/2024/12/whats-new-in-camerax-140-and-jetpack-compose-support.html)

### Features

```kotlin
/**
 * Real-time video effects using ML Kit
 */
class VideoEffectsProcessor {
    // Face detection for AR overlays
    private val faceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
            .build()
    )

    // Background segmentation (blur/replace)
    private val segmenter = Segmentation.getClient(
        SelfieSegmenterOptions.Builder()
            .setDetectorMode(SelfieSegmenterOptions.STREAM_MODE)
            .enableRawSizeMask()
            .build()
    )

    // Available effects
    sealed class VideoEffect {
        object None : VideoEffect()
        data class BackgroundBlur(val strength: Float) : VideoEffect()
        data class BackgroundReplace(val image: Bitmap) : VideoEffect()
        data class FaceFilter(val filterType: FilterType) : VideoEffect()
        object LowLight enhancement : VideoEffect()
        object NoiseReduction : VideoEffect()
    }

    // Process frame with effect
    suspend fun processFrame(frame: VideoFrame, effect: VideoEffect): VideoFrame
}
```

---

## 7. Tactical Features (Military-Grade)

### Research Source
- [DTC Sentry 6161](https://www.dtccodan.com/products/dtc-products)
- [Rajant Kinetic Mesh](https://rajant.com/markets/federal-military-civilian-defense/)

### Features

```kotlin
/**
 * Tactical communication features for mesh networks
 */
class TacticalFeatures {

    // 1. GPS-Denied Operation
    class LocationManager {
        fun getLocation(): Location {
            return when {
                gpsAvailable() -> getGPSLocation()
                meshPeersAvailable() -> triangulateFromPeers()
                else -> getLastKnownLocation()
            }
        }
    }

    // 2. SOS/Emergency Button
    class EmergencySystem {
        fun triggerSOS() {
            // Broadcast to all mesh peers
            broadcastEmergency(
                location = getCurrentLocation(),
                audioClip = recordEmergencyAudio(30.seconds),
                priority = Priority.EMERGENCY
            )
            // Vibrate pattern
            vibrator.vibrate(VibrationEffect.createWaveform(SOS_PATTERN, -1))
        }
    }

    // 3. Dead Reckoning (movement without GPS)
    class DeadReckoning {
        private val accelerometer: Sensor
        private val gyroscope: Sensor
        private val magnetometer: Sensor

        fun estimatePosition(lastKnown: Location, duration: Duration): Location
    }

    // 4. Mesh Network Health Monitor
    class MeshHealthMonitor {
        data class NodeHealth(
            val nodeId: String,
            val signalStrength: Int,    // dBm
            val latency: Duration,
            val packetLoss: Float,
            val hopCount: Int,
            val batteryLevel: Float
        )

        fun getNetworkTopology(): MeshTopology
        fun findOptimalRoute(destination: Contact): List<NodeId>
        fun detectNetworkPartition(): List<Partition>
    }

    // 5. Secure Channel Hopping
    class ChannelHopper {
        // Pseudo-random frequency hopping (when hardware supports)
        fun generateHoppingSequence(seed: ByteArray): List<Int>
        fun synchronizeWithPeers(peers: List<Contact>)
    }
}
```

---

## 8. Offline-First Architecture

### Research Source
- [Android Offline-First Patterns](https://developer.android.com/topic/architecture/data-layer/offline-first)

### Implementation

```kotlin
/**
 * Offline-first message queue with sync
 */
class OfflineMessageQueue {
    private val pendingMessages = Room.databaseBuilder(...)
        .build()
        .messageDao()

    sealed class MessageStatus {
        object Queued : MessageStatus()
        object Sending : MessageStatus()
        object Delivered : MessageStatus()
        object Read : MessageStatus()
        data class Failed(val error: String) : MessageStatus()
    }

    // Queue message for delivery
    suspend fun queueMessage(message: Message) {
        pendingMessages.insert(message.copy(status = MessageStatus.Queued))
        attemptDelivery(message)
    }

    // Retry failed messages when network available
    fun observeConnectivity() {
        connectivityManager.registerNetworkCallback(
            NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
                .build(),
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    retryPendingMessages()
                }
            }
        )
    }
}

/**
 * Store-and-forward for mesh networks
 */
class StoreAndForward {
    // Cache messages for offline peers
    fun storeForPeer(peerId: ByteArray, message: EncryptedMessage)

    // Forward when peer comes online
    fun onPeerConnected(peerId: ByteArray) {
        getStoredMessages(peerId).forEach { msg ->
            sendToPeer(peerId, msg)
        }
    }

    // DTN (Delay-Tolerant Networking) mode
    fun enableDTNMode() {
        // Messages can hop through intermediate nodes
        // Even if direct path unavailable
    }
}
```

---

## 9. Advanced Audio Processing

### Features

```kotlin
/**
 * Advanced audio features for harsh RF environments
 */
class AdvancedAudioProcessor {

    // 1. Codec2 for low-bandwidth voice (700 bps - 3200 bps)
    class Codec2Encoder {
        fun encode(pcm: ShortArray, mode: Codec2Mode): ByteArray
        fun decode(encoded: ByteArray): ShortArray

        enum class Codec2Mode(val bitrate: Int) {
            MODE_700C(700),    // Ultra-low bandwidth
            MODE_1200(1200),
            MODE_1600(1600),
            MODE_2400(2400),
            MODE_3200(3200)    // Higher quality
        }
    }

    // 2. RNNoise for real-time noise suppression
    class RNNoiseProcessor {
        fun processFrame(frame: FloatArray): FloatArray
        // Uses neural network for 48kHz noise suppression
    }

    // 3. Automatic Gain Control
    class AGCProcessor {
        var targetLevel: Float = -18f  // dBFS
        fun process(samples: FloatArray): FloatArray
    }

    // 4. Voice Activity Detection (VAD)
    class VADProcessor {
        fun isVoicePresent(frame: FloatArray): Boolean
        // Saves bandwidth by not transmitting silence
    }
}
```

---

## 10. Location Sharing & Blue Force Tracking

### Features

```kotlin
/**
 * Real-time location sharing for team awareness
 */
class BlueForceTracker {

    data class TeamMember(
        val contact: Contact,
        val location: LatLng,
        val heading: Float,
        val speed: Float,
        val altitude: Float,
        val accuracy: Float,
        val timestamp: Long,
        val status: MemberStatus
    )

    enum class MemberStatus {
        ACTIVE,
        IDLE,
        MOVING,
        EMERGENCY,
        OFFLINE
    }

    // Broadcast location to team
    fun startLocationSharing(interval: Duration = 5.seconds)

    // Receive team locations
    val teamLocations: StateFlow<List<TeamMember>>

    // Map integration (offline maps)
    fun renderOnMap(mapView: MapView) {
        teamLocations.collect { members ->
            members.forEach { member ->
                mapView.addMarker(
                    position = member.location,
                    icon = getStatusIcon(member.status),
                    rotation = member.heading
                )
            }
        }
    }

    // Geofencing alerts
    fun createGeofence(center: LatLng, radius: Float, name: String)
    fun onGeofenceEnter(callback: (TeamMember, Geofence) -> Unit)
    fun onGeofenceExit(callback: (TeamMember, Geofence) -> Unit)
}
```

---

## Implementation Priority Matrix

| Feature | Complexity | Impact | Priority | Estimated Effort |
|---------|------------|--------|----------|------------------|
| Group Calling (SFU) | High | Critical | P0 | 4-6 weeks |
| MLS Encryption | High | Critical | P0 | 4-6 weeks |
| PTT/Walkie-Talkie | Medium | High | P1 | 2-3 weeks |
| mDNS Discovery | Low | High | P1 | 1 week |
| Post-Quantum Crypto | Medium | Medium | P2 | 2-3 weeks |
| AR Video Effects | Medium | Medium | P2 | 2-3 weeks |
| Location Sharing | Medium | High | P1 | 2-3 weeks |
| Offline Queue | Low | High | P1 | 1 week |
| Tactical Features | High | High | P1 | 4-6 weeks |
| Codec2 Audio | Medium | Medium | P2 | 2 weeks |

---

## Dependencies to Add

```toml
# libs.versions.toml additions

[versions]
bouncycastle = "1.79"
mlkit-face = "16.1.7"
mlkit-segmentation = "16.0.0-beta5"
codec2 = "1.0.5"
rnnoise = "1.0.0"
maplibre = "11.0.0"

[libraries]
# Post-Quantum Crypto
bouncycastle-pqc = { module = "org.bouncycastle:bcprov-jdk18on", version.ref = "bouncycastle" }

# ML Kit for video effects
mlkit-face-detection = { module = "com.google.mlkit:face-detection", version.ref = "mlkit-face" }
mlkit-segmentation = { module = "com.google.mlkit:segmentation-selfie", version.ref = "mlkit-segmentation" }

# Offline maps
maplibre-android = { module = "org.maplibre.gl:android-sdk", version.ref = "maplibre" }

# Low-bandwidth audio
codec2-android = { module = "com.example:codec2-android", version.ref = "codec2" }
```

---

## References

### arXiv Papers
- [Decentralized WebRTC P2P using Kademlia](https://arxiv.org/abs/2206.07685)
- [fybrrStream: WebRTC P2P Streaming](https://arxiv.org/pdf/2105.07558)
- [Post-Quantum Cryptography Survey](https://arxiv.org/html/2508.16078v1)

### IETF Standards
- [RFC 9420: MLS Protocol](https://datatracker.ietf.org/doc/html/rfc9420)
- [MLS Architecture](https://messaginglayersecurity.rocks/mls-architecture/draft-ietf-mls-architecture.html)

### NIST Publications
- [FIPS 203: ML-KEM](https://nvlpubs.nist.gov/nistpubs/fips/nist.fips.203.pdf)
- [Post-Quantum Standardization 2025](https://postquantum.com/post-quantum/cryptography-pqc-nist/)

### Industry Resources
- [WebRTC Multiparty Architectures](https://bloggeek.me/webrtc-multiparty-architectures/)
- [LiveKit SFU Documentation](https://docs.livekit.io/reference/internals/livekit-sfu/)
- [Android NSD Documentation](https://developer.android.com/develop/connectivity/wifi/use-nsd)
- [CameraX 1.4.0 Release Notes](https://android-developers.googleblog.com/2024/12/whats-new-in-camerax-140-and-jetpack-compose-support.html)

### Military/Tactical
- [DTC Sentry 6161 Announcement](https://www.globenewswire.com/news-release/2025/09/10/3148273/0/en/DTC-Launches-Sentry-6161-Mesh-MANET-Radio.html)
- [Rajant Kinetic Mesh](https://rajant.com/markets/federal-military-civilian-defense/)

---

*Document Version: 1.0*
*Last Updated: January 2026*
*Author: Claude Code (Principal AI Architect)*
