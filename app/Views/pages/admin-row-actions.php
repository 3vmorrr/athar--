<div class="inventory-actions">
    <form method="post"><?= csrf_field() ?><input type="hidden" name="kind" value="content"><input type="hidden" name="content_type" value="<?= e($contentType) ?>"><input type="hidden" name="content_action" value="toggle"><input type="hidden" name="id" value="<?= $rowId ?>"><button class="text-button" type="submit">تبديل النشر</button></form>
    <form method="post" data-confirm="حذف هذا المحتوى نهائيًا؟"><?= csrf_field() ?><input type="hidden" name="kind" value="content"><input type="hidden" name="content_type" value="<?= e($contentType) ?>"><input type="hidden" name="content_action" value="delete"><input type="hidden" name="id" value="<?= $rowId ?>"><button class="text-button" type="submit">حذف</button></form>
</div>
