# NexHookPro v7.0.2

موديول Xposed احترافي — يعمل على **أي تطبيق** بلا أسماء حزم ثابتة.

> `NexHookPro v7.0.2 | Universal VIP unlock | reflection+fingerprint | API25-35 | safe | root & non-root`

## بطاقة الموديول

| البند | القيمة |
|---|---|
| الاسم | NexHookPro |
| الإصدار | 7.0.2 (versionCode 702) |
| الحزمة | `com.nexhook.pro` |
| أندرويد | API 25 (7.1) → API 35 (15) |
| libxposed | 101 → 102 (مدخل حديث + meta) |
| نقطة الدخول | `com.nexhook.pro.MainHook` |
| روت | LSPosed / EdXposed |
| بدون روت | NPatch / LSPatch / HKPatcher |

## الجديد في v7.0.2 (إصلاح `hooks=0`)

- **جرد متعدد المصادر**: sourceDir + publicSourceDir + splits + مجلدات شقيقة
  (الأصناف الحقيقية غالباً في split APKs).
- **فلتر word-aware**: تقسيم CamelCase ومطابقة كلمات كاملة
  (لا رفض زائد لأصناف المضيف الحقيقية).
- **تشخيص موسّع**: `inventory host=<N>` + `vip-sample` — تعرف فوراً أين تقف.

## المخرجات الجاهزة

| الملف | الوصف |
|---|---|
| `dist/NexHookPro-v7.0.2.apk` | الموديول موقّعاً (v1+v2+v3) — ترقية مباشرة فوق v7.0.x |
| `dist/NexHookPro-v7.0.2-src.zip` | السورس كود الكامل مع العدسات — يُبنى بـ `./build.sh` |
| `docs/ANALYSIS_NexHookPro_v7.0.2.md` | ملف التحليل والنتائج (مع ملفي v7.0.x) |
| `CHANGELOG.md` | سجل الإصدارات الكامل |

## التثبيت

### بدون روت (NPatch / LSPatch / HKP)
1. ثبّت `NexHookPro-v7.0.2.apk`
2. افتحه مرة
3. في المدير: Modules ← فعّل NexHookPro
4. اختر APK الهدف ← Patch ← Install

### بروت (LSPosed)
1. ثبّت الموديول (ترقية مباشرة)
2. فعّله في LSPosed
3. علّم **أي** حزمة في النطاق
4. أعد تشغيل التطبيق الهدف (تحقق سلوكياً من VIP)

## السجل والطوارئ والتشخيص

- السجل: `<cacheDir>/nexhook.log` (+ logcat وسجل LSPosed)
- الطوارئ: `<cacheDir>/nexhook_safe.flag`
- التشخيص: `inventory host=<H>` (المطلوب > 100) و`vip-sample` ثم سطر SUMMARY

## البناء

```bash
export ANDROID_HOME=/path/to/sdk
./build.sh
# الناتج:
#   dist/NexHookPro-v7.0.2.apk
#   dist/NexHookPro-v7.0.2-src.zip
```

كلمة سر المفتاح `nexhook2026` (عبر `NEXHOOK_KEY_PASS=...`) — **احتفظ بـ `keystore/`**.

## الصيانة

`src/main/java/com/nexhook/pro/core/Config.java` فقط — التفاصيل في `docs/ANALYSIS_NexHookPro_v7.0.2.md`.
