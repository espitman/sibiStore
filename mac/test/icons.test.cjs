const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs/promises');
const path = require('node:path');
const os = require('node:os');
const {parseTree,parseResources,color,ICON_REVISION} = require('../server/icons.cjs');
const {parseBadging} = require('../server/apk.cjs');
const {Library} = require('../server/library.cjs');

test('keeps vector/adaptive icon entries and decodes compiled attributes',()=>{
  assert.equal(parseBadging("package: name='test' versionCode='1' versionName='1'\napplication-icon-65534:'res/icon.xml'\n").iconPath,'res/icon.xml');
  const tree=parseTree('  E: vector (line=1)\n    A: http://schemas.android.com/apk/res/android:viewportWidth(0x01)=108\n      E: path (line=2)\n        A: http://schemas.android.com/apk/res/android:pathData(0x02)="M0,0 L10,10" (Raw: "M0,0 L10,10")\n');
  assert.equal(tree.attrs.viewportWidth,'108');
  assert.equal(tree.children[0].attrs.pathData,'M0,0 L10,10');
  assert.equal(color('#8033aabb'),'#33aabb80');
  assert.throws(()=>color('url(https://example.com)'),/Unsupported/);
  const resources=parseResources('    resource 0x7f010001 drawable/icon\n      () (file) res/icon.xml type=XML\n      (xxxhdpi-v4) (file) res/icon.png type=PNG\n');
  assert.equal(resources.get('0x7f010001').length,2);
});

test('repairs missing cached icons without importing another version or altering APK identity',async()=>{
  const temp=await fs.mkdtemp(path.join(os.tmpdir(),'sibi-icon-migration-'));
  let library;
  try {
    const folder=path.join(temp,'input'); await fs.mkdir(folder);
    await fs.writeFile(path.join(folder,'app.apk'),'fixture');
    const metadata={packageName:'test',title:'Test',versionCode:'1',versionName:'1',abis:[],certificates:['cert'],minSdk:26,tv:false,icon:null};
    library=await new Library({folder,dataDir:path.join(temp,'data'),inspect:async()=>metadata}).init();
    const original={...library.versions[0]}; await library.close();
    library=await new Library({folder,dataDir:path.join(temp,'data'),inspect:async()=>({...metadata,icon:'data:image/png;base64,example',iconRevision:ICON_REVISION})}).init();
    assert.equal(library.versions.length,1);
    assert.equal(library.catalog()[0].icon,'data:image/png;base64,example');
    for(const key of ['sha256','artifact','addedAt','versionCode','downloadUrl']) assert.equal(library.versions[0][key],original[key]);
    await library.close();
    library=await new Library({folder,dataDir:path.join(temp,'data'),inspect:async()=>{throw new Error('Should not re-inspect repaired icons')}}).init();
    assert.equal(library.catalog()[0].icon,'data:image/png;base64,example');
    assert.deepEqual(library.errors,[]);
  } finally {await library?.close();await fs.rm(temp,{recursive:true,force:true});}
});
