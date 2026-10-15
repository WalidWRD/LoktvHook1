package com.loktv.hookpro.core;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * يجرد أسماء الأصناف من جدول سلاسل DEX مباشرة من ملف APK.
 * لا يستخدم الانعكاس إطلاقًا → لا يفجّر تحميل الأصناف ولا يوقظ حماية jiagu.
 */
public final class DexNames {

    public static Set<String> collect(File apk) {
        Set<String> out = new HashSet<String>(8192);
        if (apk == null || !apk.isFile()) return out;
        ZipFile zf = null;
        try {
            zf = new ZipFile(apk);
            Enumeration<? extends ZipEntry> it = zf.entries();
            while (it.hasMoreElements()) {
                ZipEntry e = it.nextElement();
                String n = e.getName();
                if (!n.startsWith("classes") || !n.endsWith(".dex")) continue;
                if (e.getSize() > 64L * 1024 * 1024) continue;
                InputStream in = null;
                try {
                    in = zf.getInputStream(e);
                    byte[] dex = readAll(in);
                    scan(dex, out);
                } catch (Throwable t) {
                    XLog.w("DexNames.entry: " + t);
                } finally {
                    if (in != null) {
                        try { in.close(); } catch (Throwable ignored) {}
                    }
                }
            }
        } catch (Throwable t) {
            XLog.w("DexNames: " + t);
        } finally {
            if (zf != null) {
                try { zf.close(); } catch (Throwable ignored) {}
            }
        }
        return out;
    }

    private static void scan(byte[] dex, Set<String> out) {
        try {
            ByteBuffer b = ByteBuffer.wrap(dex).order(ByteOrder.LITTLE_ENDIAN);
            if (b.limit() < 0x70) return;
            if (b.getInt(0) != 0x0A786564) return; // "dex\n"
            int stringsSize = b.getInt(0x38);
            int stringsOff  = b.getInt(0x3C);
            if (stringsSize <= 0 || stringsSize > Config.MAX_DEX_STRING_SIZE) return;
            if (stringsOff <= 0 || stringsOff + stringsSize * 4 > b.limit()) return;

            for (int i = 0; i < stringsSize; i++) {
                int off = b.getInt(stringsOff + i * 4);
                if (off <= 0 || off >= b.limit()) continue;
                int p = off;
                int len = uleb128(b, p);
                if (len < 0 || len > 4096) continue;
                p += ulebSize(b, off);
                if (p + len > b.limit()) continue;
                String s = readMutf8(b, p, len);
                if (s.length() >= 6 && s.charAt(0) == 'L' && s.endsWith(";")) {
                    out.add(s.substring(1, s.length() - 1).replace('/', '.'));
                }
            }
        } catch (Throwable t) {
            XLog.w("DexNames.scan: " + t);
        }
    }

    private static int uleb128(ByteBuffer b, int off) {
        int result = 0, shift = 0;
        for (int i = 0; i < 5; i++) {
            int x = b.get(off + i) & 0xFF;
            result |= (x & 0x7F) << shift;
            if ((x & 0x80) == 0) return result;
            shift += 7;
        }
        return -1;
    }

    private static int ulebSize(ByteBuffer b, int off) {
        int sz = 1;
        for (int i = 0; i < 5; i++) {
            int x = b.get(off + i) & 0xFF;
            if ((x & 0x80) == 0) break;
            sz++;
        }
        return sz;
    }

    private static String readMutf8(ByteBuffer b, int off, int len) {
        char[] chars = new char[len];
        int ci = 0, bi = off;
        for (int i = 0; i < len; i++) {
            int a = b.get(bi++) & 0xFF;
            if (a < 0x80) {
                chars[ci++] = (char) a;
            } else if ((a & 0xE0) == 0xC0) {
                int c = b.get(bi++) & 0xFF;
                chars[ci++] = (char) (((a & 0x1F) << 6) | (c & 0x3F));
            } else {
                int c = b.get(bi++) & 0xFF;
                int d = b.get(bi++) & 0xFF;
                chars[ci++] = (char) (((a & 0x0F) << 12) | ((c & 0x3F) << 6) | (d & 0x3F));
            }
        }
        return new String(chars, 0, ci);
    }

    private static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream bo = new ByteArrayOutputStream(1 << 20);
        byte[] buf = new byte[64 * 1024];
        int n;
        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        return bo.toByteArray();
    }

    private DexNames() {}
}
