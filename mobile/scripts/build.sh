#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/env.sh"
cd "$SIBI_ANDROID_ROOT"
SIBI_MODULE="${SIBI_MODULE:-app}"
./gradlew --no-daemon ":$SIBI_MODULE:assembleDebug" "$@"
