# 🤖 AI Agent & Developer Porting Guide

This guide is designed for **AI coding assistants** (Antigravity, Claude, ChatGPT, Cursor, Roo-Code, etc.) and human developers. It provides step-by-step instructions to adapt **TV Power Hub** to any Smart TV or Android TV streaming box.

---

## 📐 Architecture Overview

TV Power Hub does **not** require root access or active USB debugging after initial install. It functions autonomously using:
1. **Embedded Java HTTP Server (`TvHttpServer.java`)**: Runs inside the app process on ports `8088` and `8080`, serving an ultra-fast tactile HTML5/JS web remote.
2. **Foreground Accessibility Service (`TvAccessibilityService.java`)**: Bound by Android OS to provide instantaneous focus navigation, gesture swiping, screen captures, volume, and hotkey broadcasts.
3. **Hardware-Specific Broadcasts & Window Services**: Direct manufacturer hooks for power, input selection, picture, and backlight.

---

## 🎯 Target Reference Variant (Baseline)

The primary reference build in this repository is tuned for:
- **Manufacturer:** TCL
- **Model / Variant:** TCL Smart TV C06 / BeyondTV (`BeyondTV4-43C06`)
- **Board / Chipset:** Realtek RTD2851 (`rtd285o_atv`)
- **OS Version:** Android TV 11 (API level 30, Red Velvet Cake)
- **Memory:** 1 GB RAM (917 MB usable) — requires lean, zero-daemon footprint
- **Resolution:** 1920 × 1080 (1080p @ 60Hz)

---

## 🔍 Step 1: Automated Device Discovery (Run via ADB)

When porting to a new TV, have the AI agent run these discovery commands over Wi-Fi ADB (`adb connect <TV_IP>:5555`):

```bash
# 1. Discover TV model, board chipset, and Android OS version
adb shell "getprop ro.product.manufacturer; getprop ro.product.model; getprop ro.board.platform; getprop ro.build.version.release"

# 2. Find native input source services, activities, and broadcast receivers
adb shell "dumpsys package | grep -B 2 -A 6 -iE 'source|input|sourcemenu'"

# 3. Discover manufacturer-specific quick menus, shortcuts, and dashboard overlays
adb shell "pm list packages | grep -iE 'tcl|sony|xiaomi|hisense|samsung|lg|philips|oneplus|amazon'"

# 4. Identify focused window when pressing the physical remote's Input/Source button
adb shell "dumpsys window | grep -E 'mCurrentFocus|mFocusedApp'"
```

---

## 🛠️ Step 2: Brand-Specific Integration Matrix

Replace the methods in `tvpowernew_src/src/com/gathin/tvpowerhub/TvAccessibilityService.java` based on the manufacturer:

### 1. TCL Smart TVs (Android 9 / 10 / 11)
```java
// Native On-Screen Source Choice Overlay
Intent intent = new Intent("com.tcl.insrc.SHOW_WINDOW");
intent.setComponent(new ComponentName("com.tcl.tv", "com.tcl.sourcemenu.insrc.ShowWindowService"));
context.startService(intent);

// TCL Power Down / Backlight
context.sendBroadcast(new Intent("com.tcl.backlight.off"));
context.sendBroadcast(new Intent("com.tcl.system.poweroff"));
```

### 2. Sony Bravia (Android TV / Google TV)
```java
// Native Sony Inputs Activity
Intent intent = new Intent();
intent.setComponent(new ComponentName("com.sony.dtv.osat.inputs", "com.sony.dtv.osat.inputs.MainActivity"));
intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
context.startActivity(intent);

// Fallback: Standard Android TV Preferences
Intent tvPref = new Intent("android.intent.action.TV_INPUT_PREFERENCES");
tvPref.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
context.startActivity(tvPref);
```

### 3. Xiaomi / Redmi TV (PatchWall OS)
```java
// Xiaomi Source Chooser
Intent intent = new Intent("com.xiaomi.mitv.tvinput.ACTION_SHOW_INPUT_SOURCE");
intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
context.startActivity(intent);
```

### 4. OnePlus TV (OxygenPlay)
```java
Intent intent = new Intent("com.oneplus.tv.input.SHOW_MENU");
intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
context.startActivity(intent);
```

### 5. Universal Android TV Fallback (Chromecast with Google TV, Philips, Sharp)
```java
// 1. Android TV Inputs Settings
try {
    Intent intent = new Intent("android.intent.action.TV_INPUT_PREFERENCES");
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    context.startActivity(intent);
} catch (Throwable t) {
    // 2. Hardware Key injection fallback
    TvAccessibilityService.sendDpad(178); // KEYCODE_TV_INPUT
}
```

---

## ⚡ Step 3: Fast Zero-Dependency Build

No Android Studio or Gradle installation required. The repository includes `build_apk.ps1` which uses the Android SDK command line tools:

```powershell
powershell -ExecutionPolicy Bypass -File .\build_apk.ps1
```

The script:
1. Compiles Java files with `javac` against `android.jar`.
2. Converts `.class` to `classes.dex` using `d8`.
3. Packages web assets and manifests with `aapt2`.
4. Aligns and signs the APK with `zipalign` and `apksigner`.
5. Outputs `TVPowerNew.apk`.

---

## 🧪 Step 4: Verification Checklist for AI Agents

1. **Verify Web Interface:** Run `curl http://<TV_IP>:8088/` and verify HTTP 200 response with self-contained assets.
2. **Verify D-Pad Response:** Test single tap on Up/Down/Left/Right/OK. Ensure no double-click occurs (guarded by frontend click suppression + backend 70ms debounce).
3. **Verify Input Menu:** Trigger `/api/action?action=input_source&key=TV_POWER_SECURE_KEY` and capture a screenshot via ADB (`screencap -p /sdcard/s.png`). Ensure the manufacturer's native overlay displays cleanly on-screen.
4. **Disconnect ADB:** Run `adb disconnect` to ensure the TV's USB ports remain fully accessible for flash drives without persistent debugging prompts.
