const test = require('node:test');
const assert = require('node:assert/strict');
const { Devices } = require('../server/devices.cjs');
const { createServer } = require('../server/http.cjs');
test('stable identities survive address changes; independent devices expire', () => {
  let time = 0; const devices = new Devices({ now: () => time });
  const request = (id, ip) => ({ ip, headers: { 'x-device-id': id, 'x-device-name': 'Quest 3', 'x-device-platform': 'vr' }, query: { platform: 'phone' } });
  devices.touch(request('device-one', '10.0.0.1')); devices.touch(request('device-two', '10.0.0.1'));
  time = 30000; devices.touch(request('device-one', '10.0.0.2'));
  assert.equal(devices.snapshot().length, 2); assert.equal(devices.snapshot()[1].platform, 'vr');
  time = 45000; assert.equal(devices.prune(), true);
  assert.deepEqual(devices.snapshot().map(d => d.address), ['10.0.0.2']);
  time = 75000; devices.prune(); assert.deepEqual(devices.snapshot(), []);
});
test('discovery probes and invalid catalogs do not register; legacy catalog polls and 304 do', async t => {
  const http = await createServer({ library: { catalog: () => [] }, serverId: 'test', port: 0, host: '127.0.0.1', advertise: false });
  t.after(() => http.close());
  await http.server.inject('/api/v1/info'); await http.server.inject('/api/v1/catalog?platform=invalid');
  assert.equal(http.devices().length, 0);
  const first = await http.server.inject('/api/v1/catalog?platform=tv');
  assert.equal(http.devices()[0].platform, 'tv');
  await http.server.inject({ url: '/api/v1/catalog?platform=tv', headers: { 'if-none-match': first.headers.etag } });
  assert.equal(http.devices().length, 1);
});
