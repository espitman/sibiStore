#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/env.sh"
SIBI_MODULE="${SIBI_MODULE:-app}"
SIBI_APP_ID="${SIBI_APP_ID:-com.sibi.store.mobile}"
SIBI_SERIAL="${1:-}"
if [[ -z "$SIBI_SERIAL" ]]; then
  echo 'Pass an explicit device serial. Start scripts/emulator.sh separately for an isolated QA device.'
  adb devices
  exit 2
fi
shift
bash "$SCRIPT_DIR/build.sh" "$@"
if [[ "$SIBI_MODULE" == tv ]]; then SIBI_APK="$SIBI_ANDROID_ROOT/../tv/app/build/outputs/apk/debug/tv-debug.apk";
else SIBI_APK="$SIBI_ANDROID_ROOT/app/build/outputs/apk/debug/app-debug.apk"; fi
adb -s "$SIBI_SERIAL" install -r -t "$SIBI_APK"
adb -s "$SIBI_SERIAL" shell am start -S -W -n "$SIBI_APP_ID/.MainActivity"
echo "Launched $SIBI_APP_ID on $SIBI_SERIAL"
