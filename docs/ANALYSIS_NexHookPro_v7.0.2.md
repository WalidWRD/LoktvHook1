# NexHookPro v7.0.2 — ملف التحليل والنتائج (مرجع دائم)

> التاريخ: 2026-10-16 | يكمّل ملفي v7.0.0 وv7.0.1 — هنا الدلتا فقط.

## 0) بطاقة الإصدار

| البند | القيمة |
|---|---|
| الإصدار | **7.0.2** (versionCode **702**) |
| الحزمة / النطاق | `com.nexhook.pro` / API 25 → 35 (بلا تغيير) |
| الجديد | جرد متعدد المصادر + فلتر word-aware + تشخيص موسّع |
| التوقيع | **نفس شهادة v7.0.x** — ترقية مباشرة |
| المخرجات | `dist/NexHookPro-v7.0.2.apk` (v1+v2+v3) + `dist/NexHookPro-v7.0.2-src.zip` |

## 1) مشكلة v7.0.1 التي يحلها v7.0.2: `hooks=0`

سببان متكاملان:

**أ. الأصناف الحقيقية في split APKs.** الجرد كان يقرأ ملفاً واحداً (sourceDir/base.apk).
التطبيقات المقسمة (App Bundle) تضع كود الميزات في `split_feature_*.apk` — فإن لم يُقرأ،
لا بصمة ولا هوك. الحل: `collectApks()` يجمع sourceDir + publicSourceDir +
splitSourceDirs[] + splitPublicSourceDirs[] + مجلدات شقيقة (base.apk الحقيقي عند wrapper)،
ثم `DexNames.scanApks()` يجردها كلها مع سطر تشخيص لكل ملف (`dex=<N> +names=<M>`).

**ب. الفلتر القديم يرفض أكثر من اللازم.** `contains("application")` على الاسم الكامل
يرفض أي صنف تمر حزمته بكلمة application (شائع في تطبيقات حقيقية). الحل: فلتر word-aware —
البادئات على الاسم الكامل كما هي، والرموز تُطابق **كلمات كاملة** على الاسم البسيط فقط
بعد تقسيم CamelCase (`ApplicationManager→[Application,Manager]` يُرفض،
`VipManager→[Vip,Manager]` يُقبل). أصناف البنية الخطرة ما زالت مرفوضة (بادئاتها تطابقها).

## 2) التشخيص الجديد (للمقارنة الميدانية)

```
apk source: /data/app/.../base.apk
apk source: /data/app/.../split_config.arm64_v8a.apk
scanApk base.apk dex=3 +names=2104
inventory total=<K>
inventory host(<pkg>)=<H>
  vip-sample: com.<pkg>.data.VipStatus
SUMMARY | ... | inventory=<K> | host=<H> | tierA=<a> tierB=<b> | ... | v=7.0.2
```

قاعدة القراءة: `host > 100` + `hooks > 0` = جرد سليم. `host = 0` = الأصناف مشفرة في native
(حد مشترك لكل موديولات جافا — موثق منذ v7.0.0).

## 3) ما حُوفظ عليه (انحرافات بأسباب)

| البند | القرار | السبب |
|---|---|---|
| `resolveClass` (أساسي ثم ملحوظون) | محفوظ في المحركين | قراءة فقط عند stub — ليست هوك ClassLoader؛ يحفظ تغطية المضغوط |
| `ModernHook/ModernEngine` | محفوظان بنفس قواعد v7.0.2 | المرآة تشمل collectApks والتشخيص وSUMMARY+host |
| `noteLoader/noted` في LoaderCollector | محفوظة | القراءة من onCreate/Activity ليست هوك CL |
| `Ctx.set/setPackageName/...` الإضافية | محفوظة | صفر أثر سلوكي |
| حارس SDK في MainHook | **محذوف** (مسودة حرفية) | minSdk=25 يفرضه المثبّت؛ تبسيط متعمد |
| `ACTIVATION_TIMEOUT_MS` | 8000 كما هي | — |

## 4) نتائج التحقق (2026-10-16، فعلياً)

| فحص المسودة | النتيجة |
|---|---|
| build.sh بلا أخطاء | BUILD SUCCESSFUL |
| apksigner v1/v2/v3 | v2+v3=true + `jar verified` |
| badging | 702 / 7.0.2 / min25 / target35 |
| `splitCamel/splitPublicSourceDirs/vip-sample/apk source` في DEX | حاضرة |
| `hook_config.json` (7.0.2 + العلمان الجديدان) | حاضر |
| نفس شهادة v7.0.1 | متطابقة — ترقية مباشرة |
| صفر `BaseDexClassLoader` / صفر هوك loadLibrary | محفوظ من v7.0.1 |

## 5) الصيانة
`core/Config.java` فقط (بصمات A/B، مفاتيح، نصوص) + `./build.sh`.
قاعدة ذهبية جديدة: أي `hooks=0` مستقبلاً يُشخَّص أولاً بسطري `inventory host` و`vip-sample`
قبل لمس أي كود — التشخيص موجود الآن لهذا الغرض.
