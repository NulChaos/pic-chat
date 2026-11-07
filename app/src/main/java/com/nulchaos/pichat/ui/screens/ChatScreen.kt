package com.nulchaos.pichat.ui.screens

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SignalCellular4Bar
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.ImageLoader
import coil.compose.AsyncImage
import com.nulchaos.pichat.WifiState
import com.nulchaos.pichat.model.ChatMessage
import com.nulchaos.pichat.model.PeerConnection

/**
 * Main chat screen: displays messages and allows sending text and images.
 * Mirrors the PictoChat grid where each "bubble" is a message from a peer.
 */
@Composable
fun ChatScreen(
    myName: String,
    onLeaveRoom: () -> Unit,
    onSendMessage: (String) -> Unit,
    onSendImage: (ByteArray, ByteArray?) -> Unit,
    chatMessages: List<ChatMessage>,
    connectedPeers: List<PeerConnection>,
    wifiState: WifiState,
    onError: String?,
    onClearError: () -> Unit,
    messageInput: String,
    onMessageInputChange: (String) -> Unit,
    onTakePhoto: () -> Unit,
) {
    var showOptionsMenu by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar
            ChatTopBar(
                title = "PictoChat Room",
                peerCount = connectedPeers.size,
                wifiState = wifiState,
                onLeave = onLeaveRoom,
                onPhotoClick = { showOptionsMenu = true },
                onCreateOptionsMenu = {
                    DropdownMenu(
                        expanded = showOptionsMenu,
                        onDismissRequest = { showOptionsMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Take Photo") },
                            leadingIcon = { Icon(Icons.Default.CameraAlt, contentDescription = null) },
                            onClick = { onTakePhoto(); showOptionsMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Send Photo from Gallery") },
                            leadingIcon = { Icon(Icons.Default.Chat, contentDescription = null) },
                            onClick = { /* TODO: gallery picker */ }
                        )
                    }
                }
            )

            // Message list
            val listState = rememberLazyListState()
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                reverseLayout = false,
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (chatMessages.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Chat,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = Color.Gray.copy(alpha = 0.5f)
                                )
                                Spacer(modifier = androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp)))
                                Text(
                                    text = "No messages yet",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.Gray
                                )
                                Text(
                                    text = "Send a message to start the chat!",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }

                items(chatMessages, key = { it.hashCode() }) { msg ->
                    MessageBubble(message = msg, isMine = false)
                }
            }

            // Input bar
            ChatInputBar(
                input = messageInput,
                onInputChange = onMessageInputChange,
                onSend = { onSendMessage(messageInput) },
                onPhotoClick = { showOptionsMenu = true }
            )
        }
    }

    // Auto-scroll to bottom when new messages arrive
    androidx.compose.runtime.LaunchedEffect(chatMessages.size) {
        if (chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(chatMessages.size - 1)
        }
    }
}

@Composable
private fun ChatTopBar(
    title: String,
    peerCount: Int,
    wifiState: WifiState,
    onLeave: () -> Unit,
    onPhotoClick: () -> Unit,
    onCreateOptionsMenu: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: back / leave button
        Icon(
            imageVector = Icons.Default.ArrowBack,
            contentDescription = "Leave room",
            modifier = Modifier.clickable(onClick = onLeave).size(24.dp),
            tint = MaterialTheme.colorScheme.onBackground
        )

        // Center: title
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onBackground
        )

        // Right: icons
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Peer count
            Text(
                text = "$peerCount online",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
            androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp)))

            // WiFi status indicator
            Icon(
                imageVector = when (wifiState) {
                    WifiState.HOSTING -> Icons.Default.SignalCellular4Bar
                    WifiState.JOINED -> Icons.Default.Wifi
                    else -> androidx.compose.material.icons.filled.WifiOff
                },
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = when (wifiState) {
                    WifiState.HOSTING, WifiState.JOINED -> Color.Green
                    else -> Color.Red
                }
            )

            androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp)))

            // Photo menu button
            Box {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = "Photo options",
                    modifier = Modifier.clickable(onClick = onPhotoClick).size(24.dp),
                    tint = MaterialTheme.colorScheme.onBackground
                )
                onCreateOptionsMenu()
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage, isMine: Boolean) {
    val alignment = if (isMine) Alignment.TopEnd else Alignment.TopStart
    val bgColor = if (isMine) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = alignment
    ) {
        Card(
            modifier = Modifier.clip(RoundedCornerShape(16.dp))
                .background(bgColor),
            shape = RoundedCornerShape(16.dp)
        ) {
            when (message) {
                is com.nulchaos.pichat.model.ChatMessage.Text -> TextBubble(message, isMine)
                is com.nulchaos.pichat.model.ChatMessage.Image -> ImageBubble(message)
                is com.nulchaos.pichat.model.ChatMessage.Handshake -> SystemMessageBubble(message)
            }
        }
    }
}

@Composable
private fun TextBubble(message: com.nulchaos.pichat.model.ChatMessage.Text, isMine: Boolean) {
    Column(modifier = Modifier.padding(12.dp)) {
        // Sender name
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.foundation.layout.Spacer(Modifier.width(4.dp)))
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = message.senderName.ifBlank { "Anon" },
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = if (isMine) Color.White else MaterialTheme.colorScheme.onSurface
            )
        }

        androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.foundation.layout.Spacer(Modifier.height(4.dp)))

        // Message text
        Text(
            text = message.text,
            style = MaterialTheme.typography.bodyMedium.copy(color = if (isMine) Color.White else MaterialTheme.colorScheme.onSurface),
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        // Timestamp
        androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.foundation.layout.Spacer(Modifier.height(4.dp)))
        Text(
            text = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(message.timestamp)),
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun ImageBubble(message: com.nulchaos.pichat.model.ChatMessage.Image) {
    Column(modifier = Modifier.padding(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                modifier = androidx.compose.foundation.layout.Spacer(Modifier).size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = message.senderName.ifBlank { "Anon" },
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.foundation.layout.Spacer(Modifier.height(4.dp)))

        // Display the image using Coil
        val imageUri = android.net.Uri.parse(
            "data:image/png;base64,${android.util.Base64.encodeToString(message.imageData, android.util.Base64.NO_WRAP)}"
        )

        androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp)))
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(12.dp)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                AsyncImage(
                    model = imageUri,
                    contentDescription = "Shared image",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.foundation.layout.Spacer(Modifier.height(4.dp)))
        Text(
            text = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(message.timestamp)),
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun SystemMessageBubble(message: com.nulchaos.pichat.model.ChatMessage.Handshake) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = when (message.type) {
                com.nulchaos.pichat.model.HandshakeType.JOIN_REQUEST -> "${message.senderName} joined the room"
                com.nulchaos.pichat.model.HandshakeType.JOIN_RESPONSE -> "Welcome!"
                com.nulchaos.pichat.model.HandshakeType.LEFT -> "${message.senderName} left the room"
                com.nulchaos.pichat.model.HandshakeType.HEARTBEAT -> ""
            },
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray.copy(alpha = 0.7f),
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

@Composable
private fun ChatInputBar(
    input: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onPhotoClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .imePadding(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Photo button
        Card(
            modifier = Modifier.clickable(onClick = onPhotoClick).size(48.dp),
            shape = RoundedCornerShape(24.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = "Take photo",
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Text input
        Card(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(24.dp)
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                placeholder = { Text("Type a message...") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                maxLines = 1,
                shape = RoundedCornerShape(24.dp),
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent
                )
            )
        }

        // Send button
        Card(
            modifier = Modifier
                .clickable(enabled = input.isNotBlank(), onClick = onSend)
                .size(48.dp),
            shape = RoundedCornerShape(24.dp),
            enabled = input.isNotBlank()
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "Send",
                    modifier = Modifier.size(24.dp),
                    tint = if (input.isNotBlank()) MaterialTheme.colorScheme.primary else Color.Gray
                )
            }
        }
    }
}
