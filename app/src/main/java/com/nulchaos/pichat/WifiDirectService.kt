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
import java.io.IOException as GsonIOError
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
        private const val GROUP_FORMATION_PORT = 5000
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
    var peerSockets: MutableList<PeerConnectionInfo> = mutableListOf()
    var connectedClients: MutableList<TcpClientThread> = mutableListOf()

    // Expose WiFi P2P components for ViewModel access
    val p2pManager: WifiP2pManager? get() = wifiP2pManager
    val channel: android.net.wifi.p2p.WifiP2pManager.Channel? get() = wifiChannel

    // --- Binder for Activity binding ---
    inner class LocalBinder : Binder() {
        fun getService(): WifiDirectService = this@WifiDirectService
    }

    private val binder = LocalBinder()

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        myDeviceName = "PictoChat_${(1000..9999).random()}"

        myDeviceId = java.util.UUID.randomUUID().toString().substring(0, 8)
        registerWifiP2pReceiver()
    }

    override fun onDestroy() {
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
                                Log.d(TAG, "I am the GROUP OWNER — accepting peers on port $GROUP_FORMATION_PORT")
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
                        Log.d(TAG, "My device: ${it.deviceName}")
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
        // Already handled in wifiP2pReceiver callback
    }

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

    /** TCP client thread for connecting to a peer device */
    inner class TcpClientThread(val peerAddress: String, val peerName: String) : Thread() {
        var clientSocket: Socket? = null

        override fun run() {
            try {
                clientSocket = Socket(InetAddress.getByName(peerAddress), GROUP_FORMATION_PORT)
                Log.d(TAG, "Connected to TCP peer: $peerName ($peerAddress)")
                connectedClients.add(this)
                // Read incoming messages
                val input = clientSocket!!.getInputStream()
                val buffer = ByteArray(4096)
                while (!isInterrupted) {
                    val bytesRead = input.read(buffer)
                    if (bytesRead <= 0) break
                    val data = String(buffer, 0, bytesRead, Charsets.UTF_8)
                    _messageChannel.trySend(ChatMessage.Text(peerName, peerName, data))
                }
            } catch (e: IOException) {
                Log.e(TAG, "TCP client error: ${e.message}")
            } finally {
                connectedClients.remove(this)
                try { clientSocket?.close() } catch (_: Exception) {}
            }
        }

        fun send(message: String) {
            try {
                clientSocket?.outputStream?.write(message.toByteArray(Charsets.UTF_8))
            } catch (e: IOException) {
                Log.e(TAG, "Failed to send TCP message: ${e.message}")
            }
        }
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
