package com.nexhook.pro.core;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * NexHookPro v7.0.2 — Core Engine.
 *
 *  قواعد لا تُخترق:
 *  1. لا هوك على أي ClassLoader.
 *  2. attachBaseContext يخزّن Context فقط — لا activate.
 *  3. التنشيط بعد Application.onCreate.
 *  4. Activity.onCreate كـ fallback.
 *  5. timeout 8s كحل أخير.
 *  6. التنشيط على Thread منفصل.
 *  7. جرد متعدد المصادر (sourceDir + publicSourceDir + splits + مجلدات شقيقة).
 *
 *  إضافة: قراءة اللوادر الملحوظة كاحتياط forName عند stub (ليست هوك ClassLoader).
 */
public final class UnlockEngine {

    private static final AtomicBoolean ACTIVATED = new AtomicBoolean(false);
    private static long startMs = System.currentTimeMillis();

    private final String pkg;
    private final ClassLoader loader;
    private final HookEngine engine = new HookEngine();

    public UnlockEngine(String pkg, ClassLoader loader) {
        this.pkg = pkg;
        this.loader = loader;
    }

    public static void markStart() { startMs = System.currentTimeMillis(); }

    // =====================================================================
    //  المرحلة السلبية
    // =====================================================================
    public void passive() {
        LoaderCollector.register(loader);
        hookAttachBaseContext();
        hookApplicationOnCreate();
        hookActivityFallback();
        XLog.i("passive | pkg=" + pkg);
    }

    private void hookAttachBaseContext() {
        try {
            XposedHelpers.findAndHookMethod(
                    ContextWrapper.class, "attachBaseContext", Context.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam p) {
                            try {
                                if (!(p.thisObject instanceof Application)) return;
                                Context c = (Context) p.args[0];
                                Ctx.setRaw(c);
                                XLog.i("ctx stored");
                            } catch (Throwable ignored) {}
                        }
                    });
        } catch (Throwable t) {
            XLog.w("attachBaseContext hook: " + t);
        }
    }

    private void hookApplicationOnCreate() {
        try {
            XposedHelpers.findAndHookMethod(
                    Application.class, "onCreate",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam p) {
                            try {
                                Object app = p.thisObject;
                                if (app instanceof Application) {
                                    LoaderCollector.noteLoader(
                                            ((Application) app).getClassLoader());
                                }
                            } catch (Throwable ignored) {}
                            XLog.i("Application.onCreate reached — activating");
                            tryActivate();
                        }
                    });
        } catch (Throwable t) {
            XLog.w("Application.onCreate hook: " + t);
        }
    }

    private void hookActivityFallback() {
        try {
            XposedHelpers.findAndHookMethod(
                    Activity.class, "onCreate", Bundle.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam p) {
                            try {
                                Object a = p.thisObject;
                                if (a instanceof Activity) {
                                    LoaderCollector.noteLoader(
                                            ((Activity) a).getClassLoader());
                                }
                            } catch (Throwable ignored) {}
                            if (!ACTIVATED.get()) {
                                XLog.i("Activity.onCreate fallback → activating");
                                tryActivate();
                            }
                        }
                    });
        } catch (Throwable t) {
            XLog.w("Activity.onCreate hook: " + t);
        }
    }

    public void tick(long elapsed) {
        if (elapsed >= Config.ACTIVATION_TIMEOUT_MS) {
            if (!ACTIVATED.get()) {
                XLog.i("timeout activation " + elapsed + "ms");
                tryActivate();
            }
        }
    }

    // =====================================================================
    //  التنشيط الآمن
    // =====================================================================
    private void tryActivate() {
        if (!ACTIVATED.compareAndSet(false, true)) return;
        if (!LoaderCollector.tryArm(loader)) return;

        new Thread(new Runnable() {
            @Override public void run() {
                Safe.run(new Runnable() {
                    @Override public void run() { activate(); }
                });
            }
        }, "nexhook-activate").start();
    }

    private void activate() {
        long t0 = System.currentTimeMillis();
        Context ctx = Ctx.get();
        XLog.i("=== activate | ctx=" + (ctx != null) + " ===");

        if (ctx != null) {
            try { XLog.setLogFile(new File(ctx.getCacheDir(), Config.LOG_FILE)); }
            catch (Throwable ignored) {}
            SignatureCheck.report(ctx, pkg);

            try {
                if (new File(ctx.getCacheDir(), Config.EMERGENCY_FLAG).exists()) {
                    XLog.w("emergency flag — disabled");
                    return;
                }
            } catch (Throwable ignored) {}
        }

        // 1) جمع كل مسارات APK
        List<File> apks = Safe.call(new Safe.Producer<List<File>>() {
            @Override public List<File> produce() throws Throwable {
                return collectApks(Ctx.get());
            }
        }, new ArrayList<File>());
        for (int i = 0; i < apks.size(); i++) {
            XLog.i("apk source: " + apks.get(i).getAbsolutePath());
        }

        // 2) جرد موحّد
        Set<String> inventory = Safe.call(new Safe.Producer<Set<String>>() {
            @Override public Set<String> produce() throws Throwable {
                return DexNames.scanApks(apks);
            }
        }, null);
        if (inventory == null) inventory = new HashSet<String>();
        int names = inventory.size();
        XLog.i("inventory total=" + names);

        // 3) تشخيص: أصناف المضيف + عينات VIP
        int hostCount = 0;
        List<String> vipSamples = new ArrayList<String>();
        for (String cls : inventory) {
            if (cls.startsWith(pkg)) hostCount++;
            if (vipSamples.size() < 20
                    && cls.toLowerCase(Locale.ROOT).contains("vip")) {
                vipSamples.add(cls);
            }
        }
        XLog.i("inventory host(" + pkg + ")=" + hostCount);
        for (int i = 0; i < vipSamples.size(); i++) {
            XLog.i("  vip-sample: " + vipSamples.get(i));
        }

        // 4) الهوك (الأساسي ثم الاحتياطيون)
        int scanned = 0, tierA = 0, tierB = 0;
        for (String cls : inventory) {
            if (scanned >= Config.MAX_CLASSES_SCAN) break;
            if (System.currentTimeMillis() - t0 > Config.SCAN_TIME_BUDGET_MS) {
                XLog.w("scan budget exceeded");
                break;
            }
            scanned++;
            if (Config.isForbidden(cls)) continue;

            int tier = Fingerprint.classify(cls);
            if (tier == 0) continue;

            Class<?> c = resolveClass(cls);
            if (c == null) continue;
            engine.hookClass(c, tier);
            if (tier == 1) tierA++; else tierB++;
        }

        // 5) الطبقات الثابتة
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

        XLog.i("SUMMARY | pkg=" + pkg
                + " | hooks=" + engine.hookCount()
                + " | classes=" + engine.classCount()
                + " | inventory=" + names
                + " | host=" + hostCount
                + " | tierA=" + tierA + " tierB=" + tierB
                + " | scanned=" + scanned
                + " | elapsed=" + (System.currentTimeMillis() - t0) + "ms"
                + " | v=" + Config.VERSION);
    }

    /** الأساسي أولاً، ثم اللوادر الملحوظة (قراءة فقط) عند stub. */
    private Class<?> resolveClass(String cls) {
        Class<?> c = Reflect.forNameSafe(cls, loader);
        if (c != null) return c;
        List<ClassLoader> noted = LoaderCollector.noted();
        for (int i = 0; i < noted.size(); i++) {
            ClassLoader cl = noted.get(i);
            if (cl == loader) continue;
            c = Reflect.forNameSafe(cls, cl);
            if (c != null) return c;
        }
        return null;
    }

    /**
     * جمع كل مسارات APK:
     *   - sourceDir
     *   - publicSourceDir
     *   - splitSourceDirs[]
     *   - splitPublicSourceDirs[]
     *   - مجلدات شقيقة حول sourceDir (للكشف عن base.apk داخل fancy wrapper)
     */
    private List<File> collectApks(Context ctx) {
        List<File> out = new ArrayList<File>();
        if (ctx == null) return out;
        try {
            ApplicationInfo ai = ctx.getApplicationInfo();

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

            // مجلدات شقيقة
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
            XLog.w("collectApks: " + t);
        }
        return out;
    }

    private void addIfFile(List<File> list, String path) {
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

    // =====================================================================
    //  طبقة SharedPreferences
    // =====================================================================
    private void hookPrefs() {
        try {
            Class<?> c = Class.forName("android.app.SharedPreferencesImpl");

            hookAfter(c, "getBoolean", new Class<?>[]{String.class, boolean.class},
                    new AfterCb() {
                        @Override public void run(Method m, XC_MethodHook.MethodHookParam p) {
                            String k = (String) p.args[0];
                            if (Config.isVipKey(k) && !Boolean.TRUE.equals(p.getResult()))
                                p.setResult(Safe.coerceReturn(m, Boolean.TRUE));
                        }
                    });

            hookAfter(c, "getInt", new Class<?>[]{String.class, int.class},
                    new AfterCb() {
                        @Override public void run(Method m, XC_MethodHook.MethodHookParam p) {
                            String k = (String) p.args[0];
                            if (Config.isVipKey(k)) {
                                long v = Config.isExpiryName(k)
                                        ? Config.VALUE_LONG_EXPIRE : Config.VALUE_LONG_DEFAULT;
                                p.setResult(Safe.coerceReturn(m, v));
                            }
                        }
                    });

            hookAfter(c, "getLong", new Class<?>[]{String.class, long.class},
                    new AfterCb() {
                        @Override public void run(Method m, XC_MethodHook.MethodHookParam p) {
                            String k = (String) p.args[0];
                            if (Config.isVipKey(k)) {
                                long v = Config.isExpiryName(k)
                                        ? Config.VALUE_LONG_EXPIRE : Config.VALUE_LONG_DEFAULT;
                                p.setResult(Safe.coerceReturn(m, v));
                            }
                        }
                    });

            hookAfter(c, "getString", new Class<?>[]{String.class, String.class},
                    new AfterCb() {
                        @Override public void run(Method m, XC_MethodHook.MethodHookParam p) {
                            String k = (String) p.args[0];
                            if (Config.isVipKey(k))
                                p.setResult(Safe.coerceReturn(m, Config.VALUE_STRING_VIP));
                        }
                    });

            XLog.i("prefs-layer: SharedPreferencesImpl hooked");
        } catch (Throwable t) {
            XLog.w("prefs hook: " + t);
        }
    }

    // =====================================================================
    //  طبقة org.json
    // =====================================================================
    private void hookJson() {
        try {
            Class<?> c = Class.forName("org.json.JSONObject");
            String[] boolNames = {"getBoolean", "optBoolean"};
            for (int i = 0; i < boolNames.length; i++) {
                final String n = boolNames[i];
                hookAfter(c, n, new Class<?>[]{String.class},
                        new AfterCb() {
                            @Override public void run(Method m, XC_MethodHook.MethodHookParam p) {
                                String k = (String) p.args[0];
                                if (Config.isVipKey(k))
                                    p.setResult(Safe.coerceReturn(m, Boolean.TRUE));
                            }
                        });
            }
            String[] intNames = {"getInt", "optInt"};
            for (int i = 0; i < intNames.length; i++) {
                final String n = intNames[i];
                hookAfter(c, n, new Class<?>[]{String.class},
                        new AfterCb() {
                            @Override public void run(Method m, XC_MethodHook.MethodHookParam p) {
                                String k = (String) p.args[0];
                                if (Config.isVipKey(k))
                                    p.setResult(Safe.coerceReturn(m, Config.VALUE_LONG_DEFAULT));
                            }
                        });
            }
            XLog.i("json-layer: JSONObject hooked");
        } catch (Throwable t) {
            XLog.w("json hook: " + t);
        }
    }

    // =====================================================================
    //  طبقة BaseBundle / Bundle
    // =====================================================================
    private void hookBundle() {
        try {
            Class<?> base = Class.forName("android.os.BaseBundle");

            hookAfter(base, "getBoolean", new Class<?>[]{String.class},
                    new AfterCb() {
                        @Override public void run(Method m, XC_MethodHook.MethodHookParam p) {
                            String k = (String) p.args[0];
                            if (Config.isVipKey(k) && !Boolean.TRUE.equals(p.getResult()))
                                p.setResult(Safe.coerceReturn(m, Boolean.TRUE));
                        }
                    });

            hookAfter(base, "getInt", new Class<?>[]{String.class},
                    new AfterCb() {
                        @Override public void run(Method m, XC_MethodHook.MethodHookParam p) {
                            String k = (String) p.args[0];
                            if (Config.isVipKey(k)) {
                                long v = Config.isExpiryName(k)
                                        ? Config.VALUE_LONG_EXPIRE : Config.VALUE_LONG_DEFAULT;
                                p.setResult(Safe.coerceReturn(m, v));
                            }
                        }
                    });

            hookAfter(base, "containsKey", new Class<?>[]{String.class},
                    new AfterCb() {
                        @Override public void run(Method m, XC_MethodHook.MethodHookParam p) {
                            String k = (String) p.args[0];
                            if (Config.isVipKey(k) && !Boolean.TRUE.equals(p.getResult()))
                                p.setResult(Safe.coerceReturn(m, Boolean.TRUE));
                        }
                    });

            Class<?> bundle = Class.forName("android.os.Bundle");
            hookAfter(bundle, "getString", new Class<?>[]{String.class},
                    new AfterCb() {
                        @Override public void run(Method m, XC_MethodHook.MethodHookParam p) {
                            String k = (String) p.args[0];
                            if (Config.isVipKey(k))
                                p.setResult(Safe.coerceReturn(m, Config.VALUE_STRING_VIP));
                        }
                    });

            XLog.i("bundle-layer: BaseBundle/Bundle hooked");
        } catch (Throwable t) {
            XLog.w("bundle hook: " + t);
        }
    }

    // =====================================================================
    //  طبقة الواجهة
    // =====================================================================
    private void hookText() {
        try {
            Class<?> tv = Class.forName("android.widget.TextView");
            Method m = Reflect.findMethod(tv, "setText", CharSequence.class);
            if (m == null) return;
            XposedBridge.hookMethod(m, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    try {
                        Object a = p.args[0];
                        if (!(a instanceof CharSequence)) return;
                        String s = a.toString();
                        for (int i = 0; i < Config.TEXT_REPLACEMENTS.length; i++) {
                            String[] r = Config.TEXT_REPLACEMENTS[i];
                            if (r.length == 2 && r[0].equals(s)) {
                                p.args[0] = r[1];
                                return;
                            }
                        }
                    } catch (Throwable ignored) {}
                }
            });
            XLog.i("text-layer: TextView.setText hooked");
        } catch (Throwable t) {
            XLog.w("text hook: " + t);
        }
    }

    // =====================================================================
    //  أداة after-hook موحدة
    // =====================================================================
    private interface AfterCb {
        void run(Method m, XC_MethodHook.MethodHookParam p);
    }

    private void hookAfter(Class<?> c, String name, Class<?>[] params, AfterCb cb) {
        Method m = Reflect.findMethod(c, name, params);
        if (m == null) return;
        try {
            XposedBridge.hookMethod(m, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    try { cb.run((Method) p.method, p); }
                    catch (Throwable ignored) {}
                }
            });
        } catch (Throwable ignored) {}
    }
}
