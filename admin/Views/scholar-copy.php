<?php
$scholarTranslation = static function (string $value) use ($adminLocale): string {
    $en = [
        'إضافة عالم'=>'Add a scholar','الاسم'=>'Name','السيرة المختصرة'=>'Short biography','اللغة'=>'Language','العربية'=>'Arabic','الإنجليزية'=>'English','أخرى'=>'Other','المصدر المعتمد (اختياري)'=>'Approved source (optional)','بيان الحقوق/الإذن (اختياري)'=>'Rights or permission (optional)','الصورة (اختيارية، JPG أو PNG أو WebP، حتى 3 MB)'=>'Optional portrait: JPG, PNG or WebP, up to 3 MB','ترتيب العرض'=>'Display order','نشر'=>'Publish','حفظ العالم'=>'Save scholar','حفظ التعديلات'=>'Save changes','تعديل التفاصيل'=>'Edit details','تبديل النشر'=>'Toggle publication','حذف'=>'Delete','حذف هذا العالم نهائيًا؟'=>'Permanently delete this scholar?','العلماء'=>'Scholars','منشور'=>'Published','مسودة'=>'Draft',
        'تم حفظ العالم.'=>'The scholar is saved.','تم تحديث العالم.'=>'The scholar is updated.',
        'بيانات العالم غير صالحة.'=>'Scholar fields must contain valid text.',
        'اسم العالم يجب أن يكون من 2 إلى 180 حرفًا.'=>'The scholar name must contain 2 to 180 characters.',
        'السيرة أو بيانات الحقوق أطول من الحد المسموح.'=>'The biography or rights statement exceeds its length limit.',
        'لغة العالم أو رابط المصدر غير صالح.'=>'Choose a supported language and an HTTPS source without URL credentials.',
        'ترتيب العرض يجب أن يكون عددًا صحيحًا بين -100000 و100000.'=>'Display order must be an integer between -100000 and 100000.',
        'العالم غير موجود.'=>'This scholar no longer exists.',
        'لا يمكن حذف عالم مرتبط بسلاسل أو دروس. أعد ربط المحتوى أولًا.'=>'Reassign this scholar’s series and lessons before deleting the scholar.',
        'ارفع صورة صالحة لا يتجاوز حجمها 3 ميجابايت.'=>'Upload a valid image no larger than 3 MB.',
        'صيغة الصورة غير مدعومة. استخدم JPG أو PNG أو WebP بأبعاد مناسبة.'=>'Use a JPG, PNG or WebP portrait between 80 and 6000 pixels on each side.',
    ];
    return $adminLocale === 'en' ? ($en[$value] ?? $value) : $value;
};
$scholarCopy = static fn(string $value): string => e($scholarTranslation($value));
