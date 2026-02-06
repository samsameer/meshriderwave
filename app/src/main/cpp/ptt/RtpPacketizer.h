/*
 * Mesh Rider Wave - RTP Packetizer (PRODUCTION-READY)
 * RFC 3550 RTP implementation for PTT multicast
 * 
 * FIXED (Feb 2026):
 * - Fixed race conditions with atomic head/tail
 * - Added unicast fallback when multicast fails
 * - Non-blocking socket with timeout for clean shutdown
 * - Proper RTP timestamp (48kHz per RFC 7587)
 * - Duplicate SSRC detection
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
#include <array>
#include <optional>

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

// RFC 7587: Opus uses 48kHz clock regardless of actual sample rate
constexpr uint32_t RTP_CLOCK_RATE = 48000;

/**
 * Jitter buffer for incoming RTP packets (THREAD-SAFE)
 * Handles packet reordering and loss concealment
 * 
 * FIXED: Atomic head/tail indices, proper memory ordering
 */
class RtpJitterBuffer {
public:
    RtpJitterBuffer();
    ~RtpJitterBuffer();

    // Add packet to buffer (thread-safe)
    void enqueue(const uint8_t* data, size_t size);

    // Get next packet (returns true if data available)
    bool dequeue(uint8_t* buffer, size_t& size);

    // Reset buffer
    void reset();

    // Get statistics
    size_t getPacketsLost() const { return packetsLost_.load(); }
    size_t getPacketsReceived() const { return packetsReceived_.load(); }
    size_t getCurrentSize() const;

private:
    static constexpr size_t BUFFER_SIZE = 50;
    static constexpr size_t MAX_PACKET_SIZE = 1500;

    struct Packet {
        std::array<uint8_t, MAX_PACKET_SIZE> data;
        size_t size;
        uint16_t seq;
        bool valid;
    };

    // FIXED: Atomic indices for thread safety
    std::atomic<size_t> head_{0};
    std::atomic<size_t> tail_{0};
    std::atomic<bool> isEmpty_{true};
    
    std::array<Packet, BUFFER_SIZE> buffer_;
    std::mutex mutex_;

    // Statistics (atomic for thread safety)
    std::atomic<size_t> packetsReceived_{0};
    std::atomic<size_t> packetsLost_{0};
    std::atomic<uint16_t> lastSeq_{0};
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
    constexpr uint8_t EF   = 0x2E;  // Expedited Forwarding (46) - PTT VOICE
}

/**
 * Transport mode - multicast or unicast fallback
 */
enum class TransportMode {
    MULTICAST,    // Preferred: efficient for many receivers
    UNICAST,      // Fallback: reliable but scales poorly
    AUTO          // Try multicast, fall back to unicast
};

/**
 * RTP Packetizer for PTT audio (PRODUCTION-READY)
 * 
 * FIXED:
 * - Non-blocking receive with timeout for clean shutdown
 * - Unicast fallback when multicast fails
 * - Proper 48kHz timestamp per RFC 7587
 * - SSRC collision detection
 */
class RtpPacketizer {
public:
    RtpPacketizer();
    ~RtpPacketizer();

    // Initialize with multicast group and transport mode
    bool initialize(const char* multicastGroup, uint16_t port, 
                   TransportMode mode = TransportMode::AUTO);

    // Start/Stop transmission
    bool start();
    void stop();

    // Send Opus-encoded audio data
    bool sendAudio(const uint8_t* opusData, size_t opusSize, bool isMarker = false);

    // Receive loop (runs in background thread)
    void startReceiveLoop();
    void stopReceiveLoop();

    // Set callback for received audio (Opus encoded)
    using AudioCallback = std::function<void(const uint8_t*, size_t, uint32_t ssrc)>;
    void setAudioCallback(AudioCallback callback) { audioCallback_ = callback; }

    // Get SSRC
    uint32_t getSSRC() const { return ssrc_; }

    // Get current transport mode
    TransportMode getTransportMode() const { return transportMode_; }

    // Statistics
    size_t getPacketsSent() const { return packetsSent_.load(); }
    size_t getPacketsReceived() const { return packetsReceived_.load(); }

    // Set DSCP QoS marking for RTP packets
    bool setDscp(uint8_t dscpValue);

    // Add unicast peer (for fallback mode)
    void addUnicastPeer(const char* ipAddress);
    void clearUnicastPeers();

private:
    // Socket
    int socket_;
    bool isRunning_;

    // RTP state
    std::atomic<uint16_t> sequence_;
    std::atomic<uint32_t> timestamp_;
    uint32_t ssrc_;
    uint32_t samplesPerFrame_;  // For timestamp calculation

    // Multicast group
    char multicastGroup_[16];
    uint16_t port_;
    TransportMode transportMode_;
    bool multicastJoined_;

    // Unicast fallback
    std::vector<std::string> unicastPeers_;
    std::mutex unicastMutex_;

    // Receive thread (PRODUCTION FIX: Non-blocking with timeout)
    std::thread receiveThread_;
    std::atomic<bool> receiveRunning_;
    int shutdownPipe_[2];  // For interrupting recvfrom()

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
    bool joinMulticastGroup();
    void leaveMulticastGroup();
    void receiveLoop();
    
    // PRODUCTION FIX: Non-blocking receive with timeout
    bool waitForData(int timeoutMs);
    
    // Send to all destinations (multicast + unicast peers)
    bool sendToAll(const uint8_t* data, size_t size);
};

} // namespace ptt
} // namespace meshrider

#endif // MESHRIDER_PTT_RTP_PACKETIZER_H
