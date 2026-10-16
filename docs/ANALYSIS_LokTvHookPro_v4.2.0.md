# LokTvHookPro v4.2.0 — ملف التحليل والنتائج (مرجع دائم، مكمّل لملف v4.1.0)

> التاريخ: 2026-10-16 | الأساس: `docs/ANALYSIS_LokTvHookPro_v4.1.0.md` (كل ما فيه ما زال سارياً).
> هذا الملف يوثّق فقط دلتا v4.2.0 — لا تُعِد التحليل، عدّل `core/Config.java` فقط.

## 0) بطاقة الإصدار

| البند | القيمة |
|---|---|
| الإصدار | **4.2.0** (versionCode **420**) |
| الحزمة | `com.loktv.hookpro` (بلا تغيير) |
| أندرويد | API 26 → 35 (بلا تغيير) |
| المخرجات | `dist/LokTvHookPro-v4.2.0.apk` + `dist/LokTvHookPro-v4.2.0-src.zip` |
| ملفات v4.1.0 | باقية في `dist/` كما هي |

## 1) الجديد في v4.2.0 (الخيارات أ + ب + ج)

### (أ) طبقة Bundle/Intent extras — `UnlockEngine.hookBundleIntent()`
- `android.os.Bundle`: `getBoolean` (×2 overloads)، `getInt` (×2)، `containsKey`.
- `android.content.Intent`: `getBooleanExtra`، `getIntExtra`، `hasExtra`.
- نفس سياسة prefs: مفتاح VIP ⇒ `true` / `99`، ومفاتيح الإحصائيات مستثناة عبر
  `isVipExtra()` (تطبيع lower + إزالة `_` + فلتر `ANALYTICS_KEY_DENY`).
- تُربط مرة واحدة (علم `bundleHooked`) + مفتاح `f_bundle`.

### (ب) بصمة Native — `core/NativeScan.java` (تشخيصية + مراقبة فقط)
- `report(apk)`: جرد `lib/*.so` من الـ APK (قراءة Zip فقط، بلا تحميل) ومطابقة
  الاسم الأساسي ببصمات VIP/Detect — يُظهر أين ننظر عند الصيانة.
  حدود: 4000 مدخل، طباعة أول 20 match.
- `installLoadSpies()`: مراقبة `System.loadLibrary` + `System.load` بـ `after`
  فقط (observe-only — لا نمنع أي مكتبة أبداً) + مفتاح `f_native`.
- Xposed لا يخطَف native مباشرة — أي تجاوز native مستقبلاً يكون عبر
  توسيع هذه الطبقة تشخيصياً أولاً.

### (ج) واجهة إعدادات تفاعلية — `ui/SettingsActivity.java` + `core/FeatureFlags.java`
- 7 مفاتيح CheckBox: prefs / json / text / bundle / vip(A+B) / detect / native.
- التخزين: `loktv_hook_prefs` داخل تطبيق الموديول + محاولة chmod مقروء
  (تنجح مع روت، harmless بدونه).
- القراءة من الهوك عبر `XSharedPreferences` داخل `Safe` — عند التعذّر
  (وضع بدون روت غالباً) تبقى الافتراضات **الكل مفعّل** ويُسجَّل ذلك في اللوج.
- سلوك المفاتيح: تُقرأ في بداية كل تمريرة (`FeatureFlags.load()` مرة واحدة)،
  وتُطبَّق من التمريرة التالية — لا حاجة لإعادة بناء.

## 2) سطر SUMMARY الجديد
```
SUMMARY hooks=... classes=... targeted=... activated=true
 features=vip-gate/tier/expiry/fields/prefs-json-text-bundle+native-log | LokTvHookPro v4.2.0
```

## 3) التحقق (2026-10-16)
- `apksigner verify`: v2+v3 true (v1/JAR مؤكد عبر `jarsigner: jar verified`).
- `aapt2 badging`: `versionCode=420`, `versionName=4.2.0`, `minSdk=26`, `target=35`.
- الـ DEX يحوي: `FeatureFlags` (+inner)، `NativeScan` (+3 inners)،
  `SettingsActivity` (+listener)، `xposed_init` سليم.

## 4) تنبيه توقيع مهم
- مفتاح التوقيع تولّد محلياً من جديد في بيئة البناء هذه (SHA-256 يبدأ بـ `7cb65d...`)،
  وهو **مختلف عن مفتاح نسخة v4.1.0** المبنية سابقاً.
- **عند الترقية من 4.1.0 → 4.2.0: احذف النسخة القديمة أولاً ثم ثبّت الجديدة**
  (توقيع مختلف = تثبيت فوق القديمة سيفشل).
- للمستقبل: ثبّت `keystore/` واحداً واحتفظ به لكل الإصدارات القادمة.

## 5) الصيانة (إضافات v4.2.0)
| التغيير | أين |
|---|---|
| مفتاح Bundle/Intent جديد | يُغطى تلقائياً عبر `VIP_KEY_HINTS` — لا كود جديد |
| استثناء extra من الإجبار | صف في `ANALYTICS_KEY_DENY` |
| ميزة جديدة قابلة للتبديل | مفتاح في `FeatureFlags` + CheckBox في `SettingsActivity` + شرط في `UnlockEngine` |
| مكتبة native مشبوهة ظهرت في اللوج | ابحث اسمها في `native vip-lib/detect-lib` ثم قرر: جافا-wrapper (بصمة موجودة) أم native خالص (يحتاج بحثاً ميدانياً) |
