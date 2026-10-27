<?php
$seriesTranslation = static function (string $value) use ($adminLocale): string {
    $en = ['المحتوى المحفوظ'=>'Saved content','إضافة سلسلة'=>'Add a series','العنوان'=>'Title','الوصف'=>'Description','العالم'=>'Scholar','بدون'=>'None','السلاسل'=>'Series','المصدر (اختياري)'=>'Source (optional)','حفظ السلسلة'=>'Save series','حذف هذه السلسلة نهائيًا؟'=>'Permanently delete this series?',
        'تم حفظ السلسلة.'=>'The series is saved.','تم تحديث السلسلة.'=>'The series is updated.',
        'بيانات السلسلة غير صالحة.'=>'Series fields must contain valid text.','راجع عنوان السلسلة ووصفها.'=>'Use a title of 2 to 220 characters and a description of at most 4000 characters.',
        'أدخل رابط مصدر HTTPS صالحًا دون بيانات دخول.'=>'Use a valid HTTPS source without URL credentials.','راجع العالم وترتيب العرض.'=>'Choose a valid scholar and an integer order between -100000 and 100000.',
        'السلسلة غير موجودة.'=>'This series no longer exists.','العالم المحدد غير موجود.'=>'The selected scholar no longer exists.',
        'توجد دروس مرتبطة بعالم آخر. أعد ربطها قبل تغيير عالم السلسلة.'=>'Reassign lessons linked to another scholar before changing the series scholar.',
        'لا يمكن حذف السلسلة قبل نقل أو حذف دروسها المرتبطة.'=>'Move or remove the series lessons before deleting the series.',
        'تعذر حفظ المحتوى. راجع الحقول أو الاتصال بقاعدة البيانات.'=>'The content could not be saved. Check the fields and try again.',
    ];
    return $adminLocale === 'en' ? ($en[$value] ?? $value) : $value;
};
$seriesCopy = static fn(string $value): string => e($seriesTranslation($scholarTranslation($value)));
