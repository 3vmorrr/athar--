<?php
declare(strict_types=1);
use Athar\Providers\Mp3Quran;
require __DIR__.'/media_provider_test.php';

$stations=[];
for($id=1;$id<=60;$id++)$stations[]=['id'=>$id,'name'=>'Station '.$id,'url'=>'https://backup.qurango.net/radio/'.$id];
$rows=[['ayah'=>2,'start_time'=>1000,'end_time'=>2000],['ayah'=>1,'start_time'=>0,'end_time'=>1000]];
$catalogRows=[['id'=>1,'name'=>'Reader','moshaf'=>[['id'=>10,'server'=>'https://server8.mp3quran.net/reader/','surah_list'=>'1,1,2x,114']]]];
$catalogProvider=new Mp3Quran(static function(string $path,array $query)use(&$stations,&$rows,&$catalogRows):array{
    if($path==='radios')return ['radios'=>$stations];
    if($path==='reciters')return ['reciters'=>$catalogRows];
    if($path==='ayat_timing/reads')return [['id'=>7,'folder_url'=>'https://server8.mp3quran.net/reader/']];
    if($path==='ayat_timing')return $rows;
    throw new RuntimeException('Unexpected fixture path');
});
$result=$catalogProvider->radios(['q'=>'Station 45','per_page'=>'25']);
media_check($result['pagination']['total']===1&&$result['items'][0]['id']===45,'search filters full catalog before pagination');
media_check($catalogProvider->radios(['page'=>'3','per_page'=>'25'])['items'][0]['id']===51,'all catalog pages reachable');
foreach([['q'=>[]],['q'=>str_repeat('a',81)],['page'=>true],['page'=>'0'],['per_page'=>'51']] as $invalid)media_reject(fn()=>$catalogProvider->radios($invalid),422,'malformed search or page rejected');
$stations[]=$stations[0];
media_check($catalogProvider->radios([])['pagination']['total']===60,'identical duplicate station collapsed');
$stations[]=['id'=>1,'name'=>'Conflicting station','url'=>'https://backup.qurango.net/radio/other'];
$stations[]=['id'=>true,'name'=>'Boolean ID','url'=>'https://backup.qurango.net/radio/bad'];
media_check($catalogProvider->radios([])['pagination']['total']===59,'ambiguous and malformed station identities excluded');
media_check($catalogProvider->reciters([])['items'][0]['editions'][0]['surahs']===[1,114],'malformed surah token never becomes valid number');
$tuple=['surah'=>'1','reciter'=>'1','edition'=>'10'];
media_check($catalogProvider->timing($tuple)['items'][0]['ayah']===1,'provider row order normalized before interval checks');
foreach([
    [['ayah'=>1,'start_time'=>0,'end_time'=>1000],['ayah'=>1,'start_time'=>1000,'end_time'=>2000]],
    [['ayah'=>1,'start_time'=>0,'end_time'=>1500],['ayah'=>2,'start_time'=>1000,'end_time'=>2000]],
    [['ayah'=>1,'start_time'=>0,'end_time'=>86400001]],
    [['ayah'=>8,'start_time'=>0,'end_time'=>1000]],
    [['ayah'=>1,'start_time'=>false,'end_time'=>1000]],
] as $invalidRows){$rows=$invalidRows;media_reject(fn()=>$catalogProvider->timing($tuple),502,'ambiguous or invalid ayah timing rejected');}
echo 'PASS: '.$checks." combined media assertions; injected fixtures only.\n";
