package com.gathin.tvpowerhub;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.audiofx.Equalizer;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONObject;

public class TvCalibratorHelper {
    private static final String TAG = "TvCalibrator";
    private static final String PREFS_NAME = "tv_calibrator_prefs";
    private static final String KEY_AUDIO_EQ = "audio_eq_mode";
    private static final String KEY_PICTURE_MODE = "picture_mode";
    private static final String CHANNEL_ID = "tv_mobile_relay";

    private static Equalizer sEqualizer = null;
    private static int sNotificationId = 1000;

    /**
     * Applies Acoustic EQ preset to the global audio session (session 0).
     */
    public static synchronized JSONObject applyAudioEq(Context context, String mode) {
        JSONObject result = new JSONObject();
        String cleanMode = (mode != null) ? mode.toLowerCase().trim() : "flat";
        String label = "Standard Audio (Flat)";

        // Set label based on selected profile
        switch (cleanMode) {
            case "dialogue_boost":
                label = "Dialogue Boost (+4.5dB Vocals)";
                break;
            case "night_mode":
                label = "Night Mode (Peak Cap Compression)";
                break;
            case "music":
                label = "Music Mode (Acoustic Balance)";
                break;
            case "flat":
            default:
                label = "Flat / Reset (Standard Audio)";
                break;
        }

        try {
            if (sEqualizer == null) {
                try {
                    sEqualizer = new Equalizer(0, 0); // Application priority 0 on session 0
                } catch (Throwable t) {
                    Log.w(TAG, "Equalizer session 0 attach note: " + t.getMessage());
                }
            }

            if (sEqualizer != null) {
                sEqualizer.setEnabled(true);
                short numBands = sEqualizer.getNumberOfBands();
                short[] bandRange = sEqualizer.getBandLevelRange(); // min and max in millibels (mB)
                short minLevel = bandRange != null && bandRange.length >= 2 ? bandRange[0] : (short) -1500;
                short maxLevel = bandRange != null && bandRange.length >= 2 ? bandRange[1] : (short) 1500;

                switch (cleanMode) {
                    case "dialogue_boost":
                        // Boost human voice range (800 Hz - 4.5 kHz) by +450 mB (+4.5 dB), roll off rumble (<250 Hz)
                        for (short b = 0; b < numBands; b++) {
                            int freq = sEqualizer.getCenterFreq(b);
                            if (freq < 250000) {
                                sEqualizer.setBandLevel(b, clamp((short) -200, minLevel, maxLevel));
                            } else if (freq >= 800000 && freq <= 4500000) {
                                sEqualizer.setBandLevel(b, clamp((short) 450, minLevel, maxLevel));
                            } else {
                                sEqualizer.setBandLevel(b, (short) 0);
                            }
                        }
                        break;

                    case "night_mode":
                        // Cut deep bass (<300 Hz) and harsh treble (>10 kHz) to keep sound intimate and non-disturbing
                        for (short b = 0; b < numBands; b++) {
                            int freq = sEqualizer.getCenterFreq(b);
                            if (freq < 300000) {
                                sEqualizer.setBandLevel(b, clamp((short) -400, minLevel, maxLevel));
                            } else if (freq > 10000000) {
                                sEqualizer.setBandLevel(b, clamp((short) -300, minLevel, maxLevel));
                            } else {
                                sEqualizer.setBandLevel(b, (short) 0);
                            }
                        }
                        break;

                    case "music":
                        // Balanced acoustic curve (+3dB bass, warm mids, +3dB crystal treble)
                        for (short b = 0; b < numBands; b++) {
                            int freq = sEqualizer.getCenterFreq(b);
                            if (freq < 150000) {
                                sEqualizer.setBandLevel(b, clamp((short) 300, minLevel, maxLevel));
                            } else if (freq < 400000) {
                                sEqualizer.setBandLevel(b, clamp((short) 150, minLevel, maxLevel));
                            } else if (freq > 8000000) {
                                sEqualizer.setBandLevel(b, clamp((short) 300, minLevel, maxLevel));
                            } else {
                                sEqualizer.setBandLevel(b, (short) 0);
                            }
                        }
                        break;

                    case "flat":
                    default:
                        for (short b = 0; b < numBands; b++) {
                            sEqualizer.setBandLevel(b, (short) 0);
                        }
                        sEqualizer.setEnabled(false);
                        break;
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "applyAudioEq error: " + t.getMessage());
        }

        // Persist mode in SharedPreferences
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_AUDIO_EQ, cleanMode).apply();

        // Broadcast to TV Audio subsystem
        try {
            Intent intent = new Intent("com.tcl.audio.mode.CHANGED");
            intent.putExtra("mode", cleanMode);
            context.sendBroadcast(intent);
        } catch (Throwable ignored) {}

        // Visual feedback on TV screen
        showTvNotification(context, "Acoustic EQ", label, "audio");

        try {
            result.put("status", "ok");
            result.put("action", "audio_eq");
            result.put("mode", cleanMode);
            result.put("label", label);
        } catch (Exception ignored) {}
        return result;
    }

    /**
     * Applies Display Calibration / Picture Mode.
     */
    public static synchronized JSONObject applyPictureMode(Context context, String mode) {
        JSONObject result = new JSONObject();
        String cleanMode = (mode != null) ? mode.toLowerCase().trim() : "standard";
        String label = "Standard Picture Profile";

        switch (cleanMode) {
            case "cinema":
                label = "Cinema Warm (D65 Color)";
                break;
            case "vivid":
            case "sports":
                label = "Vivid Sports (High Contrast)";
                break;
            case "game":
                label = "Game Mode (Low Latency ALLM)";
                break;
            case "standard":
            default:
                label = "Standard Picture Profile";
                break;
        }

        // Persist mode
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_PICTURE_MODE, cleanMode).apply();

        // Broadcast to TV Picture subsystem
        try {
            Intent intent = new Intent("com.tcl.picture.mode.CHANGED");
            intent.putExtra("mode", cleanMode);
            context.sendBroadcast(intent);
        } catch (Throwable ignored) {}

        // Visual feedback on TV screen
        showTvNotification(context, "Display Calibration", label, "display");

        try {
            result.put("status", "ok");
            result.put("action", "picture_mode");
            result.put("mode", cleanMode);
            result.put("label", label);
        } catch (Exception ignored) {}
        return result;
    }

    /**
     * Broadcasts a notification or toast alert directly to the TV screen.
     */
    public static JSONObject sendToastNotification(Context context, String title, String message, String type) {
        JSONObject result = new JSONObject();
        String displayTitle = (title != null && !title.trim().isEmpty()) ? title.trim() : "Mobile Alert";
        String displayMsg = (message != null && !message.trim().isEmpty()) ? message.trim() : "Notification received";
        String cleanType = (type != null) ? type.toLowerCase().trim() : "custom";

        // 1. Android TV Heads-Up System Notification (pops up in top right corner over any video/app)
        try {
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    NotificationChannel chan = new NotificationChannel(
                        CHANNEL_ID,
                        "Mobile TV Alerts",
                        NotificationManager.IMPORTANCE_HIGH
                    );
                    chan.setDescription("Phone alerts, calls, and messages broadcast to TV");
                    chan.enableLights(true);
                    chan.enableVibration(true);
                    nm.createNotificationChannel(chan);
                }

                Notification.Builder builder;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    builder = new Notification.Builder(context, CHANNEL_ID);
                } else {
                    builder = new Notification.Builder(context);
                }

                builder.setSmallIcon(R.drawable.ic_launcher)
                       .setContentTitle(displayTitle)
                       .setContentText(displayMsg)
                       .setAutoCancel(true);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    builder.setStyle(new Notification.BigTextStyle().bigText(displayMsg));
                }

                nm.notify(sNotificationId++, builder.build());
            }
        } catch (Throwable t) {
            Log.w(TAG, "Heads-up notification error: " + t.getMessage());
        }

        // 2. High-visibility Toast on TV screen
        showTvNotification(context, displayTitle, displayMsg, cleanType);

        try {
            result.put("status", "ok");
            result.put("action", "send_toast");
            result.put("title", displayTitle);
            result.put("message", displayMsg);
            result.put("type", cleanType);
        } catch (Exception ignored) {}
        return result;
    }

    /**
     * Helper to show a prominent on-screen Toast on the TV main looper.
     */
    public static void showTvNotification(Context context, String title, String message, String type) {
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                String prefix;
                switch (type != null ? type : "") {
                    case "call": prefix = "Incoming Call: "; break;
                    case "battery": prefix = "Battery Warning: "; break;
                    case "sms": prefix = "New Message: "; break;
                    case "audio": prefix = "Audio: "; break;
                    case "display": prefix = "Picture: "; break;
                    default: prefix = ""; break;
                }
                String text = prefix + title + "\n" + message;
                Toast toast = Toast.makeText(context.getApplicationContext(), text, Toast.LENGTH_LONG);
                toast.show();
            } catch (Throwable ignored) {}
        });
    }

    private static short clamp(short val, short min, short max) {
        if (val < min) return min;
        if (val > max) return max;
        return val;
    }
}
