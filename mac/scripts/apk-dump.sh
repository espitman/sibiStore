#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../../mobile/scripts/env.sh"
SIBI_TOOLS="$(ls -d "$ANDROID_HOME"/build-tools/* | sort | tail -1)"
"$SIBI_TOOLS/aapt2" dump "$@"
