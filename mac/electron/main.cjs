const { app, BrowserWindow, ipcMain, shell, dialog, Menu, Tray, nativeImage, clipboard } = require('electron');
const path = require('node:path');
const fs = require('node:fs/promises');
const crypto = require('node:crypto');
const { Library } = require('../server/library.cjs');
const { createServer } = require('../server/http.cjs');
let win, library, http, tray, config, quitting = false, serverError = null;
const demo = process.argv.includes('--design-preview');
const dataDir = process.env.SIBI_DATA_DIR || (demo ? path.join(app.getPath('temp'), 'sibi-store-design-preview') : app.getPath('userData'));
const configFile = path.join(dataDir, 'settings.json');
const snapshot = () => ({ ...library.snapshot(), serverId: config.serverId, running: !!http, port: http?.port || config.port,
  addresses: http?.addresses() || [], transfers: http?.transfers || [], serverError, discoveryError: http?.discoveryError(),
  openAtLogin: app.getLoginItemSettings().openAtLogin, sdk: config.sdk || '', preview: demo });
function notify() { if (win && !win.isDestroyed()) win.webContents.send('state', snapshot()); }
async function save() { await fs.writeFile(configFile, JSON.stringify(config, null, 2)); }
async function startServer() {
  serverError = null;
  try { http = await createServer({ library, serverId: config.serverId, port: config.port, advertise: !demo, host: demo ? '127.0.0.1' : '0.0.0.0', onChange: notify }); }
  catch (e) { serverError = e.message; }
  notify();
}
function window() {
  win = new BrowserWindow({ width: 1510, height: 940, minWidth: 1060, minHeight: 680, backgroundColor: '#050505', title: 'Sibi Store', titleBarStyle: 'hidden', trafficLightPosition: { x: 18, y: 20 },
    webPreferences: { preload: path.join(__dirname, 'preload.cjs'), nodeIntegration: false, contextIsolation: true, sandbox: true } });
  win.webContents.setWindowOpenHandler(() => ({ action: 'deny' }));
  win.webContents.on('will-navigate', e => e.preventDefault());
  if (process.argv.includes('--dev')) win.loadURL('http://127.0.0.1:5173');
  else win.loadFile(path.join(__dirname, '../dist/index.html'));
  win.on('close', e => { if (!quitting && !demo) { e.preventDefault(); win.hide(); } });
}
if (!app.requestSingleInstanceLock() && !demo) app.quit();
else app.whenReady().then(async () => {
  await fs.mkdir(dataDir, { recursive: true });
  config = await fs.readFile(configFile, 'utf8').then(JSON.parse).catch(() => ({ serverId: crypto.randomUUID(), folder: path.join(app.getPath('home'), 'SibiStore', 'APKs'), port: 8743 }));
  if (demo) { config.folder = path.join(dataDir, 'empty-input'); config.port = 0; }
  if (process.env.SIBI_LIBRARY_DIR) config.folder = process.env.SIBI_LIBRARY_DIR;
  if (process.env.SIBI_PORT) config.port = Number(process.env.SIBI_PORT);
  if (config.sdk) process.env.ANDROID_HOME = config.sdk;
  await save();
  library = await new Library({ folder: config.folder, dataDir }).init();
  library.on('change', notify);
  if (demo) library.versions = require('../server/demo.cjs').versions;
  await startServer();
  ipcMain.handle('snapshot', snapshot);
  ipcMain.handle('rescan', async () => { await library.scan(); return snapshot(); });
  ipcMain.handle('open-folder', () => shell.openPath(config.folder));
  ipcMain.handle('reveal', (_, hash) => { const v = library.versions.find(v => v.sha256 === hash); if (v?.artifact) shell.showItemInFolder(v.artifact); });
  ipcMain.handle('choose-folder', async () => {
    const result = await dialog.showOpenDialog(win, { properties: ['openDirectory', 'createDirectory'], defaultPath: config.folder });
    if (result.canceled) return;
    if (library.scanning) throw new Error('Wait for the current scan to finish');
    await http?.close(); http = null; await library.close();
    config.folder = result.filePaths[0]; await save();
    library = await new Library({ folder: config.folder, dataDir }).init(); library.on('change', notify); await startServer(); return snapshot();
  });
  ipcMain.handle('choose-sdk', async () => {
    const result = await dialog.showOpenDialog(win, { properties: ['openDirectory'], title: 'Choose Android SDK folder' });
    if (!result.canceled) { config.sdk = result.filePaths[0]; process.env.ANDROID_HOME = config.sdk; await save(); await library.scan(); notify(); }
  });
  ipcMain.handle('server', async (_, running) => { if (running && !http) await startServer(); else if (!running && http) { await http.close(); http = null; notify(); } });
  ipcMain.handle('login', (_, enabled) => { app.setLoginItemSettings({ openAtLogin: !!enabled }); notify(); });
  ipcMain.handle('copy-address', () => clipboard.writeText(http?.addresses()[0] || `http://localhost:${config.port}`));
  window();
  const svg = '<svg xmlns="http://www.w3.org/2000/svg" width="20" height="20"><rect x="3" y="6" width="14" height="12" rx="3" fill="none" stroke="black" stroke-width="1.8"/><path d="M7 7V5a3 3 0 0 1 6 0v2M7 11l3 3 4-4" fill="none" stroke="black" stroke-width="1.8"/></svg>';
  const icon = nativeImage.createFromDataURL(`data:image/svg+xml;base64,${Buffer.from(svg).toString('base64')}`); icon.setTemplateImage(true);
  tray = new Tray(icon); tray.setToolTip('Sibi Store');
  tray.setContextMenu(Menu.buildFromTemplate([{ label: 'Open Sibi Store', click: () => win.show() }, { label: 'Open APK folder', click: () => shell.openPath(config.folder) }, { type: 'separator' }, { label: 'Quit Sibi Store', click: () => app.quit() }]));
  app.on('activate', () => win.show()); app.on('second-instance', () => { win.show(); win.focus(); });
}).catch(e => { dialog.showErrorBox('Sibi Store could not start', e.message); app.quit(); });
app.on('before-quit', e => {
  if (quitting) return;
  e.preventDefault(); quitting = true;
  Promise.allSettled([http?.close(), library?.close()]).finally(() => app.quit());
});
