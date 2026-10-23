<section class="knowledge-page section-wrap" data-dorar-page>
    <a class="back-link" href="/">العودة إلى الرئيسية</a>
    <header class="knowledge-heading">
        <span class="eyebrow"><span class="eyebrow-dot" aria-hidden="true"></span> بحث موحّد بالمصادر</span>
        <h1>موسوعة الدرر السنية</h1>
        <p>ابحث بالعربية في الحديث والتفسير والعقيدة والفقه والأخلاق والآداب والتاريخ. كل نتيجة تعرض رابط مصدرها.</p>
    </header>
    <form class="knowledge-search" data-dorar-form role="search">
        <label for="dorar-query">كلمة أو عبارة للبحث</label>
        <div class="dorar-search-fields">
            <input id="dorar-query" name="q" type="search" minlength="2" maxlength="200" required value="<?= e(is_string($_GET['q'] ?? null) ? $_GET['q'] : '') ?>" placeholder="مثال: بر الوالدين">
            <label for="dorar-scope">الموسوعة<select id="dorar-scope" name="scope" data-dorar-scope>
                <option value="hadith">الحديث النبوي</option><option value="tafseer">التفسير</option><option value="aqeeda">العقيدة</option><option value="feqhia">الفقه</option><option value="alakhlaq">الأخلاق</option><option value="aadab">الآداب</option><option value="history">التاريخ</option>
            </select></label>
            <button class="button button-primary" type="submit">ابحث في الدرر</button>
        </div>
    </form>
    <p class="source-note">المصدر: <a href="https://dorar.net/api-services" target="_blank" rel="noopener noreferrer">خدمات واجهة الدرر السنية ↗</a>. تظهر النتيجة مع نسبتها ورابطها الأصلي.</p>
    <div class="knowledge-status" data-dorar-status role="status" aria-live="polite" hidden></div>
    <div class="knowledge-results" data-dorar-results></div>
</section>
