const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs/promises');
const path = require('node:path');
const os = require('node:os');
const {Library,hashFile} = require('../server/library.cjs');
const {createServer} = require('../server/http.cjs');
const inspect = async file => ({packageName:'source.test',title:'Source',versionCode:(await fs.readFile(file,'utf8')).startsWith('B')?'2':'1',versionName:'1',abis:[],certificates:['cert'],minSdk:26,tv:false,icon:'data:image/png;base64,test'});
async function setup(t) {
  const root=await fs.mkdtemp(path.join(os.tmpdir(),'sibi-source-'));
  const folder=path.join(root,'input'),dataDir=path.join(root,'data');
  await fs.mkdir(folder);const source=path.join(folder,'app.apk');await fs.writeFile(source,'AAAA');
  const library=await new Library({folder,dataDir,inspect}).init();
  t.after(async()=>{await library.close();await fs.rm(root,{recursive:true,force:true});});
  return {root,folder,dataDir,source,library};
}
test('changed or deleted source never serves bytes for the old hash, even before a watcher scan',async t=>{
  const {source,library}=await setup(t);await library.watcher.close();
  const http=await createServer({library,serverId:'qa',port:0,host:'127.0.0.1',advertise:false});
  t.after(()=>http.close());
  const old=library.versions[0];
  await fs.writeFile(source,'BBBB');
  assert.equal((await http.server.inject({url:old.downloadUrl,headers:{range:'bytes=1-'}})).statusCode,409);
  await library.scan();
  const current=library.versions[0];assert.equal(current.versionCode,'2');
  assert.equal((await http.server.inject(old.downloadUrl)).statusCode,404);
  const partial=await http.server.inject({url:current.downloadUrl,headers:{range:'bytes=1-2'}});
  assert.equal(partial.statusCode,206);assert.equal(partial.body,'BB');
  await fs.unlink(source);
  assert.equal((await http.server.inject(current.downloadUrl)).statusCode,404);
  await library.scan();assert.deepEqual(library.catalog(),[]);
});
test('watcher removes deleted APKs automatically and renames point to the current source',async t=>{
  const {folder,source,library}=await setup(t);
  await new Promise(resolve=>library.watcher.once('ready',resolve));
  const moved=path.join(folder,'renamed.apk');await fs.rename(source,moved);await library.scan();
  assert.equal(library.versions[0].artifact,moved);
  await fs.unlink(moved);
  const deadline=Date.now()+8000;
  while(library.versions.length && Date.now()<deadline) await new Promise(r=>setTimeout(r,50));
  assert.deepEqual(library.catalog(),[]);
});
test('migration removes only legacy copies and drops cache-only entries',async t=>{
  const {source,folder,dataDir,library}=await setup(t);await library.watcher.close();
  const hash=await hashFile(source);const legacy=path.join(dataDir,'artifacts');await fs.mkdir(legacy);
  const copy=path.join(legacy,`${hash}.apk`);await fs.copyFile(source,copy);
  const orphan=path.join(legacy,`${'f'.repeat(64)}.apk`);await fs.writeFile(orphan,'old removed source');
  const note=path.join(legacy,'keep.txt');await fs.writeFile(note,'unrelated file');
  const old={...library.versions[0],artifact:copy};
  library.db.run('UPDATE versions SET metadata = ? WHERE sha = ?',[JSON.stringify(old),hash]);
  library.db.run('INSERT INTO versions VALUES (?, ?)', ['f'.repeat(64),JSON.stringify({...old,sha256:'f'.repeat(64),versionCode:'9',artifact:orphan})]);
  await library.persist();await library.close();
  const reopened=await new Library({folder,dataDir,inspect}).init();
  try {
    assert.equal(reopened.versions.length,1);assert.equal(reopened.versions[0].artifact,source);
    assert.equal(await hashFile(source),hash);
    assert.deepEqual(await fs.readdir(legacy),['keep.txt']);
    assert.equal(await fs.readFile(note,'utf8'),'unrelated file');
  } finally {await reopened.close();}
});
