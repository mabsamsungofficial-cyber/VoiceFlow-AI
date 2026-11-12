# 📱 VoiceFlow for Android (`v3.3.0`)

Wispr Flow-inspired AI voice dictation and in-field auto-typing companion for Android smartphones.

---

## Key Features
- **In-Field Floating Pill:** Automatically appears next to the cursor when typing in eligible chatting, social media, and AI apps.
- **Push-to-Talk (Hold-to-Talk):** Hold the pill to dictate, release to immediately transcribe and paste.
- **Tap-to-Talk Toggle:** Quick tap for hands-free dictation.
- **App Whitelist:** Surfaces for WhatsApp, Telegram, Instagram, Facebook, X, LinkedIn, Discord, Reddit, ChatGPT, Claude, Gemini, Copilot, Gmail, Keep, and Notion.
- **Search Bar Exclusion:** Intelligently ignores search inputs in Play Store, Settings, YouTube, and system bars.
- **Speech Filler Removal:** Aggressively removes *hmm*, *haan*, *umm*, *uh*, *matlab*, *actually*, and false starts.
- **Magnetic Snapping:** Snaps cleanly to left or right screen edge with zero-overflow boundary clamping.
- **Direct Auto-Typing:** Types directly into WhatsApp/ChatGPT input fields via `AccessibilityNodeInfo.ACTION_SET_TEXT`.

---

## Directory Structure
- `app/src/main/java/com/smartvoice/input/FlowPillView.java`: Hardware-accelerated canvas-drawn floating capsule.
- `app/src/main/java/com/smartvoice/input/VoiceFlowAccessibilityService.java`: System accessibility overlay service, touch handler, and in-field typing.
- `app/src/main/java/com/smartvoice/input/VoiceFlowGeminiEngine.java`: Ultra-fast REST client for `gemini-flash-lite-latest` with persistent HTTP keep-alive.
- `app/src/main/java/com/smartvoice/input/MainActivity.java`: Capacitor bridge activity & runtime permission management.
- `app/build/outputs/apk/debug/app-debug.apk`: Compiled ready-to-install debug APK.

---

## How to Build
```bash
# Sync web assets to Android
npm run build:android

# Compile debug APK
npm run build:apk

# Full build
npm run build:all
```

