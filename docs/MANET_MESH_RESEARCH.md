# DEEP RESEARCH: Mobile Ad-hoc Networks (MANET) & Mesh Networking
## For Billion-Dollar Tactical PTT Applications

**Research Date:** February 2026  
**Project:** MeshRider Wave - Tactical PTT Communication Platform  
**Prepared For:** DoodleLabs MeshRider Integration

---

## EXECUTIVE SUMMARY

This document provides comprehensive analysis of MANET protocols, mesh networking technologies, and integration strategies for a tactical-grade Push-To-Talk (PTT) application. The research covers Layer-2/Layer-3 mesh protocols, WiFi Direct/P2P technologies, multihop routing strategies, and performance optimization techniques essential for communication without cellular infrastructure.

---

## 1. MANET ROUTING PROTOCOLS

### 1.1 OLSR (Optimized Link State Routing) v2 - Military Standard

**Overview:**
OLSRv2 (RFC 7181) is the IETF-standardized successor to OLSR (RFC 3626), specifically designed for mobile ad-hoc networks. It's a proactive, table-driven protocol that continuously maintains routes to all destinations.

**Key Technical Features:**

| Feature | Implementation Detail |
|---------|----------------------|
| Protocol Type | Proactive (table-driven) |
| Algorithm | Link State with MPR optimization |
| Message Types | HELLO, TC (Topology Control), MID |
| Port | UDP 698 (IANA assigned) |
| MPR Sets | Separate Flooding MPRs and Routing MPRs |
| Link Metrics | Bidirectional additive metrics (not just hop count) |

**Multipoint Relay (MPR) Mechanism:**
```
Traditional Flooding:     O(n²) transmissions for n nodes
MPR Flooding:            O(n × log n) transmissions
Optimization Factor:     30-70% reduction in control overhead
```

**MPR Selection Algorithm:**
```c
// Nodes select MPRs to cover all 2-hop neighbors
MPR_Set(N) = minimal subset of symmetric 1-hop neighbors
             such that all symmetric strict 2-hop neighbors
             are reachable via at least one MPR

Constraints:
- All 2-hop neighbors must be covered
- Minimize MPR set size for efficiency
- Use link metrics for routing MPR selection
```

**OLSRv2 vs OLSRv1 Improvements:**
1. **Link Metrics:** Supports non-hop-count metrics (ETX, latency, packet loss)
2. **Dual MPR Sets:** Separate flooding and routing MPRs
3. **RFC 5444 Packet Format:** Efficient, extensible message format
4. **NHDP Integration:** Standardized neighbor discovery (RFC 6130)
5. **Simplified Messages:** Reduced overhead, better compression

**Message Intervals (Default):**
```
HELLO_INTERVAL:        2.0 seconds
TC_INTERVAL:           5.0 seconds
MID_INTERVAL:          5.0 seconds
MAXJITTER:             0.5 × interval
```

**Android Integration:**
```kotlin
// OLSRd (daemon) integration via UBUS
class OlsrManager(private val radioClient: RadioApiClient) {
    
    suspend fun getOlsrNeighbors(): List<OlsrNeighbor> {
        val result = radioClient.ubusCall("olsrd", "neighbors", null)
        return parseOlsrNeighbors(result)
    }
    
    suspend fun getOlsrTopology(): TopologyGraph {
        val result = radioClient.ubusCall("olsrd", "topology", null)
        return buildTopologyGraph(result)
    }
    
    data class OlsrNeighbor(
        val ipAddress: String,
        val linkQuality: Float,      // OLSR LQ (0.0 - 1.0)
        val neighborLinkQuality: Float, // NLQ (0.0 - 1.0)
        val etx: Float,              // Expected Transmission Count
        val symmetric: Boolean
    )
}
```

**Military Applications:**
- NATO coalition networks
- US Army WIN-T (Warfighter Information Network-Tactical)
- Norwegian Armed Forces
- Suitable for: Large, dense networks with high mobility

---

### 1.2 B.A.T.M.A.N. (Better Approach To Mobile Ad-hoc Networking)

**Overview:**
BATMAN-adv is a Layer-2 routing protocol integrated into the Linux kernel, optimized for wireless mesh networks. DoodleLabs MeshRider uses BATMAN-adv as its default mesh protocol.

**Architecture:**
```
Layer 3 (IP)        ┌─────────────────┐
                    │   IP Routing    │
Layer 2.5           ├─────────────────┤
                    │   BATMAN-adv    │  ← Mesh routing
Layer 2             ├─────────────────┤
                    │   802.11 MAC    │
Layer 1             ├─────────────────┤
                    │  Radio PHY      │
```

**Key Technical Features:**

| Feature | Implementation Detail |
|---------|----------------------|
| Protocol Type | proactive, distance-vector-like |
| Layer | Layer 2 (Ethernet frames) |
| Kernel Module | batman-adv.ko |
| Origin Interval | 1 second (default) |
| TQ (Transmit Quality) | 0-255 scale |

**Originator Message (OGM) Format:**
```c
struct batman_ogm_packet {
    uint8_t  packet_type;      // BATADV_IV_OGM = 0x01
    uint8_t  version;          // BATADV_COMPAT_VERSION
    uint8_t  ttl;              // Time to live
    uint8_t  flags;
    uint32_t seqno;            // Sequence number
    uint8_t  orig[ETH_ALEN];   // Originator MAC address
    uint8_t  prev_sender[ETH_ALEN]; // Previous sender
    uint8_t  reserved;
    uint8_t  tq;               // Transmit Quality (0-255)
    uint16_t tvlv_len;         // TVLV length
} __packed;
```

**TQ (Transmit Quality) Calculation:**
```
TQ = (RQ × OQ) / 255

Where:
- RQ (Receive Quality) = % of OGMs received from neighbor
- OQ (Own Quality) = announced quality by neighbor
- TQ range: 0-255 (255 = perfect link)

Example:
  RQ = 200 (78% reception)
  OQ = 230 (neighbor's announced quality)
  TQ = (200 × 230) / 255 = 180
```

**BATMAN-adv Implementation (from LEDE):**
```bash
# Check mesh status via batctl
batctl o                    # Show originators
batctl n                    # Show neighbors
batctl tg                   # Show translation table
batctl gw                   # Show gateway status
batctl l                    # Show link statistics

# JSON output (used by linkstate daemon)
batctl oj                   # JSON originators
```

**DoodleLabs Integration:**
```c
// From linkstate.c - BATMAN integration
static void get_mesh_stats(linkstate_data_t *data)
{
    // Execute batctl for mesh topology
    char *out = read_cmd("/usr/sbin/batctl oj 2>/dev/null");
    json_object *arr = json_tokener_parse(out);
    
    // Parse originators and TQ values
    for (int i = 0; i < len; i++) {
        json_object *entry = json_object_array_get_idx(arr, i);
        mesh_stats_t *mesh = calloc(1, sizeof(mesh_stats_t));
        
        // Extract orig_address, tq, last_seen, hop_status
        json_object *orig_addr = NULL, *tq = NULL;
        json_object_object_get_ex(entry, "orig_address", &orig_addr);
        json_object_object_get_ex(entry, "tq", &tq);
        
        mesh->tq = json_object_get_int(tq);
        // TQ 255 = direct link, lower = multihop
        if (tq == 255) {
            snprintf(mesh->hop_status, sizeof(mesh->hop_status), "direct");
        } else {
            snprintf(mesh->hop_status, sizeof(mesh->hop_status), "hop");
        }
    }
}
```

**Gateway Mode (BATMAN Gateway Selection):**
```bash
# Configure as gateway (for internet access via mesh)
batctl gw_mode server 50    # 50 Mbit/s bandwidth announced

# Configure as client
batctl gw_mode client

# Automatic gateway selection based on TQ
```

**Performance Characteristics:**
| Metric | Value |
|--------|-------|
| Convergence Time | 2-5 seconds |
| Control Overhead | ~5-10 kbps per node |
| Maximum Hops | Unlimited (tested to 50+) |
| Throughput | 50-80% of raw WiFi |

---

### 1.3 Babel - Modern Distance-Vector Protocol

**Overview:**
Babel (RFC 6126, updated by RFC 8966) is a loop-avoiding distance-vector routing protocol designed for both wired and wireless networks. It uses sequenced routes (DSDV-style) and feasibility conditions (EIGRP-style).

**Key Innovation - Feasibility Condition:**
```
Babel uses a refined feasibility condition based on "feasibility distance":

Feasibility Distance FD(A) = minimum metric A ever advertised for prefix

Route announcement from B is FEASIBLE for A if:
  metric_advertised_by_B < FD(A)

This prevents routing loops while allowing more routes than DSDV.
```

**Algorithm Comparison:**
```
DSDV Feasibility:      C(A,B) + D(B) ≤ D(A)     [stricter]
EIGRP Feasibility:     D(B) < FD(A)             [looser]
Babel Feasibility:     (s',m') < (s,m)          [with sequence numbers]

Where (s,m) is (sequence_number, metric) pair
```

**Message Types:**
| TLV Type | Purpose |
|----------|---------|
| Hello (0x00) | Neighbor discovery |
| IHU (0x01) | "I Heard You" - bidirectional check |
| Update (0x02) | Route advertisement |
| Route Request (0x03) | Request route update |
| Seqno Request (0x04) | Request new sequence number |

**Link Cost Computation:**
```c
// Appendix A of RFC 6126
struct hello_history {
    uint8_t received[8];  // Bitmask of last 8 hellos
};

// rxcost based on reception history
rxcost = 256 / (number_of_hellos_received_in_window / 8);

// Cost = rxcost × txcost / 256
if (txcost == infinity || no_hellos_received)
    cost = infinity;
else
    cost = (rxcost * txcost) / 256;
```

**Android/Linux Integration:**
```bash
# Babeld daemon configuration
/etc/babeld.conf:
interface wlan0
  rxcost 96
  hello-interval 4
  update-interval 60

# Check routes
ip route show proto babel

# babeld CLI
echo 'dump' | nc ::1 33123
```

**When to Use Babel vs BATMAN:**
| Scenario | Preferred Protocol |
|----------|-------------------|
| Pure Layer 3 routing | Babel |
| Layer 2 bridging required | BATMAN-adv |
| Mixed wired/wireless | Babel |
| Simple mesh (all wireless) | BATMAN-adv |
| Large scale (>100 nodes) | OLSRv2 |

---

### 1.4 HWMP (Hybrid Wireless Mesh Protocol - IEEE 802.11s)

**Overview:**
HWMP is the IEEE 802.11s standard mesh protocol, built into modern WiFi chipsets. It operates at Layer 2 and is supported by many commercial mesh WiFi systems.

**Two Path Selection Modes:**

1. **On-Demand Mode (Reactive):**
   ```
   Similar to AODV:
   - Route Discovery (PREQ/Prep)
   - Route Request broadcast
   - Route Reply unicast
   - Route Error (PERR) for broken links
   ```

2. **Proactive Mode:**
   ```
   Root-based tree:
   - One node acts as Root Mesh Portal
   - Root announces path via proactive PREQ
   - All nodes maintain path to root
   - Portal connects to wired network
   ```

**HWMP Frame Format:**
```c
// Mesh Action Frame Header
struct ieee80211s_mesh_action {
    uint8_t category;      // 0x0D = Mesh
    uint8_t action_code;   // PREQ=0, PREP=1, PERR=2, etc.
    // Variable-length IEs follow
};

// Path Request Element
struct mesh_path_request {
    uint8_t element_id;
    uint8_t length;
    uint8_t flags;
    uint8_t hop_count;
    uint8_t ttl;
    uint32_t path_discovery_id;
    uint8_t originator_addr[6];
    uint32_t originator_sn;
    uint8_t target_addr[6];
    uint32_t target_sn;
    // ... optional fields
};
```

**Android Limitations:**
- Requires root access and custom firmware
- Most Android devices don't expose 802.11s in stock firmware
- Better suited for dedicated mesh hardware

---

### 1.5 DSR (Dynamic Source Routing)

**Overview:**
DSR is a reactive (on-demand) routing protocol where source nodes include complete route in packet headers.

**Operation:**
```
Route Discovery:
  1. Source broadcasts Route Request (RREQ)
  2. Each node appends its address and forwards
  3. Destination receives RREQ with complete path
  4. Destination sends Route Reply (RREP) via reverse path

Route Maintenance:
  - Each node monitors next hop
  - On link failure, sends Route Error (RERR) to source
  - Source initiates new Route Discovery
```

**DSR Header Format:**
```c
struct dsr_header {
    uint8_t next_header;
    uint8_t reserved;
    uint16_t length;
    
    // Option headers follow:
    // - Route Request
    // - Route Reply
    // - Route Error
    // - Source Route
    // - Acknowledgment
    // - Pad
};

// Source Route Option
struct dsr_source_route {
    uint8_t type;       // 0x03
    uint8_t length;
    uint8_t segments_left;
    uint8_t first_hop;
    uint8_t addresses[];  // Variable length
};
```

**Characteristics:**
| Aspect | Value |
|--------|-------|
| Routing Overhead | Low for idle traffic |
| First Packet Delay | High (route discovery) |
| Packet Size Overhead | 4-8 bytes per hop |
| Scalability | Poor beyond 50 nodes |
| Mobility Support | Good |

---

### 1.6 AODV (Ad-hoc On-demand Distance Vector)

**Overview:**
AODV (RFC 3561) is the most widely implemented reactive MANET protocol, combining on-demand route discovery with distance-vector routing table maintenance.

**Route Discovery Process:**
```
┌──────────┐                      ┌──────────┐
│  Source  │───RREQ (broadcast)──▶│  Node A  │
│ ( seeks  │◀──RREP (unicast)─────│(forwards)│
│  route)  │                      └────┬─────┘
└──────────┘                           │
     ▲                            RREQ │
     │                                 ▼
     │                           ┌──────────┐
     └───────────────────────────│Destination│
          RREP (via reverse path)│ (replies) │
                                 └──────────┘

RREQ includes:  source_addr, source_seqno, broadcast_id,
                dest_addr, dest_seqno, hop_count

RREP includes:  source_addr, dest_addr, dest_seqno,
                hop_count, lifetime
```

**AODV Message Types:**
```c
#define RREQ_TYPE 1
#define RREP_TYPE 2
#define RERR_TYPE 3
#define RREP_ACK_TYPE 4

// Route Request
struct rreq {
    uint8_t  type;
    uint8_t  flags;
    uint8_t  reserved;
    uint8_t  hop_count;
    uint32_t rreq_id;
    uint32_t dest_ip;
    uint32_t dest_seq;
    uint32_t orig_ip;
    uint32_t orig_seq;
};

// Route Reply
struct rrep {
    uint8_t  type;
    uint8_t  flags;
    uint8_t  prefix_sz;
    uint8_t  hop_count;
    uint32_t dest_ip;
    uint32_t dest_seq;
    uint32_t orig_ip;
    uint32_t lifetime;
};
```

**Sequence Number Management:**
```
Critical for loop prevention:

Destination Sequence Number (dest_seq):
- Monotonically increasing
- Higher = fresher route
- Must be >= to accept RREP
- Prevents use of stale routes

Originator Sequence Number (orig_seq):
- Used to detect duplicate RREQs
- (orig_ip, rreq_id) pair tracks seen RREQs
```

**AODV vs DSR Comparison:**
| Feature | AODV | DSR |
|---------|------|-----|
| Route Discovery | Broadcast RREQ | Broadcast RREQ |
| Routing State | Per-destination table | Route cache |
| Packet Overhead | None | Source route in header |
| Route Maintenance | Hello messages | Passive acks |
| Performance | Better for low mobility | Better for high mobility |

---

## 2. DOODLELABS MESHRIDER RADIO INTEGRATION

### 2.1 Layer-2 vs Layer-3 Mesh Networking

**Architecture Decision Matrix:**

| Aspect | Layer-2 Mesh (BATMAN-adv) | Layer-3 Mesh (OLSR/Babel) |
|--------|---------------------------|---------------------------|
| Transparency | Fully transparent | Router-like behavior |
| Protocol Support | Any IP protocol | IP only |
| Broadcast Handling | Efficient flooding | Protocol-dependent |
| Multicast | Native support | Requires optimization |
| ARP/ND | Single broadcast domain | Per-subnet |
| Roaming | Seamless | May re-IP |
| Complexity | Lower | Higher |
| Battery Life | Better (less processing) | More processing |

**DoodleLabs Implementation:**
```
MeshRider Default Stack:
┌─────────────────────────────────────┐
│  Application (PTT, Video, Data)     │
├─────────────────────────────────────┤
│  IP Layer (10.223.x.x)              │  ← Layer 3
├─────────────────────────────────────┤
│  BATMAN-adv (bat0 interface)        │  ← Layer 2.5
├─────────────────────────────────────┤
│  802.11 (adhoc/mesh mode)           │  ← Layer 2
├─────────────────────────────────────┤
│  Ath9k / IPQ4019 Radio Driver       │  ← Layer 1-2
└─────────────────────────────────────┘
```

**Current LEDE Integration (from linkstate.c):**
```c
// Mesh topology via batctl
char *out = read_cmd("/usr/sbin/batctl oj 2>/dev/null");
// Returns JSON array of originators with TQ values

// Example output:
[
  {
    "orig_address": "02:23:73:01:02:03",
    "tq": 255,              // 255 = perfect direct link
    "neigh_address": "02:23:73:01:02:03",
    "last_seen_msecs": 500,
    "best": true
  }
]
```

### 2.2 Frequency Band Capabilities

**MeshRider Radio Specifications:**

| Band | Frequency Range | Use Case | Range | Penetration |
|------|-----------------|----------|-------|-------------|
| 900MHz | 902-928 MHz | Long range, NLOS | 10+ km | Excellent |
| 2.4GHz | 2.402-2.480 GHz | General purpose | 2-5 km | Good |
| 5GHz | 5.150-5.850 GHz | High throughput | 1-3 km | Limited |
| CBRS | 3.55-3.70 GHz | Licensed shared | 5-10 km | Moderate |

**Band Selection Strategy:**
```kotlin
class BandSelectionManager {
    
    data class BandCapabilities(
        val band: String,           // "900MHz", "2.4GHz", "5GHz"
        val maxRange: Int,          // meters
        val maxThroughput: Int,     // Mbps
        val penetration: PenetrationLevel,
        val regulatory: RegulatoryDomain
    )
    
    fun selectOptimalBand(
        environment: Environment,
        rangeRequirement: Int,
        throughputRequirement: Int
    ): String {
        return when (environment) {
            Environment.URBAN -> "900MHz"  // NLOS penetration
            Environment.RURAL -> "5GHz"    // Long clear shots
            Environment.INDOOR -> "2.4GHz" // Balance
            Environment.MOBILE -> "900MHz" // Consistent coverage
        }
    }
    
    fun estimateRange(band: String, conditions: LinkConditions): Int {
        val baseRange = when (band) {
            "900MHz" -> 10000  // 10km
            "2.4GHz" -> 5000   // 5km
            "5GHz" -> 3000     // 3km
            else -> 5000
        }
        
        // Apply environmental factors
        return when {
            conditions.hasObstacles -> (baseRange * 0.3).toInt()
            conditions.hasInterference -> (baseRange * 0.6).toInt()
            else -> baseRange
        }
    }
}
```

### 2.3 BATMAN-adv Integration in LEDE/OpenWRT

**LEDE Package Structure:**
```
package/
├── batctl/                    # BATMAN control utility
│   ├── Makefile
│   └── files/
├── kmod-batman-adv/           # Kernel module
│   └── Makefile
└── linkstate/                 # DoodleLabs link monitoring
    ├── src/
    │   ├── linkstate.c        # Mesh stats collection
    │   └── linkstate.h
    └── Makefile
```

**Kernel Module Loading:**
```bash
# /etc/modules.d/50-batman-adv
batman-adv

# Module parameters
echo 1000 > /sys/class/net/bat0/mesh/orig_interval  # 1 second
```

**Network Configuration (UCI):**
```bash
# /etc/config/network
config interface 'bat0'
    option proto 'batadv'
    option routing_algo 'BATMAN_IV'
    option aggregated_ogms '1'
    option ap_isolation '0'
    option bonding '0'
    option bridge_loop_avoidance '1'
    option distributed_arp_table '1'
    option fragmentation '1'
    option gw_bandwidth '10000/10000'
    option gw_mode 'off'
    option hop_penalty '30'
    option isolation_mark '0x00000000/0x00000000'
    option log_level '0'
    option multicast_mode '1'
    option network_coding '0'
    option orig_interval '1000'
    option multicast_fanout '16'

config interface 'mesh'
    option proto 'batadv_hardif'
    option master 'bat0'
    option mtu '1532'
```

**Wireless Configuration:**
```bash
# /etc/config/wireless
config wifi-iface 'mesh'
    option device 'radio0'
    option ifname 'mesh0'
    option mode 'mesh'
    option mesh_id 'meshrider'
    option mesh_fwding '0'       # Disable 802.11s forwarding
    option mesh_ttl '1'
    option mcast_rate '54000'
    option network 'mesh'
```

### 2.4 JSON-RPC/UBUS API for Radio Management

**UBUS Service Architecture:**
```
┌─────────────────────────────────────────┐
│  Android App (Kotlin)                   │
│  - RadioApiClient                       │
├─────────────────────────────────────────┤
│  HTTP/JSON-RPC over WiFi/USB            │
├─────────────────────────────────────────┤
│  uhttpd (LEDE web server)               │
├─────────────────────────────────────────┤
│  rpcd (RPC daemon)                      │
├─────────────────────────────────────────┤
│  ubus (message bus)                     │
├─────────────┬─────────────┬─────────────┤
│  iwinfo     │  network    │  message-   │
│  (wifi)     │  (routing)  │  system     │
└─────────────┴─────────────┴─────────────┘
```

**RadioApiClient Implementation:**
```kotlin
class RadioApiClient {
    
    // UBUS Services
    companion object {
        const val UBUS_IWINFO = "iwinfo"
        const val UBUS_WIRELESS = "wireless"
        const val UBUS_SYSTEM = "system"
        const val UBUS_NETWORK = "network"
        const val UBUS_MESSAGE_SYSTEM = "message-system"
    }
    
    // Get wireless status
    suspend fun getWirelessStatus(iface: String = "wlan0"): WirelessStatus {
        val params = JSONObject().put("device", iface)
        val info = ubusCall("iwinfo", "info", params)
        
        return WirelessStatus(
            ssid = info?.optString("ssid", ""),
            channel = info?.optInt("channel", 0),
            frequency = info?.optInt("frequency", 0),
            bandwidth = parseBandwidth(info?.optInt("htmode", 20)),
            txPower = info?.optInt("txpower", 0),
            signal = info?.optInt("signal", -100),
            noise = info?.optInt("noise", -95),
            bitrate = info?.optInt("bitrate", 0),
            snr = signal - noise
        )
    }
    
    // Get mesh neighbors
    suspend fun getAssociatedStations(iface: String = "wlan0"): List<Station> {
        val params = JSONObject().put("device", iface)
        val result = ubusCall("iwinfo", "assoclist", params)
        
        return result?.optJSONArray("results")?.let { arr ->
            (0 until arr.length()).map { i ->
                val sta = arr.getJSONObject(i)
                Station(
                    macAddress = sta.optString("mac"),
                    signal = sta.optInt("signal"),
                    noise = sta.optInt("noise"),
                    snr = sta.optInt("signal") - sta.optInt("noise"),
                    txBitrate = sta.optInt("tx_rate"),
                    rxBitrate = sta.optInt("rx_rate"),
                    inactive = sta.optInt("inactive")
                )
            }
        } ?: emptyList()
    }
    
    // Channel switching
    suspend fun switchChannel(channel: Int, bandwidth: Int = 20): Boolean {
        val params = JSONObject().apply {
            put("channel", channel)
            put("bandwidth", bandwidth)
        }
        val result = ubusCall("message-system", "chswitch", params)
        return result?.optBoolean("success", false) ?: false
    }
}
```

**UBUS Call Format:**
```json
// Request
{
    "jsonrpc": "2.0",
    "id": 42,
    "method": "call",
    "params": [
        "ubus_rpc_session_id",
        "iwinfo",
        "info",
        {"device": "wlan0"}
    ]
}

// Response
{
    "jsonrpc": "2.0",
    "id": 42,
    "result": [0, {
        "ssid": "MeshRider",
        "bssid": "02:23:73:01:02:03",
        "channel": 161,
        "frequency": 5805,
        "txpower": 20,
        "signal": -65,
        "noise": -95,
        "bitrate": 150000,
        "encryption": {"enabled": false}
    }]
}
```

### 2.5 Channel Switching and Bandwidth Adaptation

**Dynamic Channel Switching:**
```kotlin
class ChannelManager(private val radioClient: RadioApiClient) {
    
    data class ChannelInfo(
        val channel: Int,
        val frequency: Int,
        val restricted: Boolean,
        val active: Boolean
    )
    
    // Available channels by band
    suspend fun getAvailableChannels(iface: String = "wlan0"): List<ChannelInfo> {
        val params = JSONObject().put("device", iface)
        val result = radioClient.ubusCall("iwinfo", "freqlist", params)
        
        return result?.optJSONArray("results")?.let { arr ->
            (0 until arr.length()).map { i ->
                val freq = arr.getJSONObject(i)
                ChannelInfo(
                    channel = freq.optInt("channel"),
                    frequency = freq.optInt("mhz"),
                    restricted = freq.optBoolean("restricted"),
                    active = freq.optBoolean("active")
                )
            }
        } ?: emptyList()
    }
    
    // Adaptive channel selection based on interference
    suspend fun selectOptimalChannel(
        available: List<ChannelInfo>,
        strategy: SelectionStrategy
    ): Int {
        val survey = conductSpectrumSurvey()
        
        return when (strategy) {
            SelectionStrategy.LEAST_INTERFERENCE -> {
                available.minByOrNull { survey.getInterference(it.channel) }
                    ?.channel ?: available.first().channel
            }
            SelectionStrategy.WIDEST_BANDWIDTH -> {
                available.filter { !it.restricted }
                    .maxByOrNull { getChannelWidth(it.channel) }
                    ?.channel ?: available.first().channel
            }
            SelectionStrategy.MESH_COMPATIBLE -> {
                // Select channel that works across all mesh nodes
                available.filter { it.active }
                    .maxByOrNull { survey.getSignalQuality(it.channel) }
                    ?.channel ?: available.first().channel
            }
        }
    }
    
    // Switch channel mesh-wide
    suspend fun switchMeshChannel(channel: Int, bandwidth: Int): Boolean {
        // Send channel switch command via message-system
        return radioClient.switchChannel(channel, bandwidth)
    }
}
```

**Bandwidth Adaptation:**
```kotlin
class BandwidthAdaptationManager {
    
    enum class ChannelWidth { WIDTH_5MHZ, WIDTH_10MHZ, WIDTH_20MHZ, WIDTH_40MHZ }
    
    fun selectBandwidth(linkConditions: LinkConditions): ChannelWidth {
        return when {
            linkConditions.snr < 10 -> ChannelWidth.WIDTH_5MHZ   // Robust
            linkConditions.snr < 15 -> ChannelWidth.WIDTH_10MHZ  // Balanced
            linkConditions.snr < 20 -> ChannelWidth.WIDTH_20MHZ  // Standard
            else -> ChannelWidth.WIDTH_40MHZ                     // High throughput
        }
    }
    
    // Throughput vs Range tradeoff
    val bandwidthCharacteristics = mapOf(
        ChannelWidth.WIDTH_5MHZ to BandwidthCharacteristics(
            throughputMbps = 6.5,
            rangeMultiplier = 2.0,      // 2x range
            penetration = "Excellent"
        ),
        ChannelWidth.WIDTH_10MHZ to BandwidthCharacteristics(
            throughputMbps = 13.0,
            rangeMultiplier = 1.4,
            penetration = "Very Good"
        ),
        ChannelWidth.WIDTH_20MHZ to BandwidthCharacteristics(
            throughputMbps = 54.0,
            rangeMultiplier = 1.0,      // Baseline
            penetration = "Good"
        ),
        ChannelWidth.WIDTH_40MHZ to BandwidthCharacteristics(
            throughputMbps = 108.0,
            rangeMultiplier = 0.7,      // 30% less range
            penetration = "Moderate"
        )
    )
}
```

### 2.6 Link Quality Metrics and Monitoring

**Link Metrics Collected by linkstate daemon:**
```c
// From linkstate.h
typedef struct {
    // Basic link info
    char mac[18];
    int rssi;                    // Signal strength (dBm)
    int8_t rssi_ant[2];          // Per-antenna RSSI
    int noise;                   // Noise floor (dBm)
    
    // Traffic statistics
    uint32_t tx_packets;
    uint32_t tx_bytes;
    uint32_t tx_retries;
    uint32_t tx_failed;
    float pl_ratio;              // Packet loss ratio (%)
    
    // Rate info
    int mcs;                     // MCS index
    int bitrate_avg;             // Average bitrate (kbps)
    int fixed_txpower;
    
    // Activity
    int inactive;                // ms since last activity
} sta_stats_t;

typedef struct {
    // BATMAN-adv mesh stats
    char orig_address[18];
    int tq;                      // Transmit Quality (0-255)
    char hop_status[8];          // "direct" or "hop"
    int last_seen_msecs;
} mesh_stats_t;
```

**Link Quality API (Android):**

```kotlin
class LinkQualityManager(private val radioClient: RadioApiClient) {
    
    data class LinkMetrics(
        // Signal metrics
        val rssi: Int,              // dBm
        val noise: Int,             // dBm
        val snr: Int,               // dB (signal - noise)
        
        // Quality indicators
        val linkQuality: Int,       // 0-100
        val tq: Int,                // BATMAN TQ (0-255)
        val etx: Float,             // Expected Transmission Count
        
        // Throughput
        val txBitrate: Int,         // kbps
        val rxBitrate: Int,         // kbps
        
        // Stability
        val packetLoss: Float,      // percentage
        val inactive: Int           // ms since last seen
    )
    
    fun calculateLinkQuality(metrics: LinkMetrics): LinkQuality {
        // Weighted scoring
        val snrScore = ((metrics.snr + 100).coerceIn(0, 100))
        val tqScore = (metrics.tq * 100) / 255
        val plScore = (100 - metrics.packetLoss * 100).toInt()
        
        val overallQuality = (snrScore * 0.4 + tqScore * 0.4 + plScore * 0.2).toInt()
        
        return LinkQuality(
            score = overallQuality,
            category = when {
                overallQuality >= 80 -> QualityCategory.EXCELLENT
                overallQuality >= 60 -> QualityCategory.GOOD
                overallQuality >= 40 -> QualityCategory.FAIR
                overallQuality >= 20 -> QualityCategory.POOR
                else -> QualityCategory.UNUSABLE
            }
        )
    }
    
    // ETX (Expected Transmission Count) calculation
    fun calculateETX(lq: Float, nlq: Float): Float {
        // ETX = 1 / (LQ × NLQ)
        // LQ = link quality (delivery ratio to neighbor)
        // NLQ = neighbor link quality (delivery ratio from neighbor)
        return if (lq > 0 && nlq > 0) {
            1.0f / (lq * nlq)
        } else {
            Float.MAX_VALUE  // Unreachable
        }
    }
    
    // Monitor link quality trends
    fun analyzeTrend(history: List<LinkMetrics>): TrendAnalysis {
        if (history.size < 3) return TrendAnalysis.STABLE
        
        val recent = history.takeLast(5).map { it.linkQuality }.average()
        val older = history.dropLast(5).takeLast(5).map { it.linkQuality }.average()
        
        return when {
            recent > older * 1.2 -> TrendAnalysis.IMPROVING
            recent < older * 0.8 -> TrendAnalysis.DEGRADING
            else -> TrendAnalysis.STABLE
        }
    }
}
```

---

## 3. WIFI DIRECT & P2P TECHNOLOGIES

### 3.1 WiFi Direct Specification and Android APIs

**WiFi Direct Architecture:**
```
┌──────────────────────────────────────────┐
│  Application Layer                       │
│  - P2P service discovery                 │
│  - Group owner negotiation               │
├──────────────────────────────────────────┤
│  WiFi P2P Framework (Android)            │
│  - WifiP2pManager                        │
│  - BroadcastReceiver                     │
├──────────────────────────────────────────┤
│  WPA Supplicant (p2p_supplicant)         │
│  - WiFi Direct protocol handling         │
├──────────────────────────────────────────┤
│  nl80211 / wext                          │
├──────────────────────────────────────────┤
│  WiFi Driver (ath9k, etc.)               │
└──────────────────────────────────────────┘
```

**WiFi Direct Key Concepts:**

| Concept | Description |
|---------|-------------|
| P2P Device | A WiFi Direct capable device |
| P2P Group | A network formed by P2P devices |
| Group Owner (GO) | Acts as AP for the group (one per group) |
| P2P Client | Connects to GO |
| P2P GO Negotiation | Determines which device becomes GO |
| Persistent Group | Reconnect without re-negotiation |

**Android WiFi Direct Implementation:**

```kotlin
class WiFiDirectManager(private val context: Context) {
    
    private val manager: WifiP2pManager = 
        context.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
    private var channel: WifiP2pManager.Channel? = null
    private var receiver: BroadcastReceiver? = null
    
    // Initialize
    fun initialize() {
        channel = manager.initialize(context, context.mainLooper, null)
        receiver = WiFiDirectBroadcastReceiver(manager, channel, this)
        
        val intentFilter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        }
        context.registerReceiver(receiver, intentFilter)
    }
    
    // Discover peers
    fun discoverPeers(onResult: (Result<List<WifiP2pDevice>>) -> Unit) {
        manager.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                // Wait for WIFI_P2P_PEERS_CHANGED_ACTION
            }
            override fun onFailure(reason: Int) {
                onResult(Result.failure(WifiP2pException(reason)))
            }
        })
    }
    
    // Connect to peer
    fun connect(device: WifiP2pDevice, onResult: (Result<Unit>) -> Unit) {
        val config = WifiP2pConfig().apply {
            deviceAddress = device.deviceAddress
            wps.setup = WpsInfo.PBC  // Push button config
        }
        
        manager.connect(channel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                onResult(Result.success(Unit))
            }
            override fun onFailure(reason: Int) {
                onResult(Result.failure(WifiP2pException(reason)))
            }
        })
    }
    
    // Create group (act as Group Owner)
    fun createGroup(onResult: (Result<String>) -> Unit) {
        manager.createGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                // Get group info to retrieve GO IP
                manager.requestGroupInfo(channel) { group ->
                    onResult(Result.success(group?.owner?.deviceAddress ?: ""))
                }
            }
            override fun onFailure(reason: Int) {
                onResult(Result.failure(WifiP2pException(reason)))
            }
        })
    }
    
    // P2P for PTT - Mesh formation
    fun formP2PMesh(devices: List<WifiP2pDevice>) {
        // Strategy: Elect one device as GO based on criteria
        val groupOwner = electGroupOwner(devices)
        
        if (isLocalDevice(groupOwner)) {
            createGroup { result ->
                // Start accepting connections
            }
        } else {
            connect(groupOwner) { result ->
                // Connected to mesh GO
            }
        }
    }
    
    private fun electGroupOwner(devices: List<WifiP2pDevice>): WifiP2pDevice {
        // Criteria: Battery level, signal strength, capabilities
        return devices.maxByOrNull { device ->
            var score = 0
            if (device.isGroupOwner) score += 50
            if (device.deviceName.contains("Relay")) score += 30
            score
        } ?: devices.first()
    }
}
```

**Required Permissions:**
```xml
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
<uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" 
    android:maxSdkVersion="32" />
<uses-permission android:name="android.permission.NEARBY_WIFI_DEVICES"
    android:usesPermissionFlags="neverForLocation" />
```

### 3.2 WiFi Aware (NAN - Neighbor Awareness Networking)

**WiFi Aware Features:**
- Service discovery without connection
- Range-based discovery (WiFi RTT integration)
- Low power background operation
- Supports publish/subscribe pattern

**WiFi Aware vs WiFi Direct:**

| Feature | WiFi Direct | WiFi Aware |
|---------|-------------|------------|
| Discovery | Device-based | Service-based |
| Range | Standard WiFi | Standard WiFi |
| Ranging | No | Yes (RTT) |
| Power | Higher | Lower |
| Connection | Always forms group | Data path on demand |
| Android Version | API 14+ | API 26+ |

**WiFi Aware Implementation:**

```kotlin
class WiFiAwareManager(private val context: Context) {
    
    private val awareManager: WifiAwareManager? = 
        context.getSystemService(Context.WIFI_AWARE_SERVICE) as? WifiAwareManager
    private var awareSession: WifiAwareSession? = null
    
    // PTT Service Configuration
    companion object {
        const val PTT_SERVICE_NAME = "_meshrider_ptt._tcp"
        const val PTT_SERVICE_INFO = "MeshRider PTT Service"
    }
    
    fun attach() {
        awareManager?.attach(
            object : AttachCallback() {
                override fun onAttached(session: WifiAwareSession) {
                    awareSession = session
                    publishPTTService()
                    subscribeToPTTServices()
                }
                override fun onAttachFailed() {
                    Log.e(TAG, "WiFi Aware attach failed")
                }
            },
            null  // No config required
        )
    }
    
    // Publish PTT service (for other devices to discover)
    fun publishPTTService() {
        val config = PublishConfig.Builder()
            .setServiceName(PTT_SERVICE_NAME)
            .setServiceSpecificInfo(PTT_SERVICE_INFO.toByteArray())
            .setPublishType(PublishConfig.PUBLISH_TYPE_UNSOLICITED)
            .build()
        
        awareSession?.publish(config, object : DiscoverySessionCallback() {
            override fun onPublishStarted(session: PublishDiscoverySession) {
                // Ready to accept PTT connections
            }
            
            override fun onMessageReceived(peerHandle: PeerHandle, message: ByteArray) {
                // Received message from subscriber
                handlePTTMessage(peerHandle, message)
            }
        }, null)
    }
    
    // Subscribe to PTT services (discover other devices)
    fun subscribeToPTTServices() {
        val config = SubscribeConfig.Builder()
            .setServiceName(PTT_SERVICE_NAME)
            .setSubscribeType(SubscribeConfig.SUBSCRIBE_TYPE_PASSIVE)
            .build()
        
        awareSession?.subscribe(config, object : DiscoverySessionCallback() {
            override fun onServiceDiscovered(
                peerHandle: PeerHandle,
                serviceSpecificInfo: ByteArray,
                matchFilter: List<ByteArray>
            ) {
                // Found a PTT peer
                val peerInfo = PeerInfo(
                    peerHandle = peerHandle,
                    serviceInfo = String(serviceSpecificInfo)
                )
                onPeerDiscovered(peerInfo)
            }
            
            override fun onServiceLost(peerHandle: PeerHandle) {
                onPeerLost(peerHandle)
            }
            
            override fun onServiceDiscoveredWithinRange(
                peerHandle: PeerHandle,
                serviceSpecificInfo: ByteArray,
                matchFilter: List<ByteArray>,
                distanceMm: Int
            ) {
                // Distance-aware discovery (Android 12+)
                onPeerDiscoveredInRange(peerHandle, distanceMm)
            }
        }, null)
    }
    
    // Create data path for PTT communication
    fun createDataPath(
        peerHandle: PeerHandle,
        networkCallback: ConnectivityManager.NetworkCallback
    ) {
        val networkSpecifier = WifiAwareNetworkSpecifier.Builder(
            discoverySession, peerHandle
        )
            .setPskPassphrase("ptt_secure_passphrase")
            .build()
        
        val networkRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI_AWARE)
            .setNetworkSpecifier(networkSpecifier)
            .build()
        
        val connectivityManager = context.getSystemService(
            Context.CONNECTIVITY_SERVICE
        ) as ConnectivityManager
        
        connectivityManager.requestNetwork(networkRequest, networkCallback)
    }
    
    // Geofenced discovery (for tactical scenarios)
    fun subscribeWithGeofence(minDistanceM: Int, maxDistanceM: Int) {
        val config = SubscribeConfig.Builder()
            .setServiceName(PTT_SERVICE_NAME)
            .setMinDistanceMm(minDistanceM * 1000)
            .setMaxDistanceMm(maxDistanceM * 1000)
            .build()
        
        // Only discovers peers within geofence
        awareSession?.subscribe(config, discoveryCallback, null)
    }
}
```

### 3.3 Android Nearby Connections API

**Nearby Connections API combines multiple technologies:**
```
┌─────────────────────────────────────────┐
│  Nearby Connections API                 │
├─────────────┬─────────────┬─────────────┤
│  Bluetooth  │  WiFi LAN   │  WebRTC     │
│  (Classic   │  (for AP    │  (for       │
│   & BLE)    │   networks) │  internet)  │
└─────────────┴─────────────┴─────────────┘
```

**Nearby Connections for PTT:**

```kotlin
class NearbyPTTManager(private val context: Context) {
    
    private val connectionsClient = Nearby.getConnectionsClient(context)
    private val serviceId = "com.doodlelabs.meshrider.ptt"
    
    fun startAdvertising() {
        val advertisingOptions = AdvertisingOptions.Builder()
            .setStrategy(Strategy.P2P_CLUSTER)  // Mesh topology
            .build()
        
        connectionsClient.startAdvertising(
            getLocalDeviceName(),
            serviceId,
            connectionLifecycleCallback,
            advertisingOptions
        )
    }
    
    fun startDiscovery() {
        val discoveryOptions = DiscoveryOptions.Builder()
            .setStrategy(Strategy.P2P_CLUSTER)
            .build()
        
        connectionsClient.startDiscovery(
            serviceId,
            endpointDiscoveryCallback,
            discoveryOptions
        )
    }
    
    fun requestConnection(endpointId: String) {
        connectionsClient.requestConnection(
            getLocalDeviceName(),
            endpointId,
            connectionLifecycleCallback
        )
    }
    
    fun sendPTTAudio(audioData: ByteArray, endpoints: List<String>) {
        connectionsClient.sendPayload(
            endpoints,
            Payload.fromBytes(audioData)
        )
    }
    
    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            // Auto-accept connections in tactical scenarios
            connectionsClient.acceptConnection(endpointId, payloadCallback)
        }
        
        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                onPeerConnected(endpointId)
            }
        }
        
        override fun onDisconnected(endpointId: String) {
            onPeerDisconnected(endpointId)
        }
    }
    
    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            when (payload.type) {
                Payload.Type.BYTES -> {
                    val audioData = payload.asBytes()
                    playReceivedAudio(audioData)
                }
                Payload.Type.STREAM -> {
                    // Handle streaming PTT
                }
                Payload.Type.FILE -> {
                    // Handle file transfer
                }
            }
        }
        
        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            // Track transfer progress
        }
    }
}
```

### 3.4 Bluetooth Low Energy (BLE) for Discovery

**BLE for Mesh Discovery:**

```kotlin
class BLEDiscoveryManager(private val context: Context) {
    
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) 
        as BluetoothManager
    private val bluetoothAdapter = bluetoothManager.adapter
    
    // BLE Service UUID for MeshRider
    companion object {
        val MESH_SERVICE_UUID = UUID.fromString("0000MESHRIDER-0000-1000-8000-00805F9B34FB")
        val PTT_CHARACTERISTIC_UUID = UUID.fromString("0000PTT-0000-1000-8000-00805F9B34FB")
        
        // Advertising packet format
        const val ADV_PACKET_SIZE = 31  // BLE advertising limit
    }
    
    // Start BLE advertising with mesh info
    @SuppressLint("MissingPermission")
    fun startAdvertising(meshInfo: MeshInfo) {
        val advertiser = bluetoothAdapter.bluetoothLeAdvertiser
        
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .setTimeout(0)  // Advertise indefinitely
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .build()
        
        // Encode mesh info into advertisement data
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceUuid(ParcelUuid(MESH_SERVICE_UUID))
            .addServiceData(
                ParcelUuid(MESH_SERVICE_UUID),
                encodeMeshAdvertisement(meshInfo)
            )
            .build()
        
        advertiser.startAdvertising(settings, data, advertiseCallback)
    }
    
    // Scan for BLE mesh devices
    @SuppressLint("MissingPermission")
    fun startScanning() {
        val scanner = bluetoothAdapter.bluetoothLeScanner
        
        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(MESH_SERVICE_UUID))
            .build()
        
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .build()
        
        scanner.startScan(listOf(filter), settings, scanCallback)
    }
    
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val serviceData = result.scanRecord?.getServiceData(
                ParcelUuid(MESH_SERVICE_UUID)
            )
            
            if (serviceData != null) {
                val meshInfo = decodeMeshAdvertisement(serviceData)
                val rssi = result.rssi
                
                onMeshDeviceDiscovered(meshInfo, rssi)
            }
        }
    }
    
    // Encode mesh info into 20-byte advertisement
    private fun encodeMeshAdvertisement(info: MeshInfo): ByteArray {
        return ByteArray(20).apply {
            // 6 bytes: Device ID (truncated MAC)
            info.deviceId.take(6).forEachIndexed { i, b -> this[i] = b }
            
            // 4 bytes: IP address (last 4 octets of 10.223.x.x)
            info.ipAddress.toIpBytes().copyInto(this, 6)
            
            // 2 bytes: Channel
            this[10] = (info.channel shr 8).toByte()
            this[11] = (info.channel and 0xFF).toByte()
            
            // 2 bytes: TQ (BATMAN metric)
            this[12] = (info.tq shr 8).toByte()
            this[13] = (info.tq and 0xFF).toByte()
            
            // 2 bytes: Hop count
            this[14] = info.hopCount.toByte()
            
            // 4 bytes: Service capabilities bitmap
            this[15] = if (info.supportsPTT) 0x01 else 0x00
            this[16] = if (info.supportsVideo) 0x01 else 0x00
            this[17] = if (info.supportsData) 0x01 else 0x00
            this[18] = if (info.isGateway) 0x01 else 0x00
            
            // 1 byte: Reserved
            this[19] = 0x00
        }
    }
}
```

### 3.5 Hybrid WiFi/BT Mesh Strategies

**Multi-Transport Architecture:**
```
┌─────────────────────────────────────────────────────┐
│  Application Layer (PTT, Messaging, Location)       │
├─────────────────────────────────────────────────────┤
│  Transport Selection Logic                          │
│  - Priority: Direct WiFi > Mesh > BLE > Internet   │
├─────────────┬─────────────────┬─────────────────────┤
│  WiFi       │  BATMAN-adv     │  Long range,        │
│  (Primary)  │  MeshRider      │  high throughput    │
├─────────────┼─────────────────┼─────────────────────┤
│  WiFi       │  WiFi Direct/   │  Direct device      │
│  (Fallback) │  Aware          │  connections        │
├─────────────┼─────────────────┼─────────────────────┤
│  Bluetooth  │  BLE GATT       │  Discovery,         │
│  (Discovery)│  Mesh           │  low bandwidth      │
└─────────────┴─────────────────┴─────────────────────┘
```

**Transport Selection Algorithm:**

```kotlin
class HybridTransportManager {
    
    data class TransportCapabilities(
        val type: TransportType,
        val range: Int,           // meters
        val throughput: Int,      // kbps
        val latency: Int,         // ms
        val powerConsumption: PowerLevel,
        val reliability: Float    // 0-1
    )
    
    enum class TransportType {
        BATMAN_MESH,      // Primary - MeshRider radio
        WIFI_DIRECT,      // Secondary - Direct P2P
        WIFI_AWARE,       // Secondary - NAN
        BLE_MESH,         // Tertiary - Low power
        INTERNET          // Fallback - Cloud relay
    }
    
    fun selectTransportForPTT(
        destination: Device,
        priority: MessagePriority
    ): TransportType {
        // Check available transports to destination
        val available = getAvailableTransports(destination)
        
        return when {
            // Priority 1: BATMAN mesh (if connected via MeshRider)
            available.contains(TransportType.BATMAN_MESH) &&
            getMeshHops(destination) <= 3 -> {
                TransportType.BATMAN_MESH
            }
            
            // Priority 2: WiFi Direct (if in range)
            available.contains(TransportType.WIFI_DIRECT) -> {
                TransportType.WIFI_DIRECT
            }
            
            // Priority 3: WiFi Aware (for discovery + data)
            available.contains(TransportType.WIFI_AWARE) -> {
                TransportType.WIFI_AWARE
            }
            
            // Priority 4: BLE Mesh (low power, short messages)
            available.contains(TransportType.BLE_MESH) &&
            priority == MessagePriority.LOW -> {
                TransportType.BLE_MESH
            }
            
            // Fallback: Internet/cloud relay
            else -> TransportType.INTERNET
        }
    }
    
    // Adaptive transport switching based on conditions
    fun adaptTransport(current: TransportType, metrics: LinkMetrics): TransportType {
        return when {
            current == TransportType.BATMAN_MESH && metrics.tq < 100 -> {
                // Mesh quality degraded, try WiFi Direct
                TransportType.WIFI_DIRECT
            }
            current == TransportType.WIFI_DIRECT && metrics.rssi < -80 -> {
                // WiFi Direct range exceeded, fallback to mesh
                TransportType.BATMAN_MESH
            }
            else -> current
        }
    }
}
```

---

## 4. MULTIHOP ROUTING

### 4.1 Store-and-Forward Messaging

**Store-and-Forward Architecture:**
```
Scenario: Message from A to C (no direct link)

A ──► B (stores message) ──► C (delivers)
    
Time t1: A sends to B, B stores (A out of range of C)
Time t2: B forwards to C when C is in range

Store-and-Forward Queue:
┌─────────────────────────────────────────┐
│  Message ID  │  Dest  │  Expiry  │  Retries │
├─────────────────────────────────────────┤
│  msg_001     │  NodeC │  T+3600  │  0       │  ← Pending
│  msg_002     │  NodeD │  T+1800  │  2       │  ← Retry scheduled
│  msg_003     │  NodeE │  T+7200  │  1       │  ← Forwarded
└─────────────────────────────────────────┘
```

**Implementation:**

```kotlin
class StoreAndForwardManager(
    private val database: MessageDatabase,
    private val transport: TransportLayer
) {
    
    data class PendingMessage(
        val id: String,
        val destination: DeviceId,
        val payload: ByteArray,
        val priority: Priority,
        val createdAt: Long,
        val expiryAt: Long,
        val maxRetries: Int,
        val retryCount: Int = 0,
        val status: MessageStatus
    )
    
    suspend fun sendMessage(message: OutboundMessage): Result<String> {
        // Try immediate delivery
        val immediate = transport.send(message)
        
        if (immediate.isSuccess) {
            return Result.success(message.id)
        }
        
        // Store for later delivery
        val pending = PendingMessage(
            id = message.id,
            destination = message.destination,
            payload = message.payload,
            priority = message.priority,
            createdAt = System.currentTimeMillis(),
            expiryAt = System.currentTimeMillis() + message.ttl * 1000,
            maxRetries = if (message.priority == Priority.EMERGENCY) 10 else 3,
            status = MessageStatus.PENDING
        )
        
        database.storeMessage(pending)
        
        // Schedule retry
        scheduleRetry(pending)
        
        return Result.success(message.id)  // Accepted for delivery
    }
    
    suspend fun onNeighborDiscovered(neighbor: Device) {
        // Check if we have pending messages for this neighbor
        val pending = database.getPendingMessagesForDestination(neighbor.id)
        
        pending.forEach { message ->
            try {
                val result = transport.send(message)
                if (result.isSuccess) {
                    database.markDelivered(message.id)
                } else {
                    scheduleRetry(message)
                }
            } catch (e: Exception) {
                scheduleRetry(message)
            }
        }
    }
    
    private fun scheduleRetry(message: PendingMessage) {
        if (message.retryCount >= message.maxRetries) {
            database.markFailed(message.id)
            return
        }
        
        // Exponential backoff
        val delay = calculateBackoff(message.retryCount)
        
        CoroutineScope(Dispatchers.Default).launch {
            delay(delay)
            
            // Check if destination is now reachable
            if (transport.isReachable(message.destination)) {
                attemptDelivery(message)
            } else {
                // Keep in queue, will retry on next contact
                database.incrementRetry(message.id)
            }
        }
    }
    
    private fun calculateBackoff(retryCount: Int): Long {
        // Exponential backoff: 1s, 2s, 4s, 8s, 16s...
        val baseDelay = 1000L
        return min(baseDelay * (1 shl retryCount), 60000L)  // Max 60s
    }
}
```

### 4.2 Delay/Disruption Tolerant Networking (DTN)

**DTN Architecture (RFC 4838, RFC 5050):**
```
┌─────────────────────────────────────────┐
│  Application Layer                      │
│  - PTT, messaging, file transfer        │
├─────────────────────────────────────────┤
│  Bundle Protocol Agent (BPA)            │
│  - Custody transfer                     │
│  - Fragmentation/reassembly             │
│  - Scheduling                           │
├─────────────────────────────────────────┤
│  Convergence Layer Adapters             │
│  - TCPCL (TCP Convergence Layer)        │
│  - LTP (Licklider Transmission Protocol)│
│  - UDP                                  │
└─────────────────────────────────────────┘
```

**Bundle Protocol Concepts:**

| Concept | Description |
|---------|-------------|
| Bundle | DTN protocol data unit (message) |
| Endpoint ID | URI identifying bundle destination |
| Custody Transfer | Guaranteed delivery via acknowledgment |
| Fragmentation | Split large bundles for unreliable links |
| Contact Graph Routing | Route based on predicted contacts |

**Bundle Header Format:**
```
Primary Bundle Block:
┌──────────┬─────────────┬────────────────────────┐
│ Version  │ Proc. Flags │ Block Length           │
├──────────┼─────────────┼────────────────────────┤
│ Destination EID (offset)                        │
├─────────────────────────────────────────────────┤
│ Source EID (offset)                             │
├─────────────────────────────────────────────────┤
│ Creation Timestamp (time + sequence)            │
├─────────────────────────────────────────────────┤
│ Lifetime                                        │
├─────────────────────────────────────────────────┤
│ Dictionary (scheme names + SSPs)                │
└─────────────────────────────────────────────────┘
```

**DTN Implementation for Tactical PTT:**

```kotlin
class DTNManager {
    
    data class Bundle(
        val version: Int = 6,  // BPv6
        val processingFlags: ProcessingFlags,
        val destination: EndpointId,
        val source: EndpointId,
        val reportTo: EndpointId,
        val creationTime: Long,
        val sequenceNumber: Long,
        val lifetime: Long,  // seconds
        val payload: ByteArray,
        val fragments: List<Fragment>? = null
    )
    
    data class ProcessingFlags(
        val isFragment: Boolean = false,
        val custodyTransferRequested: Boolean = true,
        val isAdminRecord: Boolean = false,
        val doNotFragment: Boolean = false,
        val priority: BundlePriority = BundlePriority.NORMAL,
        val requestReports: ReportRequest = ReportRequest()
    )
    
    // Create bundle for PTT audio
    fun createPTTBundle(
        audioData: ByteArray,
        destination: EndpointId,
        priority: Priority
    ): Bundle {
        return Bundle(
            processingFlags = ProcessingFlags(
                custodyTransferRequested = true,
                priority = when (priority) {
                    Priority.EMERGENCY -> BundlePriority.EXPEDITED
                    Priority.HIGH -> BundlePriority.NORMAL
                    else -> BundlePriority.BULK
                },
                requestReports = ReportRequest(
                    receptionReport = true,
                    custodyReport = true,
                    forwardingReport = false,
                    deliveryReport = true
                )
            ),
            destination = destination,
            source = localEndpointId,
            creationTime = System.currentTimeMillis() / 1000,
            sequenceNumber = nextSequenceNumber(),
            lifetime = when (priority) {
                Priority.EMERGENCY -> 3600  // 1 hour
                Priority.HIGH -> 7200       // 2 hours
                else -> 86400               // 24 hours
            },
            payload = audioData
        )
    }
    
    // Custody transfer - reliable delivery
    suspend fun sendWithCustody(bundle: Bundle): CustodyResult {
        val nextHop = routingTable.getNextHop(bundle.destination)
        
        // Send bundle
        val result = convergenceLayer.send(bundle, nextHop)
        
        if (result.isSuccess) {
            if (bundle.processingFlags.custodyTransferRequested) {
                // Wait for custody acceptance
                val custodySignal = waitForCustodySignal(bundle.id, timeout = 30_000)
                
                return if (custodySignal?.accepted == true) {
                    // Custody transferred, we're no longer responsible
                    CustodyResult.TRANSFERRED
                } else {
                    // Custody refused, retry
                    CustodyResult.REFUSED
                }
            }
        }
        
        return CustodyResult.FAILED
    }
}
```

### 4.3 Bundle Protocol (RFC 5050)

**Bundle Protocol Blocks:**

| Block Type | Code | Purpose |
|------------|------|---------|
| Primary | 0 | Routing info, timestamps |
| Payload | 1 | Application data |
| Bundle Security | 2 | Authentication, encryption |
| Previous Node | 3 | Trace route |
| Bundle Age | 4 | Calculate remaining lifetime |
| ... | 5+ | Extension blocks |

**Convergence Layer Adapters:**

```kotlin
interface ConvergenceLayer {
    suspend fun send(bundle: Bundle, destination: NodeId): Result<Unit>
    suspend fun receive(): Flow<Bundle>
    fun isReachable(destination: NodeId): Boolean
}

class TCPConvergenceLayer : ConvergenceLayer {
    
    override suspend fun send(bundle: Bundle, destination: NodeId): Result<Unit> {
        // TCPCL contact header
        val contactHeader = byteArrayOf(
            0x01,  // Version
            0x00,  // Flags
            0x00,  // Keepalive
            0x00, 0x00  // Segment length
        )
        
        // Send contact header + bundle
        return try {
            socket.getOutputStream().write(contactHeader)
            socket.getOutputStream().write(serializeBundle(bundle))
            Result.success(Unit)
        } catch (e: IOException) {
            Result.failure(e)
        }
    }
}

class LTPConvergenceLayer : ConvergenceLayer {
    // LTP - for deep space / highly disrupted links
    // Provides reliable transmission over unreliable underlying transport
    
    override suspend fun send(bundle: Bundle, destination: NodeId): Result<Unit> {
        // LTP segment: Red part (reliable) + Green part (unreliable)
        val segments = createLTPSegments(bundle)
        
        // Send red parts with acknowledgment
        segments.redParts.forEach { segment ->
            transmitWithAck(segment)
        }
        
        // Send green parts best-effort
        segments.greenParts.forEach { segment ->
            transmit(segment)
        }
        
        return Result.success(Unit)
    }
}
```

### 4.4 Flooding Protocols for Emergency Broadcast

**Emergency Broadcast Requirements:**
- Guaranteed delivery to all reachable nodes
- Rapid propagation (< 2 seconds for network diameter)
- Duplicate suppression
- Priority handling

**Optimized Flooding (Trickle + MPR):**

```kotlin
class EmergencyFloodingProtocol {
    
    data class EmergencyMessage(
        val id: String,              // Unique ID for deduplication
        val originator: DeviceId,
        val timestamp: Long,
        val priority: EmergencyPriority,
        val payload: ByteArray,
        val ttl: Int,                // Hops remaining
        val sequence: Long           // For ordering
    )
    
    // MPR-based efficient flooding
    suspend fun floodEmergency(message: EmergencyMessage) {
        // Get MPR set (from OLSR/BATMAN)
        val mprSet = routingProtocol.getMPRSet()
        
        // Only MPRs forward to minimize transmissions
        if (isMPR() || message.originator == localDeviceId) {
            val neighbors = getAllSymmetricNeighbors()
            
            neighbors.forEach { neighbor ->
                if (neighbor !in receivedMessages.getSources(message.id)) {
                    transport.send(message.copy(ttl = message.ttl - 1), neighbor)
                }
            }
        }
    }
    
    // Trickle algorithm for metadata consistency
    class TrickleTimer(
        private val minInterval: Long = 100,   // ms
        private val maxInterval: Long = 10000, // ms
        private val redundancy: Int = 3
    ) {
        private var interval = minInterval
        private var counter = 0
        
        fun onHeardConsistent() {
            counter++
        }
        
        fun onHeardInconsistent() {
            // Reset to fast transmission
            interval = minInterval
            counter = 0
            transmit()
        }
        
        fun onTimeout() {
            if (counter < redundancy) {
                transmit()
            }
            
            // Double interval (up to max)
            interval = min(interval * 2, maxInterval)
            counter = 0
        }
    }
}
```

**Epidemic Routing (for very sparse networks):**

```kotlin
class EpidemicRouting {
    
    // Store-carry-forward with transitive delivery
    data class MessageVector(
        val messageId: String,
        val destination: DeviceId,
        val hopCount: Int,
        val timestamp: Long,
        val size: Int
    )
    
    // When two nodes meet, exchange summaries
    suspend fun onContact(peer: Device) {
        // Send summary vector (message IDs we have)
        val mySummary = getSummaryVector()
        peer.sendSummaryVector(mySummary)
        
        // Receive peer's summary
        val peerSummary = peer.receiveSummaryVector()
        
        // Determine messages to exchange
        val iWant = peerSummary.filter { it !in mySummary }
        val peerWants = mySummary.filter { it !in peerSummary }
        
        // Exchange messages
        iWant.forEach { id ->
            val message = getMessage(id)
            if (shouldForwardTo(message, peer)) {
                peer.sendMessage(message)
            }
        }
        
        // Anti-entropy: delete acknowledged messages
        ackMessages(peerWants)
    }
    
    // Vaccination: stop forwarding delivered messages
    fun onDeliveryConfirmation(messageId: String) {
        val message = getMessage(messageId)
        message.immunityList.add(localDeviceId)
        
        // If enough nodes immune, delete from buffer
        if (message.immunityList.size >= IMMUNITY_THRESHOLD) {
            deleteMessage(messageId)
        }
    }
}
```

---

## 5. PERFORMANCE OPTIMIZATION

### 5.1 Link Quality Metrics (RSSI, SNR, ETX)

**Comprehensive Link Metrics:**

```kotlin
class LinkQualityMetrics {
    
    data class LinkMetrics(
        // Signal strength
        val rssi: Int,              // dBm (-30 to -100 typical)
        val noise: Int,             // dBm (-95 typical)
        val snr: Int,               // dB (RSSI - Noise)
        
        // Link quality indicators
        val lq: Float,              // Link Quality (0.0 - 1.0)
        val nlq: Float,             // Neighbor Link Quality (0.0 - 1.0)
        val etx: Float,             // Expected Transmission Count
        
        // Traffic statistics
        val txPackets: Long,
        val txRetries: Long,
        val txFailed: Long,
        val rxPackets: Long,
        val rxErrors: Long,
        
        // Rate info
        val txRate: Int,            // kbps
        val rxRate: Int,            // kbps
        val mcs: Int,               // Modulation Coding Scheme
        
        // Temporal
        val inactive: Int,          // ms since last seen
        val lastSeen: Long
    )
    
    // RSSI to Link Quality mapping
    fun rssiToQuality(rssi: Int): Float {
        return when {
            rssi >= -50 -> 1.0f      // Excellent
            rssi >= -60 -> 0.8f      // Good
            rssi >= -70 -> 0.6f      // Fair
            rssi >= -80 -> 0.4f      // Poor
            rssi >= -90 -> 0.2f      // Bad
            else -> 0.0f             // Unusable
        }
    }
    
    // ETX calculation (from OLSR)
    fun calculateETX(lq: Float, nlq: Float): Float {
        // ETX = 1 / (LQ × NLQ)
        return if (lq > 0 && nlq > 0) {
            1.0f / (lq * nlq)
        } else {
            Float.MAX_VALUE
        }
    }
    
    // Airtime Link Metric (802.11s)
    fun calculateAirtimeMetric(
        packetErrorRate: Float,
        phyRate: Int,      // Mbps
        channelWidth: Int  // MHz
    ): Int {
        // Airtime = [O + D/r] / (1 - p)
        // O = protocol overhead
        // D = packet size
        // r = rate
        // p = error probability
        
        val o = 100.0  // overhead in microseconds
        val d = 1500.0 * 8  // typical packet in bits
        val r = phyRate * 1000000.0  // bps
        val p = packetErrorRate
        
        val airtime = (o + (d / r) * 1000000) / (1 - p)
        return airtime.toInt()
    }
    
    // Composite link score for routing decisions
    fun calculateLinkScore(metrics: LinkMetrics): Float {
        // Weighted combination of factors
        val snrScore = (metrics.snr + 100).coerceIn(0, 100) / 100f
        val etxScore = (1.0f / metrics.etx).coerceIn(0f, 1f)
        val stabilityScore = calculateStabilityScore(metrics)
        
        return (snrScore * 0.4f + etxScore * 0.4f + stabilityScore * 0.2f)
    }
    
    private fun calculateStabilityScore(metrics: LinkMetrics): Float {
        val totalTx = metrics.txPackets + metrics.txRetries + metrics.txFailed
        val successRate = if (totalTx > 0) {
            metrics.txPackets.toFloat() / totalTx
        } else 1.0f
        
        return successRate
    }
}
```

### 5.2 Adaptive Bitrate Based on Link Conditions

**Adaptive Bitrate Controller:**

```kotlin
class AdaptiveBitrateController {
    
    data class BitrateConfig(
        val audioBitrate: Int,      // Opus bitrate in bps
        val fec: Boolean,           // Forward Error Correction
        val dtx: Boolean,           // Discontinuous Transmission
        val packetLossConcealment: Boolean,
        val frameSize: Int          // ms (10, 20, 40, 60)
    )
    
    // Bitrate adaptation table
    val bitrateTable = mapOf(
        LinkCondition.EXCELLENT to BitrateConfig(
            audioBitrate = 24000,   // High quality
            fec = false,
            dtx = true,
            packetLossConcealment = false,
            frameSize = 20
        ),
        LinkCondition.GOOD to BitrateConfig(
            audioBitrate = 16000,
            fec = false,
            dtx = true,
            packetLossConcealment = true,
            frameSize = 20
        ),
        LinkCondition.FAIR to BitrateConfig(
            audioBitrate = 12000,
            fec = true,
            dtx = false,
            packetLossConcealment = true,
            frameSize = 40
        ),
        LinkCondition.POOR to BitrateConfig(
            audioBitrate = 8000,
            fec = true,
            dtx = false,
            packetLossConcealment = true,
            frameSize = 60
        ),
        LinkCondition.UNUSABLE to BitrateConfig(
            audioBitrate = 6000,    // Minimum viable
            fec = true,
            dtx = false,
            packetLossConcealment = true,
            frameSize = 60
        )
    )
    
    fun adaptBitrate(metrics: LinkMetrics): BitrateConfig {
        val condition = classifyLinkCondition(metrics)
        return bitrateTable[condition] ?: bitrateTable[LinkCondition.FAIR]!!
    }
    
    private fun classifyLinkCondition(metrics: LinkMetrics): LinkCondition {
        return when {
            metrics.snr >= 25 && metrics.etx < 1.5f -> LinkCondition.EXCELLENT
            metrics.snr >= 20 && metrics.etx < 2.0f -> LinkCondition.GOOD
            metrics.snr >= 15 && metrics.etx < 3.0f -> LinkCondition.FAIR
            metrics.snr >= 10 && metrics.etx < 5.0f -> LinkCondition.POOR
            else -> LinkCondition.UNUSABLE
        }
    }
    
    // Dynamic adjustment based on real-time feedback
    fun dynamicAdjustment(
        currentConfig: BitrateConfig,
        feedback: NetworkFeedback
    ): BitrateConfig {
        return when {
            // Increase bitrate if network is stable
            feedback.packetLoss < 0.01 && feedback.jitter < 50 -> {
                currentConfig.copy(
                    audioBitrate = min(currentConfig.audioBitrate + 2000, 24000)
                )
            }
            
            // Decrease bitrate on packet loss
            feedback.packetLoss > 0.05 -> {
                currentConfig.copy(
                    audioBitrate = max(currentConfig.audioBitrate - 4000, 6000),
                    fec = true
                )
            }
            
            // Enable FEC on moderate loss
            feedback.packetLoss > 0.02 && !currentConfig.fec -> {
                currentConfig.copy(fec = true)
            }
            
            else -> currentConfig
        }
    }
}
```

### 5.3 Route Optimization Algorithms

**Dijkstra with Link Metrics:**

```kotlin
class RouteOptimizer {
    
    data class NetworkGraph(
        val nodes: Set<NodeId>,
        val edges: Map<NodeId, List<Edge>>
    )
    
    data class Edge(
        val to: NodeId,
        val metric: Float,      // Lower is better
        val bandwidth: Int,     // kbps
        val latency: Int        // ms
    )
    
    // Dijkstra's algorithm with ETX metric
    fun computeShortestPaths(graph: NetworkGraph, source: NodeId): Map<NodeId, Path> {
        val distances = mutableMapOf<NodeId, Float>().withDefault { Float.MAX_VALUE }
        val previous = mutableMapOf<NodeId, NodeId?>()
        val unvisited = graph.nodes.toMutableSet()
        
        distances[source] = 0f
        
        while (unvisited.isNotEmpty()) {
            val current = unvisited.minByOrNull { distances.getValue(it) } ?: break
            unvisited.remove(current)
            
            if (distances.getValue(current) == Float.MAX_VALUE) break
            
            for (edge in graph.edges[current] ?: emptyList()) {
                if (edge.to !in unvisited) continue
                
                val newDist = distances.getValue(current) + edge.metric
                if (newDist < distances.getValue(edge.to)) {
                    distances[edge.to] = newDist
                    previous[edge.to] = current
                }
            }
        }
        
        // Build path map
        return graph.nodes.associateWith { node ->
            reconstructPath(previous, source, node)
        }
    }
    
    // Multi-objective optimization (bandwidth, latency, reliability)
    fun computeParetoOptimalPaths(
        graph: NetworkGraph,
        source: NodeId,
        destination: NodeId
    ): List<Path> {
        // Modified Dijkstra tracking multiple objectives
        val frontier = mutableListOf<PathState>()
        frontier.add(PathState(source, 0f, 0, 0, emptyList()))
        
        val solutions = mutableListOf<Path>()
        
        while (frontier.isNotEmpty()) {
            val current = frontier.removeAt(0)
            
            if (current.node == destination) {
                solutions.add(Path(current.path, current.metric, current.bandwidth, current.latency))
                continue
            }
            
            for (edge in graph.edges[current.node] ?: emptyList()) {
                val newState = PathState(
                    node = edge.to,
                    metric = current.metric + edge.metric,
                    bandwidth = min(current.bandwidth, edge.bandwidth),
                    latency = current.latency + edge.latency,
                    path = current.path + edge.to
                )
                
                // Check if dominated by existing solution
                if (!isDominated(newState, frontier)) {
                    frontier.add(newState)
                    frontier.removeAll { isDominated(it, listOf(newState)) }
                }
            }
        }
        
        return solutions
    }
    
    private fun isDominated(state: PathState, others: List<PathState>): Boolean {
        return others.any { other ->
            other.metric <= state.metric &&
            other.bandwidth >= state.bandwidth &&
            other.latency <= state.latency &&
            (other.metric < state.metric ||
             other.bandwidth > state.bandwidth ||
             other.latency < state.latency)
        }
    }
}
```

### 5.4 Congestion Control in Mesh Networks

**Mesh-Aware Congestion Control:**

```kotlin
class MeshCongestionController {
    
    data class CongestionState(
        val queueDepth: Int,
        val dropRate: Float,
        val linkUtilization: Float,
        val roundTripTime: Long
    )
    
    // AIMD (Additive Increase Multiplicative Decrease) for mesh
    class AIMDController(
        private val initialRate: Int = 64000,  // 64 kbps
        private val maxRate: Int = 1000000,     // 1 Mbps
        private val minRate: Int = 8000         // 8 kbps
    ) {
        private var currentRate = initialRate
        private var additiveStep = 8000         // 8 kbps increase
        private var multiplicativeFactor = 0.5f
        
        fun onSuccess() {
            currentRate = min(currentRate + additiveStep, maxRate)
        }
        
        fun onCongestion() {
            currentRate = max((currentRate * multiplicativeFactor).toInt(), minRate)
        }
        
        fun getCurrentRate(): Int = currentRate
    }
    
    // Bufferbloat control
    class CoDelAlgorithm(
        private val targetQueueDelay: Long = 5,  // ms
        private val interval: Long = 100         // ms
    ) {
        private var firstAboveTime: Long? = null
        private var dropping = false
        private var dropCount = 0
        private var lastDropTime: Long = 0
        
        fun shouldDrop(packet: Packet, now: Long): Boolean {
            val sojournTime = now - packet.enqueueTime
            
            return if (sojournTime < targetQueueDelay || packet.queueLength < 2) {
                // Packet left quickly or queue is small
                firstAboveTime = null
                false
            } else {
                // Queue is building up
                if (firstAboveTime == null) {
                    firstAboveTime = now
                }
                
                if (now - firstAboveTime!! >= interval) {
                    // We have been above target for interval, start dropping
                    dropping = true
                    dropCount++
                    lastDropTime = now
                    true
                } else {
                    false
                }
            }
        }
    }
    
    // Fair queueing for PTT traffic
    class FairQueueScheduler {
        
        data class Flow(
            val id: String,
            val priority: TrafficPriority,
            val virtualTime: Long,
            val packets: Queue<Packet>
        )
        
        enum class TrafficPriority {
            EMERGENCY_PTT,      // Highest - always first
            NORMAL_PTT,         // High
            VIDEO,              // Medium
            DATA,               // Low
            BACKGROUND          // Lowest
        }
        
        private val flows = mutableMapOf<String, Flow>()
        private var currentVirtualTime = 0L
        
        fun enqueue(packet: Packet, flowId: String, priority: TrafficPriority) {
            val flow = flows.getOrPut(flowId) {
                Flow(flowId, priority, currentVirtualTime, LinkedList())
            }
            flow.packets.add(packet)
        }
        
        fun dequeue(): Packet? {
            // Find flow with minimum virtual time
            val selectedFlow = flows.values
                .filter { it.packets.isNotEmpty() }
                .minByOrNull { it.virtualTime }
                ?: return null
            
            val packet = selectedFlow.packets.poll()
            
            // Update virtual time based on packet size and priority weight
            val weight = when (selectedFlow.priority) {
                TrafficPriority.EMERGENCY_PTT -> 8
                TrafficPriority.NORMAL_PTT -> 4
                TrafficPriority.VIDEO -> 2
                TrafficPriority.DATA -> 1
                TrafficPriority.BACKGROUND -> 1
            }
            
            selectedFlow.virtualTime += packet.size / weight
            currentVirtualTime = selectedFlow.virtualTime
            
            return packet
        }
    }
}
```

---

## 6. COMMUNICATION WITHOUT CELLULAR INFRASTRUCTURE

### 6.1 Standalone Mesh Network Modes

**Mesh Network Topologies:**

```
1. Fully Distributed (Ad-hoc):
   
   A ←──→ B ←──→ C ←──→ D
   ↑______↓      ↑______↓
   
   - No single point of failure
   - Best for peer-to-peer PTT
   - Requires routing protocol (BATMAN/OLSR)

2. Cluster-based:
   
        A ←──→ B
        ↑      ↑
   C ←──┼──→ CH1 ←──→ CH2 ←──┼──→ E
        ↓      ↓      ↑       ↓
        D      F      G       H
   
   - CH = Cluster Head
   - Reduces routing overhead
   - Good for large networks

3. Backbone + Edge:
   
   [BB1] ←────→ [BB2] ←────→ [BB3]
     ↑             ↑             ↑
     ↓             ↓             ↓
    [E1]          [E2]          [E3]
     ↑             ↑             ↑
     A             B             C
   
   - BB = Backbone nodes (fixed, high power)
   - E = Edge nodes
   - Scalable for tactical deployments
```

### 6.2 Hybrid Infrastructure-Independent Deployment

**Deployment Scenarios:**

| Scenario | Network Type | Protocol Stack | Range |
|----------|--------------|----------------|-------|
| Small Team (2-10) | WiFi Direct P2P | Native Android | 100m |
| Platoon (10-50) | MeshRider + BATMAN | LEDE/OpenWRT | 2-5km |
| Company (50-200) | Multi-channel mesh | OLSRv2 | 10km+ |
| Battalion (200+) | Hierarchical mesh | OLSRv2 + clusters | 50km+ |

**Hybrid Connectivity:**

```kotlin
class InfrastructureIndependentNetwork {
    
    enum class NetworkMode {
        STANDALONE_MESH,        // Pure mesh, no infrastructure
        OPPORTUNISTIC,          // Use infrastructure when available
        TETHERED,               // Gateway to internet via satellite/cellular
        HYBRID                  // Dynamic switching
    }
    
    fun configureForScenario(scenario: DeploymentScenario) {
        when (scenario) {
            is DeploymentScenario.UrbanCombat -> {
                // Short range, high density, NLOS
                enableMultiHopRouting(maxHops = 5)
                setChannelWidth(ChannelWidth.WIDTH_10MHZ)
                enableStoreAndForward(ttl = 3600)
            }
            
            is DeploymentScenario.RuralPatrol -> {
                // Long range, low density, LOS
                enableLongRangeMode()
                setChannelWidth(ChannelWidth.WIDTH_5MHZ)
                setTxPower(PowerLevel.MAX)
            }
            
            is DeploymentScenario.DisasterRelief -> {
                // Mixed environment, unknown topology
                enableAdaptiveRouting()
                enableDTNMode()
                setGatewayDiscovery(true)
            }
        }
    }
    
    // Gateway discovery and selection
    suspend fun discoverGateways(): List<GatewayInfo> {
        val gateways = mutableListOf<GatewayInfo>()
        
        // Check for MeshRider gateway
        val batmanGateways = getBatmanGateways()
        gateways.addAll(batmanGateways.map { 
            GatewayInfo(it.address, GatewayType.MESH, it.bandwidth) 
        })
        
        // Check for cellular/satellite gateway
        val connectedDevices = peerDiscovery.getConnectedDevices()
        connectedDevices.forEach { device ->
            if (device.hasInternetAccess) {
                gateways.add(GatewayInfo(
                    device.id, 
                    GatewayType.INTERNET_BACKHAUL,
                    device.availableBandwidth
                ))
            }
        }
        
        return gateways.sortedByDescending { it.bandwidth }
    }
}
```

### 6.3 Power Management for Extended Operations

**Battery-Aware Mesh Operation:**

```kotlin
class PowerManagementController {
    
    data class PowerState(
        val batteryLevel: Int,          // 0-100
        val isCharging: Boolean,
        val powerSource: PowerSource
    )
    
    enum class PowerProfile {
        MAXIMUM_PERFORMANCE,    // Full routing, no sleep
        BALANCED,               // Normal operation
        POWER_SAVER,            // Reduced beacon rates
        EMERGENCY               // Minimal operation, sleep
    }
    
    fun applyPowerProfile(profile: PowerProfile) {
        when (profile) {
            PowerProfile.MAXIMUM_PERFORMANCE -> {
                setBeaconInterval(100)      // 100ms
                setRoutingUpdates(1000)     // 1 second
                setTxPower(PowerLevel.MAX)
                disableSleep()
            }
            
            PowerProfile.BALANCED -> {
                setBeaconInterval(250)
                setRoutingUpdates(5000)     // 5 seconds
                setTxPower(PowerLevel.NORMAL)
                enableSleep(sleepDuration = 50)  // 50ms doze
            }
            
            PowerProfile.POWER_SAVER -> {
                setBeaconInterval(1000)
                setRoutingUpdates(30000)    // 30 seconds
                setTxPower(PowerLevel.LOW)
                enableSleep(sleepDuration = 200)
            }
            
            PowerProfile.EMERGENCY -> {
                setBeaconInterval(5000)     // 5 seconds
                setRoutingUpdates(120000)   // 2 minutes
                setTxPower(PowerLevel.MIN)
                enableDeepSleep(wakeInterval = 5000)
            }
        }
    }
    
    fun autoAdjustBasedOnBattery() {
        val state = getPowerState()
        
        when {
            state.batteryLevel > 50 || state.isCharging -> {
                applyPowerProfile(PowerProfile.BALANCED)
            }
            state.batteryLevel > 25 -> {
                applyPowerProfile(PowerProfile.POWER_SAVER)
            }
            else -> {
                applyPowerProfile(PowerProfile.EMERGENCY)
            }
        }
    }
}
```

---

## 7. INTEGRATION RECOMMENDATIONS

### 7.1 Recommended Protocol Stack for MeshRider PTT

```
┌─────────────────────────────────────────────────────────────┐
│ APPLICATION LAYER                                           │
│ - PTT Audio (Opus 6-24 kbps)                               │
│ - Floor Control (3GPP MCPTT)                               │
│ - Messaging                                                │
│ - Location Sharing                                         │
├─────────────────────────────────────────────────────────────┤
│ TRANSPORT LAYER                                             │
│ - RTP/UDP Multicast (PTT audio)                            │
│ - TCP (signaling, reliable messages)                       │
│ - UDP (discovery, beacons)                                 │
├─────────────────────────────────────────────────────────────┤
│ ROUTING LAYER                                               │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │ BATMAN-adv (Layer 2 mesh, default)                      │ │
│ │ - TQ-based routing                                      │ │
│ │ - Transparent bridging                                  │ │
│ └─────────────────────────────────────────────────────────┘ │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │ OLSRv2 (Layer 3, large scale)                           │ │
│ │ - Link metrics (ETX, latency)                           │ │
│ │ - MPR optimization                                      │ │
│ └─────────────────────────────────────────────────────────┘ │
├─────────────────────────────────────────────────────────────┤
│ LINK LAYER                                                  │
│ - 802.11 (WiFi) in ad-hoc / mesh mode                      │
│ - Channel selection: 900MHz (NLOS) / 5GHz (throughput)     │
│ - Adaptive bandwidth (5/10/20/40 MHz)                      │
├─────────────────────────────────────────────────────────────┤
│ PHYSICAL LAYER                                              │
│ - DoodleLabs MeshRider Radio                               │
│ - 900MHz / 2.4GHz / 5GHz / CBRS                           │
│ - Programmable via JSON-RPC/UBUS                          │
└─────────────────────────────────────────────────────────────┘
```

### 7.2 Performance Benchmarks

| Metric | Target | Notes |
|--------|--------|-------|
| PTT Latency | < 200ms | From PTT press to audio playback |
| Mesh Convergence | < 5 seconds | After topology change |
| Voice Quality | MOS ≥ 3.5 | Opus at 16+ kbps |
| Range (900MHz) | 10km+ | LOS conditions |
| Range (5GHz) | 2km | LOS conditions |
| Throughput per hop | 30-50 Mbps | 40MHz channel |
| Concurrent PTT Groups | 16+ | Independent talkgroups |
| Battery Life | 8+ hours | Active mesh participation |

### 7.3 Android Implementation Checklist

```kotlin
// 1. MeshRider Radio Integration
- [x] RadioApiClient for JSON-RPC/UBUS
- [x] Wireless status monitoring
- [x] Channel switching API
- [x] Link quality metrics

// 2. PTT Core
- [x] Opus codec integration
- [x] RTP multicast transport
- [x] Floor control (3GPP MCPTT)
- [x] DSCP QoS marking

// 3. Discovery
- [x] mDNS peer discovery
- [x] Beacon-based identity
- [x] WiFi Direct fallback
- [x] BLE low-power discovery

// 4. Transport
- [x] Multicast audio (primary)
- [x] TCP signaling
- [x] Store-and-forward fallback
- [ ] DTN integration (future)

// 5. Security
- [x] Ed25519 identity
- [x] libsodium encryption
- [x] MLS group encryption
- [ ] Bundle Protocol security (future)
```

---

## REFERENCES

### Standards and RFCs
1. RFC 3626 - Optimized Link State Routing Protocol (OLSR)
2. RFC 7181 - OLSR Version 2
3. RFC 6126 - The Babel Routing Protocol
4. RFC 5050 - Bundle Protocol Specification
5. IEEE 802.11s - Wireless Mesh Networking
6. RFC 3550 - RTP: Transport Protocol for Real-Time Applications
7. 3GPP TS 24.379 - Mission Critical Push To Talk (MCPTT)

### Open Source Implementations
1. olsrd / olsrd2 - OLSR daemon (http://www.olsr.org/)
2. batman-adv - Layer 2 mesh (https://www.open-mesh.org/)
3. babeld - Babel routing daemon (https://github.com/jech/babeld)
4. DTN2 / ION - Bundle Protocol implementations

### DoodleLabs Documentation
1. MeshRider LEDE Firmware Guide
2. JSON-RPC API Reference
3. UBUS Service Documentation

---

**Document Version:** 1.0  
**Last Updated:** February 6, 2026  
**Author:** Research compiled for DoodleLabs MeshRider Integration  
**Classification:** Technical Reference
