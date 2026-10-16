package com.nexhook.pro.core;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

public final class Reflect {

    private Reflect() {}

    public static Field findField(Class<?> start, String name) {
        Class<?> c = start;
        while (c != null) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (Throwable ignored) { c = c.getSuperclass(); }
        }
        return null;
    }

    public static Method findMethod(Class<?> start, String name, Class<?>... params) {
        Class<?> c = start;
        while (c != null) {
            try {
                Method m = c.getDeclaredMethod(name, params);
                m.setAccessible(true);
                return m;
            } catch (Throwable ignored) { c = c.getSuperclass(); }
        }
        return null;
    }

    public static List<Method> allMethods(Class<?> clazz) {
        List<Method> out = new ArrayList<Method>();
        HashSet<String> seen = new HashSet<String>();
        Class<?> c = clazz;
        while (c != null && c != Object.class) {
            Method[] declared;
            try {
                declared = c.getDeclaredMethods();
            } catch (Throwable t) {
                break;
            }
            for (int i = 0; i < declared.length; i++) {
                Method m = declared[i];
                int mod = m.getModifiers();
                if (Modifier.isAbstract(mod)) continue;
                if (Modifier.isNative(mod)) continue;
                String key = m.getName() + "#" + Arrays.toString(m.getParameterTypes());
                if (seen.add(key)) {
                    try { m.setAccessible(true); } catch (Throwable ignored) {}
                    out.add(m);
                }
            }
            c = c.getSuperclass();
        }
        return out;
    }

    public static Class<?> forNameSafe(String name, ClassLoader cl) {
        try { return Class.forName(name, false, cl); }
        catch (Throwable t) { return null; }
    }
}
