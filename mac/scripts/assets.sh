#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
SIBI_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
swift "$SCRIPT_DIR/render-assets.swift" "$SIBI_ROOT"
iconutil -c icns "$SIBI_ROOT/mac/assets/sibi.iconset" -o "$SIBI_ROOT/mac/assets/sibi.icns"
