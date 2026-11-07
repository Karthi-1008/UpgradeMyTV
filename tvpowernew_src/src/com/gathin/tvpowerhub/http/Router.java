package com.gathin.tvpowerhub.http;

import android.content.Context;
import android.util.Log;

import com.gathin.tvpowerhub.PowerHubService;
import com.gathin.tvpowerhub.api.DiagApi;
import com.gathin.tvpowerhub.api.FilesApi;
import com.gathin.tvpowerhub.api.KeyApi;
import com.gathin.tvpowerhub.api.MediaApi;
import com.gathin.tvpowerhub.api.SystemApi;
import com.gathin.tvpowerhub.api.TextApi;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public class Router {
    private static final String TAG = "Router";

    public static void route(Context context, Socket socket, HttpRequest req, InputStream in, OutputStream out, int port) throws Exception {
        HttpResponse resp = new HttpResponse(out);
        String path = req.getPath();
        String method = req.getMethod();

        // 1. Handle CORS pre-flight OPTIONS request
        if ("OPTIONS".equalsIgnoreCase(method)) {
            String corsHeader = "HTTP/1.1 204 No Content\r\n" +
                    "Access-Control-Allow-Origin: *\r\n" +
                    "Access-Control-Allow-Methods: GET, POST, OPTIONS, PUT, DELETE\r\n" +
                    "Access-Control-Allow-Headers: Content-Type, X-TVHub-Key, Range\r\n" +
                    "Connection: close\r\n\r\n";
            out.write(corsHeader.getBytes("UTF-8"));
            out.flush();
            return;
        }

        // 2. Public health check / discovery (ping)
        if (path.equals("/ping") || path.endsWith("/ping") || "ping".equals(req.getParam("action"))) {
            DiagApi.handlePing(context, port, req, resp);
            return;
        }

        // 3. Authenticate private API endpoints
        boolean isApi = path.equals("/cgi-bin/api") || path.equals("/api") || path.startsWith("/api/");
        if (isApi) {
            String suppliedKey = req.getParam("key");
            if (suppliedKey == null || suppliedKey.isEmpty()) {
                suppliedKey = req.getHeader("x-tvhub-key");
            }

            if (!PowerHubService.isValidToken(context, suppliedKey)) {
                resp.sendError(401, "UNAUTHORIZED", "Unauthorized: Invalid or missing pairing key",
                        "Scan the TV QR code to pair your device");
                return;
            }

            // Determine API action
            String action = req.getParam("action");
            if (action == null && path.startsWith("/api/v1/")) {
                action = path.substring("/api/v1/".length());
            } else if (action == null && path.startsWith("/api/")) {
                action = path.substring("/api/".length());
            }

            if (action == null || action.isEmpty()) {
                resp.sendError(400, "BAD_REQUEST", "Missing action", "Specify an action parameter or route");
                return;
            }

            handleApiAction(context, socket, action, req, in, resp, out);
            return;
        }

        // 4. Handle Static Web Assets and Screen Capture
        handleStatic(context, req, resp, out);
    }

    private static void handleApiAction(Context context, Socket socket, String action,
                                        HttpRequest req, InputStream in, HttpResponse resp, OutputStream out) throws Exception {
        switch (action) {
            case "ping":
                DiagApi.handlePing(context, 8080, req, resp);
                break;
            case "capabilities":
                DiagApi.handleCapabilities(context, req, resp);
                break;
            case "status":
                DiagApi.handleStatus(context, SystemApi.getRemainingSleepMinutes(), req, resp);
                break;
            case "key":
                KeyApi.handleKey(context, req, resp);
                break;
            case "text":
                TextApi.handleText(context, req, resp);
                break;
            case "input_source":
                KeyApi.handleInputSource(context, req, resp);
                break;
            case "screenshot":
                SystemApi.handleScreenshot(context, req, resp);
                break;
            case "boost":
                SystemApi.handleBoost(context, req, resp);
                break;
            case "open_url":
                SystemApi.handleOpenUrl(context, req, resp);
                break;
            case "sleep_timer":
                SystemApi.handleSleepTimer(context, req, resp);
                break;
            case "send_toast":
            case "notify":
                SystemApi.handleNotify(context, req, resp);
                break;
            case "media":
                MediaApi.handleMedia(context, req, resp);
                break;
            case "now_playing":
            case "media/now_playing":
                MediaApi.handleNowPlaying(context, req, resp);
                break;
            case "seek":
            case "media/seek":
                MediaApi.handleSeek(context, req, resp);
                break;
            case "get_volume":
            case "volume":
                MediaApi.handleGetVolume(context, req, resp);
                break;
            case "set_volume":
            case "volume/set":
                MediaApi.handleSetVolume(context, req, resp);
                break;
            case "list_files":
            case "files":
                FilesApi.handleListFiles(context, req, resp);
                break;
            case "download_file":
            case "files/download":
                FilesApi.handleDownloadFile(context, req, resp, out);
                break;
            case "download_zip":
            case "files/download_zip":
                FilesApi.handleDownloadZip(context, req, resp, out);
                break;
            case "upload_file":
            case "files/upload":
                FilesApi.handleUploadFile(context, socket, req, in, resp, out);
                break;
            case "delete_file":
            case "files/delete":
                FilesApi.handleDeleteFile(context, req, resp);
                break;
            case "restore_file":
            case "files/restore":
                FilesApi.handleRestoreFile(context, req, resp);
                break;
            case "play_on_tv":
            case "files/play":
                FilesApi.handlePlayOnTv(context, req, resp);
                break;
            case "copy_file":
            case "files/copy":
                FilesApi.handleCopyFile(context, req, resp);
                break;
            case "stream_media":
            case "files/stream":
                FilesApi.handleStreamMedia(context, req, resp, out);
                break;
            case "usb_benchmark":
                FilesApi.handleUsbBenchmark(context, req, resp);
                break;
            default:
                resp.sendError(400, "UNKNOWN_ACTION", "Unknown API action: " + action, "Check /api/v1/capabilities for supported actions");
                break;
        }
    }

    private static void handleStatic(Context context, HttpRequest req, HttpResponse resp, OutputStream out) throws Exception {
        String path = req.getPath();
        String cleanPath = path;
        if (cleanPath.equals("/") || cleanPath.isEmpty()) {
            cleanPath = "/index.html";
        }

        // Screen capture image
        if (cleanPath.equals("/screen.png")) {
            String suppliedKey = req.getParam("key");
            if (suppliedKey == null) suppliedKey = req.getHeader("x-tvhub-key");
            if (!PowerHubService.isValidToken(context, suppliedKey)) {
                resp.sendError(401, "UNAUTHORIZED", "Unauthorized to view screen capture", null);
                return;
            }

            File screenFile = new File(context.getExternalFilesDir(null), "screen.png");
            if (!screenFile.exists()) screenFile = new File(context.getFilesDir(), "screen.png");
            if (screenFile.exists()) {
                resp.sendFileWithRange(screenFile, req.getHeader("range"));
            } else {
                resp.sendError(404, "NOT_FOUND", "No screen capture available yet", "Trigger /api/v1/screenshot first");
            }
            return;
        }

        // Read asset from APK bundled assets with complete stream loop
        String assetPath = "web" + (cleanPath.startsWith("/") ? cleanPath : "/" + cleanPath);
        try {
            InputStream is = context.getAssets().open(assetPath);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[32768];
            int n;
            while ((n = is.read(buf)) != -1) {
                baos.write(buf, 0, n);
            }
            is.close();
            byte[] bytes = baos.toByteArray();
            String mime = HttpResponse.getMimeType(cleanPath);
            boolean cacheable = !cleanPath.endsWith(".html") && !cleanPath.endsWith(".js") && !cleanPath.endsWith(".json");
            resp.sendBytes(200, mime, bytes, cacheable);
            return;
        } catch (Exception ignored) {}

        // Fallback: check /data/local/tmp/www
        File tmpFile = new File("/data/local/tmp/www", cleanPath.startsWith("/") ? cleanPath.substring(1) : cleanPath);
        if (tmpFile.exists() && tmpFile.isFile()) {
            resp.sendFileWithRange(tmpFile, req.getHeader("range"));
            return;
        }

        resp.sendError(404, "NOT_FOUND", "Resource not found: " + path, null);
    }
}
