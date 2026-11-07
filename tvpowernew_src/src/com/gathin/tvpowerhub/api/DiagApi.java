package com.gathin.tvpowerhub.api;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import com.gathin.tvpowerhub.PowerHubService;
import com.gathin.tvpowerhub.TvAccessibilityService;
import com.gathin.tvpowerhub.UsbStorageHelper;
import com.gathin.tvpowerhub.core.CapabilityDetector;
import com.gathin.tvpowerhub.http.HttpRequest;
import com.gathin.tvpowerhub.http.HttpResponse;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;

public class DiagApi {

    public static void handlePing(Context context, int port, HttpRequest req, HttpResponse resp) throws Exception {
        JSONObject pingRes = new JSONObject();
        pingRes.put("ok", true);
        pingRes.put("status", "ok");
        pingRes.put("app", "tvpower");
        pingRes.put("id", PowerHubService.getTvDeviceId(context));
        pingRes.put("name", "TV Power House");
        pingRes.put("version", "3.0.0");
        pingRes.put("port", port);
        pingRes.put("time", System.currentTimeMillis());
        resp.sendJsonWithCors(200, pingRes);
    }

    public static void handleCapabilities(Context context, HttpRequest req, HttpResponse resp) throws Exception {
        JSONObject caps = CapabilityDetector.getCapabilities(context, true);
        resp.sendJsonWithCors(200, caps);
    }

    public static void handleStatus(Context context, int remainingSleepMinutes, HttpRequest req, HttpResponse resp) throws Exception {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        if (am != null) am.getMemoryInfo(mi);

        File internal = Environment.getDataDirectory();
        StatFs stat = new StatFs(internal.getPath());
        long totalBytes = stat.getTotalBytes();
        long availBytes = stat.getAvailableBytes();
        long usedBytes = totalBytes - availBytes;
        int pct = totalBytes > 0 ? (int) ((usedBytes * 100) / totalBytes) : 0;

        DisplayMetrics dm = new DisplayMetrics();
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (wm != null) wm.getDefaultDisplay().getRealMetrics(dm);

        long uptimeMs = SystemClock.elapsedRealtime();
        long hours = uptimeMs / 3600000;
        long mins = (uptimeMs % 3600000) / 60000;
        long secs = (uptimeMs % 60000) / 1000;
        String uptimeStr = hours + "h " + mins + "m " + secs + "s";

        String usbStatus = "none";
        try {
            JSONArray usbArray = UsbStorageHelper.getUsbDrivesJson(context);
            if (usbArray != null && usbArray.length() > 0) {
                usbStatus = usbArray.length() + " connected";
            }
        } catch (Exception ignored) {}

        JSONObject json = new JSONObject();
        json.put("status", "connected");
        json.put("mem_total_kb", String.valueOf(mi.totalMem / 1024));
        json.put("mem_avail_kb", String.valueOf(mi.availMem / 1024));
        json.put("storage_total", formatBytes(totalBytes));
        json.put("storage_used", formatBytes(usedBytes));
        json.put("storage_avail", formatBytes(availBytes));
        json.put("storage_pct", pct + "%");
        json.put("model", Build.MODEL);
        json.put("brand", Build.MANUFACTURER);
        json.put("android_ver", Build.VERSION.RELEASE);
        json.put("sdk", String.valueOf(Build.VERSION.SDK_INT));
        json.put("resolution", dm.widthPixels + "x" + dm.heightPixels);
        json.put("density", String.valueOf(dm.densityDpi));
        json.put("cpu", Build.HARDWARE != null && !Build.HARDWARE.isEmpty() ? Build.HARDWARE : Build.BOARD);
        json.put("uptime", uptimeStr);
        json.put("usb_drives", usbStatus);
        json.put("focus", TvAccessibilityService.getActiveApp(context));
        json.put("accessibility_enabled", TvAccessibilityService.isConnected());

        boolean allFiles = true;
        if (Build.VERSION.SDK_INT >= 30) {
            allFiles = Environment.isExternalStorageManager();
        }
        json.put("all_files_access", allFiles);
        json.put("pairing_token", PowerHubService.getOrGeneratePairingToken(context));
        json.put("sleep_timer_mins", remainingSleepMinutes);

        resp.sendJson(200, json);
    }

    private static String formatBytes(long bytes) {
        if (bytes <= 0) return "0 B";
        if (bytes >= 1073741824L) {
            return String.format(java.util.Locale.US, "%.1fG", bytes / 1073741824.0);
        } else if (bytes >= 1048576L) {
            return String.format(java.util.Locale.US, "%.1fM", bytes / 1048576.0);
        } else {
            return String.format(java.util.Locale.US, "%.1fK", bytes / 1024.0);
        }
    }
}
