#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../../mobile/scripts/env.sh"
cd "$SIBI_ANDROID_ROOT"
./gradlew --no-daemon :vrbridge:exportUnityLibraries "$@"
