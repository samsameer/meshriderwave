# MeshRider Wave - MCPTT Gateway Vision

**Document Version:** 1.0
**Date:** January 27, 2026
**Author:** Jabbir Basha P
**Organization:** DoodleLabs Singapore

---

## Executive Summary

MeshRider Wave is not just a mesh PTT solution — it is a **bridge between tactical mesh networks and the global MCPTT ecosystem**. By embedding a gateway function directly into the app, MeshRider devices seamlessly integrate with any standard MCPTT network (AT&T, Verizon, Motorola, etc.) without requiring infrastructure changes.

**The Vision:** Every MeshRider device becomes a potential gateway. When connected to LTE, it blends into the MCPTT network. When operating in mesh-only mode, it routes traffic through any LTE-connected peer. The network self-organizes, routes intelligently, and **everyone just works**.

---

## The Problem

```
┌─────────────────────────────────────────────────────────────────────┐
│                     TODAY'S FRAGMENTED WORLD                         │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  LTE MCPTT Network              Mesh Network (Isolated)             │
│  ┌──────────────┐               ┌──────────────┐                   │
│  │ Motorola     │               │ MeshRider    │                   │
│  │ AT&T MCPTT   │   ◄───────►   │ Radios       │   ◄─────── NO     │
│  │ Devices      │   Cannot      │              │   Communication  │
│  └──────────────┘   Talk        └──────────────┘                   │
│                                                                      │
│  Police, Fire,     Tactical teams, Search & Rescue                  │
│  EMS, Military     operating beyond LTE coverage                    │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

**Issues:**
1. MCPTT devices work only where LTE exists
2. Mesh networks are isolated islands
3. No interoperability between ecosystems
4. Expensive infrastructure required to extend coverage
5. Emergency responders cannot coordinate across domain boundaries

---

## The Vision: Seamless Multi-Domain MCPTT

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         LTE MCPTT UNIVERSE                             │
│  (Motorola, AT&T, Verizon — Standard MCPTT Devices)                    │
└──────────────────────────────────────┬──────────────────────────────────┘
                                       │
                    ┌──────────────────┼──────────────────┐
                    │                  │                  │
               ┌────▼────┐        ┌────▼────┐        ┌────▼────┐
               │Gateway  │        │Gateway  │        │Gateway  │
               │Node A   │        │Node B   │        │Node C   │
               │LTE+Mesh │        │LTE+Mesh │        │LTE+Mesh │
               └────┬────┘        └────┬────┘        └────┬────┘
                    │                  │                  │
┌───────────────────┼──────────────────┼──────────────────┼───────────────┐
│                   │         NO LTE ZONE (MeshRider Mesh Network)        │
│                   │                  │                  │               │
│              ┌────┴────┐        ┌────┴────┐        ┌────┴────┐          │
│              │Mesh Only│        │Mesh Only│        │Mesh Only│          │
│              │Node D   │        │Node E   │        │Node F   │          │
│              └─────────┘        └─────────┘        └─────────┘          │
└─────────────────────────────────────────────────────────────────────────┘
```

**Key Principle:** The mesh network is an **extension** of the MCPTT network, not a separate system.

---

## Architecture

### The Gateway-in-the-App

```
┌─────────────────────────────────────────────────────────────────────────┐
│                      MESH RIDER WAVE (APP)                              │
│                                                                         │
│  ┌────────────────────┐              ┌────────────────────┐            │
│  │   LTE / IMS        │              │   MeshRider Mesh   │            │
│  │   (Standard MCPTT) │              │   (Proprietary)    │            │
│  │                    │              │                    │            │
│  │  • AT&T Client     │              │  • Beacon Disc.    │            │
│  │  • SIP/IMS Stack   │              │  • P2P Floor Ctrl  │            │
│  │  • RTCP Floor      │              │  • Multicast RTP   │            │
│  │  • AMR-WB Codec    │              │  • Opus Codec      │            │
│  └─────────┬──────────┘              └─────────┬──────────┘            │
│            │                                   │                        │
│            │          ┌───────────────┐       │                        │
│            └──────────►   GATEWAY     ◄───────┘                        │
│                        │  (Bridge)     │                               │
│                        └───────┬───────┘                               │
│                                │                                       │
│                        ┌───────▼───────┐                               │
│                        │  Mixer /      │                               │
│                        │  Router       │                               │
│                        └───────┬───────┘                               │
│                                │                                       │
│                        ┌───────▼───────┐                               │
│                        │  Codec        │                               │
│                        │  Transcoder   │                               │
│                        │  AMR-WB↔Opus  │                               │
│                        └───────────────┘                               │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

### How It Works

**Inside LTE Coverage:**
```
MeshRider Device → AT&T MCPTT Client → LTE/IMS → MCPTT Network
                      (Installed on device)
```
- Device runs the standard AT&T MCPTT client
- MeshRider app stays idle (or uses mesh for local groups)
- Zero distinction from any other MCPTT device

**Outside LTE Coverage:**
```
MeshRider Device → Mesh Network → Gateway Node (with LTE) → AT&T Client → MCPTT Network
```

**Multiple Gateway Nodes:**
- Any LTE-connected device can serve as gateway
- Automatic failover if one gateway loses LTE
- Load balancing across available gateways
- Self-healing network

---

## The ATAK Parallel

The architecture follows the proven ATAK (Android Tactical Assault Kit) pattern:

| ATAK | MeshRider Wave |
|------|----------------|
| ATAK Client | MeshRider App |
| CoT Protocol (Cursor on Target) | Mesh Protocol |
| TAK Server Plugin | MCPTT Gateway Module |
| Routes to CoT Network | Routes to AT&T MCPTT Client |
| Multiple plugin types | Multiple gateway targets |

```
ATAK Pattern:
┌──────────┐     ┌──────────┐     ┌──────────┐
│ ATAK     │────►│ Plugin   │────►│ CoT      │
│ Client   │     │(Gateway) │     │ Network  │
└──────────┘     └──────────┘     └──────────┘

MeshRider Pattern:
┌──────────┐     ┌──────────┐     ┌──────────┐
│MeshRider │────►│ Gateway  │────►│ AT&T     │
│ App      │     │ Module   │     │ MCPTT    │
└──────────┘     └──────────┘     └──────────┘
```

**Key Insight:** We don't need to implement SIP/IMS. The AT&T client handles that. We just route traffic to it.

---

## Routing Scenarios

### Scenario 1: LTE MCPTT User Speaks → Mesh Users Hear

```
┌──────────────┐
│ Police       │
│ Command      │  Presses PTT
│ (MCPTT)      │
└──────┬───────┘
       │
       │ LTE/IMS
       ▼
┌──────────────┐
│ AT&T MCPTT   │  Receives RTP (AMR-WB)
│ Network      │
└──────┬───────┘
       │
       │ RTP
       ▼
┌──────────────┐
│ Gateway Node │
│ (LTE+Mesh)   │  ┌─────────────────┐
│              │  │                 │
│              └─►│ Transcoder      │
│                 │ AMR-WB → Opus   │
│                 └────────┬────────┘
└──────────────────────────┼───────────┘
                            │
                            │ Mesh Multicast
                            ▼
                    ┌───────────────┐
                    │ MeshRider     │
                    │ Node D        │  Hears audio
                    │ (Mesh Only)   │
                    └───────────────┘
```

### Scenario 2: Mesh User Speaks → LTE MCPTT Users Hear

```
┌──────────────┐
│ MeshRider    │  Presses PTT
│ Node D       │
│ (Mesh Only)  │
└──────┬───────┘
       │
       │ Mesh Multicast (Opus)
       ▼
┌──────────────┐
│ Gateway Node │  ┌─────────────────┐
│ (LTE+Mesh)   │  │                 │
│              │  │ Transcoder      │
│              ◄─┤ Opus → AMR-WB    │
│                 └────────┬────────┘
└──────────────────────────┼───────────┘
                            │
                            │ RTP
                            ▼
┌──────────────┐
│ AT&T MCPTT   │
│ Network      │  Broadcasts to all MCPTT users
└──────┬───────┘
       │
       │ LTE/IMS
       ▼
┌──────────────┐
│ All MCPTT    │  Hear audio
│ Devices      │
└──────────────┘
```

### Scenario 3: Gateway Failover

```
Initial State:
┌──────────┐     ┌──────────┐     ┌──────────┐
│Gateway A │     │Gateway B │     │Gateway C │
│ LTE OK   │     │ LTE OK   │     │ LTE OK   │
└──────────┘     └──────────┘     └──────────┘
     │               │               │
     └───────┬───────┴───────┬───────┘
             │               │
         Mesh Network (Load Balanced)

Gateway A Loses LTE:
┌──────────┐     ┌──────────┐     ┌──────────┐
│Gateway A │     │Gateway B │     │Gateway C │
│ LTE DOWN │     │ LTE OK   │     │ LTE OK   │
└─────┬────┘     └──────┬────┘     └──────┬────┘
      │   (Failover)  │               │
      └───────────────┴───────────────┘
                  │
              Mesh Network
                  │
        Traffic rerouted to B and C
```

---

## Use Cases

### 1. Emergency Response

**Scenario:** Natural disaster with partial LTE coverage

| Role | Equipment | Network | Capability |
|------|-----------|---------|------------|
| Command Post | Motorola MCPTT | LTE | Full coverage |
| Search Team Alpha | MeshRider + Gateway | LTE + Mesh | Bridge |
| Search Team Beta | MeshRider (mesh only) | Mesh | Extended range |
| Medical Unit | MeshRider + Gateway | LTE + Mesh | Bridge |

**Outcome:** All teams communicate seamlessly despite coverage gaps.

### 2. Military Operations

**Scenario:** Forward operating base with extended patrol

- Base station: Full MCPTT infrastructure
- Patrol units: MeshRider radios (no LTE)
- Gateway unit: Vehicle with LTE modem
- Result: Patrol can coordinate with base via mesh → gateway → MCPTT

### 3. Public Safety

**Scenario:** Large event with temporary infrastructure

- Stadium: LTE MCPTT network
- Parking/Perimeter: MeshRider mesh
- Gateway: Fixed units at stadium exits
- Result: Seamless communication across entire venue

### 4. Industrial

**Scenario:** Remote facility with cellular dead zones

- Control room: LTE MCPTT
- Underground/Remote areas: MeshRider mesh
- Gateway: Surface repeaters
- Result: Full site coverage without infrastructure investment

---

## Technical Implementation

### Gateway Module Structure

```kotlin
/**
 * MCPTT Gateway - Bridges Mesh and MCPTT domains
 */
class MCPTTGateway {

    // =========================================================================
    // EXTERNAL INTERFACE (MCPTT Client Integration)
    // =========================================================================

    interface MCPTTClientCallback {
        fun onIncomingCall(callId: String, from: String)
        fun onFloorGranted(floorId: String)
        fun onFloorRevoked(reason: String)
        fun onRTPPacket(packet: ByteArray)
    }

    // =========================================================================
    // INTERNAL INTERFACE (Mesh Network)
    // =========================================================================

    interface MeshClientCallback {
        fun onIncomingMeshPacket(packet: MeshPacket)
        fun onMeshFloorRequest(userId: String, priority: Priority)
        fun onMeshAudio(audio: ByteArray, codec: Codec)
    }

    // =========================================================================
    // TRANSLATION LAYER
    // =========================================================================

    fun translateMCPTTToMesh(mcpttPacket: MCPTTPacket): MeshPacket
    fun translateMeshToMCPTT(meshPacket: MeshPacket): MCPTTPacket

    // =========================================================================
    // CODEC TRANSCODING
    // =========================================================================

    fun transcodeAMRWBToOpus(amr: ByteArray): ByteArray
    fun transcodeOpusToAMRWB(opus: ByteArray): ByteArray

    // =========================================================================
    // ROUTING LOGIC
    // =========================================================================

    fun selectGateway(): GatewayNode  // Intelligent selection
    fun handleFailover()              // Automatic failover
    fun balanceLoad()                 // Distribute traffic
}
```

### Codec Transcoding

| Codec | Bitrate | Quality | Use Case |
|-------|---------|---------|----------|
| AMR-WB | 23.85 kbps | Good | MCPTT standard |
| Opus | 12-24 kbps | Excellent | Mesh network |
| PCM | 256 kbps | Best | Legacy fallback |

**Transcoding Decision Tree:**
```
Source → Target | Action
----------------|--------
AMR-WB → Mesh   | AMR-WB → Opus (bandwidth savings)
Opus → MCPTT    | Opus → AMR-WB (compatibility)
Opus → Opus     | No transcoding (mesh-to-mesh)
```

---

## Gateway Selection Algorithm

```kotlin
fun selectBestGateway(availableGateways: List<GatewayNode>): GatewayNode {
    return availableGateways
        .filter { it.lteQuality > THRESHOLD }
        .maxByOrNull { gateway ->
            // Scoring factors:
            gateway.lteQuality * 0.4 +           // Signal strength
            gateway.loadFactor * 0.3 +            // Current load
            gateway.batteryLevel * 0.2 +          // Battery
            gateway.latency * 0.1                 // Network latency
        } ?: fallbackGateway()
}
```

**Factors:**
- LTE signal strength
- Current load (number of active calls)
- Battery level
- Network latency
- Historical reliability

---

## Security Considerations

### Domain Isolation

| Domain | Identity | Encryption | Key Management |
|--------|----------|------------|----------------|
| MCPTT | IMSI/SIP URI | IMS-AKA | Carrier HSS |
| Mesh | Ed25519 keypair | XSalsa20-Poly1305 | Distributed |

### Gateway Security

- **Identity Mapping:** Map Ed25519 keys to SIP URIs (one-to-one)
- **Key Derivation:** Channel keys derived from MCPTT group IDs
- **Replay Protection:** Sequence numbers across domains
- **No Key Exposure:** MCPTT keys never exposed to mesh domain

### Threat Model

| Threat | Mitigation |
|--------|------------|
| Gateway compromise | Multiple gateways, detection |
| Eavesdropping | End-to-end encryption in both domains |
| Man-in-the-middle | Signature verification both sides |
| Replay attack | Sequence numbers + timestamps |

---

## Benefits

### For End Users

| Benefit | Description |
|---------|-------------|
| Seamless Communication | No need to know which network others are on |
| Extended Coverage | Mesh extends MCPTT beyond LTE |
| No Behavior Change | Use PTT button same way always |
| Automatic Failover | No manual switching required |

### For Network Operators

| Benefit | Description |
|---------|-------------|
| No Infrastructure Changes | MeshRider is client-side only |
| No Standards Violation | Uses standard MCPTT client |
| Incremental Deployment | Add devices as needed |
| Coverage Extension | Reach areas infrastructure cannot |

### For System Integrators

| Benefit | Description |
|---------|-------------|
| Standards Compliant | Works with any MCPTT vendor |
| Vendor Agnostic | Not locked to single provider |
| Future Proof | Adapts to new MCPTT versions |
| Low Integration Cost | Gateway module is self-contained |

---

## Deployment Models

### Model 1: Mixed Fleet

```
Existing MCPTT devices → Continue using MCPTT client
MeshRider devices → Add gateway capability
Result: Unified communication network
```

### Model 2: Coverage Extension

```
Core MCPTT coverage → Standard infrastructure
Edge coverage → MeshRider with gateway
Result: Extended range without new towers
```

### Model 3: Disaster Recovery

```
Normal operation → MCPTT over LTE
Emergency/LTE down → MeshRider mesh + gateway fallback
Result: Always-on communication
```

---

## Roadmap

### Phase 1: Foundation (Current - 85% Complete)
- [x] Mesh PTT with floor control
- [x] Opus codec
- [x] Multicast RTP
- [x] Military-grade encryption
- [ ] Unit tests (80% target)

### Phase 2: Gateway Module
- [ ] MCPTT client integration
- [ ] Codec transcoder (AMR-WB ↔ Opus)
- [ ] Protocol translation layer
- [ ] Routing logic

### Phase 3: Multi-Gateway
- [ ] Gateway discovery
- [ ] Automatic failover
- [ ] Load balancing
- [ ] Health monitoring

### Phase 4: Production
- [ ] Field testing with real MCPTT networks
- [ ] Performance optimization
- [ ] Security audit
- [ ] Documentation

---

## Conclusion

MeshRider Wave is not just another PTT app. It is a **bridge between tactical mesh networks and the global MCPTT ecosystem**.

By embedding gateway functionality directly into the app, we enable:
- Seamless interoperability without infrastructure changes
- Extended coverage through self-organizing mesh networks
- Automatic failover and resilience
- Standards compliance without proprietary lock-in

**The vision:** Every MeshRider device is a potential gateway. The network self-organizes, routes intelligently, and everyone just works.

---

**Contact:** Jabbir Basha P | DoodleLabs Singapore
