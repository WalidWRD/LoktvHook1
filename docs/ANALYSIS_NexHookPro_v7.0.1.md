# NexHookPro v7.0.1 — ملف التحليل والنتائج (مرجع دائم)

> التاريخ: 2026-10-16 | الهدف: أي تطبيق — بلا حزم ثابتة.
> يكمّل `ANALYSIS_NexHookPro_v7.0.0.md` (الأساس) — هذا الملف يوثّق دلتا v7.0.1 فقط.

## 0) بطاقة الإصدار

| البند | القيمة |
|---|---|
| الإصدار | **7.0.1** (versionCode **701**) |
| الحزمة | `com.nexhook.pro` (بلا تغيير) |
| أندرويد | API 25 → 35 (بلا تغيير) |
| التغيير الجوهري | **حذف كل هوك ClassLoader** — التنشيط بعد `Application.onCreate` |
| التوقيع | **نفس شهادة v7.0.0** — ترقية مباشرة فوقها |
| المخرجات | `dist/NexHookPro-v7.0.1.apk` (v1+v2+v3) + `dist/NexHookPro-v7.0.1-src.zip` |

## 1) جدول الإصلاحات v7.0.0 → v7.0.1 (متحقق في الكود)

| # | الخطأ في v7.0.0 | الإصلاح في v7.0.1 | الملف |
|---|---|---|---|
| 1 | هوك PathClassLoader/ActivityThread → انهيار androidx.startup في installContentProviders | حذف كامل — صفر هوك على أي ClassLoader (فحص DEX: صفر `BaseDexClassLoader`) | LoaderCollector (أُعيدت كتابته: register/tryArm فقط) |
| 2 | التنشيط في attachBaseContext (مبكر، داخل bind) | التأجيل حتى `Application.onCreate` (بعد installContentProviders) + fallback أول Activity + timeout 8s | UnlockEngine / ModernEngine |
| 3 | `Ctx.set()` يستدعي getApplicationContext() (خطر reentrancy في attach) | `Ctx.setRaw()` خام في مسار attach | Ctx |
| 4 | التنشيط على main thread (يعطّل الإقلاع) | Thread منفصل `nexhook-activate` (+ `nexhook-activate-modern`) | UnlockEngine / ModernEngine |
| 5 | لا fallback عند فوات onCreate | أول Activity يُفعّل إن لم يسبقه أحد | UnlockEngine / ModernEngine |
| 6 | timeout 4s | timeout 8s (`ACTIVATION_TIMEOUT_MS`) + tick 60×250ms | Config / MainHook |
| 7 | جرد wrapper المشفر فقط | `resolveApk`: sourceDir ثم `base.apk` بنفس المجلد ثم publicSourceDir | UnlockEngine / ModernEngine |

## 2) سلسلة التنشيط الآمنة الجديدة

```
attachBaseContext → Ctx.setRaw()          [تخزين فقط، ~5ms، لا activate]
↓
installContentProviders (النظام — لا تدخل إطلاقاً)
↓
Application.onCreate → tryActivate() → Thread "nexhook-activate" → activate()
↓ (إن فات)
Activity.onCreate (fallback, إن لم يُفعَّل بعد)
↓ (حل أخير)
timeout 8s عبر nexhook-tick (60×250ms)
```

## 3) ما حُوفظ عليه عمداً (انحرافات عن المسودة بأسباب)

| البند | القرار | السبب |
|---|---|---|
| قراءة `getClassLoader()` من onCreate/Activity | **محفوظة** كاحتياط forName | ليست هوكاً على ClassLoader — قراءة فقط عند فشل الأساسي (stub)؛ تحفظ تغطية المضغوط |
| `ModernHook` + `ModernEngine` (إرث v6) | **محفوظان** بنفس قواعد v7.0.1 | صفر كلفة عند عدم الاستخدام؛ نفس السلامة (لا CL-hooks، تفعيل واحد، thread منفصل) |
| حارس SDK (25+) في MainHook | محفوظ | لا يغيّر سلوكاً — يتجاهل نظيفاً فقط |
| `XposedBridge.log` في XLog | محفوظ | بدونه لا يظهر شيء في سجل LSPosed |
| بناء Gradle | محفوظ | نفس خطوط المسودة اليدوية لكن متحقق منه فعلياً (v1+v2+v3) |

## 4) نتائج التحقق (2026-10-16، فعلياً)

| فحص المسودة | النتيجة |
|---|---|
| build.sh بلا أخطاء | BUILD SUCCESSFUL من أول مرة |
| apksigner v1/v2/v3 | v2+v3=true + `jar verified` (v1) |
| classes.dex + xposed_init=MainHook + hook_config.json + manifest | كلها حاضرة |
| صفر `BaseDexClassLoader` في الكود | **0** (فحص DEX) |
| صفر هوك loadLibrary | الحارس + نص UI فقط، صفر مواقع هوك |
| صفر `load spies` / `activation pass` / `libmmkv` / `LoaderCollector failed` | **0** |
| صفر بقايا loktv | **0** |
| نفس شهادة v7.0.0 | متطابقة (`af49c0...`) — ترقية مباشرة |
| manifest metas السبعة | xposed×4 + libxposed×3 حاضرة |
| badging | 701 / 7.0.1 / min25 / target35 |

سجل الإقلاع المتوقع (للمقارنة الميدانية):
```
zygote | modulePath=... | v=7.0.1
target | pkg=<any> | v=7.0.1
passive | pkg=<any>
ctx stored
Application.onCreate reached — activating
=== activate | ctx=true ===
signature: pkg=... vc=... vn=... sha256=...
inventory apk=/data/app/.../base.apk
inventory names=<N>
prefs-layer / json-layer / bundle-layer / text-layer ... hooked
SUMMARY | hooks=... | tierA=... tierB=... | v=7.0.1
```

## 5) الصيانة
`core/Config.java` فقط + `./build.sh`. ثم قارن سطر SUMMARY و`inventory apk=` (يكشف wrapper مقابل base).
⚠️ المحظورات الدائمة: أي هوك ClassLoader، أي هوك loadLibrary، أسماء حزم حماية في الأهداف،
`getApplicationContext()` داخل attach، تنشيط قبل onCreate.

## 6) حدود معلنة
- فحوص native خالصة (stamp-cert-sha256) خارج قدرة أي موديول جافا.
- المسار الحديث بلا اسم عملية (يعمل على أي عملية — التكرار ممنوع داخلياً).
- الأرقام الميدانية (SUMMARY لكل هدف) تحتاج جهازاً.
