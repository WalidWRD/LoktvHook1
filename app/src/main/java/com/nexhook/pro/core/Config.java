package com.nexhook.pro.core;

import java.util.Locale;

public final class Config {

    public static final String VERSION = "7.0.2";
    public static final String LOG_FILE = "nexhook.log";
    public static final String EMERGENCY_FLAG = "nexhook_safe.flag";

    /** أدنى إصدار مدعوم: Android 7.1 (API 25). الأعلى يُتحمَّل دائماً. */
    public static final int MIN_SDK = 25;

    public static final int  MAX_HOOKS = 900;
    public static final int  MAX_HOOKS_PER_CLASS = 60;
    public static final int  MAX_CLASSES_SCAN = 6000;
    public static final long SCAN_TIME_BUDGET_MS = 2500L;
    public static final long ACTIVATION_TIMEOUT_MS = 8000L;

    // ⚠️ لا تُلمس — بنية تحتية (درس v5/v6: لمسها = انهيار)
    public static final String[] FORBIDDEN_PREFIXES = {
            "com.nexhook.pro",
            "com.pairip", "fancybypass",
            "com.stub", "com.secneo", "com.qihoo",
            "com.tencent.stubshell", "com.tencent.legu",
            "com.wrapper.proxyapplication",
            "com.nqshield", "com.dx.mobile", "com.bangcle",
            "com.bytedance.pangle",
            "org.lsposed", "de.robv", "top.canyie",
            "dalvik.", "libcore.", "sun.", "com.android.dex",
            "android.", "androidx.", "com.google.",
            "kotlin", "okhttp3", "squareup", "facebook",
            "bumptech", "org.json", "io.reactivex",
            "crashlytics", "apache"
    };

    public static final String[] FORBIDDEN_TOKENS = {
            "classloader", "appfactory", "application",
            "contentprovider", "provider", "activitythread",
            "loadedapk", "instrumentation", "bootclassloader",
            "multidex", "pathlist", "classpath",
            "proxyapplication", "stubapp", "stubapplication",
            "fancy", "bypass", "pairip", "licensecheck",
            "licens", "neutralis", "detection"
    };

    // درجة A — بصمات صريحة
    public static final String[] VIP_FINGERPRINTS = {
            "userstatus", "usertier", "vipitem", "viptier",
            "entitlement", "premium", "subscri", "membership",
            "purchase", "isvip", "ispro", "ispremium",
            "expire", "expiry", "validuntil", "endtime",
            "billing", "sku", "invoice", "proversion"
    };

    // درجة B — تلميحات ضعيفة (الربط فيها مشروط باسم الدالة فقط)
    public static final String[] FINGERPRINT_CLASS_HINTS = {
            "billing", "supporttv", "userloader", "userinfo",
            "accountmanager", "storemanager", "orderstatus",
            "subscriptionmanager", "purchasemanager"
    };

    public static final String[] VIP_KEY_HINTS = {
            "vip", "premium", "pro", "subscri", "tier",
            "entitlement", "expire", "expiry", "validuntil",
            "membership", "purchase", "paid", "unlock"
    };

    public static final String[] DATA_EXCLUDE_HINTS = {
            "analytics", "count", "click", "seen", "first",
            "session", "impression", "tracking", "log", "debug"
    };

    public static final long   VALUE_LONG_DEFAULT = 99L;
    public static final long   VALUE_LONG_EXPIRE  = 2100L;
    public static final String VALUE_STRING_VIP   = "VIP";

    public static final String[][] TEXT_REPLACEMENTS = {
            {"VIP Expired", "VIP Active"},
            {"Expired",     "Active"},
            {"Free User",   "VIP User"}
    };

    // =========================================================================
    //  الفلتر word-aware: بادئات على الكامل + رموز ككلمات كاملة على البسيط
    //  (يمنع رفض MyVipManager بسبب "application" داخل الحزمة)
    // =========================================================================
    public static boolean isForbidden(String className) {
        if (className == null || className.isEmpty()) return true;

        // 1) بادئة على الاسم الكامل
        for (int i = 0; i < FORBIDDEN_PREFIXES.length; i++) {
            if (className.startsWith(FORBIDDEN_PREFIXES[i])) return true;
        }

        // 2) رموز على الاسم البسيط (بلا حزمة، بلا داخلي $)
        int lastDot = className.lastIndexOf('.');
        String simple = (lastDot < 0) ? className : className.substring(lastDot + 1);
        int dollar = simple.indexOf('$');
        String outer = (dollar > 0) ? simple.substring(0, dollar) : simple;

        return hasForbiddenToken(outer);
    }

    private static boolean hasForbiddenToken(String simple) {
        if (simple == null || simple.isEmpty()) return false;
        String[] parts = splitCamel(simple);
        for (int i = 0; i < parts.length; i++) {
            String pl = parts[i].toLowerCase(Locale.ROOT);
            for (int j = 0; j < FORBIDDEN_TOKENS.length; j++) {
                if (pl.equals(FORBIDDEN_TOKENS[j])) return true;
            }
        }
        return false;
    }

    /** ApplicationManager → [Application, Manager] */
    private static String[] splitCamel(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (i > 0 && Character.isUpperCase(c) && Character.isLowerCase(s.charAt(i - 1))) {
                sb.append(' ');
            }
            sb.append(c);
        }
        return sb.toString().split("\\s+");
    }

    public static boolean isVipName(String n) {
        if (n == null) return false;
        String l = n.toLowerCase(Locale.ROOT);
        for (String s : VIP_FINGERPRINTS) if (l.contains(s)) return true;
        return false;
    }

    public static boolean isVipKey(String key) {
        if (key == null) return false;
        String norm = key.toLowerCase(Locale.ROOT).replace("_", "");
        for (String ex : DATA_EXCLUDE_HINTS) if (norm.contains(ex)) return false;
        for (String s : VIP_KEY_HINTS) if (norm.contains(s)) return true;
        return false;
    }

    public static boolean isExpiryName(String n) {
        if (n == null) return false;
        String l = n.toLowerCase(Locale.ROOT);
        return l.contains("expire") || l.contains("expiry")
                || l.contains("validuntil") || l.contains("endtime");
    }

    private Config() {}
}
