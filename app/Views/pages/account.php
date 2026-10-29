<section class="knowledge-page section-wrap account-page"<?= !empty($clearGuestProgress) ? ' data-clear-guest-progress' : '' ?>>
    <header class="account-page-heading">
        <div>
            <span class="eyebrow">مساحتك الخاصة</span>
            <h1>حسابي</h1>
            <p><?= e($user['display_name'] ?: $user['email']) ?> · علاماتك وتقدمك لا يراهما غيرك.</p>
        </div>
        <?php if (!empty($user['email_verified_at'])): ?><a class="button button-secondary account-export" href="/account?export=1">تصدير بياناتي JSON</a><?php endif; ?>
    </header>

    <?php if ($accountError !== ''): ?><div class="notice notice-error" role="alert"><?= e($accountError) ?></div><?php endif; ?>

    <?php if (empty($user['email_verified_at'])): ?>
        <section class="account-verification account-pending-verification" id="account-verification" aria-labelledby="verify-account-heading">
            <span class="eyebrow">خطوة أخيرة لتفعيل الحساب</span>
            <h2 id="verify-account-heading">أكد بريدك الإلكتروني</h2>
            <p>الحساب غير مفعّل بعد. أدخل رمز التحقق المكوّن من 6 أرقام المرسل إلى <strong dir="ltr"><?= e($user['email']) ?></strong>. لن تتاح بيانات الحساب ومزاياه قبل التأكيد.</p>
            <form class="form-stack account-form-grid" method="post" action="/account">
                <?= csrf_field() ?><input type="hidden" name="action" value="verify-email-code">
                <label>رمز التحقق<input name="code" inputmode="numeric" pattern="[0-9]{6}" minlength="6" maxlength="6" autocomplete="one-time-code" required></label>
                <div class="account-form-action"><button class="button button-primary" type="submit">تأكيد البريد وتفعيل الحساب</button></div>
            </form>
            <form method="post" action="/account"><?= csrf_field() ?><input type="hidden" name="action" value="resend-verification"><button class="text-button" type="submit">إرسال رمز جديد</button></form>
        </section>
    <?php else: ?>
    <div class="account-layout">
        <aside class="account-sidebar">
            <span class="account-sidebar-title">أقسام الحساب</span>
            <nav class="account-sidebar-nav" aria-label="أقسام الحساب" data-account-nav>
                <a href="#account-overview" aria-current="location" aria-controls="account-overview">النظرة العامة</a>
                <a href="#account-profile" aria-controls="account-profile">بيانات الحساب</a>
                <a href="#account-quran" aria-controls="account-quran">القرآن والعلامات</a>
                <a href="#account-learning" aria-controls="account-learning">الدروس والمفضلة</a>
                <a href="#account-preferences" aria-controls="account-preferences">التفضيلات</a>
                <a href="#account-security" aria-controls="account-security">الأمان والخصوصية</a>
            </nav>
            <p class="account-sidebar-note">حسابك اختياري؛ تصفح محتوى أثر متاح للجميع.</p>
        </aside>

        <div class="account-content">
            <section class="account-section account-panel" id="account-overview" tabindex="-1" data-account-panel>
                <div class="section-heading account-section-heading">
                    <div><span class="eyebrow">إحصاءاتك</span><h2>نشاطك في أثر</h2></div>
                    <p>ملخص إنجازاتك المحفوظة</p>
                </div>
                <div class="inventory-grid account-stats">
                    <article><h3>جلسات القرآن <span><?= (int) $accountStats['quran_sessions'] ?></span></h3></article>
                    <article><h3>دروس مكتملة <span><?= (int) $accountStats['lesson_completions'] ?></span></h3></article>
                    <article><h3>أذكار مكتملة <span><?= (int) $accountStats['adhkar_completions'] ?></span></h3></article>
                    <article><h3>كتب نُزّلت <span><?= (int) $accountStats['book_downloads'] ?></span></h3></article>
                    <article><h3>أيام نشطة <span><?= (int) $accountStats['active_days'] ?></span></h3></article>
                </div>
                <p class="source-note account-privacy-note">نحتفظ بأعداد يومية إجمالية فقط، من دون سجلّ بأسماء الآيات أو الأذكار التي قرأتها.</p>

                <div class="account-overview-grid">
                    <article class="knowledge-card account-card">
                        <span class="eyebrow">القرآن الكريم</span>
                        <h2>آخر موضع قراءة</h2>
                        <?php if ($position): ?>
                            <p>السورة <?= (int) $position['surah'] ?> · الآية <?= (int) $position['ayah'] ?></p>
                            <a class="button button-secondary" href="/quran?surah=<?= (int) $position['surah'] ?>&amp;ayah=<?= (int) $position['ayah'] ?>">تابع القراءة</a>
                        <?php else: ?>
                            <p>سيظهر موضعك هنا بعد القراءة وتسجيل الدخول.</p><a href="/quran">افتح القرآن الكريم</a>
                        <?php endif; ?>
                    </article>

                    <article class="knowledge-card account-card">
                        <span class="eyebrow">خطة شخصية</span>
                        <h2>خطة الختمة</h2>
                        <?php $planDraft=$accountError!==''&&in_array($_POST['action']??'', ['plan','plan-restart'],true);
                        $draftValue=static fn(string $key,string $fallback):string=>$planDraft&&is_string($_POST[$key]??null)?mb_substr($_POST[$key],0,20,'UTF-8'):$fallback;
                        $draftRevision=$planDraft?(int)($_POST['expected_revision']??0):(int)($plan['revision']??0); ?>
                        <?php if ($plan): ?>
                            <p>المتبقي <?= (int)$plan['remaining_pages'] ?> صفحة حتى <?= e($plan['target_date']) ?> · الحالة: <?= $plan['status']==='active'?'نشطة':($plan['status']==='paused'?'متوقفة مؤقتًا':'مكتملة') ?>.</p>
                            <p class="plan-rate">ورد اليوم: <strong><?= (int)$plan['daily_pages'] ?> صفحة</strong> · <?= (int)$plan['days_remaining'] ?> يومًا متبقيًا (بتوقيت UTC، ويشمل يوم الإتمام).</p>
                            <?php if ($plan['overdue']): ?><p>انتهى الموعد المستهدف. حدّث الموعد أو سجّل تقدمك صراحةً؛ لا تُحتسب الصفحات تلقائيًا.</p><?php endif; ?>
                            <?php if ($plan['status']!=='finished'): ?><div class="plan-actions">
                                <?php foreach (['paused'=>'إيقاف مؤقت','active'=>'استئناف','finished'=>'إنهاء الخطة'] as $status=>$label): ?>
                                <form method="post"<?= $status==='finished'?' data-confirm="تسجيل هذه الخطة كمكتملة بعد إكمال 604 صفحات؟"':'' ?>><?= csrf_field() ?><input type="hidden" name="action" value="plan-status"><input type="hidden" name="expected_revision" value="<?= (int)$plan['revision'] ?>"><input type="hidden" name="status" value="<?= e($status) ?>"><button class="button button-secondary" type="submit"<?= ($status==='finished'&&(int)$plan['completed_pages']!==604)||$status===$plan['status']?' disabled':'' ?>><?= e($label) ?></button></form>
                                <?php endforeach; ?>
                            </div><?php endif; ?>
                        <?php else: ?><p>اختر تاريخ البداية وموعد الإتمام. التقدم يُسجّل باختيارك فقط.</p><?php endif; ?>
                        <form class="form-stack account-plan-form" method="post" action="/account"<?= ($plan['status']??'')==='finished'?' data-confirm="بدء ختمة جديدة وتصفير صفحات هذه الخطة؟"':'' ?>>
                            <?= csrf_field() ?><input type="hidden" name="action" value="<?= ($plan['status']??'')==='finished'?'plan-restart':'plan' ?>">
                            <input type="hidden" name="expected_revision" value="<?= $draftRevision ?>">
                            <label for="plan-start">تاريخ البداية<input id="plan-start" name="start_date" type="date" value="<?= e($draftValue('start_date',($plan['status']??'')==='finished'?gmdate('Y-m-d'):($plan['start_date']??gmdate('Y-m-d')))) ?>" required></label>
                            <label for="plan-target">موعد الإتمام<input id="plan-target" name="target_date" type="date" value="<?= e($draftValue('target_date',($plan['status']??'')==='finished'?gmdate('Y-m-d',time()+30*86400):($plan['target_date']??gmdate('Y-m-d',time()+30*86400)))) ?>" required></label>
                            <?php if (($plan['status']??'')!=='finished'): ?><label for="plan-pages">الصفحات المنجزة من 604<input id="plan-pages" name="completed_pages" type="number" min="0" max="604" value="<?= e($draftValue('completed_pages',(string)($plan['completed_pages']??0))) ?>" required></label><?php endif; ?>
                            <button class="button button-primary" type="submit"><?= ($plan['status']??'')==='finished'?'بدء ختمة جديدة':'حفظ الخطة' ?></button>
                            <?php if ($planDraft): ?><a class="text-button" href="/account">مراجعة الخطة الحالية وإلغاء هذه المسودة</a><?php endif; ?>
                        </form>
                    </article>
                </div>
            </section>

            <section class="account-section account-panel" id="account-profile" tabindex="-1" data-account-panel hidden>
                <div class="section-heading account-section-heading"><div><span class="eyebrow">معلوماتك</span><h2>بيانات الحساب</h2></div></div>
                <p class="account-email-line">البريد: <?= e($user['email']) ?> <span aria-hidden="true">·</span> اسم المستخدم: @<?= e($user['username'] ?? '') ?></p>
                <form class="form-stack account-form-grid" method="post" action="/account">
                    <?= csrf_field() ?><input type="hidden" name="action" value="profile-save">
                    <label>الاسم الظاهر<input name="display_name" minlength="2" maxlength="80" value="<?= e($user['display_name'] ?? '') ?>" required></label>
                    <label>اسم المستخدم<input name="username" pattern="[a-z0-9_]{3,24}" minlength="3" maxlength="24" value="<?= e($user['username'] ?? '') ?>" required><small>3–24 حرفًا إنجليزيًا صغيرًا أو رقمًا أو شرطة سفلية.</small></label>
                    <label>كلمة المرور الحالية<input type="password" name="current_password" autocomplete="current-password" required></label>
                    <label class="check-label"><input type="checkbox" name="email_marketing_opt_in" value="1"<?= !empty($user['email_marketing_opt_in']) ? ' checked' : '' ?>> تلقي أخبار وتحديثات أثر بالبريد. يمكن إلغاء الاشتراك في أي وقت.</label>
                    <div class="account-form-action"><button class="button button-secondary" type="submit">حفظ بيانات الحساب</button></div>
                </form>
                <?php if (empty($user['email_verified_at'])): ?>
                    <div class="account-verification">
                        <h3>تأكيد البريد الإلكتروني</h3>
                        <p>حالة البريد: غير مؤكد. أدخل الرمز الذي أرسلناه إلى <?= e($user['email']) ?>.</p>
                        <form class="form-stack account-form-grid" method="post" action="/account">
                            <?= csrf_field() ?><input type="hidden" name="action" value="verify-email-code">
                            <label>رمز التحقق<input name="code" inputmode="numeric" pattern="[0-9]{6}" minlength="6" maxlength="6" autocomplete="one-time-code" required></label>
                            <div class="account-form-action"><button class="button button-primary" type="submit">تأكيد البريد</button></div>
                        </form>
                        <form method="post" action="/account"><?= csrf_field() ?><input type="hidden" name="action" value="resend-verification"><button class="text-button" type="submit">إرسال رمز جديد</button></form>
                    </div>
                <?php else: ?><p class="source-note account-verified-note">تم تأكيد البريد الإلكتروني.</p><?php endif; ?>
            </section>

            <section class="account-section account-panel" id="account-quran" tabindex="-1" data-account-panel hidden>
                <div class="section-heading account-section-heading"><div><span class="eyebrow">مرجعك الشخصي</span><h2>القرآن والعلامات</h2></div></div>
                <h3 class="account-subheading">الآيات المحفوظة</h3>
                <?php if (!$bookmarks): ?><p class="source-note">لم تحفظ آيات بعد. استخدم زر الحفظ بجوار الآية في صفحة القرآن.</p>
                <?php else: ?><div class="knowledge-results account-bookmarks"><?php foreach ($bookmarks as $bookmark): ?>
                    <article class="knowledge-card bookmark-card"><a href="/quran?surah=<?= (int) $bookmark['surah'] ?>&amp;ayah=<?= (int) $bookmark['ayah'] ?>">سورة <?= (int) $bookmark['surah'] ?> · الآية <?= (int) $bookmark['ayah'] ?></a>
                        <form method="post" action="/account" data-confirm="حذف هذه العلامة؟"><?= csrf_field() ?><input type="hidden" name="action" value="bookmark-delete"><input type="hidden" name="id" value="<?= (int) $bookmark['id'] ?>"><button class="text-button" type="submit">حذف</button></form>
                    </article>
                <?php endforeach; ?></div><?php endif; ?>
            </section>

            <section class="account-section account-panel" id="account-learning" tabindex="-1" data-account-panel hidden>
                <div class="section-heading account-section-heading"><div><span class="eyebrow">تعلمك ومتابعتك</span><h2>الدروس والمفضلة</h2></div></div>
                <h3 class="account-subheading">المفضلة</h3>
                <?php if (!$favorites): ?><p class="source-note">لم تضف كتبًا أو دروسًا إلى المفضلة بعد.</p>
                <?php else: ?><div class="knowledge-results account-bookmarks"><?php foreach ($favorites as $favorite): ?>
                    <article class="knowledge-card bookmark-card"><div><span class="source-chip"><?= $favorite['target_type'] === 'book' ? 'كتاب' : 'درس' ?></span><p><strong><?= e($favorite['title']) ?></strong><?php if ($favorite['author']): ?><br><span class="book-details"><?= e($favorite['author']) ?></span><?php endif; ?></p></div><a class="button button-secondary" href="<?= $favorite['target_type'] === 'book' ? '/books' : '/lessons' ?>">عرض</a></article>
                <?php endforeach; ?></div><?php endif; ?>

                <h3 class="account-subheading account-subheading-spaced">تقدم الدروس</h3>
                <?php if (!$videoProgress): ?><p class="source-note">سيظهر تقدم الدروس التي تتابعها من فهرس أثر هنا.</p>
                <?php else: ?><div class="knowledge-results account-bookmarks"><?php foreach ($videoProgress as $item): ?>
                    <article class="knowledge-card bookmark-card"><div><strong><?= e($item['title']) ?></strong><p><?= $item['is_complete'] ? 'مكتمل' : 'آخر موضع محفوظ عند ' . (int) $item['position_seconds'] . ' ثانية' ?></p></div><a class="button button-secondary" href="/lessons?lesson=<?= (int) $item['id'] ?>">استئناف تقريبي</a></article>
                <?php endforeach; ?></div><?php endif; ?>
            </section>

            <section class="account-section account-panel" id="account-preferences" tabindex="-1" data-account-panel hidden>
                <div class="section-heading account-section-heading"><div><span class="eyebrow">تجربتك</span><h2>تفضيلات الحساب</h2></div></div>
                <form class="form-stack account-preferences-form" method="post" action="/account">
                    <?= csrf_field() ?><input type="hidden" name="action" value="save-preferences">
                    <label>المظهر<select name="theme"><option value="light"<?= $themePreference === 'light' ? ' selected' : '' ?>>فاتح</option><option value="dark"<?= $themePreference === 'dark' ? ' selected' : '' ?>>داكن</option></select></label>
                    <button class="button button-secondary" type="submit">حفظ التفضيلات</button>
                </form>
            </section>

            <section class="account-section account-panel" id="account-security" tabindex="-1" data-account-panel hidden>
                <div class="section-heading account-section-heading"><div><span class="eyebrow">حماية بياناتك</span><h2>الأمان والخصوصية</h2></div></div>
                <div class="account-security-grid">
                    <article class="account-security-card">
                        <h3>تغيير كلمة المرور</h3><p>سيؤدي حفظ كلمة مرور جديدة إلى إبطال الجلسات الأخرى على أجهزتك.</p>
                        <form class="form-stack" method="post" action="/account"><?= csrf_field() ?><input type="hidden" name="action" value="change-password"><label>كلمة المرور الحالية<input type="password" name="current_password" autocomplete="current-password" required></label><label>كلمة المرور الجديدة<input type="password" name="new_password" minlength="10" maxlength="200" autocomplete="new-password" data-password-strength-check required></label><label>تأكيد كلمة المرور<input type="password" name="new_password_confirmation" minlength="10" maxlength="200" autocomplete="new-password" required></label><button class="button button-secondary" type="submit">تغيير كلمة المرور</button></form>
                    </article>
                    <article class="account-security-card account-delete-card">
                        <h3>حذف الحساب</h3><p>سيؤدي ذلك إلى حذف الحساب وعلاماته وتقدمه وخطة الختمة. قد يُحتفظ بسجل تدقيق محدود لعملية الحذف لأغراض أمنية وتشغيلية.</p>
                        <form class="form-stack" method="post" action="/account" data-confirm="سيُحذف الحساب وبياناته نهائيًا، مع الاحتفاظ بسجل تدقيق محدود. هل تريد المتابعة؟"><?= csrf_field() ?><input type="hidden" name="action" value="delete-account"><label for="delete-password">أكد كلمة المرور<input id="delete-password" type="password" name="password" autocomplete="current-password" required></label><button class="button button-danger" type="submit">حذف حسابي</button></form>
                    </article>
                </div>
            </section>
        </div>
    </div>
    <?php endif; ?>
</section>
