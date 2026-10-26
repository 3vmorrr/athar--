<?php
declare(strict_types=1);
namespace Athar\Engagement;
interface PushProvider
{
    public function name(): string;
    public function send(PushMessage $message): PushSendResult;
}
