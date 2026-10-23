<section class="knowledge-page section-wrap" data-hadith-page>
    <a class="back-link" href="/">العودة إلى الرئيسية</a>
    <header class="knowledge-heading">
        <span class="eyebrow"><span class="eyebrow-dot" aria-hidden="true"></span> الموسوعة الحديثية</span>
        <h1>الحديث النبوي</h1>
        <p>ابحث بالعربية، وتظهر خلاصة حكم المحدّث مع كل نتيجة ورابطها المرجعي.</p>
    </header>
    <form class="knowledge-search" data-hadith-form role="search">
        <label for="hadith-query">كلمة أو عبارة في الحديث</label>
        <div class="search-row">
            <input id="hadith-query" name="q" type="search" minlength="2" maxlength="200" required value="<?= e(is_string($_GET['q'] ?? null) ? $_GET['q'] : '') ?>" placeholder="مثال: إنما الأعمال بالنيات">
            <button class="button button-primary" type="submit">ابحث</button>
        </div>
    </form>
    <p class="source-note">المصدر: <a href="https://dorar.net" target="_blank" rel="noopener noreferrer">الموسوعة الحديثية في الدرر السنية ↗</a></p>
    <div class="knowledge-status" data-hadith-status role="status" aria-live="polite" hidden></div>
    <div class="knowledge-results" data-hadith-results></div>
</section>
