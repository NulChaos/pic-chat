package com.picchat.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.picchat.data.ChatMessage
import com.picchat.viewmodel.ChatViewModel

@Composable
fun ChatScreen(
    viewModel: ChatViewModel = remember { ChatViewModel() },
    onUserNickChange: (String) -> Unit = {},
    onSaveNickname: () -> Unit = {}
) {
    val messages by viewModel.messages.collectAsState()
    var showDrawModal by remember { mutableStateOf(false) }
    var drawGrid by remember { mutableStateOf(IntArray(24) { -1 }) }
    var currentColorIndex by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header with nickname & birthday status
        MaterialTheme.shapes.small.background()
        Card(elevation = CardDefaults.cardElevation(defaultElevation = 4.dp), modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Nickname: ${viewModel.userNickname}", style = MaterialTheme.typography.titleMedium)
                    Text("Birthday: ${viewModel.birthMonth}/${viewModel.birthDay}", style = MaterialTheme.typography.bodySmall)
                    if (viewModel.isBirthday) {
                        Text("🎂 It's your birthday!", color = Color.Red, fontWeight = FontWeight.Bold)
                    } else {
                        val otherBirthdays = findUpcomingBirthdays(messages, 7) // 7 days ahead
                        if (otherBirthdays.isNotEmpty()) {
                            Text("🎉 Birthday coming up! 🎈", style = MaterialTheme.typography.bodySmall, color = Color(0xFF2196F3))
                        }
                    }
                }
            }
        }

        // Message list
        LazyColumn(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
            items(messages, key = { it.id }) { msg ->
                ChatBubble(msg)
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        // Drawing modal (bottom sheet style)
        if (showDrawModal) {
            ModalDrawingDrawer(
                grid = drawGrid, onGridUpdate = { drawGrid = it },
                currentColorIndex = currentColorIndex, onColorChange = { currentColorIndex = it },
                onCancel = { showDrawModal = false },
                onSend = { 
                    viewModel.addDrawingMessage(drawGrid, currentColorIndex)
                    drawGrid = IntArray(24) { -1 }
                    showDrawModal = false
                }
            )
        }

        // Input bar
        Card(modifier = Modifier.padding(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { showDrawModal = true }) {
                    Icon(Icons.Default.Draw, "Draw", tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(modifier = Modifier.width(8.dp))
                var text by remember { mutableStateOf("") }
                OutlinedTextField(
                    value = text, onValueChange = { text = it },
                    label = { Text("Type a message...") },
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = { if (text.isNotBlank()) { viewModel.addTextMessage(text); text = "" } }) {
                    Text("Send")
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    val isMe = message is ChatMessage.Text || message is ChatMessage.Drawing && message.senderId == "me"
    
    Card(
        modifier = Modifier.fillMaxWidth()
            .background(if (isMe) Color.LightGray.copy(alpha = 0.4f) else Color.White, MaterialTheme.shapes.medium),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(message.senderNickname ?: "Unknown", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.Blue)
            
            // Mixed content: DS Pictochat style - image/grid at top, text at bottom (or vice versa)
            when (message) {
                is ChatMessage.Drawing -> PixelDrawingCard(message.grid, message.colorIndex)
                is ChatMessage.Text -> Text(message.content, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp))
                is ChatMessage.BirthdayBubble -> BirthdayBanner(message.nickname)
            }

            // Timestamp
            val timeStr = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date(message.timestamp))
            Text(timeStr, style = MaterialTheme.typography.labelSmall, color = Color.Gray, modifier = Modifier.align(Alignment.End).padding(top = 4.dp))
        }
    }
}

@Composable
private fun PixelDrawingCard(grid: IntArray, colorIdx: Int) {
    val colors = listOf(
        androidx.compose.ui.graphics.Color.White,
        androidx.compose.ui.graphics.Color(0xFF8B4513),
        androidx.compose.ui.graphics.Color(0xFF333333),
        androidx.compose.material3.MaterialTheme.colorScheme.primary,
        androidx.compose.ui.graphics.Color(0xFFFFD700),
        androidx.compose.ui.graphics.Color(0xFF90EE90),
        androidx.compose.ui.graphics.Color(0xFF87CEEB),
        androidx.compose.ui.graphics.Color(0xFFFF69B4)
    )

    Column {
        androidx.compose.foundation.layout.Box(modifier = androidx.compose.foundation.layout Modifier.padding(4.dp)) {
            repeat(6) { r ->
                Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(2.dp)) {
                    repeat(4) { c ->
                        val idx = r * 4 + c
                        Box(
                            modifier = Modifier.size(36.dp)
                                .background(if (grid[idx] != -1) colors[grid[idx]] else androidx.compose.ui.graphics.Color.White, MaterialTheme.shapes.small)
                                .border(0.5.dp, Color.LightGray, MaterialTheme.shapes.small)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BirthdayBanner(nickname: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(8.dp)) {
        Card(elevation = CardDefaults.cardElevation(4.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFD700))) {
            Text("🎂 Happy Birthday $nickname! 🥳", style = MaterialTheme.typography.titleMedium, color = Color.Red)
        }
    }
}

private fun findUpcomingBirthdays(messages: List<ChatMessage>, daysAhead: Int): List<String> {
    val upcoming = mutableListOf<String>()
    val now = java.time.LocalDate.now()
    messages.filterIsInstance(ChatMessage.Text::class.java).forEach { msg ->
        if (msg.senderId != "me") {
            val diff = java.time.temporal.ChronoUnit.DAYS.between(now, java.time.LocalDate.of(2026, msg.birthMonth, msg.birthDay))
            if (diff in 1..daysAhead) upcoming.add(msg.senderNickname ?: "?")
        }
    }
    return upcoming
}
