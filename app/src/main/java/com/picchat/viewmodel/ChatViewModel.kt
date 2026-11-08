package com.picchat.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.picchat.data.ChatMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ChatViewModel : ViewModel() {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    // User profile (persisted to SharedPreferences in real app)
    var userNickname by mutableStateOf("You")
    var birthMonth by mutableStateOf(1)
    var birthDay by mutableStateOf(1)
    val isBirthday get() { 
        val now = java.time.LocalDate.now()
        return now.monthValue == birthMonth && now.dayOfMonth == birthDay 
    }

    fun addTextMessage(content: String) {
        if (isBirthday) {
            _messages.value += ChatMessage.BirthdayBubble(
                userId = "system", nickname = userNickname,
                month = birthMonth, day = birthDay, year = 0L
            )
        }
        val msg = ChatMessage.Text(
            id = System.currentTimeMillis().toString(),
            senderId = "me", senderNickname = userNickname,
            content = content, timestamp = System.currentTimeMillis(),
            birthMonth = birthMonth, birthDay = birthDay
        )
        _messages.value = _messages.value + msg
    }

    fun addDrawingMessage(grid: IntArray, colorIndex: Int) {
        if (isBirthday) {
            _messages.value += ChatMessage.BirthdayBubble(
                userId = "system", nickname = userNickname,
                month = birthMonth, day = birthDay, year = 0L
            )
        }
        val msg = ChatMessage.Drawing(
            id = System.currentTimeMillis().toString(),
            senderId = "me", senderNickname = userNickname,
            grid = grid, colorIndex = colorIndex,
            timestamp = System.currentTimeMillis(),
            birthMonth = birthMonth, birthDay = birthDay
        )
        _messages.value = _messages.value + msg
    }

    fun updateNickname(nickname: String) { userNickname = nickname }
    fun updateBirthMonth(month: Int) { birthMonth = month }
    fun updateBirthDay(day: Int) { birthDay = day }

    // Incoming messages from Wi-Fi Direct
    fun addIncomingMessage(id: String, senderId: String, senderNickname: String, content: String? = null, grid: IntArray? = null) {
        val msg = if (grid != null) {
            ChatMessage.Drawing(id, senderId, senderNickname, grid, -1, System.currentTimeMillis(), birthMonth, birthDay)
        } else {
            ChatMessage.Text(id, senderId, senderNickname, content ?: "", System.currentTimeMillis(), birthMonth, birthDay)
        }
        _messages.value = _messages.value + msg
    }

    // Birthday check broadcast
    fun checkBirthdays() {
        // In real app: listen to incoming BirthdayBubble messages
    }
}
