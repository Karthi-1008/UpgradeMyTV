package com.gathin.tvpowerhub;

import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class UsbStorageHelper {
    private static final String TAG = "UsbStorageHelper";

    private static volatile List<DriveInfo> sCachedDrives = null;
    private static volatile long sLastScanTime = 0;
    private static final long CACHE_DURATION_MS = 20_000; // 20s cache to avoid I/O wear (F-16)

    public static class DriveInfo {
        public String name;
        public String path;
        public String type; // "usb" or "internal"
        public String fsType; // "FAT32", "exFAT", "NTFS", "EXT4", etc.
        public long totalBytes;
        public long freeBytes;
        public boolean isWritable;

        public JSONObject toJson() {
            JSONObject obj = new JSONObject();
            try {
                obj.put("name", name);
                obj.put("path", path);
                obj.put("type", type);
                obj.put("fs_type", fsType != null ? fsType : "FAT32/exFAT");
                obj.put("total_bytes", totalBytes);
                obj.put("free_bytes", freeBytes);
                obj.put("total_str", formatBytes(totalBytes));
                obj.put("free_str", formatBytes(freeBytes));
                obj.put("is_writable", isWritable);
            } catch (Exception ignored) {}
            return obj;
        }
    }

    public static synchronized void invalidateCache() {
        sCachedDrives = null;
        sLastScanTime = 0;
    }

    public static synchronized List<DriveInfo> scanAllDrives(Context context) {
        long now = System.currentTimeMillis();
        if (sCachedDrives != null && (now - sLastScanTime) < CACHE_DURATION_MS) {
            return sCachedDrives;
        }

        Map<String, DriveInfo> driveMap = new LinkedHashMap<>();

        // 1. Android StorageManager API (Primary official method)
        try {
            StorageManager sm = (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);
            if (sm != null) {
                List<StorageVolume> volumes = sm.getStorageVolumes();
                for (StorageVolume vol : volumes) {
                    if (vol.isRemovable()) {
                        File dir = null;
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            try { dir = vol.getDirectory(); } catch (Throwable ignored) {}
                        }
                        if (dir == null) {
                            try {
                                Method getPath = vol.getClass().getMethod("getPath");
                                String p = (String) getPath.invoke(vol);
                                if (p != null) dir = new File(p);
                            } catch (Throwable ignored) {}
                        }
                        if (dir != null && dir.exists()) {
                            String desc = vol.getDescription(context);
                            addDrive(driveMap, dir, desc != null && !desc.isEmpty() ? desc : "USB Drive (" + dir.getName() + ")", "usb");
                        }
                    }
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "StorageManager scan error: " + t.getMessage());
        }

        // 2. Context getExternalFilesDirs (Guaranteed Android fallback for secondary storage)
        try {
            File[] extDirs = context.getExternalFilesDirs(null);
            if (extDirs != null) {
                for (File ext : extDirs) {
                    if (ext == null) continue;
                    String fullPath = ext.getAbsolutePath();
                    int idx = fullPath.indexOf("/Android/data");
                    if (idx > 0) {
                        String root = fullPath.substring(0, idx);
                        if (!root.equals("/storage/emulated/0") && !root.equals("/storage/self") && !root.contains("emulated")) {
                            File rootDir = new File(root);
                            if (rootDir.exists()) {
                                addDrive(driveMap, rootDir, "USB: " + rootDir.getName(), "usb");
                            }
                        }
                    }
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "getExternalFilesDirs scan error: " + t.getMessage());
        }

        // 3. Fallback folder checks (/storage, /mnt/media_rw)
        scanDirectoryCandidates(driveMap, new File("/storage"));
        scanDirectoryCandidates(driveMap, new File("/mnt/media_rw"));

        List<DriveInfo> result = new ArrayList<>(driveMap.values());
        sCachedDrives = result;
        sLastScanTime = now;
        return result;
    }

    private static void scanDirectoryCandidates(Map<String, DriveInfo> driveMap, File parent) {
        if (!parent.exists() || !parent.isDirectory()) return;
        try {
            File[] subs = parent.listFiles();
            if (subs != null) {
                for (File s : subs) {
                    String n = s.getName();
                    if (!n.equals("emulated") && !n.equals("self") && !n.equals("knox") && !n.equals("enc_emulated") && s.isDirectory()) {
                        addDrive(driveMap, s, "USB Drive (" + n + ")", "usb");
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    private static void addDrive(Map<String, DriveInfo> map, File dir, String name, String type) {
        String path = dir.getAbsolutePath();
        String uuidKey = dir.getName().toUpperCase();

        // Deduplicate across /mnt/media_rw and /storage (always prefer /storage path)
        for (String existingPath : map.keySet()) {
            File existing = new File(existingPath);
            if (existing.getName().equalsIgnoreCase(uuidKey)) {
                if (existingPath.startsWith("/storage/") && !path.startsWith("/storage/")) {
                    return; // Already have cleaner /storage path
                }
                if (!existingPath.startsWith("/storage/") && path.startsWith("/storage/")) {
                    map.remove(existingPath); // Upgrade to /storage path
                    break;
                }
                return;
            }
        }

        DriveInfo info = new DriveInfo();
        info.path = path;
        info.name = name;
        info.type = type;

        // Determine exact filesystem and mount flags from Linux mount table
        MountDetails md = queryMountDetails(uuidKey);
        info.fsType = md.fsType;

        try {
            StatFs stat = new StatFs(path);
            long total = stat.getTotalBytes();
            long free = stat.getAvailableBytes();
            info.totalBytes = total;
            info.freeBytes = free;
        } catch (Throwable ignored) {
            info.totalBytes = 0;
            info.freeBytes = 0;
        }

        // Determine writability:
        // 1. If kernel mounted it read-only (ro), it cannot be written
        if (md.isKernelReadOnly) {
            info.isWritable = false;
        } else {
            // 2. On Android 11+ (API 30+), check All Files Access permission
            boolean hasManageStorage = true;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                hasManageStorage = Environment.isExternalStorageManager();
            }
            if (hasManageStorage) {
                // If kernel is rw and app has MANAGE_EXTERNAL_STORAGE, drive is writable
                info.isWritable = true;
            } else {
                info.isWritable = testWritableSafely(dir);
            }
        }

        map.put(path, info);
    }

    private static class MountDetails {
        public String fsType = "FAT32";
        public boolean isKernelReadOnly = false;
    }

    private static MountDetails queryMountDetails(String uuid) {
        MountDetails details = new MountDetails();
        boolean isFatUuid = uuid != null && uuid.matches("^[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}$");
        File mountsFile = new File("/proc/mounts");
        if (!mountsFile.exists() || !mountsFile.canRead()) {
            details.fsType = isFatUuid ? "FAT32" : "FAT32/exFAT";
            return details;
        }

        try (BufferedReader br = new BufferedReader(new FileReader(mountsFile))) {
            String line;
            String bestFs = null;
            boolean isRo = false;

            while ((line = br.readLine()) != null) {
                String lineUpper = line.toUpperCase();
                if (!lineUpper.contains(uuid.toUpperCase())) continue;

                String[] tokens = line.split("\\s+");
                if (tokens.length >= 4) {
                    String mountTarget = tokens[1];
                    String rawFs = tokens[2].toLowerCase();
                    String options = tokens[3].toLowerCase();

                    // Prioritize block device mount over fuse/sdcardfs wrapper
                    boolean isBlockMount = mountTarget.startsWith("/mnt/media_rw") || tokens[0].startsWith("/dev/block");
                    boolean roFlag = options.startsWith("ro,") || options.contains(",ro,") || options.endsWith(",ro") || options.equals("ro");

                    if (isBlockMount || bestFs == null) {
                        isRo = roFlag;
                        if (rawFs.contains("vfat") || rawFs.contains("fat") || rawFs.contains("msdos")) {
                            bestFs = "FAT32";
                        } else if (rawFs.contains("exfat") || rawFs.contains("texfat") || rawFs.contains("sdfat")) {
                            bestFs = "exFAT";
                        } else if (rawFs.contains("ntfs") || rawFs.contains("tcl_ntfs") || rawFs.contains("antfs")) {
                            bestFs = "NTFS";
                        } else if (rawFs.contains("ext4") || rawFs.contains("ext3") || rawFs.contains("f2fs")) {
                            bestFs = "EXT4";
                        } else if (rawFs.contains("fuseblk") || rawFs.contains("fuse") || rawFs.contains("sdcardfs")) {
                            if (isFatUuid) {
                                bestFs = "FAT32";
                            } else if (roFlag) {
                                bestFs = "NTFS";
                            } else {
                                bestFs = "exFAT";
                            }
                        } else {
                            bestFs = isFatUuid ? "FAT32" : rawFs.toUpperCase();
                        }
                        if (isBlockMount) break;
                    }
                }
            }

            details.fsType = (bestFs != null) ? bestFs : (isFatUuid ? "FAT32" : "FAT32/exFAT");
            details.isKernelReadOnly = isRo;
        } catch (Throwable t) {
            Log.w(TAG, "queryMountDetails error: " + t.getMessage());
        }

        return details;
    }

    private static boolean testWritableSafely(File dir) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) return false;
        File test = new File(dir, "tvhub_perm_test.tmp");
        try {
            if (test.exists()) test.delete();
            try (FileOutputStream fos = new FileOutputStream(test)) {
                fos.write(42);
                fos.flush();
            }
            test.delete();
            return true;
        } catch (Throwable ignored) {
            try { if (test.exists()) test.delete(); } catch (Throwable ignored2) {}
        }
        return dir.canWrite();
    }

    public static boolean isFat32(File path, Context context) {
        if (path == null) return false;
        String abs = path.getAbsolutePath();
        if (abs.startsWith("/storage/emulated/0") || abs.startsWith("/data")) return false; // Internal storage is ext4/f2fs
        List<DriveInfo> drives = scanAllDrives(context);
        for (DriveInfo d : drives) {
            if (abs.startsWith(d.path)) {
                String fs = d.fsType != null ? d.fsType.toUpperCase() : "";
                return (fs.contains("FAT") || fs.contains("VFAT")) && !fs.contains("EXFAT");
            }
        }
        return false;
    }

    public static boolean isPathAllowed(File file, Context context) {
        if (file == null) return false;
        try {
            File existing = file;
            while (existing != null && !existing.exists()) {
                existing = existing.getParentFile();
            }
            if (existing == null) return false;

            String canonical = file.getCanonicalPath();
            String existingCanon = existing.getCanonicalPath();

            // Allow primary external storage
            if (canonical.equals("/storage/emulated/0") || canonical.startsWith("/storage/emulated/0/") ||
                existingCanon.equals("/storage/emulated/0") || existingCanon.startsWith("/storage/emulated/0/")) {
                return true;
            }
            // Allow any detected USB root
            List<DriveInfo> drives = scanAllDrives(context);
            for (DriveInfo d : drives) {
                File driveRoot = new File(d.path);
                String driveCanonical = driveRoot.getCanonicalPath();
                if (canonical.equals(driveCanonical) || canonical.startsWith(driveCanonical + File.separator) ||
                    existingCanon.equals(driveCanonical) || existingCanon.startsWith(driveCanonical + File.separator)) {
                    return true;
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "isPathAllowed error: " + t.getMessage());
        }
        return false;
    }

    public static JSONArray getUsbDrivesJson(Context context) {
        JSONArray arr = new JSONArray();
        List<DriveInfo> drives = scanAllDrives(context);
        for (DriveInfo d : drives) {
            arr.put(d.toJson());
        }
        return arr;
    }

    public static JSONObject runBenchmark(File targetDir, Context context) {
        JSONObject res = new JSONObject();
        if (targetDir == null || !targetDir.exists() || !targetDir.isDirectory()) {
            try {
                res.put("status", "error");
                res.put("message", "Target directory does not exist");
            } catch (Exception ignored) {}
            return res;
        }

        File benchFile = new File(targetDir, ".tvhub_bench_test.tmp");
        final int TEST_SIZE_BYTES = 4 * 1024 * 1024; // 4 MB test file
        final byte[] buffer = new byte[64 * 1024]; // 64 KB buffer
        for (int i = 0; i < buffer.length; i++) buffer[i] = (byte) (i % 127);

        try {
            StatFs stat = new StatFs(targetDir.getAbsolutePath());
            long total = stat.getTotalBytes();
            long free = stat.getAvailableBytes();
            long used = total - free;

            if (free < TEST_SIZE_BYTES + 10 * 1024 * 1024) {
                res.put("status", "error");
                res.put("message", "Not enough free space on storage to run speed test");
                res.put("write_status", "no_space");
                return res;
            }

            // Measure Write Speed
            long startWrite = System.nanoTime();
            try (FileOutputStream fos = new FileOutputStream(benchFile)) {
                int written = 0;
                while (written < TEST_SIZE_BYTES) {
                    int toWrite = Math.min(buffer.length, TEST_SIZE_BYTES - written);
                    fos.write(buffer, 0, toWrite);
                    written += toWrite;
                }
                fos.flush();
                fos.getFD().sync();
            }
            long elapsedWriteNs = System.nanoTime() - startWrite;
            double writeDurationSec = Math.max(0.001, elapsedWriteNs / 1_000_000_000.0);
            double writeSpeedMbps = (TEST_SIZE_BYTES / (1024.0 * 1024.0)) / writeDurationSec;

            // Measure Read Speed
            long startRead = System.nanoTime();
            int readTotal = 0;
            try (FileInputStream fis = new FileInputStream(benchFile)) {
                int r;
                while ((r = fis.read(buffer)) != -1) {
                    readTotal += r;
                }
            }
            long elapsedReadNs = System.nanoTime() - startRead;
            double readDurationSec = Math.max(0.001, elapsedReadNs / 1_000_000_000.0);
            double readSpeedMbps = (readTotal / (1024.0 * 1024.0)) / readDurationSec;

            // Delete temporary benchmark file
            if (benchFile.exists()) benchFile.delete();

            res.put("status", "ok");
            res.put("path", targetDir.getAbsolutePath());
            res.put("write_speed_mbps", Math.round(writeSpeedMbps * 10.0) / 10.0);
            res.put("read_speed_mbps", Math.round(readSpeedMbps * 10.0) / 10.0);
            res.put("write_status", "ok");
            res.put("total_bytes", total);
            res.put("free_bytes", free);
            res.put("used_bytes", used);
            res.put("total_str", formatBytes(total));
            res.put("free_str", formatBytes(free));
            res.put("used_str", formatBytes(used));
            res.put("test_size_mb", 4);
        } catch (Throwable t) {
            if (benchFile.exists()) benchFile.delete();
            try {
                // If write failed, test read speed on an existing readable file
                File sampleFile = findFirstReadableFile(targetDir);
                if (sampleFile != null && sampleFile.length() >= 1024 * 1024) {
                    long startRead = System.nanoTime();
                    int readTotal = 0;
                    try (FileInputStream fis = new FileInputStream(sampleFile)) {
                        int r;
                        while (readTotal < TEST_SIZE_BYTES && (r = fis.read(buffer)) != -1) {
                            readTotal += r;
                        }
                    }
                    long elapsedReadNs = System.nanoTime() - startRead;
                    double readDurationSec = Math.max(0.001, elapsedReadNs / 1_000_000_000.0);
                    double readSpeedMbps = (readTotal / (1024.0 * 1024.0)) / readDurationSec;

                    StatFs stat = new StatFs(targetDir.getAbsolutePath());
                    long total = stat.getTotalBytes();
                    long free = stat.getAvailableBytes();
                    long used = total - free;

                    res.put("status", "ok");
                    res.put("path", targetDir.getAbsolutePath());
                    res.put("write_speed_mbps", JSONObject.NULL);
                    res.put("write_status", "read_only");
                    res.put("read_speed_mbps", Math.round(readSpeedMbps * 10.0) / 10.0);
                    res.put("total_bytes", total);
                    res.put("free_bytes", free);
                    res.put("used_bytes", used);
                    res.put("total_str", formatBytes(total));
                    res.put("free_str", formatBytes(free));
                    res.put("used_str", formatBytes(used));
                    res.put("test_size_mb", Math.round(readTotal / (1024.0 * 1024.0)));
                    return res;
                }
                res.put("status", "error");
                res.put("message", "Benchmark error: " + t.getMessage());
            } catch (Exception ignored) {}
        } finally {
            if (benchFile.exists()) benchFile.delete();
        }

        return res;
    }

    private static File findFirstReadableFile(File dir) {
        if (dir == null || !dir.exists()) return null;
        File[] files = dir.listFiles();
        if (files == null) return null;
        for (File f : files) {
            if (f.isFile() && f.canRead() && f.length() >= 1024 * 1024) {
                return f;
            }
        }
        for (File f : files) {
            if (f.isDirectory() && f.canRead()) {
                File sub = findFirstReadableFile(f);
                if (sub != null) return sub;
            }
        }
        return null;
    }

    public static String formatBytes(long bytes) {
        if (bytes <= 0) return "0 B";
        if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
        if (bytes < 1024 * 1024 * 1024) return String.format(java.util.Locale.US, "%.1f MB", (double) bytes / (1024 * 1024));
        return String.format(java.util.Locale.US, "%.1f GB", (double) bytes / (1024 * 1024 * 1024));
    }
}
