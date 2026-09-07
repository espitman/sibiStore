const { execFile } = require('node:child_process');
const { promisify } = require('node:util');
const fs = require('node:fs/promises');
const path = require('node:path');
const os = require('node:os');
const run = promisify(execFile);
const { extractIcon, ICON_REVISION } = require('./icons.cjs');
const { signerCommand } = require('./java.cjs');
const CLASSIFICATION_REVISION = 1;

function parseManifestVr(text) {
  const stack = [];
  for (const line of text.split(/\r?\n/)) {
    const element = /^(\s*)E:\s+([^\s(]+)/.exec(line);
    if (element) {
      const indent = element[1].length;
      while (stack.length && stack.at(-1).indent >= indent) stack.pop();
      stack.push({ indent, name: element[2] });
      continue;
    }
    const attribute = /^(\s*)A:\s+(.+?)=(?:"([^"]*)"|([^\s]+))/.exec(line);
    if (!attribute || !stack.length) continue;
    const key = attribute[2].replace(/\(0x[0-9a-f]+\)$/i, '').split(':').at(-1);
    const value = attribute[3] ?? attribute[4];
    const owner = stack.at(-1).name;
    if (key === 'name' && owner === 'category' && value === 'com.oculus.intent.category.VR') return true;
    if (key === 'name' && owner === 'meta-data' && value === 'com.oculus.supportedDevices') return true;
  }
  return false;
}

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
    iconPath: [...text.matchAll(/^application-icon-(\d+):'([^']+\.(?:png|webp|jpe?g|xml))'/gm)].sort((a,b) => Number(b[1])-Number(a[1]))[0]?.[2] || text.match(/^application:.*\bicon='([^']+\.(?:png|webp|jpe?g|xml))'/m)?.[1] };
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
  const signer = await signerCommand(toolDir);
  const [{ stdout: signature }, { stdout: manifest }] = await Promise.all([
    run(signer.file, [...signer.args, 'verify', '--print-certs', file], { timeout: 45000, maxBuffer: 1024 * 1024 }),
    run(path.join(toolDir, 'aapt2'), ['dump', 'xmltree', file, '--file', 'AndroidManifest.xml'], { timeout: 45000, maxBuffer: 8 * 1024 * 1024 })
  ]);
  const certificates = [...signature.matchAll(/^Signer #\d+ certificate SHA-256 digest: ([a-fA-F0-9]+)$/gm)].map(m => m[1].toLowerCase()).sort();
  if (!certificates.length) throw new Error('No verified signing certificate found');
  let icon = null; let iconError = null;
  try { icon = await extractIcon(file, metadata.iconPath, toolDir); }
  catch (e) { iconError = e.message; }

  delete metadata.iconPath;
  return { ...metadata, vr: parseManifestVr(manifest), classificationRevision: CLASSIFICATION_REVISION, certificates, icon, iconRevision: icon ? ICON_REVISION : 0, iconError };
}
module.exports = { inspectApk, parseBadging, parseManifestVr, tools, CLASSIFICATION_REVISION };
