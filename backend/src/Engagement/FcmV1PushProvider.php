<?php
declare(strict_types=1);

namespace Athar\Engagement;

use DateTimeImmutable;
use DateTimeZone;

final class FcmV1PushProvider implements PushProvider
{
    public function __construct(
        private string $projectId,
        private PushAccessTokenSource $tokens,
        private PushHttpTransport $http,
    ) {
        if(!preg_match('/^[a-z][a-z0-9-]{4,62}$/',$projectId))throw new \InvalidArgumentException('invalid_fcm_project');
    }

    public function name(): string { return 'fcm_v1'; }

    public function send(PushMessage $message): PushSendResult
    {
        $now=new DateTimeImmutable('now',new DateTimeZone('UTC'));
        if($message->expiresAt<=$now)return PushSendResult::permanent('expired');
        $ttl=max(0,$message->expiresAt->getTimestamp()-$now->getTimestamp());
        $data=[];
        foreach($message->data as $key=>$value)$data[$key]=(string)$value;
        $data['category']=$message->category;
        $data['title']=$message->title;
        $data['body']=$message->body;
        $data['dedupe_key']=$message->idempotencyKey;
        $data['expires_at_ms']=(string)($message->expiresAt->getTimestamp()*1000);

        $payload=['message'=>[
            'token'=>$message->token,
            'data'=>$data,
            'android'=>[
                'priority'=>'normal',
                'ttl'=>$ttl.'s',
            ],
        ]];
        try{
            $token=$this->tokens->accessToken();
            $reply=$this->http->request(
                'POST',
                'https://fcm.googleapis.com/v1/projects/'.$this->projectId.'/messages:send',
                [
                    'Authorization'=>'Bearer '.$token,
                    'Content-Type'=>'application/json; charset=UTF-8',
                    'Accept'=>'application/json',
                ],
                json_encode($payload,JSON_UNESCAPED_SLASHES|JSON_THROW_ON_ERROR)
            );
        }catch(\Throwable $failure){
            return PushSendResult::transient('transport_unavailable');
        }

        if($reply->status>=200&&$reply->status<300){
            try{$json=$reply->json();$id=is_string($json['name']??null)?$json['name']:null;}
            catch(\Throwable $ignored){$id=null;}
            return PushSendResult::sent($id);
        }

        $error=$this->providerError($reply);
        if(in_array($error,['UNREGISTERED','SENDER_ID_MISMATCH'],true))return PushSendResult::invalidToken(strtolower($error));
        if(in_array($reply->status,[408,425,429],true)||$reply->status>=500)return PushSendResult::transient('fcm_http_'.$reply->status);
        if(in_array($reply->status,[401,403],true))return PushSendResult::permanent('provider_auth');
        return PushSendResult::permanent($error!==null?'fcm_'.strtolower($error):'fcm_http_'.$reply->status);
    }

    private function providerError(PushHttpResponse $reply): ?string
    {
        try{
            $json=$reply->json();
            $details=$json['error']['details']??[];
            if(is_array($details)){
                foreach($details as $detail){
                    if(is_array($detail)&&is_string($detail['errorCode']??null))return $detail['errorCode'];
                }
            }
            $status=$json['error']['status']??null;
            return is_string($status)?$status:null;
        }catch(\Throwable $ignored){return null;}
    }
}

final class DisabledPushProvider implements PushProvider
{
    public function name(): string { return 'disabled'; }
    public function send(PushMessage $message): PushSendResult { return PushSendResult::permanent('provider_disabled'); }
}

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
