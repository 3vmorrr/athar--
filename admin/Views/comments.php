<?php
$commentUrl=static fn(int $page):string=>'/admin?'.http_build_query(['comments_page'=>$page,'comments_status'=>$commentFilter]).'#admin-comments';
$commentLabels=['pending'=>['بانتظار المراجعة','Pending review'],'visible'=>['منشور','Published'],'hidden'=>['مخفي','Hidden']];
?>
<section class="admin-inventory admin-panel" id="admin-comments" data-admin-panel hidden>
<div class="section-heading"><h2><?= $editorialCopy('مراجعة تعليقات الدروس','Lesson comment moderation') ?></h2><span class="source-chip"><?= $commentCount === null ? $editorialCopy('غير متاح','Unavailable') : (int)$commentCount.' '.$editorialCopy('تعليقًا مطابقًا','matching comments') ?></span></div>
<?php if ($commentCount === null): ?><p class="notice notice-error"><?= $editorialCopy('تتطلب المراجعة بريدًا إداريًا مؤكدًا.','Moderation requires a verified Admin email address.') ?></p><?php else: ?>
<p class="source-note"><?= $editorialCopy('كل قرار يتطلب سببًا ويحفظ نسخة وسجلًا تشغيليًا. راجع الردود واحذفها أولًا قبل حذف التعليق الأصلي.','Each decision requires a reason and records a version and operational audit. Review and delete replies before deleting their original comment.') ?></p>
<form method="get" action="/admin#admin-comments" class="form-stack"><label><?= $editorialCopy('حالة التعليقات','Comment status') ?><select name="comments_status"><option value="all"><?= $editorialCopy('الكل','All') ?></option><?php foreach ($commentLabels as $state=>$label): ?><option value="<?= $state ?>" <?= $state === $commentFilter ? 'selected' : '' ?>><?= $editorialCopy(...$label) ?></option><?php endforeach; ?></select></label><button class="button button-secondary"><?= $editorialCopy('عرض','Show') ?></button></form>
<?php if (!$pendingComments): ?><p class="source-note"><?= $editorialCopy('لا توجد تعليقات مطابقة.','No matching comments.') ?></p><?php else: ?><div class="knowledge-results admin-comment-list">
<?php foreach ($pendingComments as $comment):
    $commentDraft=$adminError !== '' && in_array($_POST['kind'] ?? '',['comment-review','comment-delete'],true) && (int)($_POST['comment_id'] ?? 0) === (int)$comment['id'];
    $commentRevision=$commentDraft ? (int)(filter_var($_POST['expected_revision'] ?? 0,FILTER_VALIDATE_INT) ?: 0) : (int)$comment['revision'];
    $commentConflict=$commentDraft && $commentRevision !== (int)$comment['revision'];
    $reason=$commentDraft && is_string($_POST['reason'] ?? null) ? mb_substr($_POST['reason'],0,501,'UTF-8') : '';
?>
<article class="knowledge-card admin-comment-card" data-comment-record="<?= (int)$comment['id'] ?>"><h3><?= e($comment['lesson_title']) ?></h3>
<p><?= e($comment['display_name'] ?: '@'.$comment['username']) ?> · <bdi><?= e($comment['created_at']) ?> UTC</bdi></p>
<p><?= $editorialCopy(...$commentLabels[$comment['status']]) ?> · <?= $editorialCopy('النسخة','Version') ?> <?= (int)$comment['revision'] ?> · <?= $comment['parent_id'] === null ? $editorialCopy('تعليق أصلي','Original comment') : $editorialCopy('رد على التعليق','Reply to comment').' #'.(int)$comment['parent_id'] ?></p>
<p><?= nl2br(e($comment['body'])) ?></p>
<?php if ($comment['parent_id'] !== null): ?><p class="source-note"><?= $editorialCopy('ظهور الرد للقراء الآخرين يعتمد أيضًا على نشر التعليق الأصلي.','Visibility of this reply to other readers also depends on its original comment being public.') ?></p><?php endif; ?>
<?php if ($comment['moderation_reason'] !== null): ?><p class="source-note"><?= $editorialCopy('سبب القرار المحفوظ','Saved decision reason') ?>: <?= e($comment['moderation_reason']) ?></p><?php endif; ?>
<?php if ($commentConflict): ?><p class="notice notice-error" data-comment-conflict><?= $editorialCopy('احتُفظ بسبب قرارك دون الكتابة فوق مراجعة أحدث.','Your unsaved reason is retained; a newer review has not been overwritten.') ?> <a href="<?= e(str_replace('#admin-comments','&comments_reload='.(int)$comment['id'].'#admin-comments',$commentUrl($commentPage))) ?>"><?= $editorialCopy('تحميل النسخة الحالية','Load current version') ?></a></p><?php endif; ?>
<form method="post" action="<?= e($commentUrl($commentPage)) ?>" class="form-stack" data-comment-form>
<?= csrf_field() ?><input type="hidden" name="kind" value="comment-review"><input type="hidden" name="comment_id" value="<?= (int)$comment['id'] ?>"><input type="hidden" name="expected_revision" value="<?= (int)$commentRevision ?>">
<label><?= $editorialCopy('سبب القرار','Decision reason') ?><textarea name="reason" minlength="2" maxlength="500" rows="2" required><?= e($reason) ?></textarea></label>
<div class="hero-actions"><?php foreach (['visible'=>['نشر','Publish'],'hidden'=>['إخفاء','Hide'],'delete'=>['حذف','Delete']] as $decision=>$label): ?><button class="button <?= $decision === 'delete' ? 'button-danger' : 'button-secondary' ?>" name="decision" value="<?= $decision ?>" <?= $decision === 'delete' ? 'data-comment-delete-confirm="'.$editorialCopy('حذف هذا التعليق نهائيًا؟ سيُحتفظ بسجل القرار.','Permanently delete this comment? Its decision record is retained.').'"' : '' ?> <?= $commentConflict ? 'disabled' : '' ?>><?= $editorialCopy(...$label) ?></button><?php endforeach; ?></div>
</form></article><?php endforeach; ?></div><?php endif; ?>
<?php if ($commentPageCount > 1): ?><nav class="admin-pagination" aria-label="<?= $editorialCopy('صفحات التعليقات','Comment pages') ?>"><?php if ($commentPage>1): ?><a class="button button-secondary" href="<?= e($commentUrl($commentPage-1)) ?>"><?= $editorialCopy('الأحدث','Newer') ?></a><?php endif; ?><span><?= $editorialCopy('صفحة','Page') ?> <?= (int)$commentPage ?> <?= $editorialCopy('من','of') ?> <?= (int)$commentPageCount ?></span><?php if ($commentPage<$commentPageCount): ?><a class="button button-secondary" href="<?= e($commentUrl($commentPage+1)) ?>"><?= $editorialCopy('الأقدم','Older') ?></a><?php endif; ?></nav><?php endif; ?>
<?php endif; ?></section>
