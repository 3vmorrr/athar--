<?php
declare(strict_types=1);
namespace Athar\Providers;
use Athar\Domain\QuranIndex;
use Athar\Http\ApiError;
final class Quranpedia
{
    private $fetch;
    public function __construct(callable $fetch){$this->fetch=$fetch;}
    public function books(array $params):array
    {
        $this->number($params,'surah',114);$raw=($this->fetch)('category/3/books');if(!is_array($raw))throw new ApiError('service_unavailable',502);$items=[];
        foreach($raw as $row)if(is_array($row)&&is_numeric($row['id']??null)&&is_string($row['name']??null)){$author=$row['author']??'';$items[]=['id'=>(int)$row['id'],'name'=>$row['name'],'author'=>is_array($author)?(string)($author['ar_name']??''):(is_string($author)?$author:'')];}
        $query=$params['q']??'';if(!is_string($query)||mb_strlen($query,'UTF-8')>80)throw new ApiError('invalid_fields',422,['q'=>'invalid']);if(trim($query)!=='')$items=array_values(array_filter($items,static fn(array $row):bool=>mb_stripos($row['name'].' '.$row['author'],trim($query),0,'UTF-8')!==false));
        [$page,$size]=$this->paging($params);
        return ['items'=>array_slice($items,($page-1)*$size,$size),'pagination'=>$this->pagination($page,$size,count($items)),'source'=>['name'=>'Quranpedia','url'=>'https://quranpedia.net']];
    }
    public function content(array $params):array
    {
        $surah=$this->number($params,'surah',114);$ayah=$this->number($params,'ayah',286);$book=$this->number($params,'book',9999999);[$page,$size]=$this->paging($params);QuranIndex::standard()->globalIndex($surah,$ayah);
        $listed=($this->fetch)('category/3/books');if(!is_array($listed))throw new ApiError('service_unavailable',502);$known=false;foreach($listed as $entry)if(is_array($entry)&&(int)($entry['id']??0)===$book){$known=true;break;}if(!$known)throw new ApiError('not_found',404);
        $path='ayah/'.$surah.'/'.$ayah.'/book/'.$book;$raw=($this->fetch)($path);
        if(!is_array($raw['book']??null)||!is_array($raw['content']??null))throw new ApiError('service_unavailable',502);
        if((int)($raw['book']['id']??0)!==$book)throw new ApiError('service_unavailable',502);
        $blocks=[];
        foreach($raw['content'] as $row)if(is_array($row)&&is_string($row['text']??null)){
            $text=$this->plain($row['text']);
            if($text==='')continue;
            $blocks[]=['text'=>$text,'part'=>(int)($row['part']??0),'page'=>(int)($row['page']??0),'references'=>(string)($row['ayahs']??'')];
        }
        if(!is_string($raw['book']['name']??null)||trim($raw['book']['name'])==='')throw new ApiError('service_unavailable',502);
        $author=$raw['book']['author']??null;
        // Preserve unpaged clients: explicit paging opts into bounded slices.
        if(!array_key_exists('page',$params)&&!array_key_exists('per_page',$params))$size=max(1,count($blocks));
        return ['surah'=>$surah,'ayah'=>$ayah,'book'=>['id'=>$book,'name'=>(string)($raw['book']['name']??''),'author'=>is_array($author)?(string)($author['full_name']??$author['ar_name']??''):(is_string($author)?$author:'')],'items'=>array_slice($blocks,($page-1)*$size,$size),'pagination'=>$this->pagination($page,$size,count($blocks)),'source'=>['name'=>'Quranpedia','url'=>'https://api.quranpedia.net/v1/'.$path,'retrieved_at'=>gmdate('c')]];
    }
    private function pagination(int $page, int $perPage, int $total): array
    {
        $pages = $total === 0 ? 0 : (int) ceil($total / $perPage);
        return [
            'page' => $page,
            'per_page' => $perPage,
            'total' => $total,
            'pages' => $pages,
            'has_more' => $pages > 0 && $page < $pages,
        ];
    }

    private function paging(array $params): array
    {
        return [
            $this->optionalNumber($params, 'page', 10000, 1),
            $this->optionalNumber($params, 'per_page', 50, 20),
        ];
    }

    private function plain(string $html):string
    {
        $html=preg_replace('#<(script|style)\b[^>]*>.*?</\1>#is','',$html)??'';
        $html=preg_replace('#<br\s*/?>|</(?:p|div|li|h[1-6])>#i',"\n",$html)??'';
        return trim(html_entity_decode(strip_tags($html),ENT_QUOTES|ENT_HTML5,'UTF-8'));
    }
    private function optionalNumber(
        array $params,
        string $key,
        int $max,
        int $default,
    ): int {
        if (!array_key_exists($key, $params)) {
            return $default;
        }
        return $this->number($params, $key, $max);
    }

    private function number(array $params,string $key,int $max):int{$value=$params[$key]??null;if((!is_int($value)&&!is_string($value))||!preg_match('/^[1-9][0-9]{0,6}$/',(string)$value)||(int)$value>$max)throw new ApiError('invalid_fields',422,[$key=>'invalid']);return (int)$value;}
}
