<?php
$eventTranslation = static function (string $message) use ($adminLocale): string {
    $en = [
        'تم حفظ حالة الدرس القادم/المباشر وتسجيل التغيير.'=>'The event is saved and its change is recorded.',
        'المنطقة الزمنية غير صالحة.'=>'Choose a valid time zone.',
        'اكتب موعدًا محليًا صالحًا بدقة لا تتجاوز المللي ثانية.'=>'Enter a valid local date/time with at most millisecond precision.',
        'الموعد المحلي غير صالح أو خارج النطاق المسموح.'=>'Use a valid calendar date between 2020 and 2100.',
        'هذا الوقت المحلي غير موجود بسبب تغيير التوقيت الصيفي. اختر وقتًا آخر.'=>'That local time does not exist during the daylight-saving change. Choose another time.',
        'فرق UTC لا يطابق الموعد والمنطقة الزمنية. صححه أو اتركه فارغًا لوقت غير ملتبس.'=>'The UTC offset does not match this local time and zone. Correct it or leave it blank for an unambiguous time.',
        'هذا الوقت المحلي يتكرر بسبب تغيير التوقيت. حدد فرق UTC المقصود.'=>'That local time occurs twice. Enter the intended UTC offset.',
        'بيانات الحدث غير صالحة.'=>'Event fields must contain valid text.',
        'راجع المدة ومرجع الكتاب ونطاق الصفحات.'=>'Check the duration, book reference and page range.',
        'الدرس أو نسخة الحدث غير صالحة.'=>'Choose a valid lesson and saved version.',
        'أكد أنك تحققت الآن من البث المباشر لدى المشغل الرسمي.'=>'Confirm that you have checked the official provider is live now.',
        'بيانات الموضوعات غير صالحة.'=>'Topic text is invalid.',
        'لا يمكن إدخال أكثر من 12 موضوعًا لكل لغة.'=>'Use at most 12 topics for each language.',
        'أدخل عنوانًا إنجليزيًا مع الوصف أو اطلب حذف الترجمة صراحةً.'=>'Provide an English title with its description, or explicitly clear the translation.',
        'تغيّر الحدث في جلسة أخرى. احتفظنا بمسودتك؛ حمّل النسخة الحالية قبل الحفظ.'=>'Another editor changed this event. Your draft is retained; load the current version before saving.',
        'الدرس أو الحدث غير موجود.'=>'The lesson or event no longer exists.',
        'راجع انتقال الحالة وأعلام البث والإعادة والتأكيد.'=>'Check the status transition, live/replay flags and confirmation.',
        'راجع الموعد والمنطقة الزمنية.'=>'Check the schedule and time zone.',
        'راجع العناوين والأوصاف وحدود أطوالها.'=>'Check the titles, descriptions and their length limits.',
        'راجع اسم الدورة والموضوعات: 12 موضوعًا بحد أقصى، ومن 2 إلى 80 حرفًا لكل موضوع.'=>'Check the course title and topics: at most 12 topics, each 2 to 80 characters.',
        'راجع الكتاب المرتبط ونطاق الصفحات.'=>'Check the related book and page range.',
        'يلزم فيديو YouTube صالح للبث أو الإعادة.'=>'A valid YouTube video is required for live or replay status.',
        'راجع بيانات الحدث وأعد المحاولة.'=>'Check the event fields and try again.',
    ];
    return $adminLocale === 'en' ? ($en[$message] ?? $message) : $message;
};
$eventCopy = static fn(string $ar, string $en): string => e($adminLocale === 'en' ? $en : $ar);
$eventStatuses = ['draft'=>['مسودة','Draft'],'scheduled'=>['مجدول','Scheduled'],'live'=>['مباشر','Live'],'published'=>['منشور / إعادة','Published / replay'],'cancelled'=>['ملغي','Cancelled']];
