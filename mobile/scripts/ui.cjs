const { execFileSync } = require('node:child_process');
const path=require('node:path');
const [serial,action,text]=process.argv.slice(2);
const script=path.resolve(__dirname,'device.sh');
const call=(args)=>execFileSync('bash',[script,serial,...args],{encoding:'utf8',maxBuffer:8*1024*1024});
const xml=call(['ui']);
const nodes=[...xml.matchAll(/<node\b([^>]+)>?/g)].map(m=>Object.fromEntries([...m[1].matchAll(/([\w-]+)="([^"]*)"/g)].map(a=>[a[1],a[2]])));
if(action==='list') console.log(nodes.filter(n=>n.text||n['content-desc']).map(n=>({text:n.text,description:n['content-desc'],bounds:n.bounds,focused:n.focused})))
else if(action==='tap') {
  const node=nodes.find(n=>n.text===text||n['content-desc']===text);
  if(!node) throw Error(`No visible control: ${text}`);
  const box=node.bounds.match(/\d+/g).map(Number);
  console.log(`Tap ${text} at ${node.bounds}`);
  call(['shell','input','tap',String(Math.round((box[0]+box[2])/2)),String(Math.round((box[1]+box[3])/2))]);
} else throw Error('Use list or tap <exact text>');
