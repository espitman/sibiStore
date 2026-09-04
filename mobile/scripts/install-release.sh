#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/env.sh"
SIBI_SERIAL="${1:?Pass an explicit device serial}"
SIBI_PLATFORM="${SIBI_PLATFORM:-mobile}"
SIBI_APP_ID="${SIBI_APP_ID:-com.sibi.store.mobile}"
SIBI_APK="$SIBI_ANDROID_ROOT/../$SIBI_PLATFORM/release/sibi-store-$SIBI_PLATFORM.apk"
if [[ ! -f "$SIBI_APK" ]]; then echo 'Run scripts/release.sh first.'; exit 2; fi
# Never automatically uninstall a differently signed build or erase device data.
adb -s "$SIBI_SERIAL" install -r "$SIBI_APK"
adb -s "$SIBI_SERIAL" shell am start -S -W -n "$SIBI_APP_ID/.MainActivity"
