/*
 * Mesh Rider Wave - PTT Audio Engine (PRODUCTION-READY)
 * Following developer.android.com Oboe guidelines
 * Low-latency audio for Samsung S24+ devices
 *
 * FIXED (Feb 2026):
 * - Oboe low-latency audio path (<20ms)
 * - Fixed race conditions with atomics
 * - Added AEC (Acoustic Echo Cancellation)
 * - Proper timestamp calculation per RFC 3550
 * - Opus codec integration enabled (3GPP TS 26.179 MCPTT)
 */

#ifndef MESHRIDER_PTT_AUDIO_ENGINE_H
#define MESHRIDER_PTT_AUDIO_ENGINE_H

#include "oboe/Oboe.h"
#include <memory>
#include <atomic>
#include <functional>
#include <thread>
#include <mutex>
#include <condition_variable>
#include <queue>
#include "RtpPacketizer.h"
#include "OpusCodec.h"

namespace meshrider {
namespace ptt {

// Following AAudio performance mode guidelines
// per developer.android.com/ndk/guides/audio/aaudio
constexpr int32_t kSampleRate = 16000;       // 16kHz for voice
constexpr int32_t kChannelCount = 1;          // Mono for PTT
constexpr int32_t kFramesPerBurst = 192;      // ~12ms at 16kHz (low latency)
constexpr int32_t kOpusFrameSize = 320;       // 20ms @ 16kHz (samples)
constexpr int32_t kPcmFrameSizeBytes = 640;   // 20ms @ 16kHz (bytes, 16-bit)

// Audio state callback
class AudioEngineCallback {
public:
    virtual ~AudioEngineCallback() = default;
    virtual void onAudioReady() = 0;
    virtual void onAudioError(int errorCode) = 0;
    virtual void onAudioData(const uint8_t* data, size_t size) = 0;
};

// Forward declarations
class CaptureCallback;
class PlaybackCallback;

/**
 * PTT Audio Engine - Low latency capture/playback
 * Uses Oboe with EXCLUSIVE mode and LOW_LATENCY performance
 * Following Samsung Knox audio guidelines
 *
 * PRODUCTION FIXES:
 * - Oboe low-latency audio path
 * - Atomic state flags
 * - Proper RTP timestamp per RFC 3550
 * - AEC enabled for speakerphone
 * - Opus codec encoding/decoding (3GPP MCPTT)
 */
class AudioEngine {
public:
    AudioEngine();
    ~AudioEngine();

    // Initialize audio engine with Opus codec
    bool initialize(AudioEngineCallback* callback);

    // Start/Stop capture (TX) - now includes Opus encoding
    bool startCapture();
    void stopCapture();

    // Start/Stop playback (RX) - now includes Opus decoding
    bool startPlayback();
    void stopPlayback();

    // State queries
    bool isCapturing() const { return isCapturing_.load(); }
    bool isPlaying() const { return isPlaying_.load(); }
    int32_t getLatencyMillis() const;

    // Audio routing with AEC
    void setSpeakerOutput(bool enable);
    void setBluetoothOutput(bool enable);
    bool isAecEnabled() const { return aecEnabled_; }

    // Get codec statistics
    struct CodecStats {
        uint64_t framesEncoded;
        uint64_t framesDecoded;
        uint64_t bytesEncoded;
        uint64_t bytesTransmitted;
        double compressionRatio;
    };
    CodecStats getStats() const;

    // Enqueue received audio data from network (Opus-encoded)
    // This forwards the data to PlaybackCallback for decoding and playback
    void enqueueReceivedAudio(const uint8_t* data, size_t size);

private:
    // Oboe streams
    std::shared_ptr<oboe::AudioStream> captureStream_;
    std::shared_ptr<oboe::AudioStream> playbackStream_;

    // State flags (atomic for thread safety)
    std::atomic<bool> isCapturing_{false};
    std::atomic<bool> isPlaying_{false};
    std::atomic<bool> aecEnabled_{false};

    // Callback
    AudioEngineCallback* callback_ = nullptr;

    // Opus codec (3GPP TS 26.179 MCPTT mandatory codec)
    std::unique_ptr<OpusEncoder> opusEncoder_;
    std::unique_ptr<OpusDecoder> opusDecoder_;
    std::mutex codecMutex_;

    // Statistics
    mutable std::mutex statsMutex_;
    CodecStats stats_;

    // PCM accumulator for 20ms frame alignment
    std::vector<int16_t> pcmBuffer_;
    std::mutex pcmBufferMutex_;

    // Stream configuration following Oboe best practices
    oboe::Result createCaptureStream();
    oboe::Result createPlaybackStream();

    // Audio callbacks
    std::unique_ptr<CaptureCallback> captureCallback_;
    std::unique_ptr<PlaybackCallback> playbackCallback_;

    friend class CaptureCallback;
    friend class PlaybackCallback;
};

/**
 * Capture callback - runs on high-priority audio thread
 * Following AAudio guidelines for low latency
 * 
 * PRODUCTION FIX: Encodes to Opus before sending to network
 */
class CaptureCallback : public oboe::AudioStreamCallback {
public:
    explicit CaptureCallback(AudioEngine* engine) : engine_(engine) {}

    oboe::DataCallbackResult onAudioReady(
        oboe::AudioStream* stream,
        void* audioData,
        int32_t numFrames) override;

    void onErrorBeforeClose(
        oboe::AudioStream* stream,
        oboe::Result error) override;

private:
    AudioEngine* engine_;
};

/**
 * Playback callback - receives audio data from network
 * Decodes Opus-encoded audio to PCM for playback
 */
class PlaybackCallback : public oboe::AudioStreamCallback {
public:
    explicit PlaybackCallback(AudioEngine* engine) : engine_(engine) {}

    oboe::DataCallbackResult onAudioReady(
        oboe::AudioStream* stream,
        void* audioData,
        int32_t numFrames) override;

    void onErrorBeforeClose(
        oboe::AudioStream* stream,
        oboe::Result error) override;

    // Feed audio data from network
    void enqueueAudio(const uint8_t* data, size_t size);

    // CRITICAL FIX: Public methods for jitter buffer management
    // AudioEngine needs to reset the buffer on playback start
    void resetJitterBuffer();

private:
    AudioEngine* engine_;

    // Jitter buffer for received Opus packets
    std::unique_ptr<RtpJitterBuffer> jitterBuffer_;

    // PCM output buffer
    std::vector<int16_t> outputBuffer_;
    size_t outputBufferPos_ = 0;
    std::mutex bufferMutex_;
};

} // namespace ptt
} // namespace meshrider

#endif // MESHRIDER_PTT_AUDIO_ENGINE_H
