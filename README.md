# MeshRider Wave Android

<div align="center">

**Military-Grade Push-to-Talk for DoodleLabs MeshRider Mesh Radios**

[![Build Status](https://img.shields.io/badge/build-passing-brightgreen)]()
[![Platform](https://img.shields.io/badge/platform-Android%208.0+-blue)]()
[![Kotlin](https://img.shields.io/badge/kotlin-2.1.0-purple)]()
[![License](https://img.shields.io/badge/license-proprietary-red)]()

[Quick Start](#quick-start) • [Features](#features) • [Architecture](#architecture) • [Documentation](#documentation)

</div>

---

## Overview

MeshRider Wave is a tactical Push-to-Talk (PTT) application designed for deployment with DoodleLabs MeshRider mesh radios. The app transforms any Android device into a secure, military-grade voice communication terminal operating over resilient mesh networks.

> **Key Concept:** The app handles all PTT logic, audio processing, and encryption. The MeshRider radio provides IP transport only.

| Attribute | Value |
|-----------|-------|
| **Package** | `com.doodlelabs.meshriderwave` |
| **Min SDK** | 26 (Android 8.0 Oreo) |
| **Target SDK** | 35 (Android 15) |
| **Language** | Kotlin 2.1.0 |
| **UI Framework** | Jetpack Compose + Material 3 |
| **Status** | Beta (Production Testing Phase) |

---

## Quick Start

### Prerequisites

- Android Studio Ladybug (2024.3.1) or later
- JDK 17
- Android device or emulator (API 26+)
- MeshRider radio (optional for development)

### Build & Install

```bash
# Clone the repository
git clone https://github.com/doodlelabs/meshrider-wave-android.git
cd meshrider-wave-android

# Build debug APK
./gradlew assembleDebug

# Install on connected device
./gradlew installDebug

# Build release APK
./gradlew assembleRelease
```

### First Run

1. Grant required permissions (Microphone, Location, Nearby Devices)
2. Configure network settings for your MeshRider subnet (default: 10.223.x.x)
3. Scan QR code or enter contact details to add peers
4. Join or create a talkgroup
5. Press and hold PTT button to transmit

---

## Features

### Core Capabilities

| Feature | Description | Status |
|---------|-------------|--------|
| **PTT Voice** | Half-duplex voice with floor control | ✅ Complete |
| **Opus Codec** | 6-24 kbps voice compression (10-40x) | ✅ Complete |
| **Multicast RTP** | Efficient one-to-many transmission | ✅ Complete |
| **E2E Encryption** | libsodium + MLS group encryption | ✅ Complete |
| **Identity-First Discovery** | Multi-source peer discovery (mDNS + beacon) | ✅ Complete |
| **Smart Address Resolution** | Network-type-aware address prioritization | ✅ Complete |
| **Blue Force Tracking** | Real-time GPS location sharing | ✅ Complete |
| **SOS Emergency** | Priority broadcast with geofencing | ✅ Complete |
| **Offline Messaging** | Store-and-forward when offline | ✅ Complete |
| **QR Contact Exchange** | No phone numbers needed | ✅ Complete |
| **ATAK Integration** | CoT protocol + plugin | ✅ Complete |
| **Radio API** | JSON-RPC control of MeshRider radios | ✅ Complete |

### Audio Pipeline

```
TX: Microphone → VAD → Opus Encoder → RTP Packetizer → Multicast UDP
    (16kHz)      (20ms)  (6-24kbps)    (RFC 3550)      (239.255.0.x:5004)

RX: Multicast UDP → RTP Depacketizer → Jitter Buffer → Opus Decoder → Speaker
                     (RFC 3550)         (20-100ms)      (16kHz)
```

**Specifications:**
- Sample Rate: 16 kHz (narrowband voice)
- Frame Size: 20ms (320 samples)
- Opus Bitrate: 6-24 kbps (adaptive)
- Jitter Buffer: 20-100ms (adaptive, RFC 3550)
- QoS: DSCP EF (46) for expedited forwarding

### Talkgroup Addressing

```
Talkgroup 1:   239.255.0.1:5004
Talkgroup 2:   239.255.0.2:5004
...
Talkgroup N:   239.255.0.N:5004  (N = 1-255)
```

### Security

| Layer | Implementation |
|-------|----------------|
| Key Exchange | Ed25519 signing + X25519 ECDH |
| Symmetric | XSalsa20-Poly1305 (AEAD) |
| Key Derivation | Argon2id (password-based) |
| Group Keys | MLS (Messaging Layer Security) |
| RTP | DSCP marking (future: SRTP) |

---

## Architecture

### System Overview

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          ANDROID DEVICE                                      │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                     MR Wave Application                              │    │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────────┐   │    │
│  │  │ Presentation │  │    Core      │  │         Data             │   │    │
│  │  │              │  │              │  │                          │   │    │
│  │  │ • Screens    │  │ • PTT        │  │ • Settings DataStore    │   │    │
│  │  │ • ViewModels │←→│ • Audio      │←→│ • Contact JSON          │   │    │
│  │  │ • Navigation │  │ • Crypto     │  │ • Room DB (future)      │   │    │
│  │  │ • Theme      │  │ • Network    │  │                          │   │    │
│  │  │              │  │ • Location   │  │                          │   │    │
│  │  │              │  │ • Radio API  │  │                          │   │    │
│  │  └──────────────┘  └──────┬───────┘  └──────────────────────────┘   │    │
│  └───────────────────────────┼──────────────────────────────────────────┘    │
│                              │                                               │
│                              │ UDP Multicast (239.255.0.x:5004)             │
│                              │ + Radio API (HTTP JSON-RPC)                  │
│                              ↓                                               │
├─────────────────────────────────────────────────────────────────────────────┤
│                         MESHRIDER RADIO                                      │
│  ┌──────────────────────────────────────────────────────────────────────┐   │
│  │  BATMAN-adv Mesh  │  MN-MIMO Waveform  │  JSON-RPC API (UBUS)        │   │
│  └──────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Module Structure

```
app/src/main/kotlin/com/doodlelabs/meshriderwave/
├── MeshRiderApp.kt                    # Hilt Application
├── core/
│   ├── audio/
│   │   ├── AdaptiveJitterBuffer.kt    # RFC 3550 jitter buffer
│   │   ├── MulticastAudioManager.kt   # TX/RX audio pipeline
│   │   ├── OpusCodecManager.kt        # Opus/fallback codecs
│   │   ├── OpusFallbackCodec.kt       # ADPCM/G.711 for API<29
│   │   └── RTPPacketManager.kt        # RTP protocol (RFC 3550)
│   ├── atak/
│   │   ├── ATAKBridge.kt              # Intent receiver
│   │   ├── ATAKIntents.kt             # Intent constants
│   │   ├── CoTManager.kt              # CoT multicast
│   │   └── CoTMessage.kt              # CoT XML protocol
│   ├── crypto/
│   │   ├── CryptoManager.kt           # libsodium wrapper
│   │   └── MLSManager.kt              # MLS group encryption
│   ├── di/
│   │   └── AppModule.kt               # Hilt modules
│   ├── discovery/                     # Identity-First Discovery [NEW]
│   │   ├── BeaconManager.kt           # Multicast beacon send/receive
│   │   ├── ContactAddressSync.kt      # Sync discovered addresses
│   │   └── IdentityBeacon.kt          # Ed25519 signed beacon format
│   ├── location/
│   │   └── LocationSharingManager.kt  # Blue Force Tracking
│   ├── messaging/
│   │   └── OfflineMessageManager.kt   # Store-and-forward
│   ├── network/
│   │   ├── Connector.kt               # Smart P2P connection [ENHANCED]
│   │   ├── MeshNetworkManager.kt      # Signaling
│   │   ├── MeshService.kt             # Foreground service
│   │   ├── NetworkTypeDetector.kt     # Network monitoring [NEW]
│   │   └── PeerDiscoveryManager.kt    # mDNS discovery
│   ├── ptt/
│   │   ├── PTTManager.kt              # Floor control
│   │   └── PTTAudioManager.kt         # PTT audio handling
│   ├── radio/
│   │   ├── RadioApiClient.kt          # JSON-RPC/UBUS client
│   │   ├── RadioDiscoveryService.kt   # UDP discovery
│   │   └── RadioModule.kt             # Hilt DI
│   ├── sos/
│   │   └── SOSManager.kt              # Emergency system
│   └── util/
│       └── Logger.kt                  # Logging utility
├── data/
│   ├── local/
│   │   └── SettingsDataStore.kt       # Preferences
│   └── repository/
│       ├── ContactRepositoryImpl.kt
│       └── SettingsRepositoryImpl.kt
├── domain/
│   ├── model/
│   │   ├── AddressRecord.kt           # Address registry entry [NEW]
│   │   ├── CallState.kt
│   │   ├── Contact.kt                 # Enhanced with addressRegistry
│   │   ├── Event.kt
│   │   └── NetworkType.kt             # Network classification [NEW]
│   └── repository/
│       ├── ContactRepository.kt
│       └── SettingsRepository.kt
└── presentation/
    ├── MainActivity.kt
    ├── navigation/
    │   └── NavGraph.kt
    ├── ui/
    │   ├── components/
    │   │   ├── BottomNavBar.kt
    │   │   ├── PremiumComponents.kt
    │   │   └── PremiumDialogs.kt
    │   ├── screens/
    │   │   ├── channels/
    │   │   ├── contacts/
    │   │   ├── dashboard/
    │   │   ├── groups/
    │   │   ├── map/
    │   │   ├── qr/
    │   │   └── settings/
    │   └── theme/
    │       ├── PremiumColors.kt
    │       ├── PremiumTheme.kt
    │       └── Theme.kt
    └── viewmodel/
        ├── ChannelsViewModel.kt
        ├── DashboardViewModel.kt
        ├── GroupsViewModel.kt
        ├── MainViewModel.kt
        ├── MapViewModel.kt
        └── RadioStatusViewModel.kt
```

### ATAK Plugin Module

```
atak-plugin/
├── build.gradle.kts
├── src/main/
│   ├── AndroidManifest.xml
│   ├── java/com/doodlelabs/meshriderwave/atak/
│   │   ├── MRWavePlugin.kt              # Main plugin class
│   │   ├── MRWavePluginLifecycleProvider.kt
│   │   ├── MRWavePluginService.kt
│   │   ├── receivers/
│   │   │   ├── ChannelDropdownReceiver.kt
│   │   │   ├── CoTReceiver.kt
│   │   │   ├── MRWaveResponseReceiver.kt
│   │   │   └── PTTToolbarReceiver.kt
│   │   └── toolbar/
│   │       └── PTTToolbarComponent.kt
│   └── res/
│       ├── drawable/
│       └── values/strings.xml
└── atak-stubs/                          # SDK stubs for development
```

---

## Documentation

| Document | Description |
|----------|-------------|
| [README.md](README.md) | This file - Quick start and overview |
| [CLAUDE.md](CLAUDE.md) | Complete technical reference for AI assistants |
| [HANDOFF.md](HANDOFF.md) | Project handoff documentation |
| [QUICK-REFERENCE.md](QUICK-REFERENCE.md) | One-page cheat sheet |
| [ARCHITECTURE.md](docs/ARCHITECTURE.md) | System design document |
| [API_REFERENCE.md](docs/API_REFERENCE.md) | Module API documentation |
| [FIELD_TESTING.md](docs/FIELD_TESTING.md) | Radio field testing guide |

---

## Key Components

### PTTManager (`core/ptt/PTTManager.kt`)

Floor control and voice transmission management.

```kotlin
// Start transmission
pttManager.startTransmission(
    channelId = "channel-001",
    priority = PTTPriority.NORMAL
)

// Emergency override
pttManager.startTransmission(
    channelId = "channel-001",
    priority = PTTPriority.EMERGENCY,
    isEmergency = true
)

// Stop transmission
pttManager.stopTransmission()
```

### RadioApiClient (`core/radio/RadioApiClient.kt`)

JSON-RPC client for MeshRider radio control.

```kotlin
// Connect to radio
radioApiClient.connect("10.223.232.141", "root", "doodle")

// Get wireless status
val status = radioApiClient.getWirelessStatus()
// status.ssid, status.channel, status.signal, status.snr

// Get mesh peers
val stations = radioApiClient.getAssociatedStations()

// Switch channel (mesh-wide)
radioApiClient.switchChannel(channel = 149, bandwidth = 20)

// Get GPS from radio
val gps = radioApiClient.getGpsLocation()
```

### AdaptiveJitterBuffer (`core/audio/AdaptiveJitterBuffer.kt`)

RFC 3550 compliant jitter buffer with adaptive depth.

```kotlin
val buffer = JitterBufferFactory.createForPTT(
    frameTimeMs = 20,
    onPacketLoss = { count, lastSeq -> codec.decodePLC() }
)

// Receiver thread
buffer.put(packet)

// Playback thread
val packet = buffer.poll()
if (packet != null) {
    playAudio(packet.payload)
}
```

---

## Network Configuration

### Default Ports

| Port | Protocol | Purpose |
|------|----------|---------|
| 5004 | UDP | RTP Multicast (voice) |
| 6969 | UDP | CoT Multicast (ATAK SA) |
| 6970 | UDP | CoT Multicast (Mesh Rider) |
| 7777 | UDP | Identity Beacon (239.255.77.1) |
| 10001 | TCP | P2P Signaling |
| 11111 | UDP | Radio Discovery |
| 80 | HTTP | Radio JSON-RPC API |

### MeshRider Subnets

```kotlin
val MESH_SUBNETS = listOf(
    "10.223.",      // Default MeshRider
    "192.168.20.",  // Alternative
    "10.0.0."       // Lab setup
)
```

### DSCP QoS Marking

Voice traffic is marked with DSCP EF (Expedited Forwarding):
- DSCP Value: 46
- TOS Byte: 0xB8 (184)
- Per: RFC 2474, RFC 2475

---

## Testing

### Unit Tests

```bash
./gradlew test
```

### Instrumented Tests

```bash
./gradlew connectedAndroidTest
```

### Lint Check

```bash
./gradlew lint
```

---

## Dependencies

### Core

| Library | Version | Purpose |
|---------|---------|---------|
| Kotlin | 2.1.0 | Language |
| Compose BOM | 2024.12.01 | UI Framework |
| Hilt | 2.53.1 | Dependency Injection |
| WebRTC | 119.0.0 | Voice/Video calls |
| libsodium | 2.0.2 | Cryptography |
| ZXing | 3.5.3 / 4.3.0 | QR Code |

### Android

| Library | Version | Purpose |
|---------|---------|---------|
| Core KTX | 1.15.0 | Kotlin extensions |
| AppCompat | 1.7.0 | Compatibility |
| Navigation | 2.8.5 | Compose navigation |
| DataStore | 1.1.1 | Preferences |
| Play Services Location | 21.3.0 | GPS |
| Play Services Maps | 19.1.0 | Maps |

---

## Troubleshooting

### Common Issues

| Issue | Solution |
|-------|----------|
| No audio on TX | Check microphone permission |
| No audio on RX | Check multicast group joined |
| Radio not found | Verify IP subnet (10.223.x.x) |
| ATAK not connecting | Check signature permissions |
| Build fails (Java) | Use JDK 17: `export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64` |

### Debug Logging

```bash
# All MeshRider logs
adb logcat -s MeshRider:*

# Specific components
adb logcat -s MeshRider:PTTManager MeshRider:RadioApiClient

# WebRTC logs
adb logcat | grep -E "(webrtc|WebRTC)"
```

---

## Contributing

See [DEVELOPER_GUIDE.md](docs/DEVELOPER_GUIDE.md) for contribution guidelines.

### Code Style

- Follow [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use ktlint for formatting
- Document all public APIs
- Write unit tests for new features

---

## License

```
Copyright (C) 2024-2026 DoodleLabs Singapore Pte Ltd. All Rights Reserved.

PROPRIETARY AND CONFIDENTIAL

This software is the proprietary information of DoodleLabs.
Unauthorized copying, modification, distribution, or use is strictly
prohibited without express written permission from DoodleLabs.
```

---

## Contact

**Developer:** Jabbir Basha P
**Company:** DoodleLabs Singapore Pte Ltd
**Website:** [doodlelabs.com](https://doodlelabs.com)

---

<div align="center">

**MeshRider Wave** — Tactical Voice for the Modern Battlefield

*Built with precision. Deployed with confidence.*

</div>
