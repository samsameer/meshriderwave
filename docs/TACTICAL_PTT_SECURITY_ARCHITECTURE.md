# Military-Grade Security Architecture for Tactical PTT Communications

## Executive Summary

This document provides comprehensive security architecture recommendations for MeshRider Wave tactical Push-To-Talk (PTT) communications, targeting military-grade security with FIPS 140-2/140-3 compliance and NSA Commercial Solutions for Classified (CSfC) program alignment.

**Current Project Context:**
- MeshRider Wave uses libsodium, MLS (Messaging Layer Security)
- E2E encryption for voice and data
- Target: Military-grade security (FIPS 140-2, NSA CSfC)

---

## Table of Contents

1. [End-to-End Encryption Architecture](#1-end-to-end-encryption-architecture)
2. [Cryptographic Algorithms](#2-cryptographic-algorithms)
3. [Key Management Infrastructure](#3-key-management-infrastructure)
4. [FIPS 140-2/140-3 Compliance](#4-fips-140-2140-3-compliance)
5. [Network Security Architecture](#5-network-security-architecture)
6. [Threat Modeling & Countermeasures](#6-threat-modeling--countermeasures)
7. [Security Testing Framework](#7-security-testing-framework)
8. [Post-Quantum Cryptography Migration](#8-post-quantum-cryptography-migration)

---

## 1. End-to-End Encryption Architecture

### 1.1 Double Ratchet Algorithm (Signal Protocol)

The Double Ratchet algorithm provides the gold standard for secure messaging with forward secrecy and post-compromise security.

**Core Properties:**
- **Forward Secrecy (FS):** Past messages cannot be decrypted even if current keys are compromised
- **Post-Compromise Security (PCS):** Future messages are protected after a compromise through continuous key ratcheting
- **Break-in Recovery:** Automatic recovery from key compromise through DH ratchet steps

**PTT-Specific Adaptations:**

```kotlin
// Double Ratchet for PTT Voice Streams
class PTTRatchetSession {
    // Root key derived from initial key agreement (PQXDH)
    private var rootKey: ByteArray
    
    // Chain keys for sending/receiving
    private var sendingChainKey: ByteArray
    private var receivingChainKey: ByteArray
    
    // DH ratchet key pairs
    private var localRatchetKeyPair: KeyPair
    private var remoteRatchetPublicKey: PublicKey?
    
    // Message counters
    private var sendingMessageNumber: Int = 0
    private var receivingMessageNumber: Int = 0
    
    fun encryptVoiceFrame(plaintext: ByteArray): EncryptedFrame {
        // Symmetric ratchet step
        val (newChainKey, messageKey) = KDF_CK(sendingChainKey)
        sendingChainKey = newChainKey
        
        // Encrypt with AES-256-GCM or ChaCha20-Poly1305
        val ciphertext = AEAD_Encrypt(messageKey, plaintext, associatedData)
        
        return EncryptedFrame(
            header = createHeader(),
            ciphertext = ciphertext,
            messageNumber = sendingMessageNumber++
        )
    }
}
```

**Implementation Requirements:**
- **KDF:** HKDF-SHA256 or HKDF-SHA512
- **AEAD:** AES-256-GCM or ChaCha20-Poly1305
- **DH:** X25519 or X448 (Curve25519/Curve448)
- **MAX_SKIP:** 1000 (tolerates lost packets in tactical environment)

### 1.2 MLS (Messaging Layer Security - RFC 9420)

MLS is the IETF-standardized group key establishment protocol providing efficient asynchronous group communication.

**Key Benefits for PTT:**
- Scales efficiently from 2 to 10,000+ members (O(log n) operations)
- Forward secrecy and post-compromise security for groups
- Asynchronous operation (members need not be online simultaneously)
- Tree-based ratchet structure for efficient key updates

**MLS Architecture for Tactical PTT:**

```
┌─────────────────────────────────────────────────────────────┐
│                    MLS GROUP (Tactical Net)                  │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐    │
│  │ Member A │  │ Member B │  │ Member C │  │ Member D │    │
│  │ (Leader) │  │ (Medic)  │  │ (Comms)  │  │ (Scout)  │    │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘  └────┬─────┘    │
│       │              │              │              │         │
│       └──────────────┴──────────────┴──────────────┘         │
│                      Ratchet Tree                             │
│  ┌───────────────────────────────────────────────────────┐   │
│  │                    Root Secret                         │   │
│  │                   /            \                       │   │
│  │            Node AB              Node CD                 │   │
│  │           /      \            /      \                 │   │
│  │       Leaf A    Leaf B    Leaf C    Leaf D             │   │
│  └───────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

**MLS Cipher Suites for Tactical Use:**
| Cipher Suite | KEM | Signature | Hash | Use Case |
|-------------|-----|-----------|------|----------|
| 0x0001 | DHKEM(X25519, HKDF-SHA256) | Ed25519 | SHA256 | Standard |
| 0x0004 | DHKEM(P-256, HKDF-SHA256) | ECDSA(P-256) | SHA256 | FIPS compliant |
| 0x0007 | DHKEM(X448, HKDF-SHA512) | Ed448 | SHA512 | High security |
| 0x000B | ML-KEM-768 + X25519 | Ed25519 | SHA256 | Hybrid PQC |

**Update Strategy for PTT:**
- **Group Updates:** Every 24 hours or after member removal
- **Self Updates:** Every 4 hours during active operation
- **Immediate Updates:** After any member compromise suspicion

### 1.3 SRTP (Secure RTP) for Voice Encryption

SRTP (RFC 3711) is the standard for securing real-time voice communications.

**SRTP Profile for Tactical PTT:**

```
┌──────────────────────────────────────────────────────────────┐
│                    SRTP PACKET STRUCTURE                     │
├──────────────────────────────────────────────────────────────┤
│  RTP Header (12+ bytes)                                      │
│  ├── Version (2 bits): 2                                     │
│  ├── Padding (1 bit): 0                                      │
│  ├── Extension (1 bit): 0                                    │
│  ├── CSRC Count (4 bits): 0                                  │
│  ├── Marker (1 bit): PTT state                               │
│  ├── Payload Type (7 bits): 111 (Dynamic)                    │
│  ├── Sequence Number (16 bits)                               │
│  ├── Timestamp (32 bits)                                     │
│  └── SSRC (32 bits)                                          │
├──────────────────────────────────────────────────────────────┤
│  Encrypted Payload (Opus encoded voice)                      │
├──────────────────────────────────────────────────────────────┤
│  SRTP MKI (Optional, 4 bytes)                                │
├──────────────────────────────────────────────────────────────┤
│  Authentication Tag (10 bytes truncated HMAC-SHA1)          │
└──────────────────────────────────────────────────────────────┘
```

**Recommended SRTP Cryptographic Transforms:**

| Parameter | Recommendation | Security Level |
|-----------|---------------|----------------|
| Encryption | AES-256-CTR | 256-bit |
| Authentication | HMAC-SHA1-160 (80-bit tag) | 80-bit |
| Key Derivation | AES-256-CM PRF | 256-bit |
| Master Salt | 112-bit random | 112-bit |
| ROC Handling | 32-bit rollover counter | 48-bit index |

**Alternative for Mobile Devices:**
- **Encryption:** AES-256-GCM (combined AEAD, no separate MAC)
- **Key Derivation:** HKDF-SHA256
- **Benefits:** Better performance on mobile, simpler implementation

**Key Derivation for SRTP:**
```
SRTP Session Keys = KDF(Master Key, Master Salt, Key Deriv Rate)
├── Encryption Key (n_e bits)
├── Authentication Key (n_a bits)
└── Salting Key (n_s bits)
```

### 1.4 DTLS for Datagram Transport

DTLS 1.2/1.3 (RFC 6347/9147) provides connection-oriented security over UDP.

**DTLS 1.3 Handshake for PTT Control Channel:**

```
┌────────────────────────────────────────────────────────────────┐
│                    DTLS 1.3 HANDSHAKE FLOW                     │
├────────────────────────────────────────────────────────────────┤
│                                                                │
│  Client (EUD)                    Server (Radio)                │
│  ─────────────────────────────────────────────────────────────│
│                                                                │
│  ClientHello                                                   │
│  + Key Share (X25519)                                          │
│  + Signature Algorithms                                          │
│  + Supported Groups                                              │
│  ────────────────────────────>                                 │
│                                   ServerHello                  │
│                                   + Key Share                  │
│                                   {EncryptedExtensions}        │
│                                   {Certificate}                │
│                                   {CertificateVerify}          │
│                                   {Finished}                   │
│                                 <────────────────────────────  │
│                                                                │
│  {Certificate}                                                 │
│  {CertificateVerify}                                           │
│  {Finished}                                                    │
│  [Application Data] ─────────────────────────────────────────>│
│                                                                │
│                              [Application Data] <─────────────│
│                                                                │
└────────────────────────────────────────────────────────────────┘
{} = encrypted with handshake keys
[] = encrypted with application keys
```

**DTLS Cipher Suites for Tactical PTT:**

| Cipher Suite | Key Exchange | Authentication | Encryption | Hash |
|-------------|--------------|----------------|------------|------|
| TLS_AES_256_GCM_SHA384 | ECDHE | RSA/ECDSA | AES-256-GCM | SHA384 |
| TLS_CHACHA20_POLY1305_SHA256 | ECDHE | RSA/ECDSA | ChaCha20-Poly1305 | SHA256 |
| TLS_AES_128_CCM_SHA256 | ECDHE | RSA/ECDSA | AES-128-CCM | SHA256 |

**DTLS Considerations for PTT:**
- **PMTU Discovery:** Essential for mesh networks with varying MTU
- **Retransmission:** DTLS handles handshake packet loss
- **Anti-Replay:** Built-in replay protection with sliding window
- **0-RTT:** DTLS 1.3 supports 0-RTT for faster session resumption

### 1.5 Certificate Management

**X.509 Certificate Profile for Tactical PTT:**

```
Certificate:
    Data:
        Version: 3 (0x2)
        Serial Number: Unique per-device
        Signature Algorithm: ecdsa-with-SHA384
        Issuer: CN=Tactical CA, OU=MeshRider, O=DoodleLabs
        Validity:
            Not Before: [Issuance Date]
            Not After: [Issuance Date + 2 years]
        Subject: CN=PTT-Device-[UUID], OU=[Unit ID], O=[Organization]
        Subject Public Key Info:
            Public Key Algorithm: id-ecPublicKey
                Public-Key: (P-384 curve)
        X509v3 extensions:
            X509v3 Subject Alternative Name:
                DNS:ptt-[UUID].tactical.mesh
                IP Address: [ACP Address]
            X509v3 Key Usage: critical
                Digital Signature, Key Encipherment
            X509v3 Extended Key Usage:
                TLS Web Client Authentication,
                TLS Web Server Authentication,
                1.3.6.1.4.1.XXXX.1 (PTT End Entity)
            X509v3 Subject Key Identifier:
                [SHA-256 hash of public key]
            X509v3 Authority Key Identifier:
                [CA key identifier]
            1.3.6.1.4.1.XXXX.2 (Tactical Role Extension):
                Role: [LEADER|MEDIC|COMMS|SCOUT|...]
                Clearance: [UNCLASSIFIED|CONFIDENTIAL|SECRET|TOP SECRET]
                Unit: [Unit Identifier]
```

**Certificate Lifecycle Management:**

```
┌─────────────────────────────────────────────────────────────────┐
│              CERTIFICATE LIFECYCLE FOR TACTICAL PTT             │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐      │
│  │   IDevID     │    │   LDevID     │    │   ACP Cert   │      │
│  │ (Factory)    │───>│ (Enrollment) │───>│ (Operational)│      │
│  │              │    │              │    │              │      │
│  │ • Device ID  │    │ • Domain ID  │    │ • ACP Addr   │      │
│  │ • Serial #   │    │ • Unit/Role  │    │ • Role Attrs │      │
│  │ • Mfg CA     │    │ • Domain CA  │    │ • Short life │      │
│  └──────────────┘    └──────────────┘    └──────────────┘      │
│         │                   │                   │               │
│         ▼                   ▼                   ▼               │
│    [BRSKI Bootstrap]  [EST Enrollment]   [Auto-renewal]        │
│                                                                 │
│  Renewal: Automatic at 80% of lifetime (EST re-enrollment)     │
│  Revocation: OCSP stapling or CRL distribution                 │
│  Compromise: Immediate revocation + re-key                     │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## 2. Cryptographic Algorithms

### 2.1 Algorithm Selection Framework

**NIST-Approved Algorithms (FIPS 140-2/140-3):**

| Category | Algorithm | Mode/Variant | Key Size | Status |
|----------|-----------|--------------|----------|--------|
| Symmetric Encryption | AES | GCM | 256-bit | Approved |
| Symmetric Encryption | AES | CTR | 256-bit | Approved |
| Authenticated Encryption | AES-GCM | AEAD | 256-bit | Approved |
| Stream Cipher | ChaCha20 | Poly1305 | 256-bit | Approved (RFC 8439) |
| Hash Function | SHA-2 | SHA-256 | - | Approved |
| Hash Function | SHA-2 | SHA-384 | - | Approved |
| Hash Function | SHA-2 | SHA-512 | - | Approved |
| Message Authentication | HMAC | SHA-256 | 256-bit | Approved |
| Key Derivation | HKDF | SHA-256 | Variable | Approved |
| Key Derivation | PBKDF2 | SHA-256 | Variable | Approved |

**Non-FIPS but Widely Deployed (Risk Assessment Required):**

| Algorithm | Use Case | Security Level | Consideration |
|-----------|----------|----------------|---------------|
| ChaCha20-Poly1305 | Mobile devices | 256-bit | Fast on non-AES-NI hardware |
| X25519 | ECDH | 128-bit | RFC 7748, but not FIPS-approved |
| Ed25519 | Signatures | 128-bit | RFC 8032, but not FIPS-approved |
| BLAKE2b | Hashing | 256-bit | Fast, but not FIPS-approved |

### 2.2 AES-256-GCM (Authenticated Encryption)

**Implementation Requirements:**

```kotlin
// AES-256-GCM with Android Keystore
class AES256GCMEngine {
    private val KEY_ALIAS = "ptt_aes256_gcm_key"
    private val ANDROID_KEYSTORE = "AndroidKeyStore"
    private val TRANSFORMATION = "AES/GCM/NoPadding"
    private val GCM_TAG_LENGTH = 128 // bits
    private val GCM_IV_LENGTH = 96 // bits (12 bytes)
    
    fun generateKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        
        val keyGenSpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .setUserAuthenticationRequired(false)
            .build()
        
        keyGenerator.init(keyGenSpec)
        return keyGenerator.generateKey()
    }
    
    fun encrypt(plaintext: ByteArray, associatedData: ByteArray?): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getKey())
        
        associatedData?.let { cipher.updateAAD(it) }
        
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext)
        
        // Prepend IV to ciphertext
        return iv + ciphertext
    }
    
    fun decrypt(ciphertextWithIv: ByteArray, associatedData: ByteArray?): ByteArray {
        val iv = ciphertextWithIv.copyOfRange(0, 12)
        val ciphertext = ciphertextWithIv.copyOfRange(12, ciphertextWithIv.size)
        
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, getKey(), spec)
        
        associatedData?.let { cipher.updateAAD(it) }
        
        return cipher.doFinal(ciphertext)
    }
}
```

**Security Considerations:**
- **Nonce Reuse:** Never reuse nonce-key pairs (catastrophic failure)
- **IV Length:** Must be 96 bits for GCM (other lengths less secure)
- **Tag Length:** 128 bits recommended (minimum 96 bits per NIST)
- **Message Size:** Maximum 2^36-32 bytes (~64GB) per key/nonce

### 2.3 ChaCha20-Poly1305 (Mobile-Optimized)

ChaCha20-Poly1305 (RFC 8439) provides superior performance on mobile devices without AES-NI hardware acceleration.

**Performance Comparison (ARM Cortex-A73):**

| Algorithm | Throughput | Power Usage | Latency |
|-----------|------------|-------------|---------|
| AES-256-GCM (software) | 45 MB/s | High | 12ms |
| AES-256-GCM (AES-NI) | 800 MB/s | Low | 1ms |
| ChaCha20-Poly1305 | 380 MB/s | Medium | 2ms |

**Implementation:**

```c
// libsodium-based ChaCha20-Poly1305
#include <sodium.h>

int ptt_encrypt_chacha20(
    const uint8_t *plaintext,
    size_t plaintext_len,
    const uint8_t *key,        // 32 bytes
    const uint8_t *nonce,      // 12 bytes (96-bit)
    const uint8_t *ad,         // Associated data
    size_t ad_len,
    uint8_t *ciphertext,       // Output: plaintext_len + 16 bytes (tag)
    uint8_t *tag               // Output: 16 bytes
) {
    // Use libsodium's high-level AEAD API
    return crypto_aead_chacha20poly1305_ietf_encrypt_detached(
        ciphertext,
        tag,
        NULL,  // tag_len not needed
        plaintext,
        plaintext_len,
        ad,
        ad_len,
        NULL,  // nsec (not used)
        nonce,
        key
    );
}
```

**Hybrid Deployment Strategy:**
- **Android with AES-NI:** Use AES-256-GCM
- **Android without AES-NI:** Use ChaCha20-Poly1305
- **LEDE Firmware:** Use ChaCha20-Poly1305 (better software performance)
- **Detect at runtime:** Check for AES-NI support

### 2.4 ECDH Key Exchange (X25519/X448)

**X25519 for PTT Session Key Establishment:**

```kotlin
// X25519 ECDH using Android Keystore or libsodium
class X25519KeyExchange {
    
    fun generateKeyPair(): KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_X25519,
            ANDROID_KEYSTORE
        )
        
        val spec = KeyGenParameterSpec.Builder(
            "x25519_" + UUID.randomUUID(),
            KeyProperties.PURPOSE_AGREE_KEY
        )
            .setAlgorithmParameterSpec(ECGenParameterSpec("X25519"))
            .build()
        
        keyPairGenerator.initialize(spec)
        return keyPairGenerator.generateKeyPair()
    }
    
    fun deriveSharedSecret(
        privateKey: PrivateKey,
        remotePublicKey: PublicKey
    ): ByteArray {
        val keyAgreement = KeyAgreement.getInstance("X25519")
        keyAgreement.init(privateKey)
        keyAgreement.doPhase(remotePublicKey, true)
        return keyAgreement.generateSecret()
    }
}
```

**ECDH Security Levels:**

| Curve | Security Level | Public Key Size | Use Case |
|-------|----------------|-----------------|----------|
| X25519 | 128-bit | 32 bytes | Standard PTT |
| X448 | 224-bit | 56 bytes | High-security PTT |
| P-256 | 128-bit | 32 bytes | FIPS-compliant |
| P-384 | 192-bit | 48 bytes | FIPS high-security |

### 2.5 Digital Signatures (Ed25519/ECDSA)

**Ed25519 for Message Authentication:**

```kotlin
// Ed25519 for PTT message signing
class Ed25519Signature {
    
    fun sign(message: ByteArray, privateKey: PrivateKey): ByteArray {
        val signature = Signature.getInstance("Ed25519")
        signature.initSign(privateKey)
        signature.update(message)
        return signature.sign()
    }
    
    fun verify(message: ByteArray, signature: ByteArray, publicKey: PublicKey): Boolean {
        val sig = Signature.getInstance("Ed25519")
        sig.initVerify(publicKey)
        sig.update(message)
        return sig.verify(signature)
    }
}
```

**Signature Algorithm Comparison:**

| Algorithm | Security Level | Signature Size | Speed (sign/verify) | FIPS |
|-----------|----------------|----------------|---------------------|------|
| Ed25519 | 128-bit | 64 bytes | Fast/Fast | No |
| Ed448 | 224-bit | 114 bytes | Fast/Fast | No |
| ECDSA P-256 | 128-bit | 64 bytes | Medium/Medium | Yes |
| ECDSA P-384 | 192-bit | 96 bytes | Slow/Medium | Yes |
| RSA-3072 | 128-bit | 384 bytes | Slow/Fast | Yes |

### 2.6 CRYSTALS-Kyber (Post-Quantum KEM)

**ML-KEM-768 (formerly Kyber-768) for Hybrid PQC:**

ML-KEM-768 provides 192-bit security equivalent against quantum attacks.

```
Hybrid Key Encapsulation (X25519 + ML-KEM-768):

┌─────────────────────────────────────────────────────────────────┐
│                     HYBRID KEY ENCAPSULATION                    │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  Traditional Key Exchange (X25519)                              │
│  ├── Client generates ephemeral X25519 key pair                 │
│  ├── Client sends X25519 public key                             │
│  ├── Server generates ephemeral X25519 key pair                 │
│  ├── Server computes X25519 shared secret                       │
│  └── Client computes X25519 shared secret                       │
│                                                                 │
│  Post-Quantum KEM (ML-KEM-768)                                  │
│  ├── Server generates ML-KEM key pair                           │
│  ├── Server sends ML-KEM public key                             │
│  ├── Client encapsulates secret with ML-KEM pk                  │
│  ├── Client sends ML-KEM ciphertext                             │
│  └── Server decapsulates ML-KEM shared secret                   │
│                                                                 │
│  Combined Secret Derivation                                     │
│  └── shared_secret = HKDF(X25519_ss || ML-KEM_ss)               │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

**ML-KEM Parameters (NIST FIPS 203):**

| Parameter | ML-KEM-512 | ML-KEM-768 | ML-KEM-1024 |
|-----------|------------|------------|-------------|
| Security Level | 128-bit | 192-bit | 256-bit |
| Public Key Size | 800 bytes | 1,184 bytes | 1,568 bytes |
| Ciphertext Size | 768 bytes | 1,088 bytes | 1,568 bytes |
| Shared Secret Size | 32 bytes | 32 bytes | 32 bytes |

**Bandwidth Considerations for Mesh Networks:**
- ML-KEM-768 adds ~2.2KB per handshake
- Acceptable for tactical mesh with low node churn
- Consider ML-KEM-512 for very constrained bandwidth

### 2.7 CRYSTALS-Dilithium (Post-Quantum Signatures)

**ML-DSA (Dilithium) for Quantum-Resistant Authentication:**

| Parameter | ML-DSA-44 | ML-DSA-65 | ML-DSA-87 |
|-----------|-----------|-----------|-----------|
| Security Level | 128-bit | 192-bit | 256-bit |
| Public Key Size | 1,312 bytes | 1,952 bytes | 2,592 bytes |
| Signature Size | 2,420 bytes | 3,293 bytes | 4,595 bytes |

**Hybrid Signature Scheme:**
- Combine Ed25519/ECDSA with ML-DSA
- Sign message with both algorithms
- Verifier checks both signatures
- Protects against both classical and quantum attacks

---

## 3. Key Management Infrastructure

### 3.1 Pre-Shared Keys (PSK) for Mesh

**PSK Hierarchy for Tactical Operations:**

```
Master Network Key (MNK) - 256-bit
├── Daily Key (DK) = HKDF(MNK, "day:" || date)
│   ├── Channel Key (CK) = HKDF(DK, "channel:" || channel_id)
│   │   ├── Group Encryption Key (GEK)
│   │   ├── Group Authentication Key (GAK)
│   │   └── Key Encryption Key (KEK)
│   └── Device Pairwise Keys = HKDF(DK, "pairwise:" || device_a || device_b)
│
├── Emergency Key (EK) = HKDF(MNK, "emergency:" || emergency_code)
│   └── Used for emergency override communications
│
└── Revocation Key (RK) = HKDF(MNK, "revoke:" || timestamp)
    └── Used for emergency key revocation broadcast
```

**PSK Distribution via MLS:**
```kotlin
class PSKDistributionViaMLS {
    
    fun injectPSKIntoMLSGroup(
        mlsGroup: MLSGroup,
        psk: ByteArray,
        pskId: String
    ) {
        // Create PreSharedKey proposal
        val pskProposal = Proposal.preSharedKey(
            PreSharedKeyID(
                pskId,
                psk // The actual pre-shared key
            )
        )
        
        // Commit the proposal
        val commit = mlsGroup.commit(listOf(pskProposal))
        
        // Distribute to all members via MLS
        broadcastCommit(commit)
    }
}
```

### 3.2 Public Key Infrastructure (PKI)

**Hierarchical PKI Architecture:**

```
┌─────────────────────────────────────────────────────────────────┐
│                   TACTICAL PKI HIERARCHY                        │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│                         ┌──────────────┐                        │
│                         │   Root CA    │                        │
│                         │ (Offline HSM)│                        │
│                         └──────┬───────┘                        │
│                                │                                │
│              ┌─────────────────┼─────────────────┐              │
│              │                 │                 │              │
│              ▼                 ▼                 ▼              │
│      ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│      │ Operational  │  │  Emergency   │  │   Backup     │      │
│      │    CA (OCA)  │  │     CA       │  │     CA       │      │
│      └──────┬───────┘  └──────┬───────┘  └──────┬───────┘      │
│             │                 │                 │               │
│      ┌──────┴──────┐   ┌──────┴──────┐   ┌──────┴──────┐       │
│      │   Device    │   │   Device    │   │   Device    │       │
│      │Certificates │   │Certificates │   │Certificates │       │
│      │  (LDevID)   │   │  (Emergency)│   │  (Backup)   │       │
│      └─────────────┘   └─────────────┘   └─────────────┘       │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

**Certificate Profiles:**

| Certificate Type | Validity | Key Algorithm | Use Case |
|-----------------|----------|---------------|----------|
| IDevID (Factory) | 10 years | P-384 | Device identity |
| LDevID (Operational) | 2 years | P-384/P-256 | Network access |
| ACP Certificate | 90 days | P-256 | Autonomic control plane |
| PTT Group Certificate | 24 hours | X25519 | Group communications |
| Emergency Certificate | 72 hours | P-384 | Emergency override |

### 3.3 Key Rotation Strategies

**Automated Key Rotation Schedule:**

```
Key Rotation Timeline for Tactical Operation:

T+0:00  ──────────────────────────────────────────────────────────
        [Operation Start]
        • Generate operation-specific Master Key
        • Issue 90-day device certificates
        • Establish MLS group with 10,000 member capacity
        
T+4:00  ──────────────────────────────────────────────────────────
        [First Rotation]
        • Rotate SRTP session keys (every 2^48 packets or 4 hours)
        • Update MLS epoch (forward secrecy refresh)
        • Refresh DTLS session keys
        
T+24:00 ──────────────────────────────────────────────────────────
        [Daily Rotation]
        • Rotate daily pre-shared keys
        • Issue new PTT group certificates
        • Update channel encryption keys
        
T+168:00 ─────────────────────────────────────────────────────────
        [Weekly Rotation]
        • Rotate long-term signing keys
        • Update certificate revocation lists
        • Audit key usage logs
```

**Key Rotation Implementation:**

```kotlin
class KeyRotationManager {
    private val rotationSchedule = mapOf(
        KeyType.SRTP_SESSION to Duration.ofHours(4),
        KeyType.DTLS_SESSION to Duration.ofHours(12),
        KeyType.MLS_EPOCH to Duration.ofHours(24),
        KeyType.PSK_DAILY to Duration.ofHours(24),
        KeyType.PTT_GROUP to Duration.ofHours(24),
        KeyType.DEVICE_CERT to Duration.ofDays(90)
    )
    
    suspend fun performRotation(keyType: KeyType) {
        when (keyType) {
            KeyType.SRTP_SESSION -> rotateSRTPSessionKeys()
            KeyType.MLS_EPOCH -> rotateMLSEpoch()
            KeyType.PSK_DAILY -> rotateDailyPSK()
            KeyType.PTT_GROUP -> rotatePTTGroupKeys()
            else -> standardRotation(keyType)
        }
    }
    
    private suspend fun rotateMLSEpoch() {
        // MLS Commit with Update proposal for self
        val updateProposal = mlsGroup.createUpdateProposal()
        val commit = mlsGroup.commit(listOf(updateProposal))
        
        // Broadcast to group
        broadcastToGroup(commit)
        
        // Verify all members have updated
        awaitEpochConfirmation()
    }
}
```

### 3.4 Forward Secrecy

**Forward Secrecy Implementation:**

Forward secrecy ensures that session keys cannot be compromised even if long-term private keys are compromised.

```
Forward Secrecy Chain for PTT:

Long-term Identity Key (IK) ──┐
                              ├──> Ephemeral Key Agreement
Long-term Signed PreKey (SPK)─┘      (X25519 + ML-KEM-768)
         │                              │
         │                              ▼
         │                    Ephemeral Session Keys
         │                         (Per-session)
         │                              │
         ▼                              ▼
    Post-compromise                Message Keys
    Recovery via                      (Per-message)
    Ratcheting                           │
         │                               ▼
         │                    ┌──────────────────┐
         │                    │  Message Chain   │
         └───────────────────>│  KDF Ratchet     │
                              └──────────────────┘
```

**Requirements:**
- Ephemeral keys generated per-session
- Ephemeral keys deleted immediately after use
- Key material never persisted
- Automatic session key rotation every 2^48 packets

### 3.5 Post-Compromise Security

**Post-Compromise Security via Continuous Key Update:**

```kotlin
class PostCompromiseSecurity {
    
    // Triggered every 4 hours or on suspicion of compromise
    fun performSelfUpdate() {
        // 1. Generate new ephemeral key pair
        val newKeyPair = generateEphemeralKeyPair()
        
        // 2. Create MLS Update proposal
        val updateProposal = MLSUpdateProposal(newKeyPair.publicKey)
        
        // 3. Commit to group
        val commit = mlsGroup.commit(listOf(updateProposal))
        
        // 4. Securely delete old private keys
        secureDelete(oldPrivateKey)
        
        // 5. Broadcast commit to all group members
        broadcast(commit)
    }
    
    // Auto-trigger on compromise detection
    fun onCompromiseDetected() {
        // Immediate emergency rotation
        performSelfUpdate()
        
        // Notify security officer
        alertSecurityTeam()
        
        // Log incident
        auditLog.logSecurityEvent(SecurityEvent.KEY_COMPROMISE_DETECTED)
    }
}
```

### 3.6 Hardware Security Modules (HSM)

**HSM Integration Architecture:**

```
┌─────────────────────────────────────────────────────────────────┐
│                   HSM INTEGRATION OPTIONS                       │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  Option 1: Samsung Knox StrongBox (Android)                     │
│  ┌──────────────┐  ┌──────────────┐                            │
│  │  Android App │  │  StrongBox   │                            │
│  │              │──│   SE (HSM)   │                            │
│  │ • Key usage  │  │ • Key gen    │                            │
│  │ • Crypto ops │  │ • Private key│                            │
│  │ • Protocol   │  │   storage    │                            │
│  └──────────────┘  └──────────────┘                            │
│                                                                 │
│  Option 2: External HSM (Tactical Gateway)                      │
│  ┌──────────┐      ┌──────────┐      ┌──────────┐              │
│  │  Android │<────>│  Radio   │<────>│  USB HSM │              │
│  │  Device  │ WiFi │ Gateway  │ USB  │ (NitroKey│              │
│  └──────────┘      └──────────┘      └──────────┘              │
│                                                                 │
│  Option 3: Cloud HSM (when backend available)                   │
│  ┌──────────┐      ┌──────────┐      ┌──────────┐              │
│  │  Android │<────>│  Radio   │<────>│ Cloud HSM│              │
│  │  Device  │ Mesh │          │ SAT  │ (AWS/Azure│              │
│  └──────────┘      └──────────┘      └──────────┘              │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

**Android StrongBox Key Generation:**

```kotlin
fun generateStrongBoxKey(): SecretKey {
    val keyGenerator = KeyGenerator.getInstance(
        KeyProperties.KEY_ALGORITHM_AES,
        "AndroidKeyStore"
    )
    
    val keyGenSpec = KeyGenParameterSpec.Builder(
        "strongbox_ptt_key",
        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
    )
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setKeySize(256)
        .setIsStrongBoxBacked(true) // Require StrongBox
        .setUserAuthenticationRequired(true)
        .setUserAuthenticationValidityDurationSeconds(300)
        .build()
    
    keyGenerator.init(keyGenSpec)
    return keyGenerator.generateKey()
}
```

---

## 4. FIPS 140-2/140-3 Compliance

### 4.1 FIPS 140-2/140-3 Security Levels

| Level | Description | Requirements | Tactical PTT Applicability |
|-------|-------------|--------------|---------------------------|
| **Level 1** | Software-based | Production-grade components | Mobile apps, firmware |
| **Level 2** | Tamper-evident | Tamper-evident coatings/seals | Field devices, radios |
| **Level 3** | Tamper-resistant | Active tamper detection & zeroization | Secure handsets, HSMs |
| **Level 4** | Tamper-proof | Environmental failure protection | Classified networks |

### 4.2 FIPS 140-3 Transition Timeline

```
FIPS 140-3 Transition Schedule:

2020 ────── FIPS 140-3 published
    │
2022 ────── No new FIPS 140-2 submissions accepted
    │
2024 ────── FIPS 140-2 validation certificates still issued
    │
2026 ────── FIPS 140-2 moves to "Historical" list
    │        (Existing systems can continue using)
    │
Future ──── All new systems must use FIPS 140-3
```

### 4.3 Validated Cryptographic Modules

**FIPS 140-2/140-3 Validated Modules for Android:**

| Module | Vendor | Level | Algorithms | Certificate |
|--------|--------|-------|------------|-------------|
| OpenSSL FIPS | OpenSSL | 1 | AES, SHA, HMAC, RSA | #XXXX |
| BoringSSL | Google | 1 | AES-GCM, ECDSA, HKDF | #YYYY |
| Samsung Knox SDK | Samsung | 2 | AES, RSA, ECDSA | #ZZZZ |
| wolfCrypt | wolfSSL | 1 | AES, ChaCha20, Curve25519 | #AAAA |

**Recommended for MeshRider Wave:**
- **Android:** Samsung Knox SDK (Level 2) + BoringSSL
- **LEDE Firmware:** wolfCrypt or OpenSSL FIPS module

### 4.4 Android Keystore Integration

**FIPS-Compliant Key Storage:**

```kotlin
class FIPSKeyStoreManager(context: Context) {
    private val keyStore = KeyStore.getInstance("AndroidKeyStore")
    
    init {
        keyStore.load(null)
    }
    
    fun generateFIPSCompliantKeyPair(alias: String): KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC,  // ECDSA for FIPS
            "AndroidKeyStore"
        )
        
        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        )
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp384r1")) // P-384
            .setDigests(KeyProperties.DIGEST_SHA384)
            .setUserAuthenticationRequired(true)
            .setUserAuthenticationValidityDurationSeconds(300)
            .setAttestationChallenge(generateAttestationChallenge())
            .build()
        
        keyPairGenerator.initialize(spec)
        return keyPairGenerator.generateKeyPair()
    }
    
    // Verify key resides in secure hardware (TEE or StrongBox)
    fun isKeyInSecureHardware(alias: String): Boolean {
        val entry = keyStore.getEntry(alias, null) as? KeyStore.PrivateKeyEntry
            ?: return false
        
        val keyInfo = KeyFactory.getInstance(
            entry.privateKey.algorithm,
            "AndroidKeyStore"
        ).getKeySpec(entry.privateKey, KeyInfo::class.java)
        
        return keyInfo.securityLevel == KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT ||
               keyInfo.securityLevel == KeyProperties.SECURITY_LEVEL_STRONGBOX
    }
}
```

### 4.5 Samsung Knox TEE

**Samsung Knox SDK for Tactical Security:**

```kotlin
// Samsung Knox SDK integration for enhanced security
class KnoxSecurityManager(context: Context) {
    private val enterpriseDeviceManager: EnterpriseDeviceManager
    private val restrictionPolicy: RestrictionPolicy
    
    init {
        enterpriseDeviceManager = EnterpriseDeviceManager.getInstance(context)
        restrictionPolicy = enterpriseDeviceManager.restrictionPolicy
    }
    
    // Enable FIPS mode for all cryptographic operations
    fun enableFIPSMode(): Boolean {
        return try {
            // Knox provides FIPS-validated crypto implementations
            restrictionPolicy.setCameraState(false) // Disable camera if needed
            restrictionPolicy.setScreenCapture(false) // Prevent screenshots
            true
        } catch (e: SecurityException) {
            false
        }
    }
    
    // Configure TIMA (TrustZone-based Integrity Measurement Architecture)
    fun enableTIMA(): Boolean {
        // TIMA provides runtime integrity measurement
        // Requires Knox Premium SDK license
        return enterpriseDeviceManager.getTimaKeystore() != null
    }
}
```

### 4.6 ARM TrustZone

**TrustZone Architecture for PTT:**

```
┌─────────────────────────────────────────────────────────────────┐
│                    ARM TRUSTZONE ARCHITECTURE                   │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  Normal World (Rich OS)         Secure World (TEE)              │
│  ┌──────────────────────┐       ┌──────────────────────┐       │
│  │  Android OS          │       │  Trusted OS          │       │
│  │  ┌────────────────┐  │       │  ┌────────────────┐  │       │
│  │  │   PTT App      │  │       │  │ Crypto Driver  │  │       │
│  │  │                │  │ SMC   │  │ Key Storage    │  │       │
│  │  │ • UI           │──┼───────┼──>│ Secure Key Gen │  │       │
│  │  │ • Protocol     │  │       │  │ Random Number  │  │       │
│  │  │ • Network      │  │       │  │ Auth Tokens    │  │       │
│  │  └────────────────┘  │       │  └────────────────┘  │       │
│  │                      │       │                      │       │
│  │  ┌────────────────┐  │       │  ┌────────────────┐  │       │
│  │  │ Android        │  │       │  │ Secure Storage │  │       │
│  │  │ Keystore API   │──┼───────┼──>│ (RPMB)         │  │       │
│  │  └────────────────┘  │       │  └────────────────┘  │       │
│  └──────────────────────┘       └──────────────────────┘       │
│           │                                │                    │
│           └──────────────┬─────────────────┘                    │
│                          │                                      │
│                   Secure Monitor                                │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## 5. Network Security Architecture

### 5.1 VPN Technologies

**WireGuard for Tactical Mesh:**

WireGuard provides a modern, efficient VPN solution ideal for tactical mesh networks.

```
WireGuard Protocol Summary:
┌─────────────────────────────────────────────────────────────────┐
│  Crypto: Curve25519, ChaCha20-Poly1305, BLAKE2s, HKDF          │
│  Transport: UDP (can work over any datagram transport)          │
│  Handshake: Noise_IK pattern (identity hiding, 1-RTT)          │
│  Key Rotation: Every 2 minutes or 2^64 packets                 │
│  PMTU: 1420 bytes default (configurable)                       │
└─────────────────────────────────────────────────────────────────┘
```

**WireGuard Integration for LEDE:**

```bash
# WireGuard configuration for MeshRider Radio
# /etc/config/network

config interface 'wg0'
    option proto 'wireguard'
    option private_key '[RADIO_PRIVATE_KEY]'
    option listen_port '51820'
    list addresses '10.200.0.1/24'
    
config wireguard_wg0
    option public_key '[EUD_PUBLIC_KEY]'
    option preshared_key '[PSK]'
    option persistent_keepalive '25'
    option route_allowed_ips '1'
    list allowed_ips '10.200.0.2/32'
```

**Comparison of VPN Technologies:**

| Feature | WireGuard | OpenVPN | IPsec/IKEv2 |
|---------|-----------|---------|-------------|
| Code Size | ~4,000 LOC | ~100,000 LOC | ~400,000 LOC |
| Crypto | Modern (ChaCha20) | OpenSSL (configurable) | Kernel-based |
| Performance | High | Medium | High (with HW) |
| Setup | Simple | Complex | Complex |
| Roaming | Excellent | Good | Good |
| Auditability | High | Medium | Low |
| FIPS Compliance | No | Yes (OpenSSL FIPS) | Yes |

### 5.2 MACsec (Layer 2 Encryption)

**MACsec for Radio-to-Radio Links:**

```
┌─────────────────────────────────────────────────────────────────┐
│                     MACsec IMPLEMENTATION                       │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  Radio A <──────────────────────────────────────────> Radio B  │
│                                                                 │
│  ┌──────────────┐                              ┌──────────────┐│
│  │   MKA        │      802.1AE Encrypted       │   MKA        ││
│  │ Key Agreement│<────────────────────────────>│ Key Agreement││
│  └──────┬───────┘                              └──────┬───────┘│
│         │                                            │          │
│         ▼                                            ▼          │
│  ┌──────────────┐                              ┌──────────────┐│
│  │   SAK        │                              │   SAK        ││
│  │ (AES-128/256)│                              │ (AES-128/256)││
│  └──────┬───────┘                              └──────┬───────┘│
│         │                                            │          │
│         ▼                                            ▼          │
│  ┌──────────────┐                              ┌──────────────┐│
│  │  Ethernet    │      Encrypted Frame         │  Ethernet    ││
│  │  Frame       │<────────────────────────────>│  Frame       ││
│  └──────────────┘                              └──────────────┘│
│                                                                 │
│  MKA: MACsec Key Agreement (802.1X-2010)                        │
│  SAK: Secure Association Key                                     │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 5.3 Zero Trust Architecture

**Zero Trust Principles for Tactical PTT:**

```
┌─────────────────────────────────────────────────────────────────┐
│              ZERO TRUST ARCHITECTURE FOR TACTICAL PTT           │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  Principle 1: Never Trust, Always Verify                        │
│  ├── Every device authenticates before network access           │
│  ├── Every packet is encrypted and authenticated                │
│  └── Continuous verification of device health                   │
│                                                                 │
│  Principle 2: Least Privilege Access                            │
│  ├── Role-based access control (RBAC)                           │
│  ├── Time-bound credentials                                     │
│  └── Dynamic authorization based on context                     │
│                                                                 │
│  Principle 3: Assume Breach                                     │
│  ├── Micro-segmentation of network                              │
│  ├── Encryption of all data in transit                          │
│  └── Comprehensive logging and monitoring                       │
│                                                                 │
│  Principle 4: Verify Explicitly                                 │
│  ├── Multi-factor authentication where possible                 │
│  ├── Device attestation                                         │
│  └── Certificate pinning                                        │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

**Implementation:**

```kotlin
class ZeroTrustPolicyEngine {
    
    data class AccessContext(
        val deviceAttestation: DeviceAttestation,
        val userIdentity: UserIdentity,
        val networkContext: NetworkContext,
        val timeContext: TimeContext,
        val locationContext: LocationContext?
    )
    
    fun evaluateAccess(
        resource: Resource,
        action: Action,
        context: AccessContext
    ): AccessDecision {
        // Check device attestation
        if (!context.deviceAttestation.isValid) {
            return AccessDecision.DENY(DeviceAttestationFailed)
        }
        
        // Check certificate validity
        if (!context.deviceAttestation.certificateValid) {
            return AccessDecision.DENY(CertificateInvalid)
        }
        
        // Check role-based permissions
        if (!hasPermission(context.userIdentity.role, resource, action)) {
            return AccessDecision.DENY(InsufficientPermissions)
        }
        
        // Check time-based restrictions
        if (!isWithinOperationalHours(context.timeContext)) {
            return AccessDecision.DENY(OutsideOperationalHours)
        }
        
        // All checks passed
        return AccessDecision.ALLOW(
            sessionTimeout = calculateDynamicTimeout(context)
        )
    }
}
```

### 5.4 Micro-segmentation

**Network Segmentation for Tactical Operations:**

```
┌─────────────────────────────────────────────────────────────────┐
│              TACTICAL NETWORK MICRO-SEGMENTATION                │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                    Command Segment                       │   │
│  │  ┌────────┐  ┌────────┐  ┌────────┐                     │   │
│  │  │Command │  │ Intel  │  │ Comms  │  [Highest Security]   │   │
│  │  │ Center │  │   Hub  │  │ Officer│                     │   │
│  │  └────────┘  └────────┘  └────────┘                     │   │
│  └─────────────────────────────────────────────────────────┘   │
│                              │                                  │
│                              ▼                                  │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                   Operations Segment                     │   │
│  │  ┌────────┐  ┌────────┐  ┌────────┐  ┌────────┐        │   │
│  │  │ Squad  │  │ Squad  │  │ Medic  │  │ Scout  │        │   │
│  │  │ Alpha  │  │  Beta  │  │  Team  │  │  Team  │        │   │
│  │  └────────┘  └────────┘  └────────┘  └────────┘        │   │
│  └─────────────────────────────────────────────────────────┘   │
│                              │                                  │
│                              ▼                                  │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                  Support Segment                         │   │
│  │  ┌────────┐  ┌────────┐  ┌────────┐                     │   │
│  │  │Supply  │  │ Medical│  │  UAV   │  [Limited Access]     │   │
│  │  │  Convoy│  │ Station│  │ Control│                     │   │
│  │  └────────┘  └────────┘  └────────┘                     │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
│  Segments isolated by cryptographic boundaries (different keys) │
│  Inter-segment communication requires explicit authorization    │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 5.5 Certificate Pinning

**Certificate Pinning Implementation:**

```kotlin
class CertificatePinningConfig {
    
    fun createPinnedClient(): OkHttpClient {
        val certificatePinner = CertificatePinner.Builder()
            .add("tactical.meshrider.com", 
                "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=") // Primary
            .add("tactical.meshrider.com", 
                "sha256/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=") // Backup
            .add("backup.meshrider.com",
                "sha256/CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC=")
            .build()
        
        return OkHttpClient.Builder()
            .certificatePinner(certificatePinner)
            .build()
    }
    
    // Network Security Config for Android
    fun getNetworkSecurityConfig(): String {
        return """
        <?xml version="1.0" encoding="utf-8"?>
        <network-security-config>
            <domain-config>
                <domain includeSubdomains="true">tactical.meshrider.com</domain>
                <pin-set expiration="2025-12-31">
                    <pin digest="SHA-256">AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=</pin>
                    <pin digest="SHA-256">BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=</pin>
                </pin-set>
                <trust-anchors>
                    <certificates src="system"/>
                </trust-anchors>
            </domain-config>
        </network-security-config>
        """.trimIndent()
    }
}
```

---

## 6. Threat Modeling & Countermeasures

### 6.1 STRIDE Methodology

**STRIDE Threat Analysis for PTT:**

| Threat | Category | Description | Risk Level | Mitigation |
|--------|----------|-------------|------------|------------|
| Eavesdropping | Information Disclosure | Unauthorized interception of voice/data | **Critical** | E2E encryption (AES-256-GCM/ChaCha20) |
| Man-in-the-Middle | Spoofing, Tampering | Attacker intercepts/modifies traffic | **Critical** | Certificate pinning, mutual auth |
| Replay Attacks | Tampering | Replaying captured PTT sessions | **High** | Sequence numbers, timestamps, nonces |
| Jamming | Denial of Service | RF interference to block communications | **High** | Frequency hopping, mesh redundancy |
| Impersonation | Spoofing | Attacker poses as legitimate user | **Critical** | Strong authentication, biometrics |
| Key Compromise | Information Disclosure | Theft of cryptographic keys | **Critical** | HSM storage, key rotation, TEE |
| Traffic Analysis | Information Disclosure | Deriving intel from metadata | **Medium** | Constant-rate padding, cover traffic |
| Supply Chain | All | Compromised hardware/software | **High** | Secure boot, code signing, attestation |

### 6.2 PTT-Specific Threats

**Floor Control Hijacking:**

```
Attack: Attacker sends spoofed floor control messages to take control

Countermeasures:
├── Authenticate all floor control messages (HMAC-SHA256)
├── Sequence numbers to prevent replay
├── Rate limiting on floor requests
└── Timeout with automatic floor release (30 seconds max)
```

**Priority Override Abuse:**

```
Attack: Unauthorized use of emergency priority channels

Countermeasures:
├── Multi-factor authentication for emergency override
├── Cryptographic attestation of device role
├── Audit logging of all priority overrides
└── Callback verification for emergency calls
```

**Late Entry Attack:**

```
Attack: Compromised device joins group late to access future keys

Countermeasures:
├── MLS prevents access to past epochs
├── Forward secrecy per epoch
├── Member validation on every join
└── Key isolation between epochs
```

### 6.3 Countermeasures Matrix

| Threat | Technical Control | Operational Control | Detection |
|--------|-------------------|---------------------|-----------|
| Eavesdropping | AES-256-GCM E2E encryption | Regular key rotation | Traffic anomaly detection |
| MITM | Certificate pinning, mTLS | Out-of-band verification | Certificate mismatch alerts |
| Replay | 64-bit sequence numbers, timestamps | Clock synchronization | Sequence gap detection |
| Jamming | Mesh networking, multiple paths | Physical security patrols | Signal strength monitoring |
| Impersonation | X.509 certificates + biometrics | Regular identity checks | Failed auth logging |
| Key Theft | HSM/TEE storage | Physical access controls | Key extraction attempts |
| Traffic Analysis | Cover traffic, padding | Operational noise | Pattern analysis |
| Supply Chain | Secure boot, attestation | Trusted supplier program | Hash verification |

---

## 7. Security Testing Framework

### 7.1 OWASP MASVS Compliance

**Mobile Application Security Verification Standard (MASVS) Level 2 (Defense-in-Depth):**

| Requirement | Description | Implementation |
|-------------|-------------|----------------|
| MSTG-ARCH-1 | All app components identified | Architecture documentation |
| MSTG-ARCH-2 | Security controls enforced on server | Backend validation |
| MSTG-ARCH-3 | High-level architecture defined | Threat model documented |
| MSTG-ARCH-4 | Data considered sensitive identified | Classification schema |
| MSTG-ARCH-5 | Crypto keys managed independently | HSM/Keystore usage |
| MSTG-ARCH-6 | Replay attacks prevented | Sequence numbers, nonces |
| MSTG-ARCH-7 | Session management secure | Short-lived tokens |
| MSTG-ARCH-8 | Security decisions on server | Server-side authorization |
| MSTG-ARCH-9 | Biometric auth uses system APIs | BiometricPrompt API |
| MSTG-ARCH-10 | Sensitive data not logged | Logging scrubbing |
| MSTG-ARCH-11 | Third-party components verified | SBOM, dependency scanning |
| MSTG-ARCH-12 | Debug info removed from release | ProGuard/R8 rules |

### 7.2 Static Analysis (SAST)

**SAST Tools for Kotlin/Android:**

```yaml
# GitHub Actions SAST Pipeline
name: Security Scan

on: [push, pull_request]

jobs:
  sast:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      # Detekt - Kotlin static analysis
      - name: Run Detekt
        uses: detekt/detekt-action@v1
        with:
          config: detekt-config.yml
      
      # MobSF - Mobile Security Framework
      - name: Run MobSF
        uses: MobSF/mobsfscan-action@v1
        with:
          args: '--type android'
      
      # Semgrep - Lightweight static analysis
      - name: Run Semgrep
        uses: returntocorp/semgrep-action@v1
        with:
          config: >-
            p/security-audit
            p/owasp-mobile
            p/kotlin
      
      # Dependency vulnerability scanning
      - name: Run OWASP Dependency-Check
        uses: dependency-check/Dependency-Check_Action@main
        with:
          project: 'meshrider-wave'
          path: '.'
          format: 'ALL'
```

**Custom Security Rules:**

```kotlin
// Detekt custom rule for crypto misuse
class WeakCryptoUsage(config: Config) : Rule(config) {
    override val issue = Issue(
        "WeakCryptoUsage",
        Severity.Defect,
        "Use of weak cryptographic algorithms",
        Debt.TWENTY_MINS
    )
    
    private val weakAlgorithms = listOf("DES", "3DES", "RC4", "MD5", "SHA1")
    
    override fun visitCallExpression(expression: KtCallExpression) {
        val methodName = expression.calleeExpression?.text ?: return
        
        if (weakAlgorithms.any { methodName.contains(it, ignoreCase = true) }) {
            report(CodeSmell(
                issue,
                Entity.from(expression),
                "Weak cryptographic algorithm '$methodName' detected. " +
                "Use AES-256-GCM or ChaCha20-Poly1305 instead."
            ))
        }
    }
}
```

### 7.3 Dynamic Analysis (DAST)

**Dynamic Testing Tools:**

| Tool | Purpose | Integration |
|------|---------|-------------|
| OWASP ZAP | Web/API vulnerability scanning | CI/CD pipeline |
| Burp Suite | Manual penetration testing | Security assessments |
| Frida | Runtime instrumentation | Mobile testing |
| Objection | Mobile runtime exploration | iOS/Android testing |
| Drozer | Android security assessment | Component testing |

**Frida Script for PTT Security Testing:**

```javascript
// Frida script to intercept crypto operations
Interceptor.attach(Module.findExportByName(null, "EVP_CipherInit_ex"), {
    onEnter: function(args) {
        var cipherName = Memory.readCString(args[1]);
        console.log("[CRYPTO] Cipher init: " + cipherName);
        
        // Check for weak ciphers
        if (cipherName.indexOf("DES") !== -1 || cipherName.indexOf("RC4") !== -1) {
            console.warn("[WARNING] Weak cipher detected: " + cipherName);
        }
    }
});

// Hook KeyStore operations
Interceptor.attach(Java.use("android.security.keystore.KeyGenParameterSpec$Builder").setKeySize.implementation, {
    onEnter: function(args) {
        var keySize = args[1];
        console.log("[KEYSTORE] Key size: " + keySize);
        
        if (keySize < 256) {
            console.error("[ERROR] Key size too small: " + keySize);
        }
    }
});
```

### 7.4 Fuzzing

**Fuzzing Targets for PTT:**

```c
// libFuzzer target for RTP packet parsing
extern "C" int LLVMFuzzerTestOneInput(const uint8_t *data, size_t size) {
    // Create RTP packet from fuzzer input
    RTPPacket packet;
    
    // Attempt to parse
    if (RTP_ParsePacket(&packet, data, size) == SUCCESS) {
        // Validate packet structure
        RTP_ValidatePacket(&packet);
        
        // Attempt decryption (should fail for random data)
        uint8_t plaintext[1500];
        size_t plaintext_len;
        
        SRTP_Decrypt(context, &packet, plaintext, &plaintext_len);
    }
    
    return 0;
}
```

**AFL++ Fuzzing Setup:**

```bash
# Build with AFL++ instrumentation
export CC=afl-clang-fast
export CXX=afl-clang-fast++

# Build the target
make clean
make

# Run fuzzer with dictionary
afl-fuzz -i inputs/ -o findings/ \
    -x rtp.dict \
    ./target @@
```

### 7.5 Penetration Testing Methodology

**PTT-Specific Penetration Test Scenarios:**

| Test ID | Scenario | Objective | Tools |
|---------|----------|-----------|-------|
| PT-001 | Eavesdropping on multicast | Verify encryption strength | Wireshark, custom decoder |
| PT-002 | Floor control hijacking | Test authentication | Custom PTT protocol fuzzer |
| PT-003 | Key extraction from memory | Assess runtime security | Frida, /proc/mem analysis |
| PT-004 | Certificate bypass | Test PKI implementation | mitmproxy, custom CA |
| PT-005 | Replay attack | Verify anti-replay measures | Packet capture replay |
| PT-006 | DoS via flooding | Test resilience | hping3, custom load generator |
| PT-007 | Supply chain validation | Verify integrity | Binary analysis, signature check |
| PT-008 | Side-channel analysis | Test timing/cache attacks | Custom timing measurement |

---

## 8. Post-Quantum Cryptography Migration

### 8.1 NIST Post-Quantum Standards

**Released Standards (August 2024):**

| Standard | Algorithm | Type | Security Level | FIPS |
|----------|-----------|------|----------------|------|
| FIPS 203 | ML-KEM | KEM | 128-256 bit | Yes |
| FIPS 204 | ML-DSA | Signature | 128-256 bit | Yes |
| FIPS 205 | SLH-DSA | Signature | 128-256 bit | Yes |

**In Development:**

| Algorithm | Type | Expected | Notes |
|-----------|------|----------|-------|
| Falcon | Signature | 2025 | NTRU-based, smaller signatures |
| HQC | KEM | 2026 | Code-based, conservative |

### 8.2 Hybrid Classical/PQC Approach

**Hybrid Key Exchange Architecture:**

```
┌─────────────────────────────────────────────────────────────────┐
│              HYBRID KEY EXCHANGE (PQC + TRADITIONAL)            │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  Traditional: X25519 (128-bit classical security)              │
│  Post-Quantum: ML-KEM-768 (192-bit quantum security)           │
│                                                                 │
│  Combined Secret Derivation:                                    │
│  shared_secret = KDF(X25519_shared_secret || ML_KEM_ss)        │
│                                                                 │
│  Security Properties:                                           │
│  ├── Secure if X25519 broken (quantum computer)                 │
│  ├── Secure if ML-KEM broken (implementation flaw)              │
│  └── No downgrade attacks possible                              │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 8.3 Migration Strategy

**Phased Migration Timeline:**

```
Phase 1: Hybrid Deployment (2024-2026)
├── Deploy hybrid key exchange alongside traditional
├── Enable PQC algorithms in "prefer but not require" mode
├── Monitor performance impact on battery/throughput
└── Gather telemetry on compatibility

Phase 2: PQC-Preferred (2026-2028)
├── Make hybrid mode the default
├── Deprecate pure classical key exchange
├── Update compliance documentation
└── Train operators on new procedures

Phase 3: PQC-Required (2028-2030)
├── Require hybrid or pure PQC for new deployments
├── Maintain backward compatibility for legacy systems
├── Full FIPS 203/204 compliance
└── Complete migration documentation

Phase 4: Classical Deprecation (2030-2035)
├── NIST deprecates classical algorithms
├── Remove classical-only support
├── Pure PQC operation (if quantum threat realized)
└── Archive migration documentation
```

### 8.4 Implementation Guidelines

**libsodium with PQC Extension:**

```c
// Hybrid X25519 + ML-KEM-768 using liboqs + libsodium
#include <oqs/oqs.h>
#include <sodium.h>

int hybrid_key_exchange(
    uint8_t *shared_secret,      // Output: 32 bytes
    const uint8_t *x25519_sk,    // Input: X25519 secret key
    const uint8_t *x25519_pk,    // Input: X25519 public key
    const uint8_t *mlkem_sk,     // Input: ML-KEM secret key
    const uint8_t *mlkem_ct      // Input: ML-KEM ciphertext
) {
    uint8_t x25519_ss[crypto_scalarmult_BYTES];
    uint8_t mlkem_ss[OQS_KEM_ml_kem_768_length_shared_secret];
    
    // X25519 shared secret
    crypto_scalarmult(x25519_ss, x25519_sk, x25519_pk);
    
    // ML-KEM decapsulation
    OQS_KEM_decaps(OQS_KEM_ml_kem_768, mlkem_ss, mlkem_ct, mlkem_sk);
    
    // Combine with HKDF
    uint8_t combined[crypto_scalarmult_BYTES + 
                     OQS_KEM_ml_kem_768_length_shared_secret];
    memcpy(combined, x25519_ss, crypto_scalarmult_BYTES);
    memcpy(combined + crypto_scalarmult_BYTES, mlkem_ss, 
           OQS_KEM_ml_kem_768_length_shared_secret);
    
    crypto_generichash(shared_secret, 32, combined, sizeof(combined), 
                       NULL, 0);
    
    // Clear sensitive data
    sodium_memzero(x25519_ss, sizeof(x25519_ss));
    sodium_memzero(mlkem_ss, sizeof(mlkem_ss));
    sodium_memzero(combined, sizeof(combined));
    
    return 0;
}
```

---

## 9. Implementation Roadmap

### 9.1 Phase 1: Foundation (Months 1-3)

- [ ] Implement AES-256-GCM and ChaCha20-Poly1305 in Android Keystore
- [ ] Integrate libsodium with FIPS validation
- [ ] Set up MLS group management framework
- [ ] Implement SRTP for voice encryption
- [ ] Establish certificate management infrastructure

### 9.2 Phase 2: Hardening (Months 4-6)

- [ ] Add Samsung Knox StrongBox support
- [ ] Implement certificate pinning
- [ ] Deploy WireGuard for mesh VPN
- [ ] Add MLS with ratchet tree optimization
- [ ] Implement comprehensive key rotation

### 9.3 Phase 3: Compliance (Months 7-9)

- [ ] Achieve FIPS 140-2 Level 1 validation
- [ ] Complete OWASP MASVS L2 assessment
- [ ] Implement zero-trust policy engine
- [ ] Deploy network micro-segmentation
- [ ] Complete security testing framework

### 9.4 Phase 4: Future-Proofing (Months 10-12)

- [ ] Implement hybrid PQC key exchange
- [ ] Add ML-KEM-768 and ML-DSA-65 support
- [ ] Complete quantum-resistant migration plan
- [ ] Achieve NSA CSfC compliance
- [ ] Final security audit and penetration test

---

## 10. Compliance Summary

| Standard | Level | Status | Target Date |
|----------|-------|--------|-------------|
| FIPS 140-2 | Level 1 | In Progress | Q3 2025 |
| FIPS 140-3 | Level 1 | Planned | Q1 2026 |
| OWASP MASVS | Level 2 | In Progress | Q2 2025 |
| NSA CSfC | Capability Package | Planned | Q4 2025 |
| Common Criteria | EAL4+ | Future | 2026 |

---

## 11. References

### Standards and RFCs
- RFC 9420: The Messaging Layer Security (MLS) Protocol
- RFC 3711: The Secure Real-time Transport Protocol (SRTP)
- RFC 6347: Datagram Transport Layer Security Version 1.2
- RFC 8439: ChaCha20 and Poly1305 for IETF Protocols
- RFC 8994: An Autonomic Control Plane (ACP)
- RFC 9180: Hybrid Public Key Encryption (HPKE)

### NIST Publications
- FIPS 140-2: Security Requirements for Cryptographic Modules
- FIPS 140-3: Security Requirements for Cryptographic Modules
- FIPS 203: Module-Lattice-Based Key-Encapsulation Mechanism Standard (ML-KEM)
- FIPS 204: Module-Lattice-Based Digital Signature Standard (ML-DSA)
- FIPS 205: Stateless Hash-Based Digital Signature Standard (SLH-DSA)
- SP 800-56A: Recommendation for Pair-Wise Key Establishment Schemes
- SP 800-57: Recommendation for Key Management

### Industry Specifications
- 3GPP TS 24.379: Mission Critical Push to Talk (MCPTT)
- Signal Protocol Specifications (Double Ratchet)
- WireGuard Protocol Specification
- OWASP Mobile Security Testing Guide (MSTG)

---

*Document Version: 1.0*
*Last Updated: February 2026*
*Classification: UNCLASSIFIED*
*Distribution: MeshRider Engineering Team*
