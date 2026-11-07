package com.gathin.tvpowerhub.http;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class HttpRequest {
    private final String mMethod;
    private final String mUri;
    private final String mPath;
    private final String mQueryString;
    private final Map<String, String> mHeaders;
    private final Map<String, String> mParams;
    private final byte[] mBody;

    private HttpRequest(String method, String uri, String path, String queryString,
                        Map<String, String> headers, Map<String, String> params, byte[] body) {
        this.mMethod = method;
        this.mUri = uri;
        this.mPath = path;
        this.mQueryString = queryString;
        this.mHeaders = headers;
        this.mParams = params;
        this.mBody = body;
    }

    public String getMethod() { return mMethod; }
    public String getUri() { return mUri; }
    public String getPath() { return mPath; }
    public String getQueryString() { return mQueryString; }
    public Map<String, String> getHeaders() { return mHeaders; }
    public Map<String, String> getParams() { return mParams; }
    public byte[] getBody() { return mBody; }

    public String getHeader(String name) {
        if (name == null) return null;
        return mHeaders.get(name.toLowerCase());
    }

    public String getParam(String name) {
        return mParams.get(name);
    }

    public String getParam(String name, String fallback) {
        String val = mParams.get(name);
        return (val != null && !val.isEmpty()) ? val : fallback;
    }

    public int getIntParam(String name, int fallback) {
        String val = mParams.get(name);
        if (val == null || val.isEmpty()) return fallback;
        try {
            return Integer.parseInt(val);
        } catch (Exception e) {
            return fallback;
        }
    }

    public boolean isWebSocketUpgrade() {
        String upgrade = getHeader("upgrade");
        String connection = getHeader("connection");
        return "websocket".equalsIgnoreCase(upgrade) &&
               connection != null && connection.toLowerCase().contains("upgrade");
    }

    public static HttpRequest parse(BufferedInputStream in) throws IOException {
        ByteArrayOutputStream lineBuf = new ByteArrayOutputStream();
        List<String> headerLines = new ArrayList<>();
        int prev = -1;
        int b;
        int totalHeaderBytes = 0;

        while ((b = in.read()) != -1) {
            totalHeaderBytes++;
            if (totalHeaderBytes > 16384) { // Cap headers at 16KB to prevent DoS
                throw new IOException("HTTP headers exceed 16KB limit");
            }
            if (prev == '\r' && b == '\n') {
                byte[] rawLine = lineBuf.toByteArray();
                String line = new String(rawLine, 0, rawLine.length - 1, StandardCharsets.UTF_8).trim();
                lineBuf.reset();
                if (line.isEmpty()) break;
                headerLines.add(line);
            } else {
                lineBuf.write(b);
            }
            prev = b;
        }

        if (headerLines.isEmpty()) {
            return null;
        }

        String requestLine = headerLines.get(0);
        String[] parts = requestLine.split(" ");
        if (parts.length < 2) {
            return null;
        }

        String method = parts[0].toUpperCase();
        String uri = parts[1];

        Map<String, String> headers = new HashMap<>();
        for (int i = 1; i < headerLines.size(); i++) {
            String h = headerLines.get(i);
            int colon = h.indexOf(':');
            if (colon > 0) {
                headers.put(h.substring(0, colon).trim().toLowerCase(), h.substring(colon + 1).trim());
            }
        }

        String path = uri;
        String queryString = "";
        int qIdx = uri.indexOf('?');
        if (qIdx >= 0) {
            path = uri.substring(0, qIdx);
            queryString = uri.substring(qIdx + 1);
        }

        Map<String, String> params = parseQueryString(queryString);

        long contentLength = 0;
        if (headers.containsKey("content-length")) {
            try {
                contentLength = Long.parseLong(headers.get("content-length"));
            } catch (Exception ignored) {}
        }

        byte[] body = null;
        if (contentLength > 0 && contentLength <= 1048576) { // Read body up to 1MB into memory
            body = new byte[(int) contentLength];
            int totalRead = 0;
            while (totalRead < contentLength) {
                int r = in.read(body, totalRead, (int) (contentLength - totalRead));
                if (r < 0) break;
                totalRead += r;
            }

            // If JSON body, merge keys into params for convenience
            String ct = headers.get("content-type");
            if (ct != null && ct.contains("application/json")) {
                try {
                    JSONObject json = new JSONObject(new String(body, 0, totalRead, StandardCharsets.UTF_8));
                    for (Iterator<String> it = json.keys(); it.hasNext(); ) {
                        String k = it.next();
                        if (!params.containsKey(k)) {
                            params.put(k, json.optString(k, ""));
                        }
                    }
                } catch (Exception ignored) {}
            }
        }

        return new HttpRequest(method, uri, path, queryString, headers, params, body);
    }

    public static Map<String, String> parseQueryString(String qs) {
        Map<String, String> map = new HashMap<>();
        if (qs == null || qs.isEmpty()) return map;
        String[] pairs = qs.split("&");
        for (String pair : pairs) {
            if (pair.isEmpty()) continue;
            int idx = pair.indexOf("=");
            try {
                if (idx > 0) {
                    String k = URLDecoder.decode(pair.substring(0, idx), "UTF-8");
                    String v = URLDecoder.decode(pair.substring(idx + 1), "UTF-8");
                    map.put(k, v);
                } else {
                    String k = URLDecoder.decode(pair, "UTF-8");
                    map.put(k, "");
                }
            } catch (Exception ignored) {}
        }
        return map;
    }
}
