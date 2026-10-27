<?php
$eventNavigation = ['users_q'=>$managedUserSearch ?? '','users_page'=>$managedUserPage ?? 1,'audit_q'=>$auditSearch ?? '','audit_page'=>$auditPage ?? 1,'comments_page'=>$commentPage ?? 1,'events_page'=>$lessonEventPageNumber,'event_edit'=>$eventFocus ?: null];
$eventUrl = static function (array $changes = []) use ($eventNavigation): string {
    $context = array_filter(array_merge($eventNavigation,$changes), static fn($value): bool => $value !== null && $value !== '');
    return '/admin' . ($context ? '?' . http_build_query($context) : '') . '#admin-events';
};
?>
<section class="admin-inventory admin-panel" id="admin-events" data-admin-panel hidden>
<div class="section-heading"><h2><?= $eventCopy('القادم والمباشر','Upcoming & Live') ?></h2><span class="source-chip"><?= $lessonEventTotal === null ? $eventCopy('غير متاح','Unavailable') : (int)$lessonEventTotal . ' ' . $eventCopy('حدثًا','events') ?></span></div>
<p class="source-note"><?= $eventCopy('تحقق من المشغل الرسمي قبل تأكيد البث. حفظ الحدث يحدّث نشر الدرس حسب الحالة والظهور؛ المشاهدة تتطلب درسًا منشورًا.','Check the official provider before confirming live. Saving an event updates lesson publication from its status and visibility; Watch requires a published lesson.') ?></p>
<?php if (in_array($lessonEventLoadState,['verification_required','unavailable'],true)): ?>
<p class="notice notice-error"><?= $lessonEventLoadState === 'verification_required' ? $eventCopy('يلزم بريد إداري مؤكد لإدارة الأحداث.','Event editing requires a verified Admin email address.') : $eventCopy('تعذر تحميل الأحداث. احتُفظ بالبيانات؛ أعد المحاولة.','Events could not be loaded. Data is preserved; retry the page.') ?></p>
<?php else: ?>
<?php if ($lessonEventLoadState === 'partial'): ?><p class="notice notice-error"><?= $eventCopy('تعذر تحميل بعض السجلات. أعد المحاولة قبل تعديلها.','Some records could not be loaded. Retry before editing them.') ?></p><?php endif; ?>
<?php
$eventExistingSet = array_fill_keys($eventExistingLessonIds,true);
$eventEligibleLessons = array_values(array_filter($lessons, static fn(array $lesson): bool => !isset($eventExistingSet[(int)$lesson['id']])));
$eventSelectedRecord = $eventFocusedRecord;
foreach ($lessonEvents as $candidate) if ($candidate['id'] === $eventFocus) $eventSelectedRecord = $candidate;
if ($eventSelectedRecord !== null) { $eventRecord=$eventSelectedRecord; require __DIR__.'/event-form.php'; unset($eventRecord); }
require __DIR__.'/event-form.php';
?>
<div class="admin-event-list">
<?php if ($lessonEventTotal === 0): ?><p class="source-note"><?= $eventCopy('لا توجد أحداث محفوظة بعد.','No events have been saved yet.') ?></p><?php endif; ?>
<?php foreach ($lessonEvents as $eventRecord): if ($eventRecord['id'] === $eventFocus) continue; require __DIR__.'/event-form.php'; endforeach; unset($eventRecord); ?>
</div>
<?php if ($lessonEventPageCount > 1): ?><nav class="admin-pagination" aria-label="<?= $eventCopy('صفحات الأحداث','Event pages') ?>">
<?php if ($lessonEventPageNumber > 1): ?><a class="button button-secondary" href="<?= e($eventUrl(['events_page'=>$lessonEventPageNumber-1,'event_edit'=>null])) ?>"><?= $eventCopy('السابق','Previous') ?></a><?php endif; ?>
<span><?= $eventCopy('صفحة','Page') ?> <?= (int)$lessonEventPageNumber ?> <?= $eventCopy('من','of') ?> <?= (int)$lessonEventPageCount ?></span>
<?php if ($lessonEventPageNumber < $lessonEventPageCount): ?><a class="button button-secondary" href="<?= e($eventUrl(['events_page'=>$lessonEventPageNumber+1,'event_edit'=>null])) ?>"><?= $eventCopy('التالي','Next') ?></a><?php endif; ?></nav><?php endif; ?>
<?php endif; ?></section>
