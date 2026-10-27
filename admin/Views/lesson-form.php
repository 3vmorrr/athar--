<?php
$editingLesson = ($lessonFormMode ?? '') === 'edit'; $lessonDraft = $lessonDraft ?? [];
if ($adminError !== '' && ((!$editingLesson && ($_POST['kind'] ?? '') === 'lesson') || ($editingLesson && ($_POST['content_type'] ?? '') === 'lesson' && ($_POST['content_action'] ?? '') === 'update' && (int)($_POST['id'] ?? 0) === (int)$lessonDraft['id']))) {
    foreach (['title','description','source_url','scholar_id','series_id','sort_order'] as $field) if (is_string($_POST[$field] ?? null)) $lessonDraft[$field] = mb_substr($_POST[$field], 0, 4000, 'UTF-8');
    $lessonDraft['is_published'] = isset($_POST['publish']);
}
?>
<?php if (!$editingLesson): ?><article class="knowledge-card admin-lesson-create"><h2><?= $lessonCopy('إضافة درس YouTube') ?></h2><?php else: ?>
<div class="inventory-actions"><?php foreach (['toggle'=>'تبديل النشر','delete'=>'حذف'] as $action=>$label): ?><form method="post" action="/admin#admin-content" <?= $action === 'delete' ? 'data-confirm="' . $lessonCopy('حذف هذا الدرس نهائيًا؟') . '"' : '' ?>><?= csrf_field() ?><input type="hidden" name="kind" value="content"><input type="hidden" name="content_type" value="lesson"><input type="hidden" name="content_action" value="<?= $action ?>"><input type="hidden" name="id" value="<?= (int)$lessonDraft['id'] ?>"><button class="text-button"><?= $lessonCopy($label) ?></button></form><?php endforeach; ?></div><details class="inventory-edit"><summary><?= $lessonCopy('تعديل التفاصيل') ?></summary><?php endif; ?>
<form class="form-stack" method="post" action="/admin#<?= $editingLesson ? 'admin-content' : 'admin-create' ?>" data-lesson-form>
<?= csrf_field() ?><input type="hidden" name="kind" value="<?= $editingLesson ? 'content' : 'lesson' ?>">
<?php if ($editingLesson): ?><input type="hidden" name="content_type" value="lesson"><input type="hidden" name="content_action" value="update"><input type="hidden" name="id" value="<?= (int)$lessonDraft['id'] ?>"><?php endif; ?>
<label><?= $lessonCopy('العنوان') ?><input name="title" minlength="2" maxlength="220" value="<?= e((string)($lessonDraft['title'] ?? '')) ?>" required></label>
<label><?= $lessonCopy('الوصف') ?><textarea name="description" maxlength="4000" rows="3"><?= e((string)($lessonDraft['description'] ?? '')) ?></textarea></label>
<label><?= $lessonCopy('رابط فيديو YouTube') ?><input type="url" name="source_url" dir="ltr" maxlength="500" value="<?= e((string)($lessonDraft['source_url'] ?? '')) ?>" required><small><?= $lessonCopy('استخدم رابط مشاهدة أو youtu.be أو رابط تضمين معتمد. يبقى التشغيل لدى المشغل الرسمي.') ?></small></label>
<label><?= $lessonCopy('العالم') ?><select name="scholar_id"><option value="0"><?= $lessonCopy('بدون') ?></option><?php foreach ($scholars as $optionScholar): ?><option value="<?= (int)$optionScholar['id'] ?>" <?= (int)($lessonDraft['scholar_id'] ?? 0) === (int)$optionScholar['id'] ? 'selected' : '' ?>><?= e($optionScholar['name']) ?></option><?php endforeach; ?></select></label>
<label><?= $lessonCopy('السلسلة') ?><select name="series_id"><option value="0"><?= $lessonCopy('بدون') ?></option><?php foreach ($seriesItems as $optionSeries): ?><option value="<?= (int)$optionSeries['id'] ?>" <?= (int)($lessonDraft['series_id'] ?? 0) === (int)$optionSeries['id'] ? 'selected' : '' ?>><?= e($optionSeries['title']) ?></option><?php endforeach; ?></select></label>
<label><?= $lessonCopy('ترتيب العرض') ?><input type="number" name="sort_order" dir="ltr" min="-100000" max="100000" value="<?= e((string)($lessonDraft['sort_order'] ?? 0)) ?>" required></label>
<label class="check-label"><input type="checkbox" name="publish" <?= !empty($lessonDraft['is_published']) ? 'checked' : '' ?>><?= $lessonCopy('نشر') ?></label>
<button class="button button-primary"><?= $lessonCopy($editingLesson ? 'حفظ التعديلات' : 'حفظ الدرس') ?></button>
</form><?php if (!$editingLesson): ?></article><?php else: ?></details><?php endif; ?>
