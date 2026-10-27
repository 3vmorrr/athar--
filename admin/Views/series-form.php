<?php
$editingSeries = ($seriesFormMode ?? '') === 'edit';
$seriesDraft = $seriesDraft ?? [];
if ($adminError !== '' && ((!$editingSeries && ($_POST['kind'] ?? '') === 'series') || ($editingSeries && ($_POST['content_type'] ?? '') === 'series' && ($_POST['content_action'] ?? '') === 'update' && (int)($_POST['id'] ?? 0) === (int)$seriesDraft['id']))) {
    foreach (['title','description','source_url','scholar_id','sort_order'] as $field) if (is_string($_POST[$field] ?? null)) $seriesDraft[$field] = mb_substr($_POST[$field], 0, 4000, 'UTF-8');
    $seriesDraft['is_published'] = isset($_POST['publish']);
}
?>
<?php if (!$editingSeries): ?><article class="knowledge-card admin-series-create"><h2><?= $seriesCopy('إضافة سلسلة') ?></h2><?php else: ?>
<div class="inventory-actions"><?php foreach (['toggle'=>'تبديل النشر','delete'=>'حذف'] as $action=>$label): ?><form method="post" action="/admin#admin-content" <?= $action === 'delete' ? 'data-confirm="' . $seriesCopy('حذف هذه السلسلة نهائيًا؟') . '"' : '' ?>><?= csrf_field() ?><input type="hidden" name="kind" value="content"><input type="hidden" name="content_type" value="series"><input type="hidden" name="content_action" value="<?= $action ?>"><input type="hidden" name="id" value="<?= (int)$seriesDraft['id'] ?>"><button class="text-button"><?= $seriesCopy($label) ?></button></form><?php endforeach; ?></div>
<details class="inventory-edit"><summary><?= $seriesCopy('تعديل التفاصيل') ?></summary><?php endif; ?>
<form class="form-stack" method="post" action="/admin#<?= $editingSeries ? 'admin-content' : 'admin-create' ?>" data-series-form>
<?= csrf_field() ?><input type="hidden" name="kind" value="<?= $editingSeries ? 'content' : 'series' ?>">
<?php if ($editingSeries): ?><input type="hidden" name="content_type" value="series"><input type="hidden" name="content_action" value="update"><input type="hidden" name="id" value="<?= (int)$seriesDraft['id'] ?>"><?php endif; ?>
<label><?= $seriesCopy('العنوان') ?><input name="title" minlength="2" maxlength="220" value="<?= e((string)($seriesDraft['title'] ?? '')) ?>" required></label>
<label><?= $seriesCopy('الوصف') ?><textarea name="description" rows="3" maxlength="4000"><?= e((string)($seriesDraft['description'] ?? '')) ?></textarea></label>
<label><?= $seriesCopy('المصدر (اختياري)') ?><input type="url" name="source_url" dir="ltr" maxlength="500" value="<?= e((string)($seriesDraft['source_url'] ?? '')) ?>"></label>
<label><?= $seriesCopy('العالم') ?><select name="scholar_id"><option value="0"><?= $seriesCopy('بدون') ?></option><?php foreach ($scholars as $optionScholar): ?><option value="<?= (int)$optionScholar['id'] ?>" <?= (int)($seriesDraft['scholar_id'] ?? 0) === (int)$optionScholar['id'] ? 'selected' : '' ?>><?= e($optionScholar['name']) ?></option><?php endforeach; ?></select></label>
<label><?= $seriesCopy('ترتيب العرض') ?><input type="number" name="sort_order" dir="ltr" min="-100000" max="100000" value="<?= e((string)($seriesDraft['sort_order'] ?? 0)) ?>" required></label>
<label class="check-label"><input type="checkbox" name="publish" <?= !empty($seriesDraft['is_published']) ? 'checked' : '' ?>><?= $seriesCopy('نشر') ?></label>
<button class="button button-primary"><?= $seriesCopy($editingSeries ? 'حفظ التعديلات' : 'حفظ السلسلة') ?></button>
</form><?php if (!$editingSeries): ?></article><?php else: ?></details><?php endif; ?>
