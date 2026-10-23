<section class="knowledge-page section-wrap account-page">
    <header class="knowledge-heading"><span class="eyebrow">مساحتك الخاصة</span><h1>حسابي</h1><p><?= e($user['email']) ?> · علاماتك وتقدمك لا يراها غيرك.</p></header>
    <?php if ($flashMessage): ?><div class="notice" role="status"><?= e($flashMessage['message']) ?></div><?php endif; ?>
    <?php if ($accountError !== ''): ?><div class="notice notice-error" role="alert"><?= e($accountError) ?></div><?php endif; ?>
    <div class="account-grid">
        <article class="knowledge-card account-card">
            <h2>آخر موضع قراءة</h2>
            <?php if ($position): ?><p>السورة <?= (int) $position['surah'] ?> · الآية <?= (int) $position['ayah'] ?></p><a class="button button-secondary" href="/quran?surah=<?= (int) $position['surah'] ?>&amp;ayah=<?= (int) $position['ayah'] ?>">تابع القراءة</a>
            <?php else: ?><p>سيظهر موضعك هنا بعد القراءة وتسجيل الدخول.</p><a href="/quran">افتح القرآن الكريم</a><?php endif; ?>
        </article>
        <article class="knowledge-card account-card">
            <h2>خطة الختمة</h2>
            <?php if ($plan): $remaining = max(0, (int) $plan['total_pages'] - (int) $plan['completed_pages']); $days = max(1, (int) (new DateTimeImmutable('today'))->diff(new DateTimeImmutable($plan['target_date']))->format('%r%a')); ?>
                <p>المتبقي <?= $remaining ?> صفحة حتى <?= e($plan['target_date']) ?> · الحالة: <?= $plan['status'] === 'active' ? 'نشطة' : ($plan['status'] === 'paused' ? 'متوقفة مؤقتًا' : 'مكتملة') ?>.</p>
                <p class="plan-rate">المعدل الحالي: <strong><?= (int) ceil($remaining / $days) ?> صفحة يوميًا</strong> على <?= $days ?> يومًا متبقيًا.</p>
                <div class="plan-actions"><form method="post"><?= csrf_field() ?><input type="hidden" name="action" value="plan-status"><input type="hidden" name="status" value="paused"><button class="button button-secondary" type="submit">إيقاف مؤقت</button></form><form method="post"><?= csrf_field() ?><input type="hidden" name="action" value="plan-status"><input type="hidden" name="status" value="active"><button class="button button-secondary" type="submit">استئناف</button></form><form method="post" data-confirm="تسجيل هذه الخطة كمكتملة؟"><?= csrf_field() ?><input type="hidden" name="action" value="plan-status"><input type="hidden" name="status" value="finished"><button class="button button-secondary" type="submit">إنهاء الخطة</button></form></div>
            <?php else: ?><p>اختر موعدًا مستهدفًا، ويمكنك تعديل خطتك لاحقًا.</p><?php endif; ?>
            <form class="form-stack" method="post" action="/account">
                <?= csrf_field() ?><input type="hidden" name="action" value="plan">
                <label for="plan-target">موعد الإتمام<input id="plan-target" name="target_date" type="date" min="<?= e(date('Y-m-d', strtotime('+1 day'))) ?>" value="<?= e((string) ($plan['target_date'] ?? '')) ?>" required></label>
                <label for="plan-pages">الصفحات المنجزة من 604<input id="plan-pages" name="completed_pages" type="number" min="0" max="604" value="<?= (int) ($plan['completed_pages'] ?? 0) ?>" required></label>
                <button class="button button-primary" type="submit">حفظ الخطة</button>
            </form>
        </article>
    </div>
    <section class="account-section">
        <div class="section-heading"><div><span class="eyebrow">مرجعك الشخصي</span><h2>الآيات المحفوظة</h2></div><a class="button button-secondary" href="/account?export=1">تصدير بياناتي JSON</a></div>
        <?php if (!$bookmarks): ?><p class="source-note">لم تحفظ آيات بعد. استخدم زر الحفظ بجوار الآية في صفحة القرآن.</p>
        <?php else: ?><div class="knowledge-results account-bookmarks"><?php foreach ($bookmarks as $bookmark): ?>
            <article class="knowledge-card bookmark-card"><a href="/quran?surah=<?= (int) $bookmark['surah'] ?>&amp;ayah=<?= (int) $bookmark['ayah'] ?>">سورة <?= (int) $bookmark['surah'] ?> · الآية <?= (int) $bookmark['ayah'] ?></a>
                <form method="post" action="/account" data-confirm="حذف هذه العلامة؟"><?= csrf_field() ?><input type="hidden" name="action" value="bookmark-delete"><input type="hidden" name="id" value="<?= (int) $bookmark['id'] ?>"><button class="text-button" type="submit">حذف</button></form>
            </article>
        <?php endforeach; ?></div><?php endif; ?>
    </section>
    <section class="account-section"><div class="section-heading"><div><span class="eyebrow">قائمة شخصية</span><h2>المفضلة</h2></div></div><?php if (!$favorites): ?><p class="source-note">لم تضف كتبًا أو دروسًا إلى المفضلة بعد.</p><?php else: ?><div class="knowledge-results account-bookmarks"><?php foreach ($favorites as $favorite): ?><article class="knowledge-card bookmark-card"><div><span class="source-chip"><?= $favorite['target_type'] === 'book' ? 'كتاب' : 'درس' ?></span><p><strong><?= e($favorite['title']) ?></strong><?php if ($favorite['author']): ?><br><span class="book-details"><?= e($favorite['author']) ?></span><?php endif; ?></p></div><a class="button button-secondary" href="<?= $favorite['target_type'] === 'book' ? '/books' : '/lessons' ?>">عرض</a></article><?php endforeach; ?></div><?php endif; ?></section>
    <section class="account-section"><div class="section-heading"><div><span class="eyebrow">مشاهدة الدروس</span><h2>تقدم الدروس</h2></div></div><?php if (!$videoProgress): ?><p class="source-note">سيظهر تقدم الدروس التي تتابعها من فهرس أثر هنا.</p><?php else: ?><div class="knowledge-results account-bookmarks"><?php foreach ($videoProgress as $item): ?><article class="knowledge-card bookmark-card"><div><strong><?= e($item['title']) ?></strong><p><?= $item['is_complete'] ? 'مكتمل' : 'آخر موضع محفوظ عند ' . (int) $item['position_seconds'] . ' ثانية' ?></p></div><a class="button button-secondary" href="/lessons?lesson=<?= (int) $item['id'] ?>">استئناف تقريبي</a></article><?php endforeach; ?></div><?php endif; ?></section>
    <section class="account-section privacy-panel"><h2>حذف الحساب</h2><p>سيؤدي ذلك إلى حذف حسابك وعلاماتك وتقدمك وخطة الختمة نهائيًا.</p><form class="inline-form" method="post" action="/account" data-confirm="سيُحذف الحساب وبياناته نهائيًا. هل تريد المتابعة؟"><?= csrf_field() ?><input type="hidden" name="action" value="delete-account"><label for="delete-password">أكد كلمة المرور<input id="delete-password" type="password" name="password" autocomplete="current-password" required></label><button class="button button-danger" type="submit">حذف حسابي</button></form></section>
</section>
