#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/unity-env.sh"
mkdir -p "$SIBI_VR_ROOT/Logs"
"$SIBI_UNITY" -batchmode -projectPath "$SIBI_VR_ROOT" -executeMethod StorePreview.Capture -logFile "$SIBI_VR_ROOT/Logs/preview.log" "$@"
