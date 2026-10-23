<!doctype html>
<html lang="ar" dir="rtl" data-theme="light" data-saved-theme="<?= e((string) ($themePreference ?? '')) ?>">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta name="theme-color" content="#F5F3ED">
    <meta name="description" content="<?= e($seoDescription ?? 'اقرأ القرآن، وابحث عن الحديث، واستكشف الدروس والكتب والأذكار في منصة أثر العربية.') ?>">
    <meta name="robots" content="<?= e($seoRobots ?? (!empty($canonicalUrl) ? 'index,follow,max-image-preview:large' : 'noindex,nofollow')) ?>">
    <?php if (!empty($canonicalUrl)): ?><link rel="canonical" href="<?= e($canonicalUrl) ?>"><?php endif; ?>
    <meta property="og:type" content="<?= e($ogType ?? 'website') ?>">
    <meta property="og:locale" content="ar_AR">
    <meta property="og:site_name" content="أثر">
    <meta property="og:title" content="<?= e($seoTitle ?? ($title . ' | أثر')) ?>">
    <meta property="og:description" content="<?= e($seoDescription ?? 'منصة أثر العربية للقرآن والمعرفة والعبادة.') ?>">
    <?php if (!empty($canonicalUrl)): ?><meta property="og:url" content="<?= e($canonicalUrl) ?>"><?php endif; ?>
    <?php if (!empty($siteBaseUrl)): ?><meta property="og:image" content="<?= e($siteBaseUrl . '/assets/images/athar-logo-cropped.png') ?>"><meta property="og:image:type" content="image/png"><meta property="og:image:width" content="692"><meta property="og:image:height" content="640"><meta property="og:image:alt" content="شعار أثر"><meta name="twitter:image" content="<?= e($siteBaseUrl . '/assets/images/athar-logo-cropped.png') ?>"><?php endif; ?>
    <meta name="twitter:card" content="summary">
    <meta name="twitter:title" content="<?= e($seoTitle ?? ($title . ' | أثر')) ?>">
    <meta name="twitter:description" content="<?= e($seoDescription ?? 'منصة أثر العربية للقرآن والمعرفة والعبادة.') ?>">
    <meta name="csrf-token" content="<?= e(csrf_token()) ?>">
    <title><?= e($seoTitle ?? ($title . ' | أثر')) ?></title>
    <link rel="icon" type="image/png" sizes="512x512" href="/assets/images/athar-favicon.png">
    <link rel="apple-touch-icon" href="/assets/images/athar-favicon.png">
    <link rel="manifest" href="/site.webmanifest">
    <link rel="stylesheet" href="/assets/css/app.css?v=<?= (int) (@filemtime(dirname(__DIR__, 2) . '/public/assets/css/app.css') ?: 1) ?>">
    <script src="/assets/js/app.js?v=<?= (int) (@filemtime(dirname(__DIR__, 2) . '/public/assets/js/app.js') ?: 1) ?>" defer></script>
    <?php if (!empty($loadKnowledgeScript)): ?><script src="/assets/js/knowledge.js?v=<?= (int) (@filemtime(dirname(__DIR__, 2) . '/public/assets/js/knowledge.js') ?: 1) ?>" defer></script><?php endif; ?>
    <?php if (!empty($structuredData)): ?><script type="application/ld+json"><?= json_encode($structuredData, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES | JSON_HEX_TAG | JSON_HEX_AMP | JSON_HEX_APOS | JSON_HEX_QUOT) ?></script><?php endif; ?>
</head>
<body>
    <a class="skip-link" href="#main-content">انتقل إلى المحتوى</a>

    <header class="site-header">
        <div class="header-inner">
            <a class="brand" href="/" aria-label="الصفحة الرئيسية">
                <img class="brand-logo" src="/assets/images/athar-logo-cropped.png?v=<?= (int) (@filemtime(dirname(__DIR__, 2) . '/public/assets/images/athar-logo-cropped.png') ?: 1) ?>" alt="أثر" width="150" height="92" fetchpriority="high">
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
            <div class="footer-main"><a class="footer-brand" href="/" aria-label="أثر"><img src="/assets/images/athar-logo-cropped.png?v=<?= (int) (@filemtime(dirname(__DIR__, 2) . '/public/assets/images/athar-logo-cropped.png') ?: 1) ?>" alt="أثر" width="120" height="76" loading="lazy"></a><p>منصة عربية مفتوحة للقرآن والمعرفة والعبادة.</p></div>
            <nav class="footer-nav" aria-label="روابط الموقع"><a href="/quran">القرآن</a><a href="/hadith">الحديث</a><a href="/lessons">الدروس</a><a href="/books">الكتب</a><a href="/adhkar">الأذكار</a><a href="/about">عن أثر</a></nav>
            <small class="footer-copy">© <?= date('Y') ?> أثر</small>
        </div>
    </footer>
</body>
</html>
