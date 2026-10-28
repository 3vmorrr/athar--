<?php
declare(strict_types=1);
namespace Athar\Catalog;

final class BookAccess
{
    public static function https(string $url): bool
    {
        if(!\Athar\Infrastructure\HttpsUrl::valid($url))return false;
        $parts=parse_url($url);$host=strtolower(trim((string)($parts['host']??''),'[]'));
        if(isset($parts['port'])&&(int)$parts['port']!==443)return false;
        if($host===''||str_ends_with($host,'.')||$host==='localhost'||str_ends_with($host,'.localhost')||str_ends_with($host,'.local'))return false;
        if(preg_match('/^0x[0-9a-f]+$/i',$host))return false;
        $literal=filter_var($host,FILTER_VALIDATE_IP);
        if($literal!==false){
            $bytes=inet_pton($host);
            if(strlen($bytes)===16&&substr($bytes,0,12)===str_repeat("\0",10)."\xff\xff"){
                $host=inet_ntop(substr($bytes,12));$bytes=inet_pton($host);
            }
            if(filter_var($host,FILTER_VALIDATE_IP,FILTER_FLAG_NO_PRIV_RANGE|FILTER_FLAG_NO_RES_RANGE)===false)return false;
            if(strlen($bytes)===16&&ord($bytes[0])===255)return false;
            if(strlen($bytes)===4){
                [$a,$b,$c]=array_values(unpack('C4',$bytes));
                if($a===0||$a>=224)return false;
                if(($a===100&&$b>=64&&$b<=127)||($a===198&&$b>=18&&$b<=19))return false;
                if(($a===192&&$b===0&&in_array($c,[0,2],true))||($a===198&&$b===51&&$c===100)||($a===203&&$b===0&&$c===113))return false;
            }
        }elseif(preg_match('/^[0-9.]+$/',$host)||str_contains($host,':'))return false;
        return true;
    }

    public static function downloadable(array $book): bool
    {
        return !empty($book['can_download']) && trim((string)($book['rights_basis'] ?? '')) !== ''
            && self::https((string)($book['source_url'] ?? '')) && self::https((string)($book['download_url'] ?? ''));
    }
}
