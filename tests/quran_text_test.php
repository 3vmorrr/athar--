<?php
declare(strict_types=1);
require dirname(__DIR__).'/backend/bootstrap.php';
use Athar\Domain\QuranText;
use Athar\Domain\QuranIndex;
$checks=0;function assert_text(bool $v,string $name):void{global $checks;$checks++;if(!$v)throw new RuntimeException('FAIL: '.$name);}
$q=QuranText::standard();$index=QuranIndex::standard();$xml=simplexml_load_file(dirname(__DIR__).'/shared/quran/tanzil-uthmani.xml');
foreach($xml->sura as $surah)foreach($surah->aya as $aya){$item=$q->ayah((int)$surah['index'],(int)$aya['index']);assert_text($item['text']===(string)$aya['text'],'display text is byte-preserved');assert_text($item['page']>=1&&$item['page']<=604&&$item['juz']>=1&&$item['juz']<=30&&$item['hizb']>=1&&$item['hizb']<=60,'section bounds');}
assert_text($q->read(['mode'=>'page','number'=>604])['scope']['end_index']===6236,'last Quran page');
assert_text($q->read(['mode'=>'hizb','number'=>60])['scope']['end_index']===6236,'last hizb');
assert_text($q->read(['mode'=>'juz','number'=>30])['scope']['end_index']===6236,'last juz');
assert_text($q->read(['mode'=>'range','start_index'=>7,'end_index'=>8])['scope']['verse_count']===2,'cross-surah reader range');
assert_text($q->search('الحمد لله')['pagination']['total']>0,'normalized Arabic search');
assert_text($q->daily('2026-10-25')['ayah']===$q->daily('2026-10-25')['ayah'],'deterministic daily verse');
foreach($index->metadata()['sections'] as $mode=>$sections){$previous=0;foreach($sections as $section){$global=$index->globalIndex((int)$section['surah'],(int)$section['ayah']);assert_text($global>$previous,'section references strictly increasing');$previous=$global;}}
echo 'PASS: '.$checks." verbatim Quran text, canonical sections, Arabic search and daily-selection assertions.\n";
