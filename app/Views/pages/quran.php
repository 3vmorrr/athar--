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
$readingMode = in_array($_GET['mode'] ?? '', ['juz', 'page'], true) ? (string) $_GET['mode'] : 'surah';
$selectedJuz = max(1, min(30, filter_input(INPUT_GET, 'juz', FILTER_VALIDATE_INT) ?: 1));
$selectedPage = max(1, min(604, filter_input(INPUT_GET, 'page', FILTER_VALIDATE_INT) ?: 1));
?>
<section class="quran-page section-wrap" data-quran-reader data-surah="<?= $selectedSurah ?>" data-ayah="<?= $selectedAyah ?? '' ?>" data-mode="<?= e($readingMode) ?>" data-juz="<?= $selectedJuz ?>" data-page="<?= $selectedPage ?>" data-signed-in="<?= current_user() ? 'true' : 'false' ?>">
    <a class="back-link" href="/">العودة إلى الرئيسية</a>
    <div class="quran-heading">
        <div><span class="eyebrow"><span class="eyebrow-dot" aria-hidden="true"></span> المصحف وأدوات القراءة</span><h1>القرآن الكريم</h1><p>اقرأ بالسورة أو الجزء أو الصفحة، استمع باختيار القارئ، وتابع موضعك أو راجع حفظك.</p></div>
        <a class="source-link" href="https://alquran.cloud/" target="_blank" rel="noopener noreferrer">مصدر النص والتلاوة: Al Quran Cloud <span aria-hidden="true">↗</span></a>
    </div>

    <nav class="quran-tool-nav" aria-label="أدوات القرآن">
        <a href="#reader-tools">القراءة</a><a href="#audio-tools">الاستماع</a><a href="#search-tools">البحث</a><a href="#tafsir-heading">التفسير</a>
    </nav>

    <section id="reader-tools" class="quran-tool-section" aria-labelledby="reader-tools-heading">
        <div class="section-heading"><div><span class="eyebrow">المصحف</span><h2 id="reader-tools-heading">اختر موضع القراءة</h2></div></div>
        <form class="quran-controls" data-quran-form>
            <label for="quran-mode">طريقة التصفح<select id="quran-mode" name="mode" data-reading-mode><option value="surah"<?= $readingMode === 'surah' ? ' selected' : '' ?>>بالسورة</option><option value="juz"<?= $readingMode === 'juz' ? ' selected' : '' ?>>بالجزء</option><option value="page"<?= $readingMode === 'page' ? ' selected' : '' ?>>بالصفحة</option></select></label>
            <label for="quran-surah" data-mode-control="surah"<?= $readingMode !== 'surah' ? ' hidden' : '' ?>>السورة<select id="quran-surah" name="surah"><?php foreach ($surahs as $index => $surahName): $number = $index + 1; ?><option value="<?= $number ?>"<?= $selectedSurah === $number ? ' selected' : '' ?>><?= $number ?>. <?= e($surahName) ?></option><?php endforeach; ?></select></label>
            <label for="quran-ayah" data-mode-control="surah"<?= $readingMode !== 'surah' ? ' hidden' : '' ?>>ابدأ من الآية (اختياري)<input id="quran-ayah" name="ayah" type="number" min="1" inputmode="numeric" value="<?= $selectedAyah ?? '' ?>" placeholder="أول السورة"></label>
            <label for="quran-juz" data-mode-control="juz"<?= $readingMode !== 'juz' ? ' hidden' : '' ?>>الجزء<select id="quran-juz" name="juz"><?php for ($juz = 1; $juz <= 30; $juz++): ?><option value="<?= $juz ?>"<?= $selectedJuz === $juz ? ' selected' : '' ?>>الجزء <?= $juz ?></option><?php endfor; ?></select></label>
            <label for="quran-page" data-mode-control="page"<?= $readingMode !== 'page' ? ' hidden' : '' ?>>رقم الصفحة<input id="quran-page" name="page" type="number" min="1" max="604" inputmode="numeric" value="<?= $selectedPage ?>"></label>
            <button class="button button-primary" type="submit">عرض الموضع</button>
            <div class="reading-size" aria-label="حجم خط القراءة"><button class="icon-button" type="button" data-font-step="-1" aria-label="تصغير الخط">−</button><span>حجم الخط</span><button class="icon-button" type="button" data-font-step="1" aria-label="تكبير الخط">+</button></div>
        </form>
        <div class="quran-status" data-quran-status role="status" aria-live="polite" aria-atomic="true">جارٍ تحميل الآيات…</div>
        <article class="quran-reading" data-quran-content aria-label="نص القرآن" hidden></article>
        <p class="quran-attribution">النص بالرسم العثماني والتلاوة عبر <a href="https://alquran.cloud/" target="_blank" rel="noopener noreferrer">Al Quran Cloud</a>؛ تُعرض النصوص كما يرسلها المصدر.</p>
    </section>

    <section id="audio-tools" class="quran-tool-section" aria-labelledby="audio-heading">
        <div class="section-heading"><div><span class="eyebrow">استمع واختر القارئ</span><h2 id="audio-heading">مشغل التلاوة</h2></div></div>
        <div class="quran-audio" data-quran-audio>
            <div class="quran-audio-settings">
                <label for="quran-reciter">القارئ<select id="quran-reciter" data-quran-reciter><option value="ar.alafasy">مشاري راشد العفاسي</option></select></label>
                <label for="quran-audio-scope">نطاق الاستماع<select id="quran-audio-scope" data-audio-scope><option value="current">من الموضع المحدد</option><option value="surah">السورة حتى آخرها</option><option value="reader-list">الموضع المعروض كاملًا</option></select></label>
                <label class="audio-repeat-control"><input type="checkbox" data-audio-repeat> تكرار الآية الحالية</label>
            </div>
            <audio controls preload="none" aria-label="مشغل تلاوة القرآن"></audio>
            <div class="quran-audio-actions"><button class="button button-secondary" type="button" data-audio-previous>الآية السابقة</button><button class="button button-secondary" type="button" data-audio-next>الآية التالية</button></div>
            <p class="audio-status" data-audio-status role="status" aria-live="polite">اختر السورة والقارئ، ثم اضغط تشغيل.</p>
            <p class="audio-lock-note">عند دعم المتصفح، تظهر بيانات التلاوة وأزرار التحكم في شاشة القفل وسماعة الهاتف.</p>
        </div>
    </section>

    <section id="search-tools" class="quran-tool-section" aria-labelledby="search-tools-heading">
        <div class="section-heading"><div><span class="eyebrow">بحث بالآيات</span><h2 id="search-tools-heading">ابحث في القرآن</h2></div></div>
        <form class="knowledge-search quran-search" data-quran-search-form role="search"><label for="quran-search-query">كلمة أو عبارة من الآية</label><div class="search-row"><input id="quran-search-query" name="q" type="search" minlength="2" maxlength="60" value="<?= e(is_string($_GET['q'] ?? null) ? $_GET['q'] : '') ?>" placeholder="مثال: إن مع العسر يسرا"><button class="button button-secondary" type="submit">ابحث في القرآن</button></div></form>
        <div class="knowledge-status" data-quran-search-status role="status" aria-live="polite" hidden></div><div class="knowledge-results quran-search-results" data-quran-search-results></div>
    </section>

    <section class="tafsir-section quran-tool-section" data-quran-tafsir hidden aria-labelledby="tafsir-heading">
        <div class="section-heading"><div><span class="eyebrow">من مصدر التفسير</span><h2 id="tafsir-heading">تفسير الآية</h2></div><button class="button button-secondary" type="button" data-tafsir-toggle aria-expanded="false">عرض التفسير</button></div>
        <div class="tafsir-frame" data-tafsir-frame hidden></div><p class="quran-attribution">يعرض هذا القسم محتوى Quranpedia كما ورد من المصدر، مع بيانات الكتاب داخله.</p>
    </section>
</section>
