<?php
declare(strict_types=1);
namespace Athar\Engagement;
final class PushProviderFactory
{
    public static function fromConfig(PushProviderConfig $config): PushProvider
    {
        if($config->provider==='disabled')return new DisabledPushProvider();
        $http=new CurlPushHttpTransport();
        $credential=$config->credentialPath();
        $tokens=new FcmServiceAccountTokenSource($credential,$http);
        return new FcmV1PushProvider((string)$config->projectId,$tokens,$http);
    }
}
