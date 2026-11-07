package com.gathin.tvpowerhub.api;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import com.gathin.tvpowerhub.PowerHubService;
import com.gathin.tvpowerhub.TvAccessibilityService;
import com.gathin.tvpowerhub.http.HttpRequest;
import com.gathin.tvpowerhub.http.HttpResponse;

import org.json.JSONObject;

import java.io.File;
import java.net.URLDecoder;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class SystemApi {
    private static final String TAG = "SystemApi";
    private static final ExecutorService sThreadPool = Executors.newCachedThreadPool();
    private static final ScheduledExecutorService sSleepExecutor = Executors.newSingleThreadScheduledExecutor();
    private static ScheduledFuture<?> sActiveSleepTimer = null;
    private static int sRemainingSleepMinutes = 0;

    public static int getRemainingSleepMinutes() {
        return sRemainingSleepMinutes;
    }

    public static void handleScreenshot(Context context, HttpRequest req, HttpResponse resp) throws Exception {
        String suppliedKey = req.getParam("key");
        if (suppliedKey == null) suppliedKey = req.getHeader("x-tvhub-key");

        File screenFile = new File(context.getExternalFilesDir(null), "screen.png");
        boolean captured = TvAccessibilityService.takeScreenCapture(screenFile);
        if (!captured) {
            screenFile = new File(context.getFilesDir(), "screen.png");
            captured = TvAccessibilityService.takeScreenCapture(screenFile);
        }

        if (captured) {
            long ts = System.currentTimeMillis();
            JSONObject res = new JSONObject();
            res.put("status", "ok");
            res.put("action", "screenshot");
            res.put("captured", true);
            res.put("url", "/screen.png?t=" + ts + "&key=" + suppliedKey);
            resp.sendJson(200, res);
        } else {
            resp.sendError(500, "SCREENSHOT_FAILED", "Screenshot capture failed", "Ensure Accessibility Service is enabled");
        }
    }

    public static void handleBoost(Context context, HttpRequest req, HttpResponse resp) throws Exception {
        sThreadPool.execute(() -> {
            try {
                ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
                if (am != null) {
                    List<ActivityManager.RunningAppProcessInfo> processes = am.getRunningAppProcesses();
                    if (processes != null) {
                        for (ActivityManager.RunningAppProcessInfo pi : processes) {
                            if (!pi.processName.equals(context.getPackageName()) &&
                                    pi.importance >= ActivityManager.RunningAppProcessInfo.IMPORTANCE_BACKGROUND) {
                                am.killBackgroundProcesses(pi.processName);
                            }
                        }
                    }
                }
                System.gc();
            } catch (Exception ignored) {}
        });

        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        if (am != null) am.getMemoryInfo(mi);

        long availKb = mi.availMem / 1024;
        long availMb = mi.availMem / (1024 * 1024);
        long totalMb = mi.totalMem / (1024 * 1024);

        JSONObject res = new JSONObject();
        res.put("status", "ok");
        res.put("action", "boost");
        res.put("mem_avail_kb", String.valueOf(availKb));
        res.put("avail_mb", availMb);
        res.put("total_mb", totalMb);
        resp.sendJson(200, res);
    }

    public static void handleOpenUrl(Context context, HttpRequest req, HttpResponse resp) throws Exception {
        String targetUrl = req.getParam("url");
        String target = req.getParam("target"); // "smarttube", "youtube", "browser"

        if (targetUrl == null || targetUrl.isEmpty()) {
            resp.sendError(400, "BAD_REQUEST", "Missing 'url' parameter", "Provide the URL to open");
            return;
        }

        String decodedUrl = URLDecoder.decode(targetUrl, "UTF-8");
        if (!decodedUrl.startsWith("http://") && !decodedUrl.startsWith("https://")) {
            decodedUrl = "https://" + decodedUrl;
        }

        try {
            PackageManager pm = context.getPackageManager();
            String chosenPkg = null;

            if ("smarttube".equalsIgnoreCase(target)) {
                String[] candidates = new String[]{
                        "org.smarttube.stable",
                        "com.teamsmart.videomanager.tv",
                        "com.liskovsoft.smarttubetv.beta",
                        "com.liskovsoft.videomanager"
                };
                for (String c : candidates) {
                    try {
                        pm.getPackageInfo(c, 0);
                        chosenPkg = c;
                        break;
                    } catch (Exception ignored) {}
                }
            } else if ("youtube".equalsIgnoreCase(target)) {
                String[] candidates = new String[]{
                        "com.google.android.youtube.tv",
                        "com.google.android.youtube"
                };
                for (String c : candidates) {
                    try {
                        pm.getPackageInfo(c, 0);
                        chosenPkg = c;
                        break;
                    } catch (Exception ignored) {}
                }
            } else if ("browser".equalsIgnoreCase(target)) {
                String[] candidates = new String[]{
                        "com.tcl.browser",
                        "org.mozilla.tv.firefox",
                        "com.android.chrome"
                };
                for (String c : candidates) {
                    try {
                        pm.getPackageInfo(c, 0);
                        chosenPkg = c;
                        break;
                    } catch (Exception ignored) {}
                }
            }

            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(decodedUrl));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (chosenPkg != null) {
                intent.setPackage(chosenPkg);
            }

            context.startActivity(intent);

            JSONObject res = new JSONObject();
            res.put("status", "ok");
            res.put("action", "open_url");
            res.put("url", decodedUrl);
            res.put("package", chosenPkg != null ? chosenPkg : "default_handler");
            resp.sendJson(200, res);
        } catch (Exception e) {
            resp.sendError(500, "LAUNCH_FAILED", "Failed to launch URL: " + e.getMessage(), null);
        }
    }

    public static synchronized void handleSleepTimer(Context context, HttpRequest req, HttpResponse resp) throws Exception {
        String minutesStr = req.getParam("minutes");
        int minutes = 0;
        try {
            minutes = Integer.parseInt(minutesStr);
        } catch (Exception ignored) {}

        if (sActiveSleepTimer != null) {
            sActiveSleepTimer.cancel(true);
            sActiveSleepTimer = null;
        }

        sRemainingSleepMinutes = Math.max(0, minutes);

        if (sRemainingSleepMinutes > 0) {
            sActiveSleepTimer = sSleepExecutor.schedule(() -> {
                try {
                    sRemainingSleepMinutes = 0;
                    // Trigger screen off (M7 Soft Power)
                    TvAccessibilityService.doScreenOff(context);
                } catch (Exception ignored) {}
            }, sRemainingSleepMinutes, TimeUnit.MINUTES);
        }

        JSONObject res = new JSONObject();
        res.put("status", "ok");
        res.put("action", "sleep_timer");
        res.put("minutes", sRemainingSleepMinutes);
        resp.sendJson(200, res);
    }

    public static void handleNotify(Context context, HttpRequest req, HttpResponse resp) throws Exception {
        String title = req.getParam("title", "TV Power");
        String message = req.getParam("message", "");
        if (message.isEmpty()) {
            message = req.getParam("text", "");
        }

        final String toastText = (title.isEmpty() ? "" : title + ": ") + message;
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                Toast.makeText(context.getApplicationContext(), toastText, Toast.LENGTH_LONG).show();
            } catch (Exception ignored) {}
        });

        JSONObject res = new JSONObject();
        res.put("status", "ok");
        res.put("action", "notify");
        res.put("delivered", true);
        resp.sendJson(200, res);
    }
}
