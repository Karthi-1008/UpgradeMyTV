package com.gathin.tvpowerhub.api;

import android.content.Context;

import com.gathin.tvpowerhub.TvAccessibilityService;
import com.gathin.tvpowerhub.http.HttpRequest;
import com.gathin.tvpowerhub.http.HttpResponse;

import org.json.JSONObject;

import java.net.URLDecoder;

public class TextApi {

    public static void handleText(Context context, HttpRequest req, HttpResponse resp) throws Exception {
        String text = req.getParam("text");
        if (text == null) {
            text = req.getParam("v");
        }

        if (text != null) {
            String decoded = URLDecoder.decode(text, "UTF-8");
            boolean typed = TvAccessibilityService.sendText(decoded);
            JSONObject res = new JSONObject();
            res.put("status", "ok");
            res.put("action", "text");
            res.put("sent", decoded);
            res.put("handled", typed);
            resp.sendJson(200, res);
        } else {
            resp.sendError(400, "BAD_REQUEST", "Missing 'text' parameter", "Provide the text string to type");
        }
    }
}
