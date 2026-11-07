package com.gathin.tvpowerhub.api;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
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
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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
                item.put("modified", f.lastModified());
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

    /**
     * Streams an entire directory as a ZIP stream on-the-fly with zero temporary disk usage (M9).
     */
    public static void handleDownloadZip(Context context, HttpRequest req, HttpResponse resp, OutputStream out) throws Exception {
        String reqPath = req.getParam("path");
        if (reqPath == null) {
            resp.sendError(400, "BAD_REQUEST", "Missing 'path' parameter", null);
            return;
        }
        reqPath = URLDecoder.decode(reqPath, "UTF-8");
        File folder = new File(reqPath);
        if (!UsbStorageHelper.isPathAllowed(folder, context)) {
            resp.sendError(403, "ACCESS_DENIED", "Access not permitted", null);
            return;
        }
        if (!folder.exists() || !folder.isDirectory()) {
            resp.sendError(404, "NOT_FOUND", "Folder not found", null);
            return;
        }

        String zipName = folder.getName() + ".zip";
        String header = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: application/zip\r\n" +
                "Content-Disposition: attachment; filename=\"" + zipName.replace("\"", "") + "\"\r\n" +
                "Connection: close\r\n\r\n";
        out.write(header.getBytes(StandardCharsets.UTF_8));

        try (ZipOutputStream zos = new ZipOutputStream(out)) {
            zipFolderRecursive(folder, folder.getAbsolutePath(), zos);
            zos.finish();
        }
        out.flush();
    }

    private static void zipFolderRecursive(File current, String rootPath, ZipOutputStream zos) {
        File[] files = current.listFiles();
        if (files == null) return;
        byte[] buf = new byte[32768];

        for (File f : files) {
            if (f.getName().startsWith(".")) continue;
            if (f.isDirectory()) {
                zipFolderRecursive(f, rootPath, zos);
            } else {
                try {
                    String relative = f.getAbsolutePath().substring(rootPath.length());
                    if (relative.startsWith(File.separator)) relative = relative.substring(1);
                    relative = relative.replace('\\', '/');

                    ZipEntry ze = new ZipEntry(relative);
                    ze.setTime(f.lastModified());
                    zos.putNextEntry(ze);

                    try (FileInputStream fis = new FileInputStream(f)) {
                        int r;
                        while ((r = fis.read(buf)) != -1) {
                            zos.write(buf, 0, r);
                        }
                    }
                    zos.closeEntry();
                } catch (Exception ignored) {}
            }
        }
    }

    /**
     * Chunked resumable file upload handler (M9).
     */
    public static void handleUploadFile(Context context, Socket socket, HttpRequest req, InputStream in,
                                        HttpResponse resp, OutputStream out) throws Exception {
        try {
            socket.setSoTimeout(300_000); // 5 minutes timeout
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

        // Chunking parameters (Content-Range or query parameters)
        long chunkOffset = 0;
        long totalSize = -1;
        String contentRange = req.getHeader("content-range");
        if (contentRange != null && contentRange.startsWith("bytes ")) {
            try {
                // e.g. "bytes 0-1048575/5242880"
                String rangePart = contentRange.substring(6).trim();
                int slash = rangePart.indexOf('/');
                if (slash > 0) {
                    totalSize = Long.parseLong(rangePart.substring(slash + 1));
                    String[] byteParts = rangePart.substring(0, slash).split("-");
                    chunkOffset = Long.parseLong(byteParts[0]);
                }
            } catch (Exception ignored) {}
        } else {
            chunkOffset = req.getIntParam("offset", 0);
            totalSize = req.getIntParam("total", -1);
        }

        long chunkLength = 0;
        try {
            chunkLength = Long.parseLong(req.getHeader("content-length"));
        } catch (Exception ignored) {}

        if (chunkLength <= 0 && req.getBody() != null) {
            chunkLength = req.getBody().length;
        }

        if (chunkLength <= 0) {
            resp.sendError(411, "LENGTH_REQUIRED", "Missing or zero Content-Length header", null);
            return;
        }

        // Storage space check on first chunk
        if (chunkOffset == 0) {
            try {
                StatFs stat = new StatFs(targetDir.getAbsolutePath());
                long free = stat.getAvailableBytes();
                long needed = (totalSize > 0 ? totalSize : chunkLength) + 10 * 1024 * 1024L;
                if (free < needed) {
                    resp.sendError(507, "NO_SPACE", "Insufficient storage space on destination drive", null);
                    return;
                }
            } catch (Exception ignored) {}
        }

        File partFile = new File(targetDir, name + ".part");
        File finalFile = new File(targetDir, name);

        // Open in append mode if chunkOffset > 0, otherwise overwrite partFile
        boolean append = (chunkOffset > 0 && partFile.exists());
        try (FileOutputStream fos = new FileOutputStream(partFile, append)) {
            if (req.getBody() != null && req.getBody().length > 0) {
                fos.write(req.getBody());
            } else {
                byte[] buf = new byte[128 * 1024];
                long remaining = chunkLength;
                while (remaining > 0) {
                    int toRead = (int) Math.min((long) buf.length, remaining);
                    int r = in.read(buf, 0, toRead);
                    if (r < 0) break;
                    fos.write(buf, 0, r);
                    remaining -= r;
                }
            }
            fos.flush();
            try { fos.getFD().sync(); } catch (Exception ignored) {}
        }

        boolean isComplete = (totalSize <= 0) || (partFile.length() >= totalSize);
        if (isComplete) {
            if (finalFile.exists()) finalFile.delete();
            boolean renamed = partFile.renameTo(finalFile);
            if (!renamed) {
                resp.sendError(500, "RENAME_FAILED", "Could not finalize uploaded file", null);
                return;
            }
            UsbStorageHelper.invalidateCache();
            JSONObject res = new JSONObject();
            res.put("status", "ok");
            res.put("action", "upload");
            res.put("complete", true);
            res.put("path", finalFile.getAbsolutePath());
            res.put("size", finalFile.length());
            resp.sendJson(200, res);
        } else {
            JSONObject res = new JSONObject();
            res.put("status", "ok");
            res.put("action", "upload_chunk");
            res.put("complete", false);
            res.put("received_bytes", partFile.length());
            res.put("total_bytes", totalSize);
            resp.sendJson(200, res);
        }
    }

    /**
     * Delete file or move to trash bin with instant undo capability (M9).
     */
    public static void handleDeleteFile(Context context, HttpRequest req, HttpResponse resp) throws Exception {
        String reqPath = req.getParam("path");
        boolean useTrash = !"false".equalsIgnoreCase(req.getParam("trash", "true"));

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

        if (useTrash && f.isFile()) {
            File parentDir = f.getParentFile();
            File trashDir = new File(parentDir != null ? parentDir : f, ".tvhub_trash");
            if (!trashDir.exists()) trashDir.mkdirs();

            String trashName = System.currentTimeMillis() + "_" + f.getName();
            File trashFile = new File(trashDir, trashName);
            boolean moved = f.renameTo(trashFile);
            if (moved) {
                UsbStorageHelper.invalidateCache();
                JSONObject res = new JSONObject();
                res.put("status", "ok");
                res.put("action", "trash");
                res.put("trashed", true);
                res.put("trash_path", trashFile.getAbsolutePath());
                res.put("original_path", reqPath);
                resp.sendJson(200, res);
                return;
            }
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

    /**
     * Restores a file from the trash bin back to its original location (M9).
     */
    public static void handleRestoreFile(Context context, HttpRequest req, HttpResponse resp) throws Exception {
        String trashPath = req.getParam("trash_path");
        String originalPath = req.getParam("original_path");

        if (trashPath == null || originalPath == null) {
            resp.sendError(400, "BAD_REQUEST", "Missing trash_path or original_path", null);
            return;
        }

        File trashFile = new File(URLDecoder.decode(trashPath, "UTF-8"));
        File originalFile = new File(URLDecoder.decode(originalPath, "UTF-8"));

        if (!trashFile.exists()) {
            resp.sendError(404, "NOT_FOUND", "Trash item no longer exists", null);
            return;
        }

        boolean restored = trashFile.renameTo(originalFile);
        if (restored) {
            UsbStorageHelper.invalidateCache();
            JSONObject res = new JSONObject();
            res.put("status", "ok");
            res.put("action", "restore");
            res.put("restored_path", originalFile.getAbsolutePath());
            resp.sendJson(200, res);
        } else {
            resp.sendError(500, "RESTORE_FAILED", "Failed to restore file", null);
        }
    }

    /**
     * "Play on TV": opens the video, audio, or image in the TV's native player (M9).
     */
    public static void handlePlayOnTv(Context context, HttpRequest req, HttpResponse resp) throws Exception {
        String reqPath = req.getParam("path");
        if (reqPath == null) {
            resp.sendError(400, "BAD_REQUEST", "Missing 'path' parameter", null);
            return;
        }
        reqPath = URLDecoder.decode(reqPath, "UTF-8");
        File f = new File(reqPath);

        if (!f.exists() || !f.isFile()) {
            resp.sendError(404, "NOT_FOUND", "File not found", null);
            return;
        }

        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            String mime = HttpResponse.getMimeType(f.getName());
            intent.setDataAndType(Uri.fromFile(f), mime);
            context.startActivity(intent);

            JSONObject res = new JSONObject();
            res.put("status", "ok");
            res.put("action", "play_on_tv");
            res.put("path", f.getAbsolutePath());
            res.put("mime", mime);
            resp.sendJson(200, res);
        } catch (Exception e) {
            resp.sendError(500, "LAUNCH_FAILED", "No app available on TV to play " + f.getName(), null);
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
