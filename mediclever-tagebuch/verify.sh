#!/usr/bin/env bash
# Ein Befehl fuer alles: Kotlin-Tests (Logik, App-Durchlaeufe, Screenshots), Debug-APK,
# Release-Build mit Code-Verkleinerung und die Firestore-Regeltests gegen den Emulator.
set -euo pipefail
cd "$(dirname "$0")"
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:lintDebug --console=plain
if [ "${SKIP_FIREBASE:-}" != "1" ]; then
  (cd firebase && { [ -d node_modules ] || npm ci; } && npm test)
fi
echo "Alles gruen. Screenshots: app/build/shots/  APK: app/build/outputs/apk/debug/app-debug.apk"
