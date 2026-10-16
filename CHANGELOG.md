# Changelog

## v7.0.2 — 2026-10-16
- **جرد متعدد المصادر**: sourceDir + publicSourceDir + splits + مجلدات شقيقة
- **فلتر word-aware**: تقسيم CamelCase للمطابقة على كلمات كاملة
- **تشخيص موسّع**: inventory host=<N> + vip-sample (حتى 20)
- إصلاح hooks=0 عندما تكون الأصناف الحقيقية في split APKs
- SUMMARY يحمل host بجانب inventory/tierA/tierB

## v7.0.1 — 2026-10-16
- **إصلاح جوهري**: حذف كل هوك ClassLoader (BaseDexClassLoader/loadClass/findClass)
- إصلاح انهيار androidx.startup في installContentProviders
- attachBaseContext يخزّن Context فقط عبر setRaw (لا activate، لا getApplicationContext)
- التنشيط بعد Application.onCreate بدل attachBaseContext
- Activity.onCreate كـ fallback للتنشيط
- timeout 8s (ACTIVATION_TIMEOUT_MS) + tick 60×250ms
- نُقل التنشيط إلى Thread منفصل "nexhook-activate"
- محاولة قراءة base.apk الحقيقي عند fancy wrapper
- قراءة getClassLoader() كاحتياط forName (ليست هوك ClassLoader)

## v7.0.0 — 2026-10-16
- هوية جديدة: NexHookPro (`com.nexhook.pro`) — API 25 → 35
- مدخل موحد legacy `MainHook` + مدخل حديث `ModernHook` (libxposed 101 → 102)
- لا حزم ثابتة — كل الاعتماد على البصمة والانعكاس
- لا هوك على System.loadLibrary / Runtime.loadLibrary0 (درس libmmkv)
- ContextWrapper#attachBaseContext بدل Application (درس NoSuchMethodError)
- BaseBundle بدل Bundle للدوال الأساسية (درس NoSuchMethodError)
- AtomicBoolean + tryArm لمنع تكرار التنشيط وحلقة re-arm
- Safe.coerceReturn يمنع ClassCastException في كل فرض
- فلتر بنية تحتية صارم قبل أي forName (لا fancybypass/pairip)
- سجل `nexhook.log` + مفتاح طوارئ `nexhook_safe.flag`
- `hook_config.json` مشحون + `build.sh` ينتج APK + source ZIP
- ترقيات الدرجة: قيم انتهاء 2100، مطابقة TextView تامة

## v6.0.0 — LokTvHookPro (الجيل السابق)
- مدخل مزدوج أول + HookEngine مستخرج + منع re-arm + API 21+
- انظر `docs/ANALYSIS_LokTvHookPro_v6.0.0.md`

## v4.2.0 / v4.1.0 — LokTvHookPro (الأجيال الأولى)
- طبقات prefs/json/text/bundle + بصمة native + إعدادات تفاعلية
- انظر `docs/ANALYSIS_LokTvHookPro_v4.*.md`
