<?php
declare(strict_types=1);
namespace Athar\Infrastructure;
use Athar\Http\ApiError;

/** Public catalog JSON only. No user-supplied origin, redirects, credentials or media proxy. */
final class PublicProviderJson
{
    private string $directory;
    public function __construct(string $directory){$this->directory=$directory;}
    public function mp3(string $path,array $query=[]):array
    {
        if(!in_array($path,['reciters','radios','ayat_timing/reads','ayat_timing'],true))throw new \InvalidArgumentException('Unsupported provider path.');
        $url='https://www.mp3quran.net/api/v3/'.$path.($query?'?'.http_build_query($query):'');
        return $this->fetch($url);
    }
    public function quranpedia(string $path):array
    {
        if(!preg_match('#^(?:category/3/books|surah/tafsirs/[1-9][0-9]{0,2}|ayah/[1-9][0-9]{0,2}/[1-9][0-9]{0,2}/book/[1-9][0-9]{0,6})$#',$path))throw new \InvalidArgumentException('Unsupported provider path.');
        return $this->fetch('https://api.quranpedia.net/v1/'.$path);
    }
    private function fetch(string $url):array
    {
        if(!is_dir($this->directory)&&!mkdir($this->directory,0700,true)&&!is_dir($this->directory))throw new ApiError('service_unavailable',503);
        $cache=$this->directory.DIRECTORY_SEPARATOR.hash('sha256',$url).'.json';
        $cached=is_file($cache)?file_get_contents($cache):false;
        if(is_string($cached)&&filemtime($cache)>time()-3600){$decoded=json_decode($cached,true);if(is_array($decoded))return $decoded;}
        $context=stream_context_create(['http'=>['timeout'=>12,'follow_location'=>0,'max_redirects'=>0,'ignore_errors'=>true,'header'=>"Accept: application/json\r\nUser-Agent: Athar/1.0\r\n"],'ssl'=>['verify_peer'=>true,'verify_peer_name'=>true]]);
        $stream=@fopen($url,'rb',false,$context);
        if(!$stream)throw new ApiError('service_unavailable',502);
        try{
            $headers=stream_get_meta_data($stream)['wrapper_data']??[];
            if(!isset($headers[0])||!preg_match('#^HTTP/\S+ 200\b#',$headers[0]))throw new ApiError('service_unavailable',502);
            $raw=stream_get_contents($stream,4*1024*1024+1);
            if(!is_string($raw)||strlen($raw)>4*1024*1024||stream_get_meta_data($stream)['timed_out'])throw new ApiError('service_unavailable',502);
            $data=json_decode($raw,true);if(!is_array($data))throw new ApiError('service_unavailable',502);
        }finally{fclose($stream);}
        $temporary=$cache.'.'.bin2hex(random_bytes(6)).'.tmp';
        if(file_put_contents($temporary,$raw,LOCK_EX)!==false){@chmod($temporary,0600);if(!@rename($temporary,$cache))@unlink($temporary);}
        return $data;
    }
}
