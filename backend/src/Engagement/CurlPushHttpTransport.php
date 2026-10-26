<?php
declare(strict_types=1);

namespace Athar\Engagement;

final class CurlPushHttpTransport implements PushHttpTransport
{
    public function __construct(private int $timeoutSeconds=15)
    {
        if($timeoutSeconds<1||$timeoutSeconds>60)throw new \InvalidArgumentException('invalid_push_timeout');
        if(!function_exists('curl_init'))throw new \RuntimeException('curl_extension_required');
    }

    public function request(string $method,string $url,array $headers,string $body): PushHttpResponse
    {
        if(!in_array($method,['POST'],true))throw new \InvalidArgumentException('invalid_push_http_method');
        if(!str_starts_with($url,'https://'))throw new \InvalidArgumentException('push_https_required');
        $handle=curl_init($url);
        if($handle===false)throw new \RuntimeException('push_transport_init_failed');
        $safeHeaders=[];
        foreach($headers as $name=>$value){
            if(!is_string($name)||!is_string($value)||preg_match('/[\r\n]/',$name.$value))throw new \InvalidArgumentException('invalid_push_header');
            $safeHeaders[]=$name.': '.$value;
        }
        curl_setopt_array($handle,[
            CURLOPT_POST=>true,
            CURLOPT_POSTFIELDS=>$body,
            CURLOPT_HTTPHEADER=>$safeHeaders,
            CURLOPT_RETURNTRANSFER=>true,
            CURLOPT_CONNECTTIMEOUT=>5,
            CURLOPT_TIMEOUT=>$this->timeoutSeconds,
            CURLOPT_FOLLOWLOCATION=>false,
            CURLOPT_PROTOCOLS=>CURLPROTO_HTTPS,
        ]);
        $response=curl_exec($handle);
        if($response===false){
            $code=curl_errno($handle);
            curl_close($handle);
            throw new \RuntimeException('push_transport_error_'.$code);
        }
        $status=(int)curl_getinfo($handle,CURLINFO_RESPONSE_CODE);
        curl_close($handle);
        return new PushHttpResponse($status,(string)$response);
    }
}
