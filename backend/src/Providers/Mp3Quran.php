<?php
declare(strict_types=1);
namespace Athar\Providers;
use Athar\Domain\QuranIndex;
use Athar\Http\ApiError;

final class Mp3Quran
{
    private $fetch;
    public function __construct(callable $fetch){$this->fetch=$fetch;}
    private function language(array $params):string{$language=$params['language']??'ar';if(!in_array($language,['ar','en','eng'],true))throw new ApiError('invalid_fields',422,['language'=>'unsupported']);return $language==='en'?'eng':$language;}
    private function get(string $path,array $query=[]):array{return ($this->fetch)($path,$query);}
    public static function mediaUrl(string $url,bool $radio=false):bool
    {
        if(!filter_var($url,FILTER_VALIDATE_URL)||preg_match('/[\r\n]/',$url))return false;$parts=parse_url($url);$host=strtolower($parts['host']??'');
        return ($parts['scheme']??'')==='https'&&!isset($parts['user'])&&!isset($parts['pass'])&&!isset($parts['port'])&&(preg_match('/^(?:[a-z0-9-]+\.)*mp3quran\.net$/',$host)||($radio&&(preg_match('/^(?:[a-z0-9-]+\.)*qurango\.net$/',$host)||$host==='stream.radiojar.com')));
    }
    private function catalog(string $language):array
    {
        $payload=$this->get('reciters',['language'=>$language]);if(!is_array($payload['reciters']??null))throw new ApiError('service_unavailable',502);$items=[];
        foreach($payload['reciters'] as $reciter){
            if(!is_array($reciter)||!is_numeric($reciter['id']??null)||!is_string($reciter['name']??null))continue;$editions=[];
            foreach($reciter['moshaf']??[] as $edition){$server=(string)($edition['server']??'');if(!self::mediaUrl($server)||!is_numeric($edition['id']??null))continue;
                $surahs=array_values(array_unique(array_filter(array_map('intval',explode(',',(string)($edition['surah_list']??''))),static fn(int $n):bool=>$n>=1&&$n<=114)));
                if(!$surahs)continue;$editions[]=['id'=>(int)$edition['id'],'name'=>(string)($edition['name']??''),'surahs'=>$surahs,'server'=>rtrim($server,'/').'/'];
            }
            if($editions)$items[]=['id'=>(int)$reciter['id'],'name'=>$reciter['name'],'editions'=>$editions];
        }
        if(!$items)throw new ApiError('service_unavailable',502);return $items;
    }
    public function reciters(array $params):array
    {
        $language=$this->language($params);$items=$this->catalog($language);$q=trim((string)($params['q']??''));if(mb_strlen($q,'UTF-8')>80)throw new ApiError('invalid_fields',422,['q'=>'too_long']);
        if($q!=='')$items=array_values(array_filter($items,static fn(array $r):bool=>mb_stripos($r['name'],$q,0,'UTF-8')!==false));
        if(isset($params['surah'])){$surah=$this->number($params,'surah',114);$items=array_values(array_filter($items,static fn(array $r):bool=>(bool)array_filter($r['editions'],static fn(array $e):bool=>in_array($surah,$e['surahs'],true))));}
        foreach($items as &$reciter)foreach($reciter['editions'] as &$edition)unset($edition['server']);unset($reciter,$edition);
        return $this->page($items,$params)+['source'=>['name'=>'MP3Quran','url'=>'https://www.mp3quran.net/ar/api']];
    }
    public function audio(array $params):array
    {
        $surah=$this->number($params,'surah',114);$id=$this->number($params,'reciter',1000000);$editionId=$this->number($params,'edition',1000000);
        foreach($this->catalog($this->language($params)) as $reciter)if($reciter['id']===$id)foreach($reciter['editions'] as $edition)if($edition['id']===$editionId&&in_array($surah,$edition['surahs'],true)){
            $url=$edition['server'].str_pad((string)$surah,3,'0',STR_PAD_LEFT).'.mp3';$read=null;
            try{foreach($this->get('ayat_timing/reads') as $entry)if(is_array($entry)&&rtrim((string)($entry['folder_url']??''),'/')===rtrim($edition['server'],'/')){$read=(int)$entry['id'];break;}}catch(ApiError $ignored){}
            return ['url'=>$url,'reciter_id'=>$id,'reciter_name'=>$reciter['name'],'edition_id'=>$editionId,'edition_name'=>$edition['name'],'surah'=>$surah,'timing_read'=>$read,'tracking_available'=>$read!==null,'source'=>'MP3Quran'];
        }
        throw new ApiError('not_found',404);
    }
    public function timing(array $params):array
    {
        $audio=$this->audio($params);if(!$audio['timing_read'])return ['items'=>[],'available'=>false,'recording'=>$audio];
        $raw=$this->get('ayat_timing',['surah'=>$audio['surah'],'read'=>$audio['timing_read']]);$items=[];$previous=-1;
        foreach($raw as $row){if(!is_array($row)||!isset($row['ayah'],$row['start_time'],$row['end_time']))continue;$ayah=(int)$row['ayah'];$start=(int)$row['start_time'];$end=(int)$row['end_time'];
            if($ayah<1||$ayah>QuranIndex::standard()->ayahCount($audio['surah']))continue;
            if($start<0||$end<=$start||$start<$previous)throw new ApiError('service_unavailable',502);$previous=$start;$items[]=['ayah'=>$ayah,'start_ms'=>$start,'end_ms'=>$end];
        }
        return ['items'=>$items,'available'=>(bool)$items,'recording'=>$audio];
    }
    public function radios(array $params):array
    {
        $raw=$this->get('radios',['language'=>$this->language($params)]);if(!is_array($raw['radios']??null))throw new ApiError('service_unavailable',502);$items=[];
        foreach($raw['radios'] as $row)if(is_array($row)&&self::mediaUrl((string)($row['url']??''),true))$items[]=['id'=>(int)$row['id'],'name'=>(string)$row['name'],'url'=>$row['url']];
        return $this->page($items,$params)+['source'=>'MP3Quran'];
    }
    private function page(array $items,array $params):array{$page=max(1,min(10000,(int)($params['page']??1)));$size=max(1,min(50,(int)($params['per_page']??20)));return ['items'=>array_slice($items,($page-1)*$size,$size),'pagination'=>['page'=>$page,'per_page'=>$size,'total'=>count($items),'has_more'=>$page*$size<count($items)]];}
    private function number(array $params,string $key,int $max):int{$value=$params[$key]??null;if(!is_scalar($value)||!preg_match('/^[1-9][0-9]{0,6}$/',(string)$value)||(int)$value>$max)throw new ApiError('invalid_fields',422,[$key=>'invalid']);return (int)$value;}
}
