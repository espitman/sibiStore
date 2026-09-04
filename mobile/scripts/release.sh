#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/env.sh"
SIBI_MODULE="${SIBI_MODULE:-app}"
SIBI_PLATFORM="${SIBI_PLATFORM:-mobile}"
bash "$SCRIPT_DIR/test.sh"
cd "$SIBI_ANDROID_ROOT"
./gradlew --no-daemon ":$SIBI_MODULE:assembleRelease" "$@"
SIBI_KEYS="${SIBI_SIGNING_DIR:-$HOME/.local/share/sibi-store/signing}"
mkdir -p "$SIBI_KEYS"; chmod 700 "$SIBI_KEYS"
if [[ ! -f "$SIBI_KEYS/release.jks" ]]; then
  umask 077
  openssl rand -hex 32 > "$SIBI_KEYS/password"
  keytool -genkeypair -keystore "$SIBI_KEYS/release.jks" -storepass:file "$SIBI_KEYS/password" -keypass:file "$SIBI_KEYS/password" -alias sibi-store -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=Sibi Store'
fi
SIBI_TOOLS="$(ls -d "$ANDROID_HOME"/build-tools/* | sort | tail -1)"
if [[ "$SIBI_MODULE" == tv ]]; then SIBI_PLATFORM_ROOT="$SIBI_ANDROID_ROOT/../tv"; else SIBI_PLATFORM_ROOT="$SIBI_ANDROID_ROOT"; fi
mkdir -p "$SIBI_PLATFORM_ROOT/release"
SIBI_INPUT="$SIBI_PLATFORM_ROOT/app/build/outputs/apk/release/$SIBI_MODULE-release-unsigned.apk"
SIBI_OUTPUT="$SIBI_PLATFORM_ROOT/release/sibi-store-$SIBI_PLATFORM.apk"
"$SIBI_TOOLS/apksigner" sign --ks "$SIBI_KEYS/release.jks" --ks-key-alias sibi-store --ks-pass "file:$SIBI_KEYS/password" --out "$SIBI_OUTPUT" "$SIBI_INPUT"
"$SIBI_TOOLS/apksigner" verify --verbose "$SIBI_OUTPUT"
echo "Signed APK: $SIBI_OUTPUT"
echo "Back up the private signing directory outside the repository: $SIBI_KEYS"
