package com.gathin.tvpowerhub.ws;

import android.content.Context;
import android.media.AudioManager;
import android.os.SystemClock;
import android.util.Base64;
import android.util.Log;

import com.gathin.tvpowerhub.PowerHubService;
import com.gathin.tvpowerhub.TvAccessibilityService;
import com.gathin.tvpowerhub.TvKeyboardService;
import com.gathin.tvpowerhub.http.HttpRequest;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class WebSocketHandler {
    private static final String TAG = "WebSocketHandler";
    private static final String WS_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    private final Context mContext;
    private final Socket mSocket;
    private final HttpRequest mInitialRequest;
    private final InputStream mIn;
    private final OutputStream mOut;
    private volatile boolean mOpen = true;

    // Server-side repeat timer for smooth hold-to-repeat (A2)
    private static final ScheduledExecutorService sRepeatExecutor = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> mActiveRepeatFuture = null;
    private int mRepeatingKeyCode = -1;

    public WebSocketHandler(Context context, Socket socket, HttpRequest initialRequest,
                            InputStream in, OutputStream out) {
        this.mContext = context;
        this.mSocket = socket;
        this.mInitialRequest = initialRequest;
        this.mIn = in;
        this.mOut = out;
    }

    public void run() {
        try {
            // 1. Verify WebSocket upgrade headers
            String secKey = mInitialRequest.getHeader("sec-websocket-key");
            if (secKey == null || secKey.trim().isEmpty()) {
                mSocket.close();
                return;
            }

            // 2. Perform RFC 6455 handshake
            String acceptKey = generateAcceptKey(secKey.trim());
            String handshake = "HTTP/1.1 101 Switching Protocols\r\n" +
                    "Upgrade: websocket\r\n" +
                    "Connection: Upgrade\r\n" +
                    "Sec-WebSocket-Accept: " + acceptKey + "\r\n" +
                    "\r\n";
            mOut.write(handshake.getBytes(StandardCharsets.UTF_8));
            mOut.flush();

            Log.i(TAG, "WebSocket connection established from " + mSocket.getRemoteSocketAddress());
            sendTextMessage(new JSONObject()
                    .put("t", "connected")
                    .put("msg", "TV Powerhouse WebSocket Ready")
                    .put("time", System.currentTimeMillis())
                    .toString());

            // 3. Frame processing loop
            mSocket.setSoTimeout(30000); // 30s read timeout
            while (mOpen && !mSocket.isClosed()) {
                int b0 = mIn.read();
                if (b0 == -1) break;

                boolean fin = (b0 & 0x80) != 0;
                int opcode = b0 & 0x0F;

                int b1 = mIn.read();
                if (b1 == -1) break;

                boolean masked = (b1 & 0x80) != 0;
                long payloadLen = b1 & 0x7F;

                if (payloadLen == 126) {
                    int b2 = mIn.read();
                    int b3 = mIn.read();
                    if (b2 == -1 || b3 == -1) break;
                    payloadLen = ((b2 & 0xFF) << 8) | (b3 & 0xFF);
                } else if (payloadLen == 127) {
                    byte[] lenBytes = new byte[8];
                    int r = readFully(mIn, lenBytes, 0, 8);
                    if (r < 8) break;
                    payloadLen = 0;
                    for (int i = 0; i < 8; i++) {
                        payloadLen = (payloadLen << 8) | (lenBytes[i] & 0xFF);
                    }
                }

                if (payloadLen > 65536) { // Hard cap at 64KB for TV RAM safety
                    Log.w(TAG, "WebSocket frame too large: " + payloadLen);
                    sendClose(1009, "Frame exceeds 64KB");
                    break;
                }

                byte[] mask = null;
                if (masked) {
                    mask = new byte[4];
                    int r = readFully(mIn, mask, 0, 4);
                    if (r < 4) break;
                }

                byte[] payload = new byte[(int) payloadLen];
                if (payloadLen > 0) {
                    int r = readFully(mIn, payload, 0, (int) payloadLen);
                    if (r < payloadLen) break;
                    if (masked && mask != null) {
                        for (int i = 0; i < payload.length; i++) {
                            payload[i] = (byte) (payload[i] ^ mask[i % 4]);
                        }
                    }
                }

                handleFrame(opcode, payload);
            }
        } catch (Exception e) {
            Log.d(TAG, "WebSocket closed: " + e.getMessage());
        } finally {
            close();
        }
    }

    private void handleFrame(int opcode, byte[] payload) throws Exception {
        switch (opcode) {
            case 1: // Text frame
                String text = new String(payload, StandardCharsets.UTF_8);
                handleMessage(text);
                break;
            case 8: // Connection Close
                sendClose(1000, "Normal closure");
                mOpen = false;
                break;
            case 9: // Ping
                sendFrame(10, payload); // Pong with identical payload
                break;
            case 10: // Pong
                break;
            default:
                break;
        }
    }

    private void handleMessage(String msg) {
        long startTime = SystemClock.elapsedRealtime();
        try {
            JSONObject json = new JSONObject(msg);
            String type = json.optString("t", "");
            long id = json.optLong("id", -1);

            if ("ping".equals(type)) {
                sendTextMessage(new JSONObject().put("t", "pong").put("time", System.currentTimeMillis()).toString());
                return;
            }

            if ("key".equals(type)) {
                int code = json.optInt("c", -1);
                if (code > 0) {
                    executeKey(code);
                }
                long elapsed = SystemClock.elapsedRealtime() - startTime;
                if (id >= 0) {
                    sendTextMessage(new JSONObject()
                            .put("ack", id)
                            .put("ms", elapsed)
                            .put("via", "a11y")
                            .toString());
                }
                return;
            }

            if ("keyrep".equals(type)) {
                int code = json.optInt("c", -1);
                startKeyRepeat(code);
                if (id >= 0) {
                    sendTextMessage(new JSONObject().put("ack", id).put("repeating", code).toString());
                }
                return;
            }

            if ("keyup".equals(type)) {
                stopKeyRepeat();
                if (id >= 0) {
                    sendTextMessage(new JSONObject().put("ack", id).put("repeat_stopped", true).toString());
                }
                return;
            }

            if ("vol".equals(type)) {
                int delta = json.optInt("v", 0);
                AudioManager am = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
                if (am != null) {
                    if (delta > 0) am.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI);
                    else if (delta < 0) am.adjustVolume(AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI);
                }
                if (id >= 0) {
                    sendTextMessage(new JSONObject().put("ack", id).put("vol_adjusted", delta).toString());
                }
                return;
            }

            if ("text".equals(type)) {
                String val = json.optString("v", "");
                String mode = json.optString("mode", "append");
                boolean handled = false;
                String engine = "none";
                if (TvKeyboardService.isImeConnected()) {
                    if ("replace".equalsIgnoreCase(mode)) {
                        handled = TvKeyboardService.replaceText(val);
                    } else {
                        handled = TvKeyboardService.commitUnicodeText(val);
                    }
                    if (handled) engine = "ime";
                }
                if (!handled) {
                    handled = TvAccessibilityService.sendText(val);
                    if (handled) engine = "a11y";
                }
                if (id >= 0) {
                    sendTextMessage(new JSONObject()
                            .put("ack", id)
                            .put("handled", handled)
                            .put("engine", engine)
                            .toString());
                }
                return;
            }

            if ("action".equals(type)) {
                String act = json.optString("a", "");
                if ("home".equals(act)) TvAccessibilityService.doHome(mContext);
                else if ("back".equals(act)) TvAccessibilityService.doBack();
                else if ("settings".equals(act)) TvAccessibilityService.doSettings(mContext);
                else if ("input".equals(act)) TvAccessibilityService.doInputSource(mContext);
                if (id >= 0) {
                    sendTextMessage(new JSONObject().put("ack", id).put("action", act).toString());
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Error handling WS message: " + e.getMessage());
        }
    }

    private synchronized void startKeyRepeat(int code) {
        if (mRepeatingKeyCode == code && mActiveRepeatFuture != null && !mActiveRepeatFuture.isDone()) {
            return;
        }
        stopKeyRepeat();
        mRepeatingKeyCode = code;
        executeKey(code);
        // Server-side repeat at 14 Hz (approx 70ms interval) with initial 350ms delay
        mActiveRepeatFuture = sRepeatExecutor.scheduleWithFixedDelay(() -> {
            if (mOpen && mRepeatingKeyCode > 0) {
                executeKey(mRepeatingKeyCode);
            }
        }, 350, 70, TimeUnit.MILLISECONDS);
    }

    private synchronized void stopKeyRepeat() {
        mRepeatingKeyCode = -1;
        if (mActiveRepeatFuture != null) {
            mActiveRepeatFuture.cancel(true);
            mActiveRepeatFuture = null;
        }
    }

    private void executeKey(int codeInt) {
        if (codeInt == 24) { // VOLUME_UP
            AudioManager am = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
            if (am != null) am.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI);
        } else if (codeInt == 25) { // VOLUME_DOWN
            AudioManager am = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
            if (am != null) am.adjustVolume(AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI);
        } else if (codeInt == 164) { // VOLUME_MUTE
            AudioManager am = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
            if (am != null) am.adjustVolume(AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI);
        } else if (codeInt == 3) { // HOME
            TvAccessibilityService.doHome(mContext);
        } else if (codeInt == 4) { // BACK
            TvAccessibilityService.doBack();
        } else if (codeInt == 176 || codeInt == 82) { // SETTINGS / MENU
            TvAccessibilityService.doSettings(mContext);
        } else if (codeInt == 178) { // INPUT SOURCE
            TvAccessibilityService.doInputSource(mContext);
        } else if (codeInt == 187) { // RECENTS
            TvAccessibilityService.doRecents();
        } else if (codeInt == 67) { // BACKSPACE
            TvAccessibilityService.doBackspace();
        } else if (codeInt == 85 || codeInt == 87 || codeInt == 88 || codeInt == 89 || codeInt == 90 || codeInt == 126 || codeInt == 127) { // MEDIA
            TvAccessibilityService.sendMedia(mContext, codeInt);
        } else {
            TvAccessibilityService.sendDpad(codeInt);
        }
    }

    public synchronized void sendTextMessage(String message) {
        if (!mOpen) return;
        byte[] payload = message.getBytes(StandardCharsets.UTF_8);
        try {
            sendFrame(1, payload); // 1 = Text frame
        } catch (IOException e) {
            close();
        }
    }

    private synchronized void sendClose(int code, String reason) {
        if (!mOpen) return;
        try {
            byte[] reasonBytes = reason.getBytes(StandardCharsets.UTF_8);
            byte[] payload = new byte[2 + reasonBytes.length];
            payload[0] = (byte) ((code >> 8) & 0xFF);
            payload[1] = (byte) (code & 0xFF);
            System.arraycopy(reasonBytes, 0, payload, 2, reasonBytes.length);
            sendFrame(8, payload);
        } catch (Exception ignored) {}
    }

    private synchronized void sendFrame(int opcode, byte[] payload) throws IOException {
        int len = payload.length;
        ByteArrayOutputStream outBuf = new ByteArrayOutputStream();
        outBuf.write(0x80 | (opcode & 0x0F)); // FIN + opcode

        // Server to client frames are unmasked (mask bit = 0)
        if (len <= 125) {
            outBuf.write(len);
        } else if (len <= 65535) {
            outBuf.write(126);
            outBuf.write((len >> 8) & 0xFF);
            outBuf.write(len & 0xFF);
        } else {
            outBuf.write(127);
            for (int i = 7; i >= 0; i--) {
                outBuf.write((int) ((len >> (8 * i)) & 0xFF));
            }
        }

        if (len > 0) {
            outBuf.write(payload);
        }

        mOut.write(outBuf.toByteArray());
        mOut.flush();
    }

    public static String generateAcceptKey(String clientKey) {
        try {
            String combined = clientKey + WS_GUID;
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] hash = md.digest(combined.getBytes(StandardCharsets.UTF_8));
            return Base64.encodeToString(hash, Base64.NO_WRAP);
        } catch (Exception e) {
            throw new RuntimeException("SHA-1 digest unavailable", e);
        }
    }

    private static int readFully(InputStream in, byte[] b, int off, int len) throws IOException {
        int total = 0;
        while (total < len) {
            int r = in.read(b, off + total, len - total);
            if (r < 0) break;
            total += r;
        }
        return total;
    }

    public synchronized void close() {
        mOpen = false;
        stopKeyRepeat();
        try {
            mSocket.close();
        } catch (Exception ignored) {}
    }
}
