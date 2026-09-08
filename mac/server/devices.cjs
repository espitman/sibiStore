const crypto = require('node:crypto');
// HTTP clients are considered connected while their catalog heartbeat is recent.
class Devices {
  constructor({ now = Date.now, timeout = 45000 } = {}) { this.now = now; this.timeout = timeout; this.entries = new Map(); this.credentials = new Map(); }
  touch(request) {
    const text = (value, max) => typeof value === 'string' ? value.replace(/[\x00-\x1f\x7f]/g, '').trim().slice(0, max) : '';
    const suppliedId = text(request.headers['x-device-id'], 80);
    const id = /^[a-zA-Z0-9_-]{8,80}$/.test(suppliedId) ? suppliedId : `ip:${request.ip}`;
    const token = text(request.headers['x-device-token'], 128);
    const tokenHash = token.length >= 32 ? crypto.createHash('sha256').update(token).digest('hex') : '';
    if (this.credentials.has(id) && this.credentials.get(id) !== tokenHash) return;
    if (tokenHash) this.credentials.set(id, tokenHash);
    const capabilities = text(request.headers['x-device-capabilities'], 100).split(',');
    const canReceiveFiles = !!tokenHash && !id.startsWith('ip:') && capabilities.includes('files-v1');
    const canPeer = !!tokenHash && !id.startsWith('ip:') && capabilities.includes('peers-v1');
    const type = text(request.headers['x-device-platform'], 10) || request.query?.platform;
    const platform = ['phone', 'tv', 'vr'].includes(type) ? type : 'unknown';
    const previous = this.entries.get(id);
    this.entries.delete(id);
    this.entries.set(id, { id, name: text(request.headers['x-device-name'], 100) || previous?.name || 'Unknown device',
      platform, canReceiveFiles, canPeer, address: request.ip, lastSeen: this.now() });
    if (this.entries.size > 256) { const oldest = this.entries.keys().next().value; this.entries.delete(oldest); this.credentials.delete(oldest); }
  }
  credential(id) { return this.credentials.get(id); }
  prune() { let changed = false; for (const [id, device] of this.entries) if (this.now() - device.lastSeen >= this.timeout) { this.entries.delete(id); this.credentials.delete(id); changed = true; } return changed; }
  snapshot() { return [...this.entries.values()].filter(d => this.now() - d.lastSeen < this.timeout).map(d => ({ ...d, lastSeen: new Date(d.lastSeen).toISOString() })); }
}
module.exports = { Devices };
