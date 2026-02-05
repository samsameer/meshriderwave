/*
 * Mesh Rider Wave - PTT Audio Engine
 * Following developer.android Oboe guidelines
 * Low-latency audio for Samsung S24+ devices
 */

#ifndef MESHRIDER_PTT_AUDIO_ENGINE_H
#define MESHRIDER_PTT_AUDIO_ENGINE_H

#include <oboe/oboe.h>
#include <memory>
#include <atomic>
#include <functional>
#include "RtpPacketizer.h"

namespace meshrider {
namespace ptt {

// Following AAudio performance mode guidelines
// per developer.android.com/ndk/guides/audio/aaudio
constexpr int32_t kSampleRate = 16000;  // 16kHz for voice
constexpr int32_t kChannelCount = 1;     // Mono for PTT
constexpr int32_t kFramesPerBurst = 192; // ~12ms at 16kHz (low latency)

// Audio state callback
class AudioEngineCallback {
public:
    virtual ~AudioEngineCallback() = default;
    virtual void onAudioReady() = 0;
    virtual void onAudioError(int errorCode) = 0;
    virtual void onAudioData(const uint8_t* data, size_t size) = 0;
};

/**
 * PTT Audio Engine - Low latency capture/playback
 * Uses Oboe with EXCLUSIVE mode and LOW_LATENCY performance
 * Following Samsung Knox audio guidelines
 */
class AudioEngine {
public:
    AudioEngine();
    ~AudioEngine();

    // Initialize audio engine
    bool initialize(AudioEngineCallback* callback);

    // Start/Stop capture (TX)
    bool startCapture();
    void stopCapture();

    // Start/Stop playback (RX)
    bool startPlayback();
    void stopPlayback();

    // State queries
    bool isCapturing() const { return isCapturing_.load(); }
    bool isPlaying() const { return isPlaying_.load(); }
    int32_t getLatencyMillis() const;

    // Audio routing
    void setSpeakerOutput(bool enable);
    void setBluetoothOutput(bool enable);

private:
    // Oboe streams
    std::shared_ptr<oboe::AudioStream> captureStream_;
    std::shared_ptr<oboe::AudioStream> playbackStream_;

    // State flags (atomic for thread safety)
    std::atomic<bool> isCapturing_{false};
    std::atomic<bool> isPlaying_{false};

    // Callback
    AudioEngineCallback* callback_ = nullptr;

    // Audio data buffers
    static constexpr size_t kBufferSize = 3840; // 20ms @ 16kHz mono PCM16

    // Stream configuration following Oboe best practices
    oboe::Result createCaptureStream();
    oboe::Result createPlaybackStream();

    // Audio callbacks
    class CaptureCallback;
    class PlaybackCallback;

    friend class CaptureCallback;
    friend class PlaybackCallback;
};

/**
 * Capture callback - runs on high-priority audio thread
 * Following AAudio guidelines for low latency
 */
class AudioEngine::CaptureCallback : public oboe::AudioStreamCallback {
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
 * Playback callback - receives encoded Opus data from network
 * Decodes and plays through speaker
 */
class AudioEngine::PlaybackCallback : public oboe::AudioStreamCallback {
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

private:
    AudioEngine* engine_;
    std::unique_ptr<RtpJitterBuffer> jitterBuffer_;
};

} // namespace ptt
} // namespace meshrider

#endif // MESHRIDER_PTT_AUDIO_ENGINE_H
