/*
 * Mesh Rider Wave - RTP Packetizer
 * RFC 3550 RTP implementation for PTT multicast
 */

#ifndef MESHRIDER_PTT_RTP_PACKETIZER_H
#define MESHRIDER_PTT_RTP_PACKETIZER_H

#include <cstdint>
#include <vector>
#include <atomic>
#include <mutex>
#include <condition_variable>
#include <thread>
#include <functional>

namespace meshrider {
namespace ptt {

// RTP Header (RFC 3550)
#pragma pack(push, 1)
struct RtpHeader {
    uint8_t  vpxcc;      // V=2, P, X, CC
    uint8_t  mpt;        // M, PT
    uint16_t seq;        // Sequence number
    uint32_t timestamp;  // Timestamp
    uint32_t ssrc;       // Synchronization source

    void setVersion(uint8_t v) {
        vpxcc = (v & 0x03) << 6;
    }
    void setMarker(bool m) {
        mpt = m ? (mpt | 0x80) : (mpt & 0x7F);
    }
    void setPayloadType(uint8_t pt) {
        mpt = (mpt & 0x80) | (pt & 0x7F);
    }
};
#pragma pack(pop)

// RTP configuration
constexpr int RTP_VERSION = 2;
constexpr int RTP_PAYLOAD_OPUS = 111;  // Dynamic PT for Opus
constexpr int RTP_HEADER_SIZE = 12;
constexpr int MAX_PACKET_SIZE = 1400;  // MTU-safe

/**
 * Jitter buffer for incoming RTP packets
 * Handles packet reordering and loss concealment
 */
class RtpJitterBuffer {
public:
    RtpJitterBuffer();
    ~RtpJitterBuffer();

    // Add packet to buffer
    void enqueue(const uint8_t* data, size_t size);

    // Get next packet (returns true if data available)
    bool dequeue(uint8_t* buffer, size_t& size);

    // Reset buffer
    void reset();

    // Get statistics
    size_t getPacketsLost() const { return packetsLost_; }
    size_t getPacketsReceived() const { return packetsReceived_; }

private:
    static constexpr size_t BUFFER_SIZE = 50;  // 50 packets = ~1 second at 20ms/frame
    static constexpr size_t MAX_PACKET_SIZE = 1500;

    struct Packet {
        uint8_t data[MAX_PACKET_SIZE];
        size_t size;
        uint16_t seq;
        bool valid;
    };

    Packet buffer_[BUFFER_SIZE];
    size_t head_;
    size_t tail_;
    std::atomic<bool> isEmpty_;
    std::mutex mutex_;

    // Statistics
    std::atomic<size_t> packetsReceived_;
    std::atomic<size_t> packetsLost_;
    uint16_t lastSeq_;
};

/**
 * DSCP QoS values for QoS marking
 * Per RFC 3246, RFC 5865 for QoS on IP networks
 * EF (Expedited Forwarding) = 46 for voice/video (PTT)
 */
namespace DSCP {
    constexpr uint8_t CS0  = 0x00;  // Best Effort
    constexpr uint8_t CS1  = 0x08;  // Scavenger
    constexpr uint8_t AF11 = 0x0A;  // Priority
    constexpr uint8_t AF21 = 0x12;  // Immediate
    constexpr uint8_t AF31 = 0x1A;  // Flash
    constexpr uint8_t AF41 = 0x22;  // Flash Override
    constexpr uint8_t AF32 = 0x14;  // Assured Forwarding (AF32)
    constexpr uint8_t AF33 = 0x1C;  // Assured Forwarding (AF33)
    constexpr uint8_t AF41 = 0x22;  // Assured Forwarding (AF41)
    constexpr uint8_t EF   = 0x2E;  // Expedited Forwarding (46) - PTT VOICE
    constexpr uint8_t VOICE_ACE = EF;   // Voice with ACK
    constexpr uint8_t NC   = 0x00;  // Network Control
}

/**
 * RTP Packetizer for multicast PTT audio
 */
class RtpPacketizer {
public:
    RtpPacketizer();
    ~RtpPacketizer();

    // Initialize with multicast group
    bool initialize(const char* multicastGroup, uint16_t port);

    // Start/Stop transmission
    bool start();
    void stop();

    // Packetize and send audio data
    bool sendAudio(const uint8_t* pcmData, size_t pcmSize, bool isMarker = false);

    // Receive loop (runs in background thread)
    void startReceiveLoop();
    void stopReceiveLoop();

    // Set callback for received audio
    using AudioCallback = std::function<void(const uint8_t*, size_t, uint32_t ssrc)>;
    void setAudioCallback(AudioCallback callback) { audioCallback_ = callback; }

    // Get SSRC
    uint32_t getSSRC() const { return ssrc_; }

    // Statistics
    size_t getPacketsSent() const { return packetsSent_; }
    size_t getPacketsReceived() const { return packetsReceived_; }

    // Set DSCP QoS marking for RTP packets
    // Use DSCP::EF (46) for Expedited Forwarding (PTT voice priority)
    bool setDscp(uint8_t dscpValue);

private:
    // Socket
    int socket_;
    bool isRunning_;

    // RTP state
    std::atomic<uint16_t> sequence_;
    std::atomic<uint32_t> timestamp_;
    uint32_t ssrc_;

    // Multicast group
    char multicastGroup_[16];
    uint16_t port_;

    // Receive thread
    std::thread receiveThread_;
    std::atomic<bool> receiveRunning_;

    // Jitter buffer
    RtpJitterBuffer jitterBuffer_;

    // Callback
    AudioCallback audioCallback_;

    // Statistics
    std::atomic<size_t> packetsSent_;
    std::atomic<size_t> packetsReceived_;

    // Helper methods
    bool createSocket();
    void closeSocket();
    void joinMulticastGroup();
    void receiveLoop();
};

} // namespace ptt
} // namespace meshrider

#endif // MESHRIDER_PTT_RTP_PACKETIZER_H
