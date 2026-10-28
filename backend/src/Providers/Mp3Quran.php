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
    private function get(string $path,array $query=[]):array{$value=($this->fetch)($path,$query);if(!is_array($value))throw new ApiError('service_unavailable',502);return $value;}
    public static function mediaUrl(string $url,bool $radio=false):bool
    {
        if(!filter_var($url,FILTER_VALIDATE_URL)||preg_match('/[\r\n]/',$url))return false;$parts=parse_url($url);$host=strtolower($parts['host']??'');
        return ($parts['scheme']??'')==='https'&&!isset($parts['user'])&&!isset($parts['pass'])&&!isset($parts['port'])&&(preg_match('/^(?:[a-z0-9-]+\.)*mp3quran\.net$/',$host)||($radio&&(preg_match('/^(?:[a-z0-9-]+\.)*qurango\.net$/',$host)||$host==='stream.radiojar.com')));
    }
    private function catalog(string $language):array
    {
        $payload=$this->get('reciters',['language'=>$language]);if(!is_array($payload['reciters']??null))throw new ApiError('service_unavailable',502);$items=[];
        foreach($payload['reciters'] as $reciter){
            if(!is_array($reciter)||!is_string($reciter['name']??null)||trim($reciter['name'])==='')continue;
            $reciterId=$this->providerId($reciter['id']??null,1000000);if($reciterId===null||!is_array($reciter['moshaf']??null))continue;
            $editions=[];
            foreach($reciter['moshaf'] as $edition){
                if(!is_array($edition)||!is_string($edition['server']??null))continue;
                $server=$edition['server'];$editionId=$this->providerId($edition['id']??null,1000000);
                if(!self::mediaUrl($server)||$editionId===null)continue;
                $list=$edition['surah_list']??null;if(!is_string($list)&&!is_int($list))continue;
                $surahs=[];foreach(explode(',',(string)$list) as $token){$token=trim($token);if(preg_match('/^[0-9]{1,3}$/',$token)&&(int)$token>=1&&(int)$token<=114)$surahs[]=(int)$token;}
                $surahs=array_values(array_unique($surahs));sort($surahs);
                if(!$surahs)continue;
                $editions[]=['id'=>$editionId,'name'=>is_string($edition['name']??null)?trim($edition['name']):'','surahs'=>$surahs,'server'=>rtrim($server,'/').'/'];
            }
            $editions=$this->uniqueRows($editions);
            if($editions)$items[]=['id'=>$reciterId,'name'=>trim($reciter['name']),'editions'=>$editions];
        }
        $items=$this->uniqueRows($items);
        if(!$items)throw new ApiError('service_unavailable',502);return $items;
    }
    public function reciters(array $params):array
    {
        $language=$this->language($params);$items=$this->catalog($language);$q=$this->searchQuery($params);
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
            try{foreach($this->get('ayat_timing/reads') as $entry)if(is_array($entry)&&is_string($entry['folder_url']??null)&&rtrim($entry['folder_url'],'/')===rtrim($edition['server'],'/')&&$this->providerId($entry['id']??null,1000000)!==null){$read=(int)$entry['id'];break;}}catch(ApiError $ignored){}
            return ['url'=>$url,'reciter_id'=>$id,'reciter_name'=>$reciter['name'],'edition_id'=>$editionId,'edition_name'=>$edition['name'],'surah'=>$surah,'timing_read'=>$read,'tracking_available'=>$read!==null,'source'=>'MP3Quran'];
        }
        throw new ApiError('not_found',404);
    }
    public function timing(array $params):array
    {
        $audio=$this->audio($params);if(!$audio['timing_read'])return ['items'=>[],'available'=>false,'recording'=>$audio];
        $raw=$this->get('ayat_timing',['surah'=>$audio['surah'],'read'=>$audio['timing_read']]);$items=[];$seen=[];
        foreach($raw as $row){
            if(!is_array($row)||!isset($row['ayah'],$row['start_time'],$row['end_time']))throw new ApiError('service_unavailable',502);
            $ayah=$this->milliseconds($row['ayah']);$start=$this->milliseconds($row['start_time']);$end=$this->milliseconds($row['end_time']);
            if($ayah===0)continue; // An unnumbered introduction is never attributed to an ayah.
            if($ayah<1||$ayah>QuranIndex::standard()->ayahCount($audio['surah'])||isset($seen[$ayah])||$end<=$start)throw new ApiError('service_unavailable',502);
            $seen[$ayah]=true;$items[]=['ayah'=>$ayah,'start_ms'=>$start,'end_ms'=>$end];
        }
        usort($items,static fn(array $a,array $b):int=>$a['ayah']<=>$b['ayah']);
        $previousEnd=0;foreach($items as $item){if($item['start_ms']<$previousEnd)throw new ApiError('service_unavailable',502);$previousEnd=$item['end_ms'];}
        return ['items'=>$items,'available'=>(bool)$items,'recording'=>$audio];
    }
    public function radios(array $params):array
    {
        $query=$this->searchQuery($params);
        $raw=$this->get('radios',['language'=>$this->language($params)]);if(!is_array($raw['radios']??null))throw new ApiError('service_unavailable',502);$items=[];
        foreach($raw['radios'] as $row){
            if(!is_array($row)||!is_string($row['url']??null)||!is_string($row['name']??null)||!self::mediaUrl($row['url'],true))continue;
            $id=$this->providerId($row['id']??null,9999999);$name=trim($row['name']);
            if($id===null||$name===''||mb_strlen($name,'UTF-8')>300)continue;
            $items[]=['id'=>$id,'name'=>$name,'url'=>$row['url']];
        }
        $items=$this->uniqueRows($items);
        if($query!=='')$items=array_values(array_filter($items,static fn(array $row):bool=>mb_stripos($row['name'],$query,0,'UTF-8')!==false));
        return $this->page($items,$params)+['source'=>'MP3Quran'];
    }
    private function searchQuery(array $params):string
    {
        $query=$params['q']??'';
        if(!is_string($query)||mb_strlen($query,'UTF-8')>80)throw new ApiError('invalid_fields',422,['q'=>'invalid']);
        return trim($query);
    }
    private function providerId($value,int $max):?int
    {
        return (is_int($value)||is_string($value))&&preg_match('/^[1-9][0-9]{0,6}$/',(string)$value)&&(int)$value<=$max?(int)$value:null;
    }
    private function uniqueRows(array $rows):array
    {
        $unique=[];$ambiguous=[];
        foreach($rows as $row){$id=$row['id'];if(isset($ambiguous[$id]))continue;if(isset($unique[$id])&&$unique[$id]!==$row){unset($unique[$id]);$ambiguous[$id]=true;}else $unique[$id]=$row;}
        return array_values($unique);
    }
    private function milliseconds($value):int
    {
        if((!is_int($value)&&!is_string($value))||!preg_match('/^[0-9]{1,9}$/',(string)$value)||(int)$value>86400000)throw new ApiError('service_unavailable',502);
        return (int)$value;
    }
    private function page(array $items,array $params):array{$page=array_key_exists('page',$params)?$this->number($params,'page',10000):1;$size=array_key_exists('per_page',$params)?$this->number($params,'per_page',50):20;return ['items'=>array_slice($items,($page-1)*$size,$size),'pagination'=>['page'=>$page,'per_page'=>$size,'total'=>count($items),'has_more'=>$page*$size<count($items)]];}
    private function number(array $params,string $key,int $max):int{$value=$params[$key]??null;if((!is_int($value)&&!is_string($value))||!preg_match('/^[1-9][0-9]{0,6}$/',(string)$value)||(int)$value>$max)throw new ApiError('invalid_fields',422,[$key=>'invalid']);return (int)$value;}
}
