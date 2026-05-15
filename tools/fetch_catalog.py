#!/usr/bin/env python3
"""
Step 1: Fetch full Xtream Codes catalog from API and save as local JSON.
Run with VPN connected: mullvad connect && python3 tools/fetch_catalog.py

Output: tools/catalog/ directory with JSON files
"""

import json
import os
import time
import urllib.request

SERVER = "http://cf.candycloud-8k.men"
USERNAME = "b5885330ec"
PASSWORD = "5e46b997af"

OUT_DIR = os.path.join(os.path.dirname(__file__), "catalog")

# Category prefixes to include for LIVE (keep catalog manageable)
LIVE_CATEGORY_PREFIXES = [
    "4K|", "8K|",
    "US|", "CA|",
    "ASIA|",
    "RX|",
]
LIVE_CATEGORY_KEYWORDS = ["cricket", "india", "hindi", "punjabi"]


def api_url(action, category_id=None):
    url = f"{SERVER}/player_api.php?username={USERNAME}&password={PASSWORD}&action={action}"
    if category_id is not None:
        url += f"&category_id={category_id}"
    return url


def fetch(url, retries=3):
    for attempt in range(retries):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
            with urllib.request.urlopen(req, timeout=60) as resp:
                return json.loads(resp.read().decode())
        except Exception as e:
            if attempt < retries - 1:
                print(f"  retry {attempt+1}: {e}")
                time.sleep(2)
            else:
                raise


def should_include(name):
    upper = (name or "").upper()
    for p in LIVE_CATEGORY_PREFIXES:
        if upper.startswith(p.upper()):
            return True
    lower = (name or "").lower()
    return any(kw in lower for kw in LIVE_CATEGORY_KEYWORDS)


def main():
    os.makedirs(OUT_DIR, exist_ok=True)

    # 1. Auth check
    print("Authenticating...")
    info = fetch(api_url(""))
    exp = info.get("user_info", {}).get("exp_date", "?")
    status = info.get("user_info", {}).get("status", "?")
    max_conn = info.get("user_info", {}).get("max_connections", "?")
    print(f"  Status: {status}, Expires: {exp}, Max connections: {max_conn}")
    with open(os.path.join(OUT_DIR, "server_info.json"), "w") as f:
        json.dump(info, f, indent=2)

    # 2. Live categories
    print("\nFetching live categories...")
    all_live_cats = fetch(api_url("get_live_categories"))
    print(f"  Total: {len(all_live_cats)}")

    included = [c for c in all_live_cats if should_include(c.get("category_name", ""))]
    excluded = [c for c in all_live_cats if not should_include(c.get("category_name", ""))]
    print(f"  Included: {len(included)}")
    print(f"  Excluded: {len(excluded)}")

    with open(os.path.join(OUT_DIR, "live_categories_all.json"), "w") as f:
        json.dump(all_live_cats, f, indent=2)
    with open(os.path.join(OUT_DIR, "live_categories_filtered.json"), "w") as f:
        json.dump(included, f, indent=2)

    # 3. Live channels per included category
    print("\nFetching live channels...")
    all_channels = []
    seen = set()
    for i, cat in enumerate(included):
        cat_id = cat["category_id"]
        cat_name = cat["category_name"]
        try:
            streams = fetch(api_url("get_live_streams", cat_id))
        except Exception as e:
            print(f"  SKIP {cat_name}: {e}")
            continue

        new = 0
        for s in streams:
            sid = s.get("stream_id")
            if sid and sid not in seen:
                seen.add(sid)
                s["_category_name"] = cat_name
                s["_category_id"] = cat_id
                all_channels.append(s)
                new += 1
        print(f"  [{i+1}/{len(included)}] {cat_name}: {new} new ({len(streams)} total)")

    with open(os.path.join(OUT_DIR, "live_channels.json"), "w") as f:
        json.dump(all_channels, f, indent=2)
    print(f"  Total unique channels: {len(all_channels)}")

    # 4. VOD categories
    print("\nFetching VOD categories...")
    vod_cats = fetch(api_url("get_vod_categories"))
    print(f"  Total: {len(vod_cats)}")
    with open(os.path.join(OUT_DIR, "vod_categories.json"), "w") as f:
        json.dump(vod_cats, f, indent=2)

    # 5. Series categories
    print("\nFetching series categories...")
    series_cats = fetch(api_url("get_series_categories"))
    print(f"  Total: {len(series_cats)}")
    with open(os.path.join(OUT_DIR, "series_categories.json"), "w") as f:
        json.dump(series_cats, f, indent=2)

    # Summary
    print(f"\n{'='*60}")
    print(f"Catalog saved to: {OUT_DIR}/")
    print(f"  server_info.json         — auth & server details")
    print(f"  live_categories_all.json — all {len(all_live_cats)} live categories")
    print(f"  live_categories_filtered.json — {len(included)} filtered categories")
    print(f"  live_channels.json       — {len(all_channels)} unique channels")
    print(f"  vod_categories.json      — {len(vod_cats)} VOD categories")
    print(f"  series_categories.json   — {len(series_cats)} series categories")
    print(f"{'='*60}")
    print(f"\nNext: python3 tools/build_db.py")


if __name__ == "__main__":
    main()
