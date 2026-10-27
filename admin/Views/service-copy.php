<?php
$serviceCopy=static fn(string $ar,string $en):string=>e($adminLocale === 'en' ? $en : $ar);
$serviceTranslation=static function(string $message) use($adminLocale):string {
    $messages=[
        'حقول إعدادات الخدمة غير صالحة أو أطول من الحد المسموح.'=>'Service fields are invalid or exceed their allowed lengths.',
        'أدخل بريد Gmail صالحًا.'=>'Enter a valid Gmail account email address.',
        'أدخل بريد اختبار صالحًا وأكد تصريح صاحبه.'=>'Provide a valid test email and confirm the recipient’s authorization.',
        'أدخل App Password جديدة عند تغيير حساب البريد.'=>'Provide a new App Password when changing the email account.',
        'أدخل App Password الخاصة بالتطبيق.'=>'Provide the account’s App Password.',
        'حدد مستلم اختبار مصرحًا به قبل إرسال اختبار.'=>'Authorize a test recipient before requesting a test.',
        'حُفظت الإعدادات؛ انتهت حصة الاختبار لهذه الساعة.'=>'Settings saved; the test quota for this hour is exhausted.',
        'حُفظت الإعدادات؛ تعذر إكمال اختبار SMTP. راجع الاتصال وبيانات الحساب والمستلم.'=>'Settings saved; the SMTP test failed. Check the connection, credentials and authorized recipient.',
        'الإعدادات المشفرة تتطلب مفتاحها الأصلي. استعد المفتاح قبل تعديلها.'=>'Encrypted settings require their original key. Restore that key before editing them.',
        'تم حفظ إعدادات Gmail.'=>'Gmail settings saved.',
        'قبل خادم SMTP رسالة الاختبار. تحقق من صندوق المستلم المصرح به.'=>'SMTP accepted the test message. Check the authorized recipient’s inbox.',
        'تم حفظ إعدادات بحث الدرر السنية.'=>'Dorar search settings saved.',
        'كلمة مرور المالك غير صحيحة.'=>'The Owner password is incorrect.',
        'نسخة الإعدادات العامة غير صالحة.'=>'The public settings version is invalid.',
        'هذه الشاشة تقبل الإعدادات العامة فقط.'=>'Only public default fields are accepted here.',
        'راجع طريقة الحساب والمذهب والقاعدة والهدف والمصدر والنصوص.'=>'Check the calculation method, madhab, rule, target, source and text.',
        'تغيّرت الإعدادات العامة من جلسة أخرى. أعد تحميلها قبل الحفظ.'=>'Another editor changed the public defaults. Reload before saving.',
        'تعذرت قراءة الإعدادات العامة. احتُفظ بالقيم الحالية.'=>'Public defaults could not be read. The current values are preserved.',
        'تم حفظ الإعدادات العامة لقيام الليل.'=>'Public Night Prayer defaults saved.',
    ];return $adminLocale === 'en' ? ($messages[$message] ?? $message) : $message;
};
$serviceStates=['missing'=>['غير محفوظ','Missing'],'readable'=>['محفوظ وقابل للقراءة','Stored and readable'],'unreadable'=>['تعذرت قراءة الإعداد المشفر','Encrypted setting is unreadable'],'unauthorized'=>['تصريح الاختبار غير مكتمل','Test authorization is incomplete'],'environment'=>['محدد في بيئة الخادم','Provided by the server environment']];
