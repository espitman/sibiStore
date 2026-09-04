#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR/.."
source "$SCRIPT_DIR/env.sh"
if [[ ! -d node_modules ]]; then bash "$SCRIPT_DIR/setup.sh"; fi
npm run build -- "$@"
