#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/unity-env.sh"
mkdir -p "$SIBI_VR_ROOT/Logs" "$SIBI_VR_ROOT/test-results"
"$SIBI_UNITY" -batchmode -projectPath "$SIBI_VR_ROOT" -runTests -testPlatform EditMode -testResults "$SIBI_VR_ROOT/test-results/editmode.xml" -logFile "$SIBI_VR_ROOT/Logs/test.log" "$@"
