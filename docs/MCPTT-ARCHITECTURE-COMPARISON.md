# Mesh Rider Wave vs Standard 3GPP MCPTT Architecture

**Document Version:** 1.0
**Date:** January 26, 2026
**Author:** Jabbir Basha P
**Organization:** DoodleLabs Singapore

---

## Executive Summary

Mesh Rider Wave implements **3GPP MCPTT-compliant floor control** (TS 24.379/24.380) but replaces the IMS/SIP infrastructure with a **mesh-native P2P architecture**. This is intentional — DoodleLabs radios operate in disconnected, tactical environments where IMS infrastructure does not exist.

---

## Architecture Comparison

| Component | Standard 3GPP MCPTT | Mesh Rider Wave | Rationale |
|-----------|---------------------|-----------------|-----------|
| **Signaling** | IMS SIP UA (P-CSCF, S-CSCF) | Direct P2P TCP/10001 | No IMS infrastructure in mesh |
| **Identity** | IMSI/SIM + IMS-AKA | Ed25519 keypair (libsodium) | Cryptographic identity, no carrier |
| **Floor Control** | RTCP per TS 24.380 | Custom protocol over TCP/UDP | Same state machine, different transport |
| **Arbitration** | MCPTT Server | Distributed + Elected Arbiter | Works without central server |
| **Media** | RTP + AMR-WB | RTP + Opus (multicast) | Opus is superior for mesh bandwidth |
| **Discovery** | IMS registration | Beacon (239.255.77.1:7777) + mDNS | Mesh-native peer discovery |
| **Network** | LTE/5G IMS Core | IP Mesh (BATMAN-adv, WiFi Direct) | Tactical off-network by design |

---

## What We Implement (3GPP Compliant)

### 1. Floor Control State Machine (TS 24.379/24.380)

**File:** `FloorControlManager.kt` (990 lines)

```
States: IDLE → PENDING_REQUEST → GRANTED/QUEUED/DENIED/REVOKED/RELEASING

Priority Levels:
  EMERGENCY (3) → Always preempts, 60s max
  HIGH (2)      → Preempts NORMAL/LOW
  NORMAL (1)    → Standard
  LOW (0)       → Background
```

**Implemented:**
- Floor request / granted / denied / released
- Floor taken / revoked notifications
- Priority preemption (emergency overrides all)
- Queue management (priority + Lamport timestamp for fairness)
- Heartbeat keepalive (5s interval, 10s timeout)

### 2. Arbitration Modes

**Distributed Mode (Default):**
- No central server required
- Broadcast floor request to all peers
- 2s timeout — no denial = self-grant
- Lamport clocks for collision resolution

**Centralized Mode:**
- Elected arbiter (highest priority peer)
- Arbiter makes grant/deny decisions
- Automatic election if arbiter fails (10s timeout)

### 3. Media Transmission

**File:** `MulticastPTTTransport.kt`, `PTTManager.kt`

| Mode | Codec | Bitrate | Delivery |
|------|-------|---------|----------|
| MULTICAST | Opus | 6-24 kbps | UDP RTP multicast |
| LEGACY | Raw PCM | 256 kbps | TCP unicast |

**Bandwidth:** Opus 12 kbps vs PCM 256 kbps = **21x compression**

### 4. Security (Military-Grade)

**File:** `CryptoManager.kt`

| Function | Algorithm |
|----------|-----------|
| Identity/Signing | Ed25519 (libsodium) |
| Encryption | XSalsa20-Poly1305 (AEAD) |
| Key Exchange | X25519 |
| Channel Keys | BLAKE2b derivation |

All floor control messages are signed and verified. Audio packets are encrypted with channel shared key.

---

## What We Do NOT Implement (IMS-Specific)

| 3GPP Component | Status | Why Not Needed |
|----------------|--------|----------------|
| IMS Registration | Not implemented | No IMS core in mesh networks |
| SIP INVITE/BYE | Not implemented | Direct P2P signaling instead |
| P-CSCF Discovery | Not implemented | Beacon discovery replaces this |
| Diameter AAA | Not implemented | Ed25519 trust model instead |
| IMSI/SIM Auth | Not implemented | Cryptographic keypairs instead |
| PSTN Gateway | Not implemented | Mesh-only communication |

---

## Platform & Network

### Target Platform
**Android (Kotlin)** — Native with Jetpack Compose UI

### Network Type
**Private Mesh Network** — Not public LTE/5G IMS
- DoodleLabs MeshRider radios (BATMAN-adv)
- WiFi Direct
- Any IP mesh (no cellular required)

### Scope
**Full implementation:**
- Group calls (multicast PTT)
- 1:1 private calls (WebRTC)
- Emergency mode (priority preemption, SOS)
- Blue Force Tracking (GPS sharing)
- ATAK integration (CoT protocol)

---

## Message Flow

```
1. DISCOVERY
   Device broadcasts signed beacon (239.255.77.1:7777)
   Peers verify Ed25519 signature, extract IP address
   Contact list populated automatically

2. JOIN CHANNEL
   Select channel → derive channel encryption key
   Subscribe to multicast group for that channel

3. PTT TRANSMISSION
   User presses PTT button
   → FloorControlManager.requestFloor(NORMAL)
   → Broadcast FLOOR_REQUEST to channel peers
   → Wait 2s for FLOOR_DENIED (distributed mode)
   → No denial = FLOOR_GRANTED
   → Start Opus encoding + RTP multicast
   → Peers receive, decode, play audio

4. RELEASE
   User releases PTT button
   → FloorControlManager.releaseFloor()
   → Broadcast FLOOR_RELEASED
   → Next queued user gets FLOOR_GRANTED
```

---

## 3GPP Specification References

| Specification | Title | Our Implementation |
|---------------|-------|-------------------|
| 3GPP TS 24.379 | MCPTT call control | Floor state machine compliant |
| 3GPP TS 24.380 | MCPTT media plane control | Priority/preemption compliant |
| 3GPP TS 23.379 | Functional architecture | Adapted for mesh topology |
| IETF RFC 3550 | RTP/RTCP | RTP multicast for audio |

---

## Production Status

| Gap | Current | Required | Priority |
|-----|---------|----------|----------|
| QoS Marking | None | DSCP EF (46) | P1 |
| Unit Tests | 0% | 80%+ | P1 |
| MLS Group Encryption | Skeleton | Full implementation | P2 |
| Off-network D2D | Implemented | Tested | P1 |

---

## Why This Architecture?

**Standard 3GPP MCPTT assumes:**
- LTE/5G cellular network
- IMS core infrastructure
- SIP proxies, HSS, application servers
- Carrier-managed identity (SIM)

**DoodleLabs MeshRider operates:**
- Disconnected tactical environments
- No cellular coverage
- Self-organizing mesh networks
- Cryptographic identity (no carrier)

**Our approach:** Keep the 3GPP floor control logic (which is excellent), replace the IMS transport with mesh-native P2P signaling.

---

## Compliance Summary

| Aspect | Compliance |
|--------|------------|
| Floor Control State Machine | TS 24.379/24.380 compliant |
| Priority/Preemption | Fully implemented |
| Arbitration | Distributed + Centralized |
| Media Codec | Opus (better than AMR-WB) |
| Transport | Multicast RTP (as specified) |
| Signaling | Custom (no SIP — mesh requires this) |
| Security | Ed25519/X25519 (exceeds IMS-AKA) |

---

## Conclusion

Mesh Rider Wave is a **mesh-optimized MCPTT client** that implements the critical 3GPP floor control logic without requiring IMS infrastructure. It is designed for tactical, off-network environments where DoodleLabs radios provide the transport layer.

---

**Contact:** Jabbir Basha P | DoodleLabs Singapore
