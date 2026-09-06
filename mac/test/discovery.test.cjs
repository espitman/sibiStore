const { test } = require('node:test');
const assert = require('node:assert/strict');
const { EventEmitter } = require('node:events');
const { advertise } = require('../server/discovery.cjs');

function child() {
  const process = new EventEmitter();
  Object.assign(process, { stdout: new EventEmitter(), stderr: new EventEmitter(), exitCode: null, signalCode: null });
  process.kill = signal => { process.signalCode = signal; process.emit('close', null, signal); };
  return process;
}
test('native Bonjour registers the actual port and stable identity and withdraws on shutdown', async () => {
  const process = child();
  const errors = [];
  const service = advertise({ name: 'Sibi Store — Mac', port: 12345, serverId: 'persistent-id', onError: e => errors.push(e),
    spawnProcess: (command, args, options) => {
      assert.equal(command, '/usr/bin/dns-sd');
      assert.deepEqual(args, ['-R', 'Sibi Store — Mac', '_sibistore._tcp', 'local.', '12345', 'serverId=persistent-id', 'api=1']);
      assert.equal(options.shell, undefined);
      return process;
    } });
  await service.close();
  await service.close();
  assert.equal(process.signalCode, 'SIGTERM');
  assert.deepEqual(errors, []);
});
test('registration failures reach the server status instead of silently claiming discovery', async () => {
  const process = child();
  const errors = [];
  const service = advertise({ name: 'Sibi Store', port: 8743, serverId: 'id', onError: e => errors.push(e), spawnProcess: () => process });
  process.stderr.emit('data', Buffer.from('registration denied'));
  process.exitCode = 1;
  process.emit('close', 1, null);
  assert.match(errors[0], /registration denied/);
  await service.close();
});
