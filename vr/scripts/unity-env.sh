#!/usr/bin/env bash
set -euo pipefail
SIBI_VR_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
SIBI_UNITY_VERSION=6000.0.83f1
SIBI_UNITY="${SIBI_UNITY:-/Applications/Unity/Hub/Editor/$SIBI_UNITY_VERSION/Unity.app/Contents/MacOS/Unity}"
if [[ ! -x "$SIBI_UNITY" ]]; then
  SIBI_UNITY="$HOME/Unity/Hub/Editor/$SIBI_UNITY_VERSION/Unity.app/Contents/MacOS/Unity"
fi
[[ -x "$SIBI_UNITY" ]] || { echo "Unity $SIBI_UNITY_VERSION is not installed yet. Run vr/scripts/setup-unity.sh."; exit 1; }
