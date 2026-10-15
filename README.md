# LokTvHookPro v4.1.0

> `LokTvHookPro 4.1.0 — بصمة+انعكاس، آمن، API26-35، روت/بدون روت`

## بطاقة الموديول

| البند | القيمة |
|---|---|
| الاسم | LokTvHookPro |
| الإصدار | 4.1.0 (versionCode 410) |
| الحزمة | `com.loktv.hookpro` |
| أندرويد | API 26 (8.0) → API 35 (15) |
| الدخول | `com.loktv.hookpro.HookEntry` |
| روت | LSPosed / EdXposed |
| بدون روت | NPatch / LSPatch / HKPatcher |

## المخرجات الجاهزة

| الملف | الوصف |
|---|---|
| `dist/LokTvHookPro-v4.1.0.apk` | الموديول موقّعاً (v1+v2+v3) — ثبّته مباشرة |
| `dist/LokTvHookPro-v4.1.0-src.zip` | السورس كود الكامل — يُبنى بـ `./build.sh` |
| `docs/ANALYSIS_LokTvHookPro_v4.1.0.md` | ملف التحليل والنتائج (المرجع الدائم — يغني عن إعادة التحليل) |

## البناء من السورس (أمر واحد)

```bash
./build.sh
# الناتج: dist/LokTvHookPro-v4.1.0.apk
```

المتطلبات: JDK 17 + Android SDK (يقرأ `ANDROID_HOME`) + إنترنت أول مرة فقط.
كلمة سر المفتاح الافتراضية `loktv2026` (غيّرها عبر `LOKTV_KEY_PASS=... ./build.sh`) —
**احتفظ بملف `keystore/` للتحديثات** (نفس التوقيع شرط للتحديث فوق النسخة).

## التثبيت

### بدون روت (NPatch / LSPatch / HKP)
1. ثبّت APK الموديول وافتحه مرة.
2. فعّل الموديول في المدير.
3. Local patch ← APK الهدف ← Patch ← Install.
4. احذف نسخة الهدف الأصلية (التوقيع مختلف).

### روت (LSPosed)
1. ثبّت APK الموديول.
2. فعّله في LSPosed وعلّم **أي** حزمة هدف (لا قيد على الاسم).
3. Force stop للهدف ثم افتحه.

## كيف يعمل (باختصار)

- **بلا أسماء ثابتة**: `lpparam.classLoader` + انعكاس + بصمة
  (اسم الصنف + توقيع الدالة: boolean/int/String/void) + جرد DEX من جدول السلاسل.
- **مضغوط/محمي**: جامع لوادر من 5 مصادر + سلسلة تفعيل (2ث→31ث) تلتقط الأصناف بعد فك الضغط.
- **بلا كراش**: فلتر بنية تحتية (`pairip`/`fancybypass`/...) + كل شيء داخل `Safe.run` +
  حدود عدد/زمن + kill-switch (`<cacheDir>/loktv_hook_safe.flag`).
- **قابل للصيانة**: كل الإعدادات في `core/Config.java` — انظر `docs/ANALYSIS_*` للتفاصيل.

## التحقق من البناء

```bash
apksigner verify --print-certs dist/LokTvHookPro-v4.1.0.apk
jarsigner -verify dist/LokTvHookPro-v4.1.0.apk   # v1/JAR
```
