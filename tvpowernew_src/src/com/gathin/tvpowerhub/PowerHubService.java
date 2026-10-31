package com.gathin.tvpowerhub;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.IBinder;
import android.text.format.Formatter;
import android.util.Log;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.List;

public class PowerHubService extends Service {
    private static final String TAG = "PowerHubService";
    private static final String CHANNEL_ID = "tv_power_service_channel";
    private static final int NOTIFICATION_ID = 1001;

    public static final int PRIMARY_PORT = 8088;
    public static final int SECONDARY_PORT = 8080;

    private static TvHttpServer sPrimaryServer;
    private static TvHttpServer sSecondaryServer;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForegroundNotification();
        TclInputHelper.init(this);
        enableAccessibilityServiceIfNeeded(this);
        ensureServerRunning(this);
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
                    "TV Power Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Keeps TV Web Remote & RAM Guardian active");
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
                .setContentTitle("TV Power New Active")
                .setContentText("Web Remote server is running in background")
                .setSmallIcon(R.drawable.ic_launcher)
                .setOngoing(true)
                .build();

        startForeground(NOTIFICATION_ID, notification);
    }

    public static synchronized void ensureServerRunning(Context context) {
        if (sPrimaryServer == null) {
            sPrimaryServer = new TvHttpServer(context.getApplicationContext(), PRIMARY_PORT);
            sPrimaryServer.start();
            Log.i(TAG, "Started Java HTTP Server on port " + PRIMARY_PORT);
        }
        if (sSecondaryServer == null) {
            sSecondaryServer = new TvHttpServer(context.getApplicationContext(), SECONDARY_PORT);
            sSecondaryServer.start();
            Log.i(TAG, "Started Java HTTP Server on port " + SECONDARY_PORT);
        }
    }

    public static synchronized void restartServer(Context context) {
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

    public static String getTvIpAddress(Context context) {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface intf : interfaces) {
                if (intf.isLoopback() || !intf.isUp()) continue;
                List<InetAddress> addrs = Collections.list(intf.getInetAddresses());
                for (InetAddress addr : addrs) {
                    if (!addr.isLoopbackAddress() && addr instanceof Inet4Address) {
                        String host = addr.getHostAddress();
                        if (host != null && !host.startsWith("127.")) {
                            return host;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        try {
            WifiManager wm = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm != null) {
                WifiInfo info = wm.getConnectionInfo();
                if (info != null) {
                    int ip = info.getIpAddress();
                    if (ip != 0) {
                        return Formatter.formatIpAddress(ip);
                    }
                }
            }
        } catch (Exception ignored) {}

        return "192.168.1.100";
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }
}
