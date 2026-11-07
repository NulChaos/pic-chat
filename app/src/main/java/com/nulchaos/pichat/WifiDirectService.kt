package com.nulchaos.pichat

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.nulchaos.pichat.model.ChatMessage
import com.nulchaos.pichat.model.HandshakeType
import com.nulchaos.pichat.model.PeerConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * Foreground service managing Wi-Fi Direct peer-to-peer networking.
 *
 * Flow:
 * 1. Host: Creates a group (acts as access point), waits for peers to connect via TCP port 5000.
 * 2. Client: Discovers host device, connects to it on port 5000.
 * 3. All peers communicate via raw TCP sockets — broadcast to all connected peers.
 *
 * This mirrors the Nintendo DS PictoChat architecture: one device hosts the session,
 * others join by selecting it from the discovery list. No internet required.
 */
class WifiDirectService : Service() {

    companion object {
        private const val TAG = "WifiDirectSvc"
        private const val GROUP formation_port = 5000
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "pichat_wifi_direct"

        // Intent action names for local broadcasts within the app
        const val ACTION_WIFI_STATE = "com.nulchaos.pichat.ACTION_WIFI_STATE"
        const val ACTION_PEERS_FOUND = "com.nulchaos.pichat.ACTION_PEERS_FOUND"
        const val ACTION_PEER_CONNECTED = "com.nulchaos.pichat.ACTION_PEER_CONNECTED"
        const val ACTION_PEER_DISCONNECTED = "com.nulchaos.pichat.ACTION_PEER_DISCONNECTED"
        const val ACTION_MESSAGE_RECEIVED = "com.nulchaos.pichat.ACTION_MESSAGE_RECEIVED"

        // Extra keys for broadcasts
        const val EXTRA_WIFI_STATE = "extra_wifi_state"
        const val EXTRA_PEERS = "extra_peers"
        const val EXTRA_PEER_NAME = "extra_peer_name"
        const val EXTRA_MESSAGE = "extra_message"
    }

    // --- State flows ---
    private val _wifiState = MutableStateFlow<WifiState>(WifiState.DISABLED)
    val wifiState: StateFlow<WifiState> = _wifiState.asStateFlow()

    private val _connectedPeers = MutableStateFlow<List<PeerConnection>>(emptyList())
    val connectedPeers: StateFlow<List<PeerConnection>> = _connectedPeers.asStateFlow()

    private val _discoveredPeers = MutableStateFlow<List<WifiP2pDevice>>(emptyList())
    val discoveredPeers: StateFlow<List<WifiP2pDevice>> = _discoveredPeers.asStateFlow()

    // Message channel for external consumers
    private val _messageChannel = Channel<ChatMessage>(capacity = Channel.UNLIMITED)
    val messageChannel: Channel<ChatMessage> = _messageChannel

    // Group info (which device is the group owner)
    private var groupInfo: WifiP2pInfo? = null

    // My identity
    internal var myDeviceName: String = ""
    internal var myDeviceId: String = ""

    // TCP server socket (host mode)
    private var serverSocket: ServerSocket? = null
    private var peerSockets: MutableList<PeerConnectionInfo> = mutableListOf()
    private var connectedClients: MutableList<TcpClientThread> = mutableListOf()

    // --- Binder for Activity binding ---
    inner class LocalBinder : Binder() {
        fun getService(): WifiDirectService = this@WifiDirectService
    }

    private val binder = LocalBinder()

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        myDeviceName = android.net.wifi.WifiManager.selfMacAddress?.let { mac ->
            "Picto_${mac.substring(mac.length - 4).replace(":", "")}"
        } ?: "Picto_${(1000..9999).random()}"

        myDeviceId = java.util.UUID.randomUUID().toString().substring(0, 8)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Initializing Wi-Fi Direct..."))
        registerWifiP2pReceiver()
    }

    override fun onDestroy() {
        cleanup()
        unregisterReceiver(wifiP2pReceiver)
        super.onDestroy()
    }

    // ==================== Wi-Fi Direct API setup ====================

    private lateinit var wifiP2pManager: WifiP2pManager
    private lateinit var wifiChannel: WifiP2pManager.Channel

    private fun initWifiP2p() {
        wifiP2pManager = getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
        wifiChannel = wifiP2pManager.initialize(this, mainLooper, {})
    }

    private val wifiP2pReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                    _wifiState.value = if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                        WifiState.ENABLED
                    } else {
                        WifiState.DISABLED
                    }
                    broadcastWifiState(_wifiState.value)
                }

                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                    // Will fetch peers in next available callback
                }

                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    wifiP2pManager.requestConnectionInfo(wifiChannel) { info ->
                        groupInfo = info
                        if (info.groupFormed) {
                            if (info.isGroupOwner) {
                                _wifiState.value = WifiState.HOSTING
                                broadcastWifiState(_wifiState.value)
                                startServerSocket()
                                Log.d(TAG, "I am the GROUP OWNER — accepting peers on port $GROUP_formation_port")
                            } else {
                                _wifiState.value = WifiState.JOINED
                                broadcastWifiState(_wifiState.value)
                            }
                        } else {
                            _wifiState.value = WifiState.IDLE
                            broadcastWifiState(_wifiState.value)
                            stopServerSocket()
                        }
                    }
                }

                WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                    val device = intent.getParcelableExtra<WifiP2pDevice>(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE)
                    device?.let {
                        myDeviceName = it.deviceName
                        Log.d(TAG, "My device: ${it.deviceName} (${it.deviceStatus})")
                    }
                }
            }
        }
    }

    private fun registerWifiP2pReceiver() {
        initWifiP2p()
        val filter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        }
        registerReceiver(wifiP2pReceiver, filter)
    }

    // ==================== Discovery ====================

    /** Start discovering other devices */
    fun startDiscovery() {
        if (_wifiState.value != WifiState.ENABLED) return
        wifiP2pManager.discoverPeers(wifiChannel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "Discovery initiated")
            }

            override fun onFailure(reason: Int) {
                Log.e(TAG, "Discovery failed: $reason")
            }
        })

        // Periodically request updated peer list
        schedulePeerRefresh()
    }

    private var peerRefreshRunnable: Runnable? = null
    private val peerRefreshHandler = android.os.Handler(android.os.Looper.getMainLooper())

    private fun schedulePeerRefresh() {
        peerRefreshRunnable?.let { peerRefreshHandler.removeCallbacks(it) }
        peerRefreshRunnable = object : Runnable {
            override fun run() {
                wifiP2pManager.requestPeers(wifiChannel) { peers ->
                    val list = peers.deviceList.toList()
                    _discoveredPeers.value = list.filterNot { it.deviceName == myDeviceName }
                    broadcastPeersFound(_discoveredPeers.value)
                }
                peerRefreshHandler.postDelayed(this, 5000) // every 5 seconds
            }
        }
        peerRefreshHandler.post(peerRefreshRunnable!!)
    }

    fun cancelDiscovery() {
        peerRefreshRunnable?.let { peerRefreshHandler.removeCallbacks(it) }
        peerRefreshRunnable = null
    }

    // ==================== Connection Actions ====================

    /** Host a room: form a group (become group owner), accept incoming TCP connections */
    fun createGroup() {
        if (_wifiState.value !in listOf(WifiState.ENABLED, WifiState.IDLE)) return
        wifiP2pManager.createGroup(wifiChannel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "Group creation requested")
            }

            override fun onFailure(reason: Int) {
                Log.e(TAG, "Group creation failed: $reason")
            }
        })
    }

    /** Join a room by connecting to the host device */
    fun joinDevice(device: WifiP2pDevice) {
        val config = WifiP2pConfig().apply {
            this.deviceAddress = device.deviceAddress
            deviceName = device.deviceName
        }
        wifiP2pManager.connect(wifiChannel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "Connection request sent to ${device.deviceName}")
            }

            override fun onFailure(reason: Int) {
                Log.e(TAG, "Connection failed: $reason")
            }
        })
    }

    /** Remove group / leave room */
    fun removeGroup() {
        wifiP2pManager.removeGroup(wifiChannel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "Group removed")
            }

            override fun onFailure(reason: Int) {
                Log.e(TAG, "Group removal failed: $reason")
            }
        })
    }

    // ==================== TCP Communication Layer ====================

    /** Start listening for peer TCP connections (host only) */
    private fun startServerSocket() {
        if (_wifiState.value != WifiState.HOSTING) return
        Thread(Dispatchers.IO.asExecutor()) {
            try {
                serverSocket = ServerSocket(GROUP_formation_port)
                Log.d(TAG, "TCP server listening on port $GROUP_formation_port")
                while (!Thread.currentThread().isInterrupted) {
                    val client = serverSocket?.accept()
                    if (client != null) {
                        peerSockets.add(PeerConnectionInfo(client, client.inetAddress.hostAddress ?: "unknown"))
                        Log.d(TAG, "Peer connected: ${client.inetAddress.hostAddress}")

                        // Send handshake to announce presence
                        val msg = ChatMessage.Handshake(
                            type = HandshakeType.JOIN_RESPONSE,
                            senderId = myDeviceId,
                            senderName = myDeviceName
                        )
                        sendToAllPeers(msg)

                        // Start a reader thread for this peer
                        val clientThread = TcpClientThread(client, this@WifiDirectService)
                        connectedClients.add(clientThread)
                        clientThread.start()
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "Server socket error", e)
            }
        }.start()
    }

    /** Connect as a client to the host device */
    fun connectToHost(hostAddress: String, hostName: String) {
        Thread(Dispatchers.IO.asExecutor()) {
            try {
                val socket = Socket(InetAddress.getByName(hostAddress), GROUP_formation_port)
                socket.keepAlive = true
                peerSockets.add(PeerConnectionInfo(socket, hostAddress))
                Log.d(TAG, "Connected to host: $hostName ($hostAddress)")

                // Send handshake
                val msg = ChatMessage.Handshake(
                    type = HandshakeType.JOIN_REQUEST,
                    senderId = myDeviceId,
                    senderName = myDeviceName
                )
                sendToAllPeers(msg)

                val clientThread = TcpClientThread(socket, this@WifiDirectService)
                connectedClients.add(clientThread)
                clientThread.start()
            } catch (e: IOException) {
                Log.e(TAG, "Failed to connect to host $hostAddress", e)
            }
        }.start()
    }

    /** Send a message to all connected peers */
    fun broadcastMessage(message: ChatMessage) {
        val data = serializeMessage(message)
        peerSockets.forEach { info ->
            try {
                val out = info.socket?.let {
                    java.io.ObjectOutputStream(
                        it.getOutputStream()
                    )
                }
                // Use a simple JSON-based protocol over TCP instead of Java serialization
                // (more portable across devices / languages)
                writeJsonMessage(info.outputStream, message)
            } catch (e: IOException) {
                Log.e(TAG, "Failed to send message to peer", e)
            }
        }
    }

    fun sendMessageToPeer(socketInfo: PeerConnectionInfo, message: ChatMessage) {
        try {
            writeJsonMessage(socketInfo.outputStream, message)
        } catch (e: IOException) {
            Log.e(TAG, "Failed to send message", e)
        }
    }

    private fun sendToAllPeers(message: ChatMessage) {
        peerSockets.forEach { info ->
            try {
                writeJsonMessage(info.outputStream, message)
            } catch (e: IOException) {
                Log.e(TAG, "Failed to broadcast", e)
            }
        }
    }

    // ==================== JSON serialization for TCP messages ====================

    /** Simple JSON-based wire protocol using kotlinx.serialization-compatible approach */
    private fun writeJsonMessage(outputStream: OutputStream?, message: ChatMessage) {
        if (outputStream == null) return
        val json = when (message) {
            is ChatMessage.Text -> {
                """{"type":"text","senderId":"${message.senderId}","senderName":"${message.senderName}","text":"${escapeJson(message.text)}","ts":${message.timestamp}}"""
            }
            is ChatMessage.Image -> {
                val base64 = android.util.Base64.encodeToString(message.imageData, android.util.Base64.NO_WRAP)
                """{"type":"image","senderId":"${message.senderId}","senderName":"${message.senderName}","data":"$base64","ts":${message.timestamp}}"""
            }
            is ChatMessage.Handshake -> {
                """{"type":"handshake","subtype":"${message.type.name}","senderId":"${message.senderId}","senderName":"${message.senderName}"}"""
            }
        }
        // Write length-prefix JSON (4-byte big-endian int + payload)
        val bytes = json.toByteArray(Charsets.UTF_8)
        outputStream.write(bytes.size.toInt().let { i ->
            byteArrayOf(
                (i shr 24).toByte(),
                (i shr 16).toByte(),
                (i shr 8).toByte(),
                i.toByte()
            )
        })
        outputStream.write(bytes)
        outputStream.flush()
    }

    /** Read a length-prefixed JSON message from input stream */
    fun readMessage(inputStream: InputStream?): ChatMessage? {
        if (inputStream == null) return null
        val lenBytes = ByteArray(4)
        val nRead = inputStream.read(lenBytes, 0, 4)
        if (nRead < 4) return null // connection closed or partial read

        val len = ((lenBytes[0].toInt() and 0xFF) shl 24) or
                ((lenBytes[1].toInt() and 0xFF) shl 16) or
                ((lenBytes[2].toInt() and 0xFF) shl 8) or
                (lenBytes[3].toInt() and 0xFF)

        val data = ByteArray(len)
        var offset = 0
        while (offset < len) {
            val read = inputStream.read(data, offset, len - offset)
            if (read == -1) break
            offset += read
        }

        val json = String(data, Charsets.UTF_8)
        return parseMessage(json)
    }

    private fun parseMessage(json: String): ChatMessage {
        // Minimal JSON parser — extract fields without external deps
        fun getString(key: String): String {
            val pattern = "\"$key\":\""
            val idx = json.indexOf(pattern) + pattern.length
            if (idx < 2) return ""
            var end = idx
            var escape = false
            while (end < json.length) {
                if (escape) { end++; continue }
                when (json[end]) {
                    '\\' -> escape = true
                    '"' -> break
                }
                end++
            }
            return json.substring(idx, end).replace("\\\"", "\"").replace("\\\\", "\\")
        }

        fun getLong(key: String): Long {
            val pattern = "\"$key\":"
            val idx = json.indexOf(pattern) + pattern.length
            if (idx < 2) return System.currentTimeMillis()
            var end = idx
            while (end < json.length && json[end].isDigit()) end++
            return json.substring(idx, end).toLongOrNull() ?: System.currentTimeMillis()
        }

        val type = getString("type")
        return when (type) {
            "text" -> ChatMessage.Text(
                senderId = getString("senderId"),
                senderName = getString("senderName"),
                text = getString("text"),
                timestamp = getLong("ts")
            )
            "image" -> {
                val base64Data = getString("data")
                ChatMessage.Image(
                    senderId = getString("senderId"),
                    senderName = getString("senderName"),
                    imageData = android.util.Base64.decode(base64Data, android.util.Base64.NO_WRAP),
                    thumbnailData = null,
                    timestamp = getLong("ts")
                )
            }
            "handshake" -> ChatMessage.Handshake(
                type = enumValueOf<HandshakeType>(getString("subtype")),
                senderId = getString("senderId"),
                senderName = getString("senderName")
            )
            else -> ChatMessage.Text(senderId = "", senderName = "Unknown", text = json)
        }
    }

    private fun escapeJson(text: String): String {
        return text.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    // ==================== Peer TCP client thread ====================

    private class TcpClientThread(
        private val socket: Socket,
        private val service: WifiDirectService
    ) : Thread("Pichat-Peer-${socket.inetAddress.hostAddress}") {
        override fun run() {
            try {
                val input = socket.getInputStream()
                while (!isInterrupted) {
                    val msg = service.readMessage(input)
                    if (msg != null) {
                        // Deliver to message channel and broadcast to app UI
                        service._messageChannel.trySend(msg).onSuccess {
                            val intent = Intent(WifiDirectService.ACTION_MESSAGE_RECEIVED).apply {
                                putExtra(WifiDirectService.EXTRA_MESSAGE, msg)
                            }
                            service.sendBroadcast(intent)
                        }
                    } else {
                        break // connection closed
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "Peer thread error", e)
            } finally {
                cleanupConnection(socket)
            }
        }
    }

    private fun cleanupConnection(socket: Socket) {
        try { socket.close() } catch (_: Exception) {}
        peerSockets.removeAll { it.socket == socket }
        connectedClients.removeAll { it.socket == socket }
        Log.d(TAG, "Connection cleaned up")
    }

    // ==================== Notifications ====================

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "PictoChat Wi-Fi Direct",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Maintains local mesh connection" }
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(content: String) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle("PictoChat")
        .setContentText(content)
        .setOngoing(true)
        .build()

    // ==================== Cleanup ====================

    private fun cleanup() {
        stopServerSocket()
        connectedClients.forEach { it.interrupt(); try { it.join(1000L) } catch (_: Exception) {} }
        connectedClients.clear()
        peerSockets.clear()
        _connectedPeers.value = emptyList()
    }

    private fun startServerSocket() { /* Already handled in wifiP2pReceiver callback */ }
    private fun stopServerSocket() {
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
    }

    // ==================== Broadcast helpers ====================

    private fun broadcastWifiState(state: WifiState) {
        sendBroadcast(Intent(ACTION_WIFI_STATE).putExtra(EXTRA_WIFI_STATE, state.ordinal))
    }

    private fun broadcastPeersFound(peers: List<WifiP2pDevice>) {
        sendBroadcast(Intent(ACTION_PEERS_FOUND).putExtra(EXTRA_PEERS, peers.toTypedArray() as Array<out android.os.Parcelable>))
    }

    // ==================== Data class for socket tracking ====================

    data class PeerConnectionInfo(
        val socket: Socket,
        val address: String
    ) {
        val outputStream: OutputStream? get() = try { socket?.getOutputStream() } catch (_: Exception) { null }
    }
}

/** State of the Wi-Fi Direct session */
enum class WifiState {
    DISABLED,  // Wi-Fi Direct not supported or disabled
    ENABLED,   // Wi-Fi Direct available but no group formed
    IDLE,      // Group exists but I'm not owner
    JOINED,    // Joined a group as client
    HOSTING,   // I'm the group owner (hosting)
}
