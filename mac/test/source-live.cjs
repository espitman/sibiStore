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
  const getCatalog=platform=>fetch(`${base}/api/v1/catalog${platform?`?platform=${platform}`:''}`).then(r=>{assert(r.ok);return r.json();});
  const [catalog,legacy,phone,tv,vr]=await Promise.all([getCatalog('all'),getCatalog(),getCatalog('phone'),getCatalog('tv'),getCatalog('vr')]);
  assert.equal(catalog.serverId,settings.serverId);
  assert.equal(catalog.apps.flatMap(a=>a.versions).length,versions.length);
  assert(versions.every(v=>typeof v.vr==='boolean' && v.classificationRevision===1));
  const hashes=value=>value.apps.flatMap(a=>a.versions).map(v=>v.sha256).sort();
  const expected=platform=>catalog.apps.flatMap(a=>a.versions).filter(v=>platform==='legacy'?v.platform!=='vr':v.platform===platform).map(v=>v.sha256).sort();
  assert.deepEqual(hashes(legacy),expected('legacy'));
  assert.deepEqual(hashes(phone),expected('phone'));
  assert.deepEqual(hashes(tv),expected('tv'));
  assert.deepEqual(hashes(vr),expected('vr'));
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
  const legacyFiles=await fs.readdir(path.join(dataDir,'artifacts')).catch(e=>{if(e.code==='ENOENT') return [];throw e;});
  assert(!legacyFiles.some(name=>/\.(apk|partial)$/.test(name)));
  console.log(`Verified ${versions.length} versions served directly from the selected folder; ${hashes(vr).length} classified as VR; platform catalogs, byte ranges, and no-copy storage are correct.`);
})().catch(e=>{console.error(e);process.exitCode=1;});
