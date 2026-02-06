# WebRTC & Real-Time Communication Technologies - Deep Research
## Comprehensive Technical Analysis for MeshRider Wave PTT Applications

**Research Date:** February 2026  
**Project Context:** MeshRider Wave - WebRTC for voice/video calls, Native PTT with Opus/RTP  
**Target:** Low-latency, mesh-compatible communications

---

## Executive Summary

This research provides an in-depth analysis of WebRTC and real-time communication technologies specifically for Push-To-Talk (PTT) applications in mesh network environments. The analysis covers architecture, codecs, signaling protocols, media servers, optimization techniques, mobile implementations, emerging standards, and hybrid approaches.

**Key Findings:**
- WebRTC's DataChannel is ideal for PTT floor control and signaling
- Opus codec is mandatory in WebRTC and optimal for PTT (6-24 kbps adaptive)
- Native RTP multicast remains superior for PTT group communications vs. WebRTC's peer-to-peer model
- Hybrid approaches (WebRTC for 1:1 calls + Native RTP for PTT multicast) offer the best architecture
- mediasoup and Pion are the recommended media servers for mesh PTT use cases

---

## 1. WEBRTC ARCHITECTURE

### 1.1 PeerConnection API

The **RTCPeerConnection** interface is the core of WebRTC, managing the connection between two peers:

```
┌─────────────────────────────────────────────────────────────────┐
│                    RTCPeerConnection                            │
├─────────────────────────────────────────────────────────────────┤
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────────┐ │
│  │   Signaling │  │    ICE      │  │      DTLS/SRTP          │ │
│  │   State     │  │   Agent     │  │      Transport          │ │
│  └──────┬──────┘  └──────┬──────┘  └─────────────────────────┘ │
│         │                │                                      │
│  ┌──────▼────────────────▼──────────────────────────────────┐  │
│  │                 RtpTransceivers                           │  │
│  │  ┌──────────────┐  ┌──────────────┐  ┌────────────────┐  │  │
│  │  │  RtpSender   │  │ RtpReceiver  │  │ MediaStreamTrack│  │  │
│  │  └──────────────┘  └──────────────┘  └────────────────┘  │  │
│  └──────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

**Key Components:**
- **RtpTransceiver**: Manages bidirectional media flow for one "m=" section (SDP)
- **RtpSender**: Controls encoding and transmission of media
- **RtpReceiver**: Handles reception and decoding
- **ICE Agent**: Manages candidate gathering and connectivity checks

### 1.2 DataChannel for Signaling/Control

**RTCDataChannel** provides a bidirectional data transport over SCTP (Stream Control Transmission Protocol) encapsulated in DTLS:

```kotlin
// Android DataChannel for PTT Floor Control
val dataChannelInit = DataChannel.Init().apply {
    ordered = true           // Ensure floor control messages arrive in order
    maxRetransmits = 3       // Reliable delivery for critical control
    negotiated = true        // Pre-negotiated channel ID
    id = 0                   // Channel identifier
}

val dataChannel = peerConnection.createDataChannel("ptt-control", dataChannelInit)
```

**PTT Floor Control Protocol via DataChannel:**
```json
{
  "type": "floor_request",
  "timestamp": 1707234567890,
  "priority": 7,
  "channel_id": 1,
  "user_id": "user@meshrider.local"
}
```

**DataChannel Characteristics for PTT:**
| Property | PTT Requirement | Configuration |
|----------|----------------|---------------|
| Delivery | Reliable | ordered=true |
| Latency | <50ms | maxRetransmits=0 (for real-time) |
| Security | DTLS | Built-in encryption |
| Multiplexing | Multiple channels | Separate for control/status |

### 1.3 ICE (Interactive Connectivity Establishment)

ICE is defined in **RFC 8445** and provides NAT traversal through:

**Candidate Types:**
1. **Host Candidates** - Direct local IP addresses
2. **Server-Reflexive** - Public IP discovered via STUN
3. **Peer-Reflexive** - Discovered during connectivity checks
4. **Relayed** - TURN relay for symmetric NAT traversal

```
┌─────────────────────────────────────────────────────────────────┐
│                     ICE Architecture                            │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│   ┌──────────┐        STUN Binding        ┌──────────┐          │
│   │  Agent A │◄──────────────────────────►│ STUN/TURN│          │
│   │          │        Request/Response    │  Server  │          │
│   └────┬─────┘                            └────┬─────┘          │
│        │                                        │               │
│        │ Candidates:                            │               │
│        │ - host: 192.168.1.10:5004              │               │
│        │ - srflx: 203.0.113.5:8998              │               │
│        │ - relay: 198.51.100.20:3478            │               │
│        │                                        │               │
│        │  ┌────────────────────────────────┐   │               │
│        └──►│     Signaling Server           │◄──┘               │
│           │  (Exchanges candidates SDP)    │                   │
│           └────────────────────────────────┘                   │
│                               │                                │
│        ┌──────────────────────┴──────────────────────┐        │
│        │              Connectivity Checks             │        │
│        │  (STUN Binding requests between candidates)  │        │
│        └──────────────────────────────────────────────┘        │
│                               │                                │
│   ┌──────────┐           Selected Pair                   ┌──────────┐
│   │  Agent B │◄──────────────────────────────────────────►│  Agent A │
│   │          │        (Best path for media)               │          │
│   └──────────┘                                           └──────────┘
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

**ICE Lite for Mesh Networks:**
For mesh network radios with public IPs, implement **ICE Lite** (RFC 8445, Section 2.5):
- Only host candidates
- No connectivity checks generated
- Responds to checks from full ICE implementations
- Significantly reduced complexity for embedded devices

### 1.4 STUN/TURN Servers for NAT Traversal

**STUN (RFC 5389):** Session Traversal Utilities for NAT
- Discovers public IP address and port
- Low latency (<10ms typically)
- No media relaying (just discovery)

**TURN (RFC 5766/8656):** Traversal Using Relays around NAT
- Media relay for symmetric NAT scenarios
- Higher latency (relayed path)
- Bandwidth costs

**Configuration for MeshRider PTT:**
```javascript
// WebRTC ICE Configuration
const iceConfig = {
  iceServers: [
    { urls: 'stun:stun.meshrider.local:3478' },
    {
      urls: 'turn:turn.meshrider.local:3478',
      username: 'meshuser',
      credential: 'securepassword'
    }
  ],
  iceTransportPolicy: 'all',  // or 'relay' for privacy
  bundlePolicy: 'max-bundle',
  rtcpMuxPolicy: 'require'
};
```

**Mesh Network Considerations:**
- In mesh networks with public IPs (like DoodleLabs radios), STUN is often unnecessary
- TURN only needed when connecting to devices behind corporate firewalls
- Consider ICE Lite implementation on radio firmware

### 1.5 SDP Offer/Answer Model

**JSEP (JavaScript Session Establishment Protocol - RFC 8829)** defines the signaling model:

```
Offer/Answer Exchange Flow:
┌─────────┐                              ┌─────────┐
│ Offerer │                              │ Answerer│
└────┬────┘                              └────┬────┘
     │                                        │
     │ 1. createOffer()                       │
     │    ↓ Generates SDP offer               │
     │                                        │
     │ 2. setLocalDescription(offer)          │
     │    ↓ ICE gathering begins              │
     │                                        │
     │ 3. ────────────── SDP Offer ─────────►│
     │    (via signaling channel)             │
     │                                        │
     │                                        │ 4. setRemoteDescription(offer)
     │                                        │    ↓ Apply received offer
     │                                        │
     │                                        │ 5. createAnswer()
     │                                        │    ↓ Generate SDP answer
     │                                        │
     │                                        │ 6. setLocalDescription(answer)
     │                                        │
     │ 7. ◄──────────── SDP Answer ──────────│
     │    (via signaling channel)             │
     │                                        │
     │ 8. setRemoteDescription(answer)        │
     │    ↓ ICE connectivity checks           │
     │    ↓ DTLS handshake                    │
     │    ↓ SRTP key derivation               │
     │                                        │
     │◄══════════ Media/Data Flow ══════════►│
     │                                        │
```

**Simplified SDP for PTT Audio:**
```sdp
v=0
o=- 1234567890 2 IN IP4 0.0.0.0
s=MeshRider PTT Session
t=0 0
a=group:BUNDLE 0
a=msid-semantic: WMS ptt-stream

m=audio 5004 UDP/TLS/RTP/SAVPF 111 103 104 9 0 8 106 105 13 110 112 113 126
a=rtpmap:111 opus/48000/2
a=fmtp:111 minptime=10;useinbandfec=1
a=rtcp-fb:111 transport-cc
a=extmap:1 urn:ietf:params:rtp-hdrext:ssrc-audio-level
a=sendrecv
a=mid:0
a=ice-ufrag:8hhY
a=ice-pwd:asd88fgpdd777uzjYhagZg
a=fingerprint:sha-256 D2:FA:0E:2C:...
a=setup:actpass
a=rtcp-mux
a=rtcp-rsize
```

### 1.6 WebRTC NV (Next Version) Roadmap

**WebRTC NV** addresses current limitations:

| Feature | Status | PTT Relevance |
|---------|--------|---------------|
| WebTransport | Draft | Lower latency signaling |
| Insertable Streams | Available | Custom encryption for PTT |
| Simulcast | Stable | Adaptive quality for mesh |
| SVC (Scalable Video) | Available | Bandwidth adaptation |
| AV1 Codec | Emerging | Future video support |

**Insertable Streams for PTT Encryption:**
```javascript
// Custom end-to-end encryption for PTT
const sender = pc.getSenders()[0];
const senderStreams = sender.createEncodedStreams();

const transformStream = new TransformStream({
  transform(encodedFrame, controller) {
    // Apply custom PTT encryption
    const encrypted = encryptPTTFrame(encodedFrame.data, pttKey);
    encodedFrame.data = encrypted;
    controller.enqueue(encodedFrame);
  }
});

senderStreams.readable
  .pipeThrough(transformStream)
  .pipeTo(senderStreams.writable);
```

---

## 2. CODECS

### 2.1 Opus (Mandatory in WebRTC)

**RFC 6716** defines Opus as the mandatory-to-implement audio codec for WebRTC.

**Opus Architecture:**
```
┌─────────────────────────────────────────────────────────────┐
│                        Opus Codec                            │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌──────────────┐    Hybrid Mode     ┌──────────────┐       │
│  │  SILK Layer  │◄──────────────────►│  CELT Layer  │       │
│  │  (Speech)    │   (8kHz cutoff)    │   (Music)    │       │
│  └──────────────┘                    └──────────────┘       │
│         │                                   │               │
│         └──────────┬────────────────────────┘               │
│                    │                                        │
│                    ▼                                        │
│           ┌──────────────┐                                  │
│           │  Combined    │                                  │
│           │  Output      │                                  │
│           └──────────────┘                                  │
│                                                              │
│  Modes: SILK-only │ Hybrid │ CELT-only                      │
│  Bandwidth: NB │ WB │ SWB │ FB                              │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

**Opus Configuration for PTT:**
| Parameter | Value | Notes |
|-----------|-------|-------|
| Sample Rate | 48 kHz | WebRTC default |
| Frame Duration | 20 ms | Optimal for latency |
| Bitrate | 6-24 kbps | Adaptive based on network |
| Complexity | 3 | Balance quality/CPU |
| FEC | Enabled | Packet loss resilience |
| DTX | Optional | Silence compression |

**Opus Sweet Spots (RFC 6716, Section 2.1.1):**
```
8-12 kbps   → NB (narrowband) speech, excellent quality
16-20 kbps  → WB (wideband) speech, toll quality
28-40 kbps  → FB (fullband) speech, broadcast quality
48-64 kbps  → FB mono music
64-128 kbps → FB stereo music
```

### 2.2 Legacy Codecs

**G.711 (PCM):**
- 64 kbps, no compression
- Mandatory in WebRTC for PSTN interop
- High latency tolerance but bandwidth inefficient

**G.722:**
- 64 kbps ADPCM
- Wideband audio (7 kHz)
- Legacy equipment compatibility

### 2.3 EVS (Enhanced Voice Services)

**3GPP TS 26.445** - Next-generation codec for 5G:
- 5.9 kbps to 128 kbps
- Super-wideband (SWB) and fullband (FB) support
- Advanced error concealment
- **Not yet mandatory in WebRTC** but emerging

**Comparison for PTT:**
| Codec | Bitrate | Latency | Quality | WebRTC |
|-------|---------|---------|---------|--------|
| Opus | 6-24 kbps | 20ms | ★★★★★ | Mandatory |
| EVS | 5.9-24 kbps | 20ms | ★★★★★ | Optional |
| G.722 | 64 kbps | 20ms | ★★★☆☆ | Optional |
| G.711 | 64 kbps | 0.125ms | ★★☆☆☆ | Mandatory |

### 2.4 AAC-ELD (Apple)

**AAC Enhanced Low Delay:**
- 15-64 kbps
- Optimized for Apple's ecosystem
- Not native to WebRTC (requires transcoding)
- Use case: iOS native PTT app integration

### 2.5 Codec Negotiation (SDP)

**Offer/Answer Codec Selection:**
```sdp
; Offerer includes multiple codecs in preference order
m=audio 5004 UDP/TLS/RTP/SAVPF 111 103 9 0
a=rtpmap:111 opus/48000/2
a=rtpmap:103 ISAC/16000
a=rtpmap:9 G722/8000
a=rtpmap:0 PCMU/8000

; Answerer selects supported codec
m=audio 5004 UDP/TLS/RTP/SAVPF 111
a=rtpmap:111 opus/48000/2
a=fmtp:111 minptime=10;useinbandfec=1
```

**PTT-Specific Opus Parameters:**
```sdp
; Low-latency PTT configuration
a=rtpmap:111 opus/48000/2
a=fmtp:111 
   minptime=10;              ; Minimum packet time (10ms)
   useinbandfec=1;           ; Forward error correction
   stereo=0;                 ; Mono for PTT
   sprop-stereo=0;           ; Stereo hint
   maxaveragebitrate=24000;  ; Cap at 24 kbps
   maxplaybackrate=48000;    ; Decoder capability
```

### 2.6 Hardware Acceleration

**Android MediaCodec for Opus:**
```kotlin
// Hardware-accelerated Opus encoding
val codec = MediaCodec.createEncoderByType("audio/opus")
val format = MediaFormat.createAudioFormat("audio/opus", 48000, 1).apply {
    setInteger(MediaFormat.KEY_BIT_RATE, 24000)
    setInteger(MediaFormat.KEY_AAC_PROFILE, 
               MediaCodecInfo.CodecProfileLevel.AACObjectELD)
}
codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
```

**Hardware Support Matrix:**
| Platform | Opus Hardware | Notes |
|----------|---------------|-------|
| Android 10+ | Partial | MediaCodec supported |
| iOS 11+ | Yes | Hardware-accelerated |
| Snapdragon | Yes | QDSP6 offload |
| Embedded Linux | Limited | Software fallback |

---

## 3. SIGNALING PROTOCOLS

### 3.1 SIP (Session Initiation Protocol)

**RFC 3261** defines SIP for session management:

```
SIP Trapezoid Architecture:
┌─────────┐         ┌──────────┐         ┌─────────┐
│  Alice  │────────►│ atlanta  │────────►│ biloxi │
│  (UAC)  │         │  Proxy   │         │  Proxy  │
│         │◄────────│          │◄────────│         │
└────┬────┘         └──────────┘         └────┬────┘
     │                                        │
     │           ┌──────────────┐            │
     └──────────►│  Location    │◄───────────┘
                 │   Service    │
                 └──────────────┘
```

**SIP Methods for PTT:**
| Method | PTT Use Case |
|--------|--------------|
| INVITE | Initiate PTT session |
| ACK | Confirm session establishment |
| BYE | End PTT session |
| CANCEL | Abort pending floor request |
| REGISTER | User registration |
| INFO | Floor control messages |
| MESSAGE | Text messages |

**SIP for PTT Floor Control (3GPP MCPTT):**
```
Floor Request:  INFO sip:floor@mcptt.meshrider.local SIP/2.0
Floor Grant:    200 OK with Floor Control XML
Floor Release:  INFO with release indication
```

**Pros for PTT:**
- Mature protocol with extensive infrastructure
- 3GPP MCPTT standardization
- PSTN interworking

**Cons for PTT:**
- High overhead for simple PTT
- Complex state machines
- Not ideal for mesh networks

### 3.2 XMPP/Jingle

**XEP-0163, XEP-0167** define Jingle for VoIP over XMPP:

```xml
<!-- XMPP Jingle Session Initiation -->
<iq from='user@meshrider.local/mobile' 
    to='group@muc.meshrider.local' 
    type='set'>
  <jingle xmlns='urn:xmpp:jingle:1'
          action='session-initiate'
          sid='a73sjjvkla37jfea'>
    <content creator='initiator' name='ptt-audio'>
      <description xmlns='urn:xmpp:jingle:apps:rtp:1' 
                   media='audio'>
        <payload-type id='111' name='opus' clockrate='48000'/>
      </description>
      <transport xmlns='urn:xmpp:jingle:transports:ice-udp:1'>
        <candidate component='1' 
                   foundation='1'
                   generation='0'
                   id='el0747fg11'
                   ip='192.168.1.10'
                   network='1'
                   port='5004'
                   priority='2130706431'
                   protocol='udp'
                   type='host'/>
      </transport>
    </content>
  </jingle>
</iq>
```

**Pros for PTT:**
- XML-based, extensible
- Presence awareness
- MUC (Multi-User Chat) for groups

**Cons for PTT:**
- XML overhead
- Less efficient than binary protocols
- Limited mobile optimization

### 3.3 MQTT for IoT Signaling

**MQTT 5.0** (OASIS Standard) for lightweight signaling:

```
MQTT Architecture for PTT:
┌─────────┐      ┌─────────────┐      ┌─────────┐
│ Device A│◄────►│   MQTT      │◄────►│ Device B│
│ (Phone) │      │   Broker    │      │ (Radio) │
└────┬────┘      └─────────────┘      └────┬────┘
     │                                      │
     │ Topics:                              │
     │ - meshrider/ptt/floor/request        │
     │ - meshrider/ptt/floor/grant          │
     │ - meshrider/ptt/floor/release        │
     │ - meshrider/sdp/offer                │
     │ - meshrider/sdp/answer               │
     │ - meshrider/ice/candidate            │
     │                                      │
```

**MQTT Topics for MeshRider PTT:**
```
meshrider/
├── ptt/
│   ├── floor/
│   │   ├── request/{channel_id}/{user_id}
│   │   ├── grant/{channel_id}/{user_id}
│   │   ├── release/{channel_id}/{user_id}
│   │   └── queue/{channel_id}
│   ├── status/
│   │   ├── online/{user_id}
│   │   └── channel/{channel_id}/{user_id}
│   └── presence/
│       └── heartbeat/{user_id}
├── signaling/
│   ├── sdp/offer/{session_id}
│   ├── sdp/answer/{session_id}
│   └── ice/candidate/{session_id}
└── config/
    └── channel/{channel_id}
```

**MQTT QoS Levels:**
| QoS | Use Case | Guarantee |
|-----|----------|-----------|
| 0 | Presence updates | At most once |
| 1 | SDP exchange | At least once |
| 2 | Floor control | Exactly once |

**Pros for Mesh PTT:**
- Extremely lightweight (2-byte header minimum)
- Publish/subscribe model fits PTT groups
- Low bandwidth overhead
- Good for mesh networks

**Cons:**
- Requires broker (no direct P2P)
- No built-in encryption (use TLS/MQTT 5 properties)

### 3.4 WebSocket for Real-Time

**RFC 6455** defines WebSocket for bidirectional communication:

```
WebSocket Handshake:
Client Request:
  GET /ptt-signal HTTP/1.1
  Host: meshrider.local
  Upgrade: websocket
  Connection: Upgrade
  Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==
  Sec-WebSocket-Protocol: meshrider-ptt-v1

Server Response:
  HTTP/1.1 101 Switching Protocols
  Upgrade: websocket
  Connection: Upgrade
  Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo=
  Sec-WebSocket-Protocol: meshrider-ptt-v1
```

**WebSocket Frame Structure for PTT Signaling:**
```
 0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-------+-+-------------+-------------------------------+
|F|R|R|R| opcode|M| Payload len |    Extended payload length    |
|I|S|S|S|  (4)  |A|     (7)     |             (16/64)           |
|N|V|V|V|       |S|             |   (if payload len==126/127)   |
| |1|2|3|       |K|             |                               |
+-+-+-+-+-------+-+-------------+ - - - - - - - - - - - - - - - +
|     Extended payload length continued, if payload len == 127  |
+ - - - - - - - - - - - - - - - +-------------------------------+
|                               |Masking-key, if MASK set to 1  |
+-------------------------------+-------------------------------+
| Masking-key (continued)       |          Payload Data         |
+-------------------------------- - - - - - - - - - - - - - - - +
:                     Payload Data continued ...                :
+ - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - +
|                     Payload Data continued ...                |
+---------------------------------------------------------------+
```

**WebSocket Subprotocol for MeshRider PTT:**
```json
{
  "protocol": "meshrider-ptt-v1",
  "message_types": {
    "sdp_offer": 0x01,
    "sdp_answer": 0x02,
    "ice_candidate": 0x03,
    "floor_request": 0x10,
    "floor_grant": 0x11,
    "floor_release": 0x12,
    "floor_deny": 0x13,
    "heartbeat": 0x20,
    "status_update": 0x21
  }
}
```

### 3.5 Matrix Protocol

**Matrix Specification** for decentralized communication:

```
Matrix Architecture:
┌─────────┐      ┌──────────────┐      ┌─────────┐
│  Client │◄────►│  Homeserver  │◄────►│  Client │
│    A    │      │  (matrix.org)│      │    B    │
└────┬────┘      └──────┬───────┘      └────┬────┘
     │                  │                   │
     │ Client-Server API│  Server-Server API│
     │    (CS API)      │     (SS API)      │
     │                  │                   │
     │         ┌────────▼────────┐          │
     └────────►│  Event Graph    │◄─────────┘
               │  (Room DAG)     │
               └─────────────────┘
```

**Matrix for PTT:**
- **Room = PTT Channel**
- **Events = Floor control messages**
- **Federation = Mesh network interconnection**

**Example Matrix Event:**
```json
{
  "type": "com.meshrider.ptt.floor_request",
  "sender": "@user:meshrider.local",
  "content": {
    "channel_id": "general",
    "priority": 7,
    "timestamp": 1707234567890,
    "duration_estimate": 5000
  },
  "event_id": "$143273582443PhrSn:meshrider.local"
}
```

### 3.6 Custom Signaling Over Mesh

**Recommended for MeshRider: Hybrid MQTT + WebSocket**

```
Mesh Network Signaling Stack:
┌─────────────────────────────────────────┐
│      Application (PTT Controller)       │
├─────────────────────────────────────────┤
│    Floor Control Protocol (Custom)      │
├─────────────────────────────────────────┤
│    MQTT 5.0    │      WebSocket         │
│  (Control msgs)│      (WebRTC SDP/ICE)  │
├─────────────────────────────────────────┤
│         TCP over Mesh Network           │
├─────────────────────────────────────────┤
│      DoodleLabs Mesh Radio Layer        │
└─────────────────────────────────────────┘
```

**Custom Floor Control Protocol:**
```protobuf
// floor_control.proto
syntax = "proto3";

message FloorControlMessage {
  enum Type {
    FLOOR_REQUEST = 0;
    FLOOR_GRANT = 1;
    FLOOR_DENY = 2;
    FLOOR_RELEASE = 3;
    FLOOR_IDLE = 4;
    FLOOR_REVOKE = 5;
    STATUS_UPDATE = 6;
  }
  
  Type type = 1;
  string user_id = 2;
  string channel_id = 3;
  uint64 timestamp = 4;
  uint32 priority = 5;
  uint32 duration_ms = 6;
  string reason = 7;
  
  message QueueInfo {
    uint32 position = 1;
    uint32 estimated_wait_ms = 2;
  }
  QueueInfo queue_info = 8;
}
```

---

## 4. MEDIA SERVERS

### 4.1 Janus Gateway

**Janus** - General purpose WebRTC server (C implementation):

```
Janus Architecture:
┌─────────────────────────────────────────────────────────────┐
│                        Janus Gateway                         │
├─────────────────────────────────────────────────────────────┤
│  Core                                                        │
│  ├── ICE handling                                            │
│  ├── DTLS/SRTP stack                                         │
│  ├── SDP parsing                                             │
│  └── Plugin interface                                        │
│                                                              │
│  Plugins                                                     │
│  ├── Streaming (RTSP→WebRTC)                                 │
│  ├── VideoRoom (SFU)  ◄── PTT Use Case                       │
│  ├── AudioBridge (MCU)                                       │
│  ├── Record & Playback                                       │
│  └── SIP Gateway                                             │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

**Janus VideoRoom for PTT:**
```c
// Janus VideoRoom configuration for PTT
{
  "room": 1234,
  "description": "MeshRider PTT Channel",
  "secret": "adminpwd",
  "publishers": 20,        // Max concurrent speakers
  "bitrate": 64000,        // 64 kbps per stream
  "audiocodec": "opus",
  "video": false,          // Audio only for PTT
  "audiolevel_ext": true,  // Audio level detection
  "audio_active_packets": 25,
  "audio_level_average": 25
}
```

**Pros:**
- Flexible plugin system
- C implementation (small footprint)
- Active development

**Cons:**
- MCU mode (mixing) adds latency
- Complex configuration

### 4.2 Kurento (Deprecated)

**Status:** Deprecated as of 2021
**Recommendation:** Do not use for new projects
**Migration Path:** mediasoup or Janus

### 4.3 mediasoup (Recommended)

**mediasoup** - Modern SFU for WebRTC:

```
mediasoup Architecture:
┌─────────────────────────────────────────────────────────────┐
│                      mediasoup                              │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  Worker (C++ subprocess)                                     │
│  ├── Router (SFU engine)                                     │
│  │   ├── AudioLevelObserver                                  │
│  │   ├── ActiveSpeakerObserver                               │
│  │   └── Transport                                           │
│  │       ├── Producer (receive from client)                  │
│  │       └── Consumer (send to client)                       │
│  │                                                           │
│  └── libwebrtc components                                    │
│      ├── ICE/DTLS/SRTP                                      │
│      ├── Congestion Control (GCC)                           │
│      └── Simulcast/SVC handling                             │
│                                                              │
│  Node.js/Rust/C++ API                                       │
│  └── Application control                                    │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

**mediasoup for PTT:**
```javascript
// mediasoup configuration for PTT
const router = await worker.createRouter({
  mediaCodecs: [
    {
      kind: 'audio',
      mimeType: 'audio/opus',
      clockRate: 48000,
      channels: 1,  // Mono for PTT
      parameters: {
        'sprop-stereo': 0,
        'usedtx': 1,
        'useinbandfec': 1
      }
    }
  ]
});

// Enable audio level detection for floor control
const audioLevelObserver = await router.createAudioLevelObserver({
  maxEntries: 1,
  threshold: -70,
  interval: 800
});

// Detect active speaker for floor control
audioLevelObserver.on('volumes', (volumes) => {
  const { producer, volume } = volumes[0];
  console.log('Active speaker:', producer.id, 'level:', volume);
  // Trigger floor grant/grant logic
});
```

**Pros for PTT:**
- Modern SFU (Selective Forwarding Unit) - no mixing latency
- Excellent Node.js/Rust APIs
- Simulcast support
- Active speaker detection built-in
- Low CPU usage

**Cons:**
- Requires Node.js runtime
- Learning curve

### 4.4 Jitsi Videobridge (SFU)

**Jitsi Videobridge** - Java-based SFU:

```
Jitsi Architecture:
┌─────────────────────────────────────────────────────────────┐
│                   Jitsi Videobridge                         │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  Conference                                                  │
│  ├── Endpoint (per participant)                              │
│  │   ├── SctpConnection (DataChannel)                       │
│  │   └── Transceiver                                        │
│  │       ├── RtpSender                                      │
│  │       └── RtpReceiver                                   │
│  │                                                           │
│  ├── BitrateAllocator (for bandwidth management)            │
│  └── SpeechActivityDetector (for active speaker)            │
│                                                              │
│  Features                                                    │
│  ├── LastN (forward only N active speakers)                 │
│  ├── Simulcast                                              │
│  └── End-to-end encryption (Insertable Streams)             │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

**Pros:**
- Mature, production-proven
- LastN for large conferences
- Good documentation

**Cons:**
- Java runtime required
- Higher resource usage than mediasoup

### 4.5 Pion (Go - Cloud Native)

**Pion** - Pure Go WebRTC implementation:

```go
// Pion WebRTC for PTT server
package main

import (
    "github.com/pion/webrtc/v4"
)

func main() {
    // Configure media engine for Opus
    m := &webrtc.MediaEngine{}
    m.RegisterCodec(webrtc.RTPCodecParameters{
        RTPCodecCapability: webrtc.RTPCodecCapability{
            MimeType:  webrtc.MimeTypeOpus,
            ClockRate: 48000,
            Channels:  1,
            SDPFmtpLine: "minptime=10;useinbandfec=1",
        },
        PayloadType: 111,
    }, webrtc.RTPCodecTypeAudio)
    
    // Create API
    api := webrtc.NewAPI(webrtc.WithMediaEngine(m))
    
    // Create peer connection
    config := webrtc.Configuration{
        ICEServers: []webrtc.ICEServer{
            {URLs: []string{"stun:stun.meshrider.local:3478"}},
        },
    }
    
    pc, _ := api.NewPeerConnection(config)
    
    // Handle incoming audio track
    pc.OnTrack(func(track *webrtc.TrackRemote, receiver *webrtc.RTPReceiver) {
        // Forward to all other participants (SFU logic)
        go forwardToParticipants(track)
    })
}
```

**Pros:**
- Pure Go (no CGO)
- Cloud-native
- Fast build times
- Active community

**Cons:**
- Less mature than Janus/mediasoup
- Manual SFU implementation needed

### 4.6 Comparison for PTT Use Case

| Server | Language | Architecture | PTT Suitability | Latency | Resource Usage |
|--------|----------|--------------|-----------------|---------|----------------|
| **Janus** | C | MCU/SFU | ★★★☆☆ | Medium | Low |
| **mediasoup** | C++/Node.js | SFU | ★★★★★ | Low | Very Low |
| **Jitsi** | Java | SFU | ★★★★☆ | Low | Medium |
| **Pion** | Go | Custom | ★★★★☆ | Low | Low |
| **Kurento** | C++ | Pipeline | ★☆☆☆☆ | High | High |

**Recommendation for MeshRider PTT:**
- **1:1 Calls:** Use WebRTC native (no server)
- **Group PTT:** Use mediasoup SFU with audio-level-based forwarding
- **Radio Bridge:** Use Janus streaming plugin

---

## 5. REAL-TIME OPTIMIZATION

### 5.1 Low-Latency Encoding

**1-Frame VBV (Video Buffering Verifier) for Audio:**
```c
// Opus encoder low-latency configuration
opus_int32 opus_encoder_ctl(OpusEncoder *enc, int request, ...);

// Set minimum latency
opus_encoder_ctl(enc, OPUS_SET_EXPERT_FRAME_DURATION(OPUS_FRAMESIZE_10_MS));

// Enable in-band FEC
opus_encoder_ctl(enc, OPUS_SET_INBAND_FEC(1));

// Set expected packet loss
opus_encoder_ctl(enc, OPUS_SET_PACKET_LOSS_PERC(5));

// Complexity (0-10, lower = less latency)
opus_encoder_ctl(enc, OPUS_SET_COMPLEXITY(3));
```

**Frame Duration Trade-offs:**
| Frame Size | Latency | Overhead | Quality |
|------------|---------|----------|---------|
| 2.5 ms | Very Low | High | Good |
| 5 ms | Low | Medium | Better |
| **10 ms** | **Low** | **Low** | **Best** |
| 20 ms | Standard | Lower | Best |
| 40 ms | Higher | Lowest | Good |

### 5.2 UDP Socket Tuning

**Linux Socket Options for Low Latency:**
```c
// Socket creation
int sock = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP);

// Disable Nagle's algorithm equivalent
int nodelay = 1;
setsockopt(sock, IPPROTO_UDP, UDP_NOCHECKSUM, &nodelay, sizeof(nodelay));

// Increase socket buffer sizes
int rcvbuf = 262144;  // 256KB
int sndbuf = 262144;
setsockopt(sock, SOL_SOCKET, SO_RCVBUF, &rcvbuf, sizeof(rcvbuf));
setsockopt(sock, SOL_SOCKET, SO_SNDBUF, &sndbuf, sizeof(sndbuf));

// QoS marking (DSCP EF - Expedited Forwarding)
int dscp = 0x2E << 2;  // 46 << 2 = EF
setsockopt(sock, IPPROTO_IP, IP_TOS, &dscp, sizeof(dscp));

// Bind to CPU core (reduce context switching)
pthread_setaffinity_np(thread, sizeof(cpu_set_t), &cpuset);

// Real-time priority
struct sched_param param;
param.sched_priority = sched_get_priority_max(SCHED_FIFO);
pthread_setschedparam(thread, SCHED_FIFO, &param);
```

**Android Network Thread Priority:**
```kotlin
// Set thread priority for audio network operations
Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)

// Or using HandlerThread
val networkThread = HandlerThread("PTT-Network", 
    Process.THREAD_PRIORITY_URGENT_AUDIO).apply {
    start()
}
```

### 5.3 Congestion Control (GCC)

**Google Congestion Control (GCC) for WebRTC:**
```
GCC Architecture:
┌─────────────────────────────────────────────────────────────┐
│                  Congestion Controller                       │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  Sender Side                                                 │
│  ├── Loss-based controller                                   │
│  │   └── Adjust rate based on packet loss                   │
│  ├── Delay-based controller                                  │
│  │   └── Trendline estimator (inter-arrival time)           │
│  └── Rate controller                                         │
│      └── min(loss_rate, delay_rate)                         │
│                                                              │
│  Receiver Side                                               │
│  ├── Remote rate estimator                                   │
│  │   └── Transport-wide RTCP feedback                       │
│  └── Sends back bandwidth estimate                          │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

**PTT-Specific Congestion Control:**
```kotlin
// Disable GCC for constant bitrate PTT
val constraints = MediaConstraints()
constraints.mandatory.add(
    MediaConstraints.KeyValuePair("googHighpassFilter", "false")
)
constraints.mandatory.add(
    MediaConstraints.KeyValuePair("googEchoCancellation", "false")
)

// Or use WebRTC's transport-cc
val rtpParameters = sender.parameters
rtpParameters.degradationPreference = 
    DegradationPreference.MAINTAIN_FRamerate  // Not applicable to audio
```

**Mesh Network Adaptation:**
```c
// Custom congestion control for mesh networks
// Override WebRTC's default for fixed radio links

// Fixed bitrate for mesh (no adaptation needed)
#define PTT_BITRATE 24000  // 24 kbps

// Disable bandwidth probing
#define DISABLE_BANDWIDTH_PROBING 1

// Static rate allocation
static int get_target_bitrate_bps() {
    return PTT_BITRATE;  // Constant for mesh radio
}
```

### 5.4 Bandwidth Estimation

**Transport-Wide CC (TWCC) for PTT:**
```sdp
// Enable transport-wide congestion control
a=extmap:3 http://www.ietf.org/id/draft-holmer-rmcat-transport-wide-cc-extensions-01
a=rtcp-fb:111 transport-cc
```

**Fixed Bandwidth for Mesh Networks:**
```kotlin
// DoodleLabs radios have predictable bandwidth
// Override dynamic estimation with static configuration

class MeshBandwidthEstimator {
    private val linkCapacity: Int = when (radioModel) {
        "DL-900" -> 50000      // 50 kbps usable
        "DL-1800" -> 100000    // 100 kbps usable
        "DL-2400" -> 150000    // 150 kbps usable
        else -> 24000          // Conservative default
    }
    
    fun getTargetBitrate(): Int {
        // Reserve 30% for overhead/retransmissions
        return (linkCapacity * 0.7).toInt()
    }
}
```

### 5.5 Simulcast/SVC for Scalability

**Simulcast for Multi-Quality PTT:**
```javascript
// WebRTC Simulcast (not typically used for audio)
// For video PTT, send multiple resolutions
const senders = pc.getSenders();
const sender = senders.find(s => s.track.kind === 'video');

const params = sender.getParameters();
params.encodings = [
  { rid: 'low', maxBitrate: 100000 },
  { rid: 'medium', maxBitrate: 300000 },
  { rid: 'high', maxBitrate: 900000 }
];

sender.setParameters(params);
```

**SVC (Scalable Video Coding) - AV1:**
```
SVC Temporal Layers:
Layer 0: Base layer (lowest frame rate)
Layer 1: Enhancement layer 1
Layer 2: Enhancement layer 2

Receiver can drop higher layers based on bandwidth
```

---

## 6. WEBRTC FOR MOBILE

### 6.1 libwebrtc Build for Android

**Building WebRTC for Android:**
```bash
# Fetch depot_tools
git clone https://chromium.googlesource.com/chromium/tools/depot_tools.git
export PATH=$PATH:/path/to/depot_tools

# Fetch WebRTC
fetch --nohooks webrtc_android
cd src
gclient sync

# Generate build files
gn gen out/release --args='
  target_os="android"
  target_cpu="arm64"
  is_debug=false
  rtc_use_h264=true
  rtc_include_tests=false
  rtc_build_examples=false
'

# Build
ninja -C out/release libwebrtc
```

**Using Prebuilt:**
```gradle
// build.gradle dependencies
implementation 'org.webrtc:google-webrtc:1.0.32006'
```

### 6.2 GPU Memory Optimization

**Texture Memory Management:**
```kotlin
// Enable hardware acceleration for video
val eglBase = EglBase.create()

// Use SurfaceTexture for camera
cameraEnumerator.createCapturer(deviceName, cameraEventsHandler)?.apply {
    initialize(surfaceTextureHelper, context, videoSource.capturerObserver)
    startCapture(width, height, framerate)
}

// Release when done
videoSource.dispose()
surfaceTextureHelper.dispose()
eglBase.release()
```

**Memory Monitoring:**
```kotlin
// Monitor GPU memory for PTT video calls
val memoryInfo = ActivityManager.MemoryInfo()
activityManager.getMemoryInfo(memoryInfo)

if (memoryInfo.availMem < 50 * 1024 * 1024) {  // < 50MB
    // Reduce video quality or disable video
    videoCapturer.stopCapture()
}
```

### 6.3 Battery Consumption

**Battery Optimization Strategies:**
```kotlin
// Use low-power audio mode
val audioConstraints = MediaConstraints()
audioConstraints.mandatory.add(
    MediaConstraints.KeyValuePair("googEchoCancellation", "true")
)
audioConstraints.mandatory.add(
    MediaConstraints.KeyValuePair("googNoiseSuppression", "true")
)
audioConstraints.mandatory.add(
    MediaConstraints.KeyValuePair("googAutoGainControl", "true")
)

// Disable video when in background
override fun onPause() {
    super.onPause()
    if (!isInPttCall) {
        videoCapturer.stopCapture()
        // Keep audio for PTT
    }
}
```

**Doze Mode Handling:**
```kotlin
// Acquire wake lock for PTT operations
val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
val wakeLock = powerManager.newWakeLock(
    PowerManager.PARTIAL_WAKE_LOCK,
    "MeshRider::PTTWakeLock"
)

// During active PTT transmission
wakeLock.acquire(10*60*1000L)  // 10 minutes max

// Release when done
wakeLock.release()
```

### 6.4 Background Mode Handling

**Android Foreground Service for PTT:**
```kotlin
class PttService : Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Start as foreground service
        val notification = createNotification()
        startForeground(PTT_NOTIFICATION_ID, notification)
        
        // Maintain WebRTC connection in background
        initializePeerConnection()
        
        return START_STICKY
    }
}

// AndroidManifest.xml
<service android:name=".PttService"
    android:foregroundServiceType="microphone|dataSync"
    android:exported="false" />
```

**Background Execution Limits:**
```kotlin
// Use WorkManager for deferred PTT signaling
val pttWorkRequest = 
    PeriodicWorkRequestBuilder<PttSyncWorker>(15, TimeUnit.MINUTES)
        .setConstraints(Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build())
        .build()

WorkManager.getInstance(context).enqueueUniquePeriodicWork(
    "ptt_sync",
    ExistingPeriodicWorkPolicy.KEEP,
    pttWorkRequest
)
```

### 6.5 Bluetooth Audio Routing

**Bluetooth SCO for PTT Headsets:**
```kotlin
class BluetoothAudioManager(private val context: Context) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
    
    fun connectBluetoothSco() {
        // Start SCO connection
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.isBluetoothScoOn = true
        audioManager.startBluetoothSco()
        
        // Route audio to Bluetooth
        audioManager.isSpeakerphoneOn = false
    }
    
    fun disconnectBluetoothSco() {
        audioManager.stopBluetoothSco()
        audioManager.isBluetoothScoOn = false
    }
}
```

**Bluetooth PTT Button Handling:**
```kotlin
// Handle Bluetooth headset media buttons
class MediaButtonReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_MEDIA_BUTTON == intent.action) {
            val event = intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
            when (event?.keyCode) {
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                KeyEvent.KEYCODE_HEADSETHOOK -> {
                    // Toggle PTT
                    PTTManager.toggleTransmission()
                }
            }
        }
    }
}
```

---

## 7. EMERGING STANDARDS

### 7.1 WebTransport (HTTP/3)

**WebTransport over HTTP/3 (draft-ietf-webtrans-http3):**

```
WebTransport vs WebSocket:
┌─────────────────┬──────────────────┬──────────────────┐
│ Feature         │ WebSocket        │ WebTransport     │
├─────────────────┼──────────────────┼──────────────────┤
│ Transport       │ TCP              │ QUIC/UDP         │
│ Multiplexing    │ No (ordered)     │ Yes (streams)    │
│ Latency         │ ~100ms           │ ~50ms            │
│ Reliability     │ Always reliable  │ Configurable     │
│ Congestion      │ TCP CC           │ QUIC CC          │
│ 0-RTT           │ No               │ Yes              │
└─────────────────┴──────────────────┴──────────────────┘
```

**WebTransport for PTT Signaling:**
```javascript
// WebTransport client
const wt = new WebTransport('https://meshrider.local:4433/ptt');
await wt.ready;

// Bidirectional stream for floor control
const stream = await wt.createBidirectionalStream();
const writer = stream.writable.getWriter();
const reader = stream.readable.getReader();

// Send floor request
const encoder = new TextEncoder();
await writer.write(encoder.encode(JSON.stringify({
  type: 'floor_request',
  channel: 'general',
  priority: 7
})));
```

**PTT Use Case:**
- Lower latency signaling than WebSocket
- Unreliable datagrams for non-critical updates
- Better for mesh networks with packet loss

### 7.2 WISH (WebRTC-HTTP Ingestion Protocol)

**WISH (draft-ietf-wish-whip):**
- Standard for ingesting WebRTC to media servers
- HTTP-based signaling
- Simpler than full SDP exchange

**WHIP for PTT Server:**
```http
POST /whip/endpoint HTTP/1.1
Host: meshrider.local
Content-Type: application/sdp

v=0
o=- 1234567890 2 IN IP4 0.0.0.0
s=PTT Ingestion
t=0 0
m=audio 9 UDP/TLS/RTP/SAVPF 111
a=rtpmap:111 opus/48000/2

HTTP/1.1 201 Created
Location: https://meshrider.local/whip/endpoint/abc123
Content-Type: application/sdp

v=0
o=- 9876543210 2 IN IP4 0.0.0.0
s=Answer
t=0 0
m=audio 9 UDP/TLS/RTP/SAVPF 111
a=rtpmap:111 opus/48000/2
```

### 7.3 WHIP (WebRTC-HTTP Egress Protocol)

**WHEP (draft-ietf-wish-whep):**
- Playback/subscription protocol
- Complements WHIP for consumption

### 7.4 AV1 Codec Support

**AV1 for Future Video PTT:**
```
AV1 Advantages over H.264/VP8:
- 30% better compression
- Royalty-free
- SVC support built-in
- Better low-light performance

AV1 Complexity:
- Higher encoding CPU usage
- Hardware decode support growing
- Android 10+ supports AV1 decode
```

**Android AV1 Support:**
```kotlin
// Check AV1 support
val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
val av1Decoder = codecList.findDecoderForFormat(
    MediaFormat.createVideoFormat("video/av01", 640, 480)
)

if (av1Decoder != null) {
    // Use AV1 for video PTT
}
```

---

## 8. HYBRID APPROACHES

### 8.1 WebRTC for 1:1 Calls

**Architecture:**
```
1:1 Voice Call (WebRTC):
┌──────────┐                    ┌──────────┐
│ Phone A  │◄══════════════════►│ Phone B  │
│          │   WebRTC P2P       │          │
│ ┌──────┐ │   DTLS/SRTP        │ ┌──────┐ │
│ │ Opus │ │◄══════════════════►│ │ Opus │ │
│ └──┬───┘ │   over Mesh        │ └──┬───┘ │
│    │     │                    │    │     │
│ ┌──▼───┐ │                    │ ┌──▼───┐ │
│ │ ICE  │ │                    │ │ ICE  │ │
│ └──┬───┘ │                    │ └──┬───┘ │
└────┼─────┘                    └────┼─────┘
     │                              │
     └──────────┬───────────────────┘
                │ Mesh Network
                │ (UDP traversal)
```

**Implementation:**
```kotlin
// Direct peer-to-peer for 1:1 calls
val iceConfig = PeerConnection.IceServer.builder("stun:stun.meshrider.local:3478").createIceServer()
val rtcConfig = PeerConnection.RTCConfiguration(listOf(iceConfig)).apply {
    iceTransportsType = PeerConnection.IceTransportsType.ALL
    bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
    rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
}
```

### 8.2 Native RTP for PTT Multicast

**Architecture:**
```
PTT Multicast (Native RTP):
┌──────────┐                    ┌──────────┐
│ Phone A  │                    │ Phone B  │
│          │                    │          │
│ ┌──────┐ │    UDP Multicast   │ ┌──────┐ │
│ │ Opus │ │──►239.255.0.1:5004──►│ Opus │ │
│ └──┬───┘ │                    │ └──┬───┘ │
│    │     │   ┌──────────┐     │    │     │
│ ┌──▼───┐ │   │          │     │ ┌──▼───┐ │
│ │ RTP  │ │   │  Radio 1 │     │ │ RTP  │ │
│ └──┬───┘ │   │  (mesh)  │     │ └──┬───┘ │
└────┼─────┘   └────┬─────┘     └────┼─────┘
     │              │                │
     └──────────────┼────────────────┘
                    │
               ┌────▼────┐
               │ Radio 2 │
               │ (mesh)  │
               └────┬────┘
                    │
               ┌────▼────┐
               │ Radio N │
               └─────────┘
```

**Implementation:**
```kotlin
// Native RTP multicast for PTT
class PttMulticastManager {
    private val multicastGroup = InetAddress.getByName("239.255.0.1")
    private val port = 5004
    
    private val socket = MulticastSocket(port).apply {
        joinGroup(multicastGroup)
        // Disable loopback for sender
        loopbackMode = true
    }
    
    fun sendAudioPacket(rtpPacket: ByteArray) {
        val packet = DatagramPacket(
            rtpPacket, 
            rtpPacket.size,
            multicastGroup, 
            port
        )
        socket.send(packet)
    }
    
    fun receiveAudioPackets(callback: (ByteArray) -> Unit) {
        Thread {
            val buffer = ByteArray(1500)
            while (isActive) {
                val packet = DatagramPacket(buffer, buffer.size)
                socket.receive(packet)
                callback(packet.data.copyOf(packet.length))
            }
        }.start()
    }
}
```

### 8.3 DataChannel for Floor Control

**Architecture:**
```
Hybrid PTT System:
┌─────────────────────────────────────────────────────────────┐
│                     Mobile Device                            │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌──────────────────────────────────────────────────────┐   │
│  │              UI Layer (PTT Button)                    │   │
│  └──────────────────────┬───────────────────────────────┘   │
│                         │                                    │
│         ┌───────────────┼───────────────┐                   │
│         │               │               │                   │
│         ▼               ▼               ▼                   │
│  ┌────────────┐  ┌────────────┐  ┌────────────┐             │
│  │ WebRTC     │  │ Native RTP │  │ DataChannel│             │
│  │ 1:1 Calls  │  │ PTT Audio  │  │ Floor Ctrl │             │
│  │            │  │ Multicast  │  │            │             │
│  │ ┌────────┐ │  │ ┌────────┐ │  │ ┌────────┐ │             │
│  │ │ Opus   │ │  │ │ Opus   │ │  │ │ DTLS   │ │             │
│  │ │ 24kbps │ │  │ │ 24kbps │ │  │ │ SCTP   │ │             │
│  │ └────┬───┘ │  │ └────┬───┘ │  │ └────┬───┘ │             │
│  │      │     │  │      │     │  │      │     │             │
│  │ ┌────▼───┐ │  │ ┌────▼───┐ │  │ ┌────▼───┐ │             │
│  │ │ ICE/   │ │  │ │ UDP    │ │  │ │ ICE/   │ │             │
│  │ │ DTLS   │ │  │ │ Multi- │ │  │ │ DTLS   │ │             │
│  │ │ SRTP   │ │  │ │ cast   │ │  │ │        │ │             │
│  │ └────────┘ │  │ └────────┘ │  │ └────────┘ │             │
│  └────────────┘  └────────────┘  └────────────┘             │
│                                                              │
└─────────────────────────────────────────────────────────────┘
                              │
                    ┌─────────┴─────────┐
                    │   Mesh Network    │
                    │ (DoodleLabs Radio)│
                    └───────────────────┘
```

### 8.4 Switching Between Modes

**State Machine:**
```kotlin
sealed class CommunicationMode {
    object Idle : CommunicationMode()
    object PTT_Receiving : CommunicationMode()
    data class PTT_Transmitting(val channelId: Int) : CommunicationMode()
    data class Call_1to1(val peerId: String) : CommunicationMode()
    data class Group_Call(val groupId: String) : CommunicationMode()
}

class ModeManager {
    private var currentMode: CommunicationMode = CommunicationMode.Idle
    
    fun switchToPTT(channelId: Int) {
        // Close 1:1 call if active
        if (currentMode is CommunicationMode.Call_1to1) {
            webRTCManager.closeCall()
        }
        
        // Join multicast group
        rtpManager.joinChannel(channelId)
        currentMode = CommunicationMode.PTT_Receiving(channelId)
    }
    
    fun switchTo1to1(peerId: String) {
        // Leave multicast
        rtpManager.leaveAllChannels()
        
        // Establish WebRTC call
        webRTCManager.startCall(peerId)
        currentMode = CommunicationMode.Call_1to1(peerId)
    }
    
    fun startPTTTransmission(): Boolean {
        // Request floor via DataChannel
        return floorControl.requestFloor()
    }
}
```

---

## 9. IMPLEMENTATION RECOMMENDATIONS

### 9.1 Recommended Architecture for MeshRider Wave

```
┌─────────────────────────────────────────────────────────────────────┐
│                    MeshRider Wave PTT Architecture                   │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │                    Android Application                        │   │
│  │  ┌─────────────┐  ┌─────────────┐  ┌──────────────────────┐  │   │
│  │  │   PTT UI    │  │  Call UI    │  │    Settings/Config   │  │   │
│  │  └──────┬──────┘  └──────┬──────┘  └──────────┬───────────┘  │   │
│  │         │                │                     │              │   │
│  │  ┌──────▼────────────────▼─────────────────────▼──────────┐  │   │
│  │  │                 Communication Manager                   │  │   │
│  │  │  ┌──────────┐  ┌──────────┐  ┌──────────┐             │  │   │
│  │  │  │  PTT     │  │  Call    │  │  Signaling│             │  │   │
│  │  │  │ Manager  │  │ Manager  │  │ Manager   │             │  │   │
│  │  │  └────┬─────┘  └────┬─────┘  └────┬─────┘             │  │   │
│  │  └───────┼─────────────┼─────────────┼───────────────────┘  │   │
│  │          │             │             │                      │   │
│  │  ┌───────▼─────────────▼──────┐ ┌────▼─────────────────┐   │   │
│  │  │      Audio Engine          │ │    Network Stack     │   │   │
│  │  │  ┌──────────┐ ┌──────────┐ │ │  ┌────────────────┐  │   │   │
│  │  │  │  Opus    │ │  Audio   │ │ │  │  UDP Multicast │  │   │   │
│  │  │  │ Encoder  │ │  Capture │ │ │  │  (PTT Audio)   │  │   │   │
│  │  │  │  24kbps  │ │  (48kHz) │ │ │  └────────────────┘  │   │   │
│  │  │  └──────────┘ └──────────┘ │ │  ┌────────────────┐  │   │   │
│  │  │  ┌──────────┐ ┌──────────┐ │ │  │  WebRTC        │  │   │   │
│  │  │  │  Opus    │ │  Audio   │ │ │  │  (1:1 Calls)   │  │   │   │
│  │  │  │ Decoder  │ │  Playback│ │ │  └────────────────┘  │   │   │
│  │  │  └──────────┘ └──────────┘ │ │  ┌────────────────┐  │   │   │
│  │  └────────────────────────────┘ │  │  DataChannel   │  │   │   │
│  │                                 │  │  (Floor Ctrl)  │  │   │   │
│  │                                 │  └────────────────┘  │   │   │
│  │                                 └──────────────────────┘   │   │
│  └────────────────────────────────────────────────────────────┘   │
│                              │                                       │
│  ┌───────────────────────────┴───────────────────────────────┐      │
│  │                  Mesh Radio (LEDE/pttd)                    │      │
│  │  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐  │      │
│  │  │  USB     │  │  Opus    │  │  RTP     │  │  UDP     │  │      │
│  │  │  Audio   │──►│ Encoder  │──►│ Packet │──►│Multicast│  │      │
│  │  │  (ALSA)  │  │  24kbps  │  │  (5004)  │  │ (239.x)  │  │      │
│  │  └──────────┘  └──────────┘  └──────────┘  └──────────┘  │      │
│  │                                                           │      │
│  │  ┌──────────┐  ┌──────────┐  ┌──────────┐                │      │
│  │  │  Floor   │  │  UBUS    │  │  Link    │                │      │
│  │  │  Control │  │  API     │  │  Monitor │                │      │
│  │  └──────────┘  └──────────┘  └──────────┘                │      │
│  └──────────────────────────────────────────────────────────┘      │
│                              │                                       │
└──────────────────────────────┼───────────────────────────────────────┘
                               │
                    ┌──────────┴──────────┐
                    │   DoodleLabs Mesh   │
                    │    Radio Network    │
                    └─────────────────────┘
```

### 9.2 Technology Stack Summary

| Layer | Technology | Rationale |
|-------|------------|-----------|
| **Audio Codec** | Opus 24kbps | Mandatory in WebRTC, optimal for PTT |
| **1:1 Calls** | WebRTC | Industry standard, encrypted |
| **PTT Audio** | UDP Multicast + RTP | Lowest latency, mesh-compatible |
| **Floor Control** | DataChannel or MQTT | Reliable, low-latency signaling |
| **Signaling** | WebSocket/MQTT | Widely supported, efficient |
| **Media Server** | mediasoup SFU | For group calls > 1:1 |
| **Mobile** | libwebrtc | Stable, well-documented |

### 9.3 Performance Targets

| Metric | Target | Acceptable | Measurement |
|--------|--------|------------|-------------|
| PTT Activation | <100ms | <200ms | Button press to audio start |
| Audio Latency | <150ms | <300ms | End-to-end audio delay |
| Floor Grant | <50ms | <100ms | Request to grant notification |
| 1:1 Call Setup | <2s | <5s | ICE + DTLS completion |
| Packet Loss Recovery | <50ms | <100ms | FEC + PLC concealment |

### 9.4 Security Considerations

```
Security Layers:
┌───────────────────────────────────────┐
│  Application: End-to-end encryption   │ (Optional for PTT)
├───────────────────────────────────────┤
│  WebRTC: DTLS + SRTP                  │ (Standard)
├───────────────────────────────────────┤
│  Transport: DTLS for DataChannel      │ (Standard)
├───────────────────────────────────────┤
│  Network: WPA3 on Mesh                │ (Radio layer)
└───────────────────────────────────────┘
```

---

## 10. REFERENCES

### Standards Documents
1. **RFC 8829** - JSEP (JavaScript Session Establishment Protocol)
2. **RFC 8445** - ICE (Interactive Connectivity Establishment)
3. **RFC 8838** - Trickle ICE
4. **RFC 6716** - Opus Audio Codec
5. **RFC 6455** - WebSocket Protocol
6. **RFC 3261** - SIP (Session Initiation Protocol)
7. **RFC 8832** - WebRTC Data Channel Establishment Protocol
8. **WebTransport over HTTP/3** - draft-ietf-webtrans-http3

### WebRTC Resources
- WebRTC.org - https://webrtc.org
- WebRTC for the Curious - https://webrtcforthecurious.com
- Pion WebRTC - https://github.com/pion/webrtc
- mediasoup - https://mediasoup.org

### Mesh Network Context
- DoodleLabs Mesh Rider Technology
- LEDE/OpenWRT Mesh Configuration
- 3GPP MCPTT (Mission Critical Push-To-Talk) - TS 23.379

---

*Document Version: 1.0*  
*Last Updated: February 2026*  
*Author: Research Analysis for MeshRider Wave Project*
