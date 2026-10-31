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

import java.io.File;
import java.io.FileOutputStream;

public class TvAccessibilityService extends AccessibilityService {
    private static final String TAG = "TvAccessibilityService";
    private static volatile TvAccessibilityService sInstance;

    private static int sScreenWidth = 1280;
    private static int sScreenHeight = 720;

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
                sScreenWidth = dm.widthPixels > 0 ? dm.widthPixels : 1280;
                sScreenHeight = dm.heightPixels > 0 ? dm.heightPixels : 720;
            }
        } catch (Throwable ignored) {}
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // No-op
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
        // 1. TCL native on-screen Source Choice popup service
        try {
            Intent intent = new Intent("com.tcl.insrc.SHOW_WINDOW");
            intent.setComponent(new android.content.ComponentName("com.tcl.tv", "com.tcl.sourcemenu.insrc.ShowWindowService"));
            if (sInstance != null) {
                sInstance.startService(intent);
            } else {
                context.startService(intent);
            }
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "ShowWindowService start failed: " + t.getMessage());
        }

        try {
            Intent intent = new Intent("com.tcl.insrc.SHOW_WINDOW");
            if (sInstance != null) {
                sInstance.startService(intent);
            } else {
                context.startService(intent);
            }
            return true;
        } catch (Throwable ignored) {}

        // Broadcast fallbacks
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
                AccessibilityNodeInfo root = sInstance.getRootInActiveWindow();
                if (root != null && root.getPackageName() != null) {
                    pkg = root.getPackageName().toString();
                }
            } catch (Throwable ignored) {}
        }

        if (pkg == null || pkg.isEmpty()) {
            try {
                android.app.ActivityManager am = (android.app.ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
                if (am != null) {
                    java.util.List<android.app.ActivityManager.RunningAppProcessInfo> procs = am.getRunningAppProcesses();
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

        // Clean user-friendly app titles
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
            return "TV Power New";
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

        // Direct fallback navigation in media players
        if (keycode == 89) { // Rewind / Skip 10s back -> Left focus or swipe
            sendDpad(21);
        } else if (keycode == 90) { // Fast Forward / Skip 10s forward -> Right focus or swipe
            sendDpad(22);
        } else if (keycode == 85 || keycode == 126 || keycode == 127) { // Play/Pause -> Click
            sendDpad(23);
        }
    }

    private static long sLastDpadTime = 0;
    private static int sLastDpadKey = 0;

    public static boolean sendDpad(int keycode) {
        if (sInstance == null) return false;
        long now = android.os.SystemClock.uptimeMillis();
        if (keycode == sLastDpadKey && (now - sLastDpadTime) < 70) {
            return true; // Debounce rapid duplicate ghost tap
        }
        sLastDpadKey = keycode;
        sLastDpadTime = now;
        try {
            sInstance.updateScreenDimensions();
            AccessibilityNodeInfo root = sInstance.getRootInActiveWindow();

            AccessibilityNodeInfo focused = null;
            if (root != null) {
                focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
                if (focused == null) {
                    focused = root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY);
                }
                if (focused == null) {
                    focused = findFirstFocusable(root);
                    if (focused != null) {
                        focused.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
                        focused.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS);
                    }
                }
            }

            int direction = -1;
            if (keycode == 19) direction = View.FOCUS_UP;
            else if (keycode == 20) direction = View.FOCUS_DOWN;
            else if (keycode == 21) direction = View.FOCUS_LEFT;
            else if (keycode == 22) direction = View.FOCUS_RIGHT;
            else if (keycode == 23 || keycode == 66) {
                // DPAD_CENTER or ENTER
                if (focused != null) {
                    AccessibilityNodeInfo clickable = focused;
                    while (clickable != null && !clickable.isClickable()) {
                        clickable = clickable.getParent();
                    }
                    if (clickable != null) {
                        boolean ok = clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                        if (!ok) ok = clickable.performAction(AccessibilityNodeInfo.ACTION_SELECT);
                        if (ok) return true;
                    } else {
                        boolean ok = focused.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                        if (!ok) ok = focused.performAction(AccessibilityNodeInfo.ACTION_SELECT);
                        if (ok) return true;
                    }
                    Rect bounds = new Rect();
                    (clickable != null ? clickable : focused).getBoundsInScreen(bounds);
                    if (bounds.width() > 0 && bounds.height() > 0) {
                        dispatchTap(bounds.centerX(), bounds.centerY());
                        return true;
                    }
                }
                dispatchTap(sScreenWidth / 2.0f, sScreenHeight / 2.0f);
                return true;
            }

            if (direction != -1) {
                if (focused != null) {
                    AccessibilityNodeInfo next = focused.focusSearch(direction);
                    if (next != null) {
                        next.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
                        next.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS);
                        return true;
                    }
                }

                // Fallback to gesture swipe for custom UI / video players
                float cx = sScreenWidth / 2.0f;
                float cy = sScreenHeight / 2.0f;
                if (keycode == 19) { // UP -> swipe down
                    dispatchSwipe(cx, cy - 140, cx, cy + 140, 60);
                } else if (keycode == 20) { // DOWN -> swipe up
                    dispatchSwipe(cx, cy + 140, cx, cy - 140, 60);
                } else if (keycode == 21) { // LEFT -> swipe right
                    dispatchSwipe(cx - 200, cy, cx + 200, cy, 60);
                } else if (keycode == 22) { // RIGHT -> swipe left
                    dispatchSwipe(cx + 200, cy, cx - 200, cy, 60);
                }
                return true;
            }
        } catch (Throwable t) {
            Log.w(TAG, "Error in sendDpad: " + t.getMessage());
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

    public static boolean sendText(String text) {
        if (sInstance == null || text == null) return false;
        try {
            AccessibilityNodeInfo root = sInstance.getRootInActiveWindow();
            if (root == null) return false;
            AccessibilityNodeInfo target = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
            if (target == null) {
                target = root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY);
            }
            if (target == null || !target.isEditable()) {
                target = findEditableNode(root);
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
            AccessibilityNodeInfo root = sInstance.getRootInActiveWindow();
            if (root == null) return false;
            AccessibilityNodeInfo target = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
            if (target == null || !target.isEditable()) {
                target = findEditableNode(root);
            }
            if (target != null && target.getText() != null) {
                String current = target.getText().toString();
                if (!current.isEmpty()) {
                    String updated = current.substring(0, current.length() - 1);
                    Bundle args = new Bundle();
                    args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, updated);
                    return target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private static AccessibilityNodeInfo findFirstFocusable(AccessibilityNodeInfo node) {
        if (node == null) return null;
        if (node.isFocusable() || node.isClickable()) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                AccessibilityNodeInfo res = findFirstFocusable(child);
                if (res != null) return res;
            }
        }
        return null;
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
            sInstance.takeScreenshot(Display.DEFAULT_DISPLAY, sInstance.getMainExecutor(), new TakeScreenshotCallback() {
                @Override
                public void onSuccess(ScreenshotResult result) {
                    try {
                        Bitmap bitmap = Bitmap.wrapHardwareBuffer(result.getHardwareBuffer(), result.getColorSpace());
                        if (bitmap != null) {
                            Bitmap copy = bitmap.copy(Bitmap.Config.ARGB_8888, false);
                            if (dest.getParentFile() != null) dest.getParentFile().mkdirs();
                            FileOutputStream fos = new FileOutputStream(dest);
                            copy.compress(Bitmap.CompressFormat.PNG, 85, fos);
                            fos.flush();
                            fos.close();
                            Log.i(TAG, "Native accessibility screenshot saved to " + dest.getAbsolutePath());
                        }
                    } catch (Throwable t) {
                        Log.e(TAG, "Failed to save screenshot bitmap: " + t.getMessage());
                    } finally {
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
                    done.wait(1500);
                }
            }
            return dest.exists() && dest.length() > 0;
        } catch (Throwable t) {
            Log.w(TAG, "Error in takeScreenCapture: " + t.getMessage());
        }
        return false;
    }
}
