#!/usr/bin/env python3
"""
Step 2: Build a Room-compatible SQLite DB from local catalog JSON.
Schema is extracted directly from Room's exported 52.json — guaranteed match.

Usage:
    python3 tools/build_db.py            # Filtered (US/CA/India/4K/Sports)
    python3 tools/build_db.py --all      # All 882 categories
    # Then push:
    adb -s 192.168.2.21:5555 shell am force-stop com.streamvault.app
    adb -s 192.168.2.21:5555 shell "run-as com.streamvault.app sh -c 'cat > databases/streamvault.db'" < tools/output/streamvault_seed.db
    adb -s 192.168.2.21:5555 shell "run-as com.streamvault.app rm -f databases/streamvault.db-wal databases/streamvault.db-shm"
"""

import json
import hashlib
import sqlite3
import time
import sys
import os

SERVER = "http://candycloudstrong8k.xyz"
USERNAME = "b5885330ec"
PASSWORD = "5e46b997af"
PROVIDER_NAME = "G2G Family"

TOOLS_DIR = os.path.dirname(os.path.abspath(__file__))
CATALOG_DIR = os.path.join(TOOLS_DIR, "catalog")
OUTPUT_DIR = os.path.join(TOOLS_DIR, "output")
OUTPUT_DB = os.path.join(OUTPUT_DIR, "streamvault_seed.db")
SCHEMA_JSON = os.path.join(TOOLS_DIR, "..", "data", "schemas",
    "com.streamvault.data.local.StreamVaultDatabase", "52.json")


def fingerprint(data_str):
    return hashlib.sha256(data_str.encode()).hexdigest()[:16]


def load_catalog(filename):
    with open(os.path.join(CATALOG_DIR, filename)) as f:
        return json.load(f)


def create_schema(conn, schema_path):
    """Create all tables/indices from Room's exported schema JSON."""
    with open(schema_path) as f:
        schema = json.load(f)

    db_info = schema["database"]
    identity_hash = db_info["identityHash"]
    version = db_info["version"]

    cur = conn.cursor()

    # Android metadata
    cur.execute("CREATE TABLE IF NOT EXISTS android_metadata (locale TEXT)")
    cur.execute("INSERT INTO android_metadata VALUES('en_US')")

    # Room master table with identity hash
    cur.execute("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
    cur.execute("INSERT INTO room_master_table VALUES(42, ?)", (identity_hash,))

    # Create all entities
    for entity in db_info["entities"]:
        table_name = entity["tableName"]
        create_sql = entity["createSql"].replace("${TABLE_NAME}", table_name)
        cur.execute(create_sql)

        for idx in entity.get("indices", []):
            idx_sql = idx["createSql"].replace("${TABLE_NAME}", table_name)
            cur.execute(idx_sql)

    # FTS tables
    for fts in db_info.get("ftsEntities", []) if "ftsEntities" in db_info else []:
        table_name = fts["tableName"]
        create_sql = fts["createSql"].replace("${TABLE_NAME}", table_name)
        cur.execute(create_sql)

    # FTS tables might be in views or separate — handle manually if needed
    # Room's FTS4 virtual tables with content sync triggers
    fts_tables = {
        "channels_fts": "channels",
        "movies_fts": "movies",
        "series_fts": "series",
    }
    for fts_name, content_table in fts_tables.items():
        try:
            cur.execute(f"CREATE VIRTUAL TABLE IF NOT EXISTS `{fts_name}` USING FTS4(`name` TEXT NOT NULL, content=`{content_table}`)")
        except:
            pass  # Already created

        # FTS sync triggers
        for when, action in [
            ("BEFORE UPDATE", f"DELETE FROM `{fts_name}` WHERE `docid`=OLD.`rowid`"),
            ("BEFORE DELETE", f"DELETE FROM `{fts_name}` WHERE `docid`=OLD.`rowid`"),
            ("AFTER UPDATE", f"INSERT INTO `{fts_name}`(`docid`, `name`) VALUES (NEW.`rowid`, NEW.`name`)"),
            ("AFTER INSERT", f"INSERT INTO `{fts_name}`(`docid`, `name`) VALUES (NEW.`rowid`, NEW.`name`)"),
        ]:
            trigger_name = f"room_fts_content_sync_{fts_name}_{when.replace(' ', '_')}"
            try:
                cur.execute(f"CREATE TRIGGER IF NOT EXISTS {trigger_name} {when} ON `{content_table}` BEGIN {action}; END")
            except:
                pass

    # Set user_version for Room
    cur.execute(f"PRAGMA user_version = {version}")

    conn.commit()
    return identity_hash, version


def main():
    print("=" * 60)
    print("StreamVault DB Builder (from local catalog)")
    print("=" * 60)

    if not os.path.exists(os.path.join(CATALOG_DIR, "live_channels.json")):
        print("ERROR: No catalog found. Run fetch_catalog.py first.")
        sys.exit(1)

    if not os.path.exists(SCHEMA_JSON):
        print(f"ERROR: Room schema not found at {SCHEMA_JSON}")
        sys.exit(1)

    os.makedirs(OUTPUT_DIR, exist_ok=True)
    if os.path.exists(OUTPUT_DB):
        os.remove(OUTPUT_DB)

    conn = sqlite3.connect(OUTPUT_DB)
    conn.execute("PRAGMA journal_mode=DELETE")
    conn.execute("PRAGMA foreign_keys=ON")

    # Create schema from Room's JSON
    print("Creating schema from Room v52 JSON...")
    identity_hash, version = create_schema(conn, SCHEMA_JSON)
    print(f"  Version: {version}, Hash: {identity_hash}")

    cur = conn.cursor()
    now_ms = int(time.time() * 1000)

    # Provider (plaintext password — app will encrypt on first read)
    print("\nCreating provider...")
    cur.execute("""
        INSERT INTO providers (name, type, server_url, username, password,
            m3u_url, epg_url, http_user_agent, http_headers,
            stalker_mac_address, stalker_device_profile, stalker_device_timezone,
            stalker_device_locale, is_active, max_connections, expiration_date,
            allowed_output_formats_json, epg_sync_mode, xtream_fast_sync_enabled,
            xtream_live_sync_mode, m3u_vod_classification_enabled,
            status, last_synced_at, created_at)
        VALUES (?, 'XTREAM_CODES', ?, ?, ?,
            '', '', '', '',
            '', '', '', '',
            1, 1, 1781388000000,
            '["m3u8","ts"]', 'BACKGROUND', 0,
            'AUTO', 0,
            'ACTIVE', ?, ?)
    """, (PROVIDER_NAME, SERVER, USERNAME, PASSWORD, now_ms, now_ms))
    provider_id = cur.lastrowid
    print(f"  Provider ID: {provider_id}")

    # Categories — use --all flag to include all 882 live categories
    # Channels are only pre-loaded for filtered categories; app lazy-loads the rest
    use_all = "--all" in sys.argv
    live_cat_file = "live_categories_all.json" if use_all else "live_categories_filtered.json"
    mode_label = f"ALL ({live_cat_file})" if use_all else f"FILTERED ({live_cat_file})"
    print(f"\nInserting categories ({mode_label})...")
    for cat_file, cat_type in [
        (live_cat_file, "LIVE"),
        ("vod_categories.json", "MOVIE"),
        ("series_categories.json", "SERIES"),
    ]:
        cats = load_catalog(cat_file)
        for cat in cats:
            cat_id = int(cat["category_id"])
            name = cat["category_name"]
            fp = fingerprint(f"{cat_type}:{cat_id}:{name}")
            cur.execute("""
                INSERT OR IGNORE INTO categories
                (category_id, name, parent_id, type, provider_id, is_adult, is_user_protected, sync_fingerprint)
                VALUES (?, ?, NULL, ?, ?, 0, 0, ?)
            """, (cat_id, name, cat_type, provider_id, fp))
        print(f"  {len(cats)} {cat_type} categories")

    # Channels
    print("\nInserting channels...")
    channels = load_catalog("live_channels.json")
    inserted = 0
    for i, ch in enumerate(channels):
        stream_id = int(ch.get("stream_id", 0))
        if stream_id == 0:
            continue
        name = (ch.get("name") or "").strip()
        if not name or name.startswith("#"):
            continue

        cat_id = ch.get("_category_id") or ch.get("category_id")
        cat_name = ch.get("_category_name") or ch.get("category_name", "")
        logo = ch.get("stream_icon", "") or ""
        epg_id = ch.get("epg_channel_id", "") or ""
        stream_url = f"xtream://{provider_id}/live/{stream_id}?ext=&src="
        is_adult = 1 if str(ch.get("is_adult", "0")) == "1" else 0
        fp = fingerprint(f"{stream_id}:{name}:{cat_id}")
        logical_group = f"live_{cat_id}" if cat_id else "live_0"

        cur.execute("""
            INSERT OR IGNORE INTO channels
            (stream_id, name, logo_url, group_title, category_id, category_name,
             stream_url, epg_channel_id, number, catch_up_supported, catch_up_days,
             catchUpSource, provider_id, is_adult, is_user_protected,
             logical_group_id, error_count, quality_options_json, sync_fingerprint)
            VALUES (?, ?, ?, ?, ?, ?,
                    ?, ?, ?, 0, 0,
                    NULL, ?, ?, 0,
                    ?, 0, NULL, ?)
        """, (stream_id, name, logo, cat_name, cat_id, cat_name,
              stream_url, epg_id, i + 1,
              provider_id, is_adult, logical_group, fp))
        inserted += 1

    print(f"  {inserted} channels")

    # FTS rebuild
    print("\nRebuilding FTS...")
    cur.execute("INSERT INTO channels_fts(channels_fts) VALUES('rebuild')")

    # Mark sync complete (schema has per-section timestamps, not section column)
    cur.execute("""
        INSERT OR REPLACE INTO sync_metadata
        (provider_id, last_live_sync, last_live_success,
         last_movie_sync, last_series_sync, last_series_success,
         last_epg_sync, last_epg_success,
         last_movie_attempt, last_movie_success, last_movie_partial,
         live_count, movie_count, series_count, epg_count,
         last_sync_status, movie_sync_mode,
         movie_warnings_count, movie_catalog_stale,
         live_avoid_full_until, movie_avoid_full_until, series_avoid_full_until,
         live_sequential_failures_remembered, live_healthy_sync_streak,
         movie_parallel_failures_remembered, movie_healthy_sync_streak,
         series_sequential_failures_remembered, series_healthy_sync_streak)
        VALUES (?, ?, ?,
                ?, ?, ?,
                ?, ?,
                ?, ?, 0,
                ?, 0, 0, 0,
                'SUCCESS', 'FULL',
                0, 0,
                0, 0, 0,
                0, 1,
                0, 0,
                0, 0)
    """, (provider_id, now_ms, now_ms,
          now_ms, now_ms, now_ms,  # movie/series sync times
          now_ms, now_ms,          # epg sync times
          now_ms, now_ms,          # movie attempt/success
          inserted))

    cur.execute("""
        INSERT OR REPLACE INTO xtream_live_onboarding_state
        (provider_id, provider_type, content_type, phase, staged_session_id,
         import_strategy, next_category_index,
         accepted_row_count, staged_flush_count,
         sync_profile_batch_size, sync_profile_low_memory,
         sync_profile_memory_class_mb, sync_profile_available_mem_mb,
         created_at, updated_at, completed_at)
        VALUES (?, 'XTREAM_CODES', 'LIVE', 'COMPLETED', NULL,
                'full', 0,
                ?, 0,
                500, 0,
                256, 256,
                ?, ?, ?)
    """, (provider_id, inserted, now_ms, now_ms, now_ms))

    # Mark VOD and series index jobs as completed so app doesn't re-trigger
    vod_cat_count = len(load_catalog("vod_categories.json"))
    series_cat_count = len(load_catalog("series_categories.json"))
    for section, cat_count in [("MOVIE", vod_cat_count), ("SERIES", series_cat_count)]:
        cur.execute("""
            INSERT OR REPLACE INTO xtream_index_jobs
            (provider_id, section, state, total_categories, completed_categories,
             next_category_index, failed_categories, indexed_rows,
             skipped_malformed_rows, deleted_pruned_rows,
             priority_category_id, priority_requested_at,
             last_error, last_attempt_at, last_success_at, updated_at)
            VALUES (?, ?, 'COMPLETED', ?, ?, 0, 0, 0, 0, 0,
                    0, 0,
                    NULL, ?, ?, ?)
        """, (provider_id, section, cat_count, cat_count, now_ms, now_ms, now_ms))

    # Update provider last_synced_at to now
    cur.execute("UPDATE providers SET last_synced_at = ? WHERE id = ?", (now_ms, provider_id))

    # Load favorites from favorites.json
    fav_path = os.path.join(TOOLS_DIR, "favorites.json")
    if os.path.exists(fav_path):
        print("\nInserting favorites...")
        with open(fav_path) as f:
            fav_data = json.load(f)
        position = 0
        added = 0
        for fav in fav_data.get("favorites", []):
            sid = fav["stream_id"]
            # Find channel DB id by stream_id
            cur.execute("SELECT id FROM channels WHERE stream_id = ? AND provider_id = ?",
                        (sid, provider_id))
            row = cur.fetchone()
            if row:
                position += 1024
                cur.execute("""
                    INSERT OR IGNORE INTO favorites
                    (provider_id, content_id, content_type, position, group_id, group_key, added_at)
                    VALUES (?, ?, 'LIVE', ?, NULL, 0, ?)
                """, (provider_id, row[0], position, now_ms))
                added += 1
            else:
                print(f"  WARN: stream_id {sid} not found — {fav.get('note', '')}")
        print(f"  {added} favorites added")
    else:
        print("\nNo favorites.json found, skipping")

    conn.commit()

    # Verify
    cur.execute("SELECT COUNT(*) FROM channels")
    ch_count = cur.fetchone()[0]
    cur.execute("SELECT COUNT(*) FROM categories")
    cat_count = cur.fetchone()[0]
    cur.execute("SELECT identity_hash FROM room_master_table")
    hash_check = cur.fetchone()[0]
    cur.execute("PRAGMA user_version")
    ver_check = cur.fetchone()[0]
    cur.execute("PRAGMA integrity_check")
    integrity = cur.fetchone()[0]

    conn.close()
    db_size = os.path.getsize(OUTPUT_DB) / (1024 * 1024)

    print(f"\n{'=' * 60}")
    print(f"Database: {OUTPUT_DB}")
    print(f"Size:       {db_size:.1f} MB")
    print(f"Channels:   {ch_count}")
    print(f"Categories: {cat_count}")
    print(f"Room hash:  {hash_check}")
    print(f"DB version: {ver_check}")
    print(f"Integrity:  {integrity}")
    print(f"{'=' * 60}")


if __name__ == "__main__":
    main()
