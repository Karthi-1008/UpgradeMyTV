package com.gathin.tvpowerhub.api;

import android.content.Context;
import android.os.StatFs;
import android.util.Log;

import com.gathin.tvpowerhub.UsbStorageHelper;
import com.gathin.tvpowerhub.http.HttpRequest;
import com.gathin.tvpowerhub.http.HttpResponse;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

public class FilesApi {
    private static final String TAG = "FilesApi";

    public static void handleListFiles(Context context, HttpRequest req, HttpResponse resp) throws Exception {
        String reqPath = req.getParam("path");
        if (reqPath == null || reqPath.isEmpty()) reqPath = "/storage/emulated/0/Download";
        reqPath = URLDecoder.decode(reqPath, "UTF-8");
        File dir = new File(reqPath);

        if (!UsbStorageHelper.isPathAllowed(dir, context)) {
            resp.sendError(403, "ACCESS_DENIED", "Access to this path is not permitted", null);
            return;
        }

        if (!dir.exists() || !dir.isDirectory()) {
            resp.sendError(404, "DIR_NOT_FOUND", "Folder not found or drive was disconnected", null);
            return;
        }

        JSONArray drives = UsbStorageHelper.getUsbDrivesJson(context);
        JSONArray items = new JSONArray();

        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.getName().startsWith(".")) continue;
                JSONObject item = new JSONObject();
                String nameLower = f.getName().toLowerCase();
                item.put("name", f.getName());
                item.put("is_dir", f.isDirectory());
                item.put("size", f.isDirectory() ? 0 : f.length());
                item.put("size_str", UsbStorageHelper.formatBytes(f.length()));
                item.put("is_apk", nameLower.endsWith(".apk"));
                item.put("is_video", nameLower.matches(".*\\.(mp4|mkv|webm|avi|mov|ts|m4v)$"));
                item.put("is_audio", nameLower.matches(".*\\.(mp3|wav|flac|aac|m4a|ogg)$"));
                item.put("is_image", nameLower.matches(".*\\.(jpg|jpeg|png|webp|gif|bmp)$"));
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
        resp.sendJson(200, res);
    }

    public static void handleDownloadFile(Context context, HttpRequest req, HttpResponse resp, OutputStream out) throws Exception {
        String reqPath = req.getParam("path");
        if (reqPath == null) {
            resp.sendError(400, "BAD_REQUEST", "Missing 'path' parameter", null);
            return;
        }
        reqPath = URLDecoder.decode(reqPath, "UTF-8");
        File f = new File(reqPath);
        if (!UsbStorageHelper.isPathAllowed(f, context)) {
            resp.sendError(403, "ACCESS_DENIED", "Access not permitted", null);
            return;
        }
        if (!f.exists() || !f.isFile()) {
            resp.sendError(404, "NOT_FOUND", "File not found", null);
            return;
        }

        String mime = HttpResponse.getMimeType(f.getName());
        String headersStr = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: " + mime + "\r\n" +
                "Content-Length: " + f.length() + "\r\n" +
                "Content-Disposition: attachment; filename=\"" + f.getName().replace("\"", "") + "\"\r\n" +
                "Connection: close\r\n\r\n";
        out.write(headersStr.getBytes(StandardCharsets.UTF_8));

        try (FileInputStream fis = new FileInputStream(f)) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = fis.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
        }
        out.flush();
    }

    public static void handleUploadFile(Context context, Socket socket, HttpRequest req, InputStream in,
                                        HttpResponse resp, OutputStream out) throws Exception {
        try {
            socket.setSoTimeout(300_000); // 5 minutes for uploads
        } catch (Exception ignored) {}

        String destDir = req.getParam("dest", "/storage/emulated/0/Download");
        String name = req.getParam("name", "uploaded_" + System.currentTimeMillis());

        destDir = URLDecoder.decode(destDir, "UTF-8");
        name = URLDecoder.decode(name, "UTF-8");
        name = new File(name).getName();
        if (name.startsWith(".") || name.isEmpty()) name = "file_" + System.currentTimeMillis();

        File targetDir = new File(destDir);
        if (!UsbStorageHelper.isPathAllowed(targetDir, context)) {
            resp.sendError(403, "ACCESS_DENIED", "Upload to this location is not permitted", null);
            return;
        }

        if (!targetDir.exists()) targetDir.mkdirs();
        File targetFile = new File(targetDir, name);

        long contentLength = 0;
        try {
            contentLength = Long.parseLong(req.getHeader("content-length"));
        } catch (Exception ignored) {}

        if (contentLength <= 0) {
            resp.sendError(411, "LENGTH_REQUIRED", "Missing or zero Content-Length header", null);
            return;
        }

        if (contentLength >= 4294967295L && UsbStorageHelper.isFat32(targetDir, context)) {
            resp.sendError(400, "FAT32_LIMIT", "File size exceeds FAT32 4GB limit", null);
            return;
        }

        try {
            StatFs stat = new StatFs(targetDir.getAbsolutePath());
            long free = stat.getAvailableBytes();
            if (free < contentLength + 10 * 1024 * 1024L) {
                resp.sendError(507, "NO_SPACE", "Insufficient storage space on destination drive", null);
                return;
            }
        } catch (Exception ignored) {}

        try (FileOutputStream fos = new FileOutputStream(targetFile)) {
            byte[] buf = new byte[128 * 1024];
            long remaining = contentLength;
            while (remaining > 0) {
                int toRead = (int) Math.min((long) buf.length, remaining);
                int r = in.read(buf, 0, toRead);
                if (r < 0) break;
                fos.write(buf, 0, r);
                remaining -= r;
            }
            fos.flush();
            try { fos.getFD().sync(); } catch (Exception ignored) {}

            if (remaining == 0 && targetFile.length() == contentLength) {
                UsbStorageHelper.invalidateCache();
                JSONObject res = new JSONObject();
                res.put("status", "ok");
                res.put("action", "upload");
                res.put("path", targetFile.getAbsolutePath());
                res.put("size", targetFile.length());
                resp.sendJson(200, res);
            } else {
                targetFile.delete();
                resp.sendError(500, "UPLOAD_INTERRUPTED", "Upload incomplete: connection interrupted mid-transfer", null);
            }
        } catch (Exception e) {
            if (targetFile.exists()) targetFile.delete();
            resp.sendError(500, "WRITE_FAILED", "Write failed: " + e.getMessage(), null);
        }
    }

    public static void handleDeleteFile(Context context, HttpRequest req, HttpResponse resp) throws Exception {
        String reqPath = req.getParam("path");
        if (reqPath == null) {
            resp.sendError(400, "BAD_REQUEST", "Missing 'path' parameter", null);
            return;
        }
        reqPath = URLDecoder.decode(reqPath, "UTF-8");
        File f = new File(reqPath);

        String abs = f.getAbsolutePath();
        if (abs.equals("/storage/emulated/0") || abs.equals("/storage") || abs.equals("/storage/self") || abs.equals("/mnt")) {
            resp.sendError(403, "FORBIDDEN_ROOT", "Cannot delete storage root", null);
            return;
        }

        if (!UsbStorageHelper.isPathAllowed(f, context)) {
            resp.sendError(403, "ACCESS_DENIED", "Access not permitted", null);
            return;
        }

        boolean ok = deleteRecursive(f);
        if (ok) {
            UsbStorageHelper.invalidateCache();
            JSONObject res = new JSONObject();
            res.put("status", "ok");
            res.put("action", "delete");
            res.put("path", reqPath);
            resp.sendJson(200, res);
        } else {
            resp.sendError(500, "DELETE_FAILED", "Failed to delete file. Storage may be read-only.", null);
        }
    }

    public static void handleCopyFile(Context context, HttpRequest req, HttpResponse resp) throws Exception {
        String src = req.getParam("src");
        String dest = req.getParam("dest");
        if (src == null || dest == null) {
            resp.sendError(400, "BAD_REQUEST", "Missing source or destination path", null);
            return;
        }
        src = URLDecoder.decode(src, "UTF-8");
        dest = URLDecoder.decode(dest, "UTF-8");

        File srcFile = new File(src);
        File destFile = new File(dest);

        if (!srcFile.exists()) {
            resp.sendError(404, "NOT_FOUND", "Source file not found", null);
            return;
        }

        if (!UsbStorageHelper.isPathAllowed(srcFile, context) || !UsbStorageHelper.isPathAllowed(destFile, context)) {
            resp.sendError(403, "ACCESS_DENIED", "Access to storage path not permitted", null);
            return;
        }

        if (destFile.isDirectory() || dest.endsWith("/")) {
            destFile = new File(destFile, srcFile.getName());
        }

        boolean ok = performCopy(srcFile, destFile);
        if (ok) {
            UsbStorageHelper.invalidateCache();
            JSONObject res = new JSONObject();
            res.put("status", "ok");
            res.put("action", "copy");
            res.put("src", src);
            res.put("dest", destFile.getAbsolutePath());
            resp.sendJson(200, res);
        } else {
            resp.sendError(500, "COPY_FAILED", "Failed to copy file", null);
        }
    }

    public static void handleStreamMedia(Context context, HttpRequest req, HttpResponse resp, OutputStream out) throws Exception {
        String reqPath = req.getParam("path");
        if (reqPath == null) {
            resp.sendError(400, "BAD_REQUEST", "Missing 'path' parameter", null);
            return;
        }
        reqPath = URLDecoder.decode(reqPath, "UTF-8");
        File target = new File(reqPath);
        if (!UsbStorageHelper.isPathAllowed(target, context)) {
            resp.sendError(403, "ACCESS_DENIED", "Access not permitted", null);
            return;
        }
        if (!target.exists() || !target.isFile()) {
            resp.sendError(404, "NOT_FOUND", "File not found", null);
            return;
        }

        resp.sendFileWithRange(target, req.getHeader("range"));
    }

    public static void handleUsbBenchmark(Context context, HttpRequest req, HttpResponse resp) throws Exception {
        String reqPath = req.getParam("path", "/storage/emulated/0/Download");
        reqPath = URLDecoder.decode(reqPath, "UTF-8");
        File dir = new File(reqPath);
        if (!dir.exists() || !dir.isDirectory()) {
            dir = new File("/storage/emulated/0");
        }
        JSONObject benchResult = UsbStorageHelper.runBenchmark(dir, context);
        resp.sendJson(200, benchResult);
    }

    private static boolean deleteRecursive(File f) {
        if (!f.exists()) return true;
        if (f.isDirectory()) {
            File[] subs = f.listFiles();
            if (subs != null) {
                for (File sub : subs) {
                    if (!deleteRecursive(sub)) return false;
                }
            }
        }
        return f.delete();
    }

    private static boolean performCopy(File src, File dest) {
        try {
            if (src.isDirectory()) {
                if (!dest.exists() && !dest.mkdirs()) return false;
                File[] subs = src.listFiles();
                if (subs != null) {
                    for (File sub : subs) {
                        if (!performCopy(sub, new File(dest, sub.getName()))) return false;
                    }
                }
                return true;
            } else {
                File parent = dest.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) return false;
                try (FileInputStream in = new FileInputStream(src);
                     FileOutputStream out = new FileOutputStream(dest)) {
                    byte[] buf = new byte[128 * 1024];
                    int n;
                    while ((n = in.read(buf)) != -1) {
                        out.write(buf, 0, n);
                    }
                    out.flush();
                    try { out.getFD().sync(); } catch (Exception ignored) {}
                }
                return true;
            }
        } catch (Exception e) {
            return false;
        }
    }
}
