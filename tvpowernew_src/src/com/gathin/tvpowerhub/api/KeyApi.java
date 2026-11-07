package com.gathin.tvpowerhub.api;

import android.content.Context;
import android.media.AudioManager;

import com.gathin.tvpowerhub.TvAccessibilityService;
import com.gathin.tvpowerhub.http.HttpRequest;
import com.gathin.tvpowerhub.http.HttpResponse;

import org.json.JSONObject;

public class KeyApi {

    public static void handleKey(Context context, HttpRequest req, HttpResponse resp) throws Exception {
        String code = req.getParam("code");
        if (code == null || code.isEmpty()) {
            resp.sendError(400, "BAD_REQUEST", "Missing 'code' parameter", "Provide an Android KeyEvent keycode number");
            return;
        }

        int codeInt = -1;
        try {
            codeInt = Integer.parseInt(code);
        } catch (Exception ignored) {}

        if (codeInt <= 0) {
            resp.sendError(400, "INVALID_CODE", "Invalid key code: " + code, "Keycode must be a positive integer");
            return;
        }

        if (codeInt == 24) { // VOLUME_UP
            AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (am != null) am.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI);
        } else if (codeInt == 25) { // VOLUME_DOWN
            AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (am != null) am.adjustVolume(AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI);
        } else if (codeInt == 164) { // VOLUME_MUTE
            AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (am != null) am.adjustVolume(AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI);
        } else if (codeInt == 3) { // HOME
            TvAccessibilityService.doHome(context);
        } else if (codeInt == 4) { // BACK
            TvAccessibilityService.doBack();
        } else if (codeInt == 26) { // POWER
            TvAccessibilityService.doPower(context);
        } else if (codeInt == 176 || codeInt == 82) { // SETTINGS / MENU
            TvAccessibilityService.doSettings(context);
        } else if (codeInt == 178) { // INPUT SOURCE
            TvAccessibilityService.doInputSource(context);
        } else if (codeInt == 223) { // SCREEN OFF
            TvAccessibilityService.doScreenOff(context);
        } else if (codeInt == 187) { // RECENTS
            TvAccessibilityService.doRecents();
        } else if (codeInt == 67) { // BACKSPACE
            TvAccessibilityService.doBackspace();
        } else if (codeInt == 85 || codeInt == 87 || codeInt == 88 || codeInt == 89 || codeInt == 90 || codeInt == 126 || codeInt == 127) { // MEDIA
            TvAccessibilityService.sendMedia(context, codeInt);
        } else {
            TvAccessibilityService.sendDpad(codeInt);
        }

        JSONObject res = new JSONObject();
        res.put("status", "ok");
        res.put("action", "key");
        res.put("code", code);
        resp.sendJson(200, res);
    }

    public static void handleInputSource(Context context, HttpRequest req, HttpResponse resp) throws Exception {
        String source = req.getParam("source");
        boolean handled = TvAccessibilityService.switchInput(context, source);
        JSONObject res = new JSONObject();
        res.put("status", "ok");
        res.put("action", "input_source");
        res.put("source", source);
        res.put("handled", handled);
        resp.sendJson(200, res);
    }
}
