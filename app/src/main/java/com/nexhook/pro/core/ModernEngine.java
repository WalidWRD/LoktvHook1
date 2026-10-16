package com.nexhook.pro.core;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModuleInterface;

/**
 * محرك المسار الحديث (libxposed 102) — مرآة UnlockEngine/HookEngine.
 * نفس القواعد: Fingerprint للدرجات، BaseBundle للبيانات، coerceReturn للفرض،
 * لا loadLibrary، تفعيل واحد (AtomicBoolean + tryArm).
 *
 * <p>حد معلن: PackageLoadedParam بلا اسم عملية — يعمل على أي عملية للحزمة
 * (منع التكرار داخلياً: تفعيل واحد + عدم تكرار بالتوقيع).
 */
public final class ModernEngine {

    private static volatile XposedInterface XI;
    private static final AtomicBoolean ACTIVATED = new AtomicBoolean(false);

    private static final AtomicInteger HOOKS = new AtomicInteger(0);
    private static final Set<String> SEEN = new HashSet<String>();

    private static volatile boolean dataDone = false;
    private static volatile boolean spiesDone = false;
    private static volatile ClassLoader bootLoader = null;
    private static volatile String pkg = null;

    public static void onPackageLoaded(
            XposedInterface xi,
            XposedModuleInterface.PackageLoadedParam lp) {
        if (xi == null || lp == null) return;
        XI = xi;

        String p;
        try {
            p = lp.getPackageName();
        } catch (Throwable t) {
            return;
        }
        if (p == null || p.isEmpty()) return;
        if (p.startsWith("com.nexhook.")) return;
        if (sdkInt() < Config.MIN_SDK) return;
        pkg = p;

        boolean first = true;
        try { first = lp.isFirstPackage(); } catch (Throwable ignored) {}
        mlog(Log.INFO, "target | pkg=" + p + " | firstPkg=" + first
                + " | engine=modern | v=" + Config.VERSION);

        try {
            bootLoader = lp.getDefaultClassLoader();
        } catch (Throwable ignored) {
            bootLoader = null;
        }
        if (bootLoader != null) {
            LoaderCollector.register(bootLoader);
        }

        installSpiesOnce();

        // تفعيل مؤجل واحد (8 ثوانٍ) — الالتقاط المبكر عبر attach/onCreate إن سبق.
        Thread delayed = new Thread(new Runnable() {
            @Override public void run() {
                try { Thread.sleep(Config.ACTIVATION_TIMEOUT_MS); }
                catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                tryActivate();
            }
        }, "nexhook-modern");
        try {
            delayed.setDaemon(true);
            delayed.start();
        } catch (Throwable ignored) {}
    }

    // ================================================================
    // جواسيس سلبية (مرة واحدة): ContextWrapper/Application/Activity
    // ================================================================
    private static void installSpiesOnce() {
        if (spiesDone) return;
        spiesDone = true;
        Safe.run(new Runnable() {
            @Override public void run() {
                hookAfter("android.content.ContextWrapper", "attachBaseContext",
                        new Class<?>[]{Context.class}, new After() {
                            @Override public void run(Chain0 c) {
                                try {
                                    if (c.thisObj() instanceof Application) {
                                        Context ctx = (Context) c.arg(0);
                                        Ctx.setRaw(ctx);
                                        mlog(Log.INFO, "ctx stored");
                                        // لا تفعيل هنا — المحفّز onCreate أو timeout.
                                    }
                                } catch (Throwable ignored) {}
                            }
                        });
                hookAfter("android.app.Application", "onCreate",
                        new Class<?>[0], new After() {
                            @Override public void run(Chain0 c) {
                                try {
                                    Object app = c.thisObj();
                                    if (app instanceof Application) {
                                        // قراءة فقط — ليست هوك ClassLoader.
                                        LoaderCollector.noteLoader(
                                                ((Application) app).getClassLoader());
                                    }
                                } catch (Throwable ignored) {}
                                mlog(Log.INFO,
                                        "Application.onCreate reached — activating");
                                tryActivate();
                            }
                        });
                hookAfter("android.app.Activity", "onCreate",
                        new Class<?>[]{Bundle.class}, new After() {
                            @Override public void run(Chain0 c) {
                                try {
                                    Object a = c.thisObj();
                                    if (a instanceof Activity) {
                                        LoaderCollector.noteLoader(
                                                ((Activity) a).getClassLoader());
                                    }
                                } catch (Throwable ignored) {}
                            }
                        });
            }
        });
    }

    private static void tryActivate() {
        if (!ACTIVATED.compareAndSet(false, true)) return;
        if (bootLoader != null && !LoaderCollector.tryArm(bootLoader)) return;
        // العمل الثقيل على Thread منفصل — لا يعطّل main.
        new Thread(new Runnable() {
            @Override public void run() {
                Safe.run(new Runnable() {
                    @Override public void run() { activate(); }
                });
            }
        }, "nexhook-activate-modern").start();
    }

    // ================================================================
    // التفعيل الواحد
    // ================================================================
    static void activate() {
        long t0 = System.currentTimeMillis();
        boolean hasCtx = false;
        try { hasCtx = (Ctx.get() != null); } catch (Throwable ignored) {}
        mlog(Log.INFO, "=== activate | ctx=" + hasCtx + " engine=modern ===");

        Context ctx = Ctx.get();
        if (ctx != null) {
            try { XLog.setLogFile(new File(ctx.getCacheDir(), Config.LOG_FILE)); }
            catch (Throwable ignored) {}
            SignatureCheck.report(ctx, pkg);
            try {
                if (new File(ctx.getCacheDir(), Config.EMERGENCY_FLAG).exists()) {
                    mlog(Log.WARN, "emergency flag — host hooks disabled");
                    return;
                }
            } catch (Throwable ignored) {}
        }

        // 1) جمع كل مسارات APK (مرآة المسار القديم).
        final List<File> apks = new ArrayList<File>();
        Safe.run(new Runnable() {
            @Override public void run() {
                apks.addAll(collectApks(Ctx.get()));
            }
        });
        for (int i = 0; i < apks.size(); i++) {
            mlog(Log.INFO, "apk source: " + apks.get(i).getAbsolutePath());
        }

        // 2) جرد موحّد.
        final Set<String> names = new HashSet<String>();
        Safe.run(new Runnable() {
            @Override public void run() {
                names.addAll(DexNames.scanApks(apks));
                mlog(Log.INFO, "inventory total=" + names.size());
            }
        });

        // 3) تشخيص: أصناف المضيف + عينات VIP.
        int hostCount = 0;
        List<String> vipSamples = new ArrayList<String>();
        for (String cls : names) {
            if (cls.startsWith(pkg)) hostCount++;
            if (vipSamples.size() < 20
                    && cls.toLowerCase(Locale.US).contains("vip")) {
                vipSamples.add(cls);
            }
        }
        mlog(Log.INFO, "inventory host(" + pkg + ")=" + hostCount);
        for (int i = 0; i < vipSamples.size(); i++) {
            mlog(Log.INFO, "  vip-sample: " + vipSamples.get(i));
        }

        long t1 = System.currentTimeMillis();
        int scanned = 0, tierA = 0, tierB = 0;
        for (String name : names) {
            if (scanned >= Config.MAX_CLASSES_SCAN) break;
            if (System.currentTimeMillis() - t1 > Config.SCAN_TIME_BUDGET_MS) {
                mlog(Log.WARN, "scan budget exceeded");
                break;
            }
            scanned++;
            if (Config.isForbidden(name)) continue;
            int tier = Fingerprint.classify(name);
            if (tier == 0) continue;
            if (hookClassByName(name, tier)) {
                if (tier == 1) tierA++; else tierB++;
            }
        }

        hookDataLayersOnce();

        mlog(Log.INFO, "SUMMARY | pkg=" + pkg
                + " | hooks=" + HOOKS.get()
                + " | inventory=" + names.size()
                + " | host=" + hostCount
                + " | tierA=" + tierA + " tierB=" + tierB
                + " | scanned=" + scanned
                + " | engine=modern | v=" + Config.VERSION);
    }

    /** نفس منطق المسار القديم: splits + مجلدات شقيقة. */
    private static List<File> collectApks(Context ctx) {
        List<File> out = new ArrayList<File>();
        if (ctx == null) return out;
        try {
            android.content.pm.ApplicationInfo ai = ctx.getApplicationInfo();

            addIfFile(out, ai.sourceDir);
            addIfFile(out, ai.publicSourceDir);

            if (ai.splitSourceDirs != null) {
                for (int i = 0; i < ai.splitSourceDirs.length; i++) {
                    addIfFile(out, ai.splitSourceDirs[i]);
                }
            }
            if (ai.splitPublicSourceDirs != null) {
                for (int i = 0; i < ai.splitPublicSourceDirs.length; i++) {
                    addIfFile(out, ai.splitPublicSourceDirs[i]);
                }
            }

            if (ai.sourceDir != null) {
                File src = new File(ai.sourceDir);
                File dir = src.getParentFile();
                if (dir != null) {
                    File up = dir.getParentFile();
                    if (up != null) {
                        File[] kids = up.listFiles();
                        if (kids != null) {
                            for (int i = 0; i < kids.length; i++) {
                                File k = kids[i];
                                if (k.isDirectory()) {
                                    File base = new File(k, "base.apk");
                                    if (base.isFile()) {
                                        addIfFile(out, base.getAbsolutePath());
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Throwable t) {
            mlog(Log.WARN, "collectApks: " + t);
        }
        return out;
    }

    private static void addIfFile(List<File> list, String path) {
        if (path == null || path.isEmpty()) return;
        try {
            File f = new File(path);
            if (f.isFile()) {
                for (int i = 0; i < list.size(); i++) {
                    if (list.get(i).getAbsolutePath().equals(f.getAbsolutePath())) return;
                }
                list.add(f);
            }
        } catch (Throwable ignored) {}
    }

    private static boolean hookClassByName(String name, int tier) {
        if (name == null) return false;
        Class<?> k = resolveClass(name);
        if (k == null) return false;
        if (k.isInterface() || Modifier.isAbstract(k.getModifiers())) return false;

        int added = 0;
        int perClass = 0;
        List<Method> methods = Reflect.allMethods(k);
        for (int i = 0; i < methods.size(); i++) {
            final Method m = methods.get(i);
            if (perClass >= Config.MAX_HOOKS_PER_CLASS) break;
            if (HOOKS.get() >= Config.MAX_HOOKS) return added > 0;
            if (!modernCanHook(m)) continue;
            if (!modernShouldHook(m, tier)) continue;
            final int fTier = tier;
            try {
                hookExec(m, new XposedInterface.Hooker() {
                    @Override public Object intercept(XposedInterface.Chain chain) {
                        try {
                            return dispatch(m, chain, fTier);
                        } catch (Throwable t) {
                            return proceedQuiet(chain);
                        }
                    }
                });
                added++;
                perClass++;
                HOOKS.incrementAndGet();
            } catch (Throwable ignored) {}
        }
        if (added > 0) {
            mlog(Log.DEBUG, "hooked class: " + name + " hooks+=" + added);
        }
        return added > 0;
    }

    private static boolean modernCanHook(Method m) {
        if (m == null) return false;
        int mod = m.getModifiers();
        if (Modifier.isAbstract(mod)) return false;
        if (Modifier.isNative(mod)) return false;
        String n = m.getName();
        if ("<init>".equals(n) || "<clinit>".equals(n)) return false;
        if ("toString".equals(n) || "hashCode".equals(n) || "equals".equals(n)
                || "getClass".equals(n) || "clone".equals(n) || "wait".equals(n)
                || "notify".equals(n) || "notifyAll".equals(n) || "finalize".equals(n)) {
            return false;
        }
        if ("loadLibrary".equals(n)) return false;
        String sig;
        try {
            sig = m.getDeclaringClass().getName() + "#" + n
                    + Arrays.toString(m.getParameterTypes());
        } catch (Throwable t) {
            return false;
        }
        synchronized (SEEN) { return SEEN.add(sig); }
    }

    private static boolean modernShouldHook(Method m, int tier) {
        if (tier == 1) return true;
        if (tier == 2) {
            try {
                return Config.isVipName(m.getName().toLowerCase(Locale.ROOT));
            } catch (Throwable t) {
                return false;
            }
        }
        return false;
    }

    /** مرآة HookEngine.SafeHook بميكانيكا Chain (تخطّي الأصل عند الفرض). */
    private static Object dispatch(Method m, XposedInterface.Chain chain, int tier)
            throws Throwable {
        Class<?> rt = m.getReturnType();
        if (rt == void.class) {
            chain.proceed();
            return null;
        }
        String n = m.getName().toLowerCase(Locale.ROOT);
        boolean vip = Config.isVipName(n);

        if (rt == boolean.class || rt == Boolean.class) {
            if (vip) return Boolean.TRUE;
            return chain.proceed();
        }
        if (rt == String.class) {
            if (vip) return Config.VALUE_STRING_VIP;
            return chain.proceed();
        }
        if (rt == int.class || rt == Integer.class
                || rt == long.class || rt == Long.class
                || rt == short.class || rt == Short.class
                || rt == byte.class || rt == Byte.class) {
            if (vip) {
                long v = Config.isExpiryName(n)
                        ? Config.VALUE_LONG_EXPIRE : Config.VALUE_LONG_DEFAULT;
                return Safe.coerceReturn(m, v);
            }
            return chain.proceed();
        }
        return chain.proceed();
    }

    // ================================================================
    // طبقات البيانات الثابتة (مرة واحدة): prefs/json/BaseBundle/text
    // ================================================================
    private static void hookDataLayersOnce() {
        if (dataDone) return;
        dataDone = true;
        Safe.run(new Runnable() {
            @Override public void run() { hookPrefs(); }
        });
        Safe.run(new Runnable() {
            @Override public void run() { hookJson(); }
        });
        Safe.run(new Runnable() {
            @Override public void run() { hookBundle(); }
        });
        Safe.run(new Runnable() {
            @Override public void run() { hookText(); }
        });
    }

    private static void hookPrefs() {
        Class<?> impl = frameworkClass("android.app.SharedPreferencesImpl");
        if (impl == null) {
            mlog(Log.WARN, "prefs: SharedPreferencesImpl not found, skipping");
            return;
        }
        hookKeyed(impl, "getBoolean", new Class<?>[]{String.class, boolean.class},
                new Forced() {
                    @Override public Object value(String k) {
                        if (Config.isVipKey(k)) return Boolean.TRUE;
                        return null;
                    }
                });
        hookKeyed(impl, "getInt", new Class<?>[]{String.class, int.class},
                new Forced() {
                    @Override public Object value(String k) {
                        if (!Config.isVipKey(k)) return null;
                        return Config.isExpiryName(k)
                                ? Config.VALUE_LONG_EXPIRE : Config.VALUE_LONG_DEFAULT;
                    }
                });
        hookKeyed(impl, "getLong", new Class<?>[]{String.class, long.class},
                new Forced() {
                    @Override public Object value(String k) {
                        if (!Config.isVipKey(k)) return null;
                        return Config.isExpiryName(k)
                                ? Config.VALUE_LONG_EXPIRE : Config.VALUE_LONG_DEFAULT;
                    }
                });
        hookKeyed(impl, "getString", new Class<?>[]{String.class, String.class},
                new Forced() {
                    @Override public Object value(String k) {
                        if (Config.isVipKey(k)) return Config.VALUE_STRING_VIP;
                        return null;
                    }
                });
        mlog(Log.INFO, "prefs-layer: SharedPreferencesImpl hooked (modern)");
    }

    private static void hookJson() {
        final Class<?> c = JSONObject.class;
        String[] boolNames = {"getBoolean", "optBoolean"};
        for (int i = 0; i < boolNames.length; i++) {
            final String mName = boolNames[i];
            Safe.run(new Runnable() {
                @Override public void run() {
                    for (Method mm : c.getDeclaredMethods()) {
                        if (!mm.getName().equals(mName)) continue;
                        if (mm.getParameterTypes().length != 1) continue;
                        if (mm.getParameterTypes()[0] != String.class) continue;
                        final Method target = mm;
                        hookExec(target, new XposedInterface.Hooker() {
                            @Override public Object intercept(XposedInterface.Chain chain) {
                                try {
                                    String k = (String) chain.getArg(0);
                                    if (Config.isVipKey(k)) return Boolean.TRUE;
                                    return chain.proceed();
                                } catch (Throwable t) {
                                    return proceedQuiet(chain);
                                }
                            }
                        });
                    }
                }
            });
        }
        String[] intNames = {"getInt", "optInt"};
        for (int i = 0; i < intNames.length; i++) {
            final String mName = intNames[i];
            Safe.run(new Runnable() {
                @Override public void run() {
                    for (Method mm : c.getDeclaredMethods()) {
                        if (!mm.getName().equals(mName)) continue;
                        if (mm.getParameterTypes().length != 1) continue;
                        if (mm.getParameterTypes()[0] != String.class) continue;
                        final Method target = mm;
                        hookExec(target, new XposedInterface.Hooker() {
                            @Override public Object intercept(XposedInterface.Chain chain) {
                                try {
                                    String k = (String) chain.getArg(0);
                                    if (!Config.isVipKey(k)) return chain.proceed();
                                    long v = Config.isExpiryName(k)
                                            ? Config.VALUE_LONG_EXPIRE
                                            : Config.VALUE_LONG_DEFAULT;
                                    return Safe.coerceReturn(target, v);
                                } catch (Throwable t) {
                                    return proceedQuiet(chain);
                                }
                            }
                        });
                    }
                }
            });
        }
        mlog(Log.INFO, "json-layer: JSONObject hooked (modern)");
    }

    private static void hookBundle() {
        Class<?> base = frameworkClass("android.os.BaseBundle");
        if (base != null) {
            hookKeyed(base, "getBoolean", new Class<?>[]{String.class},
                    new Forced() {
                        @Override public Object value(String k) {
                            if (Config.isVipKey(k)) return Boolean.TRUE;
                            return null;
                        }
                    });
            hookKeyed(base, "getInt", new Class<?>[]{String.class},
                    new Forced() {
                        @Override public Object value(String k) {
                            if (!Config.isVipKey(k)) return null;
                            return Config.isExpiryName(k)
                                    ? Config.VALUE_LONG_EXPIRE : Config.VALUE_LONG_DEFAULT;
                        }
                    });
            hookKeyed(base, "containsKey", new Class<?>[]{String.class},
                    new Forced() {
                        @Override public Object value(String k) {
                            if (Config.isVipKey(k)) return Boolean.TRUE;
                            return null;
                        }
                    });
        }
        Class<?> bundle = frameworkClass("android.os.Bundle");
        if (bundle != null) {
            hookKeyed(bundle, "getString", new Class<?>[]{String.class},
                    new Forced() {
                        @Override public Object value(String k) {
                            if (Config.isVipKey(k)) return Config.VALUE_STRING_VIP;
                            return null;
                        }
                    });
        }
        mlog(Log.INFO, "bundle-layer: BaseBundle/Bundle hooked (modern)");
    }

    private static void hookText() {
        try {
            Method setText = null;
            Method[] all = TextView.class.getDeclaredMethods();
            for (int i = 0; i < all.length; i++) {
                Method mm = all[i];
                if (!mm.getName().equals("setText")) continue;
                if (mm.getParameterTypes().length != 1) continue;
                if (mm.getParameterTypes()[0] != CharSequence.class) continue;
                setText = mm;
                break;
            }
            if (setText == null) return;
            hookExec(setText, new XposedInterface.Hooker() {
                @Override public Object intercept(XposedInterface.Chain chain) {
                    try {
                        Object arg = chain.getArg(0);
                        if (arg instanceof CharSequence) {
                            String s = arg.toString();
                            for (int i = 0; i < Config.TEXT_REPLACEMENTS.length; i++) {
                                String[] r = Config.TEXT_REPLACEMENTS[i];
                                if (r.length == 2 && r[0].equals(s)) {
                                    return chain.proceed(new Object[]{r[1]});
                                }
                            }
                        }
                        return chain.proceed();
                    } catch (Throwable t) {
                        return proceedQuiet(chain);
                    }
                }
            });
            mlog(Log.INFO, "text-layer: TextView.setText hooked (modern)");
        } catch (Throwable t) {
            mlog(Log.WARN, "text hook: " + t);
        }
    }

    // ================================================================
    // بدائيات حديثة صغيرة
    // ================================================================
    private interface Forced {
        Object value(String key);
    }

    private interface After {
        void run(Chain0 c);
    }

    private static class Chain0 {
        final XposedInterface.Chain chain;
        Chain0(XposedInterface.Chain chain) { this.chain = chain; }
        Object arg(int i) {
            try { return chain.getArg(i); } catch (Throwable t) { return null; }
        }
        Object thisObj() {
            try { return chain.getThisObject(); } catch (Throwable t) { return null; }
        }
    }

    private static void hookAfter(String cls, String name, Class<?>[] params,
                                 final After after) {
        Method m;
        try {
            m = Class.forName(cls).getDeclaredMethod(name, params);
        } catch (Throwable t) {
            return;
        }
        hookExec(m, new XposedInterface.Hooker() {
            @Override public Object intercept(XposedInterface.Chain chain) {
                Object r;
                try {
                    r = chain.proceed();
                } catch (Throwable t) {
                    return null;
                }
                try {
                    after.run(new Chain0(chain));
                } catch (Throwable ignored) {}
                return r;
            }
        });
    }

    private static void hookKeyed(Class<?> cls, String name, Class<?>[] params,
                                 final Forced forced) {
        Method m;
        try {
            m = cls.getDeclaredMethod(name, params);
        } catch (Throwable t) {
            return;
        }
        hookExec(m, new XposedInterface.Hooker() {
            @Override public Object intercept(XposedInterface.Chain chain) {
                try {
                    String k = (String) chain.getArg(0);
                    Object v = forced.value(k);
                    if (v == null) return chain.proceed();
                    return v;
                } catch (Throwable t) {
                    return proceedQuiet(chain);
                }
            }
        });
    }

    private static void hookExec(java.lang.reflect.Executable m, XposedInterface.Hooker h) {
        XposedInterface xi = XI;
        if (xi == null || m == null || h == null) return;
        try {
            xi.hook(m)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(h);
        } catch (Throwable t) {
            mlog(Log.WARN, "hookExec " + m + ": " + t);
        }
    }

    private static Object proceedQuiet(XposedInterface.Chain chain) {
        try {
            return chain.proceed();
        } catch (Throwable t) {
            return null;
        }
    }

    // ================================================================
    // أدوات
    // ================================================================
    private static Class<?> frameworkClass(String name) {
        try {
            return Class.forName(name);
        } catch (Throwable t) {
            return null;
        }
    }

    /** الأساسي أولاً، ثم اللوادر الملحوظة (قراءة فقط) عند stub. */
    private static Class<?> resolveClass(String cls) {
        Class<?> c = Reflect.forNameSafe(cls, bootLoader);
        if (c != null) return c;
        List<ClassLoader> noted = LoaderCollector.noted();
        for (int i = 0; i < noted.size(); i++) {
            ClassLoader cl = noted.get(i);
            if (cl == bootLoader) continue;
            c = Reflect.forNameSafe(cls, cl);
            if (c != null) return c;
        }
        return null;
    }

    private static File apkOf(Context ctx) {
        return resolveApk(ctx);
    }

    /** sourceDir ثم base.apk (fancy wrapper) ثم publicSourceDir. */
    private static File resolveApk(Context ctx) {
        if (ctx == null) return null;
        try {
            String src = ctx.getApplicationInfo().sourceDir;
            if (src != null) {
                File f = new File(src);
                if (f.isFile()) {
                    if (!"base.apk".equals(f.getName())) {
                        File parent = f.getParentFile();
                        if (parent != null) {
                            File candidate = new File(parent, "base.apk");
                            if (candidate.isFile()) return candidate;
                        }
                    }
                    return f;
                }
            }
            String pub = ctx.getApplicationInfo().publicSourceDir;
            if (pub != null) {
                File f = new File(pub);
                if (f.isFile()) return f;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static int sdkInt() {
        try {
            return android.os.Build.VERSION.SDK_INT;
        } catch (Throwable t) {
            return Config.MIN_SDK;
        }
    }

    static void mlog(int prio, String msg) {
        try {
            if (XI != null) {
                XI.log(prio, "NexHookPro", msg);
                return;
            }
        } catch (Throwable ignored) {}
        XLog.i(msg);
    }

    private ModernEngine() {}
}
