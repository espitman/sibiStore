const fs = require('node:fs/promises');
const { createReadStream } = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const { EventEmitter } = require('node:events');
const initSqlJs = require('sql.js');
const chokidar = require('chokidar');
const { inspectApk } = require('./apk.cjs');
const { ICON_REVISION } = require('./icons.cjs');

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
    await fs.mkdir(this.dataDir, { recursive: true });
    const SQL = await initSqlJs();
    this.dbPath = path.join(this.dataDir, 'library.sqlite');
    this.db = new SQL.Database(await fs.readFile(this.dbPath).catch(() => undefined));
    this.db.run('CREATE TABLE IF NOT EXISTS versions (sha TEXT PRIMARY KEY, metadata TEXT NOT NULL)');
    const rows = this.db.exec('SELECT metadata FROM versions')[0]?.values || [];
    this.versions = rows.map(r => JSON.parse(r[0]));
    this.db.run('CREATE TABLE IF NOT EXISTS signers (package TEXT PRIMARY KEY, certificates TEXT NOT NULL)');
    for (const v of this.versions) this.db.run('INSERT OR IGNORE INTO signers VALUES (?, ?)', [v.packageName,JSON.stringify(v.certificates)]);
    await this.scan();
    await this.removeLegacyCopies();
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
    if (this.scanPromise) { this.scanAgain = true; return this.scanPromise; }
    this.scanPromise = this.performScan();
    try { await this.scanPromise; } finally { this.scanPromise = null; }
  }
  async performScan() {
    this.scanning = true; this.errors = []; this.emit('change');
    try {
      const previous = new Map(this.versions.map(v => [v.sha256,v]));
      const next = [];
      const files = await this.files(this.folder).catch(e => { if(e.code === 'ENOENT') return []; throw e; });
      for (const file of files.sort()) {
        try {
          const before = await fs.stat(file);
          const sha256 = await hashFile(file);
          if (next.some(v => v.sha256 === sha256)) continue;
          const existing = previous.get(sha256);
          let meta = existing;
          if (!meta || (!meta.icon && meta.iconRevision !== ICON_REVISION)) meta = {...meta,...await this.inspect(file)};
          const after = await fs.stat(file);
          if (before.size !== after.size || before.mtimeMs !== after.mtimeMs || before.ctimeMs !== after.ctimeMs || (!existing && await hashFile(file) !== sha256)) {
            throw new Error('File is still changing. It will be checked again after copying finishes.');
          }
          const sameApp = next.filter(v => v.packageName === meta.packageName);
          if (sameApp.some(v => v.versionCode === meta.versionCode && JSON.stringify(v.abis) === JSON.stringify(meta.abis))) throw new Error('Version conflict: this version code already exists with different content');
          const pin = this.db.exec('SELECT certificates FROM signers WHERE package = ?', [meta.packageName])[0]?.values[0]?.[0];
          if (pin && pin !== JSON.stringify(meta.certificates)) throw new Error('Signing certificate differs from the versions already in this library');
          this.db.run('INSERT OR IGNORE INTO signers VALUES (?, ?)', [meta.packageName,JSON.stringify(meta.certificates)]);
          next.push({...meta,sha256,size:after.size,filename:path.basename(file),addedAt:existing?.addedAt || new Date().toISOString(),artifact:file,downloadUrl:`/artifacts/${sha256}.apk`});
        } catch (e) { this.errors.push({file:path.basename(file),message:e.message}); }
      }
      this.db.run('BEGIN');
      try {
        this.db.run('DELETE FROM versions');
        for (const v of next) this.db.run('INSERT INTO versions VALUES (?, ?)', [v.sha256,JSON.stringify(v)]);
        this.db.run('COMMIT');
      } catch(e) {this.db.run('ROLLBACK');throw e;}
      await this.persist();
      this.versions = next;
      this.lastScan = new Date().toISOString();
    } finally {
      this.scanning = false; this.emit('change');
      if (this.scanAgain) { this.scanAgain = false; await this.performScan(); }
    }
  }
  async removeLegacyCopies() {
    const legacy = path.join(this.dataDir,'artifacts');
    const stat = await fs.lstat(legacy).catch(e => {if(e.code==='ENOENT') return null; throw e;});
    if (!stat?.isDirectory() || stat.isSymbolicLink()) return;
    const real = await fs.realpath(legacy);
    const folder = await fs.realpath(this.folder).catch(()=>path.resolve(this.folder));
    // Never remove files if the user selected the old cache itself as the library.
    const within = (parent, child) => {const relative=path.relative(parent,child);return !relative || (!relative.startsWith('..'+path.sep) && relative!=='..' && !path.isAbsolute(relative));};
    if (within(real,folder) || within(folder,real)) return;
    for (const entry of await fs.readdir(legacy,{withFileTypes:true})) {
      if (entry.isFile() && /^(?:[a-f0-9]{64}\.apk|[a-f0-9-]{36}\.partial)$/.test(entry.name)) await fs.unlink(path.join(legacy,entry.name));
    }
    await fs.rmdir(legacy).catch(e=>{if(e.code!=='ENOTEMPTY') throw e;});
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
  async close() { clearTimeout(this.debounce); await this.watcher?.close(); await this.scanPromise; this.db?.close(); }
}
module.exports = { Library, hashFile, compareCodes };
