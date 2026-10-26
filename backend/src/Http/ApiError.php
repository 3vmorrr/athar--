<?php
declare(strict_types=1);
namespace Athar\Http;
use RuntimeException;
final class ApiError extends RuntimeException
{
    public int $status;
    public array $fields;
    public function __construct(string $code, int $status = 422, array $fields = []) { parent::__construct($code); $this->status = $status; $this->fields = $fields; }
}
