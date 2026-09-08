// HTTP clients are considered connected while their catalog heartbeat is recent.
class Devices {
  constructor({ now = Date.now, timeout = 45000 } = {}) { this.now = now; this.timeout = timeout; this.entries = new Map(); }
  touch(request) {
    const text = (value, max) => typeof value === 'string' ? value.replace(/[\x00-\x1f\x7f]/g, '').trim().slice(0, max) : '';
    const suppliedId = text(request.headers['x-device-id'], 80);
    const id = /^[a-zA-Z0-9_-]{8,80}$/.test(suppliedId) ? suppliedId : `ip:${request.ip}`;
    const type = text(request.headers['x-device-platform'], 10) || request.query?.platform;
    const platform = ['phone', 'tv', 'vr'].includes(type) ? type : 'unknown';
    const previous = this.entries.get(id);
    this.entries.delete(id);
    this.entries.set(id, { id, name: text(request.headers['x-device-name'], 100) || previous?.name || 'Unknown device',
      platform, address: request.ip, lastSeen: this.now() });
    if (this.entries.size > 256) this.entries.delete(this.entries.keys().next().value);
  }
  prune() { let changed = false; for (const [id, device] of this.entries) if (this.now() - device.lastSeen >= this.timeout) { this.entries.delete(id); changed = true; } return changed; }
  snapshot() { return [...this.entries.values()].filter(d => this.now() - d.lastSeen < this.timeout).map(d => ({ ...d, lastSeen: new Date(d.lastSeen).toISOString() })); }
}
module.exports = { Devices };
