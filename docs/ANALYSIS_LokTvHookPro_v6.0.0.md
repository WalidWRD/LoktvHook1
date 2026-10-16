# LokTvHookPro v6.0.0 — ملف التحليل والنتائج (مرجع دائم)

> التاريخ: 2026-10-16 | الأساس: ملفا v4.1.0 وv4.2.0 (كل ما فيهما سارٍ ما لم يُذكر خلافه هنا).
> هذا الملف يوثّق دلتا v6.0.0 + الانحرافات المقصودة عن مسودة المواصفة مع أسبابها.

## 0) بطاقة الإصدار

| البند | القيمة |
|---|---|
| الاسم | LokTvHookPro |
| الإصدار | **6.0.0** (versionCode **600**) |
| الحزمة | `com.loktv.hookpro` (بلا تغيير) |
| أندرويد | **API 21 (5.0) → API 35 (15)** — موسّعة تحت 26 بتسامح + حراس runtime |
| المدخل القديم | `com.loktv.hookpro.HookEntry` ← `assets/xposed_init` (legacy api 82) |
| المدخل الحديث | `com.loktv.hookpro.MainHook` ← `META-INF/xposed/java_init.list` (libxposed **102.0.0**) |
| الاعتماديات | `app/libs/xposed-api-82.jar` + `app/libs/libxposed-api-102.jar` (compileOnly، مُورَّدة محلياً — بناء hermetic) |
| العقد المشحون | `assets/hook_config.json` (v6.0.0 — الحدود والمداخل ومفتاح الطوارئ) |
| التوقيع | **نفس شهادة v4.2.0** (`7cb65d...`) — الترقية فوقها مباشرة بلا حذف |
| المخرجات | `dist/LokTvHookPro-v6.0.0.apk` + `dist/LokTvHookPro-v6.0.0-src.zip` |

## 1) الجديد في v6.0.0

### (أ) مدخل مزدوج MainHook + HookEntry + libxposed 101→102
- `io.github.libxposed:api:102.0.0` حقيقية من Maven Central (AAR → استُخرج `classes.jar` إلى `app/libs`).
- `MainHook extends XposedModule` + `ModernEngine` (~600 سطر) بنفس السياسة تماماً:
  بصمة + انعكاس، فلتر infra، حدود 900/60/3000ms، سلسلة التفعيل نفسها، kill-switch نفسه.
- القرارات مشتركة (Config/DexNames/FeatureFlags/Ctx/LoaderCollector) — ميكانيكا الهوك فقط مختلفة
  (`XposedInterface.hook().setExceptionMode(PROTECTIVE).intercept()` بدل `XposedBridge`).
- `FeatureFlags.loadModern()` تقرأ عبر `getRemotePreferences()` من الفريمورك — تعمل بروت وبدون روت
  (أفضل من XSharedPreferences في المسار القديم).
- حدود معلنة بصدق للمسار الحديث: بلا اسم عملية في `PackageLoadedParam`
  (منع التكرار بأعلام once + tryArm + عدم التكرار بالتوقيع)، ودوال الكشف ذات إرجاع كائن
  تُراقَب فقط بدل تحييدها (أسلم من إرجاع null).

### (ب) HookEngine.java مستخرج (حسب شجرة المواصفة)
- منطق `hookClass` انتقل من UnlockEngine إلى `core/HookEngine.java` بلا تغيير سلوكي، مع:
  جرد الدوال عبر الوراثة (`Reflect.allMethods`)، كل `setResult` عبر `Safe.coerceReturn`
  (يستحيل `ClassCastException` من نوع `Long على int`)، عدم تكرار بتوقيع الدالة.
- UnlockEngine أصبح منسّق طبقات (prefs/json/text/bundle/native + جرد + تفويض).

### (ج) ترقيات المواصفة المدمجة
- `Safe.coerceReturn` + `Reflect.allMethods` + `XLog.v/e(t)/setLogFile` + `LoaderCollector.tryArm`
  (يمنع re-arm عبر التمريرات — حلقة التفعيل أصبحت خارجية/داخلية مزدوجة الحماية).
- فلتر infra مدمج: رموز `fancy/bypass/pairip/licensecheck/licens/neutralis/detection` +
  بادئات `okhttp3/squareup/bumptech/org.json/io.reactivex/crashlytics/apache`.
- بصمات VIP مدمجة (`usertier/viptier/isvip/ispro/ispremium/validuntil/endtime/sku/invoice`) +
  مفاتيح (`unlock/validuntil`) + استثناءات (`tracking/log/debug`) + قواعد TextView إنجليزية.
- قيم الانتهاء: أسماء `expire/expiry/validuntil/endtime` تُفرض بـ **2100** بدل 99 (prefs/JSON/حديث).
- زر مفتاح الطوارئ في شاشة الموديول (يكتب `loktv_hook_safe.flag` في cache — يسري فوراً
  في وضع بدون روت حيث يعمل الكود داخل عملية الهدف).
- `hook_config.json` مشحون داخل الـ APK (العقد: النسخة/الميزات/الحدود/المداخل/الطوارئ).

## 2) انحرافات مقصودة عن مسودة المواصفة (مع السبب)

| المسودة | المنفذ | السبب |
|---|---|---|
| `TARGET_PACKAGES` مغلقة + بوابة `isTargetedPackage` | `TARGET_ALL_PACKAGES=true` باقٍ (أي حزمة) | شرطك الدائم: «بغض النظر عن اسم الحزمة» يعلو على المسودة |
| `Safe.run(Runnable)` بتوقيع جديد | أُبقي التوقيع `run(tag, Block)` وأُضيف `coerceReturn` | التوقيع الجديد كان سيكسر كل مواقع الاستدعاء بلا فائدة سلوكية |
| `UnlockEngine` المسودة (after-hooks + مراقب `loadClass`) | أُبقي محرك before-hooks المجرب + HookEngine | المسار المجرب v4.x يعمل ميدانياً؛ إعادة الكتابة بلا جهاز اختبار مخاطرة غير مبررة. مراقب `loadClass` العام حُذف عمداً (مسار ساخن hot-path) |
| `xposedminversion=82` فقط | أُبقي 82 (التوافق القديم أولاً) | meta واحدة للمدخلين؛ 82 تُرضي كل الأطر، والمدخل الحديث يُكتشف عبر `java_init.list` |
| مفتاح الطوارئ من الشاشة يكفي | الزر + توثيق القيد | في وضع روت-LSPosed الشاشة تعمل في عملية الموديول لا الهدف — الحل: مدير ملفات على cache الهدف (موثق في README) |

## 3) التحقق (2026-10-16)
- `BUILD SUCCESSFUL` من أول محاولة (لا أخطاء تجميع للمسارين).
- `apksigner verify`: **v1=true, v2=true, v3=true** (أول نسخة تحقق v1 صريحاً).
- `jarsigner: jar verified`.
- `badging`: `versionCode=600`, `versionName=6.0.0`, `minSdk=21`, `target=35`.
- الـ APK يحوي: `assets/xposed_init` (HookEntry) + `META-INF/xposed/java_init.list` (MainHook) +
  `assets/hook_config.json` + `classes.dex` (MainHook/ModernEngine/HookEngine/UnlockEngine…).
- إطارا الهوك **مراجع فقط غير مدمجة** (8 سلسلة libxposed + 10 de.robv — لا classes مكررة).
- نفس شهادة v4.2.0 — ترقية مباشرة.

## 4) الصيانة (v6.0.0)
| التغيير | أين |
|---|---|
| سياسة بصمة/مفتاح/قيمة | `core/Config.java` (يُقرأ من المسارين) — ثم حدّث `hook_config.json` يدوياً بنفس القيم |
| منطق ربط (legacy) | `core/HookEngine.java` |
| منطق ربط (modern) | `core/ModernEngine.java` — أي تغيير سياسة طبّقه هنا أيضاً (مرآة مقصودة) |
| مفتاح ميزة جديد | `FeatureFlags` + CheckBox + شرط في المحركين |
| ترقية libxposed | حدّث `app/libs/libxposed-api-102.jar` + رقم `hook_config.json` |
| بناء | `./build.sh` (البناء hermetic: العدسات مُورَّدة، الشبكة للـ AGP فقط) |

## 5) غير مؤكد (يحتاج جهازاً — كالمعتاد)
- سلوك المسار الحديث على LSPosed فعلياً (الكود مترجم ومتحقق منه، غير مُشغَّل جهازياً).
- أي مدخل يختاره كل مدير (LSPatch/NPatch/HKP) عند وجود المدخلين — اللوج يكشف (`engine=modern` مقابل بانر legacy).
- فتح كل بوابة VIP لكل إصدار (قارن سطر `SUMMARY`).
