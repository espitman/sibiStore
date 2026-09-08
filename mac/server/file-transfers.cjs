const fs = require('node:fs/promises');
const crypto = require('node:crypto');
const path = require('node:path');
const { constants } = require('node:fs');
const tokenHash = token => crypto.createHash('sha256').update(String(token || '')).digest('hex');
class FileTransfers {
  constructor({ stateFile, onChange = () => {} } = {}) { this.stateFile = stateFile; this.onChange = onChange; this.jobs = []; this.staged = new Map(); this.streams = new Map(); this.writes = Promise.resolve(); }
  async init() {
    if (this.stateFile) {
      try { this.jobs = JSON.parse(await fs.readFile(this.stateFile, 'utf8')); }
      catch (error) { if (error.code !== 'ENOENT') throw error; }
      if (!Array.isArray(this.jobs)) throw new Error('Invalid file transfer queue');
      for (const job of this.jobs) if (job.status === 'receiving') job.status = 'queued';
    }
    return this;
  }
  snapshot() { return this.jobs.map(({ source, credential, ...job }) => job); }
  async save() {
    const json = JSON.stringify(this.jobs);
    this.writes = this.writes.catch(() => {}).then(async () => {
      if (!this.stateFile) return;
      await fs.mkdir(path.dirname(this.stateFile), { recursive: true });
      await fs.writeFile(this.stateFile + '.tmp', json, { mode: 0o600 });
      await fs.rename(this.stateFile + '.tmp', this.stateFile);
    });
    await this.writes; this.onChange();
  }
  async stage(paths) {
    if (this.preparing) throw new Error('Wait for file preparation to finish');
    if (!Array.isArray(paths) || !paths.length || paths.length > 100) throw new Error('Choose between 1 and 100 files');
    const entries = [];
    for (const input of new Set(paths)) {
      if (typeof input !== 'string' || !path.isAbsolute(input)) throw new Error('Invalid file path');
      const source = await fs.realpath(input); const stat = await fs.stat(source);
      if (!stat.isFile()) throw new Error('Only files can be sent. Choose the files inside folders.');
      const item = { id: crypto.randomUUID(), name: path.basename(input), size: stat.size, source };
      entries.push(item);
    }
    this.staged.clear();
    for (const entry of entries) this.staged.set(entry.id, entry);
    return entries.map(({source, ...entry}) => entry);
  }
  async enqueue(fileIds, deviceIds, devices) {
    if (this.preparing) throw new Error('Files are already being prepared');
    this.preparing = true;
    try { return await this.prepareBatch(fileIds, deviceIds, devices); }
    finally { this.preparing = false; }
  }
  async prepareBatch(fileIds, deviceIds, devices) {
    if (!Array.isArray(fileIds) || !fileIds.length || !Array.isArray(deviceIds) || !deviceIds.length) throw new Error('Select files and devices');
    if (fileIds.length * deviceIds.length > 1000) throw new Error('Send at most 1,000 file/device pairs at a time');
    const selected = [...new Set(deviceIds)].map(id => {
      const device = devices.snapshot().find(d => d.id === id && d.canReceiveFiles);
      if (!device || !devices.credential(id)) throw new Error('A selected device disconnected or needs a client update');
      return { ...device, credential: devices.credential(id) };
    });
    const sources = [];
    for (const id of new Set(fileIds)) {
      const file = this.staged.get(id); if (!file) throw new Error('Choose the files again');
      const handle = await fs.open(file.source, constants.O_RDONLY | constants.O_NOFOLLOW);
      try {
        const before = await handle.stat(); if (!before.isFile()) throw new Error('Source is not a file');
        const hash = crypto.createHash('sha256');
        for await (const chunk of handle.createReadStream({autoClose: false})) hash.update(chunk);
        const after = await handle.stat();
        if (before.size !== after.size || before.mtimeMs !== after.mtimeMs || before.ctimeMs !== after.ctimeMs) throw new Error('A source file changed. Choose it again.');
        sources.push({ name: file.name, size: after.size, sha256: hash.digest('hex'), source: { path: file.source, size: after.size, mtime: after.mtimeMs, ctime: after.ctimeMs, ino: after.ino, dev: after.dev } });
      } finally { await handle.close(); }
    }
    if (this.jobs.filter(j => ['queued','receiving'].includes(j.status)).length + sources.length * selected.length > 1000) throw new Error('The transfer queue is full');
    this.jobs = this.jobs.filter(j => ['queued','receiving'].includes(j.status)).concat(this.jobs.filter(j => !['queued','receiving'].includes(j.status)).slice(0,200));
    const batch = [];
    for (const file of sources) for (const device of selected) batch.push({ ...file, id: crypto.randomUUID(), deviceId: device.id, device: device.name, credential: device.credential, status: 'queued', bytes: 0, error: '', startedAt: new Date().toISOString() });
    this.jobs.unshift(...batch); await this.save(); this.staged.clear();
  }
  authorized(job, request) { return job.deviceId === request.headers['x-device-id'] && job.credential === tokenHash(request.headers['x-device-token']); }
  inbox(request) { return this.jobs.filter(j => ['queued','receiving'].includes(j.status) && this.authorized(j,request)).map(j => ({ id:j.id, name:j.name, size:j.size, sha256:j.sha256, downloadUrl:`/api/v1/inbox/${j.id}/file` })); }
  async action(id, action) {
    const job = this.jobs.find(j => j.id === id); if (!job) throw new Error('Transfer not found');
    if (action === 'cancel' && ['queued','receiving','failed'].includes(job.status)) { job.status='cancelled'; this.streams.get(id)?.destroy(); }
    else if (action === 'retry' && job.status === 'failed') { job.status='queued'; job.error=''; }
    else throw new Error('This transfer cannot be changed');
    await this.save();
  }
  routes(server, serverId, parseRange) {
    server.get('/api/v1/inbox', async request => ({serverId, files:this.inbox(request)}));
    server.post('/api/v1/inbox/:id/status', async (request, reply) => {
      const job=this.jobs.find(j=>j.id===request.params.id);
      if (!job || !this.authorized(job,request)) return reply.code(404).send({error:'Transfer not found'});
      const {status,bytes,error}=request.body || {};
      if (!['receiving','completed','failed'].includes(status) || !Number.isSafeInteger(bytes) || bytes<0 || bytes>job.size || (status==='completed' && bytes!==job.size)) return reply.code(400).send({error:'Invalid transfer status'});
      if (job.status==='cancelled') return reply.code(409).send({error:'Transfer cancelled'});
      if (job.status==='completed') return {ok:true};
      job.status=status; job.bytes=bytes; job.error=typeof error==='string'?error.slice(0,300):'';
      await this.save(); return {ok:true};
    });
    server.get('/api/v1/inbox/:id/file', async (request,reply) => {
      const job=this.jobs.find(j=>j.id===request.params.id);
      if (!job || !this.authorized(job,request) || !['queued','receiving'].includes(job.status)) return reply.code(404).send({error:'Transfer unavailable'});
      let file;
      try {
        file=await fs.open(job.source.path,constants.O_RDONLY | constants.O_NOFOLLOW);
        const st=await file.stat(); const src=job.source;
        if (!st.isFile() || st.size!==src.size || st.mtimeMs!==src.mtime || st.ctimeMs!==src.ctime || st.ino!==src.ino || st.dev!==src.dev) throw new Error('Source changed');
      } catch(error) {
        await file?.close(); job.status='failed'; job.error='Source file was removed or changed. Choose it again.'; await this.save();
        return reply.code(409).send({error:job.error});
      }
      const etag=`"${job.sha256}"`; let start=0,end=job.size-1;
      reply.header('ETag',etag).header('Accept-Ranges','bytes').header('Cache-Control','no-store').header('Content-Type','application/octet-stream');
      if(request.headers.range && (!request.headers['if-range'] || request.headers['if-range']===etag)) {
        const range=parseRange(request.headers.range,job.size);
        if(!range){await file.close();return reply.code(416).header('Content-Range',`bytes */${job.size}`).send();}
        ({start,end}=range);reply.code(206).header('Content-Range',`bytes ${start}-${end}/${job.size}`);
      }
      reply.header('Content-Length',Math.max(0,end-start+1));
      if(request.method==='HEAD' || !job.size){await file.close();return reply.send(Buffer.alloc(0));}
      const stream=file.createReadStream({start,end,autoClose:true});
      this.streams.get(job.id)?.destroy(); this.streams.set(job.id,stream);
      job.status='receiving'; job.bytes=start; this.onChange(); let last=0;
      stream.on('data',chunk=>{job.bytes=Math.min(job.size,job.bytes+chunk.length); if(Date.now()-last>500){last=Date.now();this.onChange();}});
      stream.on('error',()=>{});
      const cleanup=()=>{if(this.streams.get(job.id)===stream)this.streams.delete(job.id);};
      stream.on('close',cleanup);reply.raw.on('close',()=>{if(!reply.raw.writableFinished)stream.destroy();});
      return reply.send(stream);
    });
  }
  async close() { for(const stream of this.streams.values())stream.destroy(); await this.writes; }
}
module.exports={FileTransfers};
