#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
export SIBI_MODULE=tv SIBI_PLATFORM=tv
bash "$SCRIPT_DIR/../../mobile/scripts/release.sh" "$@"
