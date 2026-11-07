package com.gathin.tvpowerhub;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Path;
import android.graphics.Rect;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

public class TvAccessibilityService extends AccessibilityService {
    private static final String TAG = "TvAccessibilityService";
    private static volatile TvAccessibilityService sInstance;

    private static int sScreenWidth = 1920;
    private static int sScreenHeight = 1080;

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        sInstance = this;
        updateScreenDimensions();
        Log.i(TAG, "TvAccessibilityService successfully connected and active!");
    }

    private void updateScreenDimensions() {
        try {
            WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
            if (wm != null) {
                DisplayMetrics dm = new DisplayMetrics();
                wm.getDefaultDisplay().getRealMetrics(dm);
                sScreenWidth = dm.widthPixels > 0 ? dm.widthPixels : 1920;
                sScreenHeight = dm.heightPixels > 0 ? dm.heightPixels : 1080;
            }
        } catch (Throwable ignored) {}
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // App Lock / Parental PIN completely removed per user request
    }

    @Override
    public void onInterrupt() {
        Log.w(TAG, "TvAccessibilityService interrupted");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (sInstance == this) {
            sInstance = null;
        }
    }

    public static TvAccessibilityService getInstance() {
        return sInstance;
    }

    public static boolean isConnected() {
        return sInstance != null;
    }

    public static boolean doBack() {
        if (sInstance != null) {
            return sInstance.performGlobalAction(GLOBAL_ACTION_BACK);
        }
        return false;
    }

    public static boolean doHome(Context context) {
        try {
            Intent homeIntent = new Intent(Intent.ACTION_MAIN);
            homeIntent.addCategory(Intent.CATEGORY_HOME);
            homeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            context.startActivity(homeIntent);
        } catch (Throwable t) {
            Log.w(TAG, "Failed to start home intent: " + t.getMessage());
        }
        if (sInstance != null) {
            sInstance.performGlobalAction(GLOBAL_ACTION_HOME);
        }
        return true;
    }

    public static boolean doPower(Context context) {
        if (sInstance != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            sInstance.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
        }
        try {
            context.sendBroadcast(new Intent("com.tcl.backlight.off"));
            context.sendBroadcast(new Intent("com.tcl.system.poweroff"));
            context.sendBroadcast(new Intent("android.intent.action.ACTION_POWER_DOWN"));
        } catch (Throwable ignored) {}
        return true;
    }

    public static boolean doSettings(Context context) {
        try {
            Intent intent = new Intent(Settings.ACTION_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "Error opening settings: " + t.getMessage());
        }
        return false;
    }

    public static boolean doInputSource(Context context) {
        try {
            Intent intent = new Intent("com.tcl.insrc.SHOW_WINDOW");
            intent.setComponent(new android.content.ComponentName("com.tcl.tv", "com.tcl.sourcemenu.insrc.ShowWindowService"));
            if (sInstance != null) {
                sInstance.startService(intent);
            } else {
                context.startService(intent);
            }
            return true;
        } catch (Throwable ignored) {}

        try {
            Intent intent = new Intent("com.tcl.insrc.SHOW_WINDOW");
            if (sInstance != null) {
                sInstance.startService(intent);
            } else {
                context.startService(intent);
            }
            return true;
        } catch (Throwable ignored) {}

        try {
            context.sendBroadcast(new Intent("com.tcl.ShortcutKey"));
            context.sendBroadcast(new Intent("com.tcl.action.SHOW_INPUT_SOURCE"));
            context.sendBroadcast(new Intent("com.tcl.intent.action.HOTKEY_INPUT"));
        } catch (Throwable ignored) {}

        return false;
    }

    public static boolean switchInput(Context context, String source) {
        return doInputSource(context);
    }

    public static String getActiveApp(Context context) {
        String pkg = null;
        if (sInstance != null) {
            try {
                AccessibilityNodeInfo root = getBestRootNode();
                if (root != null && root.getPackageName() != null) {
                    pkg = root.getPackageName().toString();
                }
            } catch (Throwable ignored) {}
        }

        if (pkg == null || pkg.isEmpty()) {
            try {
                android.app.ActivityManager am = (android.app.ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
                if (am != null) {
                    List<android.app.ActivityManager.RunningAppProcessInfo> procs = am.getRunningAppProcesses();
                    if (procs != null && !procs.isEmpty()) {
                        for (android.app.ActivityManager.RunningAppProcessInfo pi : procs) {
                            if (pi.importance == android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                                pkg = pi.processName;
                                break;
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }

        if (pkg == null || pkg.isEmpty()) {
            return "Android TV Home";
        }

        if (pkg.contains("launcher") || pkg.contains("waterfall") || pkg.contains("tvos") || pkg.contains("leanbacklauncher")) {
            return "Android TV Home";
        } else if (pkg.equals("com.google.android.youtube.tv") || pkg.equals("com.google.android.youtube")) {
            return "YouTube";
        } else if (pkg.contains("smarttube")) {
            return "SmartTube";
        } else if (pkg.equals("com.netflix.ninja")) {
            return "Netflix";
        } else if (pkg.equals("com.amazon.amazonvideo.livingroom")) {
            return "Prime Video";
        } else if (pkg.contains("settings")) {
            return "TV Settings";
        } else if (pkg.equals("com.tcl.browser")) {
            return "TCL Browser";
        } else if (pkg.contains("tvpowerhub")) {
            return "TV Power House";
        } else if (pkg.contains("tvinput") || pkg.contains("shortcutmenu")) {
            return "Input Sources";
        }

        try {
            android.content.pm.PackageManager pm = context.getPackageManager();
            android.content.pm.ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            CharSequence label = pm.getApplicationLabel(ai);
            if (label != null && label.length() > 0) {
                return label.toString();
            }
        } catch (Throwable ignored) {}

        return pkg;
    }

    public static boolean doScreenOff(Context context) {
        if (sInstance != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            sInstance.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
        }
        try {
            context.sendBroadcast(new Intent("com.tcl.backlight.off"));
        } catch (Throwable ignored) {}
        return true;
    }

    public static boolean doRecents() {
        if (sInstance != null) {
            return sInstance.performGlobalAction(GLOBAL_ACTION_RECENTS);
        }
        return false;
    }

    public static void sendMedia(Context context, int keycode) {
        AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (am != null) {
            try {
                am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keycode));
                am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, keycode));
            } catch (Throwable ignored) {}
        }
    }

    // =========================================================================
    // Multi-Window & True Interactive Root Node Resolution
    // =========================================================================

    public static AccessibilityNodeInfo getBestRootNode() {
        if (sInstance == null) return null;

        // 1. Try standard active window root
        AccessibilityNodeInfo root = null;
        try {
            root = sInstance.getRootInActiveWindow();
        } catch (Throwable ignored) {}

        if (root != null) {
            Rect r = new Rect();
            root.getBoundsInScreen(r);
            if (r.width() > 0 && r.height() > 0) return root;
        }

        // 2. Iterate interactive windows in Z-order
        try {
            List<AccessibilityWindowInfo> windows = sInstance.getWindows();
            if (windows != null && !windows.isEmpty()) {
                // First: Focused window
                for (AccessibilityWindowInfo win : windows) {
                    if (win != null && win.isFocused()) {
                        AccessibilityNodeInfo wRoot = win.getRoot();
                        if (wRoot != null) return wRoot;
                    }
                }
                // Second: Topmost APPLICATION window
                for (AccessibilityWindowInfo win : windows) {
                    if (win != null && win.getType() == AccessibilityWindowInfo.TYPE_APPLICATION) {
                        AccessibilityNodeInfo wRoot = win.getRoot();
                        if (wRoot != null) return wRoot;
                    }
                }
            }
        } catch (Throwable ignored) {}

        return root;
    }

    private static AccessibilityNodeInfo getCurrentFocusNode(AccessibilityNodeInfo root) {
        if (root == null) return null;
        AccessibilityNodeInfo focused = null;
        try {
            focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
            if (focused == null) {
                focused = root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY);
            }
        } catch (Throwable ignored) {}

        // Fallback: search all windows
        if (focused == null && sInstance != null) {
            try {
                List<AccessibilityWindowInfo> windows = sInstance.getWindows();
                if (windows != null) {
                    for (AccessibilityWindowInfo win : windows) {
                        if (win != null) {
                            AccessibilityNodeInfo wRoot = win.getRoot();
                            if (wRoot != null) {
                                focused = wRoot.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
                                if (focused == null) {
                                    focused = wRoot.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY);
                                }
                                if (focused != null) break;
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }

        // If no node has focus yet inside the app, pick the first visible interactive element
        if (focused == null) {
            focused = findBestInteractiveNode(root);
            if (focused != null) {
                try {
                    focused.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
                    focused.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS);
                } catch (Throwable ignored) {}
            }
        }

        return focused;
    }

    private static AccessibilityNodeInfo findBestInteractiveNode(AccessibilityNodeInfo root) {
        if (root == null) return null;
        List<AccessibilityNodeInfo> candidates = new ArrayList<>();
        Rect screenRect = new Rect(0, 0, sScreenWidth, sScreenHeight);
        collectInteractiveNodes(root, candidates, screenRect);
        if (!candidates.isEmpty()) {
            return candidates.get(0);
        }
        return null;
    }

    private static void collectInteractiveNodes(AccessibilityNodeInfo node, List<AccessibilityNodeInfo> list, Rect screenRect) {
        if (node == null || !node.isVisibleToUser()) return;
        Rect r = new Rect();
        node.getBoundsInScreen(r);
        if (r.width() > 0 && r.height() > 0 && Rect.intersects(screenRect, r)) {
            // Must NOT be a massive full-screen background container (e.g. DecorView)
            boolean isNotHugeWrapper = r.width() < screenRect.width() * 0.95 || r.height() < screenRect.height() * 0.95;
            if ((node.isFocusable() || node.isClickable()) && isNotHugeWrapper) {
                list.add(node);
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                collectInteractiveNodes(child, list, screenRect);
            }
        }
    }

    private static AccessibilityNodeInfo findScrollableParent(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo cur = node;
        while (cur != null) {
            if (cur.isScrollable()) {
                return cur;
            }
            cur = cur.getParent();
        }
        return null;
    }

    private static AccessibilityNodeInfo findClickableParent(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo cur = node;
        while (cur != null) {
            if (cur.isClickable()) {
                return cur;
            }
            cur = cur.getParent();
        }
        return null;
    }

    // =========================================================================
    // Comprehensive D-Pad Navigation Engine (Native + Spatial Geometry)
    // =========================================================================

    private static long sLastDpadTime = 0;
    private static int sLastDpadKey = 0;

    public static boolean sendDpad(int keycode) {
        if (sInstance == null) return false;
        long now = System.currentTimeMillis();
        if (keycode == sLastDpadKey && (now - sLastDpadTime) < 60) {
            return true; // Debounce rapid synthetic duplicate
        }
        sLastDpadKey = keycode;
        sLastDpadTime = now;

        try {
            sInstance.updateScreenDimensions();
            AccessibilityNodeInfo root = getBestRootNode();
            AccessibilityNodeInfo focused = getCurrentFocusNode(root);

            // Handle DPAD_CENTER (OK) and ENTER (F-13: click OR tap, never both)
            if (keycode == 23 || keycode == 66) {
                if (focused != null) {
                    boolean ok = focused.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    if (!ok) ok = focused.performAction(AccessibilityNodeInfo.ACTION_SELECT);
                    if (!ok) {
                        AccessibilityNodeInfo parentClickable = findClickableParent(focused);
                        if (parentClickable != null) {
                            ok = parentClickable.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                        }
                    }
                    if (ok) return true; // Action was processed by UI node, do NOT tap!
                    Rect bounds = new Rect();
                    focused.getBoundsInScreen(bounds);
                    if (bounds.width() > 0 && bounds.height() > 0) {
                        dispatchTap(bounds.centerX(), bounds.centerY());
                        return true;
                    }
                }
                dispatchTap(sScreenWidth / 2.0f, sScreenHeight / 2.0f);
                return true;
            }

            int direction = -1;
            if (keycode == 19) direction = View.FOCUS_UP;
            else if (keycode == 20) direction = View.FOCUS_DOWN;
            else if (keycode == 21) direction = View.FOCUS_LEFT;
            else if (keycode == 22) direction = View.FOCUS_RIGHT;

            if (direction != -1) {
                // 1. Try standard focusSearch first
                if (focused != null) {
                    AccessibilityNodeInfo next = null;
                    try {
                        next = focused.focusSearch(direction);
                    } catch (Throwable ignored) {}

                    if (next != null && !next.equals(focused)) {
                        next.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
                        next.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS);
                        try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                next.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.getId());
                            }
                        } catch (Throwable ignored) {}
                        return true;
                    }
                }

                // 2. Spatial Geometric Directional Search across the entire visible window
                if (root != null) {
                    List<AccessibilityNodeInfo> candidates = new ArrayList<>();
                    Rect screenRect = new Rect(0, 0, sScreenWidth, sScreenHeight);
                    collectInteractiveNodes(root, candidates, screenRect);

                    Rect curRect = new Rect();
                    if (focused != null) {
                        focused.getBoundsInScreen(curRect);
                    } else {
                        // Default reference point
                        curRect.set(sScreenWidth / 2 - 20, sScreenHeight / 2 - 20, sScreenWidth / 2 + 20, sScreenHeight / 2 + 20);
                    }

                    AccessibilityNodeInfo bestCandidate = null;
                    double bestScore = Double.MAX_VALUE;

                    for (AccessibilityNodeInfo cand : candidates) {
                        if (cand == null || cand.equals(focused)) continue;
                        Rect candRect = new Rect();
                        cand.getBoundsInScreen(candRect);

                        double score = Double.MAX_VALUE;
                        if (direction == View.FOCUS_RIGHT) {
                            if (candRect.centerX() > curRect.centerX() + 6) {
                                double dx = candRect.centerX() - curRect.centerX();
                                double dy = Math.abs(candRect.centerY() - curRect.centerY());
                                score = dx + (dy * 2.2);
                            }
                        } else if (direction == View.FOCUS_LEFT) {
                            if (candRect.centerX() < curRect.centerX() - 6) {
                                double dx = curRect.centerX() - candRect.centerX();
                                double dy = Math.abs(candRect.centerY() - curRect.centerY());
                                score = dx + (dy * 2.2);
                            }
                        } else if (direction == View.FOCUS_DOWN) {
                            if (candRect.centerY() > curRect.centerY() + 6) {
                                double dy = candRect.centerY() - curRect.centerY();
                                double dx = Math.abs(candRect.centerX() - curRect.centerX());
                                score = dy + (dx * 1.8);
                            }
                        } else if (direction == View.FOCUS_UP) {
                            if (candRect.centerY() < curRect.centerY() - 6) {
                                double dy = curRect.centerY() - candRect.centerY();
                                double dx = Math.abs(candRect.centerX() - curRect.centerX());
                                score = dy + (dx * 1.8);
                            }
                        }

                        if (score < bestScore) {
                            bestScore = score;
                            bestCandidate = cand;
                        }
                    }

                    if (bestCandidate != null) {
                        bestCandidate.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
                        bestCandidate.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS);
                        try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                bestCandidate.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.getId());
                            }
                        } catch (Throwable ignored) {}
                        return true;
                    }
                }

                // 3. Container Scroll fallback when reaching edge of row or list
                AccessibilityNodeInfo scrollable = (focused != null) ? findScrollableParent(focused) : null;
                if (scrollable != null) {
                    if (direction == View.FOCUS_DOWN || direction == View.FOCUS_RIGHT) {
                        scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
                    } else {
                        scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);
                    }
                    return true;
                }

                // 4. Edge fallback swipe (custom video player controls / canvases)
                float cx = sScreenWidth / 2.0f;
                float cy = sScreenHeight / 2.0f;
                if (keycode == 19) { // UP
                    dispatchSwipe(cx, cy - 120, cx, cy + 120, 60);
                } else if (keycode == 20) { // DOWN
                    dispatchSwipe(cx, cy + 120, cx, cy - 120, 60);
                } else if (keycode == 21) { // LEFT
                    dispatchSwipe(cx - 180, cy, cx + 180, cy, 60);
                } else if (keycode == 22) { // RIGHT
                    dispatchSwipe(cx + 180, cy, cx - 180, cy, 60);
                }
                return true;
            }
        } catch (Throwable t) {
            Log.e(TAG, "Error in sendDpad: " + t.getMessage(), t);
        }
        return false;
    }

    public static void dispatchSwipe(float startX, float startY, float endX, float endY, long durationMs) {
        if (sInstance == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return;
        try {
            Path path = new Path();
            path.moveTo(startX, startY);
            path.lineTo(endX, endY);
            GestureDescription.Builder builder = new GestureDescription.Builder();
            builder.addStroke(new GestureDescription.StrokeDescription(path, 0, durationMs));
            sInstance.dispatchGesture(builder.build(), null, null);
        } catch (Throwable t) {
            Log.w(TAG, "Error in dispatchSwipe: " + t.getMessage());
        }
    }

    public static void dispatchTap(float x, float y) {
        if (sInstance == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return;
        try {
            Path path = new Path();
            path.moveTo(x, y);
            GestureDescription.Builder builder = new GestureDescription.Builder();
            builder.addStroke(new GestureDescription.StrokeDescription(path, 0, 45));
            sInstance.dispatchGesture(builder.build(), null, null);
        } catch (Throwable t) {
            Log.w(TAG, "Error in dispatchTap: " + t.getMessage());
        }
    }

    // =========================================================================
    // Live Remote Text Input & Backspace (Search Bars, Passwords, URLs)
    // =========================================================================

    public static boolean sendText(String text) {
        if (sInstance == null || text == null) return false;
        try {
            AccessibilityNodeInfo root = getBestRootNode();
            if (root == null) return false;

            AccessibilityNodeInfo target = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
            if (target == null) {
                target = root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY);
            }
            if (target == null || !target.isEditable()) {
                target = findEditableNode(root);
            }

            // Also check all windows for editable input
            if (target == null) {
                List<AccessibilityWindowInfo> windows = sInstance.getWindows();
                if (windows != null) {
                    for (AccessibilityWindowInfo win : windows) {
                        if (win != null) {
                            AccessibilityNodeInfo wRoot = win.getRoot();
                            if (wRoot != null) {
                                target = findEditableNode(wRoot);
                                if (target != null) break;
                            }
                        }
                    }
                }
            }

            if (target != null) {
                Bundle args = new Bundle();
                args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
                return target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
            }
        } catch (Throwable t) {
            Log.w(TAG, "Error in sendText: " + t.getMessage());
        }
        return false;
    }

    public static boolean doBackspace() {
        if (sInstance == null) return false;
        try {
            AccessibilityNodeInfo root = getBestRootNode();
            if (root == null) return false;

            AccessibilityNodeInfo target = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
            if (target == null || !target.isEditable()) {
                target = findEditableNode(root);
            }
            if (target != null && target.getText() != null) {
                String current = target.getText().toString();
                if (!current.isEmpty()) {
                    int len = current.length();
                    int offset = Character.offsetByCodePoints(current, 0, Math.max(0, Character.codePointCount(current, 0, len) - 1));
                    String updated = current.substring(0, offset);
                    Bundle args = new Bundle();
                    args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, updated);
                    return target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private static AccessibilityNodeInfo findEditableNode(AccessibilityNodeInfo node) {
        if (node == null) return null;
        if (node.isEditable()) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                AccessibilityNodeInfo res = findEditableNode(child);
                if (res != null) return res;
            }
        }
        return null;
    }

    public static boolean takeScreenCapture(File dest) {
        if (sInstance == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return false;
        }
        try {
            final boolean[] done = new boolean[]{false};
            final File tempDest = new File(dest.getParentFile(), dest.getName() + ".tmp");
            sInstance.takeScreenshot(Display.DEFAULT_DISPLAY, sInstance.getMainExecutor(), new TakeScreenshotCallback() {
                @Override
                public void onSuccess(ScreenshotResult result) {
                    android.hardware.HardwareBuffer hwBuffer = null;
                    Bitmap bitmap = null;
                    Bitmap copy = null;
                    try {
                        hwBuffer = result.getHardwareBuffer();
                        bitmap = Bitmap.wrapHardwareBuffer(hwBuffer, result.getColorSpace());
                        if (bitmap != null) {
                            copy = bitmap.copy(Bitmap.Config.ARGB_8888, false);
                            if (dest.getParentFile() != null) dest.getParentFile().mkdirs();
                            try (FileOutputStream fos = new FileOutputStream(tempDest)) {
                                copy.compress(Bitmap.CompressFormat.JPEG, 85, fos);
                                fos.flush();
                            }
                            if (tempDest.exists() && tempDest.length() > 0) {
                                if (dest.exists()) dest.delete();
                                tempDest.renameTo(dest);
                            }
                            Log.i(TAG, "Native accessibility screenshot saved to " + dest.getAbsolutePath());
                        }
                    } catch (Throwable t) {
                        Log.e(TAG, "Failed to save screenshot bitmap: " + t.getMessage());
                        if (tempDest.exists()) tempDest.delete();
                    } finally {
                        if (copy != null) copy.recycle();
                        if (bitmap != null) bitmap.recycle();
                        if (hwBuffer != null) hwBuffer.close();
                        synchronized (done) {
                            done[0] = true;
                            done.notifyAll();
                        }
                    }
                }

                @Override
                public void onFailure(int errorCode) {
                    Log.w(TAG, "takeScreenshot onFailure code: " + errorCode);
                    synchronized (done) {
                        done[0] = true;
                        done.notifyAll();
                    }
                }
            });

            synchronized (done) {
                if (!done[0]) {
                    done.wait(3500);
                }
            }
            return dest.exists() && dest.length() > 0;
        } catch (Throwable t) {
            Log.w(TAG, "Error in takeScreenCapture: " + t.getMessage());
        }
        return false;
    }
}
