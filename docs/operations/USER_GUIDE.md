# Athar user guide

This guide describes the intended user-facing product on the release branch. A feature that still has an external/physical acceptance blocker in \`KNOWN_LIMITS.md\` must not be advertised as production-proven until that evidence exists.

## العربية

### البدء والحساب

- افتح التطبيق وأكمل الإعداد الأولي.
- يمكنك تسجيل الدخول بالحساب المصرح به واستخدام العربية أو الإنجليزية والثيم الفاتح/الداكن.
- بيانات التقدم الخاصة مرتبطة بالحساب، ويجب ألا تظهر بعد الانتقال إلى حساب آخر.
- في حالة انتهاء الجلسة أو إلغائها، سيطلب التطبيق تسجيل الدخول بدل الاستمرار على بيانات حساب قديم.

### القرآن

- قراءة القرآن والبحث والتنقل بين السور والآيات.
- حفظ موضع القراءة والإشارات المرجعية.
- أدوات الصوت تعمل عبر المصادر المعتمدة فقط.
- مراجعة التلاوة المحلية/النموذجية لا تُعتبر حكمًا على صحة التلاوة أو بديلًا عن معلّم؛ استخدم الملاحظات كإشارة مساعدة فقط.

### المعرفة والكتب

- استكشف الدروس والسلاسل والعلماء والكتب المنشورة.
- إذا كان هناك تقدم حقيقي محفوظ في درس غير مكتمل، قد يظهر زر متابعة التعلم.
- بيانات الكتاب مثل المؤلف والوصف والسنة تظهر فقط عندما تكون موجودة فعليًا في الكتالوج.
- حقوق القراءة/التنزيل تُعرض من صفحة الكتاب ولا يجب افتراض أن كل كتاب قابل للتنزيل.

### الأذكار والتذكيرات

- يمكن استخدام الأذكار والروتينات المحلية والتذكيرات المدعومة.
- إعدادات الإشعارات والموافقة وساعات الهدوء تتحكم في ما يُسمح بعرضه.
- صندوق الإشعارات يحتفظ بحالة محلية مشفرة للحساب ويدعم مزامنة القراءة/الحذف عند عودة الشبكة.
- التذكير المحلي المستحق لا يعتمد على وجود الإنترنت.

### قيام الليل

- إعدادات قيام الليل وحالة المستخدم الخاصة تبقى ضمن الحساب.
- الإعدادات العامة التي يحددها Admin منفصلة عن الجلسات/الملاحظات الخاصة.
- عند تعذر قراءة البيانات المشفرة، يعرض التطبيق حالة خطأ بدل اختراع تقدم.

### الصوت

- التشغيل الخلفي يستخدم Media3 وخدمة تشغيل مخصصة.
- تسجيل الخروج أو الانتقال لحالة حساب غير مسموحة يوقف التشغيل ويمسح عناصر الجلسة.
- مشاكل المصدر الخارجي أو الشبكة يجب أن تظهر كحالة قابلة للمحاولة، لا كتقدم مزيف.

## English

The same rules apply in English UI: account-scoped private state, truthful continuation only from saved data, published catalog data only, approved media sources, explicit offline/error states, and no fabricated progress or notification delivery claims.

For release support, record only app version, device model/API, error category and non-sensitive reproduction steps. Never send passwords, OTP codes, bearer tokens, Admin cookies, private worship notes or notification contents in support evidence.
