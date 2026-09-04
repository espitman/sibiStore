#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/env.sh"
cd "$SIBI_ANDROID_ROOT"
./gradlew --no-daemon :core:testDebugUnitTest ":${SIBI_MODULE:-app}:lintDebug" "$@"
