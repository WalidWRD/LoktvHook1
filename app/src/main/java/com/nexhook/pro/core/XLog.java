package com.nexhook.pro.core;

import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import de.robv.android.xposed.XposedBridge;

public final class XLog {

    public static final String TAG = "NexHookPro";
    private static final Object LOCK = new Object();
    private static File logFile;
    private static final SimpleDateFormat SDF =
            new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);

    private XLog() {}

    public static void setLogFile(File f) {
        synchronized (LOCK) {
            logFile = f;
            if (logFile == null) return;
            File p = logFile.getParentFile();
            if (p != null && !p.exists()) {
                try { p.mkdirs(); } catch (Throwable ignored) {}
            }
            if (!logFile.exists()) {
                try { logFile.createNewFile(); } catch (IOException ignored) {}
            }
        }
    }

    public static void d(String m) { w("D", m); }
    public static void i(String m) { w("I", m); }
    public static void w(String m) { w("W", m); }
    public static void e(String m) { w("E", m); }
    public static void e(String m, Throwable t) {
        w("E", m + " | " + (t == null ? "null"
                : t.getClass().getSimpleName() + ": " + t.getMessage()));
    }

    private static void w(String lvl, String msg) {
        String line = SDF.format(new Date()) + " [" + lvl + "] " + msg;
        // سجل الفريمورك أولاً (يظهر في مدير LSPosed) — محمي تماماً.
        try { XposedBridge.log(TAG + ": " + msg); } catch (Throwable ignored) {}
        try {
            int p = Log.INFO;
            if ("D".equals(lvl)) p = Log.DEBUG;
            else if ("W".equals(lvl)) p = Log.WARN;
            else if ("E".equals(lvl)) p = Log.ERROR;
            Log.println(p, TAG, msg);
        } catch (Throwable ignored) {}

        synchronized (LOCK) {
            if (logFile == null) return;
            FileWriter fw = null;
            try {
                fw = new FileWriter(logFile, true);
                fw.write(line);
                fw.write('\n');
            } catch (Throwable ignored) {
            } finally {
                if (fw != null) {
                    try { fw.close(); } catch (Throwable ignored) {}
                }
            }
        }
    }
}
