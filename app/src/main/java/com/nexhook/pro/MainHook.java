package com.nexhook.pro;

import com.nexhook.pro.core.Config;
import com.nexhook.pro.core.UnlockEngine;
import com.nexhook.pro.core.XLog;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * NexHookPro v7.0.2 — Entry Point.
 *
 *  يعمل مع legacy API-82 (LSPosed/EdXposed/NPatch/LSPatch/HKP).
 *  متوافق مع libxposed 101→102 (Legacy compatibility mode).
 *  لا يعتمد أي اسم حزمة ثابتة.
 */
public class MainHook implements IXposedHookZygoteInit, IXposedHookLoadPackage {

    @Override
    public void initZygote(StartupParam sp) {
        XLog.i("zygote | modulePath=" + sp.modulePath + " | v=" + Config.VERSION);
        UnlockEngine.markStart();
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) {
        try {
            if (lp == null) return;

            String pkg = lp.packageName;
            if (pkg == null || pkg.isEmpty()) return;
            if (pkg.startsWith("com.nexhook.")) return;

            String proc = lp.processName;
            if (proc != null && !proc.equals(pkg) && !proc.startsWith(pkg + ":")) return;

            XLog.i("target | pkg=" + pkg
                    + " | proc=" + proc
                    + " | firstApp=" + lp.isFirstApplication
                    + " | v=" + Config.VERSION);

            final UnlockEngine eng = new UnlockEngine(pkg, lp.classLoader);
            eng.passive();

            final long t0 = System.currentTimeMillis();
            new Thread(new Runnable() {
                @Override public void run() {
                    for (int i = 0; i < 60; i++) {
                        try { Thread.sleep(250); } catch (InterruptedException ignored) {}
                        eng.tick(System.currentTimeMillis() - t0);
                    }
                }
            }, "nexhook-tick").start();

        } catch (Throwable t) {
            XLog.e("handleLoadPackage", t);
        }
    }
}
