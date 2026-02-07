/*
 * Mesh Rider Wave - VAD Processor
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 *
 * MILITARY-GRADE Voice Activity Detection Header
 * - WebRTC VAD algorithm integration
 * - Frame-by-frame processing (30ms at 16kHz)
 * - Configurable aggressiveness levels
 */

#ifndef MESH_RIDER_VAD_PROCESSOR_H
#define MESH_RIDER_VAD_PROCESSOR_H

#include <memory>
#include <cstddef>
#include <cstdint>

/**
 * MILITARY-GRADE Voice Activity Detection Processor
 *
 * Features:
 * - Energy-based VAD with adaptive threshold
 * - Configurable aggressiveness (0-3)
 * - Frame-by-frame processing
 * - Voice probability output
 *
 * Aggressiveness Levels:
 * - 0: LOW - least sensitive, fewest false positives
 * - 1: MEDIUM
 * - 2: MEDIUM-HIGH (default)
 * - 3: HIGH - most sensitive, may trigger on noise
 */
class VadProcessor {
public:
    /**
     * Create VAD processor
     *
     * @param sampleRate Sample rate in Hz (typically 16000 for PTT)
     * @param frameSizeMs Frame size in milliseconds (typically 30ms)
     */
    VadProcessor(int sampleRate, int frameSizeMs);

    /**
     * Destroy VAD processor
     */
    ~VadProcessor();

    /**
     * Process audio frame for voice activity
     *
     * @param audio Input audio samples (16-bit PCM, mono)
     * @param length Number of samples in the frame
     * @return true if voice detected, false otherwise
     */
    bool processFrame(const int16_t* audio, size_t length);

    /**
     * Set VAD aggressiveness level
     *
     * @param level Aggressiveness (0=LOW, 1=MEDIUM, 2=MEDIUM-HIGH, 3=HIGH)
     */
    void setAggressiveness(int level);

    /**
     * Get voice probability (0.0 to 1.0)
     *
     * @return Probability of voice presence
     */
    float getVoiceProbability() const;

    /**
     * Reset internal state
     */
    void reset();

    // Prevent copying
    VadProcessor(const VadProcessor&) = delete;
    VadProcessor& operator=(const VadProcessor&) = delete;

    // Allow moving
    VadProcessor(VadProcessor&&) noexcept;
    VadProcessor& operator=(VadProcessor&&) noexcept;

private:
    class Impl;
    std::unique_ptr<Impl> impl_;
};

#endif // MESH_RIDER_VAD_PROCESSOR_H
