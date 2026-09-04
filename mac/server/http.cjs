const Fastify = require('fastify');
const { createReadStream } = require('node:fs');
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
  const transfers = []; let bonjour, service;
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
    const etag = `"${v.sha256}"`;
    reply.header('Accept-Ranges', 'bytes').header('ETag', etag).header('Content-Type', 'application/vnd.android.package-archive').header('Cache-Control', 'private, max-age=31536000, immutable');
    if (request.headers['if-none-match'] === etag && !request.headers.range) return reply.code(304).send();
    let start = 0, end = v.size - 1;
    if (request.headers.range && (!request.headers['if-range'] || request.headers['if-range'] === etag)) {
      const range = parseRange(request.headers.range, v.size);
      if (!range) return reply.code(416).header('Content-Range', `bytes */${v.size}`).send();
      ({ start, end } = range);
      reply.code(206).header('Content-Range', `bytes ${start}-${end}/${v.size}`);
    }
    reply.header('Content-Length', end - start + 1);
    if (request.method === 'HEAD') return reply.send();
    const transfer = { id: crypto.randomUUID(), title: v.title, device: String(request.headers['x-device-name'] || request.ip).slice(0, 100), bytes: start, size: v.size, status: 'active', startedAt: new Date().toISOString() };
    transfers.unshift(transfer); if (transfers.length > 100) transfers.pop(); onChange();
    const stream = createReadStream(v.artifact, { start, end });
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
      const { Bonjour } = require('bonjour-service');
      bonjour = new Bonjour({}, e => { discoveryError = e.message; onChange(); });
      service = bonjour.publish({ name: `Sibi Store — ${os.hostname()}`, type: 'sibistore', port: actualPort, txt: { serverId, api: '1' } });
      service.on('error', e => { discoveryError = e.message; onChange(); });
    } catch (e) { discoveryError = e.message; }
  }
  return { server, transfers, port: actualPort, addresses: () => addresses(actualPort), discoveryError: () => discoveryError,
    async close() { if (service) await new Promise(resolve => service.stop(resolve)); bonjour?.destroy(); await server.close(); } };
}
module.exports = { createServer, parseRange, addresses };
