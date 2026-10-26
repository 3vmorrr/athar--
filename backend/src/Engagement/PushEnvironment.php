<?php
declare(strict_types=1);

namespace Athar\Engagement;

final class PushEnvironment
{
    public static function current(): string
    {
        $explicit=strtolower(trim((string)(getenv('ATHAR_PUSH_ENVIRONMENT')?:'')));
        if($explicit!==''){
            if(!preg_match('/^[a-f0-9]{12}$/',$explicit))throw new \RuntimeException('ATHAR_PUSH_ENVIRONMENT must be 12 hexadecimal characters.');
            return $explicit;
        }
        $base=trim((string)(getenv('ATHAR_BASE_URL')?:''));
        if($base===''||filter_var($base,FILTER_VALIDATE_URL)===false)throw new \RuntimeException('ATHAR_PUSH_ENVIRONMENT or ATHAR_BASE_URL is required.');
        $api=rtrim($base,'/').'/api/v1/';
        return substr(hash('sha256',$api),0,12);
    }
}
