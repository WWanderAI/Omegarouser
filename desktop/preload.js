const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('omegaAPI', {
  setAdblock: (enabled) => ipcRenderer.invoke('set-adblock', enabled),
  setupPrivateSession: (partition) => ipcRenderer.invoke('setup-private-session', partition),
  onDownloadUpdate: (callback) => ipcRenderer.on('download-update', (event, data) => callback(data))
});
