package com.gathin.tvpowerhub.api;

import android.content.Context;

import com.gathin.tvpowerhub.TvAccessibilityService;
import com.gathin.tvpowerhub.http.HttpRequest;
import com.gathin.tvpowerhub.http.HttpResponse;

import org.json.JSONObject;

public class MediaApi {

    public static void handleMedia(Context context, HttpRequest req, HttpResponse resp) throws Exception {
        String subAction = req.getParam("sub_action");
        if (subAction == null) subAction = req.getParam("action");

        int codeInt = 85; // KEYCODE_MEDIA_PLAY_PAUSE
        if ("play_pause".equalsIgnoreCase(subAction)) codeInt = 85;
        else if ("play".equalsIgnoreCase(subAction)) codeInt = 126;
        else if ("pause".equalsIgnoreCase(subAction)) codeInt = 127;
        else if ("next".equalsIgnoreCase(subAction)) codeInt = 87;
        else if ("previous".equalsIgnoreCase(subAction)) codeInt = 88;
        else if ("rewind".equalsIgnoreCase(subAction)) codeInt = 89;
        else if ("fast_forward".equalsIgnoreCase(subAction)) codeInt = 90;
        else if ("stop".equalsIgnoreCase(subAction)) codeInt = 86;

        TvAccessibilityService.sendMedia(context, codeInt);

        JSONObject res = new JSONObject();
        res.put("status", "ok");
        res.put("action", "media");
        res.put("sub_action", subAction);
        res.put("keycode", codeInt);
        resp.sendJson(200, res);
    }
}
