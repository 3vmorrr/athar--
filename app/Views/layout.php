<!doctype html>
<html lang="ar" dir="rtl" data-theme="light">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta name="theme-color" content="#F5F3ED">
    <meta name="description" content="أثر — منصة عربية مجانية للقرآن والمعرفة الإسلامية وأدوات العبادة الشخصية.">
    <meta name="csrf-token" content="<?= e(csrf_token()) ?>">
    <title><?= e($title) ?> | أثر</title>
    <link rel="icon" type="image/png" href="/assets/images/athar-logo.png">
    <link rel="stylesheet" href="/assets/css/app.css">
    <script src="/assets/js/app.js" defer></script>
    <script src="/assets/js/knowledge.js" defer></script>
</head>
<body>
    <a class="skip-link" href="#main-content">انتقل إلى المحتوى</a>

    <header class="site-header">
        <div class="header-inner">
            <a class="brand" href="/" aria-label="أثر — الصفحة الرئيسية">
                <img class="brand-logo" src="/assets/images/athar-logo.png" alt="" width="64" height="52">
                <span class="brand-copy">
                    <span class="brand-name">أثر</span>
                    <span class="brand-tagline">معرفةٌ تنفع وأثرٌ يبقى</span>
                </span>
            </a>

            <button class="icon-button menu-toggle" type="button" aria-label="فتح القائمة" aria-expanded="false" aria-controls="primary-nav" data-menu-toggle>
                <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 7h16M4 12h16M4 17h16" /></svg>
            </button>

            <nav class="primary-nav" id="primary-nav" aria-label="التنقل الرئيسي" data-open="false">
                <a href="/quran"<?= $activePath === '/quran' ? ' aria-current="page"' : '' ?>>القرآن</a>
                <a href="/hadith"<?= $activePath === '/hadith' ? ' aria-current="page"' : '' ?>>الحديث</a>
                <a href="/books"<?= $activePath === '/books' ? ' aria-current="page"' : '' ?>>الكتب</a>
                <a href="/lessons"<?= $activePath === '/lessons' ? ' aria-current="page"' : '' ?>>الدروس</a>
                <a href="/adhkar"<?= $activePath === '/adhkar' ? ' aria-current="page"' : '' ?>>الأذكار</a>
            </nav>

            <div class="header-actions">
                <?php if (!empty($user)): ?>
                    <a class="account-link" href="/account">حسابي</a>
                    <?php if (in_array(($user['role'] ?? ''), ['admin','owner'], true)): ?><a class="account-link" href="/admin">الإدارة</a><?php endif; ?>
                    <form method="post" action="/logout" class="logout-form"><?= csrf_field() ?><button class="text-button" type="submit">خروج</button></form>
                <?php else: ?>
                    <a class="account-link" href="/login">دخول</a>
                <?php endif; ?>
                <button class="theme-toggle" type="button" data-theme-toggle aria-pressed="false">
                    <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M20.2 15.2A8.5 8.5 0 0 1 8.8 3.8 8.5 8.5 0 1 0 20.2 15.2Z" /></svg>
                    <span data-theme-label>الوضع الداكن</span>
                </button>
            </div>
        </div>
    </header>

    <main id="main-content" class="main-content">
        <?php if (!empty($flashMessage)): ?><div class="global-flash section-wrap" role="status"><?= e($flashMessage['message']) ?></div><?php endif; ?>
        <?= $content ?>
    </main>

    <footer class="site-footer">
        <div class="footer-inner">
            <a class="footer-brand" href="/">أثر</a>
            <p>منصة مجانية للقرآن والمعرفة الإسلامية النافعة.</p>
            <a href="/about">عن أثر ومصادر المحتوى</a>
        </div>
    </footer>
</body>
</html>
