<?php
$selectedLessonId = filter_var($_GET['lesson'] ?? null, FILTER_VALIDATE_INT) ?: 0;
$selectedLesson = null;
foreach ($curatedLessons as $curatedLesson) {
    if ((int) $curatedLesson['id'] === $selectedLessonId) { $selectedLesson = $curatedLesson; break; }
}
$videoParam = $selectedLesson ? (string) $selectedLesson['source_url'] : (is_string($_GET['video'] ?? null) ? trim($_GET['video']) : '');
?>
<section class="knowledge-page section-wrap" data-lessons-page data-video="<?= e($videoParam) ?>" data-lesson-id="<?= (int) ($selectedLesson['id'] ?? 0) ?>" data-resume-seconds="<?= (int) ($selectedLesson['position_seconds'] ?? 0) ?>">
    <a class="back-link" href="/">العودة إلى الرئيسية</a>
    <header class="knowledge-heading">
        <span class="eyebrow"><span class="eyebrow-dot" aria-hidden="true"></span> مكتبة الدروس</span>
        <h1>الدروس والسلاسل</h1>
        <p>ابدأ من صفحة الشيخ، تابع السلاسل ودروسها بالترتيب، أو شاهد درسًا مستقلًا.</p>
    </header>

    <?php if ($curatedScholars): ?>
        <section class="curated-section" aria-labelledby="scholars-heading">
            <div class="section-heading"><div><span class="eyebrow">تصفح حسب الشيخ</span><h2 id="scholars-heading">العلماء</h2></div></div>
            <div class="knowledge-results">
                <?php foreach ($curatedScholars as $scholar): ?>
                    <article class="knowledge-card scholar-card"><?php if (!empty($scholar['image_key'])): ?><img class="scholar-portrait" src="/scholar-image?key=<?= rawurlencode((string) $scholar['image_key']) ?>" alt="<?= e((string) $scholar['name']) ?>" loading="lazy" width="160" height="160"><?php endif; ?><h3><a href="/scholar?id=<?= (int) $scholar['id'] ?>"><?= e($scholar['name']) ?></a></h3>
                        <a class="button button-secondary" href="/scholar?id=<?= (int) $scholar['id'] ?>">السلاسل والدروس</a>
                    </article>
                <?php endforeach; ?>
            </div>
        </section>
    <?php endif; ?>

    <section class="curated-section" aria-labelledby="series-heading">
        <div class="section-heading"><div><span class="eyebrow">مرتبة حسب الشيخ</span><h2 id="series-heading">السلاسل العلمية</h2></div></div>
        <?php if (!$curatedSeries): ?><p class="source-note">لا توجد سلاسل منشورة حاليًا.</p>
        <?php else: ?><div class="knowledge-results">
            <?php foreach ($curatedSeries as $series): ?>
                <article class="knowledge-card">
                    <?php if ($series['scholar_name']): ?><p class="book-details">للشيخ <?= e($series['scholar_name']) ?></p><?php endif; ?>
                    <h3><a href="/series?id=<?= (int) $series['id'] ?>"><?= e($series['title']) ?></a></h3>
                    <?php if ($series['description']): ?><p><?= e($series['description']) ?></p><?php endif; ?>
                    <span class="source-chip"><?= (int) $series['lesson_count'] ?> درس منشور</span>
                </article>
            <?php endforeach; ?>
        </div><?php endif; ?>
    </section>

    <section class="curated-section" aria-labelledby="independent-lessons-heading">
        <div class="section-heading"><div><span class="eyebrow">فيديوهات خارج السلاسل</span><h2 id="independent-lessons-heading">دروس مستقلة</h2></div></div>
        <?php if (!$curatedLessons): ?><p class="source-note">لا توجد دروس مستقلة منشورة حاليًا.</p>
        <?php else: ?><div class="knowledge-results">
            <?php foreach ($curatedLessons as $lesson): ?>
                <article class="knowledge-card lesson-card">
                    <?php if ($lesson['scholar_name']): ?><a class="source-chip" href="/scholar?id=<?= (int) $lesson['scholar_id'] ?>"><?= e($lesson['scholar_name']) ?></a><?php else: ?><span class="source-chip">درس مستقل</span><?php endif; ?>
                    <h3><?= e($lesson['title']) ?></h3>
                    <?php if ($lesson['description']): ?><p><?= e($lesson['description']) ?></p><?php endif; ?>
                    <a class="button button-secondary" href="/lessons?lesson=<?= (int) $lesson['id'] ?>">شاهد الدرس</a>
                    <?php if (current_user()): ?><button type="button" class="button button-secondary favorite-button" data-favorite data-type="lesson" data-id="<?= (int) $lesson['id'] ?>" data-saved="<?= $lesson['is_favorite'] ? 'true' : 'false' ?>" aria-pressed="<?= $lesson['is_favorite'] ? 'true' : 'false' ?>"><?= $lesson['is_favorite'] ? 'إزالة من المفضلة' : 'أضف للمفضلة' ?></button><?php else: ?><a href="/login" class="favorite-login">سجّل الدخول لحفظ الدرس</a><?php endif; ?>
                </article>
            <?php endforeach; ?>
        </div><?php endif; ?>
    </section>

    <?php if ($videoParam !== ''): ?>
        <div class="knowledge-status" data-video-status role="status" aria-live="polite" hidden></div>
        <div class="video-player-wrap" data-video-player hidden></div>
        <p class="progress-feedback" data-video-progress-status role="status" aria-live="polite" hidden></p>
        <?php if ($selectedLesson): ?><article class="video-detail"><span class="source-chip"><?= e($selectedLesson['scholar_name'] ?: 'درس مستقل') ?></span><h2><?= e($selectedLesson['title']) ?></h2><?php if ($selectedLesson['description']): ?><p><?= nl2br(e($selectedLesson['description'])) ?></p><?php endif; ?><a href="<?= e($selectedLesson['source_url']) ?>" target="_blank" rel="noopener noreferrer">فتح الفيديو على YouTube ↗</a></article><?php endif; ?>
    <?php endif; ?>

    <?php if ($selectedFreeLesson): ?>
        <section class="comments-section" aria-labelledby="free-comments-title">
            <div class="section-heading"><div><span class="eyebrow">مشاركة المشاهدين</span><h2 id="free-comments-title">التعليقات (<?= count(array_filter($freeLessonComments, static fn($comment) => $comment['status'] === 'visible')) ?>)</h2></div></div>
            <?php if (current_user()): ?>
                <form class="form-stack comment-form" method="post" action="/lesson-comment"><?= csrf_field() ?><input type="hidden" name="lesson_id" value="<?= (int) $selectedFreeLesson['id'] ?>"><input type="hidden" name="series_id" value="0"><label>اكتب تعليقك<textarea name="body" minlength="2" maxlength="2000" rows="3" required></textarea></label><button class="button button-secondary" type="submit">إرسال للمراجعة</button><small>يظهر التعليق بعد مراجعته.</small></form>
            <?php else: ?><p class="source-note"><a href="/login">سجّل الدخول</a> للمشاركة بتعليق.</p><?php endif; ?>
            <div class="comment-list"><?php foreach ($freeLessonComments as $comment): ?><article class="comment-card"><div><strong><?= e($comment['display_name'] ?: '@' . $comment['username']) ?></strong><?php if ($comment['status'] === 'pending'): ?><span class="source-chip">بانتظار المراجعة</span><?php endif; ?></div><p><?= nl2br(e($comment['body'])) ?></p><time><?= e((string) $comment['created_at']) ?></time></article><?php endforeach; ?></div>
        </section>
    <?php endif; ?>

    <section class="curated-section free-video-section" aria-labelledby="free-video-heading">
        <div class="section-heading"><div><span class="eyebrow">مشاهدة مباشرة</span><h2 id="free-video-heading">فيديو حر</h2></div></div>
        <p class="source-note">أدخل رابط فيديو YouTube لمشاهدته بالمشغل الرسمي. لا نحمّل الفيديو ولا نخفي أدوات المشغل أو إعلاناته.</p>
        <form class="knowledge-search" data-video-form>
            <label for="video-url">رابط فيديو YouTube</label>
            <div class="search-row"><input id="video-url" name="video" type="url" maxlength="300" value="<?= e($videoParam) ?>" placeholder="https://www.youtube.com/watch?v=…"><button class="button button-primary" type="submit">عرض الفيديو</button></div>
        </form>
        <p class="source-note">المشغل الرسمي من <a href="https://developers.google.com/youtube/iframe_api_reference" target="_blank" rel="noopener noreferrer">YouTube</a>. قد لا تتوفر بعض الفيديوهات للتضمين؛ يمكن فتحها مباشرة على YouTube.</p>
        <?php if ($videoParam === ''): ?><div class="knowledge-status" data-video-status role="status" aria-live="polite" hidden></div><div class="video-player-wrap" data-video-player hidden></div><p class="progress-feedback" data-video-progress-status role="status" aria-live="polite" hidden></p><?php endif; ?>
    </section>
</section>
