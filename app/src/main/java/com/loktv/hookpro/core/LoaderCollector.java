package com.loktv.hookpro.core;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.WeakHashMap;

import dalvik.system.BaseDexClassLoader;
import dalvik.system.InMemoryDexClassLoader;
import dalvik.system.PathClassLoader;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * يجمع كل ClassLoader (الأصلي + المفكوك) من 5 مصادر — يعمل حتى لو التطبيق
 * مبني بـ Stub (التطبيقات المضغوطة/المحمية) حيث lpparam.classLoader وحده لا يكفي.
 */
public final class LoaderCollector {

    private static final Set<ClassLoader> LOADERS =
            Collections.newSetFromMap(new WeakHashMap<ClassLoader, Boolean>());
    private static final Set<ClassLoader> REAL = Collections.newSetFromMap(
            new WeakHashMap<ClassLoader, Boolean>());
    private static volatile boolean installed = false;

    @SuppressWarnings("unchecked")
    public static void install(XC_LoadPackage.LoadPackageParam lp) {
        if (!installed) {
            installed = true;
            Safe.run("LoaderCollector", new Safe.Block() {
                @Override public void run() throws Throwable {
                    add(lp.classLoader, false);

                    // 1) Application.attachBaseContext / onCreate
                    XposedHelpers.findAndHookMethod(Application.class,
                            "attachBaseContext", Context.class, new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam p) {
                            try {
                                Application a = (Application) p.thisObject;
                                Ctx.setApplication(a);
                                add(a.getClassLoader(), true);
                                XLog.i("loader collected: " + a.getClassLoader());
                            } catch (Throwable t) {
                                XLog.w("attachBaseContext spy: " + t);
                            }
                        }
                    });

                    XposedHelpers.findAndHookMethod(Application.class, "onCreate",
                            new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam p) {
                            try {
                                Application a = (Application) p.thisObject;
                                Ctx.set(a);
                                add(a.getClassLoader(), true);
                                XLog.i("loader collected: " + a.getClassLoader());
                            } catch (Throwable t) {
                                XLog.w("onCreate spy: " + t);
                            }
                        }
                    });

                    // 2) Activity.onCreate spy — يعمل حتى لو التطبيق مبني بـ Stub
                    XposedHelpers.findAndHookMethod(Activity.class, "onCreate", Bundle.class,
                            new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam p) {
                            try {
                                Activity a = (Activity) p.thisObject;
                                Ctx.set(a);
                                add(a.getClassLoader(), true);
                            } catch (Throwable t) {
                                XLog.w("activity spy: " + t);
                            }
                        }
                    });

                    // 3) PathClassLoader constructors
                    try {
                        XposedBridge.hookAllConstructors(PathClassLoader.class,
                                new XC_MethodHook() {
                            @Override protected void afterHookedMethod(MethodHookParam p) {
                                try {
                                    add((ClassLoader) p.thisObject, true);
                                } catch (Throwable t) {
                                    XLog.w("pathloader spy: " + t);
                                }
                            }
                        });
                    } catch (Throwable ignored) {}

                    // 4) InMemoryDexClassLoader constructors (API 26+ — محمي بـ try)
                    try {
                        XposedBridge.hookAllConstructors(InMemoryDexClassLoader.class,
                                new XC_MethodHook() {
                            @Override protected void afterHookedMethod(MethodHookParam p) {
                                try {
                                    add((ClassLoader) p.thisObject, true);
                                } catch (Throwable t) {
                                    XLog.w("inmemory spy: " + t);
                                }
                            }
                        });
                    } catch (Throwable ignored) {}

                    // 5) ActivityThread.currentApplication fallback
                    try {
                        Class<?> at = Class.forName("android.app.ActivityThread");
                        XposedHelpers.findAndHookMethod(at, "currentApplication",
                                new XC_MethodHook() {
                            @Override protected void afterHookedMethod(MethodHookParam p) {
                                try {
                                    Application a = (Application) p.getResult();
                                    if (a != null) {
                                        Ctx.set(a);
                                        add(a.getClassLoader(), true);
                                    }
                                } catch (Throwable t) {
                                    XLog.w("activitythread spy: " + t);
                                }
                            }
                        });
                    } catch (Throwable ignored) {}

                    // إشارة استخدام BaseDexClassLoader (يُبقي الاستيراد مستخدماً)
                    XLog.d("watching loaders incl. "
                            + BaseDexClassLoader.class.getSimpleName());
                }
            });
        }
        // In case the module is injected into a live process
        add(lp.classLoader, false);
    }

    public static void observe(XC_LoadPackage.LoadPackageParam lp) {
        add(lp.classLoader, false);
    }

    private static void add(ClassLoader cl, boolean real) {
        if (cl == null) return;
        synchronized (LOADERS) {
            if (LOADERS.add(cl) && real) {
                REAL.add(cl);
                Ctx.setClassLoader(cl);
            }
        }
    }

    public static Set<ClassLoader> all() {
        synchronized (LOADERS) { return new HashSet<ClassLoader>(LOADERS); }
    }

    public static Set<ClassLoader> real() {
        synchronized (LOADERS) { return new HashSet<ClassLoader>(REAL); }
    }

    private LoaderCollector() {}
}
