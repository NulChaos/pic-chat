# PictoChat 🎤📸

**Offline peer-to-peer messaging via Wi-Fi Direct** — inspired by the Nintendo DS PictoChat app.

## Features

- **No internet required** — communicates entirely over local Wi-Fi Direct mesh
- **Create rooms** — become the group owner and host a session
- **Join rooms** — discover nearby devices and join their sessions
- **Text & image messaging** — share typed messages and photos in real-time
- **Jetpack Compose UI** — modern Material Design 3 interface with light/dark theme
- **TCP over Wi-Fi Direct** — raw TCP sockets for reliable peer-to-peer data transfer

## Architecture

```
┌─────────────────────────────────────────────────┐
│                  PictoChat App                   │
├──────────────┬──────────────────────────────────┤
│  Jetpack     │  Wi-Fi Direct Networking         │
│  Compose UI  │                                   │
│              │  ┌─────────────┐  ┌───────────┐  │
│  Room Screen │  │  Group      │  │ TCP       │  │
│  Chat Screen │  │  Owner      │  │ Sockets   │  │
│  (Material3) │  │  (Host)     │  │ (Port 5000)│ │
└──────────────┘  └─────────────┘  └───────────┘  │
                    │              │                │
              Android   WifiP2pManager    Raw TCP   │
               API                 Network         │
└─────────────────────────────────────────────────────────┐
```

### Networking Flow

1. **Host** calls `createGroup()` → becomes Wi-Fi Direct Group Owner (like a soft AP)
2. **Peers** discover the host via `discoverPeers()` → connect as clients
3. Host opens **TCP server on port 5000** → accepts peer connections
4. All peers exchange messages via JSON-over-TCP with length-prefix framing
5. Handshake protocol manages room membership (JOIN, LEFT, HEARTBEAT)

## Tech Stack

- **Language**: Kotlin 2.0
- **UI**: Jetpack Compose + Material Design 3
- **Networking**: Android Wi-Fi Direct API (WifiP2pManager) + TCP Sockets
- **Architecture**: MVVM with ViewModel + StateFlow
- **Target SDK**: 35 (Android 15)
- **Min SDK**: 26 (Android 8.0 — required for WifiP2p APIs)

## Prerequisites

- Android Studio Ladybug (or later)
- Java 17+ JDK
- Android SDK with API level 35

## Building

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Install on connected device
./gradlew installDebug

# Run tests
./gradlew test
```

## Usage

1. Open PictoChat on both devices
2. Ensure Wi-Fi is **ON** (required for Wi-Fi Direct even without internet)
3. On one device, tap **"Create Room"** → becomes the host
4. On other devices, tap **"Discover Rooms"** → select the host's room
5. Start chatting with text and photos!

### Permissions Required

- Wi-Fi State
- Location (required by Android for Wi-Fi Direct discovery)
- Camera (for photo sharing)
- Bluetooth Scan/Connect (fallback connectivity)

## Project Structure

```
app/
├── src/main/java/com/nulchaos/pichat/
│   ├── MainActivity.kt              # App entry point + navigation
│   ├── WifiDirectService.kt         # Wi-Fi Direct service + TCP layer
│   ├── model/
│   │   └── WiFiModels.kt            # Data models (ChatMessage, PeerConnection)
│   ├── ui/
│   │   ├── screens/
│   │   │   ├── RoomScreen.kt        # Room selection screen
│   │   │   └── ChatScreen.kt        # Active chat screen
│   │   └── theme/
│   │       ├── Theme.kt             # Color scheme + PictoChatTheme
│   │       └── Type.kt              # Typography definitions
│   └── viewmodel/
│       └── ChatViewModel.kt         # App state management
├── src/main/res/
│   ├── values/                      # Strings, colors, themes
│   ├── mipmap-anydpi-v26/           # Adaptive icons
│   └── drawable/                    # Vector drawables
└── build.gradle.kts                 # App-level Gradle config
build.gradle.kts                     # Root Gradle config
settings.gradle.kts                  # Project settings
gradle.properties                    # Gradle properties
```

## Future Enhancements

- [ ] CameraX integration for actual photo capture
- [ ] Image gallery picker for existing photos
- [ ] Bluetooth LE fallback when Wi-Fi Direct unavailable
- [ ] Encrypted communication (TLS) over TCP
- [ ] Group chat with up to 8 participants
- [ ] Push-to-talk voice messaging
- [ ] Haptic feedback on message send/receive

## Known Limitations

- Requires Android 8.0+ (API 26+) for Wi-Fi Direct APIs
- Wi-Fi must be enabled (cannot use Bluetooth-only mode yet)
- No auto-reconnect if a peer disconnects mid-session
- Raw TCP without TLS — not suitable for sensitive communications

## License

MIT License — see LICENSE file.

## Acknowledgments

Inspired by [Nintendo DS PictoChat](https://www.nintendo.com/us/store/products/nintendo-ds-instruction-manual-pack/) 
and the spirit of offline mesh networking.
