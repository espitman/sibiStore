const test = require('node:test');
const assert = require('node:assert/strict');
const { Devices } = require('../server/devices.cjs');
const { PeerTransfers } = require('../server/peer-transfers.cjs');
const { createServer } = require('../server/http.cjs');

const sender = { 'x-device-id': 'sender-device', 'x-device-token': 's'.repeat(36), 'x-device-capabilities': 'files-v1,peers-v1', 'x-device-name': 'Sender phone', 'x-device-platform': 'phone' };
const recipient = { 'x-device-id': 'recipient-device', 'x-device-token': 'r'.repeat(36), 'x-device-capabilities': 'peers-v1', 'x-device-name': 'Living room TV', 'x-device-platform': 'tv' };

async function setup(t, options = {}) {
  const devices = new Devices({ now: options.now || Date.now });
  const peers = new PeerTransfers({ devices, serverId: 'mac-id', now: options.now || Date.now, ...options });
  const http = await createServer({ library: { catalog: () => [] }, serverId: 'mac-id', port: 0, host: '127.0.0.1', advertise: false, peerTransfers: peers });
  t.after(() => http.close());
  for (const headers of [sender, recipient]) await http.server.inject({ url: '/api/v1/catalog', headers });
  return { http, peers };
}
const call = (http, method, url, headers, payload) => http.server.inject({ method, url, headers, payload });

test('registers only authenticated peers and derives their observed address', async t => {
  const { http } = await setup(t);
  assert.equal((await call(http, 'POST', '/api/v1/peers/register', { ...sender, 'x-device-token': 'wrong' }, { port: 9123 })).statusCode, 401);
  assert.equal((await call(http, 'POST', '/api/v1/peers/register', sender, { port: 0 })).statusCode, 400);
  await call(http, 'POST', '/api/v1/peers/register', sender, { port: 9123 });
  await call(http, 'POST', '/api/v1/peers/register', recipient, { port: 9456 });
  const body = (await call(http, 'GET', '/api/v1/peers', sender)).json();
  assert.equal(body.serverId, 'mac-id'); assert.deepEqual(body.peers, [{ id: 'recipient-device', name: 'Living room TV', platform: 'tv', address: '127.0.0.1', port: 9456 }]);
});

test('keeps capabilities hidden from recipient until acceptance and authorizes transitions', async t => {
  const { http } = await setup(t);
  await call(http, 'POST', '/api/v1/peers/register', recipient, { port: 9456 });
  const created = await call(http, 'POST', '/api/v1/peer-transfers', sender, { recipientId: 'recipient-device', port: 9123, files: [{ id: 'file-one', name: 'Example.apk', size: 123, sha256: 'a'.repeat(64) }] });
  assert.equal(created.statusCode, 201); const offer = created.json();
  assert.equal(offer.status, 'pending'); assert.ok(offer.files[0].capabilityToken); assert.equal(offer.sender.address, '127.0.0.1');
  const pending = (await call(http, 'GET', '/api/v1/peer-transfers', recipient)).json().transfers[0];
  assert.equal(pending.files[0].capabilityToken, undefined); assert.equal(pending.sender.address, undefined); assert.deepEqual(pending.recipient, { id: 'recipient-device', name: 'Living room TV', platform: 'tv' });
  assert.equal((await call(http, 'POST', `/api/v1/peer-transfers/${offer.id}/decision`, sender, { accept: true })).statusCode, 404);
  const accepted = (await call(http, 'POST', `/api/v1/peer-transfers/${offer.id}/decision`, recipient, { accept: true })).json();
  assert.equal(accepted.status, 'accepted'); assert.equal(accepted.sender.port, 9123); assert.equal(accepted.files[0].capabilityToken, offer.files[0].capabilityToken);
  assert.equal((await call(http, 'POST', `/api/v1/peer-transfers/${offer.id}/status`, sender, { status: 'completed' })).statusCode, 409);
  assert.equal((await call(http, 'POST', `/api/v1/peer-transfers/${offer.id}/status`, recipient, { status: 'completed' })).json().status, 'completed');
});

test('bounds metadata, expires pending offers, and permits sender cancellation', async t => {
  let time = 1000; const { http } = await setup(t, { now: () => time, offerTtl: 100, registrationTtl: 1000 });
  await call(http, 'POST', '/api/v1/peers/register', recipient, { port: 9456 });
  const metadata = { recipientId: 'recipient-device', port: 9123, files: [{ id: 'one', name: 'one.apk', size: 1, sha256: 'b'.repeat(64) }] };
  const first = (await call(http, 'POST', '/api/v1/peer-transfers', sender, metadata)).json();
  assert.equal((await call(http, 'POST', `/api/v1/peer-transfers/${first.id}/status`, sender, { status: 'cancelled' })).json().status, 'cancelled');
  const second = (await call(http, 'POST', '/api/v1/peer-transfers', sender, metadata)).json(); time += 101;
  const expired = (await call(http, 'GET', '/api/v1/peer-transfers', recipient)).json().transfers.find(item => item.id === second.id);
  assert.equal(expired.status, 'failed'); assert.equal(expired.error, 'Offer expired');
  assert.equal((await call(http, 'POST', '/api/v1/peer-transfers', sender, { ...metadata, files: Array.from({ length: 101 }, (_, i) => ({ ...metadata.files[0], id: `f${i}` })) })).statusCode, 400);
});

test('preserves active offers, expires accepted offers, and makes terminal acknowledgements idempotent', async t => {
  let time = 1000; const { http, peers } = await setup(t, { now: () => time, offerTtl: 100, acceptedTtl: 200, registrationTtl: 1000, maxJobs: 2, maxTerminalJobs: 1 });
  await call(http, 'POST', '/api/v1/peers/register', recipient, { port: 9456 });
  const metadata = number => ({ recipientId: 'recipient-device', port: 9123, files: [{ id: `file-${number}`, name: `${number}.apk`, size: 1, sha256: String(number).repeat(64) }] });
  const accepted = (await call(http, 'POST', '/api/v1/peer-transfers', sender, metadata(1))).json();
  await call(http, 'POST', `/api/v1/peer-transfers/${accepted.id}/decision`, recipient, { accept: true });
  const pending = (await call(http, 'POST', '/api/v1/peer-transfers', sender, metadata(2))).json();
  peers.jobs.push({ ...peers.jobs[0], id: 'terminal-new', status: 'rejected', createdAt: time + 2, updatedAt: time + 2 });
  peers.jobs.push({ ...peers.jobs[0], id: 'terminal-old', status: 'rejected', createdAt: time + 1, updatedAt: time + 1 });
  peers.clean();
  assert.ok(peers.jobs.some(job => job.id === accepted.id)); assert.ok(peers.jobs.some(job => job.id === pending.id));
  assert.equal(peers.jobs.filter(job => FINAL_FOR_TEST.has(job.status)).length, 1);
  peers.maxTerminalJobs = 10;
  time += 101; peers.clean(); assert.equal(peers.jobs.find(job => job.id === pending.id).error, 'Offer expired');
  time += 100; peers.clean(); assert.equal(peers.jobs.find(job => job.id === accepted.id).error, 'Accepted transfer expired');

  time += 1; await call(http, 'POST', '/api/v1/peers/register', recipient, { port: 9456 });
  const completed = (await call(http, 'POST', '/api/v1/peer-transfers', sender, metadata(3))).json();
  await call(http, 'POST', `/api/v1/peer-transfers/${completed.id}/decision`, recipient, { accept: true });
  const first = await call(http, 'POST', `/api/v1/peer-transfers/${completed.id}/status`, recipient, { status: 'completed' });
  const repeated = await call(http, 'POST', `/api/v1/peer-transfers/${completed.id}/status`, recipient, { status: 'completed' });
  assert.equal(first.statusCode, 200); assert.equal(repeated.statusCode, 200);
});

const FINAL_FOR_TEST = new Set(['rejected', 'cancelled', 'completed', 'failed']);

test('registration enrolls after restart and refreshes active sender endpoint without changing tokens', async t => {
  const { http } = await setup(t);
  http.deviceRegistry.entries.clear(); http.deviceRegistry.credentials.clear();
  assert.equal((await call(http, 'POST', '/api/v1/peers/register', sender, { port: 9123 })).statusCode, 200);
  await call(http, 'POST', '/api/v1/peers/register', recipient, { port: 9456 });
  const offer = (await call(http, 'POST', '/api/v1/peer-transfers', sender, { recipientId: 'recipient-device', port: 9123, files: [{ id: 'stable', name: 'stable.apk', size: 2, sha256: 'f'.repeat(64) }] })).json();
  await call(http, 'POST', `/api/v1/peer-transfers/${offer.id}/decision`, recipient, { accept: true });
  await call(http, 'POST', '/api/v1/peers/register', sender, { port: 9222 });
  const refreshed = (await call(http, 'GET', '/api/v1/peer-transfers', recipient)).json().transfers.find(item => item.id === offer.id);
  assert.equal(refreshed.sender.port, 9222); assert.equal(refreshed.files[0].capabilityToken, offer.files[0].capabilityToken);
  assert.equal((await call(http, 'POST', '/api/v1/peers/register', { ...sender, 'x-device-token': 'x'.repeat(36) }, { port: 9333 })).statusCode, 401);
});

test('idempotent creation returns the original offer and rejects key reuse with different metadata', async t => {
  const { http, peers } = await setup(t);
  await call(http, 'POST', '/api/v1/peers/register', recipient, { port: 9456 });
  const payload = { clientRequestId: 'c'.repeat(64), recipientId: 'recipient-device', port: 9123, files: [
    { id: 'second', name: 'second.apk', size: 2, sha256: '2'.repeat(64) },
    { id: 'first', name: 'first.apk', size: 1, sha256: '1'.repeat(64) }
  ] };
  const created = await call(http, 'POST', '/api/v1/peer-transfers', sender, payload);
  const retried = await call(http, 'POST', '/api/v1/peer-transfers', sender, { ...payload, port: 9222, files: [...payload.files].reverse() });
  assert.equal(created.statusCode, 201); assert.equal(retried.statusCode, 200);
  assert.equal(retried.json().id, created.json().id); assert.deepEqual(retried.json().files.map(file => file.capabilityToken).sort(), created.json().files.map(file => file.capabilityToken).sort());
  assert.equal(peers.jobs.filter(job => job.clientRequestId === payload.clientRequestId).length, 1);
  const conflict = await call(http, 'POST', '/api/v1/peer-transfers', sender, { ...payload, files: [{ ...payload.files[0], size: 3 }, payload.files[1]] });
  assert.equal(conflict.statusCode, 409);
  assert.equal((await call(http, 'POST', '/api/v1/peer-transfers', sender, { ...payload, clientRequestId: 'INVALID' })).statusCode, 400);
});

test('re-enrolling an expired device id with a different credential cannot inherit old offers', async t => {
  const { http } = await setup(t);
  await call(http, 'POST', '/api/v1/peers/register', recipient, { port: 9456 });
  const payload = { clientRequestId: 'd'.repeat(64), recipientId: 'recipient-device', port: 9123, files: [{ id: 'private', name: 'private.apk', size: 4, sha256: 'e'.repeat(64) }] };
  const offer = (await call(http, 'POST', '/api/v1/peer-transfers', sender, payload)).json();
  http.deviceRegistry.entries.clear(); http.deviceRegistry.credentials.clear();
  const replacementRecipient = { ...recipient, 'x-device-token': 'n'.repeat(36) };
  await call(http, 'POST', '/api/v1/peers/register', replacementRecipient, { port: 9555 });
  assert.deepEqual((await call(http, 'GET', '/api/v1/peer-transfers', replacementRecipient)).json().transfers, []);
  assert.equal((await call(http, 'POST', `/api/v1/peer-transfers/${offer.id}/decision`, replacementRecipient, { accept: true })).statusCode, 404);

  http.deviceRegistry.entries.clear(); http.deviceRegistry.credentials.clear();
  await call(http, 'POST', '/api/v1/peers/register', recipient, { port: 9456 });
  assert.equal((await call(http, 'GET', '/api/v1/peer-transfers', recipient)).json().transfers[0].id, offer.id);
  http.deviceRegistry.entries.clear(); http.deviceRegistry.credentials.clear();
  const replacementSender = { ...sender, 'x-device-token': 'z'.repeat(36) };
  await call(http, 'POST', '/api/v1/peers/register', replacementSender, { port: 9999 });
  assert.deepEqual((await call(http, 'GET', '/api/v1/peer-transfers', replacementSender)).json().transfers, []);
  const duplicate = await call(http, 'POST', '/api/v1/peer-transfers', replacementSender, payload);
  assert.notEqual(duplicate.statusCode, 200);
});
