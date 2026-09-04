const fs = require('node:fs/promises');
const { createReadStream } = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const { EventEmitter } = require('node:events');
const initSqlJs = require('sql.js');
const chokidar = require('chokidar');
const { inspectApk } = require('./apk.cjs');

async function hashFile(file) {
  const hash = crypto.createHash('sha256');
  for await (const chunk of createReadStream(file)) hash.update(chunk);
  return hash.digest('hex');
}
function compareCodes(a,b) { return BigInt(a) > BigInt(b) ? -1 : BigInt(a) < BigInt(b) ? 1 : 0; }
class Library extends EventEmitter {
  constructor({ folder, dataDir, inspect = inspectApk }) {
    super(); Object.assign(this, { folder, dataDir, inspect });
    this.versions = []; this.errors = []; this.scanning = false; this.lastScan = null;
  }
  async init() {
    await fs.mkdir(this.folder, { recursive: true });
    await fs.mkdir(path.join(this.dataDir, 'artifacts'), { recursive: true });
    const SQL = await initSqlJs();
    this.dbPath = path.join(this.dataDir, 'library.sqlite');
    this.db = new SQL.Database(await fs.readFile(this.dbPath).catch(() => undefined));
    this.db.run('CREATE TABLE IF NOT EXISTS versions (sha TEXT PRIMARY KEY, metadata TEXT NOT NULL)');
    const rows = this.db.exec('SELECT metadata FROM versions')[0]?.values || [];
    this.versions = rows.map(r => JSON.parse(r[0]));
    await this.scan();
    this.watcher = chokidar.watch(this.folder, { ignoreInitial: true, awaitWriteFinish: { stabilityThreshold: 2000, pollInterval: 200 }, ignored: p => path.basename(p).startsWith('.') });
    this.watcher.on('all', () => { clearTimeout(this.debounce); this.debounce = setTimeout(() => this.scan().catch(e => this.report(e)), 500); });
    this.watcher.on('error', e => this.report(e));
    return this;
  }
  report(e) { this.errors = [...this.errors, { file: 'Library', message: e.message }]; this.emit('change'); }
  async files(dir) {
    const out = [];
    for (const entry of await fs.readdir(dir, { withFileTypes: true })) {
      if (entry.name.startsWith('.')) continue;
      const file = path.join(dir, entry.name);
      if (entry.isDirectory()) out.push(...await this.files(file));
      else if (entry.isFile() && /\.apk$/i.test(file)) out.push(file);
    }
    return out;
  }
  async scan() {
    if (this.scanning) { this.scanAgain = true; return; }
    this.scanning = true; this.errors = []; this.emit('change');
    try {
      for (const file of await this.files(this.folder)) {
        let temporary;
        try {
          const before = await fs.stat(file);
          const sha256 = await hashFile(file);
          if (this.versions.some(v => v.sha256 === sha256)) continue;
          temporary = path.join(this.dataDir, 'artifacts', `${crypto.randomUUID()}.partial`);
          await fs.copyFile(file, temporary);
          const after = await fs.stat(file);
          if (before.size !== after.size || before.mtimeMs !== after.mtimeMs || await hashFile(temporary) !== sha256) {
            throw new Error('File is still changing. It will be checked again after copying finishes.');
          }
          const meta = await this.inspect(temporary);
          const sameApp = this.versions.filter(v => v.packageName === meta.packageName);
          if (sameApp.some(v => v.versionCode === meta.versionCode && JSON.stringify(v.abis) === JSON.stringify(meta.abis))) throw new Error('Version conflict: this version code already exists with different content');
          // Pin the signer set per package. Key-rotation imports require explicit future support.
          if (sameApp.some(v => JSON.stringify(v.certificates) !== JSON.stringify(meta.certificates))) throw new Error('Signing certificate differs from the versions already in this library');
          const artifact = path.join(this.dataDir, 'artifacts', `${sha256}.apk`);
          await fs.rename(temporary, artifact); temporary = null;
          const version = { ...meta, sha256, size: before.size, filename: path.basename(file), addedAt: new Date().toISOString(), artifact, downloadUrl: `/artifacts/${sha256}.apk` };
          this.db.run('INSERT INTO versions VALUES (?, ?)', [sha256, JSON.stringify(version)]);
          this.versions.push(version);
          await this.persist(); this.emit('change');
        } catch (e) { this.errors.push({ file: path.basename(file), message: e.message }); }
        finally { if (temporary) await fs.unlink(temporary).catch(() => {}); }
      }
      this.lastScan = new Date().toISOString();
    } finally {
      this.scanning = false; this.emit('change');
      if (this.scanAgain) { this.scanAgain = false; await this.scan(); }
    }
  }
  async persist() {
    await fs.writeFile(`${this.dbPath}.tmp`, Buffer.from(this.db.export()));
    await fs.rename(`${this.dbPath}.tmp`, this.dbPath);
  }
  catalog() {
    const grouped = new Map();
    for (const { artifact, ...v } of this.versions) {
      if (!grouped.has(v.packageName)) grouped.set(v.packageName, []);
      grouped.get(v.packageName).push(v);
    }
    return [...grouped].map(([packageName, versions]) => {
      versions.sort((a,b) => compareCodes(a.versionCode,b.versionCode));
      return { packageName, title: versions[0].title, icon: versions[0].icon, versions };
    }).sort((a,b) => a.title.localeCompare(b.title));
  }
  snapshot() { return { apps: this.catalog(), folder: this.folder, errors: this.errors, scanning: this.scanning, lastScan: this.lastScan }; }
  async close() { clearTimeout(this.debounce); await this.watcher?.close(); this.db?.close(); }
}
module.exports = { Library, hashFile, compareCodes };
