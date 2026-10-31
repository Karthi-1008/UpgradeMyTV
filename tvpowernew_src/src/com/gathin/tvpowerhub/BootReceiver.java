package com.gathin.tvpowerhub;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent != null ? intent.getAction() : null;
        Log.i(TAG, "TV boot/power event received: " + action + ". Starting TV Power New server...");
        try {
            TclInputHelper.init(context);
            PowerHubService.enableAccessibilityServiceIfNeeded(context);
            Intent serviceIntent = new Intent(context, PowerHubService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
            PowerHubService.ensureServerRunning(context);
        } catch (Exception e) {
            Log.e(TAG, "Error starting service on boot: " + e.getMessage());
        }
    }
}
