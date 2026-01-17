# MeshRider Wave Android - Field Testing Guide

**Version:** 2.2.0 | **Last Updated:** January 2026 | **Classification:** Operations Manual

---

## Table of Contents

1. [Overview](#overview)
2. [Prerequisites](#prerequisites)
3. [Equipment Setup](#equipment-setup)
4. [Network Configuration](#network-configuration)
5. [Test Procedures](#test-procedures)
6. [Troubleshooting](#troubleshooting)
7. [Performance Metrics](#performance-metrics)
8. [Test Log Template](#test-log-template)

---

## Overview

This guide provides step-by-step procedures for field testing MeshRider Wave with DoodleLabs MeshRider mesh radios. Field testing validates:

- PTT voice communication quality
- Network connectivity and mesh routing
- Radio API integration
- Blue Force Tracking accuracy
- SOS emergency features
- ATAK interoperability

### Test Environment Requirements

| Component | Minimum | Recommended |
|-----------|---------|-------------|
| MeshRider Radios | 2 | 3+ (mesh topology) |
| Android Devices | 2 | 3+ |
| Test Area | 100m radius | 500m+ with obstacles |
| Personnel | 2 | 3+ for mesh testing |

---

## Prerequisites

### 1. Hardware Requirements

#### MeshRider Radios

| Model | Frequency | Range | Notes |
|-------|-----------|-------|-------|
| Smart Radio 2450-8 | 2.4-5 GHz | 5+ km | Recommended |
| Smart Radio 2450-2 | 2.4 GHz | 2+ km | Budget option |
| RM-2450-E | 2.4-5 GHz | 10+ km | Extended range |

**Radio Configuration Checklist:**

```bash
# SSH to radio (default credentials: root/doodle)
ssh root@10.223.232.141

# Verify firmware version
cat /etc/openwrt_release | grep DISTRIB_DESCRIPTION

# Check mesh mode
uci get wireless.radio0.meshid

# Verify BATMAN-adv is running
batctl o  # Show originator table

# Check IP addressing
ip addr show bat0
```

#### Android Devices

| Requirement | Specification |
|-------------|---------------|
| Android Version | 8.0+ (API 26+) |
| RAM | 4+ GB |
| Storage | 2+ GB free |
| GPS | Required for BFT |
| Audio | External PTT headset recommended |

### 2. Software Requirements

```bash
# Build and install MR Wave APK
cd meshrider-wave-android
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Optional: Install ATAK-CIV for integration testing
# Download from: https://tak.gov/

# Install ADB debugging tools
sudo apt install android-tools-adb
```

### 3. Network Planning

```
Recommended Test Topology:

                    ┌─────────────────┐
                    │  Base Station   │
                    │  10.223.232.1   │
                    │  (Fixed Radio)  │
                    └────────┬────────┘
                             │
              ┌──────────────┼──────────────┐
              │              │              │
     ┌────────▼────────┐    │    ┌─────────▼───────┐
     │   Mobile Unit 1 │    │    │  Mobile Unit 2  │
     │   10.223.232.10 │    │    │  10.223.232.20  │
     │   (Android + Radio)  │    │  (Android + Radio)
     └─────────────────┘    │    └─────────────────┘
                            │
                   ┌────────▼────────┐
                   │   Mobile Unit 3 │
                   │   10.223.232.30 │
                   │   (Multi-hop)   │
                   └─────────────────┘
```

---

## Equipment Setup

### Step 1: Radio Configuration

**1.1 Power On and Connect**

```bash
# Connect radio via USB/Ethernet
# Wait for radio to boot (30-60 seconds)

# Find radio IP (default: 10.223.232.141)
ping 10.223.232.141

# Or use discovery
nmap -sn 10.223.232.0/24
```

**1.2 Verify Radio Settings**

```bash
# SSH to radio
ssh root@10.223.232.141
# Password: doodle

# Check wireless configuration
uci show wireless

# Expected output:
# wireless.radio0.channel='149'
# wireless.radio0.htmode='HT20'
# wireless.radio0.disabled='0'
# wireless.mesh0.meshid='MeshRider'
```

**1.3 Configure Mesh Network**

```bash
# Set mesh ID (must match all radios)
uci set wireless.mesh0.meshid='TestMesh'

# Set channel (must match all radios)
uci set wireless.radio0.channel='149'

# Set bandwidth (must match all radios)
uci set wireless.radio0.htmode='HT20'  # or HT40

# Apply and restart
uci commit wireless
wifi restart

# Verify mesh neighbors
batctl n
```

### Step 2: Android Device Setup

**2.1 Install Application**

```bash
# Enable USB debugging on Android device
# Settings > Developer Options > USB Debugging

# Install APK
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Grant permissions
adb shell pm grant com.doodlelabs.meshriderwave android.permission.RECORD_AUDIO
adb shell pm grant com.doodlelabs.meshriderwave android.permission.ACCESS_FINE_LOCATION
```

**2.2 Configure App Settings**

1. Launch MeshRider Wave
2. Go to **Settings**
3. Set username/callsign
4. Verify network settings:
   - Subnet: `10.223.x.x`
   - Signaling Port: `10001`
5. Enable location sharing

**2.3 Connect Device to Radio**

```
Connection Methods:

Option A: USB Ethernet Adapter
┌─────────────┐    USB-C    ┌─────────────┐   Ethernet   ┌─────────────┐
│   Android   │◄──────────►│ USB Adapter │◄────────────►│   Radio     │
│   Phone     │             │ (Gigabit)   │              │  (PoE)      │
└─────────────┘             └─────────────┘              └─────────────┘

Option B: WiFi (Access Point Mode)
┌─────────────┐    WiFi     ┌─────────────┐    Mesh      ┌─────────────┐
│   Android   │◄──────────►│   Radio     │◄────────────►│ Other Radios│
│   Phone     │  (5 GHz)   │  (AP Mode)  │  (BATMAN)    │             │
└─────────────┘             └─────────────┘              └─────────────┘

Option C: Portable Kit
┌─────────────────────────────────────────────────────────┐
│               Portable Mesh Terminal                     │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐              │
│  │ Android  │──│ Battery  │──│  Radio   │──►Antenna    │
│  │ Tablet   │  │ Pack     │  │          │              │
│  └──────────┘  └──────────┘  └──────────┘              │
└─────────────────────────────────────────────────────────┘
```

### Step 3: Verify Connectivity

**3.1 Check Network Interface**

```bash
# On Android (via adb shell)
adb shell ip addr show

# Look for interface with 10.223.x.x address
# Example: eth0: 10.223.232.50/24
```

**3.2 Verify Radio Connection in App**

1. Open MeshRider Wave
2. Go to Dashboard
3. Check "Radio Status" card
4. Should show:
   - Connected: Yes
   - SSID: Your mesh ID
   - Signal: -XX dBm
   - Peers: N (number of mesh peers)

**3.3 Ping Test**

```bash
# From Android device
adb shell ping -c 5 10.223.232.1  # Base station
adb shell ping -c 5 10.223.232.20  # Other mobile unit
```

---

## Network Configuration

### IP Addressing Scheme

| Device | IP Address | Role |
|--------|------------|------|
| Base Station Radio | 10.223.232.1 | Gateway |
| Mobile Radio 1 | 10.223.232.10 | Node |
| Mobile Radio 2 | 10.223.232.20 | Node |
| Mobile Radio 3 | 10.223.232.30 | Node |
| Android Device 1 | 10.223.232.50 | Client |
| Android Device 2 | 10.223.232.51 | Client |

### Multicast Configuration

```
PTT Voice Channels:
┌────────────────────────────────────────────────┐
│ Talkgroup 1 (Default): 239.255.0.1:5004       │
│ Talkgroup 2 (Team A):  239.255.0.2:5004       │
│ Talkgroup 3 (Team B):  239.255.0.3:5004       │
│ Emergency:             239.255.0.255:5004     │
└────────────────────────────────────────────────┘

Location Sharing:
┌────────────────────────────────────────────────┐
│ BFT Updates:  239.255.1.1:5005                │
└────────────────────────────────────────────────┘

ATAK Integration:
┌────────────────────────────────────────────────┐
│ CoT Multicast: 239.2.3.1:6969                 │
└────────────────────────────────────────────────┘
```

### Firewall Rules (if applicable)

```bash
# On radio, ensure multicast is allowed
iptables -A INPUT -p udp -d 239.0.0.0/8 -j ACCEPT
iptables -A OUTPUT -p udp -d 239.0.0.0/8 -j ACCEPT

# IGMP for multicast membership
iptables -A INPUT -p igmp -j ACCEPT
```

---

## Test Procedures

### Test 1: Radio Discovery

**Objective:** Verify app can discover MeshRider radios on the network.

**Procedure:**

1. Open MeshRider Wave
2. Go to Dashboard
3. Pull down to refresh
4. Observe "Discovered Radios" section

**Expected Result:**
- All powered radios appear in list
- Each shows IP, hostname, model
- Can tap to connect

**Pass Criteria:**
- [ ] All radios discovered within 30 seconds
- [ ] Radio details accurate
- [ ] Can connect to selected radio

---

### Test 2: Radio Status Monitoring

**Objective:** Verify real-time radio status display.

**Procedure:**

1. Connect to a radio
2. Observe status updates
3. Move to vary signal strength
4. Add/remove mesh peers

**Expected Result:**
- Signal strength updates every 5 seconds
- Peer count reflects actual mesh neighbors
- Channel and bandwidth shown correctly

**Pass Criteria:**
- [ ] Signal updates within 10 seconds
- [ ] Peer list accurate
- [ ] No UI freezes or crashes

---

### Test 3: PTT Voice - Basic

**Objective:** Verify basic push-to-talk voice transmission.

**Procedure:**

1. Device A: Join Talkgroup 1
2. Device B: Join Talkgroup 1
3. Device A: Press and hold PTT button
4. Device A: Speak test message
5. Device A: Release PTT button
6. Device B: Verify audio received

**Expected Result:**
- Audio transmits from A to B
- Latency < 200ms (subjective)
- Audio quality acceptable (no major artifacts)

**Pass Criteria:**
- [ ] Audio heard on receiving device
- [ ] Latency acceptable for conversation
- [ ] No audio dropouts > 1 second

---

### Test 4: PTT Voice - Floor Control

**Objective:** Verify only one user can transmit at a time.

**Procedure:**

1. Device A: Press PTT (acquire floor)
2. Device B: Press PTT while A is transmitting
3. Observe B's PTT button behavior
4. Device A: Release PTT
5. Device B: Verify can now transmit

**Expected Result:**
- B's PTT shows "busy" indicator
- B cannot transmit while A holds floor
- B can transmit after A releases

**Pass Criteria:**
- [ ] Floor control enforced (no crosstalk)
- [ ] Visual indication of floor status
- [ ] Floor release within 500ms

---

### Test 5: PTT Voice - Multi-hop

**Objective:** Verify voice works over multi-hop mesh paths.

**Procedure:**

1. Position Device A at base station
2. Position Device C beyond direct radio range
3. Ensure Device B can bridge A and C
4. Device A: Transmit PTT
5. Device C: Verify audio received

**Expected Result:**
- Audio traverses multiple mesh hops
- Quality may degrade slightly
- No complete audio loss

**Pass Criteria:**
- [ ] Audio received over 2+ hops
- [ ] Latency < 500ms
- [ ] Intelligible speech

---

### Test 6: Blue Force Tracking

**Objective:** Verify location sharing between devices.

**Procedure:**

1. Device A: Enable location sharing
2. Device B: Open Map screen
3. Device A: Move 50+ meters
4. Device B: Observe map update

**Expected Result:**
- A's position appears on B's map
- Position updates as A moves
- Accuracy within GPS tolerance

**Pass Criteria:**
- [ ] Peer locations visible on map
- [ ] Updates within 10 seconds
- [ ] Position accuracy < 20 meters

---

### Test 7: SOS Emergency

**Objective:** Verify emergency SOS broadcast.

**Procedure:**

1. Device A: Activate SOS
2. Device B: Verify SOS alert received
3. Device B: Acknowledge SOS
4. Device A: Verify acknowledgment received
5. Device A: Deactivate SOS

**Expected Result:**
- SOS alert appears on all devices
- Includes sender location
- Acknowledgment confirmed

**Pass Criteria:**
- [ ] SOS received within 5 seconds
- [ ] Location included in alert
- [ ] Can acknowledge and dismiss

---

### Test 8: ATAK Integration

**Objective:** Verify CoT messaging with ATAK.

**Procedure:**

1. Launch ATAK on Device A
2. Launch MR Wave on Device B
3. Verify Device B appears on ATAK map
4. Use MR Wave PTT from ATAK (if plugin installed)

**Expected Result:**
- MR Wave users visible in ATAK
- CoT updates every 5 seconds
- PTT works from ATAK toolbar

**Pass Criteria:**
- [ ] Positions sync bidirectionally
- [ ] CoT messages received
- [ ] ATAK plugin functional (if installed)

---

### Test 9: Channel Switching

**Objective:** Verify mesh-wide channel switching.

**Procedure:**

1. Connect to radio
2. Go to Settings > Channel
3. Select new channel (e.g., 36 → 149)
4. Confirm switch
5. Verify all radios switched

**Expected Result:**
- All radios switch to new channel
- Connection maintained
- PTT works after switch

**Pass Criteria:**
- [ ] Channel switch completes < 5 seconds
- [ ] All radios on new channel
- [ ] Voice communication restored

---

### Test 10: Stress Test

**Objective:** Verify system stability under load.

**Procedure:**

1. 3+ devices on same talkgroup
2. Continuous PTT activity for 30 minutes
3. Alternate transmitters every 10 seconds
4. Monitor for crashes, audio issues

**Expected Result:**
- No app crashes
- Consistent audio quality
- No memory leaks

**Pass Criteria:**
- [ ] No crashes in 30 minutes
- [ ] Audio quality consistent
- [ ] Battery drain < 20%

---

## Troubleshooting

### Common Issues and Solutions

#### Issue: Radio Not Discovered

```bash
# Check network connectivity
adb shell ping 10.223.255.255  # Should timeout (broadcast)

# Check if on correct subnet
adb shell ip route

# Force discovery refresh
# Pull down on Dashboard screen

# Manually add radio
# Settings > Add Radio Manually > Enter IP
```

#### Issue: No Audio on TX

```bash
# Check microphone permission
adb shell dumpsys package com.doodlelabs.meshriderwave | grep RECORD_AUDIO

# Check audio route
adb shell dumpsys audio

# Restart audio subsystem
# Settings > Developer > Reset Audio
```

#### Issue: No Audio on RX

```bash
# Check multicast join
adb shell netstat -g | grep 239.255

# Check speaker route
adb shell dumpsys audio | grep -A5 STREAM_VOICE_CALL

# Verify multicast routing on radio
ssh root@10.223.232.141 "ip mroute"
```

#### Issue: High Latency

```bash
# Check network quality
adb shell ping -c 100 10.223.232.1 | tail -5

# Check jitter buffer
# Dashboard > Developer > Audio Stats

# Reduce buffer size
# Settings > Audio > Jitter Buffer > Low
```

#### Issue: Floor Control Failures

```bash
# Check signaling connectivity
adb shell netstat -tn | grep 10001

# Verify TCP connection
nc -zv 10.223.232.1 10001

# Check for port conflicts
adb shell netstat -tlnp | grep 10001
```

### Debug Logging

```bash
# Enable verbose logging
adb shell setprop log.tag.MeshRider VERBOSE

# Capture all logs
adb logcat -s MeshRider:* > test_log.txt

# Specific components
adb logcat -s MeshRider:PTTManager MeshRider:RadioApiClient

# WebRTC logs
adb logcat | grep -E "(webrtc|WebRTC)"

# Network logs
adb logcat -s NetworkMonitor
```

---

## Performance Metrics

### Key Performance Indicators (KPIs)

| Metric | Target | Measurement Method |
|--------|--------|-------------------|
| Voice Latency | < 200ms | Audio analyzer |
| Floor Acquisition | < 100ms | Timestamp logs |
| Packet Loss | < 2% | RTP statistics |
| Audio MOS | > 3.5 | Perceptual testing |
| Battery Life | 8+ hours | Continuous PTT test |
| Connection Time | < 5s | Stopwatch |

### Measurement Tools

**1. Voice Latency Measurement**

```bash
# Use loopback test
# Device A transmits, Device B immediately retransmits
# Measure round-trip time / 2

# Automated with timestamps
adb logcat -s MeshRider:PTT | grep -E "TX_START|RX_START"
```

**2. Packet Loss Measurement**

```bash
# From Dashboard > Developer > Audio Stats
# Shows: Packets sent, received, lost

# Or from logs
adb logcat -s MeshRider:RTP | grep "loss"
```

**3. Signal Quality**

```bash
# From Dashboard > Radio Status
# Or via API
curl -s "http://10.223.232.141/cgi-bin/luci/rpc/ubus" \
  -d '{"method":"call","params":["session_id","iwinfo","info",{"device":"wlan0"}]}'
```

---

## Test Log Template

### Test Session Information

```
Date: _______________
Location: _______________
Weather: _______________
Test Lead: _______________
Personnel: _______________

Equipment:
- Radio 1: Model _______ IP _______ FW _______
- Radio 2: Model _______ IP _______ FW _______
- Radio 3: Model _______ IP _______ FW _______
- Device 1: Model _______ Android _______ App Version _______
- Device 2: Model _______ Android _______ App Version _______
- Device 3: Model _______ Android _______ App Version _______
```

### Test Results

| Test | Pass/Fail | Notes |
|------|-----------|-------|
| 1. Radio Discovery | ☐ | |
| 2. Radio Status | ☐ | |
| 3. PTT Basic | ☐ | |
| 4. Floor Control | ☐ | |
| 5. Multi-hop | ☐ | |
| 6. BFT | ☐ | |
| 7. SOS | ☐ | |
| 8. ATAK | ☐ | |
| 9. Channel Switch | ☐ | |
| 10. Stress Test | ☐ | |

### Performance Measurements

| Metric | Value | Pass/Fail |
|--------|-------|-----------|
| Voice Latency (ms) | | < 200ms |
| Floor Acquisition (ms) | | < 100ms |
| Packet Loss (%) | | < 2% |
| Audio Quality (MOS) | | > 3.5 |
| Battery Drain (%/hr) | | < 12.5% |

### Issues Found

| # | Description | Severity | Status |
|---|-------------|----------|--------|
| 1 | | | |
| 2 | | | |
| 3 | | | |

### Notes and Observations

```
_______________________________________________
_______________________________________________
_______________________________________________
_______________________________________________
```

### Sign-off

```
Test Completed: ☐ Yes  ☐ No (partial)
Overall Result: ☐ Pass  ☐ Fail  ☐ Pass with Issues

Tested By: _______________  Date: _______________
Reviewed By: _______________  Date: _______________
```

---

## Appendix A: Quick Reference

### Radio SSH Commands

```bash
# Connect
ssh root@10.223.232.141

# Check mesh status
batctl n           # Neighbors
batctl o           # Originators
batctl tl          # Translation table

# Wireless status
iwinfo wlan0 info
iwinfo wlan0 assoclist

# Restart wireless
wifi restart

# View logs
logread -f
```

### ADB Commands

```bash
# Install app
adb install -r app-debug.apk

# Uninstall
adb uninstall com.doodlelabs.meshriderwave

# View logs
adb logcat -s MeshRider:*

# Grant permissions
adb shell pm grant com.doodlelabs.meshriderwave android.permission.RECORD_AUDIO

# Screen capture
adb shell screencap /sdcard/test.png && adb pull /sdcard/test.png
```

### Network Diagnostics

```bash
# Ping test
ping -c 10 10.223.232.1

# Traceroute
traceroute 10.223.232.30

# Check multicast
netstat -g

# Port scan
nmap -sU -p 5004 239.255.0.1
```

---

**Document Control:**

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | Jan 2026 | Jabbir Basha | Initial release |
| 2.0 | Jan 2026 | Claude Code | Complete rewrite |

---

*Copyright (C) 2024-2026 DoodleLabs Singapore Pte Ltd. All Rights Reserved.*
