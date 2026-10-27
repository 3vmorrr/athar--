<?php
$lessonTranslation = static function (string $value) use ($adminLocale): string {
    $en = ['الدروس'=>'Lessons','إضافة درس YouTube'=>'Add a YouTube lesson','رابط فيديو YouTube'=>'YouTube video URL','السلسلة'=>'Series','حفظ الدرس'=>'Save lesson','حذف هذا الدرس نهائيًا؟'=>'Permanently delete this lesson?',
        'تم حفظ الدرس.'=>'The lesson is saved.','تم تحديث الدرس.'=>'The lesson is updated.','بيانات الدرس غير صالحة.'=>'Lesson fields must contain valid text.',
        'راجع عنوان الدرس ووصفه.'=>'Use a title of 2 to 220 characters and a description of at most 20000 characters.',
        'أدخل رابط فيديو YouTube معتمدًا يبدأ بـ HTTPS دون بيانات دخول.'=>'Use an approved HTTPS YouTube video URL without URL credentials.',
        'راجع العالم والسلسلة وترتيب العرض.'=>'Choose valid scholar/series IDs and an integer order between -100000 and 100000.',
        'الدرس غير موجود.'=>'This lesson no longer exists.','السلسلة المحددة غير موجودة.'=>'The selected series no longer exists.',
        'العالم المحدد لا يطابق عالم هذه السلسلة.'=>'The selected scholar does not match the series scholar.',
        'لهذا الدرس سجل مواعيد مرتبط، ولا يمكن حذفه.'=>'This lesson has linked event history and cannot be deleted.',
        'أنشئ درسًا منفصلًا لفيديو جديد عند وجود سجل مواعيد مرتبط.'=>'Create a separate lesson for a new video when linked event history exists.',
        'هذا الفيديو مسجل في درس آخر.'=>'This provider video is already registered in another lesson.',
        'استخدم رابط مشاهدة أو youtu.be أو رابط تضمين معتمد. يبقى التشغيل لدى المشغل الرسمي.'=>'Use a watch, youtu.be or approved embed URL. Playback stays with the official player.',
    ];
    return $adminLocale === 'en' ? ($en[$value] ?? $value) : $value;
};
$lessonCopy = static fn(string $value): string => e($lessonTranslation($seriesTranslation($scholarTranslation($value))));
