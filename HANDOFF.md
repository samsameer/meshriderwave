# MeshRider Wave — Complete Project Handoff

**Document Version:** 1.0
**Date:** January 14, 2026
**Author:** Jabbir Basha P
**For:** DoodleLabs Engineering Team

---

## Table of Contents

1. [Executive Summary](#executive-summary)
2. [What is MR Wave?](#what-is-mr-wave)
3. [Architecture Overview](#architecture-overview)
4. [MR Wave App (Android)](#mr-wave-app-android)
5. [MR Radio Integration (LEDE)](#mr-radio-integration-lede)
6. [Key Components Explained](#key-components-explained)
7. [Build & Run Instructions](#build--run-instructions)
8. [Feature Status](#feature-status)
9. [Integration Points](#integration-points)
10. [Technical Decisions](#technical-decisions)
11. [Known Issues](#known-issues)
12. [Future Roadmap](#future-roadmap)
13. [FAQ](#faq)

---

## Executive Summary

**MR Wave** is a military-grade Push-to-Talk (PTT) Android application for DoodleLabs MeshRider radios.

**Key Point:** The implementation is just an App. It uses whatever audio device is connected to the phone. The radio provides IP transport only.

| Attribute | Value |
|-----------|-------|
| Platform | Android (Kotlin + Jetpack Compose) |
| Status | BETA (80% complete) |
| Min SDK | API 26 (Android 8.0) |
| Package | `com.doodlelabs.meshriderwave` |

**What it does:**
- Push-to-Talk voice over mesh network
- End-to-end encrypted communications
- Blue Force Tracking (location sharing)
- SOS emergency alerts
- Group messaging

**What the radio does:**
- IP transport (BATMAN-adv mesh routing)
- Nothing PTT-specific — all logic is in the app

---

## What is MR Wave?

### The Problem

MeshRider radios provide excellent mesh networking, but lack native voice capability. MR Wave fills this gap with:
- Unlimited talkgroups
- Works on any Android device
- Open integration with MeshRider ecosystem

### The Solution

MR Wave turns any Android device into a tactical PTT handset:

```
┌─────────────────────────────────────────────────────────────┐
│                      USER'S PHONE                            │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐  │
│  │ MR Wave App │  │   Android   │  │  Any Headset/Mic    │  │
│  │  (PTT Logic)│  │ Audio Stack │  │  (BT/USB/3.5mm)     │  │
│  └──────┬──────┘  └──────┬──────┘  └──────────┬──────────┘  │
│         │                │                     │             │
│         └────────────────┴─────────────────────┘             │
│                          │                                   │
│                     WiFi/Ethernet                            │
│                          │                                   │
├──────────────────────────┼───────────────────────────────────┤
│                   MESHRIDER RADIO                            │
│         (IP Transport Only - No PTT Logic)                   │
└──────────────────────────┴───────────────────────────────────┘
```

### Key Advantages

| Feature | MR Wave |
|---------|---------|
| Talkgroups | Unlimited |
| Audio Codec | Opus 6-24 kbps |
| Encryption | E2E (libsodium + MLS) |
| Hardware | Any Android phone |
| Transport | Multicast RTP |

---

## Architecture Overview

### System Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        MR WAVE APP                               │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│   ┌─────────────────────────────────────────────────────────┐   │
│   │                  PRESENTATION LAYER                      │   │
│   │  Jetpack Compose UI + ViewModels + Navigation           │   │
│   └─────────────────────────────────────────────────────────┘   │
│                              │                                   │
│   ┌─────────────────────────────────────────────────────────┐   │
│   │                    DOMAIN LAYER                          │   │
│   │  Models (Contact, CallState, PTTChannel, Group)         │   │
│   │  Repository Interfaces                                   │   │
│   └─────────────────────────────────────────────────────────┘   │
│                              │                                   │
│   ┌─────────────────────────────────────────────────────────┐   │
│   │                     DATA LAYER                           │   │
│   │  Repository Implementations + DataStore + Room DB       │   │
│   └─────────────────────────────────────────────────────────┘   │
│                              │                                   │
│   ┌─────────────────────────────────────────────────────────┐   │
│   │                     CORE LAYER                           │   │
│   │                                                          │   │
│   │  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐   │   │
│   │  │  Crypto  │ │   PTT    │ │  Audio   │ │ Network  │   │   │
│   │  │libsodium │ │ Manager  │ │  Opus    │ │  Mesh    │   │   │
│   │  │   MLS    │ │  Floor   │ │  RTP     │ │ Service  │   │   │
│   │  └──────────┘ └──────────┘ └──────────┘ └──────────┘   │   │
│   │                                                          │   │
│   │  ┌──────────┐ ┌──────────┐ ┌──────────┐                │   │
│   │  │ Location │ │   SOS    │ │ Offline  │                │   │
│   │  │   BFT    │ │Emergency │ │ Message  │                │   │
│   │  └──────────┘ └──────────┘ └──────────┘                │   │
│   └─────────────────────────────────────────────────────────┘   │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
                               │
                          IP Network
                               │
┌─────────────────────────────────────────────────────────────────┐
│                     MESHRIDER RADIO (LEDE)                       │
│                                                                  │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐  │
│  │  BATMAN-adv  │  │   libortp    │  │   MN-MIMO Waveform   │  │
│  │  Mesh Layer  │  │   (exists)   │  │     (PHY Layer)      │  │
│  └──────────────┘  └──────────────┘  └──────────────────────┘  │
│                                                                  │
│  NOTE: No PTT daemon on radio. Radio is IP transport only.      │
└─────────────────────────────────────────────────────────────────┘
```

### Data Flow — PTT Transmission

```
1. User presses PTT button
         │
         ▼
2. PTTManager.requestFloor()
   - Check floor availability
   - Broadcast FLOOR_REQUEST to peers
   - Wait 200ms for denial
         │
         ▼
3. Floor Granted → startTransmission()
         │
         ▼
4. AudioRecord captures microphone
   - Source: VOICE_COMMUNICATION
   - Sample rate: 16kHz mono
   - Format: PCM 16-bit
         │
         ▼
5. OpusCodecManager.encode()
   - Input: 640 bytes PCM (20ms)
   - Output: 15-60 bytes Opus
   - Compression: 10-40x
         │
         ▼
6. RTPPacketManager.sendAudio()
   - Wrap in RTP header (RFC 3550)
   - Send to multicast 239.255.0.x:5004
   - DSCP EF (46) for QoS
         │
         ▼
7. MeshRider Radio forwards packet
   - BATMAN-adv mesh routing
   - All peers receive
         │
         ▼
8. Receiving app processes
   - RTP depacketization
   - Opus decode
   - AudioTrack playback
```

---

## MR Wave App (Android)

### Directory Structure

```
app/src/main/kotlin/com/doodlelabs/meshriderwave/
│
├── MeshRiderApp.kt              # Application class (Hilt entry point)
│
├── core/                        # Core business logic
│   ├── audio/
│   │   ├── MulticastAudioManager.kt  # High-level PTT audio manager
│   │   ├── OpusCodecManager.kt       # Opus encode/decode
│   │   └── RTPPacketManager.kt       # RTP packetization (RFC 3550)
│   │
│   ├── crypto/
│   │   ├── CryptoManager.kt          # E2E encryption (libsodium)
│   │   └── MLSGroupManager.kt        # MLS group encryption
│   │
│   ├── di/
│   │   └── AppModule.kt              # Hilt dependency injection
│   │
│   ├── emergency/
│   │   └── SOSManager.kt             # SOS emergency system
│   │
│   ├── location/
│   │   └── LocationSharingManager.kt # Blue Force Tracking
│   │
│   ├── messaging/
│   │   └── OfflineMessageManager.kt  # Store-and-forward messaging
│   │
│   ├── network/
│   │   ├── Connector.kt              # P2P connection logic
│   │   ├── MeshNetworkManager.kt     # Signaling & peer management
│   │   ├── MeshService.kt            # Foreground service
│   │   └── PeerDiscoveryManager.kt   # mDNS peer discovery
│   │
│   ├── ptt/
│   │   └── PTTManager.kt             # PTT floor control & transmission
│   │
│   └── webrtc/
│       └── RTCCall.kt                # WebRTC for 1-1 calls
│
├── data/                        # Data layer
│   ├── local/
│   │   ├── SettingsDataStore.kt      # Preferences storage
│   │   └── database/
│   │       ├── AppDatabase.kt        # Room database
│   │       ├── Daos.kt               # Data access objects
│   │       └── Entities.kt           # Database entities
│   │
│   └── repository/
│       ├── ContactRepositoryImpl.kt
│       ├── GroupRepositoryImpl.kt
│       └── SettingsRepositoryImpl.kt
│
├── domain/                      # Domain layer
│   ├── model/
│   │   ├── CallState.kt
│   │   ├── Contact.kt
│   │   ├── Event.kt
│   │   └── group/
│   │       ├── Group.kt
│   │       ├── GroupCallState.kt
│   │       └── PTTChannel.kt
│   │
│   └── repository/
│       ├── ContactRepository.kt      # Interface
│       ├── GroupRepository.kt        # Interface
│       └── SettingsRepository.kt     # Interface
│
└── presentation/                # UI layer
    ├── MainActivity.kt
    │
    ├── navigation/
    │   └── NavGraph.kt               # Compose Navigation
    │
    ├── ui/
    │   ├── components/
    │   │   ├── BottomNavBar.kt       # Bottom navigation
    │   │   ├── PremiumComponents.kt  # Glassmorphism components
    │   │   └── PremiumDialogs.kt     # Premium dialog system
    │   │
    │   ├── screens/
    │   │   ├── call/CallActivity.kt
    │   │   ├── channels/ChannelsScreen.kt
    │   │   ├── contacts/ContactsScreen.kt
    │   │   ├── dashboard/DashboardScreen.kt
    │   │   ├── groups/GroupsScreen.kt
    │   │   ├── map/MapScreen.kt
    │   │   ├── qr/QRShowScreen.kt, QRScanScreen.kt
    │   │   └── settings/SettingsScreen.kt
    │   │
    │   └── theme/
    │       ├── PremiumColors.kt      # Color palette
    │       ├── PremiumTheme.kt       # Theme definition
    │       └── Typography.kt
    │
    └── viewmodel/
        ├── ChannelsViewModel.kt
        ├── DashboardViewModel.kt
        ├── GroupsViewModel.kt
        ├── MainViewModel.kt
        └── MapViewModel.kt
```

### Key Files to Understand

| File | Lines | Purpose |
|------|-------|---------|
| `PTTManager.kt` | 1,083 | **Core PTT logic** — floor control, transmission, reception |
| `OpusCodecManager.kt` | 542 | Audio codec — compresses 256kbps to 6-24kbps |
| `RTPPacketManager.kt` | 567 | RTP protocol — packetization, multicast |
| `MeshNetworkManager.kt` | 506 | Signaling — peer connections, message routing |
| `LocationSharingManager.kt` | 728 | Blue Force Tracking — GPS, geofencing |
| `CryptoManager.kt` | ~400 | Encryption — libsodium Ed25519/X25519 |

---

## MR Radio Integration (LEDE)

### What Exists on Radio

```
/home/jabi/workspace/doodle/lede/
│
├── package/
│   ├── libs/
│   │   ├── libortp/          # RTP library v0.23.0 ✓
│   │   └── libsrtp/          # SRTP encryption v1.4.4 ✓
│   │
│   ├── network/
│   │   └── pjproject/        # SIP/media stack v2.4.5 ✓
│   │
│   └── dl-message-system/    # UDP JSON config (port 9994) ✓
│
└── No PTT daemon exists ✗
```

### What Radio Provides

1. **BATMAN-adv mesh routing** — Automatic mesh topology
2. **IP transport** — Routes packets between nodes
3. **MN-MIMO waveform** — Physical layer (proprietary)

### What Radio Does NOT Have

- No PTT daemon
- No audio capture/playback
- No floor control
- No talkgroup management

**All PTT logic is in the Android app.**

### Network Bridging (External Networks)

BATMAN-adv operates at **Layer 2** (MAC routing). Multicast frames pass through as regular L2 frames.

To bridge external Ethernet and MeshRider networks on the radio:

```bash
# L2 bridge between external network (eth0) and MR mesh (bat0)
brctl addbr br-gateway
brctl addif br-gateway eth0    # External network
brctl addif br-gateway bat0    # MeshRider mesh
ifconfig br-gateway up

# Enable multicast snooping for efficiency
echo 1 > /sys/devices/virtual/net/br-gateway/bridge/multicast_snooping
```

**How it works:**
- IP multicast 239.255.0.x maps to L2 MAC 01:00:5e:7f:00:xx
- BATMAN-adv forwards all L2 frames including multicast
- L2 bridge passes frames between external and MR segments
- No L3 routing needed — pure L2 forwarding

---

## Key Components Explained

### 1. PTTManager — The Heart of Voice

**Location:** `core/ptt/PTTManager.kt`

**What it does:**
- Manages PTT channels (talkgroups)
- Controls floor (who can speak)
- Handles audio capture and playback
- Coordinates with Opus codec and RTP transport

**Key Methods:**

```kotlin
// Create a channel
suspend fun createChannel(name: String, priority: ChannelPriority): Result<PTTChannel>

// Join existing channel
suspend fun joinChannel(invite: PTTChannelInvite): Result<PTTChannel>

// Request floor (press PTT)
suspend fun requestFloor(channel: PTTChannel?): FloorResult

// Release floor (release PTT)
suspend fun releaseFloor(channel: PTTChannel?)

// Emergency broadcast
suspend fun sendEmergencyBroadcast(channel: PTTChannel, audioClip: ByteArray?)
```

**Floor Control Algorithm:**
```
1. User presses PTT
2. Check if floor is free locally
3. Broadcast FLOOR_REQUEST to all peers
4. Wait 200ms for FLOOR_DENIED
5. If no denial → Floor granted, start transmitting
6. If denied → Show "Channel busy" message
```

### 2. OpusCodecManager — Bandwidth Saver

**Location:** `core/audio/OpusCodecManager.kt`

**What it does:**
- Compresses audio using Opus codec
- Reduces bandwidth from 256 kbps to 6-24 kbps
- Uses Android MediaCodec (API 29+)

**Bandwidth Comparison:**
```
Raw PCM 16kHz mono: 256 kbps
Opus @ 24 kbps:      24 kbps  (10.7x compression)
Opus @ 12 kbps:      12 kbps  (21.3x compression)
Opus @ 6 kbps:        6 kbps  (42.7x compression)
```

**Configuration:**
```kotlin
val config = OpusCodecManager.CodecConfig(
    sampleRate = 16000,      // 16kHz
    channels = 1,            // Mono
    bitrate = 12000,         // 12 kbps (default)
    frameSize = 320          // 20ms frames
)
```

### 3. RTPPacketManager — Network Protocol

**Location:** `core/audio/RTPPacketManager.kt`

**What it does:**
- Implements RFC 3550 (RTP)
- Handles multicast group join/leave
- Marks packets with DSCP EF for QoS

**Multicast Addressing:**
```
Talkgroup 1:  239.255.0.1:5004
Talkgroup 2:  239.255.0.2:5004
...
Talkgroup 255: 239.255.0.255:5004
```

**RTP Packet Structure:**
```
 0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|V=2|P|X|  CC   |M|     PT      |       sequence number         |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                           timestamp                           |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                             SSRC                              |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                         Opus payload                          |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```

### 4. CryptoManager — Security

**Location:** `core/crypto/CryptoManager.kt`

**What it does:**
- Generates Ed25519 key pairs (identity)
- Encrypts messages with X25519 + XSalsa20-Poly1305
- Provides sealed box encryption

**Key Operations:**
```kotlin
// Generate identity
val keyPair = cryptoManager.generateKeyPair()  // Ed25519

// Encrypt message to recipient
val encrypted = cryptoManager.encryptMessage(
    message = "Hello",
    recipientPublicKey = contact.publicKey,
    ownPublicKey = myPublicKey,
    ownSecretKey = mySecretKey
)

// Decrypt received message
val decrypted = cryptoManager.decryptMessage(
    ciphertext = encrypted,
    senderPublicKeyOut = senderKey,
    ownPublicKey = myPublicKey,
    ownSecretKey = mySecretKey
)
```

### 5. LocationSharingManager — Blue Force Tracking

**Location:** `core/location/LocationSharingManager.kt`

**What it does:**
- Shares GPS location with team
- Tracks team member positions
- Geofencing alerts (enter/exit zones)

**Update Intervals:**
```kotlin
INTERVAL_HIGH_ACCURACY = 5_000ms    // Active tracking
INTERVAL_BALANCED = 15_000ms        // Normal use
INTERVAL_LOW_POWER = 60_000ms       // Battery saving
INTERVAL_BACKGROUND = 300_000ms     // Background
```

---

## Build & Run Instructions

### Prerequisites

- Android Studio Hedgehog or newer
- JDK 17
- Android SDK 35
- Physical device or emulator (API 26+)

### Build Commands

```bash
# Navigate to project
cd /home/jabi/workspace/02_flutter_projects/mobile_apps/meshrider-wave-android

# Clean and build debug APK
./gradlew clean assembleDebug

# Install on connected device
./gradlew installDebug

# Build release APK
./gradlew assembleRelease

# Run tests
./gradlew test

# Lint check
./gradlew lint
```

### Run in Android Studio

1. Open project folder in Android Studio
2. Wait for Gradle sync
3. Select device/emulator
4. Click Run (Shift+F10)

### Required Permissions

The app requests these at runtime:
- `RECORD_AUDIO` — Microphone for PTT
- `ACCESS_FINE_LOCATION` — Blue Force Tracking
- `CAMERA` — QR code scanning
- `POST_NOTIFICATIONS` — Service notification

---

## Feature Status

### Completed (January 2026)

| Feature | Status | File |
|---------|--------|------|
| PTT System | ✅ 90% | `PTTManager.kt` |
| Opus Codec | ✅ 100% | `OpusCodecManager.kt` |
| Multicast RTP | ✅ 100% | `RTPPacketManager.kt` |
| E2E Encryption | ✅ 95% | `CryptoManager.kt` |
| MLS Groups | ✅ 80% | `MLSGroupManager.kt` |
| Blue Force Tracking | ✅ 80% | `LocationSharingManager.kt` |
| SOS Emergency | ✅ 80% | `SOSManager.kt` |
| Offline Messaging | ✅ 75% | `OfflineMessageManager.kt` |
| Premium UI | ✅ 85% | `DashboardScreen.kt` |
| QR Contact Exchange | ✅ 100% | `QRShowScreen.kt`, `QRScanScreen.kt` |

### Pending

| Feature | Status | Notes |
|---------|--------|-------|
| Unit Tests | ⚠️ 10% | Need 80%+ coverage |
| Hardware PTT Button | ❌ 0% | KeyEvent handling needed |
| ATAK Plugin | ❌ 0% | Future integration |
| Voice Commands | ❌ 0% | Future feature |
| Jitter Buffer | ⚠️ 50% | Basic implementation |

---

## Integration Points

### ATAK Integration

ATAK (Android Team Awareness Kit) can integrate via:

1. **ATAK Audio Plugin** — MR Wave provides PTT backend
2. **CoT (Cursor-on-Target)** — Share location data in ATAK format
3. **Shared Multicast** — Both use same IP layer

**ATAK is open source:** github.com/deptofdefense/AndroidTacticalAssaultKit-CIV

### External Network Bridge

Bridge external Ethernet and MR networks on radio:

```bash
# L2 bridge on MeshRider radio
brctl addbr br-gateway
brctl addif br-gateway eth0 bat0
ifconfig br-gateway up
```

### Third-Party Headphones

App uses Android audio routing — works automatically with:
- 3.5mm wired headsets
- USB audio devices
- Bluetooth HFP/A2DP headsets

No special configuration needed.

---

## Technical Decisions

### Why Kotlin + Compose?

- Modern Android standard (2025+)
- Less boilerplate than Java
- Declarative UI with Compose
- Coroutines for async operations

### Why Opus Codec?

- Best-in-class voice compression
- Built into Android MediaCodec (API 29+)
- 10-40x bandwidth savings
- Low latency (20ms frames)

### Why Multicast RTP?

- O(1) delivery to all peers (vs O(n) unicast)
- Industry standard (RFC 3550)
- DSCP QoS support
- Works with MeshRider mesh routing

### Why libsodium?

- Proven cryptographic library
- Ed25519 for identity (signing)
- X25519 for key exchange
- XSalsa20-Poly1305 for encryption

---

## Known Issues

### Active Issues

1. **Emulator Performance** — Heavy animations can crash emulator. Use physical device.

2. **AudioRecord Permission** — Ensure RECORD_AUDIO is granted before PTT.

3. **Multicast on WiFi** — Some WiFi routers block multicast. Test on MeshRider network.

### Workarounds

```kotlin
// If Opus MediaCodec unavailable (API < 29), falls back to passthrough
if (!opusCodec.isOpusEncoderSupported()) {
    // Uses raw PCM (higher bandwidth but still works)
}
```

---

## Future Roadmap

### Priority 1 — Production Ready

- [ ] Unit tests to 80%+ coverage
- [ ] Hardware PTT button support
- [ ] Jitter buffer improvements
- [ ] Packet loss concealment

### Priority 2 — Integrations

- [ ] ATAK plugin
- [ ] CoT location format
- [ ] TAK Server compatibility

### Priority 3 — Features

- [ ] Voice commands ("Mesh Rider, transmit")
- [ ] CODEC2 support (ultra-low bandwidth)
- [ ] Video PTT

---

## FAQ

### Q: Does PTT work without internet?

**Yes.** MR Wave uses local mesh network. No internet needed.

### Q: What happens if two people press PTT at same time?

Floor control handles this. First request wins. Second user gets "Channel busy" message.

### Q: Can I use Bluetooth headphones?

**Yes.** App uses Android audio routing. Any connected audio device works.

### Q: How many talkgroups are supported?

**255** talkgroups supported.

### Q: Is the audio encrypted?

**Yes.** E2E encrypted with libsodium. MLS for group keys.

### Q: What's the audio latency?

- Encoding: ~20ms (Opus frame)
- Network: Variable (mesh dependent)
- Decoding: ~20ms
- **Total: 50-150ms typical**

### Q: Can the radio do PTT without a phone?

**Not currently.** Would require building `dl-ptt` daemon in LEDE firmware. Significant additional work.

---

## Contact

**Original Developer:** Jabbir Basha P
**Email:** [internal]
**Repository:** `/home/jabi/workspace/02_flutter_projects/mobile_apps/meshrider-wave-android`

---

*Document generated: January 14, 2026*
