const { _electron: electron } = require('playwright');
const fs = require('node:fs/promises');
const path = require('node:path');
const os = require('node:os');
const assert = require('node:assert/strict');
(async () => {
  const temp = await fs.mkdtemp(path.join(os.tmpdir(), 'sibi-visual-'));
  const client = await electron.launch({ args: ['.', '--design-preview'], env: { ...process.env, SIBI_DATA_DIR: temp }, timeout: 60000 });
  try {
    const page = await client.firstWindow(); const errors = []; page.on('pageerror', e => errors.push(e.message));
    await page.getByRole('heading',{name:'App library'}).waitFor();
    await page.getByRole('heading',{name:'Version history'}).waitFor();
    await fs.mkdir('test-results',{recursive:true});
    const selected = page.locator('tbody tr.selected');
    const selectionBackground = await selected.evaluate(el => getComputedStyle(el).backgroundImage);
    await selected.hover();
    assert.notEqual(selectionBackground,'none');
    assert.equal(await selected.evaluate(el => getComputedStyle(el).backgroundImage),selectionBackground,'Selected gold highlight must remain visible on hover');
    await page.screenshot({path:'test-results/mac-library.png'});
    await page.getByRole('textbox',{name:'Search apps'}).fill('VLC');
    assert.equal(await page.locator('tbody tr').count(),1);
    await page.getByRole('textbox',{name:'Search apps'}).fill('no-such-app');
    await page.getByText('No matching apps').waitFor();
    await page.getByRole('button',{name:'Settings',exact:true}).click();
    await page.getByRole('heading',{name:'Local server'}).waitFor();
    await page.screenshot({path:'test-results/mac-settings.png'});
    await page.getByRole('button',{name:'Stop server',exact:true}).click();
    await page.getByRole('button',{name:'Start server',exact:true}).waitFor();
    await page.getByRole('button',{name:'Start server',exact:true}).click();
    await page.getByRole('button',{name:'Stop server',exact:true}).waitFor();
    assert.deepEqual(errors,[]);
    console.log('Electron UI: search, empty results, selection, settings and server stop/start passed.');
  } finally { await client.close(); await fs.rm(temp,{recursive:true,force:true}); }
})().catch(e=>{console.error(e);process.exitCode=1;});
