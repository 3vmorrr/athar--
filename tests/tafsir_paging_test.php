<?php
declare(strict_types=1);

require dirname(__DIR__) . '/backend/bootstrap.php';

use Athar\Providers\Quranpedia;
use Athar\Http\ApiError;

$checks = 0;

function tafsir_check(bool $value, string $name): void
{
    global $checks;
    $checks++;
    if (!$value) {
        throw new RuntimeException('FAIL: ' . $name);
    }
}

function tafsir_reject(callable $call, int $status, string $name): void
{
    try {
        $call();
    } catch (ApiError $e) {
        tafsir_check($e->status === $status, $name);
        return;
    } catch (InvalidArgumentException $e) {
        tafsir_check($status === 422, $name);
        return;
    }
    tafsir_check(false, $name);
}

$books = [];
for ($i = 1; $i <= 45; $i++) {
    $books[] = [
        'id' => $i,
        'name' => 'Reviewed Tafsir ' . $i,
        'author' => ['ar_name' => 'Author ' . $i],
    ];
}
$blocks = [];
for ($i = 1; $i <= 19; $i++) {
    $blocks[] = [
        'text' => '<p>Block ' . $i . ' &amp; safe</p><script>discard()</script>',
        'part' => 1,
        'page' => $i,
        'ayahs' => '1:1',
    ];
}

$calls = [];
$provider = new Quranpedia(
    static function (string $path) use (&$calls, $books, $blocks): array {
        $calls[] = $path;
        if ($path === 'category/3/books') {
            return $books;
        }
        if ($path === 'ayah/1/1/book/5') {
            return [
                'book' => [
                    'id' => 5,
                    'name' => 'Reviewed Tafsir 5',
                    'author' => ['full_name' => 'Author 5'],
                ],
                'content' => $blocks,
            ];
        }
        throw new RuntimeException('Unexpected provider request.');
    },
);

$catalog = $provider->books([
    'surah' => 1,
    'page' => 2,
    'per_page' => 20,
]);
tafsir_check(count($catalog['items']) === 20, 'catalog page size is stable');
tafsir_check($catalog['items'][0]['id'] === 21, 'catalog page offset is stable');
tafsir_check(
    $catalog['pagination'] === [
        'page' => 2,
        'per_page' => 20,
        'total' => 45,
        'pages' => 3,
        'has_more' => true,
    ],
    'catalog pagination schema is explicit',
);
tafsir_check(
    $catalog['source']['url'] === 'https://quranpedia.net',
    'catalog source attribution is stable',
);

$filtered = $provider->books([
    'surah' => 1,
    'q' => 'Tafsir 45',
    'page' => 1,
    'per_page' => 20,
]);
tafsir_check(
    count($filtered['items']) === 1 &&
        $filtered['pagination']['total'] === 1 &&
        $filtered['pagination']['pages'] === 1 &&
        $filtered['pagination']['has_more'] === false,
    'catalog search and pagination share one contract',
);

$content = $provider->content([
    'surah' => 1,
    'ayah' => 1,
    'book' => 5,
    'page' => 2,
    'per_page' => 8,
]);
tafsir_check($content['book']['author'] === 'Author 5', 'actual author preserved');
tafsir_check(count($content['items']) === 8, 'content paging caps the page');
tafsir_check($content['items'][0]['page'] === 9, 'content page offset is stable');
tafsir_check(
    $content['items'][0]['text'] === 'Block 9 & safe',
    'entities retained and executable markup removed',
);
tafsir_check(
    $content['pagination'] === [
        'page' => 2,
        'per_page' => 8,
        'total' => 19,
        'pages' => 3,
        'has_more' => true,
    ],
    'content pagination schema is explicit',
);
tafsir_check(
    $content['source']['url'] ===
        'https://api.quranpedia.net/v1/ayah/1/1/book/5',
    'direct source attribution',
);

tafsir_reject(
    fn() => $provider->books(['surah' => 1, 'page' => 0]),
    422,
    'zero catalog page rejected',
);
tafsir_reject(
    fn() => $provider->content([
        'surah' => 1,
        'ayah' => 1,
        'book' => 5,
        'per_page' => 51,
    ]),
    422,
    'oversized content page rejected',
);
tafsir_reject(
    fn() => $provider->content(['surah' => 1, 'ayah' => 1, 'book' => 99]),
    404,
    'unlisted book cannot be relabeled',
);
tafsir_reject(
    fn() => $provider->content(['surah' => 114, 'ayah' => 7, 'book' => 5]),
    422,
    'invalid canonical ayah rejected before provider',
);
tafsir_reject(
    fn() => $provider->content([
        'surah' => 'https://evil.invalid',
        'ayah' => 1,
        'book' => 5,
    ]),
    422,
    'no injectable origin or path',
);

$malformed = new Quranpedia(static function (string $path): array {
    if ($path === 'category/3/books') {
        return [['id' => 5, 'name' => 'Reviewed Tafsir 5']];
    }
    return ['book' => ['id' => 5], 'content' => 'broken'];
});
tafsir_reject(
    fn() => $malformed->content(['surah' => 1, 'ayah' => 1, 'book' => 5]),
    502,
    'malformed provider content fails closed',
);

$malformedCatalog = new Quranpedia(
    static fn(string $path): string => 'not-an-array',
);
tafsir_reject(
    fn() => $malformedCatalog->books(['surah' => 1]),
    502,
    'malformed provider catalog fails closed',
);

$legacyBlocks = array_fill(0, 61, ['text' => '<p>Legacy full content</p>']);
$legacy = new Quranpedia(static function (string $path) use ($legacyBlocks): array {
    if ($path === 'category/3/books') return [['id' => 5, 'name' => 'Legacy source']];
    return ['book' => ['id' => 5, 'name' => 'Legacy source'], 'content' => $legacyBlocks];
});
$unpaged = $legacy->content(['surah' => 1, 'ayah' => 1, 'book' => 5]);
tafsir_check(count($unpaged['items']) === 61, 'legacy clients retain full unpaged content');
$paged = $legacy->content(['surah' => 1, 'ayah' => 1, 'book' => 5, 'page' => 8, 'per_page' => 8]);
tafsir_check(count($paged['items']) === 5 && !$paged['pagination']['has_more'], 'last explicit page retains remaining blocks');
foreach ([true, 1.0, [], '01', '-1', '1e1'] as $invalid) {
    tafsir_reject(fn() => $legacy->books(['surah' => 1, 'page' => $invalid]), 422, 'invalid paging type fails closed');
}

echo 'PASS: ' . $checks .
    " tafsir catalog/content/paging/source/markup/canonical assertions.\n";
