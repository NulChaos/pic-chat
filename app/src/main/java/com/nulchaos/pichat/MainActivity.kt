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
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.lifecycleScope
import com.nulchaos.pichat.ui.screens.ChatScreen
import com.nulchaos.pichat.ui.screens.RoomScreen
import com.nulchaos.pichat.ui.theme.PictoChatTheme
import com.nulchaos.pichat.viewmodel.ChatViewModel
import kotlinx.coroutines.launch

/**
 * Main activity: bootstraps the app, manages permissions and service binding,
 * hosts the navigation between Room selection and Chat screens.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: ChatViewModel by viewModels {
        viewModelFactory {
            initializer {
                ChatViewModel(applicationContext)
            }
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

    // Track service bound state to avoid double-binding
    private var isServiceBound = false
    private var wifiSvc: WifiDirectService? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Check and request permissions
        checkPermissions()

        setContent {
            PictoChatTheme {
                val ctx = androidx.compose.ui.platform.LocalContext.current
                val chatMessages by viewModel.chatMessages.collectAsState(emptyList())
                val connectedPeers by viewModel.connectedPeers.collectAsState(emptyList())
                val discoveredDevices by viewModel.discoveredDevices.collectAsState(emptyList())
                val wifiState by viewModel.wifiState.collectAsState(WifiState.DISABLED)
                val myName by viewModel.myName.collectAsState("")
                val errorMessage by viewModel.errorMessage.collectAsState(null)

                // Navigate to chat when a room is created or joined
                var isInChat by remember { mutableStateOf(false) }
                
                // Auto-navigate when viewModel indicates we're in a room
                androidx.compose.runtime.LaunchedEffect(wifiState) {
                    if (wifiState == com.nulchaos.pichat.WifiState.HOSTING || 
                        wifiState == com.nulchaos.pichat.WifiState.JOINED) {
                        isInChat = true
                    }
                }

                when {
                    isInChat -> {
                        ChatScreen(
                            myName = myName,
                            onLeaveRoom = {
                                viewModel.leaveRoom()
                                isInChat = false
                            },
                            onSendMessage = { text -> viewModel.setMessageInput(text) },
                            onSendImage = { data, thumb -> viewModel.sendImageMessage(data, thumb) },
                            chatMessages = chatMessages,
                            connectedPeers = connectedPeers,
                            wifiState = wifiState,
                            onError = errorMessage,
                            onClearError = { viewModel.clearError() },
                            messageInput = "",
                            onMessageInputChange = {},
                            onTakePhoto = {}
                        )
                    }
                    else -> {
                        RoomScreen(
                            wifiState = wifiState,
                            discoveredDevices = discoveredDevices,
                            onNameChange = { name -> viewModel.setMyName(name) },
                            onCreateRoom = {
                                if (myName.isBlank()) {
                                    Toast.makeText(ctx, "Please enter your name", Toast.LENGTH_SHORT).show()
                                } else {
                                    viewModel.startWifiService(this)
                                }
                            },
                            onJoinRoom = { device ->
                                if (myName.isNotBlank()) {
                                    viewModel.setMyName(myName)
                                    viewModel.joinDevice(device)
                                } else {
                                    Toast.makeText(ctx, "Please enter your name first", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onStartDiscovery = { viewModel.startDiscovery() },
                            onError = errorMessage,
                            onClearError = { viewModel.clearError() },
                            onMyNameSet = { name -> viewModel.setMyName(name) },
                            myDisplayName = myName
                        )
                    }
                }
            }
        }
    }

    private fun checkPermissions() {
        val basePerms = arrayOf(
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.CAMERA
        )
        
        val extraPerms = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN)
        }
        
        val permissions = basePerms + extraPerms

        // Check if all granted
        val needed = permissions.filter { checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED }
        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        } else {
            startWifiDirectService()
        }
    }

    private fun startWifiDirectService() {
        val ctx = this
        if (!isServiceBound) {
            viewModel.initWifiP2pChannel(ctx)
            val intent = Intent(ctx, WifiDirectService::class.java).apply { setPackage(packageName) }
            try {
                bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
                isServiceBound = true
            } catch (e: Exception) {
                e.printStackTrace()
                viewModel.setError("Failed to start networking service: ${e.message}")
            }
        }
    }

    private val serviceConnection = object : android.content.ServiceConnection {
        override fun onServiceConnected(name: android.content.ComponentName?, service: android.os.IBinder?) {
            val binder = service as? WifiDirectService.LocalBinder
            if (binder != null) {
                val wifiSvc = binder.getService()

                // Observe WiFi state via ViewModel instead
                lifecycleScope.launch {
                    wifiSvc.wifiState.collect { state ->
                        when (state) {
                            in listOf(WifiState.HOSTING, WifiState.JOINED, WifiState.ENABLED) -> {}
                            WifiState.DISABLED -> {
                                viewModel.setError("Wi-Fi Direct unavailable")
                            }
                            else -> {}
                        }
                    }
                }
            }
        }

        override fun onServiceDisconnected(name: android.content.ComponentName?) {
            Toast.makeText(this@MainActivity, "Networking service disconnected", Toast.LENGTH_SHORT).show()
            viewModel.setError("Networking service disconnected")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unbindService(serviceConnection)
            isServiceBound = false
        } catch (_: Exception) {}
        viewModel.unbindService(this)
    }
}
