package com.nulchaos.pichat

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.wifi.p2p.WifiP2pDevice
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.nulchaos.pichat.ui.screens.ChatScreen
import com.nulchaos.pichat.ui.screens.RoomScreen
import com.nulchaos.pichat.ui.theme.PictoChatTheme
import com.nulchaos.pichat.viewmodel.ChatViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Main activity: bootstraps the app, manages permissions and service binding,
 * hosts the navigation between Room selection and Chat screens.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: ChatViewModel by viewModels {
        androidx.lifecycle.viewmodel.compose.viewModelFactory {
            inject<ChatViewModel>(
                android.content.Context::class.java to Context::class.java
            )
        }
    }

    // Permission launcher
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            startWifiDirectService()
        } else {
            Toast.makeText(this, "Permissions required for Wi-Fi Direct", Toast.LENGTH_LONG).show()
        }
    }

    // App state
    private var isInChat: Boolean = false
    private var myNameInput: String = ""
    private var errorMessage: String? = null
    private val chatMessages = mutableListOf<com.nulchaos.pichat.model.ChatMessage>()
    private val connectedPeers = mutableListOf<PeerConnection>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Check and request permissions
        checkPermissions()

        setContent {
            PictoChatTheme {
                if (isInChat) {
                    ChatView(
                        viewModel = viewModel,
                        onLeaveRoom = ::onLeaveRoom,
                        onSendMessage = ::onSendMessage,
                        onSendImage = ::onSendImage,
                        chatMessages = chatMessages,
                        connectedPeers = connectedPeers,
                        myNameInput = myNameInput,
                        errorMessage = errorMessage,
                        onClearError = { errorMessage = null },
                        messageInput = "",
                        onMessageInputChange = {},
                        onTakePhoto = ::onTakePhoto
                    )
                } else {
                    RoomView(
                        viewModel = viewModel,
                        onNameChange = { myNameInput = it },
                        onCreateRoom = ::onCreateRoom,
                        onJoinRoom = ::onJoinDevice,
                        onStartDiscovery = ::onStartDiscovery,
                        errorMessage = errorMessage,
                        onClearError = { errorMessage = null },
                        onMyNameSet = { myNameInput = it }
                    )
                }
            }
        }

        // Observe service state via ViewModel flow
        lifecycleScope.launch {
            viewModel.wifiState.collect { state ->
                when (state) {
                    WifiState.HOSTING -> {}  // Still in room
                    WifiState.JOINED -> {}   // Joined another's room
                    WifiState.DISABLED -> {
                        Toast.makeText(this@MainActivity, "Wi-Fi Direct disabled", Toast.LENGTH_SHORT).show()
                    }
                    else -> {}
                }
            }
        }

        lifecycleScope.launch {
            viewModel.messageInput.collect { input ->
                // Could bind to local state if needed
            }
        }
    }

    private fun checkPermissions() {
        val permissions = arrayOf(
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.CAMERA,
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            } else {
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN
            }
        )

        // Check if all granted
        val needed = permissions.filter { checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED }
        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        } else {
            startWifiDirectService()
        }
    }

    private fun startWifiDirectService() {
        viewModel.bindService(this)
        viewModel.initWifiP2pChannel(this)
        val intent = Intent(this, WifiDirectService::class.java).apply { setPackage(packageName) }
        try {
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private val serviceConnection = object : android.content.ServiceConnection {
        override fun onServiceConnected(name: android.content.ComponentName?, svc: android.os.Binder?) {
            val wifiSvc = (svc as WifiDirectService.LocalBinder).getService()

            // Observe WiFi state
            lifecycleScope.launch {
                wifiSvc.wifiState.collect { state ->
                    when (state) {
                        WifiState.HOSTING, WifiState.JOINED -> {}
                        WifiState.ENABLED -> {}
                        WifiState.DISABLED -> {
                            errorMessage = "Wi-Fi Direct unavailable"
                        }
                        else -> {}
                    }
                }
            }

            // Observe messages via broadcast receiver
            val filter = IntentFilter(WifiDirectService.ACTION_MESSAGE_RECEIVED).apply {
                addAction(WifiDirectService.ACTION_PEERS_FOUND)
                addAction(WifiDirectService.ACTION_PEER_CONNECTED)
                addAction(WifiDirectService.ACTION_PEER_DISCONNECTED)
            }
            registerReceiver(object : android.content.BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    when (intent?.action) {
                        WifiDirectService.ACTION_MESSAGE_RECEIVED -> {
                            val msg = intent.getParcelableExtra<com.nulchaos.pichat.model.ChatMessage>(WifiDirectService.EXTRA_MESSAGE)
                            if (msg != null && isInChat) {
                                chatMessages.add(msg)
                            }
                        }
                        WifiDirectService.ACTION_PEERS_FOUND -> {
                            // Refreshed via ViewModel
                        }
                        WifiDirectService.ACTION_PEER_CONNECTED -> {
                            val name = intent.getStringExtra(WifiDirectService.EXTRA_PEER_NAME) ?: "Unknown"
                            connectedPeers.add(PeerConnection(name, "local", true))
                        }
                        WifiDirectService.ACTION_PEER_DISCONNECTED -> {
                            val name = intent.getStringExtra(WifiDirectService.EXTRA_PEER_NAME)
                            if (name != null) {
                                connectedPeers.removeAll { it.peerName == name }
                            }
                        }
                    }
                }
            }, filter)
        }

        override fun onServiceDisconnected(name: android.content.ComponentName?) {
            Toast.makeText(this@MainActivity, "Networking service disconnected", Toast.LENGTH_SHORT).show()
        }
    }

    // --- Actions ---

    private fun onCreateRoom() {
        if (myNameInput.isBlank()) {
            errorMessage = "Please enter your name"
            return
        }
        viewModel.setMyName(myNameInput)
        startWifiDirectService()
        viewModel.createRoom()
        isInChat = true
        chatMessages.clear()
        connectedPeers.clear()
    }

    private fun onJoinDevice(device: WifiP2pDevice) {
        if (myNameInput.isBlank()) {
            errorMessage = "Please enter your name first"
            return
        }
        viewModel.setMyName(myNameInput)
        startWifiDirectService()
        viewModel.joinDevice(device)
        isInChat = true
        chatMessages.clear()
        connectedPeers.clear()

        // Connect to host on port 5000
        val serviceInfo = android.net.wifi.p2p.WifiP2pDnsSdServiceInfo.newInstance(
            "_pichat",
            "_tcp",
            null
        )
        Toast.makeText(this, "Connecting to ${device.deviceName}...", Toast.LENGTH_SHORT).show()
    }

    private fun onLeaveRoom() {
        viewModel.leaveRoom()
        isInChat = false
        chatMessages.clear()
        connectedPeers.clear()
    }

    private fun onSendMessage(text: String) {
        if (text.isNotBlank()) {
            // Message sent via ViewModel's sendTextMessage or direct
            val msg = com.nulchaos.pichat.model.ChatMessage.Text(
                senderId = viewModel.myName.value,
                senderName = myNameInput,
                text = text,
                timestamp = System.currentTimeMillis()
            )
            chatMessages.add(msg)
        }
    }

    private fun onSendImage(imageData: ByteArray, thumbnailData: ByteArray?) {
        val msg = com.nulchaos.pichat.model.ChatMessage.Image(
            senderId = viewModel.myName.value,
            senderName = myNameInput,
            imageData = imageData,
            thumbnailData = thumbnailData,
            timestamp = System.currentTimeMillis()
        )
        chatMessages.add(msg)
    }

    private fun onStartDiscovery() {
        viewModel.startDiscovery()
    }

    private fun onTakePhoto() {
        Toast.makeText(this, "Camera integration — requires CameraX permission flow", Toast.LENGTH_SHORT).show()
        // TODO: Integrate with CameraX for actual photo capture
        // For now, simulate with a placeholder image
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unbindService(serviceConnection)
        } catch (_: Exception) {}
        viewModel.unbindService(this)
    }
}
