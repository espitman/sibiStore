#!/usr/bin/env bash
# Emulator/device QA helper. All device operations use an explicit serial.
set -euo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/env.sh"
SIBI_SERIAL="${1:?Pass an adb device serial}"; shift
case "${1:-}" in
  screenshot) shift; adb -s "$SIBI_SERIAL" exec-out screencap -p > "${1:?Pass output PNG path}" ;;
  ui) adb -s "$SIBI_SERIAL" shell uiautomator dump /sdcard/sibi-ui.xml >/dev/null; adb -s "$SIBI_SERIAL" shell cat /sdcard/sibi-ui.xml ;;
  *) adb -s "$SIBI_SERIAL" "$@" ;;
esac
