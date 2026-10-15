package com.loktv.hookpro;

import android.app.Application;
import android.content.Context;
import android.os.Build;

import com.loktv.hookpro.core.Config;
import com.loktv.hookpro.core.Ctx;
import com.loktv.hookpro.core.LoaderCollector;
import com.loktv.hookpro.core.Safe;
import com.loktv.hookpro.core.SignatureCheck;
import com.loktv.hookpro.core.UnlockEngine;
import com.loktv.hookpro.core.XLog;

import java.io.File;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * LokTvHookPro — نقطة الدخول.
 *
 * مرحلتان:
 *  1) فورية: جمع الـ ClassLoader + بصمة الإطار (لا تمس كود المضيف).
 *  2) متسلسلة: تفعيل UnlockEngine عبر ACTIVATION_DELAYS_MS حتى بعد فك
 *     ضغط التطبيقات المضغوطة/المحمية، مع kill-switch يوقف كل شيء بلا كراش.
 *
 * روت (LSPosed/EdXposed) وبدون روت (LSPatch/NPatch/HKP): لا APIs خاصة بالروت.
 * API 26 → 35 (الأعلى/الأقل بتحمّل، والأقل من 26 يُتجاهل نظيفاً).
 */
public class HookEntry implements IXposedHookLoadPackage, IXposedHookZygoteInit {

    @Override
    public void initZygote(StartupParam startupParam) {
        try {
            XLog.setModulePath(startupParam.modulePath);
        } catch (Throwable ignored) {}
    }

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) {
        // 0) لا نخترق أنفسنا أبداً.
        if (Config.MODULE_PACKAGE.equals(lpparam.packageName)) {
            return;
        }
        // 1) حارس الإصدار: أقل من 26 يُتجاهل، الأعلى يُتحمَّل.
        final int sdk = sdkInt();
        if (sdk < Config.MIN_SDK) {
            return;
        }

        try {
            XLog.i(Config.SHORT_DESC
                    + " | sdk=" + sdk
                    + " | pkg=" + lpparam.packageName
                    + " | proc=" + lpparam.processName
                    + " | firstApp=" + lpparam.isFirstApplication);

            // 2) فلتر العمليات: الافتراضي العملية الأولى فقط (بلا تكرار).
            if (!Config.ALLOW_SUBPROCESSES && !lpparam.isFirstApplication) {
                XLog.d("skip subprocess: " + lpparam.processName);
                return;
            }

            // 3) فلتر الحزمة: الافتراضي أي حزمة (بغض النظر عن الاسم).
            if (!Config.TARGET_ALL_PACKAGES && !isHostHint(lpparam.packageName)) {
                XLog.d("skip package: " + lpparam.packageName);
                return;
            }

            Ctx.setPackageName(lpparam.packageName);

            // 4) المرحلة 1 — جمع اللوادر من 5 مصادر (تعمل مع Stub).
            LoaderCollector.install(lpparam);

            // 5) خطاف بدء التطبيق الحقيقي: تقرير توقيع + ربط اللوج + بدء السلسلة.
            XposedHelpers.findAndHookMethod(Application.class, "onCreate",
                    new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    Safe.run("bootstrap", new Safe.Block() {
                        @Override public void run() throws Throwable {
                            Application app = (Application) p.thisObject;
                            Ctx.setApplication(app);
                            bindLogFile(app);
                            SignatureCheck.report(app);
                            startActivationChain();
                        }
                    });
                }
            });

            // احتياط: لو حُقن الموديول في عملية حيّة بعد onCreate، ابدأ السلسلة فوراً.
            startActivationChain();
        } catch (Throwable t) {
            // الحارس الأخير: لا يتسرّب أي استثناء إلى التطبيق الهدف.
            try { XLog.w("HookEntry guard: " + t); } catch (Throwable ignored) {}
        }
    }

    /** سلسلة التفعيل عبر التأخيرات — تلتقط الأصناف بعد فك الضغط المتأخر. */
    private void startActivationChain() {
        Safe.run("chain", new Safe.Block() {
            @Override public void run() throws Throwable {
                Thread t = new Thread(new Runnable() {
                    @Override public void run() {
                        long elapsed = 0L;
                        for (int i = 0; i < Config.ACTIVATION_DELAYS_MS.length; i++) {
                            long waitFor = Config.ACTIVATION_DELAYS_MS[i] - elapsed;
                            if (waitFor > 0) {
                                try {
                                    Thread.sleep(waitFor);
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                    return;
                                }
                                elapsed = Config.ACTIVATION_DELAYS_MS[i];
                            }
                            if (killSwitchOn()) {
                                XLog.w("kill-switch ON — activation halted safely");
                                return;
                            }
                            final int pass = i + 1;
                            Safe.run("pass-" + pass, new Safe.Block() {
                                @Override public void run() throws Throwable {
                                    Context ctx = Ctx.get();
                                    XLog.i("activation pass " + pass
                                            + "/" + Config.ACTIVATION_DELAYS_MS.length);
                                    UnlockEngine.activate(ctx);
                                }
                            });
                        }
                    }
                }, "LokTvHookPro-Activate");
                t.setDaemon(true);
                t.start();
            }
        });
    }

    /** kill-switch: وجود الملف في cacheDir الهدف = إيقاف فوري بلا كراش. */
    private boolean killSwitchOn() {
        try {
            Context ctx = Ctx.get();
            if (ctx == null) return false;
            File dir = null;
            try { dir = ctx.getCacheDir(); } catch (Throwable ignored) {}
            if (dir == null) return false;
            return new File(dir, Config.KILLSWITCH_FILE).exists();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void bindLogFile(Context ctx) {
        try {
            File dir = null;
            try { dir = ctx.getCacheDir(); } catch (Throwable ignored) {}
            if (dir != null) {
                XLog.bindFile(new File(dir, Config.LOG_FILE));
            }
        } catch (Throwable ignored) {}
    }

    private boolean isHostHint(String pkg) {
        if (pkg == null) return false;
        String p = pkg.toLowerCase();
        for (int i = 0; i < Config.HOST_PKG_HINTS.length; i++) {
            if (p.startsWith(Config.HOST_PKG_HINTS[i])) return true;
        }
        return false;
    }

    private int sdkInt() {
        try {
            return Build.VERSION.SDK_INT;
        } catch (Throwable t) {
            return Config.MIN_SDK;
        }
    }
}
