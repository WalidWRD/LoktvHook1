package com.loktv.hookpro.core;

import android.app.Application;
import android.content.Context;

/** سياق التطبيق الهدف — يُملأ تدريجياً مع فك ضغط التطبيقات المحمية. */
public final class Ctx {

    private static volatile Context   sApp   = null;
    private static volatile String    sPkg   = null;
    private static volatile ClassLoader sCl  = null;

    public static void set(Context c) {
        if (c == null) return;
        if (sApp == null) {
            Context app = null;
            try { app = c.getApplicationContext(); } catch (Throwable ignored) {}
            sApp = (app != null) ? app : c;
        }
    }

    public static void setApplication(Application a) {
        if (a != null) sApp = a;
    }

    public static Context get() { return sApp; }

    public static void setPackageName(String p) { sPkg = p; }
    public static String getPackageName()       { return sPkg; }

    public static void setClassLoader(ClassLoader cl) { if (cl != null) sCl = cl; }
    public static ClassLoader getClassLoader()        { return sCl; }

    private Ctx() {}
}
