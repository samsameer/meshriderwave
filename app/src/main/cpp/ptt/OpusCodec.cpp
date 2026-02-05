/*
 * Mesh Rider Wave - Opus Codec Implementation
 * 3GPP TS 26.179 MCPTT mandatory codec
 */

#include "OpusCodec.h"
#include <android/log.h>
#include <cstring>

#define LOG_TAG "MeshRider:OpusCodec"

namespace meshrider {
namespace ptt {

// ============================================================================
// OpusEncoder Implementation
// ============================================================================

OpusEncoder::OpusEncoder()
    : encoder_(nullptr)
    , bitrate_(OPUS_BITRATE)
    , fecEnabled_(false)
    , complexity_(5)  // Medium complexity
{
}

OpusEncoder::~OpusEncoder() {
    if (encoder_) {
        opus_encoder_destroy(encoder_);
        encoder_ = nullptr;
    }
}

bool OpusEncoder::initialize(OpusMode mode) {
    // Create encoder
    int error = OPUS_OK;
    encoder_ = opus_encoder_create(
        OPUS_SAMPLE_RATE,
        OPUS_CHANNELS,
        static_cast<int>(mode),
        &error
    );

    if (error != OPUS_OK || !encoder_) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG,
            "Failed to create Opus encoder: %s", opus_strerror(error));
        return false;
    }

    // Configure encoder
    opus_encoder_ctl(encoder_, OPUS_SET_BITRATE(bitrate_));
    opus_encoder_ctl(encoder_, OPUS_SET_COMPLEXITY(complexity_));
    opus_encoder_ctl(encoder_, OPUS_SET_INBAND_FEC(fecEnabled_ ? 1 : 0));
    opus_encoder_ctl(encoder_, OPUS_SET_PACKET_LOSS_PERC(5));  // Assume 5% packet loss

    // Set expected packet loss for PLC
    opus_encoder_ctl(encoder_, OPUS_SET_DTX(1));  // Discontinuous transmission

    __android_log_print(ANDROID_LOG_INFO, LOG_TAG,
        "Opus encoder initialized: %d Hz, %d ch, %d bps",
        OPUS_SAMPLE_RATE, OPUS_CHANNELS, bitrate_);

    return true;
}

int OpusEncoder::encode(const int16_t* pcm, int frameSize,
                        uint8_t* output, int maxOutputSize) {
    if (!encoder_) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "Encoder not initialized");
        return -1;
    }

    if (frameSize != OPUS_FRAME_SIZE) {
        __android_log_print(ANDROID_LOG_WARNING, LOG_TAG,
            "Frame size mismatch: expected %d, got %d", OPUS_FRAME_SIZE, frameSize);
    }

    const opus_int16* pcmData = reinterpret_cast<const opus_int16*>(pcm);

    int encodedBytes = opus_encode(
        encoder_,
        pcmData,
        frameSize,
        output,
        maxOutputSize
    );

    if (encodedBytes < 0) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG,
            "Opus encode failed: %s", opus_strerror(encodedBytes));
        return encodedBytes;
    }

    return encodedBytes;
}

void OpusEncoder::reset() {
    if (encoder_) {
        opus_encoder_ctl(encoder_, OPUS_RESET_STATE);
        __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, "Encoder reset");
    }
}

void OpusEncoder::setBitrate(int bitrate) {
    if (bitrate < 6000 || bitrate > 64000) {
        __android_log_print(ANDROID_LOG_WARN, LOG_TAG,
            "Bitrate %d out of range, clamping to [6000, 64000]", bitrate);
        bitrate = std::max(6000, std::min(64000, bitrate));
    }
    bitrate_ = bitrate;
    if (encoder_) {
        opus_encoder_ctl(encoder_, OPUS_SET_BITRATE(bitrate));
    }
}

void OpusEncoder::setFEC(bool enable) {
    fecEnabled_ = enable;
    if (encoder_) {
        opus_encoder_ctl(encoder_, OPUS_SET_INBAND_FEC(enable ? 1 : 0));
    }
}

void OpusEncoder::setComplexity(int complexity) {
    if (complexity < 0 || complexity > 10) {
        __android_log_print(ANDROID_LOG_WARN, LOG_TAG,
            "Complexity %d out of range, clamping to [0, 10]", complexity);
        complexity = std::max(0, std::min(10, complexity));
    }
    complexity_ = complexity;
    if (encoder_) {
        opus_encoder_ctl(encoder_, OPUS_SET_COMPLEXITY(complexity));
    }
}


// ============================================================================
// OpusDecoder Implementation
// ============================================================================

OpusDecoder::OpusDecoder()
    : decoder_(nullptr)
    , lastError_(OPUS_OK)
{
}

OpusDecoder::~OpusDecoder() {
    if (decoder_) {
        opus_decoder_destroy(decoder_);
        decoder_ = nullptr;
    }
}

bool OpusDecoder::initialize() {
    int error = OPUS_OK;
    decoder_ = opus_decoder_create(
        OPUS_SAMPLE_RATE,
        OPUS_CHANNELS,
        &error
    );

    if (error != OPUS_OK || !decoder_) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG,
            "Failed to create Opus decoder: %s", opus_strerror(error));
        lastError_ = error;
        return false;
    }

    // Enable FEC for better packet loss handling
    opus_decoder_ctl(decoder_, OPUS_SET_INBAND_FEC(1));

    __android_log_print(ANDROID_LOG_INFO, LOG_TAG,
        "Opus decoder initialized: %d Hz, %d ch",
        OPUS_SAMPLE_RATE, OPUS_CHANNELS);

    return true;
}

int OpusDecoder::decode(const uint8_t* input, int inputSize,
                        int16_t* output, int frameSize) {
    if (!decoder_) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "Decoder not initialized");
        return -1;
    }

    opus_int16* pcmData = reinterpret_cast<opus_int16*>(output);

    int decodedSamples = opus_decode(
        decoder_,
        input,
        inputSize,
        pcmData,
        frameSize,
        0  // Don't use FEC for this packet
    );

    if (decodedSamples < 0) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG,
            "Opus decode failed: %s", opus_strerror(decodedSamples));
        lastError_ = decodedSamples;
        return decodedSamples;
    }

    lastError_ = OPUS_OK;
    return decodedSamples;
}

int OpusDecoder::decodePLC(int16_t* output, int frameSize) {
    if (!decoder_) {
        return -1;
    }

    opus_int16* pcmData = reinterpret_cast<opus_int16*>(output);

    int decodedSamples = opus_decode(
        decoder_,
        nullptr,   // No input data (packet lost)
        0,         // Zero length
        pcmData,
        frameSize,
        1  // Use FEC to reconstruct
    );

    if (decodedSamples < 0) {
        __android_log_print(ANDROID_LOG_WARN, LOG_TAG,
            "Opus PLC failed: %s", opus_strerror(decodedSamples));
        lastError_ = decodedSamples;
    }

    return decodedSamples;
}

void OpusDecoder::reset() {
    if (decoder_) {
        opus_decoder_ctl(decoder_, OPUS_RESET_STATE);
        __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, "Decoder reset");
    }
}


// ============================================================================
// OpusCodecFactory Implementation
// ============================================================================

std::unique_ptr<OpusEncoder> OpusCodecFactory::createEncoder(OpusMode mode) {
    auto encoder = std::make_unique<OpusEncoder>();
    if (!encoder->initialize(mode)) {
        return nullptr;
    }
    return encoder;
}

std::unique_ptr<OpusDecoder> OpusCodecFactory::createDecoder() {
    auto decoder = std::make_unique<OpusDecoder>();
    if (!decoder->initialize()) {
        return nullptr;
    }
    return decoder;
}

const char* OpusCodecFactory::getVersion() {
    return opus_get_version_string();
}

int OpusCodecFactory::getLookahead() {
    // Create temporary encoder to get lookahead
    int error = OPUS_OK;
    OpusEncoder* enc = opus_encoder_create(OPUS_SAMPLE_RATE, OPUS_CHANNELS,
                                          OPUS_APPLICATION_VOIP, &error);
    if (!enc) return 0;

    int lookahead = 0;
    opus_encoder_ctl(enc, OPUS_GET_LOOKAHEAD(&lookahead));
    opus_encoder_destroy(enc);

    return lookahead;
}

} // namespace ptt
} // namespace meshrider
