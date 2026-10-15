package com.loktv.hookpro.core;

import android.content.Context;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * محرك الربط: بصمة اسم الصنف + بصمة التوقيع (نوع الإرجاع) — لا أسماء ثابتة.
 * كل الطبقات محمية بـ Safe ولا تُطبَّق طبقات البيانات إلا مرة واحدة.
 */
public final class UnlockEngine {

    private static final AtomicInteger HOOKS = new AtomicInteger();
    private static final Set<String>     HOOKED = new HashSet<String>();
    private static final AtomicInteger TARGETED = new AtomicInteger();

    private static volatile boolean prefsHooked = false;
    private static volatile boolean jsonHooked = false;
    private static volatile boolean textHooked = false;

    public static void activate(Context ctx) {
        XLog.i("=== activate | ctx=" + (ctx != null) + " ===");

        // 1) اربط الطبقة الحرجة أولاً (SharedPreferences / JSON / TextView) — مرة واحدة فقط
        Safe.run("prefs", new Safe.Block() {
            @Override public void run() throws Throwable { hookSharedPreferences(); }
        });
        Safe.run("json", new Safe.Block() {
            @Override public void run() throws Throwable { hookJson(); }
        });
        Safe.run("text", new Safe.Block() {
            @Override public void run() throws Throwable { hookTextView(); }
        });

        // 2) اجرد أصناف المضيف من جدول DEX
        final Set<String> names = new HashSet<String>();
        Safe.run("dexNames", new Safe.Block() {
            @Override public void run() throws Throwable {
                File apk = apkOf(Ctx.get() != null ? Ctx.get() : ctx);
                if (apk != null) {
                    names.addAll(DexNames.collect(apk));
                    XLog.i("inventory: " + apk + " names=" + names.size());
                }
            }
        });

        // 3) فلترة الأصناف المرشّحة بالبصمة
        Set<String> candidates = new HashSet<String>();
        for (String n : names) {
            if (n == null || n.isEmpty()) continue;
            if (Config.isInfrastructure(n)) continue;
            String lower = n.toLowerCase(Locale.US);
            if (Config.isVipName(lower) || Config.isDetectName(lower)) candidates.add(n);
            if (candidates.size() > Config.MAX_CLASSES_SCAN) break;
        }
        XLog.i("scan: names=" + names.size() + " candidates=" + candidates.size());

        // 4) ربط كل مرشّح بدرجته (بحد زمني حتى لا يعلّق بدء التطبيق)
        long t0 = System.currentTimeMillis();
        for (String name : candidates) {
            if (System.currentTimeMillis() - t0 > Config.SCAN_TIME_BUDGET_MS) {
                XLog.w("scan budget exceeded");
                break;
            }
            for (ClassLoader cl : LoaderCollector.all()) {
                final String fName = name;
                final ClassLoader fCl = cl;
                Safe.run("hook:" + fName, new Safe.Block() {
                    @Override public void run() throws Throwable { hookClass(fName, fCl); }
                });
            }
        }

        XLog.i("SUMMARY hooks=" + HOOKS.get()
                + " classes=" + HOOKED.size()
                + " targeted=" + TARGETED.get()
                + " activated=true"
                + " features=vip-gate/tier/expiry/fields/prefs-json/textview"
                + " | LokTvHookPro v" + Config.VERSION_NAME);
    }

    // ================================================================
    // طبقة SharedPreferences (مرة واحدة)
    // ================================================================
    private static void hookSharedPreferences() {
        if (prefsHooked) return;
        prefsHooked = true;

        // SharedPreferencesImpl صنف إطار: جرّب boot أولاً ثم أي loader متاح
        Class<?> impl = null;
        try {
            impl = Class.forName("android.app.SharedPreferencesImpl", false, null);
        } catch (Throwable ignored) {}
        if (impl == null) {
            for (ClassLoader cl : LoaderCollector.all()) {
                try {
                    impl = Class.forName("android.app.SharedPreferencesImpl", false, cl);
                    if (impl != null) break;
                } catch (Throwable ignored) {}
            }
        }
        if (impl == null) {
            XLog.w("prefs: SharedPreferencesImpl not found, skipping");
            return;
        }
        final Class<?> target = impl;

        hookBooleanGetter(target, "getBoolean", true);
        hookIntGetter(target, "getInt", 99);
        try {
            XposedHelpers.findAndHookMethod(target, "contains", String.class,
                    new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    try {
                        String k = (String) p.args[0];
                        if (k == null) return;
                        String norm = k.toLowerCase(Locale.US).replace("_", "");
                        if (Config.isVipKey(norm)) p.setResult(true);
                    } catch (Throwable t) {
                        XLog.w("prefs.contains: " + t);
                    }
                }
            });
        } catch (Throwable t) {
            XLog.w("prefs.contains hook: " + t);
        }
        XLog.i("data-layer: SharedPreferencesImpl hooked");
    }

    private static void hookBooleanGetter(Class<?> target, String m, final boolean forced) {
        try {
            XposedHelpers.findAndHookMethod(target, m, String.class, boolean.class,
                    new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    try {
                        String k = (String) p.args[0];
                        if (k == null) return;
                        String norm = k.toLowerCase(Locale.US).replace("_", "");
                        if (Config.isAnalyticsKey(norm)) return;
                        if (Config.isVipKey(norm)) p.setResult(forced);
                    } catch (Throwable t) {
                        XLog.w("prefs." + m + ": " + t);
                    }
                }
            });
        } catch (Throwable t) {
            XLog.w("prefs." + m + " hook: " + t);
        }
    }

    private static void hookIntGetter(Class<?> target, String m, final int forced) {
        try {
            XposedHelpers.findAndHookMethod(target, m, String.class, int.class,
                    new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    try {
                        String k = (String) p.args[0];
                        if (k == null) return;
                        String norm = k.toLowerCase(Locale.US).replace("_", "");
                        if (Config.isAnalyticsKey(norm)) return;
                        if (Config.isVipKey(norm)) p.setResult(forced);
                    } catch (Throwable t) {
                        XLog.w("prefs." + m + ": " + t);
                    }
                }
            });
        } catch (Throwable t) {
            XLog.w("prefs." + m + " hook: " + t);
        }
    }

    // ================================================================
    // طبقة JSONObject (مرة واحدة)
    // ================================================================
    private static void hookJson() {
        if (jsonHooked) return;
        jsonHooked = true;

        final Class<?> c = JSONObject.class;
        String[] boolMethods = {"getBoolean", "optBoolean"};
        String[] intMethods = {"getInt", "optInt"};
        for (int i = 0; i < boolMethods.length; i++) {
            final String m = boolMethods[i];
            Safe.run("json." + m, new Safe.Block() {
                @Override public void run() throws Throwable {
                    for (Method mm : c.getDeclaredMethods()) {
                        if (!mm.getName().equals(m)) continue;
                        if (mm.getParameterTypes().length < 1) continue;
                        if (mm.getParameterTypes()[0] != String.class) continue;
                        final Method target = mm;
                        XposedBridge.hookMethod(target, new XC_MethodHook() {
                            @Override protected void beforeHookedMethod(MethodHookParam p) {
                                try {
                                    String k = (String) p.args[0];
                                    if (k == null) return;
                                    String norm = k.toLowerCase(Locale.US).replace("_", "");
                                    if (Config.isVipKey(norm)) {
                                        if (target.getReturnType() == boolean.class) {
                                            p.setResult(true);
                                        } else if (target.getReturnType() == int.class) {
                                            p.setResult(99);
                                        }
                                    }
                                } catch (Throwable t) {
                                    XLog.w("json." + m + ": " + t);
                                }
                            }
                        });
                    }
                }
            });
        }
        for (int i = 0; i < intMethods.length; i++) {
            final String m = intMethods[i];
            Safe.run("json.int." + m, new Safe.Block() {
                @Override public void run() throws Throwable {
                    for (Method mm : c.getDeclaredMethods()) {
                        if (!mm.getName().equals(m)) continue;
                        if (mm.getParameterTypes().length < 1) continue;
                        if (mm.getParameterTypes()[0] != String.class) continue;
                        XposedBridge.hookMethod(mm, new XC_MethodHook() {
                            @Override protected void beforeHookedMethod(MethodHookParam p) {
                                try {
                                    String k = (String) p.args[0];
                                    if (k == null) return;
                                    String norm = k.toLowerCase(Locale.US).replace("_", "");
                                    if (Config.isVipKey(norm)) p.setResult(99);
                                } catch (Throwable t) {
                                    XLog.w("json." + m + ": " + t);
                                }
                            }
                        });
                    }
                }
            });
        }
        XLog.i("data-layer: org.json.JSONObject hooked");
    }

    // ================================================================
    // طبقة TextView (مرة واحدة)
    // ================================================================
    private static void hookTextView() {
        if (textHooked) return;
        textHooked = true;

        try {
            XposedHelpers.findAndHookMethod(TextView.class, "setText", CharSequence.class,
                    new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    try {
                        Object arg = p.args[0];
                        if (!(arg instanceof CharSequence)) return;
                        String s = arg.toString();
                        for (int i = 0; i < Config.TEXT_REPLACEMENTS.length; i++) {
                            String[] rule = Config.TEXT_REPLACEMENTS[i];
                            if (s.equals(rule[0])) { p.args[0] = rule[1]; return; }
                        }
                    } catch (Throwable t) {
                        XLog.w("textview: " + t);
                    }
                }
            });
            XLog.i("text-layer: TextView.setText hooked ("
                    + Config.TEXT_REPLACEMENTS.length + " rules)");
        } catch (Throwable t) {
            XLog.w("textview hook: " + t);
        }
    }

    // ================================================================
    // ربط صنف واحد بالبصمة
    // ================================================================
    private static void hookClass(String name, ClassLoader cl) {
        Class<?> k;
        try { k = Class.forName(name, false, cl); }
        catch (Throwable t) { return; }

        if (k.isInterface() || Modifier.isAbstract(k.getModifiers())) return;
        synchronized (HOOKED) {
            if (HOOKED.contains(name)) return;
        }

        String lower = name.toLowerCase(Locale.US);
        boolean isVip    = Config.isVipName(lower);
        boolean isDetect = Config.isDetectName(lower);

        int added = 0;
        Method[] methods;
        try {
            methods = k.getDeclaredMethods();
        } catch (Throwable t) {
            return;
        }
        int perClass = 0;

        for (int i = 0; i < methods.length; i++) {
            Method m = methods[i];
            if (perClass >= Config.MAX_HOOKS_PER_CLASS) break;
            if (HOOKS.get() >= Config.MAX_HOOKS) return;
            int mod = m.getModifiers();
            if (Modifier.isAbstract(mod)) continue;
            if (Modifier.isNative(mod))   continue;
            String mn = m.getName();
            if ("<init>".equals(mn) || "<clinit>".equals(mn)) continue;
            if (mn.equals("toString") || mn.equals("hashCode") || mn.equals("equals")
                    || mn.equals("getClass") || mn.equals("clone")
                    || mn.equals("wait") || mn.equals("notify") || mn.equals("notifyAll")
                    || mn.equals("finalize")) continue;

            Class<?> rt = m.getReturnType();
            String mnl = mn.toLowerCase(Locale.US);

            boolean doHook = false;
            XC_MethodHook handler = null;

            if (isDetect) {
                // صنف كشف ⇒ عطّله: boolean=false، أرقام=0
                if (rt == boolean.class) {
                    handler = returnBool(false);
                    doHook = true;
                } else if (rt == int.class || rt == long.class) {
                    handler = returnZero(rt);
                    doHook = true;
                } else if (mnl.contains("call") || mnl.contains("check")
                        || mnl.contains("detect")) {
                    handler = noop();
                    doHook = true;
                }
            } else if (isVip) {
                // درجة A: كل boolean=true، كل int/long=99، أسماء VIP نصية="VIP"
                if (rt == boolean.class) {
                    handler = returnBool(true);
                    doHook = true;
                } else if (rt == int.class || rt == long.class) {
                    handler = returnInt99(rt);
                    doHook = true;
                } else if (rt == String.class && isVipMethodName(mnl)) {
                    handler = returnString("VIP");
                    doHook = true;
                } else if (isVipMethodName(mnl) && (rt == void.class)) {
                    // دوال void: اجبر الحقول المنطقية بعد التنفيذ
                    handler = forceFieldsAfter();
                    doHook = true;
                }
            } else {
                // درجة B: فقط الدوال التي اسمها VIP
                if (isVipMethodName(mnl)) {
                    if (rt == boolean.class) {
                        handler = returnBool(true); doHook = true;
                    } else if (rt == int.class || rt == long.class) {
                        handler = returnInt99(rt); doHook = true;
                    }
                }
            }

            if (doHook && handler != null) {
                try {
                    XposedBridge.hookMethod(m, handler);
                    added++; perClass++;
                    HOOKS.incrementAndGet();
                } catch (Throwable ignored) {}
            }
        }

        if (added > 0) {
            synchronized (HOOKED) {
                HOOKED.add(name);
            }
            TARGETED.incrementAndGet();
            XLog.i("hooked class: " + name + " hooks+=" + added);
        }
    }

    private static boolean isVipMethodName(String lower) {
        return lower.contains("vip") || lower.contains("premium")
                || lower.contains("subscri") || lower.contains("member")
                || lower.contains("entitle") || lower.contains("paid")
                || (lower.contains("pro") && !lower.contains("process"))
                || lower.contains("gate") || lower.contains("tier")
                || lower.contains("expire") || lower.contains("expiry");
    }

    // ====== هوكات مساعدة (بصمة نوع الإرجاع) ======
    private static XC_MethodHook returnBool(final boolean v) {
        return new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                try { p.setResult(v); } catch (Throwable t) { XLog.w("ret-bool: " + t); }
            }
        };
    }
    private static XC_MethodHook returnInt99(final Class<?> rt) {
        return new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                try {
                    p.setResult(rt == long.class ? (Object) 99L : (Object) 99);
                } catch (Throwable t) { XLog.w("ret-99: " + t); }
            }
        };
    }
    private static XC_MethodHook returnZero(final Class<?> rt) {
        return new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                try {
                    p.setResult(rt == long.class ? (Object) 0L : (Object) 0);
                } catch (Throwable t) { XLog.w("ret-0: " + t); }
            }
        };
    }
    private static XC_MethodHook returnString(final String s) {
        return new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                try { p.setResult(s); } catch (Throwable t) { XLog.w("ret-str: " + t); }
            }
        };
    }
    private static XC_MethodHook noop() {
        return new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                try {
                    if (!(p.method instanceof Method)) return;
                    Class<?> rt = ((Method) p.method).getReturnType();
                    if (rt == void.class) return;
                    if (rt == boolean.class) { p.setResult(false); return; }
                    if (rt == int.class) { p.setResult(0); return; }
                    if (rt == long.class) { p.setResult(0L); return; }
                    p.setResult(null);
                } catch (Throwable t) { XLog.w("noop: " + t); }
            }
        };
    }
    private static XC_MethodHook forceFieldsAfter() {
        return new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                try {
                    Object self = p.thisObject;
                    if (self == null) return;
                    Class<?> k = self.getClass();
                    while (k != null && k != Object.class) {
                        Field[] fields;
                        try {
                            fields = k.getDeclaredFields();
                        } catch (Throwable t) {
                            break;
                        }
                        for (int i = 0; i < fields.length; i++) {
                            final Field f = fields[i];
                            if (Modifier.isStatic(f.getModifiers())) continue;
                            if (f.getType() != boolean.class) continue;
                            Safe.run("force." + f.getName(), new Safe.Block() {
                                @Override public void run() throws Throwable {
                                    f.setAccessible(true);
                                    f.setBoolean(p.thisObject, true);
                                }
                            });
                        }
                        k = k.getSuperclass();
                    }
                } catch (Throwable t) {
                    XLog.w("forceFields: " + t);
                }
            }
        };
    }

    // ====== أداة ======
    private static File apkOf(Context ctx) {
        if (ctx == null) return null;
        try {
            String p = ctx.getPackageCodePath();
            if (p == null) return null;
            File f = new File(p);
            return f.isFile() ? f : null;
        } catch (Throwable t) { return null; }
    }

    private UnlockEngine() {}
}
