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
        if(trim($title)===''||strlen($title)>640)throw new \InvalidArgumentException('invalid_push_title');
        if(strlen($body)>2000)throw new \InvalidArgumentException('invalid_push_body');
        if($idempotencyKey===''||strlen($idempotencyKey)>160)throw new \InvalidArgumentException('invalid_push_dedupe');
        foreach($data as $key=>$value){
            if(!is_string($key)||!preg_match('/^[a-z0-9_]{1,64}$/',$key)||!is_scalar($value)){
                throw new \InvalidArgumentException('invalid_push_data');
            }
            if(strlen((string)$value)>1024)throw new \InvalidArgumentException('push_data_too_large');
        }
    }
}
