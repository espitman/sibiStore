#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR/.."
source "$SCRIPT_DIR/env.sh"
if [[ ! -d node_modules ]]; then bash "$SCRIPT_DIR/setup.sh"; fi
case "${1:-dev}" in
  installed) shift; open '/Applications/Sibi Store.app' --args "$@" ;;
  dev) shift || true; npm run dev -- "$@" ;;
  app) shift; bash "$SCRIPT_DIR/build.sh"; npm start -- "$@" ;;
  preview) shift; bash "$SCRIPT_DIR/build.sh"; npm start -- --design-preview "$@" ;;
  *) echo 'Usage: run.sh [dev|app|preview|installed]'; exit 2 ;;
esac
