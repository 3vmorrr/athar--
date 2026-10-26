<?php
declare(strict_types=1);

/** Controlled asset paths only; never accept archive paths as filesystem paths. */
function backup_asset_keys(string $kind,array $rows):array
{
    $column=$kind==='books'?'file_key':($kind==='scholars'?'image_key':null);
    if($column===null)throw new InvalidArgumentException('Unsupported backup asset kind.');
    $pattern=$kind==='books'?'/^[a-f0-9]{40}\.pdf$/':'/^[a-f0-9]{40}\.(?:jpg|png|webp)$/';
    $keys=[];
    foreach($rows as $row){$key=$row[$column]??null;if($key===null||$key==='')continue;if(!is_string($key)||!preg_match($pattern,$key))throw new RuntimeException('Invalid registered asset key; backup stopped.');$keys[$key]=true;}
    return array_keys($keys);
}

function backup_add_assets(ZipArchive $zip,string $storage,string $kind,array $keys):array
{
    $manifest=[];
    foreach($keys as $key){
        backup_asset_keys($kind,[[$kind==='books'?'file_key':'image_key'=>$key]]);
        $path=$storage.DIRECTORY_SEPARATOR.$kind.DIRECTORY_SEPARATOR.$key;
        if(!is_file($path)||is_link($path)||!$zip->addFile($path,'assets/'.$kind.'/'.$key))throw new RuntimeException('A registered backup asset is unavailable.');
        $manifest[]=['key'=>$key,'sha256'=>hash_file('sha256',$path),'bytes'=>filesize($path)];
    }
    return $manifest;
}

function backup_read_assets(ZipArchive $zip,string $kind,array $manifest,array $expectedKeys):array
{
    $assets=[];
    foreach($manifest as $asset){
        $key=$asset['key']??null;backup_asset_keys($kind,[[$kind==='books'?'file_key':'image_key'=>$key]]);
        if(!is_string($key)||$key===''||isset($assets[$key])||!is_int($asset['bytes']??null)||$asset['bytes']<1||$asset['bytes']>100*1024*1024||!is_string($asset['sha256']??null))throw new RuntimeException('Invalid asset manifest.');
        $bytes=$zip->getFromName('assets/'.$kind.'/'.$key);
        if($bytes===false||strlen($bytes)!==$asset['bytes']||!hash_equals($asset['sha256'],hash('sha256',$bytes)))throw new RuntimeException('Backup asset checksum mismatch.');
        $assets[$key]=$bytes;
    }
    $actual=array_keys($assets);sort($actual);sort($expectedKeys);
    if($actual!==$expectedKeys)throw new RuntimeException('Backup asset manifest does not cover every registered asset.');
    return $assets;
}
