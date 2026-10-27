<?php
$editingBook=($bookFormMode ?? '') === 'edit'; $bookDraft=$bookDraft ?? [];
$bookVersion=$editingBook ? admin_book_version($bookDraft) : '';
$bookSavedVersion=$bookVersion;
$bookHasDraft=$adminError !== '' && (!$editingBook ? ($_POST['kind'] ?? '') === 'book' : ($_POST['content_type'] ?? '') === 'book' && ($_POST['content_action'] ?? '') === 'update' && (int)($_POST['id'] ?? 0) === (int)$bookDraft['id']);
$bookConflict=false;
if ($bookHasDraft) {
    foreach (['title','author','description','keywords','language','publication_year','rights_holder','source_url','download_url','rights_basis'] as $field) if (is_string($_POST[$field] ?? null)) $bookDraft[$field]=mb_substr($_POST[$field],0,12001,'UTF-8');
    $bookDraft['is_published']=isset($_POST['publish']);
    if ($editingBook && is_string($_POST['expected_state'] ?? null)) { $bookConflict=!hash_equals($bookVersion,$_POST['expected_state']); $bookVersion=$_POST['expected_state']; }
}
$bookUnsafeLink=false;
foreach (['source_url','download_url'] as $field) {
    $bookLinkValue=(string)($bookDraft[$field] ?? '');
    if ($bookLinkValue !== '' && !\Athar\Catalog\BookAccess::https($bookLinkValue)) {$bookDraft[$field]='';$bookUnsafeLink=true;}
}
?>
<?php if (!$editingBook): ?><article class="knowledge-card admin-book-create"><h2><?= $bookCopy('إضافة كتاب','Add a book') ?></h2><?php else: ?>
<div class="inventory-actions"><?php foreach (['toggle'=>['تبديل النشر','Toggle publication'],'delete'=>['حذف','Delete']] as $action=>$label): ?><form method="post" action="/admin#admin-content" <?= $action === 'delete' ? 'data-confirm="'.$bookCopy('حذف الكتاب ونسخته المحلية نهائيًا؟','Permanently delete this book and its local copy?').'"' : '' ?>><?= csrf_field() ?><input type="hidden" name="kind" value="content"><input type="hidden" name="content_type" value="book"><input type="hidden" name="content_action" value="<?= $action ?>"><input type="hidden" name="id" value="<?= (int)$bookDraft['id'] ?>"><input type="hidden" name="expected_state" value="<?= e($bookSavedVersion) ?>"><button class="text-button" <?= $bookConflict ? 'disabled' : '' ?>><?= $bookCopy(...$label) ?></button></form><?php endforeach; ?></div>
<details class="inventory-edit" <?= $bookHasDraft ? 'open' : '' ?>><summary><?= $bookCopy('تعديل التفاصيل','Edit details') ?></summary><?php endif; ?>
<?php if ($bookConflict): ?><p class="notice notice-error" data-book-conflict><?= $bookCopy('احتُفظ بمسودتك دون الكتابة فوق النسخة الأحدث.','Your unsaved draft is retained; the newer version has not been overwritten.') ?> <a href="/admin?content_reload=book-<?= (int)$bookDraft['id'] ?>#admin-content"><?= $bookCopy('تحميل النسخة الحالية وتجاهل المسودة','Load current version and discard draft') ?></a></p><?php endif; ?>
<form class="form-stack" method="post" action="/admin#<?= $editingBook ? 'admin-content' : 'admin-create' ?>" data-book-form>
<?= csrf_field() ?><input type="hidden" name="kind" value="<?= $editingBook ? 'content' : 'book' ?>">
<?php if ($bookUnsafeLink): ?><p class="notice notice-error"><?= $bookCopy('رابط غير آمن أو غير صالح لم يُعرض هنا. أدخل رابط HTTPS عامًا قبل الحفظ.','An unsafe or invalid link is hidden here. Enter a public HTTPS link before saving.') ?></p><?php endif; ?>
<?php if ($editingBook): ?><input type="hidden" name="content_type" value="book"><input type="hidden" name="content_action" value="update"><input type="hidden" name="id" value="<?= (int)$bookDraft['id'] ?>"><input type="hidden" name="expected_state" value="<?= e($bookVersion) ?>"><?php endif; ?>
<?php foreach (['title'=>['العنوان','Title',240],'author'=>['المؤلف','Author',180],'keywords'=>['كلمات مفتاحية','Keywords',1000],'rights_holder'=>['صاحب الحقوق','Rights holder',180],'rights_basis'=>['أساس الحقوق أو الإذن','Rights basis or permission',500]] as $field=>$label): ?><label><?= $bookCopy($label[0],$label[1]) ?><input name="<?= $field ?>" maxlength="<?= $label[2] ?>" value="<?= e((string)($bookDraft[$field] ?? '')) ?>" <?= in_array($field,['title','author','rights_basis'],true) ? 'required' : '' ?>></label><?php endforeach; ?>
<label><?= $bookCopy('الوصف','Description') ?><textarea name="description" maxlength="12000" rows="3"><?= e((string)($bookDraft['description'] ?? '')) ?></textarea></label>
<div class="admin-form-grid"><label><?= $bookCopy('اللغة','Language') ?><select name="language"><?php foreach (['ar'=>['العربية','Arabic'],'en'=>['الإنجليزية','English'],'other'=>['أخرى','Other']] as $code=>$label): ?><option value="<?= $code ?>" <?= ($bookDraft['language'] ?? 'ar') === $code ? 'selected' : '' ?>><?= $bookCopy(...$label) ?></option><?php endforeach; ?></select></label><label><?= $bookCopy('سنة النشر','Publication year') ?><input type="number" name="publication_year" dir="ltr" min="1000" max="2200" value="<?= e((string)($bookDraft['publication_year'] ?? '')) ?>"></label></div>
<label><?= $bookCopy('رابط المصدر','Source link') ?><input type="url" dir="ltr" name="source_url" maxlength="500" value="<?= e((string)($bookDraft['source_url'] ?? '')) ?>" required></label>
<label><?= $bookCopy('رابط تنزيل مباشر (اختياري)','Direct download link (optional)') ?><input type="url" dir="ltr" name="download_url" maxlength="500" value="<?= e((string)($bookDraft['download_url'] ?? '')) ?>"><small><?= $bookCopy('بدون رابط يُعرض الكتاب في الفهرس مع مصدره وحقوقه، دون زر تنزيل. لا يلزم رفع ملف.','Without a link, the book remains in the catalog with its source and rights, without a download action. A file upload is not required.') ?></small></label>
<?php if (!empty($bookDraft['file_key'])): ?><p class="source-note"><?= $bookCopy('تعديل البيانات يحتفظ بالنسخة المحلية الحالية. التنزيل يتبع الرابط المباشر وحقوق الإتاحة.','Metadata edits preserve the existing local copy. Download availability follows the direct link and rights basis.') ?></p><?php endif; ?>
<label class="check-label"><input type="checkbox" name="publish" <?= !empty($bookDraft['is_published']) ? 'checked' : '' ?>><?= $bookCopy('نشر','Publish') ?></label>
<button class="button button-primary" <?= $bookConflict ? 'disabled' : '' ?>><?= $bookCopy($editingBook ? 'حفظ التعديلات' : 'حفظ الكتاب',$editingBook ? 'Save changes' : 'Save book') ?></button>
</form><?php if ($editingBook): ?></details><?php else: ?></article><?php endif; ?>
