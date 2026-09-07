#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR/../.."
SIBI_AGY="${SIBI_AGY:-$HOME/.local/bin/agy}"
SIBI_REVIEW_TEXT="$(cat vr/Assets/Scripts/SpatialPointers.cs)"
exec "$SIBI_AGY" --mode plan --sandbox --print-timeout 5m --print "Review only the following C# code supplied inline. DO NOT use any tools, read files, or run commands. Return at most five concrete actionable bugs about Unity UI press/drag/cancel and concurrent input. Code follows:
$SIBI_REVIEW_TEXT" "$@"
