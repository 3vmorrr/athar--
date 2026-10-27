<?php
$editorialCopy=static fn(string $ar,string $en):string=>e($adminLocale === 'en' ? $en : $ar);
$editorialTranslation=static function(string $message) use($adminLocale):string {
    $messages=[
        'راجع عنوان الذكر ونصه ومرجعه وتصنيفه.'=>'Check the item title, text, source reference and category.',
        'لا يمكن حفظ الذكر دون مصدر آمن ومراجعة موثقة.'=>'An HTTPS source without credentials and editorial review are required.',
        'عدد التكرار يجب أن يكون من 1 إلى 1000 أو يترك فارغًا.'=>'Enter a repeat count from1 to1000, or leave it blank.',
        'ترتيب الذكر غير صالح.'=>'Enter an integer order from-100000 to100000.',
        'نسخة الذكر غير صالحة؛ حدّث الصفحة ثم أعد المحاولة.'=>'The item version is invalid. Reload and retry.',
        'الذكر غير موجود.'=>'This item no longer exists.',
        'تم تعديل الذكر من جلسة أخرى. حدّث الصفحة ثم أعد المحاولة.'=>'Another editor changed this item. Reload before retrying.',
        'تم حفظ الذكر مع توثيق نسخته.'=>'Item saved with its editorial revision.',
        'تم تحديث الذكر مع توثيق نسخته.'=>'Item updated with its editorial revision.',
        'بيانات مراجعة التعليق غير صالحة.'=>'Choose a valid comment and saved version.',
        'راجع الردود واحذفها أولًا قبل حذف التعليق الأصلي.'=>'Review and delete replies before deleting the original comment.',
        'تم تعديل التعليق من جلسة أخرى. حدّث الصفحة ثم أعد المحاولة.'=>'Another moderator changed this comment. Reload before retrying.',
        'التعليق غير موجود.'=>'This comment no longer exists.',
        'راجع الحالة والسبب: من حرفين إلى 500 حرف.'=>'Choose a valid decision and provide a reason from2 to500 characters.',
        'تم حذف التعليق مع الاحتفاظ بسجل قرار المراجعة.'=>'Comment deleted; the moderation decision is retained.',
        'تم حفظ قرار المراجعة وسببه.'=>'Moderation decision and reason saved.',
    ];return $adminLocale === 'en' ? ($messages[$message] ?? $message) : $message;
};
