<?php
declare(strict_types=1);
require dirname(__DIR__).'/app/backup-assets.php';
$assertions=0;
function check(bool $value,string $message):void{global $assertions;$assertions++;if(!$value)throw new RuntimeException($message);}
function fails(callable $call,string $message):void{try{$call();}catch(RuntimeException $e){check(true,$message);return;}check(false,$message);}
$root=dirname(__DIR__).'/storage/engineering/asset-test-'.bin2hex(random_bytes(8));
mkdir($root,0700,true);mkdir($root.'/books',0700);mkdir($root.'/scholars',0700);
$book=str_repeat('a',40).'.pdf';$photo=str_repeat('b',40).'.png';$zip=new ZipArchive();
try{
    file_put_contents($root.'/books/'.$book,'%PDF-1.4 test fixture');
    file_put_contents($root.'/scholars/'.$photo,"\x89PNG\r\n\x1a\nfixture");
    check(backup_asset_keys('books',[['file_key'=>$book],['file_key'=>$book],['file_key'=>null]])===[$book],'unique registered book assets');
    check(backup_asset_keys('scholars',[['image_key'=>$photo]])===[$photo],'registered scholar photo');
    fails(fn()=>backup_asset_keys('scholars',[['image_key'=>'../../secret.png']]),'path traversal rejected');
    fails(fn()=>backup_asset_keys('books',[['file_key'=>str_repeat('a',40).'.php']]),'active file rejected');
    $zip->open($root.'/archive.zip',ZipArchive::CREATE|ZipArchive::OVERWRITE);
    $books=backup_add_assets($zip,$root,'books',[$book]);$photos=backup_add_assets($zip,$root,'scholars',[$photo]);$zip->close();
    $zip->open($root.'/archive.zip');
    check(backup_read_assets($zip,'books',$books,[$book])[$book]==='%PDF-1.4 test fixture','book archived and hash verified');
    check(backup_read_assets($zip,'scholars',$photos,[$photo])[$photo]===file_get_contents($root.'/scholars/'.$photo),'scholar photo archived and hash verified');
    fails(fn()=>backup_read_assets($zip,'scholars',[],[$photo]),'incomplete legacy photo backup rejected before restore');
    $bad=$photos;$bad[0]['sha256']=str_repeat('0',64);fails(fn()=>backup_read_assets($zip,'scholars',$bad,[$photo]),'corruption rejected');
    fails(fn()=>backup_read_assets($zip,'scholars',array_merge($photos,$photos),[$photo]),'duplicate manifest asset rejected');
    $zip->close();
    echo 'PASS: '.$assertions." backup asset coverage/checksum/path-safety assertions.\n";
}finally{
    try{$zip->close();}catch(ValueError $ignored){}
    foreach([$root.'/books/'.$book,$root.'/scholars/'.$photo,$root.'/archive.zip'] as $path)if(is_file($path))unlink($path);
    rmdir($root.'/books');rmdir($root.'/scholars');rmdir($root);
}
