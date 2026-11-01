const { app, BrowserWindow, globalShortcut, ipcMain, clipboard, Tray, Menu } = require('electron');
const path = require('path');
const fs = require('fs');
const { exec } = require('child_process');

let mainWindow = null;
let tray = null;
let isQuitting = false;

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 440,
    height: 780,
    minWidth: 360,
    minHeight: 600,
    backgroundColor: '#0e1115',
    title: 'VoiceFlow',
    autoHideMenuBar: true,
    show: false,
    webPreferences: {
      preload: path.join(__dirname, 'preload.cjs'),
      contextIsolation: true,
      nodeIntegration: false,
      backgroundThrottling: false
    }
  });

  if (app.isPackaged) {
    mainWindow.loadFile(path.join(__dirname, '..', 'dist', 'index.html'));
  } else {
    const devUrl = 'http://localhost:5173';
    mainWindow.loadURL(devUrl).catch(() => {
      mainWindow.loadFile(path.join(__dirname, '..', 'dist', 'index.html'));
    });
  }

  mainWindow.once('ready-to-show', () => {
    mainWindow.show();
  });

  mainWindow.on('close', (event) => {
    if (!isQuitting) {
      event.preventDefault();
      mainWindow.hide();
    }
  });
}

function createTray() {
  const iconPath = path.join(__dirname, 'icon.png');
  if (!fs.existsSync(iconPath)) return;

  try {
    tray = new Tray(iconPath);
    const contextMenu = Menu.buildFromTemplate([
      { label: 'Open VoiceFlow', click: () => { mainWindow.show(); mainWindow.focus(); } },
      { label: 'Dictate (Ctrl+Shift+Space)', click: () => triggerDictation() },
      { type: 'separator' },
      { label: 'Quit', click: () => { isQuitting = true; app.quit(); } }
    ]);
    tray.setToolTip('VoiceFlow - Smart Voice Dictation');
    tray.setContextMenu(contextMenu);
    tray.on('click', () => {
      if (mainWindow.isVisible()) mainWindow.hide();
      else { mainWindow.show(); mainWindow.focus(); }
    });
  } catch (err) {
    console.error('Tray init notice:', err);
  }
}

function triggerDictation() {
  if (!mainWindow) return;
  mainWindow.show();
  mainWindow.focus();
  mainWindow.webContents.send('hotkey-toggle-mic');
}

app.whenReady().then(() => {
  createWindow();
  createTray();

  // Register Global Hotkeys (Ctrl+Shift+Space & Alt+V)
  try {
    globalShortcut.register('CommandOrControl+Shift+Space', () => {
      triggerDictation();
    });

    globalShortcut.register('Alt+V', () => {
      triggerDictation();
    });
  } catch (err) {
    console.error('Failed to register global hotkey:', err);
  }

  // Handle auto-paste into focused Windows field
  ipcMain.on('auto-paste-text', (event, text) => {
    if (!text || !text.trim()) return;

    // 1. Copy Hinglish text to Windows clipboard
    clipboard.writeText(text.trim());

    // 2. Hide window so previous active app (VS Code, Notepad, Browser, etc.) receives focus
    if (mainWindow) {
      mainWindow.hide();
    }

    // 3. Simulate Ctrl+V into the active input field
    setTimeout(() => {
      const psCommand = `Add-Type -AssemblyName System.Windows.Forms; [System.Windows.Forms.SendKeys]::SendWait('^v')`;
      exec(`powershell -NoProfile -Command "${psCommand}"`, (error) => {
        if (error) {
          console.error('Auto-paste SendKeys notice:', error.message);
        }
      });
    }, 120);
  });
});

app.on('will-quit', () => {
  globalShortcut.unregisterAll();
});

app.on('activate', () => {
  if (BrowserWindow.getAllWindows().length === 0) createWindow();
});

