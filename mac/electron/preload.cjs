const { contextBridge, ipcRenderer } = require('electron');
contextBridge.exposeInMainWorld('sibi', {
  snapshot: () => ipcRenderer.invoke('snapshot'),
  rescan: () => ipcRenderer.invoke('rescan'),
  openFolder: () => ipcRenderer.invoke('open-folder'),
  reveal: hash => ipcRenderer.invoke('reveal', hash),
  chooseFolder: () => ipcRenderer.invoke('choose-folder'),
  chooseSdk: () => ipcRenderer.invoke('choose-sdk'),
  server: running => ipcRenderer.invoke('server', running),
  login: enabled => ipcRenderer.invoke('login', enabled),
  copyAddress: () => ipcRenderer.invoke('copy-address'),
  onChange: fn => { const listener = (_, state) => fn(state); ipcRenderer.on('state', listener); return () => ipcRenderer.removeListener('state', listener); }
});
