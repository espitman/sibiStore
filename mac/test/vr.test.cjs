const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs/promises');
const path = require('node:path');
const os = require('node:os');
const { Library } = require('../server/library.cjs');
const { createServer } = require('../server/http.cjs');
const { parseManifestVr, CLASSIFICATION_REVISION } = require('../server/apk.cjs');

const baseMetadata = (overrides = {}) => ({
  packageName: 'com.sibi.fixture', title: 'Fixture', versionCode: '1', versionName: '1.0',
  certificates: ['certificate'], minSdk: 26, abis: [], tv: false, vr: false,
  icon: 'data:image/png;base64,fixture', ...overrides
});

async function fixtureRoot(prefix) {
  const root = await fs.mkdtemp(path.join(os.tmpdir(), prefix));
  const folder = path.join(root, 'input');
  const dataDir = path.join(root, 'data');
  await fs.mkdir(folder);
  return { root, folder, dataDir };
}

test('VR manifest detection requires the exact category or supportedDevices metadata name', () => {
  const tree = body => `E: manifest (line=1)\n  E: application (line=2)\n${body}`;
  assert.equal(parseManifestVr(tree('    E: activity (line=3)\n      E: intent-filter (line=4)\n        E: category (line=5)\n          A: android:name(0x01010003)="com.oculus.intent.category.VR" (Raw: "com.oculus.intent.category.VR")')), true);
  assert.equal(parseManifestVr(tree('    E: meta-data (line=3)\n      A: http://schemas.android.com/apk/res/android:name(0x01010003)="com.oculus.supportedDevices" (Raw: "com.oculus.supportedDevices")\n      A: http://schemas.android.com/apk/res/android:value(0x01010024)="quest|quest2"')), true);
  assert.equal(parseManifestVr(tree('    E: application-label (line=3)\n      A: android:name(0x01010003)="com.oculus.intent.category.VR"\n    E: meta-data (line=4)\n      A: android:name(0x01010003)="example.metadata"\n      A: android:value(0x01010024)="com.oculus.supportedDevices"')), false);
  assert.equal(parseManifestVr(tree('    E: category (line=3)\n      A: android:name(0x01010003)="com.oculus.intent.category.VR.extra"')), false);
});

test('cached APK classification migrates exactly once', async () => {
  const { root, folder, dataDir } = await fixtureRoot('sibi-vr-migration-');
  const apk = path.join(folder, 'fixture.apk');
  await fs.writeFile(apk, 'fixture');
  let library;
  try {
    library = await new Library({ folder, dataDir, inspect: async () => baseMetadata() }).init();
    const cached = { ...library.versions[0] };
    delete cached.vr;
    delete cached.classificationRevision;
    library.db.run('UPDATE versions SET metadata = ? WHERE sha = ?', [JSON.stringify(cached), cached.sha256]);
    await library.persist();
    await library.close();

    let inspections = 0;
    library = await new Library({ folder, dataDir, inspect: async () => { inspections++; return baseMetadata({ vr: true }); } }).init();
    assert.equal(inspections, 1);
    assert.equal(library.versions[0].vr, true);
    assert.equal(library.versions[0].classificationRevision, CLASSIFICATION_REVISION);
    await library.close();

    library = await new Library({ folder, dataDir, inspect: async () => { throw new Error('classification migrated more than once'); } }).init();
    assert.equal(library.catalog()[0].versions[0].platform, 'vr');
    assert.deepEqual(library.errors, []);
  } finally {
    await library?.close();
    await fs.rm(root, { recursive: true, force: true });
  }
});

test('per-release override survives rename and restart, and Auto reset persists', async () => {
  const { root, folder, dataDir } = await fixtureRoot('sibi-vr-override-');
  const original = path.join(folder, 'fixture.apk');
  await fs.writeFile(original, 'fixture');
  let library;
  try {
    library = await new Library({ folder, dataDir, inspect: async () => baseMetadata({ tv: true, vr: true }) }).init();
    const sha = library.versions[0].sha256;
    assert.equal(library.catalog()[0].versions[0].platform, 'vr', 'VR takes precedence over TV in Auto');
    await assert.rejects(library.setPlatformOverride('invalid', 'tv'), /Invalid APK release hash/);
    await assert.rejects(library.setPlatformOverride('f'.repeat(64), 'tv'), /APK release not found/);
    await assert.rejects(library.setPlatformOverride(sha, 'tablet'), /Invalid platform override/);
    await library.setPlatformOverride(sha, 'tv');
    assert.deepEqual({ tv: library.catalog()[0].versions[0].tv, vr: library.catalog()[0].versions[0].vr }, { tv: true, vr: false });
    await library.setPlatformOverride(sha, 'vr');
    assert.deepEqual({ tv: library.catalog()[0].versions[0].tv, vr: library.catalog()[0].versions[0].vr }, { tv: false, vr: true });
    await library.setPlatformOverride(sha, 'phone');
    assert.deepEqual({ tv: library.catalog('phone')[0].versions[0].tv, vr: library.catalog('phone')[0].versions[0].vr }, { tv: false, vr: false });
    assert.equal(library.catalog('phone')[0].versions[0].platformOverride, 'phone');

    await fs.rename(original, path.join(folder, 'renamed.apk'));
    await library.scan();
    assert.equal(library.catalog()[0].versions[0].platform, 'phone');
    await library.close();

    library = await new Library({ folder, dataDir, inspect: async () => { throw new Error('renamed cached APK was re-inspected'); } }).init();
    assert.equal(library.catalog()[0].versions[0].platform, 'phone');
    await library.setPlatformOverride(sha, 'auto');
    assert.equal(library.catalog()[0].versions[0].platform, 'vr');
    await library.close();

    library = await new Library({ folder, dataDir, inspect: async () => { throw new Error('reset cached APK was re-inspected'); } }).init();
    assert.equal(library.catalog()[0].versions[0].platformOverride, 'auto');
    assert.equal(library.catalog()[0].versions[0].platform, 'vr');
  } finally {
    await library?.close();
    await fs.rm(root, { recursive: true, force: true });
  }
});

test('concurrent scan and override serialize SQLite writes and close waits for durability', async () => {
  const { root, folder, dataDir } = await fixtureRoot('sibi-vr-persist-');
  await fs.writeFile(path.join(folder, 'fixture.apk'), 'fixture');
  let library;
  try {
    library = await new Library({ folder, dataDir, inspect: async () => baseMetadata() }).init();
    await library.watcher.close();
    library.watcher = null;
    const sha = library.versions[0].sha256;
    const writeDatabase = library.writeDatabase.bind(library);
    let activeWrites = 0, maximumActiveWrites = 0, completedWrites = 0;
    library.writeDatabase = async snapshot => {
      activeWrites++;
      maximumActiveWrites = Math.max(maximumActiveWrites, activeWrites);
      try {
        await new Promise(resolve => setTimeout(resolve, 25));
        await writeDatabase(snapshot);
        completedWrites++;
      } finally { activeWrites--; }
    };

    const scan = library.scan();
    const override = library.setPlatformOverride(sha, 'tv');
    await library.close();
    await Promise.all([scan, override]);
    assert.equal(maximumActiveWrites, 1);
    assert.equal(completedWrites, 2);
    assert.equal(activeWrites, 0, 'close returned only after queued writes completed');

    library = await new Library({ folder, dataDir, inspect: async () => { throw new Error('durable cached APK was re-inspected'); } }).init();
    const persisted = library.catalog()[0].versions[0];
    assert.equal(persisted.platformOverride, 'tv');
    assert.equal(persisted.platform, 'tv');
  } finally {
    await library?.close();
    await fs.rm(root, { recursive: true, force: true });
  }
});

test('catalog platform queries isolate versions and ETags while legacy requests omit VR', async t => {
  const { root, folder, dataDir } = await fixtureRoot('sibi-vr-api-');
  await Promise.all(['phone', 'tv', 'vr'].map(name => fs.writeFile(path.join(folder, `${name}.apk`), name)));
  const inspect = async file => {
    const kind = await fs.readFile(file, 'utf8');
    return baseMetadata({ versionCode: { phone: '1', tv: '2', vr: '3' }[kind], versionName: kind, tv: kind !== 'phone', vr: kind === 'vr' });
  };
  const library = await new Library({ folder, dataDir, inspect }).init();
  const http = await createServer({ library, serverId: 'vr-api', port: 0, host: '127.0.0.1', advertise: false });
  t.after(async () => { await http.close(); await library.close(); await fs.rm(root, { recursive: true, force: true }); });
  const request = suffix => http.server.inject(`/api/v1/catalog${suffix}`);
  const legacy = await request('');
  const all = await request('?platform=all');
  const phone = await request('?platform=phone');
  const tv = await request('?platform=tv');
  const vr = await request('?platform=vr');
  const names = response => response.json().apps.flatMap(app => app.versions.map(version => version.versionName));
  assert.deepEqual(names(legacy), ['tv', 'phone']);
  assert.deepEqual(names(all), ['vr', 'tv', 'phone']);
  assert.deepEqual(names(phone), ['phone']);
  assert.deepEqual(names(tv), ['tv']);
  assert.deepEqual(names(vr), ['vr']);
  assert.equal(vr.json().apps[0].versions[0].tv, true, 'raw TV detection is retained for VR releases');
  assert.equal(library.catalog()[0].versions.length, 3, 'local catalog remains complete');
  assert.equal(new Set([legacy, all, phone, tv, vr].map(response => response.headers.etag)).size, 5);
  assert.equal((await http.server.inject({ url: '/api/v1/catalog?platform=tv', headers: { 'if-none-match': phone.headers.etag } })).statusCode, 200);
  assert.equal((await request('?platform=tablet')).statusCode, 400);
  assert.equal((await request('?platform=VR')).statusCode, 400);
});
