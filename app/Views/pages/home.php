<section class="hero section-wrap">
    <div class="hero-copy">
        <span class="eyebrow"><span class="eyebrow-dot" aria-hidden="true"></span> مساحةٌ للعلم والعبادة</span>
        <h1>معرفةٌ تنفع،<br><span>وأثرٌ يبقى.</span></h1>
        <p class="hero-lede">اقرأ القرآن، وابحث في مصادر إسلامية متاحة، وتابع ما ينفعك — في تجربة عربية هادئة ومجانية.</p>
        <div class="hero-actions">
            <a class="button button-primary" href="/quran">
                ابدأ بالقرآن
                <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M19 12H5m0 0 6 6m-6-6 6-6" /></svg>
            </a>
            <a class="button button-secondary" href="/lessons">استكشف الدروس</a>
        </div>
        <p class="guest-note">
            <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 3 5 6v5c0 4.7 2.9 8.1 7 10 4.1-1.9 7-5.3 7-10V6l-7-3Z"/><path d="m9 12 2 2 4-4"/></svg>
            تصفّح واقرأ دون الحاجة إلى إنشاء حساب
        </p>
    </div>

    <div class="hero-art" aria-hidden="true">
        <div class="art-halo"></div>
        <div class="art-card art-card-back"><span>اقرأ</span><i></i><i></i><i></i></div>
        <div class="art-card art-card-front">
            <span class="art-caption">تجربة القراءة</span>
            <span class="art-ayah">قراءةٌ واضحة<br>ومصدرٌ ظاهر</span>
            <span class="art-reference">المصدر ظاهر مع النص</span>
        </div>
        <span class="art-leaf art-leaf-one"></span>
        <span class="art-leaf art-leaf-two"></span>
    </div>
</section>

<section class="areas-section section-wrap" aria-labelledby="areas-heading">
    <div class="section-heading">
        <div>
            <span class="eyebrow">مساراتك في أثر</span>
            <h2 id="areas-heading">اختر ما تبحث عنه</h2>
        </div>
        <p>مجالاتٌ متكاملة، ومصادرها واضحة.</p>
    </div>

    <div class="area-grid">
        <a class="area-card area-card-featured" href="/quran">
            <span class="area-icon" aria-hidden="true"><svg viewBox="0 0 24 24"><path d="M4 5.5A2.5 2.5 0 0 1 6.5 3H20v16H6.5A2.5 2.5 0 0 0 4 21V5.5Z"/><path d="M4 6v15m4-14h8m-8 4h8m-8 4h5"/></svg></span>
            <span class="area-title">القرآن الكريم</span>
            <span class="area-description">قراءة مباشرة مع اختيار السورة والآية وتغيير حجم الخط.</span>
            <span class="area-status">متاح</span>
        </a>
        <a class="area-card" href="/hadith">
            <span class="area-icon" aria-hidden="true"><svg viewBox="0 0 24 24"><path d="M5 5.5A2.5 2.5 0 0 1 7.5 3H20v15H7.5A2.5 2.5 0 0 0 5 20.5v-15Z"/><path d="M5 6v14m4-13h7m-7 4h7m-7 4h4"/></svg></span>
            <span class="area-title">الحديث</span>
            <span class="area-description">بحثٌ مع توضيح المصدر والمرجع والدرجة عند ورودها.</span>
            <span class="area-status">متاح</span>
        </a>
        <a class="area-card" href="/books">
            <span class="area-icon" aria-hidden="true"><svg viewBox="0 0 24 24"><path d="M4 5.5A2.5 2.5 0 0 1 6.5 3H20v16H6.5A2.5 2.5 0 0 0 4 21V5.5Z"/><path d="M4 6v15m5-14h7m-7 4h7"/></svg></span>
            <span class="area-title">المكتبة</span>
            <span class="area-description">بحث في فهرس كتب التفسير وعلوم القرآن.</span>
            <span class="area-status">متاح</span>
        </a>
        <a class="area-card" href="/lessons">
            <span class="area-icon" aria-hidden="true"><svg viewBox="0 0 24 24"><rect x="3" y="5" width="18" height="14" rx="3"/><path d="m10 9 5 3-5 3V9Z"/></svg></span>
            <span class="area-title">الدروس والسلاسل</span>
            <span class="area-description">شغّل درسًا عبر المشغل الرسمي بإدخال رابطه.</span>
            <span class="area-status">متاح</span>
        </a>
        <a class="area-card" href="/adhkar">
            <span class="area-icon" aria-hidden="true"><svg viewBox="0 0 24 24"><path d="M12 3v18M5 7h14M5 17h14M7 3h10M7 21h10"/><circle cx="12" cy="12" r="2.5"/></svg></span>
            <span class="area-title">الأذكار</span>
            <span class="area-description">أذكارٌ مصنفة بنصوصها ومراجعها.</span>
            <span class="area-status">متاح</span>
        </a>
    </div>
</section>

<section class="home-recent section-wrap" aria-labelledby="home-recent-heading">
    <div class="section-heading"><div><span class="eyebrow">محتوى الموقع</span><h2 id="home-recent-heading">ابدأ من هنا</h2></div></div>
    <div class="home-recent-grid">
        <article class="knowledge-card home-feature-card"><h3>الدروس والسلاسل</h3><?php if($homeLessons): ?><ul class="home-link-list"><?php foreach($homeLessons as $lesson): ?><li><a href="<?= $lesson['series_id']?'/series?id='.(int)$lesson['series_id'].'&lesson='.(int)$lesson['id']:'/lessons?lesson='.(int)$lesson['id'] ?>"><?= e($lesson['title']) ?></a><small><?= e($lesson['series_title']?:'درس مستقل') ?></small></li><?php endforeach; ?></ul><?php else: ?><p>استكشف الدروس المنشورة في مكتبة العلماء.</p><?php endif; ?><a class="button button-secondary" href="/lessons">كل الدروس</a></article>
        <article class="knowledge-card home-feature-card"><h3>السلاسل العلمية</h3><?php if($homeSeries): ?><ul class="home-link-list"><?php foreach($homeSeries as $series): ?><li><a href="/series?id=<?= (int)$series['id'] ?>"><?= e($series['title']) ?></a></li><?php endforeach; ?></ul><?php else: ?><p>تظهر هنا السلاسل التي يضيفها فريق المحتوى.</p><?php endif; ?><a class="button button-secondary" href="/lessons">تصفح السلاسل</a></article>
        <article class="knowledge-card home-feature-card"><h3>الكتب المتاحة</h3><?php if($homeBooks): ?><ul class="home-link-list"><?php foreach($homeBooks as $book): ?><li><a href="/books"><?= e($book['title']) ?></a><small><?= e($book['author']) ?></small></li><?php endforeach; ?></ul><?php else: ?><p>تصفح مكتبة الكتب العامة.</p><?php endif; ?><a class="button button-secondary" href="/books">فتح المكتبة</a></article>
    </div>
</section>

<section class="trust-section">
    <div class="trust-inner section-wrap">
        <span class="trust-mark" aria-hidden="true"><svg viewBox="0 0 24 24"><path d="M12 3 5 6v5c0 4.7 2.9 8.1 7 10 4.1-1.9 7-5.3 7-10V6l-7-3Z"/><path d="m9 12 2 2 4-4"/></svg></span>
        <div>
            <h2>المصدر جزءٌ من الأمانة</h2>
            <p>نعرض النصوص مع مراجعها ومعلومات مصادرها. أثر منصةٌ للمعرفة والتنظيم، وليس جهةً لإصدار الفتاوى.</p>
        </div>
        <a href="/about">عن منهجية المصادر <span aria-hidden="true">←</span></a>
    </div>
</section>
