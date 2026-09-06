const Fastify = require('fastify');
const { constants } = require('node:fs');
const fs = require('node:fs/promises');
const path = require('node:path');
const crypto = require('node:crypto');
const os = require('node:os');

function parseRange(header, size) {
  const m = /^bytes=(\d*)-(\d*)$/.exec(header || '');
  if (!m || (!m[1] && !m[2])) return null;
  const start = m[1] ? Number(m[1]) : Math.max(0, size - Number(m[2]));
  const end = m[1] && m[2] ? Math.min(Number(m[2]), size - 1) : size - 1;
  return Number.isSafeInteger(start) && Number.isSafeInteger(end) && start <= end && start < size ? { start, end } : null;
}
function addresses(port) {
  return Object.values(os.networkInterfaces()).flat().filter(n => n && !n.internal && n.family === 'IPv4').map(n => `http://${n.address}:${port}`);
}
async function createServer({ library, serverId, port = 8743, host = '0.0.0.0', advertise = true, onChange = () => {} }) {
  const server = Fastify({ logger: false });
  const transfers = []; let bonjour, service, nativeDiscovery;
  server.get('/api/v1/info', async () => ({ protocolVersion: 1, serverId, name: `Sibi Store — ${os.hostname()}`, port }));
  server.get('/api/v1/catalog', async (request, reply) => {
    const catalog = { protocolVersion: 1, serverId, apps: library.catalog() };
    const etag = `"${crypto.createHash('sha256').update(JSON.stringify(catalog)).digest('hex')}"`;
    reply.header('ETag', etag).header('Cache-Control', 'no-cache');
    if (request.headers['if-none-match'] === etag) return reply.code(304).send();
    return catalog;
  });
  server.get('/artifacts/:hash.apk', async (request, reply) => {
    const v = library.versions.find(v => v.sha256 === request.params.hash);
    if (!v) return reply.code(404).send({ error: 'APK not found' });
    let file;
    try {
      const root = await fs.realpath(library.folder);
      const source = await fs.realpath(v.artifact);
      const relative = path.relative(root,source);
      if (!relative || relative === '..' || relative.startsWith('..'+path.sep) || path.isAbsolute(relative)) throw new Error('Source outside library');
      file = await fs.open(source,constants.O_RDONLY | constants.O_NOFOLLOW);
      const before = await file.stat();
      if (!before.isFile() || before.size !== v.size) throw new Error('Source changed');
      const hash = crypto.createHash('sha256'); const buffer = Buffer.alloc(256*1024); let position=0;
      while(position < before.size) {
        const {bytesRead}=await file.read(buffer,0,Math.min(buffer.length,before.size-position),position);
        if (!bytesRead) throw new Error('Source changed');
        hash.update(buffer.subarray(0,bytesRead)); position+=bytesRead;
      }
      const after = await file.stat(); const current = await fs.stat(source);
      if (hash.digest('hex') !== v.sha256 || before.mtimeMs !== after.mtimeMs || before.ctimeMs !== after.ctimeMs || before.size !== after.size || current.ino !== after.ino || current.dev !== after.dev) throw new Error('Source changed');
    } catch(e) {
      await file?.close();
      library.scan().catch(error=>library.report(error));
      return reply.code(e.code === 'ENOENT' ? 404 : 409).send({error:'APK source is missing or changed. Refresh the library.'});
    }
    const etag = `"${v.sha256}"`;
    reply.header('Accept-Ranges', 'bytes').header('ETag', etag).header('Content-Type', 'application/vnd.android.package-archive').header('Cache-Control', 'private, max-age=31536000, immutable');
    if (request.headers['if-none-match'] === etag && !request.headers.range) {await file.close();return reply.code(304).send();}
    let start = 0, end = v.size - 1;
    if (request.headers.range && (!request.headers['if-range'] || request.headers['if-range'] === etag)) {
      const range = parseRange(request.headers.range, v.size);
      if (!range) {await file.close();return reply.code(416).header('Content-Range', `bytes */${v.size}`).send();}
      ({ start, end } = range);
      reply.code(206).header('Content-Range', `bytes ${start}-${end}/${v.size}`);
    }
    reply.header('Content-Length', end - start + 1);
    if (request.method === 'HEAD') {await file.close();return reply.send();}
    const transfer = { id: crypto.randomUUID(), title: v.title, device: String(request.headers['x-device-name'] || request.ip).slice(0, 100), bytes: start, size: v.size, status: 'active', startedAt: new Date().toISOString() };
    transfers.unshift(transfer); if (transfers.length > 100) transfers.pop(); onChange();
    const stream = file.createReadStream({start,end,autoClose:true});
    let last = 0;
    stream.on('data', chunk => { transfer.bytes += chunk.length; if (Date.now() - last > 250) { last = Date.now(); onChange(); } });
    stream.on('error', () => { transfer.status = 'failed'; onChange(); });
    reply.raw.on('finish', () => { transfer.status = 'completed'; onChange(); });
    reply.raw.on('close', () => { if (!reply.raw.writableFinished) { stream.destroy(); transfer.status = 'interrupted'; onChange(); } });
    return reply.send(stream);
  });
  await server.listen({ port, host });
  const actualPort = server.server.address().port;
  let discoveryError = null;
  if (advertise) {
    try {
      const name = `Sibi Store — ${os.hostname()}`;
      const report = message => { discoveryError = message; onChange(); };
      if (process.platform === 'darwin') {
        nativeDiscovery = require('./discovery.cjs').advertise({ name, port: actualPort, serverId, onError: report });
      } else {
        const { Bonjour } = require('bonjour-service');
        bonjour = new Bonjour({}, e => report(e.message));
        service = bonjour.publish({ name, type: 'sibistore', port: actualPort, disableIPv6: true, txt: { serverId, api: '1' } });
        service.on('error', e => report(e.message));
      }
    } catch (e) { discoveryError = e.message; }
  }
  return { server, transfers, port: actualPort, addresses: () => addresses(actualPort), discoveryError: () => discoveryError,
    async close() { await nativeDiscovery?.close(); if (service) await new Promise(resolve => service.stop(resolve)); bonjour?.destroy(); await server.close(); } };
}
module.exports = { createServer, parseRange, addresses };
