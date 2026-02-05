# MeshRider Wave ATAK Plugin

**PTT and Blue Force Tracking integration for ATAK (Android Tactical Assault Kit)**

[![Build Status](https://img.shields.io/badge/build-passing-brightgreen)]()
[![ATAK](https://img.shields.io/badge/ATAK-CivTAK%204.x-blue)]()
[![Kotlin](https://img.shields.io/badge/kotlin-2.1.0-purple)]()

---

## Overview

The MeshRider Wave ATAK plugin brings Push-to-Talk voice and Blue Force Tracking into ATAK. It bridges the MR Wave app with ATAK's Cursor-on-Target (CoT) system, placing team member positions on the ATAK map and enabling PTT transmission directly from the ATAK toolbar.

```
┌─────────────────────────────────────────────────────────────┐
│                        ATAK HOST                            │
│  ┌─────────────────┐  ┌──────────────────────────────────┐  │
│  │ MR Wave Plugin  │  │         ATAK Core                │  │
│  │                 │  │                                  │  │
│  │ ┌─────────────┐ │  │ ┌────────────┐  ┌─────────────┐ │  │
│  │ │ PTT Button  │─┼──┼→│ Toolbar    │  │ Map View    │ │  │
│  │ └─────────────┘ │  │ └────────────┘  └─────────────┘ │  │
│  │                 │  │                       ↑          │  │
│  │ ┌─────────────┐ │  │                       │          │  │
│  │ │ CoT Bridge  │─┼──┼───────────────────────┘          │  │
│  │ └─────────────┘ │  │ (CotMapComponent dispatcher)     │  │
│  └────────┬────────┘  └──────────────────────────────────┘  │
│           │                                                  │
│           │ Intent Bridge (Signature Protected)              │
│           ↓                                                  │
│  ┌─────────────────┐                                        │
│  │  MR Wave App    │ (Separate APK)                         │
│  │  ATAKBridge.kt  │                                        │
│  └─────────────────┘                                        │
└─────────────────────────────────────────────────────────────┘
```

---

## Architecture

Follows the official [CivTAK plugin pattern](https://toyon.github.io/LearnATAK) with 3 core classes:

### 1. PluginLifecycle (`MRWavePlugin.kt`)

Entry point. Analogous to Android `Application` class.

- Creates and initializes `MRWaveMapComponent`
- Manages PTT toolbar component
- Bridges intent communication with MR Wave app
- Handles plugin lifecycle (start/pause/resume/destroy)

### 2. MapComponent (`MRWaveMapComponent.kt`)

Central component. Analogous to Android `Activity` class.

- Registers all DropDownReceivers via `registerDropDownReceiver()` + `DocumentedIntentFilter`
- Manages CoT dispatching to ATAK map via `CotMapComponent.getInternalDispatcher()`
- Handles Blue Force Tracking marker lifecycle
- Periodic stale position cleanup

### 3. DropDownReceivers (4 receivers)

UI panels and event handlers. Analogous to Android `Fragment` class.

| Receiver | Purpose |
|----------|---------|
| `PTTToolbarReceiver` | Handles PTT button press/release/toggle from ATAK toolbar |
| `ChannelDropdownReceiver` | Channel selector dropdown panel with `showDropDown()` |
| `CoTReceiver` | Forwards ATAK CoT messages to MR Wave app for mesh broadcast |
| `MRWaveResponseReceiver` | Processes status/PTT/channel responses from MR Wave app |

All receivers extend ATAK's `DropDownReceiver` (not Android `BroadcastReceiver`) and implement `onReceive()` + `disposeImpl()`.

---

## Module Structure

```
atak-plugin/
├── src/main/java/com/doodlelabs/meshriderwave/atak/
│   ├── MRWavePlugin.kt              # PluginLifecycle
│   ├── MRWaveMapComponent.kt        # MapComponent (CoT dispatching)
│   ├── MRWavePluginLifecycleProvider.kt  # ContentProvider entry point
│   ├── MRWavePluginService.kt       # Background service
│   ├── receivers/
│   │   ├── PTTToolbarReceiver.kt    # DropDownReceiver - PTT toolbar
│   │   ├── ChannelDropdownReceiver.kt  # DropDownReceiver - channel selector
│   │   ├── CoTReceiver.kt          # DropDownReceiver - CoT forwarding
│   │   └── MRWaveResponseReceiver.kt  # DropDownReceiver - MR Wave responses
│   ├── map/
│   │   ├── TeamMarkerManager.kt     # BFT marker management
│   │   └── TeamPosition.kt         # Team member position model
│   ├── toolbar/
│   │   └── PTTToolbarComponent.kt   # ATAK toolbar PTT button
│   └── ui/
│       ├── TacticalOverlayWidget.kt # Status overlay widget
│       └── MilitaryPTTButton.kt     # 80dp tactile PTT button
├── atak-stubs/                      # Compile-time ATAK SDK stubs
│   └── src/main/java/
│       ├── com/atakmap/android/
│       │   ├── maps/
│       │   │   ├── MapView.kt       # MapView, Marker, MapGroup, GeoPoint
│       │   │   ├── AbstractMapComponent.kt  # Base MapComponent
│       │   │   ├── PluginLayoutInflater.kt  # Plugin layout inflation
│       │   │   └── DocumentedIntentFilter   # (in AbstractMapComponent.kt)
│       │   ├── dropdown/
│       │   │   └── DropDownReceiver.kt  # Base receiver + DropDownManager
│       │   └── cot/
│       │       ├── CotEvent.kt      # CotEvent, CotPoint, CotDetail
│       │       └── CotMapComponent.kt  # Internal/External dispatchers
│       ├── com/atakmap/coremap/
│       │   └── maps/time/
│       │       └── CoordinatedTime.kt
│       └── transapps/
│           ├── maps/plugin/lifecycle/
│           │   └── Lifecycle.kt     # Plugin lifecycle interface
│           └── mapi/
│               └── MapView.kt       # TransApps MapView interface
└── build.gradle.kts
```

---

## CoT Integration

### Placing Markers on ATAK Map

```kotlin
// Create CotEvent for a team member
val event = CotEvent().apply {
    uid = "MRWave-${position.uid}"
    type = "a-f-G-U-C"          // Friendly ground unit
    how = "m-g"                  // Machine GPS
    setPoint(CotPoint(lat, lon, alt, ce = 10.0, le = 10.0))
    // ... detail with contact, group, track, precisionlocation
}

// Dispatch to local ATAK map
CotMapComponent.getInternalDispatcher().dispatch(event)

// Share with other ATAK clients on network
CotMapComponent.getExternalDispatcher().dispatch(event)
```

### CoT Type Codes (MIL-STD-2525D)

| Type | Meaning |
|------|---------|
| `a-f-G-U-C` | Friendly ground unit (standard) |
| `a-f-G-U-C-I` | Friendly ground unit (team lead) |
| `a-f-G-U-C-E-M` | Friendly ground unit (emergency/SOS) |
| `t-x-d-d` | Deletion event (remove marker) |

### Stale Position Cleanup

Positions are marked stale after 5 minutes. `TeamMarkerManager` periodically checks and dispatches `t-x-d-d` removal events for offline team members.

---

## Context Management

ATAK plugins run in ATAK's process but have their own resources. Two contexts must be managed:

| Context | Source | Use For |
|---------|--------|---------|
| `atakContext` | `MapView.getContext()` | UI components (AlertDialog, Toast, themes) |
| `pluginContext` | Plugin's own context | Plugin resources (R.layout, R.drawable, R.string) |

Use `PluginLayoutInflater.inflate(pluginContext, layoutResId, root)` to inflate plugin layouts.

---

## Intent Bridge

Communication between the plugin and MR Wave app uses signature-protected broadcasts:

### Plugin to MR Wave App

| Action | Purpose |
|--------|---------|
| `com.doodlelabs.meshriderwave.action.PTT_START` | Start PTT transmission |
| `com.doodlelabs.meshriderwave.action.PTT_STOP` | Stop PTT transmission |
| `com.doodlelabs.meshriderwave.action.GET_CHANNELS` | Request channel list |
| `com.doodlelabs.meshriderwave.action.SET_CHANNEL` | Switch active channel |
| `com.doodlelabs.meshriderwave.action.GET_STATUS` | Request current status |
| `com.doodlelabs.meshriderwave.action.FORWARD_COT` | Forward CoT from ATAK |

### MR Wave App to Plugin

| Action | Purpose |
|--------|---------|
| `*.RESPONSE` | Status/channel list response |
| `*.PTT_STATE_CHANGED` | PTT active/inactive |
| `*.CHANNEL_CHANGED` | Active channel changed |

---

## Build

```bash
# Build plugin APK
./gradlew :atak-plugin:assembleDebug

# Output: atak-plugin/build/outputs/apk/debug/atak-plugin-debug.apk
```

### Installation

1. Build the plugin APK
2. Install on ATAK-equipped device: `adb install atak-plugin-debug.apk`
3. In ATAK: Settings > Tool Manager > enable "MeshRider Wave"
4. MR Wave app must also be installed on the same device

---

## References

- [LearnATAK - Plugin Architecture](https://toyon.github.io/LearnATAK)
- [RIIS - ATAK Plugins Part 1](https://www.riis.com/blog/atak-plugins-part-1)
- [Ballantyne - ATAK Plugin SDK](https://www.ballantyne.online/atak-plugin-sdk-something-functional/)
- [CivTAK - civtak.org](https://www.civtak.org/)
- [TAK Product Center](https://tak.gov/products)
- [ATAK CIV Source](https://github.com/deptofdefense/AndroidTacticalAssaultKit-CIV)

---

**Copyright (C) 2024-2026 DoodleLabs Singapore Pte Ltd. All Rights Reserved.**

*Last Updated: February 1, 2026*
