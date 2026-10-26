<?php
$auditText = static fn(string $ar, string $en): string => e($adminLocale === 'en' ? $en : $ar);
?>
<section class="admin-inventory admin-panel" id="admin-audit" data-admin-panel hidden>
    <div class="section-heading"><div><span class="eyebrow"><?= $auditText('الشفافية والأمان','Transparency and security') ?></span><h2><?= $auditText('سجل العمليات','Audit log') ?></h2></div><span class="source-chip"><?= number_format($auditCount) ?> <?= $auditText('عملية مطابقة','matching entries') ?></span></div>
    <p class="source-note"><?= $auditText('السجل تشغيلي ومختصر؛ البريد مخفي ولا تُعرض كلمات المرور أو الرموز أو بيانات العبادة الخاصة.','This journal contains operational metadata with masked emails. Passwords, tokens and private worship data are excluded.') ?></p>
    <form class="form-stack admin-audit-search-form" method="get" action="/admin#admin-audit">
        <label><?= $auditText('البحث بالإجراء أو نوع/رقم الهدف','Search by action, target type or target ID') ?><input type="search" dir="auto" name="audit_q" maxlength="80" value="<?= e($auditSearch) ?>" placeholder="<?= $auditText('مثال: admin-user أو user أو 42','For example: admin-user, user or 42') ?>"></label>
        <div class="hero-actions"><button class="button button-primary" type="submit"><?= $auditText('بحث','Search') ?></button><?php if ($auditSearch !== ''): ?><a class="button button-secondary" href="/admin#admin-audit"><?= $auditText('مسح البحث','Clear search') ?></a><?php endif; ?></div>
    </form>
    <?php if (!$auditRows): ?><p class="source-note"><?= $auditText('لا توجد عمليات مطابقة.','No matching operations.') ?></p><?php else: ?>
    <div class="knowledge-results admin-audit-list">
        <?php foreach ($auditRows as $audit): $auditTime = new DateTimeImmutable($audit['created_at'], new DateTimeZone('UTC')); ?>
        <article class="knowledge-card admin-audit-row" data-admin-audit data-audit-id="<?= (int) $audit['id'] ?>">
            <strong dir="ltr"><?= e($audit['action']) ?></strong>
            <p><?= $auditText('نفّذها','Actor') ?>: <bdi><?= e($audit['actor_email']) ?></bdi> · <?= $auditText('الحساب المستهدف','Target account') ?>: <bdi><?= e($audit['target_email']) ?></bdi></p>
            <time dir="ltr" datetime="<?= e($auditTime->format(DATE_ATOM)) ?>"><?= e($auditTime->format('Y-m-d H:i:s')) ?> UTC</time>
            <details><summary><?= $auditText('تفاصيل العملية','Operation details') ?></summary>
                <dl class="admin-user-stats">
                    <div><dt><?= $auditText('رقم السجل','Record ID') ?></dt><dd>#<?= (int) $audit['id'] ?></dd></div>
                    <div><dt><?= $auditText('نوع الهدف','Target type') ?></dt><dd><?= e($audit['target_type']) ?></dd></div>
                    <div><dt><?= $auditText('رقم الهدف','Target ID') ?></dt><dd><?= e($audit['target_id']) ?></dd></div>
                </dl>
            </details>
        </article>
        <?php endforeach; ?>
    </div>
    <?php if ($auditPageCount > 1): ?><nav class="admin-pagination" aria-label="<?= $auditText('صفحات سجل العمليات','Audit log pages') ?>">
        <?php if ($auditPage > 1): ?><a class="button button-secondary" href="/admin?<?= e(http_build_query(['audit_q'=>$auditSearch,'audit_page'=>$auditPage-1])) ?>#admin-audit"><?= $auditText('الأحدث','Newer') ?></a><?php endif; ?>
        <span><?= $auditText('صفحة','Page') ?> <?= $auditPage ?> <?= $auditText('من','of') ?> <?= $auditPageCount ?></span>
        <?php if ($auditPage < $auditPageCount): ?><a class="button button-secondary" href="/admin?<?= e(http_build_query(['audit_q'=>$auditSearch,'audit_page'=>$auditPage+1])) ?>#admin-audit"><?= $auditText('الأقدم','Older') ?></a><?php endif; ?>
    </nav><?php endif; ?>
    <?php endif; ?>
</section>
