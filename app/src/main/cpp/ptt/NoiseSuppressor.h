/*
 * Mesh Rider Wave - Noise Suppressor
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 *
 * MILITARY-GRADE Noise Suppression Header
 * - RNNoise (Recurrent Neural Network) algorithm
 * - <10ms latency overhead
 * - Multiple noise profiles for tactical environments
 */

#ifndef MESH_RIDER_NOISE_SUPPRESSOR_H
#define MESH_RIDER_NOISE_SUPPRESSOR_H

#include <memory>
#include <cstddef>
#include <cstdint>

/**
 * MILITARY-GRADE Noise Suppressor
 *
 * Uses RNNoise library for real-time noise reduction:
 * - Recurrent Neural Network-based denoising
 * - Preserves speech while removing background noise
 * - <10ms processing latency
 * - Optimized for mobile processors
 *
 * Noise Profiles:
 * - OFF: No suppression (bypass)
 * - LOW: Office/quiet environment (-15dB)
 * - MEDIUM: Urban/vehicle (-25dB)
 * - HIGH: Wind/machinery (-35dB)
 * - EXTREME: Combat/industrial (-45dB)
 */
class NoiseSuppressor {
public:
    /**
     * Create noise suppressor
     *
     * @param sampleRate Sample rate in Hz (typically 16000 for PTT)
     * @param frameSize Frame size in samples (typically 480 for 30ms at 16kHz)
     */
    NoiseSuppressor(int sampleRate, int frameSize);

    /**
     * Destroy noise suppressor
     */
    ~NoiseSuppressor();

    /**
     * Process audio frame through noise suppressor
     *
     * @param input Input audio samples (16-bit PCM, mono)
     * @param output Output buffer for processed audio
     * @param length Number of samples in the frame
     * @return Number of samples processed
     */
    int processFrame(const int16_t* input, int16_t* output, size_t length);

    /**
     * Set noise suppression level in dB
     *
     * @param suppressionDb Suppression level (0-45 dB)
     */
    void setSuppression(int suppressionDb);

    /**
     * Get current suppression level in dB
     *
     * @return Current suppression level
     */
    int getSuppression() const;

    /**
     * Reset internal state
     */
    void reset();

    /**
     * Check if suppressor is ready
     *
     * @return true if ready for processing
     */
    bool isReady() const;

    // Prevent copying
    NoiseSuppressor(const NoiseSuppressor&) = delete;
    NoiseSuppressor& operator=(const NoiseSuppressor&) = delete;

    // Allow moving
    NoiseSuppressor(NoiseSuppressor&&) noexcept;
    NoiseSuppressor& operator=(NoiseSuppressor&&) noexcept;

private:
    class Impl;
    std::unique_ptr<Impl> impl_;
};

#endif // MESH_RIDER_NOISE_SUPPRESSOR_H
