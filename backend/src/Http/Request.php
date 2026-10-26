<?php
declare(strict_types=1);
namespace Athar\Http;
final class Request
{
    public string $method;
    public string $path;
    public array $query;
    public array $body;
    public array $headers;
    public string $ip;
    public function __construct(string $method, string $path, array $body = [], array $headers = [], array $query = [], string $ip = '127.0.0.1')
    {
        $this->method = strtoupper($method); $this->path = rtrim($path, '/') ?: '/'; $this->body = $body; $this->headers = array_change_key_case($headers, CASE_LOWER); $this->query = $query; $this->ip = $ip;
    }
    public static function fromGlobals(): self
    {
        $headers = function_exists('getallheaders') ? getallheaders() : [];
        if (isset($_SERVER['HTTP_AUTHORIZATION'])) $headers['Authorization'] = $_SERVER['HTTP_AUTHORIZATION'];
        $body = [];
        $method = strtoupper((string) ($_SERVER['REQUEST_METHOD'] ?? 'GET'));
        if (in_array($method, ['POST','PUT','PATCH','DELETE'], true)) {
            if ((int) ($_SERVER['CONTENT_LENGTH'] ?? 0) > 262144) throw new ApiError('payload_too_large', 413);
            $raw = file_get_contents('php://input', false, null, 0, 262145);
            if (is_string($raw) && strlen($raw) > 262144) throw new ApiError('payload_too_large', 413);
            if ($raw !== '' && $raw !== false) {
                if (!str_starts_with(strtolower((string) ($_SERVER['CONTENT_TYPE'] ?? '')), 'application/json')) throw new ApiError('unsupported_content_type', 415);
                $body = json_decode($raw, true);
                if (!is_array($body) || json_last_error() !== JSON_ERROR_NONE || substr(ltrim($raw), 0, 1) !== '{') throw new ApiError('invalid_json', 400);
            }
        }
        return new self($method, (string) (parse_url($_SERVER['REQUEST_URI'] ?? '/', PHP_URL_PATH) ?: '/'), $body, $headers, $_GET, (string) ($_SERVER['REMOTE_ADDR'] ?? 'unknown'));
    }
    public function bearer(): string
    {
        $header = (string) ($this->headers['authorization'] ?? '');
        return preg_match('/^Bearer ([a-f0-9]{64})$/i', $header, $m) ? $m[1] : '';
    }
    public function string(string $name, int $max = 4000): string
    {
        $value = $this->body[$name] ?? '';
        if (!is_string($value) || mb_strlen($value, 'UTF-8') > $max) throw new ApiError('invalid_fields', 422, [$name => 'invalid']);
        return $value;
    }
}
