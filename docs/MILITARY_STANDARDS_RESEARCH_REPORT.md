# Military Standards & Compliance Requirements for Tactical PTT Communications
## Comprehensive Research Report - MeshRider Wave

**Document Version:** 1.0  
**Date:** February 2026  
**Classification:** Research Report - Public Information  
**Prepared For:** MeshRider Wave Android Development Team  

---

## Executive Summary

This report provides deep research on military standards and compliance requirements for tactical Push-To-Talk (PTT) communications. MeshRider Wave, a 3GPP MCPTT-compliant PTT application using Opus codec, WebRTC, and E2E encryption, targets military B2B and FirstNet/public safety markets. This document covers the complete compliance landscape across seven critical domains.

---

## 1. 3GPP MISSION CRITICAL STANDARDS

### 1.1 MCPTT (Mission Critical Push-to-Talk)

#### Core Specifications
| Specification | Title | Purpose |
|--------------|-------|---------|
| TS 23.179 | Functional Architecture and Information Flows | Defines MCPTT service architecture |
| TS 24.379 | Mission Critical Push To Talk (MCPTT) call control | Call establishment, modification, release |
| TS 24.380 | Group state management | Group management, affiliation, authorization |
| TS 24.381 | Floor control protocol (TBCP) | Token-Based Floor Control Protocol |
| TS 24.382 | Signaling control | Signaling procedures for MCPTT |
| TS 24.383 | Media control | Media plane control procedures |
| TS 24.384 | Group management | Group configuration and management |
| TS 24.385 | UE-to-network relay | Proximity-based services |

#### MCPTT Key Features

**TBCP - Token-Based Floor Control Protocol:**
- Request-Granted-Release model
- Priority queuing mechanisms
- Override capabilities for emergency calls
- Maximum wait time indicators
- Floor denial reasons

**Priority and Preemption:**
| Priority Level | Description | Use Case |
|---------------|-------------|----------|
| 1 | Critical | Life-threatening emergency |
| 2 | High | Emergency response |
| 3 | Normal | Standard operational traffic |
| 4 | Low | Routine communications |

**Emergency Alerts:**
- **Imminent Peril Call**: Silent emergency indication
- **Ambient Listening**: Remote microphone activation
- **Emergency Alert**: One-button emergency broadcast
- **Lone Worker**: Automatic emergency trigger

**Group Management:**
- Dynamic group formation
- Persistent groups with offline membership
- Broadcast group calls
- Emergency group override

### 1.2 MCVideo (Mission Critical Video)

| Specification | Purpose |
|--------------|---------|
| TS 23.281 | MCVideo functional architecture |
| TS 24.581 | MCVideo call control |
| TS 24.582 | MCVideo media plane control |

### 1.3 MCData (Mission Critical Data)

| Specification | Purpose |
|--------------|---------|
| TS 23.282 | MCData functional architecture |
| TS 24.582 | MCData signaling |
| TS 24.583 | MCData payload delivery |

**MCData Services:**
- SDS (Short Data Service) - up to 10KB messages
- FD (File Distribution) - larger file transfers
- IP connectivity for data applications

---

## 2. SECURITY STANDARDS

### 2.1 FIPS 140-2 / FIPS 140-3

**Federal Information Processing Standards** for cryptographic modules.

#### FIPS 140-3 (Current Standard - March 2019)
| Security Level | Physical Security | Use Case |
|---------------|-------------------|----------|
| Level 1 | Production-grade components | Standard software implementations |
| Level 2 | Tamper-evident coatings/seals | Constrained environments |
| Level 3 | Strong enclosures, tamper-detection | Physical security required |
| Level 4 | Complete envelope protection | Unprotected environments |

**Implementation Requirements for MCPTT:**
- Minimum: FIPS 140-2 Level 1 or FIPS 140-3 Level 1
- Recommended: Level 2 for tactical deployments
- Hardware encryption modules preferred for Level 3+

**Approved Algorithms:**
- AES-256 (required for tactical communications)
- SHA-256/SHA-384 (hash functions)
- ECDSA P-256/P-384 (digital signatures)
- ECDH (key establishment)

**Validation Process:**
1. CMVP (Cryptographic Module Validation Program)
2. NIST-accredited testing laboratories
3. Certificate issuance for validated modules
4. Transition: All FIPS 140-2 validations move to Historical List Sept 21, 2026

### 2.2 Common Criteria (CC)

**ISO/IEC 15408** - International standard for security certification.

#### Evaluation Assurance Levels (EAL)
| Level | Description | Typical Use |
|-------|-------------|-------------|
| EAL1 | Functionally tested | Low security threats |
| EAL2 | Structurally tested | Low-to-moderate threats |
| EAL3 | Methodically tested | Moderate threats |
| EAL4 | Methodically designed | High threats, COTS products |
| EAL5 | Semiformally designed | High security, specialized |
| EAL6 | Semiformally verified | High risk, life protection |
| EAL7 | Formally verified | Extremely high risk |

**Recommendation for MCPTT:**
- Minimum: EAL2+ for commercial deployment
- Target: EAL4+ for military acceptance
- Method: NIAP-certified Common Criteria Testing Laboratory (CCTL)

### 2.3 NSA CSfC (Commercial Solutions for Classified)

**Two-layer encryption architecture** for classified communications:

```
┌─────────────────────────────────────┐
│       Outer Layer (VPN/IPsec)       │
│    Commercial/NSA-approved crypto   │
├─────────────────────────────────────┤
│       Inner Layer (Application)     │
│    NSA Suite B/Commercial crypto    │
├─────────────────────────────────────┤
│          Protected Data             │
└─────────────────────────────────────┘
```

**CSfC Components for MCPTT:**
| Component | Requirements |
|-----------|--------------|
| Data-in-Transit | IPsec/IKEv2 + SRTP double encryption |
| Data-at-Rest | AES-256-XTS for stored communications |
| Key Management | NSA Suite B PKI integration |

**Compliance Path:**
1. Use CSfC-approved components list
2. Implement layered encryption architecture
3. Obtain NSA-approved solution architect review
4. Submit for CSfC capability package approval

### 2.4 NIAP (National Information Assurance Partnership)

**US implementation of Common Criteria** for IT product security certification.

**NIAP Protection Profiles (PP) relevant to MCPTT:**
- **PP-MDF**: Mobile Device Fundamentals
- **PP-CONFIG**: VPN Client Extended Package
- **PP-MCPTT**: Mission Critical Push-to-Talk (emerging)

**Certification Process:**
1. Select appropriate Protection Profile
2. Vendor claims conformance
3. NIAP-approved lab evaluation
4. Validation and certificate issuance

### 2.5 STIGs (Security Technical Implementation Guides)

**DISA STIGs for mobile communications:**

| STIG | Applicability |
|------|--------------|
| STIG for Android OS | Device hardening |
| STIG for iOS | Device hardening |
| STIG for Voice/Video | Communication systems |
| STIG for Network Equipment | Infrastructure components |

**STIG Requirements:**
- Disable unnecessary services
- Enforce strong authentication
- Enable full device encryption
- Implement secure boot
- Regular security updates

---

## 3. PUBLIC SAFETY STANDARDS

### 3.1 FirstNet / Band 14 (700 MHz)

**First Responder Network Authority** - Nationwide public safety broadband network.

| Parameter | Specification |
|-----------|--------------|
| Frequency Band | 700 MHz (Band 14) |
| Uplink | 788-798 MHz |
| Downlink | 758-768 MHz |
| Channel Bandwidth | 5, 10, 15, 20 MHz |
| Technology | LTE/5G |

**MCPTT over FirstNet:**
- Priority and Preemption (P QoS)
- Guaranteed bandwidth for MCPTT
- QoS Class Identifier (QCI) 65 for MCPTT
- Isolated EPC for public safety

### 3.2 P25 (Project 25)

**Land Mobile Radio standard** for public safety communications.

#### P25 Phases
| Phase | Modulation | Bandwidth | Channels |
|-------|------------|-----------|----------|
| Phase I | C4FM (4FSK) | 12.5 kHz | 1 per channel |
| Phase II | TDMA | 12.5 kHz | 2 per channel |

#### P25 Open Interfaces
| Interface | Purpose |
|-----------|---------|
| CAI | Common Air Interface - radio interoperability |
| ISSI | Inter-RF Subsystem Interface - network roaming |
| CSSI | Console Subsystem Interface - dispatch consoles |
| KFI | Key Fill Interface - encryption key loading |

**Encryption Support:**
- DES-OFB (56-bit) - Deprecated
- 3DES (168-bit) - Legacy
- AES-256 (256-bit) - Current standard
- Type 1 ciphers (NSA approved)

**P25 CAP (Compliance Assessment Program):**
- Voluntary DHS program
- Testing at accredited laboratories
- Required for federal grant funding
- Equipment listed on Approved Equipment List

### 3.3 TETRA (Terrestrial Trunked Radio)

**European standard** for professional mobile radio.

| Parameter | Specification |
|-----------|--------------|
| Access Method | TDMA |
| Channels per Carrier | 4 |
| Channel Spacing | 25 kHz |
| Frequency Range | 380-470 MHz (varies by region) |
| Data Rate | 7.2 kbps per timeslot |

**TETRA Encryption:**
- TEA1: Basic level (commercial) - WEAKENED (32-bit exportable)
- TEA2: European public safety only
- TEA3: International public safety
- TEA4: Commercial use

**Security Note:** TEA1 was found to have intentional key reduction to 32 bits for export control - NOT suitable for tactical use.

### 3.4 NENA i3 (Next Generation 911)

**Standards for NG911 systems:**

| Standard | Purpose |
|----------|---------|
| NENA 08-002 | i3 Architecture |
| NENA 08-003 | i3 Functional Standards |
| NENA STA-010 | ESInet / ECRF standards |

**MCPTT Integration with NG911:**
- Location information (GIS coordinates)
- Emergency call routing
- Media anchoring
- Text-to-911 capabilities

---

## 4. NATO/COALITION STANDARDS

### 4.1 STANAG Standards

**NATO Standardization Agreements** for interoperability.

#### Relevant STANAGs for MCPTT:
| STANAG | Description |
|--------|-------------|
| STANAG 4203 | HF radio equipment standards |
| STANAG 4285 | HF modem characteristics |
| STANAG 4406 | Military message standard (X.400) |
| STANAG 5066 | HF data communications profile |
| STANAG 5516 | Link 16 - Tactical Data Link |
| STANAG 5518 | JREAP - Joint Range Extension Applications Protocol |
| STANAG 5602 | SIMPLE - Standard Interface for Military Platform Link Evaluation |
| STANAG 6016 | Link 16 MIL-STD equivalent |

### 4.2 FFTS (Friendly Force Tracking System)

**Coalition force tracking requirements:**
- Position reporting format
- Situational awareness data
- Integration with C2 systems

### 4.3 Link 16 (Tactical Data Link)

**NATO standard TDL** for military operations.

| Parameter | Specification |
|-----------|--------------|
| Frequency | 960-1215 MHz |
| Modulation | Spread spectrum |
| Data Rates | 31.6/57.6/115.2 kbps |
| Message Format | J-Series messages |

**Integration Considerations:**
- VMF messages over Link 16
- CoT integration
- Network participation groups

### 4.4 VMF (Variable Message Format)

**MIL-STD-6017** - Tactical military messaging.

**VMF K-Series Messages:**
- K01.1 - Free Text
- K02.9 - Obstacle
- K05.1 - Position Report
- K05.5 - Track Management
- K08.1 - Fire Mission

**Header Standard:** MIL-STD-2045-47001

---

## 5. INTEROPERABILITY STANDARDS

### 5.1 ATAK/CivTAK Integration

**Android Team Awareness Kit** - Situational awareness platform.

#### Versions:
| Version | Classification | Distribution |
|---------|---------------|--------------|
| ATAK-PR | Public Release | Downloadable |
| ATAK-CIV | Civilian | Google Play / Government sites |
| ATAK-GOV | Government | USG entities only |
| ATAK-MIL | Military | Military users only |

#### CoT (Cursor on Target) Protocol

**XML Schema for tactical data exchange:**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<event version="2.0" 
       uid="meshrider-ptt-001"
       type="b-m-p-s-p-loc" 
       time="2026-02-06T12:00:00Z"
       start="2026-02-06T12:00:00Z"
       stale="2026-02-06T12:05:00Z"
       how="h-e">
    <point lat="40.7128" lon="-74.0060" hae="10" ce="5" le="2"/>
    <detail>
        <status readiness="true"/>
        <contact callsign="MeshRider-1"/>
        <precisionlocation geopointsrc="GPS" altsrc="GPS"/>
    </detail>
</event>
```

**CoT Event Types:**
| Type | Description |
|------|-------------|
| a-f-G | Friendly air |
| a-h-G | Hostile air |
| b-m-p-s-p-loc | Personnel location |
| u-rb-c | Relay/bridge |

**TAK Server Integration:**
- TCP/SSL connections
- Certificate-based authentication
- Data package distribution
- Mission package management

### 5.2 MIL-STD-2525 (Joint Military Symbology)

**APP-6 (NATO)** / **MIL-STD-2525 (US)** - Common symbology standard.

**Symbol Components:**
- Frame (affiliation, battle dimension)
- Fill (color for affiliation)
- Icon (unit type/function)
- Modifiers (text fields)

**SIDC (Symbol Identification Coding):**
```
SFGPUCI----D---
│││││││││││││││
│││││││││││││└─ Status
││││││││││││└── Indicator
│││││││││││└─── Symbol 3
││││││││││└──── Symbol 2
│││││││││└───── Symbol 1
││││││││└────── Entity
│││││││└─────── Entity Type
││││││└──────── Entity Subtype
│││││└───────── Battle Dimension
││││└────────── Affiliation
│││└─────────── Order of Battle
││└──────────── Standard Identity
│└───────────── Coding Scheme
└────────────── Version
```

---

## 6. AUDIO REQUIREMENTS

### 6.1 Military Voice Quality Standards

#### STI (Speech Transmission Index)

**IEC 60268-16** - Objective rating of speech intelligibility.

| STI Value | Quality | Intelligibility |
|-----------|---------|----------------|
| 0.75-1.0 | Excellent | 96-100% |
| 0.60-0.75 | Good | 95-96% |
| 0.45-0.60 | Fair | 92-95% |
| 0.30-0.45 | Poor | 89-92% |
| 0-0.30 | Bad | <89% |

**Military Requirement:** Minimum STI 0.60 (Good) for tactical communications

#### Opus Codec Configuration for Tactical Use

| Parameter | Tactical Setting | Notes |
|-----------|-----------------|-------|
| Bitrate | 24-32 kbps | Balance quality vs. bandwidth |
| Sample Rate | 48 kHz | Full-band audio |
| Frame Size | 20 ms | Low latency |
| Mode | VoIP optimized | SILK/CELT hybrid |
| FEC | Enabled | Packet loss resilience |
| DTX | Disabled | Continuous transmission |

**Latency Requirements:**
| Metric | Target | Maximum |
|--------|--------|---------|
| Codec latency | <30 ms | <50 ms |
| Network latency | <100 ms | <300 ms |
| Total latency | <150 ms | <400 ms |
| PTT activation | <100 ms | <200 ms |
| Floor grant | <50 ms | <100 ms |

### 6.2 Hearing Protection Standards

**MIL-STD-1474** - Noise limits for military equipment.

| Parameter | Limit |
|-----------|-------|
| Peak SPL | 140 dB |
| Continuous exposure | 85 dB (8-hr TWA) |
| Impulse noise | Limit based on duration |

**MCPTT Implementation:**
- Automatic gain control (AGC)
- Peak limiting
- Volume normalization
- Emergency override with warning tone

### 6.3 Noise Reduction Requirements

**Environmental Considerations:**
- Battlefield noise (120+ dB)
- Vehicle interior noise (80-100 dB)
- Aircraft cabin noise (85-95 dB)

**Recommended Features:**
- Acoustic Echo Cancellation (AEC)
- Noise Suppression (NS)
- Automatic Gain Control (AGC)
- Voice Activity Detection (VAD)

---

## 7. ENVIRONMENTAL STANDARDS

### 7.1 MIL-STD-810 (Environmental Engineering)

**Test Method Standard for environmental resistance.**

Current Version: **MIL-STD-810H with Change Notice 1 (2022)**

#### Key Test Methods for MCPTT Devices:

| Test Method | Description | Severity Level |
|-------------|-------------|----------------|
| 500.6 | Low Pressure (Altitude) | Method 502 (15,000 ft) |
| 501.7 | High Temperature | +55°C to +71°C |
| 502.7 | Low Temperature | -25°C to -51°C |
| 503.7 | Temperature Shock | Rapid transition |
| 506.6 | Rain | Blowing rain, 40 mph |
| 507.6 | Humidity | 95% RH, cycles |
| 510.7 | Sand and Dust | Blowing dust/sand |
| 514.8 | Vibration | General transportation |
| 516.8 | Shock | Functional shock, drop |
| 521.4 | Icing/Freezing Rain | Ice accumulation |

#### Rugged Device Categories:
| Category | Use Case | Temp Range | IP Rating |
|----------|----------|------------|-----------|
| Handheld | Dismounted soldier | -20°C to +55°C | IP67 |
| Vehicle | Mounted systems | -40°C to +70°C | IP65 |
| Maritime | Shipboard | -25°C to +55°C | IP67 |
| Aviation | Aircraft | -55°C to +70°C | IP54 |

### 7.2 MIL-STD-461 (EMC)

**Electromagnetic compatibility requirements.**

Current Version: **MIL-STD-461G**

**Test Requirements:**
| Test | Description |
|------|-------------|
| CE101 | Conducted emissions, power leads |
| CE102 | Conducted emissions, antenna port |
| RE101 | Radiated emissions, magnetic field |
| RE102 | Radiated emissions, electric field |
| CS101 | Conducted susceptibility, power leads |
| CS114 | Bulk cable injection |
| CS115 | Bulk cable injection, impulse |
| CS116 | Damped sinusoidal transients |
| RS101 | Radiated susceptibility, magnetic field |
| RS103 | Radiated susceptibility, electric field |

**MCPTT EMC Considerations:**
- Radio emissions from device
- Susceptibility to jamming
- Co-site interference
- EMI shielding requirements

### 7.3 IP Ratings

**Ingress Protection ratings for environmental sealing:**

| Rating | Solid Protection | Liquid Protection |
|--------|-----------------|-------------------|
| IP54 | Dust limited | Water splashing |
| IP65 | Dust tight | Water jets |
| IP66 | Dust tight | Powerful water jets |
| IP67 | Dust tight | Temporary immersion |
| IP68 | Dust tight | Continuous immersion |

**Military Recommendation:** Minimum IP65, Target IP67

---

## COMPLIANCE ROADMAP

### Phase 1: Foundation (Months 1-6)

**Security:**
- [ ] Implement AES-256 encryption
- [ ] FIPS 140-2/140-3 validation preparation
- [ ] Security architecture documentation

**Standards:**
- [ ] 3GPP MCPTT protocol compliance
- [ ] TBCP floor control implementation
- [ ] Priority/preemption mechanisms

### Phase 2: Certification (Months 7-12)

**Security Certifications:**
- [ ] FIPS 140-3 Level 1 validation
- [ ] NIAP Common Criteria EAL2+ evaluation
- [ ] STIG compliance assessment

**Public Safety:**
- [ ] FirstNet Ready certification
- [ ] P25 ISSI integration
- [ ] NENA i3 compliance

### Phase 3: Advanced (Months 13-18)

**Military Integration:**
- [ ] NSA CSfC capability package
- [ ] ATAK plugin certification
- [ ] Common Criteria EAL4+

**Environmental:**
- [ ] MIL-STD-810H testing
- [ ] MIL-STD-461G certification
- [ ] IP67 certification

---

## CERTIFICATION REQUIREMENTS SUMMARY

| Certification | Authority | Timeline | Cost Estimate |
|--------------|-----------|----------|---------------|
| FIPS 140-3 Level 1 | NIST/CMVP | 12-18 months | $50K-$150K |
| Common Criteria EAL2+ | NIAP | 12-18 months | $100K-$300K |
| FirstNet Ready | AT&T/FirstNet | 6-9 months | $25K-$75K |
| P25 CAP | DHS/CISA | 6-12 months | $30K-$100K |
| MIL-STD-810H | ATEC | 3-6 months | $50K-$200K |
| MIL-STD-461G | ATEC | 3-6 months | $30K-$100K |
| CSfC | NSA | 12-24 months | $200K-$500K |
| STIG Compliance | DISA | 3-6 months | $20K-$50K |

---

## IMPLEMENTATION GUIDANCE

### Technical Architecture for Compliance

```
┌─────────────────────────────────────────────────────────────┐
│                    PRESENTATION LAYER                        │
│  (ATAK Plugin / MCPTT Client / FirstNet App)                │
└─────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────────────────────────────────────┐
│                    APPLICATION LAYER                         │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │   CoT/VMF    │  │  3GPP MCPTT   │  │    STANAG    │      │
│  │   Handler    │  │   Protocol    │  │   Interface  │      │
│  └──────────────┘  └──────────────┘  └──────────────┘      │
└─────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────────────────────────────────────┐
│                    SECURITY LAYER (CSfC)                     │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │   Outer VPN  │  │  Inner TLS   │  │  SRTP Crypto │      │
│  │  (IPsec/IKE) │  │   (1.3/1.2)  │  │  (AES-256)   │      │
│  └──────────────┘  └──────────────┘  └──────────────┘      │
└─────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────────────────────────────────────┐
│                     MEDIA LAYER                              │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │  Opus Codec  │  │     RTP      │  │    TBCP      │      │
│  │  (24 kbps)   │  │  (RFC 3550)  │  │Floor Control │      │
│  └──────────────┘  └──────────────┘  └──────────────┘      │
└─────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────────────────────────────────────┐
│                    TRANSPORT LAYER                           │
│  (UDP Multicast / FirstNet LTE / Tactical Radio)            │
└─────────────────────────────────────────────────────────────┘
```

### Key Implementation Decisions

1. **Encryption Strategy**: Implement CSfC double-layer with FIPS-validated modules
2. **Codec Configuration**: Opus at 24 kbps, 20ms frames, FEC enabled
3. **Protocol Stack**: Full 3GPP MCPTT with TBCP floor control
4. **Integration Points**: ATAK CoT, VMF K-Series, Link 16 via gateways
5. **Security Model**: Zero-trust with certificate-based authentication

---

## REFERENCES

### Standards Documents
1. 3GPP TS 24.379-24.385 - MCPTT Specifications
2. FIPS 140-3 - Security Requirements for Cryptographic Modules
3. ISO/IEC 15408 - Common Criteria for IT Security Evaluation
4. MIL-STD-810H - Environmental Engineering Considerations
5. MIL-STD-461G - Electromagnetic Compatibility Requirements
6. MIL-STD-2525D - Joint Military Symbology
7. STANAG 5516 - Link 16 Specification
8. IEC 60268-16 - Speech Transmission Index

### Certification Programs
- NIST CMVP: https://csrc.nist.gov/projects/cmvp
- NIAP CCEVS: https://www.niap-ccevs.org/
- FirstNet: https://www.firstnet.gov/
- DHS P25 CAP: https://www.dhs.gov/science-and-technology/p25-cap
- DISA STIGs: https://public.cyber.mil/stigs/

---

**Document End**

*This research report provides comprehensive guidance for achieving military-grade PTT certification. Regular updates are recommended as standards evolve.*
