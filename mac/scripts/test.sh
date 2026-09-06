#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR/.."
source "$SCRIPT_DIR/env.sh"
if [[ "${1:-}" == '--icons' ]]; then
  shift; node_modules/.bin/electron test/icons-qa.cjs "$@"
elif [[ "${1:-}" == '--discovery' ]]; then
  shift; node test/discovery.cjs "$@"
elif [[ "${1:-}" == '--packaged' ]]; then
  shift; node test/packaged.cjs "$@"
elif [[ "${1:-}" == '--ui' ]]; then
  shift
  bash "$SCRIPT_DIR/build.sh"
  npm run test:ui -- "$@"
else npm test -- "$@"; fi
