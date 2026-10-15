# LokTvHookPro v4.1.0 — ملف التحليل والنتائج (مرجع دائم)

> التاريخ: 2026-10-15 | الهدف: عائلة LokTV (`com.hecate.*`, `com.novan.*`, `com.looktv.*`) **وأي حزمة أخرى**.
> هذا الملف هو المرجع الوحيد المعتمد — لا تُعِد التحليل عند تحديث الهوست، عدّل `core/Config.java` فقط.

## 0) بطاقة الموديول

| البند | القيمة |
|---|---|
| الاسم | LokTvHookPro |
| الإصدار | **4.1.0** (versionCode **410**) |
| الحزمة | `com.loktv.hookpro` |
| دعم أندرويد | **API 26 (8.0) → API 35 (15)** — الأقل من 26 يُتجاهل نظيفاً، والأعلى يُتحمَّل |
| الأُطر | LSPosed / EdXposed (**روت**) — NPatch / LSPatch / HKP (**بدون روت**) |
| نقطة الدخول | `com.loktv.hookpro.HookEntry` (`assets/xposed_init`) |
| الاعتماديات | `de.robv.android.xposed:api:82` فقط (`compileOnly` — من `https://api.xposed.info/`) |
| المنهجية | `lpparam.classLoader` + انعكاس + بصمة (اسم الصنف + توقيع الدالة) + جدول سلاسل DEX |
| الحماية من الكراش | مرحلتان + فلتر بنية تحتية + `Safe.run` + kill-switch + حدود عدد/زمن |
| الوصف المدمج | `LokTvHookPro 4.1.0 — بصمة+انعكاس، آمن، API26-35، روت/بدون روت` |
| المخرجات | `dist/LokTvHookPro-v4.1.0.apk` (موقّع v1+v2+v3) + `dist/LokTvHookPro-v4.1.0-src.zip` |
| البناء | أمر واحد: `./build.sh` |

## 1) البيئة المرصودة

| العنصر | القيمة |
|---|---|
| المدير | HKP 2.0-280 |
| أندرويد | 11 (SDK 30) |
| الحزمة | `com.hecate.phantom` 1.0.2 (4) |
| الحماية | `com.pairip.licensecheck` + `fancybypass` |

## 2) الجذور التي أُصلحت (لا تعيد اكتشافها)

1. **انهيار v3.4.0**: هوك `fancybypass` + `pairip` أثناء `handleBindApplication`
   ⇒ `ExceptionInInitializerError`.
   الحل: فلتر بنية تحتية صارم (`Config.isInfrastructure`) + تأجيل التنشيط خارج مرحلة الربط.
2. **صفر هوك في v2.x**: `lpparam.classLoader` وحده هو Stub في التطبيقات المضغوطة.
   الحل: `LoaderCollector` بـ **5 مصادر** (attachBaseContext / onCreate / Activity.spy /
   PathClassLoader / InMemoryDexClassLoader + ActivityThread fallback).
3. **فقدان أصناف hecate**: كانت خارج `TARGET_PACKAGES`.
   الحل: `TARGET_ALL_PACKAGES=true` + فلتر infra (يعمل مع **أي حزمة** بلا تكرار).
4. **عدم التقاط دالات void**: البصمة على نوع الإرجاع وحده تفوّتها.
   الحل: `forceFieldsAfter()` — إجبار الحقول المنطقية بعد تنفيذ دالة `void` التي اسمها VIP.
5. **أصناف مشوّشة (رموز يونيكود)**: البصمة على اسم *الدالة* تفشل.
   الحل: البصمة على اسم *الصنف* + توقيع الدالة (boolean/int/String/void).

## 3) النتائج الميدانية المرجعية (v4.1.0)

| المؤشر | القيمة |
|---|---|
| hooks | 255 |
| classes | 657 |
| targeted | 91 |
| skipped | 0 |
| activated | true |
| features | vip-gate/tier/expiry/fields/prefs-json/textview |

> تُطبع هذه الأرقام تلقائياً في اللوج (`SUMMARY ...`) عند كل تشغيل — قارنها عند تحديث الهوست.

## 4) البنية (كل ملف ودوره)

```
app/src/main/
├── AndroidManifest.xml      xposedmodule/description/minversion/scope + SettingsActivity
├── assets/xposed_init       com.loktv.hookpro.HookEntry
├── res/values/strings.xml   الوصف المدمج (مدير الموديولات + اللوج)
├── res/values/arrays.xml    النطاق المقترح xposed_scope (المستخدم يعلّم أي حزمة)
└── java/com/loktv/hookpro/
    ├── HookEntry.java        نقطة دخول — مرحلتان + سلسلة تفعيل + kill-switch
    ├── core/Config.java      ★ كل الإعدادات هنا — الصيانة تبدأ وتنتهي هنا
    ├── core/UnlockEngine.java محرك الربط بالبصمة (VIP درجة A/B + تعطيل الكشف)
    ├── core/DexNames.java    جرد الأصناف من جدول سلاسل DEX بلا انعكاس
    ├── core/LoaderCollector.java جامع اللوادر من 5 مصادر (للمضغوط/المحمي)
    ├── core/Safe.java        تنفيذ محمي: أي استثناء = لوج فقط
    ├── core/XLog.java        لوج موحد (XposedBridge + logcat + ملف)
    ├── core/Reflect.java     انعكاس آمن عبر الوراثة
    ├── core/Ctx.java         سياق الهدف (يُملأ مع فك الضغط)
    ├── core/SignatureCheck.java تقرير توقيع/إصدار الهدف (تشخيص كل إصدار)
    └── ui/SettingsActivity.java شاشة الحالة + عرض اللوج
```

## 5) قواعد السلامة المطلقة (ممنوع كسرها عند التطوير)

- لا هوك على: `interface`, `abstract`, `native`, `<init>`, `<clinit>`.
- لا هوك على: `toString`, `hashCode`, `equals`, `getClass`, `clone`,
  `wait`, `notify`, `notifyAll`, `finalize`.
- لا هوك على أصناف تطابق فلتر infra:
  `pairip`, `fancybypass`, `stub`, `secneo`, `jiagu`, `nqshield`, `pangle`,
  `lsposed`, `android.*`, `androidx.*`, `com.google.*` ...
- أي كود جديد داخل `Safe.run` حصراً.
- kill-switch: وجود `<cacheDir>/loktv_hook_safe.flag` في الهدف = إيقاف فوري.
- الحدود: `MAX_HOOKS=900`، `MAX_HOOKS_PER_CLASS=60`، `SCAN_BUDGET=3000ms`.
- طبقات البيانات (prefs/json/text) تُربط **مرة واحدة** (أعلام `*Hooked`).

## 6) الصيانة (دون إعادة تحليل)

| التغيير المطلوب | أين |
|---|---|
| إضافة صنف/عائلة جديدة | صف في `VIP_FINGERPRINTS` أو `DETECT_FINGERPRINTS` |
| إضافة مفتاح prefs/JSON | صف في `VIP_KEY_HINTS` |
| استثناء مفتاح إحصائيات | صف في `ANALYTICS_KEY_DENY` |
| إضافة نص واجهة | صف في `TEXT_REPLACEMENTS` |
| تغيير التأخيرات/الحدود | `ACTIVATION_DELAYS_MS` / `MAX_*` |
| بناء نسخة جديدة | حدّث `VERSION_NAME/VERSION_CODE` في `app/build.gradle` + `Config` ثم `./build.sh` |

## 7) التثبيت

### بدون روت (NPatch / LSPatch / HKP)
1. ثبّت `LokTvHookPro-v4.1.0.apk` وافتحه مرة (شاشة التشخيص).
2. فعّل الموديول في المدير.
3. Local patch ← اختر APK الهدف ← Patch ← Install.
4. احذف النسخة الأصلية من الهدف (التوقيع مختلف).

### روت (LSPosed)
1. ثبّت `LokTvHookPro-v4.1.0.apk`.
2. فعّله في LSPosed.
3. علّم الحزمة الهدف في النطاق (أي حزمة — لا قيد على الاسم).
4. أعد تشغيل الهدف (Force stop ثم فتح).

### الطوارئ
أنشئ ملف `<cacheDir>/loktv_hook_safe.flag` في تطبيق الهدف → يتوقف التفعيل فوراً بلا كراش.

## 8) حدود مؤكدة/غير مؤكدة

**مؤكد (مُتحقق بالبناء والفحص 2026-10-15):**
- البناء والتوقيع (v1 JAR مؤكد عبر `jarsigner` + v2/v3 عبر `apksigner`).
- `xposed_init` + `HookEntry` داخل الـ DEX + بيانات `xposed*` في الـ Manifest.
- `package=com.loktv.hookpro`, `versionCode=410`, `versionName=4.1.0`,
  `minSdk=26`, `target=35`.
- فلتر infra يمنع انهيار `pairip`/`fancybypass` (تصميماً + مراجعة كود).
- جرد DEX بلا انعكاس (لا يوقظ jiagu).

**غير مؤكد (يحتاج جهازاً):**
- فتح كل بوابة VIP في كل إصدار من الهوست (قارن سطر `SUMMARY` في اللوج).
- سلوك API 35 على جهاز حقيقي (الكود متحمّل تصميماً عبر حارس `sdk < 26` فقط).

## 9) التشخيص عند مشكلة في جهاز محدد

1. افتح تطبيق الموديول ← انسخ اللوج (`loktphook.log`).
2. `adb logcat -b crash` + فلتر `LokTvHookPro`.
3. أرسل: سطر `SUMMARY` + تقرير `signature report` + سطر `SHORT_DESC`.
4. الإصلاح غالباً = صف جديد في `Config.java` (بصمة/مفتاح/استثناء).
