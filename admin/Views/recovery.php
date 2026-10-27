<?php
declare(strict_types=1);
if (!admin_can($admin, 'recovery')) return;
$resetEnvironment = strtolower((string) (getenv('APP_ENV') ?: 'production'));
$resetAllowed = in_array($resetEnvironment, ['local','development'], true);
$restoreCandidates = array_filter($backupArtifacts, static fn(array $item): bool => in_array($item['status'], ['ready','downloaded'], true) && new DateTimeImmutable((string) $item['expires_at'], new DateTimeZone('UTC')) > new DateTimeImmutable('now', new DateTimeZone('UTC')));
?>
<section class="owner-operations admin-panel" id="admin-backups" data-admin-panel hidden>
    <div class="section-heading"><div><span class="eyebrow"><?= $recoveryCopy('عمليات المالك','Owner operations') ?></span><h2><?= $recoveryCopy('النسخ الاحتياطية والاسترداد','Backup & recovery') ?></h2></div></div>
    <p class="source-note"><?= $recoveryCopy('تحتوي النسخ المشفرة على بيانات التطبيق وملفات الكتب وصور العلماء المسجلة. تستثني إعدادات الخدمات ومفاتيح التشفير ورموز الدخول والسجلات المؤقتة ووسائط YouTube. احفظ مفتاح النسخ المستقل خارج التطبيق.','Encrypted backups contain application data and registered book and scholar files. They exclude service settings, encryption keys, sign-in tokens, temporary logs and YouTube media. Keep the separate backup key outside the application.') ?></p>
    <?php if ($backupGrantReady): ?>
    <form method="post" action="/admin/backup-download" class="backup-download-form">
        <?= csrf_field() ?><p><?= $recoveryCopy('إذن التنزيل صالح لمدة 15 دقيقة ولمرة واحدة.','The download grant lasts 15 minutes and can be used once.') ?></p>
        <button class="button button-primary"><?= $recoveryCopy('تنزيل النسخة المشفرة','Download encrypted backup') ?></button>
    </form>
    <?php endif; ?>
    <article class="knowledge-card backup-create-card">
        <h3><?= $recoveryCopy('إنشاء نسخة كاملة','Create a full backup') ?></h3>
        <form class="form-stack" method="post" action="/admin#admin-backups">
            <?= csrf_field() ?><input type="hidden" name="kind" value="backup-create">
            <label><?= $recoveryCopy('كلمة مرور المالك','Owner password') ?><input type="password" name="password" autocomplete="current-password" required></label>
            <label><?= $recoveryCopy('اكتب عبارة التأكيد حرفيًا','Enter the confirmation exactly') ?><code dir="ltr">CREATE FULL BACKUP</code><input name="manifest_confirmation" dir="ltr" autocomplete="off" required></label>
            <button class="button button-primary"><?= $recoveryCopy('إنشاء النسخة والتحقق منها','Create and verify backup') ?></button>
        </form>
    </article>
    <div class="backup-list"><h3><?= $recoveryCopy('النسخ المتاحة','Available backups') ?></h3>
    <?php if (!$backupArtifacts): ?><p class="source-note"><?= $recoveryCopy('لا توجد نسخ متاحة بعد.','No backups are available yet.') ?></p><?php endif; ?>
    <?php foreach ($backupArtifacts as $backup): ?>
        <article class="knowledge-card backup-row"><div>
            <strong><?= $recoveryCopy('نسخة','Backup') ?> #<?= (int) $backup['id'] ?></strong>
            <p><?= $recoveryCopy('المخطط','Schema') ?> <bdi><?= e($backup['schema_version']) ?></bdi> · <?= number_format((int) $backup['byte_size'] / 1048576, 2) ?> MB</p>
            <p><?= $backup['status'] === 'ready' ? $recoveryCopy('جاهزة','Ready') : ($backup['status'] === 'downloaded' ? $recoveryCopy('تم تنزيلها','Downloaded') : $recoveryCopy('منتهية','Expired')) ?></p>
            <p><?= $recoveryCopy('تنتهي','Expires') ?>: <bdi><?= e($backup['expires_at']) ?> UTC</bdi></p>
        </div>
        <?php if ($backup['status'] === 'ready'): ?>
        <form method="post" class="grant-form form-stack" action="/admin#admin-backups">
            <?= csrf_field() ?><input type="hidden" name="kind" value="backup-grant"><input type="hidden" name="backup_id" value="<?= (int) $backup['id'] ?>">
            <label><?= $recoveryCopy('كلمة مرور المالك','Owner password') ?><input type="password" name="password" autocomplete="current-password" required></label>
            <button class="button button-secondary"><?= $recoveryCopy('إصدار إذن تنزيل 15 دقيقة','Issue a 15-minute download grant') ?></button>
        </form>
        <?php endif; ?></article>
    <?php endforeach; ?></div>
    <article class="knowledge-card backup-create-card">
        <h3><?= $recoveryCopy('استعادة نسخة كاملة','Restore a full backup') ?></h3>
        <p class="source-note"><?= $recoveryCopy('تستبدل الاستعادة بيانات التطبيق وملفاته المسجلة بمحتوى النسخة، وتبطل جلسات الدخول السابقة. يبقى مخطط قاعدة البيانات وإعدادات الخدمات. يلزم مفتاح النسخة وإصدار مخطط مطابق.','Restoration replaces application data and registered files with the backup and revokes previous sign-in sessions. The database schema and service settings remain. The backup key and matching schema version are required.') ?></p>
        <?php if (!$restoreCandidates): ?><p class="source-note"><?= $recoveryCopy('لا توجد نسخة صالحة للاستعادة. أنشئ نسخة أولًا.','No unexpired backup is available to restore. Create a backup first.') ?></p><?php endif; ?>
        <form class="form-stack" method="post" action="/admin#admin-backups" data-confirm="<?= $recoveryCopy('سيتم استبدال بيانات التطبيق وملفاته بالنسخة المختارة. هل تريد المتابعة؟','Replace application data and files with this backup?') ?>">
            <?= csrf_field() ?><input type="hidden" name="kind" value="backup-restore">
            <label><?= $recoveryCopy('النسخة','Backup') ?><select name="backup_id" required <?= !$restoreCandidates ? 'disabled' : '' ?>>
                <?php foreach ($restoreCandidates as $candidate): ?><option value="<?= (int) $candidate['id'] ?>">#<?= (int) $candidate['id'] ?> · <?= e($candidate['schema_version']) ?> · <?= e($candidate['expires_at']) ?> UTC</option><?php endforeach; ?>
            </select></label>
            <label><?= $recoveryCopy('كلمة مرور المالك','Owner password') ?><input type="password" name="password" autocomplete="current-password" required></label>
            <label><?= $recoveryCopy('اكتب عبارة التأكيد حرفيًا','Enter the confirmation exactly') ?><code dir="ltr">RESTORE FULL BACKUP</code><input name="restore_confirmation" dir="ltr" autocomplete="off" required></label>
            <button class="button button-danger" <?= !$restoreCandidates ? 'disabled' : '' ?>><?= $recoveryCopy('استعادة البيانات والملفات','Restore data and files') ?></button>
        </form>
    </article>
    <article class="knowledge-card backup-create-card">
        <h3><?= $recoveryCopy('إعادة ضبط محلية','Local reset') ?></h3>
        <p class="source-note"><?= $recoveryCopy('تحذف بيانات المستخدمين والمحتوى والتقدم والملفات المسجلة. تحتفظ بالمخطط وسجل الترحيلات والمالك والنسخ الاحتياطية. يلزم إنشاء نسخة صالحة تم التحقق منها خلال 24 ساعة، ويُفحص ملفها ومفتاحها قبل الحذف. تعمل في بيئة محلية أو تطوير فقط.','Deletes user data, content, progress and registered files. Preserves the schema, migration ledger, Owner and backups. Requires a valid backup verified within 24 hours; its file and key are checked before deletion. Available only in local or development environments.') ?></p>
        <p><?= $recoveryCopy('البيئة الحالية','Current environment') ?>: <code dir="ltr"><?= e($resetEnvironment) ?></code></p>
        <?php if (!$resetAllowed): ?><p class="source-note"><?= $recoveryCopy('إعادة الضبط غير متاحة في هذه البيئة.','Reset is unavailable in this environment.') ?></p><?php endif; ?>
        <form class="form-stack" method="post" action="/admin#admin-backups" data-confirm="<?= $recoveryCopy('سيُحذف المحتوى والبيانات الشخصية والملفات المسجلة. هل تريد المتابعة؟','Delete content, personal data and registered files?') ?>">
            <?= csrf_field() ?><input type="hidden" name="kind" value="local-reset">
            <label><?= $recoveryCopy('اكتب البيئة الحالية حرفيًا','Enter the current environment exactly') ?><input name="environment_confirmation" dir="ltr" autocomplete="off" required></label>
            <label><?= $recoveryCopy('كلمة مرور المالك','Owner password') ?><input type="password" name="password" autocomplete="current-password" required></label>
            <label><?= $recoveryCopy('اكتب عبارة الحذف حرفيًا','Enter the deletion confirmation exactly') ?><code dir="ltr">RESET ATHAR DATA</code><input name="reset_confirmation" dir="ltr" autocomplete="off" required></label>
            <button class="button button-danger" <?= !$resetAllowed ? 'disabled' : '' ?>><?= $recoveryCopy('إعادة الضبط المحلية','Reset local data') ?></button>
        </form>
    </article>
</section>
