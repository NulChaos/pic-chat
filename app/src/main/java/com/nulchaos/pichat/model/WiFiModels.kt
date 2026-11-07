package com.nulchaos.pichat.model

/** Types of messages that can be sent over Wi-Fi Direct */
sealed class ChatMessage {
    data class Text(
        val senderId: String,
        val senderName: String,
        val text: String,
        val timestamp: Long = System.currentTimeMillis()
    ) : ChatMessage()

    data class Image(
        val senderId: String,
        val senderName: String,
        val imageData: ByteArray,
        val thumbnailData: ByteArray?,
        val timestamp: Long = System.currentTimeMillis()
    ) : ChatMessage()

    /** Protocol messages for Wi-Fi Direct session management */
    data class Handshake(
        val type: HandshakeType,
        val senderId: String,
        val senderName: String,
        val roomCode: String? = null
    ) : ChatMessage()
}

enum class HandshakeType {
    JOIN_REQUEST,
    JOIN_RESPONSE,
    LEFT,
    HEARTBEAT
}

/** Connection state for the Wi-Fi Direct session */
data class PeerConnection(
    val peerName: String,
    val ipAddress: String,
    val connected: Boolean,
    val lastSeen: Long = System.currentTimeMillis()
)

/** Network manager for Wi-Fi Direct peer-to-peer communication */
class WifiDirectManager {
    // Will be implemented in the service layer
}
