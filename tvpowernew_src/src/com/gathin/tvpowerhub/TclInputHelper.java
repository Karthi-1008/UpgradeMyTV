package com.gathin.tvpowerhub;

import android.content.Context;
import android.util.Log;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

public class TclInputHelper {
    private static final String TAG = "TclInputHelper";
    private static Object sInputDevice;
    private static Method sSendKeyEventMethod;
    private static Method sSendTextMethod;
    private static boolean sInitialized = false;

    public static synchronized void init(Context context) {
        if (sInitialized) return;
        sInitialized = true;
        try {
            Class<?> cls = Class.forName("com.tcl.os.system.InputDevice");
            Constructor<?> ctor = cls.getConstructor(Context.class);
            sInputDevice = ctor.newInstance(context.getApplicationContext());
            sSendKeyEventMethod = cls.getMethod("sendKeyEvent", int.class);
            sSendTextMethod = cls.getMethod("sendText", String.class);
            Log.i(TAG, "TCL Native InputDevice initialized successfully!");
        } catch (Throwable t) {
            Log.w(TAG, "TCL Native InputDevice not available: " + t.getMessage());
        }
    }

    public static boolean sendKeyEvent(int keycode) {
        if (sSendKeyEventMethod != null) {
            try {
                sSendKeyEventMethod.invoke(null, keycode);
                Log.i(TAG, "Successfully injected keycode " + keycode + " via TCL Native InputDevice");
                return true;
            } catch (Throwable t) {
                Log.e(TAG, "Error invoking sendKeyEvent " + keycode + ": " + t.getMessage(), t);
            }
        }
        return false;
    }

    public static boolean sendText(String text) {
        if (sSendTextMethod != null) {
            try {
                sSendTextMethod.invoke(null, text);
                Log.i(TAG, "Successfully injected text via TCL Native InputDevice");
                return true;
            } catch (Throwable t) {
                Log.e(TAG, "Error invoking sendText: " + t.getMessage(), t);
            }
        }
        return false;
    }
}
