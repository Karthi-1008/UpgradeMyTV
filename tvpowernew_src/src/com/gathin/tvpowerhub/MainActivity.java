package com.gathin.tvpowerhub;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.nayuki.qrcodegen.QrCode;

public class MainActivity extends Activity implements NetworkMonitor.Listener {
    private static final String TAG = "TvHome";

    public enum ServerState {
        NO_NETWORK,
        STARTING,
        ACTIVE,
        STOPPED
    }

    private View statusDot;
    private TextView tvStatus;
    private TextView tvUrl;
    private Button btnRestart;
    private ImageView ivQr;
    private ImageView ivNoNet;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService mBgExecutor = Executors.newSingleThreadExecutor();

    private volatile ServerState mCurrentState = ServerState.STARTING;
    private volatile String mCurrentIp = null;
    private String mLastQrText = null;
    private Bitmap mLastQrBitmap = null;

    private final Runnable mPeriodicHealthCheck = new Runnable() {
        @Override
        public void run() {
            runServerHealthCheck();
            mHandler.postDelayed(this, 30_000); // 30s check while screen is visible
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusDot = findViewById(R.id.statusDot);
        tvStatus = findViewById(R.id.tvStatus);
        tvUrl = findViewById(R.id.tvUrl);
        btnRestart = findViewById(R.id.btnRestart);
        ivQr = findViewById(R.id.ivQr);
        ivNoNet = findViewById(R.id.ivNoNet);

        try {
            TclInputHelper.init(this);
            PowerHubService.enableAccessibilityServiceIfNeeded(this);
            Intent serviceIntent = new Intent(this, PowerHubService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
            PowerHubService.ensureServerRunning(this);
        } catch (Exception ignored) {}

        // Prompt for MANAGE_EXTERNAL_STORAGE on Android 11+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                } catch (Exception ignored) {}
            }
        }

        if (btnRestart != null) {
            btnRestart.requestFocus();
            btnRestart.setOnClickListener(v -> {
                btnRestart.setEnabled(false);
                Toast.makeText(MainActivity.this, "Restarting server…", Toast.LENGTH_SHORT).show();
                setServerState(ServerState.STARTING);
                mBgExecutor.execute(() -> {
                    PowerHubService.restartServer(MainActivity.this);
                    try { Thread.sleep(600); } catch (Exception ignored) {}
                    mHandler.post(() -> {
                        btnRestart.setEnabled(true);
                        runServerHealthCheck();
                    });
                });
            });
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        NetworkMonitor nm = PowerHubService.getNetworkMonitor(this);
        nm.addListener(this);
        mCurrentIp = nm.getCurrentIp();
        setServerState(mCurrentIp != null ? ServerState.STARTING : ServerState.NO_NETWORK);

        mHandler.removeCallbacks(mPeriodicHealthCheck);
        mHandler.post(mPeriodicHealthCheck);
    }

    @Override
    protected void onStop() {
        super.onStop();
        PowerHubService.getNetworkMonitor(this).removeListener(this);
        mHandler.removeCallbacks(mPeriodicHealthCheck);
    }

    @Override
    protected void onResume() {
        super.onResume();
        PowerHubService.ensureServerRunning(this);
        runServerHealthCheck();
        if (btnRestart != null) {
            btnRestart.requestFocus();
        }
    }

    @Override
    public void onIpChanged(String newIp) {
        Log.i(TAG, "IP changed: " + mCurrentIp + " -> " + newIp);
        mCurrentIp = newIp;
        mHandler.post(() -> {
            if (newIp == null) {
                setServerState(ServerState.NO_NETWORK);
            } else {
                setServerState(ServerState.STARTING);
                runServerHealthCheck();
            }
        });
    }

    private void setServerState(ServerState state) {
        mCurrentState = state;
        renderUi();
    }

    private void renderUi() {
        int port = PowerHubService.PRIMARY_PORT;
        String displayUrl = (mCurrentIp != null) ? ("http://" + mCurrentIp + ":" + port) : "—";
        String token = PowerHubService.getOrGeneratePairingToken(this);
        String fullUrlWithToken = (mCurrentIp != null) ? (displayUrl + "/#k=" + token) : null;

        if (tvUrl != null) {
            tvUrl.setText(displayUrl);
        }

        switch (mCurrentState) {
            case ACTIVE:
                if (statusDot != null) statusDot.setBackgroundColor(Color.parseColor("#4CD39A")); // Steady Green
                if (tvStatus != null) {
                    tvStatus.setText(R.string.status_active);
                    tvStatus.setTextColor(Color.parseColor("#A5A7AD"));
                }
                if (ivNoNet != null) ivNoNet.setVisibility(View.GONE);
                if (ivQr != null) {
                    ivQr.setVisibility(View.VISIBLE);
                    ivQr.setAlpha(1.0f);
                    renderQrAsync(fullUrlWithToken != null ? fullUrlWithToken : displayUrl);
                }
                break;

            case STARTING:
                if (statusDot != null) statusDot.setBackgroundColor(Color.parseColor("#F5B544")); // Amber
                if (tvStatus != null) {
                    tvStatus.setText(R.string.status_starting);
                    tvStatus.setTextColor(Color.parseColor("#F5B544"));
                }
                if (ivNoNet != null) ivNoNet.setVisibility(View.GONE);
                if (ivQr != null) {
                    ivQr.setVisibility(View.VISIBLE);
                    ivQr.setAlpha(0.35f);
                    if (fullUrlWithToken != null) {
                        renderQrAsync(fullUrlWithToken);
                    }
                }
                break;

            case NO_NETWORK:
                if (statusDot != null) statusDot.setBackgroundColor(Color.parseColor("#F5B544")); // Amber
                if (tvStatus != null) {
                    tvStatus.setText(R.string.status_no_network);
                    tvStatus.setTextColor(Color.parseColor("#F5B544"));
                }
                if (tvUrl != null) tvUrl.setText("—");
                if (ivQr != null) ivQr.setVisibility(View.GONE);
                if (ivNoNet != null) ivNoNet.setVisibility(View.VISIBLE);
                break;

            case STOPPED:
                if (statusDot != null) statusDot.setBackgroundColor(Color.parseColor("#FF5A5F")); // Red
                if (tvStatus != null) {
                    tvStatus.setText(R.string.status_stopped);
                    tvStatus.setTextColor(Color.parseColor("#FF5A5F"));
                }
                if (ivNoNet != null) ivNoNet.setVisibility(View.GONE);
                if (ivQr != null) {
                    ivQr.setVisibility(View.VISIBLE);
                    ivQr.setAlpha(0.35f);
                }
                if (btnRestart != null) btnRestart.requestFocus();
                break;
        }
    }

    private void renderQrAsync(String textToEncode) {
        if (textToEncode == null || textToEncode.isEmpty()) return;
        if (textToEncode.equals(mLastQrText) && mLastQrBitmap != null) {
            if (ivQr != null) ivQr.setImageBitmap(mLastQrBitmap);
            return;
        }

        mBgExecutor.execute(() -> {
            try {
                // High contrast standard QR: Black modules (0xFF000000) on White (0xFFFFFFFF)
                // 4-module quiet zone built into bitmap per README 3 §3.2
                Bitmap bitmap = qrBitmap(textToEncode, 720);
                mLastQrText = textToEncode;
                mLastQrBitmap = bitmap;

                mHandler.post(() -> {
                    if (ivQr != null) {
                        ivQr.setImageBitmap(bitmap);
                        if (mCurrentState == ServerState.ACTIVE) {
                            ivQr.setAlpha(1.0f);
                        }
                    }
                });
            } catch (Throwable t) {
                Log.w(TAG, "QR generation failed: " + t.getMessage());
            }
        });
    }

    /**
     * Fast, pure integer module scale QR generation.
     * Complies with README 3 §3.2 & §3.10: Black modules on pure white, 4-module quiet zone.
     */
    private static Bitmap qrBitmap(String text, int targetPx) {
        QrCode qr = QrCode.encodeText(text, QrCode.Ecc.MEDIUM);
        int border = 4;
        int modules = qr.size + border * 2;
        int scale = Math.max(1, targetPx / modules);
        int size = modules * scale;
        int[] pix = new int[size * size];

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int modX = x / scale - border;
                int modY = y / scale - border;
                boolean dark = qr.getModule(modX, modY);
                pix[y * size + x] = dark ? 0xFF000000 : 0xFFFFFFFF;
            }
        }

        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        b.setPixels(pix, 0, size, 0, 0, size, size);
        return b;
    }

    private void runServerHealthCheck() {
        if (mCurrentIp == null) {
            setServerState(ServerState.NO_NETWORK);
            return;
        }

        mBgExecutor.execute(() -> {
            boolean ok = false;
            try {
                URL url = new URL("http://127.0.0.1:" + PowerHubService.PRIMARY_PORT + "/api?action=ping");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(1000);
                conn.setReadTimeout(1000);
                int code = conn.getResponseCode();
                if (code == 200) {
                    try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                        String line;
                        while ((line = br.readLine()) != null) {
                            if (line.contains("tvpower")) {
                                ok = true;
                                break;
                            }
                        }
                    }
                }
                conn.disconnect();
            } catch (Throwable ignored) {}

            final boolean isHealthy = ok;
            mHandler.post(() -> {
                if (mCurrentIp == null) {
                    setServerState(ServerState.NO_NETWORK);
                } else if (isHealthy) {
                    setServerState(ServerState.ACTIVE);
                } else {
                    if (mCurrentState != ServerState.STARTING) {
                        setServerState(ServerState.STOPPED);
                    }
                }
            });
        });
    }
}
