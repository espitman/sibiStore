#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
node --test "$SCRIPT_DIR/png-audit.test.cjs"
export SIBI_MODULE=tv
bash "$SCRIPT_DIR/../../mobile/scripts/test.sh" "$@"
