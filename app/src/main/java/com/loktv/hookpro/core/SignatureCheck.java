package com.loktv.hookpro.core;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;

import java.security.MessageDigest;

/** تقرير تشخيصي لتوقيع/إصدار التطبيق الهدف — يُسهّل الصيانة لكل إصدار. */
public final class SignatureCheck {

    @SuppressWarnings("deprecation")
    public static void report(Context ctx) {
        if (ctx == null) return;
        Safe.run("SignatureCheck", new Safe.Block() {
            @Override public void run() throws Throwable {
                PackageManager pm = ctx.getPackageManager();
                String pkg = ctx.getPackageName();
                PackageInfo pi = pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES);
                StringBuilder sb = new StringBuilder();
                sb.append("target=").append(pkg).append('\n');
                sb.append("versionName=").append(pi.versionName).append('\n');
                sb.append("versionCode=").append(pi.versionCode).append('\n');
                String inst = "unknown";
                try {
                    inst = pm.getInstallerPackageName(pkg);
                } catch (Throwable ignored) {}
                sb.append("installer=").append(inst).append('\n');

                Signature[] sigs = pi.signatures;
                sb.append("certs=").append(sigs == null ? 0 : sigs.length).append('\n');
                if (sigs != null) {
                    MessageDigest md = MessageDigest.getInstance("SHA-256");
                    for (int i = 0; i < sigs.length; i++) {
                        byte[] h = md.digest(sigs[i].toByteArray());
                        StringBuilder hx = new StringBuilder();
                        for (int j = 0; j < h.length; j++) {
                            hx.append(String.format("%02x", h[j]));
                        }
                        sb.append("cert[").append(i).append("].sha256=").append(hx).append('\n');
                    }
                }
                XLog.i("signature report start for " + pkg);
                XLog.i(sb.toString());
            }
        });
    }

    private SignatureCheck() {}
}
