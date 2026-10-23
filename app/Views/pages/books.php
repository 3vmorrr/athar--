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
    <?php if ($curatedBooks): ?><section class="curated-section" aria-labelledby="curated-books-heading"><div class="section-heading"><div><span class="eyebrow">إتاحة موثقة من إدارة أثر</span><h2 id="curated-books-heading">كتب مختارة</h2></div></div><div class="knowledge-results">
        <?php foreach ($curatedBooks as $book): ?><article class="knowledge-card book-card"><span class="source-chip">حقوق الإتاحة موثقة</span><h2><?= e($book['title']) ?></h2><p class="book-author">المؤلف: <?= e($book['author']) ?></p><?php if ($book['description']): ?><p><?= e($book['description']) ?></p><?php endif; ?><div class="card-meta"><a href="<?= e($book['source_url']) ?>" target="_blank" rel="noopener noreferrer">المصدر ↗</a><?php if ($book['can_download'] && $book['file_key']): ?><a class="button button-secondary" href="/book-download?id=<?= (int) $book['id'] ?>">تنزيل PDF</a><?php endif; ?></div></article><?php endforeach; ?>
    </div></section><?php endif; ?>
    <div class="knowledge-status" data-books-status role="status" aria-live="polite" hidden></div>
    <div class="knowledge-results" data-books-results></div>
</section>
