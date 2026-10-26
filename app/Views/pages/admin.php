<?php
$adminLocale = $adminLocale ?? 'ar';
$adminShellCopy = $adminLocale === 'en' ? [
    'eyebrow' => 'Platform administration',
    'title' => 'Athar Dashboard',
    'lede' => 'Operate content and services from an isolated administration workspace.',
    'sidebar' => 'Platform',
    'nav_label' => 'Administration sections',
    'overview' => 'Overview & analytics',
    'create' => 'Create content',
    'content' => 'Saved content',
    'events' => 'Upcoming & Live',
    'comments' => 'Comment moderation',
    'campaigns' => 'Email campaigns',
    'services' => 'Service settings',
    'night_prayer' => 'Night Prayer defaults',
    'users' => 'User management',
    'backups' => 'Backup & recovery',
    'audit' => 'Audit log',
    'note' => 'Choose a section to display its tools and data.',
] : [
    'eyebrow' => 'إدارة المنصة',
    'title' => 'لوحة أثر',
    'lede' => 'أدِر المحتوى والخدمات من مساحة إدارة مستقلة عن واجهة التصفح العامة.',
    'sidebar' => 'إدارة المنصة',
    'nav_label' => 'أقسام لوحة الإدارة',
    'overview' => 'نظرة عامة وتحليلات',
    'create' => 'إضافة محتوى',
    'content' => 'المحتوى المحفوظ',
    'events' => 'القادم والمباشر',
    'comments' => 'مراجعة التعليقات',
    'campaigns' => 'حملات البريد',
    'services' => 'إعدادات الخدمات',
    'night_prayer' => 'إعدادات قيام الليل',
    'users' => 'إدارة المستخدمين',
    'backups' => 'النسخ والاستعادة',
    'audit' => 'سجل العمليات',
    'note' => 'اختر قسمًا لعرض أدواته وبياناته.',
];
?>
<section class="knowledge-page section-wrap admin-page">
    <header class="knowledge-heading">
        <span class="eyebrow"><?= e($adminShellCopy['eyebrow']) ?></span>
        <h1><?= e($adminShellCopy['title']) ?></h1>
        <p><?= e($adminShellCopy['lede']) ?></p>
    </header>
    <?php if ($adminError !== ''): ?><div class="notice notice-error" role="alert"><?= e($adminError) ?></div><?php endif; ?>
    <div class="admin-layout">
        <aside class="admin-sidebar" id="admin-primary-nav">
            <strong class="admin-sidebar-title"><?= e($adminShellCopy['sidebar']) ?></strong>
            <nav class="admin-sidebar-nav" aria-label="<?= e($adminShellCopy['nav_label']) ?>" data-admin-nav>
                <a href="#admin-overview" aria-current="location" aria-controls="admin-overview"><?= e($adminShellCopy['overview']) ?></a>
                <a href="#admin-create" aria-controls="admin-create"><?= e($adminShellCopy['create']) ?></a>
                <a href="#admin-content" aria-controls="admin-content"><?= e($adminShellCopy['content']) ?></a>
                <a href="#admin-events" aria-controls="admin-events"><?= e($adminShellCopy['events']) ?> <span><?= count($lessonEvents) ?></span></a>
                <a href="#admin-comments" aria-controls="admin-comments"><?= e($adminShellCopy['comments']) ?> <span><?= (int) $dashboardMetrics['pending_comments'] ?></span></a>
                <?php if (admin_can($admin, 'campaigns')): ?><a href="#admin-campaigns" aria-controls="admin-campaigns"><?= e($adminShellCopy['campaigns']) ?></a><?php endif; ?>
                <?php if (admin_can($admin, 'services')): ?><a href="#admin-services" aria-controls="admin-services"><?= e($adminShellCopy['services']) ?></a><?php endif; ?>
                <?php if (admin_can($admin, 'night_prayer_defaults')): ?><a href="#admin-night-prayer" aria-controls="admin-night-prayer"><?= e($adminShellCopy['night_prayer']) ?></a><?php endif; ?>
                <?php if (admin_can($admin, 'users')): ?><a href="#admin-users" aria-controls="admin-users"><?= e($adminShellCopy['users']) ?> <span><?= $managedUserTotal ?></span></a><?php endif; ?>
                <?php if (admin_can($admin, 'recovery')): ?><a href="#admin-backups" aria-controls="admin-backups"><?= e($adminShellCopy['backups']) ?></a><?php endif; ?>
                <?php if (admin_can($admin, 'audit')): ?><a href="#admin-audit" aria-controls="admin-audit"><?= e($adminShellCopy['audit']) ?></a><?php endif; ?>
            </nav>
            <p class="admin-sidebar-note"><?= e($adminShellCopy['note']) ?></p>
        </aside>
        <div class="admin-content">
    <?php if (admin_can($admin, 'services')): ?>
    <section class="admin-inventory admin-panel" id="admin-services" data-admin-panel hidden>
        <div class="section-heading"><div><span class="eyebrow">خدمات المنصة</span><h2>إعدادات الخدمات</h2></div></div>
        <p class="source-note">يُنشئ الموقع مفتاح تشفير محليًا تلقائيًا داخل storage خارج مجلد الويب. في الإنتاج استخدم مخزن أسرار الخادم لمفتاح ATHAR_SETTINGS_KEY، واضبط ATHAR_BASE_URL على رابط الموقع العام حتى يظهر الشعار وتعمل روابط البريد؛ لا تضف المفاتيح إلى Git.</p>
        <article class="knowledge-card admin-service-card">
            <h3>بريد Gmail لرموز OTP والحملات</h3>
            <p class="source-note">يحفظ App Password مشفرًا ولا يعرضه مرة أخرى. إرسال الاختبار متاح فقط إلى مستلم اختبار ثابت تم التصريح به وحفظه من المالك.</p>
            <div class="admin-service-diagnostics">
                <span>بيانات SMTP: <strong><?= $serviceDiagnostics['smtp_credentials'] ? 'مضبوطة' : 'غير مكتملة' ?></strong></span>
                <span>مفتاح التشفير: <strong><?= e($serviceDiagnostics['settings_key_source']) ?></strong></span>
                <span>Base URL: <strong dir="ltr"><?= e($serviceDiagnostics['base_url']) ?></strong></span>
                <span>HTTPS للإنتاج: <strong><?= $serviceDiagnostics['base_url_https'] ? 'نعم' : 'لا/محلي' ?></strong></span>
                <span>مستلم الاختبار: <strong><?= e($smtpTestRecipientMasked) ?></strong></span>
            </div>
            <form class="form-stack" method="post">
                <?= csrf_field() ?><input type="hidden" name="kind" value="gmail-settings">
                <label>بريد Gmail<input name="gmail_email" type="email" maxlength="190" dir="ltr" value="<?= e($smtpConfig['smtp_username']) ?>" autocomplete="username" required></label>
                <label>App Password<input name="smtp_password" type="password" maxlength="500" dir="ltr" autocomplete="new-password" placeholder="<?= $smtpPasswordConfigured ? 'محفوظة؛ اتركها فارغة للإبقاء عليها' : 'أدخل App Password' ?>" <?= $smtpPasswordConfigured ? '' : 'required' ?>></label>
                <hr>
                <label>بريد اختبار جديد (اختياري)<input name="test_recipient" type="email" maxlength="190" dir="ltr" autocomplete="off" placeholder="authorized@example.com"></label>
                <label class="check-label"><input type="checkbox" name="test_recipient_authorized"> أؤكد أن صاحب هذا البريد مصرح له باستلام رسائل اختبار التطوير.</label>
                <?php if ($smtpTestRecipientConfigured): ?><label class="check-label"><input type="checkbox" name="clear_test_recipient"> حذف مستلم الاختبار المحفوظ</label><?php endif; ?>
                <label>كلمة مرور المالك عند تغيير مستلم الاختبار<input type="password" name="owner_password" autocomplete="current-password"></label>
                <div class="hero-actions">
                    <button class="button button-primary" type="submit">حفظ إعدادات Gmail</button>
                    <button class="button button-secondary" type="submit" name="send_test_email" value="1"<?= $smtpTestRecipientConfigured ? '' : ' disabled' ?>>حفظ وإرسال اختبار واحد</button>
                </div>
            </form>
        </article>
        <article class="knowledge-card admin-service-card"><h3>بحث الدرر السنية</h3><p class="source-note">هذا الإعداد خاص بواجهة البحث الحديثي ولا يغيّر بريد Gmail. يلزم رمز API بعد موافقة الدرر.</p>
            <form class="form-stack" method="post"><?= csrf_field() ?><input type="hidden" name="kind" value="dorar-settings"><label>رمز API المعتمد<input name="dorar_api_token" type="password" maxlength="500" autocomplete="new-password" placeholder="<?= $dorarTokenConfigured ? 'محفوظ؛ اتركه فارغًا للإبقاء عليه' : 'أدخل الرمز بعد موافقة الدرر' ?>"></label><?php if ($dorarTokenConfigured): ?><label class="check-label"><input type="checkbox" name="clear_dorar_token"> حذف الرمز المحفوظ</label><?php endif; ?><p class="source-note">حالة الربط: <?= $dorarTokenConfigured ? 'مُعدّ' : 'ينتظر رمز API رسميًا' ?>.</p><button class="button button-secondary" type="submit">حفظ إعدادات الدرر</button></form>
        </article>
    </section>
    <?php endif; ?>
    <?php if (admin_can($admin, 'night_prayer_defaults')): ?>
    <section class="admin-inventory admin-panel" id="admin-night-prayer" data-admin-panel hidden>
        <div class="section-heading">
            <div><span class="eyebrow">إعدادات عامة فقط</span><h2>قيام الليل</h2></div>
            <span class="source-chip">revision <?= (int)$nightPrayerPublicDefaults['revision'] ?></span>
        </div>
        <p class="source-note">هذه الشاشة تدير defaults ونصوص المصدر العامة فقط. لا تقرأ أو تعرض جلسات المستخدمين، الملاحظات الخاصة، بيانات الإعفاءات، أو أي سجل عبادة شخصي.</p>
        <form class="form-stack" method="post">
            <?= csrf_field() ?><input type="hidden" name="kind" value="night-prayer-defaults">
            <label class="check-label"><input type="checkbox" name="enabled"<?= $nightPrayerPublicDefaults['enabled'] ? ' checked' : '' ?>> تفعيل حفظ جلسات قيام الليل</label>
            <div class="admin-form-grid">
                <label>طريقة الحساب الافتراضية<input name="calculation_method" maxlength="40" value="<?= e((string)$nightPrayerPublicDefaults['calculation_method']) ?>" required></label>
                <label>المذهب الافتراضي<select name="madhab"><option value="standard"<?= $nightPrayerPublicDefaults['madhab']==='standard'?' selected':'' ?>>Standard</option><option value="hanafi"<?= $nightPrayerPublicDefaults['madhab']==='hanafi'?' selected':'' ?>>Hanafi</option></select></label>
                <label>قاعدة خطوط العرض العليا<select name="high_latitude_rule"><option value="middle_of_night"<?= $nightPrayerPublicDefaults['high_latitude_rule']==='middle_of_night'?' selected':'' ?>>Middle of night</option><option value="seventh_of_night"<?= $nightPrayerPublicDefaults['high_latitude_rule']==='seventh_of_night'?' selected':'' ?>>Seventh of night</option><option value="angle_based"<?= $nightPrayerPublicDefaults['high_latitude_rule']==='angle_based'?' selected':'' ?>>Angle based</option></select></label>
                <label>هدف الآيات الافتراضي<input type="number" name="target_verses" min="1" max="6236" value="<?= (int)$nightPrayerPublicDefaults['target_verses'] ?>" required></label>
            </div>
            <div class="admin-form-grid">
                <label>اسم المصدر بالعربية<input name="source_name_ar" maxlength="120" value="<?= e((string)$nightPrayerPublicDefaults['source_name_ar']) ?>" required></label>
                <label>Source name<input name="source_name_en" maxlength="120" value="<?= e((string)$nightPrayerPublicDefaults['source_name_en']) ?>" required></label>
            </div>
            <label>رابط المصدر العام<input type="url" name="source_url" maxlength="500" value="<?= e((string)$nightPrayerPublicDefaults['source_url']) ?>" required></label>
            <label>النص التعريفي العربي<textarea name="intro_ar" maxlength="1200" rows="4"><?= e((string)$nightPrayerPublicDefaults['intro_ar']) ?></textarea></label>
            <label>English intro<textarea name="intro_en" maxlength="1200" rows="4"><?= e((string)$nightPrayerPublicDefaults['intro_en']) ?></textarea></label>
            <button class="button button-primary" type="submit">حفظ الإعدادات العامة</button>
        </form>
    </section>
    <?php endif; ?>
    <section class="admin-inventory admin-panel" id="admin-campaigns" data-admin-panel hidden>
        <div class="section-heading"><div><span class="eyebrow">التواصل مع المجتمع</span><h2>حملات البريد الإلكتروني</h2></div><span class="source-chip"><?= number_format($mailingOptInCount) ?> مشتركًا مؤكد البريد</span></div>
        <p class="source-note">تصل الحملة فقط لمن أكد بريده واختار الاشتراك. ابدأ الإرسال مرة واحدة وسيكمل الموقع تلقائيًا على دفعات من 3 رسائل، ويمكنك إيقافه بعد الدفعة الحالية. يتوقف عند 450 رسالة خلال 24 ساعة لترك مساحة لرسائل التحقق. كل رسالة تحمل شعار أثر ورابط إلغاء الاشتراك.</p>
        <article class="knowledge-card admin-campaign-composer"><h3>إنشاء حملة</h3><form class="form-stack" method="post"><?= csrf_field() ?><input type="hidden" name="kind" value="campaign-create"><label>عنوان الرسالة<input name="subject" maxlength="180" minlength="3" required></label><label>نص الرسالة<textarea name="body" maxlength="4000" minlength="10" rows="7" required></textarea><small>نص عادي آمن؛ سيظهر داخل قالب أثر المتوافق مع Gmail والهواتف.</small></label><button class="button button-primary" type="submit" <?= !$mailingOptInCount || !$smtpPasswordConfigured ? 'disabled' : '' ?>>تجهيز الحملة للمشتركين</button></form><?php if (!$smtpPasswordConfigured): ?><p class="source-note">احفظ بريد Gmail وApp Password أولًا من إعدادات الخدمات.</p><?php elseif (!$mailingOptInCount): ?><p class="source-note">لا يوجد مشترك مؤكد البريد بعد. يمكن للمستخدم الاشتراك من بيانات الحساب.</p><?php endif; ?></article>
        <p class="source-note admin-campaign-feedback" data-campaign-feedback role="status" aria-live="polite"></p>
        <button class="button button-secondary" type="button" data-campaign-stop hidden>إيقاف بعد الدفعة الحالية</button>
        <div class="admin-campaign-list"><h3>الحملات السابقة</h3><?php if (!$emailCampaigns): ?><p class="source-note">لم تُنشأ حملات بعد.</p><?php else: ?><?php foreach ($emailCampaigns as $campaign): $campaignStatus = ['draft'=>'جاهزة للإرسال','sending'=>'قيد الإرسال','paused'=>'متوقفة مؤقتًا','completed'=>'مكتملة'][$campaign['status']] ?? $campaign['status']; ?><article class="knowledge-card admin-campaign-row"><div class="admin-campaign-heading"><div><strong><?= e($campaign['subject']) ?></strong><p>أُنشئت <?= e($campaign['created_at']) ?> · بواسطة <?= e($campaign['creator_email'] ?: 'النظام') ?></p></div><span class="source-chip"><?= e($campaignStatus) ?></span></div><div class="admin-campaign-stats"><span><?= (int)$campaign['recipient_sent'] ?> أُرسلت</span><span><?= (int)$campaign['recipient_pending'] ?> متبقية</span><span><?= (int)$campaign['recipient_failed'] ?> فشلت</span><span><?= (int)$campaign['recipient_skipped'] ?> تم تخطيها</span></div><?php if (in_array($campaign['status'], ['draft','sending','paused'], true) && (int)$campaign['recipient_pending'] > 0): ?><form method="post" class="admin-campaign-action" data-campaign-batch><?= csrf_field() ?><input type="hidden" name="kind" value="campaign-batch"><input type="hidden" name="campaign_id" value="<?= (int)$campaign['id'] ?>"><button class="button button-primary" type="submit">بدء الإرسال (3 رسائل لكل دفعة)</button></form><?php endif; ?><?php if ((int)$campaign['recipient_failed'] > 0): ?><form method="post" class="admin-campaign-action" data-confirm="ستُعاد الرسائل الفاشلة فقط إلى قائمة الإرسال."><?= csrf_field() ?><input type="hidden" name="kind" value="campaign-retry-failed"><input type="hidden" name="campaign_id" value="<?= (int)$campaign['id'] ?>"><button class="button button-secondary" type="submit">إعادة محاولة الرسائل الفاشلة</button></form><?php endif; ?></article><?php endforeach; ?><?php endif; ?></div>
    </section>
    <section class="admin-inventory admin-panel" id="admin-overview" data-admin-panel><div class="section-heading"><div><span class="eyebrow">مؤشرات المنصة</span><h2>نظرة عامة وتحليلات</h2></div></div><div class="inventory-grid admin-metric-grid"><article><h3>الحسابات <span><?= (int) $dashboardMetrics['users'] ?></span></h3></article><article><h3>العلماء المنشورون <span><?= (int) $dashboardMetrics['scholars'] ?></span></h3></article><article><h3>السلاسل المنشورة <span><?= (int) $dashboardMetrics['series'] ?></span></h3></article><article><h3>الدروس المنشورة <span><?= (int) $dashboardMetrics['lessons'] ?></span></h3></article><article><h3>الكتب المنشورة <span><?= (int) $dashboardMetrics['books'] ?></span></h3></article><article><h3>تعليقات تنتظر المراجعة <span><?= (int) $dashboardMetrics['pending_comments'] ?></span></h3></article></div><div class="admin-analytics"><h3>إشارات تشغيلية آمنة</h3><div class="admin-analytics-grid"><article><span>مستخدمون نشطون خلال 30 يومًا</span><strong><?= (int) $dashboardMetrics['active_users_30d'] < 5 ? '&lt;5' : number_format((int) $dashboardMetrics['active_users_30d']) ?></strong></article><article><span>سجل العمليات</span><strong><?= number_format($auditCount) ?></strong></article></div><p class="source-note">لا تعرض لوحة الإدارة إحصاءات عبادة فردية أو مجمعة مثل جلسات القرآن والأذكار والختمة.</p></div></section>
    <?php if (admin_can($admin, 'recovery')): ?><section class="owner-operations admin-panel" id="admin-backups" data-admin-panel hidden><div class="section-heading"><div><span class="eyebrow">عمليات المالك</span><h2>نسخ احتياطية مشفرة</h2></div></div>
        <p class="source-note">تضم النسخة قاعدة البيانات ومخططها وملفات الكتب المرفوعة. تستثني بيانات SMTP والدرر وسجلات الدخول المؤقتة ومفاتيح التشفير وملفات YouTube. تحفظ خارج مجلد الويب، وتتطلب ضبط مفتاح <code>ATHAR_BACKUP_KEY</code> منفصلًا.</p>
        <?php if ($backupGrantReady): ?><form method="post" action="/admin/backup-download" class="backup-download-form"><?= csrf_field() ?><p>إذن التنزيل صالح لمدة 15 دقيقة ولمرة واحدة.</p><button class="button button-primary" type="submit">تنزيل النسخة المشفرة</button></form><?php endif; ?>
        <article class="knowledge-card backup-create-card"><h3>إنشاء نسخة كاملة الآن</h3><form class="form-stack" method="post"><?= csrf_field() ?><input type="hidden" name="kind" value="backup-create"><label>أعد إدخال كلمة المرور<input type="password" name="password" autocomplete="current-password" required></label><label>اكتب عبارة التأكيد<input name="manifest_confirmation" value="" placeholder="CREATE FULL BACKUP" required></label><button class="button button-primary" type="submit">إنشاء النسخة والتحقق منها</button></form></article>
        <div class="backup-list"><h3>النسخ المتاحة</h3><?php if (!$backupArtifacts): ?><p class="source-note">لا توجد نسخ متاحة بعد.</p><?php else: ?><?php foreach ($backupArtifacts as $backup): ?><article class="knowledge-card backup-row"><div><strong>نسخة #<?= (int) $backup['id'] ?></strong><p>المخطط <?= e($backup['schema_version']) ?> · <?= number_format((int) $backup['byte_size'] / 1048576, 2) ?> MB · <?= $backup['status'] === 'ready' ? 'جاهزة' : ($backup['status'] === 'downloaded' ? 'تم تنزيلها' : 'منتهية') ?></p><p>تنتهي: <?= e($backup['expires_at']) ?> UTC</p></div><?php if ($backup['status'] === 'ready'): ?><form method="post" class="grant-form"><?= csrf_field() ?><input type="hidden" name="kind" value="backup-grant"><input type="hidden" name="backup_id" value="<?= (int) $backup['id'] ?>"><label>كلمة المرور<input type="password" name="password" autocomplete="current-password" required></label><button class="button button-secondary" type="submit">إصدار إذن تنزيل 15 دقيقة</button></form><?php endif; ?></article><?php endforeach; ?><?php endif; ?></div>
        <?php $restoreCandidates = array_filter($backupArtifacts, static fn($item) => in_array($item['status'], ['ready','downloaded'], true) && strtotime((string) $item['expires_at']) > time()); ?>
        <article class="knowledge-card backup-create-card"><h3>استعادة نسخة كاملة</h3><p class="source-note">الاستعادة تستبدل بيانات التطبيق والملفات المدارة بمحتوى النسخة. مخطط قاعدة البيانات وإعدادات البيئة يظلان كما هما. يلزم ملف النسخة ومفتاحه المحلي، وإصدار مخطط مطابق.</p><form class="form-stack" method="post" data-confirm="سيتم استبدال بيانات التطبيق بالنسخة المختارة. هل تريد المتابعة؟"><?= csrf_field() ?><input type="hidden" name="kind" value="backup-restore"><label>النسخة<select name="backup_id" required><?php foreach ($restoreCandidates as $candidate): ?><option value="<?= (int) $candidate['id'] ?>">نسخة #<?= (int) $candidate['id'] ?> · <?= e($candidate['schema_version']) ?> · <?= e($candidate['expires_at']) ?> UTC</option><?php endforeach; ?></select></label><label>كلمة مرور المالك<input type="password" name="password" autocomplete="current-password" required></label><label>اكتب عبارة التأكيد<input name="restore_confirmation" placeholder="RESTORE FULL BACKUP" required></label><button class="button button-danger" type="submit" <?= !$restoreCandidates ? 'disabled' : '' ?>>استعادة البيانات والملفات</button></form></article>
        <article class="knowledge-card backup-create-card"><h3>إعادة ضبط محلية</h3><p class="source-note">تحذف سجلات المستخدمين والمحتوى والتقدم وملفات الكتب. تحتفظ بالمخطط وسجل الترحيلات والمالك والنسخ الاحتياطية؛ ويُكتب سجل العملية خارج قاعدة البيانات. تعمل فقط عندما تكون APP_ENV=local أو development، وتتطلب نسخة كاملة تم التحقق منها خلال آخر 24 ساعة.</p><form class="form-stack" method="post" data-confirm="سيُحذف المحتوى والبيانات الشخصية وملفات الكتب نهائيًا. هل تريد المتابعة؟"><?= csrf_field() ?><input type="hidden" name="kind" value="local-reset"><label>اكتب البيئة الحالية حرفيًا<input name="environment_confirmation" placeholder="<?= e((string) (getenv('APP_ENV') ?: 'production')) ?>" required></label><label>كلمة مرور المالك<input type="password" name="password" autocomplete="current-password" required></label><label>اكتب عبارة الحذف<input name="reset_confirmation" placeholder="RESET ATHAR DATA" required></label><button class="button button-danger" type="submit">إعادة الضبط المحلية</button></form></article>
    </section><?php endif; ?>
    <div class="admin-grid admin-panel" id="admin-create" data-admin-panel hidden>
        <article class="knowledge-card"><h2>إضافة عالم</h2><form class="form-stack" method="post" enctype="multipart/form-data"><?= csrf_field() ?><input type="hidden" name="kind" value="scholar"><label>الاسم<input name="name" maxlength="180" required></label><label>السيرة المختصرة<textarea name="biography" maxlength="6000" rows="4"></textarea></label><label>اللغة<select name="language"><option value="ar">العربية</option><option value="en">الإنجليزية</option><option value="other">أخرى</option></select></label><label>المصدر المعتمد (اختياري)<input type="url" name="source_url" maxlength="500" placeholder="https://"></label><label>بيان الحقوق/الإذن (اختياري)<input name="rights_basis" maxlength="500"></label><label>الصورة (اختيارية، JPG أو PNG أو WebP، حتى 3 MB)<input type="file" name="scholar_image" accept="image/jpeg,image/png,image/webp"></label><label>ترتيب العرض<input type="number" name="sort_order" min="-100000" max="100000" value="0"></label><label class="check-label"><input type="checkbox" name="publish"> نشر</label><button class="button button-primary">حفظ العالم</button></form></article>
        <article class="knowledge-card"><h2>إضافة سلسلة</h2><form class="form-stack" method="post"><?= csrf_field() ?><input type="hidden" name="kind" value="series"><label>العنوان<input name="title" maxlength="220" required></label><label>الوصف<textarea name="description" maxlength="4000" rows="3"></textarea></label><label>المصدر (اختياري)<input type="url" name="source_url" maxlength="500"></label><label>العالم<select name="scholar_id"><option value="0">بدون</option><?php foreach ($scholars as $scholar): ?><option value="<?= (int) $scholar['id'] ?>"><?= e($scholar['name']) ?></option><?php endforeach; ?></select></label><label class="check-label"><input type="checkbox" name="publish"> نشر</label><button class="button button-primary">حفظ السلسلة</button></form></article>
        <article class="knowledge-card"><h2>إضافة درس YouTube</h2><form class="form-stack" method="post"><?= csrf_field() ?><input type="hidden" name="kind" value="lesson"><label>العنوان<input name="title" maxlength="220" required></label><label>الوصف<textarea name="description" maxlength="4000" rows="3"></textarea></label><label>رابط YouTube<input type="url" name="source_url" maxlength="500" required></label><label>العالم<select name="scholar_id"><option value="0">بدون</option><?php foreach ($scholars as $scholar): ?><option value="<?= (int) $scholar['id'] ?>"><?= e($scholar['name']) ?></option><?php endforeach; ?></select></label><label>السلسلة<select name="series_id"><option value="0">بدون</option><?php foreach ($seriesItems as $seriesItem): ?><option value="<?= (int) $seriesItem['id'] ?>"><?= e($seriesItem['title']) ?></option><?php endforeach; ?></select></label><label class="check-label"><input type="checkbox" name="publish"> نشر</label><button class="button button-primary">حفظ الدرس</button></form></article>
        <article class="knowledge-card"><h2>إضافة كتاب</h2><form class="form-stack" method="post"><?= csrf_field() ?><input type="hidden" name="kind" value="book"><label>العنوان<input name="title" maxlength="240" required></label><label>المؤلف<input name="author" maxlength="180" required></label><label>الوصف<textarea name="description" maxlength="4000" rows="3"></textarea></label><label>كلمات مفتاحية<input name="keywords" maxlength="1000" placeholder="تفسير، علوم القرآن"></label><label>اللغة<select name="language"><option value="ar">العربية</option><option value="en">الإنجليزية</option><option value="other">أخرى</option></select></label><label>سنة النشر<input type="number" name="publication_year" min="1000" max="2200"></label><label>صاحب الحقوق<input name="rights_holder" maxlength="180"></label><label>رابط المصدر<input type="url" name="source_url" maxlength="500" required></label><label>رابط تنزيل مباشر (اختياري)<input type="url" name="download_url" maxlength="500" placeholder="https://example.org/book.pdf"><small>اتركه فارغًا لإتاحة الكتاب كمرجع/فهرس دون تنزيل.</small></label><label>أساس الحقوق أو الإذن<input name="rights_basis" maxlength="500" required></label><label class="check-label"><input type="checkbox" name="publish"> نشر</label><button class="button button-primary">حفظ الكتاب</button></form></article>
        <article class="knowledge-card"><h2>إضافة ذكر مُراجع</h2><form class="form-stack" method="post"><?= csrf_field() ?><input type="hidden" name="kind" value="adhkar"><label>العنوان<input name="title" maxlength="200" required></label><label>النص<textarea name="formula" maxlength="5000" rows="4" required></textarea></label><label>التوثيق/المرجع<input name="source_reference" maxlength="300" required></label><label>رابط المصدر<input type="url" name="source_url" maxlength="500" required></label><label>التصنيف<input name="category" maxlength="80" required></label><label>التكرار (اختياري)<input type="number" name="repeat_count" min="1" max="1000"></label><label class="check-label"><input type="checkbox" name="reviewed" required> تمت المراجعة التحريرية</label><label class="check-label"><input type="checkbox" name="publish"> نشر</label><button class="button button-primary">حفظ الذكر</button></form></article>
    </div>
    <section class="admin-inventory admin-panel" id="admin-content" data-admin-panel hidden><h2>المحتوى المحفوظ</h2>
        <div class="inventory-grid"><article><h3>العلماء <span><?= count($scholars) ?></span></h3><?php $contentType='scholar'; foreach ($scholars as $row): ?><div class="inventory-row"><p><?= e($row['name']) ?> · <?= $row['is_published'] ? 'منشور' : 'مسودة' ?></p><?php $rowId=(int) $row['id']; require __DIR__ . '/admin-row-actions.php'; ?></div><?php endforeach; ?></article>
        <article><h3>السلاسل <span><?= count($seriesItems) ?></span></h3><?php $contentType='series'; foreach ($seriesItems as $row): ?><div class="inventory-row"><p><?= e($row['title']) ?> · <?= $row['is_published'] ? 'منشور' : 'مسودة' ?></p><?php $rowId=(int) $row['id']; require __DIR__ . '/admin-row-actions.php'; ?></div><?php endforeach; ?></article>
        <article><h3>الدروس <span><?= count($lessons) ?></span></h3><?php $contentType='lesson'; foreach ($lessons as $row): ?><div class="inventory-row"><p><?= e($row['title']) ?> · <?= $row['is_published'] ? 'منشور' : 'مسودة' ?></p><?php $rowId=(int) $row['id']; require __DIR__ . '/admin-row-actions.php'; ?></div><?php endforeach; ?></article>
        <article><h3>الكتب <span><?= count($books) ?></span></h3><?php $contentType='book'; foreach ($books as $row): ?><div class="inventory-row"><p><?= e($row['title']) ?> · <?= $row['is_published'] ? 'منشور' : 'مسودة' ?></p><?php $rowId=(int) $row['id']; require __DIR__ . '/admin-row-actions.php'; ?></div><?php endforeach; ?></article>
        <article><h3>الأذكار <span><?= count($adhkarItems) ?></span></h3><?php $contentType='adhkar'; foreach ($adhkarItems as $row): ?><div class="inventory-row"><p><?= e($row['title']) ?> · <?= $row['is_published'] ? 'منشور' : 'مسودة' ?></p><?php $rowId=(int) $row['id']; require __DIR__ . '/admin-row-actions.php'; ?></div><?php endforeach; ?></article></div>
    </section>
    <section class="admin-inventory admin-panel" id="admin-users" data-admin-panel hidden>
        <div class="section-heading"><div><span class="eyebrow">الحسابات والخصوصية</span><h2>إدارة المستخدمين</h2></div><span class="source-chip">عرض <?= count($managedUsers) ?> من أصل <?= $managedUserTotal ?> حساب · صفحة <?= $managedUserPage ?> من <?= $managedUserPageCount ?></span></div>
        <p class="source-note">ابحث في الحسابات وافتح تفاصيل الاستخدام. يمكن تعديل البريد والاسم واسم المستخدم والاشتراك البريدي؛ تغيير الصلاحيات وحسابات المديرين متاح لمالك المنصة فقط. لا تعرض هذه الشاشة كلمات المرور أو الرموز السرية.</p>
        <form class="form-stack admin-user-search-form" method="get" action="/admin#admin-users">
            <label class="admin-filter-label">البحث بالبريد أو الاسم أو اسم المستخدم أو رقم الحساب
                <input type="search" name="users_q" value="<?= e($managedUserSearch) ?>" data-admin-user-search placeholder="اكتب جزءًا من الاسم أو البريد" autocomplete="off">
            </label>
            <div class="hero-actions">
                <button class="button button-primary" type="submit">بحث</button>
                <?php if ($managedUserSearch !== ''): ?><a class="button button-secondary" href="/admin#admin-users">مسح البحث</a><?php endif; ?>
            </div>
        </form>
        <div class="knowledge-results admin-user-list" data-admin-user-list>
        <?php foreach ($managedUsers as $managedUser):
            $userSearch = mb_strtolower(implode(' ', [(string) $managedUser['email'], (string) ($managedUser['display_name'] ?? ''), (string) ($managedUser['username'] ?? ''), (string) $managedUser['role']]), 'UTF-8');
            $canManageAccount = $managedUser['role'] !== 'owner' && (int) $managedUser['id'] !== (int) $admin['id'] && (($admin['role'] ?? '') === 'owner' || $managedUser['role'] === 'user');
        ?>
            <article class="knowledge-card admin-user-card" data-admin-user data-search="<?= e($userSearch) ?>">
                <header class="admin-user-heading"><div><strong><?= e($managedUser['display_name'] ?: 'بدون اسم عرض') ?></strong><p dir="ltr"><?= e($managedUser['email']) ?></p><span class="source-chip">@<?= e($managedUser['username'] ?: '—') ?></span></div><span class="source-chip admin-role-chip"><?= $managedUser['role'] === 'owner' ? 'مالك المنصة' : ($managedUser['role'] === 'admin' ? 'مدير' : 'مستخدم') ?></span></header>
                <div class="admin-user-facts"><span><?= $managedUser['email_verified_at'] ? 'البريد مؤكد' : 'البريد غير مؤكد' ?></span><span><?= $managedUser['suspended_at'] ? 'الحساب موقوف' : 'الحساب نشط' ?></span><span><?= !empty($managedUser['email_marketing_opt_in']) ? 'مشترك في التحديثات' : 'غير مشترك بالبريد' ?></span><span>انضم <?= e(substr((string) $managedUser['created_at'], 0, 10)) ?></span><span>آخر دخول <?= e($managedUser['last_login'] ? substr((string) $managedUser['last_login'], 0, 16) : 'لا يوجد سجل بعد') ?></span></div>
                <details class="admin-user-details"><summary>تفاصيل الحساب التشغيلية</summary><dl class="admin-user-stats">
                    <div><dt>رقم الحساب</dt><dd>#<?= (int) $managedUser['id'] ?></dd></div>
                    <div><dt>الدور</dt><dd><?= e((string) $managedUser['role']) ?></dd></div>
                    <div><dt>تاريخ الإنشاء</dt><dd><?= e(substr((string) $managedUser['created_at'], 0, 16)) ?></dd></div>
                    <div><dt>آخر تحديث للحساب</dt><dd><?= e(substr((string) $managedUser['updated_at'], 0, 16)) ?></dd></div>
                    <div><dt>آخر تسجيل دخول</dt><dd><?= e($managedUser['last_login'] ? substr((string) $managedUser['last_login'], 0, 16) : 'لا يوجد سجل بعد') ?></dd></div>
                    <div><dt>حالة البريد</dt><dd><?= $managedUser['email_verified_at'] ? 'مؤكد' : 'غير مؤكد' ?></dd></div>
                    <div><dt>حالة الحساب</dt><dd><?= $managedUser['suspended_at'] ? 'موقوف' : 'نشط' ?></dd></div>
                    <div><dt>رسائل التحديثات</dt><dd><?= !empty($managedUser['email_marketing_opt_in']) ? 'مشترك' : 'غير مشترك' ?></dd></div>
                </dl><p class="source-note">لا تعرض لوحة الإدارة موضع القرآن أو الختمة أو الأذكار أو سجل العبادة الخاص بالمستخدم.</p></details>
                <?php if ($canManageAccount): ?><div class="admin-user-actions">
                    <details><summary>تعديل بيانات المستخدم</summary><form class="form-stack" method="post"><?= csrf_field() ?><input type="hidden" name="kind" value="user-profile-update"><input type="hidden" name="user_id" value="<?= (int) $managedUser['id'] ?>"><label>البريد الإلكتروني<input name="email" type="email" dir="ltr" maxlength="190" value="<?= e($managedUser['email']) ?>" required></label><label>اسم العرض<input name="display_name" maxlength="80" minlength="2" value="<?= e($managedUser['display_name'] ?? '') ?>" required></label><label>اسم المستخدم<input name="username" dir="ltr" pattern="[a-z0-9_]{3,24}" maxlength="24" value="<?= e($managedUser['username'] ?? '') ?>" required></label><p class="source-note">تغيير البريد يلغي الاشتراك ويُعيد طلب تأكيد البريد وينهي الجلسات الحالية للحساب. الاشتراك البريدي يديره المستخدم بنفسه.</p><label>أعد إدخال كلمة مرورك<input type="password" name="password" autocomplete="current-password" required></label><button class="button button-secondary" type="submit">حفظ بيانات الحساب</button></form></details>
                    <details>
                        <summary><?= $managedUser['suspended_at'] ? 'إعادة تفعيل الحساب' : 'إيقاف الحساب' ?></summary>
                        <p class="source-note"><?= $managedUser['suspended_at'] ? 'إعادة التفعيل لا تعيد أي جلسة قديمة؛ سيحتاج المستخدم إلى تسجيل الدخول من جديد.' : 'الإيقاف يبطل جلسات الحساب الحالية فورًا دون حذف بياناته.' ?></p>
                        <form class="form-stack" method="post">
                            <?= csrf_field() ?>
                            <input type="hidden" name="kind" value="user-suspension">
                            <input type="hidden" name="user_id" value="<?= (int) $managedUser['id'] ?>">
                            <input type="hidden" name="state" value="<?= $managedUser['suspended_at'] ? 'restore' : 'suspend' ?>">
                            <label>أعد إدخال كلمة مرورك<input type="password" name="password" autocomplete="current-password" required></label>
                            <button class="button <?= $managedUser['suspended_at'] ? 'button-secondary' : 'button-danger' ?>" type="submit"><?= $managedUser['suspended_at'] ? 'إعادة تفعيل الحساب' : 'إيقاف الحساب وإبطال جلساته' ?></button>
                        </form>
                    </details>
                    <?php if (($admin['role'] ?? '') === 'owner' && $managedUser['role'] !== 'owner'): ?><details><summary>تغيير الصلاحية</summary><form class="form-stack" method="post"><?= csrf_field() ?><input type="hidden" name="kind" value="user-role"><input type="hidden" name="user_id" value="<?= (int) $managedUser['id'] ?>"><label>الصلاحية<select name="role"><option value="user"<?= $managedUser['role'] === 'user' ? ' selected' : '' ?>>مستخدم</option><option value="admin"<?= $managedUser['role'] === 'admin' ? ' selected' : '' ?>>مدير محتوى</option></select></label><label>أعد إدخال كلمة مرور المالك<input type="password" name="password" autocomplete="current-password" required></label><button class="button button-secondary" type="submit">حفظ الصلاحية وإبطال الجلسات</button></form></details><?php endif; ?>
                    <details class="admin-user-delete"><summary>حذف الحساب</summary><p class="source-note">سيُحذف الحساب وتقدمه وعلاماته ومفضلاته نهائيًا، ويُحتفظ بسطر التدقيق فقط.</p><form class="form-stack" method="post" data-confirm="سيُحذف الحساب وكل بياناته المرتبطة نهائيًا."><?= csrf_field() ?><input type="hidden" name="kind" value="user-delete"><input type="hidden" name="user_id" value="<?= (int) $managedUser['id'] ?>"><label>اكتب البريد لتأكيد الحذف<input type="email" name="email_confirmation" dir="ltr" autocomplete="off" required></label><label>أعد إدخال كلمة مرورك<input type="password" name="password" autocomplete="current-password" required></label><button class="button button-danger" type="submit">حذف الحساب نهائيًا</button></form></details>
                </div><?php elseif ($managedUser['role'] === 'owner'): ?><p class="source-note">حساب المالك محمي من التعديل والحذف عبر هذه الشاشة.</p><?php elseif ((int) $managedUser['id'] === (int) $admin['id']): ?><p class="source-note">لا يمكن للمدير تعديل حسابه من هذه الشاشة.</p><?php else: ?><p class="source-note">تفاصيل المدير ظاهرة، لكن تعديلها أو حذفها محصور بمالك المنصة.</p><?php endif; ?>
            </article>
        <?php endforeach; ?>
        <p class="source-note" data-admin-user-empty hidden>لا توجد حسابات تطابق بحثك.</p>
        </div>
        <?php if ($managedUserPageCount > 1): ?><nav class="admin-pagination" aria-label="صفحات المستخدمين"><?php if ($managedUserPage > 1): ?><a class="button button-secondary" href="/admin?<?= e(http_build_query(['users_q'=>$managedUserSearch,'users_page'=>$managedUserPage-1])) ?>#admin-users">السابق</a><?php endif; ?><span>صفحة <?= $managedUserPage ?> من <?= $managedUserPageCount ?></span><?php if ($managedUserPage < $managedUserPageCount): ?><a class="button button-secondary" href="/admin?<?= e(http_build_query(['users_q'=>$managedUserSearch,'users_page'=>$managedUserPage+1])) ?>#admin-users">التالي</a><?php endif; ?></nav><?php endif; ?>
    </section>
    <section class="admin-inventory admin-panel" id="admin-events" data-admin-panel hidden>
        <div class="section-heading"><div><span class="eyebrow">الجدولة والبث</span><h2>القادم والمباشر</h2></div><span class="source-chip"><?= count($lessonEvents) ?> حدثًا</span></div>
        <p class="source-note">يحفظ هذا المحرر نفس نموذج Upcoming/Live المستخدم في الـAPI والتطبيق. الوقت يُكتب محليًا مع اسم المنطقة الزمنية، ويحوّله الخادم إلى UTC. كل تعديل يستخدم revision لمنع الكتابة فوق تغيير أحدث.</p>
        <?php $eventLessonIds = array_map(static fn(array $event): int => (int) $event['id'], $lessonEvents); ?>
        <article class="knowledge-card admin-event-card">
            <h3>إنشاء حدث لدرس</h3>
            <form class="form-stack" method="post">
                <?= csrf_field() ?><input type="hidden" name="kind" value="lesson-event"><input type="hidden" name="expected_revision" value="0">
                <label>الدرس<select name="lesson_id" required><option value="">اختر درسًا</option><?php foreach ($lessons as $lesson): if (in_array((int)$lesson['id'],$eventLessonIds,true)) continue; ?><option value="<?= (int)$lesson['id'] ?>"><?= e((string)$lesson['title']) ?></option><?php endforeach; ?></select></label>
                <label>العنوان العربي<input name="title_ar" maxlength="220" required></label>
                <label>الوصف العربي<textarea name="description_ar" maxlength="20000" rows="3"></textarea></label>
                <div class="admin-form-grid">
                    <label>الحالة<select name="status"><option value="draft">مسودة</option><option value="scheduled">مجدول</option><option value="live">مباشر</option><option value="published">منشور/إعادة</option><option value="cancelled">ملغي</option></select></label>
                    <label>الظهور<select name="visibility"><option value="public">عام</option><option value="private">خاص</option></select></label>
                    <label>المنطقة الزمنية<input name="schedule_timezone" value="UTC" maxlength="80" required></label>
                    <label>الموعد المحلي<input type="datetime-local" name="scheduled_local"></label>
                    <label>المدة المتوقعة بالدقائق<input type="number" name="estimated_duration_minutes" min="1" max="1440"></label>
                </div>
                <div class="admin-check-grid">
                    <label class="check-label"><input type="checkbox" name="is_live_event"> حدث مباشر</label>
                    <label class="check-label"><input type="checkbox" name="reminders_enabled"> السماح بالتذكير</label>
                    <label class="check-label"><input type="checkbox" name="replay_available"> إعادة المشاهدة متاحة</label>
                </div>
                <label>العنوان الإنجليزي<input name="title_en" maxlength="220"></label>
                <label>الوصف الإنجليزي<textarea name="description_en" maxlength="20000" rows="3"></textarea></label>
                <div class="admin-form-grid">
                    <label>اسم الدورة بالعربية<input name="course_title_ar" maxlength="200"></label>
                    <label>Course title<input name="course_title_en" maxlength="200"></label>
                </div>
                <label>الموضوعات العربية (سطر أو فاصلة بين كل موضوع)<textarea name="topics_ar" rows="3"></textarea></label>
                <label>English topics<textarea name="topics_en" rows="3"></textarea></label>
                <div class="admin-form-grid">
                    <label>كتاب مرتبط<select name="related_book_id"><option value="">بدون</option><?php foreach ($books as $book): ?><option value="<?= (int)$book['id'] ?>"><?= e((string)$book['title']) ?></option><?php endforeach; ?></select></label>
                    <label>من صفحة<input type="number" name="related_page_start" min="1"></label>
                    <label>إلى صفحة<input type="number" name="related_page_end" min="1"></label>
                </div>
                <button class="button button-primary" type="submit">حفظ الحدث</button>
            </form>
        </article>

        <div class="knowledge-results admin-event-list">
        <?php foreach ($lessonEvents as $event):
            $eventContext = is_array($event['editor_context'] ?? null) ? $event['editor_context'] : [];
            $eventEnglish = is_array($event['translations']['en'] ?? null) ? $event['translations']['en'] : [];
            $eventLocal = '';
            if (!empty($event['scheduled_at'])) {
                try { $eventLocal = (new DateTimeImmutable((string)$event['scheduled_at']))->setTimezone(new DateTimeZone((string)$event['schedule_timezone']))->format('Y-m-d\TH:i'); } catch (Throwable $ignored) { }
            }
        ?>
            <article class="knowledge-card admin-event-card">
                <div class="section-heading"><div><strong><?= e((string)$event['title']) ?></strong><p class="source-note">Event #<?= (int)$event['event_id'] ?> · revision <?= (int)$event['revision'] ?></p></div><span class="source-chip"><?= e((string)$event['status']) ?></span></div>
                <form class="form-stack" method="post">
                    <?= csrf_field() ?><input type="hidden" name="kind" value="lesson-event"><input type="hidden" name="lesson_id" value="<?= (int)$event['id'] ?>"><input type="hidden" name="expected_revision" value="<?= (int)$event['revision'] ?>">
                    <label>العنوان العربي<input name="title_ar" maxlength="220" value="<?= e((string)$event['translations']['ar']['title']) ?>" required></label>
                    <label>الوصف العربي<textarea name="description_ar" maxlength="20000" rows="3"><?= e((string)($event['translations']['ar']['description'] ?? '')) ?></textarea></label>
                    <div class="admin-form-grid">
                        <label>الحالة<select name="status"><?php foreach (['draft'=>'مسودة','scheduled'=>'مجدول','live'=>'مباشر','published'=>'منشور/إعادة','cancelled'=>'ملغي'] as $statusValue=>$statusLabel): ?><option value="<?= e($statusValue) ?>"<?= $event['status']===$statusValue?' selected':'' ?>><?= e($statusLabel) ?></option><?php endforeach; ?></select></label>
                        <label>الظهور<select name="visibility"><option value="public"<?= $event['visibility']==='public'?' selected':'' ?>>عام</option><option value="private"<?= $event['visibility']==='private'?' selected':'' ?>>خاص</option></select></label>
                        <label>المنطقة الزمنية<input name="schedule_timezone" maxlength="80" value="<?= e((string)$event['schedule_timezone']) ?>" required></label>
                        <label>الموعد المحلي<input type="datetime-local" name="scheduled_local" value="<?= e($eventLocal) ?>"></label>
                        <label>المدة المتوقعة بالدقائق<input type="number" name="estimated_duration_minutes" min="1" max="1440" value="<?= e((string)($event['estimated_duration_minutes'] ?? '')) ?>"></label>
                    </div>
                    <div class="admin-check-grid">
                        <label class="check-label"><input type="checkbox" name="is_live_event"<?= $event['is_live_event']?' checked':'' ?>> حدث مباشر</label>
                        <label class="check-label"><input type="checkbox" name="reminders_enabled"<?= $event['reminders_enabled']?' checked':'' ?>> السماح بالتذكير</label>
                        <label class="check-label"><input type="checkbox" name="replay_available"<?= $event['replay_available']?' checked':'' ?>> إعادة المشاهدة متاحة</label>
                    </div>
                    <label>العنوان الإنجليزي<input name="title_en" maxlength="220" value="<?= e((string)($eventEnglish['title'] ?? '')) ?>"></label>
                    <label>الوصف الإنجليزي<textarea name="description_en" maxlength="20000" rows="3"><?= e((string)($eventEnglish['description'] ?? '')) ?></textarea></label>
                    <?php if ($eventEnglish): ?><label class="check-label"><input type="checkbox" name="clear_english_translation"> حذف الترجمة الإنجليزية</label><?php endif; ?>
                    <div class="admin-form-grid">
                        <label>اسم الدورة بالعربية<input name="course_title_ar" maxlength="200" value="<?= e((string)($eventContext['course_title_ar'] ?? '')) ?>"></label>
                        <label>Course title<input name="course_title_en" maxlength="200" value="<?= e((string)($eventContext['course_title_en'] ?? '')) ?>"></label>
                    </div>
                    <label>الموضوعات العربية<textarea name="topics_ar" rows="3"><?= e(implode("\n",(array)($eventContext['topics_ar'] ?? []))) ?></textarea></label>
                    <label>English topics<textarea name="topics_en" rows="3"><?= e(implode("\n",(array)($eventContext['topics_en'] ?? []))) ?></textarea></label>
                    <div class="admin-form-grid">
                        <label>كتاب مرتبط<select name="related_book_id"><option value="">بدون</option><?php foreach ($books as $book): ?><option value="<?= (int)$book['id'] ?>"<?= (int)($eventContext['related_book_id'] ?? 0)===(int)$book['id']?' selected':'' ?>><?= e((string)$book['title']) ?></option><?php endforeach; ?></select></label>
                        <label>من صفحة<input type="number" name="related_page_start" min="1" value="<?= e((string)($eventContext['related_page_start'] ?? '')) ?>"></label>
                        <label>إلى صفحة<input type="number" name="related_page_end" min="1" value="<?= e((string)($eventContext['related_page_end'] ?? '')) ?>"></label>
                    </div>
                    <button class="button button-primary" type="submit">حفظ التعديلات</button>
                </form>
            </article>
        <?php endforeach; ?>
        </div>
    </section>
    <section class="admin-inventory admin-panel" id="admin-comments" data-admin-panel hidden>
        <div class="section-heading"><div><span class="eyebrow">المجتمع</span><h2>مراجعة تعليقات الدروس</h2></div><span class="source-chip"><?= number_format($commentCount) ?> تعليق</span></div>
        <p class="source-note">كل قرار نشر أو إخفاء أو حذف يتطلب سببًا ويستخدم revision لمنع الكتابة فوق مراجعة أحدث. حذف تعليق رئيسي يحذف ردوده التابعة وفق علاقة قاعدة البيانات، لذلك استخدمه فقط عند الحاجة.</p>
        <?php if (!$pendingComments): ?><p class="source-note">لا توجد تعليقات.</p><?php else: ?>
        <div class="knowledge-results admin-comment-list">
        <?php foreach ($pendingComments as $comment): ?>
            <article class="knowledge-card admin-comment-card">
                <div class="section-heading">
                    <div>
                        <strong><?= e($comment['lesson_title']) ?></strong>
                        <p class="source-note">
                            <?= $comment['parent_id'] === null ? 'تعليق رئيسي' : 'رد على تعليق #' . (int)$comment['parent_id'] ?>
                            · #<?= (int)$comment['id'] ?>
                            · revision <?= (int)$comment['revision'] ?>
                        </p>
                    </div>
                    <span class="source-chip"><?= $comment['status']==='pending'?'بانتظار المراجعة':($comment['status']==='visible'?'منشور':'مخفي') ?></span>
                </div>
                <p><?= e($comment['display_name'] ?: '@'.$comment['username']) ?> · <?= e($comment['created_at']) ?></p>
                <blockquote class="admin-comment-body"><?= nl2br(e($comment['body'])) ?></blockquote>
                <?php if (!empty($comment['moderation_reason'])): ?><p class="source-note">آخر سبب مراجعة: <?= e((string)$comment['moderation_reason']) ?></p><?php endif; ?>
                <div class="admin-comment-actions">
                    <?php if ($comment['status'] !== 'visible'): ?>
                    <form class="form-stack" method="post">
                        <?= csrf_field() ?>
                        <input type="hidden" name="kind" value="comment-review">
                        <input type="hidden" name="comment_id" value="<?= (int)$comment['id'] ?>">
                        <input type="hidden" name="expected_revision" value="<?= (int)$comment['revision'] ?>">
                        <input type="hidden" name="status" value="visible">
                        <label>سبب النشر<input name="reason" maxlength="500" minlength="2" placeholder="مثال: تمت المراجعة ولا توجد مخالفة" required></label>
                        <button class="button button-primary" type="submit">نشر التعليق</button>
                    </form>
                    <?php endif; ?>
                    <?php if ($comment['status'] !== 'hidden'): ?>
                    <form class="form-stack" method="post">
                        <?= csrf_field() ?>
                        <input type="hidden" name="kind" value="comment-review">
                        <input type="hidden" name="comment_id" value="<?= (int)$comment['id'] ?>">
                        <input type="hidden" name="expected_revision" value="<?= (int)$comment['revision'] ?>">
                        <input type="hidden" name="status" value="hidden">
                        <label>سبب الإخفاء<input name="reason" maxlength="500" minlength="2" placeholder="سبب واضح ومختصر" required></label>
                        <button class="button button-secondary" type="submit">إخفاء</button>
                    </form>
                    <?php endif; ?>
                    <form class="form-stack" method="post" data-confirm="حذف التعليق نهائيًا؟ سيتم الاحتفاظ بسجل قرار المراجعة فقط.">
                        <?= csrf_field() ?>
                        <input type="hidden" name="kind" value="comment-delete">
                        <input type="hidden" name="comment_id" value="<?= (int)$comment['id'] ?>">
                        <input type="hidden" name="expected_revision" value="<?= (int)$comment['revision'] ?>">
                        <label>سبب الحذف<input name="reason" maxlength="500" minlength="2" placeholder="سبب الحذف" required></label>
                        <button class="button button-danger" type="submit">حذف</button>
                    </form>
                </div>
            </article>
        <?php endforeach; ?>
        </div>
        <?php endif; ?>
        <?php if ($commentPageCount > 1): ?><nav class="admin-pagination" aria-label="صفحات التعليقات"><?php if ($commentPage > 1): ?><a class="button button-secondary" href="/admin?comments_page=<?= $commentPage - 1 ?>#admin-comments">الأحدث</a><?php endif; ?><span>صفحة <?= $commentPage ?> من <?= $commentPageCount ?></span><?php if ($commentPage < $commentPageCount): ?><a class="button button-secondary" href="/admin?comments_page=<?= $commentPage + 1 ?>#admin-comments">الأقدم</a><?php endif; ?></nav><?php endif; ?>
    </section>
    <section class="admin-inventory admin-panel" id="admin-audit" data-admin-panel hidden><div class="section-heading"><div><span class="eyebrow">الشفافية والأمان</span><h2>سجل العمليات</h2></div><span class="source-chip"><?= number_format($auditCount) ?> عملية مسجلة</span></div><p class="source-note">السجل تشغيلي ومختصر؛ البريد يظهر بصورة مخفية ولا تُعرض كلمات المرور أو الرموز أو بيانات العبادة الخاصة.</p><form class="form-stack" method="get" action="/admin#admin-audit"><label class="admin-filter-label">البحث بالإجراء أو نوع/رقم الهدف<input type="search" name="audit_q" value="<?= e($auditSearch) ?>" data-admin-audit-search placeholder="مثال: admin-user أو user أو 42" autocomplete="off"></label><div class="hero-actions"><button class="button button-primary" type="submit">بحث</button><?php if ($auditSearch !== ''): ?><a class="button button-secondary" href="/admin#admin-audit">مسح البحث</a><?php endif; ?></div></form><?php if (!$auditRows): ?><p class="source-note">لا توجد عمليات مطابقة.</p><?php else: ?><div class="knowledge-results admin-audit-list"><?php foreach ($auditRows as $audit): $auditLocalSearch = mb_strtolower(implode(' ', [(string) $audit['action'], (string) $audit['target_type'], (string) $audit['target_id']]), 'UTF-8'); ?><article class="knowledge-card admin-audit-row" data-admin-audit data-search="<?= e($auditLocalSearch) ?>"><strong><?= e(str_replace(['account-login-succeeded','account-registered','account-logout','admin-login-succeeded','admin-logout','admin-user-profile-updated','admin-user-deleted','admin-user-suspended','admin-user-restored','user-role-changed-to-admin','user-role-changed-to-user'], ['تسجيل دخول ناجح','إنشاء حساب','تسجيل خروج','دخول إدارة ناجح','خروج من الإدارة','تعديل بيانات مستخدم','حذف مستخدم','إيقاف مستخدم','إعادة تفعيل مستخدم','ترقية إلى مدير','تغيير إلى مستخدم'], (string) $audit['action'])) ?></strong><p><?= e($audit['target_type']) ?><?= $audit['target_email'] ? ' · الحساب: ' . e(admin_redact_email((string) $audit['target_email'])) : ($audit['target_id'] ? ' · رقم: ' . e((string) $audit['target_id']) : '') ?> · نفّذها <?= e($audit['actor_email'] ? admin_redact_email((string) $audit['actor_email']) : 'النظام') ?></p><time datetime="<?= e(date(DATE_ATOM, strtotime((string) $audit['created_at']))) ?>"><?= e((string) $audit['created_at']) ?></time></article><?php endforeach; ?></div><?php if ($auditPageCount > 1): ?><nav class="admin-pagination" aria-label="صفحات سجل العمليات"><?php if ($auditPage > 1): ?><a class="button button-secondary" href="/admin?<?= e(http_build_query(['audit_q'=>$auditSearch,'audit_page'=>$auditPage-1])) ?>#admin-audit">الأحدث</a><?php endif; ?><span>صفحة <?= $auditPage ?> من <?= $auditPageCount ?></span><?php if ($auditPage < $auditPageCount): ?><a class="button button-secondary" href="/admin?<?= e(http_build_query(['audit_q'=>$auditSearch,'audit_page'=>$auditPage+1])) ?>#admin-audit">الأقدم</a><?php endif; ?></nav><?php endif; ?><?php endif; ?></section>
        </div>
    </div>
</section>
