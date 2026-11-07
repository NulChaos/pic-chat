package com.nulchaos.pichat.viewmodel

import android.content.Context
import android.content.Intent
import android.net.wifi.p2p.WifiP2pDevice
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nulchaos.pichat.WifiDirectService
import com.nulchaos.pichat.WifiState
import com.nulchaos.pichat.model.ChatMessage
import com.nulchaos.pichat.model.HandshakeType
import com.nulchaos.pichat.model.PeerConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** ViewModel managing PictoChat state: room creation, joining, messaging */
class ChatViewModel(private val context: Context) : ViewModel() {

    companion object {
        private const val TAG = "ChatViewModel"
    }

    // --- State ---
    private val _wifiState = MutableStateFlow<WifiState>(WifiState.DISABLED)
    val wifiState: StateFlow<WifiState> = _wifiState.asStateFlow()

    private val _myName = MutableStateFlow("")
    val myName: StateFlow<String> = _myName.asStateFlow()

    private val _messageInput = MutableStateFlow("")
    val messageInput: StateFlow<String> = _messageInput.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _connectedPeers = MutableStateFlow<List<PeerConnection>>(emptyList())
    val connectedPeers: StateFlow<List<PeerConnection>> = _connectedPeers.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<WifiP2pDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<WifiP2pDevice>> = _discoveredDevices.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Service reference
    private var service: WifiDirectService? = null
    private var isBound = false

    fun bindService(ctx: Context) {
        if (isBound) return
        try {
            val intent = Intent(ctx, WifiDirectService::class.java).apply {
                setPackage(ctx.packageName)
            }
            ctx.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            isBound = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind Wi-Fi Direct service", e)
            _errorMessage.value = "Failed to start networking service: ${e.message}"
        }
    }

    fun unbindService(ctx: Context) {
        if (!isBound) return
        try {
            ctx.unbindService(serviceConnection)
            isBound = false
        } catch (_: Exception) {}
    }

    private val serviceConnection = object : android.content.ServiceConnection {
        override fun onServiceConnected(name: android.content.ComponentName?, svc: android.os.Binder?) {
            service = (svc as WifiDirectService.LocalBinder).getService()
            observeService()
        }

        override fun onServiceDisconnected(name: android.content.ComponentName?) {
            service = null
        }
    }

    private fun observeService() {
        val svc = service ?: return

        viewModelScope.launch {
            service?.wifiState?.collect { state ->
                _wifiState.value = state
                if (state == WifiState.HOSTING) {
                    _errorMessage.value = null
                }
            }
        }

        viewModelScope.launch {
            service?.messageChannel?.receiveCatching()?.collect { msg ->
                handleIncomingMessage(msg)
            }
        }

        // Register broadcast receiver for peer events
        val filter = android.content.IntentFilter().apply {
            addAction(WifiDirectService.ACTION_PEERS_FOUND)
            addAction(WifiDirectService.ACTION_PEER_CONNECTED)
            addAction(WifiDirectService.ACTION_PEER_DISCONNECTED)
        }
        context.registerReceiver(peerBroadcastReceiver, filter)
    }

    private val peerBroadcastReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                WifiDirectService.ACTION_PEERS_FOUND -> {
                    // Refresh via service
                    viewModelScope.launch {
                        _discoveredDevices.value = service?.let { s ->
                            wifiP2pManager.requestPeers(wifiChannel) { peers ->
                                peers.deviceList.filter { it.deviceName != s.myDeviceName }
                                    .toList()
                            }
                        } ?: emptyList()
                    }
                }
            }
        }
    }

    // We need a WifiP2pManager for peer refresh
    private val wifiP2pManager: WifiP2pManager?
        get() = service?.let { svc ->
            // Access via reflection or through the service interface
            null  // Use service's internal manager instead
        }

    private var _wifiChannel: android.net.wifi.p2p.WifiP2pManager.Channel? = null
    private val wifiChannel: android.net.wifi.p2p.WifiP2pManager.Channel?
        get() = _wifiChannel

    // Initialize WifiP2pManager channel from service
    fun initWifiP2pChannel(ctx: Context) {
        _wifiChannel = android.net.wifi.p2p.WifiP2pManager().initialize(
            ctx,
            android.os.Looper.getMainLooper(),
            null
        )
    }

    // --- Actions ---

    fun setMyName(name: String) {
        if (name.isNotBlank()) _myName.update { name.trim() }
    }

    fun setMessageInput(text: String) {
        _messageInput.update { text }
    }

    fun sendTextMessage() {
        val input = _messageInput.value.trim()
        if (input.isEmpty() || service == null) return
        val msg = ChatMessage.Text(
            senderId = service?.myDeviceId ?: "",
            senderName = _myName.value,
            text = input
        )
        sendToAllPeers(msg)
        // Also add to local list for echo
        _chatMessages.update { it + msg }
        _messageInput.update { "" }
    }

    fun sendImageMessage(imageData: ByteArray, thumbnailData: ByteArray?) {
        if (service == null) return
        val msg = ChatMessage.Image(
            senderId = service?.myDeviceId ?: "",
            senderName = _myName.value,
            imageData = imageData,
            thumbnailData = thumbnailData
        )
        sendToAllPeers(msg)
        _chatMessages.update { it + msg }
    }

    private fun sendToAllPeers(message: ChatMessage) {
        // Send via TCP sockets managed by service
        // The service broadcasts internally; we just trigger the call
        service?.let { svc ->
            // Direct method to broadcast
            try {
                val json = when (message) {
                    is ChatMessage.Text -> """{"type":"text","senderId":"${message.senderId}","senderName":"${message.senderName}","text":"${escapeJson(message.text)}","ts":${message.timestamp}}"""
                    is ChatMessage.Image -> {
                        val base64 = android.util.Base64.encodeToString(message.imageData, android.util.Base64.NO_WRAP)
                        """{"type":"image","senderId":"${message.senderId}","senderName":"${message.senderName}","data":"$base64","ts":${message.timestamp}}"""
                    }
                    is ChatMessage.Handshake -> {
                        """{"type":"handshake","subtype":"${message.type.name}","senderId":"${message.senderId}","senderName":"${message.senderName}"}"""
                    }
                }
                // Write via each peer socket
                svc.peerSockets.forEach { info ->
                    try {
                        val out = java.io.ObjectOutputStream(info.socket?.getOutputStream() ?: return@forEach).apply {
                            writeObject(java.lang.Long.valueOf(42))  // marker
                        }
                        // Actually use the service's internal method
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to send to peer", e)
                    }
                }
            } catch (e: Exception) {
                _errorMessage.value = "Send failed: ${e.message}"
            }
        }
    }

    private fun escapeJson(text: String): String {
        return text.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
    }

    private fun handleIncomingMessage(msg: ChatMessage) {
        when (msg) {
            is ChatMessage.Text -> _chatMessages.update { it + msg }
            is ChatMessage.Image -> _chatMessages.update { it + msg }
            is ChatMessage.Handshake -> {
                if (msg.type == HandshakeType.JOIN_REQUEST) {
                    // New peer joined; add to connected list
                    val existing = _connectedPeers.value.find { it.ipAddress != "" }
                    val peers = _connectedPeers.value + PeerConnection(
                        peerName = msg.senderName,
                        ipAddress = "local",
                        connected = true
                    )
                    _connectedPeers.update { peers }
                } else if (msg.type == HandshakeType.LEFT) {
                    // Remove peer
                    _connectedPeers.update {
                        it.filter { p -> p.peerName != msg.senderName }
                    }
                }
            }
        }
    }

    // --- Room management ---

    fun createRoom() {
        service?.let { svc ->
            if (svc.wifiState.value == WifiState.ENABLED) {
                svc.createGroup()
                _errorMessage.value = null
            } else {
                _errorMessage.value = "Wi-Fi Direct not enabled"
            }
        } ?: run {
            _errorMessage.value = "Service not bound — try reconnecting"
        }
    }

    fun joinDevice(device: WifiP2pDevice) {
        service?.joinDevice(device) ?: run {
            _errorMessage.value = "Service not bound"
        }
    }

    fun leaveRoom() {
        service?.let { svc ->
            // Broadcast LEFT handshake before leaving
            val msg = ChatMessage.Handshake(
                type = HandshakeType.LEFT,
                senderId = svc.myDeviceId,
                senderName = _myName.value
            )
            sendToAllPeers(msg)
            svc.removeGroup()
        }
    }

    fun startDiscovery() {
        service?.startDiscovery() ?: run {
            _errorMessage.value = "Service not bound"
        }
    }

    fun clearError() {
        _errorMessage.update { null }
    }

    override fun onCleared() {
        super.onCleared()
        // Don't unbind here — let Activity manage lifecycle
    }
}
