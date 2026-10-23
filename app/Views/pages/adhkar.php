<section class="knowledge-page section-wrap" data-adhkar-page>
    <a class="back-link" href="/">العودة إلى الرئيسية</a>
    <header class="knowledge-heading">
        <span class="eyebrow"><span class="eyebrow-dot" aria-hidden="true"></span> أذكار وأدعية بمراجعها</span>
        <h1>الأذكار</h1>
        <p>تصفح الأذكار والأدعية، واستخدم العداد الشخصي عند الحاجة.</p>
    </header>
    <form class="knowledge-search" data-adhkar-form role="search">
        <label for="adhkar-query">ابحث في النص أو الاسم</label>
        <div class="search-row">
            <input id="adhkar-query" name="q" type="search" minlength="2" maxlength="80" value="<?= e(is_string($_GET['q'] ?? null) ? $_GET['q'] : '') ?>" placeholder="مثال: التسبيح">
            <select name="collection" aria-label="نوع المحتوى"><option value="adhkaars">الأذكار</option><option value="duas">الأدعية</option><option value="salawaats">الصلاة على النبي ﷺ</option></select>
            <button class="button button-primary" type="submit">ابحث</button>
        </div>
    </form>
    <p class="source-note">تُعرض مراجع الأذكار مع كل نصّ، وتظهر بقية النتائج عند طلب المزيد.</p>
    <div class="knowledge-status" data-adhkar-status role="status" aria-live="polite" hidden></div>
    <div class="knowledge-results" data-adhkar-results></div>
    <?php if ($curatedAdhkar): ?><section class="curated-section"><div class="section-heading"><div><span class="eyebrow">مختارة ومراجَعة للموقع</span><h2>أذكار موثقة</h2></div></div><div class="knowledge-results">
        <?php foreach ($curatedAdhkar as $item): ?><article class="knowledge-card dhikr-card"><span class="source-chip"><?= e($item['category']) ?></span><h2><?= e($item['title']) ?></h2><p class="dhikr-formula"><?= e($item['formula']) ?></p><?php if ($item['repeat_count']): ?><p>عدد التكرار كما ورد بالمصدر: <?= (int) $item['repeat_count'] ?></p><?php endif; ?><p class="dhikr-source">المرجع: <?= e($item['source_reference']) ?> · <a href="<?= e($item['source_url']) ?>" target="_blank" rel="noopener noreferrer">المصدر ↗</a></p></article><?php endforeach; ?>
    </div></section><?php endif; ?>
</section>
