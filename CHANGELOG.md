# 📜 VoiceFlow Changelog

All notable changes to the VoiceFlow multi-platform ecosystem will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## 🛡️ The VoiceFlow Immutable Contract (Golden Update Rules)

Every future contribution and update to VoiceFlow MUST adhere to these non-negotiable principles:
1. **Zero Regression:** No update shall break existing core features. Windows hotkeys (`Ctrl+Shift+Space`), Roman Hinglish conversational prompts, and Web dashboard workflows must remain intact.
2. **Platform Isolation:** Android, Windows, and Web platforms must be independently testable and maintain their respective version tracks.
3. **Additive & Measurable Improvement:** Every update must deliver a measurable improvement (speed, accuracy, battery efficiency, or UI polish).
4. **Git Tag Checkpoint:** Every milestone must be committed and tagged in Git before public release for instant, one-click rollback if required.

---

## [3.3.0] - 2026-11-12

### 📱 Android Companion (`v3.3.0`)
- **Added:** Native Push-to-Talk (Hold-to-Talk) mode — touch and hold the pill to dictate, release to immediately transcribe and auto-paste into the active field.
- **Added:** Tap-to-Talk toggle mode — quick tap (<350ms) toggles recording hands-free.
- **Improved:** AI transcription engine upgraded to `gemini-flash-lite-latest` with fallback to `gemini-3.5-flash-lite`, slashing end-to-end response time from ~3.8s down to **~1.0s**.
- **Improved:** Strict speech filler stripper prompt aggressively filters out *hmm*, *haan*, *umm*, *uh*, *matlab*, *actually*, hesitation pauses, and mid-sentence stutters.
- **Improved:** Enabled HTTP keep-alive connection pooling (`http.keepAlive=true`) to eliminate repeat SSL/TLS handshake latency on mobile networks.
- **Fixed:** Resolved issue where holding the pill for >700ms inadvertently opened the main app instead of recording speech.
- **Removed:** Deleted legacy `FloatingMicService.java` and removed its obsolete declaration from `AndroidManifest.xml`.
- **Removed:** Purged unneeded `reference/` directory (50MB external reference APK) and orphan `release/` folder.
- **Performance:** Optimized `FlowPillView.java` by caching `LinearGradient` in `onSizeChanged()`, eliminating per-frame memory allocation during 60 FPS recording animations.

### 🌐 Web / Core Dashboard (`v3.3.0`)
- **Improved:** Default AI model synced to `gemini-flash-lite-latest`.
- **Improved:** Multi-platform monorepo folder layout with root unified build scripts.

---

## [3.1.0] - 2026-11-12

### 📱 Android Companion (`v3.1.0`)
- **Added:** Intelligent App Whitelist — overlay only surfaces for text inputs in WhatsApp, Telegram, Instagram, Facebook, X, LinkedIn, Discord, Reddit, ChatGPT, Claude, Gemini, Copilot, Gmail, Keep, and Notion.
- **Added:** Search Bar Exclusion — automatically ignores search inputs across Play Store, Settings, YouTube, and system search bars.
- **Added:** Placeholder & Hint Filter — detects and discards default hint strings (e.g. "Message", "मैसेज", "संदेश", "Type a message") so they are never prepended to user speech.
- **Added:** Magnetic screen edge docking — automatically snaps cleanly to the left or right screen border on drag release.
- **Added:** Zero-overflow screen boundary clamping — expanding pill during recording/processing never bleeds outside display boundaries.
- **Fixed:** Two-way synchronization for `auto_copy_enabled` toggle via `SharedPreferences`.

---

## [3.0.0] - 2026-11-12

### 📱 Android Companion (`v3.0.0`)
- **Added:** Wispr Flow-inspired floating accessibility companion.
- **Added:** `TYPE_ACCESSIBILITY_OVERLAY` rendering to float over third-party applications without requiring full-screen draw permissions.
- **Added:** In-field direct typing via `AccessibilityNodeInfo.ACTION_SET_TEXT` and clipboard fallback.
- **Added:** Custom hardware-accelerated [FlowPillView](file:///android/app/src/main/java/com/smartvoice/input/FlowPillView.java) with vector microphone, sine-wave equalizer bars, spinner, and success checkmark.

---

## [2.1.0] - 2026-11-01

### 💻 Windows Desktop (`v2.1.0`)
- **Added:** System-wide global hotkeys: `Ctrl + Shift + Space` and `Alt + V`.
- **Added:** Auto-paste injection using PowerShell `[System.Windows.Forms.SendKeys]::SendWait('^v')`.
- **Added:** System Tray background daemon (`VoiceFlowLauncher.cs` and Electron tray menu) with minimize-to-tray on window close.
- **Added:** Portable `.exe` builder configuration via `electron-builder`.

---

## [2.0.0] - 2026-10-28

### 🌐 Web & Shared Core (`v2.0.0`)
- **Added:** Conversational Roman Hinglish transcription with contextual slang understanding.
- **Added:** Output style selectors: Hinglish (Roman), Hindi (Devanagari), and English.
- **Added:** AI Prompt Expert Mode — converts spoken ideas into comprehensive prompts for ChatGPT/Claude.
- **Added:** Local recording history drawer with playback, search, and one-click copy.

