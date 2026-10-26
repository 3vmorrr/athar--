<?php
declare(strict_types=1);
$locale = admin_locale();
$rtl = $locale === 'ar';
$copy = $rtl ? [
    'title' => 'دخول إدارة أثر',
    'eyebrow' => 'مساحة الإدارة المستقلة',
    'body' => 'سجّل الدخول بحساب مدير أو مالك. جلسة الإدارة منفصلة عن حساب التصفح العام.',
    'email' => 'البريد الإلكتروني',
    'password' => 'كلمة المرور',
    'submit' => 'دخول لوحة الإدارة',
    'back' => 'العودة إلى أثر',
    'language' => 'English',
    'language_code' => 'en',
] : [
    'title' => 'Athar Admin Sign In',
    'eyebrow' => 'Independent administration',
    'body' => 'Sign in with an Admin or Owner account. The Admin session is isolated from the public website session.',
    'email' => 'Email address',
    'password' => 'Password',
    'submit' => 'Sign in to Admin',
    'back' => 'Back to Athar',
    'language' => 'العربية',
    'language_code' => 'ar',
];
?>
<!doctype html>
<html lang="<?= e($locale) ?>" dir="<?= $rtl ? 'rtl' : 'ltr' ?>">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width,initial-scale=1">
    <meta name="robots" content="noindex,nofollow">
    <meta name="referrer" content="no-referrer">
    <meta name="theme-color" content="#174F40">
    <title><?= e($copy['title']) ?></title>
    <link rel="icon" type="image/png" href="/assets/images/athar-favicon.png">
    <link rel="stylesheet" href="/assets/css/admin-auth.css?v=1">
</head>
<body>
    <main class="admin-auth-shell">
        <section class="admin-auth-card" aria-labelledby="admin-login-title">
            <header class="admin-auth-brand">
                <img src="/assets/images/athar-logo-cropped.png" width="86" height="80" alt="أثر">
                <div>
                    <span class="admin-auth-eyebrow"><?= e($copy['eyebrow']) ?></span>
                    <h1 id="admin-login-title"><?= e($copy['title']) ?></h1>
                </div>
            </header>

            <p class="admin-auth-copy"><?= e($copy['body']) ?></p>

            <?php if (($adminAuthError ?? '') !== ''): ?>
                <div class="admin-auth-alert" role="alert"><?= e((string) $adminAuthError) ?></div>
            <?php endif; ?>

            <form method="post" action="/admin/login?lang=<?= e($locale) ?>" class="admin-auth-form">
                <?= csrf_field() ?>
                <label>
                    <span><?= e($copy['email']) ?></span>
                    <input
                        name="email"
                        type="email"
                        maxlength="190"
                        autocomplete="username"
                        inputmode="email"
                        dir="ltr"
                        required
                        value="<?= e((string) ($_POST['email'] ?? '')) ?>"
                    >
                </label>
                <label>
                    <span><?= e($copy['password']) ?></span>
                    <input
                        name="password"
                        type="password"
                        minlength="10"
                        maxlength="200"
                        autocomplete="current-password"
                        required
                    >
                </label>
                <button type="submit"><?= e($copy['submit']) ?></button>
            </form>

            <footer class="admin-auth-footer">
                <a href="/"><?= e($copy['back']) ?></a>
                <a href="/admin/login?lang=<?= e($copy['language_code']) ?>" hreflang="<?= e($copy['language_code']) ?>"><?= e($copy['language']) ?></a>
            </footer>
        </section>
    </main>
</body>
</html>
