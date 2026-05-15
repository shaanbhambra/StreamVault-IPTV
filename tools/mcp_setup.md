# StreamVault MCP Server Setup

## Add to Claude Code

Run this command to add the StreamVault MCP server:

```bash
claude mcp add streamvault -- /Users/shaanbhambra/projects/StreamVault-IPTV/tools/.venv/bin/python3 /Users/shaanbhambra/projects/StreamVault-IPTV/tools/mcp_server.py
```

## Prerequisites

1. StreamVault debug APK installed on TV
2. App running (auto-starts the debug HTTP API on port 8585)
3. Port forwarding set up (auto-handled by MCP server on first call):
   ```bash
   adb -s 192.168.2.21:5555 forward tcp:8585 tcp:8585
   ```

## Available MCP Tools

| Tool | Description |
|------|-------------|
| `streamvault_status` | Provider info, channel/favorite counts, VPN state |
| `search_channels` | Search channels by name |
| `list_channels_by_category` | Browse channels in a category |
| `get_channel` | Get details of a specific channel |
| `list_categories` | List all categories (LIVE/MOVIE/SERIES) |
| `list_favorites` | List all favorite channels |
| `add_favorite` | Add channel to favorites |
| `remove_favorite` | Remove channel from favorites |
| `launch_streamvault` | Launch the app on TV |
| `stop_streamvault` | Stop the app on TV |
| `tv_input` | Type text on the TV |
| `tv_keypress` | Press a key (enter, back, up, down, etc.) |
