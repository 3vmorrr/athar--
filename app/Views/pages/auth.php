<section class="knowledge-page section-wrap auth-page">
    <div class="auth-card">
        <span class="eyebrow"><span class="eyebrow-dot" aria-hidden="true"></span> مساحة شخصية اختيارية</span>
        <h1><?= $path === '/login' ? 'أهلًا بعودتك' : 'أنشئ حسابك في أثر' ?></h1>
        <p><?= $path === '/login' ? 'تابع قراءتك وعلاماتك وخطة الختمة.' : 'احفظ موضع قراءتك وعلاماتك وخطة الختمة على أجهزتك.' ?></p>
        <?php if ($authError !== ''): ?><div class="notice notice-error" role="alert"><?= e($authError) ?></div><?php endif; ?>
        <form class="form-stack" method="post" action="<?= e($path) ?>">
            <?= csrf_field() ?>
            <label for="auth-email">البريد الإلكتروني<input id="auth-email" name="email" type="email" maxlength="190" autocomplete="email" required value="<?= e((string) ($_POST['email'] ?? '')) ?>"></label>
            <label for="auth-password">كلمة المرور<input id="auth-password" name="password" type="password" minlength="10" maxlength="200" autocomplete="<?= $path === '/login' ? 'current-password' : 'new-password' ?>" required></label>
            <button class="button button-primary" type="submit"><?= $path === '/login' ? 'تسجيل الدخول' : 'إنشاء الحساب' ?></button>
        </form>
        <p class="auth-switch"><?php if ($path === '/login'): ?>ليس لديك حساب؟ <a href="/register">إنشاء حساب</a><?php else: ?>لديك حساب؟ <a href="/login">تسجيل الدخول</a><?php endif; ?></p>
        <p class="source-note">التصفح العام متاح دون حساب. لا تُرسل بياناتك إلا عند اختيار الحفظ.</p>
    </div>
</section>
