#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
SIBI_SOURCE="$SCRIPT_DIR/../release/mac-$(uname -m)/Sibi Store.app"
SIBI_DESTINATION="${1:-/Applications/Sibi Store.app}"
if [[ $# -gt 1 ]]; then echo 'Usage: install-release.sh [destination.app]'; exit 2; fi
[[ -d "$SIBI_SOURCE" && ! -L "$SIBI_DESTINATION" ]] || { echo 'Release or destination is unavailable or unsafe'; exit 1; }
codesign --verify --deep --strict "$SIBI_SOURCE"
if [[ -e "$SIBI_DESTINATION" ]]; then
  [[ "$(/usr/libexec/PlistBuddy -c 'Print :CFBundleIdentifier' "$SIBI_DESTINATION/Contents/Info.plist")" == com.sibi.store.server ]] || { echo 'Refusing to replace another application'; exit 1; }
fi
SIBI_STAGE="$(mktemp -d "$SCRIPT_DIR/../release/install-copy.XXXXXX")"
ditto "$SIBI_SOURCE" "$SIBI_STAGE/Sibi Store.app"
codesign --verify --deep --strict "$SIBI_STAGE/Sibi Store.app"
if [[ -e "$SIBI_DESTINATION" ]]; then mv "$SIBI_DESTINATION" "$SIBI_STAGE/previous.app"; fi
if ! mv "$SIBI_STAGE/Sibi Store.app" "$SIBI_DESTINATION"; then
  if [[ -d "$SIBI_STAGE/previous.app" ]]; then mv "$SIBI_STAGE/previous.app" "$SIBI_DESTINATION"; fi
  exit 1
fi
echo "Installed: $SIBI_DESTINATION"
