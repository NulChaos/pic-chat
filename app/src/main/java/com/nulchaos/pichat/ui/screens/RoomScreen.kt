package com.nulchaos.pichat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SignalCellular4Bar
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nulchaos.pichat.WifiState
import com.nulchaos.pichat.model.ChatMessage

/**
 * Home screen: create a room or join an existing one.
 * This is the entry point, mimicking PictoChat's "Room" menu.
 */
@Composable
fun RoomScreen(
    wifiState: WifiState,
    discoveredDevices: List<android.net.wifi.p2p.WifiP2pDevice>,
    onNameChange: (String) -> Unit,
    onCreateRoom: () -> Unit,
    onJoinRoom: (android.net.wifi.p2p.WifiP2pDevice) -> Unit,
    onStartDiscovery: () -> Unit,
    onError: String?,
    onClearError: () -> Unit,
    onMyNameSet: (String) -> Unit,
    myDisplayName: String = "",
) {
    var roomName by remember { mutableStateOf("") }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // App icon and title
            Icon(
                imageVector = Icons.Default.Chat,
                contentDescription = null,
                modifier = Modifier
                    .padding(bottom = 16.dp)
                    .size(72.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "PictoChat",
                style = MaterialTheme.typography.displayLarge.copy(color = MaterialTheme.colorScheme.primary),
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Offline messaging via Wi-Fi Direct",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )

            Spacer(modifier = androidx.compose.foundation.layout.Spacer(Modifier.height(32.dp)))

            // Enter display name
            OutlinedTextField(
                value = myDisplayName,
                onValueChange = {
                    roomName = it
                    onMyNameSet(it)
                },
                label = { Text("Your name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Enter your nickname") }
            )

            Spacer(modifier = androidx.compose.foundation.layout.Spacer(Modifier.height(24.dp)))

            // Create Room button
            val canCreate = wifiState in listOf(WifiState.ENABLED) && roomName.isNotBlank()
            ChatActionButton(
                label = "Create Room",
                icon = Icons.Default.Add,
                enabled = canCreate,
                onClick = { if (canCreate) onCreateRoom() }
            )

            Spacer(modifier = androidx.compose.foundation.layout.Spacer(Modifier.height(12.dp)))

            // Discover button
            ChatActionButton(
                label = "Discover Rooms",
                icon = Icons.Default.Search,
                enabled = wifiState == WifiState.ENABLED,
                onClick = onStartDiscovery
            )

            if (discoveredDevices.isNotEmpty()) {
                Spacer(modifier = androidx.compose.foundation.layout.Spacer(Modifier.height(16.dp)))
                Text(
                    text = "Available Rooms",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp)))

                discoveredDevices.forEach { device ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { onJoinRoom(device) },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = device.deviceName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Tap to join",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // WiFi state indicator
            Spacer(modifier = androidx.compose.foundation.layout.Spacer(Modifier.height(24.dp)))
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = if (wifiState == WifiState.DISABLED) Icons.Default.WifiOff else Icons.Default.Wifi,
                    contentDescription = null,
                    tint = if (wifiState == WifiState.DISABLED) Color.Red else Color.Green,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp)))
                Text(
                    text = when (wifiState) {
                        WifiState.DISABLED -> "Wi-Fi Direct unavailable"
                        WifiState.ENABLED -> "Wi-Fi Direct ready — turn on Wi-Fi"
                        WifiState.HOSTING -> "Hosting a room"
                        WifiState.JOINED -> "Joined a room"
                        WifiState.IDLE -> "Preparing..."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = when (wifiState) {
                        WifiState.DISABLED -> Color.Red
                        else -> Color.Green
                    }
                )
            }

            // Error message
            onError?.let { error ->
                Spacer(modifier = androidx.compose.foundation.layout.Spacer(Modifier.height(16.dp)))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = error,
                        color = Color.Red,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss",
                        modifier = Modifier.clickable { onClearError() }.size(20.dp),
                        tint = Color.Red
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (enabled) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.3f)
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
            androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp)))
            Text(
                text = label,
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
