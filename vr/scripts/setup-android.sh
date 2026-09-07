#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../../mobile/scripts/env.sh"
if [[ ! -x "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]]; then
  SIBI_STAGE="$(mktemp -d "${TMPDIR:-/tmp}/sibi-android.XXXXXX")"
  trap 'rm -rf -- "$SIBI_STAGE"' EXIT
  curl --fail --location --retry 3 'https://dl.google.com/android/repository/commandlinetools-mac-12266719_latest.zip' -o "$SIBI_STAGE/tools.zip"
  python3 - "$SIBI_STAGE/tools.zip" <<'PY'
import base64,hashlib,sys
h=hashlib.sha384()
with open(sys.argv[1],'rb') as f:
    for b in iter(lambda:f.read(1048576),b''):h.update(b)
assert base64.b64encode(h.digest()).decode()=='Q1n+ef1ChK97sZedZt3cUPS69jRa3Rs5DLfbD9KXI91gb5tncO42czPjW8i7xx4O', 'Android tools checksum mismatch'
PY
  unzip -q "$SIBI_STAGE/tools.zip" -d "$SIBI_STAGE/extracted"
  mkdir -p "$ANDROID_HOME/cmdline-tools"
  mv "$SIBI_STAGE/extracted/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
fi
"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" "$@"
