package com.nexhook.pro.core;

import java.lang.reflect.Method;

public final class Safe {

    private Safe() {}

    public static void run(Runnable r) {
        try { r.run(); } catch (Throwable t) { XLog.e("Safe.run", t); }
    }

    public interface Producer<T> { T produce() throws Throwable; }

    public static <T> T call(Producer<T> p, T fallback) {
        try { return p.produce(); }
        catch (Throwable t) { XLog.e("Safe.call", t); return fallback; }
    }

    /** يحل ClassCastException: Long cannot be cast to Integer (درس v5.0.0). */
    public static Object coerceReturn(Method m, Object value) {
        if (m == null) return value;
        Class<?> rt = m.getReturnType();
        if (rt == void.class) return null;

        if (value == null) {
            if (rt == int.class)     return 0;
            if (rt == long.class)    return 0L;
            if (rt == short.class)   return (short) 0;
            if (rt == byte.class)    return (byte) 0;
            if (rt == boolean.class) return Boolean.FALSE;
            if (rt == float.class)   return 0f;
            if (rt == double.class)  return 0d;
            if (rt == char.class)    return (char) 0;
            return null;
        }

        Number n;
        if (value instanceof Number)       n = (Number) value;
        else if (value instanceof Boolean) n = ((Boolean) value) ? 1 : 0;
        else {
            try { n = Long.parseLong(value.toString()); }
            catch (Throwable t) { n = 0L; }
        }

        if (rt == int.class     || rt == Integer.class)   return n.intValue();
        if (rt == long.class    || rt == Long.class)      return n.longValue();
        if (rt == short.class   || rt == Short.class)     return n.shortValue();
        if (rt == byte.class    || rt == Byte.class)      return n.byteValue();
        if (rt == float.class   || rt == Float.class)     return n.floatValue();
        if (rt == double.class  || rt == Double.class)    return n.doubleValue();
        if (rt == boolean.class || rt == Boolean.class)   return n.intValue() != 0;
        if (rt == char.class    || rt == Character.class) return (char) n.intValue();
        return value;
    }
}
