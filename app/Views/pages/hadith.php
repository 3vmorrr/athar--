<section class="knowledge-page section-wrap" data-hadith-page>
    <a class="back-link" href="/">العودة إلى الرئيسية</a>
    <header class="knowledge-heading">
        <span class="eyebrow"><span class="eyebrow-dot" aria-hidden="true"></span> بحث مباشر في مجموعات الحديث</span>
        <h1>الحديث النبوي</h1>
        <p>ابحث في النصوص المتاحة، وتحقق دائمًا من المرجع قبل الاقتباس أو الاستدلال.</p>
    </header>
    <form class="knowledge-search" data-hadith-form role="search">
        <label for="hadith-query">كلمة أو عبارة بالإنجليزية (نتائج النص العربي)</label>
        <div class="search-row">
            <input id="hadith-query" name="q" type="search" minlength="2" maxlength="80" required value="<?= e(is_string($_GET['q'] ?? null) ? $_GET['q'] : '') ?>" placeholder="مثال: prayer">
            <select name="collection" aria-label="مجموعة الحديث">
                <option value="">كل المجموعات</option><option value="bukhari">صحيح البخاري</option><option value="muslim">صحيح مسلم</option><option value="abudawud">سنن أبي داود</option><option value="tirmidhi">جامع الترمذي</option>
            </select>
            <button class="button button-primary" type="submit">ابحث</button>
        </div>
    </form>
    <p class="source-note">المصدر البرمجي: <a href="https://ummahapi.com/hadith-api" target="_blank" rel="noopener noreferrer">UmmahAPI</a>، والبيانات من مجموعة <a href="https://github.com/fawazahmed0/hadith-api" target="_blank" rel="noopener noreferrer">hadith-api</a>. البحث بالكلمات الإنجليزية فقط، والنتائج محدودة بما يفهرسه المصدر؛ الدرجة تُعرض فقط عند ورودها معه.</p>
    <div class="knowledge-status" data-hadith-status role="status" aria-live="polite" hidden></div>
    <div class="knowledge-results" data-hadith-results></div>
</section>
