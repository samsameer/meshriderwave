# Mesh Rider Wave - Project Board

**Project Manager View** | **Last Updated:** January 22, 2026 | **Version:** 2.3.0

---

## Project Overview

| Metric | Value |
|--------|-------|
| **Overall Completion** | 65% |
| **Production Readiness** | BETA |
| **Target Release** | Q2 2026 |
| **Team Size** | 1 Developer |

### Platforms

| Platform | Completion | Status | Codebase |
|----------|------------|--------|----------|
| **Android App** | 82% | BETA | `/Users/jabbir/development/meshriderwave` |
| **LEDE Firmware** | 30% | ALPHA | `/home/jabi/workspace/doodle/lede` |

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         MESH RIDER WAVE ECOSYSTEM                            │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│   ┌─────────────────────────────────┐    ┌─────────────────────────────────┐│
│   │         ANDROID APP             │    │        LEDE FIRMWARE            ││
│   │    (End User Device - EUD)      │    │     (MeshRider Radio)           ││
│   │                                 │    │                                 ││
│   │  • PTTManager                   │    │  • dl-ptt daemon                ││
│   │  • FloorControlManager          │    │  • Audio capture (ALSA)         ││
│   │  • OpusCodecManager             │    │  • Opus/CODEC2 encoding         ││
│   │  • MulticastAudioManager        │    │  • RTP multicast                ││
│   │  • Premium UI (Compose)         │    │  • LuCI web interface           ││
│   │                                 │    │  • UCI configuration            ││
│   └───────────────┬─────────────────┘    └───────────────┬─────────────────┘│
│                   │                                      │                   │
│                   │     USB/Ethernet                     │                   │
│                   │         │                            │                   │
│                   ▼         ▼                            ▼                   │
│   ┌─────────────────────────────────────────────────────────────────────────┐│
│   │                      BATMAN-adv MESH NETWORK                            ││
│   │                   (Layer 2 Bridge - Multicast)                          ││
│   │                                                                         ││
│   │        239.255.0.1:5004 ──── PTT Channel 1                              ││
│   │        239.255.0.2:5004 ──── PTT Channel 2                              ││
│   │        239.255.0.x:5004 ──── PTT Channel x                              ││
│   └─────────────────────────────────────────────────────────────────────────┘│
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Kanban Board

### Legend

| Priority | Label | Description |
|----------|-------|-------------|
| P0 | `CRITICAL` | Blocks production deployment |
| P1 | `HIGH` | Required for MVP |
| P2 | `MEDIUM` | Important but not blocking |
| P3 | `LOW` | Nice to have |

| Type | Label |
|------|-------|
| `feature` | New functionality |
| `bug` | Defect fix |
| `security` | Security enhancement |
| `test` | Test coverage |
| `docs` | Documentation |
| `infra` | Infrastructure/DevOps |

---

## BACKLOG (Icebox)

### P3 - Future Enhancements (Android)

| ID | Title | Type | Platform | Effort | Notes |
|----|-------|------|----------|--------|-------|
| B-001 | Post-Quantum Key Exchange | `security` | Android | 3 weeks | ML-KEM (Kyber) for future-proofing |
| B-002 | CODEC2 Low-Bandwidth Mode | `feature` | Both | 2 weeks | 0.7-3.2 kbps for extreme conditions |
| B-003 | Lottie Animations | `feature` | Android | 1 week | Empty states, loading animations |
| B-004 | Firebase Crashlytics | `infra` | Android | 3 days | Crash reporting for production |
| B-005 | Room Database Migration | `feature` | Android | 2 weeks | Call history, message persistence |
| B-006 | Bluetooth PTT Button | `feature` | Android | 1 week | External hardware PTT support |
| B-007 | Widget Support | `feature` | Android | 1 week | Home screen PTT widget |
| B-008 | Wear OS Companion | `feature` | Android | 3 weeks | Smartwatch PTT app |
| B-009 | iOS Version | `feature` | iOS | 8 weeks | Cross-platform expansion |
| B-010 | Desktop Version | `feature` | Desktop | 4 weeks | Electron/Compose Desktop |

### P3 - Future Enhancements (LEDE)

| ID | Title | Type | Platform | Effort | Notes |
|----|-------|------|----------|--------|-------|
| B-011 | PTT Hardware Button GPIO | `feature` | LEDE | 1 week | Physical PTT button on radio |
| B-012 | Channel Scanning | `feature` | LEDE | 2 weeks | Auto-scan for activity |
| B-013 | Voice Recording | `feature` | LEDE | 1 week | Record transmissions locally |
| B-014 | Remote Radio Management | `feature` | LEDE | 2 weeks | Fleet-wide config push |
| B-015 | SNMP Monitoring | `feature` | LEDE | 1 week | Network management integration |

---

## TO DO (Sprint Ready)

---

### ANDROID APP

#### P0 - Critical (Production Blockers)

| ID | Title | Type | Platform | Effort | Acceptance Criteria |
|----|-------|------|----------|--------|---------------------|
| T-001 | SRTP Voice Encryption | `security` | Android | 2 weeks | RFC 3711 compliant, AES-128-CM, per-session keys from MLS |
| T-002 | Unit Test Coverage 80% | `test` | Android | 3 weeks | All core modules: PTT, Crypto, Network, Audio |
| T-003 | Integration Tests | `test` | Android | 2 weeks | End-to-end PTT flow, multi-device scenarios |
| T-004 | Load Testing (10+ devices) | `test` | Both | 1 week | Floor control collision with 10+ simultaneous requests |
| T-005 | MeshRider Hardware Testing | `test` | Both | 1 week | Validate on actual DoodleLabs radios |

#### P1 - High Priority

| ID | Title | Type | Platform | Effort | Acceptance Criteria |
|----|-------|------|----------|--------|---------------------|
| T-006 | Opus VAD/DTX Integration | `feature` | Android | 3 days | Bandwidth savings during silence |
| T-007 | Audio Routing (Speaker/Earpiece/BT) | `feature` | Android | 3 days | User can select audio output |
| T-008 | Call History with Room | `feature` | Android | 1 week | Persist call logs, search, filter |
| T-009 | Video Call SurfaceViewRenderer | `feature` | Android | 3 days | Display remote video in CallActivity |
| T-010 | Proper Error Snackbars | `feature` | Android | 2 days | User-friendly error messages |

#### P2 - Medium Priority

| ID | Title | Type | Platform | Effort | Acceptance Criteria |
|----|-------|------|----------|--------|---------------------|
| T-011 | Accessibility Support | `feature` | Android | 1 week | contentDescription for all UI elements |
| T-012 | Loading Skeleton Screens | `feature` | Android | 3 days | Shimmer effect during data load |
| T-013 | Pull-to-Refresh Contacts | `feature` | Android | 1 day | Refresh contact list gesture |
| T-014 | Onboarding Flow | `feature` | Android | 1 week | First-time user setup wizard |
| T-015 | Share Contact QR | `feature` | Android | 2 days | Share QR via other apps |
| T-016 | ATAK Channel Dropdown | `feature` | Android | 3 days | ATAK plugin channel selector |

#### P3 - Low Priority

| ID | Title | Type | Platform | Effort | Acceptance Criteria |
|----|-------|------|----------|--------|---------------------|
| T-017 | Fix Deprecation Warnings | `bug` | Android | 1 day | ArrowBack, statusBarColor, NsdManager |
| T-018 | Configurable Beacon Interval | `feature` | Android | 2 days | User can adjust discovery frequency |
| T-019 | Dark/Light Theme Toggle | `feature` | Android | 2 days | System/Manual theme selection |

---

### LEDE FIRMWARE (MeshRider Radio)

#### P0 - Critical (Production Blockers)

| ID | Title | Type | Platform | Effort | Acceptance Criteria |
|----|-------|------|----------|--------|---------------------|
| L-001 | dl-ptt Package Skeleton | `feature` | LEDE | 3 days | OpenWRT Makefile, directory structure, builds |
| L-002 | PTT Daemon Core (main.c) | `feature` | LEDE | 1 week | Daemonize, signal handling, config loading |
| L-003 | Audio Capture (ALSA) | `feature` | LEDE | 1 week | Microphone input, 16kHz mono PCM |
| L-004 | Audio Playback (ALSA) | `feature` | LEDE | 1 week | Speaker output, jitter buffer |
| L-005 | Opus Encoder Integration | `feature` | LEDE | 1 week | Link libopus, encode 20ms frames |
| L-006 | RTP Multicast TX | `feature` | LEDE | 1 week | RFC 3550, send to 239.255.0.x:5004 |
| L-007 | RTP Multicast RX | `feature` | LEDE | 1 week | Join multicast, receive packets |
| L-008 | Floor Control Client | `feature` | LEDE | 2 weeks | Match Android FloorControlProtocol |

#### P1 - High Priority

| ID | Title | Type | Platform | Effort | Acceptance Criteria |
|----|-------|------|----------|--------|---------------------|
| L-009 | UCI Configuration | `feature` | LEDE | 3 days | /etc/config/ptt, channel settings |
| L-010 | LuCI Web Interface | `feature` | LEDE | 2 weeks | PTT settings page, channel management |
| L-011 | Init Script | `infra` | LEDE | 1 day | /etc/init.d/dl-ptt, start/stop/enable |
| L-012 | SRTP Encryption | `security` | LEDE | 2 weeks | Link libsrtp, key exchange |
| L-013 | Talkgroup Manager | `feature` | LEDE | 1 week | Multi-channel support, scanning |

#### P2 - Medium Priority

| ID | Title | Type | Platform | Effort | Acceptance Criteria |
|----|-------|------|----------|--------|---------------------|
| L-014 | DSCP QoS Marking | `feature` | LEDE | 3 days | Set DSCP EF (46) on packets |
| L-015 | Statistics/Metrics | `feature` | LEDE | 1 week | TX/RX counters, latency stats |
| L-016 | Logging System | `feature` | LEDE | 3 days | syslog integration, log levels |
| L-017 | CODEC2 Fallback | `feature` | LEDE | 2 weeks | Ultra-low bandwidth mode |
| L-018 | Radio API Integration | `feature` | LEDE | 1 week | Get link quality, channel info |

#### P3 - Low Priority

| ID | Title | Type | Platform | Effort | Acceptance Criteria |
|----|-------|------|----------|--------|---------------------|
| L-019 | PTT LED Indicator | `feature` | LEDE | 2 days | GPIO control for TX/RX LED |
| L-020 | Firmware OTA Update | `infra` | LEDE | 2 weeks | Remote firmware deployment |
| L-021 | CLI Tool (pttctl) | `feature` | LEDE | 1 week | Command-line PTT control |

---

### LEDE Package Structure (dl-ptt)

```
package/dl-ptt/
├── Makefile                    # OpenWRT package definition
├── src/
│   ├── main.c                  # PTT daemon entry point
│   ├── config.c                # UCI config parser
│   ├── config.h
│   ├── audio_capture.c         # ALSA microphone capture
│   ├── audio_capture.h
│   ├── audio_playback.c        # ALSA speaker output
│   ├── audio_playback.h
│   ├── codec.c                 # Opus/CODEC2 encoding
│   ├── codec.h
│   ├── rtp.c                   # RTP packetization
│   ├── rtp.h
│   ├── multicast.c             # Multicast socket management
│   ├── multicast.h
│   ├── floor_control.c         # Half-duplex arbitration
│   ├── floor_control.h
│   ├── talkgroup.c             # Channel management
│   ├── talkgroup.h
│   ├── crypto.c                # SRTP encryption
│   ├── crypto.h
│   └── utils.c                 # Logging, helpers
├── files/
│   ├── dl-ptt.init             # Init script
│   └── dl-ptt.config           # Default UCI config
├── luci/
│   └── luci-app-ptt/
│       ├── Makefile
│       ├── htdocs/
│       │   └── luci-static/
│       │       └── ptt/
│       │           └── ptt.js   # JavaScript UI
│       ├── luasrc/
│       │   ├── controller/
│       │   │   └── ptt.lua      # Controller
│       │   ├── model/
│       │   │   └── cbi/
│       │   │       └── ptt.lua  # CBI model
│       │   └── view/
│       │       └── ptt/
│       │           └── status.htm
│       └── root/
│           └── etc/
│               └── uci-defaults/
│                   └── luci-app-ptt
└── tests/
    ├── test_codec.c
    ├── test_rtp.c
    └── test_floor.c
```

### LEDE UCI Configuration Example

```
# /etc/config/ptt

config ptt 'main'
    option enabled '1'
    option default_channel '1'
    option audio_device 'hw:0,0'
    option sample_rate '16000'
    option codec 'opus'
    option bitrate '24000'
    option ptime '20'
    option jitter_buffer '60'
    option dscp '46'

config channel 'channel1'
    option name 'Command'
    option multicast_addr '239.255.0.1'
    option port '5004'
    option priority 'normal'
    option encryption '1'

config channel 'channel2'
    option name 'Alpha'
    option multicast_addr '239.255.0.2'
    option port '5004'
    option priority 'normal'
    option encryption '1'

config channel 'emergency'
    option name 'Emergency'
    option multicast_addr '239.255.0.255'
    option port '5004'
    option priority 'emergency'
    option encryption '1'
```

---

## IN PROGRESS

| ID | Title | Assignee | Started | Progress | Blockers |
|----|-------|----------|---------|----------|----------|
| | *No items currently in progress* | | | | |

---

## IN REVIEW / TESTING

| ID | Title | Reviewer | PR Link | Test Status |
|----|-------|----------|---------|-------------|
| | *No items currently in review* | | | |

---

## DONE (January 2026)

### Core Infrastructure

| ID | Title | Completed | Notes |
|----|-------|-----------|-------|
| D-001 | MLS Encryption Manager | Jan 10, 2026 | `core/crypto/MLSManager.kt` |
| D-002 | PTT System Core | Jan 10, 2026 | `PTTManager.kt`, `PTTAudioManager.kt` |
| D-003 | Peer Discovery (mDNS) | Jan 10, 2026 | `PeerDiscoveryManager.kt` |
| D-004 | Premium Theme System | Jan 10, 2026 | Glassmorphism UI |
| D-005 | Premium UI Components | Jan 11, 2026 | `PremiumComponents.kt`, `PremiumDialogs.kt` |

### Tactical Features

| ID | Title | Completed | Notes |
|----|-------|-----------|-------|
| D-006 | Location Sharing | Jan 10, 2026 | Blue Force Tracking |
| D-007 | SOS Emergency System | Jan 10, 2026 | Geofencing + alerts |
| D-008 | Offline Messaging | Jan 10, 2026 | Store-and-forward |
| D-009 | Opus Codec Integration | Jan 12, 2026 | 6-24 kbps adaptive |
| D-010 | Multicast RTP Transport | Jan 12, 2026 | RFC 3550 compliant |
| D-011 | Adaptive Jitter Buffer | Jan 13, 2026 | 20-100ms dynamic |
| D-012 | DSCP QoS Marking | Jan 13, 2026 | EF (46) for voice |

### UI Screens

| ID | Title | Completed | Notes |
|----|-------|-----------|-------|
| D-013 | Dashboard Screen | Jan 10, 2026 | Network orb, quick stats |
| D-014 | Groups Screen | Jan 10, 2026 | Create/join/leave groups |
| D-015 | Channels Screen | Jan 10, 2026 | PTT channel management |
| D-016 | Map Screen | Jan 10, 2026 | Blue Force Tracking |
| D-017 | Settings Screen | Jan 10, 2026 | App configuration |
| D-018 | Contacts Screen | Jan 10, 2026 | Contact management |
| D-019 | QR Show/Scan Screens | Jan 10, 2026 | Contact exchange |

### Floor Control (Military-Grade)

| ID | Title | Completed | Notes |
|----|-------|-----------|-------|
| D-020 | FloorControlManager | Jan 22, 2026 | 3GPP MCPTT state machine |
| D-021 | FloorControlProtocol | Jan 22, 2026 | Encrypted messages |
| D-022 | FloorArbitrator | Jan 22, 2026 | Centralized/distributed |
| D-023 | Floor Control Tests | Jan 22, 2026 | Unit test coverage |
| D-024 | PTT_GUIDE.md Update | Jan 22, 2026 | CTO assessment 8.5/10 |

---

## Epics Overview

---

### ANDROID EPICS

### Epic 1: Security Hardening `[60% Complete]`

| Task | Status | Priority |
|------|--------|----------|
| E2E Encryption (libsodium) | DONE | P0 |
| MLS Group Encryption | DONE | P0 |
| Signed Floor Control Messages | DONE | P0 |
| SRTP Voice Encryption | TO DO | P0 |
| Post-Quantum Keys | BACKLOG | P3 |

### Epic 2: Audio Pipeline `[85% Complete]`

| Task | Status | Priority |
|------|--------|----------|
| Opus Codec | DONE | P0 |
| RTP Packetization | DONE | P0 |
| Multicast Transport | DONE | P0 |
| Adaptive Jitter Buffer | DONE | P0 |
| VAD/DTX | TO DO | P1 |
| Audio Routing | TO DO | P1 |

### Epic 3: Floor Control `[95% Complete]`

| Task | Status | Priority |
|------|--------|----------|
| State Machine | DONE | P0 |
| Priority Preemption | DONE | P0 |
| Emergency Override | DONE | P0 |
| Lamport Timestamps | DONE | P0 |
| Centralized Arbiter | DONE | P1 |
| 10+ Device Load Test | TO DO | P0 |

### Epic 4: Test Coverage `[15% Complete]`

| Task | Status | Priority |
|------|--------|----------|
| CryptoManager Tests | DONE | P0 |
| OpusCodec Tests | DONE | P0 |
| RTPPacket Tests | DONE | P0 |
| FloorControl Tests | DONE | P0 |
| Connector Tests | DONE | P0 |
| PTTManager Tests | TO DO | P0 |
| Integration Tests | TO DO | P0 |
| UI Tests | TO DO | P1 |
| Load Tests | TO DO | P0 |

### Epic 5: UI/UX Polish `[80% Complete]`

| Task | Status | Priority |
|------|--------|----------|
| Premium Theme | DONE | P1 |
| Glassmorphism Components | DONE | P1 |
| Premium Dialogs | DONE | P1 |
| Bottom Navigation | DONE | P1 |
| Accessibility | TO DO | P2 |
| Skeleton Screens | TO DO | P2 |
| Onboarding | TO DO | P2 |

### Epic 6: Platform Integration `[40% Complete]`

| Task | Status | Priority |
|------|--------|----------|
| WiFi Multicast | DONE | P0 |
| MeshRider Compatibility | DONE | P0 |
| BATMAN-adv L2 Bridge | DONE | P0 |
| ATAK Plugin | IN PROGRESS | P1 |
| MeshRider Hardware Test | TO DO | P0 |

---

### LEDE FIRMWARE EPICS

### Epic 7: LEDE Core Infrastructure `[0% Complete]`

| Task | Status | Priority |
|------|--------|----------|
| Package Skeleton (Makefile) | TO DO | P0 |
| PTT Daemon Core | TO DO | P0 |
| Init Script | TO DO | P1 |
| UCI Configuration | TO DO | P1 |
| Logging System | TO DO | P2 |

### Epic 8: LEDE Audio Pipeline `[0% Complete]`

| Task | Status | Priority |
|------|--------|----------|
| ALSA Audio Capture | TO DO | P0 |
| ALSA Audio Playback | TO DO | P0 |
| Opus Encoder | TO DO | P0 |
| Opus Decoder | TO DO | P0 |
| Jitter Buffer | TO DO | P0 |
| CODEC2 Fallback | TO DO | P2 |

### Epic 9: LEDE Network Stack `[0% Complete]`

| Task | Status | Priority |
|------|--------|----------|
| RTP Packetization | TO DO | P0 |
| Multicast TX | TO DO | P0 |
| Multicast RX | TO DO | P0 |
| SRTP Encryption | TO DO | P1 |
| DSCP QoS Marking | TO DO | P2 |

### Epic 10: LEDE Floor Control `[0% Complete]`

| Task | Status | Priority |
|------|--------|----------|
| Floor Control Protocol | TO DO | P0 |
| State Machine | TO DO | P0 |
| Priority Handling | TO DO | P0 |
| Emergency Override | TO DO | P0 |
| Talkgroup Manager | TO DO | P1 |

### Epic 11: LEDE Web Interface `[0% Complete]`

| Task | Status | Priority |
|------|--------|----------|
| LuCI App Structure | TO DO | P1 |
| PTT Status Page | TO DO | P1 |
| Channel Configuration | TO DO | P1 |
| Statistics Dashboard | TO DO | P2 |

### Epic 12: Cross-Platform Integration `[10% Complete]`

| Task | Status | Priority |
|------|--------|----------|
| Protocol Compatibility | IN PROGRESS | P0 |
| Android ↔ LEDE PTT | TO DO | P0 |
| Shared Encryption Keys | TO DO | P0 |
| Multi-hop Testing | TO DO | P0 |
| Field Deployment Test | TO DO | P0 |

---

## Sprint Planning (Suggested)

---

### PHASE 1: ANDROID COMPLETION (7 weeks)

### Sprint 1: Security & Testing Foundation (2 weeks)

**Goal:** Achieve production-ready security and 50% test coverage

| ID | Title | Platform | Points |
|----|-------|----------|--------|
| T-001 | SRTP Voice Encryption | Android | 13 |
| T-002 | Unit Test Coverage (PTTManager) | Android | 8 |
| T-002 | Unit Test Coverage (NetworkManager) | Android | 5 |

**Total Points:** 26

### Sprint 2: Testing & Quality (2 weeks)

**Goal:** Complete test coverage and load testing

| ID | Title | Platform | Points |
|----|-------|----------|--------|
| T-002 | Unit Test Coverage (Remaining) | Android | 13 |
| T-003 | Integration Tests | Android | 13 |
| T-004 | Load Testing (10+ devices) | Both | 8 |

**Total Points:** 34

### Sprint 3: Hardware Validation (1 week)

**Goal:** Validate Android on MeshRider hardware

| ID | Title | Platform | Points |
|----|-------|----------|--------|
| T-005 | MeshRider Hardware Testing | Both | 8 |
| T-006 | Opus VAD/DTX Integration | Android | 3 |
| T-010 | Proper Error Snackbars | Android | 2 |

**Total Points:** 13

### Sprint 4: Feature Polish (2 weeks)

**Goal:** Complete Android MVP features

| ID | Title | Platform | Points |
|----|-------|----------|--------|
| T-007 | Audio Routing | Android | 3 |
| T-008 | Call History with Room | Android | 8 |
| T-009 | Video Call SurfaceViewRenderer | Android | 3 |
| T-014 | Onboarding Flow | Android | 8 |

**Total Points:** 22

---

### PHASE 2: LEDE FIRMWARE DEVELOPMENT (10 weeks)

### Sprint 5: LEDE Foundation (2 weeks)

**Goal:** Build package skeleton and daemon core

| ID | Title | Platform | Points |
|----|-------|----------|--------|
| L-001 | dl-ptt Package Skeleton | LEDE | 5 |
| L-002 | PTT Daemon Core (main.c) | LEDE | 13 |
| L-011 | Init Script | LEDE | 3 |
| L-009 | UCI Configuration | LEDE | 5 |

**Total Points:** 26

### Sprint 6: LEDE Audio Pipeline (2 weeks)

**Goal:** Implement audio capture and playback

| ID | Title | Platform | Points |
|----|-------|----------|--------|
| L-003 | Audio Capture (ALSA) | LEDE | 13 |
| L-004 | Audio Playback (ALSA) | LEDE | 13 |
| L-016 | Logging System | LEDE | 5 |

**Total Points:** 31

### Sprint 7: LEDE Codec & RTP (2 weeks)

**Goal:** Implement Opus codec and RTP transport

| ID | Title | Platform | Points |
|----|-------|----------|--------|
| L-005 | Opus Encoder Integration | LEDE | 13 |
| L-006 | RTP Multicast TX | LEDE | 8 |
| L-007 | RTP Multicast RX | LEDE | 8 |

**Total Points:** 29

### Sprint 8: LEDE Floor Control (2 weeks)

**Goal:** Implement floor control matching Android

| ID | Title | Platform | Points |
|----|-------|----------|--------|
| L-008 | Floor Control Client | LEDE | 21 |
| L-013 | Talkgroup Manager | LEDE | 8 |

**Total Points:** 29

### Sprint 9: LEDE Web Interface (2 weeks)

**Goal:** LuCI integration and security

| ID | Title | Platform | Points |
|----|-------|----------|--------|
| L-010 | LuCI Web Interface | LEDE | 21 |
| L-012 | SRTP Encryption | LEDE | 13 |

**Total Points:** 34

---

### PHASE 3: INTEGRATION & DEPLOYMENT (3 weeks)

### Sprint 10: Cross-Platform Integration (2 weeks)

**Goal:** Android ↔ LEDE interoperability

| ID | Title | Platform | Points |
|----|-------|----------|--------|
| INT-001 | Android ↔ LEDE PTT Testing | Both | 13 |
| INT-002 | Multi-hop Mesh Testing | Both | 8 |
| INT-003 | Encryption Key Exchange | Both | 8 |
| L-014 | DSCP QoS Marking | LEDE | 3 |

**Total Points:** 32

### Sprint 11: Field Testing & Release (1 week)

**Goal:** Real-world deployment validation

| ID | Title | Platform | Points |
|----|-------|----------|--------|
| FT-001 | Field Deployment Test (5+ radios) | Both | 13 |
| FT-002 | Performance Benchmarks | Both | 5 |
| FT-003 | Documentation Finalization | Both | 3 |

**Total Points:** 21

---

## Risk Register

### Android Risks

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| SRTP integration complexity | Medium | High | Use existing libsrtp library |
| Test coverage delays | Medium | Medium | Prioritize critical path tests |
| Performance issues on low-end devices | Medium | Medium | Profile and optimize early |
| App store rejection | Low | High | Follow Android guidelines strictly |
| WebRTC version conflicts | Low | Medium | Pin to stable version |

### LEDE Firmware Risks

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| ALSA driver compatibility | Medium | High | Test on multiple radio hardware revisions |
| OpenWRT version conflicts | Medium | High | Target specific LEDE version, document dependencies |
| Memory constraints (embedded) | High | Medium | Optimize buffer sizes, use static allocation |
| Cross-compilation issues | Medium | Medium | Set up CI with proper toolchain |
| libopus linking issues | Medium | Medium | Use OpenWRT package feed version |
| Real-time audio latency | Medium | High | Use PREEMPT kernel, optimize thread priorities |
| Flash storage limits | Medium | Medium | Minimize package size, use overlayfs |

### Integration Risks

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| Protocol mismatch Android ↔ LEDE | Medium | High | Define protocol spec first, test early |
| Encryption key sync failures | Medium | High | Implement key exchange protocol carefully |
| Multi-hop latency too high | Medium | Medium | Optimize BATMAN-adv settings |
| MeshRider firmware incompatibility | Low | High | Test early with DoodleLabs team |
| Field conditions (RF interference) | Medium | Medium | Implement robust error handling, PLC |

---

## Milestones

### Android Milestones

| Milestone | Target Date | Status | Deliverables |
|-----------|-------------|--------|--------------|
| M1: Android Alpha | Jan 31, 2026 | ON TRACK | Core PTT, Floor Control, E2E Encryption |
| M2: Android Security | Feb 15, 2026 | PLANNED | SRTP, Full encryption coverage |
| M3: Android Test 80% | Mar 01, 2026 | PLANNED | Unit + Integration tests |
| M4: Android Beta | Mar 15, 2026 | PLANNED | Feature complete, hardware validated |
| M5: Android Production | Apr 01, 2026 | PLANNED | Play Store ready |

### LEDE Firmware Milestones

| Milestone | Target Date | Status | Deliverables |
|-----------|-------------|--------|--------------|
| M6: LEDE Skeleton | Apr 15, 2026 | PLANNED | Package builds, daemon runs |
| M7: LEDE Audio | May 01, 2026 | PLANNED | Audio capture/playback working |
| M8: LEDE PTT Alpha | May 15, 2026 | PLANNED | Basic PTT over multicast |
| M9: LEDE Floor Control | Jun 01, 2026 | PLANNED | Full floor control protocol |
| M10: LEDE LuCI | Jun 15, 2026 | PLANNED | Web interface complete |
| M11: LEDE Beta | Jul 01, 2026 | PLANNED | Feature complete |

### Integration Milestones

| Milestone | Target Date | Status | Deliverables |
|-----------|-------------|--------|--------------|
| M12: Cross-Platform Alpha | Jul 15, 2026 | PLANNED | Android ↔ LEDE interop working |
| M13: Field Test | Aug 01, 2026 | PLANNED | 5+ radio mesh deployment |
| M14: Production Release | Aug 15, 2026 | PLANNED | Full system ready |

---

## Team Velocity

| Sprint | Planned | Completed | Velocity |
|--------|---------|-----------|----------|
| Pre-Sprint (Jan 1-21) | - | 24 items | - |
| Sprint 0 (Jan 22) | 6 | 6 | 21 pts |

**Average Velocity:** 21 points/week (solo developer)

---

## Technical Debt Tracker

| Item | Severity | File | Description |
|------|----------|------|-------------|
| TD-001 | Medium | `ContactDetailScreen.kt:319` | TODO: Share contact QR |
| TD-002 | Low | `HomeScreen.kt:401` | TODO: Get real contact status |
| TD-003 | Medium | `FloorControlProtocol.kt:613` | TODO: Proper multicast delivery |
| TD-004 | Low | `FloorControlProtocol.kt:621` | TODO: Get own name from settings |
| TD-005 | Medium | `BeaconManager.kt:462` | TODO: Configurable beacon interval |
| TD-006 | Medium | ATAK Plugin | TODO: Channel selector dropdown |
| TD-007 | Low | Various | Deprecation warnings (ArrowBack, statusBarColor) |

---

## Definition of Done

- [ ] Code compiles without errors
- [ ] Unit tests written and passing
- [ ] Code reviewed (self-review for solo)
- [ ] Documentation updated
- [ ] No new lint warnings
- [ ] Works on API 26+ devices
- [ ] No security vulnerabilities introduced
- [ ] CLAUDE.md updated with changes

---

## Stakeholder Communication

### Weekly Status Report Template

```
MESH RIDER WAVE - Weekly Status Report
Week of: [DATE]

COMPLETED THIS WEEK:
- [Item 1]
- [Item 2]

IN PROGRESS:
- [Item 1] - [X]% complete

BLOCKERS:
- [None / Description]

NEXT WEEK PLAN:
- [Item 1]
- [Item 2]

METRICS:
- Build Status: PASSING/FAILING
- Test Coverage: XX%
- Open Bugs: X
```

---

## Quick Links

### Android

| Resource | Link |
|----------|------|
| Android Codebase | `/Users/jabbir/development/meshriderwave` |
| CLAUDE.md | `./CLAUDE.md` |
| PTT Guide | `./docs/PTT_GUIDE.md` |
| Build | `./gradlew assembleDebug` |
| Tests | `./gradlew test` |

### LEDE Firmware

| Resource | Link |
|----------|------|
| LEDE Codebase | `/home/jabi/workspace/doodle/lede` |
| dl-ptt Package | `/home/jabi/workspace/doodle/lede/package/dl-ptt` |
| Build | `make package/dl-ptt/compile V=s` |
| Config | `make menuconfig` |
| Deploy | `scp bin/packages/.../dl-ptt*.ipk root@10.223.232.141:/tmp/` |
| Install | `opkg install /tmp/dl-ptt*.ipk` |

### Documentation

| Resource | Link |
|----------|------|
| OpenWRT Dev Guide | https://openwrt.org/docs/guide-developer |
| ALSA Programming | https://www.alsa-project.org/alsa-doc/alsa-lib/ |
| libopus API | https://opus-codec.org/docs/ |
| MeshRider Docs | https://doodlelabs.com/support/ |

---

**Document Owner:** Jabbir Basha P
**Last Updated:** January 22, 2026
**Next Review:** January 29, 2026
