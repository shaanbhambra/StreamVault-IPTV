#!/Users/shaanbhambra/projects/StreamVault-IPTV/tools/.venv/bin/python3
"""
MCP Server for StreamVault IPTV — exposes the debug HTTP API as MCP tools.

Prerequisites:
  1. StreamVault debug APK running on TV (192.168.2.21:5555)
  2. Port forwarded: adb -s 192.168.2.21:5555 forward tcp:8585 tcp:8585
  3. pip install mcp

Add to Claude Code MCP config:
  {
    "mcpServers": {
      "streamvault": {
        "command": "python3",
        "args": ["/Users/shaanbhambra/projects/StreamVault-IPTV/tools/mcp_server.py"]
      }
    }
  }
"""

import json
import subprocess
import sys
import urllib.request
import urllib.error

API_BASE = "http://127.0.0.1:8585"
ADB_DEVICE = "192.168.2.21:5555"

try:
    from mcp.server.fastmcp import FastMCP
except ImportError:
    print("Install mcp: pip install mcp", file=sys.stderr)
    sys.exit(1)

mcp = FastMCP("StreamVault IPTV")


def api_get(path: str) -> dict:
    """GET request to the StreamVault debug API."""
    url = f"{API_BASE}{path}"
    req = urllib.request.Request(url)
    try:
        with urllib.request.urlopen(req, timeout=5) as resp:
            return json.loads(resp.read().decode())
    except urllib.error.URLError:
        # Try to set up port forwarding and retry
        subprocess.run(
            ["adb", "-s", ADB_DEVICE, "forward", "tcp:8585", "tcp:8585"],
            capture_output=True
        )
        with urllib.request.urlopen(req, timeout=5) as resp:
            return json.loads(resp.read().decode())


def api_post(path: str, data: dict) -> dict:
    """POST request to the StreamVault debug API."""
    url = f"{API_BASE}{path}"
    body = json.dumps(data).encode()
    req = urllib.request.Request(url, data=body, headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=5) as resp:
        return json.loads(resp.read().decode())


def adb_cmd(cmd: str) -> str:
    """Run an ADB command."""
    result = subprocess.run(
        ["adb", "-s", ADB_DEVICE] + cmd.split(),
        capture_output=True, text=True, timeout=10
    )
    return result.stdout.strip() or result.stderr.strip()


@mcp.tool()
def streamvault_status() -> str:
    """Get StreamVault status: provider info, channel/favorite counts, VPN state."""
    data = api_get("/status")
    p = data.get("provider", {})
    lines = [
        f"Provider: {p.get('name', '?')} ({p.get('status', '?')})",
        f"Server: {p.get('server_url', '?')}",
        f"Channels: {data.get('channel_count', 0)}",
        f"Categories: {data.get('category_count', 0)}",
        f"Favorites: {data.get('favorite_count', 0)}",
        f"VPN: {'Active' if data.get('vpn_active') else 'INACTIVE'}",
        f"Network: {'Connected' if data.get('network_available') else 'Disconnected'}",
    ]
    return "\n".join(lines)


@mcp.tool()
def search_channels(query: str, limit: int = 20) -> str:
    """Search IPTV channels by name. Returns matching channels with IDs."""
    data = api_get(f"/channels?search={urllib.parse.quote(query)}&limit={limit}")
    channels = data.get("channels", [])
    if not channels:
        return f"No channels found for '{query}'"
    lines = [f"Found {len(channels)} channels for '{query}':"]
    for ch in channels:
        lines.append(f"  [{ch['id']}] {ch['name']} (stream:{ch['stream_id']}, cat:{ch['category_name']})")
    return "\n".join(lines)


@mcp.tool()
def list_channels_by_category(category_id: int, limit: int = 30, offset: int = 0) -> str:
    """List channels in a specific category by category_id."""
    data = api_get(f"/channels?category_id={category_id}&limit={limit}&offset={offset}")
    channels = data.get("channels", [])
    if not channels:
        return f"No channels in category {category_id}"
    lines = [f"{len(channels)} channels in category {category_id}:"]
    for ch in channels:
        lines.append(f"  [{ch['id']}] {ch['name']}")
    return "\n".join(lines)


@mcp.tool()
def get_channel(channel_id: int) -> str:
    """Get details of a specific channel by its database ID."""
    data = api_get(f"/channel/{channel_id}")
    if "error" in data:
        return f"Error: {data['error']}"
    return json.dumps(data, indent=2)


@mcp.tool()
def list_categories(content_type: str = "LIVE") -> str:
    """List all categories. content_type: LIVE, MOVIE, or SERIES."""
    data = api_get(f"/categories?type={content_type}")
    cats = data.get("categories", [])
    lines = [f"{len(cats)} {content_type} categories:"]
    for cat in cats:
        lines.append(f"  [{cat['category_id']}] {cat['name']}")
    return "\n".join(lines)


@mcp.tool()
def list_favorites() -> str:
    """List all favorite channels."""
    data = api_get("/favorites")
    favs = data.get("favorites", [])
    if not favs:
        return "No favorites set"
    lines = [f"{len(favs)} favorites:"]
    for f in favs:
        lines.append(f"  [{f['channel_id']}] {f['channel_name']} ({f['category_name']})")
    return "\n".join(lines)


@mcp.tool()
def add_favorite(channel_id: int) -> str:
    """Add a channel to favorites by its database ID."""
    data = api_post("/favorites/add", {"channel_id": channel_id})
    if data.get("success"):
        return f"Added channel {channel_id} to favorites"
    return f"Failed: {data}"


@mcp.tool()
def remove_favorite(channel_id: int) -> str:
    """Remove a channel from favorites by its database ID."""
    data = api_post("/favorites/remove", {"channel_id": channel_id})
    if data.get("success"):
        return f"Removed channel {channel_id} from favorites"
    return f"Failed: {data}"


@mcp.tool()
def play_channel(channel_id: int) -> str:
    """Play/tune to a specific channel by its database ID. Launches the player."""
    data = api_post("/play", {"channel_id": channel_id})
    if data.get("success"):
        return f"Now playing: {data.get('playing')} (channel {channel_id})"
    return f"Failed: {data}"


@mcp.tool()
def search_epg(query: str) -> str:
    """Search for live/upcoming games and programs. Checks both EPG data AND channel names
    (since many IPTV channels encode game info in their name like 'NEXT | MIN - TIMBERWOLVES VS SAS - SPURS').
    Use this to find what's currently on or upcoming."""

    lines = []

    # Check EPG database first
    data = api_get(f"/epg?search={urllib.parse.quote(query)}")
    programs = data.get("programs", [])
    if programs:
        lines.append(f"EPG matches for '{query}':")
        for p in programs:
            import datetime
            start = datetime.datetime.fromtimestamp(p['start_time']).strftime('%H:%M')
            end = datetime.datetime.fromtimestamp(p['end_time']).strftime('%H:%M')
            ch_name = p.get('channel_name', '?')
            ch_id = p.get('channel_id', 0)
            lines.append(f"  {start}-{end} | [{ch_id}] {ch_name}: {p['title']}")

    # Also search channel names — PPV channels often have game info in the name
    words = query.split()
    for word in words:
        if len(word) < 3:
            continue
        ch_data = api_get(f"/channels?search={urllib.parse.quote(word)}&limit=30")
        for ch in ch_data.get("channels", []):
            name = ch.get("name", "")
            # Look for game-like channel names
            if any(kw in name.upper() for kw in ["NEXT |", " VS ", " @ ", "GAME "]):
                lines.append(f"  LIVE/NEXT | [{ch['id']}] {name}")

    if not lines:
        return f"No games or programs found for '{query}'. EPG may not be loaded — try searching channels directly with search_channels()."

    # Deduplicate
    seen = set()
    unique = []
    for line in lines:
        if line not in seen:
            seen.add(line)
            unique.append(line)

    return "\n".join(unique)


@mcp.tool()
def whats_on_now(limit: int = 30) -> str:
    """Show what's currently airing / upcoming games across all channels.
    Checks EPG + scans PPV channel names for active game matchups."""
    lines = []

    # EPG data
    data = api_get(f"/epg?limit={limit}")
    programs = data.get("programs", [])
    if programs:
        for p in programs:
            import datetime
            end = datetime.datetime.fromtimestamp(p['end_time']).strftime('%H:%M')
            ch_name = p.get('channel_name', '?')
            ch_id = p.get('channel_id', 0)
            lines.append(f"  until {end} | [{ch_id}] {ch_name}: {p['title']}")

    # Scan PPV categories for live/next games
    for cat_keyword in ["NBA PASS", "NBA PPV", "NFL PPV", "NHL PPV", "MLB PPV", "PPV EVENT"]:
        try:
            ch_data = api_get(f"/channels?search={urllib.parse.quote(cat_keyword)}&limit=10")
            for ch in ch_data.get("channels", []):
                name = ch.get("name", "")
                if "NEXT |" in name:
                    lines.append(f"  UPCOMING | [{ch['id']}] {name}")
        except:
            pass

    if not lines:
        return "No EPG data or live games found. EPG may not be synced yet."

    return f"Currently on / upcoming:\n" + "\n".join(lines[:40])


@mcp.tool()
def find_and_play(query: str, prefer_4k: bool = True) -> str:
    """Find a channel or program matching the query and play it. Prefers 4K/HD versions.
    Searches channel names for game matchups (e.g. 'Timberwolves vs Spurs'), team names, network names.
    Example: 'NBA playoff', 'CNN', 'Raptors game', 'Timberwolves Spurs'."""

    # Search channels — this finds PPV game-specific channels with matchup names
    ch_data = api_get(f"/channels?search={urllib.parse.quote(query)}&limit=50")
    channels = ch_data.get("channels", [])

    # Also search with individual words for team matchups
    words = query.split()
    if len(words) >= 2:
        for word in words:
            if len(word) >= 4:  # Skip short words
                extra = api_get(f"/channels?search={urllib.parse.quote(word)}&limit=30")
                seen_ids = {c["id"] for c in channels}
                for ch in extra.get("channels", []):
                    if ch["id"] not in seen_ids:
                        channels.append(ch)
                        seen_ids.add(ch["id"])

    if not channels:
        return f"Nothing found for '{query}'"

    # Score channels — prefer: NEXT/live games > HD > 4K label > regular
    def score(ch):
        name = ch.get("name", "")
        s = 0
        if "NEXT |" in name or "VS" in name.upper() or "vs" in name:
            s += 100  # Active/upcoming game
        if prefer_4k and ("4K" in name or "UHD" in name):
            s += 50
        if "HD" in name or "ᴴᴰ" in name:
            s += 30
        if "NO EVENT" in name or name.startswith("-") or name.startswith("#"):
            s -= 200
        if "SD" in name:
            s -= 20
        # Boost if multiple query words match
        for word in words:
            if word.upper() in name.upper():
                s += 10
        return s

    channels.sort(key=score, reverse=True)
    best = channels[0]

    result = api_post("/play", {"channel_id": best["id"]})
    if result.get("success"):
        return f"Playing: {best['name']} (channel {best['id']})"
    return f"Found but failed to play: {best['name']}"


@mcp.tool()
def quick_switch(query: str, prefer_4k: bool = True) -> str:
    """Instantly search and switch to a channel in one step. Fastest way to change channels. Example: 'ESPN', 'CNN', 'NBA', 'CP24'."""
    data = api_post("/quick_switch", {"query": query, "prefer_4k": prefer_4k})
    if data.get("success"):
        return f"Switched to: {data.get('playing')} (channel {data.get('channel_id')})"
    return f"Failed: {data.get('error', 'unknown')}"


@mcp.tool()
def enable_all_categories() -> str:
    """Enable all IPTV categories (Arabic, European, etc.) that were previously filtered out."""
    data = api_post("/categories/toggle_all", {})
    if data.get("success"):
        return f"Enabled {data.get('total_categories')} categories ({data.get('newly_added')} newly added). Navigate to Categories tab to browse."
    return f"Failed: {data.get('error', 'unknown')}"


@mcp.tool()
def show_qr_on_tv() -> str:
    """Display the QR code on the TV screen so the user can scan it with their phone."""
    data = api_post("/show_qr", {})
    if data.get("success"):
        return f"QR displayed on TV. Phone URL: {data.get('url')}"
    return f"Failed: {data}"


@mcp.tool()
def get_companion_url() -> str:
    """Get the URL for the phone companion web UI. Share this with the user to control the TV from their phone."""
    # Get the TV's IP from ADB
    result = subprocess.run(
        ["adb", "-s", ADB_DEVICE, "shell", "ip", "route", "get", "1"],
        capture_output=True, text=True, timeout=5
    )
    ip = "192.168.2.21"  # fallback
    for part in result.stdout.split():
        if part.startswith("192.168."):
            ip = part
            break
    url = f"http://{ip}:8585/"
    qr_url = f"http://{ip}:8585/qr"
    return f"Phone companion: {url}\nQR code page: {qr_url}\n\nOpen either URL on your phone (same WiFi network)."


@mcp.tool()
def launch_streamvault() -> str:
    """Launch StreamVault app on the TV."""
    result = adb_cmd("shell monkey -p com.streamvault.app -c android.intent.category.LAUNCHER 1")
    return "StreamVault launched" if "Events injected" in result else f"Launch result: {result}"


@mcp.tool()
def stop_streamvault() -> str:
    """Stop StreamVault app on the TV."""
    adb_cmd("shell am force-stop com.streamvault.app")
    return "StreamVault stopped"


@mcp.tool()
def tv_input(text: str) -> str:
    """Type text on the TV via ADB input."""
    adb_cmd(f"shell input text '{text}'")
    return f"Typed: {text}"


@mcp.tool()
def tv_keypress(key: str) -> str:
    """Press a key on the TV. Keys: enter(66), back(4), home(3), up(19), down(20), left(21), right(22), ok(23)."""
    key_map = {
        "enter": "66", "back": "4", "home": "3",
        "up": "19", "down": "20", "left": "21", "right": "22",
        "ok": "23", "play": "126", "pause": "127",
    }
    keycode = key_map.get(key.lower(), key)
    adb_cmd(f"shell input keyevent {keycode}")
    return f"Pressed: {key} (keycode {keycode})"


import urllib.parse

if __name__ == "__main__":
    mcp.run(transport="stdio")
