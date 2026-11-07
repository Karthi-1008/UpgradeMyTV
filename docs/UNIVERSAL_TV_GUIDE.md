# Universal Android TV Support Guide

TVPowerHouse is designed from the ground up to run on **any Android TV, Google TV, or Fire TV device** (API 26 through API 36+), ranging from budget 1 GB RAM 32-bit ARMv7 sets to premium 4K Google TV streamers.

---

## 1. Architecture Overview

```
                      +-----------------------------+
                      |      Web / Phone Client     |
                      +--------------+--------------+
                                     |
               +---------------------+---------------------+
               | (Sub-40ms WebSocket)| (REST / HTTP Keep-Alive)
               v                     v
+-----------------------------------------------------------+
|                   TVPowerHouse Backend                    |
|                                                           |
|  [HttpRequest] -----> [Router] -----> [Api Handlers]      |
|                              |                            |
|             +----------------+----------------+           |
|             v                                 v           |
|  [CapabilityDetector]               [KeyInjector]         |
|  - Queries System & Features        - Tier 1: Daemon/Shell|
|  - Matches JSON Device Profile      - Tier 2: TV IME      |
|                                     - Tier 3: A11y Engine |
+-----------------------------------------------------------+
```

### Key Principles:
1. **Zero External Dependencies**: Uses only standard Android framework APIs. No external JARs or bloated libraries.
2. **Dynamic Capability Detection**: Features are never hard-coded. The app queries the TV's hardware, API level, and permissions at runtime.
3. **Pluggable Device Profiles**: TV brand-specific quirks (such as hardware input menus, standby keys, and launcher packages) are defined as simple JSON files in `assets/profiles/`.

---

## 2. Included Profiles

| Profile ID | Manufacturer | Target Devices | Special Features |
|---|---|---|---|
| `tcl_c06` | TCL | TCL BeyondTV, C06, Android 11 RTD2851 sets | Native `ShowWindowService` hardware input menu overlay |
| `sony_bravia` | Sony | Sony Bravia Android TVs | `com.sony.dtv.osat.inputs` native launcher |
| `xiaomi_patchwall` | Xiaomi | Mi TV, Redmi TV, Mi Box | PatchWall TV home integration |
| `amazon_firetv` | Amazon | Fire TV Edition TVs, Fire TV Sticks | Non-Play-Services compatibility mode |
| `generic_android_tv` | Any | Any standard Android / Google TV set | Fallback to universal `KEYCODE_TV_INPUT` and standard Leanback |

---

## 3. How to Add Support for Your TV

If your TV has custom input-switching buttons, unique launchers, or proprietary picture modes, follow these 3 steps:

### Step 1: Inspect Your TV with ADB
Connect your PC to your TV over Wi-Fi:
```bash
adb connect <TV_IP>:5555
adb shell getprop ro.product.manufacturer
adb shell getprop ro.product.model
```

Find installed input and TV packages:
```bash
adb shell pm list packages -s | grep -E "input|source|tv"
```

### Step 2: Create a Custom Profile
Copy [`docs/TV_PROFILE_TEMPLATE.json`](file:///c:/Users/karth/Downloads/UpgradeMyTV/docs/TV_PROFILE_TEMPLATE.json) to `tvpowernew_src/assets/profiles/<brand>_<model>.json`.

Example for Philips TV (`philips_android.json`):
```json
{
  "id": "philips_android",
  "name": "Philips Android TV",
  "match": {
    "manufacturer": "(?i)Philips|TPVision",
    "model": ".*"
  },
  "sourceMenu": [
    {
      "type": "key",
      "code": 178,
      "description": "Standard TV Input"
    }
  ],
  "sleep": [
    {
      "type": "key",
      "code": 223,
      "description": "Soft Sleep Key"
    }
  ]
}
```

### Step 3: Register in CapabilityDetector
Add your new profile to `CapabilityDetector.java` inside `profileFiles`:
```java
String[] profileFiles = new String[]{
    "profiles/philips_android.json",
    "profiles/tcl_c06.json",
    ...
};
```

Run `build_apk.ps1`, install to the TV, and query:
```bash
curl http://<TV_IP>:8080/api/v1/capabilities
```
Verify that `"profile": {"id": "philips_android"}` is returned!

---

## 4. Troubleshooting Device Quirks

- **Black screen on screenshot**: Some TV SoCs (like Realtek RTD2851 and MediaTek) render hardware-accelerated video on a secure hardware overlay that Android's `takeScreenshot` API cannot capture. TVPowerHouse gracefully detects this and indicates video overlay mode.
- **Power Off drops Wi-Fi**: Most Android TV chipsets enter deep low-power states on `KEYCODE_POWER`, shutting down the Wi-Fi chip. Use **Soft Power** (`KEYCODE_SLEEP` / code `223`), which blanks the screen and backlight while keeping the Wi-Fi radio and TVPowerHouse server responsive for instant wake-up.
