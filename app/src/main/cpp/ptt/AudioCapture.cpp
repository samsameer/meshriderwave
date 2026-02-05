/*
 * Mesh Rider Wave - PTT Audio Capture
 * Low-latency audio capture using Oboe
 * Following developer.android.com/games/sdk/oboe/low-latency-audio
 */

#include "AudioEngine.h"
#include <android/log.h>
#include <cstring>

#define TAG "MeshRider:PTT-Capture"

namespace meshrider {
namespace ptt {

oboe::Result AudioEngine::createCaptureStream() {
    oboe::AudioStreamBuilder builder;

    // Per Oboe low-latency guidelines:
    // - PerformanceMode::LowLatency for minimal latency
    // - SharingMode::Exclusive to avoid mixing with other apps
    // - Sample rate 16kHz for voice (MCPTT standard)
    builder.setDirection(oboe::Direction::Input)
           ->setFormat(oboe::AudioFormat::I16)  // PCM 16-bit for voice
           ->setChannelCount(kChannelCount)
           ->setSampleRate(kSampleRate)
           ->setFramesPerDataCallback(kFramesPerBurst)
           ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
           ->setSharingMode(oboe::SharingMode::Exclusive)
           ->setUsage(oboe::Usage::VoiceCommunication)  // PTT use case
           ->setContentType(oboe::ContentType::Speech)
           ->setInputPreset(oboe::InputPreset::VoiceCommunication)  // Best for PTT
           ->setCallback(&captureCallback_)
           ->setBufferCapacityInFrames(kBufferSize * 2);

    // Build stream
    return builder.openStream(captureStream_);
}

// Capture callback - receives audio from microphone
oboe::DataCallbackResult AudioEngine::CaptureCallback::onAudioReady(
    oboe::AudioStream* stream,
    void* audioData,
    int32_t numFrames) {

    if (!engine_->isCapturing_) {
        // Stream stopped, output silence
        std::memset(audioData, 0, numFrames * sizeof(int16_t));
        return oboe::DataCallbackResult::Continue;
    }

    // Process audio data
    // 1. Encode to Opus (done in RTP packetizer)
    // 2. Send via multicast RTP
    if (engine_->callback_) {
        engine_->callback_->onAudioData(
            static_cast<const uint8_t*>(audioData),
            numFrames * sizeof(int16_t)
        );
    }

    return oboe::DataCallbackResult::Continue;
}

void AudioEngine::CaptureCallback::onErrorBeforeClose(
    oboe::AudioStream* stream,
    oboe::Result error) {

    __android_log_print(ANDROID_LOG_ERROR, TAG,
                        "Capture stream error: %s",
        oboe::convertToText(error));

    if (engine_->callback_) {
        engine_->callback_->onAudioError(static_cast<int>(error));
    }
}

} // namespace ptt
} // namespace meshrider
