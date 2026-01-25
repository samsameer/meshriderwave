# CLAUDE.md - Mesh Rider Wave Android

**Version:** 2.5.0 | **Platform:** Native Android (Kotlin) | **Status:** BETA (85%)

> **Key:** App uses phone's audio device. Radio provides IP transport only.

## Quick Reference

| Attribute | Value |
|-----------|-------|
| Package | `com.doodlelabs.meshriderwave` |
| Min/Target SDK | 26 / 35 |
| Language | Kotlin 2.1.0 |
| UI | Jetpack Compose + Material 3 |
| Architecture | Clean Architecture + MVVM |

## Build Commands

```bash
./gradlew assembleDebug      # Build debug APK
./gradlew installDebug       # Install on device
./gradlew test               # Run unit tests
./gradlew clean              # Clean build
```

## Latest Fixes (January 25, 2026)

### CTO-Level Quality Audit

1. **Radar Real GPS** - `TacticalDashboardScreen.kt`: Uses REAL team member GPS positions (no fake/simulated)
2. **Contact Online Status** - `HomeScreen.kt` + `MainViewModel.kt`: Real status from mDNS + Beacon discovery
3. **SFU Election Metrics** - `GroupCallManager.kt`: Real CPU cores, bandwidth, latency (no placeholders)
4. **Battery Optimization** - Adaptive polling: 10s active → 60s background → 5min doze
5. **Starlink Theme** - Monochrome cyan accent, red for SOS only

### Previous Fixes (January 24, 2026)

1. **Samsung Tablet PTT Crash** - `PTTManager.kt:679-737`: Audio HAL exception handling
2. **Video One-Way Bug** - `CallActivity.kt:78-206`: Pending video track attachment
3. **Memory Leak** - `DashboardViewModel.kt`: Proper job cancellation in `onCleared()`
4. **Calls Not Connecting** - `Connector.kt:83-136`: Address error feedback

### Production Gaps

| Gap | Current | Required | Priority |
|-----|---------|----------|----------|
| Audio Codec | Raw PCM 256kbps | Opus 6-24kbps | P0 |
| Transport | Unicast TCP | Multicast RTP | P0 |
| QoS | None | DSCP EF (46) | P1 |
| Tests | 0% | 80%+ | P1 |

## Architecture

```
app/src/main/kotlin/com/doodlelabs/meshriderwave/
├── core/
│   ├── crypto/          # CryptoManager (libsodium), MLSManager
│   ├── discovery/       # BeaconManager, ContactAddressSync, IdentityBeacon
│   ├── network/         # Connector, MeshNetworkManager, MeshService
│   ├── ptt/             # PTTManager, FloorControlManager (3GPP MCPTT)
│   ├── location/        # LocationSharingManager (Blue Force Tracking)
│   ├── group/           # GroupCallManager (P2P mesh + SFU)
│   └── webrtc/          # RTCCall
├── data/repository/     # ContactRepositoryImpl, SettingsRepositoryImpl
├── domain/model/        # Contact, CallState, AddressRecord, NetworkType
└── presentation/
    ├── ui/screens/      # TacticalDashboard, Contacts, Groups, Channels, Map
    ├── ui/components/   # PremiumComponents, PremiumDialogs
    └── viewmodel/       # DashboardViewModel (real GPS, real online status)
```

## Key Components (All Real Data)

### TacticalDashboardScreen (Situational Awareness)
- **Readiness Levels:** ONLINE/READY/STANDBY/OFFLINE (real service status)
- **Radar:** Real GPS positions (500m range, bearing calculation)
- **Metrics:** Real peer count from mDNS + Beacon discovery
- **Telemetry:** Real RSSI/SNR/Link quality from radio API

### Online Status Detection
```kotlin
// MainViewModel combines both discovery sources
combine(peerDiscoveryManager.discoveredPeers, beaconManager.discoveredPeersFlow)
    → onlinePeerKeys: Set<String>  // Real online peer public keys
```

### PTTManager
- 3GPP MCPTT floor control with priority preemption
- EMERGENCY > HIGH > NORMAL > LOW
- 200ms floor request timeout

### Identity Discovery
- Beacon: 239.255.77.1:7777 (Ed25519 signed)
- mDNS: _meshrider._tcp (link-local)
- Network types: MESHRIDER (10.223.x.x), WIFI, WIFI_DIRECT, LINK_LOCAL

## Network Config

| Port | Protocol | Purpose |
|------|----------|---------|
| 10001 | TCP | Signaling |
| 7777 | UDP | Identity beacons |

## Theme (Starlink-Inspired Monochrome)

```kotlin
// TacticalColors - Professional single-accent
Background = 0xFF000000       // Pure black
Accent = 0xFF00B4D8           // Starlink cyan (primary)
AccentBright = 0xFF48CAE4     // Highlight
AccentDim = 0xFF0077B6        // Secondary
Critical = 0xFFDC2626         // Red (SOS only)
TextPrimary = 0xFFFFFFFF      // White
TextSecondary = 0xFFA3A3A3    // Gray
```

## Dependencies (All Latest Jan 2026)

| Library | Version |
|---------|---------|
| AGP | 8.13.2 |
| Kotlin | 2.1.0 |
| Compose BOM | 2024.12.01 |
| Hilt | 2.53.1 |
| WebRTC | 119.0.0 |
| libsodium | 2.0.2 |

## Battery Optimization

| Scenario | Polling Interval |
|----------|------------------|
| Active (foreground) | 10s |
| Background | 60s |
| Beacon (active peers) | 30s |
| Beacon (idle) | 2min |
| Peer cleanup | 5min |
| Animations | Lifecycle-aware (pause when backgrounded) |

## What's Real vs TODO

### 100% Real
- Peer discovery (mDNS + Beacon)
- Contact online status
- Radar GPS positions
- Radio telemetry (RSSI/SNR)
- WebRTC calls
- E2E encryption
- Floor control state machine

### TODO
- PTT multicast audio delivery (unicast works)
- Opus codec integration
- DSCP QoS marking
- Unit tests (0%)

## Debug

```bash
adb logcat -s MeshRider:*              # All logs
adb shell netstat -tlnp | grep 10001   # Check port
```

---

**Developer:** Jabbir Basha P | DoodleLabs Singapore
**Build Status:** PASSING | **Last Audit:** Jan 25, 2026
**License:** Proprietary - Unauthorized use prohibited
