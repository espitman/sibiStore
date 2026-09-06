#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
SIBI_SOURCE="$SCRIPT_DIR/../release/mac-arm64/Sibi Store.app"
SIBI_DESTINATION="$HOME/Desktop/Sibi Store.app"
[[ -d "$SIBI_SOURCE" && -d "$HOME/Desktop" && ! -L "$SIBI_DESTINATION" ]] || { echo 'Desktop destination or release is unavailable or unsafe'; exit 1; }
if [[ -e "$SIBI_DESTINATION" ]]; then
  [[ -d "$SIBI_DESTINATION" && -f "$SIBI_DESTINATION/Contents/Info.plist" ]] || { echo 'Unexpected Desktop item; refusing to replace it'; exit 1; }
  [[ "$(/usr/libexec/PlistBuddy -c 'Print :CFBundleIdentifier' "$SIBI_DESTINATION/Contents/Info.plist")" == com.sibi.store.server ]] || { echo 'Desktop app has a different bundle ID'; exit 1; }
fi
# Stage the complete bundle, preserving symlinks and signatures; do not merge stale files.
SIBI_STAGE="$(mktemp -d "$SCRIPT_DIR/../release/desktop-copy.XXXXXX")"
ditto "$SIBI_SOURCE" "$SIBI_STAGE/Sibi Store.app"
codesign --verify --deep --strict "$SIBI_STAGE/Sibi Store.app"
if [[ -e "$SIBI_DESTINATION" ]]; then mv "$SIBI_DESTINATION" "$SIBI_STAGE/previous.app"; fi
if ! mv "$SIBI_STAGE/Sibi Store.app" "$SIBI_DESTINATION"; then
  if [[ -d "$SIBI_STAGE/previous.app" ]]; then mv "$SIBI_STAGE/previous.app" "$SIBI_DESTINATION"; fi
  exit 1
fi
echo "Desktop application: $SIBI_DESTINATION"
if [[ -d "$SIBI_STAGE/previous.app" ]]; then echo "Previous Desktop app retained at: $SIBI_STAGE/previous.app"; else rmdir "$SIBI_STAGE"; fi
