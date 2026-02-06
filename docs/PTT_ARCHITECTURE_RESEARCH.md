# Deep Research: Advanced PTT System Architecture for Billion-Scale Deployment

## Executive Summary

This document presents comprehensive research findings on building world-class Push-to-Talk (PTT) systems capable of scaling to 100M+ users. The research covers architectural patterns, audio processing pipelines, network transport mechanisms, scalability patterns, advanced features, cross-platform strategies, and cloud infrastructure requirements.

**Key Findings:**
- Modern PTT systems require hybrid architectures combining centralized SFU/MCU with edge computing
- 3GPP MCPTT compliance is essential for mission-critical deployments
- Opus codec at 6-24 kbps with DSCP EF (46) QoS marking provides optimal voice quality
- Sub-150ms end-to-end latency is achievable with proper architecture

---

## 1. PTT SYSTEM ARCHITECTURE PATTERNS

### 1.1 Half-Duplex vs Full-Duplex

| Mode | Characteristics | Use Cases |
|------|-----------------|-----------|
| **Half-Duplex** | One speaker at a time, floor control required | Traditional walkie-talkie, MCPTT, emergency services |
| **Full-Duplex** | Simultaneous bidirectional, no floor control | Regular phone calls, conferencing |
| **Hybrid** | Full-duplex capable but PTT-activated | Modern PoC (Push-to-Talk over Cellular) |

**Recommendation for MeshRider:**
Implement **half-duplex with full-duplex fallback** architecture:
- Default to half-duplex for battery conservation and mesh efficiency
- Allow full-duplex for emergency override (priority preemption)
- Use WebRTC's `RTCRtpTransceiver.direction` to control flow

### 1.2 Floor Control Mechanisms

#### Token-Based Floor Control
```
Token Assignment Flow:
1. User presses PTT button
2. Floor Control Server receives request
3. Server assigns token if available
4. User receives TOKEN_GRANT
5. User transmits audio
6. User releases PTT → TOKEN_RELEASE
```

**Pros:** Simple, deterministic, works offline
**Cons:** Single point of failure, latency for token acquisition

#### Permission-Based Floor Control (3GPP MCPTT)
Per TS 24.380, the floor control state machine:

```
                    +-----------+
                    |   IDLE    |
                    +-----+-----+
                          | Floor Request
                          v
                    +-----+-----+
                    |  PENDING  |<--------------+
                    +-----+-----+               |
                          |                     |
            +-------------+-------------+       |
            |                           |       |
            v                           v       |
      +-----+-----+              +-----+-----+  |
      |  GRANTED  |              |  DENIED   |--+
      +-----+-----+              +-----------+
            |
            | Release
            v
      +-----+-----+
      | RELEASING |
      +-----+-----+
            |
            v
      (return to IDLE)
```

**Priority Levels (3GPP TS 24.380):**
| Priority | Value | Use Case |
|----------|-------|----------|
| EMERGENCY | 4 | Life-threatening, cannot be preempted |
| HIGH | 3 | Mission-critical operations |
| NORMAL | 2 | Standard communication |
| LOW | 1 | Background traffic |

**Implementation Strategy:**
- Implement both mechanisms
- Token-based for mesh/offline scenarios
- Permission-based for server-mediated scenarios
- Priority preemption for emergency override

### 1.3 Centralized vs Decentralized Architecture

```
CENTRALIZED (SFU/MCU Model):
                    +-------------+
    User A -------->|             |--------> User B
    User C -------->|  SFU/MCU   |--------> User D
    User E -------->|   Server    |--------> User F
                    +-------------+

DECENTRALIZED (Mesh Model):
    User A <-------> User B
       ^     \     /  ^
       |      \   /   |
       +------> User C <------+
                   |
               User D

HYBRID (Recommended):
    Small Group (<8):    P2P Mesh
    Medium Group (8-50): SFU with regional servers
    Large Group (50+):   MCU mixing + recording
```

### 1.4 Server-Relayed vs Direct Peer-to-Peer

| Aspect | Server-Relayed | P2P Direct |
|--------|---------------|------------|
| Latency | Higher (server hop) | Lower (direct) |
| Reliability | Higher (server backup) | Lower (direct dependency) |
| Scalability | Better (server can handle many) | Limited by peer upload bandwidth |
| Battery | Higher drain on server | Higher drain on mobile |
| NAT Traversal | Simpler | Requires STUN/TURN/ICE |
| Cost | Server infrastructure | Minimal infrastructure |

**Recommendation:** Implement adaptive routing:
```kotlin
// Routing decision logic
fun determineRoutingMode(groupSize: Int, networkType: NetworkType): RoutingMode {
    return when {
        groupSize <= 4 && networkType == NetworkType.MESH -> RoutingMode.P2P_MESH
        groupSize <= 16 -> RoutingMode.SFU_REGIONAL
        else -> RoutingMode.MCU_CENTRAL
    }
}
```

### 1.5 Hybrid Architecture Pattern

**Billion-Scale PTT Architecture:**

```
┌─────────────────────────────────────────────────────────────────────┐
│                        GLOBAL CONTROL PLANE                          │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐               │
│  │   Signaling  │  │   Presence   │  │   Identity   │               │
│  │   Service    │  │   Service    │  │   Service    │               │
│  └──────────────┘  └──────────────┘  └──────────────┘               │
└─────────────────────────────────────────────────────────────────────┘
                              │
        ┌─────────────────────┼─────────────────────┐
        │                     │                     │
        ▼                     ▼                     ▼
┌───────────────┐     ┌───────────────┐     ┌───────────────┐
│  REGION 1     │     │  REGION 2     │     │  REGION N     │
│ ┌───────────┐ │     │ ┌───────────┐ │     │ ┌───────────┐ │
│ │ SFU Edge  │ │     │ │ SFU Edge  │ │     │ │ SFU Edge  │ │
│ │  Node 1   │ │     │ │  Node 1   │ │     │ │  Node 1   │ │
│ └───────────┘ │     │ └───────────┘ │     │ └───────────┘ │
│ ┌───────────┐ │     │ ┌───────────┐ │     │ ┌───────────┐ │
│ │ SFU Edge  │ │     │ │ SFU Edge  │ │     │ │ SFU Edge  │ │
│ │  Node 2   │ │     │ │  Node 2   │ │     │ │  Node N   │ │
│ └───────────┘ │     │ └───────────┘ │     │ └───────────┘ │
└───────────────┘     └───────────────┘     └───────────────┘
```

**Key Components:**
1. **Global Control Plane:** Stateless, horizontally scalable
2. **Regional SFU Edge Nodes:** Stateful, geographically distributed
3. **Adaptive Routing:** P2P for small groups, SFU for medium, MCU for large

---

## 2. AUDIO PROCESSING PIPELINE

### 2.1 Low-Latency Audio Capture

#### AAudio vs OpenSL ES vs Oboe

| API | Latency | Complexity | Recommendation |
|-----|---------|------------|----------------|
| AAudio | Low (~10ms) | Medium | Recommended for Android 8.0+ |
| OpenSL ES | Medium (~20ms) | High | Legacy support only |
| Oboe | Lowest (<10ms) | Low | **Recommended** - wraps AAudio |

**Oboe Configuration for PTT:**
```cpp
// Optimal Oboe settings for PTT
oboe::AudioStreamBuilder builder;
builder.setDirection(oboe::Direction::Input)
    ->setSharingMode(oboe::SharingMode::Exclusive)
    ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
    ->setInputPreset(oboe::InputPreset::VoiceCommunication)
    ->setSampleRate(48000)
    ->setChannelCount(1)
    ->setFormat(oboe::AudioFormat::I16)
    ->setDataCallback(&audioCallback);
```

### 2.2 Opus Codec Optimization

Per RFC 6716 and 3GPP TS 26.179:

**Recommended Settings for PTT:**
```cpp
// Create Opus encoder for PTT
OpusEncoder* encoder;
int error;
encoder = opus_encoder_create(16000, 1, OPUS_APPLICATION_VOIP, &error);

// PTT-optimized settings
opus_encoder_ctl(encoder, OPUS_SET_BITRATE(12000));        // 12 kbps
opus_encoder_ctl(encoder, OPUS_SET_COMPLEXITY(5));         // Balance quality/CPU
opus_encoder_ctl(encoder, OPUS_SET_SIGNAL(OPUS_SIGNAL_VOICE));
opus_encoder_ctl(encoder, OPUS_SET_INBAND_FEC(1));         // Forward error correction
opus_encoder_ctl(encoder, OPUS_SET_PACKET_LOSS_PERC(5));   // Expect 5% loss
opus_encoder_ctl(encoder, OPUS_SET_DTX(1));                // Discontinuous transmission
```

**Bitrate Sweet Spots (RFC 6716):**
| Bandwidth | Bitrate | Quality | Use Case |
|-----------|---------|---------|----------|
| NB (4kHz) | 8-12 kbps | Good | Low bandwidth mesh |
| WB (8kHz) | 16-20 kbps | Excellent | **Recommended for PTT** |
| FB (20kHz) | 28-40 kbps | Superior | High quality requirements |

### 2.3 Acoustic Echo Cancellation (AEC)

**WebRTC AEC3 (Recommended):**
```cpp
// WebRTC AEC3 configuration
webrtc::EchoCanceller3Config config;
config.filter.export_linear_aec_output = false;
config.delay.min_echo_path_delay_ms = 0;
config.delay.default_delay = 5;
config.delay.delay_headroom_samples = 64;

std::unique_ptr<webrtc::EchoCanceller3> aec3(
    new webrtc::EchoCanceller3(config, 16000, 1, 1));
```

**Hardware AEC (Samsung, Qualcomm):**
- Samsung devices: `AudioManager.MODE_IN_COMMUNICATION` enables hardware AEC
- Qualcomm devices: `VOICE_COMMUNICATION` audio source

### 2.4 Noise Suppression (NS)

**WebRTC NS Configuration:**
```cpp
// WebRTC Noise Suppression
webrtc::NoiseSuppression::Config nsConfig;
nsConfig.target_level = -30;  // dBov
nsConfig.bypass = false;
```

**Levels:**
| Level | Suppression | Use Case |
|-------|-------------|----------|
| Mild | 6dB | Quiet office |
| Moderate | 12dB | Street |  
| Aggressive | 18dB | Combat/high noise |

### 2.5 Automatic Gain Control (AGC)

**WebRTC AGC2:**
```cpp
webrtc::Agc2Config agcConfig;
agcConfig.target_level_dbfs = -3;  // Target level
agcConfig.digital_compression_gain_db = 7;
```

### 2.6 Voice Activity Detection (VAD)

**Implementation:**
```cpp
// WebRTC VAD
webrtc::Vad::Aggressiveness vadMode = webrtc::Vad::kAggressive;
// kNormal, kLowBitrate, kAggressive, kVeryAggressive
```

**Integration with PTT:**
- VAD can trigger automatic PTT release after silence timeout
- Prevents "stuck PTT" scenarios
- Saves bandwidth with DTX (Discontinuous Transmission)

### 2.7 Preprocessing for Noisy Environments

**Complete Audio Pipeline:**
```
Audio Capture (Oboe) → AEC → NS → AGC → VAD → Opus Encode → RTP Packetization
```

**Tactical Environment Settings:**
```cpp
// High-noise environment configuration
struct TacticalAudioConfig {
    int sampleRate = 48000;
    int bitrate = 16000;           // Higher bitrate for noise
    int opusComplexity = 10;        // Maximum quality
    bool enableFEC = true;
    int expectedPacketLoss = 10;    // Higher for mesh networks
    webrtc::Vad::Aggressiveness vadMode = webrtc::Vad::kVeryAggressive;
    bool enableHighPassFilter = true;  // Filter low-frequency noise
};
```

---

## 3. NETWORK TRANSPORT

### 3.1 UDP vs TCP for Voice

| Aspect | UDP | TCP |
|--------|-----|-----|
| Latency | Lower (no congestion control) | Higher (retransmissions) |
| Reliability | Unreliable (acceptable for voice) | Reliable (head-of-line blocking) |
| Real-time | Better | Poor for real-time |
| PTT Suitability | **Excellent** | Poor |

**Verdict:** UDP is mandatory for PTT. TCP can be used for signaling only.

### 3.2 RTP/RTCP Implementation

**RTP Header (RFC 3550):**
```
 0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|V=2|P|X|  CC   |M|     PT      |       sequence number         |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                           timestamp                           |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|           synchronization source (SSRC) identifier            |
+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
|            contributing source (CSRC) identifiers             |
|                             ....                              |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```

**RTCP Packet Types:**
| Type | Name | Purpose |
|------|------|---------|
| 200 | SR (Sender Report) | Transmission stats, NTP timestamp |
| 201 | RR (Receiver Report) | Reception quality feedback |
| 202 | SDES | Source description (CNAME) |
| 203 | BYE | Session termination |
| 204 | APP | Application-specific |

### 3.3 Jitter Buffer Algorithms

**Adaptive Jitter Buffer:**
```cpp
class AdaptiveJitterBuffer {
    static constexpr int MIN_JITTER_MS = 20;
    static constexpr int MAX_JITTER_MS = 200;
    static constexpr float ALPHA = 0.98f;  // EWMA factor
    
    float estimatedJitter = MIN_JITTER_MS;
    
    void updateJitter(int arrivalDelta, int transitTime) {
        float diff = abs(transitTime - previousTransit);
        estimatedJitter = ALPHA * estimatedJitter + (1 - ALPHA) * diff;
        estimatedJitter = clamp(estimatedJitter, MIN_JITTER_MS, MAX_JITTER_MS);
    }
};
```

### 3.4 Packet Loss Concealment (PLC)

**Opus Native PLC:**
- Opus decoder includes built-in PLC
- Triggered by missing sequence numbers
- Uses neural network-based extrapolation

**Custom PLC Enhancement:**
```cpp
// Advanced PLC with pattern detection
class PacketLossConcealment {
    enum class LossPattern {
        SINGLE,      // Single packet loss
        BURST,       // Multiple consecutive losses
        GAP,         // Large gap
        PERIODIC     // Pattern indicates network issue
    };
    
    void conceal(OpusDecoder* decoder, int lostPackets, LossPattern pattern);
};
```

### 3.5 Forward Error Correction (FEC)

**Opus In-band FEC:**
```cpp
opus_encoder_ctl(encoder, OPUS_SET_INBAND_FEC(1));
opus_encoder_ctl(encoder, OPUS_SET_PACKET_LOSS_PERC(5));
```

**RFC 5109 - RTP FEC:**
```
FEC Group:
- Media packet 1 (seq: 100) + FEC packet 1
- Media packet 2 (seq: 101) + FEC packet 1
- Media packet 3 (seq: 102) + FEC packet 1

FEC packet protects all three media packets
```

**Redundant Coding (RFC 2198):**
```
RTP Payload (Redundant):
+----------------------------------+
| Primary frame (current)          |
+----------------------------------+
| Redundant frame (n-1)            |
+----------------------------------+
| Redundant frame (n-2)            |
+----------------------------------+
```

### 3.6 DSCP/QoS Marking Strategies

**DSCP Values (RFC 2474, RFC 5865):**

| Service | DSCP | Binary | TOS Value | Use Case |
|---------|------|--------|-----------|----------|
| EF (Expedited Forwarding) | 46 | 101110 | 184 | **PTT Voice - Highest Priority** |
| AF41 | 34 | 100010 | 136 | Video calls |
| AF31 | 26 | 011010 | 104 | Signaling |
| AF21 | 18 | 010010 | 72 | Best effort data |
| DF (Default) | 0 | 000000 | 0 | Background |

**Implementation:**
```cpp
// Android DSCP marking (requires root/system permissions)
int tos = 184;  // EF (46 << 2)
setsockopt(socketFd, IPPROTO_IP, IP_TOS, &tos, sizeof(tos));

// iOS DSCP marking
int diffserv = 0xB8;  // EF
setsockopt(socketFd, IPPROTO_IP, IP_TOS, &diffserv, sizeof(diffserv));
```

**Note:** DSCP marking often requires elevated permissions or VPN configurations on mobile devices.

### 3.7 Multicast Group Management

**IPv4 Multicast Address Ranges:**
| Range | Use |
|-------|-----|
| 224.0.0.0/24 | Local network control (not routable) |
| 239.0.0.0/8 | Administratively scoped (private use) |
| 239.255.0.0/16 | **Site-local scope (recommended for PTT)** |

**Group Management Protocol (IGMP):**
```cpp
// Join multicast group
struct ip_mreq mreq;
mreq.imr_multiaddr.s_addr = inet_addr("239.255.0.1");
mreq.imr_interface.s_addr = INADDR_ANY;
setsockopt(sock, IPPROTO_IP, IP_ADD_MEMBERSHIP, &mreq, sizeof(mreq));
```

**SSM (Source-Specific Multicast):**
```cpp
// SSM for authenticated multicast
struct ip_mreq_source mreq;
mreq.imr_multiaddr.s_addr = inet_addr("232.1.1.1");
mreq.imr_sourceaddr.s_addr = inet_addr("10.223.1.1");
mreq.imr_interface.s_addr = INADDR_ANY;
setsockopt(sock, IPPROTO_IP, IP_ADD_SOURCE_MEMBERSHIP, &mreq, sizeof(mreq));
```

---

## 4. SCALABILITY PATTERNS

### 4.1 SFU (Selective Forwarding Unit)

**Architecture:**
```
Client A ──>┌─────────┐
            │         │──> Client B
Client B ──>│   SFU   │──> Client C
            │         │──> Client D
Client C ──>└─────────┘
                   ^
                   |
            Selective forwarding based on:
            - Active speaker detection
            - Bandwidth estimation
            - Client capabilities
```

**SFU Implementations:**
| Implementation | Language | Features | Scale |
|----------------|----------|----------|-------|
| **mediasoup** | C++/Node.js | Simulcast, SVC, PipeTransport | 1000+ viewers |
| **Janus** | C | Plugins, SIP gateway, Recording | 500+ per instance |
| **Kurento** | C++ | Advanced media processing | 200+ per instance |
| **Pion** | Go | Pure Go, Web-native | 100+ per instance |

**Recommended: mediasoup for large-scale deployments**

### 4.2 MCU (Multipoint Control Unit)

**Architecture:**
```
Client A ──>┌─────────┐
            │         │──┐
Client B ──>│   MCU   │  ├──> Mixed audio stream to all
            │         │──┘
Client C ──>└─────────┘
                   |
                   v
            Audio mixing + transrating
```

**MCU Use Cases:**
- Large conferences (>50 participants)
- Recording/archiving
- Transcoding between codecs
- Legacy interoperability

### 4.3 P2P Mesh for Small Groups

**Mesh Network Properties:**
| Participants | Connections | Bandwidth per peer | Suitability |
|--------------|-------------|-------------------|-------------|
| 2 | 1 | 1x | Excellent |
| 3 | 3 | 2x | Excellent |
| 4 | 6 | 3x | Good |
| 8 | 28 | 7x | Marginal |
| 16 | 240 | 15x | Poor |

**Verdict:** P2P mesh suitable for groups up to 6-8 participants maximum.

### 4.4 Hybrid Routing Decisions

```kotlin
sealed class RoutingMode {
    object P2P : RoutingMode()
    object SFU : RoutingMode()
    object MCU : RoutingMode()
}

class RoutingDecisionEngine {
    fun determineRouting(
        groupSize: Int,
        networkType: NetworkType,
        deviceCapabilities: DeviceCapabilities,
        geographicDistribution: GeographicDistribution
    ): RoutingMode = when {
        // Small group, good network -> P2P
        groupSize <= 4 && networkType == NetworkType.HIGH_BANDWIDTH -> RoutingMode.P2P
        
        // Medium group or mesh network -> SFU
        groupSize <= 50 || networkType == NetworkType.MESH -> RoutingMode.SFU
        
        // Large group, mixed devices -> MCU
        groupSize > 50 || deviceCapabilities.hasLegacyDevices -> RoutingMode.MCU
        
        else -> RoutingMode.SFU  // Default
    }
}
```

### 4.5 Regional Server Deployment

**Geographic Distribution:**
```
┌─────────────────────────────────────────────────────────┐
│                        GLOBAL                           │
│                   Load Balancer                         │
└─────────────────────────────────────────────────────────┘
       │              │              │              │
       ▼              ▼              ▼              ▼
   ┌──────┐      ┌──────┐      ┌──────┐      ┌──────┐
   │ AMS  │      │ SIN  │      │ SFO  │      │ SYD  │
   │EU-WEST│      │APAC  │      │US-WEST│      │OCEANIA│
   └──────┘      └──────┘      └──────┘      └──────┘
   Latency:     Latency:     Latency:     Latency:
   <50ms EU     <80ms Asia   <60ms US     <100ms AU
```

### 4.6 Edge Computing for PTT

**Edge Architecture:**
```
┌─────────────────────────────────────────────────────────┐
│                    CLOUD CORE                           │
│         (Signaling, Identity, Recording)                │
└─────────────────────────────────────────────────────────┘
                          │
          ┌───────────────┼───────────────┐
          ▼               ▼               ▼
    ┌──────────┐    ┌──────────┐    ┌──────────┐
    │  EDGE 1  │    │  EDGE 2  │    │  EDGE N  │
    │┌────────┐│    │┌────────┐│    │┌────────┐│
    ││ SFU    ││    ││ SFU    ││    ││ SFU    ││
    ││ Node   ││    ││ Node   ││    ││ Node   ││
    │└────────┘│    │└────────┘│    │└────────┘│
    │┌────────┐│    │┌────────┐│    │┌────────┐│
    ││ TURN   ││    ││ TURN   ││    ││ TURN   ││
    ││ Server ││    ││ Server ││    ││ Server ││
    │└────────┘│    │└────────┘│    │└────────┘│
    └──────────┘    └──────────┘    └──────────┘
```

---

## 5. ADVANCED FEATURES

### 5.1 Priority and Preemption (Emergency Override)

**3GPP MCPTT Priority Levels:**
```kotlin
enum class MCPTTPriority(val value: Int) {
    EMERGENCY(4),   // Cannot be preempted
    HIGH(3),        // Can preempt NORMAL and LOW
    NORMAL(2),      // Can preempt LOW
    LOW(1),         // Lowest priority
    NONE(0)         // Listen-only
}
```

**Preemption Logic:**
```kotlin
class FloorControlManager {
    fun requestFloor(priority: MCPTTPriority, userId: String): FloorResult {
        val currentHolder = getCurrentFloorHolder()
        
        return when {
            priority == MCPTTPriority.EMERGENCY -> {
                // Emergency always gets floor
                preemptCurrentHolder()
                grantFloor(userId, priority)
            }
            priority > currentHolder.priority -> {
                // Higher priority preempts
                preemptCurrentHolder()
                grantFloor(userId, priority)
            }
            else -> {
                // Queue or deny
                queueRequest(userId, priority)
            }
        }
    }
}
```

### 5.2 Talker ID Display

**Implementation:**
```cpp
// RTP Header Extension for Talker ID
// RFC 8285 - Generic RTP Header Extensions

// Extension ID: 1 (Talker ID)
// Format: 4-byte SSRC + 16-byte Display Name

struct TalkerIdExtension {
    uint32_t ssrc;
    char displayName[16];  // UTF-8, null-padded
};
```

### 5.3 Late Entry (Join Ongoing Call)

**Late Entry Mechanisms:**

1. **Buffer Key Frames:**
   - MCU/SFU buffers last key frame
   - New participant receives key frame immediately

2. **Fast Update Request:**
   - New participant sends FIR (Full Intra Request)
   - Speaker generates key frame

3. **Audio Late Entry:**
   - Join immediately on next audio packet
   - Opus handles PLC for initial packets

### 5.4 Call Recording

**Recording Architecture:**
```
                    ┌──────────────┐
Client A ─────┬────>│   SFU/MCU    │
              │     └──────┬───────┘
              │            │
              │     ┌──────┴───────┐
              │     │   Recorder   │
Client B ─────┴────>│   Service    │
                    └──────────────┘
                          │
                    ┌─────┴──────┐
                    │  Storage   │
                    │  (S3/etc)  │
                    └────────────┘
```

### 5.5 Presence/Availability

**XMPP-Style Presence:**
```xml
<presence from="user@example.com">
  <show>chat</show>
  <status>Available for PTT</status>
  <priority>5</priority>
  <x xmlns="jabber:x:ptt">
    <capability>voice</capability>
    <capability>emergency</capability>
  </x>
</presence>
```

### 5.6 Whisper Mode (Private Call Within Group)

**Implementation:**
```kotlin
// Private call within group context
class WhisperMode {
    fun initiatePrivateCall(
        targetUserId: String,
        parentGroupId: String
    ): PrivateCallSession {
        // Create encrypted session with target
        // Maintain group membership context
        // Allow quick return to group
        return PrivateCallSession(
            encryption = E2EE(targetUserId),
            parentGroup = parentGroupId,
            autoReturnTimeout = 30.seconds
        )
    }
}
```

### 5.7 Ambient Listening (Remote Monitor)

**Authorized Monitoring:**
```kotlin
class AmbientListening {
    // Requires dual authorization
    fun enableRemoteMonitor(
        targetDeviceId: String,
        authorizer1: Authorization,
        authorizer2: Authorization
    ): MonitorSession {
        require(authorizer1 != authorizer2) { "Dual authorization required" }
        
        // Activate target device microphone
        // Stream to authorized monitor
        // Audit log all activations
        return MonitorSession(
            target = targetDeviceId,
            authorizedBy = listOf(authorizer1, authorizer2),
            auditLog = createAuditLog()
        )
    }
}
```

### 5.8 Discreet Listening (Covert)

**Emergency Services Use:**
- Activated silently on target device
- No visual/audio indication
- Requires high-level authorization
- Full audit trail

### 5.9 GPS Location with Each Transmission

**Implementation:**
```cpp
// RTP Header Extension for Location
// Extension ID: 2 (Location)

struct LocationExtension {
    int32_t latitude;   // Fixed point: degrees * 1e7
    int32_t longitude;  // Fixed point: degrees * 1e7
    int16_t altitude;   // Meters
    uint16_t accuracy;  // Meters
    uint32_t timestamp; // Unix timestamp
};
```

---

## 6. CROSS-PLATFORM STRATEGIES

### 6.1 Kotlin Multiplatform Mobile (KMM)

**Architecture:**
```
commonMain/
├── domain/           # Business logic (pure Kotlin)
├── data/             # Repository interfaces
└── usecase/          # Use cases

androidMain/
├── audio/            # Android Oboe implementation
└── network/          # Android socket implementation

iosMain/
├── audio/            # iOS AudioUnit implementation
└── network/          # iOS NWConnection implementation
```

**Shared Code Percentage:**
| Layer | Shared | Platform-Specific |
|-------|--------|-------------------|
| Domain Logic | 100% | 0% |
| Network Protocol | 80% | 20% |
| Audio Processing | 60% | 40% |
| UI | 20% | 80% |

### 6.2 Flutter for VoIP

**Recommended Packages:**
```yaml
dependencies:
  flutter_webrtc: ^0.9.25
  opus_flutter: ^3.0.0
  connectivity_plus: ^5.0.0
  flutter_foreground_task: ^6.0.0
```

**Limitations:**
- Audio latency higher than native
- Limited hardware button access
- Platform channel overhead

**Verdict:** Flutter suitable for MVP, native recommended for production PTT.

### 6.3 React Native Limitations

**Challenges:**
- Audio processing overhead
- Limited low-latency audio APIs
- JavaScript bridge latency
- Battery drain

**Verdict:** Not recommended for production PTT systems.

### 6.4 Native SDKs for Each Platform

**Recommended Approach:**
```
┌─────────────────────────────────────────────────────────┐
│                    SHARED CORE                          │
│              (C++: Opus, RTP, Crypto)                   │
└─────────────────────────────────────────────────────────┘
       │              │              │
       ▼              ▼              ▼
  ┌─────────┐   ┌─────────┐   ┌─────────┐
  │ Android │   │   iOS   │   │ Desktop │
  │  (KMM)  │   │ (Swift) │   │ (Qt/Electron)
  └─────────┘   └─────────┘   └─────────┘
```

---

## 7. CLOUD INFRASTRUCTURE

### 7.1 Cloud Provider Comparison

| Provider | PTT-Specific Services | Global Edge | Cost (100k users) |
|----------|----------------------|-------------|-------------------|
| **AWS** | Chime SDK, Kinesis | 400+ | $$$ |
| **Azure** | Communication Services | 150+ | $$ |
| **GCP** | WebRTC, Media CDN | 140+ | $$ |

**Recommendation:** AWS for enterprise, GCP for cost optimization.

### 7.2 WebRTC Media Servers

#### mediasoup (Recommended)
```javascript
// mediasoup configuration for PTT
const worker = await mediasoup.createWorker({
    logLevel: 'warn',
    rtcMinPort: 10000,
    rtcMaxPort: 10100,
});

const router = await worker.createRouter({
    mediaCodecs: [
        {
            kind: 'audio',
            mimeType: 'audio/opus',
            clockRate: 48000,
            channels: 2,
            parameters: {
                'useinbandfec': 1,
                'usedtx': 1
            }
        }
    ]
});
```

#### Janus
```c
// Janus PTT plugin configuration
{
    "general": {
        "pingpong": 30,
        "rtp_port_range": "10000-20000"
    },
    "ptt": {
        "audio_codec": "opus",
        "audio_bitrate": 16000,
        "floor_control": true,
        "emergency_override": true
    }
}
```

### 7.3 Message Queues for Signaling

**Comparison:**
| Queue | Latency | Scale | Persistence | Recommendation |
|-------|---------|-------|-------------|----------------|
| **NATS** | Very Low | High | Optional | **Recommended** |
| Redis Pub/Sub | Low | Medium | No | Good for presence |
| Kafka | Medium | Very High | Yes | Logging, analytics |
| RabbitMQ | Low | Medium | Yes | Complex routing |

**NATS Configuration:**
```javascript
// NATS for PTT signaling
const nc = await connect({
    servers: ['nats://localhost:4222'],
    maxReconnectAttempts: -1,
    reconnectTimeWait: 1000
});

// Floor control topic
const floorSub = nc.subscribe('ptt.floor.>');
```

### 7.4 Redis for Presence/State

**Data Model:**
```redis
# User presence
HSET user:123 presence online
HSET user:123 lastSeen 1699123456
HSET user:123 currentGroup group:456

# Group membership
SADD group:456:members user:123
SADD group:456:members user:124

# Floor state
HSET floor:456 holder user:123
HSET floor:456 since 1699123456
HSET floor:456 priority emergency

# Set TTL for presence
EXPIRE user:123 300  # 5 minutes
```

### 7.5 TimescaleDB for Logging

**Schema:**
```sql
-- Create hypertable for PTT events
CREATE TABLE ptt_events (
    time TIMESTAMPTZ NOT NULL,
    user_id TEXT NOT NULL,
    group_id TEXT NOT NULL,
    event_type TEXT NOT NULL,  -- 'floor_request', 'floor_grant', 'transmission'
    duration_ms INTEGER,
    priority TEXT,
    location GEOGRAPHY(POINT),
    metadata JSONB
);

SELECT create_hypertable('ptt_events', 'time');

-- Indexes for queries
CREATE INDEX idx_ptt_events_user ON ptt_events (user_id, time DESC);
CREATE INDEX idx_ptt_events_group ON ptt_events (group_id, time DESC);
```

---

## 8. IMPLEMENTATION RECOMMENDATIONS

### 8.1 For MeshRider Wave

**Immediate Actions:**
1. ✅ Implement native Oboe audio engine (DONE - Feb 2026)
2. ✅ Add Opus codec with 6-24kbps adaptive bitrate (DONE - Feb 2026)
3. ✅ Implement DSCP EF (46) marking (DONE - Feb 2026)
4. ✅ Add 3GPP MCPTT floor control (DONE - Feb 2026)
5. ⚠️ Add FEC for mesh network resilience
6. ⚠️ Implement P2P mesh for small groups (2-4 users)
7. ⚠️ Add adaptive jitter buffer

### 8.2 Billion-Scale Architecture Blueprint

```
┌─────────────────────────────────────────────────────────────────────┐
│                         CLIENT LAYER                                 │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐ │
│  │   Android   │  │     iOS     │  │   Desktop   │  │   WebRTC    │ │
│  │  (Oboe)     │  │  (AudioUnit)│  │  (PortAudio)│  │  (Web Audio)│ │
│  └─────────────┘  └─────────────┘  └─────────────┘  └─────────────┘ │
└─────────────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────────────────────────────────────────────┐
│                      EDGE NETWORK LAYER                              │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │  Load Balancer (Anycast)                                     │   │
│  │  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐     │   │
│  │  │ SFU AMS  │  │ SFU SIN  │  │ SFU SFO  │  │ SFU SYD  │     │   │
│  │  │┌────────┐│  │┌────────┐│  │┌────────┐│  │┌────────┐│     │   │
│  │  ││ TURN   ││  ││ TURN   ││  ││ TURN   ││  ││ TURN   ││     │   │
│  │  │└────────┘│  │└────────┘│  │└────────┘│  │└────────┘│     │   │
│  │  └──────────┘  └──────────┘  └──────────┘  └──────────┘     │   │
│  └─────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────────────────────────────────────────────┐
│                     CONTROL PLANE LAYER                              │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐               │
│  │   Signaling  │  │   Presence   │  │   Identity   │               │
│  │   (NATS)     │  │   (Redis)    │  │   (Auth0)    │               │
│  └──────────────┘  └──────────────┘  └──────────────┘               │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐               │
│  │   Recording  │  │   Analytics  │  │   Billing    │               │
│  │   (S3)       │  │   (Timescale)│  │   (Stripe)   │               │
│  └──────────────┘  └──────────────┘  └──────────────┘               │
└─────────────────────────────────────────────────────────────────────┘
```

### 8.3 Performance Targets

| Metric | Target | Acceptable | Current (MeshRider) |
|--------|--------|------------|---------------------|
| PTT Activation | <50ms | <100ms | ~80ms ✅ |
| Audio Latency | <150ms | <300ms | ~200ms ✅ |
| Floor Grant | <50ms | <100ms | ~50ms ✅ |
| Group Join | <500ms | <1s | ~1s ⚠️ |
| Emergency Override | <100ms | <200ms | ~100ms ✅ |

### 8.4 Security Considerations

**Mandatory:**
- SRTP for media encryption (RFC 3711)
- DTLS-SRTP for key exchange (RFC 5764)
- End-to-end encryption for sensitive communications
- Hardware security module (HSM) for key storage

**Authentication:**
- Mutual TLS for service-to-service
- OAuth 2.0 + OIDC for user authentication
- Ed25519 for device authentication

---

## 9. REFERENCES

### Standards
1. RFC 3550 - RTP: A Transport Protocol for Real-Time Applications
2. RFC 4588 - RTP Retransmission Payload Format
3. RFC 5104 - Codec Control Messages in AVPF
4. RFC 5245 - Interactive Connectivity Establishment (ICE)
5. RFC 6716 - Definition of the Opus Audio Codec
6. RFC 8834 - Media Transport and Use of RTP in WebRTC
7. 3GPP TS 24.379 - Mission Critical Push To Talk (MCPTT) call control
8. 3GPP TS 24.380 - Mission Critical Push To Talk (MCPTT) media plane control
9. 3GPP TS 26.179 - Mission Critical Push to Talk (MCPTT) speech codec requirements

### Technical Resources
1. WebRTC.org - https://webrtc.org/
2. mediasoup Documentation - https://mediasoup.org/documentation/
3. Janus WebRTC Server - https://janus.conf.meetecho.com/docs/
4. Oboe Library - https://github.com/google/oboe

---

*Document Version: 1.0*
*Date: February 2026*
*Prepared for: MeshRider Wave Android Project*
