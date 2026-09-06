const { spawn } = require('node:child_process');
const os = require('node:os');
const assert = require('node:assert/strict');

function observe(args, ready) {
  return new Promise((resolve, reject) => {
    const child = spawn('/usr/bin/dns-sd', args);
    let output = '';
    let done = false;
    const finish = (error, result) => {
      if (done) return;
      done = true; clearTimeout(timer); child.kill();
      if (error) reject(error); else resolve(result);
    };
    const timer = setTimeout(() => finish(new Error(output || 'No mDNS service found')), 12000);
    child.stdout.on('data', chunk => {
      output += chunk;
      const result = ready(output);
      if (result) finish(null, result);
    });
    child.on('error', error => finish(error));
    child.on('exit', code => { if (!done) finish(new Error(`dns-sd exited (${code}): ${output}`)); });
  });
}
(async () => {
  // Verify the service through Apple's independent browser, then resolve its
  // port/TXT and fetch the real catalog. Seeing a PTR alone is insufficient.
  const name = `Sibi Store — ${os.hostname()}`;
  const browse = await observe(['-B', '_sibistore._tcp', 'local.'], text =>
    text.split('\n').some(line => line.includes('Add') && line.includes(name)) && text);
  console.log(browse.trim());
  const resolved = await observe(['-L', name, '_sibistore._tcp', 'local.'], text => {
    const endpoint = text.match(/can be reached at (.+?):(\d+) /);
    const identity = text.match(/serverId=([^\s]+)/);
    return endpoint && identity && { hostname: endpoint[1], port: endpoint[2], id: identity[1] };
  });
  const base = `http://${resolved.hostname}:${resolved.port}`;
  const info = await fetch(`${base}/api/v1/info`, { signal: AbortSignal.timeout(5000) }).then(r => { assert.ok(r.ok); return r.json(); });
  assert.equal(info.serverId, resolved.id);
  assert.equal(info.protocolVersion, 1);
  const catalog = await fetch(`${base}/api/v1/catalog`, { signal: AbortSignal.timeout(5000) }).then(r => { assert.ok(r.ok); return r.json(); });
  assert.equal(catalog.serverId, resolved.id);
  assert.ok(Array.isArray(catalog.apps));
  console.log(`Bonjour resolved ${base}; server identity and catalog verified (${catalog.apps.length} apps).`);
})().catch(error => { console.error(error); process.exitCode = 1; });
