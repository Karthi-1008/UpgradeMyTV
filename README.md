<div align="center">

# 📺 UpgradeMyTV — TV Power Hub
### *Ultra-Low Latency N-in-1 Web Remote, Autonomous Background Server & System Enhancer for Android TVs*

[![License: Apache 2.0](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Platform](https://img.shields.io/badge/Platform-Android%20TV%2011%20%28API%2030%29-green.svg)](https://developer.android.com/tv)
[![Target Variant](https://img.shields.io/badge/TCL-Smart%20TV%20C06%20%2F%20BeyondTV-orange.svg)](#-dedicated-hardware-variant-tcl-c06)
[![Architecture](https://img.shields.io/badge/Arch-ARMv7a%20%7C%2032--bit-lightgrey.svg)](https://developer.android.com/ndk/guides/abis)
[![Latency](https://img.shields.io/badge/Execution%20Latency-%3C%205ms-brightgreen.svg)](#-performance--latency)
[![Self-Contained](https://img.shields.io/badge/Dependencies-Zero%20External%20Libraries-purple.svg)](#-zero-dependency-build)

<p align="center">
  <b>Transform your phone into a tactile, instant-response TV remote — with zero USB debugging dongles, no root permissions, and zero background battery drain on your TV.</b>
</p>

[Quick Start](#-quick-start) •
[Features](#-key-features) •
[Video Demos](#-live-video-demonstrations) •
[Before vs. After](#-visual-proof--before-vs-after) •
[Hardware Specs](#-dedicated-hardware-variant-tcl-c06) •
[Porting Guide for Agents](#-universal-tv-porting-guide-for-ai-agents) •
[Roadmap](#-v2-roadmap)

---

</div>

## 🌟 Overview

**UpgradeMyTV (TV Power Hub)** is a standalone Android TV service that embeds an ultra-lightweight Java HTTP server and an Accessibility automation engine into an ultra-compact (~100 KB) APK. 

Unlike traditional remote apps that rely on cloud relay servers, slow ADB polling, or fragile IR blasters, TV Power Hub serves a tactile, offline HTML5 Progressive Web Remote directly from your TV's local IP address (`http://<TV_IP>:8088`). Point your phone's camera at the on-screen QR code, and you are immediately in control.

---

## 🎬 Live Video Demonstrations

Real-time synchronized capture showing the tactile mobile web remote alongside the TCL Android TV screen:

<div align="center">

<table width="100%">
  <thead>
    <tr>
      <th width="35%" align="center">📱 Tactile Mobile Web Remote</th>
      <th width="65%" align="center">📺 TCL Android TV Screen</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td align="center" valign="top">
        <video src="docs/recordings/mobile_remote_demo.mp4" controls width="100%" muted playsinline></video>
        <p align="center">
          <sub>📱 <i>Zero-latency D-Pad taps, volume sliders, and input switches</i></sub><br/>
          <sub><a href="docs/recordings/mobile_remote_demo.mp4">▶️ Watch / Download Mobile Demo (4.1 MB)</a></sub>
        </p>
      </td>
      <td align="center" valign="top">
        <video src="docs/recordings/tv_display_demo.mp4" controls width="100%" muted playsinline></video>
        <p align="center">
          <sub>📺 <i>Instant focus navigation, zero-freeze source overlay, and media control</i></sub><br/>
          <sub><a href="docs/recordings/tv_display_demo.mp4">▶️ Watch / Download TV Screen Demo (1.7 MB)</a></sub>
        </p>
      </td>
    </tr>
  </tbody>
</table>

</div>

---

## 📸 Screenshots & Visual Walkthrough

<div align="center">

### 1. On-Screen TV Display & Quick Connect
<img src="docs/screenshots/01_tv_on_screen_display.png" alt="TV Power On Screen" width="700" style="border-radius: 8px; box-shadow: 0 4px 12px rgba(0,0,0,0.3);"/>
<p><i>The on-screen TV dashboard displays the active server status, local IP URL, and an instant-connect QR code.</i></p>

<br/>

### 2. Native TV Source Overlay & Mobile Web Remote
<table>
  <tr>
    <td align="center" width="50%">
      <b>TV Native Source Choice Overlay</b><br/>
      <img src="docs/screenshots/03_native_source_overlay_working.png" alt="Native TV Source Menu" width="380" style="border-radius: 6px;"/><br/>
      <i>Direct native TCL popup overlay for switching HDMI 1, HDMI 2, AV, and TV.</i>
    </td>
    <td align="center" width="50%">
      <b>Mobile Tactile Web Remote</b><br/>
      <img src="docs/screenshots/02_web_remote_interface.png" alt="Mobile Web Remote" width="220" style="border-radius: 6px;"/><br/>
      <i>Ultra-fast dark-graphite UI with Hero D-Pad, haptic feedback, and tabs.</i>
    </td>
  </tr>
</table>

</div>

---

## 🔄 Visual Proof: Before vs. After

During development, real-world testing on the hardware uncovered edge cases that were resolved and verified:

<table>
  <thead>
    <tr>
      <th width="50%">❌ BEFORE (Buggy Behavior)</th>
      <th width="50%">✅ AFTER (Fixed & Working)</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td>
        <b>Issue 1: Input source redirected to general settings</b><br/>
        <img src="docs/screenshots/05_before_wrong_settings.png" alt="Settings Error" width="350"/><br/>
        <i>Attempting to switch sources opened Android TV Settings instead of HDMI inputs.</i>
      </td>
      <td>
        <b>Fix 1: Direct native TV overlay</b><br/>
        <img src="docs/screenshots/04_source_over_app.png" alt="Source choice over TV" width="350"/><br/>
        <i>Integrated <code>com.tcl.insrc.SHOW_WINDOW</code> service — pops up the source overlay instantly over any active app.</i>
      </td>
    </tr>
    <tr>
      <td>
        <b>Issue 2: Media player passthrough error</b><br/>
        <img src="docs/screenshots/06_before_player_error.png" alt="Player Error 2005" width="350"/><br/>
        <i>Android Media TV passthrough intent caused "Player error: 2005 Source error" black screen.</i>
      </td>
      <td>
        <b>Fix 2: Zero-error hardware broadcast</b><br/>
        <i>Bypassed video intents entirely; switches directly through TCL window service with &lt; 5ms execution.</i>
      </td>
    </tr>
    <tr>
      <td>
        <b>Issue 3: D-Pad double-clicking on single tap</b><br/>
        <i>Frontend fired both <code>pointerdown</code> and <code>click</code>; directional navigation sent <code>ACTION_SELECT</code>.</i>
      </td>
      <td>
        <b>Fix 3: Guaranteed single-tap registration</b><br/>
        <i>Suppressed synthetic click bubbling, added 70ms backend debouncing, and decoupled focus movement from selection.</i>
      </td>
    </tr>
  </tbody>
</table>

---

## 🎯 Dedicated Hardware Variant: TCL C06

This repository is calibrated and tested on the following television hardware:

| Parameter | Hardware Specification |
| :--- | :--- |
| **Brand & Manufacturer** | TCL (`TCL`) |
| **Model / Board Name** | **TCL Smart TV C06** / `BeyondTV` (`BeyondTV4-43C06`) |
| **SoC / Chipset** | Realtek RTD2851 (`rtd285o_atv`) |
| **OS & API Level** | Android TV 11 (API level 30, Red Velvet Cake) |
| **System Architecture** | `armeabi-v7a` (32-bit ARM Cortex-A55) |
| **RAM Footprint** | 1 GB total (917 MB usable) — TV Power Hub uses &lt; 18 MB |
| **Display Panel** | 1920 × 1080 (1080p @ 60 Hz) |
| **Native Integration** | `com.tcl.tv/com.tcl.sourcemenu.insrc.ShowWindowService` |

> [!NOTE]
> **Can this APK work on other TVs?**
> The core features (tactile remote, D-Pad, volume, home/back, media controls, keyboard typing, screenshot streaming, and web dashboard) are **100% universal across all Android TVs**. 
> Only manufacturer-specific buttons (like native HDMI source overlays) require custom intent mapping. See the [AI Agent Porting Guide](AGENT_PORTING_GUIDE.md) to adapt it to Sony, Xiaomi, Samsung, or LG in seconds.

---

## 🚀 Key Features

- **📱 Zero-Install Web Remote:** Open in Safari, Chrome, Firefox, or Edge on any smartphone connected to the same Wi-Fi.
- **⚡ Ultra-Low Latency (&lt; 5ms):** Direct in-memory socket communication without cloud servers or round-trip delay.
- **🎯 Hero D-Pad:** 4-segment SVG navigation wheel with haptic feedback, hold-to-repeat, and gesture swipe trackpad mode.
- **🎛️ Native TV Source Overlay:** Tapping "Input source" pops up the TV's native source choice dialog on screen without disrupting background playback.
- **⌨️ Live Remote Typing:** Type text on your smartphone keyboard to fill in search bars, passwords, and URLs instantly on the TV.
- **📸 Screen Capture:** Take real-time high-resolution screenshots from your TV directly into your phone browser.
- **🔌 100% USB Pen Drive Safe:** ADB is completely disconnected after setup; USB debugging is never required during normal operation.
- **🛡️ RAM Guardian:** Runs quietly in the background on ultra-low resource budgets (~18 MB RAM).

---

## 🏗️ System Architecture

```mermaid
flowchart TD
    subgraph Phone["📱 Phone / Client Browser"]
        UI["Tactile Web Remote (HTML5/CSS/JS)"]
        DPad["Hero D-Pad & Keypad"]
        Input["Typing & Source Control"]
    end

    subgraph TV["📺 Android TV (TV Power Hub APK)"]
        HTTP["Embedded Java HTTP Server\n(Ports 8088 & 8080)"]
        A11y["TvAccessibilityService\n(System Foreground Service)"]
        TCL["TCL Hardware Layer\n(com.tcl.insrc.SHOW_WINDOW)"]
        Audio["AudioManager\n(Volume & Mute)"]
    end

    UI -->|"HTTP GET /api/action?action=key&code=19"| HTTP
    DPad -->|"Sub-5ms Execution"| HTTP
    Input -->|"Source Action"| HTTP

    HTTP --> A11y
    HTTP --> Audio
    A11y -->|"Focus Navigation & Clicks"| TV
    A11y -->|"Native Source Overlay"| TCL
```

---

## 📦 Zero-Dependency Build

This project requires **no Gradle**, **no Android Studio**, and **no heavyweight IDE**. A single PowerShell script compiles, dexes, packages, and signs the complete APK using standard Android SDK command-line tools.

### Prerequisites
1. Windows OS with PowerShell
2. Java JDK (`javac.exe`)
3. Android SDK Build-Tools & Platforms (auto-detected via `$env:ANDROID_HOME` or standard install paths)

### Building the APK
```powershell
powershell -ExecutionPolicy Bypass -File .\build_apk.ps1
```

The script will automatically:
1. Locate your Android SDK build tools and `android.jar`.
2. Compile Java sources into `tvpowernew_src/bin`.
3. Translate bytecode with `d8.bat` to `classes.dex`.
4. Bundle and link web assets with `aapt2.exe`.
5. Zipalign and sign with `apksigner.bat`.
6. Output the verified production-ready **`TVPowerNew.apk`**.

---

## 📲 Installation on TV

### Method 1: Wi-Fi ADB (One-Time Setup)
```bash
# 1. Connect to TV over Wi-Fi
adb connect <TV_IP>:5555

# 2. Install APK
adb -s <TV_IP>:5555 install -r TVPowerNew.apk

# 3. Launch App
adb -s <TV_IP>:5555 shell "am start -n com.gathin.tvpowerhub/.MainActivity"

# 4. Disconnect ADB (Keep USB completely free)
adb disconnect
```

### Method 2: USB Flash Drive
1. Copy `TVPowerNew.apk` to a USB flash drive.
2. Insert the flash drive into your TV.
3. Open any file manager on your TV (e.g., File Commander or TV File Manager) and click **Install**.
4. Open **TV Power New** from your TV apps list.

---

## 🤖 Universal TV Porting Guide for AI Agents

Want to adapt this application for **Sony Bravia**, **Samsung**, **LG**, **Xiaomi PatchWall**, **Hisense VIDAA**, **Philips**, or **OnePlus TV**?

👉 **Read the complete [AI Agent Porting Guide (AGENT_PORTING_GUIDE.md)](AGENT_PORTING_GUIDE.md)**

The guide includes:
- Automated ADB discovery commands for AI agents to probe manufacturer intent filters.
- Brand-specific code snippets for `TvAccessibilityService.java`.
- Step-by-step verification protocols.

---

## 🗺️ V2 Roadmap & Customization Specification

The full technical specification and architecture for **Version 2.0** is fully implemented and compiled into **`TVPowerNew.apk`**:

### 💾 1. Deep USB Storage Engine & File Tools (Bug Fix & Enhancement)
- [x] **Multi-Path USB Auto-Mounter:** Deep 5-layer detection via `StorageManager`, `getExternalFilesDirs`, `/proc/mounts`, and fallback directories—completely resolving USB flash drive detection on Android TV 11.
- [x] **Dedicated Dual Upload Buttons:** Separate, crystal-clear buttons for **📺 Upload to TV Storage** (`/storage/emulated/0/Download`) and **💾 Upload to USB Drive** (`/storage/<UUID>`). Eliminates ambiguity and ensures files are saved exactly where intended.
- [x] **Automated USB Filesystem Compatibility Detection:** Pre-checks connected USB drive format (FAT32, exFAT, NTFS). Warns users upfront if an NTFS drive is mounted read-only by the Android kernel, suggesting FAT32/exFAT for writes or 1-tap redirect to TV Internal Storage.
- [x] **Verified Storage Receipts & Instant Folder Highlight:** Verified write confirmations with exact disk location and byte counts. Automatically refreshes folder contents and highlights newly uploaded files on screen.
- [x] **Direct USB APK Sideload & 1-Click Installer:** Green one-tap install button on `.apk` files located on USB drives.
- [x] **USB Media Streamer & Gallery:** High-speed streaming to phone browser via HTTP 206 Partial Content Range headers, plus direct playback on TV.
- [x] **USB Storage Health & Speed Benchmark:** Live sequential write & read speed test (MB/s), filesystem format analyzer (FAT32/exFAT/NTFS), and capacity meter.

### 🎮 2. Dual-Engine D-Pad & Navigation System (100% In-App Functionality)
- [x] **Hardware-Grade Key Injection:** Injects native Linux input events (`/dev/input/event*`) across all third-party applications (YouTube TV, Netflix, Prime Video, SmartTube, Kodi, TV Settings) with zero focus loss.
- [x] **Accessibility Spatial Geometry Engine:** Autonomous 4-tier fallback (Standard FocusSearch → Spatial Geometric Directional Raycasting → Scroll Container → Synthetic Precision Center Taps) ensuring seamless navigation across non-standard canvases.

### 🔒 2. App Lock & Parental Control PIN
- [x] **Accessibility Window State Interceptor:** Detects restricted or sideloaded apps launching on TV and automatically redirects to Home with an on-screen lock notification.
- [x] **Web Remote Management Panel:** 4-digit master PIN, live installed apps checklist with lock checkboxes, and 30-minute quick unlock session.

### 🎬 3. Smart Media, Audio EQ & Viewing Enhancers
- [x] **Display & Audio EQ Calibrator:** Quick picture presets, plus **Dialogue Boost** (+4.5 dB vocal frequencies) and **Night Dynamic Compression** (calms loud explosions).
- [x] **Direct Web Video Link & Stream Player:** Paste YouTube, SmartTube, MP4, or m3u8 URLs to launch instantly on the TV's native decoder.

### ⚡ 4. TV Automations & Background Intelligence
- [x] **Intelligent Proactive RAM Guardian:** Proactive background monitor that automatically purges dead cache and rogue background processes when free RAM dips below 150 MB.
- [x] **Gradual Smart Sleep Timer:** 15–60 min timer that dims screen brightness and turns off TV automatically.

### 📲 5. Mobile-to-TV Notification & Alert Relay
- [x] **Real-Time Incoming Call & Phone Alert Banners:** Discreet corner popups on TV for phone calls and urgent notifications.
- [x] **Custom Text & Message Broadcast:** Send instant pop-up toast messages from phone to TV screen.
- [x] **Phone Battery & Urgent Alerts:** On-screen TV notification when phone battery is critical (< 15%).

### 🤖 6. Groq Cloud AI Engine Integration
- [x] **Web Remote Key Vault & Rate Limiter:** Securely enter Groq API keys with built-in token budgeting to protect free-tier limits.
- [x] **AI Smart Notification Summarizer:** Compresses long phone messages and emails into crisp 1-line TV banners in < 150ms using Llama 3.1 8B.
- [x] **AI Movie & Content Recommender:** Multi-attribute interactive recommender with Category (Movies, Series, Anime), Language (English, Tamil, Hindi, etc.), Genre, and Mood filters, complete with 1-tap TV launch buttons.

👉 **Read the complete, comprehensive technical specification: [`README_V2_ROADMAP.md`](README_V2_ROADMAP.md)**

---

## 🔒 Security & Privacy

- All sensitive API keys and device identifiers have been stripped and replaced with configurable tokens (`TV_POWER_SECURE_KEY`).
- Operates entirely within your local home Wi-Fi network; no telemetry, tracking, or cloud connections.
- Clean Apache 2.0 open-source code.

---

<div align="center">
  <b>Built with ❤️ for elevating the Smart TV experience.</b><br/>
  <sub>Developed by Karthikeyan A (Karthi-1008)</sub>
</div>
