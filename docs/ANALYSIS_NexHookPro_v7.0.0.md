# NexHookPro v7.0.0 — ملف التحليل والنتائج (مرجع دائم)

> التاريخ: 2026-10-16 | الهدف: أي تطبيق — بلا حزم ثابتة، بلا أسماء ثابتة.
> يعتمد على: انعكاس + بصمة + جرد DEX. متوافق مع: LSPosed/EdXposed/NPatch/LSPatch/HKP.
> يغني عن إعادة التحليل: كل الصيانة في `core/Config.java` + هذا الملف.

## 0) بطاقة الموديول

| البند | القيمة |
|---|---|
| الاسم | NexHookPro |
| الإصدار | **7.0.0** (versionCode **700**) |
| الحزمة | `com.nexhook.pro` (هوية جديدة — تطبيق جديد لا ترقية فوق LokTvHookPro) |
| أندرويد | **API 25 (7.1) → API 35 (15)** |
| libxposed | **101 → 102**: مدخل حديث `ModernHook` (API 102 الحقيقية) + meta-data (102/102/101) + مدخل legacy |
| نقطة الدخول | `com.nexhook.pro.MainHook` (legacy api-82 عبر `assets/xposed_init`) |
| السجل | `<cacheDir>/nexhook.log` (+ logcat + سجل LSPosed عبر XposedBridge) |
| مفتاح الطوارئ | `<cacheDir>/nexhook_safe.flag` |
| المخرجات | `dist/NexHookPro-v7.0.0.apk` (v1+v2+v3) + `dist/NexHookPro-v7.0.0-src.zip` |
| التوقيع | مفتاح جديد `keystore/nexhookpro.jks` (alias nexhook) — احتفظ به للتحديثات |

## 1) لماذا نسخة جديدة كاملة

- v4.x: مشروط ببصمات أضيق + طبقات تكاثرت (7 مفاتيح) — يعمل لكن معقد الصيانة.
- v5.0.0 (ميداني): انهيار ClassCastException (Long→Integer) من لمس `fancybypass`.
- v6.0.0 (ميداني): انهيار UnsatisfiedLinkError (libmmkv) من هوك `System.loadLibrary`.
- v7.0.0: **بلا حزم ثابتة، بلا هوك على البنية التحتية، لا loadLibrary إطلاقاً، لا fancybypass**.

## 2) الانهيارات السابقة — الوقاية المطبقة (كلها متحقق منها في الكود)

| # | الانهيار | السبب | الوقاية في v7.0.0 | الدليل |
|---|---|---|---|---|
| 1 | ClassCastException Long→Integer | فرض Long على دالة int في fancybypass | `Safe.coerceReturn` في كل فرض + فلتر infra قبل أي forName | `HookEngine.SafeHook` + كل طبقات `UnlockEngine` |
| 2 | ExceptionInInitializerError | هوك أثناء handleBindApplication | المرحلة السلبية فقط في `handleLoadPackage`؛ النشطة بعد `attach` أو 4s | `MainHook` + `UnlockEngine.passive/tick` |
| 3 | UnsatisfiedLinkError libmmkv | هوك System.loadLibrary | **حذف كامل**: صفر مواقع هوك (تحقق DEX: الحارس + نص UI فقط) | grep السورس + سلاسل DEX |
| 4 | NoSuchMethodError attachBaseContext | الدالة على ContextWrapper لا Application | الهوك على `ContextWrapper` + فلتر `instanceof Application` | `UnlockEngine` + `ModernEngine` + `LoaderCollector` |
| 5 | NoSuchMethodError Bundle#getBoolean | الدالة على BaseBundle | `getBoolean/getInt/containsKey` على `BaseBundle` + `getString` على `Bundle` | `hookBundle` في المحركين |
| 6 | حلقة re-arm / ضجيج السجل | إعادة تسليح نفس loader | `tryArm(identityHashCode)` + `ACTIVATED` مرة واحدة | `LoaderCollector` + `UnlockEngine` |
| 7 | dlopen failed | تدخل في مسار loadLibrary | لا تدخل أصلاً (انظر #3) | — |

## 3) البنية

### المرحلتان
| المرحلة | متى | ماذا |
|---|---|---|
| سلبية | handleLoadPackage | تسجيل loader + جواسيس ContextWrapper/onCreate/Activity (مراقبة فقط) + مؤقت tick |
| نشطة | attach أو 4s، مرة واحدة | توقيع + طوارئ + جرد DEX + هوك A/B + طبقات prefs/json/bundle/text + SUMMARY |

### الاكتشاف (ثلاثة مسارات، بلا أسماء ثابتة)
1. جرد DEX من base.apk (جدول السلاسل، بلا انعكاس) مع فلتر infra أثناء الجرد نفسه.
2. `forName(name, false, loader)` + `allMethods` عبر الوراثة.
3. جواسيس لوادر سلبية (5 مصادر legacy) — المراقبة فقط، الهوك النشط لاحقاً.

### الدرجات
- **A** (اسم VIP صريح): كل boolean/int/long/String آمنة (انتهاء→2100).
- **B** (تلميح ضعيف): فقط دوال اسمها VIP.
- طبقات ثابتة بلا درجات: SharedPreferencesImpl (bool/int/long/string) + JSONObject + BaseBundle/Bundle + TextView (مطابقة تامة).

### السلامة المطلقة
لا interface/abstract/native، لا init/clinit/toString/equals/.../finalize، لا loadLibrary،
لا infra (30+ بادئة، 22 رمزاً)، حدود 900/60/6000/2500ms، كل callback داخل try/catch،
PROTECTIVE في المسار الحديث، طوارئ بملف cache.

## 4) نتائج البناء (2026-10-16، متحقق فعلياً)

| الفحص | القيمة |
|---|---|
| الأدوات | Gradle AGP 8.5.2: aapt2 → javac 8 → d8 (min-api 25) → zipalign → apksigner |
| badging | `com.nexhook.pro`, 700, 7.0.0, minSdk 25, target 35 |
| التوقيع | v2+v3=true عبر apksigner + `jar verified` عبر jarsigner (v1) |
| المحتويات | classes.dex + `assets/xposed_init`=MainHook + `java_init.list`=ModernHook + `hook_config.json` + manifest |
| العدسات | compileOnly فقط (مراجع لا مدمجة) — `app/libs` مُورَّدة في السورس ZIP |
| بقايا loktv | صفر في DEX |
| meta | xposedmodule/description/minversion=82/scope + libxposed 102/api 102/min 101 |

## 5) التثبيت
بدون روت: ثبّت APK ← افتحه ← فعّله في المدير ← Patch الهدف ← Install (احذف أصل الهدف).
بروت: ثبّته ← فعّله في LSPosed ← علّم أي حزمة ← أعد تشغيل الهدف.
الطوارئ: `<cacheDir>/nexhook_safe.flag` في الهدف ثم إعادة تشغيل (زر الشاشة يسري فوراً بدون روت).

## 6) الصيانة (دون إعادة تحليل)
`core/Config.java` فقط: VIP_FINGERPRINTS (درجة A)، FINGERPRINT_CLASS_HINTS (درجة B)،
VIP_KEY_HINTS، TEXT_REPLACEMENTS. ثم `./build.sh`.
⚠️ FORBIDDEN_* لا تُلمس. أي تغيير سياسة يُطبَّق في المحركين (legacy + modern — مرآة مقصودة).
سطر SUMMARY يكشف أي مدخل اختاره المدير (`engine=modern` مقابل بانر legacy).

## 7) انحرافات مقصودة عن المسودة (مع السبب)
1. **Manifest بلا `package`/version/uses-sdk**: ملكية AGP (تكرارها خطأ دمج) — للبناء اليدوي أعد إضافة
   `package="com.nexhook.pro"` + version + uses-sdk (موثق في تعليق أعلى Manifest).
2. **بناء Gradle لا سكربت aapt2 اليدوي**: نفس الخطوط (aapt2→javac→d8→zipalign→apksigner v1+v2+v3)
   لكن متحقق منه فعلياً هنا — اليدوي لم يُختبر.
3. **XposedBridge.log أُعيد إلى XLog** (سطر واحد محمي): بدونه لا يظهر شيء في سجل LSPosed.
4. **مدخل حديث ثانٍ ModernHook** (إرث v6): المسودة meta فقط؛ الإبقاء يوسّع التوافق بلا أي كلفة
   runtime عند عدم استخدامه، وبنفس قواعد v7 كلها.
5. **جواسيس سلبية أوسع** (onCreate/Activity/PathClassLoader): مراقبة فقط، تفيد المضغوط/المحمي.
6. **ANALYSIS في `docs/`** (تقليد المستودع) لا الجذر + `app/libs` مُورَّدة (المسودة: ضعها يدوياً).

## 8) حدود معلنة بصدق
- حماية native خالصة (stamp-cert-sha256 في .so) لا يجاوزها أي موديول جافا — حد مشترك لكل Xposed.
- المسار الحديث بلا اسم عملية (يعمل على أي عملية للحزمة — التكرار ممنوع داخلياً).
- السلوك الميداني (أرقام SUMMARY لكل هدف) يحتاج جهازاً — اللوج يكشف كل شيء للمقارنة.
