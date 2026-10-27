<?php
declare(strict_types=1);

final class AdminEventConflict extends RuntimeException {}

/** Convert a strict wall-clock value to an explicit instant without normalizing gaps. */
function admin_event_instant(string $local, string $zone, string $offset = ''): ?string
{
    if (!in_array($zone, DateTimeZone::listIdentifiers(DateTimeZone::ALL_WITH_BC), true)) throw new RuntimeException('المنطقة الزمنية غير صالحة.');
    if ($local === '') return null;
    if (!preg_match('/^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2})(?::(\d{2})(?:\.(\d{1,3}))?)?$/D', $local, $parts)) throw new RuntimeException('اكتب موعدًا محليًا صالحًا بدقة لا تتجاوز المللي ثانية.');
    $wall = $parts[1] . ':' . ($parts[2] ?? '00') . '.' . str_pad($parts[3] ?? '', 3, '0');
    $parsed = DateTimeImmutable::createFromFormat('!Y-m-d\TH:i:s.v', $wall, new DateTimeZone('UTC'));
    $errors = DateTimeImmutable::getLastErrors();
    if (!$parsed || ($errors !== false && ($errors['warning_count'] || $errors['error_count'])) || $parsed->format('Y-m-d\TH:i:s.v') !== $wall || (int)$parsed->format('Y') < 2020 || (int)$parsed->format('Y') > 2100) throw new RuntimeException('الموعد المحلي غير صالح أو خارج النطاق المسموح.');
    $timezone = new DateTimeZone($zone); $epoch = $parsed->getTimestamp(); $offsets = [];
    foreach ($timezone->getTransitions($epoch - 172800, $epoch + 172800) ?: [] as $transition) $offsets[(int)$transition['offset']] = true;
    $candidates = [];
    foreach (array_keys($offsets) as $seconds) {
        $instant = DateTimeImmutable::createFromFormat('U.u', ($epoch - $seconds) . '.' . $parsed->format('u'));
        if ($instant && $instant->setTimezone($timezone)->format('Y-m-d\TH:i:s.v') === $wall) {
            $instant = $instant->setTimezone($timezone); $candidates[$instant->format('P')] = $instant;
        }
    }
    if (!$candidates) throw new RuntimeException('هذا الوقت المحلي غير موجود بسبب تغيير التوقيت الصيفي. اختر وقتًا آخر.');
    if ($offset === 'Z') $offset = '+00:00';
    if ($offset !== '') {
        if (!isset($candidates[$offset])) throw new RuntimeException('فرق UTC لا يطابق الموعد والمنطقة الزمنية. صححه أو اتركه فارغًا لوقت غير ملتبس.');
        return $candidates[$offset]->format('Y-m-d\TH:i:s.vP');
    }
    if (count($candidates) !== 1) throw new RuntimeException('هذا الوقت المحلي يتكرر بسبب تغيير التوقيت. حدد فرق UTC المقصود.');
    return reset($candidates)->format('Y-m-d\TH:i:s.vP');
}

function admin_event_text(array $post, string $key, string $default = ''): string
{
    $value = $post[$key] ?? $default;
    if (!is_string($value)) throw new RuntimeException('بيانات الحدث غير صالحة.');
    return trim($value);
}

function admin_event_number(array $post, string $key, int $maximum = PHP_INT_MAX): ?int
{
    $raw = admin_event_text($post, $key);
    if ($raw === '') return null;
    $value = filter_var($raw, FILTER_VALIDATE_INT, ['options'=>['min_range'=>1,'max_range'=>$maximum]]);
    if ($value === false) throw new RuntimeException('راجع المدة ومرجع الكتاب ونطاق الصفحات.');
    return $value;
}

function admin_event_save(array $actor, array $post): array
{
    if (!admin_can($actor, 'content') || empty($actor['email_verified_at']) || !empty($actor['suspended_at'])) admin_deny_action($actor, 'admin-action', 'lesson-event');
    $lesson = filter_var($post['lesson_id'] ?? null, FILTER_VALIDATE_INT);
    $revision = filter_var($post['expected_revision'] ?? null, FILTER_VALIDATE_INT);
    if (!$lesson || $lesson < 1 || $revision === false || $revision < 0) throw new RuntimeException('الدرس أو نسخة الحدث غير صالحة.');
    $zone = admin_event_text($post, 'schedule_timezone', 'UTC');
    $status = admin_event_text($post, 'status', 'draft');
    $confirmedLive = in_array($post['confirm_live'] ?? null, ['on', '1'], true);
    if ($status === 'live' && !$confirmedLive) throw new RuntimeException('أكد أنك تحققت الآن من البث المباشر لدى المشغل الرسمي.');
    $topics = static function (string $raw): array {
        $parts = preg_split('/[\r\n,،]+/u', $raw);
        if ($parts === false) throw new RuntimeException('بيانات الموضوعات غير صالحة.');
        $parts = array_values(array_filter(array_map('trim', $parts), static fn(string $part): bool => $part !== ''));
        if (count($parts) > 12) throw new RuntimeException('لا يمكن إدخال أكثر من 12 موضوعًا لكل لغة.');
        return $parts;
    };
    $input = [
        'expected_revision'=>$revision,'status'=>$status,'visibility'=>admin_event_text($post,'visibility','public'),
        'scheduled_at'=>admin_event_instant(admin_event_text($post,'scheduled_local'), $zone, admin_event_text($post,'schedule_offset')),
        'schedule_timezone'=>$zone,'estimated_duration_minutes'=>admin_event_number($post,'estimated_duration_minutes',1440),
        'is_live_event'=>isset($post['is_live_event']),'reminders_enabled'=>isset($post['reminders_enabled']),'replay_available'=>isset($post['replay_available']),
        'confirm_live'=>$confirmedLive,
        'course_title_ar'=>admin_event_text($post,'course_title_ar') ?: null,'course_title_en'=>admin_event_text($post,'course_title_en') ?: null,
        'topics_ar'=>$topics(admin_event_text($post,'topics_ar')),'topics_en'=>$topics(admin_event_text($post,'topics_en')),
        'related_book_id'=>admin_event_number($post,'related_book_id'),'related_page_start'=>admin_event_number($post,'related_page_start',100000),'related_page_end'=>admin_event_number($post,'related_page_end',100000),
    ];
    $arabicTitle = admin_event_text($post, 'title_ar'); $arabicDescription = admin_event_text($post, 'description_ar');
    if ($arabicTitle !== '' || $revision > 0) $input['title_ar'] = $arabicTitle;
    if (isset($post['clear_arabic_description'])) $input['description_ar'] = null;
    elseif (array_key_exists('description_ar', $post) && ($arabicDescription !== '' || $revision > 0)) $input['description_ar'] = $arabicDescription ?: null;
    $englishTitle = admin_event_text($post, 'title_en'); $englishDescription = admin_event_text($post,'description_en');
    if (isset($post['clear_english_translation'])) $input['title_en'] = null;
    elseif ($englishTitle !== '') { $input['title_en'] = $englishTitle; $input['description_en'] = $englishDescription ?: null; }
    elseif ($englishDescription !== '') throw new RuntimeException('أدخل عنوانًا إنجليزيًا مع الوصف أو اطلب حذف الترجمة صراحةً.');
    try { return (new \Athar\Learning\UpcomingLessonService(db()))->save($actor, $lesson, $input); }
    catch (\Athar\Http\ApiError $failure) {
        if ($failure->getMessage() === 'conflict') throw new AdminEventConflict('تغيّر الحدث في جلسة أخرى. احتفظنا بمسودتك؛ حمّل النسخة الحالية قبل الحفظ.');
        if ($failure->status === 403) admin_deny_action($actor, 'admin-action', 'lesson-event');
        if ($failure->status === 404) throw new RuntimeException('الدرس أو الحدث غير موجود.');
        $field = array_key_first($failure->fields);
        throw new RuntimeException(match ($field) {
            'status','confirm_live','is_live_event','replay_available' => 'راجع انتقال الحالة وأعلام البث والإعادة والتأكيد.',
            'scheduled_at','schedule_timezone' => 'راجع الموعد والمنطقة الزمنية.',
            'title_ar','description_ar','title_en','description_en' => 'راجع العناوين والأوصاف وحدود أطوالها.',
            'course_title_ar','course_title_en','topics_ar','topics_en' => 'راجع اسم الدورة والموضوعات: 12 موضوعًا بحد أقصى، ومن 2 إلى 80 حرفًا لكل موضوع.',
            'related_book_id','related_page_start','related_page_end' => 'راجع الكتاب المرتبط ونطاق الصفحات.',
            'youtube_id' => 'يلزم فيديو YouTube صالح للبث أو الإعادة.',
            default => 'راجع بيانات الحدث وأعد المحاولة.',
        });
    }
}
