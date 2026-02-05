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
| Telecom | Core-Telecom Jetpack (CallsManager) |

## Build Commands

```bash
./gradlew assembleDebug                          # Build debug APK
./gradlew :atak-plugin:assembleDebug             # Build ATAK plugin APK
./gradlew installDebug                           # Install on device
./gradlew test                                   # Run unit tests
./gradlew clean                                  # Clean build
```

## Latest Fixes (February 1, 2026)

### Core-Telecom Integration (developer.android.com compliant)

1. **TelecomCallManager** - `core/telecom/TelecomCallManager.kt`: Wraps `androidx.core.telecom.CallsManager` for proper VoIP call registration with Android OS
2. **CallStyle Notifications** - `core/telecom/CallNotificationManager.kt`: Uses `NotificationCompat.CallStyle.forIncomingCall()` / `forOngoingCall()` per Android docs
3. **Two Notification Channels** - `MeshRiderApp.kt`: `CHANNEL_CALLS_INCOMING` (IMPORTANCE_HIGH + ringtone) + `CHANNEL_CALLS_ONGOING` (IMPORTANCE_DEFAULT)
4. **Telecom Audio Routing** - `CallActivity.kt`: Speaker/earpiece/bluetooth via `CallControlScope.requestEndpointChange()`
5. **Foreground Service** - `AndroidManifest.xml`: `phoneCall` foreground service type + `MANAGE_OWN_CALLS` permission
6. **Settings Screen** - `SettingsScreen.kt`: Added Calls section (Telecom Integration, Audio Routing, Bluetooth), updated Audio/Video and Notifications sections

### ATAK Plugin — Official Architecture Fix

1. **3-Class Pattern** - Now follows `PluginLifecycle → MapComponent → DropDownReceiver(s)` per CivTAK/TAK docs
2. **MRWaveMapComponent** - `MRWaveMapComponent.kt`: Missing 2nd core class, now registers all receivers via `registerDropDownReceiver()` + `DocumentedIntentFilter`
3. **CoT Dispatching** - `CotMapComponent.getInternalDispatcher().dispatch(event)` for local ATAK map markers
4. **All Receivers** - `ChannelDropdownReceiver`, `MRWaveResponseReceiver`, `PTTToolbarReceiver`, `CoTReceiver` now extend ATAK `DropDownReceiver` (not `BroadcastReceiver`)
5. **SDK Stubs** - Added `AbstractMapComponent`, `CotEvent`, `CotMapComponent`, `PluginLayoutInflater` stubs

### Previous Fixes (January 25, 2026)

1. **Radar Real GPS** - `TacticalDashboardScreen.kt`: Uses REAL team member GPS positions (no fake/simulated)
2. **Contact Online Status** - `HomeScreen.kt` + `MainViewModel.kt`: Real status from mDNS + Beacon discovery
3. **SFU Election Metrics** - `GroupCallManager.kt`: Real CPU cores, bandwidth, latency (no placeholders)
4. **Battery Optimization** - Adaptive polling: 10s active → 60s background → 5min doze
5. **Starlink Theme** - Monochrome cyan accent, red for SOS only

### Previous Fixes (January 24, 2026)

1. **Samsung Tablet PTT Crash** - `PTTManager.kt:679-737`: Audio HAL exception handling
2. **Video One-Way Bug** - `CallActivity.kt:78-206`: Pending video track attachment + mid-call renegotiation
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
│   ├── telecom/         # TelecomCallManager, CallNotificationManager
│   ├── location/        # LocationSharingManager (Blue Force Tracking)
│   ├── group/           # GroupCallManager (P2P mesh + SFU)
│   └── webrtc/          # RTCCall (mid-call video renegotiation)
├── data/repository/     # ContactRepositoryImpl, SettingsRepositoryImpl
├── domain/model/        # Contact, CallState, AddressRecord, NetworkType
└── presentation/
    ├── ui/screens/      # TacticalDashboard, Contacts, Groups, Channels, Map, Settings
    ├── ui/components/   # PremiumComponents, PremiumDialogs
    └── viewmodel/       # DashboardViewModel (real GPS, real online status)
```

### ATAK Plugin Architecture

```
atak-plugin/
├── src/main/java/com/doodlelabs/meshriderwave/atak/
│   ├── MRWavePlugin.kt              # 1st core class: PluginLifecycle
│   ├── MRWaveMapComponent.kt        # 2nd core class: MapComponent (CoT dispatching)
│   ├── receivers/
│   │   ├── PTTToolbarReceiver.kt    # 3rd core class: DropDownReceiver
│   │   ├── ChannelDropdownReceiver.kt
│   │   ├── CoTReceiver.kt
│   │   └── MRWaveResponseReceiver.kt
│   ├── map/
│   │   ├── TeamMarkerManager.kt     # BFT marker management
│   │   └── TeamPosition.kt
│   ├── toolbar/
│   │   └── PTTToolbarComponent.kt
│   └── ui/
│       ├── TacticalOverlayWidget.kt
│       └── MilitaryPTTButton.kt
└── atak-stubs/                      # Compile-time ATAK SDK stubs
    └── src/main/java/
        ├── com/atakmap/android/
        │   ├── maps/               # MapView, AbstractMapComponent, PluginLayoutInflater
        │   ├── dropdown/           # DropDownReceiver, DropDownManager
        │   └── cot/               # CotEvent, CotMapComponent, CotDispatcher
        ├── transapps/              # Lifecycle, PluginContext
        └── com/atakmap/coremap/    # CoordinatedTime
```

## Key Components (All Real Data)

### Core-Telecom Integration (developer.android.com)
```kotlin
// TelecomCallManager wraps CallsManager
callsManager.addCall(callAttributes, onAnswer, onDisconnect, onSetActive, onSetInactive) {
    callControlScope = this
    launch { currentCallEndpoint.collect { ... } }  // Audio routing
    launch { availableEndpoints.collect { ... } }
    launch { isMuted.collect { ... } }
}
```

### CallStyle Notifications
```kotlin
// Two channels: Incoming (IMPORTANCE_HIGH) + Ongoing (IMPORTANCE_DEFAULT)
NotificationCompat.CallStyle.forIncomingCall(person, declinePI, answerPI)
NotificationCompat.CallStyle.forOngoingCall(person, hangupPI)
```

### ATAK CoT Dispatching
```kotlin
// Place markers on ATAK map (internal) or share on network (external)
CotMapComponent.getInternalDispatcher().dispatch(cotEvent)  // Local map
CotMapComponent.getExternalDispatcher().dispatch(cotEvent)  // Network
```

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

### WebRTC Mid-Call Video Renegotiation
- `onRenegotiationNeeded()` → create offer → send via data channel
- `handleRenegotiationOffer()` / `handleRenegotiationAnswer()` for data channel SDP exchange
- Camera enable sends `"CameraEnabled"` message to reset remote video setup

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

## Dependencies (All Latest Feb 2026)

| Library | Version | Purpose |
|---------|---------|---------|
| AGP | 8.13.2 | Build |
| Kotlin | 2.1.0 | Language |
| Compose BOM | 2024.12.01 | UI |
| Hilt | 2.53.1 | DI |
| WebRTC | 119.0.0 | Voice/Video |
| libsodium | 2.0.2 | Crypto |
| Core-Telecom | 1.0.0 | Android Telecom |

## Permissions

| Permission | Purpose |
|------------|---------|
| `RECORD_AUDIO` | PTT voice transmission |
| `CAMERA` | Video calls |
| `ACCESS_FINE_LOCATION` | Blue Force Tracking |
| `MANAGE_OWN_CALLS` | Core-Telecom VoIP registration |
| `FOREGROUND_SERVICE_PHONE_CALL` | Call foreground service |
| `FOREGROUND_SERVICE_MICROPHONE` | PTT foreground service |
| `FOREGROUND_SERVICE_CAMERA` | Video foreground service |
| `POST_NOTIFICATIONS` | Call notifications (API 33+) |

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
- WebRTC calls (voice + video + mid-call upgrade)
- E2E encryption
- Floor control state machine
- Core-Telecom integration (CallsManager, audio routing)
- CallStyle notifications (incoming + ongoing)
- ATAK plugin (3-class architecture, CoT dispatching)

### TODO
- PTT multicast audio delivery (unicast works)
- Opus codec integration
- DSCP QoS marking
- Unit tests (0%)
- UWB ranging for tactical radar (Phase 4)

## Debug

```bash
adb logcat -s MeshRider:*              # All app logs
adb logcat -s MRWave:*                 # ATAK plugin logs
adb shell netstat -tlnp | grep 10001   # Check signaling port
```

## Test Devices

| Device | Serial | IP |
|--------|--------|----|
| Samsung Galaxy S24+ #1 | R5CY91LEA9D | 192.168.1.10 |
| Samsung Galaxy S24+ #2 | R5CY91LELHW | 192.168.1.8 |

---

**Developer:** Jabbir Basha P | DoodleLabs Singapore
**Build Status:** PASSING | **Last Audit:** Feb 1, 2026
**License:** Proprietary - Unauthorized use prohibited
