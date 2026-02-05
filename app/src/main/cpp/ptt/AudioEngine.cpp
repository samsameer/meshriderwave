/*
 * Mesh Rider Wave - PTT Audio Engine Implementation
 * Low-latency audio following Oboe best practices
 */

#include "AudioEngine.h"
#include <android/log.h>
#include <aaudio/AAudio.h>

#define TAG "MeshRider:PTT-Engine"

namespace meshrider {
namespace ptt {

AudioEngine::AudioEngine()
    : captureCallback_(this),
      playbackCallback_(this) {
}

AudioEngine::~AudioEngine() {
    stopCapture();
    stopPlayback();
}

bool AudioEngine::initialize(AudioEngineCallback* callback) {
    callback_ = callback;

    // Create capture stream (microphone)
    auto result = createCaptureStream();
    if (result != oboe::Result::OK) {
        __android_log_print(ANDROID_LOG_ERROR, TAG,
                            "Failed to create capture stream: %s",
                            oboe::convertToText(result));
        return false;
    }

    // Create playback stream (speaker)
    result = createPlaybackStream();
    if (result != oboe::Result::OK) {
        __android_log_print(ANDROID_LOG_ERROR, TAG,
                            "Failed to create playback stream: %s",
                            oboe::convertToText(result));
        return false;
    }

    __android_log_print(ANDROID_LOG_INFO, TAG,
                        "Audio engine initialized: %d Hz, %d ch",
                        kSampleRate, kChannelCount);

    return true;
}

bool AudioEngine::startCapture() {
    if (isCapturing_.load()) {
        return true;  // Already capturing
    }

    if (!captureStream_) {
        __android_log_print(ANDROID_LOG_ERROR, TAG,
                            "Capture stream not initialized");
        return false;
    }

    auto result = captureStream_->requestStart();
    if (result != oboe::Result::OK) {
        __android_log_print(ANDROID_LOG_ERROR, TAG,
                            "Failed to start capture: %s",
                            oboe::convertToText(result));
        return false;
    }

    isCapturing_.store(true);
    __android_log_print(ANDROID_LOG_INFO, TAG,
                        "Audio capture started");
    return true;
}

void AudioEngine::stopCapture() {
    if (!isCapturing_.load()) {
        return;
    }

    if (captureStream_) {
        captureStream_->stop();
        captureStream_->close();
    }

    isCapturing_.store(false);
    __android_log_print(ANDROID_LOG_INFO, TAG,
                        "Audio capture stopped");
}

bool AudioEngine::startPlayback() {
    if (isPlaying_.load()) {
        return true;  // Already playing
    }

    if (!playbackStream_) {
        __android_log_print(ANDROID_LOG_ERROR, TAG,
                            "Playback stream not initialized");
        return false;
    }

    auto result = playbackStream_->requestStart();
    if (result != oboe::Result::OK) {
        __android_log_print(ANDROID_LOG_ERROR, TAG,
                            "Failed to start playback: %s",
                            oboe::convertToText(result));
        return false;
    }

    isPlaying_.store(true);
    __android_log_print(ANDROID_LOG_INFO, TAG,
                        "Audio playback started");
    return true;
}

void AudioEngine::stopPlayback() {
    if (!isPlaying_.load()) {
        return;
    }

    if (playbackStream_) {
        playbackStream_->stop();
        playbackStream_->close();
    }

    isPlaying_.store(false);
    __android_log_print(ANDROID_LOG_INFO, TAG,
                        "Audio playback stopped");
}

int32_t AudioEngine::getLatencyMillis() const {
    if (!captureStream_ || !playbackStream_) {
        return 0;
    }

    // Get latency from Oboe
    auto captureLatency = captureStream_->getLatencyMillis();
    auto playbackLatency = playbackStream_->getLatencyMillis();

    return static_cast<int32_t>(captureLatency + playbackLatency);
}

void AudioEngine::setSpeakerOutput(bool enable) {
    // Configure output to speaker
    // Per Samsung Knox audio guidelines
}

void AudioEngine::setBluetoothOutput(bool enable) {
    // Configure output to Bluetooth headset
    // Per Samsung Knox Bluetooth guidelines
}

} // namespace ptt
} // namespace meshrider
