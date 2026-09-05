#!/usr/bin/env bash
# Full-frame rendering for the isolated SwiftShader QA TV, never physical devices.
set -euo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
SIBI_SERIAL="${1:?Pass a QA device serial}"
if [[ "$SIBI_SERIAL" != emulator-* ]]; then exit 0; fi
SIBI_AVD_NAME="$(bash "$SCRIPT_DIR/device.sh" "$SIBI_SERIAL" emu avd name | tr -d '\r' | head -n 1)"
if [[ "$SIBI_AVD_NAME" != SibiStore_TV ]]; then exit 0; fi
# Incremental damage sometimes omitted unchanged text in this emulator's screenshots.
# Cold-launch the client after setting these non-persistent, emulator-only properties.
bash "$SCRIPT_DIR/device.sh" "$SIBI_SERIAL" shell setprop debug.hwui.use_buffer_age false
bash "$SCRIPT_DIR/device.sh" "$SIBI_SERIAL" shell setprop debug.hwui.skip_empty_damage false
