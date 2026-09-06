const fs=require('node:fs/promises');
const path=require('node:path');
const os=require('node:os');
const assert=require('node:assert/strict');
const initSqlJs=require('sql.js');
const {hashFile}=require('../server/library.cjs');
(async()=>{
  const dataDir=process.argv[2] || path.join(os.homedir(),'Library/Application Support/sibi-store-server');
  const settings=JSON.parse(await fs.readFile(path.join(dataDir,'settings.json'),'utf8'));
  const root=await fs.realpath(settings.folder);
  const SQL=await initSqlJs();const db=new SQL.Database(await fs.readFile(path.join(dataDir,'library.sqlite')));
  const versions=(db.exec('SELECT metadata FROM versions')[0]?.values || []).map(row=>JSON.parse(row[0]));db.close();
  const base=`http://127.0.0.1:${settings.port}`;
  const catalog=await fetch(`${base}/api/v1/catalog`).then(r=>r.json());
  assert.equal(catalog.serverId,settings.serverId);
  assert.equal(catalog.apps.flatMap(a=>a.versions).length,versions.length);
  for(const v of versions) {
    const relative=path.relative(root,await fs.realpath(v.artifact));
    assert(relative && relative!=='..' && !relative.startsWith('..'+path.sep) && !path.isAbsolute(relative));
    assert.equal(await hashFile(v.artifact),v.sha256);
    const response=await fetch(`${base}${v.downloadUrl}`,{headers:{Range:'bytes=0-15'}});
    assert.equal(response.status,206);
    const handle=await fs.open(v.artifact,'r');const bytes=Buffer.alloc(16);
    try {await handle.read(bytes,0,16,0);} finally {await handle.close();}
    assert.deepEqual(Buffer.from(await response.arrayBuffer()),bytes);
  }
  const legacy=await fs.readdir(path.join(dataDir,'artifacts')).catch(e=>{if(e.code==='ENOENT') return [];throw e;});
  assert(!legacy.some(name=>/\.(apk|partial)$/.test(name)));
  console.log(`Verified ${versions.length} versions served directly from the selected folder; byte ranges match originals; no internal APK copies.`);
})().catch(e=>{console.error(e);process.exitCode=1;});
