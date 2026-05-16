# StreamVault IPTV — Complete Documentation

## Overview

StreamVault is a customized Android TV IPTV player forked from [Davidona/StreamVault-IPTV](https://github.com/Davidona/StreamVault-IPTV). This fork adds a debug HTTP API, phone companion PWA, AI-powered search, and a pre-built database pipeline for instant setup.

**Provider:** G2G Family (CandyCloud / Strong 8K)
**Device:** Hisense Android TV (MediaTek, API 30) at `192.168.2.21:5555`
**VPN:** WireGuard always-on VPN (required for IPTV streams)

---

## Architecture

```
┌─────────────────────────┐     WiFi (LAN)      ┌──────────────────┐
│    Hisense Android TV    │ ◄──────────────────► │   Phone (PWA)    │
│                          │                      │                  │
│  StreamVault App         │     http://:8585     │  Companion UI    │
│  ├─ Media3 Player        │ ◄──────────────────► │  ├─ Search       │
│  ├─ Room Database        │                      │  ├─ Favorites    │
│  ├─ Debug API (NanoHTTPD)│                      │  ├─ AI Chat      │
│  ├─ Gemini AI Client     │                      │  ├─ Voice Search │
│  └─ Foreground Service   │                      │  ├─ Settings     │
│                          │                      │  └─ Zap Mode     │
└──────────┬───────────────┘                      └──────────────────┘
           │ ADB (192.168.2.21:5555)
           │ Port Forward tcp:8585
┌──────────┴───────────────┐
│     Mac (Development)     │
│  ├─ tools/fetch_catalog.py│  Xtream API → JSON
│  ├─ tools/build_db.py     │  JSON → Room SQLite DB
│  ├─ tools/push_db.sh      │  DB → TV via ADB
│  ├─ tools/mcp_server.py   │  MCP for Claude Code
│  └─ gradlew assembleDebug │  Build APK
└───────────────────────────┘
```

---

## Quick Start

### First-Time Setup

```bash
# 1. Clone
gh repo clone <your-username>/StreamVault-IPTV
cd StreamVault-IPTV

# 2. Configure credentials
cp local.properties.example local.properties
# Edit local.properties:
#   sdk.dir=/Users/<you>/Library/Android/sdk
#   xtream.dev.server=http://candycloudstrong8k.xyz
#   xtream.dev.username=b5885330ec
#   xtream.dev.password=5e46b997af
#   xtream.dev.name=G2G Family
#   gemini.api.key=<your-key>

# 3. Build
./gradlew assembleDebug

# 4. Fetch channel catalog (needs VPN)
mullvad connect
python3 tools/fetch_catalog.py
mullvad disconnect

# 5. Build pre-populated database
python3 tools/build_db.py          # Filtered (US/CA/India/4K)
# or
python3 tools/build_db.py --all    # All 882 categories

# 6. Install and push
adb connect 192.168.2.21:5555
adb install -r app/build/outputs/apk/debug/app-debug.apk
tools/push_db.sh
```

### Daily Usage

```bash
# Push updated DB (after editing favorites.json, etc.)
tools/push_db.sh

# Full rebuild + push
tools/push_apk.sh

# Quick channel switch via CLI
adb forward tcp:8585 tcp:8585
curl -X POST http://127.0.0.1:8585/quick_switch -d '{"query":"ESPN"}'
```

---

## Phone Companion (PWA)

**URL:** `http://192.168.2.21:8585/`

Open on your phone (same WiFi). Install as PWA: Share → Add to Home Screen.

### Tabs

| Tab | Description |
|-----|-------------|
| **Favorites** | Your 42 curated channels |
| **All** | Browse all channels |
| **Sports** | ESPN, NBA, TSN, Sportsnet |
| **News** | CNN, Fox News, CBC, CTV |
| **4K** | True 4K UHD channels |
| **Canada** | Canadian channels |
| **Toronto** | CP24, CityTV, CTV Toronto, Global |
| **Categories** | Browse all categories with channel counts |
| **AI** | Chat with Gemini — "Find me the Raptors game" |
| **Settings** | VPN toggle, QR code, provider info |

### Features

- **Voice Search:** Tap microphone → speak → auto-routes to AI or channel search
- **Zap Mode:** Toggle "Zap: ON" → tap any channel to instantly play on TV
- **EPG Now Playing:** Shows current program name under each channel
- **QR Code:** Settings → Show QR Code on TV for other devices

---

## Debug HTTP API

Runs on port 8585 as a foreground service (survives app backgrounding).

### Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/` | Companion web UI |
| GET | `/status` | Provider info, channel/favorite counts, VPN state |
| GET | `/channels?search=X&limit=N` | Search channels |
| GET | `/channels?category_id=X` | Browse by category |
| GET | `/channel/:id` | Channel details |
| GET | `/categories?type=LIVE` | List categories with counts |
| GET | `/favorites` | List favorites |
| POST | `/favorites/add` | Add favorite `{"channel_id": 123}` |
| POST | `/favorites/remove` | Remove favorite `{"channel_id": 123}` |
| POST | `/play` | Play channel `{"channel_id": 123}` |
| POST | `/quick_switch` | Search + play `{"query": "ESPN"}` |
| GET | `/epg?search=NBA` | Search EPG programs |
| POST | `/epg/now` | Batch EPG `{"channel_ids": [1,2,3]}` |
| POST | `/ai` | AI search `{"query": "...", "mode": "chat"}` |
| GET | `/ai/status` | Gemini configuration status |
| GET | `/settings` | Device settings, VPN, provider |
| POST | `/vpn/on` | Connect WireGuard VPN |
| POST | `/vpn/off` | Disconnect WireGuard VPN |
| POST | `/categories/toggle_all` | Enable all 882 categories |
| POST | `/launch` | Bring StreamVault to foreground |
| POST | `/wake` | Wake TV screen + launch app |
| POST | `/show_qr` | Display QR code on TV |
| POST | `/clear_prefs` | Clear DataStore (hidden categories) |
| GET | `/benchmark` | Performance benchmarks |
| POST | `/precache` | Pre-cache all channels |
| GET | `/qr` | QR code page |
| GET | `/manifest.json` | PWA manifest |
| GET | `/sw.js` | Service worker |

---

## MCP Server

Registered as `streamvault` in Claude Code. Enables LLM-driven TV control.

### Setup

```bash
claude mcp add streamvault -- \
  /path/to/StreamVault-IPTV/tools/.venv/bin/python3 \
  /path/to/StreamVault-IPTV/tools/mcp_server.py
```

### Tools

| Tool | Description |
|------|-------------|
| `streamvault_status()` | Provider, channel counts, VPN state |
| `search_channels("NBA")` | Search channels by name |
| `list_channels_by_category(492)` | Browse category |
| `get_channel(123)` | Channel details |
| `list_categories("LIVE")` | All categories |
| `list_favorites()` | Current favorites |
| `add_favorite(123)` | Add to favorites |
| `remove_favorite(123)` | Remove from favorites |
| `play_channel(123)` | Tune to channel |
| `quick_switch("ESPN")` | Search + instant play |
| `find_and_play("NBA playoff")` | Smart search + play (prefers 4K) |
| `search_epg("basketball")` | Search program guide |
| `whats_on_now()` | Currently airing programs |
| `enable_all_categories()` | Show all 882 categories |
| `show_qr_on_tv()` | Display QR code on TV |
| `get_companion_url()` | Phone companion URL |
| `launch_streamvault()` | Launch app on TV |
| `stop_streamvault()` | Stop app |
| `tv_input("text")` | Type text on TV |
| `tv_keypress("enter")` | Press remote key |

---

## Pre-Built Database Pipeline

### Files

```
tools/
├── fetch_catalog.py         # Fetches Xtream API → JSON (needs VPN)
├── build_db.py              # JSON → Room-compatible SQLite DB
├── push_db.sh               # Pushes DB to TV, clears prefs, relaunches
├── push_apk.sh              # Full build + install + push DB
├── favorites.json           # 42 curated favorite channels
├── mcp_server.py            # MCP server for Claude Code
├── catalog/                 # Raw JSON from API
│   ├── server_info.json
│   ├── live_categories_all.json       # 882 categories
│   ├── live_categories_filtered.json  # 157 categories (US/CA/India/4K)
│   ├── live_channels.json             # 16k channels
│   ├── vod_categories.json            # 426 VOD categories
│   └── series_categories.json         # 354 series categories
└── output/
    └── streamvault_seed.db            # Pre-built Room DB (6.8MB)
```

### Workflow

1. **Fetch** (once, or when catalog changes): `mullvad connect && python3 tools/fetch_catalog.py && mullvad disconnect`
2. **Build**: `python3 tools/build_db.py` (or `--all` for all categories)
3. **Push**: `tools/push_db.sh`

### Favorites

Edit `tools/favorites.json` to customize. Format:
```json
{"favorites": [{"stream_id": 437219, "note": "US: ESPN USA HD"}]}
```

Rebuild and push after editing.

---

## IPTV Provider Details

**Provider:** G2G Family / CandyCloud / Strong 8K
**Primary Server:** `http://cf.candycloud-8k.men`
**Fastest Server:** `http://candycloudstrong8k.xyz` (1.3s, direct)

### All Servers (all accept our credentials)

| Server | Latency | Type |
|--------|---------|------|
| candycloudstrong8k.xyz | 1296ms | Direct (fastest) |
| cf.candycloud-8k.men | 1342ms | Cloudflare CDN |
| pro.candycloud-8k.men | 1431ms | Cloudflare CDN |
| cf.matrix.candycloud-8k.men | 5021ms | Matrix (slow) |
| pro.matrix.candycloud-8k.men | 5015ms | Matrix (slow) |
| vip.matrix.candycloud-8k.men | 5021ms | Matrix (slow) |

**Credentials:** `b5885330ec` / `5e46b997af`
**Limits:** 1 simultaneous connection (same-IP exempt)
**Expiry:** Check via `/status` endpoint

---

## Player Optimizations

Tuned for Hisense MediaTek TV (API 30):

| Setting | Default | Tuned | Impact |
|---------|---------|-------|--------|
| Live min buffer | 8s | 4s | Faster channel start |
| Playback buffer | 1.5s | 0.8s | Begin playback sooner |
| Rebuffer threshold | 5s | 3s | Faster stall recovery |
| TextureView timeout | 9s | 5s | Faster GPU fallback |
| Connect timeout | 12s | 8s | Faster failure detection |
| Read timeout | 20s | 12s | Faster retry cycle |
| Decoder policy | AUTO (neutral) | AUTO (HW preferred) | Better MediaTek compat |

### Codec Priority
1. MediaTek hardware (OMX.MTK.*, c2.mtk.*)
2. Qualcomm hardware (OMX.QCOM.*, c2.qti.*)
3. Other hardware
4. Software (OMX.Google.*, c2.android.*)
5. FFmpeg (audio only: ac3, eac3, dca, mp2, truehd)

---

## AI Integration

**Model:** Gemini 3.1 Flash Lite (via generativelanguage.googleapis.com)
**API Key:** In `local.properties` as `gemini.api.key`

### How It Works
1. User asks a question (phone companion or MCP)
2. Server builds context from favorites + channel search results
3. Sends to Gemini with structured JSON response schema
4. Returns channel IDs with play buttons

### Example Queries
- "Find me the NBA game tonight"
- "What Canadian news channels are there?"
- "Show me 4K sports channels"
- "I want to watch the Raptors"

---

## Troubleshooting

| Issue | Fix |
|-------|-----|
| Channels not loading | Check VPN is connected. Try different server URL. |
| "Preparing your library" | Push pre-built DB: `tools/push_db.sh` |
| Only 8 favorites showing | Clear DataStore: `tools/push_db.sh` (auto-clears) |
| API not responding | Wait 10s after app launch. Re-forward: `adb forward tcp:8585 tcp:8585` |
| API dies on background | Fixed — runs as foreground service now |
| Audio but no video | TextureView fallback will auto-trigger after 5s |
| Silent audio (no sound) | FFmpeg audio fallback auto-triggers for EAC3/DTS |
| VPN toggle error | Fixed — runs on main thread Handler |
| Account banned | Stop multi-stream. Use single server. Wait 1 hour. |

---

## Project Structure

```
StreamVault-IPTV/
├── app/                           # Android TV app (Kotlin, Compose)
│   ├── src/main/                  # Production code
│   │   └── java/com/streamvault/app/
│   │       ├── MainActivity.kt    # Entry point, VPN check, debug API init
│   │       ├── di/                # Hilt DI modules
│   │       │   ├── NetworkModule.kt  # OkHttp, player engine, audio focus
│   │       │   └── DatabaseModule.kt # Room database
│   │       └── ui/screens/        # Compose screens
│   │           ├── home/          # Channel browsing
│   │           ├── player/        # Video playback
│   │           └── settings/      # App settings
│   ├── src/debug/                 # Debug-only code
│   │   ├── AndroidManifest.xml    # Foreground service registration
│   │   ├── assets/companion.html  # Phone companion PWA
│   │   └── java/com/streamvault/app/debug/
│   │       ├── DebugApiServer.kt  # NanoHTTPD HTTP API
│   │       ├── DebugApiService.kt # Foreground service wrapper
│   │       ├── DebugApiStarter.kt # Reflection-based init
│   │       ├── DebugDao.kt       # Raw SQLite queries
│   │       ├── GeminiClient.kt   # Gemini AI integration
│   │       └── QrCodeGenerator.kt
│   ├── build.gradle.kts          # App config, signing, Gemini key
│   └── compose-stability-config.txt  # Compose recomposition hints
├── data/                          # Data layer (Room, API clients)
├── domain/                        # Domain models (Channel, Category, etc.)
├── player/                        # Media3/ExoPlayer playback
│   ├── playback/
│   │   ├── PlaybackBufferPolicies.kt  # Buffer tuning
│   │   ├── PlayerTimeoutProfile.kt    # Network timeouts
│   │   └── CodecPreference.kt        # Decoder selection
│   └── Media3PlayerEngine.kt     # Player engine
├── tools/                         # Mac-side toolchain
└── DOCS.md                        # This file
```
