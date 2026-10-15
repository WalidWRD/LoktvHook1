package com.loktv.hookpro.core;

/** كل تنفيذ محمي: أي استثناء يُسجَّل فقط ولا يكسر التطبيق الهدف أبداً. */
public final class Safe {

    public interface Block { void run() throws Throwable; }
    public interface Call<T> { T run() throws Throwable; }

    public static void run(String tag, Block b) {
        try { b.run(); }
        catch (Throwable t) { XLog.w(tag + " failed: " + t); }
    }

    public static <T> T call(String tag, Call<T> c, T fallback) {
        try { return c.run(); }
        catch (Throwable t) { XLog.w(tag + " failed: " + t); return fallback; }
    }

    private Safe() {}
}
