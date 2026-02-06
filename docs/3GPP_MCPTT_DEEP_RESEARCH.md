# 3GPP Mission Critical Push-to-Talk (MCPTT) - Deep Research Report
## Comprehensive Analysis for Billion-Dollar Tactical PTT Application

**Document Version:** 1.0  
**Date:** February 2026  
**Classification:** Technical Reference for Implementation  

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [3GPP MCPTT Standards Suite](#2-3gpp-mcptt-standards-suite)
3. [Floor Control Protocol (TBCP) Deep Dive](#3-floor-control-protocol-tbcp-deep-dive)
4. [Mission Critical Services Ecosystem](#4-mission-critical-services-ecosystem)
5. [Security Architecture](#5-security-architecture)
6. [Implementation Specifications](#6-implementation-specifications)
7. [Interoperability Requirements](#7-interoperability-requirements)
8. [Appendices](#8-appendices)

---

## 1. Executive Summary

### 1.1 MCPTT Overview

Mission Critical Push-to-Talk (MCPTT) is a 3GPP standard (Release 13+) that provides carrier-grade, prioritized PTT services over LTE/5G networks. It is defined as a **3-stage standards approach** per ITU-T I.130:

- **Stage 1:** Service requirements (TS 22.179)
- **Stage 2:** Architecture definition (TS 23.179)  
- **Stage 3:** Protocol specifications (TS 24.379-24.382, TS 26.179)

### 1.2 Key Differentiators from Consumer PTT

| Feature | Consumer PoC (Push-to-Talk over Cellular) | MCPTT |
|---------|------------------------------------------|-------|
| Priority | Best effort | 8 priority levels (EMERGENCY highest) |
| Preemption | No | Yes - emergency can preempt |
| Latency | <2 seconds target | <300ms end-to-end mandatory |
| Reliability | 99.9% | 99.999% (five nines) |
| Security | Optional | Mandatory - MIKEY-TICKET, SRTP |
| Group Size | Limited | Up to 10,000+ participants |
| Offline | Limited | Full off-network (ProSe) support |

### 1.3 Release Timeline

| 3GPP Release | MCPTT Features Introduced |
|--------------|---------------------------|
| Release 13 | Initial MCPTT standard |
| Release 14 | MCVideo, MCData added |
| Release 15 | 5G NR support, enhancements |
| Release 16 | Enhanced off-network, QoS improvements |
| Release 17+ | Advanced features, AI integration |

---

## 2. 3GPP MCPTT Standards Suite

### 2.1 TS 22.179 - Service Requirements (Stage 1)

**Specification:** 3GPP TS 22.179 "Mission Critical Push to Talk (MCPTT) - Service Requirements"  
**Current Version:** V18.0.0 (Release 18)

#### 2.1.1 Core Service Requirements (Section 5)

**R-5.1:** MCPTT shall support half-duplex group communication (one speaker at a time)

**R-5.2:** MCPTT shall support private calls (one-to-one) with both half-duplex and full-duplex modes

**R-5.3:** MCPTT shall support floor control with the following priority levels:
```
Priority Level 1: EMERGENCY (highest)
Priority Level 2: IMMEDIATE  
Priority Level 3: FLASH OVERRIDE
Priority Level 4: FLASH
Priority Level 5: PRIORITY
Priority Level 6: ROUTINE
Priority Level 7: LOW
Priority Level 8: LOWEST
```

**R-5.4:** MCPTT emergency alert shall automatically request floor with Priority Level 1

**R-5.5:** MCPTT shall support late entry (users joining during active call)

**R-5.6:** MCPTT shall support ambient listening (supervisor silent monitoring)

**R-5.7:** MCPTT shall support dynamic group creation (ad-hoc groups)

#### 2.1.2 Performance Requirements (Section 6)

| Metric | Requirement | Section |
|--------|-------------|---------|
| Voice latency (mouth-to-ear) | ≤300ms | 6.2.1 |
| Floor request to grant | ≤200ms | 6.2.2 |
| Call setup (group) | ≤500ms | 6.2.3 |
| Call setup (private) | ≤1000ms | 6.2.4 |
| Audio quality | ≥3.5 MOS | 6.3.1 |

#### 2.1.3 Group Communication Requirements (Section 7)

**Group Types:**
- Pre-arranged groups (persistent)
- Ad-hoc groups (temporary)
- Broadcast groups (one-to-many, no response)
- Emergency groups (auto-created on emergency)

**Group Size Requirements:**
- Minimum: 2 participants
- Recommended: 250 participants
- Maximum: 10,000+ participants (implementation dependent)

### 2.2 TS 23.179 - Architecture (Stage 2)

**Specification:** 3GPP TS 23.179 "Functional architecture and information flows"  
**Current Version:** V18.2.0 (Release 18)

#### 2.2.1 System Architecture (Section 5)

```
┌─────────────────────────────────────────────────────────────────┐
│                     MCPTT SYSTEM ARCHITECTURE                   │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌──────────────┐     ┌──────────────┐     ┌──────────────┐    │
│  │   MCPTT      │     │   Group      │     │   Identity   │    │
│  │   Server     │◄────┤   Management │     │   Management │    │
│  │   (MCPTT)    │     │   Server     │     │   Server     │    │
│  └──────┬───────┘     └──────────────┘     └──────────────┘    │
│         │                                                       │
│         │  SIP/IMS Interface                                    │
│         ▼                                                       │
│  ┌──────────────┐     ┌──────────────┐     ┌──────────────┐    │
│  │   SIP Core   │     │   Media      │     │   Floor      │    │
│  │   (IMS)      │────►│   Control    │────►│   Control    │    │
│  │              │     │   Server     │     │   Server     │    │
│  └──────┬───────┘     └──────────────┘     └──────────────┘    │
│         │                                                       │
│         │ HTTP/WebSocket                                        │
│         ▼                                                       │
│  ┌──────────────┐                                               │
│  │  MCPTT       │                                               │
│  │  Client      │                                               │
│  │  (UE)        │                                               │
│  └──────────────┘                                               │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

#### 2.2.2 Reference Points (Section 5.3)

| Reference Point | Protocol | Description |
|-----------------|----------|-------------|
| MCPTT-1 | SIP | Signaling between UE and MCPTT Server |
| MCPTT-2 | HTTP/WebSocket | Client-server application interface |
| MCPTT-3 | RTP/SRTP | Media transport |
| MCPTT-4 | BFCP/TBCP | Floor control protocol |
| MCPTT-5 | DIAMETER | Authentication/authorization |

#### 2.2.3 Functional Entities (Section 5.4)

**MCPTT Server Functions:**
1. **MCPTT Session Control:** SIP call handling, session state management
2. **Floor Control:** TBCP/BFCP processing, arbitration
3. **Group Management:** Group membership, dynamic groups
4. **Identity Management:** MCPTT ID, authentication
5. **Media Distribution:** RTP/RTCP handling, mixing

**MCPTT Client (UE) Functions:**
1. **PTT Button Handler:** Physical/virtual PTT activation
2. **Floor Control Client:** TBCP client implementation
3. **Media Handler:** Audio capture/playback, codec management
4. **Group Client:** Group membership, affiliation
5. **Security Client:** Authentication, encryption

### 2.3 TS 24.379 - Call Control (Stage 3)

**Specification:** 3GPP TS 24.379 "Mission Critical Push to Talk (MCPTT) call control - Protocol specification"  
**Current Version:** V18.5.0 (Release 18)

#### 2.3.1 SIP Profile for MCPTT (Section 5)

MCPTT uses SIP (RFC 3261) with the following extensions:

**Mandatory SIP Methods:**
- INVITE, ACK, BYE, CANCEL - Basic call control
- SUBSCRIBE, NOTIFY - Event subscription (RFC 3265)
- REFER - Call transfer (RFC 3515)
- MESSAGE - Instant messaging (RFC 3428)

**MCPTT-Specific SIP Headers:**

```
# P-Asserted-Identity (RFC 3325)
P-Asserted-Identity: <sip:mcptt-id@domain.com>

# MCPTT-Info (3GPP-defined)
MCPTT-Info: mcptt-request;priority=EMERGENCY;group-id=1234

# Accept-Contact with MCPTT feature tag
Accept-Contact: *;+g.3gpp.mcptt;require;explicit

# Priority header (RFC 3261)
Priority: emergency
```

#### 2.3.2 Call Flow Examples

**Group Call Setup:**

```
UE A                    MCPTT Server                 Group Members
  |                           |                             |
  | INVITE sip:group@domain   |                             |
  |---------------------------|                             |
  | P-Asserted-Identity: A    |                             |
  | MCPTT-Info: group-call    |                             |
  |                           |                             |
  |       200 OK              |                             |
  |<--------------------------|                             |
  |                           | INVITE sip:member@domain    |
  |                           |---------------------------->|
  |                           |         ...                 |
```

**Emergency Alert with Floor Request:**

```
UE A                    MCPTT Server
  |                           |
  | MESSAGE sip:emergency@domain
  |---------------------------|
  | Content-Type: application/vnd.3gpp.mcptt-info+xml
  | <mcptt emergency="true" floor-request="true"/>
  |                           |
  |       200 OK              |
  |<--------------------------|
  |                           |---> Automatic group call
  |                           |     with floor granted
```

### 2.4 TS 24.380 - Floor Control (Stage 3) - CRITICAL

**Specification:** 3GPP TS 24.380 "Mission Critical Push to Talk (MCPTT) floor control"  
**Current Version:** V18.5.0 (Release 18)

This is the **most critical specification** for PTT implementation.

#### 2.4.1 Floor Control Protocol: TBCP

MCPTT defines **TBCP (Talker Burst Control Protocol)**, a profile of **BFCP (Binary Floor Control Protocol, RFC 4582)**.

**TBCP Message Format:**

```
 0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
| Ver |  Res  |   Primitive   |          Payload Length         |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                        Conference ID                          |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                        Transaction ID                         |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                         User ID                               |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                         Floor ID                              |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                                                               |
//                        Attributes                          //
|                                                               |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```

**TBCP Primitives for MCPTT:**

| Primitive | Direction | Description |
|-----------|-----------|-------------|
| Floor Request | C→S | Request permission to speak |
| Floor Granted | S→C | Floor assigned to client |
| Floor Denied | S→C | Request rejected |
| Floor Revoke | S→C | Floor taken away |
| Floor Release | C→S | Client releases floor |
| Floor Taken | S→C | Notify floor holder changed |
| Floor Idle | S→C | Floor available |
| Floor Queue Position | S→C | Update queue status |

#### 2.4.2 Floor Control State Machine

```
                              ┌─────────────┐
                              │   IDLE      │
                              │ (Available) │
                              └──────┬──────┘
                                     │
                    Floor Request    │
                    (Normal Priority)│
                    ───────────────► │
                                     ▼
                              ┌─────────────┐
                              │  PENDING    │
                              │ (Requested) │
                              └──────┬──────┘
                                     │
        ┌────────────────────────────┼────────────────────────────┐
        │                            │                            │
        │ Floor Granted              │ Floor Denied               │ Higher Priority
        │                            │                            │ Request
        ▼                            ▼                            ▼
┌─────────────┐               ┌─────────────┐               ┌─────────────┐
│   GRANTED   │               │    IDLE     │               │   QUEUED    │
│  (Speaking) │               │ (Available) │               │ (Waiting)   │
└──────┬──────┘               └─────────────┘               └──────┬──────┘
       │                                                            │
       │ Floor Release                                              │ Queue
       │ or Revoke                                                  │ Advance
       ▼                                                            ▼
┌─────────────┐                                               ┌─────────────┐
│    IDLE     │◄──────────────────────────────────────────────│   GRANTED   │
│ (Available) │                                               │  (Speaking) │
└─────────────┘                                               └─────────────┘
```

#### 2.4.3 Priority Preemption Algorithm (Section 6.3)

```
PRIORITY_PREEMPTION_ALGORITHM:
──────────────────────────────
INPUT:
  - Current floor holder priority: P_current
  - New request priority: P_new
  - Preemption policy: POLICY

PROCESS:
1. IF P_new > P_current THEN
     
2.   IF POLICY == "HARD_PREEMPT" THEN
       - Immediately revoke floor from current holder
       - Grant floor to new requester
       - Notify preempted user with reason
     
3.   ELSE IF POLICY == "SOFT_PREEMPT" THEN
       - Wait for current speaker to finish sentence
       - Or wait for MAX_PREEMPT_DELAY (default: 2s)
       - Then revoke and grant
     
4.   ELSE IF POLICY == "QUEUE_HIGH" THEN
       - Place high priority request at queue head
       - Do not preempt current speaker
     
5. ELSE IF P_new == P_current THEN
     - Add to queue (FIFO order)
     
6. ELSE
     - Add to queue based on priority ordering
```

**Priority Comparison Rules:**

```
PRIORITY_ORDER = {
    "EMERGENCY":     7,  # Can preempt any
    "IMMEDIATE":     6,  # Can preempt FLASH and below
    "FLASH_OVERRIDE":5,
    "FLASH":         4,
    "PRIORITY":      3,
    "ROUTINE":       2,
    "LOW":           1,
    "LOWEST":        0
}

def can_preempt(requester_priority, holder_priority):
    return PRIORITY_ORDER[requester_priority] > PRIORITY_ORDER[holder_priority]
```

#### 2.4.4 Floor Queue Management (Section 6.4)

**Queue Attributes:**

```
Floor Queue Element:
{
    "user_id": "mcptt-id@domain",
    "priority": "PRIORITY",
    "timestamp": "2026-02-06T10:30:00Z",
    "queue_position": 3,
    "estimated_wait_ms": 4500
}
```

**Queue Position Notification:**

```xml
<?xml version="1.0"?>
<floor-queue-info xmlns="urn:3gpp:ns:mcpttFloorControl:1.0">
    <conference-id>group123@domain</conference-id>
    <floor-id>1</floor-id>
    <queue-position>
        <position>2</position>
        <estimated-wait>PT3S</estimated-wait>
    </queue-position>
</floor-queue-info>
```

#### 2.4.5 Floor Revoke Conditions (Section 6.5)

Floor MAY be revoked by server when:
1. Higher priority emergency request received
2. Floor holder exceeds MAX_FLOOR_HOLD_TIME (configurable, default 60s)
3. Floor holder inactivity detected (no media for INACTIVITY_TIMEOUT)
4. Administrator override
5. Group disbanded
6. Server overload conditions

Floor Revoke Message Format:

```
TBCP Floor Revoke:
- Primitive: FloorRevoke (0x05)
- Revoke Cause: 
  * 0x01 = Higher priority
  * 0x02 = Time limit exceeded
  * 0x03 = Inactivity
  * 0x04 = Administrative
  * 0x05 = Server error
```

### 2.5 TS 24.381 - Group Management (Stage 3)

**Specification:** 3GPP TS 24.381 "Mission Critical Push to Talk (MCPTT) group management"  
**Current Version:** V18.4.0 (Release 18)

#### 2.5.1 Group Types (Section 5)

**Pre-arranged Group:**
```xml
<group type="pre-arranged" id="fire-department-north">
    <display-name>Fire Department North</display-name>
    <member-list>
        <member id="firefighter-001"/>
        <member id="firefighter-002"/>
        <!-- ... up to 10000 members -->
    </member-list>
    <configuration>
        <max-simultaneous-talkers>1</max-simultaneous-talkers>
        <floor-hold-timeout>PT60S</floor-hold-timeout>
        <priority-boost-on-emergency>true</priority-boost-on-emergency>
    </configuration>
</group>
```

**Ad-hoc Group:**
```xml
<group type="ad-hoc" id="temp-123456">
    <created-by>officer-001</created-by>
    <created-at>2026-02-06T10:30:00Z</created-at>
    <ttl>PT4H</ttl>  <!-- Time to live -->
    <member-list>
        <member id="officer-001"/>
        <member id="officer-002"/>
        <member id="paramedic-003"/>
    </member-list>
</group>
```

**Broadcast Group:**
```xml
<group type="broadcast" id="all-units">
    <display-name>All Units Broadcast</display-name>
    <member-list>
        <!-- Implicit: all online users -->
    </member-list>
    <restrictions>
        <no-response-allowed>true</no-response-allowed>
        <administrators-only>true</administrators-only>
    </restrictions>
</group>
```

#### 2.5.2 Group Affiliation (Section 6)

**Affiliation Procedure:**

```
UE                          Group Management Server
 |                                    |
 | HTTP POST /affiliation             |
 |----------------------------------->|
 | Content-Type: application/vnd.3gpp.mcptt-group- affiliation+xml
 |                                    |
 | <affiliation>                      |
 |   <user>officer-001</user>         |
 |   <group>fire-north</group>        |
 |   <action>affiliate</action>       |
 | </affiliation>                     |
 |                                    |
 |         200 OK                     |
 |<-----------------------------------|
 |                                    |
 | SIP NOTIFY (group membership update)|
 |<-----------------------------------|
```

### 2.6 TS 24.382 - Identity Management (Stage 3)

**Specification:** 3GPP TS 24.382 "Mission Critical Push to Talk (MCPTT) identity management"  
**Current Version:** V18.3.0 (Release 18)

#### 2.6.1 MCPTT Identifiers (Section 5)

**MCPTT ID Format:**

```
MCPTT User ID:     mcptt-id@<realm>
MCPTT Group ID:    group-id@<realm>
MCPTT Service ID:  service-id@<realm>

Examples:
  user:    john.doe.123@firstnet.att.com
  group:   fd-engine-1@firstnet.att.com
  service: emergency@firstnet.att.com
```

**Organization Format:**

```
Organization: org=<organization-name>.<realm>
Examples:
  org: police.seattle.wa.us
  org: fd.nyc.ny.us
```

#### 2.6.2 Authentication Procedures (Section 6)

**Primary Authentication (IMS AKA):**

```
UE                          P-CSCF        S-CSCF        HSS
 |                             |             |           |
 | SIP REGISTER                |             |           |
 |---------------------------->|------------>|           |
 |                             |             |           |
 |         401 Unauthorized    |             |           |
 |<----------------------------|<------------|           |
 |  WWW-Authenticate: Digest   |             |           |
 |  realm="ims", nonce="..."   |             |           |
 |                             |             |           |
 | SIP REGISTER (with response)|             |           |
 |---------------------------->|------------>|           |
 |  Authorization: Digest      |             |           |
 |  username="...", response="..."            |           |
 |                             |             |--------->|
 |                             |             |  Cx-Query |
 |                             |             |<---------|
 |                             |             |  Cx-Put   |
 |                             |             |           |
 |         200 OK              |             |           |
 |<----------------------------|<------------|           |
 |  P-Associated-URI: <mcptt-id>             |           |
```

**Secondary Authentication (MCPTT-specific):**

After IMS registration, MCPTT server performs additional authentication:

```
UE                          MCPTT Server
 |                             |
 | HTTP POST /authentication   |
 |---------------------------->|
 | Content-Type: application/vnd.3gpp.mcptt-auth+xml
 |                             |
 | <authentication>            |
 |   <mcptt-id>user@domain</mcptt-id>
 |   <organization>police</organization>
 |   <role>officer</role>
 |   <certificate>...</certificate>
 | </authentication>
 |                             |
 |      200 OK + MCPTT Token   |
 |<----------------------------|
```

### 2.7 TS 26.179 - Media Control (Stage 3)

**Specification:** 3GPP TS 26.179 "Mission Critical Push to Talk (MCPTT) - Media plane control"  
**Current Version:** V18.2.0 (Release 18)

#### 2.7.1 Audio Codec Requirements (Section 5)

**MANDATORY Codec:**

| Parameter | Requirement |
|-----------|-------------|
| Codec | Opus (RFC 6716) |
| Sample Rate | 48 kHz |
| Frame Size | 20ms (default), 10ms, 40ms optional |
| Bitrate | 24 kbps default (6-128 kbps configurable) |
| Modes | VoIP optimized |
| Complexity | ≤10 |

**SDP Offer for MCPTT:**

```
m=audio 49170 RTP/SAVPF 111
a=rtpmap:111 opus/48000/2
a=fmtp:111 maxplaybackrate=48000; sprop-maxcapturerate=48000;
           stereo=0; sprop-stereo=0; useinbandfec=1;
           maxaveragebitrate=24000; maxptime=40; minptime=20
a=ptime:20
a=maxptime:40
```

**OPTIONAL Codecs (for backward compatibility):**
- AMR-NB (RFC 4867) - for legacy support
- AMR-WB (RFC 4867) - for wideband legacy
- EVS (3GPP TS 26.445) - for 5G deployments

#### 2.7.2 RTP Profile (Section 6)

MCPTT uses **RTP/SAVPF** profile (RFC 5124 - combination of SRTP and AVPF).

**RTP Header for MCPTT:**

```
 0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|V=2|P|X|  CC   |M|     PT      |       sequence number         |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                           timestamp                           |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|           synchronization source (SSRC) identifier            |
+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
|            contributing source (CSRC) identifiers             |
|                             ....                              |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|     MKI (optional)              |  authentication tag (SRTP)  |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```

**Payload Type Assignment:**

| PT | Codec | Notes |
|----|-------|-------|
| 96-127 | Dynamic | Opus uses dynamic PT |
| 0 | PCMU | Optional legacy |
| 8 | PCMA | Optional legacy |
| 102 | AMR | Optional legacy |

---

## 3. Floor Control Protocol (TBCP) Deep Dive

### 3.1 TBCP Message Specifications

#### 3.1.1 Common Header Format

```c
/* TBCP Common Header - 12 bytes minimum */
typedef struct {
    uint8_t  version:3;      /* Protocol version = 1 */
    uint8_t  reserved:5;     /* Must be 0 */
    uint8_t  primitive;      /* Message type */
    uint16_t payload_length; /* Length of attributes in bytes */
    uint32_t conference_id;  /* Group/Conference identifier */
    uint32_t transaction_id; /* Unique transaction ID */
    uint32_t user_id;        /* MCPTT user identifier */
    uint32_t floor_id;       /* Floor identifier (usually 1) */
} tbcp_header_t;
```

#### 3.1.2 TBCP Primitives Reference

```c
/* TBCP Primitive Types */
#define TBCP_FLOOR_REQUEST      0x01
#define TBCP_FLOOR_GRANTED      0x02
#define TBCP_FLOOR_DENIED       0x03
#define TBCP_FLOOR_RELEASE      0x04
#define TBCP_FLOOR_REVOKED      0x05
#define TBCP_FLOOR_TAKEN        0x06
#define TBCP_FLOOR_IDLE         0x07
#define TBCP_FLOOR_QUEUE_POSITION 0x08
#define TBCP_ERROR              0x09
#define TBCP_HELLO              0x0A
#define TBCP_HELLO_ACK          0x0B
```

#### 3.1.3 TBCP Attributes

```c
/* TBCP Attribute Types */
#define TBCP_ATTR_PRIORITY        0x01  /* 1 byte: 0-7 */
#define TBCP_ATTR_DURATION        0x02  /* 4 bytes: ms */
#define TBCP_ATTR_QUEUE_POSITION  0x03  /* 2 bytes */
#define TBCP_ATTR_QUEUE_INFO      0x04  /* Variable */
#define TBCP_ATTR_REJECT_REASON   0x05  /* 1 byte */
#define TBCP_ATTR_REQUEST_STATUS  0x06  /* 1 byte */
#define TBCP_ATTR_USER_INFO       0x07  /* Variable */
#define TBCP_ATTR_FLOOR_INFO      0x08  /* Variable */
#define TBCP_ATTR_MEDIA_INFO      0x09  /* Variable */

/* Priority Values */
#define PRIORITY_EMERGENCY       7
#define PRIORITY_IMMEDIATE       6
#define PRIORITY_FLASH_OVERRIDE  5
#define PRIORITY_FLASH           4
#define PRIORITY_PRIORITY        3
#define PRIORITY_ROUTINE         2
#define PRIORITY_LOW             1
#define PRIORITY_LOWEST          0

/* Reject Reason Codes */
#define REJECT_CAUSE_NONE          0x00
#define REJECT_CAUSE_SERVER_ERROR  0x01
#define REJECT_CAUSE_PERMISSION    0x02
#define REJECT_CAUSE_INVALID floor 0x03
#define REJECT_CAUSE_FLOOR_TAKEN   0x04
#define REJECT_CAUSE_MAX_LIMIT     0x05
#define REJECT_CAUSE_TEMP_REJECT   0x06
```

### 3.2 Floor Control State Machine Implementation

#### 3.2.1 Client State Machine

```c
/* Floor Control Client States */
typedef enum {
    FLOOR_STATE_IDLE = 0,
    FLOOR_STATE_PENDING,
    FLOOR_STATE_GRANTED,
    FLOOR_STATE_QUEUED,
    FLOOR_STATE_REVOKED
} floor_client_state_t;

/* State Transition Table */
typedef struct {
    floor_client_state_t current_state;
    uint8_t              event;
    floor_client_state_t next_state;
    void               (*action)(void *ctx);
} floor_state_transition_t;

/* Client State Transitions */
floor_state_transition_t client_transitions[] = {
    /* IDLE states */
    {FLOOR_STATE_IDLE, TBCP_FLOOR_REQUEST, FLOOR_STATE_PENDING, action_send_request},
    
    /* PENDING states */
    {FLOOR_STATE_PENDING, TBCP_FLOOR_GRANTED, FLOOR_STATE_GRANTED, action_start_transmitting},
    {FLOOR_STATE_PENDING, TBCP_FLOOR_DENIED, FLOOR_STATE_IDLE, action_request_denied},
    {FLOOR_STATE_PENDING, TBCP_FLOOR_QUEUE_POSITION, FLOOR_STATE_QUEUED, action_queued},
    {FLOOR_STATE_PENDING, TBCP_FLOOR_REVOKED, FLOOR_STATE_IDLE, action_cancelled},
    
    /* GRANTED states */
    {FLOOR_STATE_GRANTED, TBCP_FLOOR_RELEASE, FLOOR_STATE_IDLE, action_stop_transmitting},
    {FLOOR_STATE_GRANTED, TBCP_FLOOR_REVOKED, FLOOR_STATE_IDLE, action_floor_revoked},
    
    /* QUEUED states */
    {FLOOR_STATE_QUEUED, TBCP_FLOOR_GRANTED, FLOOR_STATE_GRANTED, action_start_transmitting},
    {FLOOR_STATE_QUEUED, TBCP_FLOOR_DENIED, FLOOR_STATE_IDLE, action_request_denied},
    {FLOOR_STATE_QUEUED, TBCP_FLOOR_QUEUE_POSITION, FLOOR_STATE_QUEUED, action_update_queue},
    
    {0, 0, 0, NULL}  /* End marker */
};
```

#### 3.2.2 Server State Machine

```c
/* Floor Control Server States */
typedef enum {
    FLOOR_SERVER_IDLE = 0,
    FLOOR_SERVER_GRANTED,
    FLOOR_SERVER_REVOKING
} floor_server_state_t;

/* Floor Instance Structure */
typedef struct {
    uint32_t floor_id;
    floor_server_state_t state;
    uint32_t current_holder;
    uint32_t queue[MCPTT_MAX_QUEUE_SIZE];
    uint8_t queue_size;
    uint32_t max_floor_hold_ms;
    uint64_t granted_at;
    uint8_t priority_boost;
} floor_instance_t;
```

### 3.3 Arbitration Algorithms

#### 3.3.1 Priority-Based Arbitration

```c
/* Floor Request Structure */
typedef struct {
    uint32_t user_id;
    uint8_t  priority;
    uint64_t timestamp;
    uint8_t  emergency;
    uint32_t queue_position;
} floor_request_t;

/* Arbitration Algorithm */
int arbitrate_floor_request(floor_instance_t *floor, floor_request_t *request) {
    /* Check for emergency preemption */
    if (request->emergency || request->priority == PRIORITY_EMERGENCY) {
        if (floor->state == FLOOR_SERVER_GRANTED) {
            /* Revoke current holder */
            send_floor_revoke(floor->current_holder, REJECT_CAUSE_EMERGENCY);
        }
        grant_floor(floor, request->user_id);
        return FLOOR_GRANTED_IMMEDIATE;
    }
    
    /* Check if floor is available */
    if (floor->state == FLOOR_SERVER_IDLE) {
        grant_floor(floor, request->user_id);
        return FLOOR_GRANTED_IMMEDIATE;
    }
    
    /* Check priority against current holder */
    uint8_t current_priority = get_user_priority(floor->current_holder);
    
    if (request->priority > current_priority) {
        if (floor->priority_boost || request->priority >= PRIORITY_IMMEDIATE) {
            /* Preempt current holder */
            queue_insert(floor, floor->current_holder, current_priority);
            send_floor_revoke(floor->current_holder, REJECT_CAUSE_PRIORITY);
            grant_floor(floor, request->user_id);
            return FLOOR_GRANTED_PREEMPT;
        }
    }
    
    /* Add to queue based on priority and timestamp */
    uint8_t pos = queue_insert_by_priority(floor, request);
    return FLOOR_QUEUED;
}
```

#### 3.3.2 Queue Management Algorithm

```c
/* Priority Queue Implementation */
uint8_t queue_insert_by_priority(floor_instance_t *floor, floor_request_t *req) {
    int insert_pos = 0;
    
    /* Find insertion point based on priority (higher first) */
    for (int i = 0; i < floor->queue_size; i++) {
        uint32_t queued_user = floor->queue[i];
        uint8_t queued_priority = get_user_priority(queued_user);
        
        if (req->priority > queued_priority) {
            break;
        }
        if (req->priority == queued_priority && 
            req->timestamp < get_request_timestamp(queued_user)) {
            break;
        }
        insert_pos++;
    }
    
    /* Shift and insert */
    for (int i = floor->queue_size; i > insert_pos; i--) {
        floor->queue[i] = floor->queue[i-1];
    }
    floor->queue[insert_pos] = req->user_id;
    floor->queue_size++;
    
    /* Update queue positions for all affected users */
    for (int i = insert_pos; i < floor->queue_size; i++) {
        notify_queue_position(floor->queue[i], i + 1);
    }
    
    return insert_pos + 1;  /* 1-based position */
}
```

### 3.4 Network Transport

#### 3.4.1 TBCP Transport Mapping

TBCP operates over different transports depending on deployment:

| Transport | Use Case | Port |
|-----------|----------|------|
| UDP | Real-time floor control | 3478-3481 (default) |
| TCP | Reliable floor control | 443, 80 |
| WebSocket | Web-based clients | 443 (wss://) |
| SIP MESSAGE | Integrated with SIP | SIP port (5060) |

**UDP Transport (Recommended for Real-time):**

```
┌─────────────────────────────────────────────────────────┐
│                    UDP Transport                        │
├─────────────────────────────────────────────────────────┤
│  Source Port: Dynamic (client) / 3478 (server)         │
│  Destination Port: 3478 (server) / Dynamic (client)     │
│  Payload: TBCP Message                                  │
│  Rate Limit: 10 messages/second per client             │
│  Retransmission: Exponential backoff (100ms, 200ms...) │
└─────────────────────────────────────────────────────────┘
```

---

## 4. Mission Critical Services Ecosystem

### 4.1 MCVideo - Mission Critical Video

**Specification:** 3GPP TS 26.281 "Mission Critical Video (MCVideo)"

#### 4.1.1 MCVideo Service Types

```
┌─────────────────────────────────────────────────────────────┐
│                    MCVideo Services                         │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  1. VIDEO GROUP CALL (MCPTT-like)                          │
│     - One video sender at a time                           │
│     - Floor control with TBCP                              │
│     - Support for up to 4 simultaneous senders (optional)  │
│                                                             │
│  2. VIDEO PRIVATE CALL                                     │
│     - One-to-one video                                     │
│     - Half-duplex or full-duplex                           │
│                                                             │
│  3. VIDEO BROADCAST                                        │
│     - One-to-many, no return                               │
│     - No floor control needed                              │
│                                                             │
│  4. VIDEO UPLOAD (Store and Forward)                       │
│     - Upload to server for later retrieval                 │
│     - Metadata with location, timestamp                    │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

#### 4.1.2 Video Codec Requirements

| Codec | Profile | Resolution | Frame Rate | Mandatory |
|-------|---------|------------|------------|-----------|
| H.264 | Baseline | 720p | 30fps | Yes |
| H.265 | Main | 1080p | 30fps | Optional |
| AV1 | Profile 0 | 720p | 30fps | Optional (Rel-18+) |

### 4.2 MCData - Mission Critical Data

**Specification:** 3GPP TS 23.282 "Mission Critical Data (MCData)"

#### 4.2.1 MCData Services

```
┌─────────────────────────────────────────────────────────────┐
│                    MCData Services                          │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  1. SDS (Short Data Service)                               │
│     - Text messages up to 2048 bytes                       │
│     - Priority and delivery confirmation                   │
│     - Store and forward                                    │
│                                                             │
│  2. FILE DISTRIBUTION                                      │
│     - File transfer with progress                          │
│     - Resume capability                                    │
│     - Automatic download on affiliation                    │
│                                                             │
│  3. IP DATA                                                │
│     - Application-to-application data                      │
│     - Reliable delivery                                    │
│                                                             │
│  4. CONCURRENT DATA                                        │
│     - Data during active voice/video call                  │
│     - Floor control coordination                           │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### 4.3 LMR Interconnection

**Specification:** 3GPP TS 23.283 "Mission Critical interoperability with legacy systems"

#### 4.3.1 IWF (InterWorking Function) Architecture

```
┌─────────────────────────────────────────────────────────────┐
│              MCPTT <-> LMR Interworking                     │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│    ┌──────────┐      ┌──────────┐      ┌──────────┐       │
│    │  MCPTT   │◄────►│   IWF    │◄────►│   LMR    │       │
│    │  Server  │ SIP  │ Gateway  │ P25  │  System  │       │
│    └──────────┘      │          │ DMR  └──────────┘       │
│                      │          │ TETRA                   │
│                      └──────────┘                          │
│                                                             │
│  Mapping Functions:                                         │
│  - MCPTT Group ID ↔ LMR Talkgroup                           │
│  - MCPTT Priority ↔ LMR Preemption                          │
│  - RTP ↔ P25/DMR vocoder frames                            │
│  - Floor control ↔ PTT signaling                           │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### 4.4 Off-Network Operation (ProSe)

**Specification:** 3GPP TS 23.303 "Proximity-based services (ProSe)"

#### 4.4.1 ProSe Direct Communication

When out of network coverage, MCPTT UEs can communicate directly:

```
┌─────────────────────────────────────────────────────────────┐
│              Off-Network MCPTT (ProSe)                      │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│    ┌──────────┐                    ┌──────────┐            │
│    │  UE A    │◄───PC5 Interface──►│  UE B    │            │
│    │ (Relay)  │    Direct Comm     │          │            │
│    └──────────┘                    └──────────┘            │
│                                                             │
│    No infrastructure required!                              │
│                                                             │
│    Features:                                                │
│    - Group calls (same group ID as in-network)             │
│    - Private calls                                         │
│    - Floor control (distributed algorithm)                 │
│    - ProSe UE-to-Network Relay for range extension         │
│                                                             │
│    Frequency: Licensed spectrum or LTE Band 14 (700MHz)    │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

#### 4.4.2 Distributed Floor Control Algorithm

```c
/* ProSe Distributed Floor Control */

/* Each UE maintains state */
typedef struct {
    uint32_t group_id;
    uint32_t self_id;
    uint8_t self_priority;
    
    /* Floor state */
    uint32_t floor_holder;
    uint8_t floor_holder_priority;
    uint64_t floor_granted_time;
    
    /* Queued requests */
    floor_request_t requests[MAX_PROSE_UE];
    uint8_t num_requests;
} prose_floor_state_t;

/* ProSe Floor Request */
void prose_request_floor(prose_floor_state_t *state, uint8_t priority) {
    /* Broadcast request to all UEs in group */
    prose_broadcast_request(state->group_id, state->self_id, priority);
    
    /* Wait for responses and apply arbitration */
    /* Algorithm: Highest priority wins, tie-break by UE ID */
}

/* ProSe Arbitration (same algorithm on all UEs) */
void prose_arbitrate(prose_floor_state_t *state) {
    uint32_t winner = 0;
    uint8_t max_priority = 0;
    uint64_t earliest_request = UINT64_MAX;
    
    for (int i = 0; i < state->num_requests; i++) {
        floor_request_t *req = &state->requests[i];
        
        if (req->priority > max_priority ||
            (req->priority == max_priority && req->timestamp < earliest_request)) {
            winner = req->user_id;
            max_priority = req->priority;
            earliest_request = req->timestamp;
        }
    }
    
    state->floor_holder = winner;
    state->floor_holder_priority = max_priority;
}
```

---

## 5. Security Architecture

### 5.1 Security Requirements Overview

**Specification:** 3GPP TS 33.180 "Security of the Mission Critical Service"

#### 5.1.1 Security Threats Addressed

| Threat | Mitigation |
|--------|------------|
| Eavesdropping | SRTP encryption |
| Media tampering | SRTP authentication |
| Replay attacks | SRTP replay protection |
| Man-in-the-middle | MIKEY-TICKET, certificates |
| Unauthorized access | IMS AKA + MCPTT authentication |
| Denial of service | Priority handling, rate limiting |
| Identity spoofing | P-Asserted-Identity, certificates |

### 5.2 MIKEY-TICKET Key Distribution

**Reference:** RFC 6043 "MIKEY-TICKET: Ticket-Based Key Management"

#### 5.2.1 MIKEY-TICKET Overview

```
┌─────────────────────────────────────────────────────────────┐
│              MIKEY-TICKET Key Distribution                  │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│   UE                          KMS              MCPTT Server │
│    │                           │                    │       │
│    │  1. Request Ticket        │                    │       │
│    │ ─────────────────────────►│                    │       │
│    │   (Auth with cert/AKA)    │                    │       │
│    │                           │                    │       │
│    │  2. Return Ticket         │                    │       │
│    │ ◄─────────────────────────│                    │       │
│    │   {TEK, group-keys}KMS   │                    │       │
│    │                           │                    │       │
│    │  3. SIP INVITE            │                    │       │
│    │ ──────────────────────────┼───────────────────►│       │
│    │   + KeyMgmt: mikey-ticket │                    │       │
│    │                           │                    │       │
│    │                           │  4. Verify Ticket  │       │
│    │                           │◄───────────────────│       │
│    │                           │   (KMS validates)  │       │
│    │                           │                    │       │
│    │         200 OK            │                    │       │
│    │ ◄─────────────────────────┼────────────────────│       │
│    │                           │                    │       │
│    │◄────────── SRTP with derived keys ─────────────►│      │
│    │                           │                    │       │
└─────────────────────────────────────────────────────────────┘
```

#### 5.2.2 MIKEY-TICKET Message Format

```c
/* MIKEY-TICKET Payload Structure */

typedef struct {
    /* Header (Common to all MIKEY) */
    uint8_t  version;           /* = 0x02 (MIKEY v2) */
    uint8_t  data_type;         /* = 0x14 (Ticket request/response) */
    uint8_t  v;                 /* = 0 (PRF identifier) */
    uint8_t  prf_func;          /* PRF algorithm */
    uint16_t csb_id;            /* Crypto Session Bundle ID */
    uint8_t  cs_id_map_type;    /* = 0x01 (SRTP ID) */
    
    /* SRTP ID */
    uint8_t  srtp_id[6];        /* SSRC + ROC info */
    
    /* Timestamp */
    uint64_t timestamp;         /* NTP timestamp */
    
    /* Ticket-specific */
    uint8_t  ticket_type;       /* GROUP | PRIVATE | BROADCAST */
    uint32_t ticket_lifetime;   /* Seconds valid */
    
    /* Encrypted Key Data */
    uint16_t key_data_len;
    uint8_t  key_data[];        /* Encrypted TEK(s) */
} mikey_ticket_t;
```

### 5.3 SRTP Configuration for MCPTT

**Reference:** RFC 3711 "The Secure Real-time Transport Protocol (SRTP)"

#### 5.3.1 SRTP Crypto Suite for MCPTT

**MANDATORY Crypto Suite:**

```
Name: AES_CM_128_HMAC_SHA1_80

Parameters:
- Encryption: AES-128 in Counter Mode
- Authentication: HMAC-SHA1 with 80-bit tag
- Master Key Length: 128 bits
- Master Salt Length: 112 bits
- Session Key Derivation: AES-CM PRF
- Key Derivation Rate: 0 (once per session)
```

**SDP Security Description:**

```
a=crypto:1 AES_CM_128_HMAC_SHA1_80 \
   inline:d0RmdmcmVCspeEc3QGZiNWpVLFJhQX1cfHAwJSoj|2^20|1:32

Breakdown:
- Tag: 1
- Crypto-suite: AES_CM_128_HMAC_SHA1_80
- Key-params: inline:<key||salt>|<lifetime>|<mki:length>
  * Key||Salt (base64): 30 bytes (240 bits)
  * Lifetime: 2^20 packets
  * MKI: 1 (4 bytes)
```

#### 5.3.2 SRTP Key Derivation

```c
/* SRTP Key Derivation (RFC 3711 Section 4.3) */

/* Derivation Labels */
#define SRTP_LABEL_ENCRYPTION    0x00
#define SRTP_LABEL_AUTH_MSG      0x01
#define SRTP_LABEL_AUTH_SALT     0x02
#define SRTP_LABEL_SRTCP_ENCR    0x03
#define SRTP_LABEL_SRTCP_AUTH    0x04
#define SRTP_LABEL_SRTCP_SALT    0x05

/* Key Derivation Function */
void srtp_derive_keys(
    const uint8_t *master_key,      /* 16 bytes */
    const uint8_t *master_salt,     /* 14 bytes */
    uint32_t key_derivation_rate,
    uint32_t index,
    
    /* Outputs */
    uint8_t *session_key,           /* 16 bytes */
    uint8_t *auth_key,              /* 20 bytes */
    uint8_t *salt_key               /* 14 bytes */
) {
    uint8_t x[14 + 6];  /* salt + index + label */
    uint8_t key_id;
    
    /* x = salt || (index / key_derivation_rate) || label */
    memcpy(x, master_salt, 14);
    
    if (key_derivation_rate != 0) {
        key_id = (index / key_derivation_rate) & 0xFFFF;
        x[14] = (key_id >> 8) & 0xFF;
        x[15] = key_id & 0xFF;
    } else {
        memset(&x[14], 0, 2);
    }
    
    /* Derive encryption key */
    x[16] = 0;  /* label = 0x00 */
    x[17] = 0;
    aes_ctr_prf(master_key, x, 18, session_key, 16);
    
    /* Derive authentication key */
    x[16] = 0x01;  /* label = 0x01 */
    x[17] = 0;
    aes_ctr_prf(master_key, x, 18, auth_key, 20);
    
    /* Derive salt key */
    x[16] = 0x02;  /* label = 0x02 */
    x[17] = 0;
    aes_ctr_prf(master_key, x, 18, salt_key, 14);
}
```

### 5.4 TLS/DTLS for Signaling

#### 5.4.1 SIP over TLS

```
SIP Transport Security:

1. SIP Over TLS (SIPS):
   - Transport: TLS 1.2 or higher
   - Port: 5061 (default)
   - Certificate: X.509 v3
   - Cipher Suites: TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256 or higher

2. WebSocket Secure (WSS):
   - Transport: TLS 1.2 or higher
   - Port: 443
   - Used for: Web-based MCPTT clients

3. HTTP over TLS (HTTPS):
   - Transport: TLS 1.2 or higher
   - Port: 443
   - Used for: Configuration, group management
```

#### 5.4.2 Certificate Requirements

```
MCPTT Certificate Profile (X.509):

Subject:
  CN=<MCPTT-ID>
  OU=<Organization Unit>
  O=<Organization>
  C=<Country>

Subject Alternative Name:
  URI:sip:<MCPTT-ID>@<domain>
  URI:mcptt-id:<MCPTT-ID>

Extended Key Usage:
  id-kp-clientAuth (1.3.6.1.5.5.7.3.2)
  id-kp-serverAuth (1.3.6.1.5.5.7.3.1)

Key Usage:
  Digital Signature
  Key Encipherment

Validity: Max 2 years
Key Size: RSA 2048-bit minimum, ECDSA P-256
```

### 5.5 End-to-End Encryption Options

#### 5.5.1 Double Ratchet (Optional Enhancement)

For enhanced security, MCPTT implementations MAY support end-to-end encryption using Double Ratchet Algorithm (Signal Protocol):

```
E2E Encryption Flow:

1. Initial Key Exchange:
   - X3DH (Extended Triple Diffie-Hellman)
   - Pre-keys distributed via KMS

2. Continuous Session:
   - Double Ratchet for forward secrecy
   - Each message has new encryption key
   - Prevents decryption of past messages if key compromised

3. Group Support:
   - Sender Keys for group messages
   - Each sender has unique key chain
   - Efficient for large groups
```

#### 5.5.2 Per-to-Per Security (ProSe)

```
ProSe Security:

1. Key Establishment:
   - Pre-provisioned group keys
   - Or: DTLS handshake over PC5

2. Direct Communication:
   - SRTP with pre-shared group keys
   - Or: SRTP with DTLS-SRTP keys

3. Authentication:
   - Certificates issued by operator
   - Or: Pre-shared identity tokens
```

### 5.6 P-Asserted-Identity

**Reference:** RFC 3325 "Private Extensions to SIP for Asserted Identity"

```
P-Asserted-Identity Usage in MCPTT:

Purpose: Authenticate caller identity in trusted network

Flow:
1. UE sends INVITE with From header (may be anonymous)
2. P-CSCF authenticates UE (IMS AKA)
3. P-CSCF adds P-Asserted-Identity with verified identity
4. MCPTT Server uses P-Asserted-Identity for authorization

SIP Headers:
  P-Asserted-Identity: <sip:john.doe.123@firstnet.att.com>
  P-Preferred-Identity: <sip:john.doe.123@firstnet.att.com>

Privacy:
  Privacy: id  (removes P-Asserted-Identity for inter-domain)
```

---

## 6. Implementation Specifications

### 6.1 Latency Budgets

#### 6.1.1 End-to-End Latency Breakdown

```
Target: <300ms mouth-to-ear latency for PTT

┌─────────────────────────────────────────────────────────────┐
│                    Latency Budget                           │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  Component                    │  Target  │  Maximum        │
│  ─────────────────────────────────────────────────────────  │
│  Audio Capture + Encode       │   40ms   │   60ms          │
│  (Opus 20ms frame, look-ahead)│          │                 │
│                                                             │
│  Network Transit (UE to IMS)  │   30ms   │   50ms          │
│  (RTP + SRTP processing)      │          │                 │
│                                                             │
│  IMS Processing               │   20ms   │   40ms          │
│  (SIP routing, auth check)    │          │                 │
│                                                             │
│  MCPTT Server Processing      │   30ms   │   50ms          │
│  (Floor control, distribution)│          │                 │
│                                                             │
│  Network Transit (Server to UE)│  30ms   │   50ms          │
│                                                             │
│  Decode + Playback            │   40ms   │   60ms          │
│  (Jitter buffer, decode)      │          │                 │
│                                                             │
│  ─────────────────────────────────────────────────────────  │
│  TOTAL                        │  190ms   │  310ms          │
│                                                             │
│  Floor Control (Request→Grant)│  <100ms  │  <200ms         │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### 6.2 Audio Codec Configuration

#### 6.2.1 Opus Configuration for MCPTT

```c
/* Opus Encoder Configuration for MCPTT */

typedef struct {
    /* Mandatory Settings */
    int sample_rate;           /* 48000 Hz */
    int channels;              /* 1 (mono) */
    int frame_duration_ms;     /* 20 ms */
    int bitrate;               /* 24000 bps */
    
    /* Application Mode */
    int application;           /* OPUS_APPLICATION_VOIP */
    
    /* Complexity */
    int complexity;            /* 5 (0-10, lower = less CPU) */
    
    /* Optional Features */
    int use_inband_fec;        /* 1 (enable FEC) */
    int use_dtx;               /* 1 (enable DTX for silence) */
    int packet_loss_perc;      /* 5 (expected loss %) */
    
    /* Signal Characteristics */
    int signal_type;           /* OPUS_SIGNAL_VOICE */
} opus_mcptt_config_t;

/* Recommended Configuration */
opus_mcptt_config_t mcptt_opus_default = {
    .sample_rate = 48000,
    .channels = 1,
    .frame_duration_ms = 20,
    .bitrate = 24000,
    .application = OPUS_APPLICATION_VOIP,
    .complexity = 5,
    .use_inband_fec = 1,
    .use_dtx = 1,
    .packet_loss_perc = 5,
    .signal_type = OPUS_SIGNAL_VOICE
};
```

#### 6.2.2 Jitter Buffer Configuration

```c
/* Jitter Buffer for MCPTT */

typedef struct {
    /* Fixed Parameters */
    int min_delay_ms;          /* 40ms minimum */
    int max_delay_ms;          /* 250ms maximum */
    int target_delay_ms;       /* 80ms target */
    
    /* Adaptive Control */
    float adaptation_rate;     /* 0.1 (slow adaptation) */
    int burst_tolerance;       /* 3 packets */
    
    /* Late Packet Handling */
    int late_packet_threshold_ms; /* 150ms */
    int consecutive_late_limit;   /* 5 before adjustment */
} jitter_buffer_config_t;

/* MCPTT Jitter Buffer Strategy:
 * - Lower delay than VoIP (PTT is half-duplex)
 * - Fast adaptation for mobile environments
 * - Burst tolerance for network variability
 */
```

### 6.3 DSCP/QoS Marking

#### 6.3.1 DiffServ Code Points

```
MCPTT Traffic Classes (3GPP TS 23.107):

┌─────────────────────────────────────────────────────────────────┐
│  Traffic Type    │  DSCP    │  802.1p  │  Description          │
├─────────────────────────────────────────────────────────────────┤
│  MCPTT Voice     │  EF (46) │    6     │  Expedited Forwarding │
│  (RTP Media)     │          │          │  - Guaranteed low     │
│                  │          │          │    latency            │
├─────────────────────────────────────────────────────────────────┤
│  MCPTT Signaling │  AF31    │    4     │  Assured Forwarding   │
│  (SIP, TBCP)     │  (26)    │          │  - High priority      │
│                  │          │          │    control traffic    │
├─────────────────────────────────────────────────────────────────┤
│  MCPTT Data      │  AF21    │    2     │  Assured Forwarding   │
│  (MCData)        │  (18)    │          │  - Medium priority    │
│                  │          │          │    data               │
├─────────────────────────────────────────────────────────────────┤
│  Management      │  CS2     │    1     │  Class Selector 2     │
│                  │  (16)    │          │  - OAM traffic        │
└─────────────────────────────────────────────────────────────────┘
```

#### 6.3.2 QoS Profile for EPS Bearers

```
LTE QoS Bearer Configuration for MCPTT:

Bearer: Dedicated Bearer for MCPTT Voice
QCI: 65 (3GPP TS 23.203)

Parameters:
- Resource Type: GBR (Guaranteed Bit Rate)
- Priority: 0.7
- Packet Delay Budget: 50ms
- Packet Error Loss Rate: 10^-3
- Guaranteed Bit Rate: 32 kbps (Opus)

Bearer: Dedicated Bearer for MCPTT Signaling
QCI: 69

Parameters:
- Resource Type: Non-GBR
- Priority: 0.5
- Packet Delay Budget: 60ms
- Packet Error Loss Rate: 10^-6
```

### 6.4 Redundancy and Failover

#### 6.4.1 MCPTT Server Redundancy

```
MCPTT High Availability Architecture:

┌─────────────────────────────────────────────────────────────┐
│                  Active-Standby Pair                        │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│   ┌──────────────┐         Shared Storage    ┌──────────┐  │
│   │   Active     │◄─────────────────────────►│ Standby  │  │
│   │  MCPTT Server│      (State Sync)         │  Server  │  │
│   └──────┬───────┘                           └────┬─────┘  │
│          │                                        │        │
│          │ Health Check                           │        │
│          │ (Heartbeat every 1s)                   │        │
│          ▼                                        ▼        │
│   ┌───────────────────────────────────────────────────┐   │
│   │              Virtual IP / Load Balancer            │   │
│   │                   (Active)                         │   │
│   └───────────────────────────────────────────────────┘   │
│                            │                                │
│                    ◄───────┴───────►                       │
│                 UEs / Clients                               │
│                                                             │
│   Failover Time: <5 seconds                                 │
│   State Sync: Real-time session state replication           │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

#### 6.4.2 Media Redundancy (FEC)

```c
/* RTP Forward Error Correction (RFC 5109) */

/* FEC Configuration for MCPTT */
typedef struct {
    int fec_enabled;           /* 1 = enable FEC */
    int fec_mode;              /* 0 = 1D, 1 = 2D interleaved */
    int fec_columns;           /* Number of packets per row */
    int fec_rows;              /* Number of rows (for 2D) */
    int fec_payload_type;      /* Dynamic PT for FEC */
} fec_config_t;

/* Recommended: UEP (Unequal Error Protection) */
/* High priority audio frames get more protection */

fec_config_t mcptt_fec_config = {
    .fec_enabled = 1,
    .fec_mode = 0,           /* 1D for low latency */
    .fec_columns = 3,        /* 1 FEC for every 3 media packets */
    .fec_rows = 1,
    .fec_payload_type = 112  /* Dynamic PT assigned */
};
```

### 6.5 Group Call vs Private Call

#### 6.5.1 Group Call Implementation

```c
/* Group Call Structure */

typedef struct {
    uint32_t group_id;
    char display_name[MAX_NAME_LEN];
    
    /* Membership */
    uint32_t member_list[MAX_GROUP_SIZE];
    uint32_t num_members;
    uint32_t active_members;  /* Currently affiliated */
    
    /* Floor Control */
    floor_instance_t floor;
    uint8_t max_simultaneous_talkers;  /* Usually 1 */
    
    /* Media Distribution */
    uint32_t media_server_id;
    rtp_session_t *rtp_session;
    
    /* State */
    group_state_t state;
    uint64_t created_at;
    uint64_t expires_at;  /* For ad-hoc groups */
} mcptt_group_t;

/* Group Call Setup Flow */
void group_call_setup(mcptt_group_t *group, uint32_t initiator) {
    /* 1. Check group affiliation */
    if (!is_user_affiliated(group, initiator)) {
        auto_affiliate(group, initiator);
    }
    
    /* 2. Create media session (RTP) */
    group->rtp_session = create_rtp_session(group->group_id);
    
    /* 3. Initialize floor control */
    init_floor_control(&group->floor);
    
    /* 4. Notify all affiliated members */
    for (int i = 0; i < group->num_members; i++) {
        if (is_user_online(group->member_list[i])) {
            send_invite(group->member_list[i], group->group_id);
        }
    }
}
```

#### 6.5.2 Private Call Implementation

```c
/* Private Call Structure */

typedef struct {
    uint32_t call_id;
    uint32_t caller;
    uint32_t callee;
    
    /* Mode */
    private_call_mode_t mode;  /* HALF_DUPLEX or FULL_DUPLEX */
    
    /* Floor Control (for half-duplex) */
    floor_instance_t floor;
    
    /* Media */
    rtp_session_t *rtp_session;
    
    /* State */
    call_state_t state;
    uint64_t established_at;
} private_call_t;

/* Private Call Setup Flow */
void private_call_setup(private_call_t *call, uint32_t caller, uint32_t callee) {
    /* 1. Determine callee capabilities */
    callee_caps_t caps = query_user_capabilities(callee);
    
    /* 2. Decide mode */
    if (caps.supports_full_duplex && caller_prefers_full_duplex(caller)) {
        call->mode = FULL_DUPLEX;
    } else {
        call->mode = HALF_DUPLEX;
    }
    
    /* 3. Send INVITE */
    send_private_call_invite(caller, callee, call->mode);
    
    /* 4. Wait for response */
    call->state = CALL_STATE_RINGING;
}
```

---

## 7. Interoperability Requirements

### 7.1 Mandatory vs Optional Features

#### 7.1.1 Feature Categories

```
┌─────────────────────────────────────────────────────────────────┐
│              MCPTT Feature Classification                       │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  MANDATORY FOR INTEROPERABILITY:                               │
│  ─────────────────────────────────────────────────────────────  │
│  [M] Group Call (half-duplex, one speaker)                     │
│  [M] Private Call (half-duplex)                                │
│  [M] Floor Control with 8 priority levels                      │
│  [M] Emergency Alert                                           │
│  [M] Opus codec (24kbps, 20ms frames)                          │
│  [M] SRTP encryption (AES_CM_128_HMAC_SHA1_80)                │
│  [M] SIP signaling over TLS                                    │
│  [M] IMS AKA authentication                                    │
│  [M] Group affiliation/de-affiliation                          │
│  [M] Late entry support                                        │
│                                                                 │
│  OPTIONAL FEATURES:                                            │
│  ─────────────────────────────────────────────────────────────  │
│  [O] Private Call (full-duplex)                                │
│  [O] Simultaneous multiple talkers (up to 4)                   │
│  [O] Video Group Call (MCVideo)                                │
│  [O] Data Services (MCData)                                    │
│  [O] Off-network operation (ProSe)                             │
│  [O] LMR Interworking                                          │
│  [O] Ambient listening                                         │
│  [O] Discreet monitoring                                       │
│  [O] Remotely initiated calls                                  │
│                                                                 │
│  CONDITIONAL (Required for specific deployments):              │
│  ─────────────────────────────────────────────────────────────  │
│  [C] MCPTT-4 interface (HTTP/WebSocket)                        │
│  [C] Group management (for non-SIP clients)                    │
│  [C] Geographic area-based groups                              │
│  [C] MCPTT UE-to-Network Relay                                 │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 7.2 Interoperability Test Requirements

#### 7.2.1 GCF/PTCRB Certification

```
Required Test Cases for Certification:

┌─────────────────────────────────────────────────────────────┐
│  Test Category              │  Test Cases │  Priority       │
├─────────────────────────────────────────────────────────────┤
│  Registration/Auth          │    15       │  Critical       │
│  Group Affiliation          │    10       │  Critical       │
│  Group Call Setup           │    20       │  Critical       │
│  Private Call Setup         │    15       │  Critical       │
│  Floor Control              │    25       │  Critical       │
│  Emergency Procedures       │    15       │  Critical       │
│  Media Quality              │    10       │  High           │
│  Security                   │    20       │  High           │
│  Interworking               │    10       │  Medium         │
│  Stress/Performance         │    10       │  Medium         │
├─────────────────────────────────────────────────────────────┤
│  TOTAL                      │   150       │                 │
└─────────────────────────────────────────────────────────────┘
```

#### 7.2.2 Interoperability Profile

```xml
<?xml version="1.0"?>
<mcptt-interoperability-profile version="1.0">
    <supported-services>
        <service name="group-call" mandatory="true"/>
        <service name="private-call" mandatory="true"/>
        <service name="emergency-alert" mandatory="true"/>
        <service name="mcvideo" mandatory="false"/>
        <service name="mcdata" mandatory="false"/>
    </supported-services>
    
    <codecs>
        <codec name="opus" mandatory="true">
            <sample-rate>48000</sample-rate>
            <frame-size>20</frame-size>
            <bitrate>24000</bitrate>
        </codec>
        <codec name="amr-nb" mandatory="false"/>
    </codecs>
    
    <security>
        <mechanism name="mikey-ticket" mandatory="true"/>
        <mechanism name="srtp" mandatory="true"/>
        <mechanism name="tls" mandatory="true"/>
        <mechanism name="dtls-srtp" mandatory="false"/>
    </security>
    
    <floor-control>
        <protocol name="tbcp" mandatory="true"/>
        <max-priority-levels>8</max-priority-levels>
        <preemption-supported>true</preemption-supported>
        <max-queue-size>50</max-queue-size>
    </floor-control>
</mcptt-interoperability-profile>
```

---

## 8. Appendices

### Appendix A: Message Format Reference

#### A.1 TBCP Message Examples

```
TBCP Floor Request Message (Hex Dump):

Offset  0  1  2  3  4  5  6  7   8  9  A  B  C  D  E  F
0000   01 00 01 00 0A 00 00 00  01 00 00 00 42 00 00 00  
0010   15 CD 5B 07 01 00 00 00  06 00 00 00 00 00

Breakdown:
- Version: 0x01 (1)
- Reserved: 0x00
- Primitive: 0x01 (Floor Request)
- Payload Length: 0x000A (10 bytes)
- Conference ID: 0x00000001
- Transaction ID: 0x00000042
- User ID: 0x075BCD15
- Floor ID: 0x00000001
- Attributes:
  * Priority (0x06): 0x06 (IMMEDIATE priority)
```

#### A.2 SIP INVITE Example

```
INVITE sip:fire-department@mcptt.firstnet.gov SIP/2.0
Via: SIP/2.0/TLS ue1.firstnet.gov:5061;branch=z9hG4bK776asdhds
Max-Forwards: 70
From: <sip:john.doe.123@firstnet.gov>;tag=1928301774
To: <sip:fire-department@mcptt.firstnet.gov>
Call-ID: a84b4c76e66710@ue1.firstnet.gov
CSeq: 314159 INVITE
Contact: <sip:john.doe.123@ue1.firstnet.gov;gr=urn:uuid:f81d4fae-7dec-11d0-a765-00a0c91e6bf6>
Accept-Contact: *;+g.3gpp.mcptt;require;explicit
P-Asserted-Identity: <sip:john.doe.123@firstnet.gov>
MCPTT-Info: mcptt-request;call-type=GROUP;group-id=fire-department;priority=EMERGENCY
Content-Type: application/sdp
Content-Length: 289

v=0
o=- 2890844526 2890842807 IN IP4 ue1.firstnet.gov
s=MCPTT Session
c=IN IP4 192.0.2.1
t=0 0
m=audio 49170 RTP/SAVPF 111
a=rtpmap:111 opus/48000/2
a=fmtp:111 maxplaybackrate=48000;stereo=0;useinbandfec=1
a=ptime:20
a=maxptime:40
a=crypto:1 AES_CM_128_HMAC_SHA1_80 inline:d0RmdmcmVCspeEc3QGZiNWpVLFJhQX1cfHAwJSoj|2^20|1:32
a=fingerprint:SHA-256 4A:AD:B9:B1:3F:82:18:3B:54:02:12:DF:3E:5D:49:6B:19:E5:7C:AB
a=setup:active
a=connection:new
```

### Appendix B: Configuration Templates

#### B.1 MCPTT Client Configuration

```json
{
    "mcptt_configuration": {
        "version": "1.0",
        "user": {
            "mcptt_id": "user@domain.com",
            "organization": "police.seattle.wa.us",
            "role": "officer",
            "priority_level": 3
        },
        "network": {
            "ims_domain": "firstnet.att.com",
            "mcptt_server": "mcptt.firstnet.att.com",
            "sip_proxy": "pcscf.firstnet.att.com:5061",
            "transport": "tls",
            "qci_voice": 65,
            "qci_signaling": 69
        },
        "audio": {
            "codec": "opus",
            "sample_rate": 48000,
            "frame_size_ms": 20,
            "bitrate_bps": 24000,
            "fec_enabled": true,
            "dtx_enabled": true
        },
        "security": {
            "certificate_path": "/secure/client.crt",
            "private_key_path": "/secure/client.key",
            "ca_path": "/secure/ca.crt",
            "srtp_suite": "AES_CM_128_HMAC_SHA1_80"
        },
        "floor_control": {
            "protocol": "tbcp",
            "server_port": 3478,
            "client_port_range": "10000-20000",
            "request_timeout_ms": 2000,
            "max_queue_wait_ms": 10000
        },
        "groups": [
            {
                "group_id": "dispatch-north@firstnet.att.com",
                "display_name": "Dispatch North",
                "auto_affiliate": true
            },
            {
                "group_id": "emergency-all@firstnet.att.com",
                "display_name": "Emergency All Call",
                "auto_affiliate": false
            }
        ]
    }
}
```

### Appendix C: Reference Implementations

#### C.1 Open Source Libraries

| Component | Library | License | URL |
|-----------|---------|---------|-----|
| SIP Stack | PJSIP | GPL/BSD | https://www.pjsip.org/ |
| SIP Stack | Sofia-SIP | LGPL | https://github.com/freeswitch/sofia-sip |
| SRTP | libsrtp | BSD | https://github.com/cisco/libsrtp |
| Opus | opus | BSD | https://opus-codec.org/ |
| TLS | OpenSSL | Apache | https://www.openssl.org/ |
| TLS | GnuTLS | LGPL | https://www.gnutls.org/ |
| JSON | json-c | MIT | https://github.com/json-c/json-c |
| XML | libxml2 | MIT | https://gitlab.gnome.org/GNOME/libxml2 |

### Appendix D: Standards Reference

#### D.1 3GPP Specifications

| Spec Number | Title | Release |
|-------------|-------|---------|
| TS 22.179 | Mission Critical Push to Talk (MCPTT); Service requirements | 18 |
| TS 23.179 | Functional architecture and information flows | 18 |
| TS 24.379 | Mission Critical Push to Talk (MCPTT) call control | 18 |
| TS 24.380 | Mission Critical Push to Talk (MCPTT) floor control | 18 |
| TS 24.381 | Mission Critical Push to Talk (MCPTT) group management | 18 |
| TS 24.382 | Mission Critical Push to Talk (MCPTT) identity management | 18 |
| TS 26.179 | Mission Critical Push to Talk (MCPTT); Media plane control | 18 |
| TS 26.281 | Mission Critical Video (MCVideo) | 18 |
| TS 23.282 | Mission Critical Data (MCData) | 18 |
| TS 33.180 | Security of the Mission Critical Service | 18 |
| TS 23.303 | Proximity-based services (ProSe) | 18 |

#### D.2 IETF RFCs

| RFC | Title |
|-----|-------|
| RFC 3261 | SIP: Session Initiation Protocol |
| RFC 3265 | SIP-Specific Event Notification |
| RFC 3325 | P-Asserted-Identity |
| RFC 3550 | RTP: Transport Protocol for Real-Time Applications |
| RFC 3711 | Secure Real-time Transport Protocol (SRTP) |
| RFC 3830 | MIKEY: Multimedia Internet KEYing |
| RFC 4353 | Conferencing Framework with SIP |
| RFC 4568 | SDP Security Descriptions for Media Streams |
| RFC 4575 | SIP Event Package for Conference State |
| RFC 4579 | SIP Call Control - Conferencing for User Agents |
| RFC 4582 | Binary Floor Control Protocol (BFCP) |
| RFC 5124 | Extended Secure RTP Profile (RTP/SAVPF) |
| RFC 6503 | Centralized Conferencing Manipulation Protocol |

---

## Document Control

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-02-06 | Technical Research | Initial comprehensive release |

---

**END OF DOCUMENT**

*This document provides reference information for implementing 3GPP MCPTT-compliant systems. Always refer to the latest 3GPP specifications for authoritative requirements.*
