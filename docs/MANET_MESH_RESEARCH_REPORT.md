# Comprehensive MANET & Mesh Networking Research Report
## For DoodleLabs MeshRider Wave Tactical Communications Platform

**Research Date:** February 6, 2026  
**Prepared For:** MeshRider Wave Android Development Team  
**Status:** Technical Analysis & Architecture Recommendations

---

## Executive Summary

This report provides deep technical analysis of Mobile Ad-hoc Network (MANET) protocols, mesh networking technologies, and tactical communication systems relevant to the MeshRider Wave platform. The research covers routing protocols (batman-adv, OLSR, Babel, 802.11s), WiFi Direct/Aware alternatives, hybrid architectures, and emerging technologies for tactical deployments.

### Key Findings
- **batman-adv** is optimal for DoodleLabs' LEDE-based mesh radios due to its layer-2 operation, kernel integration, and proven tactical performance
- **Hybrid phone+radio architecture** enables best-of-both-worlds: phones provide compute/UI, radios provide long-range mesh transport
- **WiFi Direct** viable for phone-to-phone fallback; **WiFi Aware (NAN)** offers service discovery without infrastructure
- **BLE** ideal for control channel and low-power coordination
- **LoRa mesh** (Meshtastic) emerging as off-grid backup for extreme scenarios

---

## 1. MESH ROUTING PROTOCOLS

### 1.1 B.A.T.M.A.N. (Better Approach To Mobile Ad-hoc Networking)

**Overview:**
- Developed by German Freifunk community (2006) to replace OLSR for large-scale mesh deployments
- Decentralized routing - no single node has complete topology knowledge
- Each node only stores "direction" to send data, not full routes

**batman-adv (Layer 2 Version):**
- **Kernel Integration:** Part of Linux kernel since 2.6.38 (March 2011)
- **Operation:** Routes Ethernet frames directly at Data Link layer (Layer 2)
- **Virtual Interface:** Creates `bat0` interface - transparent to upper layers
- **Mesh Interface:** Any standard WiFi interface can join batman-adv mesh

**Protocol Versions:**

| Version | Key Features | Metric |
|---------|-------------|--------|
| B.A.T.M.A.N. III | Multi-interface support, gateway detection | Packet loss |
| B.A.T.M.A.N. IV (batman-adv) | Transmit Quality (TQ) metric, asymmetric link handling | TQ (0-255) |
| B.A.T.M.A.N. V | Echo Location Protocol (ELP), throughput-based metric | Throughput (Mbps) |

**Algorithm:**
1. Originator Messages (OGMs) broadcast periodically (UDP port 1966 in early versions)
2. Each node counts OGMs received and logs neighbor they came from
3. Data packets forwarded hop-by-hop toward destination based on OGM statistics
4. **NO full route calculation** - purely directional forwarding

**Advantages for Tactical Use:**
- ✅ Fast convergence during topology changes
- ✅ Handles asymmetric links (common in tactical RF)
- ✅ Low overhead - doesn't flood full topology
- ✅ Kernel-level performance (not userspace daemon)
- ✅ Multi-interface bonding for redundancy
- ✅ Gateway announcement for internet backhaul

**Current Status:**
- Stable release: batman-adv 2025.4 (October 2025)
- Active development with regular kernel updates
- Battle-tested in Freifunk community networks (1000+ nodes)

---

### 1.2 OLSR (Optimized Link State Routing)

**Overview:**
- Proactive link-state protocol optimized for mobile ad-hoc networks
- RFC 3626 (2003), OLSRv2 RFC 7181 (2014)
- Uses Hello and Topology Control (TC) messages

**Key Innovation: Multipoint Relays (MPRs)**
- Nodes select subset of 1-hop neighbors as MPRs
- Only MPRs forward TC messages - reduces flooding overhead
- MPRs selected to reach all 2-hop neighbors

**Message Types:**
- **Hello:** Link sensing, neighbor detection, MPR selection
- **TC (Topology Control):** Advertise MPR selectors to network
- **HNA (Host/Network Association):** Gateway/internet routes

**Characteristics:**
| Aspect | Value |
|--------|-------|
| Route Availability | Immediate (proactive) |
| Overhead | Higher than reactive protocols |
| Convergence | Fast with MPR optimization |
| Scalability | Good for hundreds of nodes |
| CPU/Memory | Moderate requirements |

**Criticisms:**
- Assumes bi-modal links (working/failed) - doesn't handle intermediate packet loss well
- Proactive nature consumes power even when idle
- Link quality extensions (OLSRd v0.4.8+) added for wireless

**Implementations:**
- OLSRd (Linux/BSD) - most common
- NRL-OLSR (Naval Research Lab)

---

### 1.3 Babel Routing Protocol

**Overview:**
- Distance-vector protocol with loop avoidance (RFC 8966, January 2021)
- Designed for robust operation on both wireless mesh and wired networks
- IETF standard (2021), mandatory for Homenet

**Key Features:**
- **Multiple Metrics:** Hop-count (wired), ETX (Expected Transmission Count) variant (wireless)
- **Radio Diversity:** Can account for multiple radios on same node
- **Latency Metric:** Optional delay-based routing
- **Dual Stack:** Native IPv4 and IPv6 support

**Loop Avoidance:**
- Uses concepts from DSDV, AODV, EIGRP
- Different loop avoidance techniques than traditional distance-vector

**Implementations:**
| Implementation | Platform | Features |
|----------------|----------|----------|
| babeld (reference) | Linux/BSD/Mac | Full feature set |
| BIRD | Linux/BSD | Source-specific routing, auth |
| FRR | Linux | Integrated routing suite |
| sbabeld | Minimal | Stub-only subset |

**Advantages:**
- Fast convergence reported in studies
- Low overhead
- Automatic metric selection
- Standardized and actively maintained

---

### 1.4 IEEE 802.11s (Mesh Networking Standard)

**Overview:**
- IEEE amendment for WLAN mesh networking (ratified 2011)
- Integrated into IEEE 802.11-2012 standard
- Defines architecture for self-configuring multi-hop topologies

**Architecture:**
- **Mesh Station (mesh STA):** Core mesh node
- **Mesh Access Point:** Collocated with AP for non-mesh clients
- **Mesh Portal:** Gateway to non-802.11 networks (internet)
- **Mesh Peering:** Direct logical link between mesh STAs

**Hybrid Wireless Mesh Protocol (HWMP):**
- **Mandatory** default routing protocol for 802.11s
- Combines:
  - **Proactive:** Tree-based routing (rooted at portal)
  - **Reactive:** On-demand path discovery (AODV-like)
- **Layer 2 operation:** Uses MAC addresses, not IP

**HWMP Operation:**
```
Proactive Mode:
- Portal broadcasts path announcements
- Nodes maintain path to portal (tree structure)
- Best for gateway traffic

Reactive Mode:
- RREQ broadcast when path needed
- RREP unicast back to source
- Best for peer-to-peer traffic
```

**Security: SAE (Simultaneous Authentication of Equals)**
- Password-based authentication using Diffie-Hellman
- Elliptic curve or finite cyclic groups
- No distinction between authenticator/supplicant
- AMPE (Authenticated Mesh Peering Exchange) follows SAE

**Implementations:**
- **open80211s:** Linux kernel reference (since 2.6.26)
- **FreeBSD:** Since FreeBSD 8.0
- **Commercial:** Google Wifi, MeshPoint.One

**Limitations:**
- Smaller meshes (<32 nodes) in open80211s reference
- Limited vendor adoption compared to proprietary solutions

---

### 1.5 HWMP (Hybrid Wireless Mesh Protocol)

**Details:**
- Part of 802.11s standard
- Default mandatory mesh routing protocol
- Based on AODV + tree-based routing

**Frame Format:**
- Uses Action frames (category: Mesh)
- Path Request/Reply/Error elements
- MAC-address based (Layer 2)

**Metrics:**
- Airtime Link Metric (default)
- Can use other link metrics via vendor extensions

---

### 1.6 AODV (Ad hoc On-Demand Distance Vector)

**Overview:**
- Reactive (on-demand) routing protocol
- RFC 3561 (2003), won SIGMOBILE Test of Time Award (2018)
- Used in Zigbee networks

**Mechanism:**
1. Route Request (RREQ) flooded when route needed
2. Intermediate nodes forward, update reverse path
3. Destination (or intermediate with fresh route) unicasts RREP
4. Route Error (RERR) sent when link breaks

**Features:**
- Sequence numbers prevent loops
- Expanding ring search (TTL controlled)
- Local repair on link failure
- Hello messages for local connectivity

**Advantages:**
- No overhead when no traffic
- Scales to large networks
- Simple implementation

**Disadvantages:**
- Route discovery latency
- Flooding overhead during discovery
- Not optimal for highly mobile scenarios

---

### 1.7 DSR (Dynamic Source Routing)

**Overview:**
- Reactive protocol using source routing
- RFC 4728 (2007)

**Key Difference from AODV:**
- **Full path in packet header** (source routing)
- No routing tables at intermediate nodes
- Route cache at each node

**Route Discovery:**
- RREQ accumulates node addresses
- RREP contains full path
- Route cache used to reduce discovery

**Advantages:**
- No periodic beacons (hello packets)
- Multiple routes cached
- Loop-free (source route explicit)

**Disadvantages:**
- Header overhead grows with path length
- Performance degrades with high mobility
- Stale cache entries can cause issues

---

### Protocol Comparison Summary

| Protocol | Type | Layer | Proactive | Best For |
|----------|------|-------|-----------|----------|
| **batman-adv** | Distance-vector | L2 | Yes | Large meshes, asymmetric links |
| **OLSR** | Link-state | L3 | Yes | Medium meshes, fast routing |
| **Babel** | Distance-vector | L3 | Yes | Mixed wired/wireless, dual-stack |
| **802.11s/HWMP** | Hybrid | L2 | Optional | WiFi-native mesh, AP integration |
| **AODV** | Distance-vector | L3 | No | Large scale, intermittent traffic |
| **DSR** | Source routing | L3 | No | Small-medium, route caching helps |

---

## 2. MANET CHARACTERISTICS

### 2.1 Core Properties

**Self-Forming:**
- No pre-existing infrastructure required
- Nodes discover neighbors automatically
- Network forms "on-the-fly"

**Self-Healing:**
- Automatic route repair when links fail
- Alternative paths discovered without intervention
- Graceful degradation under node failure

**Multi-Hop Routing:**
- Packets traverse multiple intermediate nodes
- Extends range beyond single-hop transmission
- Each node acts as router

**Dynamic Topology:**
- Links form/break as nodes move
- Routing must adapt continuously
- Convergence time critical for performance

### 2.2 Tactical Network Constraints

**Limited Bandwidth:**
- Shared wireless medium
- Spectrum scarcity in tactical bands
- Contention among multiple flows

**Power Constraints:**
- Battery-operated devices
- Trade-off: transmission power vs. battery life
- Sleep/wake cycles affect connectivity

**Intermittent Connectivity:**
- Terrain blocking (urban, forest, hills)
- Jamming and interference
- Node mobility causing frequent disconnections

**Security Requirements:**
- Authentication of nodes
- Encryption of traffic
- Anti-jamming capabilities
- Low probability of detection (LPD)

### 2.3 MANET Topologies

```
Full Mesh:              Partial Mesh:           Star/Hierarchical:
    A---B                   A---B                   A
   /|\ /|\                 /|                    / | \
  C-D-E-F                 C---D                 B--C--D
   \| |/                  |   |                  \ | /
    G-H                   E---F                   E
```

**Tactical Preference:** Partial mesh with hierarchical clustering
- Balances redundancy with overhead
- Supports command structure
- Enables gateway consolidation

---

## 3. WiFi DIRECT & WiFi AWARE

### 3.1 WiFi Direct (P2P)

**Overview:**
- WiFi standard for direct device-to-device connections
- Specified 2009, certified 2010
- No access point required

**Architecture:**
- **Soft AP:** Each device embeds software access point
- **P2P Group Owner (GO):** Acts as AP for group
- **P2P Client:** Connects to GO
- GO negotiated during connection setup

**Discovery:**
- Device and Service Discovery protocols
- WiFi Protected Setup (WPS) for pairing
- PIN, push-button, or NFC pairing

**Android API:**
```kotlin
// Key classes for WiFi Direct
WifiP2pManager          // Main API
WifiP2pConfig            // Connection configuration
WifiP2pDevice            // Discovered peer
WifiP2pInfo              // Connection info
WifiP2pGroup             // Group information

// Discovery
wifiP2pManager.discoverPeers(channel, listener)

// Connect
val config = WifiP2pConfig().apply {
    deviceAddress = device.deviceAddress
    wps.setup = WpsInfo.PBC  // Push button
}
wifiP2pManager.connect(channel, config, listener)
```

**Characteristics:**
| Parameter | Value |
|-----------|-------|
| Range | ~200m (device dependent) |
| Speed | Up to 250 Mbps (802.11n) |
| Power | Higher than BLE |
| Topology | Single-hop (not mesh) |
| Simultaneous | One GO, multiple clients |

**Limitations for Tactical Use:**
- Single-hop only (no multi-hop relay)
- Group reformation when GO leaves
- Not designed for mobility
- Android API limitations (no raw 802.11 access)

### 3.2 WiFi Aware (NAN - Neighbor Awareness Networking)

**Overview:**
- WiFi Alliance certification (2015)
- Enables service discovery without infrastructure
- Background discovery with low power

**Operation:**
- **Discovery:** Small periodic messages on social channels
- **Synchronization:** Devices synchronize wake times
- **Ranging:** Optional distance measurement
- **Data path:** Direct connection established after discovery

**Key Features:**
- Works without AP or internet
- Low power background operation
- Service-level discovery (not just device)
- Supports publish/subscribe model

**Android API (API 26+):**
```kotlin
// WiFi Aware classes
WifiAwareManager         // Main API
WifiAwareSession         // Active session
PublishConfig            // Service advertisement
SubscribeConfig          // Service discovery
DiscoverySessionCallback // Event handling

// Subscribe to service
val config = SubscribeConfig.Builder()
    .setServiceName("MeshRiderService")
    .build()
awareManager.subscribe(session, config, callback, handler)
```

**NAN Cluster:**
- Devices form clusters with shared timing
- Cluster merges when ranges overlap
- Anchor master coordinates timing

**Characteristics:**
| Parameter | Value |
|-----------|-------|
| Discovery range | ~100m |
| Power | Lower than WiFi Direct |
| Latency | ~100ms discovery |
| Data | Separate data path (NDP) |
| Android | API 26+ required |

**Advantages for Tactical:**
- ✅ Background discovery without connection
- ✅ Service-oriented (find "radio" not just devices)
- ✅ Lower power than WiFi Direct
- ✅ Works alongside infrastructure WiFi

---

## 4. HYBRID APPROACHES

### 4.1 Phone-to-Radio Architecture

**Current MeshRider Wave Architecture:**
```
┌─────────────┐      WiFi      ┌──────────┐    Mesh    ┌──────────┐
│ Android     │◄──────────────►│ Doodle   │◄──────────►│ Doodle   │
│ Phone (EUD) │   10.223.x.x   │ Labs     │  batman    │ Labs     │
│             │                │ Radio    │   -adv     │ Radio    │
└─────────────┘                └──────────┘            └──────────┘
     │                              │                       │
   [PTT]                         [Mesh]                  [Mesh]
   App                          Network                 Network
```

**Advantages:**
- Phones provide compute, UI, battery
- Radios provide long-range mesh transport
- Separation of concerns
- Phones can use other radios (cellular, WiFi) when available

### 4.2 Radio-to-Radio Mesh (Long-Range)

**DoodleLabs MeshRider Radios:**
- Run LEDE (OpenWRT fork)
- batman-adv in kernel
- High-power radios (up to 1W / 30dBm)
- Frequency bands: 900MHz, 2.4GHz, 5GHz
- Range: Several kilometers (terrain dependent)

**Mesh Properties:**
- Self-forming, self-healing
- Multi-hop relay
- Auto-routing with batman-adv
- Gateway support for internet backhaul

### 4.3 Phone-to-Phone Direct (Short-Range Fallback)

**Use Cases:**
- Radios unavailable or out of range
- Indoor/cave operations
- Battery conservation on radios
- Covert operations (low power)

**Technologies:**

| Technology | Range | Data Rate | Power | Notes |
|------------|-------|-----------|-------|-------|
| WiFi Direct | 100m | 54-250 Mbps | High | Single hop |
| WiFi Aware | 100m | 54-250 Mbps | Medium | Discovery focus |
| BLE 5 | 100m+ | 125kbps-2Mbps | Low | Mesh possible |
| Bluetooth Classic | 10m | 1-3 Mbps | Medium | Legacy |

### 4.4 BLE as Control Channel

**Applications:**
- Radio discovery and pairing
- Push-to-talk button state
- Low-bandwidth telemetry
- Battery status
- Configuration

**Advantages:**
- Extremely low power
- Always-on feasible
- Simple GATT service model
- Cross-platform support

**BLE GATT Service Example:**
```
Service: MeshRider Control (UUID: custom)
  Characteristic: PTT State (R/W/Notify)
  Characteristic: Radio Status (Read)
  Characteristic: Configuration (R/W)
  Characteristic: Command (Write)
```

### 4.5 NFC for Pairing

**Use Case:** Quick, secure radio pairing
- Tap phone to radio
- Exchange credentials
- Automatic WiFi connection
- Works even without mesh connectivity

**Implementation:**
- NDEF message with WiFi credentials
- Or custom APDU for secure exchange
- Android Beam replacement (deprecated) - use reader/writer mode

---

## 5. TACTICAL CONSIDERATIONS

### 5.1 Frequency Hopping

**Purpose:**
- Anti-jamming
- Low probability of intercept (LPI)
- Multiple access (CDMA)

**Types:**
1. **Slow Frequency Hopping (SFH):** < 1 hop per symbol
2. **Fast Frequency Hopping (FFH):** > 1 hop per symbol

**Military Examples:**
- **SINCGARS:** VHF FHSS, 111-112 hops/second
- **HAVE QUICK:** UHF aeronautical
- **JTIDS/MIDS:** L-band, high data rate

**Challenges:**
- Synchronization required
- Dwell time regulations (FCC part 15)
- Coexistence with non-hopping systems

### 5.2 Anti-Jamming Techniques

| Technique | Mechanism | Trade-off |
|-----------|-----------|-----------|
| Frequency Hopping | Avoid jammed frequencies | Synchronization overhead |
| Spread Spectrum | DSSS spreads signal | Processing gain vs bandwidth |
| Adaptive Power | Increase power when jammed | Battery, detection risk |
| Directional Antennas | Spatial filtering | Size, pointing requirements |
| Cognitive Radio | Sense and avoid | Complexity, spectrum sensing |

### 5.3 Low Probability of Detection (LPD)

**Techniques:**
- **Low power transmission:** Near noise floor
- **Spread spectrum:** Signal below noise floor
- **Burst transmission:** Short on-air time
- **Directional antennas:** Reduce intercept footprint

**Metrics:**
- Intercept probability vs range
- Signal-to-noise ratio at detector

### 5.4 Cognitive Radio

**Definition:** Radio that can sense spectrum and adapt parameters

**Functions:**
1. **Spectrum Sensing:** Detect primary users
2. **Spectrum Management:** Select best channel
3. **Spectrum Mobility:** Switch channels seamlessly
4. **Spectrum Sharing:** Coexist with licensed users

**IEEE 802.22 (WRAN):**
- First cognitive radio standard (2011)
- Uses TV white spaces
- Database + sensing approach

**Applications:**
- Dynamic spectrum access (DSA)
- Interference mitigation
- Opportunistic spectrum use

### 5.5 Spectrum Sensing

**Techniques:**

| Method | Description | Complexity |
|--------|-------------|------------|
| Energy Detection | Measure received power | Low |
| Matched Filter | Correlation with known signal | Medium |
| Cyclostationary | Detect signal periodicity | High |
| Cooperative | Multiple nodes share sensing | Network overhead |

**Challenges:**
- Hidden node problem
- Sensing uncertainty (SNR wall)
- Primary user emulation attacks

---

## 6. IMPLEMENTATION ARCHITECTURE

### 6.1 Network Layer Integration in Android

**Current Approach (MeshRider Wave):**
```kotlin
// Standard socket over WiFi to radio
// Radio handles mesh routing transparently
val socket = DatagramSocket()
socket.send(packet) // To 10.223.x.x
```

**Advanced: VpnService for Mesh Integration**

Android VpnService allows intercepting all traffic:

```kotlin
class MeshVpnService : VpnService() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val builder = Builder()
            .addAddress("10.223.0.100", 24)
            .addRoute("10.223.0.0", 16)  // Mesh network
            .addDnsServer("10.223.0.1")
            .establish()
        
        // Now all 10.223.x.x traffic goes through VPN interface
        val vpnInterface = builder!!.fileDescriptor
        
        // Read IP packets from descriptor
        // Forward to mesh radio via custom protocol
        // Or encapsulate and send via UDP
        
        return START_STICKY
    }
}
```

**Benefits:**
- Transparent mesh for all apps
- Custom routing decisions
- Traffic shaping and QoS
- Encryption at mesh layer

### 6.2 Multicast Socket Implementation

**PTT Audio Transport:**
```kotlin
// Join multicast group
val socket = MulticastSocket(5004)
val group = InetAddress.getByName("239.255.0.1")
socket.joinGroup(group)

// For batman-adv: multicast is forwarded across mesh
// Each radio replicates multicast on its interfaces
```

**Considerations:**
- batman-adv handles multicast optimization
- IGMP snooping on switches
- Rate limiting for flooding prevention

### 6.3 Network State Monitoring

**ConnectivityManager:**
```kotlin
val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
val networkCallback = object : ConnectivityManager.NetworkCallback() {
    override fun onAvailable(network: Network) {
        // Mesh network available
    }
    override fun onLost(network: Network) {
        // Mesh disconnected - fallback to WiFi Direct/BLE
    }
}
cm.registerDefaultNetworkCallback(networkCallback)
```

**Network Capabilities:**
```kotlin
val capabilities = cm.getNetworkCapabilities(network)
val hasMesh = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
```

### 6.4 Bandwidth Estimation

**Approaches:**
1. **Link metrics from batman-adv:** Read TQ/throughput
2. **Active probing:** Measure RTT and loss
3. **Passive monitoring:** Track actual throughput

**Android Implementation:**
```kotlin
// Parse /proc/net/dev or use TrafficStats
val rxBytes = TrafficStats.getTotalRxBytes()
val txBytes = TrafficStats.getTotalTxBytes()

// Calculate rate
val rxRate = (rxBytes - lastRxBytes) / interval
```

**batman-adv Metrics Access:**
```bash
# Via sysfs on radio
/sys/class/net/bat0/mesh/
  - orig_interval
  - hop_penalty
  
/sys/kernel/debug/batman_adv/bat0/
  - originators
  - neighbors
  - transtable_global
```

---

## 7. EMERGING TECHNOLOGIES

### 7.1 Thread Protocol (802.15.4)

**Overview:**
- IPv6-based low-power mesh for IoT
- 2.4 GHz, IEEE 802.15.4 PHY
- 6LoWPAN header compression

**Characteristics:**
| Parameter | Value |
|-----------|-------|
| Data rate | 250 kbps |
| Range | 10-30m per hop |
| Nodes | 250+ per network |
| Sleep support | Yes (battery friendly) |
| IP-native | Yes |

**Border Routers:**
- Connect Thread to WiFi/Ethernet
- Apple HomePod Mini, Google Nest, Amazon Echo

**Matter over Thread:**
- Emerging smart home standard
- Not directly applicable to tactical voice
- Good for sensor networks

### 7.2 LoRa Mesh

**LoRa PHY:**
- Chirp Spread Spectrum (CSS)
- Sub-GHz bands (868/915 MHz)
- Very long range (km to 10s of km)
- Very low data rate (0.3-27 kbps)

**LoRaWAN vs Mesh:**
- **LoRaWAN:** Star topology, gateway required
- **LoRa Mesh:** P2P, store-and-forward

**Meshtastic:**
- Open-source LoRa mesh protocol
- Text messaging and GPS
- ESP32/nRF52 hardware
- Range: 2-5km typical, 100km+ record

**Applications:**
- Off-grid text messaging
- Long-range sensor networks
- Backup when WiFi mesh fails

**Limitations for PTT:**
- Too low bandwidth for voice
- High latency (not real-time)
- Good for fallback text/SOS

### 7.3 Meshtastic Protocol

**Architecture:**
- Flooding-based (controlled)
- Store-and-forward capability
- Optional MQTT gateway to internet

**Packet Structure:**
```
[Header][Source][Destination][Payload][HMAC]
```

**Use Case for MeshRider:**
- Emergency SOS when voice fails
- Location beaconing (low power)
- Text messaging (backup)

### 7.4 Reticulum Network Stack

**Vision:**
- Cryptography-based networking
- Sovereign communication networks
- Works over any medium (LoRa, WiFi, serial)

**Features:**
- End-to-end encryption
- Self-authenticating addresses
- Resilient to high latency/low bandwidth
- No hierarchical control

**Relevance:**
- Philosophy aligns with tactical requirements
- Could provide application-layer mesh
- Integration potential with existing stack

### 7.5 Disaster.radio

**Concept:**
- Off-grid emergency communication
- Solar-powered nodes
- LoRa-based
- Not widely deployed

---

## 8. RECOMMENDATIONS FOR MESHRIDER WAVE

### 8.1 Immediate (Current Development)

1. **Stick with batman-adv** for radio-to-radio mesh
   - Proven in DoodleLabs deployment
   - Kernel-integrated performance
   - Handles asymmetric links well

2. **Maintain WiFi phone-to-radio architecture**
   - Clean separation of concerns
   - Allows phone connectivity options
   - Standard IP transport

3. **Implement BLE control channel**
   - Low-power radio discovery
   - PTT button state sync
   - Configuration channel

### 8.2 Short-Term (3-6 months)

1. **WiFi Direct Fallback**
   - Phone-to-phone when radios unavailable
   - Use for configuration without radio
   - Range: ~100m

2. **WiFi Aware Service Discovery**
   - Discover radios without connection
   - Background operation
   - Android 8.0+ (API 26+)

3. **Network State Intelligence**
   - Monitor mesh health
   - Automatic fallback decisions
   - Bandwidth estimation

### 8.3 Medium-Term (6-12 months)

1. **Hybrid LoRa Integration**
   - Meshtastic-compatible for emergency text
   - GPS beaconing (low power)
   - SOS functionality

2. **VpnService Integration (Optional)**
   - Transparent mesh for all apps
   - Custom QoS handling
   - Multi-path (WiFi + Cellular + Mesh)

3. **Cognitive Radio Features**
   - Spectrum sensing on radios
   - Channel quality monitoring
   - Dynamic frequency selection

### 8.4 Long-Term (1+ years)

1. **Adaptive Frequency Hopping**
   - If radio hardware supports
   - Anti-jamming capability
   - Coordinated with network

2. **Multi-Radio Bonding**
   - Use multiple bands simultaneously
   - batman-adv multi-interface
   - Automatic load balancing

---

## 9. TECHNICAL IMPLEMENTATION NOTES

### 9.1 Android WiFi Direct Sample

```kotlin
class WiFiDirectManager(private val context: Context) {
    private val wifiP2pManager: WifiP2pManager = 
        context.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
    private val channel: WifiP2pManager.Channel = 
        wifiP2pManager.initialize(context, Looper.getMainLooper(), null)
    
    fun discoverPeers() {
        wifiP2pManager.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "Peer discovery started")
            }
            override fun onFailure(reason: Int) {
                Log.e(TAG, "Peer discovery failed: $reason")
            }
        })
    }
    
    fun connect(device: WifiP2pDevice) {
        val config = WifiP2pConfig().apply {
            deviceAddress = device.deviceAddress
            wps.setup = WpsInfo.PBC
        }
        wifiP2pManager.connect(channel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "Connection initiated")
            }
            override fun onFailure(reason: Int) {
                Log.e(TAG, "Connection failed: $reason")
            }
        })
    }
}
```

### 9.2 BLE Control Channel Sample

```kotlin
class MeshControlService : Service() {
    private val SERVICE_UUID = UUID.fromString("...")
    private val PTT_CHARACTERISTIC_UUID = UUID.fromString("...")
    
    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onCharacteristicReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic
        ) {
            when (characteristic.uuid) {
                PTT_CHARACTERISTIC_UUID -> {
                    val value = getPttState()
                    gattServer.sendResponse(device, requestId, 
                        BluetoothGatt.GATT_SUCCESS, offset, value)
                }
            }
        }
    }
}
```

### 9.3 Network Monitoring

```kotlin
class MeshNetworkMonitor(context: Context) {
    private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) 
        as ConnectivityManager
    
    fun isMeshAvailable(): Boolean {
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        
        // Check for WiFi transport and validated
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
               capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
    
    fun getLinkProperties(): LinkProperties? {
        val network = cm.activeNetwork ?: return null
        return cm.getLinkProperties(network)
    }
}
```

---

## 10. REFERENCES

### Standards & RFCs
- RFC 3626: OLSR
- RFC 7181: OLSRv2
- RFC 8966: Babel Routing Protocol
- RFC 3561: AODV
- RFC 4728: DSR
- IEEE 802.11s-2011: Mesh Networking
- IEEE 802.22: Cognitive Radio WRAN

### Projects & Implementations
- open-mesh.org: batman-adv
- olsr.org: OLSRd
- open80211s.org: Linux 802.11s
- openwrt.org: LEDE firmware
- meshtastic.org: LoRa mesh
- reticulum.network: Reticulum stack

### Android Documentation
- developer.android.com/guide/topics/connectivity/wifip2p
- developer.android.com/guide/topics/connectivity/wifi-aware
- developer.android.com/reference/android/net/VpnService
- developer.android.com/guide/topics/connectivity/bluetooth/ble-overview

---

## Appendices

### A. Glossary

| Term | Definition |
|------|------------|
| **MANET** | Mobile Ad-hoc Network |
| **OLSR** | Optimized Link State Routing |
| **AODV** | Ad-hoc On-demand Distance Vector |
| **DSR** | Dynamic Source Routing |
| **HWMP** | Hybrid Wireless Mesh Protocol |
| **SAE** | Simultaneous Authentication of Equals |
| **FHSS** | Frequency Hopping Spread Spectrum |
| **DSSS** | Direct Sequence Spread Spectrum |
| **CR** | Cognitive Radio |
| **LPD** | Low Probability of Detection |
| **EUD** | End User Device |
| **PTT** | Push-To-Talk |
| **SA** | Situational Awareness |
| **BFT** | Blue Force Tracking |

### B. Acronyms

See Glossary above.

---

*Report Version: 1.0*  
*Next Review: Quarterly or as technology evolves*
