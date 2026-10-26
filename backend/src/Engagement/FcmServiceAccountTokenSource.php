<?php
declare(strict_types=1);

namespace Athar\Engagement;

final class FcmServiceAccountTokenSource implements PushAccessTokenSource
{
    private ?string $cachedToken=null;
    private int $cachedUntil=0;

    public function __construct(
        private string $credentialFile,
        private PushHttpTransport $http,
        private int $clockSkewSeconds=60,
    ) {
        if($credentialFile===''||$credentialFile[0]!=='/')throw new \InvalidArgumentException('invalid_fcm_credential_path');
    }

    public function accessToken(): string
    {
        $now=time();
        if($this->cachedToken!==null&&$now<$this->cachedUntil-$this->clockSkewSeconds)return $this->cachedToken;
        $credential=$this->readCredential();
        $header=$this->b64(json_encode(['alg'=>'RS256','typ'=>'JWT'],JSON_THROW_ON_ERROR));
        $claims=$this->b64(json_encode([
            'iss'=>$credential['client_email'],
            'scope'=>'https://www.googleapis.com/auth/firebase.messaging',
            'aud'=>'https://oauth2.googleapis.com/token',
            'iat'=>$now,
            'exp'=>$now+3600,
        ],JSON_UNESCAPED_SLASHES|JSON_THROW_ON_ERROR));
        $unsigned=$header.'.'.$claims;
        $signature='';
        if(!openssl_sign($unsigned,$signature,$credential['private_key'],OPENSSL_ALGO_SHA256)){
            throw new \RuntimeException('fcm_jwt_sign_failed');
        }
        $assertion=$unsigned.'.'.$this->b64($signature);
        $body=http_build_query([
            'grant_type'=>'urn:ietf:params:oauth:grant-type:jwt-bearer',
            'assertion'=>$assertion,
        ],'','&',PHP_QUERY_RFC3986);
        $reply=$this->http->request('POST','https://oauth2.googleapis.com/token',[
            'Content-Type'=>'application/x-www-form-urlencoded',
            'Accept'=>'application/json',
        ],$body);
        if($reply->status<200||$reply->status>=300)throw new \RuntimeException('fcm_oauth_http_'.$reply->status);
        $json=$reply->json();
        $token=$json['access_token']??null;$expires=$json['expires_in']??null;
        if(!is_string($token)||strlen($token)<20||!is_int($expires)&&!ctype_digit((string)$expires)){
            throw new \RuntimeException('fcm_oauth_invalid_response');
        }
        $ttl=max(60,min(3600,(int)$expires));
        $this->cachedToken=$token;$this->cachedUntil=$now+$ttl;
        return $token;
    }

    private function readCredential(): array
    {
        $raw=file_get_contents($this->credentialFile);
        if($raw===false)throw new \RuntimeException('fcm_credential_read_failed');
        $json=json_decode($raw,true,32,JSON_THROW_ON_ERROR);
        if(!is_array($json)||($json['type']??null)!=='service_account')throw new \RuntimeException('fcm_credential_type_invalid');
        $email=$json['client_email']??null;$key=$json['private_key']??null;$uri=$json['token_uri']??null;
        if(!is_string($email)||!filter_var($email,FILTER_VALIDATE_EMAIL))throw new \RuntimeException('fcm_client_email_invalid');
        if(!is_string($key)||!str_contains($key,'BEGIN PRIVATE KEY'))throw new \RuntimeException('fcm_private_key_invalid');
        if($uri!=='https://oauth2.googleapis.com/token')throw new \RuntimeException('fcm_token_uri_invalid');
        return ['client_email'=>$email,'private_key'=>$key];
    }

    private function b64(string $value): string
    {
        return rtrim(strtr(base64_encode($value),'+/','-_'),'=');
    }
}
