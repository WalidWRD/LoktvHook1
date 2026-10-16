package com.nexhook.pro.core;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;

import java.security.MessageDigest;

public final class SignatureCheck {

    private SignatureCheck() {}

    public static void report(Context ctx, String pkg) {
        if (ctx == null) { XLog.d("signature: no ctx"); return; }
        try {
            PackageManager pm = ctx.getPackageManager();
            PackageInfo pi = pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES);
            if (pi == null || pi.signatures == null) return;

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < pi.signatures.length; i++) {
                Signature s = pi.signatures[i];
                sb.append(sha256(s.toByteArray())).append(' ');
            }

            XLog.i("signature: pkg=" + pkg + " vc=" + pi.versionCode
                    + " vn=" + pi.versionName + " sha256=" + sb.toString().trim());
        } catch (Throwable ignored) {}
    }

    private static String sha256(byte[] d) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(d);
            StringBuilder sb = new StringBuilder(h.length * 2);
            for (int i = 0; i < h.length; i++) {
                sb.append(String.format("%02x", h[i]));
            }
            return sb.toString();
        } catch (Throwable t) { return "?"; }
    }
}
