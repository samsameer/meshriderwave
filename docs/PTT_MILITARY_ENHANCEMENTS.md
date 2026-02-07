# PTT Military-Grade Enhancements - Design Document

**Version:** 1.0 | **Date:** February 7, 2026 | **Status:** Implementation

## Overview

This document outlines the military-grade enhancements for the Mesh Rider Wave PTT system, following 3GPP MCPTT standards, Samsung Knox guidelines, and military training best practices.

---

## 1. Voice Activity Detection (VAD)

### Purpose
Automatically detect when the user is speaking to initiate transmission without manual button press in certain scenarios.

### Design
- **Algorithm:** WebRTC VAD (voice_activity_detection.h)
- **Sensitivity:** Configurable (Low/Medium/High)
- **Activation:** Requires initial PTT button press to arm, then auto-transmits on speech
- **Timeout:** 2 seconds of silence stops transmission

### Implementation
```kotlin
// PttVadDetector.kt
class PttVadDetector {
    fun initialize(sampleRate: Int, frameDurationMs: Int)
    fun processAudioFrame(audioData: ShortArray): Boolean
    fun setSensitivity(level: VadSensitivity)
}

enum class VadSensitivity { LOW, MEDIUM, HIGH }
```

### Native Integration
```cpp
// cpp/ptt/VadProcessor.cpp
using webrtc::VoiceActivityDetector;

class VadProcessor {
    VoiceActivityDetector* vad;
    bool detectVoice(const int16_t* audio, size_t length);
};
```

---

## 2. Noise Suppression

### Purpose
Reduce background noise (wind, vehicles, machinery) for clear audio in tactical environments.

### Design
- **Algorithm:** RNNoise (Recurrent Neural Network)
- **Latency:** <10ms overhead
- **Modes:**
  - OFF: No suppression
  - LOW: Office environment
  - MEDIUM: Urban/vehicle
  - HIGH: Wind/machinery
  - EXTREME: Combat/industrial

### Implementation
```kotlin
// PttNoiseSuppressor.kt
class PttNoiseSuppressor {
    fun initialize(sampleRate: Int, channels: Int)
    fun processFrame(input: ShortArray, output: ShortArray)
    fun setNoiseProfile(profile: NoiseProfile)
}

enum class NoiseProfile(val suppressionDb: Int) {
    OFF(0), LOW(15), MEDIUM(25), HIGH(35), EXTREME(45)
}
```

### Native Integration
```cpp
// cpp/ptt/NoiseSuppressor.cpp
#include <rnnoise.h>

class NoiseSuppressor {
    DenoiseState* state;
    void process(float* input, float* output);
};
```

---

## 3. Emergency Preemption with SOS Beacon

### Purpose
Allow emergency calls to preempt all other PTT traffic and broadcast SOS beacon on map.

### Design
- **Priority Levels:** EMERGENCY > HIGH > NORMAL > LOW (3GPP MCPTP TS 24.379)
- **SOS Activation:** Triple-tap XCover button + 3-second hold
- **Beacon Duration:** 5 minutes or manual cancel
- **Preemption:** Immediate floor grant, all other users muted

### Implementation
```kotlin
// EmergencyManager.kt
class EmergencyManager {
    fun activateSos(): Boolean
    fun cancelSos()
    fun sendEmergencyBeacon(location: Location)
    val isSosActive: StateFlow<Boolean>
}

// FloorControlProtocol.kt extension
fun FloorControlProtocol.requestEmergencyFloor(): Boolean
```

### ATAK CoT Integration
```kotlin
// Broadcast emergency marker to ATAK
fun sendEmergencyCot(location: Location) {
    val cotEvent = CotEvent(
        type = "a-u-G",
        uid = "SOS-${ownId}",
        how = "h-E-SOS"
    ).apply {
        point = CotPoint(location.latitude, location.longitude)
        start = time.CoordinatedTime()
        stale = time.CoordinatedTime(System.currentTimeMillis() + 300000) // 5 min
    }
    cotDispatcher.dispatch(cotEvent)
}
```

---

## 4. Audio Recording & Replay

### Purpose
Record PTT transmissions for mission replay, compliance, and training purposes.

### Design
- **Format:** Opus-encoded .ptt files
- **Encryption:** AES-256-GCM at rest
- **Metadata:** Timestamp, caller ID, location, priority
- **Storage:** Encrypted local cache with 7-day retention
- **Compliance:** Export to USB/SD card with audit trail

### Implementation
```kotlin
// PttRecordingManager.kt
class PttRecordingManager {
    fun startRecording(sessionId: String)
    fun stopRecording(): PttRecording?
    fun getRecordings(): List<PttRecording>
    fun exportRecording(recordingId: String, destination: Uri)
    fun deleteOldRecordings(retentionDays: Int)
}

data class PttRecording(
    val id: String,
    val timestamp: Instant,
    val callerId: String,
    val duration: Duration,
    val location: Location?,
    val priority: Priority,
    val filePath: String
)
```

---

## 5. Training & Gamification Module

### Purpose
Improve PTT etiquette and mission-critical communication skills through gamified training.

### Design

### Ranks & Badges
```kotlin
enum class PttRank(val xpRequired: Int, val title: String) {
    RECRUIT(0, "Recruit"),
    OPERATOR(100, "Operator"),
    SENIOR_OPERATOR(500, "Senior Operator"),
    LEAD_OPERATOR(1500, "Lead Operator"),
    COMMANDER(5000, "Commander")
}

enum class PttBadge(val title: String, val description: String) {
    FIRST_TRANSMISSION("First Words", "Complete first PTT transmission"),
    CLEAR_COMMS("Clear Comms", "Maintain 90%+ audio quality for 10 transmissions"),
    EMERGENCY_RESponder("Emergency Responder", "Successfully handle 5 emergency calls"),
    RADIO_DISCIPLINE("Radio Discipline", "Zero accidental transmissions for 50 uses"),
    NIGHT_OPS("Night Ops", "Complete training exercise at night")
}
```

### Training Scenarios
```kotlin
// PttTrainingScenario.kt
sealed class TrainingScenario {
    data object BasicTransmission : TrainingScenario()
    data object EmergencyProtocol : TrainingScenario()
    data object CoordinatedAssault : TrainingScenario()
    data object VehicleComms : TrainingScenario()
    data object StealthMode : TrainingScenario()
}

class PttTrainingEngine {
    fun startScenario(scenario: TrainingScenario)
    fun evaluatePerformance(): TrainingResult
    fun awardBadge(badge: PttBadge)
    fun getXp(): Int
    fun getRank(): PttRank
}
```

### UI Components
- Progress ring showing XP to next rank
- Badge showcase with animations
- Training scenario selection screen
- Performance analytics dashboard

---

## 6. Quality Metrics Dashboard

### Purpose
Real-time visualization of PTT system performance for situational awareness.

### Metrics Tracked
| Metric | Target | Display |
|--------|--------|---------|
| Mouth-to-Ear Latency | <250ms | Circular gauge |
| Audio Quality (PESQ) | >3.5 | 5-bar indicator |
| Packet Loss | <5% | Percentage |
| Jitter | <50ms | Graph |
| Signal Strength | -80dBm+ | 5-bar |
| Battery Impact | <5%/hr | Percentage |

---

## 7. Security Enhancements

### Key Rotation
```kotlin
// Every 60 seconds during active transmission
class PttKeyRotationManager {
    fun rotateEncryptionKey()
    fun getCurrentKeyId(): String
}
```

### Hardware Attestation
```kotlin
// Verify device hasn't been compromised
class PttAttestationManager {
    fun attestDevice(): Boolean
    fun getAttestationCertificate(): X509Certificate
}
```

---

## Implementation Priority

| Priority | Feature | Effort | Impact |
|----------|---------|--------|--------|
| P0 | Emergency Preemption + SOS | Medium | Life-saving |
| P0 | Noise Suppression | Medium | High |
| P1 | VAD | Medium | High |
| P1 | Audio Recording | High | Compliance |
| P2 | Training Module | High | Readiness |
| P3 | Key Rotation | Low | Security |

---

## Testing Strategy

### Unit Tests
- VAD detector with various audio samples
- Noise suppression with noisy recordings
- Emergency state machine
- Recording encryption/decryption

### Integration Tests
- End-to-end PTT with all features
- ATAK CoT beacon dispatching
- Cross-platform (Samsung S24+, XCover Pro)

### Field Tests
- Vehicle environment (>80km/h)
- Wind conditions (>30km/h)
- Industrial noise (>90dB)
- Network degradation (packet loss, latency)

### Performance Tests
- Battery impact measurement
- Memory leak detection
- Latency measurements
- Stress testing (24-hour continuous operation)

---

## References

- [3GPP TS 22.179 - Mission Critical Services](https://www.3gpp.org/dynareport/22179.htm)
- [3GPP TS 24.379 - MCPTT Protocol](https://www.etsi.org/deliver/etsi_ts/124300_124399/124379/17.10.00_60/ts_124379v171000p.pdf)
- [Samsung Knox PTT Best Practices](https://images.samsung.com/is/content/samsung/assets/global/p6-b2b/pilot/im-asis/business/insights/a-guide-to-pushtotalk/PTT_.pdf)
- [RNNoise Library](https://github.com/dchief117/rnnoise-android)
- [WebRTC VAD](https://webrtc.googlesource.com/src/+/refs/heads/main/modules/audio_processing/agc2/)

---

**Author:** Jabbir Basha P | DoodleLabs Singapore
**Approved:** February 7, 2026
