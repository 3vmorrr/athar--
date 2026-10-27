<?php
$bookTranslation = static function(string $message) use ($adminLocale): string {
    $messages=[
        'بيانات الكتاب غير صالحة.'=>'Book fields must contain valid text.',
        'بيانات الكتاب أطول من الحد المسموح.'=>'Book fields exceed their allowed lengths.',
        'عنوان الكتاب أو اسم المؤلف غير صالح.'=>'Enter a book title and author of at least two characters.',
        'يلزم توثيق مصدر آمن وأساس حقوق الإتاحة.'=>'Provide an HTTPS source without credentials and the rights basis.',
        'رابط التنزيل المباشر يجب أن يبدأ بـ HTTPS دون بيانات دخول عند توفيره.'=>'The optional download link must use HTTPS without credentials.',
        'لغة الكتاب أو سنة النشر غير صالحة.'=>'Choose a valid language and a publication year between1000 and2200.',
        'الكتاب غير موجود.'=>'This book no longer exists.',
        'أزل مراجع الكتاب من الأحداث قبل حذفه.'=>'Remove this book’s event references before deleting it.',
        'تغيّر الكتاب في جلسة أخرى. أعد تحميله قبل الحفظ أو الحذف.'=>'Another editor changed this book. Reload it before saving or deleting.',
        'تم حذف سجل الكتاب؛ تعذر تنظيف نسخته المحلية. راجع التخزين.'=>'The book record was deleted; its local copy could not be cleaned up. Review storage.',
        'تم حفظ الكتاب.'=>'Book saved.', 'تم تحديث الكتاب.'=>'Book updated.',
    ];
    return $adminLocale === 'en' ? ($messages[$message] ?? $message) : $message;
};
$bookCopy=static fn(string $ar,string $en):string=>e($adminLocale === 'en' ? $en : $ar);
