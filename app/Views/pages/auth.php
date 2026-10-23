<section class="knowledge-page section-wrap auth-page">
    <div class="auth-card">
        <span class="eyebrow"><span class="eyebrow-dot" aria-hidden="true"></span> مساحة شخصية اختيارية</span>
        <h1><?= $path === '/login' ? 'أهلًا بعودتك' : ($path === '/register' ? 'أنشئ حسابك في أثر' : ($path === '/forgot-password' ? 'استعادة كلمة المرور' : 'تعيين كلمة مرور جديدة')) ?></h1>
        <p><?= $path === '/login' ? 'تابع قراءتك وعلاماتك وخطة الختمة.' : ($path === '/register' ? 'احفظ موضع قراءتك وعلاماتك وخطة الختمة على أجهزتك.' : ($path === '/forgot-password' ? 'أدخل بريد حسابك لنرسل رابطًا مؤقتًا لإعادة التعيين.' : 'اختر كلمة مرور جديدة لا تقل عن 10 أحرف.')) ?></p>
        <?php if ($authError !== ''): ?><div class="notice notice-error" role="alert"><?= e($authError) ?></div><?php endif; ?>
        <?php if (($authNotice ?? '') !== ''): ?><div class="notice" role="status"><?= e($authNotice) ?></div><?php endif; ?>
        <form class="form-stack" method="post" action="<?= e($path) ?><?= $path === '/reset-password' && $rawToken !== '' ? '?token=' . rawurlencode($rawToken) : '' ?>"<?= $path === '/register' ? ' data-register-form' : '' ?>>
            <?= csrf_field() ?>
            <?php if (in_array($path, ['/login','/register','/forgot-password'], true)): ?><label for="auth-email">البريد الإلكتروني<input id="auth-email" name="email" type="email" maxlength="190" autocomplete="email" required value="<?= e((string) ($_POST['email'] ?? '')) ?>"></label><?php endif; ?>
            <?php if (in_array($path, ['/login','/register'], true)): ?><label for="auth-password">كلمة المرور<input id="auth-password" name="password" type="password" minlength="10" maxlength="200" autocomplete="<?= $path === '/login' ? 'current-password' : 'new-password' ?>" required></label><?php endif; ?>
            <?php if ($path === '/register'): ?><label class="check-label"><input type="checkbox" name="merge_guest_progress" value="1" data-merge-guest-progress> مراجعة ودمج موضع القراءة وعلامات القرآن المحفوظة محليًا في هذا المتصفح فقط <span data-guest-progress-summary>لا يوجد دمج حتى أختاره وأؤكده.</span></label><input type="hidden" name="guest_progress_json" data-guest-progress-json value=""><?php endif; ?>
            <?php if ($path === '/reset-password'): ?><input type="hidden" name="token" value="<?= e($rawToken) ?>"><label for="auth-password">كلمة المرور الجديدة<input id="auth-password" name="password" type="password" minlength="10" maxlength="200" autocomplete="new-password" required></label><?php endif; ?>
            <button class="button button-primary" type="submit"><?= $path === '/login' ? 'تسجيل الدخول' : ($path === '/register' ? 'إنشاء الحساب' : ($path === '/forgot-password' ? 'إرسال رابط الاستعادة' : 'تحديث كلمة المرور')) ?></button>
        </form>
        <p class="auth-switch"><?php if ($path === '/login'): ?>ليس لديك حساب؟ <a href="/register">إنشاء حساب</a> · <a href="/forgot-password">نسيت كلمة المرور؟</a><?php elseif ($path === '/register'): ?>لديك حساب؟ <a href="/login">تسجيل الدخول</a><?php else: ?><a href="/login">العودة إلى تسجيل الدخول</a><?php endif; ?></p>
        <p class="source-note">التصفح العام متاح دون حساب. لا تُرسل بياناتك إلا عند اختيار الحفظ.</p>
    </div>
</section>
