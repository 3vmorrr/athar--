<?php
declare(strict_types=1);
namespace Athar\Engagement;
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
