#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
export SIBI_MODULE=tv SIBI_APP_ID=com.sibi.store.tv SIBI_AVD=Television_4K
bash "$SCRIPT_DIR/../../mobile/scripts/run.sh" "$@"
