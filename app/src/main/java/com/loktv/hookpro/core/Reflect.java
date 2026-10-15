package com.loktv.hookpro.core;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** انعكاس آمن عبر الوراثة (superclass walk) — لا أسماء ثابتة وحيدة. */
public final class Reflect {

    public static Field field(Class<?> c, String name) {
        Class<?> cur = c;
        while (cur != null) {
            try {
                Field f = cur.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (Throwable ignored) {}
            cur = cur.getSuperclass();
        }
        return null;
    }

    public static Object get(Object obj, Field f) {
        if (f == null) return null;
        try { return f.get(obj); } catch (Throwable t) { return null; }
    }

    public static void set(Object obj, Field f, Object v) {
        if (f == null) return;
        try { f.set(obj, v); } catch (Throwable ignored) {}
    }

    public static Method method(Class<?> c, String name, Class<?>... params) {
        Class<?> cur = c;
        while (cur != null) {
            try {
                Method m = cur.getDeclaredMethod(name, params);
                m.setAccessible(true);
                return m;
            } catch (Throwable ignored) {}
            cur = cur.getSuperclass();
        }
        return null;
    }

    public static Object call(Method m, Object target, Object... args) {
        if (m == null) return null;
        try { return m.invoke(target, args); } catch (Throwable t) { return null; }
    }

    private Reflect() {}
}
