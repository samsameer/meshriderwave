/*
 * Mesh Rider Wave - Opus Codec Wrapper
 * 3GPP TS 26.179 MCPTT mandatory codec
 * Following xiph.org/libopus guidelines
 *
 * Performance: 6-24 kbps (vs 256 kbps PCM) = 10-40x bandwidth reduction
 * Latency: 20ms frame size for low-latency PTT
 */

#ifndef MESHRIDER_PTT_OPUS_CODEC_H
#define MESHRIDER_PTT_OPUS_CODEC_H

#include <cstdint>
#include <vector>
#include <memory>
#include <opus/opus.h>

namespace meshrider {
namespace ptt {

// Opus configuration per 3GPP MCPTT
constexpr int OPUS_SAMPLE_RATE = 16000;     // 16 kHz for voice
constexpr int OPUS_CHANNELS = 1;            // Mono for PTT
constexpr int OPUS_FRAME_SIZE = 960;        // 20ms @ 16kHz (16 * 20)
constexpr int OPUS_BITRATE = 12000;         // 12 kbps (MCPTT standard)
constexpr int OPUS_MAX_PACKET_SIZE = 4000;  // Max encoded frame size

// Opus application modes
enum class OpusMode {
    VOIP        = OPUS_APPLICATION_VOIP,          // Best for VoIP/PTT
    AUDIO       = OPUS_APPLICATION_AUDIO,         // Music/fidelity
    LOW_DELAY   = OPUS_APPLICATION_RESTRICTED_LOWDELAY  // Lowest latency
};

/**
 * Opus Encoder - Compresses PCM to Opus
 * Thread-safe for real-time audio processing
 */
class OpusEncoder {
public:
    OpusEncoder();
    ~OpusEncoder();

    // Initialize encoder with specified mode
    bool initialize(OpusMode mode = OpusMode::VOIP);

    // Encode PCM frame to Opus
    // Returns: number of bytes encoded, or negative on error
    int encode(const int16_t* pcm, int frameSize, uint8_t* output, int maxOutputSize);

    // Reset encoder state
    void reset();

    // Get/Set bitrate (bps)
    void setBitrate(int bitrate);  // 6000-24000 bps
    int getBitrate() const { return bitrate_; }

    // Enable/disable forward error correction
    void setFEC(bool enable);
    bool getFEC() const { return fecEnabled_; }

    // Complexity (0-10, higher = better quality but slower)
    void setComplexity(int complexity);
    int getComplexity() const { return complexity_; }

private:
    OpusEncoder* encoder_;
    int bitrate_;
    bool fecEnabled_;
    int complexity_;
};

/**
 * Opus Decoder - Decompresses Opus to PCM
 * Handles packet loss concealment
 */
class OpusDecoder {
public:
    OpusDecoder();
    ~OpusDecoder();

    // Initialize decoder
    bool initialize();

    // Decode Opus frame to PCM
    // Returns: number of samples decoded, or negative on error
    int decode(const uint8_t* input, int inputSize, int16_t* output, int frameSize);

    // Decode with PLC (Packet Loss Concealment) when packet is lost
    int decodePLC(int16_t* output, int frameSize);

    // Reset decoder state
    void reset();

    // Get last decoder error
    int getLastError() const { return lastError_; }

private:
    OpusDecoder* decoder_;
    int lastError_;
};

/**
 * Opus Codec Factory - Creates encoder/decoder pairs
 * Manages codec lifecycle
 */
class OpusCodecFactory {
public:
    static std::unique_ptr<OpusEncoder> createEncoder(OpusMode mode = OpusMode::VOIP);
    static std::unique_ptr<OpusDecoder> createDecoder();

    // Get codec info
    static const char* getVersion();
    static int getLookahead();  // Encoder lookahead in samples
};

} // namespace ptt
} // namespace meshrider

#endif // MESHRIDER_PTT_OPUS_CODEC_H
