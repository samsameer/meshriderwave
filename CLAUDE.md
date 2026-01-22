# CLAUDE.md - Mesh Rider Wave Android

**Version:** 2.3.0 | **Last Updated:** January 21, 2026 | **Platform:** Native Android (Kotlin)

This document provides complete context for Claude Code to work effectively with the Mesh Rider Wave Android codebase. It covers architecture, patterns, dependencies, and implementation details.

> **Key Point:** The implementation is just an App. It uses whatever audio device is connected to the phone. The radio provides IP transport only.

## Related Documentation

| Document | Purpose |
|----------|---------|
| [README.md](README.md) | Quick start guide |
| [HANDOFF.md](HANDOFF.md) | Complete project handoff for team members |
| [QUICK-REFERENCE.md](QUICK-REFERENCE.md) | One-page reference card |
| [docs/PTT_GUIDE.md](docs/PTT_GUIDE.md) | PTT system guide with floor control & testing |

---

## Current Development Status

### Completed Features (January 2026)

**Phase 1 - Core Infrastructure:**
- [x] MLS Encryption Manager (`core/crypto/MLSManager.kt`)
- [x] PTT System (`core/ptt/PTTManager.kt`, `PTTAudioManager.kt`)
- [x] Peer Discovery (`core/network/PeerDiscoveryManager.kt`)
- [x] Premium Theme System (`presentation/ui/theme/PremiumTheme.kt`, `PremiumColors.kt`)
- [x] Glassmorphism UI Components (`presentation/ui/components/PremiumComponents.kt`)

**Phase 2 - Tactical Features:**
- [x] Location Sharing (`core/location/LocationSharingManager.kt`)
- [x] SOS System (`core/sos/SOSManager.kt`)
- [x] Offline Messaging (`core/messaging/OfflineMessageManager.kt`)
- [x] Geofencing with alerts

**Phase 3 - Identity-First Architecture (Jan 21, 2026):**
- [x] Network Type Detection (`core/network/NetworkTypeDetector.kt`)
- [x] Address Registry Model (`domain/model/AddressRecord.kt`, `NetworkType.kt`)
- [x] Multicast Beacon Discovery (`core/discovery/BeaconManager.kt`, `IdentityBeacon.kt`)
- [x] Contact Address Sync (`core/discovery/ContactAddressSync.kt`)
- [x] Smart Address Resolution (`core/network/Connector.kt` - enhanced)
- [x] Multi-source peer count in Dashboard

**UI Screens Implemented:**
- [x] **Dashboard** - Premium tactical dashboard with network orb, quick stats, action buttons
- [x] **Groups** - Group management with create/join/leave functionality
- [x] **Channels** - PTT channel management with voice activity
- [x] **Map** - Blue Force Tracking with peer locations
- [x] **Settings** - App configuration
- [x] **Contacts** - Contact management with QR exchange
- [x] **QR Show/Scan** - Contact exchange via QR codes

**ViewModels:**
- [x] `DashboardViewModel` - Dashboard state management
- [x] `GroupsViewModel` - Group operations
- [x] `ChannelsViewModel` - PTT channel state
- [x] `MapViewModel` - Location tracking state

### Build Status

**Last Successful Build:** January 22, 2026
```bash
./gradlew clean assembleDebug  # BUILD SUCCESSFUL
```

### Latest Update (January 22, 2026)

- **3GPP MCPTT Floor Control** - Military-grade floor control implementation:
  - `FloorControlManager.kt` - Complete state machine (IDLE → PENDING → GRANTED → RELEASING)
  - `FloorControlProtocol.kt` - Encrypted & signed floor control messages
  - `FloorArbitrator.kt` - Centralized arbiter mode for mission-critical ops
  - Lamport timestamps for distributed ordering
  - Priority-based preemption (EMERGENCY > HIGH > NORMAL > LOW)
  - Queue management with fair ordering
  - Unit tests in `FloorControlManagerTest.kt`
  - **Rating upgraded: 7.5/10 → 8.5/10**

- **PTT Documentation** - Comprehensive PTT system guide (`docs/PTT_GUIDE.md`):
  - Updated to reflect 3GPP MCPTT compliance
  - Floor control & multi-user collision handling (10+ devices)
  - WiFi and MeshRider radio testing scenarios
  - Real-world timeline examples with millisecond precision

- **UI/UX Improvements**:
  - Removed E2E Encrypted badge from Dashboard (cleaner UI)
  - Default username changed from "User" to "Mesh Rider"

- **Identity-First Architecture** - Military-grade contact address resolution:
  - `NetworkType` enum for MeshRider, WiFi, WiFi Direct, IPv6 link-local classification
  - `AddressRecord` model with reliability tracking (success/failure metrics)
  - `BeaconManager` for multicast identity beacon discovery (239.255.77.1:7777)
  - `IdentityBeacon` with Ed25519 signed payloads for authentication
  - `ContactAddressSync` bridges discovery events to persistent storage
  - `NetworkTypeDetector` monitors network changes for smart routing
  - Enhanced `Connector` with network-type-aware address prioritization
  - Combined mDNS + beacon peer count in Dashboard

### Previous Update (January 11, 2026)

- Added **Premium Dialogs** (`PremiumDialogs.kt`) with world-class UI/UX:
  - `PremiumPermissionDialog` - For runtime permissions with animated icons
  - `PremiumConfirmDialog` - For confirmations with destructive action support
  - `PremiumInputDialog` - For text input with validation
  - `PremiumCreateGroupDialog` - MLS-encrypted group creation
  - `PremiumCreateChannelDialog` - PTT channel creation
  - `PremiumSOSConfirmDialog` - Emergency SOS activation
  - `PremiumButton`, `PremiumOutlineButton`, `PremiumTextField`
- Added new colors: `AuroraGreen`, `SolarGold` to `PremiumColors`

### Known Issues (Active)

1. **Emulator Performance** - Heavy Canvas animations caused crashes; simplified to Box-based UI
2. **Start Destination** - Fixed to use Dashboard (was Home)
3. **BottomNavBar Route** - HOME route changed from "home" to "dashboard"

---

## Production Readiness Assessment

### Status: BETA (80% Complete)

**NOT RECOMMENDED for production deployment.** The app functions for development and testing but requires critical fixes before field deployment.

### Completion by Component

| Component | Status | Completion |
|-----------|--------|------------|
| Core P2P Calling | Working | 90% |
| E2E Encryption | Working | 95% |
| PTT System | Partial | 70% |
| UI/UX | Working | 85% |
| Blue Force Tracking | Working | 80% |
| SOS System | Working | 80% |
| Offline Messaging | Working | 75% |

### Critical Gaps Blocking Production

1. **Audio Codec Missing**
   - Current: Raw PCM 16-bit @ 16kHz (256 kbps per stream)
   - Required: Opus codec (6-24 kbps) or CODEC2 (0.7-3.2 kbps)
   - Impact: 10-100x higher bandwidth than competitors

2. **No Multicast Support**
   - Current: Unicast to each peer (O(n) connections for n peers)
   - Required: Multicast RTP for talkgroups
   - Impact: Cannot scale efficiently to many talkgroups

3. **No DSCP/QoS Marking**
   - Current: All traffic treated as best-effort
   - Required: DSCP EF (46) for voice per RFC 2474/2475
   - Impact: Voice quality degrades under network load

4. **No Radio Integration**
   - Current: App works over any IP network
   - Required: MeshRider radio API for channel info, link quality
   - Impact: Cannot leverage mesh-specific optimizations

5. **Missing Unit Tests**
   - Current: 0% test coverage
   - Required: 80%+ for production
   - Impact: Regression risk on updates

### PTT System Technical Analysis

**Source:** `core/ptt/PTTManager.kt` (891 lines, verified January 2026)

```kotlin
// Audio Configuration (verified from code)
private const val SAMPLE_RATE = 16000      // 16 kHz
private const val CHANNELS = 1              // Mono
private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
private const val FLOOR_REQUEST_TIMEOUT = 200L  // 200ms arbitration

// Current Implementation
- Floor control: Distributed arbitration with priority support
- Transmission: startTransmission() / stopTransmission()
- Reception: handleIncomingTransmission() with AudioTrack playback
- Emergency: isEmergency flag with priority override
```

**What Works:**
- Half-duplex floor control (FCP-OMA style)
- Priority-based transmission arbitration
- Emergency broadcast override
- Channel join/leave notifications
- Peer address registration via mDNS

**What's Missing:**
| Gap | Current | Required | Effort |
|-----|---------|----------|--------|
| Audio Codec | Raw PCM | Opus/CODEC2 | 2 weeks |
| Network Transport | Unicast TCP | Multicast RTP | 3 weeks |
| QoS | None | DSCP EF (46) | 1 week |
| Jitter Buffer | None | Adaptive 20-60ms | 1 week |
| Packet Loss Concealment | None | Opus PLC | 1 week |

---

## MR Wave Key Features

| Feature | Implementation |
|---------|----------------|
| Talkgroups | Unlimited (software-defined) |
| Audio Codec | Opus 6-24 kbps |
| Transport | Multicast RTP (L2 via BATMAN-adv) |
| Encryption | E2E libsodium + MLS |
| QoS | DSCP EF (46) |
| Platform | Android (any device) |

### Roadmap to Production

| Phase | Deliverable |
|-------|-------------|
| 1 | Opus codec integration |
| 2 | Multicast RTP transport |
| 3 | DSCP QoS marking |
| 4 | Jitter buffer + PLC |
| 5 | Unit tests (80% coverage) |
| 6 | Field testing with radios |

---

## LEDE Firmware Integration

### Current State (Verified January 2026)

**DoodleLabs LEDE Codebase:** `/home/jabi/workspace/doodle/lede/`

| Component | Status | Purpose |
|-----------|--------|---------|
| `message-system` | EXISTS | UDP JSON config sync (port 9994) - NOT for audio |
| `libortp` | EXISTS | RTP library v0.23.0 - building block |
| `libsrtp` | EXISTS | SRTP encryption v1.4.4 |
| `pjproject` | EXISTS | SIP/media stack v2.4.5 |
| PTT Daemon | MISSING | No multicast audio daemon |
| Talkgroup Manager | MISSING | No channel management |
| LuCI PTT UI | MISSING | No web interface |

### Proposed LEDE Package: `dl-ptt`

```
package/dl-ptt/
├── Makefile              # OpenWRT package definition
├── src/
│   ├── main.c            # PTT daemon entry point
│   ├── audio_capture.c   # ALSA microphone capture
│   ├── audio_playback.c  # ALSA speaker output
│   ├── codec.c           # Opus/CODEC2 encoding
│   ├── rtp_multicast.c   # Multicast RTP transport
│   ├── floor_control.c   # Half-duplex arbitration
│   └── talkgroup.c       # Channel management
├── luci/
│   └── luci-app-ptt/     # LuCI web interface
└── config/
    └── ptt               # UCI configuration
```

### Integration Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                      ANDROID APP                             │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐  │
│  │ PTTManager  │←→│ MeshNetwork │←→│  Multicast RTP      │  │
│  │  (Floor)    │  │  Manager    │  │  239.255.0.x:5004   │  │
│  └─────────────┘  └─────────────┘  └─────────────────────┘  │
├─────────────────────────────────────────────────────────────┤
│                    MESHRIDER RADIO                           │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐  │
│  │  dl-ptt     │←→│ BATMAN-adv  │←→│  MN-MIMO Waveform   │  │
│  │  daemon     │  │  Mesh       │  │  (PHY Layer)        │  │
│  └─────────────┘  └─────────────┘  └─────────────────────┘  │
└─────────────────────────────────────────────────────────────┘

Multicast Groups (L2 via BATMAN-adv):
- 239.255.0.1:5004 → Talkgroup 1
- 239.255.0.2:5004 → Talkgroup 2
- ...
- 239.255.0.255:5004 → Talkgroup 255 (unlimited)
```

---

## Project Overview

**Mesh Rider Wave** is a military-grade P2P voice/video calling application for Android. It enables secure communication over local mesh networks without requiring internet connectivity.

| Attribute | Value |
|-----------|-------|
| Package ID | `com.doodlelabs.meshriderwave` |
| Min SDK | 26 (Android 8.0) |
| Target SDK | 35 (Android 15) |
| Language | Kotlin 2.1.0 |
| UI Framework | Jetpack Compose + Material 3 |
| Architecture | Clean Architecture + MVVM |
| Base Reference | [Meshenger](https://github.com/meshenger-app/meshenger-android) |

### Key Features

- **P2P Voice/Video Calls** via WebRTC (W3C 2025)
- **E2E Encryption** using libsodium (Ed25519 + X25519)
- **MLS Group Encryption** for secure group messaging
- **Push-to-Talk (PTT)** for tactical voice channels
- **Blue Force Tracking** with location sharing
- **SOS Emergency System** with geofencing
- **Offline Messaging** with store-and-forward
- **QR Code Contact Exchange** - No phone numbers needed
- **Offline-First** - Works without internet on local mesh
- **Foreground Service** - Always listening for incoming calls
- **Boot Receiver** - Auto-starts on device boot

---

## Build & Run Commands

```bash
# Navigate to project
cd /home/jabi/workspace/02_flutter_projects/mobile_apps/meshrider-wave-android

# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Install on connected device
./gradlew installDebug

# Run all tests
./gradlew test

# Run Android instrumented tests
./gradlew connectedAndroidTest

# Clean build
./gradlew clean

# Check dependencies
./gradlew dependencies

# Lint check
./gradlew lint

# Generate dependency updates report
./gradlew dependencyUpdates
```

### Android Studio

```
1. Open Android Studio
2. File -> Open -> Select meshrider-wave-android folder
3. Wait for Gradle sync
4. Run -> Run 'app'
```

---

## Architecture Overview

### Clean Architecture Layers

```
┌─────────────────────────────────────────────────────────────┐
│                    PRESENTATION LAYER                        │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐  │
│  │  Composable │  │  ViewModel  │  │  Navigation Graph   │  │
│  │   Screens   │  │  StateFlow  │  │                     │  │
│  └─────────────┘  └─────────────┘  └─────────────────────┘  │
├─────────────────────────────────────────────────────────────┤
│                      DOMAIN LAYER                            │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐  │
│  │   Models    │  │  Repository │  │     Use Cases       │  │
│  │  (Entities) │  │ Interfaces  │  │    (if needed)      │  │
│  └─────────────┘  └─────────────┘  └─────────────────────┘  │
├─────────────────────────────────────────────────────────────┤
│                       DATA LAYER                             │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐  │
│  │ Repository  │  │  DataStore  │  │   JSON Storage      │  │
│  │    Impl     │  │  (Settings) │  │   (Contacts)        │  │
│  └─────────────┘  └─────────────┘  └─────────────────────┘  │
├─────────────────────────────────────────────────────────────┤
│                       CORE LAYER                             │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐  │
│  │   Crypto    │  │   Network   │  │      WebRTC         │  │
│  │  (libsodium)│  │ (P2P/Socket)│  │  (Voice/Video)      │  │
│  └─────────────┘  └─────────────┘  └─────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

### Directory Structure

```
app/src/main/kotlin/com/doodlelabs/meshriderwave/
├── MeshRiderApp.kt                 # Application class (Hilt entry)
├── core/
│   ├── BootReceiver.kt             # Starts service on boot
│   ├── crypto/
│   │   ├── CryptoManager.kt        # E2E encryption (libsodium)
│   │   └── MLSManager.kt           # MLS group encryption
│   ├── di/
│   │   └── AppModule.kt            # Hilt DI modules
│   ├── discovery/                  # Identity-First Discovery [NEW Jan 2026]
│   │   ├── BeaconManager.kt        # Multicast beacon send/receive
│   │   ├── ContactAddressSync.kt   # Sync discovered addresses to contacts
│   │   └── IdentityBeacon.kt       # Signed identity beacon format
│   ├── location/
│   │   └── LocationSharingManager.kt  # Blue Force Tracking
│   ├── messaging/
│   │   └── OfflineMessageManager.kt   # Store-and-forward
│   ├── network/
│   │   ├── Connector.kt            # P2P connection with smart resolution [ENHANCED]
│   │   ├── MeshNetworkManager.kt   # Signaling & peer management
│   │   ├── MeshService.kt          # Foreground service
│   │   ├── NetworkTypeDetector.kt  # Network type monitoring [NEW Jan 2026]
│   │   └── PeerDiscoveryManager.kt # mDNS peer discovery
│   ├── ptt/
│   │   ├── PTTManager.kt           # Push-to-Talk logic
│   │   └── PTTAudioManager.kt      # PTT audio handling
│   ├── sos/
│   │   └── SOSManager.kt           # Emergency SOS system
│   ├── util/
│   │   └── Logger.kt               # Logging utility
│   └── webrtc/
│       └── RTCCall.kt              # WebRTC call handler
├── data/
│   ├── local/
│   │   └── SettingsDataStore.kt    # Preferences storage
│   └── repository/
│       ├── ContactRepositoryImpl.kt
│       └── SettingsRepositoryImpl.kt
├── domain/
│   ├── model/
│   │   ├── AddressRecord.kt        # Address registry entry [NEW Jan 2026]
│   │   ├── CallState.kt            # Call state machine
│   │   ├── Contact.kt              # Contact entity [ENHANCED with addressRegistry]
│   │   ├── Event.kt                # Call history event
│   │   └── NetworkType.kt          # Network type classification [NEW Jan 2026]
│   └── repository/
│       ├── ContactRepository.kt    # Interface
│       └── SettingsRepository.kt   # Interface
└── presentation/
    ├── MainActivity.kt             # Main entry point
    ├── navigation/
    │   └── NavGraph.kt             # Compose Navigation (start: Dashboard)
    ├── ui/
    │   ├── components/
    │   │   ├── BottomNavBar.kt     # Premium bottom navigation [NEW]
    │   │   ├── PremiumComponents.kt # Glassmorphism components [NEW]
    │   │   └── PremiumDialogs.kt   # Premium dialogs system [NEW]
    │   ├── screens/
    │   │   ├── call/
    │   │   │   └── CallActivity.kt # Full-screen call UI
    │   │   ├── channels/
    │   │   │   └── ChannelsScreen.kt # PTT channels [NEW]
    │   │   ├── contacts/
    │   │   │   └── ContactsScreen.kt
    │   │   ├── dashboard/
    │   │   │   └── DashboardScreen.kt # Premium dashboard [NEW]
    │   │   ├── groups/
    │   │   │   └── GroupsScreen.kt # Group management [NEW]
    │   │   ├── home/
    │   │   │   └── HomeScreen.kt
    │   │   ├── map/
    │   │   │   └── MapScreen.kt    # Blue Force Tracking [NEW]
    │   │   ├── qr/
    │   │   │   ├── QRScanScreen.kt
    │   │   │   └── QRShowScreen.kt
    │   │   └── settings/
    │   │       └── SettingsScreen.kt
    │   └── theme/
    │       ├── PremiumColors.kt    # Premium color palette [NEW]
    │       ├── PremiumTheme.kt     # Glassmorphism theme [NEW]
    │       ├── Theme.kt            # Material 3 theming
    │       └── Typography.kt
    └── viewmodel/
        ├── ChannelsViewModel.kt    # PTT state [NEW]
        ├── DashboardViewModel.kt   # Dashboard state [NEW]
        ├── GroupsViewModel.kt      # Groups state [NEW]
        ├── MainViewModel.kt        # Shared ViewModel
        └── MapViewModel.kt         # Map/location state [NEW]
```

---

## Core Components

### 1. CryptoManager (`core/crypto/CryptoManager.kt`)

Handles all cryptographic operations using libsodium.

```kotlin
// Key generation
val keyPair = cryptoManager.generateKeyPair()  // Ed25519

// Message encryption (for peer communication)
val encrypted = cryptoManager.encryptMessage(
    message = "Hello",
    recipientPublicKey = contact.publicKey,
    ownPublicKey = myPublicKey,
    ownSecretKey = mySecretKey
)

// Message decryption
val senderKey = ByteArray(32)
val decrypted = cryptoManager.decryptMessage(
    ciphertext = encrypted,
    senderPublicKeyOut = senderKey,  // Filled with sender's key
    ownPublicKey = myPublicKey,
    ownSecretKey = mySecretKey
)

// Database encryption (password-based)
val encryptedDb = cryptoManager.encryptDatabase(data, password)
val decryptedDb = cryptoManager.decryptDatabase(encryptedDb, password)
```

**Crypto Primitives:**
| Operation | Algorithm | Library |
|-----------|-----------|---------|
| Signing | Ed25519 | libsodium |
| Encryption | X25519 + XSalsa20-Poly1305 | libsodium |
| Key Derivation | Argon2id | libsodium |
| Sealed Box | crypto_box_seal | libsodium |

### 2. MeshNetworkManager (`core/network/MeshNetworkManager.kt`)

Manages P2P signaling and connection state.

```kotlin
// Start listening for connections
meshNetworkManager.start()

// Observe incoming calls
meshNetworkManager.incomingCalls.collect { call ->
    // call.socket, call.offer, call.senderPublicKey
}

// Initiate outgoing call
val result = meshNetworkManager.initiateCall(contact, offerSdp)
when (result) {
    is CallResult.Connected -> handleAnswer(result.answer)
    is CallResult.Declined -> showDeclinedMessage()
    is CallResult.Error -> showError(result.message)
}

// Stop service
meshNetworkManager.stop()
```

**Signaling Protocol:**
```
Port: 10001 (BuildConfig.SIGNALING_PORT)

Message Format:
┌──────────────────────────────────────┐
│ Length (4 bytes, big-endian)         │
├──────────────────────────────────────┤
│ Encrypted Payload (variable)         │
│ - Sender public key (32 bytes)       │
│ - Signed JSON message                │
└──────────────────────────────────────┘

Actions:
- "call" + offer → Incoming call
- "connected" + answer → Call accepted
- "declined" → Call rejected
- "ping" / "pong" → Peer discovery
- "status_change" → Online/offline
```

### 3. Connector (`core/network/Connector.kt`)

Handles connection to peers with smart address resolution.

```kotlin
// Connect to contact (uses network-type-aware prioritization)
val socket = connector.connect(contact)
```

**Smart Address Resolution Order (Jan 2026):**
1. **Network-type match** - Prioritize addresses matching current network
2. **Reliability score** - Higher success/failure ratio first
3. Last working address (cached)
4. All addresses from addressRegistry
5. Link-local with interface names (fe80::...%wlan0)
6. EUI-64 guessed addresses (from MAC)

**Network Type Priority:**
| Current Network | Priority Order |
|----------------|----------------|
| MeshRider | MESHRIDER → WIFI → WIFI_DIRECT → LINK_LOCAL |
| WiFi | WIFI → WIFI_DIRECT → LINK_LOCAL → MESHRIDER |
| Unknown | By reliability score, then discovery time |

### 4. Identity-First Discovery System (Jan 2026)

Military-grade peer discovery based on IETF HIP RFC 7401 (Identity/Locator Separation).

```
┌─────────────────────────────────────────────────────────────┐
│                   DISCOVERY SOURCES                          │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐  │
│  │ BeaconManager│  │ PeerDiscovery│  │   Incoming Conn   │  │
│  │ (Multicast)  │  │  (mDNS)      │  │   (TCP socket)    │  │
│  └──────┬───────┘  └──────┬───────┘  └─────────┬─────────┘  │
│         │                  │                    │            │
│         └──────────────────┼────────────────────┘            │
│                            ▼                                 │
│  ┌─────────────────────────────────────────────────────────┐│
│  │              ContactAddressSync                          ││
│  │  - Matches by public key (identity)                     ││
│  │  - Updates addressRegistry with network type            ││
│  │  - Tracks reliability metrics                           ││
│  └─────────────────────────────────────────────────────────┘│
│                            ▼                                 │
│  ┌─────────────────────────────────────────────────────────┐│
│  │              Contact.addressRegistry                     ││
│  │  List<AddressRecord> with:                              ││
│  │  - address, networkType, source                         ││
│  │  - successCount, failureCount, reliability              ││
│  └─────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────┘
```

**Beacon Protocol (239.255.77.1:7777):**
```
IdentityBeacon Format:
┌──────────────────────────────────────┐
│ Magic (4 bytes): "MRWV"              │
│ Version (1 byte): 0x01               │
│ Public Key (32 bytes): Ed25519       │
│ Name Length (1 byte)                 │
│ Name (variable, max 32)              │
│ Capabilities (1 byte): bitmask       │
│ Network Type (1 byte)                │
│ Timestamp (8 bytes): millis          │
│ Signature (64 bytes): Ed25519        │
└──────────────────────────────────────┘
```

**Network Type Classification:**
| Type | Detection Pattern |
|------|------------------|
| MESHRIDER | 10.223.x.x |
| WIFI | 192.168.x.x (not .49) |
| WIFI_DIRECT | 192.168.49.x |
| LINK_LOCAL | fe80:: |
| UNKNOWN | All others |

### 5. RTCCall (`core/webrtc/RTCCall.kt`)

WebRTC call handler with proper Android audio processing.

```kotlin
// Create call
val rtcCall = RTCCall(context, isOutgoing = true)
rtcCall.initialize()

// Set up callbacks
rtcCall.onLocalDescription = { sdp ->
    // Send SDP to peer via MeshNetworkManager
}

// Create peer connection
rtcCall.createPeerConnection(offer = remoteOffer)  // For incoming
rtcCall.createPeerConnection()  // For outgoing

// Handle remote answer
rtcCall.handleAnswer(answerSdp)

// Control call
rtcCall.setMicrophoneEnabled(false)
rtcCall.setCameraEnabled(true)
rtcCall.switchCamera()

// End call
rtcCall.hangup()
rtcCall.cleanup()
```

**WebRTC Configuration:**
| Setting | Value |
|---------|-------|
| SDP Semantics | Unified Plan |
| Gathering Policy | GATHER_ONCE |
| Audio Device | JavaAudioDeviceModule |
| Hardware AEC | Enabled |
| Hardware NS | Enabled |
| Video Codec | VP8/VP9/H264 |
| Video Resolution | 1280x720 @ 25fps |

### 6. MeshService (`core/network/MeshService.kt`)

Foreground service that listens for incoming calls.

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
```

**Service Types (Android 14+):**
- `FOREGROUND_SERVICE_TYPE_MICROPHONE`
- `FOREGROUND_SERVICE_TYPE_CAMERA`
- `FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK`

---

## Domain Models

### Contact

```kotlin
data class Contact(
    val publicKey: ByteArray,           // Ed25519 (32 bytes)
    val name: String,
    val addresses: List<String> = emptyList(),
    val blocked: Boolean = false,
    val createdAt: Long,
    val lastSeenAt: Long? = null,
    val lastWorkingAddress: String? = null
) {
    val deviceId: String    // First 8 bytes hex
    val shortId: String     // First 4 bytes hex uppercase

    fun toQrData(): String  // Export for QR
    companion object {
        fun fromQrData(data: String): Contact?  // Import from QR
    }
}
```

**QR Data Format:**
```
name|base64PublicKey|address1,address2,...
```

### CallState

```kotlin
data class CallState(
    val status: Status,           // IDLE, INITIATING, RINGING, CONNECTING, CONNECTED, ENDED, ERROR
    val direction: Direction,     // INCOMING, OUTGOING
    val type: Type,               // VOICE, VIDEO
    val contact: Contact?,
    val isMicEnabled: Boolean,
    val isCameraEnabled: Boolean,
    val isSpeakerEnabled: Boolean,
    val isFrontCamera: Boolean,
    val startTime: Long?,
    val errorMessage: String?
) {
    val isActive: Boolean
    val isConnected: Boolean
    val callDuration: Long
    val durationFormatted: String  // "MM:SS" or "H:MM:SS"
}
```

---

## Dependency Injection (Hilt)

### Module: AppModule

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton
    fun provideCryptoManager(): CryptoManager

    @Provides @Singleton
    fun provideConnector(): Connector

    @Provides @Singleton
    fun provideMeshNetworkManager(...): MeshNetworkManager

    @Provides @Singleton
    fun provideSettingsDataStore(...): SettingsDataStore

    @Provides @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds abstract fun bindContactRepository(impl: ContactRepositoryImpl): ContactRepository
    @Binds abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository
}
```

### Qualifiers

```kotlin
@IoDispatcher      // Dispatchers.IO
@DefaultDispatcher // Dispatchers.Default
@MainDispatcher    // Dispatchers.Main
@ApplicationScope  // CoroutineScope for app lifetime
```

---

## UI Layer (Jetpack Compose)

### Navigation

```kotlin
sealed class Screen(val route: String) {
    data object Dashboard : Screen("dashboard")  // Start destination
    data object Home : Screen("home")
    data object Contacts : Screen("contacts")
    data object Groups : Screen("groups")
    data object Channels : Screen("channels")    // PTT
    data object Map : Screen("map")              // Blue Force Tracking
    data object Settings : Screen("settings")
    data object QRShow : Screen("qr_show")
    data object QRScan : Screen("qr_scan")
    data object ContactDetail : Screen("contact/{publicKey}")
    data object GroupDetail : Screen("group/{groupId}")
}

// Bottom Navigation Items (BottomNavBar.kt)
enum class NavItem { HOME, GROUPS, CHANNELS, MAP, SETTINGS }
// HOME route = "dashboard" (not "home")
```

### Theme Colors (Premium Glassmorphism)

```kotlin
// PremiumColors.kt - Primary theme used in app
object PremiumColors {
    // Backgrounds
    val DeepSpace = Color(0xFF0A0E1A)       // Main background
    val SpaceGray = Color(0xFF1A1F2E)       // Cards
    val SpaceGrayLight = Color(0xFF252B3D)  // Elevated surfaces

    // Accent Colors
    val ElectricCyan = Color(0xFF00D4FF)    // Primary accent
    val NeonMagenta = Color(0xFFFF006E)     // Alerts/SOS
    val AuroraGreen = Color(0xFF00FF88)     // Success/Online
    val SolarGold = Color(0xFFFFB800)       // Warnings

    // Glass Effects
    val GlassWhite = Color(0x1AFFFFFF)      // 10% white
    val GlassBorder = Color(0x33FFFFFF)     // 20% white

    // Text
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xB3FFFFFF)   // 70% white
    val TextTertiary = Color(0x80FFFFFF)    // 50% white

    // Status
    val OnlineGreen = Color(0xFF00FF88)
    val OfflineGray = Color(0xFF6B7280)
    val SOSRed = Color(0xFFFF006E)
}

// Legacy MeshRiderColors (Theme.kt) - for backwards compatibility
object MeshRiderColors {
    val Primary = Color(0xFF1565C0)         // Tactical Blue
    val Secondary = Color(0xFFFF6F00)       // DoodleLabs Orange
    val TacticalGreen = Color(0xFF2E7D32)   // Military Green
    val CallAccept = Color(0xFF4CAF50)
    val CallDecline = Color(0xFFF44336)
}
```

### ViewModel Pattern

```kotlin
@HiltViewModel
class MainViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val contactRepository: ContactRepository,
    private val meshNetworkManager: MeshNetworkManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    // Collect flows in init block
    // Expose actions as functions
}
```

---

## Network Configuration

### Ports

| Port | Protocol | Purpose |
|------|----------|---------|
| 10001 | TCP | Signaling (offer/answer/hangup) |
| 11111 | UDP | Mesh broadcast (optional future) |

### Mesh Subnets (MeshRider Radios)

```kotlin
val MESH_SUBNETS = listOf(
    "10.223.",      // Default MeshRider
    "192.168.20.",  // Alternative
    "10.0.0."       // Lab setup
)
```

### Network Security Config

```xml
<!-- res/xml/network_security_config.xml -->
<network-security-config>
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="true">10.223.0.0</domain>
        <domain includeSubdomains="true">192.168.20.0</domain>
    </domain-config>
</network-security-config>
```

---

## Storage

### DataStore (Settings)

```kotlin
// Keys
val USERNAME = stringPreferencesKey("username")
val PUBLIC_KEY = stringPreferencesKey("public_key")  // Base64
val SECRET_KEY = stringPreferencesKey("secret_key")  // Base64
val NIGHT_MODE = booleanPreferencesKey("night_mode")
```

### JSON (Contacts)

```kotlin
// Location: files/contacts.json
// Format: Array of ContactDto
[
  {
    "publicKey": "base64...",
    "name": "Alice",
    "addresses": ["10.223.1.5"],
    "blocked": false,
    "createdAt": 1704067200000
  }
]
```

---

## Dependencies (libs.versions.toml)

### Core

| Library | Version | Purpose |
|---------|---------|---------|
| kotlin | 2.1.0 | Language |
| agp | 8.7.3 | Android Gradle Plugin |
| compose-bom | 2024.12.01 | Compose Bill of Materials |
| hilt | 2.53.1 | Dependency Injection |
| navigation | 2.8.5 | Compose Navigation |

### Networking & WebRTC

| Library | Version | Purpose |
|---------|---------|---------|
| webrtc | 119.0.0 | im.conversations.webrtc |
| okhttp | 4.12.0 | HTTP client (future) |
| ktor | 3.0.2 | Async networking (future) |

### Crypto

| Library | Version | Purpose |
|---------|---------|---------|
| libsodium | 2.0.2 | E2E encryption |
| tink | 1.15.0 | Google crypto (future) |

### Storage

| Library | Version | Purpose |
|---------|---------|---------|
| datastore | 1.1.1 | Preferences |
| room | 2.6.1 | SQLite (future) |

### QR Code

| Library | Version | Purpose |
|---------|---------|---------|
| zxing-core | 3.5.3 | QR generation |
| zxing-embedded | 4.3.0 | QR scanning |

---

## Common Tasks

### Add a New Screen

```kotlin
// 1. Create Screen composable
@Composable
fun NewScreen(
    onNavigateBack: () -> Unit,
    viewModel: MainViewModel = hiltViewModel()
) { ... }

// 2. Add route to NavGraph.kt
data object NewScreen : Screen("new_screen")

// 3. Add composable to NavHost
composable(Screen.NewScreen.route) {
    NewScreen(onNavigateBack = { navController.popBackStack() })
}
```

### Add a New Service Dependency

```kotlin
// 1. Create interface in domain/repository/
interface NewRepository { ... }

// 2. Create implementation in data/repository/
class NewRepositoryImpl @Inject constructor(...) : NewRepository { ... }

// 3. Bind in AppModule.kt
@Binds abstract fun bindNewRepository(impl: NewRepositoryImpl): NewRepository

// 4. Inject in ViewModel
class MainViewModel @Inject constructor(
    private val newRepository: NewRepository
) : ViewModel()
```

### Handle WebRTC Events

```kotlin
// In RTCCall.kt createPeerConnectionObserver()
override fun onIceConnectionChange(state: IceConnectionState) {
    when (state) {
        IceConnectionState.CONNECTED -> updateState { copy(status = Status.CONNECTED) }
        IceConnectionState.DISCONNECTED -> updateState { copy(status = Status.ENDED) }
        IceConnectionState.FAILED -> updateState { copy(status = Status.ERROR) }
        else -> {}
    }
}
```

---

## Testing Strategy

### Unit Tests (TODO)

```kotlin
// CryptoManagerTest.kt
class CryptoManagerTest {
    @Test
    fun `generateKeyPair returns valid Ed25519 keys`()

    @Test
    fun `encryptMessage and decryptMessage roundtrip`()

    @Test
    fun `encryptDatabase with wrong password fails`()
}
```

### Integration Tests (TODO)

```kotlin
// MeshNetworkManagerTest.kt
class MeshNetworkManagerTest {
    @Test
    fun `start creates server socket on port 10001`()

    @Test
    fun `initiateCall sends encrypted offer`()
}
```

### UI Tests (TODO)

```kotlin
// HomeScreenTest.kt
class HomeScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `status card shows Connected when service running`()
}
```

---

## Known Issues & TODOs

### Completed (January 2026)

- [x] Dashboard screen with premium UI
- [x] Groups screen with create/join functionality
- [x] Channels screen with PTT support
- [x] Map screen with Blue Force Tracking
- [x] Bottom navigation bar
- [x] MLS encryption manager
- [x] PTT system implementation
- [x] Peer discovery via mDNS
- [x] Location sharing manager
- [x] SOS emergency system
- [x] Offline messaging
- [x] Fixed emulator crash (simplified animations)
- [x] Identity-First Architecture (NetworkType, AddressRecord, BeaconManager)
- [x] PTT floor control documentation with multi-user scenarios
- [x] Default username "Mesh Rider" (was "User")
- [x] Removed E2E Encrypted badge from Dashboard
- [x] 3GPP MCPTT Floor Control (FloorControlManager, FloorControlProtocol, FloorArbitrator)
- [x] Floor control unit tests (FloorControlManagerTest.kt)

### High Priority

- [ ] Premium permission dialogs (replace default Android dialogs)
- [ ] Add video SurfaceViewRenderer to CallActivity
- [ ] Implement call history with Room database
- [ ] Add proper error handling with Snackbar messages
- [ ] Unit tests for CryptoManager (100% coverage)

### Medium Priority

- [ ] Accessibility support (contentDescription)
- [ ] Audio routing (speaker/earpiece/bluetooth)
- [ ] Loading states and skeleton screens
- [ ] Pull-to-refresh on contacts
- [ ] Onboarding flow for first-time users

### Low Priority

- [ ] Lottie animations for empty states
- [ ] Crash reporting (Firebase Crashlytics)

### Technical Debt

- [ ] Deprecation warnings (ArrowBack, List icons - use AutoMirrored)
- [ ] Deprecation warnings (statusBarColor, navigationBarColor)
- [ ] NsdManager.resolveService deprecation

---

## Debug Tips

### View Logs

```bash
# All MeshRider logs
adb logcat -s MeshRider:*

# Specific component
adb logcat -s MeshRider:CryptoManager MeshRider:RTCCall

# WebRTC logs
adb logcat | grep -E "(webrtc|WebRTC)"
```

### Inspect Network

```bash
# Check listening port
adb shell netstat -tlnp | grep 10001

# Monitor connections
adb shell "while true; do netstat -tn | grep 10001; sleep 1; done"
```

### Test QR Code

```kotlin
// Generate test QR data
val testData = "TestUser|${Base64.encodeToString(publicKey, Base64.NO_WRAP)}|10.223.1.100"
```

---

## Reference

### Based On

- [Meshenger Android](https://github.com/meshenger-app/meshenger-android) - Proven P2P/WebRTC
- [Google Sample: Sunflower](https://github.com/android/sunflower) - Clean Architecture
- [Now in Android](https://github.com/android/nowinandroid) - Modern Android patterns

### Related Documentation

- [WebRTC Android](https://webrtc.github.io/webrtc-org/native-code/android/)
- [libsodium Documentation](https://doc.libsodium.org/)
- [Jetpack Compose](https://developer.android.com/jetpack/compose)
- [Hilt](https://developer.android.com/training/dependency-injection/hilt-android)

---

## Developer & License

### Developer

**Jabbir Basha P** — Lead Software Engineer | DoodleLabs Singapore

- 15+ years experience
- Specializations: Android, Flutter, AI/ML, WebRTC, Cryptography

### License

```
Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.

PROPRIETARY SOFTWARE - UNAUTHORIZED USE PROHIBITED

This software is proprietary and confidential. Unauthorized copying,
modification, distribution, or use is strictly prohibited without
express written permission.
```

---

*Last Updated: January 21, 2026*
*Build Status: PASSING*
*Production Status: BETA (80% Complete)*
*For questions: Review source code or ask Claude Code*
