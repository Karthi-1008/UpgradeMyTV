package com.gathin.tvpowerhub.http;

import java.io.File;
import java.io.IOException;

public class PathGuard {

    /**
     * Sanitizes a requested path against an allowed base directory.
     * Prevents directory traversal attacks (e.g. "../", "\\..\\", null bytes).
     *
     * @param baseDir The root directory that access must be confined to.
     * @param subPath The relative subpath requested.
     * @return The canonical File if inside baseDir, or null if outside or invalid.
     */
    public static File resolveSafe(File baseDir, String subPath) {
        if (baseDir == null || subPath == null) return null;
        if (subPath.contains("\0")) return null;

        try {
            String cleanSub = subPath.replace('\\', '/').trim();
            while (cleanSub.startsWith("/")) {
                cleanSub = cleanSub.substring(1);
            }

            File target = new File(baseDir, cleanSub);
            String baseCanonical = baseDir.getCanonicalPath();
            String targetCanonical = target.getCanonicalPath();

            if (targetCanonical.equals(baseCanonical) || targetCanonical.startsWith(baseCanonical + File.separator)) {
                return target;
            }
        } catch (IOException ignored) {}
        return null;
    }

    /**
     * Validates if a target path is strictly within any of the allowed storage root prefixes.
     */
    public static boolean isPathAllowed(String path, String[] allowedRoots) {
        if (path == null || path.contains("\0")) return false;
        try {
            File f = new File(path);
            String canonical = f.getCanonicalPath();
            for (String root : allowedRoots) {
                if (root == null) continue;
                String rootCanonical = new File(root).getCanonicalPath();
                if (canonical.equals(rootCanonical) || canonical.startsWith(rootCanonical + File.separator)) {
                    return true;
                }
            }
        } catch (IOException ignored) {}
        return false;
    }
}
