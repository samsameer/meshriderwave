ok
# MR Wave — Quick Reference Card

## One-Line Summary

**PTT Android app using Opus+RTP multicast over MeshRider IP transport. Radio has no PTT logic.**

---

## Build Commands

```bash
./gradlew assembleDebug     # Build
./gradlew installDebug      # Install
./gradlew test              # Test
```

---

## Key Classes

| Class | What it Does |
|-------|--------------|
| `PTTManager` | Floor control, TX/RX |
| `OpusCodecManager` | 256kbps → 6-24kbps |
| `RTPPacketManager` | Multicast 239.255.0.x:5004 |
| `CryptoManager` | Ed25519 + X25519 |
| `LocationSharingManager` | BFT + Geofencing |
| `SOSManager` | Emergency broadcast |

---

## Audio Pipeline

```
Mic → AudioRecord → Opus Encode → RTP Packet → Multicast UDP
                                                    ↓
Speaker ← AudioTrack ← Opus Decode ← RTP Parse ← Receive
```

---

## Ports & Addresses

| Service | Port/Address |
|---------|--------------|
| Signaling | TCP 10001 |
| PTT Audio | UDP 239.255.0.x:5004 |
| DSCP | EF (46) |

---

## Floor Control Flow

```
Press PTT → FLOOR_REQUEST → Wait 200ms → No denial? → TX
                                ↓
                           FLOOR_DENIED → "Busy"
```

---

## Codec Settings

| Mode | Bitrate | Use Case |
|------|---------|----------|
| Low | 6 kbps | Bad network |
| Normal | 12 kbps | Default |
| High | 24 kbps | Good network |

---

## Encryption

```
Identity: Ed25519
Exchange: X25519
Cipher:   XSalsa20-Poly1305
Groups:   MLS
```

---

## 3rd Party Audio

Works automatically — app uses `AudioRecord(VOICE_COMMUNICATION)` which routes to any connected device.

---

## External Network Bridge (on radio)

```bash
# L2 bridge (BATMAN-adv is Layer 2)
brctl addbr br-gateway
brctl addif br-gateway eth0 bat0
ifconfig br-gateway up
```
Multicast passes at L2 — no L3 routing needed.

---

## Status: 80% Complete

Done: PTT, Opus, RTP, Crypto, BFT, SOS, UI
Todo: Tests, Hardware PTT button, ATAK plugin
