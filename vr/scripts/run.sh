#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/unity-env.sh"
"$SIBI_UNITY" -projectPath "$SIBI_VR_ROOT" "$@"
