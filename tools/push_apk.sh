#!/bin/bash
# Build and push APK + DB to Hisense TV
DEVICE="192.168.2.21:5555"
PKG="com.streamvault.app"
APK="$(dirname "$0")/../app/build/outputs/apk/debug/app-debug.apk"
DB="$(dirname "$0")/output/streamvault_seed.db"

echo "=== Building APK ==="
cd "$(dirname "$0")/.." && ./gradlew assembleDebug || exit 1

echo ""
echo "=== Installing APK ==="
adb -s "$DEVICE" install -r "$APK" || exit 1

echo ""
echo "=== Launching to initialize ==="
adb -s "$DEVICE" shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 2>/dev/null
sleep 5
adb -s "$DEVICE" shell am force-stop "$PKG"
sleep 1

if [ -f "$DB" ]; then
    echo ""
    echo "=== Pushing pre-built DB ==="
    adb -s "$DEVICE" shell "run-as $PKG sh -c 'cat > databases/streamvault.db'" < "$DB"
    adb -s "$DEVICE" shell "run-as $PKG rm -f databases/streamvault.db-wal databases/streamvault.db-shm"
fi

echo ""
echo "=== Relaunching ==="
adb -s "$DEVICE" shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 2>/dev/null

echo "Done."
