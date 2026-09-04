#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR/.."
source "$SCRIPT_DIR/env.sh"
export CSC_IDENTITY_AUTO_DISCOVERY=false
bash "$SCRIPT_DIR/test.sh"
npm run package -- "$@"
echo "Mac application created under mac/release/ (local build; no GitHub Release published)."
