<section class="knowledge-page section-wrap" <?= $activeSeriesLesson ? 'data-lessons-page data-video="'.e((string)$activeSeriesLesson['source_url']).'" data-lesson-id="'.(int)$activeSeriesLesson['id'].'" data-resume-seconds="'.(int)($activeSeriesLesson['position_seconds'] ?? 0).'"' : '' ?>>
    <a class="back-link" href="/lessons">العودة إلى الدروس</a>
    <?php if (!$publicSeries): ?><h1>السلسلة غير متاحة</h1><p>قد يكون المحتوى غير منشور أو أن الرابط غير صالح.</p>
    <?php else: ?>
        <header class="knowledge-heading series-heading"><span class="eyebrow">سلسلة دروس مرتبة</span><h1><?= e($publicSeries['title']) ?></h1><?php if ($publicSeries['scholar_name']): ?><p>للشيخ <a href="/scholar?id=<?= (int)$publicSeries['scholar_id'] ?>"><?= e($publicSeries['scholar_name']) ?></a></p><?php endif; ?><?php if ($publicSeries['description']): ?><p><?= nl2br(e($publicSeries['description'])) ?></p><?php endif; ?></header>
        <?php if (!$seriesLessons): ?><p class="source-note">لا توجد دروس منشورة في هذه السلسلة حتى الآن.</p>
        <?php else: ?>
            <form class="watch-hidden-form" data-video-form aria-hidden="true"><input type="hidden" name="video" value="<?= e((string)$activeSeriesLesson['source_url']) ?>"></form>
            <div class="knowledge-status" data-video-status role="status" aria-live="polite" hidden></div>
            <div class="series-watch-layout">
                <div class="series-watch-main">
                    <div class="video-player-wrap" data-video-player hidden></div>
                    <p class="progress-feedback" data-video-progress-status role="status" aria-live="polite" hidden></p>
                    <article class="video-detail"><span class="source-chip"><?= e($publicSeries['scholar_name'] ?: 'درس') ?></span><h2 data-active-lesson-title><?= e($activeSeriesLesson['title']) ?></h2><p data-active-lesson-description><?= e((string)($activeSeriesLesson['description'] ?? '')) ?></p><a class="series-self-link" href="<?= e((string)$activeSeriesLesson['source_url']) ?>" target="_blank" rel="noopener noreferrer">فتح الفيديو على YouTube ↗</a></article>
                    <section class="comments-section" aria-labelledby="comments-title"><div class="section-heading"><div><span class="eyebrow">مشاركة المشاهدين</span><h2 id="comments-title">التعليقات (<?= count(array_filter($lessonComments,static fn($c)=>$c['status']==='visible')) ?>)</h2></div></div>
                        <?php if (current_user()): ?><form class="form-stack comment-form" method="post" action="/lesson-comment"><?= csrf_field() ?><input type="hidden" name="lesson_id" value="<?= (int)$activeSeriesLesson['id'] ?>"><input type="hidden" name="series_id" value="<?= (int)$publicSeries['id'] ?>"><label for="lesson-comment-body">اكتب تعليقك<textarea id="lesson-comment-body" name="body" minlength="2" maxlength="2000" rows="3" required></textarea></label><button class="button button-secondary" type="submit">إرسال للمراجعة</button><small>يظهر التعليق بعد مراجعته.</small></form><?php else: ?><p class="source-note"><a href="/login">سجّل الدخول</a> للمشاركة بتعليق.</p><?php endif; ?>
                        <div class="comment-list"><?php foreach ($lessonComments as $comment): ?><article class="comment-card"><div><strong><?= e($comment['display_name'] ?: '@'.$comment['username']) ?></strong><?php if ($comment['status']==='pending'): ?><span class="source-chip">بانتظار المراجعة</span><?php endif; ?></div><p><?= nl2br(e($comment['body'])) ?></p><time><?= e((string)$comment['created_at']) ?></time></article><?php endforeach; ?></div>
                    </section>
                </div>
                <aside class="series-playlist" aria-label="قائمة دروس السلسلة"><h2>دروس السلسلة <span><?= count($seriesLessons) ?></span></h2><ol><?php foreach ($seriesLessons as $index=>$item): ?><li><button type="button" class="playlist-item" data-curated-video="<?= e($item['source_url']) ?>" data-lesson-id="<?= (int)$item['id'] ?>" data-resume-seconds="<?= (int)($item['position_seconds'] ?? 0) ?>" data-lesson-title="<?= e($item['title']) ?>" data-lesson-description="<?= e((string)$item['description']) ?>" aria-current="<?= (int)$item['id']===(int)$activeSeriesLesson['id']?'true':'false' ?>"><span class="playlist-number"><?= $index+1 ?></span><span><strong><?= e($item['title']) ?></strong><small><?= e(mb_strimwidth((string)$item['description'],0,88,'…','UTF-8')) ?></small></span></button></li><?php endforeach; ?></ol></aside>
            </div>
        <?php endif; ?>
    <?php endif; ?>
</section>
