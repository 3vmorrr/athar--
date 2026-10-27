<?php
declare(strict_types=1);
namespace Athar\Http;
final class Response
{
    public int $status;
    public array $body;
    public function __construct(array $body, int $status = 200) { $this->body = $body; $this->status = $status; }
    public function emit(): void
    {
        http_response_code($this->status);
        if ($this->status === 503 && ($this->body['error']['code'] ?? '') === 'maintenance') header('Retry-After: 30');
        header('Content-Type: application/json; charset=utf-8'); header('Cache-Control: no-store, private'); header('X-Content-Type-Options: nosniff');
        echo json_encode($this->body, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES | JSON_THROW_ON_ERROR);
    }
}
