# StreamVault Fork — Feature Roadmap

## Completed

### Pre-built DB Pipeline
- `tools/fetch_catalog.py` — fetches full catalog from Xtream API (VPN required)
- `tools/build_db.py` — builds Room-compatible DB from local JSON, schema from Room's 52.json
- `tools/push_db.sh` — pushes DB to TV via ADB
- `tools/push_apk.sh` — builds APK + pushes DB in one shot
- Category filtering: only US, CA, India, Asia, 4K, Sports channels indexed
- 30 curated favorites: NBA, ESPN, TSN, Sportsnet, CNN, CBC, Fox News

### Sports Center
- ESPN-style playoff bracket with Canvas connector lines and tree layout
- Live scores, standings, box scores with player headshots and stats
- League pill selector with logos (NBA, NHL, MLB, NFL, MLS)
- SmartTube integration for game highlights
- Design system migration (AppColors, FocusSpec, AppShapes)
- ESPN API integration: scoreboard, standings, summary endpoints

## In Progress

### Smart Category Organization
- Parse channel names to extract sport type (NBA, MLB, NFL, NHL, etc.)
- Group by currently-airing game detection via EPG
- Country-based filtering (US, CA, India sub-views)
- Auto-categorize: Sports → by league, News → by network, Entertainment → by source

### Quality Badges & Sorting
- Probe stream resolution on sync (Media3 metadata or ffprobe batch)
- Store resolution/codec/FPS per channel in Room DB (new columns or quality_options_json)
- Quality badges in channel list UI (4K, 1080p, 720p, SD)
- Sort/filter by resolution within categories
- Flag misleading labels (e.g., "4K" that's actually 720p)

### Channel Deduplication
- Detect duplicates across categories (same stream_id or similar names)
- Show highest quality version, hide lower quality dupes
- Merge duplicates into single entry with quality options

## Planned

### Auto-Start with Mullvad VPN
- On app launch, check if VPN is active (ConnectivityManager.hasTransport(VPN))
- If VPN not active, launch Mullvad via intent before loading streams
- Show VPN status indicator in the UI
- Option to auto-connect WireGuard tunnel on app start

### Background DB Refresh (Standby Mode)
- Periodic catalog refresh while TV is in standby/idle
- WorkManager job with configurable interval (daily, every 6h, etc.)
- Runs `fetch_catalog.py` equivalent logic inside the app
- Only refreshes channel list, preserves favorites and watch history
- Notification on refresh completion or errors

### MCP Integration for Remote Management
- Expose StreamVault management via MCP server on the Mac
- Tools: search channels, manage favorites, check stream quality, push config
- Enables Claude to directly search/manage IPTV from conversation
- ADB-based bridge: Mac MCP server → ADB → StreamVault DB

### ADB-Pushable Config
- JSON config file in `/sdcard/Android/data/com.streamvault.app/files/config.json`
- Configurable: favorites, category filters, quality preferences, VPN settings
- App reads config on startup, applies changes
- Enables remote management without rebuilding APK

### Smart Favorites
- Auto-suggest favorites based on viewing patterns
- "Quick access" for currently live NBA games
- Time-based favorites (news in morning, sports in evening)
- Regional favorites (CA channels when home, US when traveling)

## Architecture Notes

### Toolchain (Mac-side)
```
tools/
├── fetch_catalog.py    # Xtream API → local JSON (needs VPN)
├── build_db.py         # JSON → Room SQLite DB
├── push_db.sh          # DB → TV via ADB
├── push_apk.sh         # Build + install APK + push DB
├── catalog/            # Raw JSON from API
│   ├── server_info.json
│   ├── live_categories_all.json
│   ├── live_categories_filtered.json
│   ├── live_channels.json
│   ├── vod_categories.json
│   └── series_categories.json
└── output/
    └── streamvault_seed.db  # Pre-built Room DB
```

### Device Details
- Hisense Android TV at 192.168.2.21:5555
- WireGuard always-on VPN
- Mullvad VPN as backup
- Debug APK = debuggable → `run-as` access to databases/
