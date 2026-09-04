#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR/.."
source "$SCRIPT_DIR/env.sh"
if [[ "${1:-}" == '--ui' ]]; then
  shift
  bash "$SCRIPT_DIR/build.sh"
  npm run test:ui -- "$@"
else npm test -- "$@"; fi
