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
$tool = is_string($_GET['tool'] ?? null) && in_array($_GET['tool'], ['read', 'listen', 'tafsir', 'ayah', 'search', 'daily', 'review'], true) ? $_GET['tool'] : '';
$selectedSurah = filter_input(INPUT_GET, 'surah', FILTER_VALIDATE_INT) ?: 1;
$selectedSurah = max(1, min(114, $selectedSurah));
$ayahParam = filter_input(INPUT_GET, 'ayah', FILTER_VALIDATE_INT);
$selectedAyah = $ayahParam === false || $ayahParam === null ? null : max(1, $ayahParam);
$ayahEndParam = filter_input(INPUT_GET, 'ayah_end', FILTER_VALIDATE_INT);
$selectedEndAyah = $ayahEndParam === false || $ayahEndParam === null ? $selectedAyah : max(1, $ayahEndParam);
$readingMode = in_array($_GET['mode'] ?? '', ['juz', 'hizb', 'page'], true) ? (string) $_GET['mode'] : 'surah';
$selectedJuz = max(1, min(30, filter_input(INPUT_GET, 'juz', FILTER_VALIDATE_INT) ?: 1));
$selectedHizb = max(1, min(60, filter_input(INPUT_GET, 'hizb', FILTER_VALIDATE_INT) ?: 1));
$selectedPage = max(1, min(604, filter_input(INPUT_GET, 'page', FILTER_VALIDATE_INT) ?: 1));
$reviewMode = ($_GET['review'] ?? '') === '1';
$toolDetails = [
    'read' => ['القراءة', 'اقرأ بالسورة أو الجزء أو الصفحة، واضبط حجم الخط واحفظ موضعك.'],
    'listen' => ['الاستماع', 'اختر قارئًا، ثم استمع إلى آية واحدة أو إلى السورة كاملة.'],
    'tafsir' => ['التفسير', 'اختر السورة والآية لعرض تفسيرها من مصدره.'],
    'ayah' => ['آية محددة', 'افتح آية واحدة واقرأ نصها وتفسيرها مباشرة.'],
    'search' => ['البحث في القرآن', 'ابحث بكلمة أو عبارة عربية وافتح الآية المطابقة مباشرة.'],
    'daily' => ['آية اليوم', 'آية قرآنية تتغير يوميًا، مع انتقال مباشر إلى القراءة والاستماع والتفسير.'],
    'review' => ['مراجعة التلاوة', 'استمع إلى تلاوتك وراجع تنبيهات الكلمات المحتملة مباشرة على جهازك.'],
];
?>
<section class="quran-page section-wrap" data-quran-reader data-tool="<?= e($tool) ?>" data-surah="<?= $selectedSurah ?>" data-ayah="<?= $selectedAyah ?? '' ?>" data-ayah-end="<?= $selectedEndAyah ?? '' ?>" data-mode="<?= e(in_array($tool, ['listen', 'ayah', 'tafsir', 'review'], true) ? 'surah' : $readingMode) ?>" data-juz="<?= $selectedJuz ?>" data-hizb="<?= $selectedHizb ?>" data-page="<?= $selectedPage ?>" data-review="<?= $reviewMode ? 'true' : 'false' ?>" data-signed-in="<?= current_user() ? 'true' : 'false' ?>">
    <a class="back-link" href="/">العودة إلى الرئيسية</a>
    <?php if ($tool === ''): ?>
        <div class="quran-heading"><div><span class="eyebrow"><span class="eyebrow-dot" aria-hidden="true"></span> المصحف وأدوات القراءة</span><h1>القرآن الكريم</h1><p>اختر ما تريد فعله أولًا، وسنفتح لك مساحة مخصصة للقراءة أو الاستماع أو البحث.</p></div></div>
        <div class="quran-tool-grid" aria-label="اختر أداة القرآن">
            <a class="quran-tool-card" href="/quran?tool=read"><span class="quran-tool-icon" aria-hidden="true">۞</span><span class="eyebrow">المصحف</span><h2>القراءة</h2><p>تصفح القرآن بالسورة أو الجزء أو الصفحة، مع أدوات مريحة للقراءة.</p><span class="quran-card-action">ابدأ القراءة <span aria-hidden="true">←</span></span></a>
            <a class="quran-tool-card" href="/quran?tool=listen"><span class="quran-tool-icon" aria-hidden="true">♫</span><span class="eyebrow">التلاوة</span><h2>الاستماع</h2><p>اختر قارئًا من مكتبة واسعة، وشغّل آية محددة أو سورة كاملة.</p><span class="quran-card-action">اختر قارئًا <span aria-hidden="true">←</span></span></a>
            <a class="quran-tool-card" href="/quran?tool=tafsir"><span class="quran-tool-icon" aria-hidden="true">۝</span><span class="eyebrow">تدبر</span><h2>التفسير</h2><p>انتقل مباشرة إلى تفسير الآية التي تختارها.</p><span class="quran-card-action">افتح التفسير <span aria-hidden="true">←</span></span></a>
            <a class="quran-tool-card" href="/quran?tool=ayah"><span class="quran-tool-icon" aria-hidden="true">﴿ ﴾</span><span class="eyebrow">موضع بعينه</span><h2>آية محددة</h2><p>افتح آية واحدة واقرأ نصها أو اعرض تفسيرها مباشرة.</p><span class="quran-card-action">اختر آية <span aria-hidden="true">←</span></span></a>
            <a class="quran-tool-card" href="/quran?tool=search"><span class="quran-tool-icon" aria-hidden="true">⌕</span><span class="eyebrow">الوصول السريع</span><h2>البحث في القرآن</h2><p>اكتب كلمة أو عبارة عربية للوصول إلى مواضعها في القرآن.</p><span class="quran-card-action">ابحث الآن <span aria-hidden="true">←</span></span></a>
            <a class="quran-tool-card" href="/quran?tool=daily"><span class="quran-tool-icon" aria-hidden="true">۞</span><span class="eyebrow">ورد يومي</span><h2>آية اليوم</h2><p>اقرأ آية مختارة لهذا اليوم وانتقل منها للتلاوة أو التفسير.</p><span class="quran-card-action">اقرأ آية اليوم <span aria-hidden="true">←</span></span></a>
            <a class="quran-tool-card" href="/quran?tool=read&amp;review=1"><span class="quran-tool-icon" aria-hidden="true">◉</span><span class="eyebrow">تثبيت الحفظ</span><h2>مراجعة الحفظ</h2><p>اخفِ نص الآيات واكشفها واحدةً واحدةً أثناء المراجعة.</p><span class="quran-card-action">ابدأ المراجعة <span aria-hidden="true">←</span></span></a>
            <a class="quran-tool-card" href="/quran?tool=review"><span class="quran-tool-icon" aria-hidden="true"><svg viewBox="0 0 24 24"><rect x="9" y="2" width="6" height="12" rx="3"/><path d="M5 11a7 7 0 0 0 14 0M12 18v4m-4 0h8"/></svg></span><span class="eyebrow">استماع محلي</span><h2>مراجعة التلاوة</h2><p>راجع موضعًا تختاره مع تنبيهات صوتية وبصرية للكلمات المحتمل الخطأ فيها.</p><span class="quran-card-action">ابدأ مراجعة التلاوة <span aria-hidden="true">←</span></span></a>
        </div>
    <?php else: ?>
        <div class="quran-heading"><div><span class="eyebrow">أدوات القرآن <span aria-hidden="true">/</span> <?= e($toolDetails[$tool][0]) ?></span><h1><?= e($toolDetails[$tool][0]) ?></h1><p><?= e($toolDetails[$tool][1]) ?></p></div><a class="quran-home-link" href="/quran">كل أدوات القرآن</a></div>
        <?php $activeQuranNav = in_array($tool, ['daily', 'ayah'], true) ? 'read' : $tool; ?>
        <nav class="quran-tool-nav" aria-label="التنقل بين أدوات القرآن">
            <?php foreach (['read' => 'القراءة', 'listen' => 'الاستماع', 'tafsir' => 'التفسير', 'search' => 'البحث في القرآن', 'review' => 'مراجعة التلاوة'] as $navTool => $navLabel): ?>
                <a href="/quran?tool=<?= e($navTool) ?>"<?= $activeQuranNav === $navTool ? ' aria-current="page"' : '' ?>><?= e($navLabel) ?></a>
            <?php endforeach; ?>
        </nav>
        <?php if ($tool === 'search'): ?>
            <form class="knowledge-search quran-search" data-quran-search-form role="search"><label for="quran-search-query">كلمة أو عبارة من الآية</label><div class="search-row"><input id="quran-search-query" name="q" type="search" minlength="2" maxlength="60" value="<?= e(is_string($_GET['q'] ?? null) ? $_GET['q'] : '') ?>" placeholder="مثال: إن مع العسر يسرا"><button class="button button-secondary" type="submit">ابحث في القرآن</button></div></form>
            <div class="knowledge-status" data-quran-search-status role="status" aria-live="polite" hidden></div><div class="knowledge-results quran-search-results" data-quran-search-results></div>
        <?php else: ?>
            <?php $fixedMode = in_array($tool, ['ayah', 'tafsir', 'listen', 'daily', 'review'], true); $activeMode = $fixedMode ? 'surah' : $readingMode; ?>
            <form class="quran-controls<?= $tool === 'review' ? ' quran-controls-review' : '' ?>" data-quran-form<?= $tool === 'daily' ? ' hidden' : '' ?>>
                <label for="quran-mode"<?= $fixedMode ? ' hidden' : '' ?>>طريقة التصفح<select id="quran-mode" name="mode" data-reading-mode><option value="surah"<?= $activeMode === 'surah' ? ' selected' : '' ?>>بالسورة</option><option value="juz"<?= $activeMode === 'juz' ? ' selected' : '' ?>>بالجزء</option><option value="hizb"<?= $activeMode === 'hizb' ? ' selected' : '' ?>>بالحزب</option><option value="page"<?= $activeMode === 'page' ? ' selected' : '' ?>>بالصفحة</option></select></label>
                <label for="quran-surah" data-mode-control="surah"<?= $activeMode !== 'surah' ? ' hidden' : '' ?>>السورة<select id="quran-surah" name="surah"><?php foreach ($surahs as $index => $surahName): $number = $index + 1; ?><option value="<?= $number ?>"<?= $selectedSurah === $number ? ' selected' : '' ?>><?= $number ?>. <?= e($surahName) ?></option><?php endforeach; ?></select></label>
                <label for="quran-ayah" data-mode-control="surah" data-ayah-control><?= $tool === 'review' ? 'ابدأ من الآية' : (in_array($tool, ['listen', 'ayah', 'tafsir'], true) ? 'رقم الآية' : 'ابدأ من الآية (اختياري)') ?><input id="quran-ayah" name="ayah" type="number" min="1" inputmode="numeric" value="<?= $selectedAyah ?? '' ?>" placeholder="<?= $tool === 'read' ? 'أول السورة' : 'مثال: 1' ?>"<?= in_array($tool, ['ayah', 'tafsir', 'review'], true) ? ' required' : '' ?>></label>
                <?php if ($tool === 'review'): ?><label for="quran-ayah-end" data-mode-control="surah">انتهِ عند الآية<input id="quran-ayah-end" name="ayah_end" type="number" min="1" inputmode="numeric" value="<?= $selectedEndAyah ?? '' ?>" placeholder="نفس الآية" required></label><?php endif; ?>
                <label for="quran-juz" data-mode-control="juz"<?= $activeMode !== 'juz' ? ' hidden' : '' ?>>الجزء<select id="quran-juz" name="juz"><?php for ($juz = 1; $juz <= 30; $juz++): ?><option value="<?= $juz ?>"<?= $selectedJuz === $juz ? ' selected' : '' ?>>الجزء <?= $juz ?></option><?php endfor; ?></select></label>
                <label for="quran-hizb" data-mode-control="hizb"<?= $activeMode !== 'hizb' ? ' hidden' : '' ?>>الحزب<select id="quran-hizb" name="hizb"><?php for ($hizb = 1; $hizb <= 60; $hizb++): ?><option value="<?= $hizb ?>"<?= $selectedHizb === $hizb ? ' selected' : '' ?>>الحزب <?= $hizb ?></option><?php endfor; ?></select></label>
                <label for="quran-page" data-mode-control="page"<?= $activeMode !== 'page' ? ' hidden' : '' ?>>رقم الصفحة<input id="quran-page" name="page" type="number" min="1" max="604" inputmode="numeric" value="<?= $selectedPage ?>"></label>
                <button class="button button-primary" type="submit"><?= $tool === 'listen' ? 'تجهيز الاستماع' : ($tool === 'tafsir' ? 'عرض الآية والتفسير' : ($tool === 'ayah' ? 'افتح الآية' : ($tool === 'review' ? 'تجهيز النطاق' : 'عرض الموضع'))) ?></button>
                <?php if ($tool === 'read' || $tool === 'ayah'): ?><div class="reading-size" aria-label="حجم خط القراءة"><button class="icon-button" type="button" data-font-step="-1" aria-label="تصغير الخط">−</button><span>حجم الخط</span><button class="icon-button" type="button" data-font-step="1" aria-label="تكبير الخط">+</button></div><?php endif; ?>
            </form>
            <div class="quran-status" data-quran-status role="status" aria-live="polite" aria-atomic="true">جارٍ تحميل الآيات…</div>
            <article class="quran-reading" data-quran-content aria-label="نص القرآن" hidden></article>
            <?php if ($tool === 'review'): ?>
                <section class="recitation-review quran-tool-section" data-recitation-review hidden aria-labelledby="recitation-review-heading">
                    <div class="section-heading"><div><span class="eyebrow">مراجعة خاصة على جهازك</span><h2 id="recitation-review-heading">راجع تلاوتك كلمةً كلمة</h2></div><span class="review-local-badge"><span aria-hidden="true"></span> الصوت لا يغادر جهازك</span></div>
                    <p class="recitation-review-intro">اختر السورة والآيات ثم ابدأ التلاوة. تبقى مساحة المتابعة فارغة حتى تبدأ، ثم تكشف الكلمات بالتدريج مع تقدّمك. يعمل النموذج محليًا؛ يلزم اتصال لتنزيله أول مرة (نحو 75 ميجابايت)، واترك الصفحة مفتوحة أثناء المراجعة.</p>
                    <div class="review-live-readout">
                        <div class="review-readout-heading"><span class="eyebrow">متابعة التلاوة</span><span class="review-listening-indicator" data-review-listening><i></i><i></i><i></i><i></i><i></i><span data-review-listening-label>جاهز للاستماع</span></span></div>
                        <div class="review-mushaf-page" lang="ar" dir="rtl">
                            <header class="review-mushaf-page-header"><span class="review-mushaf-ornament" aria-hidden="true">۞</span><div><strong data-review-surah>مصحف المراجعة</strong><small data-review-range>اختر نطاق الآيات للبدء</small></div><span class="review-mushaf-ornament" aria-hidden="true">۞</span></header>
                            <div class="review-mushaf-lines" data-review-lines aria-live="off"></div>
                            <p class="review-empty-state" data-review-empty>مواضع الآيات جاهزة؛ ستظهر الكلمات هنا مع تقدمك في التلاوة.</p>
                            <footer class="review-mushaf-page-footer"><span>صفحة المراجعة</span><span data-review-page-number>١ / ١</span></footer>
                        </div>
                        <div class="review-progress-meta"><span>تقدم النطاق</span><span data-review-percent>٠٪</span></div><div class="review-live-progress" role="progressbar" aria-label="تقدم مراجعة التلاوة" aria-valuemin="0" aria-valuemax="100" aria-valuenow="0"><span data-review-progress></span></div>
                    </div>
                    <div class="recitation-review-controls"><button class="button button-primary" type="button" data-review-start disabled>ابدأ المراجعة</button><button class="button button-secondary" type="button" data-review-stop disabled>إنهاء المراجعة</button><label class="review-beep-option"><input type="checkbox" data-review-beep checked> تنبيه صوتي عند رصد خطأ محتمل</label><label class="review-vibrate-option"><input type="checkbox" data-review-vibrate checked> اهتزاز عند رصد خطأ محتمل</label></div>
                    <p class="recitation-review-status" data-review-status role="status" aria-live="polite" aria-atomic="true">حدد السورة ونطاق الآيات أولًا.</p>
                    <p class="recitation-review-result" data-review-result role="status" aria-live="polite" hidden></p>
                    <div class="recitation-review-actions" data-review-actions hidden><button class="button button-secondary" type="button" data-review-action="retry">أعد الآية</button><button class="button button-primary" type="button" data-review-action="continue">تابع</button><button class="button button-secondary" type="button" data-review-action="dismiss">تجاهل التنبيه</button></div>
                    <p class="recitation-review-credit">محرك المراجعة من <a href="https://github.com/yazinsai/tilawa" target="_blank" rel="noopener noreferrer">Tilawa</a>، والنموذج وبياناته مرخصة بترخيص <a href="https://github.com/yazinsai/tilawa/blob/main/licenses/NPL-1.2.txt" target="_blank" rel="noopener noreferrer">NPL-1.2 غير التجاري</a>. تظل الميزة مجانية ما دام هذا النموذج مستخدمًا؛ يلزم استبداله أو الحصول على ترخيص قبل أي استخدام تجاري. المراجعة استرشادية ولا تحكم على التجويد.</p>
                </section>
            <?php endif; ?>
            <?php if ($tool === 'daily'): ?><div class="daily-verse-actions" data-daily-verse-actions></div><?php endif; ?>
            <p class="quran-attribution" data-quran-attribution><?php if ($tool === 'listen'): ?>السور الكاملة عبر <a href="https://quranicaudio.com/" target="_blank" rel="noopener noreferrer">QuranicAudio</a>، والآيات المنفردة عبر <a href="https://alquran.cloud/" target="_blank" rel="noopener noreferrer">Al Quran Cloud</a>.<?php else: ?>نص القرآن عبر <a href="https://alquran.cloud/" target="_blank" rel="noopener noreferrer">Al Quran Cloud</a>.<?php endif; ?></p>
            <?php if ($tool === 'listen'): ?>
                <section class="quran-tool-section" aria-labelledby="audio-heading"><div class="section-heading"><div><span class="eyebrow">مكتبة القراء</span><h2 id="audio-heading">مشغل التلاوة</h2></div></div>
                    <div class="quran-audio" data-quran-audio hidden>
                        <div class="audio-scope-choice" role="group" aria-label="اختر نطاق الاستماع"><span>ماذا تريد أن تستمع؟</span><label><input type="radio" name="audio-scope" value="current" data-audio-scope checked><span><strong>آية واحدة</strong><small>ابدأ من رقم الآية المحددة فقط</small></span></label><label><input type="radio" name="audio-scope" value="surah" data-audio-scope><span><strong>السورة كاملة</strong><small>تشغيل كامل السورة من بدايتها</small></span></label></div>
                        <div class="quran-audio-settings"><label for="quran-reciter">القارئ<select id="quran-reciter" data-quran-reciter><option value="ar.alafasy">مشاري راشد العفاسي</option></select></label><label class="audio-repeat-control"><input type="checkbox" data-audio-repeat> تكرار التلاوة</label></div><p class="audio-provider-note" data-audio-provider-note>اختيار السورة الكاملة يفتح مكتبة QuranicAudio الأوسع؛ والآية المنفردة تستخدم قراء Al Quran Cloud.</p>
                        <audio controls preload="none" aria-label="مشغل تلاوة القرآن"></audio><div class="quran-audio-actions"><button class="button button-secondary" type="button" data-audio-previous>الآية السابقة</button><button class="button button-secondary" type="button" data-audio-next>الآية التالية</button></div>
                        <p class="audio-status" data-audio-status role="status" aria-live="polite">اختر السورة والقارئ، ثم اضغط تشغيل.</p><p class="audio-lock-note">عند دعم المتصفح، تظهر بيانات التلاوة وأزرار التحكم في شاشة القفل وسماعة الهاتف.</p>
                    </div>
                </section>
            <?php endif; ?>
            <?php if (in_array($tool, ['tafsir', 'ayah'], true)): ?><section class="tafsir-section quran-tool-section" data-quran-tafsir hidden aria-labelledby="tafsir-heading"><div class="section-heading"><div><span class="eyebrow">من مصدر التفسير</span><h2 id="tafsir-heading">تفسير الآية</h2></div><button class="button button-secondary" type="button" data-tafsir-toggle aria-expanded="false">عرض التفسير</button></div><div class="tafsir-frame" data-tafsir-frame hidden></div><p class="quran-attribution">يعرض هذا القسم محتوى Quranpedia كما ورد من المصدر، مع بيانات الكتاب داخله.</p></section><?php endif; ?>
        <?php endif; ?>
    <?php endif; ?>
</section>
