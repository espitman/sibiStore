#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../../mobile/scripts/env.sh"
SIBI_APK="${1:-$SCRIPT_DIR/../release/sibi-store-vr.apk}"
if [[ $# -gt 0 ]]; then shift; fi
SIBI_TOOLS="$(ls -d "$ANDROID_HOME"/build-tools/* | sort | tail -1)"
"$SIBI_TOOLS/apksigner" verify --verbose "$@" "$SIBI_APK"
"$SIBI_TOOLS/zipalign" -c -P 16 4 "$SIBI_APK"
"$SIBI_TOOLS/aapt2" dump badging "$SIBI_APK"
SIBI_XML="$(mktemp "${TMPDIR:-/tmp}/sibi-vr-manifest.XXXXXX")"
trap 'rm -f -- "$SIBI_XML"' EXIT
"$SIBI_TOOLS/aapt2" dump xmltree --file AndroidManifest.xml "$SIBI_APK" > "$SIBI_XML"
python3 - "$SIBI_APK" "$SIBI_XML" <<'PY'
import hashlib,sys,zipfile
manifest=open(sys.argv[2]).read()
for required in ['com.sibi.store.vr','com.unity3d.player.UnityPlayerActivity','com.oculus.intent.category.VR','com.oculus.permission.HAND_TRACKING','com.oculus.supportedDevices','quest3','android.permission.REQUEST_INSTALL_PACKAGES','com.sibi.store.core.InstallResultReceiver']:
    assert required in manifest, 'Missing manifest entry: '+required
with zipfile.ZipFile(sys.argv[1]) as z:
    assert z.testzip() is None, 'APK ZIP integrity failure'
    names=z.namelist()
    abis={n.split('/')[1] for n in names if n.startswith('lib/') and n.endswith('.so')}
    assert abis=={'arm64-v8a'}, 'Expected only ARM64 native libraries: '+str(abis)
    assert 'lib/arm64-v8a/libil2cpp.so' in names
    dex=b''.join(z.read(n) for n in names if n.startswith('classes') and n.endswith('.dex'))
    assert b'Lcom/sibi/store/vr/StoreBridge;' in dex, 'Android bridge was stripped'
h=hashlib.sha256()
with open(sys.argv[1],'rb') as f:
    for b in iter(lambda:f.read(1048576),b''):h.update(b)
print('Quest manifest, ARM64, bridge, ZIP integrity and signing verified.')
print('SHA-256:',h.hexdigest())
PY
