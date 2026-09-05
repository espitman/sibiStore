#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
bash "$SCRIPT_DIR/rendering-qa.sh" "${1:?Pass a QA device serial}"
node "$SCRIPT_DIR/inspector-check.cjs" "$@"
