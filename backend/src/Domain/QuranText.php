<?php
declare(strict_types=1);
namespace Athar\Domain;
use InvalidArgumentException;
use RuntimeException;

final class QuranText
{
    private QuranIndex $index;
    private array $verses=[];
    private array $search=[];
    private array $starts=[];
    public function __construct(string $xmlPath,QuranIndex $index)
    {
        $this->index=$index;$xml=file_get_contents($xmlPath);
        if(!is_string($xml)||str_contains($xml,'<!DOCTYPE'))throw new RuntimeException('Quran text is unavailable.');
        $previous=libxml_use_internal_errors(true);
        try{$document=simplexml_load_string($xml,\SimpleXMLElement::class,LIBXML_NONET);if(!$document)throw new RuntimeException('Quran text could not be read.');}
        finally{libxml_clear_errors();libxml_use_internal_errors($previous);}
        $metadata=$index->metadata();
        foreach($metadata['sections'] as $kind=>$sections)foreach($sections as $section)$this->starts[$kind][]=$index->globalIndex((int)$section['surah'],(int)$section['ayah']);
        foreach($document->sura as $surah){$number=(int)$surah['index'];$reference=$metadata['surahs'][$number-1]??null;if(!$reference||count($surah->aya)!==$reference['ayah_count'])throw new RuntimeException('Quran text does not match the validated index.');
            foreach($surah->aya as $aya){$ayah=(int)$aya['index'];$global=$index->globalIndex($number,$ayah);$text=(string)$aya['text'];$this->verses[$global]=['global_index'=>$global,'surah'=>$number,'surah_name'=>$reference['name'],'ayah'=>$ayah,'text'=>$text,'bismillah'=>isset($aya['bismillah'])?(string)$aya['bismillah']:null,'page'=>$this->sectionNumber('page',$global),'juz'=>$this->sectionNumber('juz',$global),'hizb'=>(int)ceil($this->sectionNumber('hizb_quarter',$global)/4)];$this->search[$global]=self::normalize($text);}
        }
        if(count($this->verses)!==6236)throw new RuntimeException('Quran text is incomplete.');
    }
    public static function standard():self
    {
        static $instance;
        if(!$instance){$folder=dirname(__DIR__,3).'/shared/quran';$source=json_decode((string)file_get_contents($folder.'/SOURCE.json'),true,512,JSON_THROW_ON_ERROR);if(!hash_equals($source['sha256'],hash_file('sha256',$folder.'/tanzil-uthmani.xml')?:''))throw new RuntimeException('Quran text integrity check failed.');$instance=new self($folder.'/tanzil-uthmani.xml',QuranIndex::standard());}
        return $instance;
    }
    public function ayah(int $surah,int $ayah):array{return $this->verses[$this->index->globalIndex($surah,$ayah)];}
    public function read(array $params):array
    {
        $mode=(string)($params['mode']??'surah');$number=(int)($params['number']??$params['surah']??1);
        if($mode==='surah'){$start=$this->index->globalIndex($number,1);$end=$start+$this->index->ayahCount($number)-1;}
        elseif($mode==='ayah'){$start=$end=$this->index->globalIndex((int)($params['surah']??1),(int)($params['ayah']??1));}
        elseif($mode==='range'){$start=(int)($params['start_index']??0);$end=(int)($params['end_index']??0);$this->index->position($start);$this->index->position($end);if($end<$start)throw new InvalidArgumentException('range_end_before_start');}
        elseif(in_array($mode,['page','juz','hizb'],true)){
            $kind=$mode==='hizb'?'hizb_quarter':$mode;$step=$mode==='hizb'?4:1;$max=$mode==='hizb'?60:count($this->starts[$kind]);if($number<1||$number>$max)throw new InvalidArgumentException('invalid_quran_section');$offset=($number-1)*$step;$start=$this->starts[$kind][$offset];$end=isset($this->starts[$kind][$offset+$step])?$this->starts[$kind][$offset+$step]-1:6236;
        }else throw new InvalidArgumentException('invalid_reading_mode');
        $page=max(1,min(10000,(int)($params['page']??1)));$size=max(1,min(100,(int)($params['per_page']??50)));$total=$end-$start+1;$from=$start+($page-1)*$size;$items=[];
        for($i=$from;$i<=min($end,$from+$size-1);$i++)$items[]=$this->verses[$i];
        return ['items'=>$items,'scope'=>['mode'=>$mode,'number'=>$number,'start_index'=>$start,'end_index'=>$end,'verse_count'=>$total],'pagination'=>['page'=>$page,'per_page'=>$size,'total'=>$total,'has_more'=>$page*$size<$total],'source'=>$this->source()];
    }
    public function search(string $query,int $page=1,int $size=25):array
    {
        $normalized=self::normalize($query);if(mb_strlen($normalized,'UTF-8')<2||mb_strlen($query,'UTF-8')>80)throw new InvalidArgumentException('invalid_search_query');$matches=[];
        foreach($this->search as $global=>$text)if(mb_strpos($text,$normalized,0,'UTF-8')!==false)$matches[]=$global;
        $page=max(1,min(10000,$page));$size=max(1,min(50,$size));$slice=array_slice($matches,($page-1)*$size,$size);
        return ['items'=>array_map(fn(int $i):array=>$this->verses[$i],$slice),'pagination'=>['page'=>$page,'per_page'=>$size,'total'=>count($matches),'has_more'=>$page*$size<count($matches)],'source'=>$this->source()];
    }
    public function daily(string $date):array{if(!NightPrayer::validDate($date))throw new InvalidArgumentException('invalid_date');$index=(int)(hexdec(substr(hash('sha256',$date),0,8))%6236)+1;return ['ayah'=>$this->verses[$index],'date'=>$date,'source'=>$this->source()];}
    public function source():array{return ['name'=>'Tanzil Project','url'=>'https://tanzil.net/','version'=>'1.1','license'=>'Creative Commons Attribution 3.0; verbatim text only','terms_url'=>'https://tanzil.net/docs/text_license'];}
    public static function normalize(string $text):string
    {
        $text=preg_replace('/[\x{0610}-\x{061A}\x{064B}-\x{065F}\x{0670}\x{06D6}-\x{06ED}\x{0640}]/u','',$text)??$text;
        $text=strtr($text,['أ'=>'ا','إ'=>'ا','آ'=>'ا','ٱ'=>'ا','ى'=>'ي']);return trim(preg_replace('/\s+/u',' ',$text)??$text);
    }
    private function sectionNumber(string $kind,int $global):int
    {
        $low=0;$high=count($this->starts[$kind])-1;$answer=0;while($low<=$high){$mid=intdiv($low+$high,2);if($this->starts[$kind][$mid]<=$global){$answer=$mid;$low=$mid+1;}else $high=$mid-1;}return $answer+1;
    }
}
