package com.gathin.tvpowerhub;

import android.content.Context;
import android.util.Log;

import com.gathin.tvpowerhub.http.HttpRequest;
import com.gathin.tvpowerhub.http.Router;
import com.gathin.tvpowerhub.ws.WebSocketHandler;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TvHttpServer {
    private static final String TAG = "TvHttpServer";

    private final Context mContext;
    private final int mPort;
    private ServerSocket mServerSocket;
    private volatile boolean mRunning = false;
    private final ExecutorService mThreadPool = Executors.newFixedThreadPool(16);

    public TvHttpServer(Context context, int port) {
        this.mContext = context;
        this.mPort = port;
    }

    public synchronized boolean isAlive() {
        return mRunning && mServerSocket != null && !mServerSocket.isClosed() && mServerSocket.isBound();
    }

    public synchronized void start() {
        if (mRunning) return;
        mRunning = true;
        new Thread(() -> {
            try {
                mServerSocket = new ServerSocket();
                mServerSocket.setReuseAddress(true);
                mServerSocket.bind(new InetSocketAddress(mPort));
                Log.i(TAG, "TvHttpServer bound and listening on port " + mPort);
                while (mRunning) {
                    try {
                        Socket client = mServerSocket.accept();
                        mThreadPool.execute(() -> handleClient(client));
                    } catch (Exception e) {
                        if (!mRunning) break;
                    }
                }
            } catch (Exception e) {
                mRunning = false;
                Log.e(TAG, "Fatal ServerSocket bind error on port " + mPort + ": " + e.getMessage());
            }
        }).start();
    }

    public synchronized void stop() {
        mRunning = false;
        try {
            if (mServerSocket != null && !mServerSocket.isClosed()) {
                mServerSocket.close();
            }
        } catch (Exception ignored) {}
    }

    private void handleClient(Socket socket) {
        try {
            socket.setSoTimeout(15000);
            InputStream rawIn = socket.getInputStream();
            BufferedInputStream in = new BufferedInputStream(rawIn);
            OutputStream out = new BufferedOutputStream(socket.getOutputStream());

            HttpRequest req = HttpRequest.parse(in);
            if (req == null) {
                socket.close();
                return;
            }

            // Check if client is requesting RFC 6455 WebSocket Upgrade
            if (req.isWebSocketUpgrade()) {
                WebSocketHandler ws = new WebSocketHandler(mContext, socket, req, in, out);
                ws.run();
                return;
            }

            // Route standard HTTP request
            Router.route(mContext, socket, req, in, out, mPort);
            out.flush();
            socket.close();
        } catch (Exception e) {
            try { socket.close(); } catch (Exception ignored) {}
        }
    }
}
