package com.nexhook.pro.core;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * v7.0.1 — لا هوك على أي ClassLoader إطلاقاً:
 * لا BaseDexClassLoader، لا loadClass، لا findClass، لا منشئات لوادر.
 *
 * <p>البروتوكول: register() في المرحلة السلبية، tryArm() يستهلك التسجيل
 * مرة واحدة عند التنشيط (أي محاولة ثانية تُرفض).
 *
 * <p>إضافة محافظ عليها: noteLoader() — <b>قراءة</b> getClassLoader() من داخل
 * هوكات Application/Activity (ليست هوكاً على ClassLoader) كاحتياط للتطبيقات
 * المضغوطة عندما يكون loader الأساسي stub. تُستخدم للقراءة فقط عند فشل
 * forName على الأساسي.
 */
public final class LoaderCollector {

    private static final Set<Integer> ARMED =
            Collections.newSetFromMap(new ConcurrentHashMap<Integer, Boolean>());

    private static final List<WeakReference<ClassLoader>> NOTED =
            Collections.synchronizedList(new ArrayList<WeakReference<ClassLoader>>());

    private LoaderCollector() {}

    public static void register(ClassLoader cl) {
        if (cl == null) return;
        ARMED.add(System.identityHashCode(cl));
    }

    public static boolean tryArm(ClassLoader cl) {
        if (cl == null) return false;
        return ARMED.remove(System.identityHashCode(cl));
    }

    /** قراءة فقط — يُستدعى من هوكات Application/Activity (ليس ClassLoader). */
    public static void noteLoader(ClassLoader cl) {
        if (cl == null) return;
        synchronized (NOTED) {
            for (int i = 0; i < NOTED.size(); i++) {
                if (NOTED.get(i).get() == cl) return;
            }
            NOTED.add(new WeakReference<ClassLoader>(cl));
        }
    }

    public static List<ClassLoader> noted() {
        List<ClassLoader> out = new ArrayList<ClassLoader>();
        synchronized (NOTED) {
            for (int i = 0; i < NOTED.size(); i++) {
                ClassLoader cl = NOTED.get(i).get();
                if (cl != null) out.add(cl);
            }
        }
        return out;
    }
}
