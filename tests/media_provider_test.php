<?php
declare(strict_types=1);
require dirname(__DIR__).'/backend/bootstrap.php';
use Athar\Providers\Mp3Quran;
use Athar\Http\ApiError;
$checks=0;
function media_check(bool $value,string $name):void{global $checks;$checks++;if(!$value)throw new RuntimeException('FAIL: '.$name);}
function media_reject(callable $call,int $status,string $name):void{try{$call();}catch(ApiError $e){media_check($e->status===$status,$name);return;}media_check(false,$name);}
$timingDown=false;$invalidTiming=false;
$provider=new Mp3Quran(static function(string $path,array $query)use(&$timingDown,&$invalidTiming):array{
    if($path==='reciters')return ['reciters'=>[
        ['id'=>1,'name'=>'قارئ ألف','moshaf'=>[['id'=>10,'name'=>'حفص','server'=>'https://cdn.mp3quran.net/audio/reader/r1/','surah_list'=>'1,2,114'],['id'=>11,'name'=>'غير صالح','server'=>'https://attacker.invalid/','surah_list'=>'1']]],
        ['id'=>2,'name'=>'قارئ باء','moshaf'=>[['id'=>20,'name'=>'ورش','server'=>'https://server8.mp3quran.net/reader/','surah_list'=>'1']]],
    ]];
    if($path==='ayat_timing/reads'){if($timingDown)throw new ApiError('service_unavailable',502);return [['id'=>7,'folder_url'=>'https://cdn.mp3quran.net/audio/reader/r1/']];}
    if($path==='ayat_timing')return [['ayah'=>1,'start_time'=>0,'end_time'=>1000],['ayah'=>2,'start_time'=>$invalidTiming?-1:1000,'end_time'=>2000]];
    if($path==='radios')return ['radios'=>[['id'=>1,'name'=>'Radio','url'=>'https://backup.qurango.net/radio/example'],['id'=>2,'name'=>'Unsafe','url'=>'http://backup.qurango.net/radio/example']]];
    throw new RuntimeException('Unexpected provider path.');
});
media_check($provider->reciters(['surah'=>'114'])['pagination']['total']===1,'filter actual supported surah');
$catalog=$provider->reciters(['q'=>'باء']);media_check(count($catalog['items'])===1&&$catalog['items'][0]['id']===2,'Arabic reader search');
media_check(!isset($provider->reciters([])['items'][0]['editions'][0]['server']),'catalog hides raw server implementation');
$audio=$provider->audio(['surah'=>'1','reciter'=>'1','edition'=>'10']);media_check($audio['url']==='https://cdn.mp3quran.net/audio/reader/r1/001.mp3','recording tuple resolves exact provider URL');media_check($audio['timing_read']===7,'timing belongs to exact recording folder');
media_reject(fn()=>$provider->audio(['surah'=>'2','reciter'=>'2','edition'=>'20']),404,'unsupported reader-surah pair not fabricated');
media_reject(fn()=>$provider->audio(['surah'=>'1','reciter'=>'1','edition'=>'20']),404,'edition must belong to reader');
media_reject(fn()=>$provider->audio(['surah'=>'1','reciter'=>'https://evil.invalid','edition'=>'10']),422,'IDs cannot inject provider URLs');
$timing=$provider->timing(['surah'=>'1','reciter'=>'1','edition'=>'10']);media_check($timing['available']&&$timing['items'][1]['start_ms']===1000,'timing units preserved explicitly');
$invalidTiming=true;media_reject(fn()=>$provider->timing(['surah'=>'1','reciter'=>'1','edition'=>'10']),502,'invalid interval rejected');
$timingDown=true;media_check($provider->audio(['surah'=>'1','reciter'=>'1','edition'=>'10'])['tracking_available']===false,'timing outage does not block full audio');
media_check(count($provider->radios([])['items'])===1,'only approved HTTPS stations exposed');
media_check(!Mp3Quran::mediaUrl('https://mp3quran.net.evil.invalid/a.mp3'),'lookalike hostname rejected');
media_check(!Mp3Quran::mediaUrl('https://user:pass@server8.mp3quran.net/a.mp3'),'embedded credentials rejected');
media_check(!Mp3Quran::mediaUrl('https://127.0.0.1/a.mp3'),'local network address rejected');
echo 'PASS: '.$checks." provider catalog/recording/timing/URL-boundary assertions; injected fixtures only.\n";
