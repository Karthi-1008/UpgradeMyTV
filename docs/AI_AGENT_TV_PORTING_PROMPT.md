# AI Agent Prompt: Auto-Adapt TVPowerHouse for Any Android TV

> **How to use this prompt:**
> Copy the prompt block below and paste it directly into your AI coding assistant (such as **Antigravity**, **Claude Code**, or **ChatGPT**). Make sure your PC is connected to your TV over Wi-Fi / ADB (`adb connect <TV_IP>:5555`).

---

### Copy-Paste Prompt for AI Coding Agent:

````markdown
You are an expert Android TV reverse engineer and systems developer. Your task is to inspect my connected Android TV over ADB, extract its hardware and software characteristics, and generate a custom device profile for the TVPowerHouse (UpgradeMyTV) application so that all remote control, input switching, and power features work 100% reliably.

### Instructions:

1. **Safety First**:
   - NEVER send power off, screen off, deep sleep, or reboot commands to the TV.
   - Do not disrupt the active ADB connection.

2. **Inspect the Connected TV**:
   Execute the following ADB inspection commands to extract device characteristics:
   ```bash
   adb shell getprop ro.product.manufacturer
   adb shell getprop ro.product.model
   adb shell getprop ro.product.name
   adb shell getprop ro.board.platform
   adb shell getprop ro.build.version.sdk
   adb shell getprop ro.build.version.release
   adb shell pm list packages -s | grep -E "input|source|tv|launcher|setting"
   adb shell dumpsys tv_input | grep -E "TvInputInfo|mId"
   ```

3. **Identify Key Hardware Components**:
   - Determine the exact manufacturer regex and model pattern.
   - Detect the input-source overlay or activity package used by this TV brand (e.g., TCL `com.tcl.sourcemenu`, Sony `com.sony.dtv.osat.inputs`, Xiaomi `com.mitv.tvhome`, etc.).
   - Identify default TV launcher packages to handle home navigation accurately.
   - Check if hardware video decoding renders on a separate DRM layer (`videoLayerBlackInScreenshot`).

4. **Generate the Device Profile**:
   Create a new profile file at `tvpowernew_src/assets/profiles/<brand>_<model>.json` (and sync to `web/assets/profiles/<brand>_<model>.json`) conforming to the schema in `docs/TV_PROFILE_TEMPLATE.json`.

5. **Register and Build**:
   - Add the new profile path into `CapabilityDetector.java` under `profileFiles`.
   - Run `build_apk.ps1` to compile and package the updated APK.
   - Install the APK on the TV via `adb install -r TVPowerNew.apk`.
   - Query `http://<TV_IP>:8080/api/v1/capabilities` and verify that the TV successfully matches your new profile.
````
