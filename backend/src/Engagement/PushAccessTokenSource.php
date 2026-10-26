<?php
declare(strict_types=1);
namespace Athar\Engagement;
interface PushAccessTokenSource
{
    public function accessToken(): string;
}
