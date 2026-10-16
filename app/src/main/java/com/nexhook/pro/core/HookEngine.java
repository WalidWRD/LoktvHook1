package com.nexhook.pro.core;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/**
 * محرك الهوك الآمن (مسار legacy).
 * درجة 1 (VIP صريح): كل دالة boolean/int/long/String آمنة.
 * درجة 2 (تلميح ضعيف): فقط الدوال ذات اسم VIP.
 * كل فرض عبر Safe.coerceReturn — لا ClassCastException أبداً.
 */
public final class HookEngine {

    private final AtomicInteger hooks   = new AtomicInteger(0);
    private final AtomicInteger classes = new AtomicInteger(0);
    private final Set<String> seen = new HashSet<String>();

    public int hookCount()  { return hooks.get(); }
    public int classCount() { return classes.get(); }

    public void hookClass(Class<?> clazz, int tier) {
        if (clazz == null) return;
        if (clazz.isInterface()) return;
        if (Modifier.isAbstract(clazz.getModifiers())) return;

        String name = clazz.getName();
        if (Config.isForbidden(name)) return;
        if (hooks.get() >= Config.MAX_HOOKS) return;
        classes.incrementAndGet();

        int perClass = 0;
        List<Method> methods = Reflect.allMethods(clazz);
        for (int i = 0; i < methods.size(); i++) {
            Method m = methods.get(i);
            if (perClass >= Config.MAX_HOOKS_PER_CLASS) break;
            if (hooks.get() >= Config.MAX_HOOKS) break;
            if (!canHook(m)) continue;
            if (!shouldHook(m, tier)) continue;

            try {
                XposedBridge.hookMethod(m, new SafeHook());
                perClass++;
                hooks.incrementAndGet();
                XLog.d("hook[" + (tier == 1 ? "A" : "B") + "] "
                        + clazz.getSimpleName() + "#" + m.getName());
            } catch (Throwable t) {
                XLog.w("hook fail: " + name + "#" + m.getName());
            }
        }
    }

    private boolean canHook(Method m) {
        if (m == null) return false;
        int mod = m.getModifiers();
        if (Modifier.isAbstract(mod)) return false;
        if (Modifier.isNative(mod)) return false;
        if (Modifier.isInterface(mod)) return false;

        String n = m.getName();
        if ("<init>".equals(n) || "<clinit>".equals(n)) return false;
        if ("toString".equals(n) || "hashCode".equals(n) || "equals".equals(n)
                || "getClass".equals(n) || "clone".equals(n) || "wait".equals(n)
                || "notify".equals(n) || "notifyAll".equals(n) || "finalize".equals(n)) {
            return false;
        }
        // لا هوك على مسارات التحميل الأصلية أبداً (درس v6.0.0: libmmkv)
        if ("loadLibrary".equals(n)) return false;

        String sig;
        try {
            sig = m.getDeclaringClass().getName() + "#" + n
                    + Arrays.toString(m.getParameterTypes());
        } catch (Throwable t) {
            return false;
        }
        synchronized (seen) { return seen.add(sig); }
    }

    private boolean shouldHook(Method m, int tier) {
        if (tier == 1) return true;
        if (tier == 2) {
            String mn;
            try {
                mn = m.getName().toLowerCase(Locale.ROOT);
            } catch (Throwable t) {
                return false;
            }
            return Config.isVipName(mn);
        }
        return false;
    }

    private static final class SafeHook extends XC_MethodHook {
        @Override protected void afterHookedMethod(MethodHookParam p) {
            try {
                Method m = (Method) p.method;
                Class<?> rt = m.getReturnType();
                if (rt == void.class) return;

                String n;
                try {
                    n = m.getName().toLowerCase(Locale.ROOT);
                } catch (Throwable t) {
                    return;
                }
                boolean vip = Config.isVipName(n);

                Object repl = null;
                if (rt == boolean.class || rt == Boolean.class) {
                    if (vip) repl = Boolean.TRUE;
                } else if (rt == String.class) {
                    if (vip) repl = Config.VALUE_STRING_VIP;
                } else if (rt == int.class || rt == Integer.class
                        || rt == long.class || rt == Long.class
                        || rt == short.class || rt == Short.class
                        || rt == byte.class || rt == Byte.class) {
                    if (vip) {
                        repl = Config.isExpiryName(n)
                                ? Config.VALUE_LONG_EXPIRE
                                : Config.VALUE_LONG_DEFAULT;
                    }
                }
                if (repl == null) return;

                p.setResult(Safe.coerceReturn(m, repl));
            } catch (Throwable ignored) {}
        }
    }
}
