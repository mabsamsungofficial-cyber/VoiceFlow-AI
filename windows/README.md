# 💻 VoiceFlow for Windows (`v2.1.0`)

System-wide AI voice dictation and prompt engineering companion for Windows 10 & 11.

---

## Key Features
- **Global Hotkey Dictation:** Press `Ctrl + Shift + Space` or `Alt + V` from inside any application (VS Code, Notepad, Word, Chrome, WhatsApp Desktop).
- **Direct Auto-Paste:** Spoken text is transcribed into polished Hinglish/English and automatically pasted into the active text cursor via simulated `Ctrl + V`.
- **Background System Tray:** Minimizes quietly to the system tray on window close, ready for instant activation.
- **Standalone Portable `.exe`:** Can be packaged as a single portable Windows executable without requiring an installer.

---

## Directory Structure
- `electron/main.cjs`: Main Electron process managing window lifecycle, hotkey hooks, and auto-paste logic.
- `electron/preload.cjs`: Secure context bridge between React web UI and Electron backend.
- `electron/icon.png`: Application and system tray icon.
- `launcher/VoiceFlowLauncher.cs`: Native C# WinForms background tray daemon with native Win32 `RegisterHotKey`.
- `release/VoiceFlow.exe`: Compiled portable Windows launcher.

---

## How to Run
```bash
# Launch Electron app with hotkeys enabled
npm run start:windows

# Build portable Windows executable
npm run build:windows
```

