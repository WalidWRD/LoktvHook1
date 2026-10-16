package com.nexhook.pro;

import android.util.Log;

import com.nexhook.pro.core.Config;
import com.nexhook.pro.core.ModernEngine;

import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

/**
 * المدخل الحديث (libxposed API 102) — يُعلن عبر META-INF/xposed/java_init.list.
 * نفس سياسة MainHook تماماً (بصمة + انعكاس، بلا أسماء ثابتة، لا كراش، أي حزمة)،
 * مع قواعد v7: لا loadLibrary، ‏ContextWrapper، ‏BaseBundle، تفعيل واحد.
 * القرارات مشتركة (Config/DexNames/Fingerprint) — الميكانيكا فقط عبر libxposed.
 */
public class ModernHook extends XposedModule {

    @Override
    public void onPackageLoaded(XposedModuleInterface.PackageLoadedParam lp) {
        try {
            ModernEngine.onPackageLoaded(this, lp);
        } catch (Throwable t) {
            try {
                log(Log.WARN, "NexHookPro", "ModernHook guard: " + t);
            } catch (Throwable ignored) {}
        }
    }
}
