package com.gathin.tvpowerhub.api;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Build;
import android.util.Log;

import com.gathin.tvpowerhub.TvAccessibilityService;
import com.gathin.tvpowerhub.http.HttpRequest;
import com.gathin.tvpowerhub.http.HttpResponse;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

public class MediaApi {
    private static final String TAG = "MediaApi";

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

        // Try transport controls first if active media session is available (M3)
        boolean sessionHandled = false;
        try {
            MediaSessionManager msm = (MediaSessionManager) context.getSystemService(Context.MEDIA_SESSION_SERVICE);
            if (msm != null) {
                List<MediaController> controllers = msm.getActiveSessions(null);
                if (controllers != null && !controllers.isEmpty()) {
                    MediaController mc = controllers.get(0);
                    MediaController.TransportControls tc = mc.getTransportControls();
                    if (tc != null) {
                        if ("play_pause".equalsIgnoreCase(subAction)) {
                            PlaybackState state = mc.getPlaybackState();
                            if (state != null && state.getState() == PlaybackState.STATE_PLAYING) {
                                tc.pause();
                            } else {
                                tc.play();
                            }
                            sessionHandled = true;
                        } else if ("play".equalsIgnoreCase(subAction)) {
                            tc.play();
                            sessionHandled = true;
                        } else if ("pause".equalsIgnoreCase(subAction)) {
                            tc.pause();
                            sessionHandled = true;
                        } else if ("next".equalsIgnoreCase(subAction)) {
                            tc.skipToNext();
                            sessionHandled = true;
                        } else if ("previous".equalsIgnoreCase(subAction)) {
                            tc.skipToPrevious();
                            sessionHandled = true;
                        } else if ("stop".equalsIgnoreCase(subAction)) {
                            tc.stop();
                            sessionHandled = true;
                        }
                    }
                }
            }
        } catch (SecurityException se) {
            // Permission for getActiveSessions not enabled, fall through to key injection
            Log.d(TAG, "MediaSessionManager permission not granted: " + se.getMessage());
        } catch (Exception ignored) {}

        if (!sessionHandled) {
            TvAccessibilityService.sendMedia(context, codeInt);
        }

        JSONObject res = new JSONObject();
        res.put("status", "ok");
        res.put("action", "media");
        res.put("sub_action", subAction);
        res.put("via", sessionHandled ? "media_session" : "key_event");
        res.put("keycode", codeInt);
        resp.sendJson(200, res);
    }

    public static void handleNowPlaying(Context context, HttpRequest req, HttpResponse resp) throws Exception {
        JSONObject res = new JSONObject();
        res.put("status", "ok");
        res.put("active", false);

        try {
            MediaSessionManager msm = (MediaSessionManager) context.getSystemService(Context.MEDIA_SESSION_SERVICE);
            if (msm != null) {
                List<MediaController> controllers = msm.getActiveSessions(null);
                if (controllers != null && !controllers.isEmpty()) {
                    MediaController mc = controllers.get(0);
                    MediaMetadata meta = mc.getMetadata();
                    PlaybackState state = mc.getPlaybackState();

                    res.put("active", true);
                    res.put("package", mc.getPackageName());
                    if (meta != null) {
                        res.put("title", meta.getString(MediaMetadata.METADATA_KEY_TITLE));
                        res.put("artist", meta.getString(MediaMetadata.METADATA_KEY_ARTIST));
                        res.put("album", meta.getString(MediaMetadata.METADATA_KEY_ALBUM));
                        res.put("duration", meta.getLong(MediaMetadata.METADATA_KEY_DURATION));
                    }
                    if (state != null) {
                        res.put("state", state.getState());
                        res.put("position", state.getPosition());
                        res.put("playback_speed", state.getPlaybackSpeed());
                        res.put("playing", state.getState() == PlaybackState.STATE_PLAYING);
                    }
                }
            }
        } catch (SecurityException se) {
            res.put("active", false);
            res.put("note", "Grant notification listener access to read active media titles");
        } catch (Exception ignored) {}

        resp.sendJson(200, res);
    }

    public static void handleSeek(Context context, HttpRequest req, HttpResponse resp) throws Exception {
        String posStr = req.getParam("position");
        String deltaStr = req.getParam("delta"); // e.g. "+10000" or "-10000" ms

        boolean ok = false;
        try {
            MediaSessionManager msm = (MediaSessionManager) context.getSystemService(Context.MEDIA_SESSION_SERVICE);
            if (msm != null) {
                List<MediaController> controllers = msm.getActiveSessions(null);
                if (controllers != null && !controllers.isEmpty()) {
                    MediaController mc = controllers.get(0);
                    MediaController.TransportControls tc = mc.getTransportControls();
                    PlaybackState state = mc.getPlaybackState();
                    if (tc != null) {
                        long targetPos = 0;
                        if (posStr != null) {
                            targetPos = Long.parseLong(posStr);
                        } else if (deltaStr != null && state != null) {
                            long delta = Long.parseLong(deltaStr);
                            targetPos = Math.max(0, state.getPosition() + delta);
                        }
                        tc.seekTo(targetPos);
                        ok = true;
                    }
                }
            }
        } catch (Exception e) {
            Log.d(TAG, "Seek error: " + e.getMessage());
        }

        if (!ok) {
            // Fallback: send FF or Rewind keys
            if (deltaStr != null && deltaStr.startsWith("-")) {
                TvAccessibilityService.sendMedia(context, 89); // KEYCODE_MEDIA_REWIND
            } else {
                TvAccessibilityService.sendMedia(context, 90); // KEYCODE_MEDIA_FAST_FORWARD
            }
        }

        JSONObject res = new JSONObject();
        res.put("status", "ok");
        res.put("action", "seek");
        res.put("handled", ok);
        resp.sendJson(200, res);
    }

    public static void handleGetVolume(Context context, HttpRequest req, HttpResponse resp) throws Exception {
        AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        JSONObject res = new JSONObject();
        res.put("status", "ok");

        if (am != null) {
            int current = am.getStreamVolume(AudioManager.STREAM_MUSIC);
            int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            int min = 0;
            if (Build.VERSION.SDK_INT >= 28) {
                min = am.getStreamMinVolume(AudioManager.STREAM_MUSIC);
            }
            boolean isMuted = false;
            if (Build.VERSION.SDK_INT >= 23) {
                isMuted = am.isStreamMute(AudioManager.STREAM_MUSIC);
            } else {
                isMuted = (current == 0);
            }
            boolean isFixed = false;
            if (Build.VERSION.SDK_INT >= 21) {
                isFixed = am.isVolumeFixed();
            }

            res.put("volume", current);
            res.put("max_volume", max);
            res.put("min_volume", min);
            res.put("is_muted", isMuted);
            res.put("is_fixed", isFixed);
        }

        resp.sendJson(200, res);
    }

    public static void handleSetVolume(Context context, HttpRequest req, HttpResponse resp) throws Exception {
        String levelStr = req.getParam("level");
        String deltaStr = req.getParam("delta");

        AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (am != null) {
            if (levelStr != null) {
                int level = Integer.parseInt(levelStr);
                am.setStreamVolume(AudioManager.STREAM_MUSIC, level, AudioManager.FLAG_SHOW_UI);
            } else if (deltaStr != null) {
                int delta = Integer.parseInt(deltaStr);
                if (delta > 0) {
                    am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI);
                } else if (delta < 0) {
                    am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI);
                }
            }
        }

        handleGetVolume(context, req, resp);
    }
}
