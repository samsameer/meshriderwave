# MeshRider Wave Android

<div align="center">

**Military-Grade Push-to-Talk for DoodleLabs MeshRider Mesh Radios**

[![Build Status](https://img.shields.io/badge/build-passing-brightgreen)]()
[![Version](https://img.shields.io/badge/version-2.5.0-blue)]()
[![Platform](https://img.shields.io/badge/platform-Android%208.0+-blue)]()
[![Kotlin](https://img.shields.io/badge/kotlin-2.1.0-purple)]()
[![Compose](https://img.shields.io/badge/compose-2024.12.01-green)]()
[![License](https://img.shields.io/badge/license-proprietary-red)]()

[Quick Start](#quick-start) | [Features](#features) | [Architecture](#architecture) | [ATAK Plugin](#atak-plugin) | [Documentation](#documentation)

</div>

---

## What's New (February 2026)

### v2.5.1 - Core-Telecom & ATAK Architecture Update

- **Android Core-Telecom Integration** - Proper VoIP call registration via `CallsManager.addCall()`, audio endpoint routing, CallStyle notifications
- **ATAK Plugin Overhaul** - Refactored to official 3-class pattern (PluginLifecycle / MapComponent / DropDownReceiver) per CivTAK/TAK SDK docs
- **CoT Dispatching** - Blue Force Tracking markers placed on ATAK map via `CotMapComponent.getInternalDispatcher()`
- **Mid-Call Video Renegotiation** - Enable/disable camera during active calls via WebRTC data channel SDP exchange
- **Settings Redesign** - Calls, Audio/Video, Notifications sections with telecom integration info

### v2.5.0 - Tactical Dashboard & MCPTT (January 2026)

- **Military Tactical Dashboard** - DEFCON-style readiness levels, radar animation, Starlink-inspired UI
- **3GPP MCPTT Floor Control** - Full compliance with TS 24.379/380/381
- **German Localization** - Bundeswehr military terminology (291 strings)
- **Responsive Design** - Phones, tablets, landscape/portrait support
- **Critical Bug Fixes** - Samsung tablet crash, video one-way, memory leaks

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
| **Telecom** | Core-Telecom Jetpack (`CallsManager`) |
| **Localization** | English, German (Deutsch) |
| **Status** | Beta (Field Testing Phase) |

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

# Set Java 17 (if needed)
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64

# Build debug APK (main app)
./gradlew assembleDebug

# Build ATAK plugin APK
./gradlew :atak-plugin:assembleDebug

# Install main app on connected device
./gradlew installDebug

# Build release APK
./gradlew assembleRelease
```

### First Run

1. Grant required permissions (Microphone, Camera, Location, Nearby Devices, Notifications)
2. Configure network settings for your MeshRider subnet (default: 10.223.x.x)
3. Scan QR code or enter contact details to add peers
4. Join or create a talkgroup
5. Press and hold PTT button to transmit

---

## Features

### Core Capabilities

| Feature | Description | Status |
|---------|-------------|--------|
| **PTT Voice** | Half-duplex voice with 3GPP MCPTT floor control | Complete |
| **Voice/Video Calls** | WebRTC with mid-call video renegotiation | Complete |
| **Core-Telecom** | Android OS call registration, CallStyle notifications | Complete |
| **E2E Encryption** | libsodium + MLS group encryption | Complete |
| **Tactical Dashboard** | DEFCON readiness, radar, military UI | Complete |
| **Blue Force Tracking** | Real-time GPS location sharing | Complete |
| **SOS Emergency** | Priority broadcast with geofencing | Complete |
| **ATAK Integration** | CoT protocol + tactical plugin (3-class arch) | Complete |
| **Responsive UI** | Phones, tablets, all orientations | Complete |
| **Localization** | English, German (Bundeswehr) | Complete |

### Android Telecom Integration

The app follows [developer.android.com telecom guidelines](https://developer.android.com/develop/connectivity/telecom):

- **Core-Telecom Jetpack** (`androidx.core:core-telecom:1.0.0`) - Registers VoIP calls with Android OS via `CallsManager.addCall()`
- **CallStyle Notifications** - `forIncomingCall()` with full-screen intent, `forOngoingCall()` with hangup action
- **Two Notification Channels** - Incoming (IMPORTANCE_HIGH + ringtone) and Ongoing (IMPORTANCE_DEFAULT, silent)
- **Audio Endpoint Routing** - Speaker/earpiece/bluetooth via `CallControlScope.requestEndpointChange()`
- **Foreground Service** - `phoneCall` type with `MANAGE_OWN_CALLS` permission

### 3GPP MCPTT Compliance

| Standard | Description | Status |
|----------|-------------|--------|
| TS 24.379 | MCPTT Call Control | Implemented |
| TS 24.380 | Floor Control Protocol | Implemented |
| TS 24.381 | Group Management | Implemented |
| TS 24.382 | Identity Management | Partial |

**Floor Control State Machine:**
```
IDLE -> PENDING -> GRANTED -> RELEASING -> IDLE
          |
        DENIED -> QUEUED
```

**Priority Levels:**
| Priority | Value | Use Case |
|----------|-------|----------|
| EMERGENCY | 4 | Life-threatening situations |
| HIGH | 3 | Mission-critical ops |
| NORMAL | 2 | Standard communication |
| LOW | 1 | Background traffic |

### Security

| Layer | Implementation |
|-------|----------------|
| Key Exchange | Ed25519 signing + X25519 ECDH |
| Symmetric | XSalsa20-Poly1305 (AEAD) |
| Key Derivation | Argon2id (password-based) |
| Group Keys | MLS (RFC 9420) |
| Standards | FIPS 140-2, OWASP MASVS L2 |

---

## Architecture

### Clean Architecture + MVVM

```
app/src/main/kotlin/com/doodlelabs/meshriderwave/
├── core/
│   ├── audio/                    # Audio pipeline
│   ├── crypto/                   # E2E encryption (libsodium, MLS)
│   ├── discovery/                # Identity-first peer discovery
│   ├── location/                 # Blue Force Tracking
│   ├── messaging/                # Offline store-and-forward
│   ├── network/                  # P2P signaling, connectors
│   ├── ptt/                      # PTT manager, floor control
│   │   ├── PTTManager.kt
│   │   ├── FloorControlManager.kt    # 3GPP MCPTT
│   │   ├── FloorControlProtocol.kt
│   │   └── FloorArbitrator.kt
│   ├── telecom/                  # Android Telecom integration
│   │   ├── TelecomCallManager.kt     # CallsManager wrapper
│   │   └── CallNotificationManager.kt  # CallStyle notifications
│   ├── sos/                      # Emergency system
│   ├── webrtc/                   # RTCCall (mid-call renegotiation)
│   └── transport/                # Multicast RTP transport
├── data/                         # Repository implementations
├── domain/                       # Models, interfaces
└── presentation/
    ├── ui/
    │   ├── screens/
    │   │   ├── dashboard/        # TacticalDashboardScreen
    │   │   ├── call/             # CallActivity (telecom-integrated)
    │   │   ├── channels/
    │   │   ├── contacts/
    │   │   ├── groups/
    │   │   ├── map/
    │   │   └── settings/         # Calls, Audio/Video, Notifications
    │   ├── components/           # Premium glassmorphism
    │   └── theme/                # Starlink-inspired theme
    └── viewmodel/
```

---

## ATAK Plugin

The ATAK plugin follows the official [CivTAK plugin architecture](https://toyon.github.io/LearnATAK) with the required 3-class pattern:

```
PluginLifecycle (MRWavePlugin)
    └── MapComponent (MRWaveMapComponent)
            ├── DropDownReceiver (PTTToolbarReceiver)
            ├── DropDownReceiver (ChannelDropdownReceiver)
            ├── DropDownReceiver (CoTReceiver)
            └── DropDownReceiver (MRWaveResponseReceiver)
```

See [atak-plugin/README.md](atak-plugin/README.md) for full plugin documentation.

### Key ATAK Integration Points

- **CoT Dispatching** - Team positions placed on ATAK map via `CotMapComponent.getInternalDispatcher().dispatch()`
- **Network Sharing** - CoT shared with other ATAK clients via `getExternalDispatcher()`
- **Blue Force Tracking** - MR Wave peer positions as friendly ground unit markers (`a-f-G-U-C`)
- **PTT from ATAK** - Toolbar button triggers PTT via intent bridge to MR Wave app
- **Channel Selection** - ATAK dropdown panel for switching PTT channels

---

## Network Configuration

### Ports

| Port | Protocol | Purpose |
|------|----------|---------|
| 5004 | UDP | RTP Multicast (voice) |
| 5005 | UDP | RTCP Control |
| 6969 | UDP | CoT Multicast (ATAK) |
| 7777 | UDP | Identity Beacon |
| 10001 | TCP | P2P Signaling |
| 80 | HTTP | Radio JSON-RPC API |

### Multicast Groups

```
Talkgroup 1:   239.255.0.1:5004
Talkgroup 2:   239.255.0.2:5004
...
Talkgroup N:   239.255.0.N:5004  (N = 1-255)
Identity:      239.255.77.1:7777
```

### QoS

- DSCP: EF (46) - Expedited Forwarding
- TOS Byte: 0xB8 (184)
- Per RFC 2474/2475

---

## Localization

### Supported Languages

| Language | Code | Strings | Status |
|----------|------|---------|--------|
| English | `en` | 291 | Complete |
| German | `de` | 291 | Complete |

### Adding New Language

1. Create `app/src/main/res/values-XX/strings.xml`
2. Copy from `values/strings.xml`
3. Translate all strings
4. Test with device language change

---

## Dependencies

| Library | Version | Purpose |
|---------|---------|---------|
| Kotlin | 2.1.0 | Language |
| Compose BOM | 2024.12.01 | UI Framework |
| Hilt | 2.53.1 | Dependency Injection |
| Navigation | 2.8.5 | Compose Navigation |
| WebRTC | 119.0.0 | Voice/Video |
| libsodium | 2.0.2 | Cryptography |
| Core-Telecom | 1.0.0 | Android Telecom |
| DataStore | 1.1.1 | Preferences |

---

## Testing

```bash
# Unit tests
./gradlew test

# Instrumented tests
./gradlew connectedAndroidTest

# Lint check
./gradlew lint

# Build check (app + plugin)
./gradlew assembleDebug
```

---

## Troubleshooting

| Issue | Solution |
|-------|----------|
| Build fails (Java) | `export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64` |
| No audio on TX | Check microphone permission |
| No audio on RX | Check multicast group joined |
| Samsung tablet crash | Update to v2.5.0 (fixed) |
| Video one-way | Update to v2.5.1 (fixed with renegotiation) |
| Call notification missing | Check notification permissions (API 33+) |

### Debug Logging

```bash
adb logcat -s MeshRider:*    # Main app logs
adb logcat -s MRWave:*       # ATAK plugin logs
```

---

## Documentation

| Document | Description |
|----------|-------------|
| [README.md](README.md) | Quick start and overview |
| [CLAUDE.md](CLAUDE.md) | Technical reference for AI |
| [atak-plugin/README.md](atak-plugin/README.md) | ATAK plugin documentation |
| [HANDOFF.md](HANDOFF.md) | Project handoff docs |
| [docs/PTT_GUIDE.md](docs/PTT_GUIDE.md) | PTT system guide |

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

**MeshRider Wave** -- Tactical Voice for the Modern Battlefield

*Built with precision. Deployed with confidence.*

*Last Updated: February 1, 2026*

</div>
