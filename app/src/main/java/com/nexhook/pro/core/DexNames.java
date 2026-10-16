package com.nexhook.pro.core;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** جرد أصناف المضيف من جدول سلاسل DEX — بلا انعكاس، متعدد الملفات. */
public final class DexNames {

    private DexNames() {}

    public static Set<String> scanApks(Iterable<File> apks) {
        Set<String> out = new HashSet<String>();
        if (apks == null) return out;
        for (File f : apks) scanApk(f, out);
        return out;
    }

    public static void scanApk(File apk, Set<String> out) {
        if (apk == null || !apk.isFile() || out == null) return;
        int before = out.size();
        ZipFile zf = null;
        try {
            zf = new ZipFile(apk);
            Enumeration<? extends ZipEntry> en = zf.entries();
            int dexCount = 0;
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                if (!e.getName().endsWith(".dex")) continue;
                dexCount++;
                InputStream in = null;
                try {
                    in = zf.getInputStream(e);
                    extractNames(readAll(in), out);
                } catch (Throwable ignored) {
                } finally {
                    if (in != null) {
                        try { in.close(); } catch (Throwable ignored) {}
                    }
                }
            }
            XLog.d("scanApk " + apk.getName() + " dex=" + dexCount
                    + " +names=" + (out.size() - before));
        } catch (Throwable t) {
            XLog.w("scanApk fail " + apk.getName() + ": " + t);
        } finally {
            if (zf != null) {
                try { zf.close(); } catch (Throwable ignored) {}
            }
        }
    }

    private static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(1 << 20);
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        return bos.toByteArray();
    }

    private static void extractNames(byte[] dex, Set<String> out) {
        try {
            if (dex.length < 0x70) return;
            if (dex[0] != 0x64 || dex[1] != 0x65 || dex[2] != 0x78 || dex[3] != 0x0a) return;

            int size = readIntLE(dex, 0x38);
            int off  = readIntLE(dex, 0x3C);
            int end  = off + size * 4;
            if (off <= 0 || end > dex.length) return;

            for (int i = 0; i < size; i++) {
                String s = readMutf8(dex, readIntLE(dex, off + i * 4));
                if (s == null || s.length() < 3) continue;
                if (s.charAt(0) != 'L' || s.charAt(s.length() - 1) != ';') continue;
                String cls = s.substring(1, s.length() - 1).replace('/', '.');
                if (!Config.isForbidden(cls)) out.add(cls);
            }
        } catch (Throwable ignored) {}
    }

    private static int readIntLE(byte[] b, int o) {
        return (b[o] & 0xFF) | ((b[o + 1] & 0xFF) << 8)
             | ((b[o + 2] & 0xFF) << 16) | ((b[o + 3] & 0xFF) << 24);
    }

    private static String readMutf8(byte[] b, int o) {
        try {
            int p = o;
            long len = 0; int shift = 0;
            while (true) {
                int x = b[p++] & 0xFF;
                len |= ((long) (x & 0x7F)) << shift;
                if ((x & 0x80) == 0) break;
                shift += 7;
            }
            if (len <= 0 || len > 4096) return null;

            ByteArrayOutputStream bos = new ByteArrayOutputStream((int) len);
            int produced = 0;
            while (produced < len) {
                int a = b[p++] & 0xFF;
                if (a < 0x80) { bos.write(a); produced++; }
                else if ((a & 0xE0) == 0xC0) {
                    int c = b[p++] & 0xFF;
                    bos.write(((a & 0x1F) << 6) | (c & 0x3F)); produced++;
                } else if ((a & 0xF0) == 0xE0) {
                    int c = b[p++] & 0xFF; int d = b[p++] & 0xFF;
                    bos.write(((a & 0x0F) << 12) | ((c & 0x3F) << 6) | (d & 0x3F)); produced++;
                } else return null;
            }
            return new String(bos.toByteArray(), "UTF-8");
        } catch (Throwable t) { return null; }
    }
}
