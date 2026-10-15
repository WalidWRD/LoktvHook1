package com.loktv.hookpro.core;

import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import de.robv.android.xposed.XposedBridge;

public final class XLog {

    private static final String TAG = "LokTvHookPro";
    private static final SimpleDateFormat FMT =
            new SimpleDateFormat("HH:mm:ss", Locale.US);

    private static volatile String sModulePath = null;
    private static volatile File   sFile       = null;

    public static void setModulePath(String p) { sModulePath = p; }

    public static void bindFile(File f) { sFile = f; }

    public static void i(String msg) { out("I", msg); }
    public static void d(String msg) { out("D", msg); }
    public static void w(String msg) { out("W", msg); }
    public static void e(String msg) { out("E", msg); }

    private static void out(String lvl, String msg) {
        String line = FMT.format(new Date()) + " [" + lvl + "] " + msg;
        try { XposedBridge.log(TAG + ": " + msg); } catch (Throwable ignored) {}
        try { Log.i(TAG, msg); } catch (Throwable ignored) {}
        File f = sFile;
        if (f != null) {
            try {
                File parent = f.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }
                FileWriter w = new FileWriter(f, true);
                try {
                    w.write(line);
                    w.write('\n');
                } finally {
                    try { w.close(); } catch (Throwable ignored) {}
                }
            } catch (Throwable ignored) {}
        }
    }

    private XLog() {}
}
