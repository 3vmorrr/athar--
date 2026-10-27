<?php
$editingScholar = ($scholarFormMode ?? '') === 'edit';
$scholarDraft = $scholarDraft ?? [];
if (!$editingScholar && $adminError !== '' && ($_POST['kind'] ?? '') === 'scholar') {
    foreach (['name','biography','language','source_url','rights_basis','sort_order'] as $field) if (is_string($_POST[$field] ?? null)) $scholarDraft[$field] = mb_substr($_POST[$field], 0, 6000, 'UTF-8');
    $scholarDraft['is_published'] = isset($_POST['publish']);
}
?>
<?php if (!$editingScholar): ?><article class="knowledge-card admin-scholar-create"><h2><?= $scholarCopy('إضافة عالم') ?></h2><?php else: ?>
<div class="inventory-actions">
<?php foreach (['toggle'=>'تبديل النشر','delete'=>'حذف'] as $action=>$label): ?><form method="post" action="/admin#admin-content" <?= $action === 'delete' ? 'data-confirm="' . $scholarCopy('حذف هذا العالم نهائيًا؟') . '"' : '' ?>><?= csrf_field() ?><input type="hidden" name="kind" value="content"><input type="hidden" name="content_type" value="scholar"><input type="hidden" name="content_action" value="<?= e($action) ?>"><input type="hidden" name="id" value="<?= (int)$scholarDraft['id'] ?>"><button class="text-button"><?= $scholarCopy($label) ?></button></form><?php endforeach; ?>
</div><details class="inventory-edit"><summary><?= $scholarCopy('تعديل التفاصيل') ?></summary>
<?php endif; ?>
<form class="form-stack" method="post" enctype="multipart/form-data" action="/admin#<?= $editingScholar ? 'admin-content' : 'admin-create' ?>" data-scholar-form>
<?= csrf_field() ?><input type="hidden" name="kind" value="<?= $editingScholar ? 'content' : 'scholar' ?>">
<?php if ($editingScholar): ?><input type="hidden" name="content_type" value="scholar"><input type="hidden" name="content_action" value="update"><input type="hidden" name="id" value="<?= (int)$scholarDraft['id'] ?>"><?php endif; ?>
<label><?= $scholarCopy('الاسم') ?><input name="name" minlength="2" maxlength="180" value="<?= e((string)($scholarDraft['name'] ?? '')) ?>" required></label>
<label><?= $scholarCopy('السيرة المختصرة') ?><textarea name="biography" maxlength="6000" rows="4"><?= e((string)($scholarDraft['biography'] ?? '')) ?></textarea></label>
<label><?= $scholarCopy('اللغة') ?><select name="language"><?php foreach (['ar'=>'العربية','en'=>'الإنجليزية','other'=>'أخرى'] as $language=>$label): ?><option value="<?= $language ?>" <?= ($scholarDraft['language'] ?? 'ar') === $language ? 'selected' : '' ?>><?= $scholarCopy($label) ?></option><?php endforeach; ?></select></label>
<label><?= $scholarCopy('المصدر المعتمد (اختياري)') ?><input type="url" name="source_url" dir="ltr" maxlength="500" value="<?= e((string)($scholarDraft['source_url'] ?? '')) ?>"></label>
<label><?= $scholarCopy('بيان الحقوق/الإذن (اختياري)') ?><input name="rights_basis" maxlength="500" value="<?= e((string)($scholarDraft['rights_basis'] ?? '')) ?>"></label>
<?php if (!empty($scholarDraft['image_key'])): ?><img class="scholar-portrait-admin" width="100" src="/admin/scholar-image?key=<?= rawurlencode($scholarDraft['image_key']) ?>" alt="<?= e($scholarDraft['name']) ?>" loading="lazy"><?php endif; ?>
<label><?= $scholarCopy('الصورة (اختيارية، JPG أو PNG أو WebP، حتى 3 MB)') ?><input type="file" name="scholar_image" accept="image/jpeg,image/png,image/webp"></label>
<label><?= $scholarCopy('ترتيب العرض') ?><input type="number" name="sort_order" dir="ltr" min="-100000" max="100000" value="<?= e((string)($scholarDraft['sort_order'] ?? 0)) ?>" required></label>
<label class="check-label"><input type="checkbox" name="publish" <?= !empty($scholarDraft['is_published']) ? 'checked' : '' ?>><?= $scholarCopy('نشر') ?></label>
<button class="button button-primary"><?= $scholarCopy($editingScholar ? 'حفظ التعديلات' : 'حفظ العالم') ?></button>
</form>
<?php if (!$editingScholar): ?></article><?php else: ?></details><?php endif; ?>
