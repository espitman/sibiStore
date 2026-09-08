#!/usr/bin/env bash
# Explicit opt-in: publish signed Sibi Store releases to the Mac's selected folder.
set -euo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/" && pwd)"
source "$SCRIPT_DIR/env.sh"
[[ $# -gt 0 ]] || { echo 'Usage: publish-library.sh signed.apk [...]'; exit 2; }
SIBI_TOOLS="$(ls -d "$ANDROID_HOME"/build-tools/* | sort | tail -1)"
python3 - "$SIBI_TOOLS" "$@" <<'PY'
import hashlib, json, os, re, shutil, subprocess, sys, tempfile, zipfile
from pathlib import Path
tools = Path(sys.argv[1])
settings = Path.home() / 'Library/Application Support/sibi-store-server/settings.json'
folder = Path(json.loads(settings.read_text())['folder']).resolve(strict=True)
assert folder.is_dir(), 'Selected library folder is unavailable'
platforms = {'com.sibi.store.mobile': 'mobile', 'com.sibi.store.tv': 'tv', 'com.sibi.store.vr': 'vr'}
def digest(file):
    hasher = hashlib.sha256()
    with file.open('rb') as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b''): hasher.update(chunk)
    return hasher.hexdigest()
prepared = []
certificates = set()
for arg in sys.argv[2:]:
    source = Path(arg).resolve(strict=True)
    signature = subprocess.check_output([str(tools/'apksigner'), 'verify', '--print-certs', str(source)], text=True)
    cert = re.findall(r'Signer #\d+ certificate SHA-256 digest: (\S+)', signature)
    assert cert, 'No signing certificate'
    certificates.add(tuple(sorted(cert)))
    badging = subprocess.check_output([str(tools/'aapt2'), 'dump', 'badging', str(source)], text=True)
    match = re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", badging)
    assert match and match[1] in platforms, 'Not a Sibi Store release'
    assert 'application-debuggable' not in badging, 'Debug APK rejected'
    assert re.fullmatch(r'[0-9]+(?:\.[0-9]+)+', match[3]), 'Unsafe release version'
    subprocess.check_call([str(tools/'zipalign'), '-c', '-P', '16', '4', str(source)], stdout=subprocess.DEVNULL)
    with zipfile.ZipFile(source) as apk: assert apk.testzip() is None, 'Corrupt APK'
    target = folder / f'sibi-store-{platforms[match[1]]}-{match[3]}.apk'
    sha = digest(source)
    if target.exists(): assert target.is_file() and not target.is_symlink() and digest(target) == sha, f'Different file already exists: {target.name}'
    prepared.append((source, target, sha, match[1], match[2], match[3]))
assert len(certificates) == 1, 'Release signing identities differ'
for source, target, sha, package, code, version in prepared:
    if not target.exists():
        fd, temp = tempfile.mkstemp(prefix='.sibi-import-', suffix='.tmp', dir=folder)
        os.close(fd)
        try:
            shutil.copyfile(source, temp)
            assert digest(Path(temp)) == sha, 'Copy integrity check failed'
            os.replace(temp, target)
        finally:
            if os.path.exists(temp): os.unlink(temp)
    print(f'Published {package} {version} (code {code}): {target}')
    print(f'SHA-256: {sha}')
PY
