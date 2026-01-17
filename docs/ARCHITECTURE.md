# MeshRider Wave Android - System Architecture

**Version:** 2.2.0 | **Last Updated:** January 2026 | **Classification:** Technical Design Document

---

## Table of Contents

1. [Executive Summary](#executive-summary)
2. [System Context](#system-context)
3. [High-Level Architecture](#high-level-architecture)
4. [Component Architecture](#component-architecture)
5. [Data Architecture](#data-architecture)
6. [Network Architecture](#network-architecture)
7. [Security Architecture](#security-architecture)
8. [Audio Pipeline Architecture](#audio-pipeline-architecture)
9. [Deployment Architecture](#deployment-architecture)
10. [Quality Attributes](#quality-attributes)

---

## Executive Summary

MeshRider Wave is a tactical Push-to-Talk (PTT) application designed for deployment with DoodleLabs MeshRider mesh radios. The application provides military-grade voice communication over resilient mesh networks without requiring internet connectivity.

### Key Design Principles

| Principle | Description |
|-----------|-------------|
| **Offline-First** | All core functionality works without internet |
| **Security by Design** | End-to-end encryption with zero-knowledge architecture |
| **Low Latency** | Sub-200ms voice latency for tactical communications |
| **Resilience** | Graceful degradation under network stress |
| **Interoperability** | ATAK integration via CoT protocol |

### Technology Stack

```
┌─────────────────────────────────────────────────────────────┐
│                        APPLICATION                           │
├─────────────────────────────────────────────────────────────┤
│  Language      │ Kotlin 2.1.0                               │
│  UI Framework  │ Jetpack Compose + Material 3               │
│  Architecture  │ Clean Architecture + MVVM                  │
│  DI Framework  │ Hilt 2.53.1                                │
│  Networking    │ Multicast RTP + TCP Signaling              │
│  Crypto        │ libsodium 2.0.2 + MLS                      │
│  Audio         │ Opus Codec + Adaptive Jitter Buffer        │
└─────────────────────────────────────────────────────────────┘
```

---

## System Context

### Context Diagram (C4 Level 1)

```
                                    ┌─────────────────────┐
                                    │   ATAK Application  │
                                    │   (TAK Server/CIV)  │
                                    └──────────┬──────────┘
                                               │ CoT Protocol
                                               │ (239.2.3.1:6969)
                                               ▼
┌──────────────┐    Mesh IP Network    ┌─────────────────────┐
│   MeshRider  │◄─────────────────────►│   MR Wave Android   │
│    Radio     │   (BATMAN-adv L2)     │   (This System)     │
└──────────────┘                       └──────────┬──────────┘
       ▲                                          │
       │ MN-MIMO Waveform                         │ Local Storage
       │                                          ▼
       ▼                               ┌─────────────────────┐
┌──────────────┐                       │  Android DataStore  │
│  Other Mesh  │                       │  + JSON Files       │
│    Nodes     │                       └─────────────────────┘
└──────────────┘

External Systems:
- MeshRider Radio: IP transport layer (not audio processing)
- ATAK: Situational awareness integration
- Other MR Wave Apps: Peer-to-peer communication
```

### System Boundaries

| Component | Responsibility | Boundary |
|-----------|---------------|----------|
| MR Wave App | PTT logic, audio, encryption, UI | This system |
| MeshRider Radio | IP transport, mesh routing | External |
| ATAK | Situational awareness display | External |
| Android OS | Audio HAL, networking, GPS | Platform |

---

## High-Level Architecture

### Clean Architecture Layers

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              PRESENTATION LAYER                              │
│  ┌──────────────────┐  ┌──────────────────┐  ┌────────────────────────────┐ │
│  │    Composable    │  │    ViewModel     │  │     Navigation Graph       │ │
│  │     Screens      │  │    StateFlow     │  │                            │ │
│  │                  │  │                  │  │  Dashboard → Groups →      │ │
│  │  • Dashboard     │  │  • MainVM        │  │  Channels → Map → Settings │ │
│  │  • Groups        │  │  • GroupsVM      │  │                            │ │
│  │  • Channels      │  │  • ChannelsVM    │  │                            │ │
│  │  • Map           │  │  • MapVM         │  │                            │ │
│  │  • Settings      │  │  • RadioStatusVM │  │                            │ │
│  └──────────────────┘  └──────────────────┘  └────────────────────────────┘ │
├─────────────────────────────────────────────────────────────────────────────┤
│                                DOMAIN LAYER                                  │
│  ┌──────────────────┐  ┌──────────────────┐  ┌────────────────────────────┐ │
│  │      Models      │  │   Repository     │  │       Use Cases            │ │
│  │    (Entities)    │  │   Interfaces     │  │     (if needed)            │ │
│  │                  │  │                  │  │                            │ │
│  │  • Contact       │  │  • ContactRepo   │  │  Future: Complex           │ │
│  │  • CallState     │  │  • SettingsRepo  │  │  business logic            │ │
│  │  • Event         │  │                  │  │                            │ │
│  └──────────────────┘  └──────────────────┘  └────────────────────────────┘ │
├─────────────────────────────────────────────────────────────────────────────┤
│                                 DATA LAYER                                   │
│  ┌──────────────────┐  ┌──────────────────┐  ┌────────────────────────────┐ │
│  │   Repository     │  │    DataStore     │  │      JSON Storage          │ │
│  │      Impl        │  │   (Settings)     │  │      (Contacts)            │ │
│  │                  │  │                  │  │                            │ │
│  │  • ContactRepoI  │  │  • Preferences   │  │  • contacts.json           │ │
│  │  • SettingsRepoI │  │  • Keys          │  │  • messages.json           │ │
│  └──────────────────┘  └──────────────────┘  └────────────────────────────┘ │
├─────────────────────────────────────────────────────────────────────────────┤
│                                 CORE LAYER                                   │
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐ ┌─────────────┐           │
│  │   Crypto    │ │   Network   │ │    Audio    │ │    Radio    │           │
│  │             │ │             │ │             │ │             │           │
│  │ • Crypto    │ │ • MeshNet   │ │ • Multicast │ │ • RadioAPI  │           │
│  │   Manager   │ │   Manager   │ │   Audio     │ │   Client    │           │
│  │ • MLS       │ │ • MeshSvc   │ │ • Opus      │ │ • Discovery │           │
│  │   Manager   │ │ • Connector │ │ • Jitter    │ │   Service   │           │
│  │             │ │ • PeerDisc  │ │ • RTP       │ │             │           │
│  └─────────────┘ └─────────────┘ └─────────────┘ └─────────────┘           │
│                                                                              │
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐ ┌─────────────┐           │
│  │     PTT     │ │   Location  │ │     SOS     │ │  Messaging  │           │
│  │             │ │             │ │             │ │             │           │
│  │ • PTT       │ │ • Location  │ │ • SOS       │ │ • Offline   │           │
│  │   Manager   │ │   Sharing   │ │   Manager   │ │   Message   │           │
│  │ • PTTAudio  │ │   Manager   │ │             │ │   Manager   │           │
│  └─────────────┘ └─────────────┘ └─────────────┘ └─────────────┘           │
│                                                                              │
│  ┌─────────────┐                                                            │
│  │    ATAK     │                                                            │
│  │             │                                                            │
│  │ • ATAKBridge│                                                            │
│  │ • CoT       │                                                            │
│  │   Manager   │                                                            │
│  └─────────────┘                                                            │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Dependency Flow

```
Presentation → Domain → Data
      ↓           ↓        ↓
      └───────────┴────────┴──→ Core (shared infrastructure)
```

**Rules:**
1. Outer layers depend on inner layers, never reverse
2. Domain layer has no Android dependencies
3. Core layer provides infrastructure to all layers
4. Dependency injection via Hilt

---

## Component Architecture

### Core Components (C4 Level 2)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                            MR WAVE APPLICATION                               │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                        MESH SERVICE (Foreground)                     │    │
│  │  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────────┐  │    │
│  │  │ SignalingServer │  │  PTTManager     │  │  LocationSharing    │  │    │
│  │  │ (TCP :10001)    │  │  (Floor Ctrl)   │  │  (GPS Broadcast)    │  │    │
│  │  └────────┬────────┘  └────────┬────────┘  └──────────┬──────────┘  │    │
│  │           │                    │                       │             │    │
│  │           ▼                    ▼                       ▼             │    │
│  │  ┌─────────────────────────────────────────────────────────────┐    │    │
│  │  │                   MESH NETWORK MANAGER                       │    │    │
│  │  │  • Peer discovery (mDNS)                                    │    │    │
│  │  │  • Connection management                                     │    │    │
│  │  │  • Message routing                                          │    │    │
│  │  └─────────────────────────────────────────────────────────────┘    │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
│  ┌──────────────────────────────┐  ┌──────────────────────────────────┐    │
│  │     AUDIO SUBSYSTEM          │  │      CRYPTO SUBSYSTEM            │    │
│  │                              │  │                                  │    │
│  │  ┌────────────────────────┐  │  │  ┌────────────────────────────┐  │    │
│  │  │  OpusCodecManager      │  │  │  │  CryptoManager             │  │    │
│  │  │  • Encode: 16kHz→Opus  │  │  │  │  • Ed25519 signing         │  │    │
│  │  │  • Decode: Opus→16kHz  │  │  │  │  • X25519 key exchange     │  │    │
│  │  │  • Fallback: ADPCM     │  │  │  │  • XSalsa20-Poly1305       │  │    │
│  │  └───────────┬────────────┘  │  │  └────────────────────────────┘  │    │
│  │              │               │  │                                  │    │
│  │  ┌───────────▼────────────┐  │  │  ┌────────────────────────────┐  │    │
│  │  │  MulticastAudioManager │  │  │  │  MLSManager                │  │    │
│  │  │  • TX: Mic → Multicast │  │  │  │  • Group key agreement     │  │    │
│  │  │  • RX: Multicast → Spk │  │  │  │  • Forward secrecy         │  │    │
│  │  └───────────┬────────────┘  │  │  │  • Member add/remove       │  │    │
│  │              │               │  │  └────────────────────────────┘  │    │
│  │  ┌───────────▼────────────┐  │  │                                  │    │
│  │  │  AdaptiveJitterBuffer  │  │  └──────────────────────────────────┘    │
│  │  │  • RFC 3550 timing     │  │                                          │
│  │  │  • 20-100ms adaptive   │  │  ┌──────────────────────────────────┐    │
│  │  │  • PLC on packet loss  │  │  │      RADIO SUBSYSTEM            │    │
│  │  └───────────┬────────────┘  │  │                                  │    │
│  │              │               │  │  ┌────────────────────────────┐  │    │
│  │  ┌───────────▼────────────┐  │  │  │  RadioApiClient            │  │    │
│  │  │  RTPPacketManager      │  │  │  │  • JSON-RPC authentication │  │    │
│  │  │  • RFC 3550 packets    │  │  │  │  • UBUS service calls      │  │    │
│  │  │  • DSCP EF (46) QoS    │  │  │  │  • Wireless status         │  │    │
│  │  │  • Sequence numbers    │  │  │  │  • Channel switching       │  │    │
│  │  └────────────────────────┘  │  │  └────────────────────────────┘  │    │
│  │                              │  │                                  │    │
│  └──────────────────────────────┘  │  ┌────────────────────────────┐  │    │
│                                     │  │  RadioDiscoveryService     │  │    │
│                                     │  │  • UDP broadcast :11111    │  │    │
│                                     │  │  • Auto-discovery          │  │    │
│                                     │  │  • Multi-subnet support    │  │    │
│                                     │  └────────────────────────────┘  │    │
│                                     │                                  │    │
│                                     └──────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Component Interactions

```
┌──────────┐         ┌──────────┐         ┌──────────┐
│   User   │         │ PTT Btn  │         │  Codec   │
│  Press   │────────►│ Manager  │────────►│  Encode  │
│   PTT    │         │          │         │          │
└──────────┘         └────┬─────┘         └────┬─────┘
                          │                     │
                          ▼                     ▼
                   ┌──────────┐         ┌──────────┐
                   │  Floor   │         │   RTP    │
                   │ Control  │         │ Packetize│
                   │ Acquire  │         │          │
                   └────┬─────┘         └────┬─────┘
                        │                     │
                        ▼                     ▼
                   ┌──────────┐         ┌──────────┐
                   │  Crypto  │         │ Multicast│
                   │ Encrypt  │────────►│   Send   │
                   │          │         │239.255.0.x│
                   └──────────┘         └──────────┘

Reception Flow (reverse):
Multicast → Jitter Buffer → Decrypt → Decode → Speaker
```

---

## Data Architecture

### Data Model

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              DATA ENTITIES                                   │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌────────────────────┐     ┌────────────────────┐     ┌─────────────────┐  │
│  │      Contact       │     │     CallState      │     │      Event      │  │
│  ├────────────────────┤     ├────────────────────┤     ├─────────────────┤  │
│  │ publicKey: ByteArr │     │ status: Status     │     │ type: EventType │  │
│  │ name: String       │     │ direction: Dir     │     │ contact: Contact│  │
│  │ addresses: List    │     │ type: CallType     │     │ timestamp: Long │  │
│  │ blocked: Boolean   │     │ contact: Contact?  │     │ duration: Long? │  │
│  │ createdAt: Long    │     │ isMicEnabled: Bool │     │ success: Bool   │  │
│  │ lastSeenAt: Long?  │     │ startTime: Long?   │     └─────────────────┘  │
│  │ lastWorkingAddr: ? │     │ errorMessage: ?    │                          │
│  └────────────────────┘     └────────────────────┘                          │
│           │                          │                                       │
│           │                          │                                       │
│  ┌────────▼───────────┐     ┌────────▼───────────┐     ┌─────────────────┐  │
│  │   Talkgroup (PTT)  │     │   PeerLocation     │     │   SOSEvent      │  │
│  ├────────────────────┤     ├────────────────────┤     ├─────────────────┤  │
│  │ id: String         │     │ peerId: String     │     │ id: String      │  │
│  │ name: String       │     │ latitude: Double   │     │ senderId: String│  │
│  │ multicastAddr: Str │     │ longitude: Double  │     │ latitude: Double│  │
│  │ port: Int          │     │ altitude: Double?  │     │ longitude: Dbl  │  │
│  │ encryptionKey: Byt │     │ heading: Float?    │     │ message: String │  │
│  │ members: List<ID>  │     │ speed: Float?      │     │ timestamp: Long │  │
│  │ priority: Int      │     │ timestamp: Long    │     │ acknowledged: B │  │
│  └────────────────────┘     └────────────────────┘     └─────────────────┘  │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Storage Strategy

| Data Type | Storage | Encryption | Location |
|-----------|---------|------------|----------|
| Settings | DataStore | None | preferences_pb |
| Keypair | DataStore | Password-based (Argon2id) | settings_datastore |
| Contacts | JSON File | App-level (XSalsa20) | files/contacts.json |
| Messages | JSON File | E2E (per-contact) | files/messages.json |
| Call History | Room DB (future) | None | app database |

### State Management

```kotlin
// ViewModel StateFlow Pattern
class MainViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    // Combine multiple data sources
    val dashboardState = combine(
        contactRepository.contacts,
        pttManager.channelState,
        locationManager.peerLocations
    ) { contacts, channel, locations ->
        DashboardState(contacts, channel, locations)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardState())
}
```

---

## Network Architecture

### Protocol Stack

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              APPLICATION                                     │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │  PTT Voice  │  Signaling  │  Location  │  CoT/ATAK  │  Radio API   │    │
│  └──────┬──────┴──────┬──────┴──────┬─────┴──────┬─────┴───────┬──────┘    │
├─────────┼─────────────┼─────────────┼────────────┼─────────────┼────────────┤
│         │             │             │            │             │            │
│         ▼             ▼             ▼            ▼             ▼            │
│  ┌────────────┐ ┌───────────┐ ┌──────────┐ ┌──────────┐ ┌────────────┐     │
│  │Multicast   │ │    TCP    │ │Multicast │ │Multicast │ │  HTTP/     │     │
│  │RTP/UDP     │ │Signaling  │ │  UDP     │ │  UDP     │ │  JSON-RPC  │     │
│  │239.255.0.x │ │  :10001   │ │239.255.1.x│ │239.2.3.1 │ │   :80      │     │
│  │  :5004     │ │           │ │  :5005   │ │  :6969   │ │            │     │
│  └──────┬─────┘ └─────┬─────┘ └────┬─────┘ └────┬─────┘ └──────┬─────┘     │
│         │             │            │            │              │            │
├─────────┼─────────────┼────────────┼────────────┼──────────────┼────────────┤
│         │             │            │            │              │            │
│         └──────────┬──┴────────────┴────────────┴──────────────┘            │
│                    │                                                         │
│                    ▼                                                         │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                         IP (BATMAN-adv L2)                           │    │
│  │                     10.223.x.x / 192.168.20.x                        │    │
│  └──────────────────────────────────┬──────────────────────────────────┘    │
├─────────────────────────────────────┼───────────────────────────────────────┤
│                                     │                                        │
│                                     ▼                                        │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                      MN-MIMO WAVEFORM (PHY)                          │    │
│  │                     DoodleLabs MeshRider Radio                       │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Port Allocation

| Port | Protocol | Direction | Purpose | QoS |
|------|----------|-----------|---------|-----|
| 5004 | UDP Multicast | Bidirectional | RTP Voice | DSCP EF (46) |
| 5005 | UDP Multicast | Bidirectional | Location Sharing | DSCP AF21 |
| 6969 | UDP Multicast | Bidirectional | CoT/ATAK SA | DSCP AF21 |
| 6970 | UDP Multicast | Bidirectional | CoT Mesh Rider | DSCP AF21 |
| 10001 | TCP | Bidirectional | P2P Signaling | Best Effort |
| 11111 | UDP Broadcast | Outbound | Radio Discovery | Best Effort |
| 80 | HTTP | Outbound | Radio JSON-RPC | Best Effort |

### Multicast Addressing

```
Talkgroup Voice Channels:
┌─────────────────────────────────────────────────────────────────┐
│  Talkgroup 1:   239.255.0.1:5004   (default)                   │
│  Talkgroup 2:   239.255.0.2:5004                               │
│  Talkgroup 3:   239.255.0.3:5004                               │
│  ...                                                            │
│  Talkgroup N:   239.255.0.N:5004   (N = 1-255)                 │
└─────────────────────────────────────────────────────────────────┘

Location Sharing:
┌─────────────────────────────────────────────────────────────────┐
│  BFT Updates:   239.255.1.1:5005                               │
└─────────────────────────────────────────────────────────────────┘

ATAK Integration:
┌─────────────────────────────────────────────────────────────────┐
│  SA Multicast:  239.2.3.1:6969   (ATAK default)                │
│  MR Multicast:  239.2.3.1:6970   (Mesh Rider)                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## Security Architecture

### Cryptographic Design

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           SECURITY LAYERS                                    │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  LAYER 1: Identity                                                          │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │  Ed25519 Keypair                                                     │    │
│  │  • Generated on first launch                                         │    │
│  │  • Public key = Device identity                                      │    │
│  │  • Secret key encrypted with user password (Argon2id)               │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
│  LAYER 2: Key Exchange                                                      │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │  X25519 ECDH (Curve25519)                                           │    │
│  │  • Per-session shared secret                                         │    │
│  │  • Ed25519 → X25519 key conversion                                  │    │
│  │  • Forward secrecy via ephemeral keys                               │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
│  LAYER 3: Message Encryption                                                │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │  XSalsa20-Poly1305 (AEAD)                                           │    │
│  │  • 256-bit key                                                       │    │
│  │  • 192-bit nonce (random per message)                               │    │
│  │  • Authenticated encryption                                          │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
│  LAYER 4: Group Encryption (MLS)                                            │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │  Messaging Layer Security (RFC 9420)                                │    │
│  │  • Group key agreement                                               │    │
│  │  • Post-compromise security                                          │    │
│  │  • Efficient member add/remove                                       │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Key Hierarchy

```
Master Password (user-provided)
         │
         ▼ Argon2id (memory-hard KDF)
┌─────────────────────────────┐
│   Password-Derived Key      │
│   (256 bits)                │
└──────────────┬──────────────┘
               │
               ▼ Decrypt
┌─────────────────────────────┐
│   Ed25519 Secret Key        │──────► Sign messages
│   (256 bits)                │        Derive identity
└──────────────┬──────────────┘
               │
               ▼ Convert (birational map)
┌─────────────────────────────┐
│   X25519 Secret Key         │──────► ECDH key exchange
│   (256 bits)                │
└──────────────┬──────────────┘
               │
               ▼ ECDH with peer
┌─────────────────────────────┐
│   Shared Session Key        │──────► XSalsa20-Poly1305
│   (256 bits)                │
└─────────────────────────────┘
```

### Threat Model

| Threat | Mitigation | Status |
|--------|------------|--------|
| Eavesdropping | E2E encryption (XSalsa20-Poly1305) | ✅ |
| Man-in-the-Middle | Ed25519 signatures + public key verification | ✅ |
| Replay Attack | Nonce uniqueness + timestamp validation | ✅ |
| Key Compromise | Forward secrecy via ephemeral keys | ✅ |
| Brute Force | Argon2id password hashing (memory-hard) | ✅ |
| Traffic Analysis | Multicast (all nodes see same traffic) | ✅ |
| Denial of Service | Rate limiting + circuit breaker | ✅ |

---

## Audio Pipeline Architecture

### Transmission Pipeline

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         TRANSMISSION (TX) PIPELINE                           │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌──────────┐    ┌──────────┐    ┌──────────┐    ┌──────────┐              │
│  │Microphone│───►│   VAD    │───►│  Opus    │───►│   RTP    │              │
│  │ 16 kHz   │    │  (opt)   │    │ Encoder  │    │ Packetize│              │
│  │  Mono    │    │          │    │ 6-24kbps │    │ RFC 3550 │              │
│  └──────────┘    └──────────┘    └──────────┘    └────┬─────┘              │
│                                                        │                     │
│       Frame: 320 samples (20ms) @ 16 kHz              │                     │
│       Bitrate: 6-24 kbps (adaptive)                   ▼                     │
│                                                  ┌──────────┐              │
│  ┌──────────┐    ┌──────────┐    ┌──────────┐   │ Encrypt  │              │
│  │   UDP    │◄───│  DSCP    │◄───│ Multicast│◄──│(optional)│              │
│  │  Socket  │    │ EF (46)  │    │239.255.0.x│   │          │              │
│  │  :5004   │    │  TOS=184 │    │          │   └──────────┘              │
│  └──────────┘    └──────────┘    └──────────┘                              │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Reception Pipeline

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          RECEPTION (RX) PIPELINE                             │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌──────────┐    ┌──────────┐    ┌──────────┐    ┌──────────┐              │
│  │   UDP    │───►│ Multicast│───►│   RTP    │───►│  Jitter  │              │
│  │  Socket  │    │ Receive  │    │ Depacket │    │  Buffer  │              │
│  │  :5004   │    │239.255.0.x│    │ RFC 3550 │    │ 20-100ms │              │
│  └──────────┘    └──────────┘    └──────────┘    └────┬─────┘              │
│                                                        │                     │
│       Adaptive buffer depth based on jitter           │                     │
│       Packet reordering with sequence numbers         ▼                     │
│                                                  ┌──────────┐              │
│  ┌──────────┐    ┌──────────┐    ┌──────────┐   │ Decrypt  │              │
│  │  Speaker │◄───│  Opus    │◄───│   PLC    │◄──│(optional)│              │
│  │  16 kHz  │    │ Decoder  │    │ Packet   │   │          │              │
│  │  Mono    │    │          │    │ Loss     │   └──────────┘              │
│  └──────────┘    └──────────┘    └──────────┘                              │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Audio Specifications

| Parameter | Value | Notes |
|-----------|-------|-------|
| Sample Rate | 16 kHz | Narrowband voice |
| Channels | Mono | Single channel |
| Frame Size | 20 ms | 320 samples |
| Bit Depth | 16-bit | PCM encoding |
| Opus Bitrate | 6-24 kbps | Adaptive based on link |
| Jitter Buffer | 20-100 ms | RFC 3550 adaptive |
| End-to-End Latency | <200 ms | Target for tactical PTT |

---

## Deployment Architecture

### Android Device Deployment

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          ANDROID DEVICE                                      │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌──────────────────────────────────────────────────────────────────────┐   │
│  │                         MR WAVE APK                                   │   │
│  │  ┌─────────────────────────────────────────────────────────────┐     │   │
│  │  │                    Application Layer                         │     │   │
│  │  │  • MainActivity (Compose UI)                                │     │   │
│  │  │  • ViewModels (StateFlow)                                   │     │   │
│  │  │  • Navigation (Compose Nav)                                 │     │   │
│  │  └─────────────────────────────────────────────────────────────┘     │   │
│  │  ┌─────────────────────────────────────────────────────────────┐     │   │
│  │  │                    Service Layer                             │     │   │
│  │  │  • MeshService (Foreground)                                 │     │   │
│  │  │  • BootReceiver (Auto-start)                                │     │   │
│  │  └─────────────────────────────────────────────────────────────┘     │   │
│  │  ┌─────────────────────────────────────────────────────────────┐     │   │
│  │  │                    Native Libraries                          │     │   │
│  │  │  • libsodium.so (crypto)                                    │     │   │
│  │  │  • libopus.so (audio codec)                                 │     │   │
│  │  │  • libwebrtc.so (optional, for video)                       │     │   │
│  │  └─────────────────────────────────────────────────────────────┘     │   │
│  └──────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
│  ┌──────────────────────────────────────────────────────────────────────┐   │
│  │                         ATAK PLUGIN APK                               │   │
│  │  • MRWavePlugin.kt                                                   │   │
│  │  • PTTToolbarComponent.kt                                            │   │
│  │  • CoTReceiver.kt                                                    │   │
│  └──────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
│  ┌──────────────────────────────────────────────────────────────────────┐   │
│  │                        ANDROID SYSTEM                                 │   │
│  │  • Audio HAL (microphone, speaker)                                   │   │
│  │  • Network Stack (WiFi, Ethernet)                                    │   │
│  │  • Location Services (GPS)                                           │   │
│  │  • Notification Manager                                              │   │
│  └──────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
         │
         │ USB/Ethernet
         ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                        MESHRIDER RADIO                                       │
│  • BATMAN-adv mesh routing                                                  │
│  • MN-MIMO waveform (2.4/5 GHz)                                            │
│  • UBUS/JSON-RPC API                                                        │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Quality Attributes

### Performance Requirements

| Attribute | Requirement | Target |
|-----------|-------------|--------|
| Voice Latency | End-to-end | <200 ms |
| PTT Response | Button to TX | <50 ms |
| Floor Acquisition | Arbitration | <100 ms |
| Battery Life | Active PTT | 8+ hours |
| Memory Usage | Runtime | <150 MB |
| APK Size | Installed | <50 MB |

### Reliability

| Attribute | Requirement |
|-----------|-------------|
| Uptime | 99.9% service availability |
| Crash Rate | <0.1% sessions |
| Audio Quality | MOS 3.5+ (narrowband) |
| Packet Loss Tolerance | <10% without degradation |

### Scalability

| Dimension | Limit | Notes |
|-----------|-------|-------|
| Talkgroups | 255 | Multicast address space |
| Peers per Talkgroup | Unlimited | Multicast = O(1) send |
| Simultaneous Speakers | 1 | Half-duplex PTT |
| Contact List | 10,000 | JSON storage |

### Security

| Requirement | Implementation |
|-------------|----------------|
| Data at Rest | Encrypted (XSalsa20) |
| Data in Transit | E2E encrypted |
| Authentication | Ed25519 signatures |
| Key Management | Argon2id + secure storage |
| Audit Logging | Local only (no telemetry) |

---

## Appendix A: Technology Decisions

### Why Kotlin over Java?

| Factor | Decision |
|--------|----------|
| Null Safety | Kotlin's null safety prevents NPEs |
| Coroutines | Native async support for network/audio |
| Compose | First-class Compose support |
| Conciseness | 40% less boilerplate |

### Why Opus over Other Codecs?

| Factor | Opus | AMR-WB | CODEC2 |
|--------|------|--------|--------|
| Bitrate | 6-24 kbps | 12.65-23.85 | 0.7-3.2 |
| Latency | 5-66 ms | 25 ms | 20-40 ms |
| Quality | Excellent | Good | Fair |
| License | BSD | Proprietary | LGPL |
| Platform | Universal | Android native | Requires JNI |

### Why Multicast over Unicast?

| Factor | Multicast | Unicast |
|--------|-----------|---------|
| Bandwidth | O(1) | O(n) |
| Latency | Constant | Increases with peers |
| Mesh Friendly | Yes (L2 via BATMAN) | No |
| Complexity | Medium | Low |

---

## Appendix B: References

1. RFC 3550 - RTP: A Transport Protocol for Real-Time Applications
2. RFC 2474 - Definition of the Differentiated Services Field
3. RFC 6716 - Definition of the Opus Audio Codec
4. RFC 9420 - The Messaging Layer Security (MLS) Protocol
5. libsodium Documentation - https://doc.libsodium.org/
6. ATAK Developer Guide - https://tak.gov/
7. Android Audio Architecture - developer.android.com

---

**Document Control:**

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | Jan 2026 | Jabbir Basha | Initial release |
| 2.0 | Jan 2026 | Claude Code | Complete rewrite |

---

*Copyright (C) 2024-2026 DoodleLabs Singapore Pte Ltd. All Rights Reserved.*
