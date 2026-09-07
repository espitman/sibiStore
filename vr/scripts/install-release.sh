#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
SIBI_SERIAL="${1:?Pass an explicit Quest device serial}"
shift
SIBI_APK="$SCRIPT_DIR/../release/sibi-store-vr.apk"
SIBI_DEVICE="$SCRIPT_DIR/../../mobile/scripts/device.sh"

[[ -f "$SIBI_APK" ]] || { echo 'Run vr/scripts/release.sh first.'; exit 2; }
bash "$SCRIPT_DIR/verify-apk.sh" "$SIBI_APK"
# Preserve app data and reject signing mismatches instead of uninstalling the app.
bash "$SIBI_DEVICE" "$SIBI_SERIAL" install -r "$@" "$SIBI_APK"
bash "$SIBI_DEVICE" "$SIBI_SERIAL" shell am start -S -W -n \
    com.sibi.store.vr/com.unity3d.player.UnityPlayerActivity
