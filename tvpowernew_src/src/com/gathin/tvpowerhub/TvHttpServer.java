package com.gathin.tvpowerhub;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.KeyEvent;
import android.view.WindowManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TvHttpServer {
    private static final String TAG = "TvHttpServer";
    private static final String MASTER_KEY = "TV_POWER_SECURE_KEY";
    private static final File WWW_DIR = new File("/data/local/tmp/www");

    private final Context mContext;
    private final int mPort;
    private ServerSocket mServerSocket;
    private boolean mRunning = false;
    private final ExecutorService mThreadPool = Executors.newFixedThreadPool(12);

    public TvHttpServer(Context context, int port) {
        this.mContext = context;
        this.mPort = port;
        TclInputHelper.init(context);
    }

    public synchronized void start() {
        if (mRunning) return;
        mRunning = true;
        new Thread(() -> {
            try {
                mServerSocket = new ServerSocket(mPort);
                Log.i(TAG, "TvHttpServer started on port " + mPort);
                while (mRunning) {
                    Socket client = mServerSocket.accept();
                    mThreadPool.execute(() -> handleClient(client));
                }
            } catch (Exception e) {
                if (mRunning) {
                    Log.e(TAG, "Server error on port " + mPort + ": " + e.getMessage());
                }
            }
        }).start();
    }

    public synchronized void stop() {
        mRunning = false;
        try {
            if (mServerSocket != null && !mServerSocket.isClosed()) {
                mServerSocket.close();
            }
        } catch (Exception ignored) {}
    }

    private void handleClient(Socket socket) {
        try {
            socket.setSoTimeout(15000);
            InputStream rawIn = socket.getInputStream();
            BufferedInputStream in = new BufferedInputStream(rawIn);
            OutputStream out = new BufferedOutputStream(socket.getOutputStream());

            // Read request header lines
            ByteArrayOutputStream lineBuf = new ByteArrayOutputStream();
            List<String> headers = new ArrayList<>();
            int prev = -1;
            int b;
            while ((b = in.read()) != -1) {
                if (prev == '\r' && b == '\n') {
                    byte[] rawLine = lineBuf.toByteArray();
                    String line = new String(rawLine, 0, rawLine.length - 1, StandardCharsets.UTF_8).trim();
                    lineBuf.reset();
                    if (line.isEmpty()) {
                        break; // End of HTTP headers
                    }
                    headers.add(line);
                } else {
                    lineBuf.write(b);
                }
                prev = b;
            }

            if (headers.isEmpty()) {
                socket.close();
                return;
            }

            String requestLine = headers.get(0);
            String[] parts = requestLine.split(" ");
            if (parts.length < 2) {
                socket.close();
                return;
            }

            String method = parts[0];
            String uri = parts[1];

            // Parse headers
            Map<String, String> headerMap = new HashMap<>();
            for (int i = 1; i < headers.size(); i++) {
                String h = headers.get(i);
                int colon = h.indexOf(':');
                if (colon > 0) {
                    headerMap.put(h.substring(0, colon).trim().toLowerCase(), h.substring(colon + 1).trim());
                }
            }

            int contentLength = 0;
            if (headerMap.containsKey("content-length")) {
                try {
                    contentLength = Integer.parseInt(headerMap.get("content-length"));
                } catch (Exception ignored) {}
            }

            // Split path and query parameters
            String path = uri;
            String queryString = "";
            int qIdx = uri.indexOf('?');
            if (qIdx >= 0) {
                path = uri.substring(0, qIdx);
                queryString = uri.substring(qIdx + 1);
            }

            Map<String, String> params = parseQueryString(queryString);

            // Handle API routes
            if (path.equals("/cgi-bin/api") || path.startsWith("/api/")) {
                handleApi(method, path, params, headerMap, in, contentLength, out);
            } else {
                handleStaticFile(path, out);
            }

            out.flush();
            socket.close();
        } catch (Exception e) {
            try { socket.close(); } catch (Exception ignored) {}
        }
    }

    private void handleApi(String method, String path, Map<String, String> params, Map<String, String> headers, InputStream in, int contentLength, OutputStream out) throws Exception {
        String key = params.get("key");
        if (key == null || !key.equals(MASTER_KEY)) {
            sendJson(out, 401, new JSONObject().put("status", "error").put("message", "Unauthorized"));
            return;
        }

        String action = params.get("action");
        if (action == null && path.startsWith("/api/")) {
            action = path.substring(5);
        }

        if (action == null) {
            sendJson(out, 400, new JSONObject().put("status", "error").put("message", "Missing action"));
            return;
        }

        switch (action) {
            case "key": {
                String code = params.get("code");
                if (code != null) {
                    final int codeInt;
                    int parsed = -1;
                    try { parsed = Integer.parseInt(code); } catch (Exception ignored) {}
                    codeInt = parsed;

                    // Handle instantly
                    if (codeInt == 24) { // VOLUME_UP
                        AudioManager am = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
                        if (am != null) am.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI);
                    } else if (codeInt == 25) { // VOLUME_DOWN
                        AudioManager am = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
                        if (am != null) am.adjustVolume(AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI);
                    } else if (codeInt == 164) { // VOLUME_MUTE
                        AudioManager am = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
                        if (am != null) am.adjustVolume(AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI);
                    } else if (codeInt == 3) { // HOME
                        TvAccessibilityService.doHome(mContext);
                    } else if (codeInt == 4) { // BACK
                        TvAccessibilityService.doBack();
                    } else if (codeInt == 26) { // POWER
                        TvAccessibilityService.doPower(mContext);
                    } else if (codeInt == 176 || codeInt == 82) { // SETTINGS / MENU
                        TvAccessibilityService.doSettings(mContext);
                    } else if (codeInt == 178) { // INPUT SOURCE
                        TvAccessibilityService.doInputSource(mContext);
                    } else if (codeInt == 223) { // SCREEN OFF
                        TvAccessibilityService.doScreenOff(mContext);
                    } else if (codeInt == 187) { // RECENTS
                        TvAccessibilityService.doRecents();
                    } else if (codeInt == 67) { // BACKSPACE
                        TvAccessibilityService.doBackspace();
                    } else if (codeInt == 85 || codeInt == 87 || codeInt == 88 || codeInt == 89 || codeInt == 90 || codeInt == 126 || codeInt == 127) { // MEDIA / SEEK
                        TvAccessibilityService.sendMedia(mContext, codeInt);
                    } else {
                        // D-Pad (19, 20, 21, 22, 23, 66) & all other navigation keys
                        TvAccessibilityService.sendDpad(codeInt);
                    }
                }
                sendJson(out, 200, new JSONObject().put("status", "ok").put("action", "key").put("code", code));
                break;
            }
            case "text": {
                String text = params.get("text");
                if (text != null) {
                    String decoded = URLDecoder.decode(text, "UTF-8");
                    boolean typed = TvAccessibilityService.sendText(decoded);
                    sendJson(out, 200, new JSONObject().put("status", "ok").put("action", "text").put("sent", decoded).put("handled", typed));
                } else {
                    sendJson(out, 400, new JSONObject().put("status", "error").put("message", "Missing text"));
                }
                break;
            }
            case "input_source": {
                String source = params.get("source");
                boolean handled = TvAccessibilityService.switchInput(mContext, source);
                sendJson(out, 200, new JSONObject().put("status", "ok").put("action", "input_source").put("source", source).put("handled", handled));
                break;
            }
            case "screenshot": {
                File screenFile = new File(mContext.getExternalFilesDir(null), "screen.png");
                boolean captured = TvAccessibilityService.takeScreenCapture(screenFile);
                if (!captured) {
                    screenFile = new File(mContext.getFilesDir(), "screen.png");
                    TvAccessibilityService.takeScreenCapture(screenFile);
                }
                long ts = System.currentTimeMillis();
                sendJson(out, 200, new JSONObject().put("status", "ok").put("action", "screenshot").put("url", "/screen.png?t=" + ts));
                break;
            }
            case "boost": {
                mThreadPool.execute(() -> {
                    try {
                        ActivityManager am = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
                        if (am != null) {
                            List<ActivityManager.RunningAppProcessInfo> processes = am.getRunningAppProcesses();
                            if (processes != null) {
                                for (ActivityManager.RunningAppProcessInfo pi : processes) {
                                    if (!pi.processName.equals(mContext.getPackageName())) {
                                        am.killBackgroundProcesses(pi.processName);
                                    }
                                }
                            }
                        }
                        System.gc();
                    } catch (Exception ignored) {}
                });
                ActivityManager am = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
                ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
                if (am != null) am.getMemoryInfo(mi);
                long availKb = mi.availMem / 1024;
                sendJson(out, 200, new JSONObject().put("status", "ok").put("action", "boost").put("mem_avail_kb", String.valueOf(availKb)));
                break;
            }
            case "open_url": {
                String targetUrl = params.get("url");
                String targetApp = params.get("target");
                if (targetUrl != null) {
                    String decodedUrl = URLDecoder.decode(targetUrl, "UTF-8");
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(decodedUrl));
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        if ("smarttube".equalsIgnoreCase(targetApp)) {
                            try {
                                intent.setPackage("org.smarttube.stable");
                                mContext.startActivity(intent);
                            } catch (Exception e1) {
                                try {
                                    intent.setPackage("com.liskovsoft.videomanager");
                                    mContext.startActivity(intent);
                                } catch (Exception e2) {
                                    intent.setPackage(null);
                                    mContext.startActivity(intent);
                                }
                            }
                        } else if ("youtube".equalsIgnoreCase(targetApp)) {
                            try {
                                intent.setPackage("com.google.android.youtube.tv");
                                mContext.startActivity(intent);
                            } catch (Exception e1) {
                                intent.setPackage(null);
                                mContext.startActivity(intent);
                            }
                        } else if ("browser".equalsIgnoreCase(targetApp)) {
                            try {
                                intent.setPackage("com.tcl.browser");
                                mContext.startActivity(intent);
                            } catch (Exception e1) {
                                intent.setPackage(null);
                                mContext.startActivity(intent);
                            }
                        } else {
                            mContext.startActivity(intent);
                        }
                    } catch (Exception ignored) {}
                    sendJson(out, 200, new JSONObject().put("status", "ok").put("action", "open_url").put("url", decodedUrl).put("target", targetApp));
                } else {
                    sendJson(out, 400, new JSONObject().put("status", "error").put("message", "Missing url"));
                }
                break;
            }
            case "status": {
                JSONObject statusObj = new JSONObject();
                statusObj.put("status", "connected");
                statusObj.put("target", "TCL Smart TV");

                ActivityManager am = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
                ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
                if (am != null) am.getMemoryInfo(mi);
                statusObj.put("mem_total_kb", String.valueOf(mi.totalMem / 1024));
                statusObj.put("mem_avail_kb", String.valueOf(mi.availMem / 1024));

                // Storage stats
                try {
                    StatFs stat = new StatFs(Environment.getDataDirectory().getPath());
                    long totalBytes = stat.getTotalBytes();
                    long freeBytes = stat.getAvailableBytes();
                    long usedBytes = totalBytes - freeBytes;
                    int pct = totalBytes > 0 ? (int) ((usedBytes * 100) / totalBytes) : 0;
                    statusObj.put("storage_total", formatStorage(totalBytes));
                    statusObj.put("storage_used", formatStorage(usedBytes));
                    statusObj.put("storage_avail", formatStorage(freeBytes));
                    statusObj.put("storage_pct", pct + "%");
                } catch (Exception ignored) {}

                statusObj.put("model", Build.MODEL != null ? Build.MODEL : "Smart TV");
                statusObj.put("brand", Build.BRAND != null ? Build.BRAND : "TCL");
                statusObj.put("android_ver", Build.VERSION.RELEASE != null ? Build.VERSION.RELEASE : "11");
                statusObj.put("sdk", String.valueOf(Build.VERSION.SDK_INT));

                // Display
                WindowManager wm = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
                if (wm != null) {
                    DisplayMetrics dm = new DisplayMetrics();
                    wm.getDefaultDisplay().getRealMetrics(dm);
                    statusObj.put("resolution", dm.widthPixels + "x" + dm.heightPixels);
                    statusObj.put("density", String.valueOf(dm.densityDpi));
                }

                statusObj.put("cpu", "MediaTek Quad-Core Cortex-A55");

                // Uptime using monotonic SystemClock.elapsedRealtime()
                long uptimeMs = android.os.SystemClock.elapsedRealtime();
                long totalSecs = Math.max(0, uptimeMs / 1000);
                long days = totalSecs / 86400;
                long hours = (totalSecs % 86400) / 3600;
                long mins = (totalSecs % 3600) / 60;
                long secs = totalSecs % 60;
                String uptimeStr;
                if (days > 0) {
                    uptimeStr = days + "d " + hours + "h " + mins + "m";
                } else if (hours > 0) {
                    uptimeStr = hours + "h " + mins + "m";
                } else {
                    uptimeStr = mins + "m " + secs + "s";
                }
                statusObj.put("uptime", uptimeStr);

                // USB detection
                JSONArray usbArray = getUsbDrives();
                statusObj.put("usb_drives", usbArray.length() > 0 ? usbArray.toString() : "none");

                // Focus
                statusObj.put("focus", TvAccessibilityService.getActiveApp(mContext));

                sendJson(out, 200, statusObj);
                break;
            }
            case "list_files": {
                String reqPath = params.get("path");
                if (reqPath == null || reqPath.isEmpty()) {
                    reqPath = "/storage/emulated/0/Download";
                }
                reqPath = URLDecoder.decode(reqPath, "UTF-8");
                File dir = new File(reqPath);
                if (!dir.exists() || !dir.isDirectory()) {
                    dir = new File("/storage/emulated/0");
                    reqPath = "/storage/emulated/0";
                }

                JSONArray drives = getUsbDrives();
                JSONArray items = new JSONArray();

                File[] files = dir.listFiles();
                if (files != null) {
                    for (File f : files) {
                        if (f.getName().startsWith(".")) continue;
                        JSONObject item = new JSONObject();
                        item.put("name", f.getName());
                        item.put("is_dir", f.isDirectory());
                        item.put("size", f.isDirectory() ? 0 : f.length());
                        items.put(item);
                    }
                }

                String parent = dir.getParent();
                if (parent == null) parent = "/storage";

                JSONObject res = new JSONObject();
                res.put("status", "ok");
                res.put("path", dir.getAbsolutePath());
                res.put("parent", parent);
                res.put("drives", drives);
                res.put("items", items);
                sendJson(out, 200, res);
                break;
            }
            case "download_file": {
                String reqPath = params.get("path");
                if (reqPath == null || reqPath.isEmpty()) {
                    sendJson(out, 400, new JSONObject().put("status", "error").put("message", "Missing path"));
                    return;
                }
                reqPath = URLDecoder.decode(reqPath, "UTF-8");
                File target = new File(reqPath);
                if (!target.exists() || target.isDirectory()) {
                    sendJson(out, 404, new JSONObject().put("status", "error").put("message", "File not found"));
                    return;
                }

                String headersStr = "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: application/octet-stream\r\n" +
                        "Content-Disposition: attachment; filename=\"" + target.getName() + "\"\r\n" +
                        "Content-Length: " + target.length() + "\r\n" +
                        "Access-Control-Allow-Origin: *\r\n\r\n";
                out.write(headersStr.getBytes(StandardCharsets.UTF_8));

                FileInputStream fis = new FileInputStream(target);
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = fis.read(buf)) != -1) {
                    out.write(buf, 0, n);
                }
                fis.close();
                break;
            }
            case "upload_file": {
                String destDir = params.get("dest");
                String name = params.get("name");
                if (destDir == null || destDir.isEmpty()) destDir = "/storage/emulated/0/Download";
                if (name == null || name.isEmpty()) name = "uploaded_" + System.currentTimeMillis();

                destDir = URLDecoder.decode(destDir, "UTF-8");
                name = URLDecoder.decode(name, "UTF-8");

                File targetDir = new File(destDir);
                targetDir.mkdirs();
                File targetFile = new File(targetDir, name);

                FileOutputStream fos = new FileOutputStream(targetFile);
                byte[] buf = new byte[64 * 1024];
                int remaining = contentLength;
                while (remaining > 0) {
                    int toRead = Math.min(buf.length, remaining);
                    int r = in.read(buf, 0, toRead);
                    if (r < 0) break;
                    fos.write(buf, 0, r);
                    remaining -= r;
                }
                fos.close();

                sendJson(out, 200, new JSONObject().put("status", "ok").put("action", "upload").put("path", targetFile.getAbsolutePath()).put("size", targetFile.length()));
                break;
            }
            case "delete_file": {
                String reqPath = params.get("path");
                if (reqPath == null) {
                    sendJson(out, 400, new JSONObject().put("status", "error").put("message", "Missing path"));
                    return;
                }
                reqPath = URLDecoder.decode(reqPath, "UTF-8");
                File f = new File(reqPath);
                boolean deleted = deleteRecursive(f);
                if (deleted) {
                    sendJson(out, 200, new JSONObject().put("status", "ok").put("action", "delete").put("path", reqPath));
                } else {
                    sendJson(out, 500, new JSONObject().put("status", "error").put("message", "Failed to delete"));
                }
                break;
            }
            case "copy_file": {
                String src = params.get("src");
                String dest = params.get("dest");
                if (src == null || dest == null) {
                    sendJson(out, 400, new JSONObject().put("status", "error").put("message", "Missing src or dest"));
                    return;
                }
                src = URLDecoder.decode(src, "UTF-8");
                dest = URLDecoder.decode(dest, "UTF-8");
                File srcFile = new File(src);
                File destFile = new File(dest);
                boolean copied = copyRecursive(srcFile, destFile);
                if (copied) {
                    sendJson(out, 200, new JSONObject().put("status", "ok").put("action", "copy").put("src", src).put("dest", dest));
                } else {
                    sendJson(out, 500, new JSONObject().put("status", "error").put("message", "Failed to copy"));
                }
                break;
            }
            default:
                sendJson(out, 400, new JSONObject().put("status", "error").put("message", "Unknown action: " + action));
                break;
        }
    }

    private void handleStaticFile(String path, OutputStream out) throws Exception {
        String cleanPath = path;
        if (cleanPath.equals("/") || cleanPath.isEmpty()) {
            cleanPath = "/index.html";
        }

        // Special handling for screen capture image
        if (cleanPath.equals("/screen.png")) {
            File screenExt = new File(mContext.getExternalFilesDir(null), "screen.png");
            File screenInt = new File(mContext.getFilesDir(), "screen.png");
            File screenTmp = new File("/data/local/tmp/www/screen.png");
            File screenSd = new File("/sdcard/screen.png");
            File screenTarget = (screenExt != null && screenExt.exists()) ? screenExt :
                    ((screenInt != null && screenInt.exists()) ? screenInt :
                    (screenTmp.exists() ? screenTmp : (screenSd.exists() ? screenSd : null)));
            if (screenTarget != null) {
                String headers = "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: image/png\r\n" +
                        "Content-Length: " + screenTarget.length() + "\r\n" +
                        "Access-Control-Allow-Origin: *\r\n\r\n";
                out.write(headers.getBytes(StandardCharsets.UTF_8));
                FileInputStream fis = new FileInputStream(screenTarget);
                byte[] buf = new byte[32 * 1024];
                int n;
                while ((n = fis.read(buf)) != -1) {
                    out.write(buf, 0, n);
                }
                fis.close();
                return;
            }
        }

        // Try reading from app assets first (Bundled inside APK)
        try {
            String assetPath = "web" + (cleanPath.startsWith("/") ? cleanPath : "/" + cleanPath);
            InputStream is = mContext.getAssets().open(assetPath);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[32 * 1024];
            int n;
            while ((n = is.read(buf)) != -1) {
                baos.write(buf, 0, n);
            }
            is.close();
            byte[] bytes = baos.toByteArray();
            String mime = getMimeType(cleanPath);
            String headers = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: " + mime + "\r\n" +
                    "Content-Length: " + bytes.length + "\r\n" +
                    "Access-Control-Allow-Origin: *\r\n\r\n";
            out.write(headers.getBytes(StandardCharsets.UTF_8));
            out.write(bytes);
            return;
        } catch (Exception ignored) {}

        // Fallback: Try reading from /data/local/tmp/www
        File file = new File(WWW_DIR, cleanPath.startsWith("/") ? cleanPath.substring(1) : cleanPath);
        if (file.exists() && file.isFile()) {
            String mime = getMimeType(file.getName());
            String headers = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: " + mime + "\r\n" +
                    "Content-Length: " + file.length() + "\r\n" +
                    "Access-Control-Allow-Origin: *\r\n\r\n";
            out.write(headers.getBytes(StandardCharsets.UTF_8));
            FileInputStream fis = new FileInputStream(file);
            byte[] buf = new byte[32 * 1024];
            int n;
            while ((n = fis.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
            fis.close();
            return;
        }

        // Not Found
        String notFound = "HTTP/1.1 404 Not Found\r\nContent-Length: 9\r\n\r\nNot Found";
        out.write(notFound.getBytes(StandardCharsets.UTF_8));
    }

    private void sendJson(OutputStream out, int status, JSONObject obj) throws Exception {
        byte[] bytes = obj.toString(2).getBytes(StandardCharsets.UTF_8);
        String headers = "HTTP/1.1 " + status + " OK\r\n" +
                "Content-Type: application/json; charset=UTF-8\r\n" +
                "Content-Length: " + bytes.length + "\r\n" +
                "Access-Control-Allow-Origin: *\r\n\r\n";
        out.write(headers.getBytes(StandardCharsets.UTF_8));
        out.write(bytes);
    }

    private static Map<String, String> parseQueryString(String qs) {
        Map<String, String> map = new HashMap<>();
        if (qs == null || qs.isEmpty()) return map;
        String[] pairs = qs.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf('=');
            if (idx > 0) {
                map.put(pair.substring(0, idx), pair.substring(idx + 1));
            } else if (!pair.isEmpty()) {
                map.put(pair, "");
            }
        }
        return map;
    }

    private static String getMimeType(String name) {
        String lower = name.toLowerCase();
        if (lower.endsWith(".html")) return "text/html; charset=UTF-8";
        if (lower.endsWith(".css")) return "text/css; charset=UTF-8";
        if (lower.endsWith(".js")) return "application/javascript; charset=UTF-8";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".woff2")) return "font/woff2";
        if (lower.endsWith(".json")) return "application/json";
        return "application/octet-stream";
    }

    private static String formatStorage(long bytes) {
        if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
        if (bytes < 1024 * 1024 * 1024) return (bytes / (1024 * 1024)) + " MB";
        return String.format(java.util.Locale.US, "%.1fG", (double) bytes / (1024 * 1024 * 1024));
    }

    private static JSONArray getUsbDrives() {
        JSONArray drives = new JSONArray();
        try {
            File storage = new File("/storage");
            File[] files = storage.listFiles();
            if (files != null) {
                for (File f : files) {
                    String name = f.getName();
                    if (!name.equals("emulated") && !name.equals("self") && f.isDirectory()) {
                        JSONObject obj = new JSONObject();
                        obj.put("name", "USB: " + name);
                        obj.put("path", f.getAbsolutePath());
                        obj.put("type", "usb");
                        drives.put(obj);
                    }
                }
            }
        } catch (Exception ignored) {}
        return drives;
    }

    private static boolean deleteRecursive(File f) {
        if (!f.exists()) return true;
        if (f.isDirectory()) {
            File[] subs = f.listFiles();
            if (subs != null) {
                for (File sub : subs) {
                    deleteRecursive(sub);
                }
            }
        }
        return f.delete();
    }

    private static boolean copyRecursive(File src, File dest) {
        try {
            if (src.isDirectory()) {
                dest.mkdirs();
                File[] subs = src.listFiles();
                if (subs != null) {
                    for (File sub : subs) {
                        copyRecursive(sub, new File(dest, sub.getName()));
                    }
                }
                return true;
            } else {
                dest.getParentFile().mkdirs();
                FileInputStream in = new FileInputStream(src);
                FileOutputStream out = new FileOutputStream(dest);
                byte[] buf = new byte[32 * 1024];
                int n;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                }
                in.close();
                out.close();
                return true;
            }
        } catch (Exception e) {
            return false;
        }
    }
}
