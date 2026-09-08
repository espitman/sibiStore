const test=require('node:test'); const assert=require('node:assert/strict');
const fs=require('node:fs/promises'); const path=require('node:path'); const os=require('node:os');
const {FileTransfers}=require('../server/file-transfers.cjs'); const {createServer}=require('../server/http.cjs');
async function setup(t) {
  const dir=await fs.mkdtemp(path.join(os.tmpdir(),'sibi-files-'));
  const queue=await new FileTransfers({stateFile:path.join(dir,'queue.json')}).init();
  const http=await createServer({library:{catalog:()=>[]},serverId:'mac',port:0,host:'127.0.0.1',advertise:false,fileTransfers:queue});
  t.after(async()=>{await http.close();await fs.rm(dir,{recursive:true,force:true});});
  const one={'x-device-id':'device-one','x-device-token':'1'.repeat(36),'x-device-capabilities':'files-v1','x-device-name':'Quest','x-device-platform':'vr'};
  const two={...one,'x-device-id':'device-two','x-device-token':'2'.repeat(36),'x-device-name':'Phone','x-device-platform':'phone'};
  for(const headers of [one,two])await http.server.inject({url:'/api/v1/catalog',headers});
  const a=path.join(dir,'a.txt'),b=path.join(dir,'empty.bin');await fs.writeFile(a,'abcdefghij');await fs.writeFile(b,'');
  const staged=await queue.stage([a,b]); await queue.enqueue(staged.map(f=>f.id),['device-one','device-two'],http.deviceRegistry);
  return {dir,queue,http,one,two,a};
}
test('multiple files and devices, credentials, ranges, and saved acknowledgements',async t=>{
  const {queue,http,one,two}=await setup(t);
  assert.equal(queue.snapshot().length,4); assert.ok(queue.snapshot().every(j=>!j.source && !j.credential));
  const list=async headers=>(await http.server.inject({url:'/api/v1/inbox',headers})).json().files;
  assert.equal((await list(one)).length,2); assert.equal((await list(two)).length,2); assert.equal((await list({...one,'x-device-token':'wrong'})).length,0);
  const file=(await list(one)).find(f=>f.name==='a.txt'); assert.ok(file && file.sha256.length===64);
  assert.equal((await http.server.inject({url:file.downloadUrl,headers:two})).statusCode,404);
  const partial=await http.server.inject({url:file.downloadUrl,headers:{...one,range:'bytes=4-'}});
  assert.equal(partial.statusCode,206); assert.equal(partial.body,'efghij');
  assert.equal(queue.snapshot().find(j=>j.id===file.id).status,'receiving');
  const status=body=>http.server.inject({method:'POST',url:`/api/v1/inbox/${file.id}/status`,headers:one,payload:body});
  assert.equal((await status({status:'completed',bytes:9})).statusCode,400);
  assert.equal((await status({status:'completed',bytes:10})).statusCode,200);
  assert.equal((await list(one)).length,1);assert.equal((await list(two)).length,2);
  const empty=(await list(one))[0]; assert.equal((await http.server.inject({url:empty.downloadUrl,headers:one})).rawPayload.length,0);
});
test('queue survives restart; cancellation and source changes cannot deliver',async t=>{
  const {dir,queue,http,one,a}=await setup(t);
  const restored=await new FileTransfers({stateFile:path.join(dir,'queue.json')}).init(); assert.equal(restored.snapshot().length,4);
  const job=queue.snapshot().find(j=>j.deviceId==='device-one' && j.name==='a.txt');
  await fs.writeFile(a,'changed');
  assert.equal((await http.server.inject({url:`/api/v1/inbox/${job.id}/file`,headers:one})).statusCode,409);
  assert.equal(queue.snapshot().find(j=>j.id===job.id).status,'failed');
  const pending=queue.snapshot().find(j=>j.deviceId==='device-one' && j.name==='empty.bin');
  await queue.action(pending.id,'cancel');
  assert.equal((await http.server.inject({url:`/api/v1/inbox/${pending.id}/file`,headers:one})).statusCode,404);
  assert.equal((await http.server.inject({method:'POST',url:`/api/v1/inbox/${pending.id}/status`,headers:one,payload:{status:'completed',bytes:0}})).statusCode,409);
  assert.deepEqual((await fs.readdir(dir)).sort(),['a.txt','empty.bin','queue.json']);
});
test('reject folders and clients without file support; do not expose credential in device snapshot',async t=>{
  const {queue,http,dir,one}=await setup(t);
  await assert.rejects(queue.stage([dir]),/Only files/);
  await http.server.inject({url:'/api/v1/catalog',headers:{'x-device-id':'legacy-client'}});
  const staged=await queue.stage([path.join(dir,'a.txt')]);
  await assert.rejects(queue.enqueue(staged.map(f=>f.id),['legacy-client'],http.deviceRegistry),/update/);
  await http.server.inject({url:'/api/v1/catalog',headers:{...one,'x-device-token':'wrong'}});
  assert.equal(http.deviceRegistry.credential(one['x-device-id']),require('node:crypto').createHash('sha256').update(one['x-device-token']).digest('hex'));
  assert.ok(http.devices().every(d=>!d.token && !d.credential));
});
