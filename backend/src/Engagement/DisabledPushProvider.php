<?php
declare(strict_types=1);
namespace Athar\Engagement;
final class DisabledPushProvider implements PushProvider
{
    public function name(): string { return 'disabled'; }
    public function send(PushMessage $message): PushSendResult { return PushSendResult::permanent('provider_disabled'); }
}
