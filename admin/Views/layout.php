<?php
declare(strict_types=1);
$adminLocale = $adminLocale ?? admin_locale();
$adminTheme = $adminTheme ?? admin_theme();
$rtl = $adminLocale === 'ar';
$chrome = $rtl ? [
    'dashboard' => 'لوحة الإدارة',
    'site' => 'فتح أثر',
    'theme' => $adminTheme === 'dark' ? 'الوضع الفاتح' : 'الوضع الداكن',
    'language' => 'English',
    'language_code' => 'en',
    'menu' => 'فتح قائمة الإدارة',
    'role_owner' => 'مالك المنصة',
    'role_admin' => 'مدير',
    'logout' => 'خروج',
] : [
    'dashboard' => 'Administration',
    'site' => 'Open Athar',
    'theme' => $adminTheme === 'dark' ? 'Light mode' : 'Dark mode',
    'language' => 'العربية',
    'language_code' => 'ar',
    'menu' => 'Open administration menu',
    'role_owner' => 'Owner',
    'role_admin' => 'Admin',
    'logout' => 'Sign out',
];
?>
<!doctype html>
<html lang="<?= e($adminLocale) ?>" dir="<?= $rtl ? 'rtl' : 'ltr' ?>" data-theme="<?= e($adminTheme) ?>">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width,initial-scale=1">
    <meta name="robots" content="noindex,nofollow">
    <meta name="referrer" content="no-referrer">
    <meta name="theme-color" content="<?= $adminTheme === 'dark' ? '#101815' : '#F4F3ED' ?>">
    <meta name="csrf-token" content="<?= e(csrf_token()) ?>">
    <title><?= e($chrome['dashboard']) ?> | أثر</title>
    <link rel="icon" type="image/png" href="/assets/images/athar-favicon.png">
    <link rel="stylesheet" href="/assets/css/admin.css?v=1">
    <script src="/assets/js/admin.js?v=1" defer></script>
</head>
<body class="admin-app">
    <a class="admin-skip-link" href="#admin-main"><?= $rtl ? 'انتقل إلى المحتوى' : 'Skip to content' ?></a>

    <header class="admin-topbar">
        <div class="admin-topbar-brand">
            <button type="button" class="admin-mobile-nav-toggle" data-admin-drawer-toggle aria-expanded="false" aria-controls="admin-primary-nav" aria-label="<?= e($chrome['menu']) ?>">
                <span aria-hidden="true">☰</span>
            </button>
            <a href="/admin" class="admin-brand-link" aria-label="<?= e($chrome['dashboard']) ?>">
                <img src="/assets/images/athar-logo-cropped.png" width="54" height="50" alt="أثر">
                <span><?= e($chrome['dashboard']) ?></span>
            </a>
        </div>

        <div class="admin-topbar-actions">
            <span class="admin-actor">
                <?= e((string) ($admin['display_name'] ?: $admin['email'])) ?>
                <small><?= e(($admin['role'] ?? '') === 'owner' ? $chrome['role_owner'] : $chrome['role_admin']) ?></small>
            </span>
            <a class="admin-topbar-link" href="/"><?= e($chrome['site']) ?></a>
            <a class="admin-topbar-link" href="/admin?lang=<?= e($chrome['language_code']) ?>"><?= e($chrome['language']) ?></a>
            <a class="admin-topbar-link" href="/admin?theme=<?= $adminTheme === 'dark' ? 'light' : 'dark' ?>"><?= e($chrome['theme']) ?></a>
            <form method="post" action="/admin/logout">
                <?= csrf_field() ?>
                <button class="admin-topbar-link admin-logout" type="submit"><?= e($chrome['logout']) ?></button>
            </form>
        </div>
    </header>

    <main id="admin-main" class="admin-main">
        <?php if (!empty($flashMessage)): ?>
            <div class="admin-global-flash<?= ($flashMessage['type'] ?? 'success') === 'error' ? ' admin-global-flash-error' : '' ?>" role="<?= ($flashMessage['type'] ?? 'success') === 'error' ? 'alert' : 'status' ?>">
                <?= e((string) $flashMessage['message']) ?>
            </div>
        <?php endif; ?>
        <?= $content ?>
    </main>

    <div class="admin-drawer-scrim" data-admin-drawer-scrim hidden></div>
</body>
</html>
