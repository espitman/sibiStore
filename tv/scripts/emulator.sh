#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
export SIBI_AVD=SibiStore_TV SIBI_EMULATOR_PORT=5582 SIBI_MODULE=tv
bash "$SCRIPT_DIR/../../mobile/scripts/emulator.sh" "$@"
