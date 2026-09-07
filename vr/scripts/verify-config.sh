#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/unity-env.sh"
mkdir -p "$SIBI_VR_ROOT/Logs"
"$SIBI_UNITY" -batchmode -nographics -quit -projectPath "$SIBI_VR_ROOT" -executeMethod SibiBuild.VerifyConfiguration -logFile "$SIBI_VR_ROOT/Logs/verify-config.log" "$@"
