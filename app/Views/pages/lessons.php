<?php
$videoParam = is_string($_GET['video'] ?? null) ? trim($_GET['video']) : '';
?>
<section class="knowledge-page section-wrap" data-lessons-page data-video="<?= e($videoParam) ?>">
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
    <div class="video-player-wrap" data-video-player hidden></div>
    <?php if ($curatedLessons): ?><section class="curated-section"><div class="section-heading"><div><span class="eyebrow">اختيارات إدارة أثر</span><h2>دروس منشورة</h2></div></div><div class="knowledge-results">
        <?php foreach ($curatedLessons as $lesson): ?><article class="knowledge-card lesson-card"><span class="source-chip"><?= e($lesson['scholar_name'] ?: 'درس') ?></span><h2><?= e($lesson['title']) ?></h2><?php if ($lesson['description']): ?><p><?= e($lesson['description']) ?></p><?php endif; ?><button class="button button-secondary" type="button" data-curated-video="<?= e($lesson['source_url']) ?>">تشغيل الدرس</button></article><?php endforeach; ?>
    </div></section><?php endif; ?>
</section>
