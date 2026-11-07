package com.gathin.tvpowerhub;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

public class TclInputHelper {
    private static final String TAG = "TclInputHelper";
    private static final int DAEMON_PORT = 18888;
    private static final int DAEMON_TIMEOUT_MS = 60;
    private static boolean sDaemonAvailable = true;
    private static long sLastDaemonCheck = 0;

    public static synchronized void init(Context context) {
        new Thread(() -> {
            boolean ok = pingDaemon();
            sDaemonAvailable = ok;
            Log.i(TAG, "KeyDaemon probe: " + (ok ? "ONLINE (native key injection ready)" : "OFFLINE (will retry upon key events)"));
        }).start();
    }

    public static boolean sendKeyEvent(int keycode) {
        boolean ok = sendViaKeyDaemon(keycode);
        Log.i(TAG, "sendKeyEvent: keycode=" + keycode + " -> daemon=" + ok);
        return ok;
    }

    public static boolean sendText(String text) {
        if (text == null || text.isEmpty()) return true;
        boolean ok = sendTextViaKeyDaemon(text);
        Log.i(TAG, "sendText: text=" + text + " -> daemon=" + ok);
        return ok;
    }

    private static boolean pingDaemon() {
        Socket socket = null;
        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress("127.0.0.1", DAEMON_PORT), DAEMON_TIMEOUT_MS);
            socket.setSoTimeout(DAEMON_TIMEOUT_MS);
            OutputStream out = socket.getOutputStream();
            out.write("PING\n".getBytes("UTF-8"));
            out.flush();
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            String resp = in.readLine();
            return resp != null && resp.trim().startsWith("PONG");
        } catch (Throwable t) {
            return false;
        } finally {
            if (socket != null) {
                try { socket.close(); } catch (Throwable ignored) {}
            }
        }
    }

    private static boolean sendViaKeyDaemon(int keycode) {
        long now = System.currentTimeMillis();
        // If daemon failed recently, retry every 2 seconds
        if (!sDaemonAvailable && (now - sLastDaemonCheck) < 2000) {
            return false;
        }
        sLastDaemonCheck = now;

        Socket socket = null;
        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress("127.0.0.1", DAEMON_PORT), DAEMON_TIMEOUT_MS);
            socket.setSoTimeout(DAEMON_TIMEOUT_MS);
            OutputStream out = socket.getOutputStream();
            String msg = "KEY " + keycode + "\n";
            out.write(msg.getBytes("UTF-8"));
            out.flush();

            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            String resp = in.readLine();
            boolean ok = resp != null && resp.trim().startsWith("OK");
            sDaemonAvailable = ok;
            return ok;
        } catch (Throwable t) {
            sDaemonAvailable = false;
            return false;
        } finally {
            if (socket != null) {
                try { socket.close(); } catch (Throwable ignored) {}
            }
        }
    }

    private static boolean sendTextViaKeyDaemon(String text) {
        Socket socket = null;
        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress("127.0.0.1", DAEMON_PORT), DAEMON_TIMEOUT_MS * 3);
            socket.setSoTimeout(DAEMON_TIMEOUT_MS * 3);
            OutputStream out = socket.getOutputStream();
            String msg = "TEXT " + text + "\n";
            out.write(msg.getBytes("UTF-8"));
            out.flush();

            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            String resp = in.readLine();
            boolean ok = resp != null && resp.trim().startsWith("OK");
            sDaemonAvailable = ok;
            return ok;
        } catch (Throwable t) {
            sDaemonAvailable = false;
            return false;
        } finally {
            if (socket != null) {
                try { socket.close(); } catch (Throwable ignored) {}
            }
        }
    }
}
