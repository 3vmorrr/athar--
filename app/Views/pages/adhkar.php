<section class="knowledge-page section-wrap adhkar-page" data-adhkar-page>
    <a class="back-link" href="/">العودة إلى الرئيسية</a>
    <header class="knowledge-heading">
        <span class="eyebrow"><span class="eyebrow-dot" aria-hidden="true"></span> أذكار وأدعية بمراجعها</span>
        <h1>الأذكار</h1>
        <p>اختر قسمًا، وردّد الذكر مع عدّاده. ينتقل العرض للذكر التالي عند اكتمال العدد المطلوب.</p>
    </header>

    <div class="adhkar-section-picker">
        <label for="adhkar-category">قسم الأذكار
            <select id="adhkar-category" data-adhkar-category disabled>
                <option value="">جارٍ تحميل الأقسام…</option>
            </select>
        </label>
        <p data-adhkar-category-progress>سيظهر تقدّمك هنا.</p>
    </div>

    <div class="knowledge-status adhkar-status" data-adhkar-status role="status" aria-live="polite">جارٍ تحميل الأذكار…</div>

    <section class="adhkar-focus-section" data-adhkar-section hidden aria-labelledby="adhkar-section-heading">
        <header class="adhkar-section-heading">
            <div><span class="eyebrow">القسم الحالي</span><h2 id="adhkar-section-heading" data-adhkar-heading></h2></div>
            <span class="adhkar-item-position" data-adhkar-position></span>
        </header>
        <div class="adhkar-section-progress" role="progressbar" aria-label="تقدّم قسم الأذكار" aria-valuemin="0" aria-valuemax="100" aria-valuenow="0"><span data-adhkar-progress></span></div>
        <article class="adhkar-session-card" data-adhkar-card>
            <p class="adhkar-text" data-adhkar-text lang="ar" dir="rtl"></p>
            <div class="adhkar-repeat-meta"><span>التكرار المطلوب</span><strong data-adhkar-required></strong></div>
            <div class="adhkar-counter-controls">
                <button class="button button-primary adhkar-count-button" type="button" data-adhkar-count>سبّح +1</button>
                <output class="adhkar-count-display" data-adhkar-count-display aria-live="polite" aria-label="عدد مرات التكرار">٠ / ٠</output>
                <button class="button button-secondary adhkar-reset-button" type="button" data-adhkar-reset>تصفير</button>
            </div>
            <p class="adhkar-completion-message" data-adhkar-completion role="status" aria-live="polite" hidden></p>
        </article>
        <p class="adhkar-source-note">النصوص من ملف <a href="https://github.com/rn0x/Adhkar-json" target="_blank" rel="noopener noreferrer">Adhkar-json</a>، والمصدر المذكور في المستودع: كتاب حصن المسلم.</p>
    </section>
</section>
