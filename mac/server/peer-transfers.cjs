const crypto = require('node:crypto');

const hashToken = token => crypto.createHash('sha256').update(String(token || '')).digest('hex');
const ACTIVE = new Set(['pending', 'accepted']);
const FINAL = new Set(['rejected', 'cancelled', 'completed', 'failed']);

class PeerTransfers {
  constructor({ devices, serverId, now = Date.now, offerTtl = 15 * 60 * 1000, acceptedTtl = 24 * 60 * 60 * 1000, registrationTtl = 45 * 1000, maxJobs = 500, maxTerminalJobs = 500, onChange = () => {} }) {
    this.devices = devices; this.serverId = serverId; this.now = now; this.offerTtl = offerTtl;
    this.acceptedTtl = acceptedTtl; this.registrationTtl = registrationTtl; this.maxJobs = maxJobs; this.maxTerminalJobs = maxTerminalJobs; this.onChange = onChange;
    this.registrations = new Map(); this.jobs = [];
  }
  caller(request, { allowEnroll = false } = {}) {
    const id = request.headers['x-device-id'];
    if (!this.devices.credential(id) && !allowEnroll) return null;
    this.devices.touch(request);
    const credential = this.devices.credential(id);
    if (!credential || credential !== hashToken(request.headers['x-device-token'])) return null;
    const device = this.devices.snapshot().find(item => item.id === id && item.canPeer);
    if (!device) return null;
    return { ...device, address: request.ip, credential };
  }
  clean() {
    const now = this.now(); let changed = false;
    for (const [id, registration] of this.registrations) {
      if (now - registration.registeredAt >= this.registrationTtl || !this.devices.snapshot().some(device => device.id === id && device.canPeer)) {
        this.registrations.delete(id); changed = true;
      }
    }
    for (const job of this.jobs) if (ACTIVE.has(job.status) && now >= job.expiresAt) { const wasAccepted = job.status === 'accepted'; job.status = 'failed'; job.error = wasAccepted ? 'Accepted transfer expired' : 'Offer expired'; job.updatedAt = now; changed = true; }
    const keepAfter = now - 24 * 60 * 60 * 1000;
    const active = this.jobs.filter(job => ACTIVE.has(job.status));
    const terminal = this.jobs.filter(job => FINAL.has(job.status) && job.updatedAt >= keepAfter).slice(0, this.maxTerminalJobs);
    const kept = [...active, ...terminal].sort((a, b) => b.createdAt - a.createdAt);
    if (kept.length !== this.jobs.length) { this.jobs = kept; changed = true; }
    if (changed) this.onChange();
  }
  publicJob(job, caller) {
    const recipient = caller.id === job.recipientId && caller.credential === job.recipientCredential;
    const sender = caller.id === job.sender.id && caller.credential === job.senderCredential;
    const files = job.files.map(file => {
      const item = { id: file.id, name: file.name, size: file.size, sha256: file.sha256 };
      if (sender || (recipient && job.status === 'accepted')) item.capabilityToken = file.capabilityToken;
      return item;
    });
    const result = { id: job.id, senderId: job.sender.id, senderName: job.sender.name, recipientId: job.recipientId,
      files, status: job.status, error: job.error, createdAt: new Date(job.createdAt).toISOString(), updatedAt: new Date(job.updatedAt).toISOString(), expiresAt: new Date(job.expiresAt).toISOString() };
    if (sender || (recipient && job.status === 'accepted')) result.sender = { id: job.sender.id, name: job.sender.name, platform: job.sender.platform, address: job.sender.address, port: job.sender.port };
    else result.sender = { id: job.sender.id, name: job.sender.name, platform: job.sender.platform };
    result.recipient = { id: job.recipientId, name: job.recipientName, platform: job.recipientPlatform };
    return result;
  }
  routes(server) {
    const auth = (request, reply, allowEnroll = false) => { const caller = this.caller(request, { allowEnroll }); if (!caller) reply.code(401).send({ error: 'Peer authentication required' }); return caller; };
    server.post('/api/v1/peers/register', async (request, reply) => {
      const caller = auth(request, reply, true); if (!caller) return;
      const port = request.body?.port;
      if (!Number.isSafeInteger(port) || port < 1 || port > 65535) return reply.code(400).send({ error: 'port must be between 1 and 65535' });
      this.clean(); this.registrations.set(caller.id, { id: caller.id, name: caller.name, platform: caller.platform, address: request.ip, port, credential: caller.credential, registeredAt: this.now() });
      for (const job of this.jobs) if (ACTIVE.has(job.status) && job.sender.id === caller.id && job.senderCredential === caller.credential) { job.sender.address = request.ip; job.sender.port = port; }
      this.onChange();
      return { ok: true };
    });
    server.get('/api/v1/peers', async (request, reply) => {
      const caller = auth(request, reply); if (!caller) return;
      this.clean(); return { serverId: this.serverId, peers: [...this.registrations.values()].filter(peer => peer.id !== caller.id).map(({ registeredAt, credential, ...peer }) => peer) };
    });
    server.post('/api/v1/peer-transfers', async (request, reply) => {
      const caller = auth(request, reply); if (!caller) return;
      this.clean(); const { recipientId, files, port, clientRequestId } = request.body || {};
      if (!Number.isSafeInteger(port) || port < 1 || port > 65535) return reply.code(400).send({ error: 'port must be between 1 and 65535' });
      if (typeof recipientId !== 'string' || recipientId === caller.id) return reply.code(400).send({ error: 'Invalid recipient' });
      if (clientRequestId !== undefined && (typeof clientRequestId !== 'string' || !/^[a-f0-9]{64}$/.test(clientRequestId))) return reply.code(400).send({ error: 'Invalid clientRequestId' });
      const recipient = this.registrations.get(recipientId);
      if (!Array.isArray(files) || files.length < 1 || files.length > 100) return reply.code(400).send({ error: 'Choose between 1 and 100 files' });
      const ids = new Set(); const cleanFiles = [];
      for (const file of files) {
        if (!file || typeof file.id !== 'string' || !/^[a-zA-Z0-9_-]{1,100}$/.test(file.id) || ids.has(file.id) || typeof file.name !== 'string' || !file.name.trim() || file.name.length > 255 || /[\x00-\x1f\x7f]/.test(file.name) || !Number.isSafeInteger(file.size) || file.size < 0 || typeof file.sha256 !== 'string' || !/^[a-fA-F0-9]{64}$/.test(file.sha256)) return reply.code(400).send({ error: 'Invalid file metadata' });
        ids.add(file.id); cleanFiles.push({ id: file.id, name: file.name, size: file.size, sha256: file.sha256.toLowerCase() });
      }
      const existing = clientRequestId && this.jobs.find(job => job.sender.id === caller.id && job.senderCredential === caller.credential && job.clientRequestId === clientRequestId);
      if (existing) {
        const sameFiles = existing.files.length === cleanFiles.length && cleanFiles.every(file => {
          const stored = existing.files.find(item => item.id === file.id);
          return stored && stored.name === file.name && stored.size === file.size && stored.sha256 === file.sha256;
        });
        if (existing.recipientId !== recipientId || !sameFiles) return reply.code(409).send({ error: 'clientRequestId was already used for different transfer metadata' });
        return reply.send(this.publicJob(existing, caller));
      }
      if (!recipient) return reply.code(404).send({ error: 'Recipient is offline' });
      if (this.jobs.filter(job => ACTIVE.has(job.status)).length >= this.maxJobs) return reply.code(429).send({ error: 'Peer transfer queue is full' });
      for (const file of cleanFiles) file.capabilityToken = crypto.randomBytes(32).toString('base64url');
      const timestamp = this.now(); const job = { id: crypto.randomUUID(), clientRequestId, sender: { id: caller.id, name: caller.name, platform: caller.platform, address: request.ip, port }, senderCredential: caller.credential, recipientId, recipientName: recipient.name, recipientPlatform: recipient.platform, recipientCredential: recipient.credential, files: cleanFiles, status: 'pending', error: '', createdAt: timestamp, updatedAt: timestamp, expiresAt: timestamp + this.offerTtl };
      this.jobs.unshift(job); this.onChange(); return reply.code(201).send(this.publicJob(job, caller));
    });
    server.get('/api/v1/peer-transfers', async (request, reply) => {
      const caller = auth(request, reply); if (!caller) return;
      this.clean(); return { serverId: this.serverId, transfers: this.jobs.filter(job => (job.sender.id === caller.id && job.senderCredential === caller.credential) || (job.recipientId === caller.id && job.recipientCredential === caller.credential)).map(job => this.publicJob(job, caller)) };
    });
    server.post('/api/v1/peer-transfers/:id/decision', async (request, reply) => {
      const caller = auth(request, reply); if (!caller) return;
      this.clean(); const job = this.jobs.find(item => item.id === request.params.id);
      if (!job || job.recipientId !== caller.id || job.recipientCredential !== caller.credential) return reply.code(404).send({ error: 'Peer transfer not found' });
      if (typeof request.body?.accept !== 'boolean') return reply.code(400).send({ error: 'accept must be a boolean' });
      if (job.status !== 'pending') return reply.code(409).send({ error: 'Offer can no longer be decided' });
      job.status = request.body.accept ? 'accepted' : 'rejected'; job.updatedAt = this.now();
      if (job.status === 'accepted') job.expiresAt = job.updatedAt + this.acceptedTtl;
      this.onChange(); return this.publicJob(job, caller);
    });
    server.post('/api/v1/peer-transfers/:id/status', async (request, reply) => {
      const caller = auth(request, reply); if (!caller) return;
      this.clean(); const job = this.jobs.find(item => item.id === request.params.id); const status = request.body?.status;
      const isSender = job && job.sender.id === caller.id && job.senderCredential === caller.credential;
      const isRecipient = job && job.recipientId === caller.id && job.recipientCredential === caller.credential;
      if (!job || (!isSender && !isRecipient)) return reply.code(404).send({ error: 'Peer transfer not found' });
      if (isRecipient && ['completed', 'failed'].includes(status) && job.status === status) return this.publicJob(job, caller);
      const senderAction = isSender && status === 'cancelled' && ACTIVE.has(job.status);
      const recipientAction = isRecipient && ['completed', 'failed'].includes(status) && job.status === 'accepted';
      if (!senderAction && !recipientAction) return reply.code(409).send({ error: 'Invalid status transition' });
      job.status = status; job.error = status === 'failed' && typeof request.body.error === 'string' ? request.body.error.slice(0, 300) : ''; job.updatedAt = this.now(); this.onChange();
      return this.publicJob(job, caller);
    });
  }
}

module.exports = { PeerTransfers };
