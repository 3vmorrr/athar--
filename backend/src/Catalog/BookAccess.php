<?php
declare(strict_types=1);
namespace Athar\Catalog;

final class BookAccess
{
    public static function https(string $url): bool
    {
        return \Athar\Infrastructure\HttpsUrl::valid($url);
    }

    public static function downloadable(array $book): bool
    {
        return !empty($book['can_download']) && trim((string)($book['rights_basis'] ?? '')) !== ''
            && self::https((string)($book['source_url'] ?? '')) && self::https((string)($book['download_url'] ?? ''));
    }
}
