package com.gathin.tvpowerhub.http;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class HttpResponse {
    private final OutputStream mOut;

    public HttpResponse(OutputStream out) {
        this.mOut = out;
    }

    public OutputStream getOutputStream() {
        return mOut;
    }

    public void sendJson(int code, JSONObject json) throws IOException {
        String s = json != null ? json.toString() : "{}";
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        String header = "HTTP/1.1 " + code + " " + getStatusText(code) + "\r\n" +
                "Content-Type: application/json; charset=utf-8\r\n" +
                "Content-Length: " + bytes.length + "\r\n" +
                "Cache-Control: no-cache, no-store, must-revalidate\r\n" +
                "Connection: close\r\n\r\n";
        mOut.write(header.getBytes(StandardCharsets.UTF_8));
        mOut.write(bytes);
        mOut.flush();
    }

    public void sendJsonWithCors(int code, JSONObject json) throws IOException {
        String s = json != null ? json.toString() : "{}";
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        String header = "HTTP/1.1 " + code + " " + getStatusText(code) + "\r\n" +
                "Content-Type: application/json; charset=utf-8\r\n" +
                "Content-Length: " + bytes.length + "\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Access-Control-Allow-Methods: GET, POST, OPTIONS, PUT, DELETE\r\n" +
                "Access-Control-Allow-Headers: Content-Type, X-TVHub-Key\r\n" +
                "Cache-Control: no-cache, no-store, must-revalidate\r\n" +
                "Connection: close\r\n\r\n";
        mOut.write(header.getBytes(StandardCharsets.UTF_8));
        mOut.write(bytes);
        mOut.flush();
    }

    public void sendSuccess(JSONObject data) throws IOException {
        JSONObject res = new JSONObject();
        try {
            res.put("ok", true);
            res.put("data", data != null ? data : new JSONObject());
        } catch (Exception ignored) {}
        sendJsonWithCors(200, res);
    }

    public void sendError(int code, String errCode, String message, String hint) throws IOException {
        JSONObject res = new JSONObject();
        try {
            res.put("ok", false);
            res.put("code", errCode != null ? errCode : "ERROR");
            res.put("message", message != null ? message : "An error occurred");
            if (hint != null) res.put("hint", hint);
        } catch (Exception ignored) {}
        sendJsonWithCors(code, res);
    }

    public void sendBytes(int code, String contentType, byte[] data, boolean cacheable) throws IOException {
        String cacheHeader = cacheable ? "Cache-Control: public, max-age=86400\r\n" : "Cache-Control: no-cache, no-store, must-revalidate\r\nPragma: no-cache\r\nExpires: 0\r\n";
        String header = "HTTP/1.1 " + code + " " + getStatusText(code) + "\r\n" +
                "Content-Type: " + contentType + "\r\n" +
                "Content-Length: " + data.length + "\r\n" +
                cacheHeader +
                "Access-Control-Allow-Origin: *\r\n" +
                "Connection: close\r\n\r\n";
        mOut.write(header.getBytes(StandardCharsets.UTF_8));
        mOut.write(data);
        mOut.flush();
    }

    public void sendStream(int code, String contentType, InputStream in, long length) throws IOException {
        String header = "HTTP/1.1 " + code + " " + getStatusText(code) + "\r\n" +
                "Content-Type: " + contentType + "\r\n" +
                (length >= 0 ? "Content-Length: " + length + "\r\n" : "Transfer-Encoding: chunked\r\n") +
                "Access-Control-Allow-Origin: *\r\n" +
                "Connection: close\r\n\r\n";
        mOut.write(header.getBytes(StandardCharsets.UTF_8));

        byte[] buf = new byte[16384];
        int r;
        while ((r = in.read(buf)) != -1) {
            mOut.write(buf, 0, r);
        }
        mOut.flush();
    }

    public void sendFileWithRange(File file, String rangeHeader) throws IOException {
        long fileLength = file.length();
        long start = 0;
        long end = fileLength - 1;
        boolean isRange = false;

        if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
            String[] ranges = rangeHeader.substring(6).split("-");
            try {
                if (!ranges[0].isEmpty()) start = Long.parseLong(ranges[0]);
                if (ranges.length > 1 && !ranges[1].isEmpty()) end = Long.parseLong(ranges[1]);
                if (end >= fileLength) end = fileLength - 1;
                isRange = true;
            } catch (Exception ignored) {}
        }

        long contentLength = end - start + 1;
        String mime = getMimeType(file.getName());

        String header;
        if (isRange) {
            header = "HTTP/1.1 206 Partial Content\r\n" +
                    "Content-Type: " + mime + "\r\n" +
                    "Content-Range: bytes " + start + "-" + end + "/" + fileLength + "\r\n" +
                    "Content-Length: " + contentLength + "\r\n" +
                    "Accept-Ranges: bytes\r\n" +
                    "Access-Control-Allow-Origin: *\r\n" +
                    "Connection: close\r\n\r\n";
        } else {
            header = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: " + mime + "\r\n" +
                    "Content-Length: " + fileLength + "\r\n" +
                    "Accept-Ranges: bytes\r\n" +
                    "Access-Control-Allow-Origin: *\r\n" +
                    "Connection: close\r\n\r\n";
        }

        mOut.write(header.getBytes(StandardCharsets.UTF_8));

        try (FileInputStream fis = new FileInputStream(file)) {
            if (start > 0) {
                long skipped = fis.skip(start);
            }
            byte[] buf = new byte[32768];
            long remaining = contentLength;
            while (remaining > 0) {
                int toRead = (int) Math.min(buf.length, remaining);
                int read = fis.read(buf, 0, toRead);
                if (read <= 0) break;
                mOut.write(buf, 0, read);
                remaining -= read;
            }
        }
        mOut.flush();
    }

    public static String getMimeType(String name) {
        String n = name.toLowerCase();
        if (n.endsWith(".html") || n.endsWith(".htm")) return "text/html; charset=utf-8";
        if (n.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (n.endsWith(".css")) return "text/css; charset=utf-8";
        if (n.endsWith(".json")) return "application/json; charset=utf-8";
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        if (n.endsWith(".webp")) return "image/webp";
        if (n.endsWith(".svg")) return "image/svg+xml";
        if (n.endsWith(".ico")) return "image/x-icon";
        if (n.endsWith(".woff2")) return "font/woff2";
        if (n.endsWith(".mp4")) return "video/mp4";
        if (n.endsWith(".mkv")) return "video/x-matroska";
        if (n.endsWith(".mp3")) return "audio/mpeg";
        if (n.endsWith(".apk")) return "application/vnd.android.package-archive";
        return "application/octet-stream";
    }

    private static String getStatusText(int code) {
        switch (code) {
            case 200: return "OK";
            case 201: return "Created";
            case 204: return "No Content";
            case 206: return "Partial Content";
            case 301: return "Moved Permanently";
            case 304: return "Not Modified";
            case 400: return "Bad Request";
            case 401: return "Unauthorized";
            case 403: return "Forbidden";
            case 404: return "Not Found";
            case 409: return "Conflict";
            case 500: return "Internal Server Error";
            case 501: return "Not Implemented";
            default: return "Response";
        }
    }
}
