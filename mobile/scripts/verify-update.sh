#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/env.sh"
[[ $# == 2 ]] || { echo 'Usage: verify-update.sh previous.apk next.apk'; exit 2; }
SIBI_TOOLS="$(ls -d "$ANDROID_HOME"/build-tools/* | sort | tail -1)"
python3 - "$SIBI_TOOLS" "$@" <<'PY'
import re, subprocess, sys
from pathlib import Path
tools, previous, following = sys.argv[1:]
def inspect(apk):
    signature = subprocess.check_output([str(Path(tools) / 'apksigner'), 'verify', '--print-certs', apk], text=True)
    certificates = sorted(re.findall(r'Signer #\d+ certificate SHA-256 digest: (\S+)', signature))
    assert certificates, 'Missing signing certificate'
    badging = subprocess.check_output([str(Path(tools) / 'aapt2'), 'dump', 'badging', apk], text=True)
    package = re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']*)'", badging)
    assert package, 'Missing package metadata'
    return package[1], int(package[2]), package[3], certificates
old, new = inspect(previous), inspect(following)
assert old[0] == new[0], 'Package identity changed'
assert old[3] == new[3], 'Signing identity changed'
assert new[1] > old[1], 'Version code must increase'
print(f'Update verified: {new[0]} {old[2]} ({old[1]}) -> {new[2]} ({new[1]}), same signing certificate')
PY
