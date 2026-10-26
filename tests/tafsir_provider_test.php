<?php
declare(strict_types=1);
require dirname(__DIR__).'/backend/bootstrap.php';
use Athar\Providers\Quranpedia;
use Athar\Http\ApiError;
$checks=0;
function tafsir_check(bool $value,string $name):void{global $checks;$checks++;if(!$value)throw new RuntimeException('FAIL: '.$name);}
function tafsir_reject(callable $call,int $status,string $name):void{try{$call();}catch(ApiError $e){tafsir_check($e->status===$status,$name);return;}catch(InvalidArgumentException $e){tafsir_check($status===422,$name);return;}tafsir_check(false,$name);}
$calls=[];$provider=new Quranpedia(static function(string $path)use(&$calls):array{
    $calls[]=$path;
    if($path==='category/3/books')return [['id'=>5,'name'=>'Reviewed Tafsir','author'=>'Author']];
    if($path==='ayah/1/1/book/5')return ['book'=>['id'=>5,'name'=>'Reviewed Tafsir','author'=>['full_name'=>'Author']],'content'=>[['text'=>'<p>First &amp; second</p><script>discard()</script><p>Next paragraph</p>','part'=>1,'page'=>2,'ayahs'=>'1:1']]];
    throw new RuntimeException('Unexpected provider request.');
});
tafsir_check($provider->books(['surah'=>1])['pagination']['total']===1,'book catalog adapted');
$content=$provider->content(['surah'=>1,'ayah'=>1,'book'=>5]);tafsir_check($content['book']['author']==='Author','actual author preserved');
tafsir_check($content['items'][0]['text']==="First & second\nNext paragraph",'paragraphs and entities retained without executable markup');
tafsir_check($content['items'][0]['page']===2&&$content['items'][0]['references']==='1:1','page and ayah reference retained');
tafsir_check($content['source']['url']==='https://api.quranpedia.net/v1/ayah/1/1/book/5','direct source attribution');
tafsir_reject(fn()=>$provider->content(['surah'=>1,'ayah'=>1,'book'=>6]),404,'non-tafsir/unlisted book cannot be relabeled');
tafsir_reject(fn()=>$provider->content(['surah'=>114,'ayah'=>7,'book'=>5]),422,'invalid canonical ayah rejected before provider');
tafsir_reject(fn()=>$provider->content(['surah'=>'https://evil.invalid','ayah'=>1,'book'=>5]),422,'no injectable origin/path');
echo 'PASS: '.$checks." tafsir catalog/source/reference/markup/canonical-boundary assertions.\n";
