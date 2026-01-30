# Open Mobile TTS - Android App Setup Guide

This guide explains how the Android app will work and how users will connect it to their server.

## Overview

The Android app will be a **native mobile application** that:
- Connects to YOUR self-hosted server
- Provides offline audio playback
- Full background audio support (unlike iOS PWA)
- Lock screen controls
- Better performance than web app

## User Journey (First-Time Setup)

### 1. Install App from Play Store (or APK)

User downloads "Open Mobile TTS" app.

### 2. Welcome Screen

```
┌─────────────────────────────────┐
│  🎙️ Open Mobile TTS            │
│                                 │
│  Your Private Text-to-Speech    │
│                                 │
│  This app connects to YOUR      │
│  server. You'll need:           │
│                                 │
│  ✓ Server URL                   │
│  ✓ Username & Password          │
│                                 │
│  Don't have a server yet?       │
│  [Setup Guide] [Continue]       │
└─────────────────────────────────┘
```

### 3. Server Connection Screen

```
┌─────────────────────────────────┐
│  ← Back    Connect to Server    │
├─────────────────────────────────┤
│                                 │
│  Server URL                     │
│  ┌───────────────────────────┐ │
│  │ https://your-server.com   │ │
│  └───────────────────────────┘ │
│                                 │
│  💡 Examples:                   │
│  • https://tts.example.com      │
│  • http://192.168.1.100:8000    │
│  • http://your-ip:8000          │
│                                 │
│  [Scan QR Code] [Test Connection]│
└─────────────────────────────────┘
```

**Smart features**:
- Auto-detect http:// or https://
- Validate URL format
- Test connection before proceeding
- QR code scanning for easy setup

### 4. Test Connection

When user enters URL and clicks "Test Connection":

```
Connecting to server...
✓ Server found!
✓ TTS engine ready
✓ 11 voices available

[Continue to Login]
```

If connection fails:
```
❌ Cannot reach server

Common issues:
• Make sure server is running
• Check URL is correct
• Try http:// instead of https://
• Check firewall settings

[Try Again] [Help]
```

### 5. Login Screen

```
┌─────────────────────────────────┐
│  ← Back    Sign In               │
├─────────────────────────────────┤
│                                 │
│  Connected to:                  │
│  your-server.com                │
│                                 │
│  Username                       │
│  ┌───────────────────────────┐ │
│  │                           │ │
│  └───────────────────────────┘ │
│                                 │
│  Password                       │
│  ┌───────────────────────────┐ │
│  │ ••••••••••                │ │
│  └───────────────────────────┘ │
│                                 │
│  ☐ Remember me                  │
│                                 │
│  [Sign In]                      │
│                                 │
│  Need help? [Setup Guide]       │
└─────────────────────────────────┘
```

### 6. Main App Screen

Once logged in:
```
┌─────────────────────────────────┐
│  ☰  Open Mobile TTS      ⚙️ ⋮   │
├─────────────────────────────────┤
│                                 │
│  Enter Text                     │
│  ┌───────────────────────────┐ │
│  │ Type or paste text here...│ │
│  │                           │ │
│  │                           │ │
│  └───────────────────────────┘ │
│                                 │
│  Voice: Female (Heart)  ▼       │
│  Speed: 1.0x            [====]  │
│                                 │
│  [📄 Upload Document]            │
│  [🎙️ Generate Speech]            │
│                                 │
│  ─── History ───                │
│  🎵 "The Adolescence of..."     │
│     29 min • 2 hours ago        │
│  🎵 "Test of the system"        │
│     18 sec • yesterday          │
└─────────────────────────────────┘
```

## Technical Architecture

### App Structure

```
Android App (Kotlin/Java)
├── Activities
│   ├── WelcomeActivity      ← First launch
│   ├── SetupActivity        ← Server connection
│   ├── LoginActivity        ← Authentication
│   └── MainActivity         ← TTS interface
├── Services
│   ├── TTSService           ← Background TTS generation
│   ├── AudioPlayerService   ← Background playback
│   └── SyncService          ← History sync
├── Data
│   ├── Room Database        ← Local audio cache
│   ├── SharedPreferences    ← Server URL, credentials
│   └── File Storage         ← MP3 files
└── Network
    ├── ApiClient (Retrofit) ← HTTP client
    └── AuthInterceptor      ← JWT token handling
```

### Server Connection Flow

```
User Input URL
    ↓
Validate Format
    ↓
Test Connection (GET /health)
    ↓
    ├─ Success ──→ Save server URL
    │               ↓
    │           Proceed to login
    │
    └─ Failure ──→ Show error
                    ↓
                Suggest fixes
```

### Authentication Flow

```
User Login
    ↓
POST /token (username, password)
    ↓
    ├─ Success ──→ Save JWT token
    │               ↓
    │           Save to secure storage
    │               ↓
    │           Navigate to main app
    │
    └─ Failure ──→ Show error
                    ↓
                Allow retry
```

### TTS Generation Flow

```
User enters text
    ↓
Click "Generate Speech"
    ↓
GET /api/tts/stream?text=...
    ↓
Receive streaming response
    ↓
    ├─ Parse TIMING: metadata
    ├─ Collect MP3 chunks
    └─ Show progress (X KB received)
    ↓
Combine into MP3 file
    ↓
Save to Room database
    ↓
Play audio
```

## QR Code Setup Feature

To make setup easier, the **server** can generate a QR code containing:

```json
{
  "server_url": "https://your-server.com",
  "name": "My TTS Server"
}
```

**Server endpoint** (add to FastAPI):
```python
@app.get("/setup/qr")
async def get_setup_qr():
    """Generate QR code for easy mobile setup"""
    import qrcode
    import base64
    from io import BytesIO

    data = {
        "server_url": f"http://{settings.HOST}:{settings.PORT}",
        "name": "Open Mobile TTS"
    }

    qr = qrcode.make(json.dumps(data))
    buffer = BytesIO()
    qr.save(buffer, format='PNG')

    return {"qr_code": base64.b64encode(buffer.getvalue()).decode()}
```

**Android app** scans QR and auto-fills server URL.

## Settings Screen

```
┌─────────────────────────────────┐
│  ← Settings                      │
├─────────────────────────────────┤
│  Server Connection               │
│  URL: your-server.com            │
│  Status: ✓ Connected             │
│  [Change Server] [Test Connection]│
│                                  │
│  Account                         │
│  Username: admin                 │
│  [Logout] [Change Password]      │
│                                  │
│  Audio                           │
│  Default Voice: Female (Heart)   │
│  Default Speed: 1.0x             │
│  ☑ Auto-play generated audio     │
│  ☑ Keep screen on during playback│
│                                  │
│  Storage                         │
│  Cache size: 234 MB              │
│  [Clear Cache] [Manage Files]    │
│                                  │
│  About                           │
│  Version: 1.0.0                  │
│  [Open Source Licenses]          │
│  [Privacy Policy]                │
└─────────────────────────────────┘
```

## Key Features for Android App

### 1. Offline Playback
- Downloaded audio stored in Room database
- Works without internet after download
- Auto-sync when connected

### 2. Background Audio
- Continues playing when screen off
- Lock screen controls (play/pause/skip)
- Notification with player controls

### 3. Smart Caching
- Automatic cache management
- Configurable cache size
- LRU eviction policy

### 4. Network Optimization
- Resume partial downloads
- Retry failed requests
- Bandwidth detection

## Development Stack Recommendations

### Option 1: Native Android (Kotlin)

**Pros**:
- Best performance
- Full access to Android APIs
- Smaller app size

**Cons**:
- Android-only (need separate iOS app)
- More development time

**Stack**:
- Kotlin
- Jetpack Compose (UI)
- Retrofit (networking)
- Room (database)
- ExoPlayer (audio)
- Coroutines (async)

### Option 2: React Native

**Pros**:
- Share code with iOS
- Faster development
- Can reuse web app logic

**Cons**:
- Larger app size
- Bridge overhead

**Stack**:
- React Native
- Axios (networking)
- AsyncStorage (cache)
- react-native-track-player (audio)

### Option 3: Flutter

**Pros**:
- Beautiful UI out of box
- Share code with iOS
- Good performance

**Cons**:
- Larger app size
- Less native feel

**Stack**:
- Flutter/Dart
- Dio (networking)
- Hive (database)
- just_audio (audio player)

## Recommended: Native Android (Kotlin)

For this app, I recommend **native Android with Kotlin** because:
1. Background audio is critical (works best native)
2. Android-only for now (iOS is secondary)
3. Best performance for audio streaming
4. Smaller app size

## Implementation Roadmap

### Phase 1: MVP (Week 1-2)
- [ ] Setup wizard (server URL, login)
- [ ] Text input screen
- [ ] Basic TTS generation
- [ ] Audio playback
- [ ] Settings screen

### Phase 2: Core Features (Week 3-4)
- [ ] History with local cache
- [ ] Background audio playback
- [ ] Lock screen controls
- [ ] Download management
- [ ] Offline mode

### Phase 3: Polish (Week 5-6)
- [ ] QR code setup
- [ ] Material Design 3 UI
- [ ] Dark mode
- [ ] Voice selection
- [ ] Speed control
- [ ] Document upload

### Phase 4: Release (Week 7-8)
- [ ] Testing on multiple devices
- [ ] Performance optimization
- [ ] Play Store listing
- [ ] User documentation
- [ ] Beta testing

## Code Example: Server Connection

```kotlin
// ApiClient.kt
object ApiClient {
    private var baseUrl: String? = null
    private var token: String? = null

    fun initialize(serverUrl: String) {
        baseUrl = serverUrl.trimEnd('/')
    }

    fun setToken(jwt: String) {
        token = jwt
    }

    suspend fun testConnection(): Result<Boolean> {
        return try {
            val response = httpClient.get("$baseUrl/health")
            if (response.status.isSuccess()) {
                Result.success(true)
            } else {
                Result.failure(Exception("Server returned ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun login(username: String, password: String): Result<String> {
        return try {
            val response = httpClient.post("$baseUrl/token") {
                setBody(FormDataContent(Parameters.build {
                    append("username", username)
                    append("password", password)
                }))
            }
            val data = response.body<TokenResponse>()
            setToken(data.access_token)
            Result.success(data.access_token)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
```

## User Documentation

Include in-app:

### Help Screen
```
How to Set Up

1. Start your server
   On your computer, run:
   cd server && uvicorn src.main:app

2. Find your server URL
   • Same network: http://YOUR-IP:8000
   • Internet: https://your-domain.com

3. Enter URL in app
   • Tap "Connect to Server"
   • Enter URL
   • Tap "Test Connection"

4. Login
   • Use your server credentials
   • Tap "Remember me" to stay logged in

Need help? Visit:
github.com/yourname/openmobiletts
```

## Security Considerations

1. **HTTPS Required for Public Access**
   - App should warn if using http:// over internet
   - Allow http:// only for local network (192.168.x.x)

2. **Token Storage**
   - Use EncryptedSharedPreferences
   - Clear on logout

3. **Certificate Pinning** (optional)
   - Pin server certificate
   - Prevent MITM attacks

## Next Steps

1. **Create Android project** using Android Studio
2. **Implement setup wizard** with server connection
3. **Add API client** using Retrofit
4. **Build basic UI** with Jetpack Compose
5. **Test with your server**

Would you like me to:
1. Create the actual Android project structure?
2. Write the Kotlin code for setup wizard?
3. Create a detailed API client implementation?

This is the foundation for a robust Android app that makes it easy for users to connect to their self-hosted TTS server!
