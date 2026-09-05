#!/usr/bin/env bash
# Start an isolated QA emulator. Never take over an emulator used by another project.
set -euo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/env.sh"
SIBI_AVD="${SIBI_AVD:-SibiStore_Phone}"
SIBI_EMULATOR_PORT="${SIBI_EMULATOR_PORT:-5580}"
mkdir -p "$SIBI_ANDROID_ROOT/test-results"
export ANDROID_AVD_HOME="$SIBI_ANDROID_ROOT/test-results/avds"
node "$SCRIPT_DIR/prepare-emulator.cjs" "${SIBI_MODULE:-app}"
if adb -s "emulator-$SIBI_EMULATOR_PORT" get-state >/dev/null 2>&1; then
  echo "QA emulator already running: emulator-$SIBI_EMULATOR_PORT"; exit 0
fi
echo "Starting isolated QA emulator: emulator-$SIBI_EMULATOR_PORT. Keep this process running during QA."
if [[ "${SIBI_EMULATOR_WINDOW:-0}" != "1" ]]; then set -- -no-window "$@"; fi
exec "$ANDROID_HOME/emulator/emulator" -avd "$SIBI_AVD" -port "$SIBI_EMULATOR_PORT" -no-snapshot -no-audio -gpu swiftshader_indirect -memory 2048 "$@"
