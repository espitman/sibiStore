#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../../mobile/scripts/env.sh"
bash "$SCRIPT_DIR/build.sh" "$@"
SIBI_KEYS="${SIBI_SIGNING_DIR:-$HOME/.local/share/sibi-store/signing}"
[[ -f "$SIBI_KEYS/release.jks" && -f "$SIBI_KEYS/password" ]] || { echo 'The Sibi Store release signing key is required.'; exit 1; }
SIBI_TOOLS="$(ls -d "$ANDROID_HOME"/build-tools/* | sort | tail -1)"
mkdir -p "$SCRIPT_DIR/../release"
SIBI_OUTPUT="$SCRIPT_DIR/../release/sibi-store-vr.apk"
"$SIBI_TOOLS/zipalign" -f -P 16 4 "$SCRIPT_DIR/../Builds/sibi-store-vr-unsigned.apk" "$SCRIPT_DIR/../Builds/sibi-store-vr-aligned.apk"
"$SIBI_TOOLS/apksigner" sign --ks "$SIBI_KEYS/release.jks" --ks-key-alias sibi-store --ks-pass "file:$SIBI_KEYS/password" --out "$SIBI_OUTPUT" "$SCRIPT_DIR/../Builds/sibi-store-vr-aligned.apk"
bash "$SCRIPT_DIR/verify-apk.sh" "$SIBI_OUTPUT"
SIBI_DESKTOP="$HOME/Desktop/sibi-store-vr.apk"
[[ -d "$HOME/Desktop" && ! -L "$SIBI_DESKTOP" && ! -d "$SIBI_DESKTOP" ]] || { echo 'Unsafe Desktop destination'; exit 1; }
cp -p "$SIBI_OUTPUT" "$SIBI_DESKTOP"
cmp "$SIBI_OUTPUT" "$SIBI_DESKTOP"
echo "Signed Quest APK: $SIBI_DESKTOP"
