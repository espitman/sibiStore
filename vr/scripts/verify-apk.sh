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
SIBI_RESOURCES="$(mktemp "${TMPDIR:-/tmp}/sibi-vr-resources.XXXXXX")"
SIBI_NETWORK_XML="$(mktemp "${TMPDIR:-/tmp}/sibi-vr-network.XXXXXX")"
trap 'rm -f -- "$SIBI_XML" "$SIBI_RESOURCES" "$SIBI_NETWORK_XML"' EXIT
"$SIBI_TOOLS/aapt2" dump xmltree --file AndroidManifest.xml "$SIBI_APK" > "$SIBI_XML"
"$SIBI_TOOLS/aapt2" dump resources "$SIBI_APK" > "$SIBI_RESOURCES"
read -r SIBI_NETWORK_ID SIBI_NETWORK_PATH < <(python3 - "$SIBI_RESOURCES" <<'PY'
import re,sys
resources=open(sys.argv[1]).read()
match=re.search(r'resource (0x[0-9a-f]+) xml/sibi_store_network_security_config\n\s+\(\) \(file\) (\S+) type=XML', resources)
assert match, 'Compiled Sibi Store network security config is missing'
print(match.group(1),match.group(2))
PY
)
"$SIBI_TOOLS/aapt2" dump xmltree --file "$SIBI_NETWORK_PATH" "$SIBI_APK" > "$SIBI_NETWORK_XML"
python3 - "$SIBI_APK" "$SIBI_XML" "$SIBI_NETWORK_XML" "$SIBI_NETWORK_ID" <<'PY'
import hashlib,re,sys,zipfile
manifest=open(sys.argv[2]).read()
assert not re.search(r'android:debuggable[^\n]*=(?:true|0xffffffff)', manifest), 'Refusing a debuggable APK; Quest installs must use Release'
for required in ['com.sibi.store.vr','com.unity3d.player.UnityPlayerActivity','com.oculus.intent.category.VR','com.oculus.permission.HAND_TRACKING','com.oculus.supportedDevices','quest3','android.permission.REQUEST_INSTALL_PACKAGES','com.sibi.store.core.InstallResultReceiver']:
    assert required in manifest, 'Missing manifest entry: '+required
assert re.search(r'android:usesCleartextTraffic[^\n]*=true', manifest), 'LAN HTTP cleartext traffic is not enabled'
network_id=sys.argv[4]
assert re.search(r'android:networkSecurityConfig[^\n]*@'+re.escape(network_id)+r'\b', manifest), 'Sibi Store network security config is not selected'
network_config=open(sys.argv[3]).read()
assert re.search(r'cleartextTrafficPermitted[^\n]*=true', network_config), 'Sibi Store network security config blocks LAN HTTP'
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
print('Quest manifest, LAN HTTP policy, ARM64, bridge, ZIP integrity and signing verified.')
print('SHA-256:',h.hexdigest())
PY
