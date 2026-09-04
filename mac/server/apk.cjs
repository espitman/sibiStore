const { execFile } = require('node:child_process');
const { promisify } = require('node:util');
const fs = require('node:fs/promises');
const path = require('node:path');
const os = require('node:os');
const run = promisify(execFile);

function parseBadging(text) {
  const pkg = text.match(/^package: name='([^']+)' versionCode='(\d+)' versionName='([^']*)'/m);
  if (!pkg) throw new Error('APK manifest could not be read');
  if (/^package:.*\bsplit='/m.test(text)) throw new Error('Split APK: add a standalone universal APK instead');
  const abis = text.match(/^native-code: (.+)$/m)?.[1].match(/'([^']+)'/g)?.map(s => s.slice(1, -1)) || [];
  const minSdk = Number(text.match(/^(?:minSdkVersion|sdkVersion):'(\d+)'/m)?.[1] || 1);
  const major = text.match(/^package:.*\bversionCodeMajor='(\d+)'/m)?.[1] || '0';
  const versionCode = ((BigInt(major) << 32n) | BigInt(pkg[2])).toString();
  return { packageName: pkg[1], versionCode, versionName: pkg[3],
    title: text.match(/^application-label-en:'([^']*)'/m)?.[1] || text.match(/^application-label:'([^']*)'/m)?.[1] || text.match(/^application: label='([^']*)'/m)?.[1] || pkg[1],
    minSdk, abis, tv: /^leanback-launchable-activity:/m.test(text),
    iconPath: [...text.matchAll(/^application-icon-(\d+):'([^']+\.(?:png|webp|jpg))'/gm)].sort((a,b) => Number(b[1])-Number(a[1]))[0]?.[2] || text.match(/^application:.*\bicon='([^']+\.(?:png|webp|jpg))'/m)?.[1] };
}
async function tools() {
  const sdk = process.env.ANDROID_HOME || process.env.ANDROID_SDK_ROOT || path.join(os.homedir(), 'Library/Android/sdk');
  const roots = await fs.readdir(path.join(sdk, 'build-tools')).catch(() => []);
  const latest = roots.filter(v => /^\d+\.\d+\.\d+$/.test(v)).sort((a,b) => a.localeCompare(b, undefined, { numeric: true })).at(-1);
  if (!latest) throw new Error('Android SDK Build Tools not found. Set the SDK folder in Settings.');
  return path.join(sdk, 'build-tools', latest);
}
async function inspectApk(file) {
  const toolDir = await tools();
  const { stdout } = await run(path.join(toolDir, 'aapt2'), ['dump', 'badging', file], { timeout: 45000, maxBuffer: 8 * 1024 * 1024 });
  const metadata = parseBadging(stdout);
  const { stdout: signature } = await run(path.join(toolDir, 'apksigner'), ['verify', '--print-certs', file], { timeout: 45000, maxBuffer: 1024 * 1024 });
  const certificates = [...signature.matchAll(/^Signer #\d+ certificate SHA-256 digest: ([a-fA-F0-9]+)$/gm)].map(m => m[1].toLowerCase()).sort();
  if (!certificates.length) throw new Error('No verified signing certificate found');
  let icon = null;
  if (metadata.iconPath) {
    try {
      const { stdout: bytes } = await run('/usr/bin/unzip', ['-p', file, metadata.iconPath], { encoding: 'buffer', maxBuffer: 2 * 1024 * 1024, timeout: 10000 });
      const ext = path.extname(metadata.iconPath).slice(1).replace('jpg', 'jpeg');
      icon = `data:image/${ext};base64,${bytes.toString('base64')}`;
    } catch { /* Adaptive/vector icons get an initial-based fallback in the UI. */ }
  }
  delete metadata.iconPath;
  return { ...metadata, certificates, icon };
}
module.exports = { inspectApk, parseBadging, tools };
