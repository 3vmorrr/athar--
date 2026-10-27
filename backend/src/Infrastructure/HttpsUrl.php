<?php
declare(strict_types=1);
namespace Athar\Infrastructure;
final class HttpsUrl
{
    public static function valid(string $url): bool
    {
        if (strlen($url)>500 || filter_var($url,FILTER_VALIDATE_URL)===false) return false;
        $parts=parse_url($url);
        return is_array($parts) && strtolower($parts['scheme'] ?? '')==='https' && !empty($parts['host']) && !isset($parts['user']) && !isset($parts['pass']);
    }
}
