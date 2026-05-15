# StreamVault-IPTV — Custom Fork Setup

Forked from [Davidona/StreamVault-IPTV](https://github.com/Davidona/StreamVault-IPTV).

## Purpose
Custom IPTV player for Android TV (Hisense) using the G2G Family / CandyCloud Xtream Codes provider. Replaces Sparkle TV which had no ADB-accessible config.

## Provider: G2G Family

**Active URLs:**
- http://cf.candycloud-8k.men (primary)
- http://pro.candycloud-8k.men
- http://cf.matrix.candycloud-8k.men
- http://pro.matrix.candycloud-8k.men
- http://vip.matrix.candycloud-8k.men

**Backup URL:**
- http://candycloudstrong8k.xyz

**Xtream Codes Credentials:**
- Username: `b5885330ec`
- Password: `5e46b997af`

**G2G Account:**
- User ID: `b5885330ec`
- Password: `5e46b997af`

**Limits:**
- 1 device at a time
- Avoid Smarters Player (not compatible)
- VPN recommended (ISP blocking)

## Build

### Prerequisites
- JDK 21
- Android SDK 36
- Android NDK (optional, for FFmpeg)

### Quick Build
```bash
cd /Users/shaanbhambra/projects/StreamVault-IPTV
./gradlew assembleDebug
```

### Credentials are auto-seeded
`local.properties` has Xtream dev credentials configured. On first debug build launch, the app auto-creates the provider — no manual entry needed.

### Deploy to TV
```bash
# Install
adb -s 192.168.2.21:5555 install -r app/build/outputs/apk/debug/app-debug.apk

# Launch
adb -s 192.168.2.21:5555 shell monkey -p com.streamvault.app -c android.intent.category.LAUNCHER 1

# Clear data and re-seed
adb -s 192.168.2.21:5555 shell pm clear com.streamvault.app
```

## VPN

WireGuard is set as **always-on VPN** on the Hisense TV (`com.wireguard.android`). All traffic is automatically routed through the tunnel. No app-level VPN integration needed — StreamVault works through the VPN transparently.

Mullvad VPN is also installed on the TV as a backup.

## Architecture

StreamVault is a multi-module Kotlin app:

| Module | Purpose |
|--------|---------|
| `app` | UI (Jetpack Compose), navigation, Android TV integrations |
| `data` | Room DB, Xtream/Stalker/M3U providers, API clients |
| `domain` | Business logic, models, repository interfaces |
| `player` | Media3 playback, FFmpeg decoder |

### Key Files for Configuration
| File | Purpose |
|------|---------|
| `local.properties` | Dev credentials (auto-seeds on debug build) |
| `app/build.gradle.kts` | Version, signing, build types |
| `data/.../Entities.kt` | Room DB schema (providers, channels, favorites) |
| `data/.../XtreamUrlFactory.kt` | Xtream Codes URL construction |
| `data/.../ProviderRepositoryImpl.kt` | Provider CRUD with encrypted credentials |
| `app/.../NetworkModule.kt` | OkHttp/Retrofit setup |

### Credential Storage
- Passwords encrypted with AES-256-GCM (Android Keystore)
- Format: `enc:v1:{base64(iv + ciphertext)}`
- Credentials never logged in plaintext

### Favorites System
- `FavoriteEntity` table with `contentId`, `contentType`, `position`
- `VirtualGroupEntity` for custom favorite groups
- Position step size 1024 for ordering

## Related Project
This project is managed alongside `/Users/shaanbhambra/projects/iptv_details/` which contains:
- Provider details and credentials (`g2g_iptv_details.md`)
- Stream quality testing methods (`stream_testing.md`)
- Channel quality reports (`channel_quality_report.md`)
- Device/ADB setup reference (`device_setup.md`)
