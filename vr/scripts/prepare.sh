#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/unity-env.sh"
bash "$SCRIPT_DIR/setup-meta.sh"
mkdir -p "$SIBI_VR_ROOT/Logs"
"$SIBI_UNITY" -batchmode -nographics -quit -projectPath "$SIBI_VR_ROOT" -executeMethod SibiBuild.Prepare -logFile "$SIBI_VR_ROOT/Logs/prepare.log" "$@"
