<?php
declare(strict_types=1);
namespace Athar\Engagement;
final class PushHttpResponse
{
    public function __construct(public readonly int $status,public readonly string $body)
    {
        if($status<100||$status>599)throw new \InvalidArgumentException('invalid_http_status');
    }
    public function json(): array
    {
        $decoded=json_decode($this->body,true,64,JSON_THROW_ON_ERROR);
        if(!is_array($decoded))throw new \RuntimeException('invalid_json_response');
        return $decoded;
    }
}
