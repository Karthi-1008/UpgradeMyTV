package com.gathin.tvpowerhub;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import io.nayuki.qrcodegen.QrCode;

public class MainActivity extends Activity {
    private TextView tvUrl;
    private TextView tvStatus;
    private TextView tvStats;
    private Button btnToggle;
    private ImageView ivQrCode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Automatically start the server and keep it background running
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

        tvUrl = findViewById(R.id.tvUrl);
        tvStatus = findViewById(R.id.tvStatus);
        tvStats = findViewById(R.id.tvStats);
        btnToggle = findViewById(R.id.btnToggle);
        ivQrCode = findViewById(R.id.ivQrCode);

        // Focus Restart button by default for TV remote D-Pad navigation
        if (btnToggle != null) {
            btnToggle.requestFocus();
            btnToggle.setOnClickListener(v -> {
                Toast.makeText(MainActivity.this, "Restarting TV Server...", Toast.LENGTH_SHORT).show();
                PowerHubService.restartServer(MainActivity.this);
                btnToggle.postDelayed(this::updateUiAndQrCode, 1200);
            });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        PowerHubService.ensureServerRunning(this);
        updateUiAndQrCode();
    }

    private void updateUiAndQrCode() {
        String ip = PowerHubService.getTvIpAddress(this);
        String url = "http://" + ip + ":" + PowerHubService.PRIMARY_PORT;

        if (tvUrl != null) {
            tvUrl.setText(url);
        }
        if (tvStatus != null) {
            tvStatus.setText("\u25cf Server Active (Port " + PowerHubService.PRIMARY_PORT + " & " + PowerHubService.SECONDARY_PORT + ")");
        }
        if (tvStats != null) {
            tvStats.setText("TCL Smart TV C06 \u00b7 Native Java Web Server Active");
        }

        // Generate crisp QR Code bitmap
        try {
            QrCode qr = QrCode.encodeText(url, QrCode.Ecc.MEDIUM);
            int qrSize = qr.size;
            int scale = Math.max(8, 260 / qrSize);
            int bmpSize = qrSize * scale;

            Bitmap bitmap = Bitmap.createBitmap(bmpSize, bmpSize, Bitmap.Config.ARGB_8888);
            for (int y = 0; y < bmpSize; y++) {
                for (int x = 0; x < bmpSize; x++) {
                    boolean dark = qr.getModule(x / scale, y / scale);
                    bitmap.setPixel(x, y, dark ? Color.BLACK : Color.WHITE);
                }
            }
            if (ivQrCode != null) {
                ivQrCode.setImageBitmap(bitmap);
            }
        } catch (Exception e) {
            if (tvStatus != null) {
                tvStatus.setText("\u25cf Server Active (Scan URL: " + url + ")");
            }
        }
    }
}
