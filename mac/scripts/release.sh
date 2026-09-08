#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR/.."
source "$SCRIPT_DIR/env.sh"
export CSC_IDENTITY_AUTO_DISCOVERY=false
bash "$SCRIPT_DIR/test.sh"
# Reuse the installed Electron runtime instead of downloading it again during packaging.
npm run package -- --config.electronDist="$PWD/node_modules/electron/dist" "$@"
bash "$SCRIPT_DIR/copy-desktop.sh"
echo "Mac application created under mac/release/ (local build; no GitHub Release published)."
