#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
SIBI_VR_ROOT="$(cd -- "$SCRIPT_DIR/.." && pwd)"
SIBI_DEST="$SIBI_VR_ROOT/Packages/com.meta.xr.sdk.core"
if [[ -f "$SIBI_DEST/.sibi-205.0.0" ]]; then exit 0; fi
SIBI_STAGE="$(mktemp -d "$SIBI_VR_ROOT/Packages/.meta-stage.XXXXXX")"
trap 'rm -rf -- "$SIBI_STAGE"' EXIT
SIBI_ARCHIVE="${1:-$SIBI_STAGE/core.tgz}"
if [[ $# -gt 1 ]]; then echo 'Usage: setup-meta.sh [downloaded-archive.tgz]'; exit 2; fi
if [[ $# -eq 0 ]]; then
  curl --fail --location --retry 3 'https://npm.developer.oculus.com/com.meta.xr.sdk.core/-/com.meta.xr.sdk.core-205.0.0.tgz' -o "$SIBI_ARCHIVE"
fi
python3 - "$SIBI_ARCHIVE" <<'PY'
import base64,hashlib,sys
h=hashlib.sha512()
with open(sys.argv[1],'rb') as f:
 for chunk in iter(lambda:f.read(1024*1024),b''):h.update(chunk)
actual=base64.b64encode(h.digest()).decode()
assert actual=='Zva9QVzKXJ9JAi8sdz5uBkmQARhpwaE/u3rB90EzzKNlIAPUwAo01cb/gy+qzx5NCldyn6wyHHGAhCl+DOxtaQ==','Meta SDK integrity mismatch'
PY
tar -xzf "$SIBI_ARCHIVE" -C "$SIBI_STAGE"
[[ ! -e "$SIBI_DEST" ]] || { echo 'Existing Meta SDK directory must be reviewed before replacement'; exit 1; }
touch "$SIBI_STAGE/package/.sibi-205.0.0"
mv "$SIBI_STAGE/package" "$SIBI_DEST"
