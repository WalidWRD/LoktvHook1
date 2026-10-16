package com.nexhook.pro.core;

import java.util.Locale;

/** يحدد درجة الهوك لكل صنف: 1 = VIP صريح، 2 = تلميح ضعيف، 0 = تجاهل. */
public final class Fingerprint {

    private Fingerprint() {}

    public static int classify(String className) {
        if (className == null) return 0;
        String l = className.toLowerCase(Locale.ROOT);
        for (int i = 0; i < Config.VIP_FINGERPRINTS.length; i++) {
            if (l.contains(Config.VIP_FINGERPRINTS[i])) return 1;
        }
        for (int i = 0; i < Config.FINGERPRINT_CLASS_HINTS.length; i++) {
            if (l.contains(Config.FINGERPRINT_CLASS_HINTS[i])) return 2;
        }
        return 0;
    }
}
