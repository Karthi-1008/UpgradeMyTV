package com.gathin.tvpowerhub.core;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import com.gathin.tvpowerhub.TvAccessibilityService;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class CapabilityDetector {
    private static final String TAG = "CapabilityDetector";
    private static JSONObject sCachedCapabilities = null;

    public static synchronized JSONObject getCapabilities(Context context, boolean refresh) {
        if (sCachedCapabilities != null && !refresh) {
            return sCachedCapabilities;
        }

        JSONObject caps = new JSONObject();
        try {
            PackageManager pm = context.getPackageManager();
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            AudioManager audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

            // 1. Device identity & hardware specs
            caps.put("brand", Build.MANUFACTURER);
            caps.put("model", Build.MODEL);
            caps.put("product", Build.PRODUCT);
            caps.put("device", Build.DEVICE);
            caps.put("board", Build.BOARD);
            caps.put("sdk", Build.VERSION.SDK_INT);
            caps.put("android_ver", Build.VERSION.RELEASE);

            JSONArray abisArray = new JSONArray();
            for (String abi : Build.SUPPORTED_ABIS) {
                abisArray.put(abi);
            }
            caps.put("supported_abis", abisArray);

            // 2. RAM and System Resources
            long totalRamMb = 0;
            long availRamMb = 0;
            if (am != null) {
                ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
                am.getMemoryInfo(mi);
                totalRamMb = mi.totalMem / (1024 * 1024);
                availRamMb = mi.availMem / (1024 * 1024);
            }
            caps.put("ram_total_mb", totalRamMb);
            caps.put("ram_avail_mb", availRamMb);

            // 3. Feature & Permission capabilities
            boolean hasLeanback = pm != null && pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK);
            caps.put("leanback", hasLeanback);

            boolean a11yGranted = TvAccessibilityService.isConnected();
            caps.put("accessibility_granted", a11yGranted);

            caps.put("ime_available", com.gathin.tvpowerhub.TvKeyboardService.isImeAvailable());
            caps.put("ime_connected", com.gathin.tvpowerhub.TvKeyboardService.isImeConnected());

            boolean allFilesAccess = true;
            if (Build.VERSION.SDK_INT >= 30) {
                allFilesAccess = Environment.isExternalStorageManager();
            }
            caps.put("all_files_access", allFilesAccess);

            boolean canInstallApk = true;
            if (Build.VERSION.SDK_INT >= 26 && pm != null) {
                canInstallApk = pm.canRequestPackageInstalls();
            }
            caps.put("can_install_apk", canInstallApk);

            boolean screenshotSupported = (Build.VERSION.SDK_INT >= 30) && a11yGranted;
            caps.put("screenshot_supported", screenshotSupported);

            boolean canSetVolume = true;
            if (audio != null) {
                if (Build.VERSION.SDK_INT >= 21) {
                    canSetVolume = !audio.isVolumeFixed();
                }
            }
            caps.put("can_set_volume", canSetVolume);

            // 4. Match Device Profile from assets
            JSONObject matchedProfile = loadAndMatchProfile(context, Build.MANUFACTURER, Build.MODEL);
            caps.put("profile", matchedProfile);

            sCachedCapabilities = caps;
        } catch (Exception e) {
            Log.e(TAG, "Error detecting capabilities: " + e.getMessage());
        }

        return caps;
    }

    private static JSONObject loadAndMatchProfile(Context context, String manufacturer, String model) {
        String[] profileFiles = new String[]{
                "profiles/tcl_c06.json",
                "profiles/sony_bravia.json",
                "profiles/xiaomi_patchwall.json",
                "profiles/amazon_firetv.json",
                "profiles/default.json"
        };

        for (String profilePath : profileFiles) {
            try (InputStream is = context.getAssets().open(profilePath)) {
                byte[] bytes = new byte[is.available()];
                int read = is.read(bytes);
                if (read > 0) {
                    JSONObject p = new JSONObject(new String(bytes, 0, read, StandardCharsets.UTF_8));
                    JSONObject match = p.optJSONObject("match");
                    if (match != null) {
                        String mfgPattern = match.optString("manufacturer", ".*");
                        String modelPattern = match.optString("model", ".*");
                        if (manufacturer.matches(mfgPattern) && model.matches(modelPattern)) {
                            Log.i(TAG, "Matched TV Profile: " + p.optString("id", profilePath));
                            return p;
                        }
                    } else if (profilePath.contains("default")) {
                        return p;
                    }
                }
            } catch (Exception ignored) {}
        }

        JSONObject fallback = new JSONObject();
        try {
            fallback.put("id", "generic_android_tv");
            fallback.put("name", "Generic Android TV");
        } catch (Exception ignored) {}
        return fallback;
    }
}
