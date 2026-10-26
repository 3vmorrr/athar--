<?php
declare(strict_types=1);

namespace Athar\Engagement;

use DateTimeImmutable;

final class PushMessage
{
    public function __construct(
        public readonly string $token,
        public readonly string $category,
        public readonly string $title,
        public readonly string $body,
        public readonly array $data,
        public readonly DateTimeImmutable $expiresAt,
        public readonly string $idempotencyKey,
    ) {
        if(!preg_match('/^[A-Za-z0-9:_-]{16,4096}$/',$token))throw new \InvalidArgumentException('invalid_push_token');
        if(!in_array($category,[
            'lesson_reminders','following_updates','quran_routine','adhkar_routine',
            'salawat_friday','custom_routines','khatmah','night_prayer','campaigns'
        ],true))throw new \InvalidArgumentException('invalid_push_category');
        if(trim($title)===''||mb_strlen($title)>160)throw new \InvalidArgumentException('invalid_push_title');
        if(mb_strlen($body)>500)throw new \InvalidArgumentException('invalid_push_body');
        if($idempotencyKey===''||strlen($idempotencyKey)>160)throw new \InvalidArgumentException('invalid_push_dedupe');
        foreach($data as $key=>$value){
            if(!is_string($key)||!preg_match('/^[a-z0-9_]{1,64}$/',$key)||!is_scalar($value)){
                throw new \InvalidArgumentException('invalid_push_data');
            }
            if(strlen((string)$value)>1024)throw new \InvalidArgumentException('push_data_too_large');
        }
    }
}

final class PushSendResult
{
    private function __construct(
        public readonly string $status,
        public readonly ?string $providerMessageId,
        public readonly ?string $errorCode,
    ) {}

    public static function sent(?string $id=null): self { return new self('sent',$id,null); }
    public static function transient(string $code): self { return new self('transient',null,$code); }
    public static function invalidToken(string $code='invalid_token'): self { return new self('invalid_token',null,$code); }
    public static function permanent(string $code): self { return new self('permanent',null,$code); }
}

interface PushProvider
{
    public function name(): string;
    public function send(PushMessage $message): PushSendResult;
}

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

interface PushHttpTransport
{
    public function request(string $method,string $url,array $headers,string $body): PushHttpResponse;
}

interface PushAccessTokenSource
{
    public function accessToken(): string;
}
