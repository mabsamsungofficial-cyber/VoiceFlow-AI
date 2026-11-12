const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('electronAPI', {
  isElectron: true,
  onHotkeyToggleMic: (callback) => {
    ipcRenderer.on('hotkey-toggle-mic', () => callback());
  },
  autoPasteText: (text) => {
    ipcRenderer.send('auto-paste-text', text);
  }
});

