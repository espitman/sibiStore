const { contextBridge, ipcRenderer, webUtils } = require('electron');
contextBridge.exposeInMainWorld('sibi', {
  stageFiles: files => ipcRenderer.invoke('stage-files', Array.from(files).map(file => webUtils.getPathForFile(file))),
  chooseSendFiles: () => ipcRenderer.invoke('choose-send-files'),
  sendFiles: (ids, targets) => ipcRenderer.invoke('send-files', ids, targets),
  fileTransferAction: (id, action) => ipcRenderer.invoke('file-transfer-action', id, action),
  snapshot: () => ipcRenderer.invoke('snapshot'),
  rescan: () => ipcRenderer.invoke('rescan'),
  setPlatformOverride: (hash, platform) => ipcRenderer.invoke('set-platform-override', hash, platform),
  openFolder: () => ipcRenderer.invoke('open-folder'),
  reveal: hash => ipcRenderer.invoke('reveal', hash),
  chooseFolder: () => ipcRenderer.invoke('choose-folder'),
  chooseSdk: () => ipcRenderer.invoke('choose-sdk'),
  server: running => ipcRenderer.invoke('server', running),
  login: enabled => ipcRenderer.invoke('login', enabled),
  copyAddress: () => ipcRenderer.invoke('copy-address'),
  onChange: fn => { const listener = (_, state) => fn(state); ipcRenderer.on('state', listener); return () => ipcRenderer.removeListener('state', listener); }
});
