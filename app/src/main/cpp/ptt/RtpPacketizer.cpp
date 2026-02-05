/*
 * Mesh Rider Wave - RTP Packetizer Implementation
 * Multicast RTP for PTT audio
 */

#include "RtpPacketizer.h"
#include <android/log.h>
#include <cstring>
#include <unistd.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <random>
#include <thread>

#define TAG "MeshRider:PTT-RTP"

namespace meshrider {
namespace ptt {

// Jitter buffer implementation
RtpJitterBuffer::RtpJitterBuffer()
    : head_(0), tail_(0), isEmpty_(true),
      packetsReceived_(0), packetsLost_(0), lastSeq_(0) {
    std::memset(buffer_, 0, sizeof(buffer_));
}

RtpJitterBuffer::~RtpJitterBuffer() {
    reset();
}

void RtpJitterBuffer::enqueue(const uint8_t* data, size_t size) {
    std::lock_guard<std::mutex> lock(mutex_);

    // Extract sequence number from RTP header
    if (size < RTP_HEADER_SIZE) return;

    RtpHeader header;
    std::memcpy(&header, data, RTP_HEADER_SIZE);
    uint16_t seq = ntohs(header.seq);

    // Check for packet loss
    if (packetsReceived_ > 0 && lastSeq_ != 0) {
        uint16_t expected = (lastSeq_ + 1) & 0xFFFF;
        if (seq != expected) {
            uint16_t lost = (seq - expected) & 0xFFFF;
            packetsLost_ += lost;
        }
    }

    lastSeq_ = seq;
    packetsReceived_++;

    // Add to buffer
    size_t next = (tail_ + 1) % BUFFER_SIZE;
    if (next == head_) {
        // Buffer full, drop oldest
        head_ = (head_ + 1) % BUFFER_SIZE;
        packetsLost_++;
    }

    buffer_[tail_].size = size;
    buffer_[tail_].seq = seq;
    buffer_[tail_].valid = true;
    std::memcpy(buffer_[tail_].data, data, size);
    tail_ = next;
    isEmpty_.store(false);
}

bool RtpJitterBuffer::dequeue(uint8_t* buffer, size_t& size) {
    std::lock_guard<std::mutex> lock(mutex_);

    if (isEmpty_.load()) {
        size = 0;
        return false;
    }

    if (head_ == tail_) {
        isEmpty_.store(true);
        size = 0;
        return false;
    }

    size = buffer_[head_].size;
    std::memcpy(buffer, buffer_[head_].data, size);
    head_ = (head_ + 1) % BUFFER_SIZE;

    return true;
}

void RtpJitterBuffer::reset() {
    std::lock_guard<std::mutex> lock(mutex_);
    head_ = 0;
    tail_ = 0;
    isEmpty_.store(true);
    packetsReceived_ = 0;
    packetsLost_ = 0;
    lastSeq_ = 0;
}

// RTP Packetizer implementation
RtpPacketizer::RtpPacketizer()
    : socket_(-1), isRunning_(false),
      sequence_(0), timestamp_(0),
      port_(5004),
      receiveRunning_(false),
      packetsSent_(0), packetsReceived_(0) {

    // Generate random SSRC
    std::random_device rd;
    std::mt19937 gen(rd());
    std::uniform_int_distribution<uint32_t> dis(1, 0xFFFFFFFF);
    ssrc_ = dis(gen);

    std::memset(multicastGroup_, 0, sizeof(multicastGroup_));
}

RtpPacketizer::~RtpPacketizer() {
    stop();
    closeSocket();
}

bool RtpPacketizer::initialize(const char* multicastGroup, uint16_t port) {
    std::strncpy(multicastGroup_, multicastGroup, sizeof(multicastGroup_) - 1);
    port_ = port;

    return createSocket();
}

bool RtpPacketizer::createSocket() {
    // Create UDP socket
    socket_ = socket(AF_INET, SOCK_DGRAM, 0);
    if (socket_ < 0) {
        __android_log_print(ANDROID_LOG_ERROR, TAG,
                            "Failed to create socket: %s", strerror(errno));
        return false;
    }

    // Enable reuse address
    int reuse = 1;
    if (setsockopt(socket_, SOL_SOCKET, SO_REUSEADDR, &reuse, sizeof(reuse)) < 0) {
        __android_log_print(ANDROID_LOG_ERROR, TAG,
                            "Failed to set SO_REUSEADDR: %s", strerror(errno));
        close(socket_);
        return false;
    }

    // Bind to port
    struct sockaddr_in addr;
    std::memset(&addr, 0, sizeof(addr));
    addr.sin_family = AF_INET;
    addr.sin_addr.s_addr = INADDR_ANY;
    addr.sin_port = htons(port_);

    if (bind(socket_, (struct sockaddr*)&addr, sizeof(addr)) < 0) {
        __android_log_print(ANDROID_LOG_ERROR, TAG,
                            "Failed to bind: %s", strerror(errno));
        close(socket_);
        return false;
    }

    // Set DSCP QoS marking for PTT (Expedited Forwarding)
    // DSCP EF (46) = 0xB8 in IP TOS field (46 << 2 = 184)
    // This gives PTT packets highest priority on QoS-enabled networks
    if (!setDscp(DSCP::EF)) {
        __android_log_print(ANDROID_LOG_WARN, TAG,
                            "Failed to set DSCP EF, continuing without QoS");
        // Not fatal - continue without QoS
    }

    // Join multicast group
    joinMulticastGroup();

    __android_log_print(ANDROID_LOG_INFO, TAG,
                        "RTP socket created: group=%s, port=%d, dscp=%d",
                        multicastGroup_, port_, DSCP::EF);

    return true;
}

/**
 * Set DSCP QoS marking on socket
 * Per RFC 3246, RFC 5865 for QoS on IP networks
 * DSCP value is placed in the high 6 bits of IP TOS field
 */
bool RtpPacketizer::setDscp(uint8_t dscpValue) {
    // DSCP is in the high 6 bits of the TOS field (TOS = DSCP << 2 + ECN)
    // Shift dscpValue left by 2 to position it correctly
    int tos = dscpValue << 2;

    if (setsockopt(socket_, IPPROTO_IP, IP_TOS, &tos, sizeof(tos)) < 0) {
        __android_log_print(ANDROID_LOG_WARN, TAG,
                            "Failed to set IP_TOS (DSCP=%d): %s",
                            dscpValue, strerror(errno));
        return false;
    }

    __android_log_print(ANDROID_LOG_INFO, TAG,
                        "DSCP QoS set to %d (TOS=%d)", dscpValue, tos);
    return true;
}

void RtpPacketizer::joinMulticastGroup() {
    struct ip_mreq mreq;
    mreq.imr_multiaddr.s_addr = inet_addr(multicastGroup_);
    mreq.imr_interface.s_addr = INADDR_ANY;

    if (setsockopt(socket_, IPPROTO_IP, IP_ADD_MEMBERSHIP,
                   &mreq, sizeof(mreq)) < 0) {
        __android_log_print(ANDROID_LOG_WARN, TAG,
                            "Failed to join multicast group %s: %s",
                            multicastGroup_, strerror(errno));
    } else {
        __android_log_print(ANDROID_LOG_INFO, TAG,
                            "Joined multicast group: %s", multicastGroup_);
    }
}

void RtpPacketizer::closeSocket() {
    if (socket_ >= 0) {
        close(socket_);
        socket_ = -1;
    }
}

bool RtpPacketizer::start() {
    if (isRunning_) {
        return true;
    }

    isRunning_ = true;
    __android_log_print(ANDROID_LOG_INFO, TAG,
                        "RTP packetizer started");
    return true;
}

void RtpPacketizer::stop() {
    isRunning_ = false;
    stopReceiveLoop();
}

bool RtpPacketizer::sendAudio(const uint8_t* pcmData, size_t pcmSize, bool isMarker) {
    if (!isRunning_ || socket_ < 0) {
        return false;
    }

    // Create RTP packet
    uint8_t packet[MAX_PACKET_SIZE];
    RtpHeader* header = reinterpret_cast<RtpHeader*>(packet);

    header->setVersion(RTP_VERSION);
    header->setMarker(isMarker);
    header->setPayloadType(RTP_PAYLOAD_OPUS);
    header->seq = htons(sequence_.fetch_add(1) & 0xFFFF);
    header->timestamp = htonl(timestamp_.load());
    header->ssrc = htonl(ssrc_);

    // Copy payload (in real implementation, would encode with Opus first)
    size_t payloadSize = pcmSize;
    if (payloadSize > MAX_PACKET_SIZE - RTP_HEADER_SIZE) {
        payloadSize = MAX_PACKET_SIZE - RTP_HEADER_SIZE;
    }
    std::memcpy(packet + RTP_HEADER_SIZE, pcmData, payloadSize);

    // Send to multicast group
    struct sockaddr_in addr;
    std::memset(&addr, 0, sizeof(addr));
    addr.sin_family = AF_INET;
    addr.sin_addr.s_addr = inet_addr(multicastGroup_);
    addr.sin_port = htons(port_);

    ssize_t sent = sendto(socket_, packet, RTP_HEADER_SIZE + payloadSize, 0,
                          (struct sockaddr*)&addr, sizeof(addr));

    if (sent < 0) {
        __android_log_print(ANDROID_LOG_ERROR, TAG,
                            "Failed to send RTP packet: %s", strerror(errno));
        return false;
    }

    // Advance timestamp (16kHz sample rate, 16-bit samples = 2 bytes per sample)
    timestamp_.fetch_add(pcmSize / 2);
    packetsSent_++;

    return true;
}

void RtpPacketizer::startReceiveLoop() {
    if (receiveRunning_) {
        return;
    }

    receiveRunning_ = true;
    receiveThread_ = std::thread([this]() { receiveLoop(); });
}

void RtpPacketizer::stopReceiveLoop() {
    receiveRunning_ = false;
    if (receiveThread_.joinable()) {
        receiveThread_.join();
    }
}

void RtpPacketizer::receiveLoop() {
    __android_log_print(ANDROID_LOG_INFO, TAG,
                        "RTP receive loop started");

    uint8_t buffer[MAX_PACKET_SIZE];
    struct sockaddr_in fromAddr;
    socklen_t fromLen = sizeof(fromAddr);

    while (receiveRunning_) {
        ssize_t received = recvfrom(socket_, buffer, sizeof(buffer), 0,
                                    (struct sockaddr*)&fromAddr, &fromLen);

        if (received > RTP_HEADER_SIZE) {
            // Add to jitter buffer
            jitterBuffer_.enqueue(buffer, received);

            // Notify callback (if registered)
            if (audioCallback_) {
                // Extract SSRC from header
                RtpHeader* header = reinterpret_cast<RtpHeader*>(buffer);
                uint32_t ssrc = ntohl(header->ssrc);
                audioCallback_(buffer + RTP_HEADER_SIZE,
                              received - RTP_HEADER_SIZE,
                              ssrc);
            }

            packetsReceived_++;
        }
    }

    __android_log_print(ANDROID_LOG_INFO, TAG,
                        "RTP receive loop stopped");
}

} // namespace ptt
} // namespace meshrider
