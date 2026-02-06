/*
 * Mesh Rider Wave - PTT Audio Playback
 * Low-latency audio playback using Oboe
 * Following developer.android.com/games/sdk/oboe/low-latency-audio
 */

#include "AudioEngine.h"
#include <android/log.h>
#include <cstring>

#define TAG "MeshRider:PTT-Playback"

namespace meshrider {
namespace ptt {

oboe::Result AudioEngine::createPlaybackStream() {
    oboe::AudioStreamBuilder builder;

    // Per Oboe low-latency guidelines
    builder.setDirection(oboe::Direction::Output)
           ->setFormat(oboe::AudioFormat::I16)
           ->setChannelCount(kChannelCount)
           ->setSampleRate(kSampleRate)
           ->setFramesPerDataCallback(kFramesPerBurst)
           ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
           ->setSharingMode(oboe::SharingMode::Exclusive)
           ->setUsage(oboe::Usage::Media)  // Speaker output for PTT
           ->setContentType(oboe::ContentType::Speech)
           ->setCallback(playbackCallback_.get())
           ->setBufferCapacityInFrames(kPcmFrameSizeBytes * 4);  // Larger buffer for playback

    return builder.openStream(playbackStream_);
}

// Playback callback - feeds audio to speaker
oboe::DataCallbackResult PlaybackCallback::onAudioReady(
    oboe::AudioStream* stream,
    void* audioData,
    int32_t numFrames) {

    if (!engine_->isPlaying_) {
        // Output silence when not playing
        std::memset(audioData, 0, numFrames * sizeof(int16_t));
        return oboe::DataCallbackResult::Continue;
    }

    // Get audio from jitter buffer (received from network)
    // If no audio available, output silence
    if (jitterBuffer_) {
        size_t bytesNeeded = numFrames * sizeof(int16_t);
        bool hasData = jitterBuffer_->dequeue(
            static_cast<uint8_t*>(audioData),
            bytesNeeded
        );

        if (!hasData) {
            // No data - output silence
            std::memset(audioData, 0, bytesNeeded);
        }
    } else {
        std::memset(audioData, 0, numFrames * sizeof(int16_t));
    }

    return oboe::DataCallbackResult::Continue;
}

void PlaybackCallback::onErrorBeforeClose(
    oboe::AudioStream* stream,
    oboe::Result error) {

    __android_log_print(ANDROID_LOG_ERROR, TAG,
                        "Playback stream error: %s",
        oboe::convertToText(error));

    if (engine_->callback_) {
        engine_->callback_->onAudioError(static_cast<int>(error));
    }
}

void PlaybackCallback::enqueueAudio(
    const uint8_t* data,
    size_t size) {

    if (!jitterBuffer_) {
        jitterBuffer_ = std::make_unique<RtpJitterBuffer>();
    }
    jitterBuffer_->enqueue(data, size);
}

void PlaybackCallback::resetJitterBuffer() {
    if (jitterBuffer_) {
        jitterBuffer_->reset();
    }
}

} // namespace ptt
} // namespace meshrider
