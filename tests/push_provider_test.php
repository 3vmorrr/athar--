<?php
declare(strict_types=1);
require dirname(__DIR__).'/backend/bootstrap.php';

use Athar\Engagement\FcmV1PushProvider;
use Athar\Engagement\PushAccessTokenSource;
use Athar\Engagement\PushHttpResponse;
use Athar\Engagement\PushHttpTransport;
use Athar\Engagement\PushMessage;
use DateTimeImmutable;
use DateTimeZone;

$checks=0;
function pp_assert(bool $value,string $label):void{global $checks;$checks++;if(!$value)throw new RuntimeException('FAIL: '.$label);}

final class FixtureToken implements PushAccessTokenSource {
    public function accessToken(): string { return str_repeat('a',32); }
}
final class FixtureHttp implements PushHttpTransport {
    public array $requests=[];
    public function __construct(public PushHttpResponse $response){}
    public function request(string $method,string $url,array $headers,string $body): PushHttpResponse {
        $this->requests[]=compact('method','url','headers','body');
        return $this->response;
    }
}
function message():PushMessage{
    return new PushMessage(
        token:'token_12345678901234567890',
        category:'lesson_reminders',
        title:'Lesson',
        body:'Starts soon',
        data:[
            'version'=>'1','owner_id'=>'7','environment'=>'012345abcdef',
            'destination'=>'lesson:42'
        ],
        expiresAt:(new DateTimeImmutable('now',new DateTimeZone('UTC')))->modify('+10 minutes'),
        idempotencyKey:'lesson-reminder-fixture-42',
    );
}

$http=new FixtureHttp(new PushHttpResponse(200,'{"name":"projects/demo/messages/1"}'));
$provider=new FcmV1PushProvider('athar-demo',$tokens=new FixtureToken(),$http);
$result=$provider->send(message());
pp_assert($result->status==='sent','fake transport success classified sent');
pp_assert(count($http->requests)===1,'provider performs one request');
$request=$http->requests[0];
pp_assert($request['url']==='https://fcm.googleapis.com/v1/projects/athar-demo/messages:send','endpoint is project-scoped https');
$payload=json_decode($request['body'],true,32,JSON_THROW_ON_ERROR);
pp_assert($payload['message']['token']==='token_12345678901234567890','token only appears in server request');
pp_assert($payload['message']['data']['destination']==='lesson:42','allowlisted destination carried as data');
pp_assert(!isset($payload['message']['notification']),'provider uses data envelope for app-side validation');

$retry=new FcmV1PushProvider('athar-demo',new FixtureToken(),new FixtureHttp(new PushHttpResponse(429,'{"error":{"status":"RESOURCE_EXHAUSTED"}}')));
pp_assert($retry->send(message())->status==='transient','429 is retryable');

$invalidBody='{"error":{"status":"NOT_FOUND","details":[{"errorCode":"UNREGISTERED"}]}}';
$invalid=new FcmV1PushProvider('athar-demo',new FixtureToken(),new FixtureHttp(new PushHttpResponse(404,$invalidBody)));
pp_assert($invalid->send(message())->status==='invalid_token','unregistered token is terminal token failure');

$auth=new FcmV1PushProvider('athar-demo',new FixtureToken(),new FixtureHttp(new PushHttpResponse(401,'{"error":{"status":"UNAUTHENTICATED"}}')));
pp_assert($auth->send(message())->errorCode==='provider_auth','provider credential failure is not confused with token failure');

pp_assert(runCatching(fn()=>new PushMessage(
    'short','lesson_reminders','T','B',[],new DateTimeImmutable('+1 hour'),'dedupe-key-123456'
))->isFailure(),'invalid token rejected before transport');

echo 'PASS: '.$checks." push provider abstraction/fake transport/retry/token-classification assertions.\n";
