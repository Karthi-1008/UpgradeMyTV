package com.gathin.tvpowerhub;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.IBinder;
import android.text.format.Formatter;
import android.util.Log;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.List;

public class PowerHubService extends Service {
    private static final String TAG = "PowerHubService";
    private static final String CHANNEL_ID = "tv_power_service_channel";
    private static final int NOTIFICATION_ID = 1001;

    public static final int PRIMARY_PORT = 8088;
    public static final int SECONDARY_PORT = 8080;

    private static final String PREFS_NAME = "tv_power_hub_prefs";
    private static final String KEY_PAIRING_TOKEN = "pairing_token";
    public static final String MASTER_KEY = "TV_POWER_SECURE_KEY";

    private static TvHttpServer sPrimaryServer;
    private static TvHttpServer sSecondaryServer;
    private BroadcastReceiver mUsbReceiver;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForegroundNotification();
        getOrGeneratePairingToken(this);
        TclInputHelper.init(this);
        enableAccessibilityServiceIfNeeded(this);
        ensureServerRunning(this);
        registerUsbReceiver();
        getNetworkMonitor(this);
        startRamGuardian();
    }

    public static synchronized String getOrGeneratePairingToken(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String token = prefs.getString(KEY_PAIRING_TOKEN, null);
        if (token == null || token.isEmpty()) {
            token = generateSecureToken();
            prefs.edit().putString(KEY_PAIRING_TOKEN, token).apply();
            Log.i(TAG, "Generated fresh pairing token: " + token);
        }
        return token;
    }

    public static synchronized String rotatePairingToken(Context context) {
        String newToken = generateSecureToken();
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString(KEY_PAIRING_TOKEN, newToken).apply();
        Log.i(TAG, "Rotated pairing token to: " + newToken);
        return newToken;
    }

    public static synchronized String getTvDeviceId(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String id = prefs.getString("tv_device_id", null);
        if (id == null || id.isEmpty()) {
            id = java.util.UUID.randomUUID().toString().substring(0, 8);
            prefs.edit().putString("tv_device_id", id).apply();
        }
        return id;
    }

    private static String generateSecureToken() {
        byte[] bytes = new byte[16];
        new SecureRandom().nextBytes(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public static boolean isValidToken(Context context, String suppliedKey) {
        if (suppliedKey == null || suppliedKey.isEmpty()) return false;
        String token = getOrGeneratePairingToken(context);

        // Constant-time comparison to prevent timing attacks (S-01)
        byte[] tokenBytes = token.getBytes(StandardCharsets.UTF_8);
        byte[] suppliedBytes = suppliedKey.getBytes(StandardCharsets.UTF_8);
        if (MessageDigest.isEqual(tokenBytes, suppliedBytes)) {
            return true;
        }

        // Also allow pre-shared master key for trusted tools/scripts
        byte[] masterBytes = MASTER_KEY.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(masterBytes, suppliedBytes);
    }

    private void registerUsbReceiver() {
        if (mUsbReceiver == null) {
            mUsbReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    Log.i(TAG, "USB Media hotplug event: " + (intent != null ? intent.getAction() : "null"));
                    UsbStorageHelper.invalidateCache();
                }
            };
            try {
                IntentFilter filter = new IntentFilter();
                filter.addAction(Intent.ACTION_MEDIA_MOUNTED);
                filter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
                filter.addAction(Intent.ACTION_MEDIA_EJECT);
                filter.addAction(Intent.ACTION_MEDIA_REMOVED);
                filter.addDataScheme("file");
                if (Build.VERSION.SDK_INT >= 33) {
                    registerReceiver(mUsbReceiver, filter, Context.RECEIVER_EXPORTED);
                } else {
                    registerReceiver(mUsbReceiver, filter);
                }
                Log.i(TAG, "Runtime USB hotplug receiver registered");
            } catch (Throwable t) {
                Log.w(TAG, "Failed to register USB receiver: " + t.getMessage());
            }
        }
    }

    public static void enableAccessibilityServiceIfNeeded(Context context) {
        try {
            String serviceName = context.getPackageName() + "/" + TvAccessibilityService.class.getName();
            String enabledServices = android.provider.Settings.Secure.getString(
                    context.getContentResolver(),
                    android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            );
            if (enabledServices == null || !enabledServices.contains(serviceName)) {
                String newServices = (enabledServices == null || enabledServices.isEmpty()) ?
                        serviceName : enabledServices + ":" + serviceName;
                android.provider.Settings.Secure.putString(
                        context.getContentResolver(),
                        android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                        newServices
                );
            }
            android.provider.Settings.Secure.putInt(
                    context.getContentResolver(),
                    android.provider.Settings.Secure.ACCESSIBILITY_ENABLED,
                    1
            );
            Log.i(TAG, "Accessibility service auto-enabled successfully!");
        } catch (Throwable t) {
            Log.w(TAG, "Could not auto-enable accessibility: " + t.getMessage());
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        ensureServerRunning(this);
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "TV Power House Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Keeps TV Power House Web Remote & Storage Server active");
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void startForegroundNotification() {
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        Notification notification = builder
                .setContentTitle("TV Power House Active")
                .setContentText("Web Remote & Storage server is running")
                .setSmallIcon(R.drawable.ic_launcher)
                .setOngoing(true)
                .build();

        startForeground(NOTIFICATION_ID, notification);
    }

    public static synchronized void ensureServerRunning(Context context) {
        if (sPrimaryServer == null || !sPrimaryServer.isAlive()) {
            if (sPrimaryServer != null) sPrimaryServer.stop();
            sPrimaryServer = new TvHttpServer(context.getApplicationContext(), PRIMARY_PORT);
            sPrimaryServer.start();
            Log.i(TAG, "Started Primary Server on port " + PRIMARY_PORT);
        }
        if (sSecondaryServer == null || !sSecondaryServer.isAlive()) {
            if (sSecondaryServer != null) sSecondaryServer.stop();
            sSecondaryServer = new TvHttpServer(context.getApplicationContext(), SECONDARY_PORT);
            sSecondaryServer.start();
            Log.i(TAG, "Started Secondary Server on port " + SECONDARY_PORT);
        }
    }

    public static synchronized void restartServer(Context context) {
        if (sNetworkMonitor != null) {
            sNetworkMonitor.stop();
            sNetworkMonitor = null;
        }
        if (sPrimaryServer != null) {
            sPrimaryServer.stop();
            sPrimaryServer = null;
        }
        if (sSecondaryServer != null) {
            sSecondaryServer.stop();
            sSecondaryServer = null;
        }
        ensureServerRunning(context);
    }

    private static NetworkMonitor sNetworkMonitor;

    public static synchronized NetworkMonitor getNetworkMonitor(Context context) {
        if (sNetworkMonitor == null) {
            sNetworkMonitor = new NetworkMonitor(context.getApplicationContext());
            sNetworkMonitor.start();
        }
        return sNetworkMonitor;
    }

    public static String getTvIpAddress(Context context) {
        NetworkMonitor nm = getNetworkMonitor(context);
        String ip = nm.getCurrentIp();
        if (ip != null) return ip;
        return nm.readIpv4();
    }

    private static java.util.concurrent.ScheduledExecutorService sGuardianExecutor;

    private void startRamGuardian() {
        if (sGuardianExecutor != null && !sGuardianExecutor.isShutdown()) return;
        sGuardianExecutor = java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
        sGuardianExecutor.scheduleWithFixedDelay(() -> {
            try {
                android.app.ActivityManager am = (android.app.ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
                if (am != null) {
                    android.app.ActivityManager.MemoryInfo mi = new android.app.ActivityManager.MemoryInfo();
                    am.getMemoryInfo(mi);
                    long freeMb = mi.availMem / (1024 * 1024);
                    // Safe cleanup only when dangerously low (<100MB) without killing user apps
                    if (freeMb < 100) {
                        Log.w(TAG, "Critical low memory (" + freeMb + " MB). Running gentle memory trim...");
                        System.gc();
                    }
                }
            } catch (Throwable ignored) {}
        }, 60, 60, java.util.concurrent.TimeUnit.SECONDS);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mUsbReceiver != null) {
            try { unregisterReceiver(mUsbReceiver); } catch (Throwable ignored) {}
            mUsbReceiver = null;
        }
        if (sGuardianExecutor != null) {
            try { sGuardianExecutor.shutdownNow(); } catch (Throwable ignored) {}
            sGuardianExecutor = null;
        }
        if (sNetworkMonitor != null) {
            sNetworkMonitor.stop();
            sNetworkMonitor = null;
        }
        if (sPrimaryServer != null) {
            sPrimaryServer.stop();
            sPrimaryServer = null;
        }
        if (sSecondaryServer != null) {
            sSecondaryServer.stop();
            sSecondaryServer = null;
        }
    }
}
