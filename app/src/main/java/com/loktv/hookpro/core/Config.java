package com.loktv.hookpro.core;

/** كل الإعدادات في مكان واحد — عدّل هنا فقط عند الصيانة. */
public final class Config {

    public static final String  VERSION_NAME = "4.1.0";
    public static final int     VERSION_CODE = 410;
    public static final int     MIN_SDK      = 26; // Android 8.0
    public static final int     MAX_SDK      = 35; // Android 15

    public static final String MODULE_PACKAGE = "com.loktv.hookpro";

    /** الوصف المدمج (): يظهر في مدير الموديولات وسجل التشغيل — مختصر جداً. */
    public static final String SHORT_DESC =
            "LokTvHookPro 4.1.0 | بصمة+انعكاس | API26-35 | آمن | روت/بدون روت";

    // ==== نطاق الحقن ====
    /** true = أي حزمة يعلّمها المستخدم (بغض النظر عن اسم الحزمة). */
    public static final boolean TARGET_ALL_PACKAGES = true;
    public static final boolean ALLOW_SUBPROCESSES  = false;

    // ==== حدود السلامة ====
    public static final int MAX_HOOKS             = 900;
    public static final int MAX_HOOKS_PER_CLASS   = 60;
    public static final int MAX_CLASSES_SCAN      = 6000;
    public static final long SCAN_TIME_BUDGET_MS  = 3000L;
    public static final int MAX_DEX_STRING_SIZE   = 900_000;

    // ==== سلسلة تفعيل (للتطبيقات المضغوطة/المحمّية: تُعاد المحاولة بعد فك الضغط) ====
    public static final long[] ACTIVATION_DELAYS_MS =
            new long[]{ 2000L, 4000L, 7000L, 11000L, 16000L, 23000L, 31000L };

    /** اسم ملف kill-switch داخل cacheDir الهدف: وجوده = إيقاف فوري بلا كراش. */
    public static final String KILLSWITCH_FILE = "loktv_hook_safe.flag";
    public static final String LOG_FILE = "loktphook.log";

    // ==== فلتر البنية التحتية (لا تلمسها أبدًا) ====
    private static final String[] INFRA_PREFIXES = {
            "com.loktv.hookpro",
            "com.pairip", "fancybypass", "com.stub", "com.secneo",
            "com.qihoo", "com.tencent.stubshell", "com.tencent.legu",
            "com.wrapper.proxyapplication", "com.nqshield", "com.dx.mobile",
            "com.bangcle", "jiagu", "network0", "hidden0", "s.h.e.l.l",
            "com.bytedance.pangle", "org.lsposed", "de.robv", "top.canyie",
            "dalvik.", "libcore.", "sun.", "com.android.dex"
    };

    private static final String[] INFRA_TOKENS = {
            "classloader", "appfactory", "application", "contentprovider",
            "provider", "activitythread", "loadedapk", "instrumentation",
            "bootclassloader", "multidex", "pathlist", "classpath",
            "proxyapplication", "stubapp", "stubapplication"
    };

    // ==== تطابق البصمة (اسم الصنف) ====
    public static final String[] VIP_FINGERPRINTS = {
            "userstatus", "vipstatus", "vipitem", "vipinfo", "vipbean",
            "oldvipinfo", "uservipinfo", "usergradeinfo", "myvipinfo",
            "subscription", "subscriptionitem", "subscriptionbean",
            "membership", "entitlement", "premium", "purchase",
            "billing", "billingclient", "playstoresubscription",
            "userloader", "gate", "tier"
    };

    public static final String[] DETECT_FINGERPRINTS = {
            "antivpn", "vpn", "rootdetect", "rootcheck", "safetycheck",
            "integritycheck", "debugcheck", "emulator", "tamper",
            "signaturecheck", "licensecheck"
    };

    // ==== تطابق بادئات الحزمة المفيدة ====
    public static final String[] HOST_PKG_HINTS = {
            "com.hecate", "com.novan", "com.looktv", "com.hecate.phantom"
    };

    // ==== مفاتيح SharedPreferences / JSON ====
    public static final String[] VIP_KEY_HINTS = {
            "vip", "premium", "pro", "paid", "subscriber", "subscription",
            "membership", "entitlement", "expire", "expiry", "expiration",
            "tier", "grade", "level", "gate", "active", "valid"
    };

    public static final String[] ANALYTICS_KEY_DENY = {
            "analytics", "count", "click", "seen", "first", "session",
            "impression", "event", "track", "stat", "index", "page"
    };

    // ==== نصوص الواجهة ====
    public static final String[][] TEXT_REPLACEMENTS = {
            { "اشتراك تجريبي",  "VIP" },
            { "ترقية الحساب",   "VIP مفعّل" },
            { "غير مشترك",      "VIP" }
    };

    // ==== أدوات ====
    public static boolean isInfrastructure(String pkg) {
        if (pkg == null) return true;
        String p = pkg.toLowerCase();
        for (String s : INFRA_PREFIXES) if (p.startsWith(s)) return true;
        for (String t : INFRA_TOKENS)   if (p.contains(t))   return true;
        if (p.startsWith("android."))  return true;
        if (p.startsWith("androidx.")) return true;
        if (p.startsWith("kotlin"))    return true;
        if (p.startsWith("com.google.")) return true; // GMS + google.* infra
        return false;
    }

    public static boolean isVipName(String lowerName) {
        for (String s : VIP_FINGERPRINTS) if (lowerName.contains(s)) return true;
        return false;
    }

    public static boolean isDetectName(String lowerName) {
        for (String s : DETECT_FINGERPRINTS) if (lowerName.contains(s)) return true;
        return false;
    }

    public static boolean isAnalyticsKey(String normalized) {
        for (String s : ANALYTICS_KEY_DENY) if (normalized.contains(s)) return true;
        return false;
    }

    public static boolean isVipKey(String normalized) {
        for (String s : VIP_KEY_HINTS) if (normalized.contains(s)) return true;
        return false;
    }

    private Config() {}
}
