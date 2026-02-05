/*
 * Mesh Rider Wave - JNI Bridge for PTT Audio
 * Connects C++ Oboe audio engine with Kotlin
 * Following Kotlin/Android NDK best practices
 */

#include "AudioEngine.h"
#include "RtpPacketizer.h"
#include <android/log.h>
#include <jni.h>
#include <memory>
#include <mutex>

#define TAG "MeshRider:PTT-JNI"

namespace meshrider {
namespace ptt {

// Global audio engine instance
static std::unique_ptr<AudioEngine> g_audioEngine;
static std::unique_ptr<RtpPacketizer> g_packetizer;
static std::mutex g_engineMutex;

// JNI implementation
extern "C" {

/**
 * Initialize PTT audio engine
 * Following developer.android NDK guidelines
 */
JNIEXPORT jboolean JNICALL
Java_com_doodlelabs_meshriderwave_ptt_PttAudioEngine_nativeInitialize(
    JNIEnv* env,
    jobject /* this */,
    jstring multicastGroup,
    jint port) {

    std::lock_guard<std::mutex> lock(g_engineMutex);

    __android_log_print(ANDROID_LOG_INFO, TAG,
                        "Initializing PTT audio engine");

    try {
        // Create audio engine
        g_audioEngine = std::make_unique<AudioEngine>();

        // Create packetizer
        const char* group = env->GetStringUTFChars(multicastGroup, nullptr);
        g_packetizer = std::make_unique<RtpPacketizer>();

        bool initialized = g_packetizer->initialize(group, static_cast<uint16_t>(port));
        env->ReleaseStringUTFChars(multicastGroup, group);

        if (!initialized) {
            __android_log_print(ANDROID_LOG_ERROR, TAG,
                                "Failed to initialize RTP packetizer");
            return JNI_FALSE;
        }

        // Start packetizer
        g_packetizer->start();
        g_packetizer->startReceiveLoop();

        __android_log_print(ANDROID_LOG_INFO, TAG,
                            "PTT audio engine initialized successfully");

        return JNI_TRUE;
    } catch (const std::exception& e) {
        __android_log_print(ANDROID_LOG_ERROR, TAG,
                            "Exception during initialization: %s", e.what());
        return JNI_FALSE;
    }
}

/**
 * Start audio capture (PTT TX)
 */
JNIEXPORT jboolean JNICALL
Java_com_doodlelabs_meshriderwave_ptt_PttAudioEngine_nativeStartCapture(
    JNIEnv* env,
    jobject /* this */) {

    std::lock_guard<std::mutex> lock(g_engineMutex);

    if (!g_audioEngine) {
        __android_log_print(ANDROID_LOG_ERROR, TAG,
                            "Audio engine not initialized");
        return JNI_FALSE;
    }

    bool started = g_audioEngine->startCapture();
    __android_log_print(ANDROID_LOG_INFO, TAG,
                        "Capture started: %s", started ? "SUCCESS" : "FAILED");

    return started ? JNI_TRUE : JNI_FALSE;
}

/**
 * Stop audio capture (PTT TX release)
 */
JNIEXPORT void JNICALL
Java_com_doodlelabs_meshriderwave_ptt_PttAudioEngine_nativeStopCapture(
    JNIEnv* env,
    jobject /* this */) {

    std::lock_guard<std::mutex> lock(g_engineMutex);

    if (g_audioEngine) {
        g_audioEngine->stopCapture();
        __android_log_print(ANDROID_LOG_INFO, TAG, "Capture stopped");
    }
}

/**
 * Start audio playback (PTT RX)
 */
JNIEXPORT jboolean JNICALL
Java_com_doodlelabs_meshriderwave_ptt_PttAudioEngine_nativeStartPlayback(
    JNIEnv* env,
    jobject /* this */) {

    std::lock_guard<std::mutex> lock(g_engineMutex);

    if (!g_audioEngine) {
        __android_log_print(ANDROID_LOG_ERROR, TAG,
                            "Audio engine not initialized");
        return JNI_FALSE;
    }

    bool started = g_audioEngine->startPlayback();
    __android_log_print(ANDROID_LOG_INFO, TAG,
                        "Playback started: %s", started ? "SUCCESS" : "FAILED");

    return started ? JNI_TRUE : JNI_FALSE;
}

/**
 * Stop audio playback
 */
JNIEXPORT void JNICALL
Java_com_doodlelabs_meshriderwave_ptt_PttAudioEngine_nativeStopPlayback(
    JNIEnv* env,
    jobject /* this */) {

    std::lock_guard<std::mutex> lock(g_engineMutex);

    if (g_audioEngine) {
        g_audioEngine->stopPlayback();
        __android_log_print(ANDROID_LOG_INFO, TAG, "Playback stopped");
    }
}

/**
 * Check if currently capturing
 */
JNIEXPORT jboolean JNICALL
Java_com_doodlelabs_meshriderwave_ptt_PttAudioEngine_nativeIsCapturing(
    JNIEnv* env,
    jobject /* this */) {

    std::lock_guard<std::mutex> lock(g_engineMutex);

    if (g_audioEngine && g_audioEngine->isCapturing()) {
        return JNI_TRUE;
    }
    return JNI_FALSE;
}

/**
 * Check if currently playing
 */
JNIEXPORT jboolean JNICALL
Java_com_doodlelabs_meshriderwave_ptt_PttAudioEngine_nativeIsPlaying(
    JNIEnv* env,
    jobject /* this */) {

    std::lock_guard<std::mutex> lock(g_engineMutex);

    if (g_audioEngine && g_audioEngine->isPlaying()) {
        return JNI_TRUE;
    }
    return JNI_FALSE;
}

/**
 * Get current latency in milliseconds
 */
JNIEXPORT jint JNICALL
Java_com_doodlelabs_meshriderwave_ptt_PttAudioEngine_nativeGetLatencyMillis(
    JNIEnv* env,
    jobject /* this */) {

    std::lock_guard<std::mutex> lock(g_engineMutex);

    if (g_audioEngine) {
        return static_cast<jint>(g_audioEngine->getLatencyMillis());
    }
    return 0;
}

/**
 * Cleanup native resources
 */
JNIEXPORT void JNICALL
Java_com_doodlelabs_meshriderwave_ptt_PttAudioEngine_nativeCleanup(
    JNIEnv* env,
    jobject /* this */) {

    std::lock_guard<std::mutex> lock(g_engineMutex);

    __android_log_print(ANDROID_LOG_INFO, TAG, "Cleaning up native resources");

    if (g_audioEngine) {
        g_audioEngine->stopCapture();
        g_audioEngine->stopPlayback();
        g_audioEngine.reset();
    }

    if (g_packetizer) {
        g_packetizer->stop();
        g_packetizer.reset();
    }
}

/**
 * Send audio data via RTP multicast
 */
JNIEXPORT jboolean JNICALL
Java_com_doodlelabs_meshriderwave_ptt_PttAudioEngine_nativeSendAudio(
    JNIEnv* env,
    jobject /* this */,
    jbyteArray audioData,
    jboolean isMarker) {

    std::lock_guard<std::mutex> lock(g_engineMutex);

    if (!g_packetizer) {
        return JNI_FALSE;
    }

    jsize length = env->GetArrayLength(audioData);
    jbyte* data = env->GetByteArrayElements(audioData, nullptr);

    bool sent = g_packetizer->sendAudio(
        reinterpret_cast<const uint8_t*>(data),
        static_cast<size_t>(length),
        isMarker == JNI_TRUE
    );

    env->ReleaseByteArrayElements(audioData, data, JNI_ABORT);

    return sent ? JNI_TRUE : JNI_FALSE;
}

} // extern "C"

} // namespace ptt
} // namespace meshrider
