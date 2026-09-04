const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs/promises');
const path = require('node:path');
const os = require('node:os');
const { Library, hashFile, compareCodes } = require('../server/library.cjs');
const { parseBadging } = require('../server/apk.cjs');
const { createServer, parseRange } = require('../server/http.cjs');

test('Android manifest parsing and 64-bit version ordering', () => {
  const m = parseBadging("package: name='com.sibi.player' versionCode='42' versionName='2.4.0'\nsdkVersion:'26'\napplication-label:'Sibi Player'\nnative-code: 'arm64-v8a' 'armeabi-v7a'\nleanback-launchable-activity: name='Main'\n");
  assert.equal(m.packageName,'com.sibi.player'); assert.equal(m.tv,true); assert.deepEqual(m.abis,['arm64-v8a','armeabi-v7a']);
  assert.equal(compareCodes('9007199254740993','9007199254740992'),-1);
  assert.throws(() => parseBadging("package: name='x' versionCode='1' versionName='1' split='config.arm64'"), /Split APK/);
});
test('range parsing rejects invalid/multiple ranges', () => {
  assert.deepEqual(parseRange('bytes=5-',10),{ start:5,end:9 });
  assert.deepEqual(parseRange('bytes=-3',10),{ start:7,end:9 });
  assert.equal(parseRange('bytes=10-',10),null); assert.equal(parseRange('bytes=0-1,3-4',10),null);
});
test('library persists immutable verified files, rejects conflicts, serves resumable bytes and ETags', async t => {
  const root = await fs.mkdtemp(path.join(os.tmpdir(),'sibi-test-')); const folder = path.join(root,'input');
  await fs.mkdir(folder); await fs.writeFile(path.join(folder,'one.apk'),'0123456789');
  const inspect = async file => {
    const bytes = await fs.readFile(file,'utf8');
    if (bytes === 'broken') throw new Error('APK signature invalid');
    return { packageName:'com.sibi.test',title:'Test',versionCode:bytes === 'update' ? '2' : '1',versionName:'1.0',certificates:bytes === 'other-signer' ? ['other'] : ['sig'],minSdk:26,abis:[],tv:true };
  };
  let library = await new Library({folder,dataDir:path.join(root,'data'),inspect}).init();
  const http = await createServer({library,serverId:'test-server',port:0,host:'127.0.0.1',advertise:false});
  t.after(async () => { await http.close(); await library.close(); await fs.rm(root,{recursive:true,force:true}); });
  assert.equal(library.catalog().length,1);
  const catalog = await http.server.inject('/api/v1/catalog'); const etag = catalog.headers.etag;
  assert.equal((await http.server.inject({url:'/api/v1/catalog',headers:{'if-none-match':etag}})).statusCode,304);
  const version = library.versions[0];
  const partial = await http.server.inject({url:version.downloadUrl,headers:{range:'bytes=3-6'}});
  assert.equal(partial.statusCode,206); assert.equal(partial.body,'3456'); assert.equal(partial.headers['content-range'],'bytes 3-6/10');
  assert.equal((await http.server.inject({url:version.downloadUrl,headers:{range:'bytes=15-'}})).statusCode,416);
  await fs.unlink(path.join(folder,'one.apk'));
  assert.equal((await http.server.inject(version.downloadUrl)).body,'0123456789');
  assert.equal(await hashFile(version.artifact),version.sha256);
  await fs.writeFile(path.join(folder,'new.apk'),'update'); await library.scan(); assert.equal(library.catalog()[0].versions[0].versionCode,'2');
  await fs.writeFile(path.join(folder,'conflict.apk'),'different'); await library.scan(); assert.match(library.errors[0].message,/Version conflict/);
  await fs.writeFile(path.join(folder,'bad.apk'),'broken'); await library.scan(); assert.ok(library.errors.some(e=>/signature invalid/.test(e.message)));
  assert.equal(library.versions.length,2);
  await library.close();
  library = await new Library({folder,dataDir:path.join(root,'data'),inspect}).init(); assert.equal(library.versions.length,2);
});
