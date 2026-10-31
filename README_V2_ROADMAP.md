# TV Power Hub — Version 2.0 Feature Specification & Implementation Plan

> **Status:** Requirements Selected & Frozen  
> **Target Version:** Version 2.0  
> **Target Device:** TCL Smart TV (Android 11, MediaTek Quad-Core Cortex-A55, API 30)

---

## 🎯 Chosen Features for Version 2.0 (Selected by User)

### 🎙️ 1. Navigation & Voice Controls
- [x] **Voice Typing & Dictation:**
  - Speak into your phone microphone (via Web Speech API / HTML5 Audio).
  - Automatically transcribes voice to text and injects it directly into the active TV search field or password input box.
- [x] **Multi-Touch Gestures:**
  - Two-finger scrolling for rapid list and page navigation.
  - Pinch-to-zoom support for web browsers and photos on TV.

---

### ⚡ 2. Smart Automation & System Optimization
- [x] **Intelligent Background RAM Guardian:**
  - Proactive background memory monitor.
  - Automatically purges cached and dead background processes when free RAM dips below 150MB, preventing 4K streaming buffering and stutter.
- [x] **One-Tap Presets & Scene Modes:**
  - **Cinema Mode:** Sets calibrated backlight/volume levels and launches preferred streaming apps.
  - **Late Night Mode:** Caps maximum volume at 20% and reduces harsh audio spikes.
  - **Kids Mode:** Locks settings menu access, enforces safe volume limits, and sets auto-timeout.
- [x] **Wake-on-LAN (Network Remote Power-On):**
  - Sends network magic packets over Wi-Fi to wake/power on the TV from deep standby without needing the physical remote.

---

### 🎬 3. Media Hub, Casting & Audio
- [x] **Private Night Listening (TV Audio to Phone):**
  - Streams real-time TV audio back to your phone browser.
  - Allows you to plug headphones into your phone and watch TV without disturbing others in the room.
- [x] **Direct Web Video Link Player:**
  - Paste any video link (YouTube, MP4, MKV, m3u8 stream) from your phone to immediately launch and play on the TV's native hardware decoder.
- [x] **Phone-to-TV Media Cast:**
  - Cast photos, videos, and music stored directly on your phone to the TV screen via the web browser with zero third-party apps required.
- [x] **"Now Playing" Media Dock:**
  - Displays live track title, artist, playback progress bar / scrubber, and album art directly on the phone remote.
- [x] **Universal Content Search:**
  - Single search bar that queries across YouTube, SmartTube, local media, and connected USB storage simultaneously.

---

### 📦 4. File, App & USB Power Tools
- [x] **Wireless APK Sideload & One-Click Installer:**
  - Upload `.apk` files directly from your phone browser to the TV with automatic installation triggers.
- [x] **TV App Manager & Task Killer:**
  - View all installed TV apps (including hidden sideloaded apps not displayed on the standard launcher).
  - Launch, force-stop background tasks, clear app cache, or uninstall directly from your phone.
- [x] **USB Drive Media Gallery:**
  - Visual photo thumbnail grid and video preview player for plugged-in USB pen drives.
  - Easy batch file transfers between TV internal storage and USB storage.

---

## ⏸️ 5. Deferred / Backlog Features (Not Selected for V2)
- [ ] *Virtual Air Mouse / Gyroscope Trackpad*
- [ ] *Virtual Game Controller (ABXY & triggers)*
- [ ] *Channel Number Pad (0–9)*
- [ ] *Smart Sleep Timer with Screen Dimming*
- [ ] *Scheduled Night Maintenance Reboot*
- [ ] *Live Screen Video Streaming / PiP*
- [ ] *Screen Recording (.mp4)*
- [ ] *AMOLED Pitch-Black Theme & Custom Styles*
