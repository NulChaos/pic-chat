package com.picchat.data

import androidx.compose.ui.graphics.Color

sealed class ChatMessage {
    data class Text(
        val id: String,
        val senderId: String,
        val senderNickname: String,
        val content: String,
        val timestamp: Long,
        val birthMonth: Int = 0,
        val birthDay: Int = 0
    ) : ChatMessage()

    data class Drawing(
        val id: String,
        val senderId: String,
        val senderNickname: String,
        val grid: IntArray, // pixel colors (color index)
        val colorIndex: Int, // selected palette color
        val timestamp: Long,
        val birthMonth: Int = 0,
        val birthDay: Int = 0
    ) : ChatMessage()

    data class BirthdayBubble(
        val userId: String,
        val nickname: String,
        val month: Int,
        val day: Int,
        val year: Long
    ) : ChatMessage()
}
