package com.gathin.tvpowerhub.api;

import android.content.Context;
import android.util.Log;

import com.gathin.tvpowerhub.TvAccessibilityService;
import com.gathin.tvpowerhub.TvKeyboardService;
import com.gathin.tvpowerhub.http.HttpRequest;
import com.gathin.tvpowerhub.http.HttpResponse;

import org.json.JSONObject;

import java.net.URLDecoder;

public class TextApi {
    private static final String TAG = "TextApi";

    public static void handleText(Context context, HttpRequest req, HttpResponse resp) throws Exception {
        String mode = req.getParam("mode", "append");
        String text = req.getParam("text");
        if (text == null) text = req.getParam("v");

        String subAction = req.getParam("sub_action");
        if ("backspace".equalsIgnoreCase(subAction)) {
            boolean handled = false;
            if (TvKeyboardService.isImeConnected()) {
                handled = TvKeyboardService.deleteBackspace();
            }
            if (!handled) {
                TvAccessibilityService.doBackspace();
                handled = true;
            }
            JSONObject res = new JSONObject();
            res.put("status", "ok");
            res.put("action", "text");
            res.put("sub_action", "backspace");
            res.put("handled", handled);
            resp.sendJson(200, res);
            return;
        }

        if (text != null) {
            String decoded = URLDecoder.decode(text, "UTF-8");
            boolean handled = false;
            String engine = "none";

            // Tier 1: TvKeyboardService IME (Full Unicode: Tamil, Hindi, emoji, etc.)
            if (TvKeyboardService.isImeConnected()) {
                if ("replace".equalsIgnoreCase(mode)) {
                    handled = TvKeyboardService.replaceText(decoded);
                } else {
                    handled = TvKeyboardService.commitUnicodeText(decoded);
                }
                if (handled) engine = "ime";
            }

            // Tier 2: TvAccessibilityService
            if (!handled) {
                handled = TvAccessibilityService.sendText(decoded);
                if (handled) engine = "a11y";
            }

            JSONObject res = new JSONObject();
            res.put("status", "ok");
            res.put("action", "text");
            res.put("sent", decoded);
            res.put("mode", mode);
            res.put("handled", handled);
            res.put("engine", engine);
            resp.sendJson(200, res);
        } else {
            resp.sendError(400, "BAD_REQUEST", "Missing 'text' parameter", "Provide text to type");
        }
    }
}
