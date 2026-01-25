# MeshRider Wave Android

<div align="center">

**Military-Grade Push-to-Talk for DoodleLabs MeshRider Mesh Radios**

[![Build Status](https://img.shields.io/badge/build-passing-brightgreen)]()
[![Version](https://img.shields.io/badge/version-2.5.0-blue)]()
[![Platform](https://img.shields.io/badge/platform-Android%208.0+-blue)]()
[![Kotlin](https://img.shields.io/badge/kotlin-2.1.0-purple)]()
[![Compose](https://img.shields.io/badge/compose-2024.12.01-green)]()
[![License](https://img.shields.io/badge/license-proprietary-red)]()

[Quick Start](#quick-start) • [Features](#features) • [Architecture](#architecture) • [Screenshots](#screenshots) • [Documentation](#documentation)

</div>

---

## What's New (January 2026)

### v2.5.0 - Tactical Dashboard & MCPTT Update

- **🎖️ Military Tactical Dashboard** - DEFCON-style readiness levels, radar animation, Starlink-inspired UI
- **📡 3GPP MCPTT Floor Control** - Full compliance with TS 24.379/380/381
- **🌍 German Localization** - Bundeswehr military terminology (291 strings)
- **📱 Responsive Design** - Phones, tablets, landscape/portrait support
- **🔧 ATAK Plugin Enhancements** - TacticalOverlayWidget, MilitaryPTTButton, TeamMarkerManager
- **🐛 Critical Bug Fixes** - Samsung tablet crash, video one-way, memory leaks

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

# Build debug APK
./gradlew assembleDebug

# Install on connected device
./gradlew installDebug

# Build release APK
./gradlew assembleRelease
```

### First Run

1. Grant required permissions (Microphone, Camera, Location, Nearby Devices)
2. Configure network settings for your MeshRider subnet (default: 10.223.x.x)
3. Scan QR code or enter contact details to add peers
4. Join or create a talkgroup
5. Press and hold PTT button to transmit

---

## Features

### Core Capabilities

| Feature | Description | Status |
|---------|-------------|--------|
| **PTT Voice** | Half-duplex voice with 3GPP MCPTT floor control | ✅ Complete |
| **Opus Codec** | 6-24 kbps voice compression (10-40x) | ✅ Complete |
| **Multicast RTP** | Efficient one-to-many transmission | ✅ Complete |
| **E2E Encryption** | libsodium + MLS group encryption | ✅ Complete |
| **Tactical Dashboard** | DEFCON readiness, radar, military UI | ✅ Complete |
| **Blue Force Tracking** | Real-time GPS location sharing | ✅ Complete |
| **SOS Emergency** | Priority broadcast with geofencing | ✅ Complete |
| **Offline Messaging** | Store-and-forward when offline | ✅ Complete |
| **ATAK Integration** | CoT protocol + tactical plugin | ✅ Complete |
| **Responsive UI** | Phones, tablets, all orientations | ✅ Complete |
| **Localization** | English, German (Bundeswehr) | ✅ Complete |

### 3GPP MCPTT Compliance (January 2026)

| Standard | Description | Status |
|----------|-------------|--------|
| TS 24.379 | MCPTT Call Control | ✅ Implemented |
| TS 24.380 | Floor Control Protocol | ✅ Implemented |
| TS 24.381 | Group Management | ✅ Implemented |
| TS 24.382 | Identity Management | 🔄 Partial |

**Floor Control State Machine:**
```
IDLE → PENDING → GRANTED → RELEASING → IDLE
         ↓
       DENIED → QUEUED
```

**Priority Levels:**
| Priority | Value | Use Case |
|----------|-------|----------|
| EMERGENCY | 4 | Life-threatening situations |
| HIGH | 3 | Mission-critical ops |
| NORMAL | 2 | Standard communication |
| LOW | 1 | Background traffic |

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

### Security

| Layer | Implementation |
|-------|----------------|
| Key Exchange | Ed25519 signing + X25519 ECDH |
| Symmetric | XSalsa20-Poly1305 (AEAD) |
| Key Derivation | Argon2id (password-based) |
| Group Keys | MLS (RFC 9420) |
| Standards | FIPS 140-2, OWASP MASVS L2 |

---

## Screenshots

### Tactical Dashboard
- DEFCON-style readiness indicators (ALPHA/BRAVO/CHARLIE/DELTA)
- Real-time radar with sweep animation
- Mission status panel with metrics
- Radio telemetry (RSSI, SNR, Link Quality)
- Starlink-inspired dark theme

### PTT Channels
- Channel list with voice activity indicators
- Hold-to-talk with visual feedback
- Priority preemption support
- Emergency broadcast mode

### Blue Force Tracking
- Real-time GPS position sharing
- Team member locations on map
- CoT integration for ATAK interop
- Geofencing alerts

---

## Architecture

### Clean Architecture + MVVM

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
│  │  │              │  │ • Floor Ctrl │  │                          │   │    │
│  │  └──────────────┘  └──────┬───────┘  └──────────────────────────┘   │    │
│  └───────────────────────────┼──────────────────────────────────────────┘    │
│                              │                                               │
│                              │ UDP Multicast (239.255.0.x:5004)             │
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
│   ├── sos/                      # Emergency system
│   └── transport/                # Multicast RTP transport
│       ├── PTTTransport.kt           # SOLID interfaces
│       └── MulticastPTTTransport.kt
├── data/                         # Repository implementations
├── domain/                       # Models, interfaces
└── presentation/
    ├── ui/
    │   ├── screens/
    │   │   ├── dashboard/
    │   │   │   └── TacticalDashboardScreen.kt  # Military UI
    │   │   ├── channels/
    │   │   ├── contacts/
    │   │   ├── groups/
    │   │   ├── map/
    │   │   └── settings/
    │   ├── components/           # Premium glassmorphism
    │   └── theme/                # Starlink-inspired theme
    └── viewmodel/
```

### ATAK Plugin Module

```
atak-plugin/src/main/java/com/doodlelabs/meshriderwave/atak/
├── MRWavePlugin.kt
├── map/
│   └── TeamMarkerManager.kt      # CoT Blue Force Tracking
├── ui/
│   ├── TacticalOverlayWidget.kt  # Status overlay
│   └── MilitaryPTTButton.kt      # 80dp tactile button
└── receivers/
    ├── CoTReceiver.kt
    └── PTTToolbarReceiver.kt
```

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
| English | `en` | 291 | ✅ Complete |
| German | `de` | 291 | ✅ Complete |

### Adding New Language

1. Create `app/src/main/res/values-XX/strings.xml`
2. Copy from `values/strings.xml`
3. Translate all strings
4. Test with device language change

### German Military Terminology

| English | German | Standard |
|---------|--------|----------|
| Mission Status | EINSATZSTATUS | Bundeswehr |
| COMMS | KOMM | NATO |
| Operational | EINSATZBEREIT | Bundeswehr |
| Emergency | NOTFALL | DIN |

---

## Testing

```bash
# Unit tests
./gradlew test

# Instrumented tests
./gradlew connectedAndroidTest

# Lint check
./gradlew lint

# Build check
./gradlew assembleDebug
```

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
| DataStore | 1.1.1 | Preferences |
| WindowSizeClass | Material3 | Responsive |

---

## Troubleshooting

| Issue | Solution |
|-------|----------|
| Build fails (Java) | `export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64` |
| No audio on TX | Check microphone permission |
| No audio on RX | Check multicast group joined |
| Samsung tablet crash | Update to v2.5.0 (fixed) |
| Video one-way | Update to v2.5.0 (fixed) |

### Debug Logging

```bash
adb logcat -s MeshRider:*
```

---

## Documentation

| Document | Description |
|----------|-------------|
| [README.md](README.md) | Quick start and overview |
| [CLAUDE.md](CLAUDE.md) | Technical reference for AI |
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

**MeshRider Wave** — Tactical Voice for the Modern Battlefield

*Built with precision. Deployed with confidence.*

*Last Updated: January 25, 2026*

</div>
