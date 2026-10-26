<?php
declare(strict_types=1);
namespace Athar\Engagement;
interface PushHttpTransport
{
    public function request(string $method,string $url,array $headers,string $body): PushHttpResponse;
}
