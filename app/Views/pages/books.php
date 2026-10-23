<section class="knowledge-page section-wrap" data-books-page>
    <a class="back-link" href="/">العودة إلى الرئيسية</a>
    <header class="knowledge-heading">
        <span class="eyebrow"><span class="eyebrow-dot" aria-hidden="true"></span> فهرس الكتب المتصلة بالقرآن</span>
        <h1>مكتبة أثر</h1>
        <p>ابحث عن كتب التفسير وعلوم القرآن مع اسم المؤلف ومعلومات المصدر المتاحة.</p>
    </header>
    <form class="knowledge-search" data-books-form role="search">
        <label for="books-query">عنوان أو موضوع الكتاب</label>
        <div class="search-row">
            <input id="books-query" name="q" type="search" minlength="2" maxlength="80" value="<?= e(is_string($_GET['q'] ?? null) ? $_GET['q'] : 'تفسير') ?>" placeholder="مثال: تفسير">
            <button class="button button-primary" type="submit">ابحث في الفهرس</button>
        </div>
    </form>
    <p class="source-note">الفهرس من <a href="https://quranpedia.net" target="_blank" rel="noopener noreferrer">Quranpedia</a>. هذه نتائج ببليوغرافية وليست ملفات PDF؛ الإتاحة وحقوق كل كتاب يحددها ناشره.</p>
    <form class="knowledge-search" method="get" action="/books" role="search"><label for="local-books-query">ابحث في مكتبة أثر المحلية</label><div class="search-row"><input id="local-books-query" name="local_q" type="search" maxlength="80" value="<?= e($localBookSearch) ?>" placeholder="عنوان أو مؤلف أو كلمات مفتاحية"><button class="button button-secondary" type="submit">بحث في كتب أثر</button></div></form>
    <p class="source-note">الكتب المحلية: <?= (int) $curatedBookTotal ?> نتيجة · الصفحة <?= (int) $bookPage ?> من <?= (int) $bookPageCount ?></p>
    <?php if ($curatedBooks): ?><section class="curated-section" aria-labelledby="curated-books-heading"><div class="section-heading"><div><span class="eyebrow">إتاحة موثقة من إدارة أثر</span><h2 id="curated-books-heading">كتب مختارة</h2></div></div><div class="knowledge-results">
        <?php foreach ($curatedBooks as $book): ?><article class="knowledge-card book-card"><span class="source-chip">حقوق الإتاحة موثقة</span><h2><?= e($book['title']) ?></h2><p class="book-author">المؤلف: <?= e($book['author']) ?><?= $book['rights_holder'] ? ' · صاحب الحقوق: ' . e($book['rights_holder']) : '' ?></p><?php if ($book['description']): ?><p><?= e($book['description']) ?></p><?php endif; ?><div class="card-meta"><span><?= ($book['language'] ?? 'ar') === 'ar' ? 'العربية' : (($book['language'] ?? '') === 'en' ? 'الإنجليزية' : 'لغة أخرى') ?><?= $book['publication_year'] ? ' · ' . (int) $book['publication_year'] : '' ?></span><?php if ($book['keywords']): ?><span><?= e($book['keywords']) ?></span><?php endif; ?><a href="<?= e($book['source_url']) ?>" target="_blank" rel="noopener noreferrer">المصدر ↗</a><?php if ($book['can_download'] && ($book['file_key'] || $book['download_url'])): ?><a class="button button-secondary" href="/book-download?id=<?= (int) $book['id'] ?>"<?= $book['download_url'] ? ' target="_blank" rel="noopener noreferrer"' : '' ?>>تنزيل الكتاب</a><?php endif; ?><?php if (current_user()): ?><button type="button" class="button button-secondary favorite-button" data-favorite data-type="book" data-id="<?= (int) $book['id'] ?>" data-saved="<?= $book['is_favorite'] ? 'true' : 'false' ?>" aria-pressed="<?= $book['is_favorite'] ? 'true' : 'false' ?>"><?= $book['is_favorite'] ? 'إزالة من المفضلة' : 'أضف للمفضلة' ?></button><?php else: ?><a href="/login" class="favorite-login">سجّل الدخول للحفظ</a><?php endif; ?></div></article><?php endforeach; ?>
    </div></section><?php endif; ?>
    <?php if (!$curatedBooks && $localBookSearch !== ''): ?><p class="source-note" role="status">لا توجد كتب محلية تطابق البحث.</p><?php endif; ?>
    <?php if ($bookPageCount > 1): ?><nav class="pagination" aria-label="صفحات الكتب"><?php if ($bookPage > 1): ?><a class="button button-secondary" href="/books?local_q=<?= rawurlencode($localBookSearch) ?>&amp;book_page=<?= $bookPage - 1 ?>">السابقة</a><?php endif; ?><span>صفحة <?= (int) $bookPage ?> من <?= (int) $bookPageCount ?></span><?php if ($bookPage < $bookPageCount): ?><a class="button button-secondary" href="/books?local_q=<?= rawurlencode($localBookSearch) ?>&amp;book_page=<?= $bookPage + 1 ?>">التالية</a><?php endif; ?></nav><?php endif; ?>
    <div class="knowledge-status" data-books-status role="status" aria-live="polite" hidden></div>
    <div class="knowledge-results" data-books-results></div>
</section>
