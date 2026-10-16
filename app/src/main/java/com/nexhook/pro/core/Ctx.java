package com.nexhook.pro.core;

import android.content.Context;

import java.lang.ref.WeakReference;

public final class Ctx {

    private static volatile WeakReference<Context> ref = new WeakReference<Context>(null);
    private static volatile String pkg = null;
    private static volatile ClassLoader cl = null;

    private Ctx() {}

    public static void set(Context c) {
        if (c == null) return;
        Context app = null;
        try { app = c.getApplicationContext(); } catch (Throwable ignored) {}
        ref = new WeakReference<Context>(app != null ? app : c);
    }

    /** تخزين خام بلا getApplicationContext() — آمن داخل attachBaseContext (لا reentrancy). */
    public static void setRaw(Context c) {
        if (c != null) ref = new WeakReference<Context>(c);
    }

    public static Context get() { return ref.get(); }

    public static void setPackageName(String p) { pkg = p; }
    public static String getPackageName() { return pkg; }

    public static void setClassLoader(ClassLoader c) { if (c != null) cl = c; }
    public static ClassLoader getClassLoader() { return cl; }
}
