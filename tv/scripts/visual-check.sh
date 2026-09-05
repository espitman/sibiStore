#!/usr/bin/env bash
# Requires a connected QA TV with a populated library; does not install any library app.
set -euo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
node "$SCRIPT_DIR/visual-check.cjs" "$@"
