<?php
$surahs = [
    'الفاتحة', 'البقرة', 'آل عمران', 'النساء', 'المائدة', 'الأنعام', 'الأعراف', 'الأنفال', 'التوبة', 'يونس',
    'هود', 'يوسف', 'الرعد', 'إبراهيم', 'الحجر', 'النحل', 'الإسراء', 'الكهف', 'مريم', 'طه',
    'الأنبياء', 'الحج', 'المؤمنون', 'النور', 'الفرقان', 'الشعراء', 'النمل', 'القصص', 'العنكبوت', 'الروم',
    'لقمان', 'السجدة', 'الأحزاب', 'سبأ', 'فاطر', 'يس', 'الصافات', 'ص', 'الزمر', 'غافر',
    'فصلت', 'الشورى', 'الزخرف', 'الدخان', 'الجاثية', 'الأحقاف', 'محمد', 'الفتح', 'الحجرات', 'ق',
    'الذاريات', 'الطور', 'النجم', 'القمر', 'الرحمن', 'الواقعة', 'الحديد', 'المجادلة', 'الحشر', 'الممتحنة',
    'الصف', 'الجمعة', 'المنافقون', 'التغابن', 'الطلاق', 'التحريم', 'الملك', 'القلم', 'الحاقة', 'المعارج',
    'نوح', 'الجن', 'المزمل', 'المدثر', 'القيامة', 'الإنسان', 'المرسلات', 'النبأ', 'النازعات', 'عبس',
    'التكوير', 'الانفطار', 'المطففين', 'الانشقاق', 'البروج', 'الطارق', 'الأعلى', 'الغاشية', 'الفجر', 'البلد',
    'الشمس', 'الليل', 'الضحى', 'الشرح', 'التين', 'العلق', 'القدر', 'البينة', 'الزلزلة', 'العاديات',
    'القارعة', 'التكاثر', 'العصر', 'الهمزة', 'الفيل', 'قريش', 'الماعون', 'الكوثر', 'الكافرون', 'النصر',
    'المسد', 'الإخلاص', 'الفلق', 'الناس',
];
$selectedSurah = filter_input(INPUT_GET, 'surah', FILTER_VALIDATE_INT) ?: 1;
$selectedSurah = max(1, min(114, $selectedSurah));
$ayahParam = filter_input(INPUT_GET, 'ayah', FILTER_VALIDATE_INT);
$selectedAyah = $ayahParam === false || $ayahParam === null ? null : max(1, $ayahParam);
?>
<section class="quran-page section-wrap" data-quran-reader data-surah="<?= $selectedSurah ?>" data-ayah="<?= $selectedAyah ?? '' ?>" data-signed-in="<?= current_user() ? 'true' : 'false' ?>">
    <a class="back-link" href="/">العودة إلى الرئيسية</a>
    <div class="quran-heading">
        <div>
            <span class="eyebrow"><span class="eyebrow-dot" aria-hidden="true"></span> قراءة مباشرة من مصدر موثوق</span>
            <h1>القرآن الكريم</h1>
        <p>اختر السورة لعرض آياتها أو ابحث في النص العربي. النص والصوت يُجلبان مباشرة من المصدرين.</p>
        </div>
        <a class="source-link" href="https://quranpedia.net" target="_blank" rel="noopener noreferrer">المصدر: موسوعة القرآن الكريم <span aria-hidden="true">↗</span></a>
    </div>

    <form class="quran-controls" data-quran-form>
        <label for="quran-surah">السورة
            <select id="quran-surah" name="surah">
                <?php foreach ($surahs as $index => $surahName): $number = $index + 1; ?>
                    <option value="<?= $number ?>"<?= $selectedSurah === $number ? ' selected' : '' ?>><?= $number ?>. <?= e($surahName) ?></option>
                <?php endforeach; ?>
            </select>
        </label>
        <label for="quran-ayah">رقم الآية (اختياري)
            <input id="quran-ayah" name="ayah" type="number" min="1" inputmode="numeric" value="<?= $selectedAyah ?? '' ?>" placeholder="كل آيات السورة">
        </label>
        <button class="button button-primary" type="submit">عرض الآيات</button>
        <div class="reading-size" aria-label="حجم خط القراءة">
            <button class="icon-button" type="button" data-font-step="-1" aria-label="تصغير الخط">−</button>
            <span>حجم الخط</span>
            <button class="icon-button" type="button" data-font-step="1" aria-label="تكبير الخط">+</button>
        </div>
    </form>

    <form class="knowledge-search quran-search" data-quran-search-form role="search">
        <label for="quran-search-query">ابحث في النص القرآني</label>
        <div class="search-row">
            <input id="quran-search-query" name="q" type="search" minlength="2" maxlength="60" value="<?= e(is_string($_GET['q'] ?? null) ? $_GET['q'] : '') ?>" placeholder="كلمة من آية">
            <button class="button button-secondary" type="submit">ابحث في القرآن</button>
        </div>
    </form>
    <div class="knowledge-status" data-quran-search-status role="status" aria-live="polite" hidden></div>
    <div class="knowledge-results quran-search-results" data-quran-search-results></div>

    <div class="quran-status" data-quran-status role="status" aria-live="polite" aria-atomic="true">جارٍ تحميل الآيات…</div>
    <div class="quran-audio" data-quran-audio hidden>
        <div><strong>تلاوة مشاري العفاسي</strong><span>الصوت من Al Quran Cloud</span></div>
        <audio controls preload="none" aria-label="تلاوة السورة أو الآية المحددة"></audio>
        <a href="https://alquran.cloud/" target="_blank" rel="noopener noreferrer">المصدر ↗</a>
        <span class="audio-status" data-audio-status role="status" aria-live="polite"></span>
    </div>
    <article class="quran-reading" data-quran-content aria-label="نص السورة" hidden></article>
    <section class="tafsir-section" data-quran-tafsir hidden aria-labelledby="tafsir-heading">
        <div class="section-heading">
            <div><span class="eyebrow">من مصدر التفسير</span><h2 id="tafsir-heading">تفسير الآية المحددة</h2></div>
            <button class="button button-secondary" type="button" data-tafsir-toggle aria-expanded="false">عرض التفسير</button>
        </div>
        <div class="tafsir-frame" data-tafsir-frame hidden></div>
        <p class="quran-attribution">يعرض هذا القسم محتوى Quranpedia كما ورد من المصدر، مع بيانات الكتاب داخله.</p>
    </section>
    <p class="quran-attribution">النص من <a href="https://quranpedia.net" target="_blank" rel="noopener noreferrer">Quranpedia</a>، والتلاوة من <a href="https://alquran.cloud/" target="_blank" rel="noopener noreferrer">Al Quran Cloud</a>. إذا تعذر الاتصال، حاول مرة أخرى لاحقًا. <?php if (!current_user()): ?><a href="/login">سجّل الدخول لحفظ موضع القراءة والآيات.</a><?php endif; ?></p>
</section>
