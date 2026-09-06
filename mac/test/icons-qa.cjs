const { app } = require('electron');
const fs = require('node:fs/promises');
const path = require('node:path');
const {inspectApk} = require('../server/apk.cjs');
app.whenReady().then(async()=>{
  const [folder,output] = process.argv.slice(2);
  if(!folder || !output) throw new Error('Pass an APK folder and an output directory');
  await fs.mkdir(output,{recursive:true});
  let failures=0;
  for(const name of (await fs.readdir(folder)).filter(n=>n.endsWith('.apk'))) {
    const meta=await inspectApk(path.join(folder,name));
    if(!meta.icon) {console.error(name,meta.iconError);failures++;continue;}
    const [,format,data]=meta.icon.match(/^data:image\/([^;]+);base64,(.*)$/);
    await fs.writeFile(path.join(output,`${meta.packageName}.${format}`),Buffer.from(data,'base64'));
    console.log(name,'icon OK',format);
  }
  app.exit(failures ? 1 : 0);
}).catch(e=>{console.error(e);app.exit(1)});
