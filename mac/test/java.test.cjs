const { test } = require('node:test');
const assert = require('node:assert/strict');
const { signerCommand } = require('../server/java.cjs');

test('ARM signing pins native Java without modifying the parent environment', async () => {
  const before = { ...process.env };
  const calls = [];
  const command = await signerCommand('/sdk/build-tools/36', { platform: 'darwin', arch: 'arm64', execute: async (file, args) => {
    calls.push([file, args]);
    return { stdout: '/Library/Java/Native JDK/Contents/Home\n' };
  } });
  assert.deepEqual(calls, [
    ['/usr/libexec/java_home', ['-a', 'arm64', '-F']],
    ['/usr/bin/lipo', ['/Library/Java/Native JDK/Contents/Home/bin/java', '-verify_arch', 'arm64']]
  ]);
  assert.deepEqual(command, { file: '/usr/bin/arch', args: ['-arm64', '/Library/Java/Native JDK/Contents/Home/bin/java', '-Xmx1024M', '-jar', '/sdk/build-tools/36/lib/apksigner.jar'] });
  assert.deepEqual({ ...process.env }, before);
});

test('missing or Intel-only Java fails instead of falling back to Rosetta', async () => {
  for (const failAt of [1, 2]) {
    let calls = 0;
    await assert.rejects(signerCommand('/sdk', { platform: 'darwin', arch: 'arm64', execute: async () => {
      if (++calls === failAt) throw new Error('Unavailable architecture');
      return { stdout: '/Library/Java/Intel' };
    } }), /Apple Silicon Java/);
    assert.equal(calls, failAt);
  }
});

test('other architectures retain the SDK signer', async () => {
  assert.deepEqual(await signerCommand('/sdk', { platform: 'darwin', arch: 'x64', execute: () => assert.fail('No native lookup expected') }), { file: '/sdk/apksigner', args: [] });
});
