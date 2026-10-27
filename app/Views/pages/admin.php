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
    'users' => 'إدارة المستخدمين',
    'backups' => 'النسخ والاستعادة',
    'audit' => 'سجل العمليات',
    'note' => 'اختر قسمًا لعرض أدواته وبياناته.',
];
?>
<?php require __DIR__ . '/../../../admin/Views/user-copy.php';
require dirname(__DIR__, 3) . '/admin/Views/recovery-copy.php';
require dirname(__DIR__, 3) . '/admin/Views/scholar-copy.php';
require dirname(__DIR__, 3) . '/admin/Views/series-copy.php';
require dirname(__DIR__, 3) . '/admin/Views/lesson-copy.php';
require dirname(__DIR__, 3) . '/admin/Views/events-copy.php';
require dirname(__DIR__, 3) . '/admin/Views/book-copy.php';
require dirname(__DIR__, 3) . '/admin/Views/editorial-copy.php';
require dirname(__DIR__,3).'/admin/Views/service-copy.php';
$adminMessageTranslations=[$userTranslation,$recoveryTranslation,$scholarTranslation,$seriesTranslation,$lessonTranslation,$eventTranslation,$bookTranslation,$editorialTranslation,$serviceTranslation];
$adminTranslate=static function(string $message) use($adminMessageTranslations):string {foreach($adminMessageTranslations as $translate)$message=$translate($message);return $message;};
if (!empty($flashMessage['message'])) $flashMessage['message']=$adminTranslate((string)$flashMessage['message']);
?>
<section class="knowledge-page section-wrap admin-page">
    <header class="knowledge-heading">
        <span class="eyebrow"><?= e($adminShellCopy['eyebrow']) ?></span>
        <h1><?= e($adminShellCopy['title']) ?></h1>
        <p><?= e($adminShellCopy['lede']) ?></p>
    </header>
    <?php if ($adminError !== ''): ?><div class="notice notice-error" role="alert" tabindex="-1" data-admin-form-error><?= e($adminTranslate($adminError)) ?></div><?php endif; ?>
    <div class="admin-layout">
        <aside class="admin-sidebar" id="admin-primary-nav">
            <strong class="admin-sidebar-title"><?= e($adminShellCopy['sidebar']) ?></strong>
            <nav class="admin-sidebar-nav" aria-label="<?= e($adminShellCopy['nav_label']) ?>" data-admin-nav>
                <a href="#admin-overview" aria-current="location" aria-controls="admin-overview"><?= e($adminShellCopy['overview']) ?></a>
                <a href="#admin-create" aria-controls="admin-create"><?= e($adminShellCopy['create']) ?></a>
                <a href="#admin-content" aria-controls="admin-content"><?= e($adminShellCopy['content']) ?></a>
                <a href="#admin-events" aria-controls="admin-events"><?= e($adminShellCopy['events']) ?> <span><?= $lessonEventTotal === null ? "—" : (int)$lessonEventTotal ?></span></a>
                <a href="#admin-comments" aria-controls="admin-comments"><?= e($adminShellCopy['comments']) ?> <span><?= (int) $dashboardMetrics['pending_comments'] ?></span></a>
                <?php if (admin_can($admin, 'campaigns')): ?><a href="#admin-campaigns" aria-controls="admin-campaigns"><?= e($adminShellCopy['campaigns']) ?></a><?php endif; ?>
                <?php if (admin_can($admin,'night_prayer_defaults')): ?><a href="#admin-night-prayer" aria-controls="admin-night-prayer"><?= $serviceCopy('الإعدادات العامة لقيام الليل','Public Night Prayer defaults') ?></a><?php endif; ?>
                <?php if (admin_can($admin, 'services')): ?><a href="#admin-services" aria-controls="admin-services"><?= e($adminShellCopy['services']) ?></a><?php endif; ?>
                <?php if (admin_can($admin, 'users')): ?><a href="#admin-users" aria-controls="admin-users"><?= e($adminShellCopy['users']) ?> <span><?= $managedUserTotal ?></span></a><?php endif; ?>
                <?php if (admin_can($admin, 'recovery')): ?><a href="#admin-backups" aria-controls="admin-backups"><?= e($adminShellCopy['backups']) ?></a><?php endif; ?>
                <?php if (admin_can($admin, 'audit')): ?><a href="#admin-audit" aria-controls="admin-audit"><?= e($adminShellCopy['audit']) ?></a><?php endif; ?>
            </nav>
            <p class="admin-sidebar-note"><?= e($adminShellCopy['note']) ?></p>
        </aside>
        <div class="admin-content">
    <?php if (admin_can($admin, 'services')): ?>
    <?php require dirname(__DIR__,3).'/admin/Views/services.php'; ?>
    <?php endif; ?>
    <?php if (admin_can($admin,'night_prayer_defaults')) require dirname(__DIR__,3).'/admin/Views/night-defaults.php'; ?>
    <section class="admin-inventory admin-panel" id="admin-campaigns" data-admin-panel hidden>
        <div class="section-heading"><div><span class="eyebrow">التواصل مع المجتمع</span><h2>حملات البريد الإلكتروني</h2></div><span class="source-chip"><?= number_format($mailingOptInCount) ?> مشتركًا مؤكد البريد</span></div>
        <p class="source-note">تصل الحملة فقط لمن أكد بريده واختار الاشتراك. ابدأ الإرسال مرة واحدة وسيكمل الموقع تلقائيًا على دفعات من 3 رسائل، ويمكنك إيقافه بعد الدفعة الحالية. يتوقف عند 450 رسالة خلال 24 ساعة لترك مساحة لرسائل التحقق. كل رسالة تحمل شعار أثر ورابط إلغاء الاشتراك.</p>
        <article class="knowledge-card admin-campaign-composer"><h3>إنشاء حملة</h3><form class="form-stack" method="post"><?= csrf_field() ?><input type="hidden" name="kind" value="campaign-create"><label>عنوان الرسالة<input name="subject" maxlength="180" minlength="3" required></label><label>نص الرسالة<textarea name="body" maxlength="4000" minlength="10" rows="7" required></textarea><small>نص عادي آمن؛ سيظهر داخل قالب أثر المتوافق مع Gmail والهواتف.</small></label><button class="button button-primary" type="submit" <?= !$mailingOptInCount || !$smtpPasswordConfigured ? 'disabled' : '' ?>>تجهيز الحملة للمشتركين</button></form><?php if (!$smtpPasswordConfigured): ?><p class="source-note">احفظ بريد Gmail وApp Password أولًا من إعدادات الخدمات.</p><?php elseif (!$mailingOptInCount): ?><p class="source-note">لا يوجد مشترك مؤكد البريد بعد. يمكن للمستخدم الاشتراك من بيانات الحساب.</p><?php endif; ?></article>
        <p class="source-note admin-campaign-feedback" data-campaign-feedback role="status" aria-live="polite"></p>
        <button class="button button-secondary" type="button" data-campaign-stop hidden>إيقاف بعد الدفعة الحالية</button>
        <div class="admin-campaign-list"><h3>الحملات السابقة</h3><?php if (!$emailCampaigns): ?><p class="source-note">لم تُنشأ حملات بعد.</p><?php else: ?><?php foreach ($emailCampaigns as $campaign): $campaignStatus = ['draft'=>'جاهزة للإرسال','sending'=>'قيد الإرسال','paused'=>'متوقفة مؤقتًا','completed'=>'مكتملة'][$campaign['status']] ?? $campaign['status']; ?><article class="knowledge-card admin-campaign-row"><div class="admin-campaign-heading"><div><strong><?= e($campaign['subject']) ?></strong><p>أُنشئت <?= e($campaign['created_at']) ?> · بواسطة <?= e($campaign['creator_email'] ?: 'النظام') ?></p></div><span class="source-chip"><?= e($campaignStatus) ?></span></div><div class="admin-campaign-stats"><span><?= (int)$campaign['recipient_sent'] ?> أُرسلت</span><span><?= (int)$campaign['recipient_pending'] ?> متبقية</span><span><?= (int)$campaign['recipient_failed'] ?> فشلت</span><span><?= (int)$campaign['recipient_skipped'] ?> تم تخطيها</span></div><?php if (in_array($campaign['status'], ['draft','sending','paused'], true) && (int)$campaign['recipient_pending'] > 0): ?><form method="post" class="admin-campaign-action" data-campaign-batch><?= csrf_field() ?><input type="hidden" name="kind" value="campaign-batch"><input type="hidden" name="campaign_id" value="<?= (int)$campaign['id'] ?>"><button class="button button-primary" type="submit">بدء الإرسال (3 رسائل لكل دفعة)</button></form><?php endif; ?><?php if ((int)$campaign['recipient_failed'] > 0): ?><form method="post" class="admin-campaign-action" data-confirm="ستُعاد الرسائل الفاشلة فقط إلى قائمة الإرسال."><?= csrf_field() ?><input type="hidden" name="kind" value="campaign-retry-failed"><input type="hidden" name="campaign_id" value="<?= (int)$campaign['id'] ?>"><button class="button button-secondary" type="submit">إعادة محاولة الرسائل الفاشلة</button></form><?php endif; ?></article><?php endforeach; ?><?php endif; ?></div>
    </section>
    <?php require dirname(__DIR__, 3) . '/admin/Views/overview.php'; ?>
    <?php require dirname(__DIR__, 3) . '/admin/Views/recovery.php'; ?>
    <div class="admin-grid admin-panel" id="admin-create" data-admin-panel hidden>
        <?php $scholarDraft=[]; $scholarFormMode='create'; require dirname(__DIR__,3).'/admin/Views/scholar-form.php'; ?>
        <?php $seriesDraft=[]; $seriesFormMode='create'; require dirname(__DIR__,3).'/admin/Views/series-form.php'; ?>
        <?php $lessonDraft=[]; $lessonFormMode='create'; require dirname(__DIR__,3).'/admin/Views/lesson-form.php'; ?>
        <?php $bookDraft=[]; $bookFormMode='create'; require dirname(__DIR__,3).'/admin/Views/book-form.php'; ?>
        <?php $adhkarDraft=[]; $adhkarFormMode='create'; require dirname(__DIR__,3).'/admin/Views/adhkar-form.php'; ?>
    </div>
    <section class="admin-inventory admin-panel" id="admin-content" data-admin-panel hidden><h2><?= $seriesCopy('المحتوى المحفوظ') ?></h2>
        <div class="inventory-grid"><article><h3><?= $scholarCopy('العلماء') ?> <span><?= count($scholars) ?></span></h3><?php $contentType='scholar'; foreach ($scholars as $row): ?><div class="inventory-row"><p><?= e($row['name']) ?> · <?= $scholarCopy($row['is_published'] ? 'منشور' : 'مسودة') ?></p><?php $rowId=(int) $row['id']; require __DIR__ . '/admin-row-actions.php'; ?></div><?php endforeach; ?></article>
        <article><h3><?= $seriesCopy('السلاسل') ?> <span><?= count($seriesItems) ?></span></h3><?php $contentType='series'; foreach ($seriesItems as $row): ?><div class="inventory-row"><p><?= e($row['title']) ?> · <?= $seriesCopy($row['is_published'] ? 'منشور' : 'مسودة') ?></p><?php $rowId=(int) $row['id']; require __DIR__ . '/admin-row-actions.php'; ?></div><?php endforeach; ?></article>
        <article><h3><?= $lessonCopy('الدروس') ?> <span><?= count($lessons) ?></span></h3><?php $contentType='lesson'; foreach ($lessons as $row): ?><div class="inventory-row"><p><?= e($row['title']) ?> · <?= $lessonCopy($row['is_published'] ? 'منشور' : 'مسودة') ?></p><?php $rowId=(int) $row['id']; require __DIR__ . '/admin-row-actions.php'; ?></div><?php endforeach; ?></article>
        <article><h3><?= $bookCopy('الكتب','Books') ?> <span><?= count($books) ?></span></h3><?php $contentType='book'; foreach ($books as $row): ?><div class="inventory-row"><p><?= e($row['title']) ?> · <?= $row['is_published'] ? $bookCopy('منشور','Published') : $bookCopy('مسودة','Draft') ?></p><?php $rowId=(int) $row['id']; require __DIR__ . '/admin-row-actions.php'; ?></div><?php endforeach; ?></article>
        <article><h3><?= $editorialCopy('الأذكار','Adhkar') ?> <span><?= count($adhkarItems) ?></span></h3><?php $contentType='adhkar'; foreach ($adhkarItems as $row): ?><div class="inventory-row"><p><?= e($row['title']) ?> · <?= $row['is_published'] ? $editorialCopy('منشور','Published') : $editorialCopy('مسودة','Draft') ?></p><?php $rowId=(int) $row['id']; require __DIR__ . '/admin-row-actions.php'; ?></div><?php endforeach; ?></article></div>
    </section>
    <section class="admin-inventory admin-panel" id="admin-users" data-admin-panel hidden>
        <div class="section-heading"><div><span class="eyebrow"><?= $userCopy('الحسابات والخصوصية') ?></span><h2><?= $userCopy('إدارة المستخدمين') ?></h2></div><span class="source-chip"><?= $userCopy('عرض') ?> <?= count($managedUsers) ?> <?= $userCopy('من أصل') ?> <?= $managedUserTotal ?> <?= $userCopy('حساب · صفحة') ?> <?= $managedUserPage ?> <?= $userCopy('من') ?> <?= $managedUserPageCount ?></span></div>
        <p class="source-note"><?= $userCopy('ابحث في الحسابات وافتح تفاصيلها. يمكن تعديل البريد والاسم واسم المستخدم؛ الاشتراك البريدي يديره المستخدم بنفسه. تغيير الصلاحيات وحسابات المديرين متاح لمالك المنصة فقط. لا تعرض هذه الشاشة كلمات المرور أو الرموز السرية.') ?></p>
        <form class="form-stack admin-user-search-form" method="get" action="/admin#admin-users">
            <label class="admin-filter-label"><?= $userCopy('البحث بالبريد أو الاسم أو اسم المستخدم أو رقم الحساب') ?>
                <input type="search" dir="auto" name="users_q" value="<?= e($managedUserSearch) ?>" placeholder="<?= $userCopy('اكتب جزءًا من الاسم أو البريد') ?>" autocomplete="off">
            </label>
            <div class="hero-actions">
                <button class="button button-primary" type="submit"><?= $userCopy('بحث') ?></button>
                <?php if ($managedUserSearch !== ''): ?><a class="button button-secondary" href="/admin#admin-users"><?= $userCopy('مسح البحث') ?></a><?php endif; ?>
            </div>
        </form>
        <div class="knowledge-results admin-user-list" data-admin-user-list>
        <?php foreach ($managedUsers as $managedUser):
            $userSearch = mb_strtolower(implode(' ', [(string) $managedUser['email'], (string) ($managedUser['display_name'] ?? ''), (string) ($managedUser['username'] ?? ''), (string) $managedUser['role']]), 'UTF-8');
            $canManageAccount = admin_can_manage_user($admin, $managedUser, 'profile');
        ?>
            <article class="knowledge-card admin-user-card" data-admin-user data-search="<?= e($userSearch) ?>">
                <header class="admin-user-heading"><div><strong><?= e($managedUser['display_name'] ?: $userCopy('بدون اسم عرض')) ?></strong><p dir="ltr"><?= e($managedUser['email']) ?></p><span class="source-chip">@<?= e($managedUser['username'] ?: '—') ?></span></div><span class="source-chip admin-role-chip"><?= $managedUser['role'] === 'owner' ? $userCopy('مالك المنصة') : ($managedUser['role'] === 'admin' ? $userCopy('مدير') : $userCopy('مستخدم')) ?></span></header>
                <div class="admin-user-facts"><span><?= $managedUser['email_verified_at'] ? $userCopy('البريد مؤكد') : $userCopy('البريد غير مؤكد') ?></span><span><?= $managedUser['suspended_at'] ? $userCopy('الحساب موقوف') : $userCopy('الحساب نشط') ?></span><span><?= !empty($managedUser['email_marketing_opt_in']) ? $userCopy('مشترك في التحديثات') : $userCopy('غير مشترك بالبريد') ?></span><span><?= $userCopy('انضم') ?> <?= e(substr((string) $managedUser['created_at'], 0, 10)) ?></span><span><?= $userCopy('آخر دخول (UTC)') ?> <?= e($managedUser['last_login'] ? substr((string) $managedUser['last_login'], 0, 16) : $userCopy('لا يوجد سجل بعد')) ?></span></div>
                <details class="admin-user-details"><summary><?= $userCopy('تفاصيل الحساب التشغيلية') ?></summary><dl class="admin-user-stats">
                    <div><dt><?= $userCopy('رقم الحساب') ?></dt><dd>#<?= (int) $managedUser['id'] ?></dd></div>
                    <div><dt><?= $userCopy('الدور') ?></dt><dd><?= $userCopy(match($managedUser['role']) {'owner'=>'مالك المنصة','admin'=>'مدير',default=>'مستخدم'}) ?></dd></div>
                    <div><dt><?= $userCopy('تاريخ الإنشاء (UTC)') ?></dt><dd><?= e(substr((string) $managedUser['created_at'],0,16)) ?></dd></div>
                    <div><dt><?= $userCopy('آخر تحديث للحساب') ?></dt><dd><?= e(substr((string) $managedUser['updated_at'],0,16)) ?></dd></div>
                    <div><dt><?= $userCopy('حالة البريد') ?></dt><dd><?= $userCopy($managedUser['email_verified_at'] ? 'مؤكد' : 'غير مؤكد') ?></dd></div>
                    <div><dt><?= $userCopy('حالة الحساب') ?></dt><dd><?= $userCopy($managedUser['suspended_at'] ? 'موقوف' : 'نشط') ?></dd></div>
                    <div><dt><?= $userCopy('رسائل التحديثات') ?></dt><dd><?= $userCopy(!empty($managedUser['email_marketing_opt_in']) ? 'مشترك' : 'غير مشترك') ?></dd></div>
                </dl><p class="source-note"><?= $userCopy('لا تعرض لوحة الإدارة موضع القرآن أو الختمة أو الأذكار أو سجل العبادة الخاص بالمستخدم.') ?></p></details>
                <?php if ($canManageAccount): ?><div class="admin-user-actions">
                    <details><summary><?= $userCopy('تعديل بيانات المستخدم') ?></summary><form class="form-stack" method="post"><?= csrf_field() ?><input type="hidden" name="kind" value="user-profile-update"><input type="hidden" name="user_id" value="<?= (int) $managedUser['id'] ?>"><label><?= $userCopy('البريد الإلكتروني') ?><input name="email" type="email" dir="ltr" maxlength="190" value="<?= e($managedUser['email']) ?>" required></label><label><?= $userCopy('اسم العرض') ?><input name="display_name" maxlength="80" minlength="2" value="<?= e($managedUser['display_name'] ?? '') ?>" required></label><label><?= $userCopy('اسم المستخدم') ?><input name="username" dir="ltr" pattern="[a-z0-9_]{3,24}" maxlength="24" value="<?= e($managedUser['username'] ?? '') ?>" required></label><p class="source-note"><?= $userCopy('تغيير البريد يلغي الاشتراك ويُعيد طلب تأكيد البريد وينهي الجلسات الحالية للحساب. الاشتراك البريدي يديره المستخدم بنفسه.') ?></p><label><?= $userCopy('أعد إدخال كلمة مرورك') ?><input type="password" name="password" autocomplete="current-password" required></label><button class="button button-secondary" type="submit"><?= $userCopy('حفظ بيانات الحساب') ?></button></form></details>
                    <details>
                        <summary><?= $managedUser['suspended_at'] ? $userCopy('إعادة تفعيل الحساب') : $userCopy('إيقاف الحساب') ?></summary>
                        <p class="source-note"><?= $managedUser['suspended_at'] ? $userCopy('إعادة التفعيل لا تعيد أي جلسة قديمة؛ سيحتاج المستخدم إلى تسجيل الدخول من جديد.') : $userCopy('الإيقاف يبطل جلسات الحساب الحالية فورًا دون حذف بياناته.') ?></p>
                        <form class="form-stack" method="post">
                            <?= csrf_field() ?>
                            <input type="hidden" name="kind" value="user-suspension">
                            <input type="hidden" name="user_id" value="<?= (int) $managedUser['id'] ?>">
                            <input type="hidden" name="state" value="<?= $managedUser['suspended_at'] ? 'restore' : 'suspend' ?>">
                            <label><?= $userCopy('أعد إدخال كلمة مرورك') ?><input type="password" name="password" autocomplete="current-password" required></label>
                            <button class="button <?= $managedUser['suspended_at'] ? 'button-secondary' : 'button-danger' ?>" type="submit"><?= $managedUser['suspended_at'] ? $userCopy('إعادة تفعيل الحساب') : $userCopy('إيقاف الحساب وإبطال جلساته') ?></button>
                        </form>
                    </details>
                    <?php if (($admin['role'] ?? '') === 'owner' && $managedUser['role'] !== 'owner'): ?><details><summary><?= $userCopy('تغيير الصلاحية') ?></summary><form class="form-stack" method="post"><?= csrf_field() ?><input type="hidden" name="kind" value="user-role"><input type="hidden" name="user_id" value="<?= (int) $managedUser['id'] ?>"><label><?= $userCopy('الصلاحية') ?><select name="role"><option value="user"<?= $managedUser['role'] === 'user' ? ' selected' : '' ?>><?= $userCopy('مستخدم') ?></option><option value="admin"<?= $managedUser['role'] === 'admin' ? ' selected' : '' ?>><?= $userCopy('مدير محتوى') ?></option></select></label><label><?= $userCopy('أعد إدخال كلمة مرور المالك') ?><input type="password" name="password" autocomplete="current-password" required></label><button class="button button-secondary" type="submit"><?= $userCopy('حفظ الصلاحية وإبطال الجلسات') ?></button></form></details><?php endif; ?>
                    <details class="admin-user-delete"><summary><?= $userCopy('حذف الحساب') ?></summary><p class="source-note"><?= $userCopy('سيُحذف الحساب وتقدمه وعلاماته ومفضلاته نهائيًا، ويُحتفظ بسطر التدقيق فقط.') ?></p><form class="form-stack" method="post" data-confirm="<?= $userCopy('سيُحذف الحساب وكل بياناته المرتبطة نهائيًا.') ?>"><?= csrf_field() ?><input type="hidden" name="kind" value="user-delete"><input type="hidden" name="user_id" value="<?= (int) $managedUser['id'] ?>"><label><?= $userCopy('اكتب البريد لتأكيد الحذف') ?><input type="email" name="email_confirmation" dir="ltr" autocomplete="off" required></label><label><?= $userCopy('أعد إدخال كلمة مرورك') ?><input type="password" name="password" autocomplete="current-password" required></label><button class="button button-danger" type="submit"><?= $userCopy('حذف الحساب نهائيًا') ?></button></form></details>
                </div><?php elseif ($managedUser['role'] === 'owner'): ?><p class="source-note"><?= $userCopy('حساب المالك محمي من التعديل والحذف عبر هذه الشاشة.') ?></p><?php elseif ((int) $managedUser['id'] === (int) $admin['id']): ?><p class="source-note"><?= $userCopy('لا يمكن للمدير تعديل حسابه من هذه الشاشة.') ?></p><?php else: ?><p class="source-note"><?= $userCopy('تفاصيل المدير ظاهرة، لكن تعديلها أو حذفها محصور بمالك المنصة.') ?></p><?php endif; ?>
            </article>
        <?php endforeach; ?>
        <p class="source-note" data-admin-user-empty<?= $managedUsers ? ' hidden' : '' ?>><?= $userCopy('لا توجد حسابات تطابق بحثك.') ?></p>
        </div>
        <?php if ($managedUserPageCount > 1): ?><nav class="admin-pagination" aria-label="<?= $userCopy('صفحات المستخدمين') ?>"><?php if ($managedUserPage > 1): ?><a class="button button-secondary" href="/admin?<?= e(http_build_query(['users_q'=>$managedUserSearch,'users_page'=>$managedUserPage-1])) ?>#admin-users"><?= $userCopy('السابق') ?></a><?php endif; ?><span><?= $userCopy('صفحة') ?> <?= $managedUserPage ?> <?= $userCopy('من') ?> <?= $managedUserPageCount ?></span><?php if ($managedUserPage < $managedUserPageCount): ?><a class="button button-secondary" href="/admin?<?= e(http_build_query(['users_q'=>$managedUserSearch,'users_page'=>$managedUserPage+1])) ?>#admin-users"><?= $userCopy('التالي') ?></a><?php endif; ?></nav><?php endif; ?>
    </section>
    <?php require dirname(__DIR__, 3) . '/admin/Views/events.php'; ?>
    <?php require dirname(__DIR__,3).'/admin/Views/comments.php'; ?>
    <?php require dirname(__DIR__, 3) . '/admin/Views/audit.php'; ?>
        </div>
    </div>
</section>
