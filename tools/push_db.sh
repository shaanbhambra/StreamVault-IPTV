#!/bin/bash
# Push pre-built DB to the Hisense TV, clear prefs, and relaunch StreamVault
DEVICE="192.168.2.21:5555"
PKG="com.streamvault.app"
DB="$(dirname "$0")/output/streamvault_seed.db"

if [ ! -f "$DB" ]; then
    echo "ERROR: $DB not found. Run build_db.py first."
    exit 1
fi

# Update last_synced_at to NOW so stale check doesn't re-trigger sync
NOW_MS=$(python3 -c "import time; print(int(time.time()*1000))")
sqlite3 "$DB" "
UPDATE providers SET last_synced_at = $NOW_MS;
UPDATE sync_metadata SET
  last_live_sync = $NOW_MS, last_live_success = $NOW_MS,
  last_movie_sync = $NOW_MS, last_series_sync = $NOW_MS, last_series_success = $NOW_MS,
  last_epg_sync = $NOW_MS, last_epg_success = $NOW_MS,
  last_movie_attempt = $NOW_MS, last_movie_success = $NOW_MS;
UPDATE xtream_index_jobs SET last_success_at = $NOW_MS, updated_at = $NOW_MS;
UPDATE xtream_live_onboarding_state SET updated_at = $NOW_MS, completed_at = $NOW_MS;
"

echo "Stopping $PKG..."
adb -s "$DEVICE" shell am force-stop "$PKG"
sleep 1

echo "Pushing DB ($(du -h "$DB" | cut -f1))..."
adb -s "$DEVICE" shell "run-as $PKG sh -c 'cat > databases/streamvault.db'" < "$DB"
adb -s "$DEVICE" shell "run-as $PKG rm -f databases/streamvault.db-wal databases/streamvault.db-shm"

echo "Clearing DataStore prefs (hidden categories, etc.)..."
adb -s "$DEVICE" shell "run-as $PKG sh -c 'rm -rf files/datastore/*'"

echo "Launching..."
adb -s "$DEVICE" shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 2>/dev/null

echo "Setting up port forwarding..."
sleep 3
adb -s "$DEVICE" forward tcp:8585 tcp:8585

echo "Done. API: http://localhost:8585/status"
