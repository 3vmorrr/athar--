<?php
$selectedLessonId = filter_var($_GET['lesson'] ?? null, FILTER_VALIDATE_INT) ?: 0;
$selectedLesson = null;
foreach ($curatedLessons as $curatedLesson) { if ((int) $curatedLesson['id'] === $selectedLessonId) { $selectedLesson = $curatedLesson; break; } }
$videoParam = $selectedLesson ? (string) $selectedLesson['source_url'] : (is_string($_GET['video'] ?? null) ? trim($_GET['video']) : '');
?>
<section class="knowledge-page section-wrap" data-lessons-page data-video="<?= e($videoParam) ?>" data-lesson-id="<?= (int) ($selectedLesson['id'] ?? 0) ?>" data-resume-seconds="<?= (int) ($selectedLesson['position_seconds'] ?? 0) ?>">
    <a class="back-link" href="/">العودة إلى الرئيسية</a>
    <header class="knowledge-heading">
        <span class="eyebrow"><span class="eyebrow-dot" aria-hidden="true"></span> تشغيل عبر المشغل الرسمي</span>
        <h1>الدروس والسلاسل</h1>
        <p>أدخل رابط درس من YouTube لعرضه بالمشغل الرسمي. لا نحمّل الفيديو ولا نخفي أدوات المشغل أو إعلاناته.</p>
    </header>
    <form class="knowledge-search" data-video-form>
        <label for="video-url">رابط فيديو YouTube</label>
        <div class="search-row">
            <input id="video-url" name="video" type="url" maxlength="300" value="<?= e($videoParam) ?>" placeholder="https://www.youtube.com/watch?v=…">
            <button class="button button-primary" type="submit">عرض الدرس</button>
        </div>
    </form>
    <p class="source-note">المشغل من <a href="https://developers.google.com/youtube/iframe_api_reference" target="_blank" rel="noopener noreferrer">YouTube IFrame Player API</a>. قد لا تتوفر بعض الفيديوهات للتضمين؛ يمكن فتحها مباشرة على YouTube.</p>
    <div class="knowledge-status" data-video-status role="status" aria-live="polite" hidden></div>
    <div class="video-player-wrap" data-video-player hidden></div><p class="progress-feedback" data-video-progress-status role="status" aria-live="polite" hidden></p>
    <?php if ($curatedScholars): ?><section class="curated-section"><div class="section-heading"><div><span class="eyebrow">مكتبة العلماء</span><h2>العلماء ومحتواهم</h2></div></div><div class="knowledge-results"><?php foreach ($curatedScholars as $scholar): ?><article class="knowledge-card"><h3><a href="/scholar?id=<?= (int) $scholar['id'] ?>"><?= e($scholar['name']) ?></a></h3><?php if ($scholar['biography']): ?><p><?= e(mb_strimwidth((string) $scholar['biography'], 0, 220, '…', 'UTF-8')) ?></p><?php endif; ?><a class="button button-secondary" href="/scholar?id=<?= (int) $scholar['id'] ?>">عرض السلاسل والدروس</a></article><?php endforeach; ?></div></section><?php endif; ?>
    <?php if ($curatedLessons): ?><section class="curated-section"><div class="section-heading"><div><span class="eyebrow">اختيارات إدارة أثر</span><h2>دروس منشورة</h2></div></div><div class="knowledge-results">
        <?php foreach ($curatedLessons as $lesson): ?><article class="knowledge-card lesson-card"><?php if ($lesson['scholar_id']): ?><a class="source-chip" href="/scholar?id=<?= (int) $lesson['scholar_id'] ?>"><?= e($lesson['scholar_name']) ?></a><?php else: ?><span class="source-chip">درس</span><?php endif; ?><?php if ($lesson['series_name']): ?><p class="book-details">من سلسلة: <a href="/series?id=<?= (int) $lesson['series_id'] ?>"><?= e($lesson['series_name']) ?></a></p><?php endif; ?><h2><?= e($lesson['title']) ?></h2><?php if ($lesson['description']): ?><p><?= e($lesson['description']) ?></p><?php endif; ?><button class="button button-secondary" type="button" data-curated-video="<?= e($lesson['source_url']) ?>" data-lesson-id="<?= (int) $lesson['id'] ?>" data-resume-seconds="<?= (int) ($lesson['position_seconds'] ?? 0) ?>">تشغيل الدرس</button><?php if (current_user()): ?><button type="button" class="button button-secondary favorite-button" data-favorite data-type="lesson" data-id="<?= (int) $lesson['id'] ?>" data-saved="<?= $lesson['is_favorite'] ? 'true' : 'false' ?>" aria-pressed="<?= $lesson['is_favorite'] ? 'true' : 'false' ?>"><?= $lesson['is_favorite'] ? 'إزالة من المفضلة' : 'أضف للمفضلة' ?></button><?php else: ?><a href="/login" class="favorite-login">سجّل الدخول لحفظ الدرس</a><?php endif; ?></article><?php endforeach; ?>
    </div></section><?php endif; ?>
</section>
