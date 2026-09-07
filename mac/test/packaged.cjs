const { _electron: electron } = require('playwright');
const fs = require('node:fs/promises');
const path = require('node:path');
const os = require('node:os');
(async () => {
  const temp = await fs.mkdtemp(path.join(os.tmpdir(),'sibi-packaged-'));
  const executablePath=path.resolve(`release/mac-${process.arch}/Sibi Store.app/Contents/MacOS/Sibi Store`);
  const client=await electron.launch({executablePath,args:['--design-preview'],env:{...process.env,SIBI_DATA_DIR:temp},timeout:60000});
  try {
    const page=await client.firstWindow();
    await page.getByRole('heading',{name:'App library'}).waitFor();
    await page.getByRole('heading',{name:'Version history'}).waitFor();
    if (process.argv[2]) {
      const metadata = await client.evaluate(async ({ app }, apk) => {
        return process.mainModule.require(`${app.getAppPath()}/server/apk.cjs`).inspectApk(apk);
      }, path.resolve(process.argv[2]));
      if (!metadata.certificates?.length || !metadata.packageName || typeof metadata.vr !== 'boolean') throw new Error('Packaged APK verification failed');
      console.log(`Packaged native Java verified APK: ${metadata.packageName} ${metadata.versionName}`);
    }
    console.log('Packaged macOS app launched; bundled SQLite/runtime and IPC are working.');
  } finally {await client.close();await fs.rm(temp,{recursive:true,force:true});}
})().catch(e=>{console.error(e);process.exitCode=1;});
