#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
export SIBI_PLATFORM=tv SIBI_APP_ID=com.sibi.store.tv
bash "$SCRIPT_DIR/../../mobile/scripts/install-release.sh" "$@"
