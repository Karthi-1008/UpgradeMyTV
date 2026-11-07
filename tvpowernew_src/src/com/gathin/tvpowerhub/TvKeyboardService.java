package com.gathin.tvpowerhub;

import android.inputmethodservice.InputMethodService;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

public class TvKeyboardService extends InputMethodService {
    private static final String TAG = "TvKeyboardService";
    private static volatile TvKeyboardService sInstance = null;

    @Override
    public void onCreate() {
        super.onCreate();
        sInstance = this;
        Log.i(TAG, "TvKeyboardService created");
    }

    @Override
    public void onDestroy() {
        if (sInstance == this) {
            sInstance = null;
        }
        super.onDestroy();
        Log.i(TAG, "TvKeyboardService destroyed");
    }

    @Override
    public View onCreateInputView() {
        // Invisible input view - remote keyboard is driven from phone/web
        View v = new View(this);
        v.setVisibility(View.GONE);
        return v;
    }

    @Override
    public void onStartInput(EditorInfo attribute, boolean restarting) {
        super.onStartInput(attribute, restarting);
        sInstance = this;
    }

    @Override
    public void onFinishInput() {
        super.onFinishInput();
    }

    public static boolean isImeAvailable() {
        return sInstance != null;
    }

    public static boolean isImeConnected() {
        TvKeyboardService ime = sInstance;
        if (ime == null) return false;
        return ime.getCurrentInputConnection() != null;
    }

    /**
     * Commits a Unicode string (e.g. Tamil, Hindi, Emoji) directly into the focused editor field.
     */
    public static boolean commitUnicodeText(String text) {
        TvKeyboardService ime = sInstance;
        if (ime == null || text == null) return false;
        InputConnection ic = ime.getCurrentInputConnection();
        if (ic != null) {
            boolean ok = ic.commitText(text, 1);
            Log.d(TAG, "commitText: " + text + " -> " + ok);
            return ok;
        }
        return false;
    }

    /**
     * Replaces the entire input field with new text (used for live-diff typing).
     */
    public static boolean replaceText(String newText) {
        TvKeyboardService ime = sInstance;
        if (ime == null) return false;
        InputConnection ic = ime.getCurrentInputConnection();
        if (ic != null) {
            // Delete up to 256 characters before and after cursor, then commit newText
            ic.deleteSurroundingText(256, 256);
            if (newText != null && !newText.isEmpty()) {
                ic.commitText(newText, 1);
            }
            return true;
        }
        return false;
    }

    /**
     * Deletes one character backwards (Backspace).
     */
    public static boolean deleteBackspace() {
        TvKeyboardService ime = sInstance;
        if (ime == null) return false;
        InputConnection ic = ime.getCurrentInputConnection();
        if (ic != null) {
            CharSequence selected = ic.getSelectedText(0);
            if (selected != null && selected.length() > 0) {
                return ic.commitText("", 1);
            }
            return ic.deleteSurroundingText(1, 0);
        }
        return false;
    }

    /**
     * Sends an IME action (Search, Go, Done, Next).
     */
    public static boolean performAction(int actionCode) {
        TvKeyboardService ime = sInstance;
        if (ime == null) return false;
        InputConnection ic = ime.getCurrentInputConnection();
        if (ic != null) {
            return ic.performEditorAction(actionCode);
        }
        return false;
    }

    /**
     * Injects a KeyEvent into the focused input connection.
     */
    public static boolean sendKeyEvent(int keyCode) {
        TvKeyboardService ime = sInstance;
        if (ime == null) return false;
        ime.sendDownUpKeyEvents(keyCode);
        return true;
    }
}
